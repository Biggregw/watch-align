"""The "Suitable Photos" collection: exact copies of reviewed, suitable archived originals.

    archive/selections/<selection_id>.json   a reviewed selection manifest (photo suitability only)
    WatchAlign/Suitable Photos/<model>/<sha256>.<ext>        exact original bytes, one copy per photo
    WatchAlign/Suitable Photos/_audit/<selection_id>/...     the selection as applied and every run's outcome

Photograph suitability is deliberately separate from hour-marker assessment: a clear, reasonably face-on
photograph is suitable even when hands cover some markers. Marker decisions belong to review decisions
(tools/image_archive/review.py), and this manifest refuses marker fields and marker-based reasons.

Safety: the archived originals under unreviewed/objects/ are only read. A copy is made only for an entry that
is explicitly "suitable", found in the verified archive index, whose source bytes re-hash to its SHA-256, and
whose destination (written through the archive's immutable put: never overwritten, read back byte for byte)
re-hashes to the same SHA-256. Re-running is a no-op for copies already made; an interrupted or partially
failed run is completed by running it again. Nothing is ever deleted, moved or re-encoded.
"""
from __future__ import annotations

from datetime import datetime, timezone
from hashlib import sha256
import json
from pathlib import Path
import re

from .review import family as catalogue_family

SCHEMA = "watch-align-suitable-selection/1"
RUN_SCHEMA = "watch-align-suitable-copy-run/1"
ROOT = "Suitable Photos"
AUDIT = ROOT + "/_audit"
SELECTION_ID = re.compile(r"^sel-[0-9]{8}-[a-z0-9][a-z0-9-]{0,40}$")
HASH = re.compile(r"^[0-9a-f]{64}$")
REVIEWERS = ("owner", "chatgpt", "claude")
SUITABILITY = ("suitable", "not_suitable", "uncertain")
# Folder per model family; "unknown" is a real, explicit answer, never guessed from other data.
FOLDERS = {
    "submariner_124060": "Submariner 124060",
    "submariner_126610": "Submariner Date 126610",
    "gmt_126710": "GMT-Master II 126710",
    "unknown": "Unknown model",
}
# Photo-level reasons only. Covered or unclear hour markers are NOT a reason a photo is unsuitable.
PHOTO_REASONS = ("oblique", "blurred", "low_resolution", "dial_cropped", "heavy_glare", "not_a_photograph",
                 "wrong_model", "watermark_over_dial", "heavily_edited", "duplicate_view", "other")
MARKER_FIELDS = ("markers", "hours", "marker", "hour_markers")
MAX_ENTRIES = 2000


class SelectionError(ValueError):
    """The selection manifest is malformed or conflicts with itself; nothing is copied."""


def _time(text):
    try:
        t = datetime.fromisoformat(str(text).replace("Z", "+00:00"))
    except ValueError:
        raise SelectionError("reviewed_at must be an ISO-8601 time") from None
    if t.tzinfo is None:
        raise SelectionError("reviewed_at needs a time zone (use Z for UTC)")
    return t


