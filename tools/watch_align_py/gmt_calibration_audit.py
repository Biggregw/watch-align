#!/usr/bin/env python3
"""Calibration-corpus expansion / failure audit
(experiment/gmt-proportional-geometry-v1, phase 2).

Runs the FROZEN pose-acquisition pipeline and the FROZEN
gmt_proportional_features.compute() over every image resolved for the
calibration-split gen_candidate sources in the manifest, and reports,
per image, exactly where it survives or drops out of the pipeline:

    fetched -> pose accepted -> round-marker corridor usable ->
    triangle segmented -> simple radial features -> projective radial
    features

Every image gets a row. Nothing is silently discarded: an image that
fails is kept in the per-image CSV with a `failure_stage` and
`failure_category` explaining why, plus the raw diagnostic text from
the stage that rejected it.

This script does NOT read or write datasets/126710BLNR/results/
per_image_measurements.csv (the whole-corpus file, which also covers
reference/validation/replica rows) -- it is fully self-contained so
that file, and everything it covers, is left completely untouched.
It also never looks at replica or validation rows: only
split=calibration, class_label=gen, provenance=gen_candidate sources
are read from the manifest at all.

Two levels of output:
  - per-image audit CSV (one row per resolved image, plus one row per
    manifest source that produced zero resolved images at all)
  - per-source summary CSV (the funnel counts the task asked for)

Feature values (apex_r_simple, base_r_simple, ... and the projective
variants) are included directly on each per-image audit row when
available, so this file doubles as the expanded raw feature table for
the downstream distribution analysis -- no separate feature-extraction
pass or dependency on a previously-committed per_image_measurements.csv
is needed.
"""
from __future__ import annotations

import csv
import math
import sys
from pathlib import Path
from typing import Dict, List, Optional

import cv2

import gmt_proportional_features as gpf
import pipeline
from image_io import resize_to_max_dim

ROOT = Path(__file__).resolve().parent.parent.parent / "datasets" / "126710BLNR"
MANIFEST = ROOT / "manifest.csv"
RESOLVED = ROOT / "resolved_images.csv"

# Tilt above which this corpus's own frozen calibration profile has zero
# supporting evidence (Stage 1's supported_tilt_range_deg was [3.7, 20.7]
# across all five core features) is not itself a hard cutoff here -- we
# do not want to bias the audit by discarding high-tilt images before
# measuring them. This threshold is only used to LABEL a pipeline
# "rejected" outcome as oblique-driven for the grouped failure report,
# using the same order of magnitude the corpus's own rejected rows
# already showed (36-49 degrees) vs. its accepted rows (3.7-20.7 degrees).
OBLIQUE_REJECT_LABEL_DEG = 25.0

# A soft, reported-only signal, not a hard usability cutoff: below this
# resolution a hand/marker edge is unlikely to be reliably measurable.
# fetch_images.py already silently drops anything under 300px / 180000px^2
# before it ever reaches resolved_images.csv, so this can only flag
# images that passed that first, coarser filter but are still small.
SMALL_IMAGE_WARN_DIM = 700


def _blur_variance(gray) -> float:
    return float(cv2.Laplacian(gray, cv2.CV_64F).var())


def read_manifest_calibration_gen_candidate() -> List[dict]:
    with MANIFEST.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    return [r for r in rows if r["split"] == "calibration" and r["class_label"] == "gen"
            and r["provenance"] == "gen_candidate"]


def read_resolved() -> List[dict]:
    if not RESOLVED.exists():
        return []
    with RESOLVED.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def classify_pose_failure(result, tilt_deg: Optional[float]) -> str:
    if result.reason:
        if "dial centre" in result.reason:
            return "pose acquisition failure (no dial centre found)"
        if "dial ellipse" in result.reason:
            return "pose acquisition failure (no dial ellipse found)"
        return f"pose acquisition failure ({result.reason})"
    # reason == "" but not accepted -> rejected downstream of ellipse fit
    if tilt_deg is not None and tilt_deg >= OBLIQUE_REJECT_LABEL_DEG:
        return "image too oblique"
    return "pose rejected (failed orientation/validation/identity gate at moderate tilt)"


