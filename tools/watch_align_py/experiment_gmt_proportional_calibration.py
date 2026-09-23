#!/usr/bin/env python3
"""GMT 12-triangle proportional-geometry calibration run
(experiment/gmt-proportional-geometry-v1).

Runs gmt_proportional_features.compute() over the calibration-split
gen_candidate accepted images only (see the --class/--split/--provenance
filters below, all fixed to the values the research rules require).
Reuses the already-computed baseline ellipse/roll is NOT done here --
unlike some earlier experiments in this project, this one needs the raw
image (for triangle segmentation and marker resegmentation), so it reruns
pipeline.build() per image, matching how reticle_v2's own driver works.

Writes one row per image (per_image_gmt_proportional.csv) with every
candidate feature plus diagnostics, and physical_watch_id is carried
through explicitly so downstream analysis can respect it as the
independence unit -- this script does not itself compute population
statistics (that is a separate, explicitly-labelled analysis step so the
raw per-image evidence stays inspectable on its own).
"""
from __future__ import annotations

import csv
from pathlib import Path
from typing import Optional

import cv2

import gmt_proportional_features as gpf
import pipeline
from image_io import resize_to_max_dim


def run(per_image_csv: Path, images_root: Path, out_csv: Path,
        split: str = "calibration", class_label: str = "gen", provenance: str = "gen_candidate") -> None:
    with per_image_csv.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    selected = [r for r in rows if r.get("pipeline_outcome") == "accepted"
                and r.get("split") == split and r.get("class_label") == class_label
                and r.get("provenance") == provenance]
    print(f"selected {len(selected)} accepted images (split={split!r} class={class_label!r} provenance={provenance!r})")

    by_watch = {}
    for r in selected:
        by_watch.setdefault(r["physical_watch_id"], []).append(r)
    print(f"distinct physical_watch_id count: {len(by_watch)}")
    for pid, rs in sorted(by_watch.items()):
        print(f"  {pid}: {len(rs)} image(s)")

    out_rows = []
    for i, row in enumerate(selected, start=1):
        image_path = images_root / row.get("local_path", "")
        if not image_path.is_file():
            print(f"[{i}/{len(selected)}] MISSING {image_path}")
            continue
        raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
        if raw is None:
            print(f"[{i}/{len(selected)}] UNDECODABLE {image_path}")
            continue
        bgr = resize_to_max_dim(raw, 1600)
        gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)

        result = pipeline.build(bgr)
        print(f"[{i}/{len(selected)}] {row['source_id']} tilt={row.get('tilt_deg')}")
        if result.acquisition is None or result.acquisition.dial_ellipse is None:
            print(f"  no pose acquired, skipping")
            continue
        ellipse = result.acquisition.dial_ellipse
        roll = result.solved_roll
        dial_radius_px = result.dial_radius_px

        res = gpf.compute(gray, ellipse, roll, dial_radius_px, result.tilt_deg)
        out = {
            "source_id": row["source_id"], "physical_watch_id": row["physical_watch_id"],
            "class_label": row["class_label"], "provenance": row.get("provenance", ""),
            "split": row["split"], "factory": row.get("factory", ""),
            "local_path": row["local_path"], "tilt_deg": row.get("tilt_deg"),
            "dial_radius_px": dial_radius_px,
            "n_round_segmented": res.diagnostics.get("n_round_segmented"),
            "round_hours_contributing": ",".join(str(h) for h in res.diagnostics.get("round_hours_contributing", [])),
            "corridor_suppressed_reason": res.diagnostics.get("corridor_suppressed_reason") or "",
            "triangle_segmented": res.diagnostics.get("triangle_segmented"),
            "triangle_suppressed_reason": res.diagnostics.get("triangle_suppressed_reason", ""),
            "triangle_confidence": res.diagnostics.get("triangle_confidence"),
            "triangle_solidity": res.diagnostics.get("triangle_solidity"),
            "triangle_axis_agreement_deg": res.diagnostics.get("triangle_axis_agreement_deg"),
            "projective_correspondences_available": res.diagnostics.get("projective_correspondences_available"),
            "projective_suppressed_reason": res.diagnostics.get("projective_suppressed_reason", ""),
        }
        pf = res.diagnostics.get("projective_fit")
        if pf:
            out["projective_fit_a"] = pf["a"]
            out["projective_fit_c"] = pf["c"]
            out["projective_fit_max_abs_residual_canon"] = pf["max_abs_residual_canon"]
            out["projective_fit_condition_ok"] = pf["condition_ok"]
        for which, resid in (res.diagnostics.get("corridor_fit_residuals_px") or {}).items():
            out[f"corridor_{which}_residual_px"] = resid
        for k, v in res.features.items():
            out[k] = v
        out_rows.append(out)

    out_csv.parent.mkdir(parents=True, exist_ok=True)
    if not out_rows:
        raise RuntimeError("no rows produced")
    fieldnames = list(out_rows[0].keys())
    for r in out_rows:
        for k in r.keys():
            if k not in fieldnames:
                fieldnames.append(k)
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, restval="")
        w.writeheader()
        w.writerows(out_rows)
    print(f"wrote {len(out_rows)} rows to {out_csv}")
    n_tri = sum(1 for r in out_rows if r["triangle_segmented"])
    n_proj = sum(1 for r in out_rows if r["projective_correspondences_available"])
    print(f"triangle segmented: {n_tri}/{len(out_rows)}; projective fit available: {n_proj}/{len(out_rows)}")


def main() -> int:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image-csv", required=True, type=Path)
    ap.add_argument("--images-root", required=True, type=Path)
    ap.add_argument("--out-csv", required=True, type=Path)
    ap.add_argument("--split", default="calibration")
    ap.add_argument("--class-label", default="gen")
    ap.add_argument("--provenance", default="gen_candidate")
    args = ap.parse_args()
    run(args.per_image_csv, args.images_root, args.out_csv, args.split, args.class_label, args.provenance)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
