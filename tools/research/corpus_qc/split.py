"""Deterministic, watch-level development / validation / locked-holdout split.

python3 tools/research/corpus_qc/split.py <data/harvest checkout> <out.csv>

* The unit is physical_watch_id: all images of a watch are in one partition.
* Only ACCEPTED population watches (not reference_only) are split.
* Stratified by (class, model, factory) with largest-remainder quotas of 60/20/20 per stratum; the
  leftover slots of small strata go to whichever partition is furthest below its global target, so
  rare groups stay physically independent even where exact stratification is impossible.
* Order inside a stratum is sha256(SEED + watch id): fixed, not dependent on file order.
* The split is written once and committed; later runs must read it, not regenerate it.
"""
from __future__ import annotations

import csv
import hashlib
import sys
from collections import defaultdict
from pathlib import Path

SEED = "watch-align-corpus-split-v1"
PARTS = ("development", "validation", "holdout")
TARGET = {"development": 0.6, "validation": 0.2, "holdout": 0.2}


def make_split(watches: list[dict]) -> list[dict]:
    """watches: [{physical_watch_id, class_label, model, factory}] -> same with 'partition'."""
    strata: dict[tuple, list[dict]] = defaultdict(list)
    for w in watches:
        strata[(w["class_label"], w["model"], w["factory"] if w["class_label"] == "rep" else "Rolex")].append(w)
    total = len(watches)
    counts = {p: 0 for p in PARTS}
    out = []
    for key in sorted(strata, key=lambda k: (-len(strata[k]), k)):
        ws = sorted(strata[key], key=lambda w: hashlib.sha256((SEED + w["physical_watch_id"]).encode()).hexdigest())
        n = len(ws)
        exact = {p: TARGET[p] * n for p in PARTS}
        quota = {p: int(exact[p]) for p in PARTS}
        left = n - sum(quota.values())
        # Leftover slots: largest remainder first, ties to the partition furthest below its global target.
        order = sorted(PARTS, key=lambda p: (-(exact[p] - quota[p]),
                                            -(TARGET[p] * (sum(counts.values()) + n) - counts[p] - quota[p]), PARTS.index(p)))
        for p in order[:left]:
            quota[p] += 1
        i = 0
        for p in PARTS:
            for _ in range(quota[p]):
                out.append(dict(ws[i], partition=p, stratum="/".join(key)))
                counts[p] += 1
                i += 1
    assert len(out) == total
    return sorted(out, key=lambda w: (PARTS.index(w["partition"]), w["stratum"], w["physical_watch_id"]))


def main(argv) -> int:
    harvest, out = Path(argv[1]), Path(argv[2])
    with (harvest / "watches.csv").open(newline="", encoding="utf-8") as f:
        ws = [r for r in csv.DictReader(f) if r["dataset_decision"] == "ACCEPT" and (r.get("sample_role") or "population") == "population"]
    split = make_split([{k: r[k] for k in ("physical_watch_id", "class_label", "model", "factory")} for r in ws])
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["physical_watch_id", "partition", "stratum", "class_label", "model", "factory"])
        w.writeheader()
        w.writerows(split)
    by = defaultdict(lambda: defaultdict(int))
    for s in split:
        by[s["stratum"]][s["partition"]] += 1
    for k, v in sorted(by.items()):
        print(f"{k:32s} " + " ".join(f"{p[:3]}={v.get(p, 0)}" for p in PARTS))
    print({p: sum(1 for s in split if s["partition"] == p) for p in PARTS})
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
