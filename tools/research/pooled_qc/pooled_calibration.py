#!/usr/bin/env python3
"""Read-only pooled genuine Rolex hour-marker analysis; never modifies the app."""
import csv, hashlib, json, math, random
from collections import Counter,defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
DATA=ROOT/"tools/research/marker_audit/results"
OUTPUT=ROOT/"pooled-calibration-output"
FAMILIES={"GMT":"gmt","No-Date":"nodate","Date":"date"}
ROUND=frozenset((1,2,4,5,7,8,10,11))
BATON=frozenset((6,9))
# shape, common positions, extreme direction, expected common positions
METRICS={
 "round_position":("round",ROUND,"high",8),
 "baton_position":("baton",BATON,"high",2),
 "baton_rotation":("baton",BATON,"high",2),
 "triangle_side":("triangle",frozenset((12,)),"high",1),
 "triangle_radial":("triangle",frozenset((12,)),"high",1),
 "triangle_rotation":("triangle",frozenset((12,)),"high",1),
 "triangle_gap":("triangle",frozenset((12,)),"low",1),
}
# Only contextual. Production adjusts resolution by 0.5 px and user settings.
DEFAULTS=dict(round_position=1.,baton_position=1.,baton_rotation=1.75,
              triangle_side=1.,triangle_radial=1.25,triangle_rotation=1.2,
              triangle_gap=2.2)
def read(p):
 with p.open(newline="",encoding="utf-8") as f:return list(csv.DictReader(f))
def value(x):
 try:
  v=float(x)
  return v if math.isfinite(v) else None
 except (TypeError,ValueError):return None
