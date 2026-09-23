#!/usr/bin/env python3
"""Stage 1 AI A/B batch -- deterministic asset generation
(experiment/generic-qc-reticle).

Runs on a GitHub Actions runner (this environment's egress proxy blocks
the corpus's third-party image hosts, so image fetch cannot happen in the
interactive sandbox -- only here). For each selected calibration-split
source photo, produces 4 assets:

  1. full raw image
  2. full frozen Variant C reticle image (reticle_render.variant_c_minimal)
  3. deterministic high-resolution dial crop of (1), from dial_crop.py --
     a pure function of Watch Align's own detected ellipse centre/radius,
     never of any marker or suspected-defect location
  4. the identical crop box applied to (2)

...plus, for a fixed list of (source, hour, mode, magnitude) synthetic
perturbations, the same 4 assets computed from an edited copy of the
photo (synth_defects.apply_synthetic_defect), reusing the SAME already-
detected pose (ellipse/roll/dial_radius_px) as the unedited photo so the
crop box never shifts because of the injected edit.

Writes a manifest CSV with ground truth (hour/shape/mode/magnitude) --
this file is for the *orchestrating* session's own bookkeeping and must
never be shown to any blinded reviewing model. Images are uploaded only
as a workflow artifact, never committed to git (third-party source-photo
content).

Calibration split only. Does not inspect or optimise against validation.
"""
from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Optional

import cv2

import dial_crop
import pipeline
import reticle
import reticle_render as rr
import synth_defects as sd
from image_io import resize_to_max_dim

# One representative accepted calibration-split image per physical watch,
# chosen for a spread of tilt (2.8-11 deg) and factory/provenance, from
# datasets/126710BLNR/results/per_image_measurements.csv. Selected before
# any of this batch's results were seen.
BASE_SOURCES = [
    "rep_vsf_KfRKFwA", "gen_wex_3KSuGhC", "rep_cf_xK08a3L", "rep_arf_m304y61",
    "rep_cf_bpdi5xV", "rep_vsf_sh7PSsh", "rep_cplus_wEYZOyK", "gen_wex_e99gXKb",
    "rep_cf_QsJT2wk",
]

# (source_id, hour, mode, magnitude) -- fixed before any batch result was
# seen. Magnitudes: rotate/tangential in degrees, radial_in/out in
# canonical (dial-radius-normalised) units.
SYNTH_PLAN = [
    ("rep_vsf_KfRKFwA", 9, "rotate", 8.0),
    ("gen_wex_3KSuGhC", 9, "rotate", 16.0),
    ("rep_cf_xK08a3L", 9, "rotate", 28.0),
    ("rep_arf_m304y61", 2, "tangential", 3.0),
    ("rep_cf_bpdi5xV", 2, "tangential", 6.0),
    ("rep_vsf_sh7PSsh", 2, "tangential", 10.0),
    ("rep_cf_QsJT2wk", 10, "radial_in", 0.035),
    ("rep_cplus_wEYZOyK", 10, "radial_in", 0.065),
    ("gen_wex_e99gXKb", 10, "radial_in", 0.10),
    ("rep_vsf_KfRKFwA", 4, "radial_out", 0.035),
    ("gen_wex_3KSuGhC", 4, "radial_out", 0.065),
    ("rep_cf_xK08a3L", 4, "radial_out", 0.10),
    ("rep_arf_m304y61", 12, "rotate", 18.0),
    ("rep_cf_bpdi5xV", 6, "tangential", 6.0),
]


def _row_for_source(rows, source_id: str) -> Optional[dict]:
    candidates = [r for r in rows if r["source_id"] == source_id
                  and r.get("pipeline_outcome") == "accepted" and r.get("split") == "calibration"]
    if not candidates:
        return None
    return min(candidates, key=lambda r: float(r.get("tilt_deg") or 1e9))


def _build_pose(bgr):
    result = pipeline.build(bgr)
    if result.acquisition is None or result.acquisition.dial_ellipse is None:
        return None
    return result.acquisition.dial_ellipse, result.solved_roll, result.dial_radius_px


def _write4(out_dir: Path, example_id: str, raw_bgr, ellipse, dial_radius_px, gray_for_geo, roll):
    geo = reticle.build_geometry(gray_for_geo, ellipse, roll, dial_radius_px)
    reticle_bgr = rr.variant_c_minimal(raw_bgr, geo)
    crop_raw = dial_crop.dial_crop(raw_bgr, ellipse, dial_radius_px)
    crop_reticle = dial_crop.dial_crop(reticle_bgr, ellipse, dial_radius_px)
    paths = {}
    for name, img in (("full_raw", raw_bgr), ("full_reticle", reticle_bgr),
                       ("crop_raw", crop_raw), ("crop_reticle", crop_reticle)):
        p = out_dir / f"{example_id}__{name}.jpg"
        cv2.imwrite(str(p), img, [cv2.IMWRITE_JPEG_QUALITY, 95])
        paths[name] = p.name
    return paths, geo.n_round_segmented


