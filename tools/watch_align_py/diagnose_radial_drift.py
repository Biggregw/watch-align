#!/usr/bin/env python3
"""Diagnose WHY radial marker localisation moves with camera tilt while angular
localisation stays comparatively stable.

This is a READ-ONLY research tool. It does not change pose acquisition, marker
detection, master geometry or any production threshold. It reuses the already-
accepted ellipse/roll recorded in the committed per_image_measurements.csv
(reconstructed from its columns) and calls the existing, unmodified
marker_qc._measure_projected_marker for detection -- so pose search never runs
again here and the only new work is diagnostic measurement around that already-
accepted geometry.

Per (image, marker-hour) with a detected candidate, records:
  - the raw image-space pixel position of the detected blob and of the
    mathematically expected marker centre (tests whether the image-space
    centroid itself moves under foreshortening -- investigation Q1)
  - the basis-local (pre ellipse-undo) coordinate already computed by
    production code -- the ROI-basis-normalised displacement (Q2)
  - the current-production canonical radial/angular offset (unchanged)
  - a curvature diagnostic: the ROI basis Jacobian evaluated with a finer
    finite-difference step (eps=0.01) compared with production's eps=0.04,
    at the SAME reference point -- large disagreement means the canonical-
    to-image mapping is not well approximated by a single local affine frame
    in that neighbourhood (Q2)
  - marker footprint/shape metrics already computed by the detector: area
    fraction, anisotropy, orientation, marker shape class (Q3)
  - where the expected radial centre for the shape actually sits relative to
    the marker's own visual geometry, restated from existing master
    constants for round/baton/triangle (Q4, descriptive -- the constants
    themselves are not changed here)
  - each marker's canonical angle relative to the ellipse's own foreshortened
    axis (found by scanning where the unit canonical circle maps closest to
    the ellipse centre in image pixels), to test whether error depends on
    direction around the dial and not just tilt magnitude (Q6)

Restricted to calibration-split accepted images by default, per the research
policy of developing candidate fixes without looking at validation results.
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
from geometry import RotatedRect
from image_io import resize_to_max_dim

HOURS = [1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12]
SHAPE_OF_HOUR = {h: ("triangle" if h == 12 else "baton" if h in (6, 9) else "round") for h in HOURS}


def _resolve_image(images_root: Path, local_path: str) -> Optional[Path]:
    p = images_root / local_path
    return p if p.is_file() else None


def _ellipse_from_row(row: dict) -> Optional[RotatedRect]:
    try:
        return RotatedRect(
            float(row["ellipse_cx"]), float(row["ellipse_cy"]),
            float(row["ellipse_w"]), float(row["ellipse_h"]), float(row["ellipse_angle_deg"]),
        )
    except (KeyError, ValueError):
        return None


def _foreshortened_axis_deg(ellipse: RotatedRect, roll: float, n: int = 360) -> float:
    """Canonical angle (degrees) whose unit-circle point maps closest to the
    ellipse centre in image pixels -- i.e. the direction of the ellipse's own
    minor (most foreshortened) axis, expressed in canonical dial-angle terms."""
    best_theta, best_r2 = 0.0, math.inf
    for i in range(n):
        theta = 2.0 * math.pi * i / n
        x, y = marker_qc._map(ellipse, roll, math.cos(theta), math.sin(theta))
        r2 = (x - ellipse.cx) ** 2 + (y - ellipse.cy) ** 2
        if r2 < best_r2:
            best_r2 = r2
            best_theta = theta
    return math.degrees(best_theta)


def _basis_eps(ellipse: RotatedRect, roll: float, radius: float, angle: float, eps: float):
    ca, sa = math.cos(angle), math.sin(angle)
    center = marker_qc._map(ellipse, roll, radius * ca, radius * sa)
    r0 = marker_qc._map(ellipse, roll, (radius - eps) * ca, (radius - eps) * sa)
    r1 = marker_qc._map(ellipse, roll, (radius + eps) * ca, (radius + eps) * sa)
    tx, ty = -sa, ca
    t0 = marker_qc._map(ellipse, roll, radius * ca - eps * tx, radius * sa - eps * ty)
    t1 = marker_qc._map(ellipse, roll, radius * ca + eps * tx, radius * sa + eps * ty)
    rx = (r1[0] - r0[0]) / (2.0 * eps)
    ry = (r1[1] - r0[1]) / (2.0 * eps)
    btx = (t1[0] - t0[0]) / (2.0 * eps)
    bty = (t1[1] - t0[1]) / (2.0 * eps)
    return center, (rx, ry), (btx, bty)


def _angle_diff_mod180(a_deg: float, b_deg: float) -> float:
    d = (a_deg - b_deg) % 180.0
    if d > 90.0:
        d = 180.0 - d
    return d


def run(per_image_csv: Path, images_root: Path, out_csv: Path, split_filter: str = "calibration") -> None:
    with per_image_csv.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    selected = [r for r in rows if r.get("pipeline_outcome") == "accepted"
                and (split_filter in ("", "all") or r.get("split") == split_filter)]
    print(f"selected {len(selected)} accepted images (split={split_filter!r})")

    out_rows = []
    missing = 0
    for i, row in enumerate(selected, start=1):
        image_path = _resolve_image(images_root, row.get("local_path", ""))
        if image_path is None:
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

        roll = float(row["acquisition_roll_deg"])
        dial_radius_px = float(row["dial_radius_px"])
        tilt_deg = float(row["tilt_deg"])
        foreshortened_axis = _foreshortened_axis_deg(ellipse, roll)
        axis_ratio = min(ellipse.w, ellipse.h) / max(1.0, max(ellipse.w, ellipse.h))

        print(f"[{i}/{len(selected)}] {row['source_id']} {row['local_path']}")

        for hour in HOURS:
            c = marker_qc._measure_projected_marker(gray, ellipse, roll, dial_radius_px, hour)
            if c is None:
                continue

            expected_radius = marker_qc.expected_radius_ratio(hour)
            angle = master.angle_for_hour(hour)
            basis_prod = marker_qc._basis_at(ellipse, roll, expected_radius, angle)
            if basis_prod is None:
                continue
            expected_px = basis_prod.center

            corrected = marker_qc._undo_ellipse_distortion(
                ellipse, float(c.center[0] - ellipse.cx), float(c.center[1] - ellipse.cy))
            normalized_radius = math.hypot(corrected[0], corrected[1])
            radial_current = marker_qc.radial_offset_pct_r(normalized_radius, hour)
            corrected_clock = marker_qc._clock_angle_deg(0, 0, corrected[0], corrected[1])
            expected_clock = marker_qc._wrap360(marker_qc._hour_angle_deg(hour) + roll)
            angular_current = marker_qc._wrap180(corrected_clock - expected_clock)

            center_fine, (rx_f, ry_f), (tx_f, ty_f) = _basis_eps(ellipse, roll, expected_radius, angle, eps=0.01)
            center_coarse, (rx_c, ry_c), (tx_c, ty_c) = _basis_eps(ellipse, roll, expected_radius, angle, eps=0.08)
            jac_fine = np.array([[rx_f, tx_f], [ry_f, ty_f]])
            jac_coarse = np.array([[rx_c, tx_c], [ry_c, ty_c]])
            jac_diff_frac = float(np.linalg.norm(jac_fine - jac_coarse) / max(1e-9, np.linalg.norm(jac_fine)))

            px_dx = float(c.center[0] - expected_px[0])
            px_dy = float(c.center[1] - expected_px[1])
            px_dist = math.hypot(px_dx, px_dy)

            hour_angle_deg = math.degrees(angle) % 360.0
            tilt_alignment_deg = _angle_diff_mod180(hour_angle_deg, foreshortened_axis)

            out_rows.append({
                "source_id": row["source_id"],
                "physical_watch_id": row["physical_watch_id"],
                "class_label": row["class_label"],
                "provenance": row.get("provenance", ""),
                "split": row["split"],
                "factory": row.get("factory", ""),
                "local_path": row["local_path"],
                "tilt_deg": f"{tilt_deg:.6g}",
                "axis_ratio": f"{axis_ratio:.6g}",
                "dial_radius_px": f"{dial_radius_px:.6g}",
                "hour": hour,
                "marker_shape": SHAPE_OF_HOUR[hour],
                "foreshortened_axis_deg": f"{foreshortened_axis:.6g}",
                "tilt_alignment_deg": f"{tilt_alignment_deg:.6g}",
                "expected_px_x": f"{expected_px[0]:.6g}",
                "expected_px_y": f"{expected_px[1]:.6g}",
                "detected_px_x": f"{float(c.center[0]):.6g}",
                "detected_px_y": f"{float(c.center[1]):.6g}",
                "px_dx": f"{px_dx:.6g}",
                "px_dy": f"{px_dy:.6g}",
                "px_dist": f"{px_dist:.6g}",
                "px_dist_pct_dial_radius": f"{100.0 * px_dist / max(1.0, dial_radius_px):.6g}",
                "basis_local_radial": f"{c.radial_local:.6g}",
                "basis_local_tangential": f"{c.tangent_local:.6g}",
                "radial_current_pct_r": f"{radial_current:.6g}",
                "angular_current_deg": f"{angular_current:.6g}",
                "area_norm": f"{c.area_norm:.6g}",
                "anisotropy": f"{c.anisotropy:.6g}",
                "rotation_deg": "" if math.isnan(c.rotation_deg) else f"{c.rotation_deg:.6g}",
                "jacobian_curvature_frac": f"{jac_diff_frac:.6g}",
            })

    out_csv.parent.mkdir(parents=True, exist_ok=True)
    if not out_rows:
        raise RuntimeError("no diagnostic rows produced")
    fieldnames = list(out_rows[0].keys())
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        w.writerows(out_rows)
    print(f"wrote {len(out_rows)} marker-observation rows to {out_csv}")
    print(f"missing/undecodable images skipped: {missing}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image-csv", required=True, type=Path)
    ap.add_argument("--images-root", required=True, type=Path)
    ap.add_argument("--out-csv", required=True, type=Path)
    ap.add_argument("--split", default="calibration", help="'calibration', 'validation', or 'all'")
    args = ap.parse_args()
    run(args.per_image_csv, args.images_root, args.out_csv, args.split)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
