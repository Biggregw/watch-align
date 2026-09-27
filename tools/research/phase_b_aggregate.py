#!/usr/bin/env python3
"""Phase B aggregation: per-watch and population summaries for the Phase A
dimensionless ratios, computed across the genuine control set.

Reads docs/research/gmt-phase-b-results/per_image_measurements.csv, excludes
any image_url listed in docs/research/gmt-phase-b-results/visual_exclusions.csv
(obvious detector failures identified by visual review of the acceptance
overlays -- see docs/research/GMT_PHASE_B_GENUINE_BASELINE.md), collapses
multiple images of one physical_watch_id to a per-watch median (one physical
watch = one independent sample, never counted per-photograph), then computes
population statistics over the per-watch medians.

Does not touch the detector. A ratio with a wide or unstable distribution is
reported as such, not tightened by excluding otherwise-valid images.
"""
from __future__ import annotations

import csv
import json
import math
from collections import defaultdict
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
RESULTS_DIR = ROOT / "docs" / "research" / "gmt-phase-b-results"
PER_IMAGE_CSV = RESULTS_DIR / "per_image_measurements.csv"
EXCLUSIONS_CSV = RESULTS_DIR / "visual_exclusions.csv"

RATIO_NAMES = [
    "apex_position_in_interval",
    "base_position_in_interval",
    "triangle_height_ratio",
    "base_width_over_height",
    "horizontal_displacement_normalized",
]


def finite(v):
    try:
        return v is not None and v != "" and math.isfinite(float(v))
    except Exception:
        return False


def read_exclusions() -> set[str]:
    if not EXCLUSIONS_CSV.exists():
        return set()
    with EXCLUSIONS_CSV.open(newline="", encoding="utf-8") as f:
        return {r["image_url"] for r in csv.DictReader(f)}


def robust_stats(vals: list[float]) -> dict:
    a = np.asarray(vals, dtype=float)
    return {
        "n": int(len(a)),
        "median": float(np.median(a)),
        "mean": float(np.mean(a)),
        "std": float(np.std(a, ddof=1)) if len(a) > 1 else 0.0,
        "min": float(a.min()),
        "max": float(a.max()),
        "p10": float(np.percentile(a, 10)),
        "p90": float(np.percentile(a, 90)),
    }


def main() -> int:
    if not PER_IMAGE_CSV.exists():
        print("no per_image_measurements.csv yet -- run phase_b_measure.py first")
        return 0

    excluded_urls = read_exclusions()
    with PER_IMAGE_CSV.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    used_rows = []
    n_excluded_visual = 0
    n_fetch_failed = 0
    for r in rows:
        if r.get("fetch_ok") != "True":
            n_fetch_failed += 1
            continue
        if r.get("image_url") in excluded_urls:
            n_excluded_visual += 1
            continue
        used_rows.append(r)

    by_watch = defaultdict(list)
    for r in used_rows:
        by_watch[r["physical_watch_id"]].append(r)

    watch_medians = []
    for wid, wrows in sorted(by_watch.items()):
        entry = {"physical_watch_id": wid, "provenance_class": wrows[0]["provenance_class"],
                  "n_images": len(wrows)}
        for name in RATIO_NAMES:
            vals = [float(r[name]) for r in wrows if finite(r.get(name))]
            entry[name] = float(np.median(vals)) if vals else None
            entry[f"{name}_n_images_assessable"] = len(vals)
        watch_medians.append(entry)

    with (RESULTS_DIR / "per_physical_watch_medians.csv").open("w", newline="", encoding="utf-8") as f:
        keys = list(watch_medians[0].keys()) if watch_medians else []
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        w.writerows(watch_medians)

    population = {}
    for name in RATIO_NAMES:
        vals = [w[name] for w in watch_medians if w.get(name) is not None]
        st = robust_stats(vals) if vals else None
        population[name] = st

    summary = {
        "n_images_total": len(rows),
        "n_images_fetch_failed": n_fetch_failed,
        "n_images_excluded_visual_failure": n_excluded_visual,
        "n_images_used": len(used_rows),
        "n_independent_watches": len(watch_medians),
        "provenance_classes": sorted({w["provenance_class"] for w in watch_medians}),
        "ratios": population,
    }
    (RESULTS_DIR / "population_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

    pop_rows = []
    for name in RATIO_NAMES:
        st = population[name]
        if st:
            pop_rows.append({"ratio": name, **st})
        else:
            pop_rows.append({"ratio": name, "n": 0})
    with (RESULTS_DIR / "population_summary.csv").open("w", newline="", encoding="utf-8") as f:
        keys = sorted({k for r in pop_rows for k in r})
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        w.writerows(pop_rows)

    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
