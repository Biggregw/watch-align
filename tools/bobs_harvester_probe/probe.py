#!/usr/bin/env python3
"""Bounded live-site probe for the Bob's Rolex Harvester (diagnostics only; no harvesting).

Runs the app's own catalogue / product / capture JavaScript (copied from MainActivity v1.3) in headless Chromium with
the app's Android user agent, and records at each step what the app would see:
  catalogue  links scanned, product links found, keyword matches, pagination behaviour
  product    page load, h1, SKU, every <img>/srcset/<source>/JSON-LD image on the page, what the app's extractor keeps
  capture    the app's canvas capture per image URL, instrumented: load ok / load error / canvas SecurityError (tainted)
  download   plain HTTP GET of each image URL (status, content-type, bytes) and save for the suitability gate
Bounded: at most --pages catalogue pages and --products product pages, with delays. Images are written to --out only
(never committed or uploaded).
"""
import argparse, json, os, re, time, hashlib, urllib.parse
import requests
from playwright.sync_api import sync_playwright

UA = "Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0 Mobile Safari/537.36"
ROOT = "https://www.bobswatches.com/rolex/"

CATALOG_JS = r"""(function(){const a=[],seen=new Set();let rolex=0;document.querySelectorAll('a[href]').forEach(x=>{const im=x.querySelector('img');const parts=[x.innerText,x.textContent,x.getAttribute('aria-label'),x.getAttribute('title'),im&&im.alt].filter(Boolean);const t=parts.join(' ').replace(/\s+/g,' ').trim();const u=x.href||'';if(/Rolex/i.test(t))rolex++;if(!u||seen.has(u))return;let q;try{q=new URL(u)}catch(e){return}const path=q.pathname.toLowerCase();if(!/bobswatches\.com$/i.test(q.hostname))return;if(!/\.html$/i.test(path))return;if(/\/(rolex-blog|sell-|rolex-app|about|faq|shipping|returns)/i.test(path))return;const hay=(t+' '+u).replace(/[-_]/g,' ');if(!/Rolex/i.test(hay))return;if(!/(?:^|[^0-9])(?:[0-9]{4,6}[A-Z]{0,5})(?:[^0-9]|$)/i.test(hay))return;seen.add(u);a.push({t:t,u:u,i:im?(im.currentSrc||im.src||im.getAttribute('data-src')||''):''});});return JSON.stringify({a:a,b:(document.body.innerText||'').slice(0,24000),hrefs:document.querySelectorAll('a[href]').length,rolex:rolex,title:document.title||''});})();"""

def product_js(ref):
    q = json.dumps(ref)
    return ("(function(){"
            "const h=(document.querySelector('h1')?.innerText||'').replace(/\\s+/g,' ').trim();"
            "const b=(document.body.innerText||'').replace(/\\s+/g,' ').slice(0,18000);"
            "let sku='';const sm=b.match(/SKU\\s*[:#-]?\\s*(\\d{4,8})/i);if(sm)sku=sm[1];"
            "const ref=" + q + ";const out=[],seen=new Set();"
            "function norm(u){if(!u)return'';if(typeof u==='object')u=u.url||u.contentUrl||u['@id']||'';"
            "try{const x=new URL(String(u),location.href);if(!/^https?:$/i.test(x.protocol))return'';return x.href;}catch(e){return'';}}"
            "function add(u,a,w,h){u=norm(u);if(!u||seen.has(u))return;seen.add(u);out.push({u:u,a:a||'',w:w||0,h:h||0});}"
            "document.querySelectorAll('script[type=\"application/ld+json\"]').forEach(s=>{try{let d=JSON.parse(s.textContent),n=Array.isArray(d)?d:[d];"
            "n.forEach(x=>{if(x&&x['@type']==='Product'){if(!sku&&x.sku)sku=String(x.sku);let m=x.image||[];if(!Array.isArray(m))m=[m];m.forEach(u=>add(u,'jsonld '+ref,1000,1000));}});}catch(e){}});"
            "document.querySelectorAll('img').forEach(im=>{const u=norm(im.currentSrc||im.src||im.getAttribute('data-src')||''),a=im.alt||'',hay=(u+' '+a).toUpperCase();"
            "if(u&&((ref&&hay.includes(ref.toUpperCase()))||(sku&&hay.includes(sku))))add(u,a,im.naturalWidth||0,im.naturalHeight||0);});"
            "return JSON.stringify({h:h,b:b,s:sku,m:out});})();")

