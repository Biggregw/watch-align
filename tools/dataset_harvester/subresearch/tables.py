"""Long-format research tables from SubMeasure JSON lines.

sub_images.csv            one row per (photo, variant): identity + raw dial / pose / rehaut diagnostics
sub_landmarks.csv         one row per (photo, variant, landmark): raw landmark geometry and fit diagnostics
sub_landmark_stability.csv one row per (photo, landmark) and (photo, "dial"): min / max / spread over the
                          variants that were run. Spreads only; no stable/unstable decision.

No column may carry a verdict (CLEAR/CHECK/STRONG), a pose label (GOOD/CORRECTABLE/RETAKE), an
authenticity decision or a GMT-calibrated pass/fail flag: FORBIDDEN_COLUMN_TOKENS is checked by the
tests and by write_tables().
"""
from __future__ import annotations

import csv
import math
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

ID_COLS = ["family", "model", "class_tag", "physical_watch_id", "partition", "research_state", "sha256",
           "source_id", "image_index", "variant"]

IMAGE_COLS = ID_COLS + [
    "driver", "priors", "error", "analysis_path", "orig_w", "orig_h", "working_scale", "working_origin_x", "working_origin_y",
    "preview_dial_r", "t_a", "t_b", "t_c", "t_d", "t_e", "t_f",
    "seed_valid", "dial_reason", "seed_cx", "seed_cy", "seed_r", "seed_quality", "seed_boundary_strength",
    "seed_boundary_coverage", "seed_marker_hits",
    "edge_fit_valid", "fit_cx", "fit_cy", "fit_a", "fit_b", "fit_angle_deg", "fit_axis_ratio", "fit_rms_px", "fit_inliers",
    "fit_rays", "seed_to_fit_px", "seed_to_fit_over_r",
    "dial_found", "dial_source", "dial_cx", "dial_cy", "dial_r", "dial_r_analysis_px", "dial_complete",
    "orientation_source", "twelve_clock_deg_analysis", "round_found", "landmarks_detected",
    "mpose_markers", "mpose_ratio", "mpose_tilt_deg", "mpose_residual_over_r", "mpose_scale_over_r",
    "rehaut_roll_source", "rh_global_valid", "rh_global_top_px", "rh_global_bottom_px", "rh_global_left_px", "rh_global_right_px",
    "rh_global_mean_px", "rh_harmonic", "rh_widest_clock_deg", "rh_min_over_mean", "rh_edge_coverage", "rh_fit_residual",
    "rh_sector_source", "rh_w12_px", "rh_w3_px", "rh_w6_px", "rh_w9_px", "rh_cov12", "rh_cov3", "rh_cov6", "rh_cov9",
    "ell_valid", "ell_ratio", "ell_tilt_deg", "ell_minor_clock_deg", "ell_centre_err_over_r", "ell_major_over_r",
    "sharpness", "crushed_fraction", "blown_fraction", "glare_fraction", "mean_luma",
]

LANDMARK_COLS = ID_COLS + [
    "landmark", "kind", "hour", "detected", "reason", "fit_path", "primitive_path",
    "x", "y", "rho_r", "theta_from12_deg", "dtheta_from_nominal_deg", "theta_reference",
    "rotation_deg", "base_edge_rot_deg", "width_px", "length_px", "width_over_r", "length_over_r",
    "radius_px", "radius_over_r", "surround_radius_over_r", "inner_ring_over_r", "rings_found",
    "apex_deg", "squareness_deg", "gap_raw", "gap_over_r", "inset", "centring_raw", "centring_vs_centre_tick",
    "side_spacing_left", "side_spacing_right", "long_side_parallel_deg",
    "tick_pitch_deg", "tick_score", "ticks_inferred", "tick_chord_px", "axis_ref_disagreement_deg",
    "edge_contrast", "reject_fraction", "angle_from_seed_deg", "seed_placed_by_affine", "seed_x", "seed_y",
    "primitive_gap_over_width", "primitive_axis_rot_deg", "primitive_width_px",
    "p_left_x", "p_left_y", "p_right_x", "p_right_y", "p_tip_x", "p_tip_y",
    "p_outer_left_x", "p_outer_left_y", "p_outer_right_x", "p_outer_right_y", "p_inner_left_x", "p_inner_left_y",
    "p_inner_right_x", "p_inner_right_y",
    "tick_before_x", "tick_before_y", "tick_centre_x", "tick_centre_y", "tick_after_x", "tick_after_y",
]

