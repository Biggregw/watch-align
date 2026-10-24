#!/usr/bin/env python3
"""Read-only research: measure reviewed Bob's Watches photographs through Alpha99Pipeline.

Calls the repository's existing desktop-harness AlignmentCalib driver with the
current production model assets and alignment references. Never changes them.
Input CSV and original bytes come from the established private Dropbox inbox.
"""
from __future__ import annotations
import collections,csv,hashlib,io,json,math,os,statistics,subprocess
from datetime import datetime,timezone
from pathlib import Path
import requests

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/"research_output"/"bobs_production_parity"
IMAGES=OUT/"images"
DBX_META="https://api.dropboxapi.com/2"
DBX_CONTENT="https://content.dropboxapi.com/2"
SHORTLIST="/Watch Align/Incoming Photos/_reviewed_bobs_49_faceon_visual_shortlist_20261024.csv"
ROUTES={"124060":"submariner_124060","126610LN":"submariner_126610",
        "126610LV":"submariner_126610","126710BLNR":"gmt_126710",
        "126710BLRO":"gmt_126710"}
SKIPPED={"126622":"No validated production 126622 model assets; do not substitute another model"}

def read_file(s,t,path):
    r=s.post(DBX_CONTENT+"/files/download",headers={"Authorization":"Bearer "+t,
           "Dropbox-API-Arg":json.dumps({"path":path})},timeout=90)
    r.raise_for_status()
    return r.content

def write_file(s,t,path,data):
    r=s.post(DBX_CONTENT+"/files/upload",headers={
       "Authorization":"Bearer "+t,"Content-Type":"application/octet-stream",
       "Dropbox-API-Arg":json.dumps({"path":path,"mode":"add","autorename":False,
                                           "strict_conflict":True,"mute":True})},
       data=data,timeout=120)
    r.raise_for_status()
    saved=read_file(s,t,path)
    if saved!=data:raise RuntimeError("Dropbox results upload readback mismatch at "+path)

def f(s):
    try:
        x=float(s)
        return x if math.isfinite(x) else None
    except (TypeError,ValueError):return None

def pct(a,q):
    z=sorted(a)
    if not z:return None
    p=(len(z)-1)*q
    lo=int(p);hi=min(len(z)-1,lo+1)
    return z[lo]*(1-(p-lo))+z[hi]*(p-lo)

def compare(rows, asset, title):
    ref=json.loads((ROOT/"android/app/src/main/assets/models"/asset/"alignment/luminous_alignment.json").read_text())
    counts=collections.Counter(r.get("outcome","") for r in rows if r.get("hour",""))
    contributing=set(r["physical_watch_id"] for r in rows if r.get("fit_assessed")=="1")
    measured=collections.defaultdict(dict)
    for r in rows:
        hour=r.get("hour","")
        if not hour or r.get("fit_assessed")!="1":continue
        shape=r.get("shape")
        nominal=ref.get("hours",{}).get(hour,{})
        for component,key,ok,nom,limit in [
          ("radial","radial_R","radial_ok","radial_R","radial_limit_R"),
          ("tangential","tangential_R","tangential_ok","tangential_R","tangential_limit_R"),
          ("rotation","rotation_deg","rotation_ok","rotation_deg","rotation_limit_deg")]:
            if r.get(ok)!="1":continue
            v=f(r.get(key));n=f(nominal.get(nom))
            if v is None or n is None:continue
            # same physical watch/hour/component is one observation, regardless of repeat photos
            k=(shape,component,hour,r["physical_watch_id"])
            measured[k][r["photo_id"]]=v-n
    summary=[]
    for shape in ("triangle","baton","round"):
      for component in ("radial","tangential","rotation"):
        if shape=="round" and component=="rotation":continue
        metric=collections.defaultdict(list)
        for (sp,comp,hour,wid), photos in measured.items():
            if sp==shape and comp==component:
                metric[wid].extend(abs(statistics.median(list(photos.values()))) for _ in [0])
        values=[x for x in metric.values() for x in x]
        suffix="rotation_limit_deg" if component=="rotation" else component+"_limit_R"
        existing=f(ref.get("shapes",{}).get(shape,{}).get(suffix))
        record={"shape":shape,"component":component,"watches_with_measurement":len(metric),
          "existing_production_limit":existing,"max_observed_abs_delta":max(values) if values else None,
          "p95_observed_abs_delta":pct(values,0.95),"median_observed_abs_delta":statistics.median(values) if values else None,
          "watches_beyond_old_limit":sum(x>existing for x in values) if existing is not None else None}
        summary.append(record)
    return {"model_group":title,"app_asset":asset,"photo_rows":len(set(r.get("photo_id") for r in rows)),
       "watches_with_assessed_marker":len(contributing),"marker_outcomes":dict(counts),
       "observed_absolute_deviations":summary,
       "limitation":"One dealer; observed deviations do not establish new safe production limits. Existing refs, gates and verdicts untouched."}

