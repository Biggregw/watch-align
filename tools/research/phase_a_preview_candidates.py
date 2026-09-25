#!/usr/bin/env python3
"""Print small base64 JPEG previews of one representative image per genuine
candidate watch, straight to stdout (CI job log), so a reviewer without direct
network access to Reddit/Imgur/Rolex can still visually judge frontality and
landmark visibility before Phase A implementation choices are made.

This is a one-off visual-inspection aid, not part of the measurement harness.
Nothing here is committed to the repository; the images themselves stay
gitignored exactly as datasets/126710BLNR/.gitignore already requires.
"""
from __future__ import annotations

import base64
import csv
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "datasets" / "126710BLNR"
RESOLVED = ROOT / "resolved_images.csv"
MAX_DIM = 460


def main() -> int:
    if not RESOLVED.exists():
        print(f"missing {RESOLVED}; run fetch_images.py first")
        return 1
    with RESOLVED.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    seen_watches = set()
    for r in rows:
        wid = r["physical_watch_id"]
        if wid in seen_watches:
            continue
        seen_watches.add(wid)
        path = ROOT / r["local_path"]
        if not path.is_file():
            print(f"### MISSING {wid} ({r['source_id']}): {path} not found")
            continue
        with Image.open(path) as im:
            im = im.convert("RGB")
            im.thumbnail((MAX_DIM, MAX_DIM), Image.Resampling.LANCZOS)
            from io import BytesIO
            buf = BytesIO()
            im.save(buf, "JPEG", quality=72)
            data = buf.getvalue()
        b64 = base64.b64encode(data).decode("ascii")
        print(f"### BEGIN {wid} source={r['source_id']} provenance={r['provenance']} "
              f"bracelet={r['bracelet']} orig_wh={r['width']}x{r['height']} "
              f"thumb_bytes={len(data)}")
        # Wrap for log readability; reassemble by concatenating BASE64 lines
        # between BEGIN/END for this watch id.
        for i in range(0, len(b64), 200):
            print(b64[i:i + 200])
        print(f"### END {wid}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
