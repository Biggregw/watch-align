"""Robust automatic Watch Align calibration.

Thresholds are derived only from genuine development watches. Validation can reject them but never
move them. Holdout can confirm or reject frozen thresholds but never change them. Replica watches are
reported only as stress tests and never influence a limit.
"""
from __future__ import annotations

import csv, json, math, statistics
from pathlib import Path

MAD_TO_SIGMA=1.4826


def f(v):
    try:
        x=float(v); return x if math.isfinite(x) else math.nan
    except Exception: return math.nan


def rows(path: Path):
    if not path or not path.exists(): return []
    with path.open(newline="",encoding="utf-8") as fh: return list(csv.DictReader(fh))


def med(v):
    v=[x for x in v if math.isfinite(x)]; return statistics.median(v) if v else math.nan


def metric_watch_values(path: Path):
    out={}
    for r in rows(path):
        x=f(r.get("median")); m=r.get("metric","")
        if m and math.isfinite(x): out.setdefault(m,[]).append((r.get("physical_watch_id",""),x))
    return out


def repeatability(path: Path):
    return {r.get("metric",""):r for r in rows(path) if r.get("metric")}


def within(x,lo,hi): return math.isfinite(x) and lo<=x<=hi


def propose(config: dict, dev_watch: Path, dev_repeat: Path, val_watch: Path, rep_watch: Path|None=None) -> dict:
    dw=metric_watch_values(dev_watch); vw=metric_watch_values(val_watch); rw=metric_watch_values(rep_watch) if rep_watch else {}
    dr=repeatability(dev_repeat); policy=config["calibration_policy"]
    result={"model":config["model"],"family":config["family"],"state":"NO_CALIBRATABLE_METRICS","metrics":{},
            "policy":policy,"principle":"development genuine fixes limits; validation may reject only; replica never moves limits"}
    ready=0
    for spec in config["calibration_metrics"]:
        name=spec["metric"]; vals=[x for _,x in dw.get(name,[])]; val=[x for _,x in vw.get(name,[])]; rr=dr.get(name,{})
        rec={"metric":name,"app_key":spec.get("app_key",name),"status":"INSUFFICIENT","development_watches":len(vals),"validation_watches":len(val)}
        if len(vals)<int(policy["min_development_watches"]) or len(val)<int(policy["min_validation_watches"]):
            rec["reason"]="not enough independent genuine watches";result["metrics"][name]=rec;continue
        if rr.get("repeatability_class")=="insufficient data":
            rec["reason"]="repeatability study insufficient";result["metrics"][name]=rec;continue
        if rr.get("pose_sensitive") and not spec.get("allow_pose_sensitive",False):
            rec["reason"]="metric is pose/scale sensitive in development";rec["pose_sensitive"]=rr.get("pose_sensitive");result["metrics"][name]=rec;continue
        center=med(vals)
        bw_mad=f(rr.get("between_watch_mad")); within_mad=f(rr.get("within_watch_mad_median")); pert90=f(rr.get("perturbation_range_p90"))
        sigma=max(0.0,MAD_TO_SIGMA*bw_mad if math.isfinite(bw_mad) else 0.0)
        noise=max(MAD_TO_SIGMA*within_mad if math.isfinite(within_mad) else 0.0,0.5*pert90 if math.isfinite(pert90) else 0.0)
        dev_max=max(abs(x-center) for x in vals)
        floor=float(spec.get("minimum_half_width",0.0))
        clear_half=max(floor,dev_max+noise,float(policy["clear_sigma"])*max(sigma,noise))
        check_half=max(clear_half*float(policy["check_over_clear"]),dev_max+3*noise,float(policy["check_sigma"])*max(sigma,noise))
        lo,hi=center-clear_half,center+clear_half; clo,chi=center-check_half,center+check_half
        vclear=sum(within(x,lo,hi) for x in val)/len(val); vcheck=sum(within(x,clo,chi) for x in val)/len(val)
        rec.update({"center":center,"clear_low":lo,"clear_high":hi,"check_low":clo,"check_high":chi,"noise_floor":noise,
                    "between_watch_sigma_robust":sigma,"development_max_abs_from_center":dev_max,
                    "validation_clear_rate":vclear,"validation_check_rate":vcheck})
        if vcheck < float(policy["validation_check_rate_min"]) or vclear < float(policy["validation_clear_rate_min"]):
            rec["status"]="REJECTED_VALIDATION";rec["reason"]="frozen genuine-development limits did not validate";result["metrics"][name]=rec;continue
        rec["status"]="FROZEN_PENDING_HOLDOUT";ready+=1
        rep=[x for _,x in rw.get(name,[])]
        if rep:
            rec["replica_stress_watches"]=len(rep)
            rec["replica_outside_clear_rate"]=sum(not within(x,lo,hi) for x in rep)/len(rep)
            rec["replica_outside_check_rate"]=sum(not within(x,clo,chi) for x in rep)/len(rep)
        result["metrics"][name]=rec
    if ready: result["state"]="FROZEN_PENDING_HOLDOUT"
    return result


