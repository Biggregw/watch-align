#!/usr/bin/env python3
"""Join run_replica_control_set.py's per-control measurements against the versioned
empirical genuine profile and report, per control and per metric, the deviation and
evidence strength -- never a pass/fail verdict.

This is deliberately a separate, auditable step from run_replica_control_set.py: the
raw (blind) measurement must never be re-touched once collected, so any interpretation
happens only here, reading that frozen output. This script does not choose which
control to measure, does not see images, and does not depend on any defect label when
deciding whether/how to compute a comparison -- it only maps predeclared/known metric
names onto whatever the frozen pipeline already produced.

If a control has no measured low-tilt image (fetch blocked, pose rejected, etc.) it is
recorded as EXCLUDED with its reason -- never silently dropped and never backfilled
with an assumed/typical value. A control with n=1 image is not a second independent
watch of anything; each control_id is already one physical watch by the control-set
protocol's own construction.

Evidence classification mirrors (does not reimplement with different numbers)
android/app/src/main/java/com/watchalign/mobile/baseline/DefaultEvidenceClassifier.java:
REJECT-status or a genuine MAD at/under MIN_MAD_FOR_NORMALISATION or missing baseline
caps at WEAK; |mad_multiples| >= 3.0 is STRONG; >= 1.0 is MODERATE; otherwise WEAK. This
script does not invent a different threshold for research use than the one shipped in
the app. DIAGNOSTIC_ONLY-status genuine metrics also cap at WEAK, same as the app.

Direction wording mirrors (does not reimplement with different sign conventions)
android/app/src/main/java/com/watchalign/mobile/baseline/DirectionWording.java: radial
positive=high/negative=low (GmtMarkerQcRepair's own documented convention); tangential/
lateral positive=clockwise, worded right/left only at h12/h06 where that coincides with
screen left/right (verified against marker_consensus_analysis.py's own tangential-sign
docstring and axis_coords.canonical_axis_components's "positive = clockwise from the
axis" docstring); axis/incidence positive=clockwise; span positive=larger.
"""
from __future__ import annotations

import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESULTS_DIR = ROOT / "docs" / "research" / "gmt-replica-control-set-results"
STATUS_CSV = RESULTS_DIR / "control_status.csv"
MEDIANS_CSV = RESULTS_DIR / "per_control_medians.csv"
PROFILE_JSON = ROOT / "docs" / "research" / "gmt-genuine-baseline-profile-v1.json"
OUT_CSV = RESULTS_DIR / "discrimination_analysis.csv"

MIN_MAD_FOR_NORMALISATION = 1e-6
MODERATE_MAD_MULTIPLES = 1.0
STRONG_MAD_MULTIPLES = 3.0

# profile metric key -> (measured column in per_control_medians.csv, mapping justification).
# The Stage-3 (gmt_proportional_features.py) triangle-specific measurement of the 12
# o'clock marker uses different raw feature names than the genuine baseline profile's
# JSON keys; this table is the definitive, one-time reconciliation, justified against
# docs/research/gmt-genuine-baseline-validation-report.md's own prose column names and
# gmt_proportional_features.py's feature-dict construction (read in full to build this
# table -- not guessed). "exact name match" entries need no renaming at all.
PROFILE_TO_MEASURED = {
    "h12.apex_radial": ("h12.stage3_apex_r_simple",
                         "profile key drops gpf.py's '_simple' suffix; the validation report's "
                         "'12 apex radial' row cites no projective variant, unlike centre/base "
                         "below, so the simple (affine-normalised) variant is the intended one"),
    "h12.centre_radial_projective": ("h12.stage3_centre_r_projective",
                                      "both the report ('12 centre radial, projective') and the "
                                      "profile key explicitly name the projective variant"),
    "h12.base_radial_projective": ("h12.stage3_base_r_projective",
                                    "both the report ('12 base radial, projective') and the "
                                    "profile key explicitly name the projective variant"),
    "h12.axis_incidence": ("h12.stage3_axis_incidence_canonical",
                            "profile key drops gpf.py's '_canonical' suffix"),
    "h12.tangential_centroid_offset": ("h12.stage3_centroid_tangential_offset_canonical",
                                        "profile key reorders/drops '_canonical' from "
                                        "centroid_tangential_offset_canonical"),
    "h06.centre_radial": ("h06.centre_r", "profile key expands '_r' to '_radial'"),
    "h06.centre_tangential": ("h06.centre_t", "profile key expands '_t' to '_tangential'"),
    "h06.axis_residual_deg": ("h06.axis_residual_deg", "exact name match"),
    "h06.radial_span": ("h06.radial_span", "exact name match"),
    "h09.centre_radial": ("h09.centre_r", "profile key expands '_r' to '_radial'"),
    "h09.centre_tangential": ("h09.centre_t", "profile key expands '_t' to '_tangential'"),
    "h09.axis_residual_deg": ("h09.axis_residual_deg", "exact name match"),
    "h09.radial_span": ("h09.radial_span", "exact name match"),
}

