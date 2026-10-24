#!/usr/bin/env python3
"""One-off selected direct image URLs: no site crawling or gallery scraping."""
import csv,hashlib,json,re,sys,os
from datetime import datetime,timezone
from pathlib import Path
from urllib.parse import urlparse
import requests
sys.path.insert(0,str(Path(__file__).resolve().parent))
import dropbox_inbox_acquire as dbx
ALLOWED={"www.bobswatches.com","images.bobswatches.com"}
ROOT="/Watch Align/Incoming Photos"
class IntakeError(RuntimeError):pass

def fetch(session,url):
    parsed=urlparse(url)
    if parsed.scheme!="https" or parsed.hostname not in ALLOWED or "/images/" not in parsed.path:
        raise IntakeError("unapproved_direct_image_url")
    try:
        with session.get(url,timeout=35,allow_redirects=True,stream=True,
            headers={"User-Agent":"WatchAlignResearch/1.0 (selected direct images)","Accept":"image/*"}) as r:
            r.raise_for_status()
            dest=urlparse(r.url)
            if dest.scheme!="https" or dest.hostname not in ALLOWED:
                raise IntakeError("redirected_outside_dealer")
            chunks=[];size=0
            for c in r.iter_content(131072):
                size+=len(c)
                if size>20_000_000:raise IntakeError("operational_20mb_limit")
                chunks.append(c)
            return b"".join(chunks),r.url
    except requests.RequestException as e:
        raise IntakeError("download_failed:"+type(e).__name__+":"+str(e)[:200]) from e

def one(s,t,row):
    stock=row["stock"].strip();ref=row["reference"].strip()
    if not re.fullmatch(r"\d{5,8}",stock) or ref not in ("126710BLNR","126710BLRO"):
        raise IntakeError("invalid_stock_or_model")
    page=urlparse(row["listing_url"])
    if page.scheme!="https" or page.hostname!="www.bobswatches.com" or not page.path.endswith(".html"):
        raise IntakeError("invalid_dealer_listing")
    original,real_url=fetch(s,row["image_url"])
    width,height,ext=dbx.validate_photo(original)
    sha=hashlib.sha256(original).hexdigest()
    directory=ROOT+"/GMT-Master-II-"+ref
    name=directory+"/"+sha+ext
    meta_path=directory+"/"+sha+".json"
    meta={"status":"UNREVIEWED_NOT_CALIBRATION_APPROVED","provenance":"dealer-attributed genuine candidate",
      "independently_authenticated":False,"dealer":"Bob's Watches","model":ref,
      "stock":stock,"physical_watch_id":"bobs_"+stock,"listing_url":row["listing_url"],
      "original_image_url":row["image_url"],"resolved_image_url":real_url,
      "rights":"Personal non-commercial download only. Automated site scraping and commercial/redistribution uses not authorised by this collection; do not use in production.",
      "terms":"https://www.bobswatches.com/terms-and-conditions",
      "sha256":sha,"bytes":len(original),"width":width,"height":height,
      "image_path":name,"review":"NOT_YET_VISUALLY_REVIEWED",
      "retrieved_at":datetime.now(timezone.utc).isoformat()}
    dbx.upload(s,t,name,original)
    dbx.verify_readback(s,t,name,original)
    meta["dropbox_readback_verified"]=True
    dbx.upload(s,t,meta_path,(json.dumps(meta,indent=2)+"\n").encode())
    return {"stock":stock,"status":"VERIFIED","file":name,"provenance":meta_path,"sha256":sha,"bytes":len(original),"dimensions":[width,height]}

def main():
    with open(os.environ.get("WATCHALIGN_BOBS_MANIFEST","tools/research/bobs_direct_selected.csv"),newline="",encoding="utf-8") as f:
        rows=list(csv.DictReader(f))
    if len(rows)>2:raise IntakeError("limit_two_sources")
    session=requests.Session()
    token=dbx.token(session,os.environ)
    passed=0
    for row in rows:
        try:
            result=one(session,token,row)
            passed+=1
        except (IntakeError,dbx.Blocked,requests.RequestException,ValueError) as e:
            result={"stock":row["stock"],"status":"FAILED","error":str(e)[:300]}
        print(json.dumps(result),flush=True)
    print(json.dumps({"attempted":len(rows),"uploaded_and_verified":passed}))
    return 0 if passed else 1
if __name__=="__main__":raise SystemExit(main())
