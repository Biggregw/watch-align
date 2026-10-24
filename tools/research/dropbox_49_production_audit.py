#!/usr/bin/env python3
"""Read-only Watch Align production alignment study using reviewed Dropbox originals.

The app's unmodified AlignmentCalib desktop driver is called by the companion
GitHub workflow. No thresholds, model assets or production code are written.
One watch is one stock ID, regardless of number of photos. All outputs are research.
"""
from __future__ import annotations
import argparse,collections,csv,hashlib,io,json,math,os,pathlib,statistics,subprocess,sys
from datetime import datetime,timezone
import requests

DROPBOX="https://content.dropboxapi.com/2"
ROOT="/Watch Align/Incoming Photos"
OUT_ROOT=ROOT+"/Calibration Research"
SHORTLIST=ROOT+"/_reviewed_bobs_49_faceon_visual_shortlist_20261024.csv"
PHOTO_INDEX=ROOT+"/_reviewed_bobs_314_visual_photo_index_20261024.csv"
ROUTES={
"submariner_124060":{"124060"},
"submariner_126610":{"126610LN","126610LV"},
"gmt_126710":{"126710BLNR","126710BLRO"}}
INCLUDE={"PRIMARY_FACE_ON","ALTERNATE_FACE_ON_SAME_VIEW"}
METRICS=(("radial","radial_R","radial_ok","radial_R"),
         ("tangential","tangential_R","tangential_ok","tangential_R"),
         ("rotation","rotation_deg","rotation_ok","rotation_deg"))

class StudyError(Exception):pass

def sha(b):return hashlib.sha256(b).hexdigest()
def dbx_download(s,token,path):
    r=s.post(DROPBOX+"/files/download",headers={"Authorization":"Bearer "+token,
        "Dropbox-API-Arg":json.dumps({"path":path})},timeout=90)
    if not r.ok:raise StudyError("Dropbox download "+str(r.status_code)+" "+path+" "+r.text[:120])
    return r.content

def dbx_upload_verified(s,token,path,content):
    args={"path":path,"mode":"add","autorename":False,"strict_conflict":True,"mute":True}
    r=s.post(DROPBOX+"/files/upload",headers={"Authorization":"Bearer "+token,
        "Dropbox-API-Arg":json.dumps(args),"Content-Type":"application/octet-stream"},
        data=content,timeout=90)
    if not r.ok:raise StudyError("Dropbox upload "+str(r.status_code)+" "+path+" "+r.text[:130])
    actual=dbx_download(s,token,path)
    if len(actual)!=len(content) or sha(actual)!=sha(content):raise StudyError("Dropbox READBACK_MISMATCH "+path)

def csv_bytes(b):
    return list(csv.DictReader(io.StringIO(b.decode("utf-8-sig"))))
def nfloat(x):
    try:
        x=float(x)
        return x if math.isfinite(x) else None
    except (ValueError,TypeError):return None
def quantile(v,q):
    v=sorted(v)
    if not v:return None
    a=(len(v)-1)*q;lo=math.floor(a);hi=math.ceil(a)
    return v[lo]+(v[hi]-v[lo])*(a-lo)
def dumps(obj):return (json.dumps(obj,ensure_ascii=False,indent=2,sort_keys=True)+"\n").encode()

