#!/usr/bin/env python3
"""Recover exact bytes recorded in existing Watch Align genuine catalogues.
No discovery, no image modifications, no detector work.
Images with changed hashes are NOT accepted as recovered originals.
"""
import concurrent.futures, csv, hashlib, io, json, os, re, time, zipfile
from pathlib import Path
from urllib.parse import urlsplit
import requests
from PIL import Image

OUT=Path("genuine-recovery-out")
OUT.mkdir(exist_ok=True)
SOURCES=[
 ("gmt_126710","tools/research/alpha96_calibration/catalogue_provenance_strong.csv"),
 ("submariner_126610","tools/research/sub126610/catalogue_126610.csv")
]
COLUMNS=["model_group","model","physical_watch_id","photo_id","source","listing_url",
         "image_url","expected_sha256","observed_sha256","bytes","width","height",
         "status","http_code","reason","recovery_archive","recovery_member","notes"]
UA="Mozilla/5.0 (compatible; WatchAlignOriginalRecovery/1.0; genuine image archive integrity audit)"
records=[]
for label,path in SOURCES:
    with open(path,newline="",encoding="utf-8") as f:
        for r in csv.DictReader(f):
            records.append((label,r))
print("CATALOGUE ENTRIES",len(records),flush=True)

def attempt(item):
    label,r=item
    url=r.get("image_url","").strip()
    expected=r.get("sha256","").lower().strip()
    row=dict(model_group=label,model=r.get("model",""),physical_watch_id=r.get("physical_watch_id",""),
        photo_id=r.get("photo_id",""),source=r.get("source",""),listing_url=r.get("listing_url","") or r.get("source",""),
        image_url=url,expected_sha256=expected,observed_sha256="",bytes=0,width=0,height=0,
        status="not_attempted",http_code="",reason="",recovery_archive="",recovery_member="",notes=r.get("notes",""))
    if not re.fullmatch(r"[a-f0-9]{64}",expected or ""):
        row.update(status="invalid_sha",reason="existing catalogue SHA absent/invalid")
        return row,None
    u=urlsplit(url)
    if u.scheme!="https" or not u.hostname:
        row.update(status="invalid_url",reason="not a valid HTTPS source URL")
        return row,None
    try:
        s=requests.Session()
        x=s.get(url,headers={"User-Agent":UA,"Accept":"image/avif,image/webp,image/apng,image/*,*/*;q=0.8"},timeout=(8,15),allow_redirects=True)
        row["http_code"]=x.status_code
        if x.status_code!=200:
            row.update(status="http_error",reason=f"HTTP {x.status_code}")
            return row,None
        data=x.content
        if not 10000<=len(data)<=12*1024*1024:
            row.update(status="bad_length",reason=f"unacceptable response length {len(data)}")
            return row,None
        try:
            im=Image.open(io.BytesIO(data));im.verify()
            im=Image.open(io.BytesIO(data));w,h=im.size;fmt=im.format
        except Exception as e:
            row.update(status="invalid_image",reason=str(e)[:160])
            return row,None
        row.update(width=w,height=h,bytes=len(data))
        actual=hashlib.sha256(data).hexdigest()
        row["observed_sha256"]=actual
        if actual !=expected:
            row.update(status="sha_mismatch",reason="source delivered different bytes; do NOT use as original")
            return row,None
        if min(w,h)<350:
            row.update(status="matched_low_resolution",reason="SHA verified, but low resolution; reserved for visual audit")
        else:row.update(status="sha_verified",reason="byte-exact match with original source hash")
        ext=".png" if fmt=="PNG" else ".webp" if fmt=="WEBP" else ".jpg"
        # Recorded original bytes untouched, no image conversion
        row["recovery_member"]=label+"/originals/"+actual[:2]+"/"+actual[:24]+ext
        return row,data
    except Exception as e:
        row.update(status="network_exception",reason=str(e)[:220])
        return row,None

done=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
    futures=[pool.submit(attempt,i) for i in records]
    for i,fu in enumerate(concurrent.futures.as_completed(futures),1):
        try: row,body=fu.result()
        except Exception as e: print("WORKER ERROR",e,flush=True); continue
        done.append((row,body))
        if i%50==0:print(f"Progress {i}/{len(records)}; exact hits {sum(r['status'].startswith('sha_verified') for r,_ in done)}",flush=True)

# Stable sorted order, unique source hash, and split original bytes into <24MB ZIP parts
done.sort(key=lambda x:(x[0]["model_group"],x[0]["physical_watch_id"],x[0]["photo_id"]))
manifest=[]
for group in [n for n,_ in SOURCES]:
    subset=[(r,b) for r,b in done if r["model_group"]==group]
    chunks=[]; chunk=[]; size=0; seen=set()
    for r,body in subset:
        if body is None:continue
        if r["observed_sha256"] in seen:
            r["status"]="duplicate_sha";r["reason"]="same source bytes as another row in this model"
            continue
        seen.add(r["observed_sha256"])
        if size+len(body)>23*1024*1024 and chunk:
            chunks.append(chunk);chunk=[];size=0
        chunk.append((r,body));size+=len(body)
    if chunk:chunks.append(chunk)
    for ci,part in enumerate(chunks,1):
        name=f"RECOVERED_{group}_ORIGINALS_{ci:02d}of{len(chunks):02d}.zip"
        with zipfile.ZipFile(OUT/name,"w",compression=zipfile.ZIP_STORED) as z:
            for r,data in part:
                z.writestr(r["recovery_member"],data)
                r["recovery_archive"]=name
    print("GROUP",group,"entries",len(subset),"recovered",len(seen),"archives",len(chunks),flush=True)
    manifest.extend([r for r,b in subset])
with open(OUT/"recovery_all_catalogued.csv","w",newline="",encoding="utf-8") as f:
    wr=csv.DictWriter(f,fieldnames=COLUMNS);wr.writeheader();wr.writerows(manifest)
stats={}
for group,_ in SOURCES:
    sub=[r for r in manifest if r["model_group"]==group]
    stats[group]={"catalogued":len(sub),"outcomes":{k:sum(r["status"]==k for r in sub) for k in sorted(set(r["status"] for r in sub))},
      "distinct_matched_watches":len(set(r["physical_watch_id"] for r in sub if r["recovery_archive"]))}
(OUT/"recovery_summary.json").write_text(json.dumps(stats,indent=2))
print(json.dumps(stats,indent=2),flush=True)
