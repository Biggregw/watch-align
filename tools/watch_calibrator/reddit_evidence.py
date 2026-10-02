"""Autonomous RepTimeQC evidence enrichment for the watch-family calibrator.

Album-backed RepTimeQC posts are found through Reddit's official OAuth API only (see
reddit_oauth). Without credentials nothing is added. Full Imgur albums are preferred over
single-image native posts because they carry stronger repeatability evidence.

This module changes only the replica stress-test evidence pool. Genuine watches remain the only
population allowed to set calibration limits.
"""
from __future__ import annotations

import csv
import json
import re
from pathlib import Path

import reddit_oauth
from discover import FIELDS, canonical, stable_id


def _factory(detail: str, factories: list[str]) -> str:
    for fac in factories:
        if re.search(rf"\b{re.escape(fac)}\b", detail or "", re.I):
            return fac
    return ""


def discover_imgur_qc(config: dict, wanted: int) -> list[dict]:
    """Find album-backed QC posts through the official Reddit API (fails closed without credentials)."""
    if wanted <= 0:
        return []
    model = str(config["model"]).upper()
    factories = list(config.get("replica_factories", []))
    out, seen_posts, seen_albums = [], set(), set()
    for query in (f"{model} imgur", f"{model} QC album"):
        for post in reddit_oauth.search("RepTimeQC", query, 100):
            if len(out) >= wanted:
                return out
            url = canonical(reddit_oauth.post_url(post))
            detail = reddit_oauth.post_text(post)
            if not url or url in seen_posts:
                continue
            seen_posts.add(url)
            if not re.search(rf"(?<!\d){re.escape(model)}(?!\d)", detail, re.I):
                continue
            album = reddit_oauth.imgur_album(detail)
            factory = _factory(detail, factories)
            if not (album and factory) or album in seen_albums:
                continue
            seen_albums.add(album)
            out.append({"source_url": url, "title": post.get("title") or "", "factory": factory,
                        "album": album, "direct": ""})
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
            "provenance_note": "auto-discovered multi-photo RepTimeQC album via official Reddit API",
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