def audit_image(row: dict) -> dict:
    """row is one resolved_images.csv row. Returns one flat audit dict."""
    out = {
        "source_id": row["source_id"],
        "physical_watch_id": row["physical_watch_id"],
        "local_path": row["local_path"],
        "width": row.get("width"),
        "height": row.get("height"),
        "dhash": row.get("dhash"),
        "possible_duplicate_of": row.get("possible_duplicate_of") or "",
    }
    if out["possible_duplicate_of"]:
        out["failure_stage"] = "duplicate_flagged"
        out["failure_category"] = "duplicate/near-duplicate view"
        out["failure_detail"] = f"dhash within Hamming distance 3 of {out['possible_duplicate_of']}; kept in audit, excluded from feature aggregation"
        # still measured below -- flagged, not silently dropped

    image_path = ROOT / row["local_path"]
    if not image_path.is_file():
        out["failure_stage"] = "fetch"
        out["failure_category"] = "acquisition/fetch failure"
        out["failure_detail"] = "resolved_images.csv row present but local file missing"
        return out

    raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    if raw is None:
        out["failure_stage"] = "fetch"
        out["failure_category"] = "acquisition/fetch failure"
        out["failure_detail"] = "image file present but undecodable"
        return out

    bgr = resize_to_max_dim(raw, 1600)
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    out["blur_laplacian_var"] = round(_blur_variance(gray), 1)
    small_dim = min(int(row.get("width") or 0), int(row.get("height") or 0))
    out["small_image_warning"] = small_dim > 0 and small_dim < SMALL_IMAGE_WARN_DIM

    result = pipeline.build(bgr)
    out["tilt_deg"] = result.tilt_deg if not result.reason else None
    out["pose_accepted"] = bool(result.accepted) if not result.reason else False

    if result.reason or not result.accepted:
        out.setdefault("failure_stage", "pose")
        if not out.get("failure_category"):
            out["failure_category"] = classify_pose_failure(result, out["tilt_deg"])
            out["failure_detail"] = result.reason or (
                f"tilt={out['tilt_deg']:.1f} deg; automatic_accepted/final gates rejected"
                if out["tilt_deg"] is not None else "rejected before tilt could be computed"
            )
        return out

    ellipse = result.acquisition.dial_ellipse
    roll = result.solved_roll
    dial_radius_px = result.dial_radius_px
    res = gpf.compute(gray, ellipse, roll, dial_radius_px, result.tilt_deg)
    out["dial_radius_px"] = dial_radius_px
    out["n_round_segmented"] = res.diagnostics.get("n_round_segmented")
    out["round_hours_contributing"] = ",".join(str(h) for h in res.diagnostics.get("round_hours_contributing", []))
    out["corridor_suppressed_reason"] = res.diagnostics.get("corridor_suppressed_reason") or ""
    out["triangle_segmented"] = bool(res.diagnostics.get("triangle_segmented"))
    out["triangle_suppressed_reason"] = res.diagnostics.get("triangle_suppressed_reason", "")
    out["triangle_confidence"] = res.diagnostics.get("triangle_confidence")
    out["triangle_solidity"] = res.diagnostics.get("triangle_solidity")
    out["triangle_axis_agreement_deg"] = res.diagnostics.get("triangle_axis_agreement_deg")
    out["projective_correspondences_available"] = bool(res.diagnostics.get("projective_correspondences_available"))
    out["projective_suppressed_reason"] = res.diagnostics.get("projective_suppressed_reason", "")
    pf = res.diagnostics.get("projective_fit")
    if pf:
        out["projective_fit_a"] = pf["a"]
        out["projective_fit_c"] = pf["c"]
        out["projective_fit_max_abs_residual_canon"] = pf["max_abs_residual_canon"]
        out["projective_fit_condition_ok"] = pf["condition_ok"]
    for k, v in res.features.items():
        out[k] = v

    if not out.get("failure_category"):
        if not out["triangle_segmented"]:
            out["failure_stage"] = "triangle_segmentation"
            out["failure_category"] = "triangle segmentation failure"
            out["failure_detail"] = out["triangle_suppressed_reason"]
        elif out.get("apex_r_simple") is None:
            out["failure_stage"] = "simple_features"
            out["failure_category"] = "simple radial feature computation failure"
            out["failure_detail"] = "triangle segmented but simple canonical features not produced"
        else:
            out["failure_stage"] = ""
            out["failure_category"] = ""
            out["failure_detail"] = ""
        if not out["projective_correspondences_available"] and not out["failure_category"]:
            # usable for simple features but not projective -- not a hard
            # "failure" of the image, a coverage note for that feature
            # family only, recorded on the same row.
            out["projective_unavailable_reason"] = (
                out["projective_suppressed_reason"] or out["corridor_suppressed_reason"]
                or "round-marker corridor unavailable along the 12 axis"
            )
            if out["n_round_segmented"] is not None and out["n_round_segmented"] < 5:
                out["projective_unavailable_category"] = "round-marker segmentation failure"
            else:
                out["projective_unavailable_category"] = "projective mapping instability"
    return out


