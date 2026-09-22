#!/usr/bin/env python3
"""Diagnose why the multi-radius homography's RANSAC inlier fraction is only
~50% even on low-tilt images. Hypothesis under test: the "boundary"
correspondences (searched over canonical radius 0.94-1.08, a band 3.5x wider
than the tick window) are not reliable observations of one coplanar circle
at radius 1.000 -- they may be locking onto different physical structures
around the circumference (dial rim vs rehaut vs crystal/bezel edge), and are
always labelled as exactly radius 1.000 regardless of where in that wide
band the edge centroid actually fell.

This script does NOT touch the production RANSAC threshold or the shipped
solver. It fits two homographies per image:
  - H_ticks: ticks-only (RANSAC over the 0.891-radius tick correspondences
    alone -- the known-degenerate single-radius case; used both to test its
    own stability and as an independent "what does the flat dial plane
    predict" reference for the boundary points).
  - H_full: ticks + boundary combined, exactly as the shipped experiment
    does (RANSAC, threshold 1.5px) -- used to report per-kind inlier
    fraction/residual (request #1/#2).

For every boundary correspondence it also reports its residual against
H_ticks and the canonical radius that H_ticks^-1 implies for its detected
image point (an approximate diagnostic -- H_ticks is only exactly
determined at radius 0.891 and extrapolates outside that, but a tight
cluster at a consistent radius vs formless scatter is still informative
about whether these points sit on one coplanar circle).

Calibration-split only. No thresholds/master-geometry/production code
changed.
"""
from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path
from typing import Optional

import cv2
import numpy as np

import multi_radius_pose_solver as mrps
from geometry import RotatedRect
from image_io import resize_to_max_dim


def _ellipse_from_row(row: dict) -> Optional[RotatedRect]:
    try:
        return RotatedRect(
            float(row["ellipse_cx"]), float(row["ellipse_cy"]),
            float(row["ellipse_w"]), float(row["ellipse_h"]), float(row["ellipse_angle_deg"]),
        )
    except (KeyError, ValueError):
        return None


def _fit(src: np.ndarray, dst: np.ndarray, use_ransac: bool):
    if len(src) < 4:
        return None, None
    if use_ransac:
        H, mask = cv2.findHomography(src, dst, cv2.RANSAC, ransacReprojThreshold=1.5)
    else:
        H, mask = cv2.findHomography(src, dst, 0)
        mask = np.ones((len(src), 1), dtype=np.uint8) if H is not None else None
    return H, mask


def _apply(H: np.ndarray, x: float, y: float) -> tuple:
    v = H @ np.array([x, y, 1.0])
    return v[0] / v[2], v[1] / v[2]


def _apply_inv(H: np.ndarray, x: float, y: float) -> Optional[tuple]:
    Hinv = np.linalg.inv(H)
    return _apply(Hinv, x, y)


