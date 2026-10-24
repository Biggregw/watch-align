#!/usr/bin/env python3
"""Rights-asserted Bob's Watches listing acquisition into UNREVIEWED Dropbox.

A bounded owner-authorized research intake, NOT an unrestricted spider.
Only the explicit stock-identified listing manifest is visited. All working
image bytes are stored untouched and independently checked through Dropbox.
No quality, pose, resolution or marker filter. No calibration promotion.
"""
from __future__ import annotations
import argparse,csv,hashlib,html,json,os,re,sys,time
from datetime import datetime,timezone
from pathlib import Path
from urllib.parse import urljoin,urlparse
import requests
from bs4 import BeautifulSoup

sys.path.insert(0,str(Path(__file__).resolve().parent))
import dropbox_inbox_acquire as dbx

ROOT="/Watch Align/Incoming Photos"
IMAGE_SUFFIX=re.compile(r"\.(?:jpg|jpeg|png|webp)$",re.I)
TARGETS={"124060","126610LN","126610LV","126710BLNR","126710BLRO","126622"}
UA="WatchAlignResearch/1.0 (owner-authorized, bounded research archive)"
class IntakeError(Exception): pass

def dealer(url):
    p=urlparse(url)
    return p.scheme=="https" and p.hostname in {"www.bobswatches.com","bobswatches.com"}

def stock_image(raw, base, stock):
    if not raw: return ""
    raw=html.unescape(str(raw)).replace("\\/","/").strip()
    if raw.startswith("data:"):return ""
    u=urljoin(base,raw)
    p=urlparse(u)
    if not dealer(u) or not p.path.lower().startswith("/images/"):return ""
    if not IMAGE_SUFFIX.search(p.path):return ""
    if not re.search(r"(?<!\d)"+re.escape(stock)+r"(?!\d)",p.path):return ""
    # Preserve the original URL, including any service query parameters.
    return u

def images_on_listing(session,row):
    url=row["listing_url"]
    if not dealer(url) or not urlparse(url).path.endswith(".html"):
        raise IntakeError("invalid_dealer_listing_url")
    r=session.get(url,headers={"User-Agent":UA,"Accept":"text/html"},timeout=35)
    r.raise_for_status()
    if not dealer(r.url):raise IntakeError("listing_redirect_off_site")
    soup=BeautifulSoup(r.text,"html.parser")
    stock=row["stock"]
    text=soup.get_text(" ",strip=True)
    if not re.search(r"(?<!\d)"+re.escape(stock)+r"(?!\d)",text):
        raise IntakeError("stock_number_absent_from_listing")
    model=row["model"]
    if model not in (r.url+" "+text).upper():
        # Avoid wrong family contamination, e.g., 116710 vs 126710.
        raise IntakeError("exact_model_not_in_listing")
    found=[];seen=set()
    def add(u):
        u=stock_image(u,r.url,stock)
        if u and u not in seen:seen.add(u);found.append(u)
    for tag in soup.find_all(["a","img","source","meta"]):
        for attr in ("data-large_image","data-zoom-image","data-original","data-src",
                     "data-lazy-src","data-image","src","href","content"):
            add(tag.get(attr))
        for attr in ("srcset","data-srcset"):
            for part in (tag.get(attr) or "").split(","):
                add(part.strip().split()[0] if part.strip() else "")
    # Some gallery URLs are embedded in JSON script arrays rather than <img>.
    raw=html.unescape(r.text).replace("\\/","/")
    pattern=r'(?:(?:https?:)?//(?:www\.)?bobswatches\.com)?/images/[^"\'\s<>\\]+?\.(?:jpe?g|png|webp)(?:\?[^"\'\s<>\\]*)?'
    for m in re.finditer(pattern,raw,re.I):add(m.group())
    return found

def get_original(session,url):
    # Known gallery URL, no discovery of redirects to third parties.
    if not dealer(url):raise IntakeError("offsite_image")
    with session.get(url,headers={"User-Agent":UA,"Accept":"image/*"},timeout=50,
                     allow_redirects=True,stream=True) as r:
        r.raise_for_status()
        if not dealer(r.url):raise IntakeError("image_redirect_offsite")
        chunks=[];size=0
        for chunk in r.iter_content(131072):
            size+=len(chunk)
            if size>25_000_000:raise IntakeError("operational_max_25mb")
            chunks.append(chunk)
        return b"".join(chunks),r.url

