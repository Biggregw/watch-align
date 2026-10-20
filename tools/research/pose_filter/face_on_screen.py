#!/usr/bin/env python3
"""Read-only genuine photo pose screening, independent of marker scores.
No underlying inputs are deleted. Selection must be reviewed for case/crown angle.
"""
import argparse,csv,collections,math,pathlib,json
BASE=pathlib.Path("tools/research/marker_audit/results")
# Exploratory conservative sensitivity screens, NOT approved calibration thresholds.
CUTS={
 "strict": dict(ellipse=.995,affine=.995,opposite=1.010,edge=.004),
 "ultra_strict": dict(ellipse=.998,affine=.998,opposite=1.005,edge=.003),
}
LIMITS=dict(roundPos=.671,batonPos=.752,batonTilt=1.096,triSide=.437,triInout=.373,triGap=2.81,triRot=1.174)
def rows(path):
 with open(path,newline="",encoding="utf-8") as f:return list(csv.DictReader(f))
def f(x):
 try:return float(x)
 except (TypeError,ValueError):return math.nan
def gate(r,c):
 if r is None:return "EXCLUDE_unavailable"
 if r["status"]!="MEASURED":return "EXCLUDE_"+r["status"]
 x={k:f(r[k]) for k in ("ellipse_ratio","jacobian_ratio","opposite_scale_ratio","edge_rms_over_radius")}
 if not all(math.isfinite(v) for v in x.values()):return "EXCLUDE_nonfinite"
 fails=[]
 if x["ellipse_ratio"]<c["ellipse"]:fails.append("dial_ellipse")
 if x["jacobian_ratio"]<c["affine"]:fails.append("local_affine")
 if x["opposite_scale_ratio"]>c["opposite"]:fails.append("opposing_scale")
 if x["edge_rms_over_radius"]>c["edge"]:fails.append("unreliable_edge")
 return "CANDIDATE_requires_visual_crown_review" if not fails else "EXCLUDE_"+"+".join(fails)
def issue(r):
 if r["gate"]:return ["unreadable_marker"]
 R=f(r["rpx"]);allow=50/R;shape=r["shape"]
 side=f(r["side_pct"]);io=f(r["inout_pct"]);angle=f(r["tilt_line_deg"]);gap=f(r["gap_pct"]);o=[]
 if shape=="triangle":
  if math.isfinite(side) and abs(side)>LIMITS["triSide"]+allow:o.append("triangle_side")
  if math.isfinite(io) and abs(io)>LIMITS["triInout"]+allow:o.append("triangle_radial")
  if math.isfinite(gap) and gap<LIMITS["triGap"]-allow:o.append("triangle_gap")
  if math.isfinite(angle) and abs(angle)>LIMITS["triRot"]:o.append("triangle_rotation")
 else:
  if math.isfinite(side) and math.isfinite(io) and math.hypot(side,io)>(LIMITS["batonPos"] if shape=="baton" else LIMITS["roundPos"])+allow:o.append(shape+"_position")
  if shape=="baton" and math.isfinite(angle) and abs(angle)>LIMITS["batonTilt"]:o.append("baton_rotation")
 return o
def main():
 p=argparse.ArgumentParser()
 for arg in ("family","pose","manifest","measurements","out"):p.add_argument("--"+arg,required=True)
 a=p.parse_args();out=pathlib.Path(a.out);out.mkdir(parents=True,exist_ok=True)
 mani=rows(a.manifest);pose={r["photo_id"]:r for r in rows(a.pose)}
 meas=collections.defaultdict(list)
 for r in rows(a.measurements):meas[r["photo_id"]].append(r)
 output=[];coll=collections.defaultdict(list)
 expected=12 if a.family=="NoDate" else 11
 for m in mani:
  id=m["photo_id"];pr=pose.get(id);x={k:v for k,v in m.items() if k in ("photo_id","physical_watch_id","model","source","listing_url","image_url")}
  x["source_url"]=m.get("listing_url",m.get("source",""));x["pose_status"]=pr["status"] if pr else "MISSING"
  x["ellipse_ratio"]=pr["ellipse_ratio"] if pr else "";x["jacobian_ratio"]=pr["jacobian_ratio"] if pr else ""
  x["opposite_scale_ratio"]=pr["opposite_scale_ratio"] if pr else "";x["edge_rms_over_radius"]=pr["edge_rms_over_radius"] if pr else ""
  for name,c in CUTS.items():x[name]=gate(pr,c)
  found={r["hour"]:r for r in meas.get(id,[])}
  bad=[(r["hour"],issue(r),r["uncertain"]) for r in found.values() if issue(r)]
  x["markers_measured"]=str(len(found));x["all_markers_in_strict_pooled"]=str(len(found)==expected and not bad)
  x["outliers"]=";".join(h+":"+("+".join(metrics))+((":uncertain" if u else "")) for h,metrics,u in bad)
  output.append(x);coll[m["physical_watch_id"]].append(x)
 fields=list(output[0])
 with (out/"face_on_screen.csv").open("w",newline="") as fh:
  w=csv.DictWriter(fh,fieldnames=fields);w.writeheader();w.writerows(output)
 summary={"family":a.family,"photo_count":len(output),"watch_count":len(coll),"measured_poses":sum(x["pose_status"]=="MEASURED" for x in output),"candidate_counts":{},"baseline_strict_photo_pass":sum(x["all_markers_in_strict_pooled"]=="True" for x in output)}
 notes=[]
 for name in CUTS:
  good=[x for x in output if x[name].startswith("CANDIDATE")]
  goodw={x["physical_watch_id"] for x in good}
  checked=[x for x in good if x["markers_measured"]==str(expected)]
  passphotos=[x for x in checked if x["all_markers_in_strict_pooled"]=="True"]
  passw={x["physical_watch_id"] for x in passphotos}
  summary["candidate_counts"][name]={"photos":len(good),"watches":len(goodw),"complete_marker_photos":len(checked),"strict_pooled_all_pass_photos":len(passphotos),"strict_pooled_all_pass_watches":len(passw),"retained_genuine_outliers":[{"photo_id":x["photo_id"],"watch":x["physical_watch_id"],"issue":x["outliers"]} for x in checked if x["all_markers_in_strict_pooled"]!="True"]}
  notes.append(f"{name}: {len(good)}/{len(output)} image candidates, {len(goodw)}/{len(coll)} independent watch candidates, pooled-marker pass {len(passphotos)}/{len(checked)} completely checked candidate photos")
 (out/"summary.json").write_text(json.dumps(summary,indent=2))
 (out/"README.md").write_text("# STRICT FACE-ON RESEARCH - "+a.family+"\n\nThe original datasets are UNCHANGED. These are conservative image-only candidate screens, NOT a genuine-photo whitelist. Every surviving candidate needs independent visual case/crown review before use for calibration. The screen cannot prove mathematical zero tilt. \n\nEllipse ratio is a physical dial-edge estimate. Affine ratio and opposite-scale ratio come from the app's independent homography. Close-up/crop versions of one physical source image are not independent views. No marker outcome was used for pose acceptance.\n\n"+ "\n\n".join(notes) + "\n\nDo not treat excluded/uncertain photos as evidence of replica manufacture. Investigate pose measurement repeatability before setting production thresholds.\n")
 print(json.dumps(summary))
if __name__=="__main__":main()
