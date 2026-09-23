#!/usr/bin/env python3
"""Stage 2 genuine-reference ablation -- reference asset generation
(experiment/generic-qc-reticle).

Produces exactly two assets from the single fixed genuine reference image
(source_id=official_rolex_2026: first-party Rolex catalogue photo,
provenance=official, split=reference -- not a source-labelled
"gen_candidate", per Stage 2's explicit instruction not to treat a
gen_candidate image as authenticated ground truth):

  1. full raw reference image
  2. deterministic dial crop of the reference (dial_crop.py, the same
     function used for the Stage 1 query images -- centred on Watch
     Align's own detected dial geometry, never on any marker)

No reticle overlay is applied to the reference image in either Stage 2
condition (C or D) -- only the query image gets the overlay in D. The
reference is never altered to resemble a defect and is not re-selected
based on which synthetic defect a given query carries; it is the same
fixed file for every one of the 23 scored examples.

Runs on a GitHub Actions runner for the same reason Stage 1's asset
generation did: this repo's fetch_images.py only works from a runner,
not from the interactive research sandbox (egress proxy blocks the
corpus's image hosts).
"""
from __future__ import annotations

import csv
from pathlib import Path

import cv2

import dial_crop
import pipeline
from image_io import resize_to_max_dim

REFERENCE_SOURCE_ID = "official_rolex_2026"


def run(per_image_csv: Path, images_root: Path, out_dir: Path) -> None:
    with per_image_csv.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    row = next((r for r in rows if r["source_id"] == REFERENCE_SOURCE_ID), None)
    if row is None:
        raise RuntimeError(f"reference source {REFERENCE_SOURCE_ID!r} not found in {per_image_csv}")
    if row.get("provenance") != "official":
        raise RuntimeError(
            f"refusing to use {REFERENCE_SOURCE_ID!r} as the Stage 2 genuine reference: "
            f"provenance is {row.get('provenance')!r}, not 'official'"
        )

    image_path = images_root / row["local_path"]
    raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    if raw is None:
        raise RuntimeError(f"could not decode {image_path}")
    bgr = resize_to_max_dim(raw, 1600)

    result = pipeline.build(bgr)
    if result.acquisition is None or result.acquisition.dial_ellipse is None:
        raise RuntimeError("pipeline.build() could not acquire a pose for the reference image")
    ellipse = result.acquisition.dial_ellipse
    dial_radius_px = result.dial_radius_px

    out_dir.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(out_dir / "reference_full_raw.jpg"), bgr, [cv2.IMWRITE_JPEG_QUALITY, 95])
    crop = dial_crop.dial_crop(bgr, ellipse, dial_radius_px)
    cv2.imwrite(str(out_dir / "reference_crop_raw.jpg"), crop, [cv2.IMWRITE_JPEG_QUALITY, 95])

    print(f"reference source: {REFERENCE_SOURCE_ID} (provenance={row['provenance']}, split={row['split']})")
    print(f"reference source_url: {row.get('source_url')}")
    print(f"reference tilt_deg: {row.get('tilt_deg')}")
    print(f"reference pipeline_outcome: {row.get('pipeline_outcome')}")
    print(f"reference dial_radius_px: {dial_radius_px:.1f}")
    print("wrote reference_full_raw.jpg and reference_crop_raw.jpg")


def main() -> int:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image-csv", required=True, type=Path)
    ap.add_argument("--images-root", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    args = ap.parse_args()
    run(args.per_image_csv, args.images_root, args.out_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
