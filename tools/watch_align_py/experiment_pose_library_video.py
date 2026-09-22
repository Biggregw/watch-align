#!/usr/bin/env python3
"""Same-watch video stress test: A (baseline ellipse/affine) vs B (multi-
radius rectify-then-detect) vs C (pose-library-overlay), all three run on
frames of ONE continuous video of ONE physical replica watch. Frames are
NOT independent watches and are NOT authenticity ground truth -- used only
to check whether marker measurements of the SAME physical markers stay
stable as only the camera viewpoint changes.

Unlike the calibration-corpus experiments, this runs the full production
pipeline (pipeline.build) from scratch per frame, since these frames have
no pre-existing per_image_measurements.csv row (baseline ellipse/roll/
markers are not known in advance here).
"""
from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path
from typing import Optional

import cv2
import numpy as np

import marker_qc
import master
import multi_radius_pose_solver as mrps
import pipeline
import pose_library as pl
import pose_library_measure as plm
from image_io import resize_to_max_dim

HOURS = mrps.MARKER_HOURS
WATCH_ID = "video_same_watch"


def _finite(x) -> Optional[float]:
    try:
        xf = float(x)
    except (TypeError, ValueError):
        return None
    return xf if math.isfinite(xf) else None


def process_one(image_path: Path, frame_label: str) -> dict:
    raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    if raw is None:
        return {"frame_label": frame_label, "local_path": str(image_path), "pipeline_outcome": "undecodable"}
    bgr = resize_to_max_dim(raw, 1600)

    row: dict = {"frame_label": frame_label, "physical_watch_id": WATCH_ID,
                 "local_path": str(image_path)}
    try:
        result = pipeline.build(bgr)
    except Exception as exc:  # pragma: no cover - defensive, must not drop a frame
        row["pipeline_outcome"] = "exception"
        row["failure_reason"] = f"{type(exc).__name__}: {exc}"
        return row

    if result.reason:
        row["pipeline_outcome"] = "acquisition_failed"
        row["failure_reason"] = result.reason
        return row

    row["pipeline_outcome"] = "accepted" if result.accepted else "rejected"
    row["tilt_deg"] = _finite(result.tilt_deg)
    row["solved_roll_deg"] = _finite(result.solved_roll)

    acq = result.acquisition
    ellipse = None
    if acq is not None and acq.dial_ellipse is not None:
        e = acq.dial_ellipse
        ellipse = e
        row["ellipse_cx"] = _finite(e.cx)
        row["ellipse_cy"] = _finite(e.cy)
        row["ellipse_w"] = _finite(e.w)
        row["ellipse_h"] = _finite(e.h)
        row["ellipse_angle_deg"] = _finite(e.angle_deg)
        row["acquisition_roll_deg"] = _finite(acq.roll_deg)

    # baseline (A) marker measurements, from the production pipeline's own result.
    # result.markers holds one MarkerDiagnostic per hour (never None at a valid
    # index) with its own `.measured` flag -- must check that, not just None,
    # to match analyze_corpus.py's convention.
    if result.markers is not None:
        for m in result.markers:
            if m is None or not m.measured:
                continue
            row[f"marker_{m.hour}_A_measured"] = "1"
            row[f"marker_{m.hour}_A_radial_pct_r"] = f"{m.radial_pct_r:.6g}"
            row[f"marker_{m.hour}_A_angular_deg"] = f"{m.angular_deg:.6g}"
    for h in HOURS:
        row.setdefault(f"marker_{h}_A_measured", "0")

    if ellipse is None or result.acquisition is None:
        row["homography_note"] = "no ellipse; B/C skipped"
        return row

    roll = float(row["acquisition_roll_deg"])
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 1.2)
    edges = cv2.Canny(blur, 55, 145)

    # method B: multi-radius RANSAC homography
    b_solve, b_markers = mrps.measure_all_markers(bgr, edges, ellipse, roll)
    row["B_n_ticks"] = b_solve.n_ticks
    row["B_n_boundary"] = b_solve.n_boundary
    row["B_homography_fitted"] = "1" if b_solve.homography is not None else "0"
    row["B_reproj_error_px"] = f"{b_solve.reproj_error_px:.5f}" if math.isfinite(b_solve.reproj_error_px) else ""
    for h in HOURS:
        m = b_markers.get(h)
        if m is None:
            row[f"marker_{h}_B_measured"] = "0"
        else:
            radial, angular, area, aniso = m
            row[f"marker_{h}_B_measured"] = "1"
            row[f"marker_{h}_B_radial_pct_r"] = f"{radial:.6g}"
            row[f"marker_{h}_B_angular_deg"] = f"{angular:.6g}"

    # method C: pose-library-overlay
    c_solve, c_markers = plm.measure_all_markers(bgr, edges, ellipse, roll)
    row["C_n_ticks"] = c_solve.n_ticks
    row["C_pose_fitted"] = "1" if c_solve.pose is not None else "0"
    if c_solve.pose is not None:
        p = c_solve.pose
        row["C_theta_deg"] = f"{p.theta_deg:.4f}"
        row["C_phi_deg"] = f"{p.phi_deg:.4f}"
        row["C_psi_deg"] = f"{p.psi_deg:.4f}"
        row["C_k"] = f"{p.k:.5f}"
        row["C_score_px"] = f"{p.score_px:.5f}"
        second_best = sorted(c_solve.refined_candidates, key=lambda x: x.score_px)
        row["C_second_best_score_px"] = f"{second_best[1].score_px:.5f}" if len(second_best) > 1 else ""
        amb = plm.ambiguity_report(c_solve)
        for k, v in amb.items():
            row[f"C_amb_{k}"] = f"{v:.5f}" if isinstance(v, float) else v
    for h in HOURS:
        m = c_markers.get(h)
        if m is None:
            row[f"marker_{h}_C_measured"] = "0"
        else:
            radial, angular, area, aniso = m
            row[f"marker_{h}_C_measured"] = "1"
            row[f"marker_{h}_C_radial_pct_r"] = f"{radial:.6g}"
            row[f"marker_{h}_C_angular_deg"] = f"{angular:.6g}"

    return row


def run(images_dir: Path, out_csv: Path) -> None:
    images = sorted(images_dir.glob("*.jpg"))
    print(f"found {len(images)} frames in {images_dir}")
    rows = []
    for img in images:
        print(f"processing {img.name} ...")
        row = process_one(img, img.stem)
        rows.append(row)
        print(f"  outcome={row.get('pipeline_outcome')} tilt={row.get('tilt_deg')} "
              f"ellipse={'yes' if row.get('ellipse_cx') is not None else 'no'} "
              f"B_fitted={row.get('B_homography_fitted')} C_fitted={row.get('C_pose_fitted')}")

    all_keys = []
    seen = set()
    for r in rows:
        for k in r.keys():
            if k not in seen:
                seen.add(k)
                all_keys.append(k)
    out_csv.parent.mkdir(parents=True, exist_ok=True)
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=all_keys, restval="")
        w.writeheader()
        w.writerows(rows)
    print(f"wrote {len(rows)} rows to {out_csv}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images-dir", required=True, type=Path)
    ap.add_argument("--out-csv", required=True, type=Path)
    args = ap.parse_args()
    run(args.images_dir, args.out_csv)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
