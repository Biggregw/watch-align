#!/usr/bin/env python3
"""Validate stability of the genuine GMT physical-watch baseline.

Research only. This does not define Rolex factory tolerances and must not be
used as an authenticity classifier.

Reads the physical-watch-collapsed CSV produced by the genuine baseline
pipeline and writes:
  * baseline_stability.csv
  * genuine-baseline-v1.json
  * BASELINE_VALIDATION.md

The validator deliberately works at physical-watch level so repeated images
of one watch cannot inflate n.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
from pathlib import Path
from statistics import median

CORE = [
    "h12.stage3_apex_r_simple",
    "h12.stage3_centre_r_projective",
    "h12.stage3_base_r_projective",
    "h12.stage3_axis_incidence_canonical",
    "h12.stage3_centroid_tangential_offset_canonical",
    "h06.centre_r", "h06.centre_t", "h06.axis_residual_deg", "h06.radial_span",
    "h09.centre_r", "h09.centre_t", "h09.axis_residual_deg", "h09.radial_span",
]

# Conservative research gates. A feature must have adequate coverage and its
# leave-one-watch-out (LOWO) centre/spread must not be dominated by one watch.
MIN_N = 8
MAX_LOO_MEDIAN_SHIFT_MAD = 0.75
MAX_LOO_MAD_REL_CHANGE = 0.60
EPS = 1e-12


def finite(s):
    try:
        x = float(s)
        return x if math.isfinite(x) else None
    except (TypeError, ValueError):
        return None


def mad(xs):
    m = median(xs)
    return median(abs(x - m) for x in xs)


def stats(xs):
    return median(xs), mad(xs)


def classify(xs):
    n = len(xs)
    if n < MIN_N:
        return "insufficient", None, None, "fewer than 8 independent watches"
    med, md = stats(xs)
    loo = [stats(xs[:i] + xs[i+1:]) for i in range(n)]
    max_med = max(abs(m-med) for m, _ in loo)
    max_mad = max(abs(d-md) for _, d in loo)
    med_norm = max_med / max(md, EPS)
    mad_rel = max_mad / max(md, EPS)
    stable = med_norm <= MAX_LOO_MEDIAN_SHIFT_MAD and mad_rel <= MAX_LOO_MAD_REL_CHANGE
    reason = (f"LOWO max median shift={med_norm:.3f} MAD; "
              f"max MAD change={mad_rel:.3f}x baseline MAD")
    return "stable" if stable else "sample-sensitive", med_norm, mad_rel, reason


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", default="docs/research/gmt-genuine-baseline-results/per_physical_watch_medians.csv")
    ap.add_argument("--out", default="docs/research/gmt-genuine-baseline-results")
    args = ap.parse_args()
    rows = list(csv.DictReader(Path(args.input).open(newline="", encoding="utf-8-sig")))
    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    results = []
    for feature in CORE:
        vals = [finite(r.get(feature)) for r in rows]
        vals = [x for x in vals if x is not None]
        if not vals:
            results.append(dict(feature=feature,n=0,status="insufficient",reason="no measurements")); continue
        med, md = stats(vals)
        status, med_norm, mad_rel, reason = classify(vals)
        results.append(dict(feature=feature,n=len(vals),median=med,mad=md,
                            loo_max_median_shift_mad=med_norm,
                            loo_max_mad_relative_change=mad_rel,
                            status=status,reason=reason))

    fields = ["feature","n","median","mad","loo_max_median_shift_mad",
              "loo_max_mad_relative_change","status","reason"]
    with (out/"baseline_stability.csv").open("w", newline="", encoding="utf-8") as f:
        w=csv.DictWriter(f, fieldnames=fields); w.writeheader(); w.writerows(results)

    frozen = {r["feature"]:{"n":r["n"],"median":r.get("median"),"mad":r.get("mad"),
                              "status":r["status"]} for r in results if r["status"]=="stable"}
    payload = {
        "schema":"watch-align.genuine-gmt-baseline.v1",
        "research_only":True,
        "authenticity_classifier":False,
        "unit_of_independence":"physical_watch",
        "n_physical_watches":len(rows),
        "stability_gates":{"min_n":MIN_N,
          "max_loo_median_shift_in_baseline_mad":MAX_LOO_MEDIAN_SHIFT_MAD,
          "max_loo_mad_relative_change":MAX_LOO_MAD_REL_CHANGE},
        "provisionally_frozen_features":frozen,
        "all_core_features":results,
    }
    (out/"genuine-baseline-v1.json").write_text(json.dumps(payload, indent=2)+"\n", encoding="utf-8")

    stable=[r for r in results if r["status"]=="stable"]
    sensitive=[r for r in results if r["status"]=="sample-sensitive"]
    insufficient=[r for r in results if r["status"]=="insufficient"]
    lines=["# Genuine GMT baseline stability validation","",
      "> Research-only empirical image geometry. Not Rolex factory tolerance and not an authenticity classifier.","",
      f"Independent physical watches in input: **{len(rows)}**.","",
      "## Method","",
      "Each feature is recomputed after leaving out each physical watch in turn. A feature is provisionally stable only when coverage is adequate and neither its median nor MAD is excessively controlled by any one watch.","",
      f"Gates: n >= {MIN_N}; maximum LOWO median shift <= {MAX_LOO_MEDIAN_SHIFT_MAD} baseline MAD; maximum LOWO MAD change <= {MAX_LOO_MAD_REL_CHANGE:.0%} of baseline MAD.","",
      "These are conservative engineering stability gates, not manufacturing tolerances.","",
      "## Results","",
      "| Feature | n | median | MAD | LOWO median shift / MAD | LOWO MAD change | status |","|---|---:|---:|---:|---:|---:|---|"]
    for r in results:
        def fmt(v): return "-" if v is None else f"{v:.6g}"
        lines.append(f"| `{r['feature']}` | {r['n']} | {fmt(r.get('median'))} | {fmt(r.get('mad'))} | {fmt(r.get('loo_max_median_shift_mad'))} | {fmt(r.get('loo_max_mad_relative_change'))} | **{r['status']}** |")
    lines += ["","## Interpretation","",
      f"Provisionally stable: **{len(stable)}**. Sample-sensitive: **{len(sensitive)}**. Insufficient: **{len(insufficient)}**.","",
      "Only `stable` features are emitted into `provisionally_frozen_features` in `genuine-baseline-v1.json`. Sample-sensitive features remain useful diagnostics but must not become QC pass/fail thresholds without further evidence.",""]
    (out/"BASELINE_VALIDATION.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"validated {len(results)} features across {len(rows)} physical watches")
    print(f"stable={len(stable)} sample-sensitive={len(sensitive)} insufficient={len(insufficient)}")

if __name__ == "__main__": main()