STAB_ID = ["family", "model", "class_tag", "physical_watch_id", "partition", "research_state", "sha256", "source_id", "image_index"]
STAB_METRICS = ["rho_r", "dtheta_from_nominal_deg", "rotation_deg", "width_over_r", "length_over_r", "gap_raw", "inset", "apex_deg"]
STABILITY_COLS = STAB_ID + ["landmark", "kind", "variants_run", "variants_detected", "detected_in_orig", "variants_missing",
                            "fit_paths", "centre_spread_over_r"] + \
    [f"{m}_{s}" for m in STAB_METRICS for s in ("min", "max", "spread")] + \
    ["dial_radius_rel_spread", "dial_axis_ratio_min", "dial_axis_ratio_max"]

FORBIDDEN_COLUMN_TOKENS = ("attention", "verdict", "clear", "check", "strong", "pose_label", "retake", "correctable",
                           "good", "authentic", "genuine", "replica", "classif", "pass", "fail", "stable", "unassessable",
                           "no_readable", "att")


def forbidden_columns(cols) -> list[str]:
    bad = []
    for c in cols:
        parts = c.lower().split("_")
        if any(t in parts or (len(t) > 4 and t in c.lower()) for t in FORBIDDEN_COLUMN_TOKENS):
            bad.append(c)
    return bad


def finite(v) -> bool:
    try:
        return v is not None and v != "" and math.isfinite(float(v))
    except (TypeError, ValueError):
        return False


def wrap180(d: float) -> float:
    d = (d + 180.0) % 360.0 - 180.0
    return 180.0 if d == -180.0 else d


def ident(rec: dict, meta: dict) -> dict:
    return {"family": meta.get("family", ""), "model": meta.get("model", rec.get("model", "")), "class_tag": meta.get("class_label", ""),
            "physical_watch_id": meta.get("physical_watch_id", ""), "partition": meta.get("partition", ""),
            "research_state": meta.get("research_state", ""), "sha256": rec.get("sha256", ""), "source_id": meta.get("candidate_id", ""),
            "image_index": meta.get("image_index", ""), "variant": rec.get("variant", "")}


def image_rows(records: list[dict], meta_by_sha: dict, pixel_by_sha: dict | None = None) -> list[dict]:
    out = []
    for rec in records:
        row = ident(rec, meta_by_sha.get(rec.get("sha256", ""), {}))
        for k in IMAGE_COLS:
            if k not in row and k in rec:
                row[k] = rec[k]
        if pixel_by_sha and rec.get("variant") == "orig":
            row.update(pixel_by_sha.get(rec.get("sha256", ""), {}))
        out.append(row)
    return out


def landmark_rows(records: list[dict], meta_by_sha: dict) -> list[dict]:
    out = []
    for rec in records:
        base = ident(rec, meta_by_sha.get(rec.get("sha256", ""), {}))
        for lm in rec.get("landmarks") or []:
            row = dict(base)
            for k in LANDMARK_COLS:
                if k not in row and k in lm:
                    v = lm[k]
                    row[k] = int(v) if k == "hour" and finite(v) else v
            out.append(row)
    return out


def _spread(values: list[float], circular: bool = False) -> tuple:
    v = [float(x) for x in values if finite(x)]
    if not v:
        return (math.nan, math.nan, math.nan)
    if circular:
        ref = float(np.median(v))
        v = [ref + wrap180(x - ref) for x in v]
    return (min(v), max(v), max(v) - min(v))