def run(per_image_csv: Path, images_root: Path, out_dir: Path, manifest_csv: Path) -> None:
    with per_image_csv.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    out_dir.mkdir(parents=True, exist_ok=True)
    manifest_rows = []

    poses = {}
    bgrs = {}
    missing = []
    for source_id in BASE_SOURCES:
        row = _row_for_source(rows, source_id)
        if row is None:
            missing.append(source_id)
            continue
        image_path = images_root / row["local_path"]
        raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
        if raw is None:
            missing.append(source_id)
            continue
        bgr = resize_to_max_dim(raw, 1600)
        pose = _build_pose(bgr)
        if pose is None:
            missing.append(source_id)
            continue
        ellipse, roll, dial_radius_px = pose
        poses[source_id] = (ellipse, roll, dial_radius_px, row)
        bgrs[source_id] = bgr
        print(f"pose ok: {source_id} tilt={row.get('tilt_deg')} factory={row.get('factory')}")

    if missing:
        print(f"WARNING: {len(missing)} base sources unusable (fetch failure, decode failure, or no pose): {missing}")

    # Controls -- one per successfully-posed base source, no edit at all.
    for i, source_id in enumerate(sorted(poses.keys()), start=1):
        ellipse, roll, dial_radius_px, row = poses[source_id]
        bgr = bgrs[source_id]
        gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
        example_id = f"control_{i:02d}_{source_id}"
        paths, n_round = _write4(out_dir, example_id, bgr, ellipse, dial_radius_px, gray, roll)
        manifest_rows.append({
            "example_id": example_id, "kind": "control", "source_id": source_id,
            "tilt_deg": row.get("tilt_deg"), "factory": row.get("factory"), "class_label": row.get("class_label"),
            "hour": "", "shape": "", "mode": "", "magnitude": "", "description": "unedited control",
            "n_round_segmented": n_round, **paths,
        })

    # Synthetic-defect derivatives.
    for i, (source_id, hour, mode, magnitude) in enumerate(SYNTH_PLAN, start=1):
        if source_id not in poses:
            print(f"SKIP synth #{i}: base source {source_id} unusable")
            continue
        ellipse, roll, dial_radius_px, row = poses[source_id]
        bgr = bgrs[source_id]
        edited, rec = sd.apply_synthetic_defect(bgr, ellipse, roll, dial_radius_px, hour, mode, magnitude)
        edited_gray = cv2.cvtColor(edited, cv2.COLOR_BGR2GRAY)
        example_id = f"synth_{i:02d}_{mode}_{source_id}_h{hour}"
        paths, n_round = _write4(out_dir, example_id, edited, ellipse, dial_radius_px, edited_gray, roll)
        manifest_rows.append({
            "example_id": example_id, "kind": "synthetic", "source_id": source_id,
            "tilt_deg": row.get("tilt_deg"), "factory": row.get("factory"), "class_label": row.get("class_label"),
            "hour": hour, "shape": rec.shape, "mode": mode, "magnitude": magnitude, "description": rec.description,
            "n_round_segmented": n_round, **paths,
        })
        print(f"synth ok: {example_id} -- {rec.description}")

    if not manifest_rows:
        raise RuntimeError("no examples produced")
    fieldnames = list(manifest_rows[0].keys())
    for r in manifest_rows:
        for k in r:
            if k not in fieldnames:
                fieldnames.append(k)
    manifest_csv.parent.mkdir(parents=True, exist_ok=True)
    with manifest_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, restval="")
        w.writeheader()
        w.writerows(manifest_rows)
    print(f"wrote {len(manifest_rows)} examples ({sum(1 for r in manifest_rows if r['kind']=='control')} control, "
          f"{sum(1 for r in manifest_rows if r['kind']=='synthetic')} synthetic) to {manifest_csv}")


def main() -> int:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image-csv", required=True, type=Path)
    ap.add_argument("--images-root", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    ap.add_argument("--manifest-csv", required=True, type=Path)
    args = ap.parse_args()
    run(args.per_image_csv, args.images_root, args.out_dir, args.manifest_csv)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
