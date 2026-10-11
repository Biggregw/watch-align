#!/usr/bin/env python3
"""Bounded ONE-original-photograph end-to-end stability experiment.

Transforms copies of each original QC image; no additional original photographs
are used to decide any original's measurement. Research-only, never a production
QC threshold or genuine manufacturing tolerance.
"""
import csv
import itertools
import math
import pathlib
import shutil
import sys
from collections import defaultdict
import cv2
import numpy as np

TREATMENTS=("original","exposure_minus8","exposure_plus8",
            "contrast_minus6","contrast_plus6","jpeg85",
            "resample85","resample110","blur04")
ACCEPTED={"measured","provisional: radial edges of 12 vary with polished-surround lighting"}
SEP="__var__"

def transform(img,treatment):
    if treatment=="exposure_minus8": return np.clip(img.astype(np.float32)*0.92,0,255).astype(np.uint8)
    if treatment=="exposure_plus8": return np.clip(img.astype(np.float32)*1.08,0,255).astype(np.uint8)
    if treatment=="contrast_minus6": return np.clip((img.astype(np.float32)-128)*0.94+128,0,255).astype(np.uint8)
    if treatment=="contrast_plus6": return np.clip((img.astype(np.float32)-128)*1.06+128,0,255).astype(np.uint8)
    if treatment=="blur04": return cv2.GaussianBlur(img,(3,3),0.4)
    if treatment in ("resample85","resample110"):
        scale=0.85 if treatment=="resample85" else 1.10
        h,w=img.shape[:2]
        small=cv2.resize(img,(max(20,round(w*scale)),max(20,round(h*scale))),
                         interpolation=cv2.INTER_AREA if scale<1 else cv2.INTER_CUBIC)
        return cv2.resize(small,(w,h),interpolation=cv2.INTER_CUBIC if scale<1 else cv2.INTER_AREA)
    return img

def write_variant_images(source_root,output_root):
    """Source manifests from alpha105g/fetch_qc.py. Originals stay untouched."""
    root=pathlib.Path(source_root);dest=pathlib.Path(output_root)
    images=dest/"images";images.mkdir(parents=True,exist_ok=True)
    summary=[]
    for model in ("gmt_126710","submariner_124060"):
        source_manifest=root/model/"manifest.csv"
        if not source_manifest.exists():continue
        manifest=dest/model/"manifest.csv";manifest.parent.mkdir(parents=True,exist_ok=True)
        with manifest.open("w",newline="") as fh:
            out=csv.writer(fh);out.writerow(("photo_id","local_path"))
            for r in csv.DictReader(source_manifest.open(newline="")):
                original=root/r["local_path"];img=cv2.imread(str(original),cv2.IMREAD_COLOR)
                if img is None:continue
                for treat in TREATMENTS:
                    name=r["photo_id"]+SEP+treat
                    target=images/model/(name+".jpg");target.parent.mkdir(parents=True,exist_ok=True)
                    if treat=="original": shutil.copy2(original,target)
                    else:
                        transformed=transform(img,treat)
                        cv2.imwrite(str(target),transformed,[cv2.IMWRITE_JPEG_QUALITY,85 if treat=="jpeg85" else 96])
                    out.writerow((name,model+"/"+name+".jpg"))
                    summary.append((model,r["photo_id"],treat,str(target)))
    print("source_photos",len(set((m,p) for m,p,_,_ in summary)),"processed_variants",len(summary))
    return summary

def points(s):
    try:
        p=[tuple(map(float,v.split(":"))) for v in s.split(";")]
        return p if len(p)>=3 else None
    except (ValueError,AttributeError): return None

def mec_area(pts):
    """Minimum enclosing circle for 3/4 polygon vertices, using pairs/triples."""
    if not pts: return None
    candidates=[]
    for a,b in itertools.combinations(pts,2):
        x=(a[0]+b[0])/2;y=(a[1]+b[1])/2
        radius=math.hypot(a[0]-x,a[1]-y)
        if all(math.hypot(px-x,py-y)<=radius+1e-8 for px,py in pts):
            candidates.append(radius)
    for a,b,c in itertools.combinations(pts,3):
        A=2*(b[0]-a[0]);B=2*(b[1]-a[1])
        C=2*(c[0]-a[0]);D=2*(c[1]-a[1])
        det=A*D-B*C
        if abs(det)<1e-12:continue
        v=b[0]**2+b[1]**2-a[0]**2-a[1]**2
        w=c[0]**2+c[1]**2-a[0]**2-a[1]**2
        x=(v*D-B*w)/det;y=(A*w-v*C)/det
        radius=math.hypot(a[0]-x,a[1]-y)
        if all(math.hypot(px-x,py-y)<=radius+1e-8 for px,py in pts):
            candidates.append(radius)
    return math.pi*min(candidates)**2 if candidates else None

def safe_float(v):
    try:
        x=float(v)
        return x if math.isfinite(x) else None
    except (ValueError,TypeError):return None

