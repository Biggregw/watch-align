"""Autonomous RepTimeQC evidence enrichment for the watch-family calibrator.

Reddit's anonymous JSON/gallery pages are blocked from GitHub-hosted runners, while public
per-post Atom feeds remain readable. Discovery therefore prefers QC posts that expose a complete
Imgur album in their public post feed, then fills any remaining replica target with the native
single-image evidence already found by the normal Reddit RSS search. If official Reddit OAuth
credentials are configured, acquisition can expand native Reddit galleries later as well.

This module changes only the replica stress-test evidence pool. Genuine watches remain the only
population allowed to set calibration limits.
"""
from __future__ import annotations

import csv
import html
import json
import re
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urlparse

import requests
from bs4 import BeautifulSoup

from discover import ATOM, FIELDS, RSS_UA, canonical, search_web, stable_id, _reddit_media_from_html

TIMEOUT = 20


def _factory(detail: str, factories: list[str]) -> str:
    for fac in factories:
        if re.search(rf"\b{re.escape(fac)}\b", detail or "", re.I):
            return fac
    return ""


def _post_rss_album(post_url: str, model: str, factories: list[str]) -> dict | None:
    """Resolve one public Reddit post feed to a verified model/factory/Imgur album tuple."""
    try:
        r = requests.get(
            post_url.rstrip("/") + "/.rss",
            headers={
                "User-Agent": RSS_UA,
                "Accept": "application/atom+xml, application/xml, text/xml",
            },
            timeout=TIMEOUT,
        )
        r.raise_for_status()
        root = ET.fromstring(r.text)
    except Exception:
        return None

    source = canonical(post_url)
    entries = root.findall("a:entry", ATOM)
    preferred, other = [], []
    for entry in entries:
        link = ""
        for node in entry.findall("a:link", ATOM):
            href = node.attrib.get("href") or ""
            if href:
                link = canonical(href)
                if node.attrib.get("rel", "alternate") == "alternate":
                    break
        (preferred if link == source else other).append(entry)

    for entry in preferred + other:
        title = entry.findtext("a:title", default="", namespaces=ATOM) or ""
        content = entry.findtext("a:content", default="", namespaces=ATOM) or ""
        raw = html.unescape(content)
        text = BeautifulSoup(raw, "html.parser").get_text(" ", strip=True)
        detail = f"{title} {text} {raw}"
        if not re.search(rf"(?<!\d){re.escape(model)}(?!\d)", detail, re.I):
            continue
        album, direct = _reddit_media_from_html(content)
        if not album:
            m = re.search(r"https?://(?:www\.)?imgur\.com/a/[A-Za-z0-9_-]+", raw, re.I)
            album = m.group(0) if m else ""
        factory = _factory(detail, factories)
        if album and factory:
            return {
                "source_url": source,
                "title": title,
                "factory": factory,
                "album": album,
                "direct": direct,
            }
    return None


def discover_imgur_qc(config: dict, wanted: int) -> list[dict]:
    """Find independent album-backed RepTimeQC watches without repository bootstrap data."""
    if wanted <= 0:
        return []
    model = str(config["model"]).upper()
    factories = list(config.get("replica_factories", []))
    pages = int(config.get("discovery", {}).get("search_pages", 4))
    queries = [
        f'site:reddit.com/r/RepTimeQC/comments "{model}" "imgur.com/a"',
        f'site:reddit.com/r/RepTimeQC/comments "{model}" "imgur"',
        f'site:reddit.com/r/RepTimeQC/comments "{model}" QC album',
    ]
    hits = []
    seen_posts = set()
    for query in queries:
        for raw, title, snippet in search_web(query, pages):
            url = canonical(raw)
            host = urlparse(url).netloc.lower() if url else ""
            if not url or "reddit.com" not in host or "/comments/" not in url or url in seen_posts:
                continue
            seen_posts.add(url)
            hits.append((url, title, snippet))

    out = []
    seen_albums = set()
    for url, _, _ in hits:
        if len(out) >= wanted:
            break
        resolved = _post_rss_album(url, model, factories)
        # Keep the public feed polite and avoid burst-rate failures.
        time.sleep(0.75)
        if not resolved or resolved["album"] in seen_albums:
            continue
        seen_albums.add(resolved["album"])
        out.append(resolved)
    return out


def _read(path: Path) -> list[dict]:
    with path.open(newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def enrich(config: dict, pool_csv: Path, report: dict) -> dict:
    """Prefer full-album replica watches, then fill to the configured target with native posts."""
    rows = _read(pool_csv)
    model = str(config["model"]).upper()
    family = config["family"]
    rep_source = next(
        (s for s in config["discovery"].get("sources", []) if s.get("class") == "rep" and s.get("reddit_direct")),
        None,
    )
    if not rep_source:
        return report

    target = int(rep_source.get("target", config["discovery"].get("target_rep", 0) or 0))
    genuine = [r for r in rows if (r.get("class_tag") or "") != "rep"]
    existing = [r for r in rows if (r.get("class_tag") or "") == "rep"]
    album_rows = [r for r in existing if (r.get("image_album_url") or "").strip()]
    native_rows = [r for r in existing if not (r.get("image_album_url") or "").strip()]

    seen_urls = {canonical(r.get("source_url") or "") for r in album_rows}
    seen_albums = {(r.get("image_album_url") or "").strip() for r in album_rows}
    needed = max(0, target - len(album_rows))
    discovered = discover_imgur_qc(config, needed)
    for item in discovered:
        url, album = item["source_url"], item["album"]
        if url in seen_urls or album in seen_albums:
            continue
        cid = stable_id(rep_source.get("id_prefix", "auto_reptimeqc"), f"{rep_source['name']}|{url}")
        album_rows.append({
            "candidate_id": cid,
            "physical_watch_id": cid,
            "family": family,
            "model": model,
            "class_tag": "rep",
            "factory": item["factory"],
            "source_type": rep_source.get("source_type", "forum_qc"),
            "source_name": rep_source["name"],
            "source_url": url,
            "image_album_url": album,
            "direct_image_url": item.get("direct", ""),
            "provenance_note": "auto-discovered multi-photo RepTimeQC album from public search/post RSS",
            "candidate_status": "candidate",
            "listing_id": "",
        })
        seen_urls.add(url)
        seen_albums.add(album)

    # Full albums carry much stronger repeatability evidence, so they get first choice. Native
    # Reddit posts remain valid independent watches and fill the configured target when necessary.
    selected = album_rows[:target]
    selected_urls = {canonical(r.get("source_url") or "") for r in selected}
    for row in native_rows:
        if len(selected) >= target:
            break
        if canonical(row.get("source_url") or "") in selected_urls:
            continue
        selected.append(row)
        selected_urls.add(canonical(row.get("source_url") or ""))

    with pool_csv.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(genuine + selected)

    report = dict(report)
    report["candidates"] = len(genuine) + len(selected)
    report.setdefault("by_class", {})["rep"] = len(selected)
    report.setdefault("by_source", {})[rep_source["name"]] = len(selected)
    report["replica_media_candidates"] = {
        "target": target,
        "album_backed": sum(bool((r.get("image_album_url") or "").strip()) for r in selected),
        "native_direct_fallback": sum(not bool((r.get("image_album_url") or "").strip()) for r in selected),
        "new_album_posts_found": len(discovered),
    }
    pool_csv.with_suffix(".json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report
