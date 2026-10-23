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
        clean = {"sha256": digest, "model": model, "suitability": suitability, "reasons": list(reasons),
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
        rec = {**e, "catalogue_families": cat}
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
    """Apply one reviewed selection. Returns the run audit (also written to OneDrive unless dry_run)."""
    from .cloud_jobs import remote_index
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
    if not dry_run:
        # The applied selection is immutable: re-using an id with different content is refused.
        drive.put(f"{AUDIT}/{sid}/selection.json", raw)
    try:
        for rec in actions:
            if rec["action"] != "copy":
                continue
            if dry_run:
                rec["result"] = "would_copy"
                continue
            try:
                data = drive.get(rec["source"])
                if sha256(data).hexdigest() != rec["sha256"] or len(data) != rec["size"]:
                    rec["result"] = "failed"
                    rec["error"] = "source original failed SHA-256 verification; not copied"
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
        audit["status"] = ("dry_run" if dry_run else "complete" if not audit["summary"]["failed"]
                           and all("result" in r for r in actions if r["action"] == "copy") else "incomplete")
        if not dry_run:
            payload = json.dumps(audit, indent=2, ensure_ascii=False, sort_keys=True).encode()
            stamp = audit["finished"].replace(":", "").replace("-", "")
            try:
                drive.put(f"{AUDIT}/{sid}/run-{stamp}-{sha256(payload).hexdigest()[:8]}.json", payload)
                audit["audit_written"] = True
            except Exception:
                audit["audit_written"] = False   # the repository result still carries the full audit
    return audit


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
        elif res == "would_copy":
            s["would_copy"] += 1
        if res in ("copied_verified", "already_present_verified", "would_copy"):
            s["by_model"][FOLDERS[r["model"]]] = s["by_model"].get(FOLDERS[r["model"]], 0) + 1
    return s