def median(seq):
 a=sorted(seq)
 if not a:return None
 n=len(a);return (a[(n-1)//2]+a[n//2])/2
def quantile(seq,p):
 a=sorted(seq)
 if not a:return None
 k=p*(len(a)-1);i=math.floor(k);j=math.ceil(k)
 return a[i]+(a[j]-a[i])*(k-i)
def mad(seq):
 m=median(seq)
 return None if m is None else median([abs(x-m) for x in seq])
def bootstrap(seq,seed):
 if not seq:return [None,None]
 rng=random.Random(seed);a=[]
 for _ in range(350):
  sample=[seq[rng.randrange(len(seq))] for _ in seq]
  a.append(median(sample))
 return [quantile(a,.025),quantile(a,.975)]
def measure(m,r):
 side=value(r["side_pct"]);radial=value(r["inout_pct"])
 rot=value(r["tilt_line_deg"]);gap=value(r["gap_pct"])
 if m.endswith("_position"):return math.hypot(side,radial) if side is not None and radial is not None else None
 if m.endswith("_rotation"):return abs(rot) if rot is not None else None
 if m=="triangle_side":return abs(side) if side is not None else None
 if m=="triangle_radial":return abs(radial) if radial is not None else None
 if m=="triangle_gap":return gap
 raise ValueError(m)
def study(strict=False):
 # Single watch/hour measurement first; multiple photos of one watch cannot multiply votes.
 perhour=defaultdict(list);counts={}
 for family,prefix in FAMILIES.items():
  suitability=read(DATA/(prefix+"-suitability.csv"))
  allow={r["photo_id"] for r in suitability if r["eligible"]=="yes"}
  if len({r["photo_id"] for r in suitability})!=len(suitability) or not allow:
   raise ValueError("Suitability data missing or duplicated: "+family)
  readings=read(DATA/(prefix+"-after.csv"))
  excluded=Counter();seen=set();usable=0
  for r in readings:
   key=(r["photo_id"],r["hour"])
   if key in seen:raise ValueError("Duplicated photo/hour "+repr(key))
   seen.add(key)
   if r["photo_id"] not in allow:excluded["independent photo suitability"]+=1;continue
   if r["gate"]:excluded["measurement gate"]+=1;continue
   if r["uncertain"]:excluded["uncertain metal edge"]+=1;continue
   radius=value(r["rpx"]);cover=value(r["cover"])
   if radius is None or radius<200:excluded["small dial"]+=1;continue
   if cover is None or cover<.5:excluded["insufficient observed edge"]+=1;continue
   if strict:
    rms=value(r["fit_rms_px"]);bad=value(r["bad_frac"])
    if cover<.8 or rms is None or rms>.75 or bad is None or bad>.25:
     excluded["strict edge sensitivity"]+=1;continue
   usable+=1
   hour=int(r["hour"])
   for metric,(shape,hours,_,_) in METRICS.items():
    if r["shape"]!=shape or hour not in hours:continue
    n=measure(metric,r)
    if n is not None:perhour[(family,r["watch"],metric,hour)].append((n,r["photo_id"]))
  counts[family]={"eligible_photos":len(allow),"photos_assessed":len({r["photo_id"] for r in readings}),
    "independent_watches":len({r["watch"] for r in readings}),
    "original_rows":len(readings),"quality_usable_rows":usable,"exclusions":dict(excluded)}
 bywatch=defaultdict(list)
 for (family,watch,metric,hour),vv in perhour.items():
  bywatch[(family,watch,metric)].append((hour,median([x for x,_ in vv]),sorted(set(p for _,p in vv))))
 out=[]
 for (family,watch,metric),vv in bywatch.items():
  direction=METRICS[metric][2];needed=METRICS[metric][3]
  worst=(min if direction=="low" else max)(vv,key=lambda x:x[1])
  out.append({"family":family,"watch":watch,"metric":metric,
   "typical":median([x[1] for x in vv]),"extreme":worst[1],
   "hour":worst[0],"photos":worst[2],"complete":len(vv)==needed,"n_hours":len(vv)})
 return out,counts
def holdout(r):
 x=r["family"]+"/"+r["watch"]
 return int(hashlib.sha256(x.encode()).hexdigest()[:8],16)%5==0
def outside(x,limit,direction):
 return x<limit if direction=="low" else x>limit
def f(x):return "-" if x is None else "%.3f"%x
def main():
 all_,counts=study()
 strict,strictcounts=study(strict=True)
 OUTPUT.mkdir(exist_ok=True)
 result={"method":"Per-hour median across photos, one physical watch per metric, extremes retained",
  "quality_exclusion_not_based_on_state":True,"counts":counts,"strict_counts":strictcounts,
  "metrics":{},"qc_controls":[],
  "limitations":["This is research: no app code or thresholds changed",
   "Median/MAD do not prove an outlier is a bad photograph",
   "Held-out exceedance does not establish actual false-positive rate",
   "Original Yacht-Master/QC defect photos required for exact reproducible analysis",
   "Candidate thresholds are scalar comparisons without the app's per-photo resolution allowance"]}
 lines=["# Watch Align: shared Rolex calibration pilot","",
  "**Read-only research. No production thresholds, model configuration or detector have changed.**","",
  "Baseline photographic suitability and measured-edge quality decide eligibility. "
  "No row is removed for being outside a proposed tolerance or because it was marked POSSIBLE/CLEARLY_OFF. "
  "Each marker hour on a physical watch is represented once regardless of photo count. "
  "For comparable baton geometry only 6 and 9 are included.",
  "","## Input provenance and selection","",
  "| Genuine family | Eligible photos | Physical watches in source | Reliable marker rows | Strict-quality marker rows |",
  "|---|---:|---:|---:|---:|"]
 for family in FAMILIES:
  a,b=counts[family],strictcounts[family]
  lines.append("| %s | %d | %d | %d | %d |"%(family,a["eligible_photos"],a["independent_watches"],a["quality_usable_rows"],b["quality_usable_rows"]))
 lines+=["","## Common-shape distributions",
  "Each family median and MAD uses the median of the available common positions **within each independent watch**. "
  "For the tail, only watches with ALL relevant common hour positions reliably observed count towards the worst-marker P95. "
  "A watch is not allowed more votes because it has more photographs.",
  "","| Metric | Family | Watches | Complete watches | Median | MAD | Worst-marker P95 | Bootstrap median 95% interval |",
  "|---|---|---:|---:|---:|---:|---:|---|"]
 tail=["","## Exploratory ONE-SIZE thresholds (not deployed)",
  "Train on 80% of watches, holding 20% out by deterministic physical-watch ID. "
  "Candidate = median of complete-watch worst-markers +/- 4 × 1.4826 × MAD (minus for gap). "
  "This is an exploratory sensitivity setting, NOT a validated final tolerance.",
  "","| Metric | Training watches | Candidate | Current unadjusted default | Hold-out watches outside candidate | By family GMT / No-Date / Date |",
  "|---|---:|---:|---:|---:|---|"]
 for idx,(m,(shape,hours,sign,n)) in enumerate(METRICS.items()):
  subset=[r for r in all_ if r["metric"]==m]
  byfamily={}
  for j,family in enumerate(FAMILIES):
   vv=[r for r in subset if r["family"]==family]
   typical=[r["typical"] for r in vv]
   extremes=[r["extreme"] for r in vv if r["complete"]]
   ci=bootstrap(typical,20261020+idx*5+j)
   byfamily[family]={"watches":len(vv),"complete":len(extremes),
    "median":median(typical),"mad":mad(typical),"complete_worst_p95":quantile(extremes,.95),
    "median_bootstrap_ci95":ci,
    "strict_quality_watches":len([r for r in strict if r["family"]==family and r["metric"]==m])}
   lines.append("| %s | %s | %d | %d | %s | %s | %s | %s–%s |"%(
     m,family,len(vv),len(extremes),f(median(typical)),f(mad(typical)),
     f(quantile(extremes,.95)),f(ci[0]),f(ci[1])))
  train=[r["extreme"] for r in subset if r["complete"] and not holdout(r)]
  test=[r for r in subset if r["complete"] and holdout(r)]
  if len(train)<20 or len(test)<5:raise ValueError("Not enough complete independent watches: %s (%d / %d)"%(m,len(train),len(test)))
  centre=median(train);disp=1.4826*mad(train)
  proposed=centre-4*disp if sign=="low" else centre+4*disp
  out=[r for r in test if outside(r["extreme"],proposed,sign)]
  family_results={fam:{"tested":len([r for r in test if r["family"]==fam]),
                        "outside":len([r for r in out if r["family"]==fam])} for fam in FAMILIES}
  result["metrics"][m]={"direction":sign,"family":byfamily,"candidate_4_mad":proposed,
   "current_base_default":DEFAULTS[m],"training_complete_watches":len(train),
   "holdout_complete_watches":len(test),"holdout_outside":len(out),
   "holdout_family":family_results,
   "untrimmed_extremes":sorted(
     [{"family":r["family"],"watch":r["watch"],"hour":r["hour"],
       "value":r["extreme"],"photo_ids":r["photos"],"complete":r["complete"]} for r in subset],
      key=lambda z:z["value"],reverse=sign!="low")[:20]}
  family_note=" / ".join("%d/%d"%(family_results[fam]["outside"],family_results[fam]["tested"]) for fam in FAMILIES)
  tail.append("| %s | %d | %s | %s | %d/%d | %s |"%(
    m,len(train),f(proposed),f(DEFAULTS[m]),len(out),len(test),family_note))
 tail+=["","## Pre-existing replica QC controls",
  "These are named examples, not a complete independently labelled truth set. "
  "A candidate exceeding a scalar tolerance is NOT the same as an actual production QC verdict.",
  "","| Case | Marker | Existing state | Pooled candidate comparison |",
  "|---|---|---|---|"]
 for name,file,pid,h,features in [
    ("Known GMT crooked 6","gmt_126710-after.csv","GMT_crooked6_1s4cszq",6,
     ("baton_position","baton_rotation")),
    ("Sub 12 QC control","submariner_124060-after.csv","SUB_rejected12_1wgfy7k",12,
     ("triangle_side","triangle_radial","triangle_rotation","triangle_gap"))]:
  found=[r for r in read(DATA/"qc"/file) if r["photo_id"]==pid and int(r["hour"])==h]
  if len(found)!=1:raise ValueError("Expected QC control missing: "+name)
  row=found[0];items={}
  for m in features:
   v=measure(m,row);t=result["metrics"][m]["candidate_4_mad"]
   items[m]={"observed":v,"candidate":t,"exceeds":v is not None and outside(v,t,METRICS[m][2])}
  result["qc_controls"].append({"name":name,"existing_state":row["state"],"features":items})
  tail.append("| %s | %d | %s | %s |"%(name,h,row["state"],
   "; ".join("%s %s/%s%s"%(m,f(v["observed"]),f(v["candidate"])," *" if v["exceeds"] else "") for m,v in items.items())))
 tail+=["","## Before any APK or release change",
  "1. Investigate **why** the GMT baton positions differ from Submariner positions, including nominal geometry and photo/source bias, before claiming distributions are interchangeable.",
  "2. Inspect the original photographs and edge traces for each extreme; never discard a reading merely because it is numerically large.",
  "3. Review held-out genuine exceedances AND confirmed replica defects, including the owner's defective GMT triangle and Yacht-Master example, using their original full-resolution images.",
  "4. Check position, rotation and gap for **each individual marker**; don't average a bad marker into an overall watch pass.",
  "5. If evidence supports common tolerances, prototype a Date (11 marker) and No-Date (12 marker) generic layout with date-window alignment as a separately calibrated measurement.",
  ""]
 (OUTPUT/"results.json").write_text(json.dumps(result,indent=2,sort_keys=True)+"\n")
 (OUTPUT/"REPORT.md").write_text("\n".join(lines+tail))
 print("POOLED_INPUT",json.dumps({k:{"eligible_photos":v["eligible_photos"],"usable_rows":v["quality_usable_rows"]} for k,v in counts.items()}))
 for name,v in result["metrics"].items():
  print("POOLED_CANDIDATE",name,f(v["candidate_4_mad"]),"holdout outside",v["holdout_outside"],"/",v["holdout_complete_watches"])
 print("WROTE",OUTPUT/"REPORT.md")
if __name__=="__main__":main()
