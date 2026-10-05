#!/usr/bin/env python3
"""Populate Alpha90 androidTest assets from provenance-controlled GMT sources.

Third-party image bytes are intentionally not committed.  The repo stores curated
manifests.  CI fetches public Imgur-backed albums, normalises selected photographs,
and places the runtime copies into androidTest assets.

Two source sets are kept distinct:
- alpha90_test_pool.tsv: existing 126710BLNR research-dataset images with stable IDs;
- alpha90_external_sources.tsv: additional BLNR/BLRO/GRNR coverage gathered specifically
  for frozen Alpha90 validation.

The locked Reddit validation queue remains separate.  Nothing here silently replaces
a locked validation case or changes Alpha90 production code.
"""
from __future__ import annotations

import csv
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Iterable

from PIL import Image, ImageOps

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
POOL = HERE / "alpha90_test_pool.tsv"
EXTERNAL = HERE / "alpha90_external_sources.tsv"
DATASET = ROOT / "datasets" / "126710BLNR"
MANIFEST = DATASET / "manifest.csv"
FETCHER = DATASET / "fetch_images.py"
OUT = HERE / "app" / "src" / "androidTest" / "assets" / "alpha90_validation"
RUNTIME = OUT / "runtime_manifest.tsv"
VALID_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif"}

RUNTIME_FIELDS = [
    "case_id", "class", "thread_id", "target", "notes",
    "asset", "source_url", "status",
]