def validate_selection(doc, *, name="selection"):
    """Strict offline validation. Returns {sha256: entry} plus the duplicate-entry count."""
    if not isinstance(doc, dict) or doc.get("schema") != SCHEMA:
        raise SelectionError(f"{name}: unknown schema (expected {SCHEMA})")
    if not SELECTION_ID.fullmatch(str(doc.get("selection_id", ""))):
        raise SelectionError(f"{name}: selection_id must look like sel-YYYYMMDD-<name>")
    if doc.get("reviewer") not in REVIEWERS:
        raise SelectionError(f"{name}: reviewer must be one of {REVIEWERS}")
    _time(doc.get("reviewed_at"))
    entries = doc.get("photos")
    if not isinstance(entries, list) or not entries or len(entries) > MAX_ENTRIES:
        raise SelectionError(f"{name}: photos must be a list of 1..{MAX_ENTRIES} entries")
    chosen, duplicates = {}, 0
    for i, e in enumerate(entries):
        where = f"{name}: photos[{i}]"
        if not isinstance(e, dict):
            raise SelectionError(f"{where} is not an object")
        if any(k in e for k in MARKER_FIELDS):
            raise SelectionError(f"{where}: hour-marker assessments do not belong in a photo-suitability selection")
        digest = str(e.get("sha256", ""))
        if not HASH.fullmatch(digest):
            raise SelectionError(f"{where}: sha256 must be 64 lower-case hex characters")
        model = e.get("model")
        if model not in FOLDERS:
            raise SelectionError(f"{where}: model must be one of {sorted(FOLDERS)} (use \"unknown\" when not known)")
        suitability = e.get("suitability")
        if suitability not in SUITABILITY:
            raise SelectionError(f"{where}: suitability must be one of {SUITABILITY}")
        reasons = e.get("reasons") or []
        if not isinstance(reasons, list) or any(r not in PHOTO_REASONS for r in reasons):
            raise SelectionError(f"{where}: reasons must be photo-level reasons from {PHOTO_REASONS}; "
                                 "covered or unclear hour markers never make a photo unsuitable")
        if suitability == "suitable" and reasons:
            raise SelectionError(f"{where}: a suitable photo has no unsuitability reasons")
        if suitability == "not_suitable" and not reasons:
            raise SelectionError(f"{where}: give at least one photo-level reason for not_suitable")
        if "other" in reasons and not str(e.get("comment", "")).strip():
            raise SelectionError(f"{where}: reason 'other' needs a comment")
        # Every other field the reviewer supplied (e.g. primary-example flags, watch ids, view notes) is kept
        # verbatim in the audit: nothing is added, dropped or reinterpreted.
        clean = {**e, "sha256": digest, "model": model, "suitability": suitability, "reasons": list(reasons),
                 "comment": str(e.get("comment", ""))}
        if digest in chosen:
            before = chosen[digest]
            if (before["model"], before["suitability"]) != (model, suitability):
                raise SelectionError(f"{where}: photo {digest[:12]} is listed twice with different model or suitability")
            duplicates += 1
            continue
        chosen[digest] = clean
    return chosen, duplicates


def load_selection(path, repo):
    """Read a selection from archive/selections/ only (no paths outside the repository's selection folder)."""
    repo = Path(repo).resolve()
    folder = (repo / "archive" / "selections").resolve()
    path = (repo / path).resolve() if not Path(path).is_absolute() else Path(path).resolve()
    if path.parent != folder or path.suffix != ".json":
        raise SelectionError("selection must be a .json file directly inside archive/selections/")
    raw = path.read_bytes()
    try:
        doc = json.loads(raw)
    except json.JSONDecodeError:
        raise SelectionError(f"{path.name}: not valid JSON") from None
    if path.stem != doc.get("selection_id"):
        raise SelectionError(f"{path.name}: file name must equal its selection_id")
    return doc, raw


def destination(entry, ext):
    return f"{ROOT}/{FOLDERS[entry['model']]}/{entry['sha256']}.{ext}"


def existing_copies(drive):
    """sha256 -> [destination keys] already in the collection (any model folder)."""
    found = {}
    for folder in FOLDERS.values():
        for name, is_file, _ in drive.list_children(f"{ROOT}/{folder}"):
            m = re.fullmatch(r"([0-9a-f]{64})\.(jpg|png|webp|tif)", name)
            if is_file and m:
                found.setdefault(m.group(1), []).append(f"{ROOT}/{folder}/{name}")
    return found


def plan(selection, duplicates, index, copies):
    """Decide each entry's action without touching OneDrive. Every entry gets an explicit outcome."""
    actions = []
    for digest, e in sorted(selection.items()):
        item = index.get(digest)
        cat = sorted({f for f in (catalogue_family(a.get("model")) for a in (item or {}).get("associations") or [])
                      if f})
        rec = {**e, "catalogue_families": cat,
               # recorded from the archive index, never inferred: e.g. dealer listing or curated replica seed
               "catalogue_provenance": sorted({a.get("provenance") or "unknown"
                                               for a in (item or {}).get("associations") or []})}
        if e["suitability"] != "suitable":
            rec["action"] = "excluded"
            if digest in copies:
                rec["note"] = "already in the collection from an earlier selection; kept (nothing is ever deleted)"
                rec["existing_copies"] = copies[digest]
        elif item is None:
            rec["action"] = "held"
            rec["reason"] = "not in the verified archive index"
        elif e["model"] != "unknown" and cat and e["model"] not in cat:
            rec["action"] = "held"
            rec["reason"] = "model conflicts with the archive catalogue; correct the selection or the catalogue"
        else:
            target = destination(e, item["format"])
            other = [k for k in copies.get(digest, []) if k != target]
            if other:
                rec["action"] = "held"
                rec["reason"] = "already filed under another model folder; not copied twice"
                rec["existing_copies"] = other
            else:
                rec.update(action="copy", source=item["key"], destination=target, size=item["size"])
        actions.append(rec)
    return actions


