"""Locked watch-level development / validation / holdout split for the Submariner research corpus.

* Unit: physical_watch_id. All photos of a watch share one partition.
* Only ACCEPTED watches are split. Stratified by (class, model, factory for replicas) with
  largest-remainder quotas of 60/20/20; order inside a stratum is sha256(SEED + watch id).
* A split is written ONCE and then read, never regenerated: create()/extend() refuse to overwrite.
  Watches that appear later are "unassigned" until a new split VERSION is deliberately created with
  extend(): every watch of the base version keeps its partition, and only the new watches are split
  (60/20/20 within their own strata, separate seed). So a watch never moves between partitions.
  split_sub_v1.csv: the phase-1 pool. split_sub_v2.csv: v1 + the genuine top-up pool.
"""
from __future__ import annotations

import csv
import hashlib
from collections import defaultdict
from pathlib import Path

SEED = "watch-align-submariner-split-v1"
PARTS = ("development", "validation", "holdout")
TARGET = {"development": 0.6, "validation": 0.2, "holdout": 0.2}
FIELDS = ["physical_watch_id", "partition", "stratum", "class_label", "model", "factory", "added_in"]
UNASSIGNED = "unassigned"


def stratum(w: dict) -> tuple:
    return (w["class_label"], w["model"], (w.get("factory") or "unknown") if w["class_label"] == "rep" else "Rolex")


def make_split(watches: list[dict], seed: str = SEED) -> list[dict]:
    strata = defaultdict(list)
    for w in watches:
        strata[stratum(w)].append(w)
    counts = {p: 0 for p in PARTS}
    out = []
    for key in sorted(strata, key=lambda k: (-len(strata[k]), k)):
        ws = sorted(strata[key], key=lambda w: hashlib.sha256((seed + w["physical_watch_id"]).encode()).hexdigest())
        n = len(ws)
        exact = {p: TARGET[p] * n for p in PARTS}
        quota = {p: int(exact[p]) for p in PARTS}
        left = n - sum(quota.values())
        order = sorted(PARTS, key=lambda p: (-(exact[p] - quota[p]),
                                            -(TARGET[p] * (sum(counts.values()) + n) - counts[p] - quota[p]), PARTS.index(p)))
        for p in order[:left]:
            quota[p] += 1
        i = 0
        for p in PARTS:
            for _ in range(quota[p]):
                w = ws[i]
                out.append({"physical_watch_id": w["physical_watch_id"], "partition": p, "stratum": "/".join(key),
                            "class_label": w["class_label"], "model": w["model"], "factory": w.get("factory", "")})
                counts[p] += 1
                i += 1
    return sorted(out, key=lambda r: (PARTS.index(r["partition"]), r["stratum"], r["physical_watch_id"]))


def create(accepted_watches: list[dict], path: Path) -> list[dict]:
    if path.exists():
        raise FileExistsError(f"{path} is locked; create a new split version instead of regenerating it")
    rows = make_split(accepted_watches)
    path.parent.mkdir(parents=True, exist_ok=True)
    for r in rows:
        r["added_in"] = path.stem.replace("split_sub_", "")
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(rows)
    return rows


def read_rows(path: Path) -> list[dict]:
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def extend(base: Path, accepted_watches: list[dict], path: Path, version: str) -> list[dict]:
    """New split version = every row of `base` unchanged + the accepted watches it lacks, split among
    themselves. Returns the new rows only."""
    if path.exists():
        raise FileExistsError(f"{path} is locked; create a new split version instead of regenerating it")
    old = read_rows(base)
    have = {r["physical_watch_id"] for r in old}
    new = make_split([w for w in accepted_watches if w["physical_watch_id"] not in have], seed=f"{SEED}-{version}")
    for r in new:
        r["added_in"] = version
    for r in old:
        if not r.get("added_in"):
            r["added_in"] = base.stem.replace("split_sub_", "")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        w.writerows(old + new)
    return new


def load(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    with path.open(newline="", encoding="utf-8") as f:
        return {r["physical_watch_id"]: r["partition"] for r in csv.DictReader(f)}


def assign(watch_ids, locked: dict[str, str]) -> dict[str, str]:
    """Partition for each watch from the locked split; watches not in it are UNASSIGNED."""
    return {w: locked.get(w, UNASSIGNED) for w in watch_ids}
