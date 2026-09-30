"""Fresh watch-level split for the translation-stability cycle (split_v2).

python3 tools/research/corpus_qc/split_v2.py <data/harvest> <split_v1.csv> <out.csv>

* Excludes every watch that was in split_v1's locked holdout (that holdout is spent).
* Only watches never seen by an earlier cycle could form a fresh locked holdout. With the current
  corpus there are none, so this split has development and validation only (60/40 per stratum,
  largest remainder, fixed seed) and the report says so; no final result is claimed.
"""
from __future__ import annotations

import csv
import hashlib
import sys
from collections import defaultdict
from pathlib import Path

SEED = "watch-align-translation-split-v2"
PARTS = ("development", "validation")
TARGET = {"development": 0.6, "validation": 0.4}


def main(argv) -> int:
    harvest, v1, out = Path(argv[1]), Path(argv[2]), Path(argv[3])
    prev = {r["physical_watch_id"]: r["partition"] for r in csv.DictReader(v1.open())}
    ws = [r for r in csv.DictReader((harvest / "watches.csv").open(newline="", encoding="utf-8"))
          if r["dataset_decision"] == "ACCEPT" and (r.get("sample_role") or "population") == "population"]
    spent = [r["physical_watch_id"] for r in ws if prev.get(r["physical_watch_id"]) == "holdout"]
    unseen = [r["physical_watch_id"] for r in ws if r["physical_watch_id"] not in prev]
    pool = [r for r in ws if prev.get(r["physical_watch_id"]) != "holdout"]
    strata = defaultdict(list)
    for r in pool:
        strata[(r["class_label"], r["model"], r["factory"] if r["class_label"] == "rep" else "Rolex")].append(r)
    counts = {p: 0 for p in PARTS}
    rows = []
    for key in sorted(strata, key=lambda k: (-len(strata[k]), k)):
        items = sorted(strata[key], key=lambda r: hashlib.sha256((SEED + r["physical_watch_id"]).encode()).hexdigest())
        n = len(items)
        exact = {p: TARGET[p] * n for p in PARTS}
        quota = {p: int(exact[p]) for p in PARTS}
        left = n - sum(quota.values())
        order = sorted(PARTS, key=lambda p: (-(exact[p] - quota[p]), -(TARGET[p] * (sum(counts.values()) + n) - counts[p] - quota[p]), PARTS.index(p)))
        for p in order[:left]:
            quota[p] += 1
        i = 0
        for p in PARTS:
            for _ in range(quota[p]):
                r = items[i]
                rows.append({"physical_watch_id": r["physical_watch_id"], "partition": p, "stratum": "/".join(key),
                             "class_label": r["class_label"], "model": r["model"], "factory": r["factory"],
                             "seen_in_cycle1": prev.get(r["physical_watch_id"], "")})
                counts[p] += 1
                i += 1
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(sorted(rows, key=lambda r: (PARTS.index(r["partition"]), r["stratum"], r["physical_watch_id"])))
    print("excluded (spent cycle-1 holdout):", spent)
    print("unseen accepted watches available for a fresh locked holdout:", len(unseen), unseen)
    by = defaultdict(lambda: defaultdict(int))
    for r in rows:
        by[r["stratum"]][r["partition"]] += 1
    for k, v in sorted(by.items()):
        print(f"{k:28s} dev={v['development']} val={v['validation']}")
    print(dict(counts))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
