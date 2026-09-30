"""NORMALISE SOURCE: turn one source (post, album, listing, local group) into its still images.

Returns ImageRef items: either a URL still to download or a file already on disk. A resolver that
needs something unavailable raises Deferred (the source waits in state), and one that fails raises
FetchError with a reason code (recorded, retried per the retry policy)."""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

from .canonical import canonical_url, imgur_album_id, is_direct_image, reddit_post_id
from .config import LIMITS, REPO_ROOT
from .http import FetchError, Http
from . import reddit as rd


class Deferred(Exception):
    def __init__(self, reason: str, provider: str):
        super().__init__(reason)
        self.reason, self.provider = reason, provider


@dataclass
class ImageRef:
    url: str = ""
    path: str = ""          # local file (absolute or repo-relative)


def _abs(p: str) -> Path:
    q = Path(p)
    return q if q.is_absolute() else REPO_ROOT / q


def imgur_album(http: Http, album_id: str, work: Path, max_images: int) -> list[ImageRef]:
    """Imgur album images: the documented Imgur API when IMGUR_CLIENT_ID is set, otherwise
    gallery-dl (the tool the 126710BLNR corpus fetcher already uses), otherwise deferred."""
    cid = os.environ.get("IMGUR_CLIENT_ID", "").strip()
    if cid:
        r = http.get(f"https://api.imgur.com/3/album/{album_id}/images", headers={"Authorization": f"Client-ID {cid}"}, api=True)
        return [ImageRef(url=x["link"]) for x in parse_imgur_album(json.loads(r.content))[:max_images]]
    exe = shutil.which("gallery-dl")
    if not exe:
        raise Deferred("needs_imgur_client_id_or_gallery_dl", "imgur_album")
    out = work / f"imgur_{album_id}"
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True, exist_ok=True)
    try:
        res = subprocess.run([exe, "--no-mtime", "--range", f"1-{max_images}", "-D", str(out), f"https://imgur.com/a/{album_id}"],
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=300)
    except subprocess.TimeoutExpired:
        raise FetchError("album_timeout", album_id)
    files = sorted(p for p in out.rglob("*") if p.is_file() and p.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp"))
    if res.returncode != 0 and not files:
        low = res.stdout.lower()
        reason = "gone" if ("404" in low or "not found" in low) else "blocked_http_403" if "403" in low else "album_fetch_failed"
        raise FetchError(reason, res.stdout.strip().splitlines()[-1][:200] if res.stdout.strip() else "", retryable=reason == "album_fetch_failed")
    return [ImageRef(path=str(p)) for p in files]


def parse_imgur_album(obj: dict) -> list[dict]:
    out = []
    for x in (obj or {}).get("data") or []:
        t = x.get("type") or ""
        if x.get("link") and (not t or t.startswith("image/")) and t != "image/gif" and not x.get("animated"):
            out.append({"link": x["link"], "width": x.get("width"), "height": x.get("height")})
    return out


def page_images(http: Http, url: str, max_images: int) -> list[ImageRef]:
    """Image URLs from an HTML listing page (dealer/auction/CPO pages), with the phase B fetcher's
    parsing rules (og:image, <img> srcset, JSON-LD). Fetched with the harvester's honest client and
    robots.txt; a refusal is recorded, not worked around."""
    sys.path.insert(0, str(REPO_ROOT / "tools" / "research"))
    try:
        import phase_b_fetch_genuine as pb  # reused parsing helpers
    finally:
        sys.path.pop(0)
    from bs4 import BeautifulSoup
    import re

    r = http.get(url)
    if "html" not in r.content_type:
        raise FetchError("not_html", r.content_type, retryable=False)
    base, soup, out, seen = r.url, BeautifulSoup(r.text, "html.parser"), [], set()
    for meta in soup.find_all("meta"):
        key = (meta.get("property") or meta.get("name") or "").lower()
        if key in {"og:image", "og:image:url", "twitter:image", "twitter:image:src"}:
            pb.add_url(out, seen, base, meta.get("content"))
    for tag in soup.find_all("img"):
        for attr in ("src", "data-src", "data-original", "data-lazy-src", "data-zoom-image", "data-image"):
            pb.add_url(out, seen, base, tag.get(attr))
        for attr in ("srcset", "data-srcset"):
            val = tag.get(attr)
            if val:
                for p in reversed([p.strip().split()[0] for p in val.split(",") if p.strip()]):
                    pb.add_url(out, seen, base, p)
    for script in soup.find_all("script", attrs={"type": "application/ld+json"}):
        try:
            pb.walk_json_images(json.loads(script.string or script.get_text() or "null"), out, seen, base)
        except (ValueError, TypeError):
            pass
    out = [u for u in out if re.search(r"\.(jpe?g|png|webp)(?:$|\?)", u, re.I) or "image" in u.lower()]
    return [ImageRef(url=u) for u in out[:max_images * 2]]


def resolve(rec, http: Http, work: Path, reddit_api: "rd.RedditApi | None", max_images: int = LIMITS.max_images_per_source) -> list[ImageRef]:
    """Images for one source record (state.SourceRecord)."""
    refs: list[ImageRef] = []
    # 1. Images already on disk (repository corpus, phone uploads): never downloaded again.
    for p in rec.local_paths:
        if _abs(p).exists():
            refs.append(ImageRef(path=str(_abs(p))))
    if refs:
        return refs   # curated local copies are all kept
    # 2. Image URLs known from discovery (direct manifests, Reddit API listings, accepted lists).
    refs = [ImageRef(url=u) for u in rec.image_urls]
    albums = list(rec.meta.get("imgur_albums") or [])
    album_url = rec.meta.get("album_url") or ""
    if album_url and imgur_album_id(album_url):
        albums.append(imgur_album_id(album_url))
    url = rec.url
    if not refs and not albums:
        if url and is_direct_image(url):
            refs = [ImageRef(url=canonical_url(url))]
        elif url and imgur_album_id(url):
            albums.append(imgur_album_id(url))
        elif url and reddit_post_id(url):
            if reddit_api is None:
                raise Deferred("needs_reddit_api", "reddit")
            post = reddit_api.post(reddit_post_id(url))
            if not post:
                raise FetchError("gone", "reddit post not found", retryable=False)
            rec.title = rec.title or post["title"]
            rec.meta["subreddit"] = post["subreddit"]
            refs = [ImageRef(url=u) for u in post["images"]]
            albums += post["imgur_albums"]
        elif url:
            refs = page_images(http, url, max_images)
    for a in albums:
        if len(refs) >= max_images:
            break
        refs += imgur_album(http, a, work, max_images - len(refs))
    return refs[:max_images]
