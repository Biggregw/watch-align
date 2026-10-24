"""Verification gates for the Dropbox research mode: which archived photographs may be measured, and why not.

A photograph is accepted only when ALL of these hold (each failure is recorded with its reason, nothing is guessed):
  1. its file is still in "Suitable Photos/<model folder>/<sha256>.<ext>" and exactly once;
  2. the latest applied review selection for it (Suitable Photos/_audit/<selection>/selection.json, with a complete,
     verified copy run) says "suitable", and that run verified its copy;
  3. the source and review index has exactly one consistent record for it, saying the photograph is suitable;
  4. the index records it as genuine (photographic suitability is never taken as evidence of authenticity), with no
     replica provenance, and a confirmed physical-watch identity;
  5. the selection's model, the folder and the index reference agree, and the exact reference has explicitly
     supported app geometry (no pooling of look-alike dials);
  6. its bytes hash to the recorded SHA-256 (checked again by the measurement driver before measuring).
Missing or conflicting review evidence excludes the photograph (fail closed). Nothing here approves anything for
production calibration.
"""
from __future__ import annotations

import csv
from hashlib import sha256
import io
import json
import re

HASH = re.compile(r"^[0-9a-f]{64}$")
ROOT = "Suitable Photos"
AUDIT = ROOT + "/_audit"
DEFAULT_INDEX = "Research/WatchAlign_417_Source_And_Review_Index_20261024.csv"
# Exact reference -> the app geometry (model folder) that explicitly supports it. Statistics are always kept per exact
# reference; this map only says which app geometry measures it. A reference not listed is never measured.
GEOMETRY = {
    "124060": "submariner_124060",
    "126610LN": "submariner_126610",
    "126710BLNR": "gmt_126710",
    "126710BLRO": "gmt_126710",
    "126710GRNR": "gmt_126710",
}
FIELDS = {   # logical field -> accepted header names (first match wins); override with a column map
    "sha256": ("sha256", "original_sha256", "sha_256"),
    "reference": ("exact_reference", "reference", "model_reference", "model"),
    "dial_generation": ("dial_generation", "dial_variant", "generation"),
    "physical_watch_id": ("physical_watch_id", "watch_id"),
    "identity_status": ("identity_status", "watch_identity_status", "identity"),
    "dealer": ("dealer", "dealer_name", "source_name", "source"),
    "provenance": ("provenance",),
    "authenticity": ("authenticity", "authenticity_status", "genuine_status", "class_label"),
    "suitability": ("photographic_suitability", "suitability", "review_decision"),
    "source_url": ("source_url", "listing_url", "url"),
}
REQUIRED = ("sha256", "reference", "physical_watch_id", "identity_status", "dealer", "provenance", "authenticity",
            "suitability")
GENUINE = {"genuine", "genuine_dealer", "genuine_owner_verified", "verified_genuine", "gen"}
CONFIRMED_IDENTITY = {"confirmed", "verified", "certain"}
SUITABLE = {"suitable", "approved"}
VERIFIED_COPY = {"copied_verified", "already_present_verified"}


class InputError(ValueError):
    """The review evidence as a whole is unusable (e.g. the index lacks a required field); nothing is measured."""


def _norm(v):
    return (v or "").strip()


def read_index(raw, column_map=None):
    """sha256 -> list of index records (logical fields), plus the header mapping used."""
    text = raw.decode("utf-8-sig")
    reader = csv.DictReader(io.StringIO(text))
    headers = reader.fieldnames or []
    mapping = {}
    for field, names in FIELDS.items():
        if column_map and field in column_map:
            if column_map[field] not in headers:
                raise InputError(f"column map names {column_map[field]!r} for {field}, but the index has no such column")
            mapping[field] = column_map[field]
            continue
        mapping[field] = next((h for h in names if h in headers), None)
    missing = [f for f in REQUIRED if not mapping.get(f)]
    if missing:
        raise InputError("the source and review index lacks required fields " + ", ".join(missing)
                         + f" (columns found: {', '.join(headers)}); supply --column-map rather than guessing")
    rows = {}
    for row in reader:
        rec = {f: _norm(row.get(h)) if h else "" for f, h in mapping.items()}
        rec["sha256"] = rec["sha256"].lower()
        rows.setdefault(rec["sha256"], []).append(rec)
    return rows, mapping


def read_audit(source):
    """photo sha256 -> latest applied selection decision (with whether its copy was verified)."""
    decisions = {}
    for sid, is_file, _ in source.list(AUDIT):
        if is_file:
            continue
        names = {n for n, f, _ in source.list(f"{AUDIT}/{sid}") if f}
        if "selection.json" not in names:
            raise InputError(f"audit folder {sid} has no selection.json")
        raw = source.read(f"{AUDIT}/{sid}/selection.json")
        sel = json.loads(raw)
        runs = [json.loads(source.read(f"{AUDIT}/{sid}/{n}")) for n in sorted(names) if n.startswith("run-")]
        complete = [r for r in runs if r.get("copy_status") == "complete" and r.get("dry_run") is False
                    and r.get("selection_sha256") == sha256(raw).hexdigest()]
        verified = {p["sha256"] for r in complete for p in r.get("photos", []) if p.get("result") in VERIFIED_COPY}
        for p in sel.get("photos", []):
            d = str(p.get("sha256", "")).lower()
            entry = {"selection_id": sid, "reviewed_at": sel.get("reviewed_at", ""), "reviewer": sel.get("reviewer", ""),
                     "model": p.get("model"), "suitability": p.get("suitability"),
                     "applied": bool(complete), "copy_verified": d in verified}
            key = (entry["reviewed_at"], sid)
            if d not in decisions or key > (decisions[d]["reviewed_at"], decisions[d]["selection_id"]):
                decisions[d] = entry
    return decisions


