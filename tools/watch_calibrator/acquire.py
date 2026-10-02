#!/usr/bin/env python3
"""Watch-calibrator acquisition adapter with first-class Reddit QC media support.

The mature Submariner acquisition path remains the provenance/dedupe/storage authority. This
adapter adds Reddit-hosted media support and narrow resolvers for verified dealer gallery layouts,
then delegates the complete acquisition run to submariner_acquire. One Reddit post remains one
physical watch because discovery assigns one candidate/watch id per post. No replica image is used
to set a genuine tolerance; replica images are downstream stress-test evidence only.

Reddit is reached only through the official OAuth API (reddit_oauth, credentials from
REDDIT_CLIENT_ID / REDDIT_CLIENT_SECRET). Anonymous JSON, RSS and HTML Reddit surfaces are not
used; without credentials a Reddit post yields only the image URL the official API already gave
discovery, or nothing.
"""
from __future__ import annotations

import argparse
import csv
import html
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

from bs4 import BeautifulSoup

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
HARVESTER = REPO / "tools" / "dataset_harvester"
sys.path.insert(0, str(HARVESTER))
sys.path.insert(0, str(HERE))

import submariner_acquire as base  # noqa: E402
import dealer_media  # noqa: E402
import reddit_oauth  # noqa: E402
import source_diversity  # noqa: E402
from harvester.resolvers import ImageRef  # noqa: E402

ORIGINAL_RESOLVE = base.resolve_candidate
REDDIT_POST = re.compile(r"reddit\.com/(?:r/[^/]+/)?comments/([a-z0-9]+)", re.I)
REDDIT_IMAGE_HOSTS = {"i.redd.it", "preview.redd.it", "external-preview.redd.it"}


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
    post_id = reddit_post_id(source_url)
    if not (reddit_oauth.configured() and post_id):
        return []
    try:
        tok = reddit_oauth.token()
        if not tok:
            return []
        response = http.get(
            f"https://oauth.reddit.com/by_id/t3_{post_id}",
            headers={"Authorization": f"bearer {tok}", "User-Agent": reddit_oauth.UA},
            api=True,
        )
        payload = json.loads(response.text)
        post = payload["data"]["children"][0]["data"]
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
    if (row.get("image_album_url") or "").strip():
        return ORIGINAL_RESOLVE(row, http, work, max_images)

    source = (row.get("source_url") or "").strip()
    if source and "reddit.com/" in source.lower():
        oauth_refs = reddit_oauth_image_refs(source, http, max_images)
        direct = _image_url(row.get("direct_image_url") or "")
        direct_refs = [ImageRef(url=direct)] if direct else []
        refs = _merge_refs([oauth_refs, direct_refs], max_images)
        return refs

    verified = dealer_media.resolve_verified_dealer(row, http, max_images)
    if verified is not None:
        return verified

    return ORIGINAL_RESOLVE(row, http, work, max_images)


def _config_for_pool(pool) -> dict | None:
    """Infer the single model config represented by an acquisition pool."""
    models = set()
    pools = [Path(p) for p in (pool if isinstance(pool, (list, tuple)) else [pool])]
    for path in pools:
        if not path.exists():
            continue
        with path.open(newline="", encoding="utf-8") as fh:
            for row in csv.DictReader(fh):
                model = (row.get("model") or "").strip().upper()
                if model:
                    models.add(model)
    if len(models) != 1:
        return None
    path = REPO / "calibration" / "models" / f"{next(iter(models))}.json"
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def run(pool, out: Path, max_images: int = 12) -> dict:
    original = base.resolve_candidate
    base.resolve_candidate = resolve_candidate
    try:
        result = base.run(pool, out, max_images)
    finally:
        base.resolve_candidate = original

    config = _config_for_pool(pool)
    if config is not None:
        report = source_diversity.evaluate(config, out / "acquired_images.csv")
        source_diversity.save(report, out / "source_diversity.json")
        result["source_diversity"] = report
    return result


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--pool", type=Path, action="append", required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--max-images", type=int, default=12)
    a = ap.parse_args(argv)
    result = run(a.pool, a.out, a.max_images)
    print(json.dumps(result, indent=2, sort_keys=True))
    diversity = result.get("source_diversity") or {}
    if diversity.get("required") and not diversity.get("passed"):
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
