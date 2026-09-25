#!/usr/bin/env python3
"""Print acceptance overlays (docs/research/gmt-phase-b-results/overlays/*.png)
as small base64 JPEGs to stdout for visual QA via CI job logs. Overlays are
never committed -- see docs/research/gmt-phase-b-results/.gitignore -- and the
full-resolution PNGs are only available as the CI artifact upload.

Usage: python3 phase_b_print_overlays.py [label_prefix]
  With no argument, prints every overlay at a small size for a first-pass
  sweep. With a label prefix (e.g. a source_id), prints only matching
  overlays at full resolution for a closer look.
"""
from __future__ import annotations

import base64
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OVERLAY_DIR = ROOT / "docs" / "research" / "gmt-phase-b-results" / "overlays"

SWEEP_MAX = 260
SWEEP_QUALITY = 60
CLOSEUP_MAX = 1400
CLOSEUP_QUALITY = 85


def main() -> int:
    prefix = sys.argv[1] if len(sys.argv) > 1 else None
    files = sorted(OVERLAY_DIR.glob("*.png"))
    if prefix:
        files = [f for f in files if f.stem.startswith(prefix)]
        max_dim, quality = CLOSEUP_MAX, CLOSEUP_QUALITY
    else:
        max_dim, quality = SWEEP_MAX, SWEEP_QUALITY

    print(f"### {len(files)} overlays to print (prefix={prefix!r}, max_dim={max_dim})")
    for path in files:
        label = path.stem
        with Image.open(path) as im:
            im = im.convert("RGB")
            im.thumbnail((max_dim, max_dim), Image.Resampling.LANCZOS)
            from io import BytesIO
            buf = BytesIO()
            im.save(buf, "JPEG", quality=quality)
            data = buf.getvalue()
        b64 = base64.b64encode(data).decode("ascii")
        print(f"### BEGIN {label} bytes={len(data)}")
        for i in range(0, len(b64), 200):
            print(b64[i:i + 200])
        print(f"### END {label}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
