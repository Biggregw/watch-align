"""Autonomous source discovery for Watch Align calibration.

Given only a model config, search approved public sources and write the standard
candidate-pool CSV consumed by the existing acquisition pipeline. Discovery never
changes calibration limits and never treats a search result as an independent watch
unless it has a distinct listing/post URL.
"""
from __future__ import annotations

import csv, glob, hashlib, html, json, re, time
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import parse_qs, quote_plus, unquote, urlparse

import requests
from bs4 import BeautifulSoup

REPO=Path(__file__).resolve().parents[2]
UA = "WatchAlignResearch/1.0 (+https://github.com/Biggregw/watch-align)"
TIMEOUT = 20
FIELDS = ["candidate_id","physical_watch_id","family","model","class_tag","factory","source_type","source_name","source_url","image_album_url","provenance_note","candidate_status","listing_id"]
CLASS={"gen":"gen","genuine":"gen","rep":"rep","replica":"rep"}


def canonical(url: str) -> str:
    if not url: return ""
    u=url.strip()
    if "duckduckgo.com/l/?" in u:
        q=parse_qs(urlparse(u).query).get("uddg")
        if q: u=unquote(q[0])
    p=urlparse(u)
    return f"{p.scheme or 'https'}://{p.netloc.lower()}{p.path.rstrip('/')}"


def bing(query: str, pages: int = 2):
    out=[]
    for page in range(pages):
        first=1+page*10
        url=f"https://www.bing.com/search?format=rss&count=50&first={first}&q={quote_plus(query)}"
        try:
            r=requests.get(url,headers={"User-Agent":UA},timeout=TIMEOUT); r.raise_for_status()
            root=ET.fromstring(r.text)
            for it in root.findall(".//item"):
                out.append((it.findtext("link") or "",it.findtext("title") or "",it.findtext("description") or ""))
        except Exception:
            break
    return out


def duck(query: str):
    try:
        r=requests.get("https://html.duckduckgo.com/html/",params={"q":query},headers={"User-Agent":UA},timeout=TIMEOUT);r.raise_for_status()
        s=BeautifulSoup(r.text,"html.parser"); out=[]
        for a in s.select("a.result__a"):
            row=a.find_parent(class_="result")
            sn=row.select_one(".result__snippet") if row else None
            out.append((a.get("href") or "",a.get_text(" ",strip=True),sn.get_text(" ",strip=True) if sn else ""))
        return out
    except Exception:
        return []


def page_mentions_model(url: str, model: str) -> bool:
    try:
        r=requests.get(url,headers={"User-Agent":UA},timeout=TIMEOUT,allow_redirects=True)
        if r.status_code>=400 or not r.content.strip(): return False
        text=BeautifulSoup(r.text,"html.parser").get_text(" ",strip=True)
        return re.search(rf"(?<!\d){re.escape(model)}(?!\d)",text,re.I) is not None
    except Exception:
        return False


def reddit_album(url: str, model: str):
    m=re.search(r"reddit\.com/(?:r/[^/]+/)?comments/([a-z0-9]+)",url,re.I)
    if not m: return "", ""
    try:
        jurl=f"https://www.reddit.com/comments/{m.group(1)}.json?raw_json=1"
        r=requests.get(jurl,headers={"User-Agent":UA},timeout=TIMEOUT);r.raise_for_status()
        post=r.json()[0]["data"]["children"][0]["data"]
        text=" ".join([post.get("title", ""),post.get("selftext", ""),post.get("url_overridden_by_dest", "")])
        if model not in text: return "", text
        mm=re.search(r"https?://(?:www\.)?imgur\.com/a/[A-Za-z0-9]+",text)
        return (mm.group(0) if mm else ""), text
    except Exception:
        return "", ""


def listing_id(url: str, note: str="") -> str:
    m=re.search(r"(?:SKU|product code)\s*(\d{4,})",note or "",re.I)
    if m: return m.group(1)
    path=urlparse(url).path
    ms=re.findall(r"(?<!\d)(\d{5,})(?!\d)",path)
    return ms[-1] if ms else ""


def stable_id(prefix: str, url: str) -> str:
    return f"{prefix}_{hashlib.sha1(url.encode()).hexdigest()[:12]}"