def apply(drive, repo, selection_path, *, dry_run=False, now=None):
    """Apply one reviewed selection and return the full run audit (the request result in archive/results/).

    status is "complete" only when every planned copy is verified AND the OneDrive audit record has been written
    and read back with the same SHA-256; "dry_run" when nothing was written (sources are still re-hashed);
    "incomplete" when any copy or the audit record failed (retry with a new request: verified copies are no-ops);
    "refused" when the selection could not be recorded (for example an id re-used with different content), in
    which case nothing is copied.
    """
    from .cloud_jobs import remote_index
    from .archive import IntegrityError
    doc, raw = load_selection(selection_path, repo)
    selection, duplicates = validate_selection(doc, name=Path(selection_path).name)
    sid = doc["selection_id"]
    started = (now or datetime.now(timezone.utc)).isoformat(timespec="seconds")
    index = remote_index(drive)
    copies = existing_copies(drive)
    actions = plan(selection, duplicates, index, copies)
    audit = {"schema": RUN_SCHEMA, "selection_id": sid, "selection_sha256": sha256(raw).hexdigest(),
             "reviewer": doc["reviewer"], "reviewed_at": doc["reviewed_at"], "started": started,
             "dry_run": bool(dry_run), "duplicate_entries": duplicates, "photos": actions}
    refused = None
    if not dry_run:
        # The applied selection is immutable: re-using an id with different content is refused, and nothing is
        # copied unless the selection itself is recorded and verified.
        try:
            key = f"{AUDIT}/{sid}/selection.json"
            drive.put(key, raw)
            if drive.get(key) != raw:
                raise IntegrityError("stored selection differs from the committed selection")
            audit["selection_record"] = "written_verified"
        except IntegrityError as exc:
            refused = f"selection id {sid} already holds different content in OneDrive: {str(exc)[:120]}"
        except Exception as exc:
            refused = f"selection could not be recorded in OneDrive ({type(exc).__name__}: {str(exc)[:120]})"
        if refused:
            audit["selection_record"] = "failed"
            audit["error"] = refused
            for rec in actions:
                if rec["action"] == "copy":
                    rec["result"] = "not_attempted"
    try:
        for rec in actions:
            if rec["action"] != "copy" or refused:
                continue
            try:
                data = drive.get(rec["source"])
                if sha256(data).hexdigest() != rec["sha256"] or len(data) != rec["size"]:
                    rec["result"] = "failed"
                    rec["error"] = "source original failed SHA-256 verification; not copied"
                    continue
                if dry_run:
                    rec["result"] = "would_copy_source_verified"
                    continue
                already = rec["sha256"] in copies
                drive.put(rec["destination"], data)          # immutable: never overwrites different bytes
                check = drive.get(rec["destination"])
                if sha256(check).hexdigest() != rec["sha256"]:
                    rec["result"] = "failed"
                    rec["error"] = "destination copy failed SHA-256 verification"
                    continue
                rec["result"] = "already_present_verified" if already else "copied_verified"
            except Exception as exc:  # one photo's failure never hides the others; retry completes it
                rec["result"] = "failed"
                rec["error"] = f"{type(exc).__name__}: {str(exc)[:200]}"
    finally:
        audit["finished"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
        audit["summary"] = summarise(actions)
        pending = [r for r in actions if r["action"] == "copy"
                   and r.get("result") not in ("copied_verified", "already_present_verified",
                                               "would_copy_source_verified")]
        audit["copy_status"] = "refused" if refused else "incomplete" if pending else "complete"
        if dry_run:
            audit["audit_record"] = "not_written_dry_run"
            audit["status"] = "dry_run" if not pending else "incomplete"
        else:
            audit["audit_record"] = write_audit(drive, sid, audit)
            audit["audit_written"] = audit["audit_record"] == "written_verified"
            audit["status"] = ("refused" if refused else
                               "complete" if not pending and audit["audit_written"] else "incomplete")
    return audit


def write_audit(drive, sid, audit):
    """Write the run record to OneDrive and read it back; any failure is reported, never hidden."""
    record = {k: v for k, v in audit.items() if k not in ("status", "audit_record", "audit_written")}
    payload = json.dumps(record, indent=2, ensure_ascii=False, sort_keys=True).encode()
    stamp = audit["finished"].replace(":", "").replace("-", "")
    key = f"{AUDIT}/{sid}/run-{stamp}-{sha256(payload).hexdigest()[:8]}.json"
    try:
        drive.put(key, payload)
        if sha256(drive.get(key)).hexdigest() != sha256(payload).hexdigest():
            return f"failed: {key} read back with different bytes"
        audit["audit_key"] = key
        return "written_verified"
    except Exception as exc:
        return f"failed: {type(exc).__name__}: {str(exc)[:160]}"


def summarise(actions):
    s = {"entries": len(actions), "suitable": 0, "excluded": 0, "held": 0, "copied": 0, "already_present": 0,
         "failed": 0, "would_copy": 0, "by_model": {}}
    for r in actions:
        if r["suitability"] == "suitable":
            s["suitable"] += 1
        if r["action"] in ("excluded", "held"):
            s[r["action"]] += 1
        res = r.get("result")
        if res == "copied_verified":
            s["copied"] += 1
        elif res == "already_present_verified":
            s["already_present"] += 1
        elif res == "failed":
            s["failed"] += 1
        elif res == "would_copy_source_verified":
            s["would_copy"] += 1
        if res in ("copied_verified", "already_present_verified", "would_copy_source_verified"):
            s["by_model"][FOLDERS[r["model"]]] = s["by_model"].get(FOLDERS[r["model"]], 0) + 1
    return s


# ------------------------------------------------------------------------------------------- offline check

def check(doc, catalogue):
    """Offline report for a selection: every problem at once, nothing corrected or invented.

    catalogue: sha256 -> catalogue associations (unreviewed.provenance). Archive membership itself can only be
    confirmed against the live OneDrive index (a protected dry run).
    """
    report = {"schema_errors": [], "entries": 0, "by_suitability": {}, "by_model": {}, "suitable_by_model": {},
              "duplicates": 0, "contradictory_duplicates": [], "not_in_repository_catalogue": [],
              "catalogue_model_conflicts": [], "extra_fields": {}}
    top = {k: doc.get(k) for k in ("schema", "selection_id", "reviewer", "reviewed_at")}
    try:
        validate_selection({**top, "photos": [{"sha256": "0" * 64, "model": "unknown", "suitability": "uncertain"}]})
    except SelectionError as exc:
        report["schema_errors"].append(str(exc))
    seen = {}
    for i, e in enumerate(doc.get("photos") or []):
        report["entries"] += 1
        try:
            validate_selection({**top, "schema": SCHEMA, "selection_id": "sel-20000101-check", "reviewer": "owner",
                                "reviewed_at": "2000-01-01T00:00:00Z", "photos": [e]}, name=f"photos[{i}]")
        except SelectionError as exc:
            report["schema_errors"].append(str(exc))
        if not isinstance(e, dict):
            continue
        for k in e:
            if k not in ("sha256", "model", "suitability", "reasons", "comment"):
                report["extra_fields"][k] = report["extra_fields"].get(k, 0) + 1
        digest, model, suit = str(e.get("sha256", "")), e.get("model"), e.get("suitability")
        report["by_suitability"][str(suit)] = report["by_suitability"].get(str(suit), 0) + 1
        report["by_model"][str(model)] = report["by_model"].get(str(model), 0) + 1
        if suit == "suitable":
            report["suitable_by_model"][str(model)] = report["suitable_by_model"].get(str(model), 0) + 1
        if digest in seen:
            report["duplicates"] += 1
            if seen[digest] != (model, suit):
                report["contradictory_duplicates"].append(digest)
            continue
        seen[digest] = (model, suit)
        rows = catalogue.get(digest)
        if not rows:
            report["not_in_repository_catalogue"].append({"sha256": digest, "model": model, "suitability": suit})
            continue
        fams = sorted({f for f in (catalogue_family(r.get("model")) for r in rows) if f})
        if model in FOLDERS and model != "unknown" and fams and model not in fams:
            report["catalogue_model_conflicts"].append({"sha256": digest, "selection_model": model,
                                                        "catalogue_families": fams, "suitability": suit,
                                                        "catalogue_models": sorted({r.get("model") for r in rows})})
    report["unique_photos"] = len(seen)
    report["compatible"] = not report["schema_errors"] and not report["contradictory_duplicates"]
    report["suitable_ready_offline"] = sum(1 for d, (m, s) in seen.items() if s == "suitable" and d not in
                                           {c["sha256"] for c in report["catalogue_model_conflicts"]})
    return report


def main(argv=None):
    import argparse
    from .unreviewed import provenance
    ap = argparse.ArgumentParser(description="Offline check of a Suitable Photos selection manifest")
    ap.add_argument("selection", type=Path)
    ap.add_argument("--repo", type=Path, default=Path.cwd())
    a = ap.parse_args(argv)
    doc = json.loads(a.selection.read_text(encoding="utf-8"))
    report = check(doc, provenance(a.repo.resolve()))
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return report


if __name__ == "__main__":
    main()