def fetch(session,token,route,folder):
    wanted=ROUTES[route]
    selected=csv_bytes(dbx_download(session,token,SHORTLIST))
    views=csv_bytes(dbx_download(session,token,PHOTO_INDEX))
    valid={(r["model"],r["stock"]):r for r in selected if r["model"] in wanted}
    if len(valid)!=sum(r["model"] in wanted for r in selected):raise StudyError("duplicate shortlist watch ID")
    if not valid:raise StudyError("no watches for route")
    views=[r for r in views if (r["model"],r["stock"]) in valid and r["image_review"] in INCLUDE]
    # Verify that there is precisely one identified primary for each watch.
    bystock=collections.defaultdict(list)
    for r in views:bystock[(r["model"],r["stock"])].append(r)
    for key,primary in valid.items():
        rows=bystock.get(key,[])
        if len([r for r in rows if r["image_review"]=="PRIMARY_FACE_ON"])!=1:
            raise StudyError("unexpected primary count "+str(key))
        if rows[0].get("hash_verified")!="YES":raise StudyError("unverified manifest file")
        if not any(r["sha256"]==primary["primary_sha256"] and r["image_review"]=="PRIMARY_FACE_ON" for r in rows):
            raise StudyError("shortlist primary does not match visual index "+str(key))
    folder.mkdir(parents=True,exist_ok=True)
    imroot=folder/"images";imroot.mkdir(exist_ok=True)
    manifest=[];errors=[];counts=collections.Counter()
    for r in views:
        path=r["dropbox_path"]
        model,stock=r["model"],r["stock"]
        if not path.startswith(ROOT+"/"+model+"/") or not path.endswith(".jpg"):
            errors.append({"stock":stock,"path":path,"reason":"unsafe_image_path"});continue
        try:
            content=dbx_download(session,token,path)
            if sha(content)!=r["sha256"]:raise StudyError("SHA mismatch")
            if len(content)!=int(r["bytes"]):raise StudyError("bytes mismatch")
            local=r["sha256"]+".jpg";dest=imroot/local
            if not dest.exists():dest.write_bytes(content)
            manifest.append({"photo_id":stock+"_"+r["sha256"][:12],
                "physical_watch_id":"bobs_"+stock,"local_path":local,
                "model":model,"stock":stock,"review":r["image_review"],
                "sha256":r["sha256"],"source":path})
            counts[r["image_review"]]+=1
        except Exception as e:errors.append({"stock":stock,"path":path,"reason":str(e)[:180]})
    # Do not publish a misleading watch count from partial downloaded records.
    if errors:raise StudyError("Incomplete input acquisition: "+json.dumps(errors[:6]))
    if len(manifest)!=len(views):raise StudyError("acquired view mismatch")
    if len({r["physical_watch_id"] for r in manifest})!=len(valid):raise StudyError("some watched primary is missing")
    csv_write(folder/"manifest.csv",manifest)
    return {"watches":len(valid),"photos":len(manifest),"views":dict(counts),
       "model_watch_counts":dict(collections.Counter(r["model"] for r in selected if r["model"] in wanted)),
       "recorded_duplicate_note":"Stock 191110 already occurs in earlier 417-file archive; do not double count in a later combined cohort."}

def csv_write(path,rows):
    if not rows:return path.write_text("")
    with path.open("w",newline="",encoding="utf-8") as f:
        wr=csv.DictWriter(f,fieldnames=list(rows[0]));wr.writeheader();wr.writerows(rows)

def run_harness(route,folder):
    assets=pathlib.Path("android/app/src/main/assets/models")/route/"alignment"/"luminous_alignment.json"
    existing=json.loads(assets.read_text())
    setups={}
    for shape,ref in existing["shapes"].items():
        setups[shape]={"inset_R":float(ref["inset_R"]),
            "fixed_growth_R":0.0,
            "growth_nominal_R":float(ref["growth_nominal_R"] or 0.0),
            "reject":1.0}
    (folder/"existing_setups.json").write_text(json.dumps(setups,indent=2)+"\n")
    env=os.environ.copy();env["JAVA_TOOL_OPTIONS"]="-Dwatchalign.model="+route
    # Run existing source code, no production modifications. One initial compile.
    decide=folder/"existing_app_decisions.csv"
    cmd=["bash","tools/desktop-harness/run.sh","AlignmentCalib","decide",
         str(folder/"manifest.csv"),str(folder/"images"),str(decide),str(assets)]
    with (folder/"production_harness.log").open("w") as log:
        proc=subprocess.run(cmd,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=2700)
    if proc.returncode or not decide.exists():
        raise StudyError("Production harness failed; inspect production_harness.log (exit "+str(proc.returncode)+")")
    jar=pathlib.Path("tools/desktop-harness/.cache/opencv-4.9.0-0.jar")
    classes=pathlib.Path("tools/desktop-harness/.cache/classes")
    cmd=["java","-Xmx2g","-Dwatchalign.model="+route,"-cp",str(jar)+":"+str(classes),
         "com.watchalign.mobile.AlignmentCalib","fit",
         str(folder/"manifest.csv"),str(folder/"images"),
         str(folder/"unmodified_app_fits.csv"),str(folder/"existing_setups.json")]
    with (folder/"production_harness.log").open("a") as log:
        proc=subprocess.run(cmd,env=os.environ.copy(),stdout=log,stderr=subprocess.STDOUT,timeout=2700)
    if proc.returncode:raise StudyError("Raw production fit failed (exit "+str(proc.returncode)+")")

