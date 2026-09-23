#!/usr/bin/env python3
"""Physical-watch-level statistics for the GMT 12-triangle proportional-
geometry calibration run (experiment/gmt-proportional-geometry-v1).

Pure numeric analysis of the already-produced per-image CSV -- no image
access, runs locally. `physical_watch_id` is treated as the independence
unit throughout: population statistics are computed over per-watch
medians, not over raw images, per the research rules. Image-level rows
remain in the per-image CSV for full inspectability.

This script explicitly does NOT hide the calibration sample size. With
as few as 1-2 physical watches (see the printed population summary),
MAD/p10/p90 are reported as literal descriptive numbers but are not a
meaningful genuine-population estimate -- this is stated in the output,
not left implicit.
"""
from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Dict, List, Optional

FEATURE_COLUMNS = [
    "apex_r_simple", "centre_r_simple", "base_r_simple",
    "apex_to_base_span_simple", "base_to_minute_track_gap_simple", "centre_to_minute_track_gap_simple",
    "triangle_height_over_minute_track_r", "base_width_over_height", "base_half_width_symmetry",
    "centroid_position_within_span", "centroid_tangential_offset_canonical", "centroid_angular_offset_deg",
    "symmetry_axis_angular_deviation_deg", "base_line_angular_deviation_deg",
    "apex_r_projective", "centre_r_projective", "base_r_projective",
    "apex_to_base_span_projective", "base_to_minute_track_gap_projective", "centre_to_minute_track_gap_projective",
    "apex_r_projective_over_round_inner", "centre_r_projective_over_round_centre", "base_r_projective_over_round_outer",
]

SIMPLE_PROJECTIVE_PAIRS = [
    ("apex_r_simple", "apex_r_projective"),
    ("centre_r_simple", "centre_r_projective"),
    ("base_r_simple", "base_r_projective"),
    ("apex_to_base_span_simple", "apex_to_base_span_projective"),
    ("base_to_minute_track_gap_simple", "base_to_minute_track_gap_projective"),
    ("centre_to_minute_track_gap_simple", "centre_to_minute_track_gap_projective"),
]


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


def _pearson(xs: List[float], ys: List[float]) -> Optional[float]:
    n = len(xs)
    if n < 3:
        return None
    mx, my = sum(xs) / n, sum(ys) / n
    sxy = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    sxx = sum((x - mx) ** 2 for x in xs)
    syy = sum((y - my) ** 2 for y in ys)
    if sxx <= 0 or syy <= 0:
        return None
    return sxy / math.sqrt(sxx * syy)