def read_tsv(path: pathlib.Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def read_csv(path: pathlib.Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def thread_id(url: str) -> str:
    m = re.search(r"/comments/([^/]+)", url or "")
    return m.group(1) if m else ""


def load_runtime() -> list[dict[str, str]]:
    if not RUNTIME.exists():
        return []
    with RUNTIME.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def write_runtime(rows: list[dict[str, str]]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    with RUNTIME.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=RUNTIME_FIELDS, delimiter="\t", extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)


def image_candidates(raw_dir: pathlib.Path) -> Iterable[pathlib.Path]:
    for p in sorted(raw_dir.rglob("*")):
        if p.is_file() and p.suffix.lower() in VALID_EXT:
            yield p


def normalise_external_images(raw_dir: pathlib.Path, source_id: str, limit: int) -> list[pathlib.Path]:
    saved: list[pathlib.Path] = []
    seen: set[bytes] = set()
    for candidate in image_candidates(raw_dir):
        if len(saved) >= limit:
            break
        try:
            with Image.open(candidate) as opened:
                opened.seek(0)
                image = ImageOps.exif_transpose(opened).convert("RGB")
            if min(image.size) < 300 or image.width * image.height < 180_000:
                continue
            image.thumbnail((1800, 1800), Image.Resampling.LANCZOS)
            # Avoid obvious exact duplicates after normalisation without adding another dependency.
            thumb = image.copy()
            thumb.thumbnail((64, 64), Image.Resampling.BILINEAR)
            fingerprint = thumb.tobytes()
            if fingerprint in seen:
                continue
            seen.add(fingerprint)
            out = OUT / f"EXT_{source_id}_{len(saved):02d}.jpg"
            image.save(out, "JPEG", quality=92, optimize=True)
            saved.append(out)
        except Exception as exc:
            print(f"WARNING {source_id}: skipped {candidate.name}: {exc}", file=sys.stderr)
    return saved


def fetch_external(row: dict[str, str]) -> tuple[list[pathlib.Path], str]:
    source_id = row["source_id"].strip()
    max_images = max(1, int(row.get("max_images") or 10))
    select_count = max(1, int(row.get("select_count") or 1))
    album = row["album_url"].strip()
    if shutil.which("gallery-dl") is None:
        return [], "gallery-dl not installed"
    with tempfile.TemporaryDirectory(prefix=f"alpha90-{source_id}-") as td:
        raw = pathlib.Path(td)
        cmd = [
            "gallery-dl", "--no-mtime", "--range", f"1-{max_images}",
            "-D", str(raw), album,
        ]
        try:
            proc = subprocess.run(
                cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, timeout=240,
            )
        except Exception as exc:
            return [], f"gallery fetch exception: {exc}"
        if proc.returncode != 0:
            tail = " | ".join(proc.stdout.splitlines()[-4:])
            return [], f"gallery-dl returned {proc.returncode}: {tail}"
        files = normalise_external_images(raw, source_id, select_count)
        if not files:
            return [], "gallery downloaded but no eligible still images were normalised"
        return files, ""


def main() -> int:
    pool = read_tsv(POOL)
    external = read_tsv(EXTERNAL)
    source_rows = {r["source_id"]: r for r in read_csv(MANIFEST)}

    missing = sorted({r["source_id"] for r in pool if r["source_id"] not in source_rows})
    if missing:
        raise SystemExit(f"pool references source ids missing from dataset manifest: {missing}")

    source_ids: list[str] = []
    seen: set[str] = set()
    for r in pool:
        sid = r["source_id"]
        if sid not in seen:
            source_ids.append(sid)
            seen.add(sid)

    cmd = [sys.executable, str(FETCHER), "--clean"]
    for sid in source_ids:
        cmd += ["--source", sid]
    print(f"Fetching {len(source_ids)} provenance-controlled BLNR research sources")
    subprocess.run(cmd, cwd=ROOT, check=True)

    OUT.mkdir(parents=True, exist_ok=True)
    runtime = [
        r for r in load_runtime()
        if r.get("status") not in {"curated_pool", "external_curated_pool", "external_pool_fetch_error"}
    ]

    copied = 0
    failures: list[str] = []
    for row in pool:
        sid = row["source_id"]
        meta = source_rows[sid]
        class_label = meta["class_label"].strip().lower()
        split = meta["split"].strip()
        source_image = row["source_image"].strip()
        src = DATASET / class_label / split / sid / source_image
        asset = f"POOL_{row['case_id']}.jpg"
        dst = OUT / asset

        if not src.is_file():
            failures.append(f"{row['case_id']}: selected image missing after fetch: {src}")
            runtime.append({
                "case_id": row["case_id"], "class": row["role"],
                "thread_id": thread_id(meta.get("source_url", "")),
                "target": row["target"], "notes": row["notes"], "asset": "",
                "source_url": meta.get("source_url", ""), "status": "curated_pool_fetch_error",
            })
            continue

        shutil.copy2(src, dst)
        copied += 1
        extra = (
            f"factory={meta.get('factory','')}; model={meta.get('model','')}; "
            f"dataset_source={sid}; selected={source_image}"
        )
        runtime.append({
            "case_id": row["case_id"], "class": row["role"],
            "thread_id": thread_id(meta.get("source_url", "")),
            "target": row["target"], "notes": f"{row['notes']}; {extra}",
            "asset": asset, "source_url": meta.get("source_url", ""), "status": "curated_pool",
        })
        print(f"{row['case_id']}: {sid}/{source_image} -> {asset}")

    external_sources_ok = 0
    external_images = 0
    for row in external:
        sid = row["source_id"].strip()
        files, error = fetch_external(row)
        if not files:
            failures.append(f"{sid}: {error}")
            runtime.append({
                "case_id": sid, "class": row["role"],
                "thread_id": thread_id(row["reddit_url"]), "target": row["target"],
                "notes": f"{row['notes']}; factory={row['factory']}; model={row['model']}; album={row['album_url']}",
                "asset": "", "source_url": row["reddit_url"], "status": "external_pool_fetch_error",
            })
            print(f"WARNING {sid}: {error}", file=sys.stderr)
            continue
        external_sources_ok += 1
        for i, path in enumerate(files):
            external_images += 1
            runtime.append({
                "case_id": f"{sid}_{i:02d}", "class": row["role"],
                "thread_id": thread_id(row["reddit_url"]), "target": row["target"],
                "notes": (
                    f"{row['notes']}; factory={row['factory']}; model={row['model']}; "
                    f"album={row['album_url']}; selected_external_index={i}"
                ),
                "asset": path.name, "source_url": row["reddit_url"], "status": "external_curated_pool",
            })
        print(f"{sid}: external album -> {len(files)} runtime image(s)")

    write_runtime(runtime)
    (OUT / "curated_pool_errors.txt").write_text(
        "\n".join(failures) + ("\n" if failures else ""), encoding="utf-8"
    )

    print(f"Curated BLNR Alpha90 pool prepared: {copied}/{len(pool)} images")
    print(f"Cross-model external sources populated: {external_sources_ok}/{len(external)} sources, {external_images} images")
    if failures:
        print("Pool preparation warnings/failures:", file=sys.stderr)
        for line in failures:
            print(f"  {line}", file=sys.stderr)

    # Existing pool is strict. External source set is strict per source, but can yield
    # fewer than select_count images if the album contains videos/duplicates.
    return 0 if copied == len(pool) and external_sources_ok == len(external) else 3


if __name__ == "__main__":
    raise SystemExit(main())
