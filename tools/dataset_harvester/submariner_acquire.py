#!/usr/bin/env python3
"""Acquire the phase-1 Submariner research source pool without running GMT QC.

This is deliberately acquisition-only. It resolves dealer/listing pages and Imgur albums,
downloads still images, records provenance and exact SHA-256 duplicates, and writes manifests
for later Submariner-specific suitability/geometry work. It does NOT call the GMT analysis
harness, GmtHumanPosePolicy, GMT measurement code, or any authenticity classifier.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import shutil
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageOps

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from harvester.config import REPO_ROOT  # noqa: E402
from harvester.families import SUBMARINER_12  # noqa: E402
from harvester.http import FetchError, Http  # noqa: E402
from harvester.resolvers import ImageRef, imgur_album, page_images  # noqa: E402

DEFAULT_POOL = REPO_ROOT / "docs" / "research" / "submariner_candidate_sources_2026-09-30.csv"
DEFAULT_OUT = REPO_ROOT / "datasets" / "submariner_research"
IMAGE_EXTS = {"JPEG": ".jpg", "PNG": ".png", "WEBP": ".webp"}


@dataclass
class Acquired:
    candidate_id: str
    class_label: str
    model: str
    factory: str
    source_type: str
    source_name: str
    source_url: str
    image_album_url: str
    provenance_note: str
    image_index: int
    image_url: str
    sha256: str
    local_path: str
    width: int
    height: int
    bytes: int
    exact_duplicate_of: str = ""
    acquisition_status: str = "acquired"
    acquisition_note: str = ""


def rows(path: Path) -> list[dict]:
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def safe_id(value: str) -> str:
    return "".join(c if c.isalnum() or c in "-_" else "_" for c in value)


def image_bytes(ref: ImageRef, http: Http) -> tuple[bytes, str]:
    if ref.path:
        p = Path(ref.path)
        return p.read_bytes(), ""
    r = http.get(ref.url)
    return r.content, ref.url


def inspect(data: bytes) -> tuple[Image.Image, str]:
    import io
    im = Image.open(io.BytesIO(data))
    im.load()
    im = ImageOps.exif_transpose(im).convert("RGB")
    fmt = (Image.open(io.BytesIO(data)).format or "JPEG").upper()
    return im, IMAGE_EXTS.get(fmt, ".jpg")


def resolve_candidate(row: dict, http: Http, work: Path, max_images: int) -> list[ImageRef]:
    album = (row.get("image_album_url") or "").strip()
    source = (row.get("source_url") or "").strip()
    if album:
        from harvester.canonical import imgur_album_id
        aid = imgur_album_id(album)
        if not aid:
            raise FetchError("unsupported_album", album, retryable=False)
        return imgur_album(http, aid, work, max_images)
    if source and "reddit.com/" not in source.lower():
        return page_images(http, source, max_images)
    # Unresolved Reddit-only candidates are kept in the report, not silently dropped. The
    # candidate pool already records them so we can resolve them later with an album/API source.
    return []


def write_csv(path: Path, fieldnames: list[str], data: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        w.writeheader()
        w.writerows(data)


def run(pool: Path, out: Path, max_images: int = 12) -> dict:
    out.mkdir(parents=True, exist_ok=True)
    images_dir = out / "images"
    work = out / "work"
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True, exist_ok=True)
    http = Http()

    source_rows = rows(pool)
    allowed = {m.upper() for m in SUBMARINER_12.models}
    selected = [r for r in source_rows if (r.get("candidate_status") or "candidate") == "candidate"
                and (r.get("model") or "").upper() in allowed]

    acquired: list[Acquired] = []
    summary: list[dict] = []
    first_sha: dict[str, str] = {}
    counts = Counter()
    by_model = Counter()
    by_class = Counter()
    errors = []

    for row in selected:
        cid = row["candidate_id"]
        cwork = work / safe_id(cid)
        cwork.mkdir(parents=True, exist_ok=True)
        status = "resolved"
        note = ""
        refs: list[ImageRef] = []
        try:
            refs = resolve_candidate(row, http, cwork, max_images)
            if not refs:
                status = "unresolved_source"
                note = "No public album URL in source pool; dealer page unavailable or Reddit-only source needs later resolution"
        except FetchError as e:
            status = "fetch_error"
            note = f"{e.reason}: {e.detail}" if e.detail else e.reason
            errors.append(f"{cid}: {note}")
        except Exception as e:
            status = "resolver_error"
            note = f"{type(e).__name__}: {e}"
            errors.append(f"{cid}: {note}")

        good = 0
        for idx, ref in enumerate(refs[:max_images], start=1):
            try:
                data, image_url = image_bytes(ref, http)
                im, ext = inspect(data)
            except Exception as e:
                errors.append(f"{cid} image {idx}: {type(e).__name__}: {e}")
                continue
            h = hashlib.sha256(data).hexdigest()
            duplicate_of = first_sha.get(h, "")
            first_sha.setdefault(h, f"{cid}:{idx}")
            dest = images_dir / safe_id(cid) / f"{idx:02d}_{h[:12]}{ext}"
            dest.parent.mkdir(parents=True, exist_ok=True)
            if not dest.exists():
                dest.write_bytes(data)
            rec = Acquired(
                candidate_id=cid,
                class_label=row.get("class", ""),
                model=row.get("model", ""),
                factory=row.get("factory", ""),
                source_type=row.get("source_type", ""),
                source_name=row.get("source_name", ""),
                source_url=row.get("source_url", ""),
                image_album_url=row.get("image_album_url", ""),
                provenance_note=row.get("provenance_note", ""),
                image_index=idx,
                image_url=image_url,
                sha256=h,
                local_path=str(dest.relative_to(out)),
                width=im.width,
                height=im.height,
                bytes=len(data),
                exact_duplicate_of=duplicate_of,
            )
            acquired.append(rec)
            good += 1
            counts["images"] += 1
            if duplicate_of:
                counts["exact_duplicates"] += 1
        if good:
            counts["candidates_with_images"] += 1
            by_model[row.get("model", "")] += 1
            by_class[row.get("class", "")] += 1
            if status != "resolved":
                status = "partial"
        else:
            counts["candidates_without_images"] += 1
        summary.append({
            "candidate_id": cid,
            "class": row.get("class", ""),
            "model": row.get("model", ""),
            "factory": row.get("factory", ""),
            "source_type": row.get("source_type", ""),
            "source_url": row.get("source_url", ""),
            "image_album_url": row.get("image_album_url", ""),
            "images_acquired": good,
            "acquisition_status": status,
            "acquisition_note": note,
        })

    write_csv(out / "acquired_images.csv", list(Acquired.__dataclass_fields__), [a.__dict__ for a in acquired])
    write_csv(out / "candidate_summary.csv",
              ["candidate_id", "class", "model", "factory", "source_type", "source_url", "image_album_url",
               "images_acquired", "acquisition_status", "acquisition_note"], summary)

    report = {
        "family": SUBMARINER_12.key,
        "models": list(SUBMARINER_12.models),
        "source_pool_candidates": len(selected),
        "candidates_with_images": counts["candidates_with_images"],
        "candidates_without_images": counts["candidates_without_images"],
        "images_acquired": counts["images"],
        "exact_duplicates": counts["exact_duplicates"],
        "candidate_watches_by_class": dict(sorted(by_class.items())),
        "candidate_watches_by_model": dict(sorted(by_model.items())),
        "errors": errors,
        "note": "Acquisition only. No GMT pose, GMT QC, Submariner geometry or authenticity decision has been run.",
    }
    (out / "acquisition_report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    with (out / "acquisition_report.md").open("w", encoding="utf-8") as f:
        f.write("# Submariner source-pool acquisition\n\n")
        f.write(f"- Candidates in phase-1 pool: {len(selected)}\n")
        f.write(f"- Candidates with at least one acquired image: {counts['candidates_with_images']}\n")
        f.write(f"- Candidates still unresolved/empty: {counts['candidates_without_images']}\n")
        f.write(f"- Images acquired: {counts['images']}\n")
        f.write(f"- Exact duplicate images: {counts['exact_duplicates']}\n")
        f.write(f"- Candidate watches by class: {dict(sorted(by_class.items()))}\n")
        f.write(f"- Candidate watches by model: {dict(sorted(by_model.items()))}\n\n")
        f.write("This stage is acquisition only. No GMT pose/QC thresholds or authenticity classifier are used.\n")
    return report


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--pool", type=Path, default=DEFAULT_POOL)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--max-images", type=int, default=12)
    a = ap.parse_args(argv)
    report = run(a.pool, a.out, a.max_images)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