def add_bootstrap(config, model, family, rows, seen, counts):
    """Reuse already-curated source knowledge automatically when the repo has it. This is optional;
    new references with no prior pool still use web discovery. No user-supplied pool is required."""
    added=0
    for pattern in config["discovery"].get("bootstrap_pool_globs",[]):
        for fn in sorted(glob.glob(str(REPO/pattern))):
            with open(fn,newline="",encoding="utf-8") as fh:
                for r in csv.DictReader(fh):
                    if (r.get("model") or "").upper()!=model or (r.get("candidate_status") or "candidate")!="candidate": continue
                    cls=CLASS.get((r.get("class_tag") or r.get("class") or r.get("class_label") or "").lower(),"")
                    if not cls: continue
                    url=canonical(r.get("source_url") or ""); album=(r.get("image_album_url") or "").strip()
                    key=url or album
                    if not key or key in seen: continue
                    cid=(r.get("candidate_id") or r.get("physical_watch_id") or stable_id("bootstrap",key)).strip()
                    wid=(r.get("physical_watch_id") or cid).strip()
                    note=(r.get("provenance_note") or "")+f"; auto-bootstrap from {Path(fn).name}"
                    rows.append({"candidate_id":cid,"physical_watch_id":wid,"family":family,"model":model,"class_tag":cls,"factory":r.get("factory","") or "",
                                 "source_type":r.get("source_type","") or "known_source","source_name":r.get("source_name","") or Path(fn).name,
                                 "source_url":url,"image_album_url":album,"provenance_note":note.strip("; "),"candidate_status":"candidate",
                                 "listing_id":r.get("listing_id","") or listing_id(url,note)})
                    seen.add(key);counts[cls]+=1;added+=1
    return added


def discover(config: dict, out_csv: Path) -> dict:
    model=str(config["model"]).upper(); family=config["family"]
    seen=set(); rows=[]; counts={"gen":0,"rep":0}
    bootstrap=add_bootstrap(config,model,family,rows,seen,counts)
    for src in config["discovery"]["sources"]:
        wanted=int(src.get("target",config["discovery"].get("per_source_target",8)))
        for templ in src.get("queries",[]):
            if counts[src["class"]] >= config["discovery"].get(f"target_{src['class']}",999): break
            q=templ.format(model=model)
            hits=bing(q)+duck(q)
            for raw,title,snip in hits:
                url=canonical(html.unescape(raw))
                if not url or url in seen: continue
                host=urlparse(url).netloc.lower(); dom=src["domain"].lower()
                if not (host==dom or host.endswith("."+dom)): continue
                blob=f"{title} {snip} {url}"
                if model not in blob and not page_mentions_model(url,model): continue
                album=""; detail=blob
                if "reddit.com" in host:
                    album,detail=reddit_album(url,model)
                    if model not in detail and model not in blob: continue
                    if src.get("require_album",True) and not album: continue
                factory=""
                for fac in config.get("replica_factories",[]):
                    if re.search(rf"\b{re.escape(fac)}\b",detail,re.I): factory=fac;break
                if src["class"]=="rep" and src.get("require_factory",True) and not factory: continue
                lid=listing_id(url)
                if src.get("require_listing_id",False) and not lid: continue
                cid=stable_id(src.get("id_prefix",src["class"]),url)
                rows.append({"candidate_id":cid,"physical_watch_id":cid,"family":family,"model":model,"class_tag":src["class"],"factory":factory,
                             "source_type":src["source_type"],"source_name":src["name"],"source_url":url,"image_album_url":album,
                             "provenance_note":f"auto-discovered; query={q}","candidate_status":"candidate","listing_id":lid})
                seen.add(url); counts[src["class"]]+=1
                if sum(1 for r in rows if r["source_name"]==src["name"])>=wanted: break
            time.sleep(0.15)
    out_csv.parent.mkdir(parents=True,exist_ok=True)
    with out_csv.open("w",newline="",encoding="utf-8") as fh:
        w=csv.DictWriter(fh,fieldnames=FIELDS);w.writeheader();w.writerows(rows)
    report={"model":model,"family":family,"candidates":len(rows),"by_class":counts,"bootstrap_candidates":bootstrap,"output":str(out_csv)}
    out_csv.with_suffix(".json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    return report


def main(argv=None):
    import argparse
    ap=argparse.ArgumentParser();ap.add_argument("config",type=Path);ap.add_argument("out",type=Path);a=ap.parse_args(argv)
    print(json.dumps(discover(json.loads(a.config.read_text()),a.out),indent=2));return 0

if __name__=="__main__": raise SystemExit(main())