def eligible(r):
    return r.get("pose_status")=="accepted" and r.get("polygon_usable","").lower()=="true" and r.get("confidence") in ACCEPTED

def run_analysis(folder):
    root=pathlib.Path(folder)
    rows=[]
    for m in ("gmt_126710","submariner_124060"):
        f=root/(m+".csv")
        if f.exists():rows+=list(csv.DictReader(f.open(newline="")))
    groups=defaultdict(dict)
    for r in rows:
        name=r.get("photo_id","")
        if SEP not in name:continue
        ident,treat=name.rsplit(SEP,1)
        if treat not in TREATMENTS:continue
        k=(r["model"],ident,r.get("model_shape",""),r.get("hour",""))
        groups[k][treat]=r
    results=[];summaries=[]
    for (model,ident,shape,hour),variants in sorted(groups.items()):
        base=variants.get("original")
        if not base:continue
        # Denominator fixed from the original's EXPECTED template, never its measured shape.
        denom=mec_area(points(base.get("expected_vertices",""))) if shape in ("baton","triangle") else safe_float(base.get("expected_area"))
        if not denom or denom<=0:continue
        baseline=None
        transformed_valid=[]
        for treat in TREATMENTS:
            r=variants.get(treat)
            if not r:continue
            e=safe_float(r.get("expected_area"));a=safe_float(r.get("actual_area"));i=safe_float(r.get("overlap_area"))
            good=eligible(r) and None not in (e,a,i)
            # Since coordinates are canonical after perspective rectification, the unchanged baseline
            # enclosing-circle area can be used for ALL image treatments.
            score=100*max(0,e+a-2*i)/denom if good else None
            if treat=="original":baseline=score
            elif good:transformed_valid.append(score)
            results.append(dict(model=model,source_photo=ident,shape=shape,hour=hour,treatment=treat,
                baseline_dial_radius=base.get("dial_R_px",""),treatment_dial_radius=r.get("dial_R_px",""),
                assessed=str(good),reason=r.get("confidence",""),score_circle_pct=score if score is not None else "",
                denominator_circle_area_R2=denom))
        delta=[abs(x-baseline) for x in transformed_valid] if baseline is not None else []
        vals=sorted(transformed_valid)
        summaries.append(dict(model=model,source_photo=ident,shape=shape,hour=hour,
            dial_R_px=base.get("dial_R_px",""),
            baseline_assessed=baseline is not None,baseline_circle_pct=baseline if baseline is not None else "",
            variants_assessed=len(transformed_valid),variants_requested=len(TREATMENTS)-1,
            max_abs_change_pp=max(delta) if delta else "",
            median_abs_change_pp=float(np.median(delta)) if delta else "",
            range_variant_min=min(vals) if vals else "",range_variant_max=max(vals) if vals else "",
            stability_status=("NOT_ASSESSED" if baseline is None or len(vals)<5 else
                              "OBSERVED_SPREAD_GT_0.5PP" if max(delta)>0.5 else "OBSERVED_SPREAD_LE_0.5PP")))
    root.mkdir(parents=True,exist_ok=True)
    for filename,data in (("treatments.csv",results),("summary.csv",summaries)):
        with (root/filename).open("w",newline="") as f:
            if data:
                wr=csv.DictWriter(f,fieldnames=list(data[0]));wr.writeheader();wr.writerows(data)
    measured=[r for r in summaries if r["baseline_assessed"] and r["variants_assessed"]>=5]
    spread=[r for r in measured if r["stability_status"]=="OBSERVED_SPREAD_GT_0.5PP"]
    print("ORIGINAL_SOURCE_PHOTOS",len(set((r["model"],r["source_photo"]) for r in summaries)))
    print("MARKER_BASELINES",len(summaries),"BASELINE_ASSESSED",sum(r["baseline_assessed"] for r in summaries))
    print("AT_LEAST_FIVE_TRANSFORMS_ASSESSED",len(measured),"SPREAD_GREATER_0_5_PP",len(spread))
    print("CONTROL MARKERS (6,9,12):")
    for r in summaries:
        if r["hour"] in ("6","9","12"):
            print(r["source_photo"],"h",r["hour"],"R",r["dial_R_px"],"baseline",r["baseline_circle_pct"],
              "variants",r["variants_assessed"],"delta_max",r["max_abs_change_pp"],r["stability_status"])
    print("CRITICAL: synthetic variants from ONE source photograph, not independent QC photos.")
    print("Changes in brightness/compression/sampling DO NOT prove edge correctness or human visibility.")
    return 0

if __name__=="__main__":
    if len(sys.argv)<3 or sys.argv[1] not in ("prepare","analyse"):
        sys.exit("usage: single_photo_stress.py prepare source_qc_dir output_root | analyse output_directory unused")
    if sys.argv[1]=="prepare":write_variant_images(sys.argv[2],sys.argv[3])
    else:run_analysis(sys.argv[2])
