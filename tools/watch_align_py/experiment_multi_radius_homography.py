#!/usr/bin/env python3
"""Calibration-only A/B experiment: current ellipse-only marker measurement
vs. the new multi_radius_pose_solver (fit a homography from minute-track
ticks + dial boundary at two radii, rectify, then detect markers in
canonical space).

Read-only research experiment. Does not change production pose/marker code,
master geometry, or thresholds. Restricted to calibration-split accepted
images; validation is not touched until/unless a candidate is frozen.
"""
from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path
from typing import Optional

import cv2

import multi_radius_pose_solver as mrps
from geometry import RotatedRect
from image_io import resize_to_max_dim

HOURS = mrps.MARKER_HOURS


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

        print(f"[{i}/{len(selected)}] {row['source_id']} {row['local_path']}")
        solve, new_markers = mrps.measure_all_markers(bgr, edges, ellipse, roll)

        out = {
            "source_id": row["source_id"],
            "physical_watch_id": row["physical_watch_id"],
            "class_label": row["class_label"],
            "provenance": row.get("provenance", ""),
            "split": row["split"],
            "factory": row.get("factory", ""),
            "local_path": row["local_path"],
            "tilt_deg": row.get("tilt_deg", ""),
            "n_ticks": solve.n_ticks,
            "n_boundary": solve.n_boundary,
            "reproj_error_px": f"{solve.reproj_error_px:.6g}" if math.isfinite(solve.reproj_error_px) else "",
            "inlier_fraction": f"{solve.inlier_fraction:.6g}",
            "homography_fitted": "1" if solve.homography is not None else "0",
        }
        for hour in HOURS:
            baseline_measured = row.get(f"marker_{hour}_measured", "")
            out[f"marker_{hour}_baseline_measured"] = baseline_measured
            out[f"marker_{hour}_baseline_radial_pct_r"] = row.get(f"marker_{hour}_radial_pct_r", "")
            out[f"marker_{hour}_baseline_angular_deg"] = row.get(f"marker_{hour}_angular_deg", "")
            m = new_markers.get(hour)
            if m is None:
                out[f"marker_{hour}_new_measured"] = "0"
                out[f"marker_{hour}_new_radial_pct_r"] = ""
                out[f"marker_{hour}_new_angular_deg"] = ""
                out[f"marker_{hour}_new_area_norm"] = ""
                out[f"marker_{hour}_new_anisotropy"] = ""
            else:
                radial, angular, area, aniso = m
                out[f"marker_{hour}_new_measured"] = "1"
                out[f"marker_{hour}_new_radial_pct_r"] = f"{radial:.6g}"
                out[f"marker_{hour}_new_angular_deg"] = f"{angular:.6g}"
                out[f"marker_{hour}_new_area_norm"] = f"{area:.6g}"
                out[f"marker_{hour}_new_anisotropy"] = f"{aniso:.6g}"
        out_rows.append(out)

    out_csv.parent.mkdir(parents=True, exist_ok=True)
    if not out_rows:
        raise RuntimeError("no rows produced")
    fieldnames = list(out_rows[0].keys())
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, restval="")
        w.writeheader()
        w.writerows(out_rows)
    print(f"wrote {len(out_rows)} rows to {out_csv}")
    print(f"missing/undecodable images skipped: {missing}")
    n_fitted = sum(1 for r in out_rows if r["homography_fitted"] == "1")
    print(f"homography fitted successfully: {n_fitted}/{len(out_rows)}")


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