def finalize(frozen: dict, holdout_watch: Path) -> dict:
    hw=metric_watch_values(holdout_watch); policy=frozen["policy"]; calibrated=0
    out=json.loads(json.dumps(frozen))
    for name,rec in out["metrics"].items():
        if rec.get("status")!="FROZEN_PENDING_HOLDOUT": continue
        vals=[x for _,x in hw.get(name,[])]
        rec["holdout_watches"]=len(vals)
        if len(vals)<int(policy["min_holdout_watches"]):
            rec["status"]="INSUFFICIENT_HOLDOUT";rec["reason"]="not enough independent holdout watches";continue
        clear=sum(within(x,rec["clear_low"],rec["clear_high"]) for x in vals)/len(vals)
        check=sum(within(x,rec["check_low"],rec["check_high"]) for x in vals)/len(vals)
        rec["holdout_clear_rate"]=clear;rec["holdout_check_rate"]=check
        if check>=float(policy["holdout_check_rate_min"]) and clear>=float(policy["holdout_clear_rate_min"]):
            rec["status"]="CALIBRATED";calibrated+=1
        else:
            rec["status"]="REJECTED_HOLDOUT";rec["reason"]="frozen thresholds failed untouched holdout"
    out["state"]="CALIBRATED" if calibrated else "NO_CALIBRATABLE_METRICS"
    out["calibrated_metric_count"]=calibrated
    return out


def markdown(r: dict) -> str:
    L=[f"# Watch-family calibration: {r['model']}","",f"State: **{r['state']}**","",
       "Limits are fixed from genuine development watches. Validation and holdout can only reject them. Replica data is stress-test evidence only.",""]
    L+=["| metric | status | dev | val | holdout | clear band | check band |","|---|---|---:|---:|---:|---|---|"]
    for m,x in r["metrics"].items():
        def band(a,b): return "–" if a not in x else f"{x[a]:.6g} .. {x[b]:.6g}"
        L.append(f"| {m} | {x.get('status')} | {x.get('development_watches',0)} | {x.get('validation_watches',0)} | {x.get('holdout_watches',0)} | {band('clear_low','clear_high')} | {band('check_low','check_high')} |")
        if x.get("reason"): L.append(f"\n{x['reason']}\n")
    return "\n".join(L)+"\n"


def save(obj: dict, path: Path):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(obj,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    path.with_suffix(".md").write_text(markdown(obj),encoding="utf-8")

if __name__=="__main__":
    import argparse
    ap=argparse.ArgumentParser();ap.add_argument("config",type=Path);ap.add_argument("dev_watch",type=Path);ap.add_argument("dev_repeat",type=Path);ap.add_argument("val_watch",type=Path);ap.add_argument("out",type=Path);ap.add_argument("--rep-watch",type=Path);ap.add_argument("--holdout-watch",type=Path);a=ap.parse_args()
    c=json.loads(a.config.read_text());r=propose(c,a.dev_watch,a.dev_repeat,a.val_watch,a.rep_watch)
    if a.holdout_watch: r=finalize(r,a.holdout_watch)
    save(r,a.out)
