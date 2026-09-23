#!/usr/bin/env python3
"""Join run_replica_control_set.py's per-control measurements against the versioned
empirical genuine profile and classify, per control and per metric, whether the
known-defect label was distinguishable from genuine variation.

This is deliberately a separate, auditable step from run_replica_control_set.py:
the raw (blind) measurement must never be re-touched once collected, so any
judgment about discrimination happens only here, reading that frozen output.

If a control has no measured low-tilt image (fetch blocked, pose rejected, etc.)
it is recorded as EXCLUDED with its reason -- never silently dropped and never
backfilled with an assumed/typical value. A control with n=1 image is not a
second independent watch of anything; each control_id is already one physical
watch by the control-set protocol's own construction (see
docs/research/gmt-replica-control-set-protocol.md).

Evidence classification mirrors (does not reimplement with different numbers)
android/app/src/main/java/com/watchalign/mobile/baseline/DefaultEvidenceClassifier.java:
REJECT-status or a genuine MAD at/under MIN_MAD_FOR_NORMALISATION or missing
baseline caps at WEAK; |mad_multiples| >= 3.0 is STRONG; >= 1.0 is MODERATE;
otherwise WEAK. This script does not invent a different threshold for research
use than the one shipped in the app.
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

# The replica control CSV's predeclared expected_primary_metric values use the
# generic per-marker naming (e.g. "h12.centre_r"/"h12.centre_t"); the genuine
# profile's 12-marker metrics use Stage-3-specific triangle naming (e.g.
# "h12.apex_radial", "h12.tangential_centroid_offset") because Part 3's
# per-marker treatment and Stage 3's triangle-specific treatment of hour 12
# are two different measurements, not two names for the same number (see
# ALL_MARKER_HOURS = ROUND_HOURS + [6, 9, 12] in marker_consensus.py). Mapping
# a generic 12-marker expectation onto a Stage-3 metric is a "closest analog"
# substitution, not an exact match, and is always reported as such.
GENERIC_TO_PROFILE_CLOSEST_ANALOG = {
    "h12.centre_r": ("h12.centre_radial_projective", "closest analog, not identical measurement"),
    "h12.centre_t": ("h12.tangential_centroid_offset", "closest analog, not identical measurement"),
    "h06.centre_r": ("h06.centre_radial", "exact metric name match"),
    "h06.centre_t": ("h06.centre_tangential", "exact metric name match"),
    "h09.centre_r": ("h09.centre_radial", "exact metric name match"),
    "h09.centre_t": ("h09.centre_tangential", "exact metric name match"),
}


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
    if "returncode_4" in page_status:
        return "fetch_blocked: gallery-dl reddit extractor returned " \
               "'You've been blocked by network security' (Reddit anti-bot block on the " \
               "fetching network -- reproduced identically from the GitHub Actions runner " \
               "and this session's own sandboxed network path; no Reddit API credentials " \
               "available to attempt an authenticated alternative)"
    if int(row.get("measured_images", 0) or 0) == 0:
        return f"no_image_measured: page_status={page_status}"
    return f"no_low_tilt_image: {row.get('measured_images')} measured but none <=10deg " \
           f"(page_status={page_status})"


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
    for c in controls:
        cid = c["control_id"]
        base = {
            "control_id": cid,
            "human_label": c.get("human_label", ""),
            "label_strength": c.get("label_strength", ""),
            "expected_primary_metric": c.get("expected_primary_metric", ""),
        }
        median_row = medians_by_id.get(cid)
        if median_row is None:
            base["evaluable"] = "false"
            base["exclusion_reason"] = exclusion_reason(c)
            base["mapped_metric"] = ""
            base["metric_mapping_note"] = ""
            base["signed_deviation"] = ""
            base["mad_multiples"] = ""
            base["evidence_strength"] = ""
            out_rows.append(base)
            continue

        expected = c.get("expected_primary_metric", "")
        mapped_key, mapping_note = GENERIC_TO_PROFILE_CLOSEST_ANALOG.get(
            expected, (expected, "exact key lookup" if expected in profile_metrics else "no mapping defined"))
        profile_metric = profile_metrics.get(mapped_key)
        observed = median_row.get(mapped_key)
        if profile_metric is None or observed in (None, ""):
            base["evaluable"] = "false"
            base["exclusion_reason"] = f"no genuine baseline or observation for mapped metric {mapped_key}"
            base["mapped_metric"] = mapped_key
            base["metric_mapping_note"] = mapping_note
            base["signed_deviation"] = ""
            base["mad_multiples"] = ""
            base["evidence_strength"] = ""
            out_rows.append(base)
            continue

        observed_value = float(observed)
        median = profile_metric["median"]
        mad = profile_metric["mad"]
        signed_deviation = observed_value - median
        mad_multiples = signed_deviation / mad if mad > MIN_MAD_FOR_NORMALISATION else None
        strength = classify(mad_multiples, profile_metric["status"])

        base["evaluable"] = "true"
        base["exclusion_reason"] = ""
        base["mapped_metric"] = mapped_key
        base["metric_mapping_note"] = mapping_note
        base["signed_deviation"] = f"{signed_deviation:.6g}"
        base["mad_multiples"] = "" if mad_multiples is None else f"{mad_multiples:.4g}"
        base["evidence_strength"] = strength
        out_rows.append(base)

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    with OUT_CSV.open("w", newline="", encoding="utf-8") as f:
        fieldnames = ["control_id", "human_label", "label_strength", "expected_primary_metric",
                      "evaluable", "exclusion_reason", "mapped_metric", "metric_mapping_note",
                      "signed_deviation", "mad_multiples", "evidence_strength"]
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        w.writerows(out_rows)

    n_total = len(out_rows)
    n_evaluable = sum(1 for r in out_rows if r["evaluable"] == "true")
    print(f"controls: {n_total}, evaluable (measured + mapped baseline): {n_evaluable}")
    if n_evaluable == 0:
        print("No control produced a measurement: blind defect-discrimination validation "
              "(task item 4/5) could not be completed this run. See exclusion_reason per "
              "control in discrimination_analysis.csv.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
