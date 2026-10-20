#!/usr/bin/env python3
"""Read-only study: robust pooled calibration of existing, verified-genuine Rolex QC measurements.

Outputs reports only. It never changes the detector, app thresholds, model data,
reference measurements, UI, or model support.
"""
import csv
import hashlib
import json
import math
import random
from collections import Counter, defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
SOURCE=ROOT/"tools/research/marker_audit/results"
OUT=ROOT/"pooled-calibration-output"
FAMILIES={"GMT":"gmt","Sub No-Date":"nodate","Sub Date":"date"}
ROUNDS={1,2,4,5,7,8,10,11}
BATONS={6,9}
# Only common hour positions in all three layouts.
METRICS={
    "round_position_pct":("round",ROUNDS,"upper",8),
    "baton_position_pct":("baton",BATONS,"upper",2),
    "baton_rotation_deg":("baton",BATONS,"upper",2),
    "triangle_side_pct":("triangle",{12},"upper",1),
    "triangle_radial_pct":("triangle",{12},"upper",1),
    "triangle_rotation_deg":("triangle",{12},"upper",1),
    "triangle_gap_pct":("triangle",{12},"lower",1),
}
# Reference only: app also adds a resolution-dependent allowance.
DEFAULT_LIMIT={"round_position_pct":1.0,"baton_position_pct":1.0,
               "baton_rotation_deg":1.75,"triangle_side_pct":1.0,
               "triangle_radial_pct":1.25,"triangle_rotation_deg":1.2,
               "triangle_gap_pct":2.2}

def read(path):
    with path.open(newline="",encoding="utf-8") as f:
        return list(csv.DictReader(f))

def fnum(x):
    try:
        v=float(x)
        return v if math.isfinite(v) else None
    except (ValueError,TypeError):
        return None

