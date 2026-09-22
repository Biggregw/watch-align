#!/usr/bin/env python3
"""Visual diagnostics for the same-watch video stress test: for each frame,
render observed ticks, the full master overlay projected through the
solved pose-library pose, and the rectified expected-vs-detected marker
view -- same panel layout as render_pose_library_overlay.py, adapted to
run directly on arbitrary local frames (no pre-existing corpus CSV row).
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path

import cv2
import numpy as np

import marker_qc
import master
import multi_radius_pose_solver as mrps
import pipeline
import pose_library as pl
import pose_library_measure as plm
from image_io import resize_to_max_dim

SIDE = mrps.RECTIFY_SIDE
SCALE = mrps.RECTIFY_SCALE
PANEL_W = 640


def _fit_panel(img: np.ndarray, target_w: int) -> np.ndarray:
    h, w = img.shape[:2]
    scale = target_w / w
    return cv2.resize(img, (target_w, int(round(h * scale))), interpolation=cv2.INTER_AREA)


def render_one(image_path: Path) -> tuple:
    raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    bgr = resize_to_max_dim(raw, 1600)
    result = pipeline.build(bgr)
    if result.reason or result.acquisition is None or result.acquisition.dial_ellipse is None:
        return None, f"acquisition failed: {result.reason}"

    ellipse = result.acquisition.dial_ellipse
    roll = result.acquisition.roll_deg
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 1.2)
    edges = cv2.Canny(blur, 55, 145)

    corr = mrps.extract_correspondences(edges, ellipse, roll)
    ticks = [c for c in corr if c.kind == "tick"]
    solve, markers = plm.measure_all_markers(bgr, edges, ellipse, roll)
    amb = plm.ambiguity_report(solve)

    panel_tl = bgr.copy()
    for c in ticks:
        cv2.circle(panel_tl, (int(round(c.image_x)), int(round(c.image_y))), 4, (0, 200, 255), -1)
    cv2.putText(panel_tl, f"observed ticks n={len(ticks)}  tilt(baseline)={result.tilt_deg:.1f}deg",
                (12, 34), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2, cv2.LINE_AA)

    panel_tr = bgr.copy()
    if solve.pose is not None:
        overlay = pl.project_master_overlay(solve.pose)
        for (x, y) in overlay["tick_image_xy"].astype(int):
            cv2.circle(panel_tr, (x, y), 2, (0, 200, 255), -1)
        bpts = overlay["boundary_image_xy"].astype(int)
        for i in range(len(bpts)):
            j = (i + 1) % len(bpts)
            cv2.line(panel_tr, tuple(bpts[i]), tuple(bpts[j]), (255, 120, 0), 1, cv2.LINE_AA)
        for hour, (x, y) in zip(overlay["marker_hours"], overlay["marker_image_xy"].astype(int)):
            cv2.drawMarker(panel_tr, (x, y), (255, 255, 0), cv2.MARKER_CROSS, 16, 2)
            cv2.putText(panel_tr, str(hour), (x + 6, y - 6), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)
        cv2.putText(panel_tr, "master overlay @ solved pose", (12, 34),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2, cv2.LINE_AA)
    else:
        cv2.putText(panel_tr, "POSE FIT FAILED", (12, 34), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2, cv2.LINE_AA)

    panel_bl = np.zeros((SIDE, SIDE, 3), dtype=np.uint8)
    if solve.pose is not None:
        H = pl.pose_to_homography(solve.pose)
        panel_bl = mrps.rectify(bgr, H)

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

    panel_br = np.zeros((SIDE, SIDE, 3), dtype=np.uint8)
    lines = [f"{image_path.name}  pipeline_outcome={'accepted' if result.accepted else 'rejected'}",
             f"tilt(baseline pipeline)={result.tilt_deg:.2f}deg", ""]
    if solve.pose is not None:
        p = solve.pose
        second = sorted(solve.refined_candidates, key=lambda x: x.score_px)
        second_score = second[1].score_px if len(second) > 1 else float("nan")
        lines += [
            "solved pose:",
            f"  theta(tilt mag)  = {p.theta_deg:6.2f} deg",
            f"  phi(tilt azimuth)= {p.phi_deg:6.2f} deg",
            f"  psi(roll)        = {p.psi_deg:6.2f} deg",
            f"  k(persp.strength)= {p.k:6.5f}",
            f"  best score       = {p.score_px:6.3f} px",
            f"  2nd-best score   = {second_score:6.3f} px",
            "",
            "ambiguity (near-tied candidates):",
            f"  n_near_best = {amb.get('n_near_best','-')} / {amb.get('n_total_candidates','-')}",
            f"  theta range = [{amb.get('theta_lo', float('nan')):.1f}, {amb.get('theta_hi', float('nan')):.1f}]",
            f"  phi   range = [{amb.get('phi_lo', float('nan')):.1f}, {amb.get('phi_hi', float('nan')):.1f}]",
            f"  k     range = [{amb.get('k_lo', float('nan')):.5f}, {amb.get('k_hi', float('nan')):.5f}]",
            f"  max marker disagreement = {amb.get('max_marker_disagreement_px', float('nan')):.2f} px",
        ]
    else:
        lines += ["POSE FIT FAILED"]
    for i, line in enumerate(lines):
        cv2.putText(panel_br, line, (12, 30 + i * 24), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 1, cv2.LINE_AA)

    tl = _fit_panel(panel_tl, SIDE)
    tr = _fit_panel(panel_tr, SIDE)
    top = np.hstack([cv2.resize(tl, (SIDE, SIDE)), cv2.resize(tr, (SIDE, SIDE))])
    bottom = np.hstack([panel_bl, panel_br])
    return np.vstack([top, bottom]), None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images-dir", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    args = ap.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    for img in sorted(args.images_dir.glob("*.jpg")):
        combo, err = render_one(img)
        if combo is None:
            print(f"{img.name}: SKIPPED ({err})")
            continue
        out_path = args.out_dir / f"{img.stem}_diagnostic.jpg"
        cv2.imwrite(str(out_path), combo, [cv2.IMWRITE_JPEG_QUALITY, 92])
        print(f"{img.name}: wrote {out_path.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
