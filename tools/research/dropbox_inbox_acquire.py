#!/usr/bin/env python3
"""Research-only: acquire licensed Wikimedia originals to unreviewed Dropbox inbox."""
import argparse, csv, hashlib, io, json, os, re
from datetime import datetime, timezone
from urllib.parse import urlparse
import requests
from PIL import Image

BASE = "/Watch Align/Incoming Photos"
API = "https://commons.wikimedia.org/w/api.php"
UA = "WatchAlignResearch/1.0 (Biggregw/watch-align)"
LICENCES = {"cc0", "cc-by-2.0", "cc-by-3.0", "cc-by-4.0", "cc-by-sa-2.0", "cc-by-sa-3.0", "cc-by-sa-4.0", "public-domain"}

class Blocked(Exception): pass

def field(meta, key):
    return str((meta.get(key) or {}).get("value") or "")

def fetch_meta(s, title):
    if not title.startswith("File:"): raise Blocked("not_commons_file")
    r = s.get(API, params={"action":"query","format":"json","formatversion":2,"prop":"imageinfo",
        "iiprop":"url|size|mime|extmetadata","titles":title}, headers={"User-Agent":UA}, timeout=30)
    r.raise_for_status()
    pages = r.json().get("query",{}).get("pages", [])
    if len(pages)!=1 or not pages[0].get("imageinfo"): raise Blocked("missing_image")
    info = pages[0]["imageinfo"][0]
    ext = info.get("extmetadata") or {}
    lic = field(ext,"LicenseShortName").lower().replace(" ","-")
    if lic not in LICENCES: raise Blocked("rights_not_permitted:"+lic)
    desc = re.sub(r"<[^>]*>"," ",field(ext,"ImageDescription"))
    if re.search(r"\b(replica|fake|counterfeit|superclone|reproduction)\b",desc,re.I):
        raise Blocked("documented_replica")
    url = info.get("url","")
    if not url.startswith("https://") or not (urlparse(url).hostname or "").endswith(".wikimedia.org"):
        raise Blocked("unapproved_image_host")
    if info.get("mime") not in {"image/jpeg","image/png","image/webp"}: raise Blocked("unsupported_format")
    return {"page":info.get("descriptionurl"),"url":url,"author":field(ext,"Artist"),
      "licence":lic,"licence_url":field(ext,"LicenseUrl"),"description":desc,
      "source_size":info.get("size"),"source_dimensions":[info.get("width"),info.get("height")]}

def original(s, url):
    with s.get(url, headers={"User-Agent":UA}, timeout=45, stream=True) as r:
        r.raise_for_status()
        if not (urlparse(r.url).hostname or "").endswith(".wikimedia.org"): raise Blocked("redirected_to_unapproved_host")
        chunks=[]; total=0
        for chunk in r.iter_content(131072):
            total+=len(chunk)
            if total>25*1024*1024: raise Blocked("operational_file_limit_25mb")
            chunks.append(chunk)
        return b"".join(chunks)

def validate_photo(b):
    try:
        with Image.open(io.BytesIO(b)) as im:
            fmt=im.format; w,h=im.size; im.verify()
    except Exception as e: raise Blocked("invalid_image_bytes") from e
    if fmt not in ("JPEG","PNG","WEBP"): raise Blocked("unsupported_image")
    return w,h,{"JPEG":".jpg","PNG":".png","WEBP":".webp"}[fmt]

def token(s, env):
    if env.get("DROPBOX_ACCESS_TOKEN"):
        return env["DROPBOX_ACCESS_TOKEN"]
    if not all(env.get("DROPBOX_"+x) for x in ("REFRESH_TOKEN","APP_KEY","APP_SECRET")):
        raise Blocked("missing_dropbox_oauth_secrets")
    r=s.post("https://api.dropboxapi.com/oauth2/token",
        auth=(env["DROPBOX_APP_KEY"],env["DROPBOX_APP_SECRET"]),
        data={"grant_type":"refresh_token","refresh_token":env["DROPBOX_REFRESH_TOKEN"]},timeout=30)
    r.raise_for_status()
    return r.json()["access_token"]

