#!/usr/bin/env python3
"""Stage 3 stability analysis for the GMT 12-triangle profile
(experiment/gmt-proportional-geometry-v1, phase 3).

Restricts the PRIMARY population to <=10 degree apparent tilt images
only (per the reconciled research notes' practical-viewpoint policy),
computes physical-watch-level population statistics for a candidate
feature set spanning the hybrid normalisation policy (simple apex
radius; simple AND projective centre/base radius, so the "where
supported" choice can be made from real numbers; incidence-first
orientation candidates; shape kept secondary), and adds the two
stability checks the user asked for before any feature can be frozen:

- leave-one-physical-watch-out (LOWO): recompute the population
  median/MAD with each watch excluded in turn: max shift across all
  leave-outs is reported.
- hierarchical/watch-level bootstrap: resample watches with
  replacement (physical_watch_id is the independence unit), and within
  each resampled watch resample its own usable images with
  replacement, recompute that watch's median, then the population
  median across the resampled watches. Repeated B times.

>10 degree images are also summarised, but ONLY as a stress-test
comparison against the <=10 primary population -- never fed into the
LOWO/bootstrap stability checks or any frozen band.

This script does not decide which features to freeze; it produces the
numbers a human (or a following step) uses to make that call, mirroring
how gmt_12_triangle_profile.json was originally authored from
analyze_gmt_proportional_calibration.py's output.
"""
from __future__ import annotations

import csv
import json
import math
import random
from pathlib import Path
from typing import Dict, List, Optional

RADIAL_CANDIDATES = [
    "apex_r_simple",
    "centre_r_simple", "centre_r_projective",
    "base_r_simple", "base_r_projective",
]
ORIENTATION_CANDIDATES = [
    "centroid_tangential_offset_canonical",
    "apex_tangential_offset_canonical",
    "base_tangential_offset_canonical",
    "axis_incidence_canonical",
    "symmetry_axis_angular_deviation_deg",
]
SHAPE_SECONDARY = ["base_width_over_height"]

ALL_CANDIDATES = RADIAL_CANDIDATES + ORIENTATION_CANDIDATES + SHAPE_SECONDARY

PRIMARY_TILT_MAX_DEG = 10.0
BOOTSTRAP_ITERATIONS = 2000
BOOTSTRAP_SEED = 20260923  # fixed for reproducibility of this research run


def _f(v) -> Optional[float]:
    if v is None or v == "":
        return None
    try:
        x = float(v)
        return x if math.isfinite(x) else None
    except ValueError:
        return None


def _median(xs: List[float]) -> Optional[float]:
    if not xs:
        return None
    s = sorted(xs)
    n = len(s)
    mid = n // 2
    return s[mid] if n % 2 else (s[mid - 1] + s[mid]) / 2.0


def _mad(xs: List[float], med: Optional[float]) -> Optional[float]:
    if not xs or med is None:
        return None
    return _median([abs(x - med) for x in xs])


def _percentile(xs: List[float], q: float) -> Optional[float]:
    if not xs:
        return None
    s = sorted(xs)
    if len(s) == 1:
        return s[0]
    pos = q * (len(s) - 1)
    lo, hi = math.floor(pos), math.ceil(pos)
    if lo == hi:
        return s[lo]
    frac = pos - lo
    return s[lo] * (1 - frac) + s[hi] * frac


def _watch_medians(by_watch: Dict[str, List[float]]) -> Dict[str, float]:
    return {w: _median(vals) for w, vals in by_watch.items() if vals}


