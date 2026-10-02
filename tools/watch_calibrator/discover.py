"""Autonomous public-source discovery for Watch Align calibration.

Input is the exact watch model plus its model config. Discovery must be able to
start from zero repository source rows. Existing curated pools may be used only
when a model explicitly opts into bootstrap fallback; they are never required
for a clean proof run.

Reddit note: anonymous Reddit JSON endpoints are frequently blocked from cloud
runners. RepTimeQC discovery therefore uses Reddit's public Atom/RSS search feed
as the primary zero-credential path. When the feed exposes a native Reddit image
or an Imgur album, that media hint is carried into acquisition so acquisition
does not need a second Reddit metadata request.
"""
from __future__ import annotations

import csv
import glob
import hashlib
import html
import json
import re
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import parse_qs, quote_plus, unquote, urljoin, urlparse

import requests
from bs4 import BeautifulSoup

REPO = Path(__file__).resolve().parents[2]
UA = "WatchAlignResearch/1.2 (+https://github.com/Biggregw/watch-align)"
RSS_UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/140.0 Safari/537.36 WatchAlignResearch/1.2"
)
TIMEOUT = 20
FIELDS = [
    "candidate_id", "physical_watch_id", "family", "model", "class_tag",
    "factory", "source_type", "source_name", "source_url", "image_album_url",
    "direct_image_url", "provenance_note", "candidate_status", "listing_id",
]
CLASS = {"gen": "gen", "genuine": "gen", "rep": "rep", "replica": "rep"}
ATOM = {"a": "http://www.w3.org/2005/Atom"}
REDDIT_IMAGE_HOSTS = {"i.redd.it", "preview.redd.it", "external-preview.redd.it"}


def canonical(url: str) -> str:
    if not url:
        return ""
    u = html.unescape(url.strip())
    if "duckduckgo.com/l/?" in u:
        q = parse_qs(urlparse(u).query).get("uddg")
        if q:
            u = unquote(q[0])
    p = urlparse(u)
    if not p.netloc:
        return ""
    return f"{p.scheme or 'https'}://{p.netloc.lower()}{p.path.rstrip('/')}"


def _xml_hits(text: str):
    root = ET.fromstring(text)
    for it in root.findall(".//item"):
        yield (
            it.findtext("link") or "",
            it.findtext("title") or "",
            it.findtext("description") or "",
        )


def bing_rss(query: str, pages: int = 3):
    out = []
    for page in range(max(1, pages)):
        first = 1 + page * 10
        url = (
            "https://www.bing.com/search?format=rss"
            f"&count=50&first={first}&q={quote_plus(query)}"
        )
        try:
            r = requests.get(url, headers={"User-Agent": UA}, timeout=TIMEOUT)
            r.raise_for_status()
            out.extend(_xml_hits(r.text))
        except Exception:
            break
    return out


def bing_html(query: str, pages: int = 3):
    out = []
    for page in range(max(1, pages)):
        first = 1 + page * 10
        try:
            r = requests.get(
                "https://www.bing.com/search",
                params={"q": query, "count": 50, "first": first},
                headers={"User-Agent": UA},
                timeout=TIMEOUT,
            )
            r.raise_for_status()
            s = BeautifulSoup(r.text, "html.parser")
            for item in s.select("li.b_algo"):
                a = item.select_one("h2 a")
                if not a:
                    continue
                sn = item.select_one(".b_caption p")
                out.append((
                    a.get("href") or "",
                    a.get_text(" ", strip=True),
                    sn.get_text(" ", strip=True) if sn else "",
                ))
        except Exception:
            break
    return out


def duck(query: str):
    try:
        r = requests.get(
            "https://html.duckduckgo.com/html/",
            params={"q": query},
            headers={"User-Agent": UA},
            timeout=TIMEOUT,
        )
        r.raise_for_status()
        s = BeautifulSoup(r.text, "html.parser")
        out = []
        for a in s.select("a.result__a"):
            row = a.find_parent(class_="result")
            sn = row.select_one(".result__snippet") if row else None
            out.append((
                a.get("href") or "",
                a.get_text(" ", strip=True),
                sn.get_text(" ", strip=True) if sn else "",
            ))
        return out
    except Exception:
        return []


def search_web(query: str, pages: int):
    """Use independent public search surfaces and leave dedupe to discovery."""
    return bing_rss(query, pages) + bing_html(query, pages) + duck(query)