def acquire(session,token,row,max_images,sleep_sec):
    stock=row["stock"].strip();model=row["model"].strip().upper()
    if not re.fullmatch(r"\d{5,8}",stock) or model not in TARGETS:
        raise IntakeError("invalid_manifest_model_or_stock")
    urls=images_on_listing(session,row)
    if not urls:raise IntakeError("NO_STOCK_MATCHING_GALLERY_IMAGES")
    target=ROOT+"/"+model
    outcome={"model":model,"stock":stock,"listing":row["listing_url"],
             "found_stock_specific_image_urls":len(urls),"attempted_images":0,
             "verified_images":0,"errors":[],"files":[]}
    # No subjective photo-quality filter. Guard against page noise/rate spikes.
    for image_url in urls[:max_images]:
        outcome["attempted_images"]+=1
        time.sleep(sleep_sec)
        try:
            b,final_url=get_original(session,image_url)
            width,height,ext=dbx.validate_photo(b)
            digest=hashlib.sha256(b).hexdigest()
            image_path=target+"/"+digest+ext
            provenance_path=target+"/"+digest+".json"
            proof={
              "status":"UNREVIEWED_NOT_CALIBRATION_APPROVED",
              "watch_identity":"dealer_stock_id_only","physical_watch_id":"bobs_"+stock,
              "genuine_status":"dealer_attributed_genuine_candidate_not_independently_authenticated",
              "dealer":"Bob's Watches","model_claim":model,"stock":stock,
              "listing_url":row["listing_url"],"original_image_url":image_url,
              "resolved_image_url":final_url,"byte_size":len(b),"width":width,
              "height":height,"sha256":digest,"image_path":image_path,
              "downloaded_utc":datetime.now(timezone.utc).isoformat(),
              "permissions":"Project owner reports direct permission from Bob's Watches for automated Watch Align research collection. Scope not independently inspected; commercial redistribution rights not assumed.",
              "photo_quality":"NOT_YET_REVIEWED","calibration_approved":False}
            try:
                dbx.upload(session,token,image_path,b)
            except dbx.Blocked as e:
                if "conflict" in str(e).lower():
                    # Content-addressed already saved; only accept an actual matching Dropbox readback.
                    dbx.verify_readback(session,token,image_path,b)
                    outcome["errors"].append("exact_file_already_present:"+digest)
                    continue
                raise
            dbx.verify_readback(session,token,image_path,b)
            proof["dropbox_readback_sha256_match"]=True
            dbx.upload(session,token,provenance_path,(json.dumps(proof,indent=2,ensure_ascii=False)+"\n").encode())
            outcome["verified_images"]+=1
            outcome["files"].append({"sha256":digest,"path":image_path,"bytes":len(b),
                                     "width":width,"height":height})
        except (requests.RequestException,dbx.Blocked,IntakeError,ValueError) as e:
            outcome["errors"].append("image_failed:"+image_url+":"+type(e).__name__+":"+str(e)[:180])
    outcome["status"]="VERIFIED" if outcome["verified_images"] else "NO_NEW_VERIFIED_IMAGES"
    return outcome

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--manifest",default="tools/research/bobs_authorized_listings.csv")
    ap.add_argument("--max-listings",type=int,default=30)
    ap.add_argument("--max-images-per-watch",type=int,default=8)
    ap.add_argument("--delay",type=float,default=0.5)
    args=ap.parse_args()
    if not 1<=args.max_listings<=50 or not 1<=args.max_images_per_watch<=12 or args.delay<0.5:
        ap.error("out-of-range collection limits")
    with open(args.manifest,encoding="utf-8",newline="") as f:
        rows=list(csv.DictReader(f))[:args.max_listings]
    session=requests.Session()
    token=dbx.token(session,os.environ)
    outcomes=[]
    for row in rows:
        time.sleep(args.delay)
        try:
            result=acquire(session,token,row,args.max_images_per_watch,args.delay)
        except (requests.RequestException,dbx.Blocked,IntakeError,ValueError) as e:
            result={"model":row.get("model"),"stock":row.get("stock"),
                    "status":"LISTING_FAILED","error":type(e).__name__+":"+str(e)[:300]}
        print(json.dumps(result,ensure_ascii=False),flush=True)
        outcomes.append(result)
    summary={"listings_attempted":len(outcomes),
             "listings_with_verified_images":sum(x.get("verified_images",0)>0 for x in outcomes),
             "total_new_verified_images":sum(x.get("verified_images",0) for x in outcomes),
             "unreviewed_only":True,"calibration_modified":False}
    batch_name="_bobs_research_intake_"+datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")+".json"
    report_bytes=(json.dumps({"summary":summary,"listings":outcomes},ensure_ascii=False,indent=2)+"\n").encode()
    report_path=ROOT+"/"+batch_name
    dbx.upload(session,token,report_path,report_bytes)
    dbx.verify_readback(session,token,report_path,report_bytes)
    summary["report_path"]=report_path
    print("BATCH_SUMMARY "+json.dumps(summary),flush=True)
    return 0 if summary["total_new_verified_images"] else 1

if __name__=="__main__":raise SystemExit(main())