def analyze_feature(by_watch_primary: Dict[str, List[float]],
                     by_watch_stress: Dict[str, List[float]],
                     rng: random.Random) -> dict:
    watch_meds = _watch_medians(by_watch_primary)
    watches = sorted(watch_meds.keys())
    n_watches = len(watches)
    pop_vals = [watch_meds[w] for w in watches]
    pop_median = _median(pop_vals)
    pop_mad = _mad(pop_vals, pop_median)

    result = {
        "n_watches_primary": n_watches,
        "n_images_primary": sum(len(v) for v in by_watch_primary.values()),
        "population_median": pop_median,
        "population_mad": pop_mad,
        "population_p10": _percentile(pop_vals, 0.10),
        "population_p90": _percentile(pop_vals, 0.90),
        "population_min": min(pop_vals) if pop_vals else None,
        "population_max": max(pop_vals) if pop_vals else None,
        "within_watch_mad": [
            _mad(by_watch_primary[w], watch_meds[w]) for w in watches if len(by_watch_primary[w]) >= 2
        ],
    }

    if n_watches < 3:
        result["lowo_max_median_shift"] = None
        result["lowo_max_mad_shift"] = None
        result["bootstrap_median_of_medians"] = None
        result["bootstrap_p2_5"] = None
        result["bootstrap_p97_5"] = None
        result["bootstrap_iqr"] = None
        result["stability_note"] = "fewer than 3 primary watches -- LOWO/bootstrap not meaningful"
        _add_stress(result, by_watch_stress)
        return result

    # --- leave-one-physical-watch-out ---
    median_shifts, mad_shifts = [], []
    for held_out in watches:
        remaining = [watch_meds[w] for w in watches if w != held_out]
        m = _median(remaining)
        d = _mad(remaining, m)
        if pop_median is not None and m is not None:
            median_shifts.append(abs(m - pop_median))
        if pop_mad is not None and d is not None:
            mad_shifts.append(abs(d - pop_mad))
    result["lowo_max_median_shift"] = max(median_shifts) if median_shifts else None
    result["lowo_max_mad_shift"] = max(mad_shifts) if mad_shifts else None
    result["lowo_per_watch_median_shift"] = {
        w: (abs(_median([watch_meds[x] for x in watches if x != w]) - pop_median)
            if pop_median is not None else None)
        for w in watches
    }

    # --- hierarchical (watch-level) bootstrap ---
    boot_medians = []
    for _ in range(BOOTSTRAP_ITERATIONS):
        drawn_watch_medians = []
        for _ in range(n_watches):
            w = rng.choice(watches)
            imgs = by_watch_primary[w]
            resampled = [rng.choice(imgs) for _ in range(len(imgs))]
            wm = _median(resampled)
            if wm is not None:
                drawn_watch_medians.append(wm)
        bm = _median(drawn_watch_medians)
        if bm is not None:
            boot_medians.append(bm)
    result["bootstrap_median_of_medians"] = _median(boot_medians)
    result["bootstrap_p2_5"] = _percentile(boot_medians, 0.025)
    result["bootstrap_p97_5"] = _percentile(boot_medians, 0.975)
    q25, q75 = _percentile(boot_medians, 0.25), _percentile(boot_medians, 0.75)
    result["bootstrap_iqr"] = (q75 - q25) if (q25 is not None and q75 is not None) else None

    _add_stress(result, by_watch_stress)
    return result


def _add_stress(result: dict, by_watch_stress: Dict[str, List[float]]) -> None:
    stress_watch_meds = _watch_medians(by_watch_stress)
    stress_vals = list(stress_watch_meds.values())
    result["stress_gt10deg_n_watches"] = len(stress_watch_meds)
    result["stress_gt10deg_n_images"] = sum(len(v) for v in by_watch_stress.values())
    result["stress_gt10deg_median"] = _median(stress_vals)
    result["stress_gt10deg_mad"] = _mad(stress_vals, _median(stress_vals))


def load_rows(clean_csv: Path) -> List[dict]:
    return list(csv.DictReader(clean_csv.open(newline="", encoding="utf-8")))


def run(clean_csv: Path, out_json: Path) -> None:
    rows = load_rows(clean_csv)
    rng = random.Random(BOOTSTRAP_SEED)

    out = {"primary_tilt_max_deg": PRIMARY_TILT_MAX_DEG, "bootstrap_iterations": BOOTSTRAP_ITERATIONS,
           "features": {}}

    for feature in ALL_CANDIDATES:
        by_watch_primary: Dict[str, List[float]] = {}
        by_watch_stress: Dict[str, List[float]] = {}
        for r in rows:
            v = _f(r.get(feature))
            t = _f(r.get("tilt_deg"))
            if v is None or t is None:
                continue
            pid = r["physical_watch_id"]
            if t <= PRIMARY_TILT_MAX_DEG:
                by_watch_primary.setdefault(pid, []).append(v)
            else:
                by_watch_stress.setdefault(pid, []).append(v)
        out["features"][feature] = analyze_feature(by_watch_primary, by_watch_stress, rng)

    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(out, indent=2, default=str))
    print(f"wrote {out_json}")

    print(f"\n{'feature':45s} {'n_w':>4s} {'median':>9s} {'MAD':>9s} {'LOWO_dmed':>10s} {'boot_IQR':>10s} {'n_w_>10':>8s}")
    for feature in ALL_CANDIDATES:
        f = out["features"][feature]
        def fmt(x):
            return f"{x:.4f}" if isinstance(x, float) else "n/a"
        print(f"{feature:45s} {f['n_watches_primary']:>4d} {fmt(f['population_median']):>9s} "
              f"{fmt(f['population_mad']):>9s} {fmt(f.get('lowo_max_median_shift')):>10s} "
              f"{fmt(f.get('bootstrap_iqr')):>10s} {f['stress_gt10deg_n_watches']:>8d}")


def main() -> int:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--clean-csv", required=True, type=Path)
    ap.add_argument("--out-json", required=True, type=Path)
    args = ap.parse_args()
    run(args.clean_csv, args.out_json)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