def fetch_page(url: str):
    try:
        r = requests.get(
            url,
            headers={"User-Agent": UA, "Accept-Language": "en-GB,en;q=0.9"},
            timeout=TIMEOUT,
            allow_redirects=True,
        )
        if r.status_code >= 400 or not r.content.strip():
            return "", "", r.status_code
        return r.url, BeautifulSoup(r.text, "html.parser").get_text(" ", strip=True), r.status_code
    except Exception:
        return "", "", 0


def page_links(url: str, domain: str, model: str):
    """Crawl configured source landing/search pages, not repository source lists."""
    try:
        r = requests.get(
            url,
            headers={"User-Agent": UA, "Accept-Language": "en-GB,en;q=0.9"},
            timeout=TIMEOUT,
            allow_redirects=True,
        )
        r.raise_for_status()
        s = BeautifulSoup(r.text, "html.parser")
    except Exception:
        return []
    out = []
    for a in s.find_all("a", href=True):
        u = canonical(urljoin(r.url, a["href"]))
        if not u:
            continue
        host = urlparse(u).netloc.lower()
        if not (host == domain or host.endswith("." + domain)):
            continue
        blob = f"{a.get_text(' ', strip=True)} {u}"
        if re.search(rf"(?<!\d){re.escape(model)}(?!\d)", blob, re.I):
            out.append((u, a.get_text(" ", strip=True), "source landing page"))
    return out


def _promote_reddit_image(url: str) -> str:
    """Prefer the original i.redd.it object when RSS gives a preview rendition."""
    u = html.unescape((url or "").strip())
    if not u:
        return ""
    p = urlparse(u)
    host = p.netloc.lower()
    if host not in REDDIT_IMAGE_HOSTS:
        return ""
    if host in {"preview.redd.it", "external-preview.redd.it"}:
        name = Path(p.path).name
        if name and "." in name:
            return f"https://i.redd.it/{name}"
    return u


def _reddit_media_from_html(content_html: str) -> tuple[str, str]:
    """Return (imgur_album, first_reddit_image) from an Atom entry's HTML."""
    raw = html.unescape(content_html or "")
    album_match = re.search(r"https?://(?:www\.)?imgur\.com/a/[A-Za-z0-9_-]+", raw, re.I)
    album = album_match.group(0) if album_match else ""
    soup = BeautifulSoup(raw, "html.parser")
    candidates = []
    for tag in soup.find_all(["a", "img"]):
        candidates.extend([tag.get("href") or "", tag.get("src") or ""])
    for candidate in candidates:
        image = _promote_reddit_image(candidate)
        if image:
            return album, image
    return album, ""


def reddit_rss_search(model: str, limit: int = 100):
    """Search RepTimeQC through Reddit's public Atom feed.

    Returns five-tuples: URL, title, searchable detail, direct image hint, album hint.
    A single feed request is intentional: Reddit's anonymous RSS surface is rate-limited and the
    target calibration set only needs a modest number of independent watches.
    """
    url = "https://www.reddit.com/r/RepTimeQC/search.rss"
    try:
        r = requests.get(
            url,
            params={
                "q": model,
                "restrict_sr": "1",
                "sort": "new",
                "t": "all",
                "limit": min(max(int(limit), 1), 100),
            },
            headers={
                "User-Agent": RSS_UA,
                "Accept": "application/atom+xml, application/xml, text/xml",
            },
            timeout=TIMEOUT,
        )
        r.raise_for_status()
        root = ET.fromstring(r.text)
    except Exception:
        return []

    out = []
    seen = set()
    for entry in root.findall("a:entry", ATOM):
        title = entry.findtext("a:title", default="", namespaces=ATOM) or ""
        content = entry.findtext("a:content", default="", namespaces=ATOM) or ""
        link = ""
        for node in entry.findall("a:link", ATOM):
            href = node.attrib.get("href") or ""
            if node.attrib.get("rel", "alternate") == "alternate" and href:
                link = href
                break
            if not link and href:
                link = href
        link = canonical(link)
        if not link or link in seen:
            continue
        detail_text = BeautifulSoup(html.unescape(content), "html.parser").get_text(" ", strip=True)
        detail = f"{title} {detail_text} {content}"
        if not re.search(rf"(?<!\d){re.escape(model)}(?!\d)", detail, re.I):
            continue
        album, direct_image = _reddit_media_from_html(content)
        out.append((link, title, detail, direct_image, album))
        seen.add(link)
    return out


def reddit_search(model: str, factories: list[str], limit: int = 100):
    """Compatibility name for the primary public Reddit discovery path.

    Factory identification is performed later from each post's title/body, so one model-level RSS
    request is enough and avoids hammering the anonymous feed with one request per factory.
    """
    return reddit_rss_search(model, limit)


