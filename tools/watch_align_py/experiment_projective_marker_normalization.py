#!/usr/bin/env python3
"""Read-only experiment: compare current ellipse-normalized marker coordinates with
coordinates normalized through the *accepted final homography*.

This deliberately does not change production marker QC. Candidate marker blobs are
found with the existing detector and the existing ellipse-derived ROIs, so the only
variable is how each detected marker centre is mapped back into canonical dial space.

Outputs:
  projective_marker_per_image.csv
  projective_marker_summary.md

The question is narrow: does using H^-1 remove the high-tilt radial instability seen
in the corpus without degrading angular repeatability?
"""
from __future__ import annotations

import argparse
import csv
import math
from collections import defaultdict
from pathlib import Path
from statistics import median
from typing import Dict, Iterable, List, Optional, Tuple

import cv2
import numpy as np

import marker_qc
import pipeline


HOURS = [1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12]
HIGH_TILT_DEG = 15.0
SUSPICIOUS = {
    ("rep_cf_QsJT2wk", "rep/calibration/rep_cf_QsJT2wk/image_06.jpg"),
    ("rep_arf_8xahrjm", "rep/validation/rep_arf_8xahrjm/image_02.jpg"),
}


def _f(x, default=math.nan):
    try:
        return float(x)
    except (TypeError, ValueError):
        return default


def _mad(values: Iterable[float]) -> float:
    vals = [float(v) for v in values if math.isfinite(float(v))]
    if not vals:
        return math.nan
    m = median(vals)
    return median(abs(v - m) for v in vals)


def _med(values: Iterable[float]) -> float:
    vals = [float(v) for v in values if math.isfinite(float(v))]
    return median(vals) if vals else math.nan


def _pct_improvement(before: float, after: float) -> float:
    if not (math.isfinite(before) and math.isfinite(after)) or before <= 1e-12:
        return math.nan
    return 100.0 * (before - after) / before


def _project_inverse(h_inv: np.ndarray, x: float, y: float) -> Optional[Tuple[float, float]]:
    v = h_inv @ np.array([x, y, 1.0], dtype=np.float64)
    if not np.all(np.isfinite(v)) or abs(v[2]) < 1e-9:
        return None
    return float(v[0] / v[2]), float(v[1] / v[2])


def _baseline_offsets(ellipse, roll: float, center: np.ndarray, hour: int) -> Tuple[float, float]:
    corrected = marker_qc._undo_ellipse_distortion(
        ellipse, float(center[0] - ellipse.cx), float(center[1] - ellipse.cy)
    )
    radius = math.hypot(corrected[0], corrected[1])
    clock = marker_qc._clock_angle_deg(0, 0, corrected[0], corrected[1])
    expected_clock = marker_qc._wrap360(marker_qc._hour_angle_deg(hour) + roll)
    angular = marker_qc._wrap180(clock - expected_clock)
    radial = marker_qc.radial_offset_pct_r(radius, hour)
    return angular, radial


def _projective_offsets(h_inv: np.ndarray, center: np.ndarray, hour: int) -> Tuple[float, float]:
    canonical = _project_inverse(h_inv, float(center[0]), float(center[1]))
    if canonical is None:
        return math.nan, math.nan
    x, y = canonical
    radius = math.hypot(x, y)
    clock = marker_qc._clock_angle_deg(0, 0, x, y)
    angular = marker_qc._wrap180(clock - marker_qc._hour_angle_deg(hour))
    radial = marker_qc.radial_offset_pct_r(radius, hour)
    return angular, radial


def _safe_gate(angular: float, radial: float) -> bool:
    return (
        math.isfinite(angular)
        and math.isfinite(radial)
        and abs(angular) <= 4.0
        and abs(radial) <= 8.0
    )


def _resolve_image(images_root: Path, local_path: str) -> Optional[Path]:
    p = images_root / local_path
    if p.is_file():
        return p
    if local_path.startswith("datasets/126710BLNR/"):
        p = images_root / local_path.split("datasets/126710BLNR/", 1)[1]
        if p.is_file():
            return p
    return None


