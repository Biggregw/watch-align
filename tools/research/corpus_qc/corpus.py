"""Load the harvested control corpus (a checkout of the data/harvest branch) for QC research.

Rules enforced here, not left to callers:
* physical_watch_id is the unit of independence;
* only ACCEPTED watches with sample_role == "population" are population samples (the catalogue
  reference is kept apart as reference_only);
* every image used is verified against the sha256 recorded in state; a missing or altered image is
  an error, never silently skipped or substituted.
"""
from __future__ import annotations

import csv
import hashlib
import json
import os
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]


@dataclass
class ImageRow:
    sha256: str
    path: str                 # resolved local file
    watch: str
    cls: str
    model: str
    factory: str
    usable: bool              # harvester: suitable, not a duplicate, measured
    reasons: list = field(default_factory=list)
    sample_role: str = "population"
    decision: str = ""


def resolve(local_path: str, sha: str, stores: list[Path]) -> Path | None:
    p = Path(local_path)
    p = p if p.is_absolute() else REPO_ROOT / p
    if p.exists():
        return p
    for s in stores:
        for ext in (".jpg", ".png", ".webp", ".jpeg"):
            q = s / f"{sha}{ext}"
            if q.exists():
                return q
    return None


def sha_file(p: Path) -> str:
    h = hashlib.sha256()
    with p.open("rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def load(harvest_dir: Path, stores: list[Path] | None = None, decisions=("ACCEPT",), verify: bool = True) -> list[ImageRow]:
    """Image rows of watches with the given decisions. Raises if any image is missing/altered."""
    stores = [Path(s) for s in (stores or [])]
    stores.append(Path(harvest_dir) / "images")
    rows = list(csv.DictReader((Path(harvest_dir) / "manifest.csv").open(newline="", encoding="utf-8")))
    out, missing, altered = [], [], []
    for r in rows:
        if r["dataset_decision"] not in decisions:
            continue
        p = resolve(r["local_path"], r["sha256"], stores)
        if p is None:
            missing.append(r["sha256"])
            continue
        if verify and sha_file(p) != r["sha256"]:
            altered.append(str(p))
            continue
        out.append(ImageRow(
            sha256=r["sha256"], path=str(p), watch=r["physical_watch_id"], cls=r["class_label"], model=r["model"],
            factory=r["factory"] if r["class_label"] == "rep" else "Rolex",
            usable=r["suitable"] == "yes" and not r["duplicate_of"] and r["measurement_status"] == "measured",
            reasons=[x for x in r["suitability_reasons"].split(";") if x], sample_role=r.get("sample_role") or "population",
            decision=r["dataset_decision"]))
    if missing or altered:
        raise RuntimeError(f"corpus incomplete: {len(missing)} missing, {len(altered)} altered images "
                           f"(e.g. {(missing + altered)[:3]}). Restore them; do not substitute a subset.")
    # A sha can belong to several sources; keep one row per (sha, watch).
    seen, uniq = set(), []
    for r in out:
        if (r.sha256, r.watch) not in seen:
            seen.add((r.sha256, r.watch))
            uniq.append(r)
    return uniq


def population(rows: list[ImageRow]) -> list[ImageRow]:
    return [r for r in rows if r.decision == "ACCEPT" and r.sample_role == "population"]


def state_suitability(harvest_dir: Path) -> dict[str, dict]:
    """sha256 -> harvester image record (app analysis via Suit, pixel checks, reasons) for ALL images,
    including quarantined/rejected ones whose bytes may not be local."""
    out = {}
    with (Path(harvest_dir) / "state" / "images.jsonl").open(encoding="utf-8") as f:
        for line in f:
            d = json.loads(line)
            out[d["sha256"]] = d
    return out


def watch_decisions(harvest_dir: Path) -> dict[str, dict]:
    with (Path(harvest_dir) / "watches.csv").open(newline="", encoding="utf-8") as f:
        return {r["physical_watch_id"]: r for r in csv.DictReader(f)}
