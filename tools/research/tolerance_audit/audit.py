#!/usr/bin/env python3
"""Read-only, reproducible inventory of every supported Watch Align reference feature and
every production visual-significance gate. Validates evidence coverage; never changes limits.

Run: python3 tools/research/tolerance_audit/audit.py --out out/tolerance_audit
"""
import argparse
import csv
import json
import math
import re
from collections import Counter, defaultdict
from pathlib import Path
from statistics import median

ROOT = Path(__file__).resolve().parents[3]
MODEL_ROOT = ROOT / "android/app/src/main/assets/models"
FINDINGS = ROOT / "android/app/src/main/java/com/watchalign/mobile/Alpha99Findings.java"
MODELS = ("gmt_126710", "submariner_124060", "submariner_126610")
CITATIONS = {
    "orientation": "Westheimer & Ley 1997, doi:10.1152/jn.1997.77.5.2677: sensitivity depends on stimulus length; no universal 1-degree watch-marker result.",
    "size/spacing": "Vision research on size (~5%) and optimal segment spacing (~3%) discrimination, context-dependent; see doi/underlying methods before applying to polished watches.",
    "limitations": "Lab foveal high-contrast detection is not a directly validated threshold on a reflective Rolex dial during wrist wear."
}

def read_props(path):
    result={}
    for line in path.read_text().splitlines():
        line=line.strip()
        if not line or line.startswith("#") or "=" not in line:continue
        k,v=line.split("=",1)
        result[k.strip()]=v.strip()
    return result

def read_csv(path):
    with path.open(newline="") as stream:return list(csv.DictReader(stream))

def f(value):
    try:
        v=float(value)
        return v if math.isfinite(v) else None
    except (ValueError,TypeError):return None

def stat(vals):
    v=sorted(x for x in vals if x is not None and math.isfinite(x))
    if not v:return {"n":0}
    def pct(p):
        ix=(len(v)-1)*p/100
        return round(v[int(ix)]*(1-(ix-int(ix)))+v[min(int(ix)+1,len(v)-1)]*(ix-int(ix)),7)
    return {"n":len(v),"median":round(median(v),7),"p95":pct(95),"max":round(v[-1],7)}

def code_constant(code,name):
    m=re.search(r"\b"+re.escape(name)+r"\s*=\s*([0-9]+(?:\.[0-9]+)?)",code)
    if not m:raise ValueError("Can't establish live production constant: "+name)
    return float(m.group(1))

def required_features(markers,has_date):
    out=[]
    for marker in markers:
        if marker["shape"]=="baton":
            out.extend((marker["key"]+"_rot",marker["key"]+"_off"))
    out.extend(("rounds_off","rounds_size","round_size_rel","ring_rot","ring_shift"))
    if has_date:out.append("date_tilt")
    return out

