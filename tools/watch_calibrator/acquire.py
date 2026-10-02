#!/usr/bin/env python3
"""Watch-calibrator acquisition adapter with first-class Reddit QC media support.

The mature Submariner acquisition path remains the provenance/dedupe/storage authority. This
adapter adds Reddit-hosted media support and then delegates the complete acquisition run to
submariner_acquire. One Reddit post remains one physical watch because discovery assigns one
candidate/watch id per post. No replica image is used to set a genuine tolerance; replica images
are downstream stress-test evidence only.

Native Reddit gallery metadata is not exposed by the anonymous public post RSS feed on current
GitHub-hosted runners, and anonymous JSON/gallery pages return 403. When REDDIT_CLIENT_ID and
REDDIT_CLIENT_SECRET are configured, the resolver therefore uses Reddit's official OAuth API to
obtain gallery_data/media_metadata. Without credentials it prefers complete external albums and
keeps public RSS/direct-image evidence as a fail-closed fallback.
"""
from __future__ import annotations

import argparse
import base64
import html
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urlparse

from bs4 import BeautifulSoup

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
HARVESTER = REPO / "tools" / "dataset_harvester"
sys.path.insert(0, str(HARVESTER))

import submariner_acquire as base  # noqa: E402
from harvester.resolvers import ImageRef  # noqa: E402

ORIGINAL_RESOLVE = base.resolve_candidate
REDDIT_POST = re.compile(r"reddit\.com/(?:r/[^/]+/)?comments/([a-z0-9]+)", re.I)
REDDIT_IMAGE_HOSTS = {"i.redd.it", "preview.redd.it", "external-preview.redd.it"}
ATOM = {"a": "http://www.w3.org/2005/Atom"}
RSS_HEADERS = {"Accept": "application/atom+xml, application/xml, text/xml"}
OAUTH_UA = "WatchAlignResearch/1.3 by Biggregw"
_OAUTH_TOKEN: str | None = None


def reddit_post_id(url: str) -> str:
    m = REDDIT_POST.search(url or "")
    return m.group(1) if m else ""


def _image_url(url: str) -> str:
    u = html.unescape((url or "").strip()).replace("\\/", "/")
    if not u:
        return ""
    p = urlparse(u)
    if p.scheme not in ("http", "https"):
        return ""
    host = p.netloc.lower()
    if host not in REDDIT_IMAGE_HOSTS:
        return ""
    if host in {"preview.redd.it", "external-preview.redd.it"}:
        name = Path(p.path).name
        if name and "." in name:
            return f"https://i.redd.it/{name}"
    return u


def _dedupe_urls(urls: list[str], max_images: int) -> list[str]:
    seen, keep = set(), []
    for raw in urls:
        u = _image_url(raw)
        if not u or u in seen:
            continue
        seen.add(u)
        keep.append(u)
        if len(keep) >= max_images:
            break
    return keep


def _html_image_urls(content_html: str, max_images: int) -> list[str]:
    """Extract Reddit-hosted still images from any media HTML exposed by a public feed."""
    raw = html.unescape(content_html or "").replace("\\/", "/")
    candidates: list[str] = []
    soup = BeautifulSoup(raw, "html.parser")
    for tag in soup.find_all(["a", "img", "source"]):
        for attr in ("href", "src", "srcset"):
            value = tag.get(attr) or ""
            if attr == "srcset":
                candidates.extend(part.strip().split(" ")[0] for part in value.split(",") if part.strip())
            elif value:
                candidates.append(value)
    candidates.extend(
        re.findall(
            r"https?://(?:i|preview|external-preview)\.redd\.it/[^\s\"'<>]+",
            raw,
            re.I,
        )
    )
    return _dedupe_urls(candidates, max_images)


def _post_image_urls(post: dict) -> list[str]:
    """Return still-image URLs in gallery order where Reddit metadata supplies that order."""
    out: list[str] = []
    metadata = post.get("media_metadata") or {}
    gallery = (post.get("gallery_data") or {}).get("items") or []
    ordered_ids = [str(x.get("media_id") or "") for x in gallery if x.get("media_id")]
    if not ordered_ids:
        ordered_ids = list(metadata)
    for media_id in ordered_ids:
        item = metadata.get(media_id) or {}
        source = (item.get("s") or {}).get("u") or ""
        u = _image_url(source)
        if u:
            out.append(u)

    u = _image_url(post.get("url_overridden_by_dest") or post.get("url") or "")
    if u:
        out.append(u)

    for image in ((post.get("preview") or {}).get("images") or []):
        u = _image_url((image.get("source") or {}).get("url") or "")
        if u:
            out.append(u)

    for parent in post.get("crosspost_parent_list") or []:
        out.extend(_post_image_urls(parent))

    return _dedupe_urls(out, 1000)


