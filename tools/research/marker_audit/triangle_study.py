#!/usr/bin/env python3
"""Read-only audit of unreliable 12-triangle boundary measurements.

Uses existing SHA-verified genuine photo cohorts and per-edge traces from the
Codex audit. This does NOT change the fitter, decide states, reference geometry,
or production calibration. The numerical quality descriptors are diagnostic,
not labels for manufactured defects.
"""
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
AUD=ROOT/"tools/research/marker_audit"
RES=AUD/"results"
OUT=ROOT/"triangle-study-out"

def read_csv(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f))

def num(v):
    try:return float(v)
    except (ValueError,TypeError):return float("nan")

def unique_triangles(rows):
    result={}
    for r in rows:
        if r["shape"]!="triangle" or r["hour"]!="12":continue
        k=r["photo_id"]
        if k in result:
            if r!=result[k]:raise ValueError("Conflicting 12 triangle measurements: "+k)
            continue
        result[k]=r
    return result

def metrics(rows):
    all_=list(rows.values())
    flagged=[r for r in all_ if r["state"] in ("POSSIBLE","CLEARLY_OFF")]
    def high_rms(r):return num(r["fit_rms_px"])>1
    def high_bad(r):return num(r["bad_frac"])>.5
    def poorly_covered(r):return num(r["cover"])<.5
    return {"photos":len(all_), "states":dict(Counter(r["state"] for r in all_)),
        "flagged":len(flagged),
        "rms_over_1px":sum(map(high_rms,all_)),
        "bad_fraction_over_half":sum(map(high_bad,all_)),
        "coverage_below_half":sum(map(poorly_covered,all_)),
        "flagged_rms_over_1px":sum(map(high_rms,flagged)),
        "flagged_bad_fraction_over_half":sum(map(high_bad,flagged)),
        "flagged_coverage_below_half":sum(map(poorly_covered,flagged)),
        "unflagged_rms_over_1px":sum(high_rms(r) for r in all_ if r not in flagged),
        "unflagged_bad_fraction_over_half":sum(high_bad(r) for r in all_ if r not in flagged)}