def run(out_dir):
    code=FINDINGS.read_text()
    visible_deg=code_constant(code,"VISIBLE_DEG")
    visible_frac=code_constant(code,"VISIBLE_FRACTION")
    track_fraction=code_constant(code,"TRACK_GAP_VISIBLE_FRACTION")
    assert ("oneSidedEdge" in code and "resolution too low" in code), "Latest model confidence gates missing"
    records=[]; model_rows=[]; problems=[]; missing=[]; sig_rows=[]
    for model_name in MODELS:
        d=MODEL_ROOT/model_name
        spec=json.loads((d/"model.json").read_text())
        ref=d/"reference"
        genuine=read_csv(ref/"genuine_reference.csv")
        tri=read_csv(ref/"triangle_reference.csv")
        nom=read_props(ref/"nominal.properties")
        tri_nom=read_props(ref/"triangle_nominal.properties")
        uncertainty=read_props(ref/"uncertainty.properties")
        features=defaultdict(list)
        for row in genuine:
            feature=row["feature"]
            features[feature].append(row)
        req=required_features(spec["markers"],spec.get("date_window") is not None)
        actual=set(features)
        ordered_watch_ids={r["physical_watch_id"] for r in genuine}
        if not genuine:problems.append(f"{model_name}: empty genuine reference")
        for feature in req:
            if feature not in actual:
                missing.append({"model":model_name,"feature":feature,"reason":"no genuine reference"})
        triangle_metrics=("lateral_R","radial_R","centreline_deg","sides_deg","radial_signed_R")
        if not tri:problems.append(f"{model_name}: missing genuine 12 reference")
        for key in triangle_metrics:
            if tri and key not in tri[0]:missing.append({"model":model_name,"feature":"twelve_"+key,"reason":"not recorded"})
        for feature,rows in sorted(features.items()):
            ids=[r["physical_watch_id"] for r in rows]
            duplications=len(ids)-len(set(ids))
            if duplications>0:
                # intentionally duplicated low-res shrunk rows can be separate resolution conditions for the same watch.
                if not all(r.get("max_photo_r","") for r in rows):
                    problems.append(f"{model_name}/{feature}: {duplications} duplicate physical-watch rows (inspect resolution matching)")
            vals=[f(r["far"]) for r in rows]
            if any(x is None or x<0 for x in vals):problems.append(f"{model_name}/{feature}: non-finite or negative 'far'")
            sigkind="angle" if feature.endswith("_rot") or feature=="date_tilt" else "position_or_size"
            if feature=="round_size_rel" and model_name!="gmt_126710" and ("round_size_rel.R" not in uncertainty):
                # missing allowances intentionally mean max WORTH per guardrails: may be valid.
                pass
            records.append({"model":model_name,"feature":feature,"unique_watches":len(set(ids)),
                            "source_count":len(set(r["source"] for r in rows)),
                            "genuine":stat(vals),"uncertainty_deg":f(uncertainty.get(feature+".deg")),
                            "uncertainty_R":f(uncertainty.get(feature+".R")),
                            "sigma_watches":int(float(uncertainty.get(feature+".deg.watches",uncertainty.get(feature+".R.watches","0")))),
                            "type":sigkind})
        # Geometry-aware visibility in units of dial radius. Size 'round size' uses radius, not diameter:
        for mark in spec["markers"]:
            shape=mark["shape"];hour=mark["hour"]
            if shape=="baton":
                width=2*mark["tangential_half"]
                sig_rows.append({"model":model_name,"feature":f"{hour} baton rotation","metric":"deg",
                                 "production_visibility_bar":visible_deg,"dimension_R":2*mark["radial_half"],
                                 "derivation":"1.0 degree shared angle; not geometry adaptive","basis":"legacy judgment, no model-specific psychophysics"})
                sig_rows.append({"model":model_name,"feature":f"{hour} baton position","metric":"R",
                                 "production_visibility_bar":visible_frac*width,"dimension_R":width,
                                 "derivation":"5% of full baton width", "basis":"legacy judgment, not literature-validated"})
            elif shape=="round":
                sig_rows.append({"model":model_name,"feature":f"{hour} round position","metric":"R",
                                 "production_visibility_bar":visible_frac*2*mark["outer_r"],
                                 "dimension_R":2*mark["outer_r"],
                                 "derivation":"5% of round diameter","basis":"legacy judgment"})
                sig_rows.append({"model":model_name,"feature":f"{hour} round relative size","metric":"R",
                                 "production_visibility_bar":visible_frac*mark["outer_r"],
                                 "dimension_R":2*mark["outer_r"],
                                 "derivation":"5% of radius = 2.5% of diameter","basis":"legacy judgment"})
            elif shape=="triangle":
                base=2*mark["half_base"]
                gap=spec["pose"]["minute_track_inner_r"]-mark["base_r"]
                sig_rows.append({"model":model_name,"feature":"12 triangle centreline / sides","metric":"deg",
                                 "production_visibility_bar":visible_deg,"dimension_R":mark["base_r"]-mark["apex_r"],
                                 "derivation":"shared 1.0 degree threshold","basis":"legacy judgment"})
                sig_rows.append({"model":model_name,"feature":"12 lateral position","metric":"R",
                                 "production_visibility_bar":visible_frac*base,"dimension_R":base,
                                 "derivation":"5% of triangle base width","basis":"legacy judgment"})
                sig_rows.append({"model":model_name,"feature":"12 towards minute track","metric":"R",
                                 "production_visibility_bar":track_fraction*gap,"dimension_R":gap,
                                 "derivation":"20% of nominal inner tick minus triangle base gap","basis":"legacy judgment, track WORTH only"})
        first_round=next(x for x in spec["markers"] if x["shape"]=="round")
        sig_rows.append({"model":model_name,"feature":"marker ring rotation","metric":"deg",
                         "production_visibility_bar":visible_deg,"dimension_R":first_round["centre_r"],
                         "derivation":"shared 1.0 degree (many-marker context not validated)","basis":"legacy judgment"})
        sig_rows.append({"model":model_name,"feature":"marker ring shift","metric":"R",
                         "production_visibility_bar":visible_frac*2*first_round["outer_r"],
                         "dimension_R":2*first_round["outer_r"],
                         "derivation":"5% of round diameter","basis":"legacy judgment"})
        sig_rows.append({"model":model_name,"feature":"collective round size","metric":"R",
                         "production_visibility_bar":visible_frac*first_round["outer_r"],
                         "dimension_R":2*first_round["outer_r"],
                         "derivation":"5% of radius = 2.5% of diameter","basis":"legacy judgment"})
        if spec.get("date_window") is not None:
            sig_rows.append({"model":model_name,"feature":"date-window tilt","metric":"deg",
                             "production_visibility_bar":visible_deg,"dimension_R":None,
                             "derivation":"shared 1.0 degree","basis":"GMT calibrated; Sub date has no genuine date reference"})
        model_rows.append({"model":model_name,"genuine_rows":len(genuine),"independent_watches":len(ordered_watch_ids),
                           "reference_features":len(features),"triangle_watches":len(set(r["physical_watch_id"] for r in tri)),
                           "batons":sorted(x["hour"] for x in spec["markers"] if x["shape"]=="baton"),
                           "rounds":sum(x["shape"]=="round" for x in spec["markers"]),
                           "date_reference_present":"date_tilt" in features,
                           "k_sigma":f(uncertainty.get("k_sigma")),"visibility_geometry_gates":len([r for r in sig_rows if r["model"]==model_name]),
                           "uncertainty_borrowed":model_name=="submariner_126610"})
    report={"models":model_rows,"features":records,"visibility":sig_rows,
            "unreferenced_features":missing,"problems":problems,"science_notes":CITATIONS,
            "status":"read-only audit; thresholds must not be updated without new evidence and held-out validation",
            "not_applicable":"No date-window calibrated on 126610; no three baton on GMT/126610 because 3 is a date window."}
    out_dir.mkdir(parents=True,exist_ok=True)
    (out_dir/"audit.json").write_text(json.dumps(report,indent=2)+"\n")
    with (out_dir/"genuine_features.csv").open("w",newline="") as h:
        w=csv.writer(h);w.writerow(["model","feature","unique_watches","median","p95","max","uncertainty_R","uncertainty_deg","sigma_watches"])
        for x in records:
            w.writerow([x["model"],x["feature"],x["unique_watches"],x["genuine"].get("median"),
                        x["genuine"].get("p95"),x["genuine"].get("max"),x["uncertainty_R"],x["uncertainty_deg"],x["sigma_watches"]])
    with (out_dir/"visibility_gates.csv").open("w",newline="") as h:
        w=csv.DictWriter(h,fieldnames=["model","feature","metric","production_visibility_bar","dimension_R","derivation","basis"])
        w.writeheader();w.writerows(sig_rows)
    with (out_dir/"summary.md").open("w") as o:
        o.write("# Watch Align: all-supported-model tolerance audit\n\n")
        o.write("This is a **read-only** measurement and scientific-evidence audit, not new genuine reference calibration.\n\n")
        o.write("| Model | Unique genuine watches | Measured genuine families | Triangle watches | Missing date reference |\n")
        o.write("|---|---:|---:|---:|---|\n")
        for row in model_rows:
            o.write(f'| {row["model"]} | {row["independent_watches"]} | {row["reference_features"]} | {row["triangle_watches"]} | {"Yes" if row["model"]=="submariner_126610" else "No" if row["date_reference_present"] or row["model"]=="submariner_124060" else "Check"} |\n')
        o.write("\n## Existing visibility thresholds (not peer-reviewed watch-specific cutoffs)\n\n")
        o.write(f"- Angle: {visible_deg:.3f} degrees for baton/12/ring/date.\n")
        o.write(f"- Position/size: {visible_frac:.3%} of the specified marker dimension (some sizes use **radius rather than diameter**).\n")
        o.write(f"- Triangle track: {track_fraction:.1%} of nominal gap; track has no independent sigma, and is capped at WORTH.\n")
        o.write("\n## Gaps preventing a complete automatic update\n\n")
        o.write("- Marker **length/width measurements** are not production-reference families for batons; the outer-edge-width check only withholds unreliable positions.\n")
        o.write("- Submariner 126610 has **no validated genuine date-window reference**, so date tilt/centering cannot be calibrated.\n")
        o.write("- 126610 photo-to-photo uncertainties are borrowed from 124060 because its source contains no repeated watches.\n")
        o.write("- Scientifically measured visibility thresholds depend on viewing distance, stimulus length, contrast, context and observers. No validated dial-specific psychometric function or measured dial diameter is stored in the current specs.\n")
        o.write("- Recomputing a reference maximum from unchanged genuine images and unchanged detector code does not produce evidence of a different genuine manufacturing tolerance.\n")
        o.write("\n## Recommendation\n\n")
        o.write("**Keep current production thresholds unchanged until a controlled visibility validation justifies a feature-specific change.**\n")
        o.write("Add new reviewed genuine watches only through the calibration protocol, recompute nominals and distributions on a separate branch, validate with leave-one-watch-out and independent genuine holdouts; then apply approved results.\n")
        o.write("Test candidate appearance thresholds on known good/bad geometry with varying viewing distance/contrast before replacing legacy 1°/5%/20% rules.\n")
        if missing:o.write("\nMissing genuine families: "+", ".join(x["model"]+"/"+x["feature"] for x in missing)+"\n")
        if problems:o.write("\nData warnings: "+", ".join(problems)+"\n")
    print(json.dumps({"models":model_rows,"missing":missing,"warnings":problems,
                      "genuine_feature_count":len(records),"visibility_gate_count":len(sig_rows)},indent=2))
    # Fail only for real integrity errors. Missing date-window measurements are documented, not fabricated.
    if problems:raise SystemExit(1)

if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--out",type=Path,required=True)
    run(p.parse_args().out)