# Measured Stage-3 columns with NO genuine-population baseline yet, but directly relevant
# to interpreting a predeclared rotation/orientation defect label -- reported as raw
# observations only, never given a fabricated baseline or deviation.
NO_BASELINE_METRICS = {
    "h12.stage3_symmetry_axis_angular_deviation_deg": (
        "The literal rotation angle (degrees) of the 12 triangle's own apex-to-base axis "
        "relative to the expected pure-radial direction -- the closest true ROTATION-ANGLE "
        "analog to a predeclared 'h12.axis_residual_deg' label, which does not exist as a raw "
        "measurement for a triangle marker (axis_residual_deg is only computed for the "
        "baton-shaped h06/h09 markers via marker_consensus_analysis.principal_axis_residual_deg). "
        "No genuine-population baseline exists for this exact Stage-3 metric yet."),
}


def direction_word(profile_key: str, signed_deviation: float) -> str:
    if signed_deviation == 0.0:
        return "matching"
    positive = signed_deviation > 0.0
    marker, _, metric = profile_key.partition(".")
    if "span" in metric:
        return "larger" if positive else "smaller"
    if "radial" in metric:
        return "high" if positive else "low"
    if "tangential" in metric or "lateral" in metric:
        if marker == "h12":
            return "right" if positive else "left"
        if marker == "h06":
            return "left" if positive else "right"
        return "clockwise" if positive else "counter-clockwise"
    if "axis" in metric or "incidence" in metric:
        return "clockwise" if positive else "counter-clockwise"
    return "above" if positive else "below"


def load_profile_metrics() -> dict:
    data = json.loads(PROFILE_JSON.read_text(encoding="utf-8"))
    return {m["key"]: m for m in data["metrics"]}


def classify(mad_multiples, status: str) -> str:
    if status == "REJECT":
        return "NONE"
    if mad_multiples is None:
        return "WEAK"
    if status == "DIAGNOSTIC_ONLY":
        return "WEAK"
    magnitude = abs(mad_multiples)
    if magnitude >= STRONG_MAD_MULTIPLES:
        return "STRONG"
    if magnitude >= MODERATE_MAD_MULTIPLES:
        return "MODERATE"
    return "WEAK"


def exclusion_reason(row: dict) -> str:
    if int(row.get("low_tilt_images", 0) or 0) > 0:
        return ""
    page_status = row.get("page_status", "")
    if "returncode" in page_status and "gallery" in page_status:
        return (f"fetch_blocked: gallery-dl returned {page_status!r}. This project has "
                f"previously confirmed (2026-09-23) that Reddit's own anti-bot 'network "
                f"security' block reproduces identically from the GitHub Actions runner and "
                f"this session's own sandboxed network path; no Reddit API credentials are "
                f"available for an authenticated alternative.")
    if int(row.get("measured_images", 0) or 0) == 0:
        return f"no_image_measured: page_status={page_status}"
    return (f"no_low_tilt_image: {row.get('measured_images')} measured but none <=10deg "
            f"(page_status={page_status})")


