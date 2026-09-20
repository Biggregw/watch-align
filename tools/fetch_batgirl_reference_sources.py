#!/usr/bin/env python3
"""Fetch the fixed 126710BLNR source set used by the genuine-reference research build.

The output is written directly into androidTest assets so the Android instrumentation
builder can run the production minute-track acquisition and rectification code.

Important provenance rule:
- official_rolex_2026 is first-party Rolex catalogue imagery.
- gen_candidate_* sources are market listings labelled as genuine by their sellers/posts.
  They are not independently authenticated by this script and remain explicitly marked
  as candidates in the generated manifest.
"""

from __future__ import annotations

import csv
import hashlib
import shutil
import subprocess
from pathlib import Path

import requests
from PIL import Image, ImageOps

ROOT = Path("android/app/src/androidTest/assets/batgirl-reference/126710BLNR")
TMP = Path(".batgirl-reference-fetch")

OFFICIAL = (
    "official_rolex_2026",
    "official",
    "https://media.rolex.com/image/upload/q_auto/f_jpg/t_v7-cover-majesty-landscape/"
    "c_limit,w_1920/v1/a677b2c664f6/catalogue/2026/upright-c/m126710blnr-0002",
)

# Each source is intended to represent a different physical watch. We fetch multiple
# photos from an album, but the Android builder later selects at most one accepted image
# per source, preventing large albums from dominating the canonical reference.
CANDIDATES = [
    ("gen_candidate_wex_20260914_vmbUDwy", "gen_candidate", "https://imgur.com/a/vmbUDwy"),
    ("gen_candidate_wex_20260619_K4gqk6U", "gen_candidate", "https://imgur.com/a/K4gqk6U"),
    ("gen_candidate_wex_3KSuGhC", "gen_candidate", "https://imgur.com/a/3KSuGhC"),
    ("gen_candidate_wex_e99gXKb", "gen_candidate", "https://imgur.com/a/e99gXKb"),
    ("gen_candidate_wex_1TDYtpN", "gen_candidate", "https://imgur.com/a/1TDYtpN"),
]

VALID_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif"}


def prepare_dirs() -> None:
    shutil.rmtree(ROOT, ignore_errors=True)
    shutil.rmtree(TMP, ignore_errors=True)
    ROOT.mkdir(parents=True, exist_ok=True)
    TMP.mkdir(parents=True, exist_ok=True)


def normalize_source(source_id: str, provenance: str, url: str, raw_dir: Path) -> list[tuple]:
    dest = ROOT / source_id
    dest.mkdir(parents=True, exist_ok=True)
    rows = []
    seen = set()
    candidates = sorted(p for p in raw_dir.rglob("*") if p.is_file() and p.suffix.lower() in VALID_EXT)
    index = 0
    for p in candidates:
        try:
            im = Image.open(p)
            im.seek(0)
            im = ImageOps.exif_transpose(im).convert("RGB")
            if min(im.size) < 300 or im.width * im.height < 180_000:
                continue
            im.thumbnail((1800, 1800), Image.Resampling.LANCZOS)
            out = dest / f"image_{index:02d}.jpg"
            im.save(out, "JPEG", quality=92, optimize=True)
            digest = hashlib.sha256(out.read_bytes()).hexdigest()
            if digest in seen:
                out.unlink(missing_ok=True)
                continue
            seen.add(digest)
            rows.append((source_id, provenance, url, out.name, im.width, im.height, digest))
            index += 1
        except Exception as exc:
            print(f"skip {p}: {exc}")
    return rows


def fetch_official() -> list[tuple]:
    source_id, provenance, url = OFFICIAL
    raw_dir = TMP / source_id
    raw_dir.mkdir(parents=True, exist_ok=True)
    out = raw_dir / "official.jpg"
    response = requests.get(
        url,
        headers={"User-Agent": "Mozilla/5.0", "Referer": "https://www.rolex.com/"},
        timeout=45,
    )
    response.raise_for_status()
    out.write_bytes(response.content)
    return normalize_source(source_id, provenance, url, raw_dir)


def fetch_album(source_id: str, provenance: str, url: str) -> list[tuple]:
    raw_dir = TMP / source_id
    raw_dir.mkdir(parents=True, exist_ok=True)
    cmd = [
        "gallery-dl",
        "--no-mtime",
        "--range",
        "1-10",
        "-D",
        str(raw_dir),
        url,
    ]
    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180)
    (raw_dir / "fetch.log").write_text(proc.stdout, encoding="utf-8", errors="replace")
    if proc.returncode != 0:
        print(f"warning: gallery-dl returned {proc.returncode} for {source_id}")
    return normalize_source(source_id, provenance, url, raw_dir)


def main() -> None:
    prepare_dirs()
    rows = []
    rows.extend(fetch_official())
    for source in CANDIDATES:
        rows.extend(fetch_album(*source))

    with (ROOT / "sources.tsv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f, delimiter="\t")
        writer.writerow(["source_id", "provenance", "url", "asset_file", "width", "height", "sha256"])
        writer.writerows(rows)

    source_counts = {}
    for source_id, *_ in rows:
        source_counts[source_id] = source_counts.get(source_id, 0) + 1

    print(f"normalized images: {len(rows)}")
    for source_id, provenance, _ in [OFFICIAL] + CANDIDATES:
        print(f"{source_id}\t{provenance}\t{source_counts.get(source_id, 0)}")

    assert source_counts.get(OFFICIAL[0], 0) >= 1, "Official Rolex image was not fetched"
    populated = sum(1 for source_id, _, _ in [OFFICIAL] + CANDIDATES if source_counts.get(source_id, 0) > 0)
    assert populated >= 5, f"Only {populated} independent sources produced usable images"


if __name__ == "__main__":
    main()
