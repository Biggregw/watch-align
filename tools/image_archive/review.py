"""Reproducible visual review of archived originals, and the only route into calibration.

    review batch     a fixed list of unreviewed originals (by SHA-256) with viewing copies for a reviewer
    decision file    archive/decisions/*.json: one reviewer's verdicts on photos of a batch, and on each marker
    ledger           every decision file, in order; an owner decision overrides an AI reviewer's
    approved evidence the photos (and the individual markers) whose effective decision is "approved"

Nothing here changes, rejects or deletes an original: an "excluded" photo stays in the archive, only
its calibration use is withheld. Viewing copies are derived files (downscaled, EXIF-rotated JPEGs); the
review records always refer to the untouched original by its SHA-256.
"""
from __future__ import annotations

from datetime import datetime, timezone
from hashlib import sha256
import io
import json
from pathlib import Path
import re
import uuid

HASH = re.compile(r"^[0-9a-f]{64}$")
BATCH_ID = re.compile(r"^b\d{8}T\d{6}Z-[0-9a-f]{6}$")
DECISION_SCHEMA = "watch-align-review-decision/1"
BATCH_SCHEMA = "watch-align-review-batch/1"
EVIDENCE_SCHEMA = "watch-align-approved-evidence/1"
REVIEWERS = {"owner": 2, "chatgpt": 1, "claude": 1}
PHOTO_DECISIONS = ("approved", "excluded", "needs_owner")
MARKER_DECISIONS = ("approved", "excluded", "cant_assess")
VIEWS = ("face_on", "slightly_oblique", "oblique", "not_a_dial", "unknown")
AUTHENTICITY = ("genuine_dealer", "genuine_owner_verified", "replica", "unknown")
# Physical hour markers per app dial family (date models have no 3 o'clock marker).
FAMILY_HOURS = {
    "submariner_124060": tuple(range(1, 13)),
    "submariner_126610": tuple(h for h in range(1, 13) if h != 3),
    "gmt_126710": tuple(h for h in range(1, 13) if h != 3),
}
_FAMILY_PATTERNS = (
    ("submariner_124060", ("124060", "114060", "no-date", "nodate", "submariner_124060")),
    ("submariner_126610", ("126610", "116610", "126613", "126618", "126619", "submariner_126610")),
    ("gmt_126710", ("126710", "116710", "gmt", "blnr", "blro", "grnr")),
)
PREVIEW_SIDE = 2048
SHEET_GRID = (3, 3)


class ReviewError(ValueError):
    """A review record is malformed or refers to something it may not; never silently corrected."""


def family(model):
    text = (model or "").strip().lower()
    if text in FAMILY_HOURS:
        return text
    for name, patterns in _FAMILY_PATTERNS:
        if any(p in text for p in patterns):
            return name
    return None


def new_batch_id(now=None):
    now = now or datetime.now(timezone.utc)
    return "b" + now.strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:6]


# ------------------------------------------------------------------------------------------- batches

def batch_entries(photos, reviewed, batched, *, size=20, family_filter=None):
    """Choose unreviewed originals for a new batch: catalogued first, then by hash; never repeats a photo."""
    if not 1 <= size <= 60:
        raise ReviewError("batch size must be 1..60")
    chosen = []
    for item in sorted(photos.values(), key=lambda x: (not x.get("associations"), x["sha256"])):
        digest = item["sha256"]
        if digest in reviewed or digest in batched:
            continue
        models = [a.get("model", "") for a in item.get("associations") or []]
        fam = next((f for f in map(family, models) if f), None)
        if family_filter and fam != family_filter:
            continue
        chosen.append({
            "id": digest[:10],
            "sha256": digest,
            "key": item["key"],
            "format": item["format"],
            "size": item["size"],
            "family": fam,
            "models": sorted({m for m in models if m}),
            "physical_watch_ids": sorted({a.get("physical_watch_id") for a in item.get("associations") or []
                                          if a.get("physical_watch_id")}),
            "source_urls": sorted({a.get("source_url") for a in item.get("associations") or [] if a.get("source_url")}),
            "provenance": sorted({a.get("provenance") for a in item.get("associations") or [] if a.get("provenance")}),
            "identity_conflict": bool(item.get("physical_watch_identity_conflict")),
        })
        if len(chosen) >= size:
            break
    return chosen


