"""Verification gates for the Dropbox research mode: which archived photographs may be measured, and why not.

The index is the archive's real source and review index (Research/WatchAlign_417_Source_And_Review_Index_20261024.csv):

    sha256, original_path, bytes, initial_photo_suitability, initial_model_family, provenance_status, specific_model,
    physical_watch_id, source_name, original_source_url, association_count, identity_conflict,
    current_curated_location, calibration_approved, notes

A photograph is accepted only when ALL of these hold (each failure is recorded with its reason, nothing is guessed):
  1. its file is in exactly one "Suitable Photos/<model folder>/<sha256>.<ext>", that folder is a recognised curated
     folder, and the index's current_curated_location points to that same place;
  2. the latest applied review selection for it (Suitable Photos/_audit/<selection>/) says "suitable" and a complete,
     non-dry run verified its copy ("copied_verified" / "already_present_verified");
  3. the index has exactly one consistent row for it, its initial_photo_suitability is "suitable" and its bytes match;
  4. provenance_status is exactly "dealer_attributed_genuine_candidate" (kept verbatim: a dealer-attributed genuine
     CANDIDATE, not independently authenticated; photographic suitability is never taken as authenticity);
  5. a physical_watch_id is recorded and identity_conflict is explicitly false;
  6. the exact specific_model has explicitly supported app geometry, that geometry is the curated folder's model, and
     the selection's model agrees (a selection that said "unknown" may have been refined by a later curated move);
  7. the bytes hash to the SHA-256 (checked again at staging and by the measurement driver).
Missing or conflicting review evidence excludes the photograph (fail closed). Nothing here approves anything for
production calibration; the index's own calibration_approved value is carried through unchanged, never set.
"""
from __future__ import annotations

from collections import Counter
import csv
from hashlib import sha256
import io
import json
import re

HASH = re.compile(r"^[0-9a-f]{64}$")
ROOT = "Suitable Photos"
AUDIT = ROOT + "/_audit"
DEFAULT_INDEX = "Research/WatchAlign_417_Source_And_Review_Index_20261024.csv"
COLUMNS = ("sha256", "original_path", "bytes", "initial_photo_suitability", "initial_model_family",
           "provenance_status", "specific_model", "physical_watch_id", "source_name", "original_source_url",
           "association_count", "identity_conflict", "current_curated_location", "calibration_approved", "notes")
CANDIDATE = "dealer_attributed_genuine_candidate"
# Exact specific_model -> app geometry (model folder) that explicitly supports it. Statistics stay per exact reference;
# a reference not listed is reported as unsupported, never measured and never pooled with a look-alike.
GEOMETRY = {
    "124060": "submariner_124060",
    "126610LN": "submariner_126610",
    "126710BLNR": "gmt_126710",
    "126710BLRO": "gmt_126710",
    "126710GRNR": "gmt_126710",
}
# Curated folder name (Suitable Photos/<folder>) -> model family, as the Suitable Photos copy job names them.
FOLDERS = {
    "Submariner 124060": "submariner_124060",
    "Submariner Date 126610": "submariner_126610",
    "GMT-Master II 126710": "gmt_126710",
    "Unknown model": "unknown",
}
FALSE = {"false", "0", "no"}
TRUE = {"true", "1", "yes"}
VERIFIED_COPY = {"copied_verified", "already_present_verified"}
UNSUPPORTED = "unsupported or unspecific reference"


class InputError(ValueError):
    """The review evidence as a whole is unusable (e.g. the index has another schema); nothing is measured."""


def read_index(raw):
    """sha256 -> list of index rows (all columns, verbatim). The exact real schema is required."""
    reader = csv.DictReader(io.StringIO(raw.decode("utf-8-sig")))
    headers = [h.strip() for h in reader.fieldnames or []]
    missing = [c for c in COLUMNS if c not in headers]
    if missing:
        raise InputError("the source and review index does not have the expected schema; missing "
                         + ", ".join(missing) + f" (columns found: {', '.join(headers)})")
    rows = {}
    for row in reader:
        rec = {c: (row.get(c) or "").strip() for c in COLUMNS}
        rec["sha256"] = rec["sha256"].lower()
        rows.setdefault(rec["sha256"], []).append(rec)
    return rows


def read_audit(source):
    """photo sha256 -> latest applied selection decision (with whether a complete run verified its copy)."""
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
        verified = {p["sha256"]: p.get("destination", "") for r in complete for p in r.get("photos", [])
                    if p.get("result") in VERIFIED_COPY}
        for p in sel.get("photos", []):
            d = str(p.get("sha256", "")).lower()
            entry = {"selection_id": sid, "reviewed_at": sel.get("reviewed_at", ""), "reviewer": sel.get("reviewer", ""),
                     "model": p.get("model"), "suitability": p.get("suitability"), "applied": bool(complete),
                     "copy_verified": d in verified, "copied_to": verified.get(d, "")}
            key = (entry["reviewed_at"], sid)
            if d not in decisions or key > (decisions[d]["reviewed_at"], decisions[d]["selection_id"]):
                decisions[d] = entry
    return decisions