def main() -> int:
    if not STATUS_CSV.exists():
        print(f"missing {STATUS_CSV}; run run_replica_control_set.py first")
        return 1
    with STATUS_CSV.open(newline="", encoding="utf-8") as f:
        controls = list(csv.DictReader(f))
    medians = []
    if MEDIANS_CSV.exists() and MEDIANS_CSV.stat().st_size > 0:
        with MEDIANS_CSV.open(newline="", encoding="utf-8") as f:
            medians = list(csv.DictReader(f))
    medians_by_id = {r["control_id"]: r for r in medians}
    profile_metrics = load_profile_metrics()

    out_rows = []
    n_evaluated_controls = 0
    for c in controls:
        cid = c["control_id"]
        median_row = medians_by_id.get(cid)
        if median_row is None:
            out_rows.append({
                "control_id": cid, "human_label": c.get("human_label", ""),
                "label_strength": c.get("label_strength", ""), "profile_metric": "",
                "measured_column": "", "mapping_note": "", "evaluable": "false",
                "exclusion_reason": exclusion_reason(c), "observed_value": "",
                "genuine_median": "", "genuine_mad": "", "genuine_p10": "", "genuine_p90": "",
                "genuine_status": "", "genuine_n_watches": "", "signed_deviation": "",
                "mad_multiples": "", "evidence_strength": "", "direction": "",
                "pose_confidence": "", "tilt_deg": "",
                "research_identity_bypass_used": c.get("research_identity_bypass_used", ""),
            })
            continue

        n_evaluated_controls += 1
        pose_confidence = median_row.get("pose_confidence", "")
        tilt_deg = median_row.get("tilt_deg", "")
        bypass_used = median_row.get("research_identity_bypass_used", "")

        # One row per genuine-profile metric (13), whether or not this control's image
        # actually produced a value for it -- an absence is reported, never omitted.
        for profile_key, baseline in sorted(profile_metrics.items()):
            measured_col, mapping_note = PROFILE_TO_MEASURED.get(
                profile_key, (profile_key, "exact key lookup (no renaming needed)"))
            observed = median_row.get(measured_col)
            row = {
                "control_id": cid, "human_label": c.get("human_label", ""),
                "label_strength": c.get("label_strength", ""), "profile_metric": profile_key,
                "measured_column": measured_col, "mapping_note": mapping_note,
                "genuine_median": baseline["median"], "genuine_mad": baseline["mad"],
                "genuine_p10": baseline["p10"], "genuine_p90": baseline["p90"],
                "genuine_status": baseline["status"], "genuine_n_watches": baseline["n_watches"],
                "pose_confidence": pose_confidence, "tilt_deg": tilt_deg,
                "research_identity_bypass_used": bypass_used,
            }
            if observed in (None, ""):
                row.update({
                    "evaluable": "false",
                    "exclusion_reason": f"not computed for this image (column {measured_col} "
                                         f"absent/non-finite -- e.g. round-marker corridor or "
                                         f"projective fit unavailable for this photo)",
                    "observed_value": "", "signed_deviation": "", "mad_multiples": "",
                    "evidence_strength": "", "direction": "",
                })
            else:
                observed_value = float(observed)
                median = baseline["median"]
                mad = baseline["mad"]
                signed_deviation = observed_value - median
                mad_multiples = signed_deviation / mad if mad > MIN_MAD_FOR_NORMALISATION else None
                row.update({
                    "evaluable": "true", "exclusion_reason": "",
                    "observed_value": f"{observed_value:.6g}",
                    "signed_deviation": f"{signed_deviation:.6g}",
                    "mad_multiples": "" if mad_multiples is None else f"{mad_multiples:.4g}",
                    "evidence_strength": classify(mad_multiples, baseline["status"]),
                    "direction": direction_word(profile_key, signed_deviation),
                })
            out_rows.append(row)

        # Raw observations with no genuine baseline yet -- reported for context, never
        # given a fabricated comparison.
        for measured_col, note in sorted(NO_BASELINE_METRICS.items()):
            observed = median_row.get(measured_col)
            row = {
                "control_id": cid, "human_label": c.get("human_label", ""),
                "label_strength": c.get("label_strength", ""), "profile_metric": "",
                "measured_column": measured_col, "mapping_note": note,
                "genuine_median": "", "genuine_mad": "", "genuine_p10": "", "genuine_p90": "",
                "genuine_status": "NO_BASELINE", "genuine_n_watches": "",
                "pose_confidence": pose_confidence, "tilt_deg": tilt_deg,
                "research_identity_bypass_used": bypass_used,
                "signed_deviation": "", "mad_multiples": "", "evidence_strength": "insufficient_evidence",
            }
            if observed in (None, ""):
                row["evaluable"] = "false"
                row["exclusion_reason"] = f"not computed for this image (column {measured_col} absent)"
                row["observed_value"] = ""
                row["direction"] = ""
            else:
                observed_value = float(observed)
                row["evaluable"] = "true"
                row["exclusion_reason"] = "no genuine baseline available for this metric yet"
                row["observed_value"] = f"{observed_value:.6g}"
                # Sign-only direction: positive = clockwise, by construction of gpf.py's
                # canonical (t_radial, t_tangential) frame (axis_coords.py: "positive =
                # clockwise from the axis") -- same convention as the tangential family.
                row["direction"] = ("matching" if observed_value == 0.0
                                     else ("clockwise" if observed_value > 0.0 else "counter-clockwise"))
            out_rows.append(row)

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    with OUT_CSV.open("w", newline="", encoding="utf-8") as f:
        fieldnames = ["control_id", "human_label", "label_strength", "profile_metric",
                      "measured_column", "mapping_note", "evaluable", "exclusion_reason",
                      "observed_value", "genuine_median", "genuine_mad", "genuine_p10",
                      "genuine_p90", "genuine_status", "genuine_n_watches", "signed_deviation",
                      "mad_multiples", "evidence_strength", "direction", "pose_confidence",
                      "tilt_deg", "research_identity_bypass_used"]
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        w.writerows(out_rows)

    n_evaluable_rows = sum(1 for r in out_rows if r["evaluable"] == "true")
    print(f"controls with a measured image: {n_evaluated_controls} / {len(controls)}")
    print(f"metric-rows written: {len(out_rows)}, evaluable (observed + baseline present): {n_evaluable_rows}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