def analyze(per_image_csv: Path, out_watch_csv: Path, out_report: Path) -> None:
    rows = list(csv.DictReader(per_image_csv.open(newline="", encoding="utf-8")))

    by_watch: Dict[str, List[dict]] = {}
    for r in rows:
        by_watch.setdefault(r["physical_watch_id"], []).append(r)

    report_lines = []
    report_lines.append(f"Total images in calibration run: {len(rows)}")
    report_lines.append(f"Distinct physical_watch_id count: {len(by_watch)}")
    report_lines.append("")
    if len(by_watch) < 5:
        report_lines.append(
            f"*** CALIBRATION SAMPLE WARNING: only {len(by_watch)} independent physical "
            "watch(es) available in the calibration-split gen_candidate pool. Per-feature "
            "spread statistics (MAD, p10/p90) below are literal descriptive numbers computed "
            "from this sample; they are NOT a validated estimate of the genuine population "
            "distribution at this sample size. This experiment should be read as a feasibility "
            "and method-validation result, not a statistically supported genuine profile. ***"
        )
        report_lines.append("")

    for pid, rs in sorted(by_watch.items()):
        tilts = [_f(r["tilt_deg"]) for r in rs]
        report_lines.append(f"{pid}: {len(rs)} image(s), tilt range "
                             f"[{min(t for t in tilts if t is not None):.1f}, "
                             f"{max(t for t in tilts if t is not None):.1f}] deg")

    watch_rows = []
    for pid, rs in sorted(by_watch.items()):
        wr = {"physical_watch_id": pid, "n_images": len(rs)}
        for col in FEATURE_COLUMNS:
            vals = [_f(r.get(col)) for r in rs]
            vals = [v for v in vals if v is not None]
            wr[f"{col}_median"] = _median(vals)
            wr[f"{col}_n"] = len(vals)
            # within-watch repeatability: MAD across this watch's own images
            wr[f"{col}_within_watch_mad"] = _mad(vals, _median(vals)) if len(vals) >= 2 else None
        watch_rows.append(wr)

    out_watch_csv.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = list(watch_rows[0].keys()) if watch_rows else []
    with out_watch_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, restval="")
        w.writeheader()
        w.writerows(watch_rows)

    report_lines.append("")
    report_lines.append("=" * 78)
    report_lines.append("PER-FEATURE STATISTICS (physical-watch-level medians as the population)")
    report_lines.append("=" * 78)

    tilts_all = [_f(r["tilt_deg"]) for r in rows]
    for col in FEATURE_COLUMNS:
        watch_medians = [wr[f"{col}_median"] for wr in watch_rows if wr[f"{col}_median"] is not None]
        img_vals = [_f(r.get(col)) for r in rows]
        img_vals_present = [v for v in img_vals if v is not None]
        n_fail = len(rows) - len(img_vals_present)
        fail_rate = n_fail / len(rows) if rows else None

        within_mads = [wr[f"{col}_within_watch_mad"] for wr in watch_rows if wr.get(f"{col}_within_watch_mad") is not None]

        tilt_pairs = [(t, v) for t, v in zip(tilts_all, img_vals) if t is not None and v is not None]
        corr = _pearson([t for t, _ in tilt_pairs], [v for _, v in tilt_pairs]) if len(tilt_pairs) >= 3 else None

        le10 = [v for t, v in tilt_pairs if t <= 10.0]
        gt10 = [v for t, v in tilt_pairs if t > 10.0]

        report_lines.append(f"\n-- {col} --")
        report_lines.append(f"  n_watches_with_value={len(watch_medians)}  n_images_with_value={len(img_vals_present)}"
                             f"  measurement_failure_rate={fail_rate}")
        report_lines.append(f"  watch-median-population: median={_median(watch_medians)} "
                             f"MAD={_mad(watch_medians, _median(watch_medians))} "
                             f"p10={_percentile(watch_medians, 0.10)} p90={_percentile(watch_medians, 0.90)} "
                             f"min={min(watch_medians) if watch_medians else None} "
                             f"max={max(watch_medians) if watch_medians else None}")
        report_lines.append(f"  within-watch repeatability (MAD across a watch's own images): "
                             f"{within_mads if within_mads else 'n/a (no watch with >=2 usable images)'}")
        report_lines.append(f"  tilt correlation (image-level, Pearson r): {corr}")
        report_lines.append(f"  <=10deg subgroup: n={len(le10)} median={_median(le10)}")
        report_lines.append(f"  >10deg subgroup:  n={len(gt10)} median={_median(gt10)}")

    report_lines.append("\n" + "=" * 78)
    report_lines.append("SIMPLE vs PROJECTIVE RADIAL REPRESENTATION COMPARISON")
    report_lines.append("=" * 78)
    for simple_col, proj_col in SIMPLE_PROJECTIVE_PAIRS:
        s_vals = [_f(r.get(simple_col)) for r in rows]
        p_vals = [_f(r.get(proj_col)) for r in rows]
        s_present = [v for v in s_vals if v is not None]
        p_present = [v for v in p_vals if v is not None]
        s_tilt_pairs = [(t, v) for t, v in zip(tilts_all, s_vals) if t is not None and v is not None]
        p_tilt_pairs = [(t, v) for t, v in zip(tilts_all, p_vals) if t is not None and v is not None]
        s_corr = _pearson([t for t, _ in s_tilt_pairs], [v for _, v in s_tilt_pairs]) if len(s_tilt_pairs) >= 3 else None
        p_corr = _pearson([t for t, _ in p_tilt_pairs], [v for _, v in p_tilt_pairs]) if len(p_tilt_pairs) >= 3 else None
        report_lines.append(f"\n-- {simple_col}  vs  {proj_col} --")
        report_lines.append(f"  coverage: simple n={len(s_present)}/{len(rows)}  projective n={len(p_present)}/{len(rows)} "
                             "(projective requires >=5 round markers AND a well-conditioned fit -- strictly a subset)")
        report_lines.append(f"  simple spread:     MAD={_mad(s_present, _median(s_present))}")
        report_lines.append(f"  projective spread: MAD={_mad(p_present, _median(p_present))}")
        report_lines.append(f"  simple tilt-correlation:     {s_corr}")
        report_lines.append(f"  projective tilt-correlation: {p_corr}")

    report_lines.append("\n" + "=" * 78)
    report_lines.append("CORRELATION AMONG apex/centre/base RESIDUALS (translation vs shape-error cross-check)")
    report_lines.append("=" * 78)
    for a_col, b_col in (("apex_r_simple", "centre_r_simple"), ("centre_r_simple", "base_r_simple"),
                          ("apex_r_simple", "base_r_simple"),
                          ("apex_r_projective", "centre_r_projective"), ("centre_r_projective", "base_r_projective"),
                          ("apex_r_projective", "base_r_projective")):
        pairs = [(_f(r.get(a_col)), _f(r.get(b_col))) for r in rows]
        pairs = [(a, b) for a, b in pairs if a is not None and b is not None]
        corr = _pearson([a for a, _ in pairs], [b for _, b in pairs]) if len(pairs) >= 3 else None
        report_lines.append(f"  corr({a_col}, {b_col}) over n={len(pairs)} images: {corr}")

    out_report.write_text("\n".join(report_lines), encoding="utf-8")
    print("\n".join(report_lines))
    print(f"\nwrote {out_watch_csv} and {out_report}")


def main() -> int:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image-csv", required=True, type=Path)
    ap.add_argument("--out-watch-csv", required=True, type=Path)
    ap.add_argument("--out-report", required=True, type=Path)
    args = ap.parse_args()
    analyze(args.per_image_csv, args.out_watch_csv, args.out_report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
