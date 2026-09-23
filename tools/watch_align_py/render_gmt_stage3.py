#!/usr/bin/env python3
"""Apply the frozen Stage 3 GMT 12-triangle profile, UNCHANGED, to one
photo (experiment/gmt-proportional-geometry-v1, phase 3). Same code,
same frozen profile for both the official reference and the user's QC
photo -- no per-image tuning.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import cv2

import dial_crop
import gmt_proportional_features as gpf
import gmt_proportional_render_stage3 as gpr3
import pipeline
import reticle_v2 as rv2
from image_io import resize_to_max_dim


def run(image_path: Path, profile_path: Path, out_dir: Path, label: str) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    profile = json.loads(profile_path.read_text())

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
    res = gpf.compute(gray, ellipse, roll, dial_radius_px, result.tilt_deg)

    full_raw = bgr
    full_user = gpr3.user_mode(bgr, ellipse, roll, geo, res, profile)
    full_diag = gpr3.diagnostic_mode(bgr, ellipse, roll, geo, res, profile)
    crop_user = dial_crop.dial_crop(full_user, ellipse, dial_radius_px)
    crop_diag = dial_crop.dial_crop(full_diag, ellipse, dial_radius_px)
    panel = gpr3.diagnostic_panel(res, profile, label)

    for name, img in (("full_raw", full_raw), ("full_user_mode", full_user), ("full_diagnostic_mode", full_diag),
                       ("crop_user_mode", crop_user), ("crop_diagnostic_mode", crop_diag),
                       ("diagnostic_panel", panel)):
        cv2.imwrite(str(out_dir / f"{label}__{name}.jpg"), img, [cv2.IMWRITE_JPEG_QUALITY, 95])

    p12 = geometry_point_12(ellipse, roll, dial_radius_px)
    half = int(dial_radius_px * 0.30)
    x0, y0 = max(0, p12[0] - half), max(0, p12[1] - half)
    x1, y1 = min(bgr.shape[1], p12[0] + half), min(bgr.shape[0], p12[1] + half)

    def _upscale(img):
        crop = img[y0:y1, x0:x1]
        h, w = crop.shape[:2]
        scale = 700 / max(h, w)
        interp = cv2.INTER_CUBIC if scale > 1.0 else cv2.INTER_AREA
        return cv2.resize(crop, (max(1, round(w * scale)), max(1, round(h * scale))), interpolation=interp)

    cv2.imwrite(str(out_dir / f"{label}__crop12_user_mode.jpg"), _upscale(full_user), [cv2.IMWRITE_JPEG_QUALITY, 95])
    cv2.imwrite(str(out_dir / f"{label}__crop12_diagnostic_mode.jpg"), _upscale(full_diag), [cv2.IMWRITE_JPEG_QUALITY, 95])

    summary = {
        "label": label, "tilt_deg": result.tilt_deg, "dial_radius_px": dial_radius_px,
        "diagnostics": {k: (list(v) if isinstance(v, tuple) else v) for k, v in res.diagnostics.items()},
        "features": res.features,
    }
    (out_dir / f"{label}__measurements.json").write_text(json.dumps(summary, indent=2, default=str))

    print(f"[{label}] tilt={result.tilt_deg:.2f} "
          f"triangle_segmented={res.diagnostics.get('triangle_segmented')} "
          f"projective_available={res.diagnostics.get('projective_correspondences_available')}")
    for f in profile["selected_core_features"]:
        name = f["name"]
        v = res.features.get(name)
        print(f"  {name} [{f['role']}/{f['normalisation']}]: observed={v} calibration_median={f['calibration_median']}")


def geometry_point_12(ellipse, roll, dial_radius_px):
    import geometry
    import master
    angle = master.angle_for_hour(12)
    x, y = geometry.map_point(ellipse, 1.0, roll, math.cos(angle) * 0.72, math.sin(angle) * 0.72)
    return (int(round(x)), int(round(y)))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", required=True, type=Path)
    ap.add_argument("--profile", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    ap.add_argument("--label", required=True)
    args = ap.parse_args()
    run(args.image, args.profile, args.out_dir, args.label)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
