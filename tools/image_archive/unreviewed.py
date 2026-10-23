"""Opt-in original-photo *backup*, not calibration admission.

Unreviewed originals are deliberately separate from Archive's validated objects.
The remote manifest is a portable inventory; no file is approved or rejected for QC.
No unattended network access, harvesting, data deletion, or credential logging.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime, timezone
from hashlib import sha256
import json
from pathlib import Path
import re
import uuid

from .auth import client, external_cache_writer, token_provider
from .inventory import available_originals, catalogue_rows, read_original
from .onedrive import OneDriveBackend

HASH = re.compile(r"^[a-f0-9]{64}$")
PREFIX = "unreviewed"
MAX_BYTES = 80_000_000


def image_extension(data):
    """Recognise original image formats by magic bytes, not untrusted filenames."""
    if data.startswith(b"\xff\xd8\xff"):
        return "jpg"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if data.startswith(b"RIFF") and data[8:12] == b"WEBP":
        return "webp"
    if data.startswith((b"II*\x00", b"MM\x00*")):
        return "tif"
    return None


def provenance(repo):
    associations = defaultdict(list)
    for dataset, row in catalogue_rows(repo):
        digest = (row.get("sha256") or "").strip().lower()
        if not HASH.fullmatch(digest):
            continue
        record = {
            "dataset": dataset,
            "model": row.get("model") or row.get("family") or "unknown",
            "physical_watch_id": row.get("physical_watch_id") or "",
            "provenance": row.get("provenance") or "unknown",
            "source_url": row.get("source_url") or row.get("url") or "",
            "source_name": row.get("source_name") or row.get("source") or "",
        }
        if record not in associations[digest]:
            associations[digest].append(record)
    return associations


def candidates(repo, scan_paths):
    """Inventory actual bytes without making *any* photographic suitability decision."""
    repo = Path(repo).resolve()
    roots = [repo, *(Path(p).resolve() for p in scan_paths)]
    found = available_originals(roots)
    sources = provenance(repo)
    records = []
    for digest, location in sorted(found.items()):
        raw = read_original(location)
        ext = image_extension(raw)
        if ext is None or not (0 < len(raw) <= MAX_BYTES):
            continue
        if sha256(raw).hexdigest() != digest:
            raise ValueError("original SHA-256 changed while building archive plan")
        rows = sources.get(digest, [])
        # Unknown original provenance is explicit, never promoted to verified genuine.
        ids = {r["physical_watch_id"] for r in rows if r["physical_watch_id"]}
        records.append({
            "sha256": digest,
            "key": f"{PREFIX}/objects/{digest[:2]}/{digest}.{ext}",
            "size": len(raw),
            "format": ext,
            "review_status": "unreviewed",
            "calibration_approved": False,
            "physical_watch_identity_conflict": len(ids) > 1,
            "associations": rows,
            "local_location": location,
        })
    # Pilot from catalogued harvested originals first; preserve unknowns for later manual review.
    records.sort(key=lambda item: (not bool(item["associations"]), item["sha256"]))
    return records


def public_record(record):
    """Never publish local absolute paths or credential-cache locations."""
    return {k: v for k, v in record.items() if k != "local_location"}


def summary(records):
    return {
        "unique_originals": len(records),
        "with_catalogue_metadata": sum(bool(r["associations"]) for r in records),
        "uncatalogued_originals": sum(not r["associations"] for r in records),
        "identity_conflicts": sum(r["physical_watch_identity_conflict"] for r in records),
        "total_bytes": sum(r["size"] for r in records),
        "review_status": "unreviewed",
    }


def backup(drive, records, batch_size=20):
    if not 1 <= batch_size <= 100:
        raise ValueError("invalid batch size")
    batch = []
    manifest_keys = []
    job_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12]
    def flush():
        if not batch:
            return
        key = f"{PREFIX}/manifests/runs/{job_id}-{len(manifest_keys):04d}.json"
        document = {"schema_version": 1, "purpose": "unaltered_original_backup_only",
                    "calibration_approved": False, "review_status": "unreviewed",
                    "items": list(batch)}
        payload = json.dumps(document, sort_keys=True, ensure_ascii=False).encode("utf-8")
        drive.put(key, payload)  # Existing Graph backend verifies every uploaded byte.
        manifest_keys.append(key)
        batch.clear()
    for item in records:
        raw = read_original(item["local_location"])
        if sha256(raw).hexdigest() != item["sha256"] or image_extension(raw) != item["format"]:
            raise ValueError("original changed since preflight; aborting without approving it")
        drive.put(item["key"], raw)
        batch.append(public_record(item))
        if len(batch) >= batch_size:
            flush()
    flush()
    return {"uploaded_or_verified": len(records), "manifest_keys": manifest_keys,
            "review_status": "unreviewed", "calibration_approved": False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--scan", type=Path, action="append", default=[],
                        help="Additional extracted artifact/ZIP directory (repeatable)")
    parser.add_argument("--limit", type=int, help="Pilot: include at most N originals")
    parser.add_argument("--upload-unreviewed", action="store_true", help="Explicit remote-write approval")
    parser.add_argument("--authorized", action="store_true", help="Owner has authorized OneDrive backup")
    parser.add_argument("--client-id", help="Public Entra application ID")
    parser.add_argument("--cache", type=Path, help="Local MSAL cache outside the Git repository")
    args = parser.parse_args(argv)
    for scan in args.scan:
        if not scan.exists():
            parser.error(f"scan directory or ZIP does not exist: {scan}")
    records = candidates(args.repo, args.scan)
    if args.limit is not None:
        if args.limit < 1:
            parser.error("--limit must be positive")
        records = records[:args.limit]
    result = {"mode": "dry_run", **summary(records)}
    if args.upload_unreviewed:
        if not args.authorized or not args.client_id or not args.cache or not args.cache.is_file():
            parser.error("remote backup requires --authorized, --client-id and existing --cache")
        save = external_cache_writer(args.cache, args.repo)
        app, cache = client(args.client_id, args.cache.read_text(encoding="utf-8"))
        drive = OneDriveBackend(token_provider(app, cache, save), authorized=True)
        result = {**result, **backup(drive, records), "mode": "uploaded"}
    print(json.dumps(result, indent=2))
    return result


if __name__ == "__main__":
    main()