# everything image-like on the page (what a broader extractor could see)
ALL_IMAGES_JS = r"""(function(){const r=[];const push=(k,u,extra)=>{if(u)r.push(Object.assign({k:k,u:String(u)},extra||{}));};
document.querySelectorAll('img').forEach(im=>{push('img.currentSrc',im.currentSrc,{w:im.naturalWidth,h:im.naturalHeight,alt:im.alt,cls:im.className});push('img.src',im.getAttribute('src'));
 ['data-src','data-zoom-image','data-large','data-original','data-lazy','data-full','data-image'].forEach(a=>push('img.'+a,im.getAttribute(a)));push('img.srcset',im.getAttribute('srcset'));push('img.data-srcset',im.getAttribute('data-srcset'));});
document.querySelectorAll('source').forEach(s=>{push('source.srcset',s.getAttribute('srcset'));push('source.data-srcset',s.getAttribute('data-srcset'));});
document.querySelectorAll('a[href]').forEach(a=>{if(/\.(jpe?g|png|webp|avif)(\?|$)/i.test(a.href))push('a.href',a.href);});
document.querySelectorAll('meta[property="og:image"],meta[name="twitter:image"]').forEach(m=>push('meta',m.content));
document.querySelectorAll('script[type="application/ld+json"]').forEach(s=>push('jsonld.raw',s.textContent.slice(0,1500)));
const ov=[...document.querySelectorAll('div,section,aside')].filter(e=>{const s=getComputedStyle(e);return (s.position==='fixed')&&e.offsetWidth>200&&e.offsetHeight>100&&s.display!=='none'&&s.visibility!=='hidden';}).map(e=>(e.className||e.id||'')+': '+(e.innerText||'').replace(/\s+/g,' ').slice(0,120));
return JSON.stringify({imgs:r,overlays:ov,ready:document.readyState,url:location.href,title:document.title});})();"""

CAPTURE_JS = r"""async (urls)=>{const res=[];for(const raw of urls){const o={u:raw};try{const u=new URL(raw,location.href);o.host=u.hostname;o.sameOrigin=(u.origin===location.origin);
 if(!u.hostname.toLowerCase().endsWith('bobswatches.com')){o.r='skipped: not bobswatches host';res.push(o);continue;}
 const img=await new Promise((ok,fail)=>{const x=new Image();x.onload=()=>ok(x);x.onerror=()=>fail(new Error('load error'));x.src=u.href;});
 o.w=img.naturalWidth;o.h=img.naturalHeight;const max=1900,sc=Math.min(1,max/Math.max(o.w,o.h));const cv=document.createElement('canvas');cv.width=Math.max(1,Math.round(o.w*sc));cv.height=Math.max(1,Math.round(o.h*sc));
 cv.getContext('2d').drawImage(img,0,0,cv.width,cv.height);const d=cv.toDataURL('image/jpeg',0.95);o.r='captured';o.bytes=Math.round((d.length-23)*3/4);}
 catch(e){o.r='FAILED: '+(e&&e.name)+': '+(e&&e.message);}res.push(o);}return res;}"""

REF_ANY = re.compile(r"(?<![0-9])([0-9]{4,6}[A-Z]{0,5})(?![0-9])", re.I)

