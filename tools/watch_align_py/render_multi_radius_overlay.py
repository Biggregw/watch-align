#!/usr/bin/env python3
"""Visual diagnostics for the multi-radius homography experiment: for a
curated set of calibration images, render the rectified dial with the tick/
boundary correspondences used to fit the homography, and every marker's
expected (cyan) vs detected (yellow) position under both the current
baseline (ellipse-only) and the new (rectify-then-detect) methods.
"""
from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path
from typing import Optional

import cv2
import numpy as np

import master
import marker_qc
import multi_radius_pose_solver as mrps
from geometry import RotatedRect
from image_io import resize_to_max_dim

SIDE = mrps.RECTIFY_SIDE
SCALE = mrps.RECTIFY_SCALE


def _ellipse_from_row(row: dict) -> Optional[RotatedRect]:
    try:
        return RotatedRect(
            float(row["ellipse_cx"]), float(row["ellipse_cy"]),
            float(row["ellipse_w"]), float(row["ellipse_h"]), float(row["ellipse_angle_deg"]),
        )
    except (KeyError, ValueError):
        return None


def _canon_px(x: float, y: float) -> tuple:
    return (int(round(SIDE / 2.0 + x * SCALE)), int(round(SIDE / 2.0 + y * SCALE)))


def render_correspondence_panel(rectified: np.ndarray, solve: mrps.SolveResult) -> np.ndarray:
    out = rectified.copy()
    for c in solve.correspondences:
        color = (0, 200, 255) if c.kind == "tick" else (255, 120, 0)
        cv2.circle(out, _canon_px(c.canonical_x, c.canonical_y), 3, color, -1)
    cv2.putText(out, f"ticks(cyan-orange)={solve.n_ticks} boundary={solve.n_boundary} "
                      f"reproj={solve.reproj_error_px:.2f}px inliers={solve.inlier_fraction:.0%}",
                (8, SIDE - 12), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)
    return out


def render_marker_panel(rectified: np.ndarray, new_markers: dict, baseline_row: dict) -> np.ndarray:
    out = rectified.copy()
    for hour in mrps.MARKER_HOURS:
        radius = marker_qc.expected_radius_ratio(hour)
        angle = master.angle_for_hour(hour)
        ex, ey = _canon_px(radius * math.cos(angle), radius * math.sin(angle))
        cv2.drawMarker(out, (ex, ey), (255, 255, 0), cv2.MARKER_CROSS, 14, 2)

        m = new_markers.get(hour)
        if m is not None:
            radial_pct_r, _ang, _area, _an = m
            true_r = radius + radial_pct_r / 100.0
            dx, dy = _canon_px(true_r * math.cos(angle), true_r * math.sin(angle))
            cv2.drawMarker(out, (dx, dy), (0, 0, 255), cv2.MARKER_TILTED_CROSS, 14, 2)
        cv2.putText(out, str(hour), (ex + 6, ey - 6), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 255, 255), 1, cv2.LINE_AA)
    cv2.putText(out, "yellow=expected  red=NEW detected", (8, SIDE - 12),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)
    return out


def run(per_image_csv: Path, images_root: Path, out_dir: Path, max_images: int) -> None:
    with per_image_csv.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    cal_acc = [r for r in rows if r["pipeline_outcome"] == "accepted" and r["split"] == "calibration"]
    cal_acc.sort(key=lambda r: -float(r.get("tilt_deg", 0) or 0))
    selected = cal_acc[:max_images]

    out_dir.mkdir(parents=True, exist_ok=True)
    for i, row in enumerate(selected, start=1):
        image_path = images_root / row["local_path"]
        if not image_path.is_file():
            continue
        ellipse = _ellipse_from_row(row)
        if ellipse is None:
            continue
        raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
        if raw is None:
            continue
        bgr = resize_to_max_dim(raw, 1600)
        gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
        blur = cv2.GaussianBlur(gray, (5, 5), 1.2)
        edges = cv2.Canny(blur, 55, 145)
        roll = float(row["acquisition_roll_deg"])

        correspondences = mrps.extract_correspondences(edges, ellipse, roll)
        solve = mrps.fit_homography(correspondences)
        if solve.homography is None:
            print(f"[{i}/{len(selected)}] {row['source_id']}: homography fit failed, skipping")
            continue
        rectified = mrps.rectify(bgr, solve.homography)
        rectified_gray = cv2.cvtColor(rectified, cv2.COLOR_BGR2GRAY)
        new_markers = {h: mrps.detect_marker_in_rectified(rectified_gray, SIDE, h) for h in mrps.MARKER_HOURS}

        panel1 = render_correspondence_panel(rectified, solve)
        panel2 = render_marker_panel(rectified, new_markers, row)
        header = np.zeros((50, SIDE * 2, 3), dtype=np.uint8)
        cv2.putText(header, f"{row['source_id']}  tilt={row.get('tilt_deg','')}deg  watch={row['physical_watch_id']}",
                    (8, 32), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 1, cv2.LINE_AA)
        combo = np.vstack([header, np.hstack([panel1, panel2])])
        fname = f"{row['source_id']}__{Path(row['local_path']).stem}.jpg"
        cv2.imwrite(str(out_dir / fname), combo, [cv2.IMWRITE_JPEG_QUALITY, 90])
        print(f"[{i}/{len(selected)}] wrote {fname}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image-csv", required=True, type=Path)
    ap.add_argument("--images-root", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    ap.add_argument("--max-images", type=int, default=12)
    args = ap.parse_args()
    run(args.per_image_csv, args.images_root, args.out_dir, args.max_images)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
