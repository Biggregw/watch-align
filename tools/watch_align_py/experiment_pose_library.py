#!/usr/bin/env python3
"""Pose-library-overlay experiment (method C): fit a physically-constrained
7-DOF pose (pose_library.py) from minute-track ticks only, then rectify and
detect markers in canonical space -- same downstream detection as method B
(multi_radius_pose_solver), different homography-fitting front end.

Read-only research experiment. Does not change production pose/marker
code, master geometry, or thresholds. Restricted to calibration-split
accepted images; validation is not touched until/unless a candidate is
frozen. Reuses the corpus's already-computed baseline ellipse/roll (from
per_image_measurements.csv) as pose_library's starting point, exactly as
experiment_multi_radius_homography.py does for method B -- this script
adds method C's columns; it does not recompute baseline (A) or
multi-radius (B), which are joined separately by source_id during
analysis.
"""
from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path
from typing import Optional

import cv2

import multi_radius_pose_solver as mrps
import pose_library as pl
import pose_library_measure as plm
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
        solve, new_markers = plm.measure_all_markers(bgr, edges, ellipse, roll)
        amb = plm.ambiguity_report(solve)

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
            "pose_fitted": "1" if solve.pose is not None else "0",
        }
        if solve.pose is not None:
            p = solve.pose
            out.update({
                "pose_theta_deg": f"{p.theta_deg:.4f}",
                "pose_phi_deg": f"{p.phi_deg:.4f}",
                "pose_psi_deg": f"{p.psi_deg:.4f}",
                "pose_k": f"{p.k:.5f}",
                "pose_scale": f"{p.scale:.4f}",
                "pose_score_px": f"{p.score_px:.5f}",
            })
            out.update({f"amb_{k}": (f"{v:.5f}" if isinstance(v, float) else v) for k, v in amb.items()})
        else:
            for key in ("pose_theta_deg", "pose_phi_deg", "pose_psi_deg", "pose_k", "pose_scale", "pose_score_px"):
                out[key] = ""
        for hour in HOURS:
            m = new_markers.get(hour)
            if m is None:
                out[f"marker_{hour}_poselib_measured"] = "0"
                out[f"marker_{hour}_poselib_radial_pct_r"] = ""
                out[f"marker_{hour}_poselib_angular_deg"] = ""
                out[f"marker_{hour}_poselib_area_norm"] = ""
                out[f"marker_{hour}_poselib_anisotropy"] = ""
            else:
                radial, angular, area, aniso = m
                out[f"marker_{hour}_poselib_measured"] = "1"
                out[f"marker_{hour}_poselib_radial_pct_r"] = f"{radial:.6g}"
                out[f"marker_{hour}_poselib_angular_deg"] = f"{angular:.6g}"
                out[f"marker_{hour}_poselib_area_norm"] = f"{area:.6g}"
                out[f"marker_{hour}_poselib_anisotropy"] = f"{aniso:.6g}"
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
    n_fitted = sum(1 for r in out_rows if r["pose_fitted"] == "1")
    print(f"pose fitted successfully: {n_fitted}/{len(out_rows)}")


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
