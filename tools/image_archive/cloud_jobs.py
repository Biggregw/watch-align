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
from . import review as R
from .unreviewed import backup, candidates, image_extension, provenance, public_record, summary

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
    pin = env.get("ONEDRIVE_ACCOUNT_SHA256") or ""
    if pin and not re.fullmatch(r"[0-9a-f]{64}", pin.strip().lower()):
        raise RuntimeError("ONEDRIVE_ACCOUNT_SHA256 must be a SHA-256 hex digest")
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
    return OneDriveBackend(token_provider(application, cache, persist, pin or None), authorized=True)


def configured(environ=None):
    env = environ if environ is not None else os.environ
    return all(env.get(n) for n in ("ONEDRIVE_CLIENT_ID", "ONEDRIVE_MSAL_CACHE", "ONEDRIVE_CACHE_WRITE_TOKEN"))


def manifest_keys(drive):
    return sorted({MANIFEST_PREFIX + "/" + name for name, is_file, _ in drive.list_children(MANIFEST_PREFIX)
                   if is_file and re.fullmatch(r"[A-Za-z0-9_.-]+\.json", name)})


def remote_objects(drive):
    """Every original actually stored: sha256 -> (key, size). Foreign or malformed names fail closed."""
    found = {}
    for prefix, is_dir, _ in drive.list_children("unreviewed/objects"):
        if is_dir or not re.fullmatch(r"[0-9a-f]{2}", prefix):
            raise ValueError("unexpected entry in the originals folder")
        for name, is_file, size in drive.list_children("unreviewed/objects/" + prefix):
            key = f"unreviewed/objects/{prefix}/{name}"
            match = OBJECT_KEY.fullmatch(key)
            if not is_file or not match or match.group(1)[:2] != prefix:
                raise ValueError("unexpected original-photo object name")
            found[match.group(1)] = (key, size)
    return found


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


# ----------------------------------------------------------------------------------- reconciliation

def reconcile(drive, repo, *, repair=False, job_id=None):
    """Compare stored originals with the manifests. Repair writes a manifest for verified orphans only.

    An interrupted upload can leave originals stored but not yet listed in a manifest (the manifest is
    written every 20 photos). Each orphan is downloaded, re-hashed and format-checked before it is listed;
    a corrupt orphan is reported, never listed or deleted.
    """
    indexed = remote_index(drive)
    stored = remote_objects(drive)
    orphans = sorted(set(stored) - set(indexed))
    missing = sorted(d for d in indexed if d not in stored)
    size_mismatch = sorted(d for d in indexed if d in stored and stored[d][1] and stored[d][1] != indexed[d]["size"])
    report = {"indexed_originals": len(indexed), "stored_originals": len(stored),
              "orphans": len(orphans), "missing_objects": len(missing), "size_mismatch": len(size_mismatch),
              "missing_sha256": missing, "size_mismatch_sha256": size_mismatch, "calibration_approved": False}
    if not repair or not orphans:
        report["repaired"] = 0
        return report
    sources = provenance(Path(repo))
    items, corrupt = [], []
    for digest in orphans:
        key, size = stored[digest]
        raw = drive.get(key)
        ext = image_extension(raw)
        if sha256(raw).hexdigest() != digest or ext != OBJECT_KEY.fullmatch(key).group(2):
            corrupt.append(digest)
            continue
        rows = sources.get(digest, [])
        items.append({"sha256": digest, "key": key, "size": len(raw), "format": ext,
                      "review_status": "unreviewed", "calibration_approved": False,
                      "physical_watch_identity_conflict": len({r["physical_watch_id"] for r in rows
                                                               if r["physical_watch_id"]}) > 1,
                      "associations": rows, "recovered_by": "reconcile"})
    keys = []
    job_id = job_id or datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-recovered"
    for n in range(0, len(items), 20):
        key = f"{MANIFEST_PREFIX}/{job_id}-{n // 20:04d}.json"
        drive.put(key, json.dumps({"schema_version": 1, "purpose": SCHEMA_PURPOSE, "calibration_approved": False,
                                   "review_status": "unreviewed", "items": items[n:n + 20]},
                                  sort_keys=True, ensure_ascii=False).encode("utf-8"))
        keys.append(key)
    report.update({"repaired": len(items), "corrupt_orphans": corrupt, "recovery_manifests": keys})
    return report


