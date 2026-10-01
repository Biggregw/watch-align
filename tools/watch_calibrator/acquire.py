#!/usr/bin/env python3
"""Watch-calibrator acquisition adapter with first-class Reddit QC media support.

The mature Submariner acquisition path remains the provenance/dedupe/storage authority. This
adapter only adds a resolver for Reddit-hosted QC posts (gallery, i.redd.it/direct image and
preview fallback) and then delegates the complete acquisition run to submariner_acquire.
One Reddit post remains one physical watch because discovery assigns one candidate/watch id per
post. No replica image is used to set a genuine tolerance; this module only acquires evidence.
"""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
HARVESTER = REPO / "tools" / "dataset_harvester"
sys.path.insert(0, str(HARVESTER))

import submariner_acquire as base  # noqa: E402
from harvester.http import FetchError  # noqa: E402
from harvester.resolvers import ImageRef  # noqa: E402

ORIGINAL_RESOLVE = base.resolve_candidate
REDDIT_POST = re.compile(r"reddit\.com/(?:r/[^/]+/)?comments/([a-z0-9]+)", re.I)
REDDIT_IMAGE_HOSTS = {"i.redd.it", "preview.redd.it", "external-preview.redd.it"}


def reddit_post_id(url: str) -> str:
    m = REDDIT_POST.search(url or "")
    return m.group(1) if m else ""


def _image_url(url: str) -> str:
    u = html.unescape((url or "").strip())
    if not u:
        return ""
    p = urlparse(u)
    if p.scheme not in ("http", "https"):
        return ""
    host = p.netloc.lower()
    if host not in REDDIT_IMAGE_HOSTS:
        return ""
    return u


def _post_image_urls(post: dict) -> list[str]:
    """Return still-image URLs in gallery order where Reddit supplies that order."""
    out: list[str] = []

    # Native Reddit gallery. media_metadata contains the full-resolution source URL while
    # gallery_data preserves the user's photo order.
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

    # Single-image Reddit post.
    u = _image_url(post.get("url_overridden_by_dest") or post.get("url") or "")
    if u:
        out.append(u)

    # Preview is a conservative fallback for posts where Reddit omits media_metadata/source.
    for image in ((post.get("preview") or {}).get("images") or []):
        u = _image_url((image.get("source") or {}).get("url") or "")
        if u:
            out.append(u)

    # Some QC posts are crossposts. Treat the original post's images as belonging to this QC
    # post, but keep the physical-watch identity anchored to the RepTimeQC post itself.
    for parent in post.get("crosspost_parent_list") or []:
        out.extend(_post_image_urls(parent))

    seen, keep = set(), []
    for u in out:
        key = html.unescape(u)
        if key not in seen:
            seen.add(key)
            keep.append(key)
    return keep


def reddit_image_refs(source_url: str, http, max_images: int) -> list[ImageRef]:
    post_id = reddit_post_id(source_url)
    if not post_id:
        return []
    api_url = f"https://www.reddit.com/comments/{post_id}.json?raw_json=1"
    response = http.get(api_url, api=True)
    try:
        payload = json.loads(response.text)
        post = payload[0]["data"]["children"][0]["data"]
    except (ValueError, TypeError, KeyError, IndexError) as exc:
        raise FetchError("reddit_json_invalid", source_url, retryable=True) from exc
    return [ImageRef(url=u) for u in _post_image_urls(post)[:max_images]]


def resolve_candidate(row: dict, http, work: Path, max_images: int):
    # Existing Imgur handling remains first choice when the post supplies an album.
    if (row.get("image_album_url") or "").strip():
        return ORIGINAL_RESOLVE(row, http, work, max_images)
    source = (row.get("source_url") or "").strip()
    if source and "reddit.com/" in source.lower():
        refs = reddit_image_refs(source, http, max_images)
        if refs:
            return refs
    return ORIGINAL_RESOLVE(row, http, work, max_images)


def run(pool, out: Path, max_images: int = 12) -> dict:
    # Keep the research acquisition implementation unchanged; temporarily supply the additional
    # resolver at its documented source-resolution seam.
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
