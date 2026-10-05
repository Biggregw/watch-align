#!/usr/bin/env python3
"""Populate Alpha90 androidTest assets from the provenance-controlled GMT dataset.

This deliberately keeps third-party image bytes out of git.  The repo stores only a
curated manifest.  GitHub Actions downloads the public Imgur-backed source albums,
normalises them with the existing dataset fetcher, and copies the exact selected
images into the runtime androidTest asset directory.

The locked Reddit validation queue remains separate.  These images are a reusable
regression/coverage pool and must not silently replace a locked validation case.
"""
from __future__ import annotations

import csv
import pathlib
import re
import shutil
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
POOL = HERE / "alpha90_test_pool.tsv"
DATASET = ROOT / "datasets" / "126710BLNR"
MANIFEST = DATASET / "manifest.csv"
FETCHER = DATASET / "fetch_images.py"
OUT = HERE / "app" / "src" / "androidTest" / "assets" / "alpha90_validation"
RUNTIME = OUT / "runtime_manifest.tsv"

RUNTIME_FIELDS = [
    "case_id", "class", "thread_id", "target", "notes",
    "asset", "source_url", "status",
]


def read_tsv(path: pathlib.Path) -> list[dict[str, str]]:
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


def main() -> int:
    pool = read_tsv(POOL)
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
    print(f"Fetching {len(source_ids)} provenance-controlled GMT sources")
    subprocess.run(cmd, cwd=ROOT, check=True)

    OUT.mkdir(parents=True, exist_ok=True)
    runtime = [r for r in load_runtime() if r.get("status") != "curated_pool"]

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
                "case_id": row["case_id"],
                "class": row["role"],
                "thread_id": thread_id(meta.get("source_url", "")),
                "target": row["target"],
                "notes": row["notes"],
                "asset": "",
                "source_url": meta.get("source_url", ""),
                "status": "curated_pool_fetch_error",
            })
            continue

        shutil.copy2(src, dst)
        copied += 1
        notes = row["notes"]
        extra = f"factory={meta.get('factory','')}; model={meta.get('model','')}; dataset_source={sid}; selected={source_image}"
        runtime.append({
            "case_id": row["case_id"],
            "class": row["role"],
            "thread_id": thread_id(meta.get("source_url", "")),
            "target": row["target"],
            "notes": f"{notes}; {extra}",
            "asset": asset,
            "source_url": meta.get("source_url", ""),
            "status": "curated_pool",
        })
        print(f"{row['case_id']}: {sid}/{source_image} -> {asset}")

    write_runtime(runtime)
    (OUT / "curated_pool_errors.txt").write_text(
        "\n".join(failures) + ("\n" if failures else ""), encoding="utf-8"
    )

    print(f"Curated Alpha90 pool prepared: {copied}/{len(pool)} images")
    if failures:
        print("Pool preparation failures:", file=sys.stderr)
        for line in failures:
            print(f"  {line}", file=sys.stderr)
    return 0 if copied == len(pool) else 3


if __name__ == "__main__":
    raise SystemExit(main())