def median(x):
    if not x:return None
    v=sorted(x)
    n=len(v)
    return (v[(n-1)//2]+v[n//2])/2

def quantile(x,q):
    if not x:return None
    v=sorted(x)
    k=q*(len(v)-1)
    a,b=int(math.floor(k)),int(math.ceil(k))
    return v[a]+(v[b]-v[a])*(k-a)

def mad(x):
    m=median(x)
    return None if m is None else median([abs(v-m) for v in x])

def bootstrap_median_ci(x):
    if not x:return [None,None]
    rng=random.Random(921987+len(x))
    bs=[median([x[rng.randrange(len(x))] for _ in x]) for _ in range(500)]
    return [quantile(bs,.025),quantile(bs,.975)]

def metric(m,r):
    side=fnum(r.get("side_pct"))
    radial=fnum(r.get("inout_pct"))
    rotation=fnum(r.get("tilt_line_deg"))
    gap=fnum(r.get("gap_pct"))
    if m.endswith("position_pct"):
        return math.hypot(side,radial) if side is not None and radial is not None else None
    if m=="triangle_side_pct":return abs(side) if side is not None else None
    if m=="triangle_radial_pct":return abs(radial) if radial is not None else None
    if m.endswith("rotation_deg"):return abs(rotation) if rotation is not None else None
    if m=="triangle_gap_pct":return gap
    raise ValueError(m)

def quality(r,strict=False):
    if r["gate"]:return "measurement gated"
    if r["uncertain"]:return "edge uncertainty"
    rpx,cover=fnum(r["rpx"]),fnum(r["cover"])
    if rpx is None or rpx<200:return "low resolution"
    if cover is None or cover<.5:return "low edge coverage"
    if strict:
        rms,bad=fnum(r["fit_rms_px"]),fnum(r["bad_frac"])
        if cover<.8 or rms is None or rms>.75 or bad is None or bad>.25:
            return "strict edge-quality sensitivity"
    return None

def inputs(strict=False):
    values=defaultdict(list)
    audits={}
    for family,key in FAMILIES.items():
        original=read(SOURCE/(key+"-after.csv"))
        suitable=read(SOURCE/(key+"-suitability.csv"))
        if len({r["photo_id"] for r in suitable})!=len(suitable):
            raise ValueError("Duplicate suitability row: "+key)
        eligible={r["photo_id"] for r in suitable if r["eligible"]=="yes"}
        seen=set()
        removed=Counter()
        retained=0
        for r in original:
            ident=(r["photo_id"],r["hour"])
            if ident in seen:raise ValueError("Duplicate marker row: "+repr(ident))
            seen.add(ident)
            if r["photo_id"] not in eligible:
                removed["independent suitability review"]+=1
                continue
            why=quality(r,strict)
            if why is not None:
                removed[why]+=1
                continue
            retained+=1
            h=int(r["hour"])
            for name,(shape,hours,direction,num_hours) in METRICS.items():
                if r["shape"]==shape and h in hours:
                    v=metric(name,r)
                    if v is not None:
                        values[(family,r["watch"],name,h)].append((v,r["photo_id"]))
        audits[family]={"all_photos":len({r["photo_id"] for r in original}),
                        "eligible_photos":len(eligible),
                        "all_watch_ids":len({r["watch"] for r in original}),
                        "rows":len(original),"admissible_rows":retained,
                        "quality_exclusions":dict(removed)}
    # Repeated photos of same physical watch and hour contribute once.
    by_watch=defaultdict(list)
    for (family,watch,name,hour),observations in values.items():
        by_watch[(family,watch,name)].append({
            "hour":hour,
            "value":median([v for v,_ in observations]),
            "photos":sorted({pid for _,pid in observations})})
    records=[]
    for (family,watch,name),hours in by_watch.items():
        hours=sorted(hours,key=lambda x:x["hour"])
        direction=METRICS[name][2]
        worst=max(hours,key=lambda x:x["value"]) if direction=="upper" else min(hours,key=lambda x:x["value"])
        records.append({"family":family,"watch":watch,"metric":name,
                        "typical":median([h["value"] for h in hours]),
                        "worst":worst["value"],"worst_hour":worst["hour"],
                        "worst_photos":worst["photos"],"n_hours":len(hours),
                        "complete":len(hours)==METRICS[name][3]})
    return records,audits

def heldout(record):
    label=record["family"]+"/"+record["watch"]
    return int(hashlib.sha256(label.encode()).hexdigest()[:8],16)%5==0

def outside(v,limit,direction):
    return v>limit if direction=="upper" else v<limit

def display(v):
    return "-" if v is None else "%.3f"%v

def main():
    records,audits=inputs()
    strict_records,strict_audits=inputs(strict=True)
    OUT.mkdir(parents=True,exist_ok=True)
    result={
        "scope":"read-only genuine Rolex pooled calibration pilot",
        "rules":[
            "No old pass/fail outcome used to admit or exclude genuine calibration rows.",
            "Suitability decided from previously frozen image-level review.",
            "Gated or uncertain marker edges excluded from fit, but not deleted from evidence.",
            "Each watch/hour averaged across repeat photos by median.",
            "Only common 6 and 9 batons included; No-Date 3 baton excluded.",
            "Candidates use complete-watch worst-marker distribution, not pooled green markers.",
            "4 scaled MAD is exploratory and not a confidence interval.",
            "No results automatically transferred to production thresholds.",
            "No original Yacht-Master images in existing research; screenshot cases qualitative only."],
        "quality_audit":audits,
        "strict_quality_audit":strict_audits,
        "metrics":{},
        "controls":[]}
    lines=["# Pooled Rolex alignment calibration pilot","",
           "RESEARCH ONLY. No changes to engine, limits, model availability or production references.","",
           "The three pre-screened verified-genuine cohorts are analysed per physical watch. "
           "Photo quality and observed edge reliability, never the existing verdict, decide whether an observation is usable. "
           "Repeated photos of the same watch/hour are collapsed by median. Common 6 and 9 batons only.","",
           "## Data quality","",
           "| Family | Previously suitable photos | Recorded watches in full dataset | Reliable marker readings | Stricter edge filter readings |",
           "|---|---:|---:|---:|---:|"]
    for family in FAMILIES:
        a,b=audits[family],strict_audits[family]
        lines.append("| %s | %d | %d | %d | %d |"%(family,a["eligible_photos"],a["all_watch_ids"],a["admissible_rows"],b["admissible_rows"]))
    lines+=["",
            "Recorded-watch counts include unsuitable photos; actual per-metric counts below are more precise.",
            "All exclusions and unfiltered large observations are preserved in results.json.",
            "",
            "## Genuine distributions: each watch equal weight","",
            "The P95 is calculated on complete-watch worst marker, not on a raw photo pool.",
            "",
            "| Metric | Family | Watches measured | Complete watches | Median of watch medians | MAD | Complete-watch worst P95 | Bootstrap median CI 95% |",
            "|---|---|---:|---:|---:|---:|---:|---|"]
    summary_lines=["","## Exploratory common limits (NOT proposed for immediate use)","",
                   "Train/held-out split is deterministic and by watch, not by photograph. "
                   "Training uses the worst marker on each watch with all common hours measured. "
                   "Candidate = median of worst-marker distribution +/- 4 x 1.4826 x MAD. "
                   "The full numerical values are in results.json.",
                   "",
                   "| Metric | Complete training watches | Candidate | Current base threshold | Complete held-out watches | Held-out watches outside | Family held-out count GMT / No-Date / Date |",
                   "|---|---:|---:|---:|---:|---:|---|"]
    for name,(_,_,direction,required) in METRICS.items():
        measurements=[r for r in records if r["metric"]==name]
        fam_data={}
        for family in FAMILIES:
            m=[r for r in measurements if r["family"]==family]
            v=[x["typical"] for x in m]
            complete=[x["worst"] for x in m if x["complete"]]
            confidence=bootstrap_median_ci(v)
            fam_data[family]={
                "watches_measured":len(v),"complete_watches":len(complete),
                "median_watch":median(v),"MAD_watch":mad(v),
                "p95_worst_complete":quantile(complete,.95),
                "bootstrap_median_95pct_ci":confidence,
                "strict_filter_watches":sum(r["family"]==family and r["metric"]==name for r in strict_records)}
            lines.append("| %s | %s | %d | %d | %s | %s | %s | %s to %s |"%(
                name,family,len(v),len(complete),display(median(v)),
                display(mad(v)),display(quantile(complete,.95)),
                display(confidence[0]),display(confidence[1])))
        training=[r["worst"] for r in measurements if r["complete"] and not heldout(r)]
        hold=[r for r in measurements if r["complete"] and heldout(r)]
        if len(training)<20 or len(hold)<5:
            raise ValueError("Not enough complete watches for "+name+" : "+str((len(training),len(hold))))
        centre=median(training)
        scale=1.4826*mad(training)
        candidate=centre+4*scale if direction=="upper" else centre-4*scale
        exceed=[r for r in hold if outside(r["worst"],candidate,direction)]
        per_family={f:{"heldout":sum(x["family"]==f for x in hold),
                       "exceeded":sum(x["family"]==f for x in exceed)} for f in FAMILIES}
        result["metrics"][name]={
            "direction":direction,"families":fam_data,"candidate_four_mad":candidate,
            "current_base_default":DEFAULT_LIMIT[name],
            "train_complete_watches":len(training),"heldout_complete_watches":len(hold),
            "heldout_outside_count":len(exceed),"heldout_by_family":per_family,
            "original_largest_or_smallest":[
                {"family":r["family"],"watch":r["watch"],"hour":r["worst_hour"],
                 "value":r["worst"],"photo_ids":r["worst_photos"],"complete":r["complete"]}
                for r in sorted(measurements,key=lambda a:a["worst"],reverse=(direction=="upper"))[:12]]}
        fmt_family=" / ".join("%d/%d"%(per_family[f]["exceeded"],per_family[f]["heldout"]) for f in FAMILIES)
        summary_lines.append("| %s | %d | %s | %s | %d | %d | %s |"%(
            name,len(training),display(candidate),display(DEFAULT_LIMIT[name]),
            len(hold),len(exceed),fmt_family))
    controls=[
        ("GMT crooked 6 (known missed defect)","gmt_126710-after.csv","GMT_crooked6_1s4cszq",6,
         ["baton_position_pct","baton_rotation_deg"]),
        ("Sub 12 rejected control","submariner_124060-after.csv","SUB_rejected12_1wgfy7k",12,
         ["triangle_side_pct","triangle_radial_pct","triangle_rotation_deg","triangle_gap_pct"])
    ]
    control_lines=["","## Existing replica controls (NOT training data)","",
                   "Scalar thresholds below are not a complete replay of the Android classifier, which also has image-scale allowances and evidence gates.",
                   "",
                   "| Control | Hour | Actual 1.6.3 state | Comparisons: measured / proposed threshold |",
                   "|---|---:|---|---|"]
    for label,filename,photo,hour,names in controls:
        rr=[r for r in read(SOURCE/"qc"/filename) if r["photo_id"]==photo and int(r["hour"])==hour]
        if len(rr)!=1:raise ValueError("Missing control: "+label)
        r=rr[0]
        checks=[]
        for name in names:
            v=metric(name,r)
            th=result["metrics"][name]["candidate_four_mad"]
            ex=v is not None and outside(v,th,METRICS[name][2])
            checks.append({"metric":name,"observed":v,"candidate":th,"exceeds":ex})
        result["controls"].append({"control":label,"hour":hour,"current_state":r["state"],"metrics":checks})
        desc="; ".join("%s %s / %s%s"%(c["metric"],display(c["observed"]),display(c["candidate"])," OUTSIDE" if c["exceeds"] else "") for c in checks)
        control_lines.append("| %s | %d | %s | %s |"%(label,hour,r["state"],desc))
    final=["","## Interpretation and next checks","",
           "1. Do not update thresholds automatically. Apparent extreme genuine measures might represent photographic errors or real genuine variation.",
           "2. Compare families after matching photo source, resolution and true nominal geometry. Different medians may reflect systematic detector bias.",
           "3. Manually audit the top original outliers using full-quality image and edge evidence. Do not delete an outlier solely because it exceeds a proposed limit.",
           "4. Check known replica defects on original source photos, especially the owner's GMT 12 and Yacht-Master 8. Their screenshots are not a verified numerical reference.",
           "5. Test any revised limit on watches held out from parameter selection and separately report false negative behaviour.",
           "6. Evaluate subjective on-wrist visual importance separately from whether a measurement is outside normal genuine variation.",
           "",
           "No production Java or Android assets modified.", ""]
    (OUT/"results.json").write_text(json.dumps(result,indent=2,sort_keys=True)+"\n")
    (OUT/"REPORT.md").write_text("\n".join(lines+summary_lines+control_lines+final))
    print("POOLED_AUDIT",json.dumps(audits,sort_keys=True))
    for m,item in result["metrics"].items():
        print("POOLED_CANDIDATE",m,round(item["candidate_four_mad"],4),
              "heldout_exceeds",item["heldout_outside_count"],"/",item["heldout_complete_watches"])
    print("OUTPUT",OUT/"REPORT.md")
if __name__=="__main__":main()