def batch_document(batch_id, entries, created=None):
    if not BATCH_ID.fullmatch(batch_id):
        raise ReviewError("invalid batch id")
    return {"schema": BATCH_SCHEMA, "batch_id": batch_id,
            "created": created or datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "onedrive_folder": f"WatchAlign/review/batches/{batch_id}",
            "calibration_approved": False, "photos": entries}


def decisions_template(batch):
    photos = []
    for p in batch["photos"]:
        hours = FAMILY_HOURS.get(p["family"] or "", ())
        photos.append({"id": p["id"], "sha256": p["sha256"], "decision": "needs_owner",
                       "family": p["family"], "view": "unknown", "authenticity": "unknown",
                       "physical_watch_id": (p["physical_watch_ids"] or [""])[0],
                       "reasons": [], "comment": "",
                       "markers": {str(h): {"decision": "cant_assess", "reason": ""} for h in hours}})
    return {"schema": DECISION_SCHEMA, "batch_id": batch["batch_id"], "reviewer": "chatgpt",
            "reviewed_at": "YYYY-MM-DDTHH:MM:SSZ", "photos": photos}


def preview_bytes(raw):
    """A viewing copy: EXIF-rotated, at most PREVIEW_SIDE px, JPEG. The original is never modified."""
    from PIL import Image, ImageOps
    with Image.open(io.BytesIO(raw)) as im:
        im = ImageOps.exif_transpose(im).convert("RGB")
        im.thumbnail((PREVIEW_SIDE, PREVIEW_SIDE))
        out = io.BytesIO()
        im.save(out, "JPEG", quality=88)
        return out.getvalue(), im.size


