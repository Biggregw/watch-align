#!/usr/bin/env python3
"""Render the v2 generic geometry reticle on one photo
(experiment/generic-geometry-reticle-v2).

Deliberately generic and applied verbatim to both test photos (the
official Rolex reference and the user's own photo) -- same code path,
same constants, no per-image tuning. Produces exactly the 5 outputs
requested: raw image, full-resolution user-mode overlay, full-resolution
diagnostic-mode overlay, dial-crop user-mode, dial-crop diagnostic-mode,
plus a separate diagnostic text panel (never drawn on top of the dial).

This module does not interpret marker 12, does not compute a pass/fail,
and does not add any watch-specific defect logic -- see reticle_v2.py's
module docstring.
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path

import cv2

import dial_crop
import pipeline
import reticle_v2 as rv2
import reticle_v2_render as rr2
from image_io import resize_to_max_dim


def run(image_path: Path, out_dir: Path, label: str) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    if raw is None:
        raise RuntimeError(f"could not decode {image_path}")
    bgr = resize_to_max_dim(raw, 1600)
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)

    result = pipeline.build(bgr)
    if result.acquisition is None or result.acquisition.dial_ellipse is None:
        raise RuntimeError(f"pipeline.build() could not acquire a pose for {image_path}")
    ellipse = result.acquisition.dial_ellipse
    roll = result.solved_roll
    dial_radius_px = result.dial_radius_px

    geo = rv2.build_geometry(gray, ellipse, roll, dial_radius_px)

    full_raw = bgr
    full_user = rr2.user_mode(bgr, geo)
    full_diag = rr2.diagnostic_mode(bgr, geo)
    crop_raw = dial_crop.dial_crop(bgr, ellipse, dial_radius_px)
    crop_user = dial_crop.dial_crop(full_user, ellipse, dial_radius_px)
    crop_diag = dial_crop.dial_crop(full_diag, ellipse, dial_radius_px)

    pose_lines = [
        f"pipeline: accepted={result.accepted} automatic_accepted={result.automatic_accepted} "
        f"confidence={result.confidence:.4f}",
        f"pose: tilt_deg={result.tilt_deg:.2f} dial_radius_px={dial_radius_px:.1f} "
        f"reprojection_error_px={result.reproj:.3f} center_displacement_frac={result.center_err:.4f}",
    ]
    panel = rr2.diagnostic_panel(geo, extra_lines=pose_lines)

    for name, img in (
        ("full_raw", full_raw), ("full_user_mode", full_user), ("full_diagnostic_mode", full_diag),
        ("crop_user_mode", crop_user), ("crop_diagnostic_mode", crop_diag), ("crop_raw", crop_raw),
        ("diagnostic_panel", panel),
    ):
        cv2.imwrite(str(out_dir / f"{label}__{name}.jpg"), img, [cv2.IMWRITE_JPEG_QUALITY, 95])

    print(f"[{label}] tilt_deg={result.tilt_deg:.2f} dial_radius_px={dial_radius_px:.1f} "
          f"round_segmented={geo.n_round_segmented}/8 hours={sorted(geo.round_observations.keys())} "
          f"corridor_suppressed={geo.corridor_suppressed_reason is not None}")
    for which in ("inner", "centre", "outer"):
        s = geo.corridor_fit_summary.get(which)
        if s is not None:
            print(f"[{label}] corridor[{which}]: n={s.n_points} median_residual_px={s.median_residual_px:.3f} "
                  f"robust_std_px={s.robust_std_px:.3f}")
        elif geo.corridor_suppressed_reason:
            print(f"[{label}] corridor[{which}]: suppressed -- {geo.corridor_suppressed_reason}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    ap.add_argument("--label", required=True)
    args = ap.parse_args()
    run(args.image, args.out_dir, args.label)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