def read_folders(source):
    """sha256 -> [archive paths] in the Suitable Photos folders (every folder, recognised or not)."""
    found = {}
    for folder, is_file, _ in source.list(ROOT):
        if is_file or folder.startswith("_"):
            continue
        for name, f, size in source.list(f"{ROOT}/{folder}"):
            m = re.fullmatch(r"([0-9a-f]{64})\.(jpg|jpeg|png|webp|tif)", name)
            if f and m:
                found.setdefault(m.group(1), []).append((f"{ROOT}/{folder}/{name}", size))
    return found


def curated_location(value):
    """The index's current_curated_location as an archive-relative path (or folder), whatever root prefix it carries."""
    v = value.replace("\\", "/").strip().rstrip("/")
    i = v.find(ROOT + "/")
    return v[i:] if i >= 0 else ""


def gate(sha, paths, decision, index_rows):
    """(accepted record or None, reason)."""
    if not paths:
        return None, "not in Suitable Photos (no longer approved, or never copied)"
    if len(paths) > 1:
        return None, "present in more than one curated folder (conflicting curation)"
    path, size = paths[0]
    folder = path.split("/")[1]
    if folder not in FOLDERS:
        return None, f"curated folder '{folder}' is not a recognised Suitable Photos model folder"
    if not index_rows:
        return None, "missing from the source and review index"
    if len({json.dumps(r, sort_keys=True) for r in index_rows}) > 1:
        return None, "conflicting rows in the source and review index"
    r = index_rows[0]
    loc = curated_location(r["current_curated_location"])
    if not loc:
        return None, f"index current_curated_location is not in Suitable Photos ({r['current_curated_location'] or 'blank'})"
    if loc != path and loc != path.rsplit("/", 1)[0]:
        return None, f"actual folder {path.rsplit('/', 1)[0]} does not match index current_curated_location {loc}"
    if decision is None:
        return None, "no applied review selection for this photograph (unreviewed)"
    if not decision["applied"]:
        return None, f"selection {decision['selection_id']} has no complete verified copy run"
    if decision["suitability"] != "suitable":
        return None, f"latest selection {decision['selection_id']} says {decision['suitability']}"
    if not decision["copy_verified"]:
        return None, f"selection {decision['selection_id']} never verified this photograph's copy"
    if r["initial_photo_suitability"].lower() != "suitable":
        return None, f"index initial_photo_suitability is {r['initial_photo_suitability'] or 'blank'}"
    if r["bytes"] and size and str(size) != r["bytes"]:
        return None, f"file size {size} differs from index bytes {r['bytes']}"
    if r["provenance_status"] != CANDIDATE:
        return None, (f"provenance_status {r['provenance_status'] or 'blank'} "
                      f"(only {CANDIDATE} is measured; replicas and unknown provenance are excluded)")
    if not r["physical_watch_id"]:
        return None, "no physical_watch_id"
    if r["identity_conflict"].lower() in TRUE:
        return None, "identity conflict recorded in the index"
    if r["identity_conflict"].lower() not in FALSE:
        return None, f"identity_conflict not explicitly false ({r['identity_conflict'] or 'blank'})"
    geometry = GEOMETRY.get(r["specific_model"])
    if geometry is None:
        return None, f"{UNSUPPORTED}: {r['specific_model'] or 'blank'}"
    if FOLDERS[folder] != geometry:
        return None, f"curated folder '{folder}' does not hold {r['specific_model']} geometry ({geometry})"
    if decision["model"] not in ("unknown", None) and decision["model"] != geometry:
        return None, f"selection {decision['selection_id']} model {decision['model']} contradicts {r['specific_model']}"
    return {"sha256": sha, "archive_path": path, "reference": r["specific_model"], "app_model": geometry,
            "physical_watch_id": r["physical_watch_id"], "provenance_status": r["provenance_status"],
            "dealer": r["source_name"], "source_url": r["original_source_url"],
            "initial_model_family": r["initial_model_family"], "association_count": r["association_count"],
            "original_path": r["original_path"], "index_calibration_approved": r["calibration_approved"],
            "notes": r["notes"], "selection_id": decision["selection_id"], "selection_model": decision["model"],
            "model_refined_after_selection": decision["model"] == "unknown",
            "reviewer": decision["reviewer"], "production_calibration_approved": False}, ""