def main():
    tok=os.environ.get("DROPBOX_ACCESS_TOKEN")
    if not tok:raise RuntimeError("Missing WATCHALIGN_DROPBOX_ACCESS_TOKEN GitHub secret")
    s=requests.Session()
    OUT.mkdir(parents=True,exist_ok=True); IMAGES.mkdir(parents=True,exist_ok=True)
    manifest=read_file(s,tok,SHORTLIST).decode("utf-8-sig")
    originals=list(csv.DictReader(io.StringIO(manifest)))
    issues=[];accepted=[];unsupported=[];groups=collections.defaultdict(list)
    for rec in originals:
        watch=rec.get("watch_id","")
        model=rec.get("model","")
        path=rec.get("primary_dropbox_path","")
        sha=rec.get("primary_sha256","")
        if model in SKIPPED:
            unsupported.append({"model":model,"watch":watch,"reason":SKIPPED[model]})
            continue
        if model not in ROUTES:issues.append({"watch":watch,"error":"unknown_model_"+model});continue
        if not path.startswith("/Watch Align/Incoming Photos/") or len(sha)!=64:
            issues.append({"watch":watch,"error":"invalid_staging_path_or_hash"});continue
        try:
            content=read_file(s,tok,path)
            observed=hashlib.sha256(content).hexdigest()
            if observed!=sha:raise RuntimeError("SHA-256 mismatch: "+path)
            image=IMAGES/(sha+Path(path).suffix.lower())
            image.write_bytes(content)
            row={"photo_id":watch+"_"+sha[:10],"local_path":image.name,"physical_watch_id":watch}
            groups[ROUTES[model]].append(row)
            accepted.append({"model":model,"watch":watch,"image_sha256":sha,"bytes":len(content),"route":ROUTES[model]})
        except Exception as e:issues.append({"watch":watch,"path":path,"error":str(e)[:350]})
    if not accepted:raise RuntimeError("No source files acquired; errors: "+repr(issues[:3]))
    allraw=[];comparisons=[]
    for asset,photos in groups.items():
        manifest_path=OUT/(asset+"_manifest.csv")
        with manifest_path.open("w",newline="") as h:
            w=csv.DictWriter(h,fieldnames=["photo_id","local_path","physical_watch_id"]);w.writeheader();w.writerows(photos)
        measurement=OUT/(asset+"_production_marker_results.csv")
        reference=ROOT/"android/app/src/main/assets/models"/asset/"alignment/luminous_alignment.json"
        env=dict(os.environ);env["JAVA_TOOL_OPTIONS"]="-Dwatchalign.model="+asset
        subprocess.run(["bash","tools/desktop-harness/run.sh","AlignmentCalib","decide",
                        str(manifest_path),str(IMAGES),str(measurement),str(reference)],cwd=ROOT,env=env,check=True)
        with measurement.open(newline="") as fh: data=list(csv.DictReader(fh))
        allraw.extend(data)
        comparisons.append(compare(data,asset,asset))
    with (OUT/"all_supported_production_markers.csv").open("w",newline="") as fh:
        fields=list(allraw[0]) if allraw else ["photo_id"]
        w=csv.DictWriter(fh,fieldnames=fields);w.writeheader();w.writerows(allraw)
    now=datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    result={"method":"EXISTING_PRODUCTION_ALIGNMENT_PIPELINE_AND_ASSETS_READ_ONLY",
      "shortlist":SHORTLIST,"model_sources":"single dealer; limited diversity",
      "reviewed_shortlist_rows":len(originals),"supported_photos_hash_verified":len(accepted),
      "unsupported_watches":unsupported,"fetch_errors":issues,
      "production_routes":[{"asset":x["app_asset"],"photographs":x["photo_rows"],
                            "watches_assessed":x["watches_with_assessed_marker"]} for x in comparisons],
      "comparisons":comparisons,"production_reference_modified":False,
      "production_threshold_modified":False,
      "commercial_reuse_rights":"not verified; offline research only"}
    (OUT/"comparison_summary.json").write_text(json.dumps(result,indent=2)+"\n")
    md=["# Bob's 49-watch research: current production measurement comparison","",
      f"Source shortlist entries: {len(originals)}; original bytes fetched and hash-verified: {len(accepted)}; unavailable model watches: {len(unsupported)}; download/verification failures: {len(issues)}.","",
      "Read-only existing AlignmentCalib → Alpha99Pipeline; no new measurement engine or production threshold.","",
      "| Production model route | Images measured | Watches with assessed markers | Marker outcomes |",
      "|---|---:|---:|---|"]
    for z in comparisons:
        md.append(f"| {z['app_asset']} | {z['photo_rows']} | {z['watches_with_assessed_marker']} | {z['marker_outcomes']} |")
    md.extend(["","The CSVs contain original per-marker component readings and eligibility, not guessed tolerances.",
          "No independent Yacht-Master model asset exists; those seven images were not processed using a wrong reference.",
          "All candidate numeric envelopes are single-dealer research observations, not proposals to change production limits."])
    (OUT/"comparison_report.md").write_text("\n".join(md)+"\n")
    prefix="/Watch Align/Incoming Photos/_calibration_research_"+now
    for file,suffix in [(OUT/"comparison_summary.json",".json"),(OUT/"comparison_report.md",".md"),
                        (OUT/"all_supported_production_markers.csv",".csv")]:
        write_file(s,tok,prefix+suffix,file.read_bytes())
    print("RESEARCH_RESULT "+json.dumps({"saved_prefix":prefix,"shortlist":len(originals),
       "verified_photos":len(accepted),"unsupported":len(unsupported),"errors":len(issues),
       "routes":result["production_routes"],
       "marker_rows":len(allraw),"summary":comparisons},ensure_ascii=False),flush=True)
if __name__=="__main__":main()
