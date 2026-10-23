"""Protected cloud operations for the owner's OneDrive unreviewed photo archive.

No automatic calibration approval, new harvesting, production changes, or uploads
unless explicitly selected. Never prints tokens, signed URLs or local credential data.
"""
from __future__ import annotations

import argparse
import csv
from datetime import datetime, timezone
from hashlib import sha256
import io
import json
import os
from pathlib import Path
import re
import tempfile
import zipfile

from .auth import client, github_cache_writer, token_provider
from .inventory import read_original
from .onedrive import GRAPH, OneDriveBackend
from .unreviewed import backup, candidates, image_extension, public_record, summary

OBJECT_KEY = re.compile(r"^unreviewed/objects/[0-9a-f]{2}/([0-9a-f]{64})\.(jpg|png|webp|tif)$")
MANIFEST_PREFIX = "unreviewed/manifests/runs"
SCHEMA_PURPOSE = "unaltered_original_backup_only"


def drive_from_protected_environment(environ=None):
    env = environ if environ is not None else os.environ
    missing = [name for name in ("ONEDRIVE_CLIENT_ID", "ONEDRIVE_MSAL_CACHE",
                                 "ONEDRIVE_CACHE_WRITE_TOKEN", "GITHUB_REPOSITORY")
               if not env.get(name)]
    if missing:
        raise RuntimeError("protected OneDrive environment has not been configured")
    if not env.get("CI"):
        raise RuntimeError("remote cloud operations are restricted to protected CI")
    application, cache = client(env["ONEDRIVE_CLIENT_ID"], env["ONEDRIVE_MSAL_CACHE"])
    # GitHub CLI uses the narrowly scoped secret-writer credential only to update
    # an encrypted environment secret when MSAL refreshes; never print it.
    def persist(serialized):
        old = os.environ.get("GH_TOKEN")
        try:
            os.environ["GH_TOKEN"] = env["ONEDRIVE_CACHE_WRITE_TOKEN"]
            github_cache_writer(env["GITHUB_REPOSITORY"])(serialized)
        finally:
            if old is None:
                os.environ.pop("GH_TOKEN", None)
            else:
                os.environ["GH_TOKEN"] = old
    return OneDriveBackend(token_provider(application, cache, persist), authorized=True)


def manifest_keys(drive):
    url = drive._item(MANIFEST_PREFIX) + ":/children"
    result = []
    while url:
        response = drive._request("GET", url, allowed=(200, 404))
        if response.status_code == 404:
            break
        body = response.json()
        for entry in body.get("value", []):
            name = entry.get("name", "")
            if "file" in entry and re.fullmatch(r"[A-Za-z0-9_.-]+\.json", name):
                result.append(MANIFEST_PREFIX + "/" + name)
        url = body.get("@odata.nextLink")
        if url and not url.startswith(GRAPH + "/"):
            raise RuntimeError("unexpected OneDrive pagination host")
    return sorted(set(result))


def remote_index(drive):
    """Independently verify the manifest format, not image suitability."""
    photos = {}
    conflicts = set()
    for key in manifest_keys(drive):
        manifest = json.loads(drive.get(key))
        if (manifest.get("schema_version") != 1 or
                manifest.get("purpose") != SCHEMA_PURPOSE or
                manifest.get("calibration_approved") is not False or
                manifest.get("review_status") != "unreviewed"):
            raise ValueError("remote unreviewed manifest has unexpected schema")
        for item in manifest.get("items", []):
            match = OBJECT_KEY.fullmatch(str(item.get("key", "")))
            if not match or match.group(1) != item.get("sha256") or match.group(2) != item.get("format"):
                raise ValueError("unsafe or mismatched original-photo key")
            if item.get("calibration_approved") is not False or item.get("review_status") != "unreviewed":
                raise ValueError("unreviewed manifest may not claim calibration approval")
            digest = item["sha256"]
            if digest in photos:
                before = photos[digest]
                if before["key"] != item["key"] or before["size"] != item["size"]:
                    conflicts.add(digest)
                # Prefer the earliest record, never silently merge inconsistent metadata.
            else:
                photos[digest] = item
    if conflicts:
        raise ValueError("conflicting copies in remote manifest")
    return photos