def select_inputs(source, index_path=DEFAULT_INDEX):
    """Apply every gate to the exact current archive contents. Returns accepted, rejected, an input audit, index bytes."""
    index_raw = source.read(index_path)
    index = read_index(index_raw)
    decisions = read_audit(source)
    folders = read_folders(source)
    suitable_index = {s for s, rows in index.items() if any(r["initial_photo_suitability"].lower() == "suitable" for r in rows)}
    universe = sorted(set(folders) | suitable_index | {d for d, v in decisions.items() if v["suitability"] == "suitable"})
    accepted, rejected = [], []
    for sha in universe:
        if not HASH.fullmatch(sha):
            rejected.append({"sha256": sha, "reason": "not a SHA-256 identifier"})
            continue
        rec, why = gate(sha, folders.get(sha, []), decisions.get(sha), index.get(sha, []))
        if rec:
            accepted.append(rec)
            continue
        row = (index.get(sha) or [{}])[0]
        rejected.append({"sha256": sha, "reason": why, "specific_model": row.get("specific_model", ""),
                         "physical_watch_id": row.get("physical_watch_id", ""), "source_name": row.get("source_name", ""),
                         "provenance_status": row.get("provenance_status", ""),
                         "initial_photo_suitability": row.get("initial_photo_suitability", ""),
                         "current_curated_location": row.get("current_curated_location", ""),
                         "actual_paths": ";".join(p for p, _ in folders.get(sha, []))})
    suitable_rows = [index[s][0] for s in suitable_index]
    in_folders = [(index.get(s) or [{"specific_model": "", "physical_watch_id": ""}])[0] for s in folders]
    reasons = {r["sha256"]: r["reason"] for r in rejected}
    unsupported = [{"sha256": s, "specific_model": row.get("specific_model", ""),
                    "physical_watch_id": row.get("physical_watch_id", ""),
                    "current_curated_location": row.get("current_curated_location", ""),
                    "actual_paths": ";".join(p for p, _ in folders[s]), "gate_result": reasons.get(s, "accepted")}
                   for s, row in ((s, (index.get(s) or [{}])[0]) for s in sorted(folders))
                   if row.get("specific_model", "") not in GEOMETRY]
    audit = {
        "index_path": index_path, "index_sha256": sha256(index_raw).hexdigest(), "index_rows": sum(map(len, index.values())),
        "index_photos": len(index), "provenance_designation": CANDIDATE,
        "suitable_in_index": {"photos": len(suitable_rows),
                              "watches": len({(r["specific_model"], r["physical_watch_id"]) for r in suitable_rows}),
                              "by_reference": population(suitable_rows)},
        "selections_applied": sorted({d["selection_id"] for d in decisions.values()}),
        "photos_in_suitable_folders": len(folders),
        "in_suitable_folders": {"photos": len(in_folders),
                                "watches": len({(r["specific_model"], r["physical_watch_id"]) for r in in_folders}),
                                "by_reference": population(in_folders)},
        "accepted": {"photos": len(accepted), "watches": len({(a["reference"], a["physical_watch_id"]) for a in accepted}),
                     "by_reference": population([{"specific_model": a["reference"],
                                                  "physical_watch_id": a["physical_watch_id"]} for a in accepted])},
        # every photo in the curated folders whose exact reference has no supported geometry, whatever else failed
        "unsupported_references": unsupported,
        "rejected_by_reason": dict(Counter(r["reason"].split(":")[0] for r in rejected)),
    }
    return accepted, rejected, audit, index_raw


def population(rows):
    out = {}
    for r in rows:
        ref = r["specific_model"] or "(blank)"
        e = out.setdefault(ref, {"photos": 0, "watches": set()})
        e["photos"] += 1
        e["watches"].add(r["physical_watch_id"])
    return {k: {"photos": v["photos"], "watches": len(v["watches"])} for k, v in sorted(out.items())}


def check_snapshot(audit, snapshot):
    """Compare the eligible population with an explicit source snapshot; any difference stops the run."""
    problems = []
    want = snapshot.get("index_sha256")
    if want and want != audit["index_sha256"]:
        problems.append(f"index SHA-256 {audit['index_sha256']} is not the snapshot's {want}")
    for key in ("in_suitable_folders", "suitable_in_index", "accepted"):
        exp = snapshot.get(key)
        if exp is None:
            continue
        got = audit[key]
        for k in ("photos", "watches"):
            if k in exp and exp[k] != got[k]:
                problems.append(f"{key}.{k}: expected {exp[k]}, found {got[k]}")
        for ref, counts in (exp.get("by_reference") or {}).items():
            have = got["by_reference"].get(ref, {"photos": 0, "watches": 0})
            if have != counts:
                problems.append(f"{key}.{ref}: expected {counts}, found {have}")
        extra = set(got["by_reference"]) - set(exp.get("by_reference") or got["by_reference"])
        if exp.get("by_reference") is not None and extra:
            problems.append(f"{key}: unexpected references {sorted(extra)}")
    if "unsupported_photos" in snapshot and snapshot["unsupported_photos"] != len(audit["unsupported_references"]):
        problems.append(f"unsupported references: expected {snapshot['unsupported_photos']} photographs, "
                        f"found {len(audit['unsupported_references'])}")
    return problems
