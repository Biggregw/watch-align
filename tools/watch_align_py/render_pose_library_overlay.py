#!/usr/bin/env python3
"""Visual diagnostics for the pose-library-overlay experiment (method C).
For a curated set of images (near-frontal / medium-angle / highest-angle /
flagged-catastrophic), renders:
  - top-left: original image with the OBSERVED tick correspondences used to
    fit the pose (never the marker under test)
  - top-right: original image with the full perspective-aware master
    overlay projected through the solved pose (minute track, dial edge,
    every hour marker's expected position) -- "projected ideal features"
    and "final master overlay" together, since both are the same
    projection at different feature sets
  - bottom-left: rectified canonical view (via the solved pose's
    homography) with marker expected (yellow cross) vs detected (red
    tilted-cross) positions -- mirrors render_multi_radius_overlay's
    marker panel for direct visual comparability with method B
  - bottom-right: pose parameters + ambiguity summary as text
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
import pose_library as pl
import pose_library_measure as plm
from geometry import RotatedRect
from image_io import resize_to_max_dim

SIDE = mrps.RECTIFY_SIDE
SCALE = mrps.RECTIFY_SCALE
PANEL_W = 640


def _ellipse_from_row(row: dict) -> Optional[RotatedRect]:
    try:
        return RotatedRect(
            float(row["ellipse_cx"]), float(row["ellipse_cy"]),
            float(row["ellipse_w"]), float(row["ellipse_h"]), float(row["ellipse_angle_deg"]),
        )
    except (KeyError, ValueError):
        return None


def _fit_panel(img: np.ndarray, target_w: int = PANEL_W) -> np.ndarray:
    h, w = img.shape[:2]
    scale = target_w / w
    return cv2.resize(img, (target_w, int(round(h * scale))), interpolation=cv2.INTER_AREA)


def render_one(bgr: np.ndarray, edges: np.ndarray, ellipse: RotatedRect, roll: float,
               source_id: str, tilt: float, row: dict) -> np.ndarray:
    corr = mrps.extract_correspondences(edges, ellipse, roll)
    ticks = [c for c in corr if c.kind == "tick"]

    solve, markers = plm.measure_all_markers(bgr, edges, ellipse, roll)
    amb = plm.ambiguity_report(solve)

    # top-left: observed ticks on original image
    panel_tl = bgr.copy()
    for c in ticks:
        cv2.circle(panel_tl, (int(round(c.image_x)), int(round(c.image_y))), 4, (0, 200, 255), -1)
    cv2.putText(panel_tl, f"observed ticks n={len(ticks)}", (12, 34),
                cv2.FONT_HERSHEY_SIMPLEX, 0.9, (255, 255, 255), 2, cv2.LINE_AA)

    # top-right: full master overlay projected through the solved pose
    panel_tr = bgr.copy()
    if solve.pose is not None:
        overlay = pl.project_master_overlay(solve.pose)
        pts = overlay["tick_image_xy"].astype(int)
        for (x, y) in pts:
            cv2.circle(panel_tr, (x, y), 2, (0, 200, 255), -1)
        bpts = overlay["boundary_image_xy"].astype(int)
        for i in range(len(bpts)):
            j = (i + 1) % len(bpts)
            cv2.line(panel_tr, tuple(bpts[i]), tuple(bpts[j]), (255, 120, 0), 1, cv2.LINE_AA)
        for hour, (x, y) in zip(overlay["marker_hours"], overlay["marker_image_xy"].astype(int)):
            cv2.drawMarker(panel_tr, (x, y), (255, 255, 0), cv2.MARKER_CROSS, 16, 2)
            cv2.putText(panel_tr, str(hour), (x + 6, y - 6), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)
        cv2.putText(panel_tr, "master overlay @ solved pose", (12, 34),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.9, (255, 255, 255), 2, cv2.LINE_AA)
    else:
        cv2.putText(panel_tr, "POSE FIT FAILED", (12, 34),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 0, 255), 2, cv2.LINE_AA)

    # bottom-left: rectified view, marker expected (yellow) vs detected (red)
    panel_bl = np.zeros((SIDE, SIDE, 3), dtype=np.uint8)
    if solve.pose is not None:
        H = pl.pose_to_homography(solve.pose)
        rectified = mrps.rectify(bgr, H)
        panel_bl = rectified.copy()

        def canon_px(x, y):
            return (int(round(SIDE / 2.0 + x * SCALE)), int(round(SIDE / 2.0 + y * SCALE)))

        for hour in mrps.MARKER_HOURS:
            radius = marker_qc.expected_radius_ratio(hour)
            angle = master.angle_for_hour(hour)
            ex, ey = canon_px(radius * math.cos(angle), radius * math.sin(angle))
            cv2.drawMarker(panel_bl, (ex, ey), (255, 255, 0), cv2.MARKER_CROSS, 14, 2)
            m = markers.get(hour)
            if m is not None:
                radial_pct_r, _ang, _area, _an = m
                true_r = radius + radial_pct_r / 100.0
                dx, dy = canon_px(true_r * math.cos(angle), true_r * math.sin(angle))
                cv2.drawMarker(panel_bl, (dx, dy), (0, 0, 255), cv2.MARKER_TILTED_CROSS, 14, 2)
            cv2.putText(panel_bl, str(hour), (ex + 6, ey - 6), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 255, 255), 1, cv2.LINE_AA)
        cv2.putText(panel_bl, "rectified: yellow=expected red=detected", (8, SIDE - 12),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)

    # bottom-right: text info panel
    panel_br = np.zeros((SIDE, SIDE, 3), dtype=np.uint8)
    lines = [f"{source_id}  tilt(baseline pipeline)={tilt:.1f}deg", ""]
    if solve.pose is not None:
        p = solve.pose
        lines += [
            f"solved pose:",
            f"  theta(tilt mag)  = {p.theta_deg:6.2f} deg",
            f"  phi(tilt azimuth)= {p.phi_deg:6.2f} deg",
            f"  psi(roll)        = {p.psi_deg:6.2f} deg",
            f"  k(persp.strength)= {p.k:6.3f}",
            f"  tick fit score   = {p.score_px:6.3f} px",
            "",
            f"ambiguity (near-tied candidates):",
            f"  n_near_best = {amb.get('n_near_best','-')} / {amb.get('n_total_candidates','-')}",
            f"  margin_px   = {amb.get('margin_px', float('nan')):.3f}",
            f"  theta range = [{amb.get('theta_lo', float('nan')):.1f}, {amb.get('theta_hi', float('nan')):.1f}]",
            f"  phi   range = [{amb.get('phi_lo', float('nan')):.1f}, {amb.get('phi_hi', float('nan')):.1f}]",
            f"  k     range = [{amb.get('k_lo', float('nan')):.3f}, {amb.get('k_hi', float('nan')):.3f}]",
            f"  max marker disagreement = {amb.get('max_marker_disagreement_px', float('nan')):.2f} px",
        ]
    else:
        lines += ["POSE FIT FAILED (fewer than MIN_TICKS usable ticks)"]
    for i, line in enumerate(lines):
        cv2.putText(panel_br, line, (12, 30 + i * 24), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 1, cv2.LINE_AA)

    tl = _fit_panel(panel_tl, SIDE)
    tr = _fit_panel(panel_tr, SIDE)
    top = np.hstack([tl, tr]) if tl.shape[0] == tr.shape[0] else np.hstack(
        [cv2.resize(tl, (SIDE, SIDE)), cv2.resize(tr, (SIDE, SIDE))])
    bottom = np.hstack([panel_bl, panel_br])
    if top.shape[1] != bottom.shape[1]:
        top = cv2.resize(top, (bottom.shape[1], int(top.shape[0] * bottom.shape[1] / top.shape[1])))
    return np.vstack([top, bottom])


def run(per_image_csv: Path, images_root: Path, out_dir: Path, max_images: int) -> None:
    with per_image_csv.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    cal_acc = [r for r in rows if r["pipeline_outcome"] == "accepted" and r["split"] == "calibration"]
    cal_acc.sort(key=lambda r: float(r.get("tilt_deg", 0) or 0))
    # curated spread: near-frontal, medium, highest-angle
    n = len(cal_acc)
    lo = cal_acc[: max(1, max_images // 3)]
    mid_start = n // 2 - max_images // 6
    mid = cal_acc[max(0, mid_start): max(0, mid_start) + max(1, max_images // 3)]
    hi = cal_acc[-max(1, max_images - len(lo) - len(mid)):]
    seen = set()
    selected = []
    for r in lo + mid + hi:
        key = r["source_id"] + r["local_path"]
        if key not in seen:
            seen.add(key)
            selected.append(r)
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

        combo = render_one(bgr, edges, ellipse, roll, row["source_id"], tilt, row)
        fname = f"{i:02d}_{row['source_id']}__{Path(row['local_path']).stem}_tilt{tilt:.0f}.jpg"
        cv2.imwrite(str(out_dir / fname), combo, [cv2.IMWRITE_JPEG_QUALITY, 90])
        print(f"[{i}/{len(selected)}] wrote {fname}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image-csv", required=True, type=Path)
    ap.add_argument("--images-root", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    ap.add_argument("--max-images", type=int, default=15)
    args = ap.parse_args()
    run(args.per_image_csv, args.images_root, args.out_dir, args.max_images)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
