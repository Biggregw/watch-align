"""Official Reddit API access for the watch-family calibrator.

This is the only way the calibrator talks to Reddit. It uses Reddit's documented app-only OAuth
flow (``client_credentials``) against ``oauth.reddit.com`` with credentials taken from the
REDDIT_CLIENT_ID / REDDIT_CLIENT_SECRET environment variables (GitHub secrets in CI).

Deliberately absent: anonymous ``www.reddit.com/*.json`` endpoints, public RSS/Atom feeds, HTML
scraping and browser-like User-Agent strings. Without credentials every function returns nothing,
so Reddit evidence fails closed instead of falling back to unofficial surfaces.
"""
from __future__ import annotations

import base64
import os
import re

import requests

UA = "WatchAlignResearch/1.4 by Biggregw"
TIMEOUT = 20
IMGUR_ALBUM = re.compile(r"https?://(?:www\.)?imgur\.com/a/[A-Za-z0-9_-]+", re.I)
_TOKEN: str | None = None


def credentials() -> tuple[str, str]:
    return (
        (os.environ.get("REDDIT_CLIENT_ID") or "").strip(),
        (os.environ.get("REDDIT_CLIENT_SECRET") or "").strip(),
    )


def configured() -> bool:
    cid, secret = credentials()
    return bool(cid and secret)


def token(session=None) -> str:
    """Return a cached app-only bearer token, or '' when not configured / refused."""
    global _TOKEN
    session = session or requests
    if _TOKEN:
        return _TOKEN
    cid, secret = credentials()
    if not (cid and secret):
        return ""
    try:
        basic = base64.b64encode(f"{cid}:{secret}".encode()).decode("ascii")
        r = session.post(
            "https://www.reddit.com/api/v1/access_token",
            data={"grant_type": "client_credentials"},
            headers={"User-Agent": UA, "Authorization": f"Basic {basic}"},
            timeout=TIMEOUT,
        )
        r.raise_for_status()
        _TOKEN = str(r.json().get("access_token") or "")
    except Exception:
        _TOKEN = ""
    return _TOKEN or ""


def reset() -> None:
    global _TOKEN
    _TOKEN = None


def search(subreddit: str, query: str, limit: int = 100, session=None) -> list[dict]:
    """Search one subreddit through the official API; returns post ``data`` dicts."""
    session = session or requests
    tok = token(session)
    if not tok:
        return []
    try:
        r = session.get(
            f"https://oauth.reddit.com/r/{subreddit}/search",
            params={
                "q": query, "restrict_sr": "1", "sort": "new", "t": "all",
                "limit": min(max(int(limit), 1), 100), "raw_json": "1",
            },
            headers={"Authorization": f"bearer {tok}", "User-Agent": UA},
            timeout=TIMEOUT,
        )
        r.raise_for_status()
        return [c.get("data") or {} for c in (r.json().get("data") or {}).get("children") or []]
    except Exception:
        return []


def post_text(post: dict) -> str:
    return " ".join(str(post.get(k) or "") for k in ("title", "selftext", "url_overridden_by_dest", "url"))


def post_url(post: dict) -> str:
    link = str(post.get("permalink") or "")
    return f"https://www.reddit.com{link}" if link.startswith("/") else ""


def imgur_album(text: str) -> str:
    m = IMGUR_ALBUM.search(text or "")
    return m.group(0) if m else ""