def reddit_album(url: str, model: str, hint: str = ""):
    """Extract an Imgur album from already-discovered text.

    JSON enrichment is retained only as a best-effort legacy assist. Discovery does not depend on
    it because cloud runners can receive 403 from Reddit's anonymous JSON endpoints.
    """
    text = hint or ""
    mm = re.search(r"https?://(?:www\.)?imgur\.com/a/[A-Za-z0-9_-]+", text, re.I)
    if mm:
        return mm.group(0), text
    m = re.search(r"reddit\.com/(?:r/[^/]+/)?comments/([a-z0-9]+)", url, re.I)
    if m:
        try:
            jurl = f"https://www.reddit.com/comments/{m.group(1)}.json?raw_json=1"
            r = requests.get(jurl, headers={"User-Agent": UA}, timeout=TIMEOUT)
            r.raise_for_status()
            post = r.json()[0]["data"]["children"][0]["data"]
            text = " ".join([
                text, post.get("title", ""), post.get("selftext", ""),
                post.get("url_overridden_by_dest", ""),
            ])
        except Exception:
            pass
    mm = re.search(r"https?://(?:www\.)?imgur\.com/a/[A-Za-z0-9_-]+", text, re.I)
    return (mm.group(0) if mm else ""), text


def listing_id(url: str, text: str = "", model: str = "") -> str:
    """Prefer dealer-native SKU/product code; URL ids are a fallback."""
    for pat in (
        r"(?:SKU|Sku|product code)\s*[:#-]?\s*(\d{4,})",
        rf"(?<!\d){re.escape(model)}(?!\d)\s+\d{{4}}\s+(\d{{5,}})" if model else r"$^",
    ):
        m = re.search(pat, text or "", re.I)
        if m:
            return m.group(1)
    path = urlparse(url).path
    ms = re.findall(r"(?<!\d)(\d{5,})(?!\d)", path)
    return ms[-1] if ms else ""


def stable_id(prefix: str, identity: str) -> str:
    return f"{prefix}_{hashlib.sha1(identity.encode()).hexdigest()[:12]}"


def add_bootstrap(config, model, family, rows, seen_urls, seen_listings, counts):
    """Optional fallback only. Clean autonomous proof runs set allow_bootstrap=false."""
    if not config["discovery"].get("allow_bootstrap", False):
        return 0
    added = 0
    for pattern in config["discovery"].get("bootstrap_pool_globs", []):
        for fn in sorted(glob.glob(str(REPO / pattern))):
            with open(fn, newline="", encoding="utf-8") as fh:
                for r in csv.DictReader(fh):
                    if (r.get("model") or "").upper() != model:
                        continue
                    if (r.get("candidate_status") or "candidate") != "candidate":
                        continue
                    cls = CLASS.get(
                        (r.get("class_tag") or r.get("class") or r.get("class_label") or "").lower(),
                        "",
                    )
                    if not cls:
                        continue
                    url = canonical(r.get("source_url") or "")
                    album = (r.get("image_album_url") or "").strip()
                    direct = (r.get("direct_image_url") or "").strip()
                    key = url or album or direct
                    if not key or key in seen_urls:
                        continue
                    note = (r.get("provenance_note") or "") + f"; bootstrap fallback from {Path(fn).name}"
                    lid = (r.get("listing_id") or "") or listing_id(url, note, model)
                    lkey = ((r.get("source_name") or "").lower(), lid) if lid else None
                    if lkey and lkey in seen_listings:
                        continue
                    cid = (r.get("candidate_id") or r.get("physical_watch_id") or stable_id("bootstrap", key)).strip()
                    wid = (r.get("physical_watch_id") or cid).strip()
                    rows.append({
                        "candidate_id": cid, "physical_watch_id": wid, "family": family,
                        "model": model, "class_tag": cls, "factory": r.get("factory", "") or "",
                        "source_type": r.get("source_type", "") or "known_source",
                        "source_name": r.get("source_name", "") or Path(fn).name,
                        "source_url": url, "image_album_url": album, "direct_image_url": direct,
                        "provenance_note": note.strip("; "), "candidate_status": "candidate",
                        "listing_id": lid,
                    })
                    seen_urls.add(key)
                    if lkey:
                        seen_listings.add(lkey)
                    counts[cls] += 1
                    added += 1
    return added


