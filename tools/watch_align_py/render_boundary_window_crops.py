#!/usr/bin/env python3
"""Visual diagnostic: for a curated set of low-tilt calibration images, crop
the actual boundary search window (canonical radius 0.94-1.08, in image
space, at several angles around the circumference) and show what edge
pixels the extractor actually finds inside it, so a human can identify what
physical feature (dial rim / rehaut / crystal-bezel edge / reflection) it
is locking onto. Read-only diagnostic; no production code touched.
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
import multi_radius_pose_solver as mrps
from geometry import RotatedRect
from image_io import resize_to_max_dim

CROP_HALF = 70  # px, around the window centre in image space
ANGLES_N = 12  # every 30 degrees


def _ellipse_from_row(row: dict) -> Optional[RotatedRect]:
    try:
        return RotatedRect(
            float(row["ellipse_cx"]), float(row["ellipse_cy"]),
            float(row["ellipse_w"]), float(row["ellipse_h"]), float(row["ellipse_angle_deg"]),
        )
    except (KeyError, ValueError):
        return None


def render_one(bgr: np.ndarray, edges: np.ndarray, ellipse: RotatedRect, roll: float,
               source_id: str, tilt: float) -> np.ndarray:
    tiles = []
    for i in range(ANGLES_N):
        angle = 2.0 * math.pi * i / ANGLES_N
        loc = mrps._localize_centroid(
            edges, ellipse, roll,
            (mrps.BOUNDARY_SEARCH_LO + mrps.BOUNDARY_SEARCH_HI) / 2.0, angle,
            mrps.BOUNDARY_RADIAL_HALF, mrps.BOUNDARY_TANGENTIAL_HALF_DEG, mrps.BOUNDARY_GRID_N,
        )
        cx, cy = marker_qc._map(ellipse, roll,
                                 (mrps.BOUNDARY_SEARCH_LO + mrps.BOUNDARY_SEARCH_HI) / 2.0 * math.cos(angle),
                                 (mrps.BOUNDARY_SEARCH_LO + mrps.BOUNDARY_SEARCH_HI) / 2.0 * math.sin(angle))
        xi, yi = int(round(cx)), int(round(cy))
        x0, x1 = xi - CROP_HALF, xi + CROP_HALF
        y0, y1 = yi - CROP_HALF, yi + CROP_HALF
        h, w = bgr.shape[:2]
        pad_l, pad_t = max(0, -x0), max(0, -y0)
        cx0, cx1 = max(0, x0), min(w, x1)
        cy0, cy1 = max(0, y0), min(h, y1)
        tile = np.full((2 * CROP_HALF, 2 * CROP_HALF, 3), (40, 40, 40), dtype=np.uint8)
        if cx1 > cx0 and cy1 > cy0:
            src_crop = bgr[cy0:cy1, cx0:cx1]
            tile[pad_t:pad_t + src_crop.shape[0], pad_l:pad_l + src_crop.shape[1]] = src_crop
        # mark the window centre (crosshair) and, if localisation succeeded,
        # the detected centroid (red dot) plus a radial line showing the
        # search band's inner/outer bounds along this angle.
        c0 = (CROP_HALF, CROP_HALF)
        cv2.drawMarker(tile, c0, (0, 255, 255), cv2.MARKER_CROSS, 10, 1)
        for r in (mrps.BOUNDARY_SEARCH_LO, mrps.BOUNDARY_SEARCH_HI):
            ex, ey = marker_qc._map(ellipse, roll, r * math.cos(angle), r * math.sin(angle))
            lx, ly = int(round(ex - xi + CROP_HALF)), int(round(ey - yi + CROP_HALF))
            cv2.drawMarker(tile, (lx, ly), (255, 120, 0), cv2.MARKER_TILTED_CROSS, 8, 1)
        label = f"n=0"
        if loc is not None:
            img_x, img_y, n_px = loc
            dx, dy = int(round(img_x - xi + CROP_HALF)), int(round(img_y - yi + CROP_HALF))
            cv2.circle(tile, (dx, dy), 4, (0, 0, 255), -1)
            label = f"n={n_px}"
        cv2.putText(tile, f"{int(round(math.degrees(angle)))}deg {label}", (4, 2 * CROP_HALF - 6),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.35, (255, 255, 255), 1, cv2.LINE_AA)
        tiles.append(tile)

    cols = 4
    rows_n = math.ceil(ANGLES_N / cols)
    grid_rows = []
    for r in range(rows_n):
        row_tiles = tiles[r * cols:(r + 1) * cols]
        while len(row_tiles) < cols:
            row_tiles.append(np.zeros_like(tiles[0]))
        grid_rows.append(np.hstack(row_tiles))
    grid = np.vstack(grid_rows)
    header = np.zeros((36, grid.shape[1], 3), dtype=np.uint8)
    cv2.putText(header, f"{source_id}  tilt={tilt:.1f}deg  "
                         f"yellow=window-centre(0.94-1.08 midline)  orange=search-band inner/outer  red=detected centroid",
                (6, 24), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)
    return np.vstack([header, grid])


def run(per_image_csv: Path, images_root: Path, out_dir: Path, max_images: int) -> None:
    with per_image_csv.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    cal_acc = [r for r in rows if r["pipeline_outcome"] == "accepted" and r["split"] == "calibration"]
    cal_acc.sort(key=lambda r: float(r.get("tilt_deg", 0) or 0))
    # curated spread: favor low tilt (where a mismatch would be most
    # surprising / least explained by extrapolation error) but include a
    # few higher-tilt images too.
    lo = cal_acc[:max(1, max_images - max_images // 3)]
    hi = cal_acc[-(max_images // 3):] if max_images >= 3 else []
    selected = lo + [r for r in hi if r not in lo]
    selected = selected[:max_images]

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
        tilt = float(row.get("tilt_deg", 0) or 0)

        combo = render_one(bgr, edges, ellipse, roll, row["source_id"], tilt)
        fname = f"{i:02d}_{row['source_id']}__{Path(row['local_path']).stem}_tilt{tilt:.0f}.jpg"
        cv2.imwrite(str(out_dir / fname), combo, [cv2.IMWRITE_JPEG_QUALITY, 92])
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