def read_folders(source):
    """sha256 -> [archive paths] present in the Suitable Photos model folders."""
    found = {}
    for folder, is_file, _ in source.list(ROOT):
        if is_file or folder.startswith("_"):
            continue
        for name, f, _ in source.list(f"{ROOT}/{folder}"):
            m = re.fullmatch(r"([0-9a-f]{64})\.(jpg|jpeg|png|webp|tif)", name)
            if f and m:
                found.setdefault(m.group(1), []).append(f"{ROOT}/{folder}/{name}")
    return found


def gate(sha, paths, decision, index_rows):
    """(accepted record or None, reason). Order matters only for which reason is reported first."""
    if not paths:
        return None, "not in Suitable Photos (no longer approved, or never copied)"
    if len(paths) > 1:
        return None, "present in more than one model folder (conflicting review evidence)"
    if decision is None:
        return None, "no applied review selection for this photograph (unreviewed)"
    if not decision["applied"]:
        return None, f"selection {decision['selection_id']} has no complete verified copy run"
    if decision["suitability"] != "suitable":
        return None, f"latest selection {decision['selection_id']} says {decision['suitability']}"
    if not decision["copy_verified"]:
        return None, f"selection {decision['selection_id']} never verified this photograph's copy"
    if not index_rows:
        return None, "missing from the source and review index"
    distinct = {json.dumps(r, sort_keys=True) for r in index_rows}
    if len(distinct) > 1:
        return None, "conflicting records in the source and review index"
    r = index_rows[0]
    auth, prov = r["authenticity"].lower(), r["provenance"].lower()
    if auth in ("rep", "replica", "fake") or "replica" in auth or prov in ("rep", "replica") or "replica" in prov:
        return None, "replica (index authenticity or provenance)"
    if auth not in GENUINE:
        return None, f"authenticity not established in the index ({r['authenticity'] or 'blank'}); suitability is not authenticity"
    if not r["physical_watch_id"]:
        return None, "no physical-watch identity in the index"
    if r["identity_status"].lower() not in CONFIRMED_IDENTITY:
        return None, f"physical-watch identity not confirmed ({r['identity_status'] or 'blank'})"
    if r["suitability"].lower() not in SUITABLE:
        return None, f"index suitability is {r['suitability'] or 'blank'}"
    geometry = GEOMETRY.get(r["reference"])
    if decision["model"] not in (None, "unknown") and geometry and decision["model"] != geometry:
        return None, f"selection model {decision['model']} contradicts index reference {r['reference']}"
    if geometry is None:
        return None, f"reference {r['reference'] or 'blank'} has no explicitly supported app geometry (not pooled)"
    return {"sha256": sha, "archive_path": paths[0], "reference": r["reference"], "dial_generation": r["dial_generation"],
            "app_model": geometry, "physical_watch_id": r["physical_watch_id"], "identity_status": r["identity_status"],
            "dealer": r["dealer"], "provenance": r["provenance"], "authenticity": r["authenticity"],
            "source_url": r["source_url"], "selection_id": decision["selection_id"], "reviewer": decision["reviewer"],
            "production_calibration_approved": False}, ""


def select_inputs(source, index_path=DEFAULT_INDEX, column_map=None):
    """Apply every gate to the exact current archive contents. Returns accepted, rejected and an input audit."""
    index_raw = source.read(index_path)
    index, mapping = read_index(index_raw, column_map)
    decisions = read_audit(source)
    folders = read_folders(source)
    universe = sorted(set(folders) | {d for d, v in decisions.items() if v["suitability"] == "suitable"})
    accepted, rejected = [], []
    for sha in universe:
        if not HASH.fullmatch(sha):
            rejected.append({"sha256": sha, "reason": "not a SHA-256 identifier"})
            continue
        rec, why = gate(sha, folders.get(sha, []), decisions.get(sha), index.get(sha, []))
        if rec:
            accepted.append(rec)
        else:
            row = (index.get(sha) or [{}])[0]
            rejected.append({"sha256": sha, "reason": why, "reference": row.get("reference", ""),
                             "physical_watch_id": row.get("physical_watch_id", ""), "dealer": row.get("dealer", ""),
                             "provenance": row.get("provenance", ""), "archive_paths": ";".join(folders.get(sha, []))})
    audit = {"index_path": index_path, "index_sha256": sha256(index_raw).hexdigest(), "index_columns": mapping,
             "index_photos": len(index), "selections_applied": sorted({d["selection_id"] for d in decisions.values()}),
             "photos_in_suitable_folders": len(folders), "accepted_photos": len(accepted),
             "accepted_watches": len({(a["reference"], a["physical_watch_id"]) for a in accepted}),
             "rejected_photos": len(rejected)}
    return accepted, rejected, audit, index_raw