def run(per_image_csv: Path, images_root: Path, out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)

    with per_image_csv.open(newline="", encoding="utf-8") as f:
        source_rows = list(csv.DictReader(f))

    accepted = [r for r in source_rows if r.get("pipeline_outcome") == "accepted"]
    output_rows: List[dict] = []
    missing = 0
    rebuild_rejected = 0
    singular_h = 0

    for row in accepted:
        local_path = row.get("local_path", "")
        image_path = _resolve_image(images_root, local_path)
        if image_path is None:
            missing += 1
            continue

        bgr = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
        if bgr is None:
            missing += 1
            continue

        result = pipeline.build(bgr)
        if not result.accepted or result.H is None or result.acquisition is None:
            rebuild_rejected += 1
            continue

        try:
            h_inv = np.linalg.inv(result.H)
        except np.linalg.LinAlgError:
            singular_h += 1
            continue

        ellipse = result.acquisition.dial_ellipse
        if ellipse is None:
            rebuild_rejected += 1
            continue

        gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
        blur = cv2.GaussianBlur(gray, (5, 5), 1.2)
        edges = cv2.Canny(blur, 55, 145)

        watch_id = row.get("physical_watch_id", "")
        source_id = row.get("source_id", "")
        tilt = float(result.tilt_deg)
        roll = float(result.acquisition.roll_deg)
        dial_radius_px = float(result.dial_radius_px)

        out = {
            "source_id": source_id,
            "physical_watch_id": watch_id,
            "class_label": row.get("class_label", ""),
            "split": row.get("split", ""),
            "factory": row.get("factory", ""),
            "local_path": local_path,
            "tilt_deg": f"{tilt:.8g}",
            "center_displacement_frac": row.get("center_displacement_frac", ""),
            "pose_confidence": row.get("pose_confidence", ""),
            "suspicious_named_case": "1" if (source_id, local_path) in SUSPICIOUS else "0",
        }

        measured = 0
        for hour in HOURS:
            c = marker_qc._measure_projected_marker(
                gray, ellipse, roll, dial_radius_px, hour
            )
            prefix = f"marker_{hour}_"
            if c is None:
                out[prefix + "detected"] = "0"
                for name in (
                    "baseline_angular_deg", "baseline_radial_pct_r",
                    "projective_angular_deg", "projective_radial_pct_r",
                    "baseline_gate", "projective_gate",
                ):
                    out[prefix + name] = ""
                continue

            measured += 1
            b_ang, b_rad = _baseline_offsets(ellipse, roll, c.center, hour)
            p_ang, p_rad = _projective_offsets(h_inv, c.center, hour)
            out[prefix + "detected"] = "1"
            out[prefix + "baseline_angular_deg"] = f"{b_ang:.10g}"
            out[prefix + "baseline_radial_pct_r"] = f"{b_rad:.10g}"
            out[prefix + "projective_angular_deg"] = f"{p_ang:.10g}"
            out[prefix + "projective_radial_pct_r"] = f"{p_rad:.10g}"
            out[prefix + "baseline_gate"] = "1" if _safe_gate(b_ang, b_rad) else "0"
            out[prefix + "projective_gate"] = "1" if _safe_gate(p_ang, p_rad) else "0"

        out["markers_detected"] = str(measured)
        output_rows.append(out)

    if not output_rows:
        raise RuntimeError("No accepted images could be reprocessed")

    fieldnames: List[str] = list(output_rows[0].keys())
    csv_path = out_dir / "projective_marker_per_image.csv"
    with csv_path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        w.writerows(output_rows)

    residual_records = []
    for hour in HOURS:
        for metric in ("angular", "radial"):
            for method in ("baseline", "projective"):
                key = (f"marker_{hour}_{method}_angular_deg" if metric == "angular"
                       else f"marker_{hour}_{method}_radial_pct_r")
                by_watch: Dict[str, List[Tuple[dict, float]]] = defaultdict(list)
                for r in output_rows:
                    v = _f(r.get(key, ""))
                    if math.isfinite(v):
                        by_watch[r["physical_watch_id"]].append((r, v))
                for watch_id, items in by_watch.items():
                    if len(items) < 2:
                        continue
                    m = median(v for _r, v in items)
                    for r, v in items:
                        residual_records.append({
                            "hour": hour,
                            "metric": metric,
                            "method": method,
                            "watch": watch_id,
                            "tilt": _f(r["tilt_deg"]),
                            "residual": abs(v - m),
                        })

    def residual_summary(metric: str, method: str, high: bool) -> Tuple[int, float, float]:
        vals = [
            rr["residual"] for rr in residual_records
            if rr["metric"] == metric
            and rr["method"] == method
            and ((rr["tilt"] > HIGH_TILT_DEG) if high else (rr["tilt"] <= HIGH_TILT_DEG))
            and math.isfinite(rr["residual"])
        ]
        return len(vals), _med(vals), _mad(vals)

    def pooled_mads(metric: str, method: str) -> List[float]:
        suffix = "_deg" if metric == "angular" else "_pct_r"
        result_mads = []
        for hour in HOURS:
            key = f"marker_{hour}_{method}_{metric}{suffix}"
            by_watch = defaultdict(list)
            for r in output_rows:
                v = _f(r.get(key, ""))
                if math.isfinite(v):
                    by_watch[r["physical_watch_id"]].append(v)
            for vals in by_watch.values():
                if len(vals) >= 2:
                    result_mads.append(_mad(vals))
        return result_mads

    b_rad_mads = pooled_mads("radial", "baseline")
    p_rad_mads = pooled_mads("radial", "projective")
    b_ang_mads = pooled_mads("angular", "baseline")
    p_ang_mads = pooled_mads("angular", "projective")

    lines = []
    lines.append("# Projective marker-normalisation experiment")
    lines.append("")
    lines.append("Read-only experiment. Production pose/QC code and thresholds were not changed.")
    lines.append("")
    lines.append(f"- Source accepted rows: {len(accepted)}")
    lines.append(f"- Successfully reprocessed accepted images: {len(output_rows)}")
    lines.append(f"- Missing/undecodable images: {missing}")
    lines.append(f"- Rebuilds no longer accepted: {rebuild_rejected}")
    lines.append(f"- Singular homographies: {singular_h}")
    lines.append("")
    lines.append("## Core comparison")
    lines.append("")
    lines.append("Each marker blob is detected with the current production ellipse ROI. Only the coordinate normalisation changes:")
    lines.append("")
    lines.append("- **baseline**: current ellipse-only undo")
    lines.append("- **projective**: map the exact same detected marker centre through the inverse accepted final homography")
    lines.append("")
    lines.append("| Metric | Baseline pooled within-watch MAD | Projective pooled within-watch MAD | Improvement |")
    lines.append("|---|---:|---:|---:|")
    for label, bvals, pvals, unit in (
        ("radial", b_rad_mads, p_rad_mads, "%R"),
        ("angular", b_ang_mads, p_ang_mads, "deg"),
    ):
        b = _med(bvals)
        p = _med(pvals)
        imp = _pct_improvement(b, p)
        lines.append(f"| {label} | {b:.4f} {unit} | {p:.4f} {unit} | {imp:+.1f}% |")
    lines.append("")
    lines.append(f"## Residuals by apparent tilt (cut at {HIGH_TILT_DEG:.0f}°)")
    lines.append("")
    lines.append("Residual = absolute deviation of one photo from that physical watch's own median.")
    lines.append("")
    lines.append("| Metric | Tilt group | Method | n | Median residual | MAD of residual |")
    lines.append("|---|---|---|---:|---:|---:|")
    for metric, unit in (("radial", "%R"), ("angular", "deg")):
        for high, group in ((False, "low"), (True, "high")):
            for method in ("baseline", "projective"):
                n, medv, madv = residual_summary(metric, method, high)
                lines.append(f"| {metric} | {group} | {method} | {n} | {medv:.4f} {unit} | {madv:.4f} {unit} |")

    lines.append("")
    lines.append("## Named high-tilt audit cases")
    lines.append("")
    for sid, lp in sorted(SUSPICIOUS):
        matches = [r for r in output_rows if r["source_id"] == sid and r["local_path"] == lp]
        if not matches:
            lines.append(f"### {sid} / {lp}")
            lines.append("")
            lines.append("Not present in reprocessed output.")
            lines.append("")
            continue
        r = matches[0]
        lines.append(f"### {sid} / {lp}")
        lines.append("")
        lines.append(f"- tilt: {r['tilt_deg']}°")
        lines.append("")
        lines.append("| Hour | baseline radial | projective radial | baseline angular | projective angular |")
        lines.append("|---:|---:|---:|---:|---:|")
        for hour in HOURS:
            br = r.get(f"marker_{hour}_baseline_radial_pct_r", "")
            pr = r.get(f"marker_{hour}_projective_radial_pct_r", "")
            ba = r.get(f"marker_{hour}_baseline_angular_deg", "")
            pa = r.get(f"marker_{hour}_projective_angular_deg", "")
            if not any((br, pr, ba, pa)):
                continue
            lines.append(f"| {hour} | {br or 'n/a'} | {pr or 'n/a'} | {ba or 'n/a'} | {pa or 'n/a'} |")
        lines.append("")

    high_rad = [rr for rr in residual_records if rr["metric"] == "radial" and rr["tilt"] > HIGH_TILT_DEG]
    idx = defaultdict(dict)
    for rr in high_rad:
        idx[(rr["watch"], rr["hour"])][rr["method"]] = rr["residual"]
    pairs = [d for d in idx.values() if "baseline" in d and "projective" in d]
    better = sum(d["projective"] < d["baseline"] for d in pairs)
    worse = sum(d["projective"] > d["baseline"] for d in pairs)
    equal = len(pairs) - better - worse
    lines.append("## High-tilt directional check")
    lines.append("")
    lines.append(f"Across paired high-tilt watch/hour residuals: projective better {better}, worse {worse}, equal {equal}.")
    lines.append("")
    lines.append("This experiment is evidence only. It does not justify a production change by itself; visual review and held-out validation remain required.")

    (out_dir / "projective_marker_summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image-csv", required=True, type=Path)
    ap.add_argument("--images-root", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    args = ap.parse_args()
    run(args.per_image_csv, args.images_root, args.out_dir)


if __name__ == "__main__":
    main()
