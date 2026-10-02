"""Narrow, source-aware image extraction for verified genuine 124060 dealers.

The generic Submariner resolver intentionally requires a dealer listing id to appear in each
image path. That is a useful contamination guard, but a few verified dealers use gallery URLs
whose outer path does not contain the SKU. This module handles only those known layouts and
returns None for every other dealer so the existing strict resolver remains authoritative.
"""
from __future__ import annotations

import html
import re
from urllib.parse import parse_qs, unquote, urljoin, urlparse

from bs4 import BeautifulSoup

from harvester.resolvers import ImageRef, page_images

IMAGE_PATH = re.compile(r"\.(?:jpe?g|png|webp)(?:$|\?)", re.I)
WOOCOMMERCE_HOSTS = {"davidsw.com", "www.davidsw.com", "nashvillewatch.com", "www.nashvillewatch.com"}
EWC_HOSTS = {"europeanwatch.com", "www.europeanwatch.com"}


def _image_url(raw: str, base: str) -> str:
    value = html.unescape((raw or "").strip()).replace("\\/", "/")
    if not value:
        return ""
    url = urljoin(base, value)
    p = urlparse(url)
    if p.scheme not in ("http", "https"):
        return ""
    return url if IMAGE_PATH.search(p.path) else ""


def _dedupe(urls: list[str], max_images: int) -> list[ImageRef]:
    seen, out = set(), []
    for url in urls:
        key = url.split("#", 1)[0]
        if not key or key in seen:
            continue
        seen.add(key)
        out.append(ImageRef(url=key))
        if len(out) >= max_images:
            break
    return out


def unwrap_europeanwatch(url: str) -> str:
    """Return the real gallery image behind a Next.js /_next/image wrapper."""
    u = html.unescape((url or "").strip()).replace("\\/", "/")
    p = urlparse(u)
    if p.path.rstrip("/") != "/_next/image":
        return u
    target = (parse_qs(p.query).get("url") or [""])[0]
    return unquote(target) if target else ""


def europeanwatch_refs(row: dict, http, max_images: int) -> list[ImageRef]:
    source = (row.get("source_url") or "").strip()
    listing = (row.get("listing_id") or "").strip()
    candidates = page_images(http, source, max_images * 4)
    urls: list[str] = []
    for ref in candidates:
        u = unwrap_europeanwatch(ref.url or "")
        if not u:
            continue
        p = urlparse(u)
        # The stock/listing id must occur in the real CDN image path, not merely in page text.
        if listing and listing not in p.path:
            continue
        if IMAGE_PATH.search(p.path):
            urls.append(u)
    return _dedupe(urls, max_images)


def woocommerce_gallery_refs(row: dict, http, max_images: int) -> list[ImageRef]:
    """Extract only the product gallery, excluding related watches and site chrome."""
    source = (row.get("source_url") or "").strip()
    r = http.get(source)
    soup = BeautifulSoup(r.text, "html.parser")
    roots = soup.select(".woocommerce-product-gallery")
    if not roots:
        return []
    urls: list[str] = []
    for root in roots:
        # Prefer links and full-size attributes before thumbnails.
        for tag in root.find_all("a", href=True):
            u = _image_url(tag.get("href") or "", r.url)
            if u:
                urls.append(u)
        for tag in root.find_all(["img", "source"]):
            for attr in ("data-large_image", "data-zoom-image", "data-original", "data-src", "src"):
                u = _image_url(tag.get(attr) or "", r.url)
                if u:
                    urls.append(u)
            for attr in ("data-srcset", "srcset"):
                value = tag.get(attr) or ""
                # Largest candidates normally appear last; walk backwards.
                for part in reversed([x.strip().split()[0] for x in value.split(",") if x.strip()]):
                    u = _image_url(part, r.url)
                    if u:
                        urls.append(u)
    return _dedupe(urls, max_images)


def resolve_verified_dealer(row: dict, http, max_images: int) -> list[ImageRef] | None:
    """Resolve known dealer layouts, or None when the existing generic resolver should be used."""
    source = (row.get("source_url") or "").strip()
    if not source:
        return None
    host = urlparse(source).netloc.lower()
    if host in EWC_HOSTS:
        return europeanwatch_refs(row, http, max_images)
    if host in WOOCOMMERCE_HOSTS:
        return woocommerce_gallery_refs(row, http, max_images)
    return None
