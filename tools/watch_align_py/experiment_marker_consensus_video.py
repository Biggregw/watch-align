#!/usr/bin/env python3
"""Same-watch video stress test for the projective marker-consensus
approach (Part 7). All frames are the SAME physical watch, never treated
as independent watches. Runs the existing, unmodified production
acquisition path (pipeline.build) to get ellipse/roll per frame, then the
new marker_consensus analysis layer on top -- this script adds analysis,
it never re-derives or changes pose acquisition.
"""
from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path

import cv2

import marker_consensus as mc
import marker_consensus_analysis as mca
import pipeline
from image_io import resize_to_max_dim

ALL_HOURS = mc.ALL_MARKER_HOURS


def process_one(image_path: Path) -> list:
    raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    rows = []
    if raw is None:
        return rows
    bgr = resize_to_max_dim(raw, 1600)
    result = pipeline.build(bgr)
    if result.reason or result.acquisition is None or result.acquisition.dial_ellipse is None:
        rows.append({"frame": image_path.stem, "pipeline_outcome": "acquisition_failed",
                     "failure_reason": result.reason, "hour": "", "tilt_deg": ""})
        return rows

    ellipse = result.acquisition.dial_ellipse
    roll = result.acquisition.roll_deg
    tilt = result.tilt_deg
    dial_radius_px = (max(ellipse.w, ellipse.h) + min(ellipse.w, ellipse.h)) / 4.0
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 1.2)
    edges = cv2.Canny(blur, 55, 145)

    analysis = mca.analyze_frame(gray, edges, ellipse, roll, dial_radius_px)

    for h in ALL_HOURS:
        obs = analysis.observations.get(h)
        row = {
            "frame": image_path.stem, "pipeline_outcome": "accepted" if result.accepted else "rejected",
            "failure_reason": "", "hour": h, "tilt_deg": f"{tilt:.3f}" if math.isfinite(tilt) else "",
            "shape": obs.shape if obs else "",
            "segmented": "1" if obs else "0",
            "confidence": f"{obs.confidence:.4f}" if obs else "",
            "area_norm": f"{obs.area_norm:.5f}" if obs else "",
            "anisotropy": f"{obs.anisotropy:.4f}" if obs else "",
        }
        loo = analysis.outer_loo.get(h)
        if loo is not None:
            row["loo_outer_fit_ok"] = "1" if loo.fit_ok else "0"
            row["loo_outer_n_peers"] = loo.n_peers
            row["loo_outer_residual_px"] = f"{loo.residual_px:.4f}" if loo.fit_ok else ""
            row["loo_outer_radial_px"] = f"{loo.radial_component_px:.4f}" if loo.fit_ok else ""
            row["loo_outer_tangential_px"] = f"{loo.tangential_component_px:.4f}" if loo.fit_ok else ""
        ang = analysis.angular.get(h)
        if ang is not None:
            row["angular_residual_deg"] = f"{ang.angular_residual_deg:.4f}"
            row["principal_axis_residual_deg"] = (f"{ang.principal_axis_residual_deg:.4f}"
                                                    if math.isfinite(ang.principal_axis_residual_deg) else "")
            row["opposing_consistency_deg"] = (f"{ang.opposing_consistency_deg:.4f}"
                                                if math.isfinite(ang.opposing_consistency_deg) else "")
        clr = analysis.clearance.get(h)
        if clr is not None:
            row["clearance_px"] = f"{clr.clearance_px:.4f}"
            row["clearance_normalized"] = (f"{clr.clearance_normalized:.4f}"
                                            if math.isfinite(clr.clearance_normalized) else "")
        shp = analysis.shape_offsets.get(h)
        if shp is not None and shp.fit_ok:
            row["shape_offset_residual_px"] = f"{shp.residual_px:.4f}"
        verdict = analysis.verdicts.get(h)
        if verdict is not None:
            row["verdict_state"] = verdict.state
            row["verdict_message"] = verdict.message
            for sig, z in verdict.signals.items():
                row[f"z_{sig}"] = f"{z:.3f}" if math.isfinite(z) else ""
        rows.append(row)
    return rows


def run(images_dir: Path, out_csv: Path) -> None:
    images = sorted(images_dir.glob("*.jpg"))
    print(f"found {len(images)} frames in {images_dir}")
    all_rows = []
    for img in images:
        rows = process_one(img)
        all_rows.extend(rows)
        verdict_counts = {}
        for r in rows:
            s = r.get("verdict_state")
            if s:
                verdict_counts[s] = verdict_counts.get(s, 0) + 1
        print(f"  {img.name}: outcome={rows[0].get('pipeline_outcome')} "
              f"tilt={rows[0].get('tilt_deg')} verdicts={verdict_counts}")

    all_keys = []
    seen = set()
    for r in all_rows:
        for k in r.keys():
            if k not in seen:
                seen.add(k)
                all_keys.append(k)
    out_csv.parent.mkdir(parents=True, exist_ok=True)
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=all_keys, restval="")
        w.writeheader()
        w.writerows(all_rows)
    print(f"wrote {len(all_rows)} rows to {out_csv}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images-dir", required=True, type=Path)
    ap.add_argument("--out-csv", required=True, type=Path)
    args = ap.parse_args()
    run(args.images_dir, args.out_csv)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