def contact_sheets(previews):
    """[(label, jpeg bytes)] -> labelled 3x3 contact sheets (JPEG bytes)."""
    from PIL import Image, ImageDraw, ImageFont
    cols, rows = SHEET_GRID
    cell, label_h = 700, 70
    try:
        font = ImageFont.truetype("DejaVuSans-Bold.ttf", 40)
    except OSError:
        font = ImageFont.load_default()
    sheets = []
    per = cols * rows
    for start in range(0, len(previews), per):
        sheet = Image.new("RGB", (cols * cell, rows * (cell + label_h)), "white")
        draw = ImageDraw.Draw(sheet)
        for i, (label, data) in enumerate(previews[start:start + per]):
            with Image.open(io.BytesIO(data)) as im:
                im = im.convert("RGB")
                im.thumbnail((cell - 20, cell - 20))
                x, y = (i % cols) * cell, (i // cols) * (cell + label_h)
                sheet.paste(im, (x + (cell - im.width) // 2, y + label_h + (cell - im.height) // 2))
                draw.text((x + 12, y + 12), label, fill="black", font=font)
        out = io.BytesIO()
        sheet.save(out, "JPEG", quality=85)
        sheets.append(out.getvalue())
    return sheets


README = """Watch Align visual review batch {batch_id}

Every photo here is a VIEWING COPY (downscaled, rotated upright) of an untouched original in
WatchAlign/unreviewed/objects/. Decisions always refer to the original's SHA-256 (batch.json).

1. Look at sheet-*.jpg for an overview and photos/<id>_<family>.jpg for detail.
2. For each photo decide: approved (usable as calibration evidence), excluded (keep, but never
   use for calibration) or needs_owner. For each hour marker decide approved / excluded / cant_assess
   (a hand, glare, blur, crop or angle makes it unreliable).
3. Fill decisions-template.json and commit it to the repository as
   archive/decisions/{batch_id}-<reviewer>-<n>.json on branch experiment/archive-first-onedrive-20261023.
   The archive workflow validates it and only approved photos/markers reach calibration.
Excluded photos are never deleted.
"""


def build_batch_files(batch, fetch):
    """{relative path: bytes} for the OneDrive review folder; fetch(key) returns verified original bytes."""
    files, previews = {}, []
    for p in batch["photos"]:
        raw = fetch(p["key"])
        if sha256(raw).hexdigest() != p["sha256"] or len(raw) != p["size"]:
            raise ReviewError("archived original failed SHA-256 verification; batch not created")
        data, (w, h) = preview_bytes(raw)
        p["preview"] = f"photos/{p['id']}_{p['family'] or 'unknown'}.jpg"
        p["preview_px"] = [w, h]
        files[p["preview"]] = data
        previews.append((p["id"] + "  " + (p["family"] or "unknown model"), data))
    for i, sheet in enumerate(contact_sheets(previews), 1):
        files[f"sheet-{i:02d}.jpg"] = sheet
    files["batch.json"] = json.dumps(batch, indent=2, ensure_ascii=False).encode()
    files["decisions-template.json"] = json.dumps(decisions_template(batch), indent=2).encode()
    files["README.txt"] = README.format(batch_id=batch["batch_id"]).encode()
    return files


# ------------------------------------------------------------------------------------------- decisions

def _time(text):
    try:
        t = datetime.fromisoformat(str(text).replace("Z", "+00:00"))
    except ValueError:
        raise ReviewError("reviewed_at must be an ISO-8601 time") from None
    if t.tzinfo is None:
        raise ReviewError("reviewed_at needs a time zone (use Z for UTC)")
    return t


def validate_decision(doc, batches, *, name="decision"):
    """Strictly validate one decision document against the committed batch indexes."""
    if not isinstance(doc, dict) or doc.get("schema") != DECISION_SCHEMA:
        raise ReviewError(f"{name}: unknown schema")
    reviewer = doc.get("reviewer")
    if reviewer not in REVIEWERS:
        raise ReviewError(f"{name}: reviewer must be one of {sorted(REVIEWERS)}")
    batch = batches.get(doc.get("batch_id"))
    if batch is None:
        raise ReviewError(f"{name}: batch {doc.get('batch_id')!r} is not a committed review batch")
    _time(doc.get("reviewed_at"))
    members = {p["sha256"]: p for p in batch["photos"]}
    seen = set()
    photos = doc.get("photos")
    if not isinstance(photos, list) or not photos:
        raise ReviewError(f"{name}: no photo decisions")
    for p in photos:
        digest = str(p.get("sha256", "")).lower()
        if not HASH.fullmatch(digest) or digest not in members:
            raise ReviewError(f"{name}: photo {p.get('id')!r} is not in batch {batch['batch_id']}")
        if digest in seen:
            raise ReviewError(f"{name}: photo {digest[:10]} decided twice")
        seen.add(digest)
        if p.get("id") not in (None, digest[:10]):
            raise ReviewError(f"{name}: id {p.get('id')!r} does not match its SHA-256")
        if p.get("decision") not in PHOTO_DECISIONS:
            raise ReviewError(f"{name}: {digest[:10]} decision must be one of {PHOTO_DECISIONS}")
        if p.get("view", "unknown") not in VIEWS:
            raise ReviewError(f"{name}: {digest[:10]} view must be one of {VIEWS}")
        if p.get("authenticity", "unknown") not in AUTHENTICITY:
            raise ReviewError(f"{name}: {digest[:10]} authenticity must be one of {AUTHENTICITY}")
        fam = p.get("family") or members[digest]["family"]
        if fam is not None and fam not in FAMILY_HOURS:
            raise ReviewError(f"{name}: {digest[:10]} unknown family {fam!r}")
        markers = p.get("markers") or {}
        if not isinstance(markers, dict):
            raise ReviewError(f"{name}: {digest[:10]} markers must be an object keyed by hour")
        allowed = FAMILY_HOURS.get(fam or "", ())
        for hour, m in markers.items():
            if not str(hour).isdigit() or int(hour) not in allowed:
                raise ReviewError(f"{name}: {digest[:10]} has no physical {hour} o'clock marker for {fam}")
            if not isinstance(m, dict) or m.get("decision") not in MARKER_DECISIONS:
                raise ReviewError(f"{name}: {digest[:10]} marker {hour} decision must be one of {MARKER_DECISIONS}")
        if p["decision"] == "approved":
            if fam is None:
                raise ReviewError(f"{name}: {digest[:10]} approved without a supported model family")
            if not any(m["decision"] == "approved" for m in markers.values()):
                raise ReviewError(f"{name}: {digest[:10]} approved but no marker approved")
    return doc


def load_batches(folder):
    batches = {}
    for path in sorted(Path(folder).glob("*.json")):
        doc = json.loads(path.read_text(encoding="utf-8"))
        if doc.get("schema") != BATCH_SCHEMA or not BATCH_ID.fullmatch(str(doc.get("batch_id"))):
            raise ReviewError(f"{path.name}: not a review batch index")
        if path.stem != doc["batch_id"]:
            raise ReviewError(f"{path.name}: file name must equal its batch id")
        batches[doc["batch_id"]] = doc
    return batches


def load_decisions(folder, batches):
    decisions = []
    for path in sorted(Path(folder).glob("*.json")):
        try:
            doc = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            raise ReviewError(f"{path.name}: not valid JSON") from None
        validate_decision(doc, batches, name=path.name)
        decisions.append((path.name, doc))
    return decisions


def ledger(decisions):
    """sha256 -> effective decision: highest-authority reviewer, latest time; an equal-rank disagreement is a conflict."""
    by_photo = {}
    for name, doc in decisions:
        rank, when = REVIEWERS[doc["reviewer"]], _time(doc["reviewed_at"])
        for p in doc["photos"]:
            by_photo.setdefault(p["sha256"].lower(), []).append((rank, when, name, doc, p))
    result = {}
    for digest, entries in by_photo.items():
        top = max(e[0] for e in entries)
        best = [e for e in entries if e[0] == top]
        latest = max(e[1] for e in best)
        final = [e for e in best if e[1] == latest]
        verdicts = {json.dumps([e[4]["decision"], e[4].get("markers") or {}], sort_keys=True) for e in final}
        rank, when, name, doc, p = final[-1]
        result[digest] = {"decision": p["decision"] if len(verdicts) == 1 else "conflict",
                          "reviewer": doc["reviewer"], "reviewed_at": doc["reviewed_at"], "decision_file": name,
                          "batch_id": doc["batch_id"], "photo": p,
                          "history": [{"reviewer": e[3]["reviewer"], "decision": e[4]["decision"],
                                       "reviewed_at": e[3]["reviewed_at"], "decision_file": e[2]} for e in entries]}
    return result


def approved_evidence(effective, batches):
    """The ONLY calibration input: approved photos with their approved markers. Everything else is withheld."""
    members = {p["sha256"]: p for b in batches.values() for p in b["photos"]}
    approved, withheld = [], {"excluded": 0, "needs_owner": 0, "conflict": 0}
    for digest, e in sorted(effective.items()):
        if e["decision"] != "approved":
            withheld[e["decision"]] = withheld.get(e["decision"], 0) + 1
            continue
        p, m = e["photo"], members[digest]
        markers = p.get("markers") or {}
        approved.append({
            "sha256": digest, "key": m["key"], "family": p.get("family") or m["family"],
            "models": m["models"], "physical_watch_id": p.get("physical_watch_id") or "",
            "source_urls": m["source_urls"], "provenance": m["provenance"],
            "authenticity": p.get("authenticity", "unknown"), "view": p.get("view", "unknown"),
            "approved_hours": sorted(int(h) for h, v in markers.items() if v["decision"] == "approved"),
            "excluded_hours": sorted(int(h) for h, v in markers.items() if v["decision"] != "approved"),
            "reviewer": e["reviewer"], "reviewed_at": e["reviewed_at"], "decision_file": e["decision_file"],
        })
    return {"schema": EVIDENCE_SCHEMA, "calibration_input": True,
            "rule": "only photos whose effective review decision is approved; only their approved markers",
            "approved_photos": len(approved), "withheld": withheld,
            "reviewed_photos": len(effective), "photos": approved}


def reviewed_and_batched(batches, effective, *, now=None, stale_days=30):
    """Photos already decided, and photos waiting in a batch younger than stale_days (re-offered after that)."""
    now = now or datetime.now(timezone.utc)
    waiting = set()
    for b in batches.values():
        if (now - _time(b["created"])).days < stale_days:
            waiting |= {p["sha256"] for p in b["photos"]}
    return set(effective), waiting - set(effective)