def run(out_dir: Path) -> None:
    manifest_rows = read_manifest_calibration_gen_candidate()
    manifest_source_ids = {r["source_id"] for r in manifest_rows}
    resolved_rows = [r for r in read_resolved() if r["source_id"] in manifest_source_ids]
    resolved_source_ids = {r["source_id"] for r in resolved_rows}

    per_image_rows: List[dict] = []
    for r in resolved_rows:
        audited = audit_image(r)
        per_image_rows.append(audited)
        tag = audited.get("failure_category") or "OK"
        print(f"  {r['source_id']}/{r['local_path']}: {tag}")

    # Sources present in the manifest but with zero resolved images at all
    # (total fetch failure) -- one placeholder row each, never silently
    # dropped from the audit.
    zero_fetch_sources = manifest_source_ids - resolved_source_ids
    for source_id in sorted(zero_fetch_sources):
        mrow = next(r for r in manifest_rows if r["source_id"] == source_id)
        per_image_rows.append({
            "source_id": source_id,
            "physical_watch_id": mrow["physical_watch_id"],
            "local_path": "",
            "failure_stage": "fetch",
            "failure_category": "acquisition/fetch failure",
            "failure_detail": "source produced zero resolved images this run (gallery-dl failure or empty album after size-filtering)",
        })
        print(f"  {source_id}: zero images resolved -- acquisition/fetch failure")

    # ---- per-image audit CSV ----
    fieldnames: List[str] = []
    for row in per_image_rows:
        for k in row.keys():
            if k not in fieldnames:
                fieldnames.append(k)
    out_dir.mkdir(parents=True, exist_ok=True)
    per_image_csv = out_dir / "gmt_calibration_audit_per_image.csv"
    with per_image_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, restval="")
        w.writeheader()
        w.writerows(per_image_rows)
    print(f"wrote {len(per_image_rows)} rows to {per_image_csv}")

    # ---- per-source summary CSV ----
    by_source: Dict[str, List[dict]] = {}
    for row in per_image_rows:
        by_source.setdefault(row["source_id"], []).append(row)

    summary_rows = []
    for source_id in sorted(manifest_source_ids):
        mrow = next(r for r in manifest_rows if r["source_id"] == source_id)
        rows = by_source.get(source_id, [])
        n_fetched = sum(1 for r in rows if r.get("local_path"))
        n_pose_ok = sum(1 for r in rows if r.get("pose_accepted") is True)
        n_round_ok = sum(1 for r in rows if (r.get("n_round_segmented") or 0) >= 5
                          and not r.get("corridor_suppressed_reason"))
        n_tri_ok = sum(1 for r in rows if r.get("triangle_segmented") is True)
        n_simple_ok = sum(1 for r in rows if r.get("apex_r_simple") not in (None, ""))
        n_proj_ok = sum(1 for r in rows if r.get("projective_correspondences_available") is True)
        n_dupe = sum(1 for r in rows if r.get("failure_category") == "duplicate/near-duplicate view")
        summary_rows.append({
            "source_id": source_id,
            "physical_watch_id": mrow["physical_watch_id"],
            "manifest_max_images_cap": mrow["max_images"],
            "n_images_fetched": n_fetched,
            "n_flagged_duplicate": n_dupe,
            "n_passing_pose_gates": n_pose_ok,
            "n_usable_round_marker_geometry": n_round_ok,
            "n_usable_triangle_segmentation": n_tri_ok,
            "n_usable_simple_radial_features": n_simple_ok,
            "n_usable_projective_radial_features": n_proj_ok,
            "usable_for_calibration": n_simple_ok > 0,
        })
    summary_csv = out_dir / "gmt_calibration_audit_per_source.csv"
    with summary_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(summary_rows[0].keys()))
        w.writeheader()
        w.writerows(summary_rows)
    print(f"wrote {len(summary_rows)} rows to {summary_csv}")

    n_usable_watches = len({r["physical_watch_id"] for r in summary_rows if r["usable_for_calibration"]})
    print(f"\nindependent usable calibration gen_candidate physical watches (>=1 simple-feature image): {n_usable_watches}")
    for r in summary_rows:
        print(f"  {r['physical_watch_id']} ({r['source_id']}): fetched={r['n_images_fetched']} "
              f"pose_ok={r['n_passing_pose_gates']} round_ok={r['n_usable_round_marker_geometry']} "
              f"tri_ok={r['n_usable_triangle_segmentation']} simple_ok={r['n_usable_simple_radial_features']} "
              f"proj_ok={r['n_usable_projective_radial_features']}")


def main() -> int:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-dir", required=True, type=Path)
    args = ap.parse_args()
    run(args.out_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