def analyze(route,folder,inputs):
    dec=list(csv.DictReader(open(folder/"existing_app_decisions.csv")))
    fits=list(csv.DictReader(open(folder/"unmodified_app_fits.csv")))
    files=list(csv.DictReader(open(folder/"manifest.csv")))
    byphoto={r["photo_id"]:r for r in files}
    saw=set(r.get("photo_id") for r in dec)
    missing=set(byphoto)-saw
    if missing:raise StudyError("No decision output for photos: "+str(sorted(missing)[:5]))
    # Align fit rows to the app's production decision outputs and use quality flags.
    fitkey={(r["photo_id"],r["hour"]):r for r in fits if r.get("hour")}
    missing_fit=[(r["photo_id"],r["hour"]) for r in dec if r.get("hour") and (r["photo_id"],r["hour"]) not in fitkey]
    if missing_fit:raise StudyError("Fit/decision marker-row mismatch: "+str(missing_fit[:4]))
    existing=json.loads((pathlib.Path("android/app/src/main/assets/models")/route/"alignment"/"luminous_alignment.json").read_text())
    nom=existing["hours"]
    measurements=[];allrows=[];by_watch=collections.defaultdict(list)
    for r in dec:
        if not r.get("hour"):continue
        f=fitkey[(r["photo_id"],r["hour"])]
        marker={"photo_id":r["photo_id"],"physical_watch_id":r["physical_watch_id"],
            "model":byphoto[r["photo_id"]]["model"],"hour":r["hour"],"shape":r["shape"],
            "outcome":r["outcome"],"fit_assessed":r.get("fit_assessed"),"dial_r_px":r.get("dial_r_px"),
            "check":r.get("check")}
        for _,field,ok,_ in METRICS:
            marker[field]=r.get(field)
            marker[ok]=r.get(ok)
        allrows.append(marker)
        by_watch[r["physical_watch_id"]].append(marker)
        for name,field,gate,nomname in METRICS:
            v=nfloat(r.get(field))
            if v is None or r.get(gate)!="1" or r.get("fit_assessed")!="1":continue
            baseline=nfloat((nom.get(str(r["hour"])) or {}).get(nomname))
            if baseline is None:continue
            measurements.append({"watch":r["physical_watch_id"],"model":byphoto[r["photo_id"]]["model"],
                "photo":r["photo_id"],"hour":int(r["hour"]),"shape":r["shape"],"component":name,
                "value":v,"nominal":baseline,"delta":v-baseline,"outcome":r["outcome"]})
    if not allrows:raise StudyError("No marker data was produced")
    per_watch=collections.defaultdict(list)
    for x in measurements:per_watch[(x["watch"],x["model"],x["hour"],x["shape"],x["component"])].append(x["delta"])
    by_metric=collections.defaultdict(list)
    within=collections.defaultdict(list)
    watchvalue_rows=[]
    for (watch,model,hour,shape,comp),vals in per_watch.items():
        median=statistics.median(vals)
        key=(model,shape,comp)
        by_metric[key].append((watch,hour,median))
        if len(vals)>1:within[key].append(max(vals)-min(vals))
        watchvalue_rows.append({"physical_watch_id":watch,"model":model,"hour":hour,"shape":shape,
            "component":comp,"median_delta":round(median,9),"measured_photos":len(vals)})
    result=[]
    for (model,shape,comp),records in sorted(by_metric.items()):
        val=[abs(z[2]) for z in records]
        watches=len({z[0] for z in records})
        measurement_count=len(records)
        r={"model":model,"shape":shape,"component":comp,"independent_watches":watches,
           "unique_watch_hour_values":measurement_count,
           "median_absolute_deviation":round(statistics.median(val),8),
           "p90_absolute_deviation":round(quantile(val,.90),8),
           "p95_absolute_deviation":round(quantile(val,.95),8),
           "max_absolute_deviation":round(max(val),8),
           "within_watch_half_range_p90":round(quantile(within[(model,shape,comp)],.90)/2,8) if within[(model,shape,comp)] else None,
           "repeat_photo_watch_hour_groups":len(within[(model,shape,comp)]),
           "sample_status":"SMALL_ONE_DEALER_ONLY" if watches<15 else "ONE_DEALER_ONLY"}
        result.append(r)
    csv_write(folder/"per_watch_marker_medians.csv",watchvalue_rows)
    csv_write(folder/"photo_reliability.csv",allrows)
    csv_write(folder/"empirical_watch_level_spreads.csv",result)
    counts=collections.Counter(r["outcome"] for r in dec)
    distinct_photos=len({r["photo_id"] for r in dec})
    result_data={"status":"OFFLINE_EVIDENCE_ONLY_NOT_APPROVED_LIMITS",
      "model_route":route,"source":"Bob's Watches dealer-attributed; not independently authenticated",
      "input":inputs,"analyzed_photos":distinct_photos,
      "marker_rows":len(allrows),"measured_component_values":len(measurements),
      "marker_outcomes":dict(counts),
      "insufficient_or_missing_detection_photos":len([r for r in dec if r.get("outcome")=="NOPOSE"]),
      "reference_asset":"android/app/src/main/assets/models/"+route+"/alignment/luminous_alignment.json",
      "reference_provenance_caveat":"The Date/GMT app assets may inherit 124060 luminous tolerances; this is an independent observational audit, not validation of inherited limits.",
      "model_supported":True,
      "single_dealer_bias":True,
      "same_watch_resizes_not_independent_views":True,
      "yacht_master_status":"126622 model assets unavailable in repo; no surrogate GMT/Date measurements were run.",
      "metrics":result,"code_sha":os.environ.get("GITHUB_SHA"),
      "created_utc":datetime.now(timezone.utc).isoformat()}
    (folder/"summary.json").write_bytes(dumps(result_data))
    return result_data

