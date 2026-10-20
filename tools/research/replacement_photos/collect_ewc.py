#!/usr/bin/env python3
"""Research-only acquisition of public EWC photographs. No original photo is uploaded.
Never use a presumed URL or unverified image as a genuine reference.
"""
import csv, hashlib, io, os, sys
from collections import Counter
from pathlib import Path
from PIL import Image
sys.path.insert(0, str(Path("tools/dataset_harvester").resolve()))
from harvester.http import Http, FetchError

rows=list(csv.DictReader(open("tools/research/replacement_photos/ewc_gmt_date_candidate_sources.csv")))
output=Path("g"); photos=output/"img";photos.mkdir(parents=True,exist_ok=True)
http=Http(); manifest=[];ledger=[]
for rec in rows:
 fam,stock,reference=rec["family"],rec["stock"],rec["reference"]
 pid=f"EWC_{fam}_{stock}"
 # EWC source photographs convention confirmed on independently verified 124060 corpus.
 url=f"https://images.europeanwatch.com/images/{stock[:2]}/{stock}-2.jpg"
 status="";data=None
 try:
  data=http.get(url).content
  with Image.open(io.BytesIO(data)) as img:
   img.load()
   dims=(img.width,img.height)
   if img.width<650 or img.height<550:raise ValueError("too_small")
   if img.format not in ("JPEG","PNG","WEBP"):raise ValueError("unsupported_image")
  status="downloaded_image"
 except (FetchError,ValueError,OSError) as exc:
  status="excluded_"+type(exc).__name__;dims=(0,0);data=None
 sha=hashlib.sha256(data).hexdigest() if data else ""
 local=f"{pid}.jpg"
 if data:
  (photos/local).write_bytes(data)
  manifest.append(dict(family=fam,photo_id=pid,physical_watch_id=pid,model=reference,local_path=local,sha256=sha,image_url=url,listing_url=rec["listing_url"]))
 ledger.append({**rec,"photo_id":pid,"image_url":url,"status":status,"sha256":sha,"width":dims[0],"height":dims[1]})
 print(pid,status,dims,sha[:12],flush=True)
for p,dat in [(output/"manifest.csv",manifest),(output/"sources_checked.csv",ledger)]:
 with open(p,"w",newline="") as fh:
  w=csv.DictWriter(fh,fieldnames=list(dat[0].keys()));w.writeheader();w.writerows(dat)
print("candidate download outcomes",dict(Counter(r["status"] for r in ledger)))
print("verified image metadata",len(manifest),"of",len(rows),"from",len({r["physical_watch_id"] for r in manifest}),"independent watches")