def stability_rows(img_rows: list[dict], lm_rows: list[dict]) -> list[dict]:
    by_img = defaultdict(list)
    for r in img_rows:
        by_img[r["sha256"]].append(r)
    by_lm = defaultdict(list)
    for r in lm_rows:
        by_lm[(r["sha256"], r["landmark"])].append(r)
    out = []
    for sha, recs in sorted(by_img.items()):
        run = [r for r in recs if not r.get("error")]
        variants = sorted({r["variant"] for r in run})
        orig = next((r for r in run if r["variant"] == "orig"), None)
        if orig is None or len(variants) < 2:
            continue
        base_r = float(orig["dial_r"]) if orig.get("dial_found") and finite(orig.get("dial_r")) else math.nan
        idrow = {k: orig.get(k, "") for k in STAB_ID}
        # Dial.
        found = [r for r in run if r.get("dial_found") is True and finite(r.get("dial_cx"))]
        d = dict(idrow, landmark="dial", kind="dial", variants_run=len(variants), variants_detected=len(found),
                 detected_in_orig=bool(orig.get("dial_found")),
                 variants_missing=";".join(sorted(set(variants) - {r["variant"] for r in found})),
                 fit_paths=";".join(sorted({str(r.get("dial_source", "")) for r in found})))
        if found and finite(base_r):
            pts = np.array([[float(r["dial_cx"]), float(r["dial_cy"])] for r in found])
            med = np.median(pts, axis=0)
            d["centre_spread_over_r"] = float(np.max(np.hypot(*(pts - med).T)) / base_r)
            rs = [float(r["dial_r"]) for r in found if finite(r.get("dial_r"))]
            d["dial_radius_rel_spread"] = (max(rs) - min(rs)) / base_r if rs else math.nan
            ar = [float(r["fit_axis_ratio"]) for r in found if finite(r.get("fit_axis_ratio"))]
            if ar:
                d["dial_axis_ratio_min"], d["dial_axis_ratio_max"] = min(ar), max(ar)
        out.append(d)
        # Landmarks.
        names = sorted({lm for (s, lm) in by_lm if s == sha})
        for name in names:
            rows = [r for r in by_lm[(sha, name)] if r["variant"] in variants]
            det = [r for r in rows if r.get("detected") is True]
            o = next((r for r in rows if r["variant"] == "orig"), {})
            s = dict(idrow, landmark=name, kind=(rows[0].get("kind") if rows else ""), variants_run=len(variants),
                     variants_detected=len(det), detected_in_orig=o.get("detected") is True,
                     variants_missing=";".join(sorted(set(variants) - {r["variant"] for r in det})),
                     fit_paths=";".join(sorted({str(r.get("fit_path", "")) for r in det})))
            if det and finite(base_r):
                pts = np.array([[float(r["x"]), float(r["y"])] for r in det if finite(r.get("x"))])
                if len(pts):
                    med = np.median(pts, axis=0)
                    s["centre_spread_over_r"] = float(np.max(np.hypot(*(pts - med).T)) / base_r)
            for m in STAB_METRICS:
                lo, hi, sp = _spread([r.get(m) for r in det], circular=m in ("dtheta_from_nominal_deg",))
                s[f"{m}_min"], s[f"{m}_max"], s[f"{m}_spread"] = lo, hi, sp
            out.append(s)
    return out


def _clean(v):
    if isinstance(v, float) and not math.isfinite(v):
        return ""
    return v


def write(path: Path, rows: list[dict], cols: list[str]) -> None:
    bad = forbidden_columns(cols)
    if bad:
        raise ValueError(f"verdict-like columns are not allowed in research tables: {bad}")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=cols, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: _clean(r.get(k, "")) for k in cols})


def q(values, p) -> float:
    v = [float(x) for x in values if finite(x)]
    return float(np.percentile(v, p)) if v else math.nan