def main():
    date=unique_triangles(read_csv(RES/"checkpoint/original219-baseline.csv"))
    nd=unique_triangles(read_csv(RES/"nodate-after.csv"))
    gmt=unique_triangles(read_csv(RES/"gmt-after.csv"))
    meta={r["photo_id"]:r for r in read_csv(RES/"checkpoint/original219.csv")}
    aud=json.loads((RES/"cases-summary.json").read_text())
    summary={x["photo_id"]:x for x in aud}
    if len(date)!=219 or len(nd)!=135:
        raise ValueError("Genuine cohorts must match verified original runs (219 Date; 135 No-Date)")
    if set(date)!=set(meta):
        raise ValueError("Original Date photographed IDs differ from catalogued photo IDs")
    flags=[r for r in date.values() if r["state"] in ("POSSIBLE","CLEARLY_OFF")]
    if len(flags)!=7:raise ValueError("Expected seven ORIGINAL Date triangle warnings")
    flags.sort(key=lambda r:r["photo_id"])
    if not all(r["photo_id"] in summary for r in flags):
        raise ValueError("Incomplete original-ten per-edge audit evidence")
    found=[]
    for r in flags:
        case=summary[r["photo_id"]]["after"]
        sides=case["positive_by_side"]
        supported=sum(int(v)>0 for v in sides.values())
        sensitivity=case["uniform_2px_offset_sensitivity"]
        found.append({"photo_id":r["photo_id"],
            "watch_reference":meta[r["photo_id"]]["model"],
            "baseline_state":r["state"],
            "corrected_state":case["state"],
            "baseline_rotation_deg":num(r["tilt_line_deg"]),
            "corrected_rotation_deg":case["tilt_deg"],
            "baseline_radial_pct":num(r["inout_pct"]),
            "corrected_radial_pct":case["inout_pct"],
            "coverage":case["fit"]["cover"],
            "rms_px":case["fit"]["rms_px"],
            "bad_fraction":case["fit"]["bad_fraction"],
            "positive_edge_points_by_side":sides,
            "sides_with_positive_edge_points":supported,
            "hypothetical_uniform_2px_shift":sensitivity,
            "seconds_contact":bool(case["alpha99"].get("seconds")),
            "diagnostic_limitation":(
                "No positively weighted top-edge observations; radial position inferred from slopes"
                if int(sides.get("1",0))==0 else
                "High residual inconsistency; metal boundary/reference disagreement"
                if case["fit"]["rms_px"]>1 and case["fit"]["bad_fraction"]>.5 else
                "Requires independent visual/physical reference to distinguish deviation from photograph")})
    by_reference=defaultdict(lambda:{"photos":0,"warnings":0})
    for r in date.values():
        model=meta[r["photo_id"]]["model"]
        by_reference[model]["photos"]+=1
        by_reference[model]["warnings"]+=int(r["state"] in ("POSSIBLE","CLEARLY_OFF"))
    obj={"source_baseline":"cf373d205a873648fde46c9704aba177284bc920",
        "read_only":True,
        "reference_warning":"Quality metrics and two-pixel perturbations do not establish the true physical alignment",
        "cohorts":{"date":metrics(date),"nodate":metrics(nd),"gmt":metrics(gmt)},
        "date_reference_groups":dict(sorted(by_reference.items())),
        "original_date_triangle_findings":found}
    OUT.mkdir(exist_ok=True)
    (OUT/"triangle-study.json").write_text(json.dumps(obj,indent=2)+"\n")
    lines=["# Genuine Rolex 12-triangle boundary study (research only)","",
        "This is an independent read-only audit of the existing Watch Align measurement evidence. It changes **no production code, thresholds, references, or model visibility**.",
        "",
        "## Counts from frozen matched genuine cohorts",
        "",
        "| Dataset | Genuine photos | 12 markers flagged | RMS >1px | Bad fractions >50% | Flagged with RMS >1px | Flagged with <50% coverage |",
        "|---|---:|---:|---:|---:|---:|---:|"]
    for name in ("date","nodate","gmt"):
        m=obj["cohorts"][name]
        lines.append(f"| {name} | {m['photos']} | {m['flagged']} | {m['rms_over_1px']} | {m['bad_fraction_over_half']} | {m['flagged_rms_over_1px']} | {m['flagged_coverage_below_half']} |")
    lines.extend(["","## Original seven Date 12-triangle warnings","","| ID | Rolex reference | Reading before/after (°) | Fit RMS px | Inconsistent % | Edge support sides | 2px uniform edge shift: rotation (°) |","|---|---|---|---:|---:|---|---:|"])
    for r in found:
        sens=r["hypothetical_uniform_2px_shift"]["rotation_deg"]
        lines.append(f"| \`{r['photo_id']}\` | {r['watch_reference']} | {r['baseline_rotation_deg']:+.3f} / {r['corrected_rotation_deg']:+.3f} | {r['rms_px']:.2f} | {100*r['bad_fraction']:.0f} | {r['positive_edge_points_by_side']} | {sens:+.2f} |")
    lines.extend(["","## What the evidence can and cannot establish","",
        "- Some genuine triangles show a whole missing/zero-weight edge, yet have a full-rank numeric fit. Full rank does **not** independently verify radial placement.",
        "- Most remaining Date flags show systematic photo/metal-boundary disagreement. A fixed shape may convert a small rim/lighting offset into apparent rotation.",
        "- Some **passing** genuine triangles also have rough or inconsistent boundaries. Simply suppressing cases with a high bad fraction would be unsafe, and would not distinguish physical defects.",
        "- The 2px perturbation is a **diagnostic counterfactual** on the observed Jacobian/weights, not a measured true metal width, not an alignment calibration.",
        "- A separate real-world replica/defect control is needed before a new triangle judgment method can be trusted.",
        "",
        "## Recommended next experiment (no release change)",
        "",
        "Use the existing exact edge traces to calculate **per-side cross-validation**: independently predict a held-out visible triangle edge from the other two, and evaluate an edge-offset nuisance parameter only if there is enough spatially independent evidence. Also compare the 12 top edge position with nearby minute-track clearance independently. Do not extrapolate a single slope to a hidden top edge without reporting that uncertainty.",
        "",
        "Acceptance gate: retain all previously reliable GMT/124060/126610 measurements and real known defects, reduce genuine false warnings **without simply increasing Can't-check**, show exact before/after polygons, and obtain confirmed 126610 real-defect ground truth before enabling Date.",
        ""])
    (OUT/"TRIANGLE_STUDY.md").write_text("\n".join(lines))
    print("DATE_TRIANGLE_STUDY",json.dumps(obj["cohorts"],sort_keys=True))
    print("WROTE",OUT/"TRIANGLE_STUDY.md")
if __name__=="__main__":main()