def incremental_backup(drive, records):
    """Upload only originals not already stored; list stored-but-unindexed ones after verification.

    Re-running after an interruption costs one folder listing instead of re-downloading every photo.
    """
    stored = remote_objects(drive)
    indexed = remote_index(drive)
    upload, list_only, skipped = [], [], 0
    for r in records:
        if r["sha256"] in indexed and r["sha256"] in stored:
            skipped += 1
        elif r["sha256"] in stored:
            if stored[r["sha256"]][1] not in (0, r["size"]):
                raise ValueError("a stored original differs in size from the recovered bytes; not overwritten")
            list_only.append(r)
        else:
            upload.append(r)
    # backup() re-verifies bytes for list_only items too (put() of identical bytes is a no-op check).
    result = backup(drive, upload + list_only) if (upload or list_only) else {"manifest_keys": []}
    return {"already_archived": skipped, "uploaded": len(upload), "indexed_existing": len(list_only),
            "manifest_keys": result["manifest_keys"], "review_status": "unreviewed",
            "calibration_approved": False}


# ----------------------------------------------------------------------------------- review

def repo_paths(repo):
    base = Path(repo) / "archive"
    return {"batches": base / "batches", "decisions": base / "decisions", "results": base / "results",
            "evidence": base / "approved-evidence.json", "requests": base / "requests"}