def main():
    p=argparse.ArgumentParser();p.add_argument("--route",required=True,choices=list(ROUTES));p.add_argument("--workdir",default="/tmp/watchalign-offline");a=p.parse_args()
    work=pathlib.Path(a.workdir).resolve()/a.route;work.mkdir(parents=True,exist_ok=True)
    session=requests.Session();token=os.environ.get("DROPBOX_ACCESS_TOKEN","")
    if not token:raise StudyError("No Dropbox access token")
    inputstats=fetch(session,token,a.route,work)
    print("INPUT_READY",json.dumps(inputstats),flush=True)
    run_harness(a.route,work)
    result=analyze(a.route,work,inputstats)
    # Commit only research OUTPUTS, never photographs, references or thresholds.
    prefix="offline_20261024_"+a.route+"_"+os.environ.get("GITHUB_RUN_ID","manual")
    included=["manifest.csv","existing_app_decisions.csv","unmodified_app_fits.csv",
      "per_watch_marker_medians.csv","photo_reliability.csv","empirical_watch_level_spreads.csv",
      "summary.json","production_harness.log","existing_setups.json"]
    for filename in included:
        content=(work/filename).read_bytes()
        remote=OUT_ROOT+"/"+prefix+"_"+filename
        dbx_upload_verified(session,token,remote,content)
    print("VERIFIED_REPORT",json.dumps({"route":a.route,"report":OUT_ROOT+"/"+prefix+"_summary.json",
      "measured_photos":result["analyzed_photos"],"marker_rows":result["marker_rows"],
      "measured_component_values":result["measured_component_values"]}),flush=True)

if __name__=="__main__":
    try:main()
    except Exception as exc:
        print("RESEARCH_RUN_FAILED",type(exc).__name__,str(exc)[:500],flush=True)
        raise
