#!/usr/bin/env python3
"""Part 8: calibration-corpus run for the projective marker-consensus
approach. Reuses the already-computed baseline ellipse/roll from
per_image_measurements.csv (same pattern as experiment_multi_radius_
homography.py / experiment_pose_library.py) rather than re-running the
full pipeline -- much faster, and keeps this squarely a Python-only
research experiment on top of already-validated acquisition output.

Calibration split only. Does not inspect or optimise against validation.
Does not derive production thresholds -- this script reports descriptive
statistics (coverage, residual distributions, within-watch repeatability,
outlier rate, tilt/factory/shape dependence, segmentation failures) for
human review, nothing more.
"""
from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path
from typing import Optional

import cv2

import marker_consensus as mc
import marker_consensus_analysis as mca
from geometry import RotatedRect
from image_io import resize_to_max_dim

ALL_HOURS = mc.ALL_MARKER_HOURS


def _ellipse_from_row(row: dict) -> Optional[RotatedRect]:
    try:
        return RotatedRect(
            float(row["ellipse_cx"]), float(row["ellipse_cy"]),
            float(row["ellipse_w"]), float(row["ellipse_h"]), float(row["ellipse_angle_deg"]),
        )
    except (KeyError, ValueError):
        return None


def run(per_image_csv: Path, images_root: Path, out_csv: Path, split_filter: str = "calibration") -> None:
    with per_image_csv.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    selected = [r for r in rows if r.get("pipeline_outcome") == "accepted"
                and (split_filter in ("", "all") or r.get("split") == split_filter)]
    print(f"selected {len(selected)} accepted images (split={split_filter!r})")

    out_rows = []
    missing = 0
    for i, row in enumerate(selected, start=1):
        image_path = images_root / row.get("local_path", "")
        if not image_path.is_file():
            missing += 1
            continue
        ellipse = _ellipse_from_row(row)
        if ellipse is None:
            missing += 1
            continue
        raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
        if raw is None:
            missing += 1
            continue
        bgr = resize_to_max_dim(raw, 1600)
        gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
        blur = cv2.GaussianBlur(gray, (5, 5), 1.2)
        edges = cv2.Canny(blur, 55, 145)
        roll = float(row["acquisition_roll_deg"])
        dial_radius_px = (max(ellipse.w, ellipse.h) + min(ellipse.w, ellipse.h)) / 4.0

        print(f"[{i}/{len(selected)}] {row['source_id']} {row['local_path']}")
        analysis = mca.analyze_frame(gray, edges, ellipse, roll, dial_radius_px)

        for h in ALL_HOURS:
            obs = analysis.observations.get(h)
            out = {
                "source_id": row["source_id"], "physical_watch_id": row["physical_watch_id"],
                "class_label": row["class_label"], "provenance": row.get("provenance", ""),
                "split": row["split"], "factory": row.get("factory", ""),
                "local_path": row["local_path"], "tilt_deg": row.get("tilt_deg", ""),
                "hour": h, "shape": obs.shape if obs else "",
                "segmented": "1" if obs else "0",
                "confidence": f"{obs.confidence:.4f}" if obs else "",
            }
            loo = analysis.outer_loo.get(h)
            if loo is not None:
                out["loo_outer_fit_ok"] = "1" if loo.fit_ok else "0"
                out["loo_outer_n_peers"] = loo.n_peers
                out["loo_outer_residual_px"] = f"{loo.residual_px:.4f}" if loo.fit_ok else ""
            ang = analysis.angular.get(h)
            if ang is not None:
                out["angular_residual_deg"] = f"{ang.angular_residual_deg:.4f}"
            clr = analysis.clearance.get(h)
            if clr is not None and math.isfinite(clr.clearance_normalized):
                out["clearance_normalized"] = f"{clr.clearance_normalized:.4f}"
            shp = analysis.shape_offsets.get(h)
            if shp is not None and shp.fit_ok:
                out["shape_offset_residual_px"] = f"{shp.residual_px:.4f}"
            verdict = analysis.verdicts.get(h)
            if verdict is not None:
                out["verdict_state"] = verdict.state
            out_rows.append(out)

    out_csv.parent.mkdir(parents=True, exist_ok=True)
    if not out_rows:
        raise RuntimeError("no rows produced")
    fieldnames = list(out_rows[0].keys())
    for r in out_rows:
        for k in r.keys():
            if k not in fieldnames:
                fieldnames.append(k)
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, restval="")
        w.writeheader()
        w.writerows(out_rows)
    print(f"wrote {len(out_rows)} rows to {out_csv}")
    print(f"missing/undecodable images skipped: {missing}")
    n_segmented = sum(1 for r in out_rows if r["segmented"] == "1")
    print(f"marker-observations segmented: {n_segmented}/{len(out_rows)}")
    n_loo_ok = sum(1 for r in out_rows if r.get("loo_outer_fit_ok") == "1")
    print(f"leave-one-out envelope fits succeeded: {n_loo_ok}/{len(out_rows)}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image-csv", required=True, type=Path)
    ap.add_argument("--images-root", required=True, type=Path)
    ap.add_argument("--out-csv", required=True, type=Path)
    ap.add_argument("--split", default="calibration")
    args = ap.parse_args()
    run(args.per_image_csv, args.images_root, args.out_csv, args.split)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
