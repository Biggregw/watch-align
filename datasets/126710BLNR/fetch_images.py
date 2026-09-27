#!/usr/bin/env python3
"""Fetch and normalise the public 126710BLNR research corpus.

Third-party source photographs are intentionally not committed to the repository.
This script downloads them locally from the URLs recorded in manifest.csv, preserves
one directory per independent physical watch/QC batch, normalises orientation/size,
and writes a resolved_images.csv with hashes and provenance.

This is research data preparation, not authentication. `gen_candidate` means only
that the source/listing labels the watch as genuine. It is not independent proof.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import requests
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / "manifest.csv"
RESOLVED = ROOT / "resolved_images.csv"
VALID_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif"}
USER_AGENT = "WatchAlignResearch/1.0 (+https://github.com/Biggregw/watch-align)"


@dataclass(frozen=True)
class Source:
    source_id: str
    class_label: str
    provenance: str
    split: str
    factory: str
    model: str
    bracelet: str
    physical_watch_id: str
    source_url: str
    image_url: str
    fetch_mode: str
    max_images: int
    qc_notes: str


def read_manifest() -> list[Source]:
    rows: list[Source] = []
    with MANIFEST.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        required = {
            "source_id", "class_label", "provenance", "split", "factory", "model",
            "bracelet", "physical_watch_id", "source_url", "image_url", "fetch_mode",
            "max_images", "qc_notes",
        }
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise RuntimeError(f"manifest missing columns: {sorted(missing)}")
        for row in reader:
            rows.append(Source(
                source_id=row["source_id"].strip(),
                class_label=row["class_label"].strip(),
                provenance=row["provenance"].strip(),
                split=row["split"].strip(),
                factory=row["factory"].strip(),
                model=row["model"].strip(),
                bracelet=row["bracelet"].strip(),
                physical_watch_id=row["physical_watch_id"].strip(),
                source_url=row["source_url"].strip(),
                image_url=row["image_url"].strip(),
                fetch_mode=row["fetch_mode"].strip(),
                max_images=int(row["max_images"]),
                qc_notes=row["qc_notes"].strip(),
            ))
    validate_manifest(rows)
    return rows


def validate_manifest(rows: list[Source]) -> None:
    ids = set()
    watch_split: dict[str, str] = {}
    for row in rows:
        if row.source_id in ids:
            raise RuntimeError(f"duplicate source_id: {row.source_id}")
        ids.add(row.source_id)
        if row.model != "126710BLNR":
            raise RuntimeError(f"wrong model in {row.source_id}: {row.model}")
        if row.class_label not in {"gen", "rep"}:
            raise RuntimeError(f"bad class_label in {row.source_id}: {row.class_label}")
        if row.split not in {"calibration", "validation", "reference"}:
            raise RuntimeError(f"bad split in {row.source_id}: {row.split}")
        if row.fetch_mode not in {"direct", "gallery"}:
            raise RuntimeError(f"bad fetch_mode in {row.source_id}: {row.fetch_mode}")
        if row.class_label == "gen" and row.provenance not in {"official", "gen_candidate"}:
            raise RuntimeError(f"bad genuine provenance in {row.source_id}: {row.provenance}")
        if row.class_label == "rep" and row.provenance != "rep_labelled":
            raise RuntimeError(f"rep source must be explicitly labelled in {row.source_id}")
        previous = watch_split.get(row.physical_watch_id)
        if previous is not None and previous != row.split:
            raise RuntimeError(
                f"physical watch {row.physical_watch_id} leaks across splits: {previous} vs {row.split}"
            )
        watch_split[row.physical_watch_id] = row.split

    # `official` is a reference image, not an independent physical-watch population sample.
    gen_cal = {r.physical_watch_id for r in rows if r.class_label == "gen" and r.provenance == "gen_candidate" and r.split == "calibration"}
    gen_val = {r.physical_watch_id for r in rows if r.class_label == "gen" and r.provenance == "gen_candidate" and r.split == "validation"}
    rep_cal = {r.physical_watch_id for r in rows if r.class_label == "rep" and r.split == "calibration"}
    rep_val = {r.physical_watch_id for r in rows if r.class_label == "rep" and r.split == "validation"}
    if len(gen_cal) < 3 or len(gen_val) < 2 or len(rep_cal) < 5 or len(rep_val) < 3:
        raise RuntimeError(
            "dataset split too small: "
            f"gen calibration={len(gen_cal)}, gen validation={len(gen_val)}, "
            f"rep calibration={len(rep_cal)}, rep validation={len(rep_val)}"
        )


def fetch_direct(source: Source, raw_dir: Path) -> None:
    response = requests.get(
        source.image_url,
        headers={"User-Agent": USER_AGENT, "Referer": source.source_url},
        timeout=60,
    )
    response.raise_for_status()
    (raw_dir / "download.jpg").write_bytes(response.content)


def fetch_gallery(source: Source, raw_dir: Path) -> None:
    if shutil.which("gallery-dl") is None:
        raise RuntimeError("gallery-dl is required for album sources; install requirements.txt")
    cmd = [
        "gallery-dl",
        "--no-mtime",
        "--range", f"1-{source.max_images}",
        "-D", str(raw_dir),
        source.image_url,
    ]
    result = subprocess.run(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        timeout=240,
    )
    (raw_dir / "fetch.log").write_text(result.stdout, encoding="utf-8", errors="replace")
    if result.returncode != 0:
        raise RuntimeError(f"gallery-dl returned {result.returncode}")


def image_candidates(raw_dir: Path) -> Iterable[Path]:
    for path in sorted(raw_dir.rglob("*")):
        if path.is_file() and path.suffix.lower() in VALID_EXT:
            yield path


def dhash(image: Image.Image) -> str:
    tiny = image.convert("L").resize((9, 8), Image.Resampling.LANCZOS)
    pixels = list(tiny.getdata())
    value = 0
    bit = 0
    for y in range(8):
        for x in range(8):
            left = pixels[y * 9 + x]
            right = pixels[y * 9 + x + 1]
            if left > right:
                value |= 1 << bit
            bit += 1
    return f"{value:016x}"


def hamming_hex(a: str, b: str) -> int:
    return (int(a, 16) ^ int(b, 16)).bit_count()


def normalise_source(source: Source, raw_dir: Path, global_hashes: dict[str, tuple[str, str]]) -> list[dict[str, str]]:
    dest = ROOT / source.class_label / source.split / source.source_id
    shutil.rmtree(dest, ignore_errors=True)
    dest.mkdir(parents=True, exist_ok=True)

    rows: list[dict[str, str]] = []
    local_sha: set[str] = set()
    index = 0
    for candidate in image_candidates(raw_dir):
        try:
            with Image.open(candidate) as opened:
                opened.seek(0)
                image = ImageOps.exif_transpose(opened).convert("RGB")
            if min(image.size) < 300 or image.width * image.height < 180_000:
                continue
            image.thumbnail((1800, 1800), Image.Resampling.LANCZOS)
            out = dest / f"image_{index:02d}.jpg"
            image.save(out, "JPEG", quality=92, optimize=True)
            payload = out.read_bytes()
            sha = hashlib.sha256(payload).hexdigest()
            phash = dhash(image)
            if sha in local_sha:
                out.unlink(missing_ok=True)
                continue
            local_sha.add(sha)

            duplicate_of = ""
            if sha in global_hashes:
                duplicate_of = global_hashes[sha][0]
            else:
                # Flag strong visual near-duplicates but do not silently drop them. A human can
                # decide whether they are alternate crops or actually duplicated source watches.
                for seen_sha, (seen_id, seen_dhash) in global_hashes.items():
                    if hamming_hex(phash, seen_dhash) <= 3:
                        duplicate_of = seen_id
                        break
                global_hashes[sha] = (f"{source.source_id}/{out.name}", phash)

            rows.append({
                "source_id": source.source_id,
                "class_label": source.class_label,
                "provenance": source.provenance,
                "split": source.split,
                "factory": source.factory,
                "model": source.model,
                "bracelet": source.bracelet,
                "physical_watch_id": source.physical_watch_id,
                "source_url": source.source_url,
                "image_url": source.image_url,
                "local_path": str(out.relative_to(ROOT)),
                "width": str(image.width),
                "height": str(image.height),
                "sha256": sha,
                "dhash": phash,
                "possible_duplicate_of": duplicate_of,
                "qc_notes": source.qc_notes,
            })
            index += 1
            if index >= source.max_images:
                break
        except Exception as exc:  # bad or non-image album item
            print(f"  skip {candidate.name}: {exc}")
    return rows


def select_sources(rows: list[Source], args: argparse.Namespace) -> list[Source]:
    selected = rows
    if args.class_label:
        selected = [r for r in selected if r.class_label == args.class_label]
    if args.split:
        selected = [r for r in selected if r.split == args.split]
    if args.source:
        wanted = set(args.source)
        selected = [r for r in selected if r.source_id in wanted]
        missing = wanted - {r.source_id for r in selected}
        if missing:
            raise RuntimeError(f"unknown/unselected source ids: {sorted(missing)}")
    return selected


def write_resolved(rows: list[dict[str, str]]) -> None:
    fields = [
        "source_id", "class_label", "provenance", "split", "factory", "model", "bracelet",
        "physical_watch_id", "source_url", "image_url", "local_path", "width", "height",
        "sha256", "dhash", "possible_duplicate_of", "qc_notes",
    ]
    with RESOLVED.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--class", dest="class_label", choices=["gen", "rep"])
    parser.add_argument("--split", choices=["calibration", "validation", "reference"])
    parser.add_argument("--source", action="append", help="fetch only this source_id; repeatable")
    parser.add_argument("--clean", action="store_true", help="remove existing gen/rep downloads first")
    parser.add_argument("--strict", action="store_true", help="fail if any selected source yields zero images")
    args = parser.parse_args()

    sources = select_sources(read_manifest(), args)
    if args.clean:
        shutil.rmtree(ROOT / "gen", ignore_errors=True)
        shutil.rmtree(ROOT / "rep", ignore_errors=True)

    resolved_rows: list[dict[str, str]] = []
    global_hashes: dict[str, tuple[str, str]] = {}
    failures: list[str] = []

    with tempfile.TemporaryDirectory(prefix="watch-align-batgirl-") as tmp:
        tmp_root = Path(tmp)
        for n, source in enumerate(sources, start=1):
            print(f"[{n}/{len(sources)}] {source.source_id} ({source.class_label}/{source.split})")
            raw_dir = tmp_root / source.source_id
            raw_dir.mkdir(parents=True, exist_ok=True)
            try:
                if source.fetch_mode == "direct":
                    fetch_direct(source, raw_dir)
                else:
                    fetch_gallery(source, raw_dir)
                rows = normalise_source(source, raw_dir, global_hashes)
                if not rows:
                    raise RuntimeError("no usable images produced")
                resolved_rows.extend(rows)
                print(f"  normalised {len(rows)} image(s)")
            except Exception as exc:
                failures.append(f"{source.source_id}: {exc}")
                print(f"  FAILED: {exc}", file=sys.stderr)

    write_resolved(resolved_rows)

    source_count = len({row["source_id"] for row in resolved_rows})
    gen_watches = len({row["physical_watch_id"] for row in resolved_rows if row["class_label"] == "gen" and row["provenance"] == "gen_candidate"})
    rep_watches = len({row["physical_watch_id"] for row in resolved_rows if row["class_label"] == "rep"})
    print(f"\nresolved images: {len(resolved_rows)}")
    print(f"populated sources: {source_count}/{len(sources)}")
    print(f"independent gen_candidate watches populated: {gen_watches}")
    print(f"independent rep-labelled watches populated: {rep_watches}")
    print(f"resolved manifest: {RESOLVED}")

    if failures:
        print("\nsource failures:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        if args.strict:
            return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
