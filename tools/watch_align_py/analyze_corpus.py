#!/usr/bin/env python3
"""Run the Python Watch Align engine over a fetched image corpus and produce
machine-readable per-image and per-watch results.

This is acquisition-reliability and measurement-repeatability analysis, not
genuine-vs-replica classification. It deliberately reports what the current
engine actually does -- it does not tune thresholds or change the algorithm
to make this corpus look better.

Input: a `resolved_images.csv` produced by datasets/126710BLNR/fetch_images.py
(source_id, class_label, provenance, split, factory, physical_watch_id,
local_path, ... one row per fetched+normalised image).

Output (written to --out-dir):
  per_image_measurements.csv  -- one row per input image, every field below
  per_watch_summary.csv       -- aggregated by physical_watch_id
  run_metadata.json           -- reproducibility metadata
  README.md                   -- (not written here; committed separately)
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import platform
import statistics
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))

import pipeline
import master
from image_io import resize_to_max_dim

MARKER_HOURS = [h for h in range(1, 13) if h != 3]


def _git_commit_sha(repo_root: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=repo_root, text=True
        ).strip()
    except Exception:
        return "unknown"


def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def _normalized_h_term(h: Optional[np.ndarray], r: int, c: int) -> float:
    if h is None:
        return math.nan
    denom = h[2, 2]
    if abs(denom) < 1e-12:
        return math.nan
    return float(h[r, c] / denom)


def _finite(x) -> Optional[float]:
    if x is None:
        return None
    try:
        xf = float(x)
    except (TypeError, ValueError):
        return None
    return xf if math.isfinite(xf) else None


def analyze_one_image(bgr: np.ndarray) -> Dict[str, Any]:
    """Run the full pipeline on one already-decoded BGR image and flatten the
    result into a flat dict of plain scalars, matching per_image_measurements.csv's
    columns. Never raises for an ordinary acquisition failure -- pipeline.build()
    already fails safe; this only guards against a genuinely unexpected exception
    so no image is silently dropped from the output."""
    row: Dict[str, Any] = {}
    try:
        result = pipeline.build(bgr)
    except Exception as exc:  # pragma: no cover - defensive, must not drop a row
        row["pipeline_outcome"] = "exception"
        row["failure_reason"] = f"{type(exc).__name__}: {exc}"
        return row

    if result.reason:
        row["pipeline_outcome"] = "acquisition_failed"
        row["failure_reason"] = result.reason
        row["pose_source"] = "acquisition_failed"
        row["pose_accepted"] = False
        row["pose_confidence"] = _finite(result.confidence)
        return row

    row["pipeline_outcome"] = "accepted" if result.accepted else "rejected"
    row["failure_reason"] = ""
    row["pose_source"] = "minute_track_first"
    row["pose_accepted"] = bool(result.accepted)
    row["pose_confidence"] = _finite(result.confidence)
    row["tilt_deg"] = _finite(result.tilt_deg)
    row["dial_radius_px"] = _finite(result.dial_radius_px)
    row["projective_limit"] = _finite(result.projective_limit)
    row["solved_roll_deg"] = _finite(result.solved_roll)
    row["final_top_error_deg"] = _finite(result.final_top_error_deg)
    row["final_top_accepted"] = bool(result.final_top_accepted)
    row["automatic_accepted"] = bool(result.automatic_accepted)
    row["reprojection_error_px"] = _finite(result.reproj)
    row["center_displacement_frac"] = _finite(result.center_err)
    row["identity_required"] = bool(result.identity_required)
    row["vetoed_by_identity"] = bool(result.vetoed)

    acq = result.acquisition
    if acq is not None:
        e = acq.dial_ellipse
        if e is not None:
            row["ellipse_cx"] = _finite(e.cx)
            row["ellipse_cy"] = _finite(e.cy)
            row["ellipse_w"] = _finite(e.w)
            row["ellipse_h"] = _finite(e.h)
            row["ellipse_angle_deg"] = _finite(e.angle_deg)
        row["candidate_count"] = acq.candidate_count
        row["acquisition_roll_deg"] = _finite(acq.roll_deg)
        row["acquisition_fit_median_px"] = _finite(acq.fit_median_px)
        row["top_phase_error_deg"] = _finite(acq.top_phase_error_deg)
        row["top_phase_accepted"] = bool(acq.top_phase_accepted)
        row["boundary_confirmed"] = bool(acq.boundary_confirmed)
        row["outer_boundary_radius"] = _finite(acq.outer_boundary_radius)
        row["outer_boundary_median_px"] = _finite(acq.outer_boundary_median_px)
        row["outer_boundary_p90_px"] = _finite(acq.outer_boundary_p90_px)
        row["acquisition_usable"] = bool(acq.usable)

    fine = result.fine
    if fine is not None:
        row["fine_rotation_delta_deg"] = _finite(fine.delta_deg)
        row["fine_fit_median_px"] = _finite(fine.fit_median_px)
        row["fine_fit_ticks"] = fine.fit_ticks

    val = result.validation
    if val is not None:
        row["holdout_accepted"] = bool(val.accepted)
        row["holdout_median_px"] = _finite(val.median_px)
        row["holdout_p90_px"] = _finite(val.p90_px)
        row["holdout_inlier_fraction"] = _finite(val.inlier_fraction)
        row["holdout_ticks"] = val.holdout_ticks
        row["holdout_median_limit_px"] = _finite(val.median_limit_px)
        row["holdout_p90_limit_px"] = _finite(val.p90_limit_px)
        row["holdout_inlier_limit_px"] = _finite(val.inlier_limit_px)

    ref = result.refinement
    if ref is not None:
        row["refinement_accepted"] = bool(ref.accepted)
        row["refinement_fit_before"] = _finite(ref.fit_before)
        row["refinement_fit_after"] = _finite(ref.fit_after)
        row["refinement_evaluated_fit_after"] = _finite(ref.evaluated_fit_after)
        row["refinement_holdout_before"] = _finite(ref.holdout_before)
        row["refinement_holdout_after"] = _finite(ref.holdout_after)
        row["refinement_evaluated_holdout_after"] = _finite(ref.evaluated_holdout_after)
        row["refinement_h31"] = _finite(_normalized_h_term(ref.evaluated_homography, 2, 0))
        row["refinement_h32"] = _finite(_normalized_h_term(ref.evaluated_homography, 2, 1))

    identity = result.identity
    if identity is not None:
        row["identity_verdict"] = identity.verdict.value
        row["identity_dial_interior_median"] = _finite(identity.dial_interior_median)
        row["identity_interior_edge_fraction"] = _finite(identity.interior_edge_fraction)
        row["identity_markers_found"] = identity.markers_found
    else:
        row["identity_verdict"] = "not_evaluated" if not result.identity_required else "unknown"

    markers = result.markers
    for hour in MARKER_HOURS:
        prefix = f"marker_{hour}"
        d = markers[hour] if markers is not None else None
        if d is None:
            row[f"{prefix}_measured"] = False
            continue
        row[f"{prefix}_measured"] = bool(d.measured)
        if d.measured:
            row[f"{prefix}_angular_deg"] = _finite(d.angular_deg)
            row[f"{prefix}_radial_pct_r"] = _finite(d.radial_pct_r)
            row[f"{prefix}_body_rotation_deg"] = _finite(d.body_rotation_deg)
            if hour == 12:
                row[f"{prefix}_triangle_outward_delta_pct_r"] = _finite(
                    d.triangle_outward_delta_pct_r
                )
    return row


PER_IMAGE_PROVENANCE_FIELDS = [
    "source_id", "class_label", "provenance", "split", "factory",
    "bracelet", "physical_watch_id", "source_url", "image_url",
    "local_path", "width", "height", "sha256", "dhash", "possible_duplicate_of",
    "qc_notes",
]


def build_column_order(rows: List[Dict[str, Any]]) -> List[str]:
    seen: List[str] = list(PER_IMAGE_PROVENANCE_FIELDS)
    seen_set = set(seen)
    fixed_tail = [
        "pipeline_outcome", "failure_reason", "pose_source", "pose_accepted",
        "pose_confidence", "tilt_deg", "dial_radius_px", "projective_limit",
        "solved_roll_deg", "final_top_error_deg", "final_top_accepted",
        "automatic_accepted", "reprojection_error_px", "center_displacement_frac",
        "identity_required", "vetoed_by_identity",
        "ellipse_cx", "ellipse_cy", "ellipse_w", "ellipse_h", "ellipse_angle_deg",
        "candidate_count", "acquisition_roll_deg", "acquisition_fit_median_px",
        "top_phase_error_deg", "top_phase_accepted", "boundary_confirmed",
        "outer_boundary_radius", "outer_boundary_median_px", "outer_boundary_p90_px",
        "acquisition_usable",
        "fine_rotation_delta_deg", "fine_fit_median_px", "fine_fit_ticks",
        "holdout_accepted", "holdout_median_px", "holdout_p90_px",
        "holdout_inlier_fraction", "holdout_ticks", "holdout_median_limit_px",
        "holdout_p90_limit_px", "holdout_inlier_limit_px",
        "refinement_accepted", "refinement_fit_before", "refinement_fit_after",
        "refinement_evaluated_fit_after", "refinement_holdout_before",
        "refinement_holdout_after", "refinement_evaluated_holdout_after",
        "refinement_h31", "refinement_h32",
        "identity_verdict", "identity_dial_interior_median",
        "identity_interior_edge_fraction", "identity_markers_found",
    ]
    for hour in MARKER_HOURS:
        prefix = f"marker_{hour}"
        fixed_tail += [f"{prefix}_measured", f"{prefix}_angular_deg", f"{prefix}_radial_pct_r",
                       f"{prefix}_body_rotation_deg"]
        if hour == 12:
            fixed_tail.append(f"{prefix}_triangle_outward_delta_pct_r")
    for col in fixed_tail:
        if col not in seen_set:
            seen.append(col)
            seen_set.add(col)
    # Catch any field a row produced that wasn't anticipated above -- never drop data.
    for row in rows:
        for key in row.keys():
            if key not in seen_set:
                seen.append(key)
                seen_set.add(key)
    return seen


NUMERIC_HINT_SUFFIXES = (
    "_deg", "_px", "_frac", "confidence", "_fraction", "_pct_r", "_median",
    "radius_px", "candidate_count", "_ticks", "_limit_px", "cx", "cy", "_w",
    "_h", "h31", "h32",
)


def _is_numeric_column(col: str, rows: List[Dict[str, Any]]) -> bool:
    for row in rows:
        v = row.get(col)
        if v is None or v == "":
            continue
        if isinstance(v, bool):
            return False
        if isinstance(v, (int, float)):
            return True
        return False
    return False


def _mad(values: List[float], med: float) -> float:
    return statistics.median([abs(v - med) for v in values]) if values else math.nan


def aggregate_per_watch(rows: List[Dict[str, Any]], columns: List[str]) -> List[Dict[str, Any]]:
    by_watch: Dict[str, List[Dict[str, Any]]] = {}
    for row in rows:
        by_watch.setdefault(row["physical_watch_id"], []).append(row)

    numeric_cols = [c for c in columns if c not in PER_IMAGE_PROVENANCE_FIELDS
                    and _is_numeric_column(c, rows)]

    summaries = []
    for watch_id, watch_rows in sorted(by_watch.items()):
        first = watch_rows[0]
        summary: Dict[str, Any] = {
            "physical_watch_id": watch_id,
            "class_label": first.get("class_label", ""),
            "provenance": first.get("provenance", ""),
            "split": first.get("split", ""),
            "factory": first.get("factory", ""),
            "source_ids": ";".join(sorted({r.get("source_id", "") for r in watch_rows})),
            "n_images_total": len(watch_rows),
        }
        accepted = [r for r in watch_rows if r.get("pose_accepted") is True]
        summary["n_images_pose_accepted"] = len(accepted)
        summary["acquisition_success_rate"] = len(accepted) / len(watch_rows) if watch_rows else math.nan

        for col in numeric_cols:
            values = [float(r[col]) for r in watch_rows if r.get(col) not in (None, "")]
            n = len(values)
            summary[f"{col}__n"] = n
            if n == 0:
                summary[f"{col}__median"] = ""
                summary[f"{col}__min"] = ""
                summary[f"{col}__max"] = ""
                summary[f"{col}__mad"] = ""
                continue
            med = statistics.median(values)
            summary[f"{col}__median"] = med
            summary[f"{col}__min"] = min(values)
            summary[f"{col}__max"] = max(values)
            summary[f"{col}__mad"] = _mad(values, med)
        summaries.append(summary)
    return summaries, numeric_cols


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--resolved-csv", required=True, type=Path)
    parser.add_argument("--images-root", type=Path, default=None,
                         help="Base directory local_path is relative to (default: resolved-csv's parent)")
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--manifest-path", type=Path, default=None)
    parser.add_argument("--workflow-run-id", default="local")
    parser.add_argument("--sources-attempted", type=int, default=None)
    parser.add_argument("--sources-populated", type=int, default=None)
    parser.add_argument("--failed-source-ids", default="")
    args = parser.parse_args()

    images_root = args.images_root or args.resolved_csv.parent
    args.out_dir.mkdir(parents=True, exist_ok=True)

    with args.resolved_csv.open(newline="", encoding="utf-8") as handle:
        source_rows = list(csv.DictReader(handle))

    per_image_rows: List[Dict[str, Any]] = []
    for i, src_row in enumerate(source_rows, start=1):
        local_path = images_root / src_row["local_path"]
        print(f"[{i}/{len(source_rows)}] {src_row['source_id']} {src_row['local_path']}")
        row: Dict[str, Any] = {k: src_row.get(k, "") for k in PER_IMAGE_PROVENANCE_FIELDS}
        bgr = cv2.imread(str(local_path), cv2.IMREAD_COLOR)
        if bgr is None:
            row["pipeline_outcome"] = "decode_failed"
            row["failure_reason"] = f"cv2.imread returned None for {local_path}"
        else:
            bgr = resize_to_max_dim(bgr, 1600)
            row.update(analyze_one_image(bgr))
        per_image_rows.append(row)

    columns = build_column_order(per_image_rows)
    per_image_path = args.out_dir / "per_image_measurements.csv"
    with per_image_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns, restval="")
        writer.writeheader()
        for row in per_image_rows:
            writer.writerow({k: ("" if v is None else v) for k, v in row.items()})

    per_watch_rows, numeric_cols = aggregate_per_watch(per_image_rows, columns)
    watch_columns: List[str] = [
        "physical_watch_id", "class_label", "provenance", "split", "factory",
        "source_ids", "n_images_total", "n_images_pose_accepted", "acquisition_success_rate",
    ]
    for col in numeric_cols:
        watch_columns += [f"{col}__n", f"{col}__median", f"{col}__min", f"{col}__max", f"{col}__mad"]
    per_watch_path = args.out_dir / "per_watch_summary.csv"
    with per_watch_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=watch_columns, restval="")
        writer.writeheader()
        writer.writerows(per_watch_rows)

    repo_root = Path(__file__).resolve().parents[2]
    metadata = {
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "python_engine_git_commit": _git_commit_sha(repo_root),
        "python_version": platform.python_version(),
        "opencv_version": cv2.__version__,
        "numpy_version": np.__version__,
        "github_actions_workflow_run_id": args.workflow_run_id,
        "manifest_path": str(args.manifest_path) if args.manifest_path else None,
        "manifest_sha256": _sha256_file(args.manifest_path) if args.manifest_path and args.manifest_path.exists() else None,
        "resolved_images_sha256": _sha256_file(args.resolved_csv),
        "sources_attempted": args.sources_attempted,
        "sources_populated": args.sources_populated,
        "images_analyzed": len(per_image_rows),
        "independent_watches_analyzed": len({r["physical_watch_id"] for r in per_image_rows}),
        "failed_source_ids": [s for s in args.failed_source_ids.split(",") if s],
    }
    with (args.out_dir / "run_metadata.json").open("w", encoding="utf-8") as handle:
        json.dump(metadata, handle, indent=2)

    n_accepted = sum(1 for r in per_image_rows if r.get("pose_accepted") is True)
    print(f"\nanalyzed images: {len(per_image_rows)}")
    print(f"pose accepted: {n_accepted}/{len(per_image_rows)}")
    print(f"independent watches: {metadata['independent_watches_analyzed']}")
    print(f"wrote {per_image_path}")
    print(f"wrote {per_watch_path}")
    print(f"wrote {args.out_dir / 'run_metadata.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