def reddit_oauth_image_refs(source_url: str, http, max_images: int) -> list[ImageRef]:
    """Resolve a native gallery through Reddit's documented app-only OAuth API when configured."""
    global _OAUTH_TOKEN
    client_id = (os.environ.get("REDDIT_CLIENT_ID") or "").strip()
    client_secret = (os.environ.get("REDDIT_CLIENT_SECRET") or "").strip()
    post_id = reddit_post_id(source_url)
    if not (client_id and client_secret and post_id):
        return []
    try:
        if not _OAUTH_TOKEN:
            basic = base64.b64encode(f"{client_id}:{client_secret}".encode("utf-8")).decode("ascii")
            token_response = http.post(
                "https://www.reddit.com/api/v1/access_token",
                data={"grant_type": "client_credentials"},
                headers={"User-Agent": OAUTH_UA, "Authorization": f"Basic {basic}"},
                api=True,
            )
            token_payload = json.loads(token_response.text)
            _OAUTH_TOKEN = str(token_payload.get("access_token") or "")
            if not _OAUTH_TOKEN:
                return []
        response = http.get(
            f"https://oauth.reddit.com/by_id/t3_{post_id}",
            headers={"Authorization": f"bearer {_OAUTH_TOKEN}", "User-Agent": OAUTH_UA},
            api=True,
        )
        payload = json.loads(response.text)
        post = payload["data"]["children"][0]["data"]
    except Exception:
        return []
    return [ImageRef(url=u) for u in _post_image_urls(post)[:max_images]]


def reddit_rss_image_refs(source_url: str, http, max_images: int) -> list[ImageRef]:
    """Read any image references that happen to be exposed in a public Reddit post Atom feed."""
    post_id = reddit_post_id(source_url)
    if not post_id:
        return []
    rss_url = source_url.rstrip("/") + "/.rss"
    try:
        response = http.get(rss_url, headers=RSS_HEADERS, api=True)
        root = ET.fromstring(response.text)
    except Exception:
        return []

    entries = root.findall("a:entry", ATOM)
    ordered = []
    remainder = []
    source_norm = source_url.rstrip("/")
    for entry in entries:
        link = ""
        for node in entry.findall("a:link", ATOM):
            href = (node.attrib.get("href") or "").rstrip("/")
            if href:
                link = href
                if node.attrib.get("rel", "alternate") == "alternate":
                    break
        if link == source_norm:
            ordered.append(entry)
        else:
            remainder.append(entry)
    ordered.extend(remainder)

    for entry in ordered:
        content = entry.findtext("a:content", default="", namespaces=ATOM) or ""
        urls = _html_image_urls(content, max_images)
        if urls:
            return [ImageRef(url=u) for u in urls]
    return []


def reddit_image_refs(source_url: str, http, max_images: int) -> list[ImageRef]:
    """Legacy anonymous Reddit JSON enrichment; kept only as a best-effort fallback."""
    post_id = reddit_post_id(source_url)
    if not post_id:
        return []
    api_url = f"https://www.reddit.com/comments/{post_id}.json?raw_json=1"
    try:
        response = http.get(api_url, api=True)
        payload = json.loads(response.text)
        post = payload[0]["data"]["children"][0]["data"]
    except Exception:
        return []
    return [ImageRef(url=u) for u in _post_image_urls(post)[:max_images]]


def _merge_refs(groups: list[list[ImageRef]], max_images: int) -> list[ImageRef]:
    seen, keep = set(), []
    for group in groups:
        for ref in group:
            u = _image_url(ref.url or "")
            if not u or u in seen:
                continue
            seen.add(u)
            keep.append(ImageRef(url=u))
            if len(keep) >= max_images:
                return keep
    return keep


def resolve_candidate(row: dict, http, work: Path, max_images: int):
    # Existing Imgur handling remains first choice because it provides the complete QC set without
    # requiring Reddit metadata access.
    if (row.get("image_album_url") or "").strip():
        return ORIGINAL_RESOLVE(row, http, work, max_images)

    source = (row.get("source_url") or "").strip()
    if source and "reddit.com/" in source.lower():
        oauth_refs = reddit_oauth_image_refs(source, http, max_images)
        rss_refs = reddit_rss_image_refs(source, http, max_images)
        json_refs = reddit_image_refs(source, http, max_images) if len(oauth_refs) < max_images else []
        direct = _image_url(row.get("direct_image_url") or "")
        direct_refs = [ImageRef(url=direct)] if direct else []
        refs = _merge_refs([oauth_refs, rss_refs, json_refs, direct_refs], max_images)
        if refs:
            return refs

    return ORIGINAL_RESOLVE(row, http, work, max_images)


def run(pool, out: Path, max_images: int = 12) -> dict:
    original = base.resolve_candidate
    base.resolve_candidate = resolve_candidate
    try:
        return base.run(pool, out, max_images)
    finally:
        base.resolve_candidate = original


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--pool", type=Path, action="append", required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--max-images", type=int, default=12)
    a = ap.parse_args(argv)
    print(json.dumps(run(a.pool, a.out, a.max_images), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
