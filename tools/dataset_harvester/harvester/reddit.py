"""Optional Reddit access through the official, documented API only.

Enabled when REDDIT_CLIENT_ID and REDDIT_CLIENT_SECRET are set (a Reddit "script"/web app that
Reddit has approved for API access); app-only OAuth (client_credentials). Without them the Reddit
adapter reports itself disabled and Reddit posts found by other adapters are DEFERRED (kept in state
and processed when credentials appear); nothing is scraped from reddit.com HTML.

The listing parsing is a port of the app's RedditClient (alpha67): gallery order from
gallery_data, image URLs from media_metadata, direct i.redd.it/i.imgur.com links, Imgur albums and
links inside the post text, crossposts, and Reddit's preview copy for other image hosts.
"""
from __future__ import annotations

import json
import os
import re
import time

from .canonical import canonical_url
from .http import FetchError, Http

AUTH = "https://www.reddit.com/api/v1/access_token"
API = "https://oauth.reddit.com"
LINK = re.compile(r"https?://[^\s)\]\"'<>]+")
_IMGUR = re.compile(r"https?://(?:i\.|m\.|www\.)?imgur\.com/(a/|gallery/|t/[^/]+/)?([^/?#.\s]+)(\.[A-Za-z]+)?.*")
_DIRECT = re.compile(r"https?://[^?#]+\.(?:jpe?g|png|webp)(?:[?#].*)?", re.I)
MAX_IMAGES_PER_POST = 12


def credentials() -> tuple[str, str] | None:
    cid, secret = os.environ.get("REDDIT_CLIENT_ID", "").strip(), os.environ.get("REDDIT_CLIENT_SECRET", "").strip()
    return (cid, secret) if cid and secret else None


def clean(u: str) -> str:
    return u.replace("&amp;", "&")


def add_link(url: str, images: list, albums: list) -> None:
    m = _IMGUR.fullmatch(url)
    if m:
        kind, ident, ext = m.group(1), m.group(2), m.group(3)
        if kind:
            a = ident.rsplit("-", 1)[-1]
            if a not in albums:
                albums.append(a)
            return
        if ext and not re.fullmatch(r"(?i)\.(jpe?g|png|webp)", ext):
            return
        if len(ident) >= 5 and ident.isalnum():
            u = f"https://i.imgur.com/{ident}{ext or '.jpg'}"
            if u not in images:
                images.append(u)
        return
    if _DIRECT.fullmatch(url) and url not in images:
        images.append(url)


def _collect(d: dict, images: list, albums: list) -> None:
    if d.get("is_gallery"):
        meta = d.get("media_metadata") or {}
        for it in (d.get("gallery_data") or {}).get("items") or []:
            m = meta.get(it.get("media_id") or "") or {}
            if m.get("status") == "valid" and m.get("e") == "Image":
                u = (m.get("s") or {}).get("u")
                if u and clean(u) not in images:
                    images.append(clean(u))
    url = d.get("url_overridden_by_dest") or d.get("url")
    if url:
        add_link(clean(url), images, albums)
    for m in LINK.finditer(d.get("selftext") or ""):
        add_link(clean(m.group()), images, albums)
    if not images and not albums and d.get("post_hint") == "image":
        try:
            u = d["preview"]["images"][0]["source"]["url"]
            images.append(clean(u))
        except (KeyError, IndexError, TypeError):
            pass


def parse_post(d: dict) -> dict:
    """{id, title, permalink, subreddit, images, imgur_albums, created_utc}."""
    images, albums = [], []
    _collect(d, images, albums)
    if not images and not albums:
        for x in d.get("crosspost_parent_list") or []:
            _collect(x, images, albums)
    perm = d.get("permalink") or ""
    return {
        "id": d.get("id") or "", "title": d.get("title") or "", "subreddit": d.get("subreddit") or "",
        "permalink": canonical_url("https://www.reddit.com" + perm) if perm else "",
        "images": images[:MAX_IMAGES_PER_POST], "imgur_albums": albums, "created_utc": d.get("created_utc") or 0,
        "author": d.get("author") or "",
    }


def parse_listing(obj: dict) -> tuple[list[dict], str | None]:
    data = (obj or {}).get("data") or {}
    posts = [parse_post(c.get("data") or {}) for c in data.get("children") or [] if c.get("kind") == "t3"]
    return posts, data.get("after")


class RedditApi:
    def __init__(self, http: Http, creds: tuple[str, str]):
        self.http, self.creds = http, creds
        self.token, self.until = "", 0.0

    def _auth(self) -> None:
        if self.token and time.time() < self.until:
            return
        r = self.http.post(AUTH, data={"grant_type": "client_credentials"}, auth=self.creds)
        j = json.loads(r.content)
        if "access_token" not in j:
            raise FetchError("reddit_auth_failed", str(j.get("error", "")), retryable=False)
        self.token = j["access_token"]
        self.until = time.time() + float(j.get("expires_in", 3600)) - 60

    def _get(self, path: str) -> dict:
        self._auth()
        r = self.http.get(API + path, headers={"Authorization": f"bearer {self.token}"}, api=True, max_bytes=8_000_000)
        return json.loads(r.content)

    def search(self, subreddit: str, query: str, limit: int = 50, after: str | None = None) -> tuple[list[dict], str | None]:
        from urllib.parse import quote
        path = f"/r/{quote(subreddit)}/search?q={quote(query)}&restrict_sr=1&sort=new&limit={limit}&raw_json=1&type=link"
        if after:
            path += f"&after={quote(after)}"
        return parse_listing(self._get(path))

    def post(self, post_id: str) -> dict | None:
        posts, _ = parse_listing(self._get(f"/by_id/t3_{post_id}?raw_json=1"))
        return posts[0] if posts else None
