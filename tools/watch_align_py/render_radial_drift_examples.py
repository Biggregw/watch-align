#!/usr/bin/env python3
"""Visual diagnostics for the radial-drift root-cause investigation.

For calibration watches with both a low-tilt and a higher-tilt accepted photo,
renders a side-by-side crop of each marker's ROI showing:
  - the expected marker centre (cyan crosshair) used by production code,
  - the detected blob outline and its centroid (yellow),
  - the marker's basis-local and canonical radial/angular readout.

This is a READ-ONLY diagnostic. It does not change detection, pose or any
threshold -- it only visualises what marker_qc._measure_projected_marker
already computed for the already-accepted pose.
"""
from __future__ import annotations

import argparse
import csv
import math
from collections import defaultdict
from pathlib import Path
from typing import Optional

import cv2
import numpy as np

import marker_qc
import master
from geometry import RotatedRect
from image_io import resize_to_max_dim

HOURS = [1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12]
CROP = 260


def _ellipse_from_row(row: dict) -> Optional[RotatedRect]:
    try:
        return RotatedRect(
            float(row["ellipse_cx"]), float(row["ellipse_cy"]),
            float(row["ellipse_w"]), float(row["ellipse_h"]), float(row["ellipse_angle_deg"]),
        )
    except (KeyError, ValueError):
        return None


def render_marker_crop(gray_bgr: np.ndarray, ellipse: RotatedRect, roll: float,
                        dial_radius_px: float, hour: int) -> Optional[np.ndarray]:
    radius = marker_qc.expected_radius_ratio(hour)
    angle = master.angle_for_hour(hour)
    basis = marker_qc._basis_at(ellipse, roll, radius, angle)
    if basis is None:
        return None
    cand = marker_qc._measure_projected_marker(
        cv2.cvtColor(gray_bgr, cv2.COLOR_BGR2GRAY), ellipse, roll, dial_radius_px, hour)

    cx, cy = basis.center
    x0, y0 = int(cx - CROP / 2), int(cy - CROP / 2)
    x1, y1 = x0 + CROP, y0 + CROP
    h, w = gray_bgr.shape[:2]
    pad_l, pad_t = max(0, -x0), max(0, -y0)
    pad_r, pad_b = max(0, x1 - w), max(0, y1 - h)
    x0c, y0c, x1c, y1c = max(0, x0), max(0, y0), min(w, x1), min(h, y1)
    crop = gray_bgr[y0c:y1c, x0c:x1c]
    crop = cv2.copyMakeBorder(crop, pad_t, pad_b, pad_l, pad_r, cv2.BORDER_CONSTANT, value=(20, 20, 20))
    crop = cv2.resize(crop, (CROP * 2, CROP * 2), interpolation=cv2.INTER_NEAREST)
    scale = 2.0

    def to_crop_px(px, py):
        return (int((px - x0) * scale), int((py - y0) * scale))

    ex, ey = to_crop_px(cx, cy)
    cv2.drawMarker(crop, (ex, ey), (255, 255, 0), cv2.MARKER_CROSS, 18, 2)

    label = f"h{hour}"
    if cand is not None:
        dxp, dyp = to_crop_px(cand.center[0], cand.center[1])
        cv2.drawMarker(crop, (dxp, dyp), (0, 255, 255), cv2.MARKER_TILTED_CROSS, 18, 2)
        cv2.line(crop, (ex, ey), (dxp, dyp), (0, 165, 255), 1)
        label += f" loc=({cand.radial_local:+.3f},{cand.tangent_local:+.3f})"
    else:
        label += " not-detected"
    cv2.putText(crop, label, (6, CROP * 2 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)
    cv2.putText(crop, f"hour {hour}", (6, 20), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 1, cv2.LINE_AA)
    return crop


def build_pair_panel(low_row: dict, low_img: np.ndarray, high_row: dict, high_img: np.ndarray,
                      hours: list) -> np.ndarray:
    low_ellipse = _ellipse_from_row(low_row)
    high_ellipse = _ellipse_from_row(high_row)
    low_roll = float(low_row["acquisition_roll_deg"])
    high_roll = float(high_row["acquisition_roll_deg"])
    low_dr = float(low_row["dial_radius_px"])
    high_dr = float(high_row["dial_radius_px"])

    rows = []
    for hour in hours:
        low_crop = render_marker_crop(low_img, low_ellipse, low_roll, low_dr, hour)
        high_crop = render_marker_crop(high_img, high_ellipse, high_roll, high_dr, hour)
        if low_crop is None or high_crop is None:
            continue
        rows.append(np.hstack([low_crop, high_crop]))
    if not rows:
        return None
    header_h = 60
    canvas_w = rows[0].shape[1]
    header = np.zeros((header_h, canvas_w, 3), dtype=np.uint8)
    cv2.putText(header, f"LOW TILT {low_row['tilt_deg']} deg ({low_row['source_id']})", (10, 24),
                cv2.FONT_HERSHEY_SIMPLEX, 0.6, (200, 255, 200), 1, cv2.LINE_AA)
    cv2.putText(header, f"HIGH TILT {high_row['tilt_deg']} deg ({high_row['source_id']})",
                (canvas_w // 2 + 10, 24), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (200, 200, 255), 1, cv2.LINE_AA)
    cv2.putText(header, f"watch={low_row['physical_watch_id']}", (10, 48),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)
    return np.vstack([header] + rows)


def run(per_image_csv: Path, images_root: Path, out_dir: Path, min_spread: float, max_pairs_per_watch: int) -> None:
    with per_image_csv.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    cal_acc = [r for r in rows if r["pipeline_outcome"] == "accepted" and r["split"] == "calibration"]

    by_watch = defaultdict(list)
    for r in cal_acc:
        by_watch[r["physical_watch_id"]].append(r)

    out_dir.mkdir(parents=True, exist_ok=True)
    written = 0
    for watch_id, watch_rows in sorted(by_watch.items()):
        watch_rows.sort(key=lambda r: float(r["tilt_deg"]))
        lo, hi = watch_rows[0], watch_rows[-1]
        spread = float(hi["tilt_deg"]) - float(lo["tilt_deg"])
        if spread < min_spread:
            continue
        lo_path = images_root / lo["local_path"]
        hi_path = images_root / hi["local_path"]
        if not lo_path.is_file() or not hi_path.is_file():
            continue
        lo_img = resize_to_max_dim(cv2.imread(str(lo_path), cv2.IMREAD_COLOR), 1600)
        hi_img = resize_to_max_dim(cv2.imread(str(hi_path), cv2.IMREAD_COLOR), 1600)
        panel = build_pair_panel(lo, lo_img, hi, hi_img, HOURS)
        if panel is None:
            continue
        fname = f"{watch_id}__spread{spread:.0f}deg.jpg"
        cv2.imwrite(str(out_dir / fname), panel, [cv2.IMWRITE_JPEG_QUALITY, 92])
        print(f"wrote {fname} (spread={spread:.1f} deg, {lo['tilt_deg']} -> {hi['tilt_deg']})")
        written += 1

    print(f"wrote {written} tilt-pair panels to {out_dir}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image-csv", required=True, type=Path)
    ap.add_argument("--images-root", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    ap.add_argument("--min-spread", type=float, default=5.0)
    ap.add_argument("--max-pairs-per-watch", type=int, default=1)
    args = ap.parse_args()
    run(args.per_image_csv, args.images_root, args.out_dir, args.min_spread, args.max_pairs_per_watch)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
