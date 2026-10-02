"""Autonomous public-source discovery for Watch Align calibration.

Input is the exact watch model plus its model config. Discovery must be able to
start from zero repository source rows. Existing curated pools may be used only
when a model explicitly opts into bootstrap fallback; they are never required
for a clean proof run.

Permitted surfaces only: configured dealer seed pages (fetched with an honest User-Agent) and
Reddit through its official OAuth API (reddit_oauth). Search-engine result pages, Reddit RSS/JSON
endpoints and quarantined dealers are rejected by check_source_policy / not implemented.
"""
from __future__ import annotations

import csv
import glob
import hashlib
import html
import json
import re
import time
from pathlib import Path
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup

import reddit_oauth

REPO = Path(__file__).resolve().parents[2]
UA = "WatchAlignResearch/1.4 (+https://github.com/Biggregw/watch-align)"
# Sources whose bot protection must not be bypassed; configuring them is an error.
QUARANTINED_DOMAINS = {"watchfinder.co.uk", "watchfinder.com"}
TIMEOUT = 20
FIELDS = [
    "candidate_id", "physical_watch_id", "family", "model", "class_tag",
    "factory", "source_type", "source_name", "source_url", "image_album_url",
    "direct_image_url", "provenance_note", "candidate_status", "listing_id",
]
CLASS = {"gen": "gen", "genuine": "gen", "rep": "rep", "replica": "rep"}
REDDIT_IMAGE_HOSTS = {"i.redd.it", "preview.redd.it", "external-preview.redd.it"}


def canonical(url: str) -> str:
    if not url:
        return ""
    u = html.unescape(url.strip())
    p = urlparse(u)
    if not p.netloc:
        return ""
    return f"{p.scheme or 'https'}://{p.netloc.lower()}{p.path.rstrip('/')}"


def check_source_policy(src: dict) -> None:
    """Fail loudly on any configuration that would reintroduce a prohibited source surface."""
    dom = (src.get("domain") or "").lower()
    if any(dom == q or dom.endswith("." + q) for q in QUARANTINED_DOMAINS):
        raise ValueError(f"source {src.get('name')!r} uses quarantined domain {dom}")
    if src.get("queries"):
        raise ValueError(
            f"source {src.get('name')!r} has search-engine queries; web-search scraping is not permitted. "
            "Use dealer seed_urls or the official Reddit API (reddit_direct) instead."
        )


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
    """Prefer the original i.redd.it object when the API gives a preview rendition."""
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


def reddit_search(model: str, subreddit: str = "RepTimeQC", limit: int = 100):
    """Search a QC subreddit through Reddit's official OAuth API only.

    Returns five-tuples: URL, title, searchable detail, direct image hint, album hint. Without
    REDDIT_CLIENT_ID/REDDIT_CLIENT_SECRET this returns nothing (fail closed).
    """
    out, seen = [], set()
    for post in reddit_oauth.search(subreddit, model, limit):
        link = canonical(reddit_oauth.post_url(post))
        detail = reddit_oauth.post_text(post)
        if not link or link in seen:
            continue
        if not re.search(rf"(?<!\d){re.escape(model)}(?!\d)", detail, re.I):
            continue
        direct = _promote_reddit_image(post.get("url_overridden_by_dest") or post.get("url") or "")
        out.append((link, post.get("title") or "", detail, direct, reddit_oauth.imgur_album(detail)))
        seen.add(link)
    return out


def reddit_album(url: str, model: str, hint: str = ""):
    """Extract an Imgur album from already-discovered (official API) text. No network access."""
    return reddit_oauth.imgur_album(hint or ""), hint or ""


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


def add_replica_seeds(config, model, family, rows, seen_urls, counts) -> int:
    """Curated replica QC albums (discovery.replica_seed_csv), acquired via Imgur only.

    Replicas never set a limit, so a fixed, reviewed list is a safe stress-test source that does
    not depend on Reddit credentials. Each row needs the exact model, a stated factory and an Imgur
    album; acquisition then never touches Reddit (the album is resolved first).
    """
    rel = config["discovery"].get("replica_seed_csv")
    if not rel:
        return 0
    path = REPO / rel
    if not path.exists():
        return 0
    added = 0
    with path.open(newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            album = (r.get("image_album_url") or "").strip()
            if (r.get("model") or "").upper() != model or not (r.get("factory") or "").strip():
                continue
            if not re.match(r"https?://(?:www\.)?imgur\.com/a/", album, re.I) or album in seen_urls:
                continue
            cid = (r.get("candidate_id") or stable_id("seed_rep", album)).strip()
            rows.append({
                "candidate_id": cid, "physical_watch_id": cid, "family": family, "model": model,
                "class_tag": "rep", "factory": r["factory"].strip(), "source_type": "forum_qc",
                "source_name": "curated replica seeds", "source_url": canonical(r.get("source_url") or ""),
                "image_album_url": album, "direct_image_url": "",
                "provenance_note": ((r.get("provenance_note") or "") + f"; curated seed {path.name}").strip("; "),
                "candidate_status": "candidate", "listing_id": "",
            })
            seen_urls.add(album)
            counts["rep"] += 1
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
    reddit_status = "not requested"

    # Web/source discovery happens first and is sufficient by itself for a clean run.
    for src in dc["sources"]:
        check_source_policy(src)
        wanted = int(src.get("target", dc.get("per_source_target", 8)))
        hits = []
        for seed in src.get("seed_urls", []):
            for raw, title, snip in page_links(seed.format(model=model), src["domain"].lower(), model):
                hits.append((raw, title, snip, "", ""))
        if src.get("reddit_direct", False):
            if not reddit_oauth.configured():
                reddit_status = "skipped: REDDIT_CLIENT_ID/REDDIT_CLIENT_SECRET not configured"
            else:
                reddit_status = "official OAuth API"
            hits.extend(reddit_search(model, src.get("subreddit", "RepTimeQC")))

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

    replica_seeds = add_replica_seeds(config, model, family, rows, seen_urls, counts)

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
        "reddit_api": reddit_status,
        "replica_seed_candidates": replica_seeds,
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