def verify_remote(drive, *, max_photos=None):
    indexed = remote_index(drive)
    selected = list(sorted(indexed.items()))
    if max_photos is not None:
        selected = selected[:max_photos]
    verified = 0
    for digest, item in selected:
        payload = drive.get(item["key"])
        if sha256(payload).hexdigest() != digest or len(payload) != item["size"]:
            raise ValueError("OneDrive original byte verification failed")
        verified += 1
    return {"indexed_originals": len(indexed), "checked_originals": verified,
            "all_indexed_verified": bool(indexed) and verified == len(indexed),
            "calibration_approved": False}


def export_review_batch(drive, destination, *, limit=20, offset=0, max_bytes=22_000_000):
    """Retrieve existing originals into one bounded review ZIP, without changing OneDrive."""
    if not 1 <= limit <= 100 or offset < 0 or not 1_000_000 <= max_bytes <= 28_000_000:
        raise ValueError("review batch limits outside supported range")
    photos = remote_index(drive)
    ordered = sorted(photos.values(), key=lambda x: (not bool(x.get("associations")), x["sha256"]))[offset:]
    chosen = []
    consumed = 0
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Avoid mixing partial/corrupt output with a successful artifact.
    with tempfile.NamedTemporaryFile(dir=destination.parent, suffix=".zip", delete=False) as temp:
        tmp = Path(temp.name)
    try:
        with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_STORED) as archive:
            for item in ordered:
                if len(chosen) >= limit:
                    break
                size = int(item["size"])
                if size > max_bytes or consumed + size > max_bytes:
                    continue
                payload = drive.get(item["key"])
                if sha256(payload).hexdigest() != item["sha256"] or len(payload) != size:
                    raise ValueError("remote original corrupt: abort review export")
                archive.writestr(f"originals/{item['sha256']}.{item['format']}", payload)
                consumed += size
                chosen.append({k: v for k, v in item.items() if k != "local_location"})
            archive.writestr("catalogue.json", json.dumps({
                "schema_version": 1, "purpose": "manual_visual_review",
                "calibration_approved": False,
                "images": chosen}, indent=2, ensure_ascii=False))
            columns = ["sha256", "format", "size", "review_status", "calibration_approved",
                       "model", "physical_watch_id", "source_url", "provenance"]
            output = io.StringIO()
            writer = csv.DictWriter(output, fieldnames=columns)
            writer.writeheader()
            for item in chosen:
                assoc = item.get("associations") or [{}]
                for ref in assoc:
                    writer.writerow({**{k: item.get(k, "") for k in columns}, **{
                        k: ref.get(k, "") for k in ("model", "physical_watch_id", "source_url", "provenance")}})
            archive.writestr("catalogue.csv", output.getvalue())
        os.replace(tmp, destination)
    finally:
        tmp.unlink(missing_ok=True)
    return {"review_photos": len(chosen), "review_bytes": consumed, "review_offset": offset,
            "output": str(destination), "calibration_approved": False}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("action", choices=("preflight", "pilot", "upload", "verify", "review"))
    ap.add_argument("--repo", type=Path, default=Path.cwd())
    ap.add_argument("--scan", type=Path, action="append", default=[])
    ap.add_argument("--limit", type=int, default=3)
    ap.add_argument("--offset", type=int, default=0, help="Skip N catalogued-first originals for review batches")
    ap.add_argument("--output", type=Path, default=Path("/tmp/watch-align-review.zip"))
    args = ap.parse_args(argv)
    if args.action in ("preflight", "pilot", "upload"):
        for root in args.scan:
            if not root.exists():
                ap.error("photo recovery root missing")
        records = candidates(args.repo, args.scan)
        if args.action == "preflight":
            report = {"action": args.action, **summary(records)}
            print(json.dumps(report, indent=2))
            return report
        if args.action == "pilot":
            if not 1 <= args.limit <= 10:
                ap.error("pilot limit must be 1..10")
            records = records[:args.limit]
        drive = drive_from_protected_environment()
        report = {"action": args.action, **summary(records),
                  **backup(drive, records)}
    elif args.action == "verify":
        drive = drive_from_protected_environment()
        report = {"action": "verify", **verify_remote(drive)}
    else:
        drive = drive_from_protected_environment()
        report = {"action": "review", **export_review_batch(
            drive, args.output, limit=args.limit, offset=args.offset)}
    print(json.dumps(report, indent=2))
    return report


if __name__ == "__main__":
    main()
