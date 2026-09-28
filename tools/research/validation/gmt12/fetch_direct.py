#!/usr/bin/env python3
"""Direct-URL fetcher for GMT12 genuine-reference source manifests.

Unlike phase_b_fetch_genuine.py (which scrapes a listing page to *find*
candidate image URLs), this expects the manifest to already carry the final
CDN image URL in `image_url` with `fetch_mode=direct`, and just downloads it.
Acquisition only -- no measurement here (see measure_manifest.py).
"""
from __future__ import annotations

import csv
import sys
from io import BytesIO
from pathlib import Path

import requests
from PIL import Image, ImageOps

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140 Safari/537.36 WatchAlignResearch/1.0"
HEADERS = {"User-Agent": UA, "Accept-Language": "en-GB,en;q=0.9"}
MIN_BYTES = 15_000
MIN_DIM = 400


def download(url: str, dest: Path) -> tuple[bool, str]:
    try:
        r = requests.get(url, headers=HEADERS, timeout=30, allow_redirects=True)
        r.raise_for_status()
    except Exception as exc:
        return False, f"fetch_failed:{type(exc).__name__}:{exc}"
    data = r.content
    if len(data) < MIN_BYTES:
        return False, f"too_small:{len(data)}bytes"
    try:
        with Image.open(BytesIO(data)) as im0:
            im = ImageOps.exif_transpose(im0).convert("RGB")
    except Exception as exc:
        return False, f"decode_failed:{type(exc).__name__}:{exc}"
    if min(im.size) < MIN_DIM:
        return False, f"too_small_dim:{im.size}"
    dest.parent.mkdir(parents=True, exist_ok=True)
    im.save(dest, "JPEG", quality=95)
    return True, f"ok:{im.size[0]}x{im.size[1]}"


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: fetch_direct.py <manifest.csv> <out_dir>", file=sys.stderr)
        return 2
    manifest_path = Path(sys.argv[1])
    out_dir = Path(sys.argv[2])
    with manifest_path.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    ok = 0
    for row in rows:
        if row.get("fetch_mode") != "direct":
            print(f"skip {row['source_id']}: fetch_mode={row.get('fetch_mode')!r} not 'direct'")
            continue
        dest = out_dir / f"{row['source_id']}.jpg"
        success, detail = download(row["image_url"], dest)
        print(f"{row['source_id']}: {detail}")
        if success:
            ok += 1
    print(f"\n{ok}/{len(rows)} sources downloaded")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