def search_text(s): return re.sub(r"[^a-z0-9]+", " ", (s or "").lower()).strip()
def matches(flt, title, url):
    hay = search_text(f"{title} {url}"); return all(p in hay for p in search_text(flt).split())

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--out", required=True); ap.add_argument("--pages", type=int, default=6)
    ap.add_argument("--products", type=int, default=6); ap.add_argument("--want", action="append", default=["194818", "194255"])
    a = ap.parse_args(); os.makedirs(a.out, exist_ok=True); report = {"catalog": [], "products": []}
    with sync_playwright() as pw:
        br = pw.chromium.launch(); ctx = br.new_context(user_agent=UA, viewport={"width": 412, "height": 915}, is_mobile=True)
        pg = ctx.new_page(); links = {}
        for n in range(1, a.pages + 1):
            url = ROOT if n == 1 else f"{ROOT}?page={n}"
            t0 = time.time()
            try:
                resp = pg.goto(url, wait_until="load", timeout=60000); st = resp.status if resp else None
            except Exception as e:
                report["catalog"].append({"page": n, "url": url, "error": repr(e)}); print("CATALOG", n, "ERROR", e); continue
            pg.evaluate("window.scrollTo(0,document.body.scrollHeight)"); pg.wait_for_timeout(3200)
            o = json.loads(pg.evaluate(CATALOG_JS)); new = 0
            for z in o["a"]:
                u = urllib.parse.urlunsplit(urllib.parse.urlsplit(z["u"])._replace(query="", fragment=""))
                if u not in links: links[u] = z["t"]; new += 1
            tot = re.search(r"\bof\s+([0-9,]+)\s+results\b", o["b"], re.I)
            rec = {"page": n, "url": url, "status": st, "secs": round(time.time() - t0, 1), "title": o["title"], "hrefs": o["hrefs"],
                   "rolex_links": o["rolex"], "product_links": len(o["a"]), "new_unique": new, "results_total": tot.group(1) if tot else None,
                   "match_submariner_124060": sum(matches("submariner 124060", z["t"], z["u"]) for z in o["a"]),
                   "match_124060": sum(matches("124060", z["t"], z["u"]) for z in o["a"]),
                   "match_submariner": sum(matches("submariner", z["t"], z["u"]) for z in o["a"])}
            report["catalog"].append(rec); print("CATALOG", json.dumps(rec))
            time.sleep(1.5)
        # product pages: the two known 124060 listings first, then one 124060 and one non-Submariner model
        cand = [u for u in links if any(w in u for w in a.want)]
        cand += [u for u in links if "124060" in u and u not in cand][:1]
        cand += [u for u in links if re.search(r"126710|daytona|datejust", u, re.I) and u not in cand][:1]
        print("KNOWN-LISTING URLS FOUND:", [u for u in links if any(w in u for w in a.want)])
        for u in cand[: a.products]:
            ref = (REF_ANY.search(f"{links[u]} {u}") or [None, ""])[1].upper() if REF_ANY.search(f"{links[u]} {u}") else ""
            m = REF_ANY.search(f"{links[u]} {u}"); ref = m.group(1).upper() if m else ""
            rec = {"url": u, "catalog_title": links[u], "ref_guess": ref}
            try:
                resp = pg.goto(u, wait_until="load", timeout=60000); rec["status"] = resp.status if resp else None
                pg.evaluate("window.scrollTo(0,document.body.scrollHeight)"); pg.wait_for_timeout(3200)
                po = json.loads(pg.evaluate(product_js(ref)))
                rec.update({"h1": po["h"], "sku": po["s"], "app_extracted": po["m"]})
                allimg = json.loads(pg.evaluate(ALL_IMAGES_JS)); rec["all_images"] = allimg["imgs"]; rec["overlays"] = allimg["overlays"]
                urls = [x["u"] for x in po["m"]]
                rec["capture"] = pg.evaluate(CAPTURE_JS, urls)
                dl = []
                cookies = {c["name"]: c["value"] for c in ctx.cookies()}
                for x in urls:
                    d = {"u": x}
                    for label, hdr in (("plain", {"User-Agent": UA, "Referer": u}),
                                       ("app_accept", {"User-Agent": UA, "Referer": u, "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"})):
                        try:
                            r = requests.get(x, headers=hdr, cookies=cookies, timeout=40)
                            d[label] = {"status": r.status_code, "type": r.headers.get("content-type"), "bytes": len(r.content), "final": r.url}
                            if label == "plain" and r.ok and r.content[:2] == b"\xff\xd8":
                                fn = os.path.join(a.out, f"{ref}_{po['s'] or 'nosku'}_{hashlib.sha256(r.content).hexdigest()[:10]}.jpg")
                                open(fn, "wb").write(r.content); d["saved"] = os.path.basename(fn)
                        except Exception as e:
                            d[label] = {"error": repr(e)}
                    dl.append(d); time.sleep(0.5)
                rec["download"] = dl
            except Exception as e:
                rec["error"] = repr(e)
            report["products"].append(rec)
            print("PRODUCT", json.dumps({k: v for k, v in rec.items() if k not in ("all_images",)})[:6000])
            print("ALLIMG", u, json.dumps(rec.get("all_images", []))[:6000])
            time.sleep(2)
        br.close()
    json.dump(report, open(os.path.join(a.out, "report.json"), "w"), indent=1)

if __name__ == "__main__":
    main()
