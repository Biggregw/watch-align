#!/usr/bin/env python3
"""Fetch a first-party Rolex catalogue image as a Phase A frontal-measurement candidate.

Reuses the exact fetch mechanism already proven in CI by
android/fetch_genuine_fixtures.py (same URL, same headers) -- not the Stage 3/
projective measurement code, just the image-acquisition plumbing. This project's
established convention is to never commit third-party source-photo pixels to git;
this script writes the fetched image to a local, gitignored-by-convention output
directory so it can be inspected and measured, then delivered as a CI artifact
rather than committed.
"""
from __future__ import annotations

import sys
import urllib.request
from pathlib import Path

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140 Safari/537.36"

# Same first-party media.rolex.com catalogue URL android/fetch_genuine_fixtures.py
# uses for exact-model Android acceptance-test fixtures.
IMAGE_URL = ("https://media.rolex.com/image/upload/q_auto/f_jpg/t_v7-cover-majesty-landscape/"
             "c_limit,w_1920/v1/a677b2c664f6/catalogue/2026/upright-c/m126710blnr-0002")
REFERENCE = "126710BLNR"


def valid_image(data: bytes) -> bool:
    if len(data) < 50_000:
        return False
    return data.startswith(b"\xff\xd8\xff") or data.startswith(b"\x89PNG\r\n\x1a\n")


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "image/avif,image/webp,image/apng,image/jpeg,image/*,*/*;q=0.8",
        "Accept-Language": "en-GB,en;q=0.9",
        "Referer": "https://www.rolex.com/",
        "Cache-Control": "no-cache",
    })
    with urllib.request.urlopen(req, timeout=35) as r:
        data = r.read()
    if not valid_image(data):
        raise RuntimeError(f"not a usable image ({len(data)} bytes)")
    return data


def main() -> int:
    out_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("phase_a_candidates")
    out_dir.mkdir(parents=True, exist_ok=True)
    data = fetch(IMAGE_URL)
    dest = out_dir / f"official-{REFERENCE}-catalogue-0002.jpg"
    dest.write_bytes(data)
    (out_dir / f"official-{REFERENCE}-catalogue-0002.source.txt").write_text(
        f"url\t{IMAGE_URL}\n"
        f"reference\t{REFERENCE}\n"
        f"provenance\tfirst-party Rolex media.rolex.com catalogue image, same URL used by "
        f"android/fetch_genuine_fixtures.py for exact-model Android acceptance-test fixtures\n",
        encoding="utf-8",
    )
    print(f"wrote {dest} ({len(data)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
