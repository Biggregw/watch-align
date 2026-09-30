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
import re
import shutil
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urljoin, urlparse

from PIL import Image, ImageOps

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from harvester.config import REPO_ROOT  # noqa: E402
from harvester.families import SUBMARINER_12, family_for_model  # noqa: E402
from harvester.http import FetchError, Http  # noqa: E402
from harvester.resolvers import ImageRef, imgur_album, page_images  # noqa: E402

DEFAULT_POOL = REPO_ROOT / "docs" / "research" / "submariner_candidate_sources_2026-09-30.csv"
# Supplemental pools are added with further --pool arguments (the workflow passes every
# docs/research/submariner_*sources*.csv / *topup*.csv it finds). Both schemas are accepted:
#   candidate_id,class,model,factory,source_type,source_name,source_url,image_album_url,provenance_note,candidate_status
#   physical_watch_id,family,model,class_tag,source_type,source_name,source_url,provenance_note,candidate_status
DEFAULT_OUT = REPO_ROOT / "datasets" / "submariner_research"
IMAGE_EXTS = {"JPEG": ".jpg", "PNG": ".png", "WEBP": ".webp"}


CLASS_ALIASES = {"gen": "gen", "genuine": "gen", "rep": "rep", "replica": "rep"}


def normalize_class(value: str) -> str:
    """gen / rep, the vocabulary every harvester and research tool uses; "" when unknown."""
    return CLASS_ALIASES.get((value or "").strip().lower(), "")


def physical_watch_id(row: dict) -> str:
    """Explicit physical-watch identity: the pool's physical_watch_id column, else the candidate id
    (one candidate = one physical watch in the phase-1 pool)."""
    return (row.get("physical_watch_id") or "").strip() or row["candidate_id"]


def listing_id(row: dict) -> str:
    """The dealer's own id for this one watch (SKU / product code), or "" for albums and forums.
    Taken from an explicit listing_id column, the provenance note, or the dealer URL/candidate id."""
    if (row.get("listing_id") or "").strip():
        return row["listing_id"].strip()
    if (row.get("image_album_url") or "").strip() or "dealer" not in (row.get("source_type") or ""):
        return ""
    m = re.search(r"(?:SKU|product code)\s*(\d{4,})", row.get("provenance_note") or "", re.I)
    if m:
        return m.group(1)
    m = re.search(r"/(\d{5,})/?$", row.get("source_url") or "")
    if m:
        return m.group(1)
    m = re.search(r"_(\d{5,})$", row.get("candidate_id") or "")
    return m.group(1) if m else ""


IMG_URL = re.compile(r"""(?:https?:)?//[^\s"'<>()]+|/[^\s"'<>()]+""")


def listing_image_urls(html: str, base: str, lid: str) -> list[str]:
    """Image URLs in the page that belong to listing `lid`: the id must appear in the image's own
    path. Site navigation images and other listings' thumbnails ("related watches") are excluded,
    because they are photographs of OTHER watches or of nothing."""
    out, seen = [], set()
    for raw in IMG_URL.findall(html.replace("\\/", "/")):
        u = urljoin(base, raw.replace("&amp;", "&"))
        path = urlparse(u).path
        if not re.search(r"\.(jpe?g|png|webp)$", path, re.I) or lid not in path:
            continue
        # Resizer front-ends (e.g. /cdn-cgi/image/width=.../images/x.jpg) point at the same file.
        m = re.search(r"/cdn-cgi/image/[^/]+(/.+)$", path)
        if m:
            u = urljoin(base, m.group(1))
        key = urlparse(u).path.lower()
        if key not in seen:
            seen.add(key)
            out.append(u)
    return out


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
    physical_watch_id: str
    family: str
    class_source_value: str
    listing_id: str
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


def normalize_pool_row(r: dict, pool_name: str) -> dict:
    """One schema for every pool file: candidate_id (falls back to physical_watch_id), class (falls back
    to class_tag), physical_watch_id, and the pool it came from."""
    out = dict(r)
    out["candidate_id"] = (r.get("candidate_id") or r.get("physical_watch_id") or "").strip()
    out["physical_watch_id"] = (r.get("physical_watch_id") or "").strip() or out["candidate_id"]
    out["class"] = (r.get("class") or r.get("class_tag") or r.get("class_label") or "").strip()
    out["pool"] = pool_name
    return out


