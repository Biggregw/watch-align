"""Locked watch-level development / validation / holdout split for the watch-family calibrator.

Unit: physical_watch_id, so every photo of a watch shares one partition. Stratified by class
(and factory for replicas) with largest-remainder 60/20/20 quotas; order inside a stratum is
sha256(seed + watch id). A split file is written once and never regenerated (create() refuses to
overwrite), so a watch can never move between partitions across reruns.
"""
from __future__ import annotations

import csv
import hashlib
from collections import defaultdict
from pathlib import Path

SEED = "watch-align-family-calibrator-split-v1"
PARTS = ("development", "validation", "holdout")
TARGET = {"development": 0.6, "validation": 0.2, "holdout": 0.2}
FIELDS = ["physical_watch_id", "partition", "stratum", "class_label", "model", "factory", "added_in"]


def stratum(w: dict) -> tuple:
    rep = w["class_label"] == "rep"
    return (w["class_label"], w["model"], (w.get("factory") or "unknown") if rep else "Rolex")


def make_split(watches: list[dict], seed: str = SEED) -> list[dict]:
    strata = defaultdict(list)
    for w in watches:
        strata[stratum(w)].append(w)
    out = []
    for key in sorted(strata):
        ws = sorted(strata[key], key=lambda w: hashlib.sha256((seed + w["physical_watch_id"]).encode()).hexdigest())
        n = len(ws)
        exact = {p: TARGET[p] * n for p in PARTS}
        quota = {p: int(exact[p]) for p in PARTS}
        for p in sorted(PARTS, key=lambda p: (-(exact[p] - quota[p]), PARTS.index(p)))[: n - sum(quota.values())]:
            quota[p] += 1
        i = 0
        for p in PARTS:
            for _ in range(quota[p]):
                w = ws[i]
                i += 1
                out.append({"physical_watch_id": w["physical_watch_id"], "partition": p, "stratum": "/".join(key),
                            "class_label": w["class_label"], "model": w["model"], "factory": w.get("factory", ""),
                            "added_in": "locked_split"})
    return sorted(out, key=lambda r: (PARTS.index(r["partition"]), r["stratum"], r["physical_watch_id"]))


def watches_from_acquisition(acquired_csv: Path) -> list[dict]:
    seen = {}
    with acquired_csv.open(newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            if (r.get("acquisition_status") or "acquired") != "acquired" or r.get("exact_duplicate_of"):
                continue
            wid = r.get("physical_watch_id") or r.get("candidate_id")
            if wid and wid not in seen:
                seen[wid] = {"physical_watch_id": wid, "class_label": (r.get("class_label") or "").lower(),
                             "model": r.get("model", ""), "factory": r.get("factory", "")}
    return [w for w in seen.values() if w["class_label"] in ("gen", "rep")]


def create(watches: list[dict], path: Path) -> list[dict]:
    if path.exists():
        raise FileExistsError(f"{path} is locked; it is never regenerated")
    rows = make_split(watches)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(rows)
    return rows


def load(path: Path) -> dict[str, str]:
    with path.open(newline="", encoding="utf-8") as fh:
        return {r["physical_watch_id"]: r["partition"] for r in csv.DictReader(fh)}