def create_review_batch(drive, repo, *, size=20, family_filter=None, local_copy=None, now=None,
                        exclude_selection=None, expected_remaining=None):
    """Create private viewing copies; optionally skip every photo in a prior suitability selection.

    The selection contains SHA-256 hashes of *all* previously screened originals, regardless
    of their outcome. It is not calibration approval. Fail closed if any selected hash is
    absent from the live manifest or if the remaining archive size differs from the expected
    count. All checks occur before any viewing copies are written.
    """
    paths = repo_paths(repo)
    batches = R.load_batches(paths["batches"]) if paths["batches"].exists() else {}
    decisions = R.load_decisions(paths["decisions"], batches) if paths["decisions"].exists() else []
    reviewed, waiting = R.reviewed_and_batched(batches, R.ledger(decisions), now=now)
    indexed = remote_index(drive)
    summary = {"indexed_originals": len(indexed)}
    if exclude_selection is not None:
        from .suitable import load_selection, validate_selection
        doc, _ = load_selection(exclude_selection, repo)
        screening, _ = validate_selection(doc)
        missing = set(screening) - set(indexed)
        if missing:
            raise ValueError(f"previously screened selection has {len(missing)} hashes absent from live archive")
        indexed = {digest: item for digest, item in indexed.items() if digest not in screening}
        summary["excluded_previously_screened"] = len(screening)
        summary["remaining_after_selection"] = len(indexed)
    if expected_remaining is not None and len(indexed) != expected_remaining:
        raise ValueError(f"expected {expected_remaining} remaining originals; found {len(indexed)}; no batch created")
    entries = R.batch_entries(indexed, reviewed, waiting, size=size, family_filter=family_filter)
    if not entries:
        return {"review_batch": None, "review_photos": 0, "reason": "no unreviewed originals left", **summary}
    batch = R.batch_document(R.new_batch_id(now), entries)
    files = R.build_batch_files(batch, drive.get)
    for rel, data in files.items():
        drive.put(f"review/batches/{batch['batch_id']}/{rel}", data)
        if local_copy:
            out = Path(local_copy) / batch["batch_id"] / rel
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(data)
    paths["batches"].mkdir(parents=True, exist_ok=True)
    (paths["batches"] / f"{batch['batch_id']}.json").write_text(
        json.dumps(batch, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return {"review_batch": batch["batch_id"], "review_photos": len(entries),
            "onedrive_folder": batch["onedrive_folder"], "calibration_approved": False, **summary}


def ingest_decisions(repo, drive=None):
    """Validate every decision file, rebuild approved evidence, and mirror both to OneDrive when connected."""
    paths = repo_paths(repo)
    batches = R.load_batches(paths["batches"]) if paths["batches"].exists() else {}
    decisions = R.load_decisions(paths["decisions"], batches) if paths["decisions"].exists() else []
    effective = R.ledger(decisions)
    evidence = R.approved_evidence(effective, batches)
    paths["evidence"].parent.mkdir(parents=True, exist_ok=True)
    paths["evidence"].write_text(json.dumps(evidence, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    mirrored = 0
    if drive is not None:
        for name, doc in decisions:
            drive.put(f"review/decisions/{name}", json.dumps(doc, indent=2, sort_keys=True).encode())
            mirrored += 1
        payload = paths["evidence"].read_bytes()
        drive.put(f"review/approved/approved-evidence-{sha256(payload).hexdigest()[:16]}.json", payload)
    return {"decision_files": len(decisions), "reviewed_photos": len(effective),
            "approved_photos": evidence["approved_photos"], "withheld": evidence["withheld"],
            "mirrored_to_onedrive": mirrored}


# ----------------------------------------------------------------------------------- credentials

def keepalive(drive, environ=None, session=None):
    """Use the refresh token (persisting the rotated cache) and report the cache-writer token's expiry."""
    import requests
    env = environ if environ is not None else os.environ
    drive._root()
    report = {"microsoft_connection": "renewed_and_saved"}
    response = (session or requests).get("https://api.github.com/repos/" + env.get("GITHUB_REPOSITORY", ""),
                                         headers={"Authorization": "Bearer " + env.get("ONEDRIVE_CACHE_WRITE_TOKEN", ""),
                                                  "Accept": "application/vnd.github+json"}, timeout=30)
    expiry = response.headers.get("github-authentication-token-expiration")
    report["cache_writer_token"] = "valid" if response.status_code == 200 else f"http_{response.status_code}"
    if expiry:
        try:
            when = datetime.strptime(expiry.replace(" UTC", " +0000"), "%Y-%m-%d %H:%M:%S %z")
            days = (when - datetime.now(timezone.utc)).days
            report["cache_writer_token_days_left"] = days
            report["cache_writer_token_warning"] = days < 30
        except ValueError:
            report["cache_writer_token_days_left"] = None
    return report


def reauthorize(environ=None, *, device_login=None, writer=None, display=None):
    """Renew consent from a phone when the refresh token has lapsed (no Codespace needed).

    The Microsoft code appears in the Actions log; anyone could read it, so the new cache is saved ONLY if the
    signed-in account matches the configured owner pin (ONEDRIVE_ACCOUNT_SHA256). Scope stays Files.ReadWrite.AppFolder.
    """
    from . import auth
    env = environ if environ is not None else os.environ
    pin = (env.get("ONEDRIVE_ACCOUNT_SHA256") or "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", pin):
        raise RuntimeError("set the ONEDRIVE_ACCOUNT_SHA256 variable before re-authorising from CI")
    if not (env.get("ONEDRIVE_CLIENT_ID") and env.get("ONEDRIVE_CACHE_WRITE_TOKEN") and env.get("GITHUB_REPOSITORY")):
        raise RuntimeError("protected OneDrive environment has not been configured")
    application, cache = client(env["ONEDRIVE_CLIENT_ID"])
    def persist(serialized):
        old = os.environ.get("GH_TOKEN")
        try:
            os.environ["GH_TOKEN"] = env["ONEDRIVE_CACHE_WRITE_TOKEN"]
            (writer or github_cache_writer(env["GITHUB_REPOSITORY"]))(serialized)
        finally:
            if old is None:
                os.environ.pop("GH_TOKEN", None)
            else:
                os.environ["GH_TOKEN"] = old
    import sys
    show = display or (lambda text: print(text, file=sys.stderr, flush=True))  # live in the log, not the report
    (device_login or auth.device_login)(application, cache, persist, show, expected_fingerprint=pin)
    return {"microsoft_connection": "reauthorised_and_saved", "scope": "Files.ReadWrite.AppFolder"}


# ----------------------------------------------------------------------------------- requests

TASKS = ("verify", "reconcile", "recover", "review_batch", "keepalive", "ingest", "reauthorize", "suitable_copy")
SELECTION_PATH = re.compile(r"^archive/selections/sel-[0-9]{8}-[a-z0-9][a-z0-9-]{0,40}\.json$")
REQUEST_NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.-]{0,80}\.json$")


def parse_request(path):
    path = Path(path)
    if not REQUEST_NAME.fullmatch(path.name):
        raise ValueError("request file name must be letters, digits, '.', '_' or '-' ending in .json")
    doc = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(doc, dict) or doc.get("task") not in TASKS:
        raise ValueError(f"request task must be one of {TASKS}")
    options = doc.get("options") or {}
    allowed = {"verify": {"max_photos"}, "reconcile": {"repair"}, "keepalive": set(), "ingest": set(),
               "reauthorize": set(), "suitable_copy": {"selection", "dry_run"},
               "recover": {"sources", "confirm_no_other_transfer", "limit"},
               "review_batch": {"size", "family", "exclude_selection", "expected_remaining"}}[doc["task"]]
    if set(options) - allowed:
        raise ValueError(f"unsupported options for {doc['task']}: {sorted(set(options) - allowed)}")
    if doc["task"] == "recover":
        if options.get("confirm_no_other_transfer") is not True:
            raise ValueError("recover needs options.confirm_no_other_transfer=true (no other upload may be running)")
        for src in options.get("sources") or []:
            if not (isinstance(src, dict) and re.fullmatch(r"\d{5,20}", str(src.get("run_id", "")))
                    and re.fullmatch(r"[A-Za-z0-9_.-]{1,120}", str(src.get("artifact", "")))):
                raise ValueError("recover sources must be {run_id: digits, artifact: name}")
    if doc["task"] == "suitable_copy":
        if not SELECTION_PATH.fullmatch(str(options.get("selection", ""))):
            raise ValueError("suitable_copy needs options.selection = archive/selections/sel-YYYYMMDD-<name>.json")
        if not isinstance(options.get("dry_run", False), bool):
            raise ValueError("dry_run must be true or false")
    if doc["task"] == "review_batch":
        if not 1 <= int(options.get("size", 20)) <= 60:
            raise ValueError("review_batch size must be 1..60")
        if options.get("family") not in (None, *R.FAMILY_HOURS):
            raise ValueError("unknown family")
        exclusion = options.get("exclude_selection")
        if exclusion is not None and not SELECTION_PATH.fullmatch(str(exclusion)):
            raise ValueError("review_batch exclude_selection must be a safe archive/selections/ path")
        remaining = options.get("expected_remaining")
        if remaining is not None:
            if exclusion is None or type(remaining) is not int or not 0 <= remaining <= 2000:
                raise ValueError("expected_remaining requires exclude_selection and an integer from 0 to 2000")
    return doc["task"], options


def run_request(task, options, repo, drive, *, scan=(), local_copy=None):
    if task == "verify":
        return {**verify_remote(drive, max_photos=options.get("max_photos")),
                **{k: v for k, v in reconcile(drive, repo).items() if k != "calibration_approved"}}
    if task == "reconcile":
        return reconcile(drive, repo, repair=bool(options.get("repair")))
    if task == "recover":
        records = candidates(repo, scan)
        if options.get("limit"):
            records = records[:int(options["limit"])]
        return {**summary(records), **incremental_backup(drive, records)}
    if task == "review_batch":
        return create_review_batch(drive, repo, size=int(options.get("size", 20)),
                                   family_filter=options.get("family"), local_copy=local_copy,
                                   exclude_selection=options.get("exclude_selection"),
                                   expected_remaining=options.get("expected_remaining"))
    if task == "keepalive":
        return keepalive(drive)
    if task == "suitable_copy":
        from .suitable import apply
        return apply(drive, repo, options["selection"], dry_run=bool(options.get("dry_run")))
    return ingest_decisions(repo, drive)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("action", choices=("preflight", "pilot", "upload", "verify", "review", "request",
                                       "validate-decisions"))
    ap.add_argument("--request", type=Path, action="append", default=[], help="archive/requests/*.json to run")
    ap.add_argument("--results", type=Path, help="Write each request result here (default archive/results)")
    ap.add_argument("--local-copy", type=Path, help="Also keep review-batch files here (for an encrypted artifact)")
    ap.add_argument("--repo", type=Path, default=Path.cwd())
    ap.add_argument("--scan", type=Path, action="append", default=[])
    ap.add_argument("--limit", type=int, default=3)
    ap.add_argument("--offset", type=int, default=0, help="Skip N catalogued-first originals for review batches")
    ap.add_argument("--output", type=Path, default=Path("/tmp/watch-align-review.zip"))
    args = ap.parse_args(argv)
    if args.action == "validate-decisions":
        report = {"action": "validate-decisions", **ingest_decisions(args.repo)}
        print(json.dumps(report, indent=2))
        return report
    if args.action == "request":
        return run_requests(args)
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


def run_requests(args):
    """Run each pending request; write its result (counts and hashes only) for the repository."""
    results_dir = args.results or repo_paths(args.repo)["results"]
    results_dir.mkdir(parents=True, exist_ok=True)
    drive = None
    reports = []
    for path in args.request:
        out = results_dir / path.name
        if out.exists():
            reports.append({"request": path.name, "status": "already_done"})
            continue
        started = datetime.now(timezone.utc).isoformat(timespec="seconds")
        try:
            task, options = parse_request(path)
            if task == "reauthorize":
                result, status = reauthorize(), "done"
            elif task == "ingest" and not configured():
                result = ingest_decisions(args.repo)
                status = "done_without_onedrive"
            elif not configured():
                result, status = {}, "not_configured"
            else:
                drive = drive or drive_from_protected_environment()
                result, status = run_request(task, options, args.repo, drive, scan=args.scan,
                                             local_copy=args.local_copy), "done"
                if isinstance(result, dict) and result.get("status") in ("incomplete", "refused"):
                    status = result["status"]   # some copies or the audit record failed: a new request retries
        except Exception as exc:  # report the failure class and message, never credentials
            task = locals().get("task", "unknown")
            result, status = {"error": type(exc).__name__, "message": str(exc)[:300]}, "failed"
        report = {"request": path.name, "task": task, "status": status, "started": started,
                  "finished": datetime.now(timezone.utc).isoformat(timespec="seconds"), "result": result}
        out.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        reports.append(report)
    print(json.dumps(reports, indent=2))
    if any(r.get("status") in ("failed", "incomplete", "refused") for r in reports):
        raise SystemExit(1)
    return reports


if __name__ == "__main__":
    main()
