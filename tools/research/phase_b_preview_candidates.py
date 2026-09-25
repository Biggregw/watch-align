#!/usr/bin/env python3
"""Print small base64-encoded previews of every downloaded Phase B candidate
image to stdout, for visual suitability review via CI job logs (direct
artifact download is blocked in this environment -- see
docs/research/gmt-phase-a-image-selection.md for the same constraint in
Phase A). Each preview is labelled with source_id, image_index,
physical_watch_id, provenance_class and native resolution so the reviewer can
judge suitability and cross-reference back to candidate_status.csv.
"""
from __future__ import annotations

import base64
import csv
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
STATUS_CSV = ROOT / "datasets" / "gmt_phase_b_genuine" / "candidate_status.csv"
PREVIEW_MAX = 380
JPEG_QUALITY = 68


def main() -> int:
    if not STATUS_CSV.exists():
        print("no candidate_status.csv -- run phase_b_fetch_genuine.py first")
        return 1
    rows = [r for r in csv.DictReader(STATUS_CSV.open(encoding="utf-8")) if r.get("local_path")]
    print(f"### {len(rows)} candidate images to preview")
    for row in rows:
        path = ROOT / row["local_path"]
        if not path.exists():
            continue
        with Image.open(path) as im:
            im = im.convert("RGB")
            im.thumbnail((PREVIEW_MAX, PREVIEW_MAX), Image.Resampling.LANCZOS)
            buf = BytesIO()
            im.save(buf, "JPEG", quality=JPEG_QUALITY)
            data = buf.getvalue()
        b64 = base64.b64encode(data).decode("ascii")
        label = f"{row['source_id']}__img{row['image_index']}"
        print(
            f"### BEGIN {label} watch={row['physical_watch_id']} "
            f"provenance={row['provenance_class']} native={row['native_width']}x{row['native_height']} "
            f"bytes={len(data)}"
        )
        for i in range(0, len(b64), 200):
            print(b64[i:i + 200])
        print(f"### END {label}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
