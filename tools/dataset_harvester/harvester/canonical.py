"""Canonical source URLs, so one post/album/listing is recognised however it was linked."""
from __future__ import annotations

import re
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

TRACKING_PARAMS = {
    "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_name",
    "share_id", "context", "ref", "ref_source", "ref_campaign", "fbclid", "gclid", "igshid",
    "rdt", "si", "s", "_branch_match_id", "_branch_referrer",
}
# Query parameters that select the image itself on some CDNs and must be kept.
KEEP_ON_IMAGE_HOSTS = {"width", "height", "format", "auto", "fit", "crop", "url", "w", "h", "q", "quality"}

_REDDIT_POST = re.compile(r"^/(?:r/([^/]+)/)?comments/([a-z0-9]+)(?:/|$)", re.I)
_REDDIT_GALLERY = re.compile(r"^/gallery/([a-z0-9]+)", re.I)
_IMGUR = re.compile(r"^/(?:(a|gallery|t/[^/]+)/)?([^/?#.]+)(\.[a-z0-9]+)?$", re.I)


def canonical_url(url: str) -> str:
    """Lower-case scheme/host, https, no fragment, no tracking parameters, known hosts normalised.

    Reddit posts become https://www.reddit.com/r/<sub>/comments/<id>/ (title slug dropped);
    Imgur albums https://imgur.com/a/<id>; single Imgur images https://i.imgur.com/<id>.<ext>.
    """
    url = (url or "").strip()
    if not url:
        return ""
    if url.startswith("//"):
        url = "https:" + url
    if not re.match(r"^[a-z][a-z0-9+.-]*://", url, re.I):
        url = "https://" + url
    parts = urlsplit(url)
    scheme = "https" if parts.scheme.lower() in ("http", "https") else parts.scheme.lower()
    host = (parts.hostname or "").lower()
    if host.startswith("www.") and not host.startswith("www.reddit."):
        host = host[4:]
    path = re.sub(r"/{2,}", "/", parts.path or "/")

    if host in ("reddit.com", "old.reddit.com", "new.reddit.com", "np.reddit.com", "m.reddit.com", "www.reddit.com", "sh.reddit.com"):
        m = _REDDIT_POST.match(path)
        if m:
            sub = m.group(1)
            pid = m.group(2).lower()
            return f"https://www.reddit.com/r/{sub}/comments/{pid}/" if sub else f"https://www.reddit.com/comments/{pid}/"
        g = _REDDIT_GALLERY.match(path)
        if g:
            return f"https://www.reddit.com/comments/{g.group(1).lower()}/"
        host = "www.reddit.com"
    if host == "redd.it":
        pid = path.strip("/").split("/")[0].lower()
        if pid:
            return f"https://www.reddit.com/comments/{pid}/"

    if host in ("imgur.com", "m.imgur.com", "i.imgur.com"):
        m = _IMGUR.match(path)
        if m:
            kind, ident, ext = m.group(1), m.group(2), m.group(3)
            if kind:
                # Newer album links end in "-<id>" after a title slug.
                ident = ident.rsplit("-", 1)[-1]
                return f"https://imgur.com/a/{ident}"
            if ext and ext.lower() in (".gifv", ".mp4", ".webm"):
                return f"https://i.imgur.com/{ident}{ext.lower()}"
            return f"https://i.imgur.com/{ident}{(ext or '.jpg').lower()}"

    query = [(k, v) for k, v in parse_qsl(parts.query, keep_blank_values=False)
             if k.lower() in KEEP_ON_IMAGE_HOSTS
             or (k.lower() not in TRACKING_PARAMS and not k.lower().startswith("utm_"))]
    query.sort()
    if path != "/" and path.endswith("/") and not host.endswith("reddit.com"):
        path = path.rstrip("/")
    netloc = host + (f":{parts.port}" if parts.port and parts.port not in (80, 443) else "")
    return urlunsplit((scheme, netloc, path, urlencode(query), ""))


def host_of(url: str) -> str:
    h = (urlsplit(url).hostname or "").lower()
    return h[4:] if h.startswith("www.") else h


def reddit_post_id(url: str) -> str | None:
    c = canonical_url(url)
    m = re.search(r"reddit\.com/(?:r/[^/]+/)?comments/([a-z0-9]+)/", c)
    return m.group(1) if m else None


def reddit_subreddit(url: str) -> str | None:
    m = re.search(r"reddit\.com/r/([^/]+)/", canonical_url(url))
    return m.group(1) if m else None


def imgur_album_id(url: str) -> str | None:
    m = re.match(r"https://imgur\.com/a/([A-Za-z0-9]+)$", canonical_url(url))
    return m.group(1) if m else None


def is_direct_image(url: str) -> bool:
    c = canonical_url(url)
    path = urlsplit(c).path.lower()
    if re.search(r"\.(jpe?g|png|webp)$", path):
        return True
    host = host_of(c)
    return host in ("i.redd.it", "preview.redd.it", "i.imgur.com") and not path.endswith((".gifv", ".mp4", ".gif"))