def run(per_image_csv: Path, images_root: Path, out_summary_csv: Path,
        out_detail_csv: Path, max_images: int) -> None:
    with per_image_csv.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    selected = [r for r in rows if r.get("pipeline_outcome") == "accepted" and r.get("split") == "calibration"]
    selected.sort(key=lambda r: float(r.get("tilt_deg", 0) or 0))
    if max_images:
        selected = selected[:max_images]
    print(f"selected {len(selected)} calibration accepted images (tilt-ascending)")

    detail_rows = []
    summary_rows = []
    tick_inlier_fracs, boundary_inlier_fracs = [], []
    tick_resids_full, boundary_resids_full = [], []
    ticksonly_inlier_fracs, ticksonly_resids = [], []
    boundary_resid_vs_ticksonly = []
    boundary_implied_radius = []

    for i, row in enumerate(selected, start=1):
        image_path = images_root / row.get("local_path", "")
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

        corr = mrps.extract_correspondences(edges, ellipse, roll)
        ticks = [c for c in corr if c.kind == "tick"]
        boundary = [c for c in corr if c.kind == "boundary"]
        if len(ticks) < 8:
            print(f"[{i}/{len(selected)}] {row['source_id']}: too few ticks ({len(ticks)}), skipping")
            continue

        # H_ticks: ticks-only, RANSAC (tests request #6 -- stability of the
        # known-degenerate single-radius fit).
        tsrc = np.array([[c.canonical_x, c.canonical_y] for c in ticks])
        tdst = np.array([[c.image_x, c.image_y] for c in ticks])
        H_ticks, mask_ticks = _fit(tsrc, tdst, use_ransac=True)

        # H_full: ticks + boundary, RANSAC, threshold 1.5px -- identical to
        # the shipped experiment's fit_homography, but computed here so we
        # can split inlier/residual stats by correspondence kind.
        allsrc = np.array([[c.canonical_x, c.canonical_y] for c in corr])
        alldst = np.array([[c.image_x, c.image_y] for c in corr])
        H_full, mask_full = _fit(allsrc, alldst, use_ransac=True)

        if H_ticks is not None:
            tinl = mask_ticks.ravel().astype(bool)
            ticksonly_inlier_fracs.append(float(tinl.sum()) / len(ticks))
            for c, is_in in zip(ticks, tinl):
                if not is_in:
                    continue
                px, py = _apply(H_ticks, c.canonical_x, c.canonical_y)
                ticksonly_resids.append(math.hypot(px - c.image_x, py - c.image_y))

        if H_full is not None:
            finl = mask_full.ravel().astype(bool)
            kind_arr = [c.kind for c in corr]
            n_t = sum(1 for k in kind_arr if k == "tick")
            n_b = sum(1 for k in kind_arr if k == "boundary")
            t_in = sum(1 for k, ii in zip(kind_arr, finl) if k == "tick" and ii)
            b_in = sum(1 for k, ii in zip(kind_arr, finl) if k == "boundary" and ii)
            if n_t:
                tick_inlier_fracs.append(t_in / n_t)
            if n_b:
                boundary_inlier_fracs.append(b_in / n_b)
            for c, is_in in zip(corr, finl):
                if not is_in:
                    continue
                px, py = _apply(H_full, c.canonical_x, c.canonical_y)
                resid = math.hypot(px - c.image_x, py - c.image_y)
                if c.kind == "tick":
                    tick_resids_full.append(resid)
                else:
                    boundary_resids_full.append(resid)

            summary_rows.append({
                "source_id": row["source_id"], "physical_watch_id": row["physical_watch_id"],
                "tilt_deg": f"{tilt:.3f}", "n_ticks": n_t, "n_boundary": n_b,
                "tick_inlier_frac": f"{(t_in / n_t if n_t else float('nan')):.4f}",
                "boundary_inlier_frac": f"{(b_in / n_b if n_b else float('nan')):.4f}",
                "ticksonly_inlier_frac": f"{(ticksonly_inlier_fracs[-1] if H_ticks is not None else float('nan')):.4f}",
            })

        # Per-boundary-point detail: residual vs H_full, plus residual vs
        # H_ticks and H_ticks-implied canonical radius (approximate --
        # H_ticks is exact only at radius 0.891 and extrapolates outside
        # it, but consistent clustering vs formless scatter is still
        # informative about whether these points share one coplanar circle).
        if H_ticks is not None:
            for c in boundary:
                px, py = _apply(H_ticks, c.canonical_x, c.canonical_y)
                resid_vs_ticksonly = math.hypot(px - c.image_x, py - c.image_y)
                boundary_resid_vs_ticksonly.append(resid_vs_ticksonly)
                back = _apply_inv(H_ticks, c.image_x, c.image_y)
                implied_r = math.hypot(*back) if back is not None else float("nan")
                boundary_implied_radius.append(implied_r)
                angle_deg = math.degrees(math.atan2(c.canonical_y, c.canonical_x))
                detail_rows.append({
                    "source_id": row["source_id"], "physical_watch_id": row["physical_watch_id"],
                    "tilt_deg": f"{tilt:.3f}", "angle_deg": f"{angle_deg:.2f}",
                    "n_edge_pixels": c.n_edge_pixels,
                    "resid_vs_ticksonly_H_px": f"{resid_vs_ticksonly:.3f}",
                    "implied_canonical_radius_via_ticksonly_H": f"{implied_r:.4f}",
                })
        print(f"[{i}/{len(selected)}] {row['source_id']} tilt={tilt:.1f} "
              f"ticks={len(ticks)} boundary={len(boundary)} "
              f"tick_inlier={tick_inlier_fracs[-1] if tick_inlier_fracs else float('nan'):.2f} "
              f"boundary_inlier={boundary_inlier_fracs[-1] if boundary_inlier_fracs else float('nan'):.2f}")

    def _stats(name, vals):
        if not vals:
            print(f"{name}: no data")
            return
        arr = np.array(vals)
        print(f"{name}: n={len(arr)} mean={arr.mean():.4f} median={np.median(arr):.4f} "
              f"std={arr.std():.4f} min={arr.min():.4f} max={arr.max():.4f}")

    print("\n--- Aggregate (per-kind, H_full = ticks+boundary RANSAC @1.5px, matches shipped experiment) ---")
    _stats("tick inlier_fraction (per image)", tick_inlier_fracs)
    _stats("boundary inlier_fraction (per image)", boundary_inlier_fracs)
    _stats("tick residual_px (inliers only)", tick_resids_full)
    _stats("boundary residual_px (inliers only)", boundary_resids_full)

    print("\n--- Ticks-only homography (H_ticks, single-radius degenerate case, request #6) ---")
    _stats("ticksonly inlier_fraction (per image)", ticksonly_inlier_fracs)
    _stats("ticksonly residual_px (inliers only)", ticksonly_resids)

    print("\n--- Boundary points vs H_ticks (independent flat-dial-plane reference) ---")
    _stats("boundary residual_px vs H_ticks (ALL boundary pts, not just RANSAC inliers)", boundary_resid_vs_ticksonly)
    _stats("boundary implied canonical radius via H_ticks^-1 (nominal label is exactly 1.000)", boundary_implied_radius)

    out_summary_csv.parent.mkdir(parents=True, exist_ok=True)
    with out_summary_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(summary_rows[0].keys()) if summary_rows else [])
        if summary_rows:
            w.writeheader()
            w.writerows(summary_rows)
    with out_detail_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(detail_rows[0].keys()) if detail_rows else [])
        if detail_rows:
            w.writeheader()
            w.writerows(detail_rows)
    print(f"\nwrote {len(summary_rows)} rows to {out_summary_csv}")
    print(f"wrote {len(detail_rows)} rows to {out_detail_csv}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image-csv", required=True, type=Path)
    ap.add_argument("--images-root", required=True, type=Path)
    ap.add_argument("--out-summary-csv", required=True, type=Path)
    ap.add_argument("--out-detail-csv", required=True, type=Path)
    ap.add_argument("--max-images", type=int, default=0)
    args = ap.parse_args()
    run(args.per_image_csv, args.images_root, args.out_summary_csv, args.out_detail_csv, args.max_images)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
