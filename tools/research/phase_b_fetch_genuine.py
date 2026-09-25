#!/usr/bin/env python3
"""Phase B genuine control-set fetcher.

Downloads candidate dial photographs for every source in
datasets/gmt_phase_b_genuine/manifest.csv. This is acquisition only -- no
pose/suitability judgement and no measurement happen here. Suitability is
decided by human visual review of the printed previews (see
phase_b_preview_candidates.py and docs/research/gmt-phase-b-image-selection.md),
per docs/architecture/QC_PRINCIPLES.md's second principle: image suitability
is an input contract, not something this script (or the frozen Phase A
detector) should try to solve.

The HTML/image scraping helpers below (extract_image_urls, download_image,
image_fingerprint) are generic acquisition utilities, adapted from
tools/research/build_gmt_genuine_baseline.py on
research/proportional-geometry-knowledge-base. Nothing from that branch's
Stage 3 pose/projective measurement code is used here or anywhere in Phase B.

Downloaded images are never committed (see datasets/gmt_phase_b_genuine/.gitignore).
"""
from __future__ import annotations

import csv
import hashlib
import html as htmlmod
import json
import re
import sys
from io import BytesIO
from pathlib import Path
from urllib.parse import urljoin, urlparse

import numpy as np
import requests
from bs4 import BeautifulSoup
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
DATASET_DIR = ROOT / "datasets" / "gmt_phase_b_genuine"
MANIFEST = DATASET_DIR / "manifest.csv"
IMAGES_DIR = DATASET_DIR / "images"
STATUS_CSV = DATASET_DIR / "candidate_status.csv"

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140 Safari/537.36 WatchAlignResearch/1.0"
HEADERS = {"User-Agent": UA, "Accept-Language": "en-GB,en;q=0.9"}
MAX_CANDIDATE_URLS = 40
MIN_BYTES = 25_000
MAX_BYTES = 18_000_000
MIN_DIM = 500
MIN_AREA = 500_000
THUMBNAIL_MAX = 1800


def read_manifest() -> list[dict]:
    with MANIFEST.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def add_url(out: list[str], seen: set[str], base: str, value):
    if not value or not isinstance(value, str):
        return
    value = htmlmod.unescape(value.strip())
    if value.startswith("//"):
        value = "https:" + value
    value = urljoin(base, value)
    if not value.startswith(("http://", "https://")):
        return
    low = value.lower()
    if any(x in low for x in ("logo", "icon", "sprite", "avatar", "payment", "flag", "trustpilot", "placeholder")):
        return
    if value not in seen:
        seen.add(value)
        out.append(value)


def walk_json_images(obj, out: list[str], seen: set[str], base: str):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k.lower() in {"image", "images", "contenturl", "thumbnailurl", "url"}:
                if isinstance(v, str):
                    add_url(out, seen, base, v)
                elif isinstance(v, list):
                    for item in v:
                        if isinstance(item, str):
                            add_url(out, seen, base, item)
                        else:
                            walk_json_images(item, out, seen, base)
                else:
                    walk_json_images(v, out, seen, base)
            else:
                walk_json_images(v, out, seen, base)
    elif isinstance(obj, list):
        for item in obj:
            walk_json_images(item, out, seen, base)


def extract_image_urls(page_url: str) -> tuple[list[str], str]:
    try:
        r = requests.get(page_url, headers=HEADERS, timeout=35, allow_redirects=True)
        status = f"http_{r.status_code}"
        r.raise_for_status()
    except Exception as exc:
        return [], f"page_fetch_failed:{type(exc).__name__}:{exc}"
    base = r.url
    soup = BeautifulSoup(r.text, "html.parser")
    out: list[str] = []
    seen: set[str] = set()

    for meta in soup.find_all("meta"):
        key = (meta.get("property") or meta.get("name") or "").lower()
        if key in {"og:image", "og:image:url", "twitter:image", "twitter:image:src"}:
            add_url(out, seen, base, meta.get("content"))

    for tag in soup.find_all("img"):
        for attr in ("src", "data-src", "data-original", "data-lazy-src", "data-zoom-image", "data-image"):
            add_url(out, seen, base, tag.get(attr))
        for attr in ("srcset", "data-srcset"):
            val = tag.get(attr)
            if val:
                parts = [p.strip().split()[0] for p in val.split(",") if p.strip()]
                for p in reversed(parts):
                    add_url(out, seen, base, p)

    for script in soup.find_all("script", attrs={"type": "application/ld+json"}):
        try:
            obj = json.loads(script.string or script.get_text() or "null")
            walk_json_images(obj, out, seen, base)
        except Exception:
            pass

    rx = re.compile(r'https?:\\?/\\?/[^"\\\'<> ]+?\.(?:jpe?g|png|webp)(?:\?[^"\\\'<> ]*)?', re.I)
    for m in rx.findall(r.text):
        add_url(out, seen, base, m.replace("\\/", "/"))

    def score(u: str):
        low = u.lower()
        s = 0
        if any(x in low for x in ("126710", "gmt", "rolex", "watch", "product", "zoom", "large", "original")):
            s += 5
        if any(x in low for x in ("thumb", "thumbnail", "small", "100x", "150x")):
            s -= 4
        if urlparse(u).netloc == urlparse(base).netloc:
            s += 1
        return s

    out.sort(key=score, reverse=True)
    return out[:MAX_CANDIDATE_URLS], status