def load_pools(pools: list[Path]) -> tuple[list[dict], list[dict]]:
    """Rows from every pool, with duplicates removed. A second row for the same candidate id, or for the
    same dealer listing (source name + listing id) under a different id, is NOT a second watch: it is
    returned in `skipped` with a reason and never acquired."""
    keep, skipped, ids, listings = [], [], set(), {}
    for pool in pools:
        for r in rows(pool):
            r = normalize_pool_row(r, pool.name)
            if not r["candidate_id"]:
                continue
            lid = listing_id(r)
            lkey = ((r.get("source_name") or "").strip().lower(), lid) if lid else None
            if r["candidate_id"] in ids:
                skipped.append(dict(r, skip_reason="duplicate_candidate_id"))
                continue
            if lkey and lkey in listings:
                skipped.append(dict(r, skip_reason=f"duplicate_listing_of:{listings[lkey]}"))
                continue
            ids.add(r["candidate_id"])
            if lkey:
                listings[lkey] = r["candidate_id"]
            keep.append(r)
    return keep, skipped


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
        lid = listing_id(row)
        if lid:
            # Dealer listing: only images whose own path carries this listing's id. A generic page
            # scrape also returns site navigation images and OTHER watches' thumbnails.
            r = http.get(source)
            if not r.content.strip():
                # e.g. HTTP 202 with an empty body: a bot challenge. Recorded, never worked around.
                raise FetchError(f"empty_page_http_{r.status}", source, retryable=False)
            urls = listing_image_urls(r.text, r.url, lid)
            urls += [ref.url for ref in page_images(http, source, max_images * 4) if ref.url and lid in urlparse(ref.url).path]
            seen, keep = set(), []
            for u in urls:
                k = urlparse(u).path.lower()
                if k not in seen:
                    seen.add(k)
                    keep.append(ImageRef(url=u))
            return keep[:max_images]
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


def run(pool, out: Path, max_images: int = 12) -> dict:
    pools = [Path(p) for p in (pool if isinstance(pool, (list, tuple)) else [pool])]
    out.mkdir(parents=True, exist_ok=True)
    images_dir = out / "images"
    work = out / "work"
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True, exist_ok=True)
    http = Http()

    source_rows, skipped_rows = load_pools(pools)
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
                class_label=normalize_class(row.get("class", "")),
                model=row.get("model", ""),
                factory=row.get("factory", ""),
                source_type=row.get("source_type", ""),
                source_name=row.get("source_name", ""),
                source_url=row.get("source_url", ""),
                image_album_url=row.get("image_album_url", ""),
                provenance_note=row.get("provenance_note", ""),
                physical_watch_id=physical_watch_id(row),
                family=(family_for_model(row.get("model", "")).key if family_for_model(row.get("model", "")) else ""),
                class_source_value=row.get("class", ""),
                listing_id=listing_id(row),
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
            by_class[normalize_class(row.get("class", "")) or "unknown"] += 1
            if status != "resolved":
                status = "partial"
        else:
            counts["candidates_without_images"] += 1
        summary.append({
            "candidate_id": cid,
            "physical_watch_id": physical_watch_id(row),
            "family": SUBMARINER_12.key,
            "class_label": normalize_class(row.get("class", "")),
            "class": row.get("class", ""),
            "listing_id": listing_id(row),
            "model": row.get("model", ""),
            "factory": row.get("factory", ""),
            "source_type": row.get("source_type", ""),
            "source_url": row.get("source_url", ""),
            "image_album_url": row.get("image_album_url", ""),
            "images_acquired": good,
            "acquisition_status": status,
            "acquisition_note": note,
            "pool": row.get("pool", ""),
        })

    write_csv(out / "acquired_images.csv", list(Acquired.__dataclass_fields__), [a.__dict__ for a in acquired])
    write_csv(out / "candidate_summary.csv",
              ["candidate_id", "physical_watch_id", "family", "class_label", "class", "listing_id", "model", "factory", "source_type", "source_url", "image_album_url",
               "images_acquired", "acquisition_status", "acquisition_note", "pool"], summary)

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
        "pools": [p.name for p in pools],
        "acquisition_status_counts": dict(sorted(Counter(r["acquisition_status"] for r in summary).items())),
        "skipped_pool_rows": [{"candidate_id": r["candidate_id"], "pool": r["pool"], "reason": r["skip_reason"]} for r in skipped_rows],
        "errors": errors,
        "note": "Acquisition only. No GMT pose, GMT QC, Submariner geometry or authenticity decision has been run.",
    }
    (out / "acquisition_report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    with (out / "acquisition_report.md").open("w", encoding="utf-8") as f:
        f.write("# Submariner source-pool acquisition\n\n")
        f.write(f"- Pools: {', '.join(p.name for p in pools)}\n")
        f.write(f"- Candidates in phase-1 pools: {len(selected)} (skipped duplicates: {len(skipped_rows)})\n")
        f.write(f"- Acquisition status: {report['acquisition_status_counts']}\n")
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
    ap.add_argument("--pool", type=Path, action="append", help="pool CSV; repeat for supplemental pools")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--max-images", type=int, default=12)
    a = ap.parse_args(argv)
    report = run(a.pool or [DEFAULT_POOL], a.out, a.max_images)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