def discover(config: dict, out_csv: Path) -> dict:
    model = str(config["model"]).upper()
    family = config["family"]
    dc = config["discovery"]
    seen_urls, seen_listings = set(), set()
    rows = []
    counts = {"gen": 0, "rep": 0}
    source_counts = {}
    source_raw_hits = {}
    web_candidates = 0

    # Web/source discovery happens first and is sufficient by itself for a clean run.
    for src in dc["sources"]:
        wanted = int(src.get("target", dc.get("per_source_target", 8)))
        hits = []
        pages = int(src.get("search_pages", dc.get("search_pages", 3)))
        for templ in src.get("queries", []):
            for raw, title, snip in search_web(templ.format(model=model), pages):
                hits.append((raw, title, snip, "", ""))
        for seed in src.get("seed_urls", []):
            for raw, title, snip in page_links(seed.format(model=model), src["domain"].lower(), model):
                hits.append((raw, title, snip, "", ""))
        if src.get("reddit_direct", False):
            hits.extend(reddit_search(model, config.get("replica_factories", [])))

        source_raw_hits[src["name"]] = len(hits)
        accepted_here = 0
        for raw, title, snip, direct_hint, album_hint in hits:
            if accepted_here >= wanted:
                break
            url = canonical(raw)
            if not url or url in seen_urls:
                continue
            host = urlparse(url).netloc.lower()
            dom = src["domain"].lower()
            if not (host == dom or host.endswith("." + dom)):
                continue

            detail = f"{title} {snip} {url}"
            album = album_hint or ""
            direct_image = direct_hint or ""
            lid = ""

            if "reddit.com" in host:
                if not album:
                    found_album, detail = reddit_album(url, model, detail)
                    album = found_album or album
                if not re.search(rf"(?<!\d){re.escape(model)}(?!\d)", detail, re.I):
                    continue
                if src.get("require_album", True) and not album:
                    continue
            else:
                final_url, page_text_value, _ = fetch_page(url)
                if final_url:
                    url = canonical(final_url)
                detail = f"{detail} {page_text_value}"
                if not re.search(rf"(?<!\d){re.escape(model)}(?!\d)", detail, re.I):
                    continue
                lid = listing_id(url, detail, model)
                if src.get("require_listing_id", False) and not lid:
                    continue

            factory = ""
            for fac in config.get("replica_factories", []):
                if re.search(rf"\b{re.escape(fac)}\b", detail, re.I):
                    factory = fac
                    break
            if src["class"] == "rep" and src.get("require_factory", True) and not factory:
                continue

            # A replica candidate must have some media acquisition path. This prevents a search
            # result from inflating the independent-watch count when the photos are unreachable.
            if src["class"] == "rep" and not (album or direct_image):
                continue

            lkey = (src["name"].lower(), lid) if lid else None
            if lkey and lkey in seen_listings:
                continue
            identity = f"{src['name']}|{lid or url}"
            cid = stable_id(src.get("id_prefix", src["class"]), identity)
            rows.append({
                "candidate_id": cid,
                "physical_watch_id": cid,
                "family": family,
                "model": model,
                "class_tag": src["class"],
                "factory": factory,
                "source_type": src["source_type"],
                "source_name": src["name"],
                "source_url": url,
                "image_album_url": album,
                "direct_image_url": direct_image,
                "provenance_note": "auto-discovered from public source/search",
                "candidate_status": "candidate",
                "listing_id": lid,
            })
            seen_urls.add(url)
            if album:
                seen_urls.add(album)
            if lkey:
                seen_listings.add(lkey)
            counts[src["class"]] += 1
            accepted_here += 1
            web_candidates += 1

        source_counts[src["name"]] = accepted_here
        time.sleep(0.1)

    # Bootstrap is an explicitly configured fallback only, never part of a clean proof.
    bootstrap = 0
    if counts["gen"] < dc.get("minimum_gen_candidates", 1):
        bootstrap = add_bootstrap(
            config, model, family, rows, seen_urls, seen_listings, counts
        )

    out_csv.parent.mkdir(parents=True, exist_ok=True)
    with out_csv.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(rows)

    report = {
        "model": model,
        "family": family,
        "candidates": len(rows),
        "by_class": counts,
        "by_source": source_counts,
        "raw_hits_by_source": source_raw_hits,
        "web_candidates": web_candidates,
        "bootstrap_candidates": bootstrap,
        "clean_discovery": bootstrap == 0,
        "output": str(out_csv),
    }
    out_csv.with_suffix(".json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8"
    )
    return report


def main(argv=None):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("config", type=Path)
    ap.add_argument("out", type=Path)
    a = ap.parse_args(argv)
    print(json.dumps(discover(json.loads(a.config.read_text()), a.out), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