def summary(watch_rows: list[dict], dataset_rows: list[dict], img_rows: list[dict], lm_rows: list[dict],
            stab_rows: list[dict], acquisition: dict | None = None) -> dict:
    """Counts, detector coverage (orig variant, ACCEPTED photos) and descriptive stability.
    Descriptive only: no threshold, verdict or class decision is derived here."""
    acc_watch = [w for w in watch_rows if w["research_state"] == "ACCEPT"]
    orig = [r for r in img_rows if r["variant"] == "orig"]
    lm_orig = [r for r in lm_rows if r["variant"] == "orig"]

    def rate(rows, pred):
        return {"n": len(rows), "k": sum(1 for r in rows if pred(r)), "rate": (sum(1 for r in rows if pred(r)) / len(rows)) if rows else None}

    coverage = {"dial_found": rate(orig, lambda r: r.get("dial_found") is True),
                "dial_edge_fit": rate(orig, lambda r: r.get("edge_fit_valid") is True),
                "analysis_error": rate(orig, lambda r: bool(r.get("error")))}
    with_dial = [r for r in orig if r.get("dial_found") is True]
    dial_sha = {r["sha256"] for r in with_dial}
    per_landmark = {}
    for name in sorted({r["landmark"] for r in lm_orig}):
        rows = [r for r in lm_orig if r["landmark"] == name and r["sha256"] in dial_sha]
        if rows and rows[0].get("kind") != "date":
            per_landmark[name] = rate(rows, lambda r: r.get("detected") is True)
    tri = [r for r in lm_orig if r["landmark"] == "12" and r.get("detected") is True]
    by_group = {}
    for key in ("model", "class_tag"):
        for g in sorted({r[key] for r in with_dial}):
            rs = [r for r in lm_orig if r[key] == g and r["sha256"] in dial_sha]
            by_group[f"{key}={g}"] = {
                "photos_with_dial": len({r["sha256"] for r in rs}),
                "twelve_detected_rate": rate([r for r in rs if r["landmark"] == "12"], lambda r: r.get("detected") is True)["rate"],
                "baton_detected_rate": rate([r for r in rs if r["kind"] == "baton"], lambda r: r.get("detected") is True)["rate"],
                "round_detected_rate": rate([r for r in rs if r["kind"] == "round"], lambda r: r.get("detected") is True)["rate"],
            }
    stab = {}
    groups = {k: [r for r in stab_rows if r["kind"] == k and r.get("detected_in_orig")] for k in ("dial", "triangle", "baton", "round")}
    # The dial seed without an edge fit can lock onto another ring; report the two cases apart.
    groups["dial_edge_fit_in_every_variant"] = [r for r in groups["dial"] if r.get("fit_paths") == "edge_fit"]
    groups["dial_seed_circle_in_some_variant"] = [r for r in groups["dial"] if r.get("fit_paths") != "edge_fit"]
    for kind, rs in groups.items():
        stab[kind] = {"photo_landmarks": len(rs),
                      "lost_in_some_variant": sum(1 for r in rs if r.get("variants_missing")),
                      "centre_spread_over_r_median": q([r.get("centre_spread_over_r") for r in rs], 50),
                      "centre_spread_over_r_p95": q([r.get("centre_spread_over_r") for r in rs], 95),
                      "rotation_spread_deg_median": q([r.get("rotation_deg_spread") for r in rs], 50),
                      "rotation_spread_deg_p95": q([r.get("rotation_deg_spread") for r in rs], 95),
                      "rho_spread_median": q([r.get("rho_r_spread") for r in rs], 50),
                      "rho_spread_p95": q([r.get("rho_r_spread") for r in rs], 95)}
    ds_state = Counter(r["research_state"] for r in dataset_rows)
    return {
        "note": "Research measurements only. No verdicts, pose labels, authenticity decisions or thresholds. "
                "Independence unit: physical_watch_id. Genuine photos describe normal geometry; replicas are stress cases.",
        "acquisition": acquisition or {},
        "images": {"manifest_rows": len(dataset_rows), "unique_sha256": len({r['sha256'] for r in dataset_rows}),
                   "by_research_state": dict(ds_state),
                   "reasons": dict(Counter(r["reason"] for r in dataset_rows if r["research_state"] != "ACCEPT")),
                   "measured_photos": len(orig), "measured_photos_with_dial": len(with_dial),
                   "variant_analyses": len(img_rows)},
        "physical_watches": {
            "total": len(watch_rows), "by_research_state": dict(Counter(w["research_state"] for w in watch_rows)),
            "accepted_by_model_class": dict(Counter(f"{w['model']}/{w['class_label']}" for w in acc_watch)),
            "accepted_by_partition": dict(Counter(w.get("partition", "") for w in acc_watch)),
            "accepted_with_2plus_accepted_photos": sum(1 for w in acc_watch if int(w["images_accepted"]) >= 2),
            "not_accepted_reasons": dict(Counter(w["reason"].split(":")[0] for w in watch_rows if w["research_state"] != "ACCEPT")),
        },
        "detector_coverage_orig": coverage,
        "landmark_detection_orig_given_dial": per_landmark,
        "landmark_detection_by_group": by_group,
        "triangle_fit_paths_orig": dict(Counter(r.get("fit_path", "") for r in tri)),
        "triangle_apex_deg_orig": {"n": len(tri), "median": q([r.get("apex_deg") for r in tri], 50),
                                   "p05": q([r.get("apex_deg") for r in tri], 5), "p95": q([r.get("apex_deg") for r in tri], 95)},
        "stability_descriptive": stab,
    }