def dbx(s,t,endpoint,**kw):
    headers=kw.pop("headers",{})
    headers["Authorization"]="Bearer "+t
    r=s.post(endpoint,headers=headers,timeout=90,**kw)
    return r

def mkdir(s,t,path):
    if not path.startswith(BASE+"/"): raise Blocked("unsafe_destination")
    for d in (BASE,path):
        r=dbx(s,t,"https://api.dropboxapi.com/2/files/create_folder_v2",
              json={"path":d,"autorename":False})
        if not r.ok and not (r.status_code==409 and "conflict" in r.text.lower()):
            raise Blocked("dropbox_folder_error:"+str(r.status_code))

def upload(s,t,path,content):
    r=dbx(s,t,"https://content.dropboxapi.com/2/files/upload",data=content,
      headers={"Content-Type":"application/octet-stream","Dropbox-API-Arg":json.dumps({
        "path":path,"mode":"add","autorename":False,"strict_conflict":True,"mute":True})})
    if not r.ok: raise Blocked("dropbox_upload_error:"+str(r.status_code)+":"+r.text[:150])

def verify_readback(s,t,path,content):
    r=dbx(s,t,"https://content.dropboxapi.com/2/files/download",
          headers={"Dropbox-API-Arg":json.dumps({"path":path})})
    if not r.ok: raise Blocked("dropbox_readback_error:"+str(r.status_code))
    if len(r.content)!=len(content) or hashlib.sha256(r.content).digest()!=hashlib.sha256(content).digest():
        raise Blocked("DROPBOX_READBACK_SHA256_MISMATCH")
    return True

def process(s,t,row,dry):
    if row["provider"]!="wikimedia_commons": raise Blocked("source_not_approved")
    info=fetch_meta(s,row["file_title"])
    if dry: return {"status":"rights_metadata_checked","source":info}
    b=original(s,info["url"])
    w,h,ext=validate_photo(b)
    sha=hashlib.sha256(b).hexdigest()
    model=re.sub(r"[^A-Za-z0-9_-]","-",row["model"])[:60]
    directory=BASE+"/"+model
    image_path=directory+"/"+sha+ext
    provenance_path=directory+"/"+sha+".json"
    record={"status":"UNREVIEWED_NOT_CALIBRATION_APPROVED","authenticity":"UNVERIFIED",
      "physical_watch_id":"UNKNOWN","source_id":row["source_id"],"model_claim":row["model"],
      "commons_file":row["file_title"],"source":info,"sha256":sha,"bytes":len(b),
      "width":w,"height":h,"image_path":image_path,"utc":datetime.now(timezone.utc).isoformat()}
    mkdir(s,t,directory)
    upload(s,t,image_path,b)
    verify_readback(s,t,image_path,b)
    record["dropbox_readback"]={"sha256":sha,"bytes":len(b),"matched":True}
    upload(s,t,provenance_path,(json.dumps(record,ensure_ascii=False,indent=2)+"\n").encode())
    return {"status":"uploaded_and_readback_verified","image":image_path,
      "provenance":provenance_path,"sha256":sha,"bytes":len(b),"dimensions":[w,h]}

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--manifest",default="tools/research/dropbox_inbox_sources.csv")
    p.add_argument("--limit",type=int,default=1)
    p.add_argument("--dry-run",action="store_true")
    a=p.parse_args()
    if not 1<=a.limit<=10: p.error("limit must be 1 to 10")
    s=requests.Session()
    t="" if a.dry_run else token(s,os.environ)
    with open(a.manifest,encoding="utf-8-sig",newline="") as f: rows=list(csv.DictReader(f))
    successes=0
    for row in rows[:a.limit]:
        try:
            result=process(s,t,row,a.dry_run)
            print(json.dumps({"source_id":row["source_id"],**result}))
            successes+=result["status"]=="uploaded_and_readback_verified"
        except (Blocked,requests.RequestException,ValueError) as e:
            print(json.dumps({"source_id":row.get("source_id"),"status":"FAILED","reason":str(e)[:300]}))
    print(json.dumps({"attempted":min(len(rows),a.limit),"verified_uploads":successes}))
    return 0 if a.dry_run or successes else 1

if __name__=="__main__": raise SystemExit(main())