def download_image(url: str):
    try:
        r = requests.get(url, headers=HEADERS, timeout=30, allow_redirects=True, stream=True)
        r.raise_for_status()
        ctype = (r.headers.get("content-type") or "").lower()
        data = r.content
        if len(data) < MIN_BYTES or len(data) > MAX_BYTES:
            return None, None
        if "image" not in ctype and not re.search(r"\.(jpe?g|png|webp)(?:$|\?)", url, re.I):
            return None, None
        with Image.open(BytesIO(data)) as im0:
            im = ImageOps.exif_transpose(im0).convert("RGB")
        if min(im.size) < MIN_DIM or im.width * im.height < MIN_AREA:
            return None, None
        native_w, native_h = im.width, im.height
        im.thumbnail((THUMBNAIL_MAX, THUMBNAIL_MAX), Image.Resampling.LANCZOS)
        return im, (native_w, native_h)
    except Exception:
        return None, None


def image_fingerprint(im: Image.Image) -> str:
    small = np.array(im.convert("L").resize((32, 32), Image.Resampling.LANCZOS))
    return hashlib.sha256(small.tobytes()).hexdigest()


def main() -> int:
    only_source = sys.argv[1] if len(sys.argv) > 1 else None
    sources = read_manifest()
    if only_source:
        sources = [s for s in sources if s["source_id"] == only_source]
        if not sources:
            print(f"no manifest row for source_id={only_source!r}")
            return 1

    IMAGES_DIR.mkdir(parents=True, exist_ok=True)
    status_rows = []
    global_fp: set[str] = set()

    for si, src in enumerate(sources, 1):
        source_id = src["source_id"]
        out_dir = IMAGES_DIR / source_id
        out_dir.mkdir(parents=True, exist_ok=True)
        urls, page_status = extract_image_urls(src["source_url"])
        max_images = int(src.get("max_images") or 6)
        downloaded = 0
        errors = []
        for url in urls:
            if downloaded >= max_images:
                break
            im, native = download_image(url)
            if im is None:
                continue
            fp = image_fingerprint(im)
            if fp in global_fp:
                continue
            global_fp.add(fp)
            local_path = out_dir / f"image_{downloaded:02d}.jpg"
            im.save(local_path, "JPEG", quality=92)
            status_rows.append({
                "source_id": source_id,
                "provenance_class": src["provenance_class"],
                "physical_watch_id": src["physical_watch_id"],
                "model": src["model"],
                "source_url": src["source_url"],
                "image_index": downloaded,
                "image_url": url,
                "local_path": str(local_path.relative_to(ROOT)),
                "native_width": native[0] if native else None,
                "native_height": native[1] if native else None,
                "page_status": page_status,
            })
            downloaded += 1
        print(f"[{si}/{len(sources)}] {source_id}: candidates={len(urls)} downloaded={downloaded} page_status={page_status}")
        if downloaded == 0:
            status_rows.append({
                "source_id": source_id,
                "provenance_class": src["provenance_class"],
                "physical_watch_id": src["physical_watch_id"],
                "model": src["model"],
                "source_url": src["source_url"],
                "image_index": None,
                "image_url": None,
                "local_path": None,
                "native_width": None,
                "native_height": None,
                "page_status": page_status,
            })

    keys = ["source_id", "provenance_class", "physical_watch_id", "model", "source_url",
            "image_index", "image_url", "local_path", "native_width", "native_height", "page_status"]
    with STATUS_CSV.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        w.writerows(status_rows)

    n_sources = len(sources)
    n_with_images = len({r["source_id"] for r in status_rows if r["local_path"]})
    print(f"\n{n_with_images}/{n_sources} sources yielded at least one downloaded candidate image")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
