#!/usr/bin/env python3
"""RESEARCH ONLY. Remove shared / stock photographs from the genuine catalogue (offline).

One physical watch = one independent sample. Dealer blog, model and category pages reuse stock
photographs, so the same photograph can be attributed to several catalogue "watches". Such a photo
cannot be tied to one physical watch, so it is excluded from every watch that lists it; the watches
themselves stay separate and keep their other photos.

A photograph is a cluster of catalogue images joined by the dataset harvester's own duplicate links
(origin/data/harvest:state/images.jsonl), followed transitively through any harvested image:
  exact     identical bytes (same sha256)
  near      harvester near-duplicate (dhash and phash both close: a resized / re-encoded copy)
  dial      harvester dial duplicate (dial-crop phash close)
  possible  harvester 'possible' duplicate (weaker hash evidence)
Levels are cumulative: exact < near < dial (the harvester's duplicate definition) < possible.
A photo is 'shared' at a level when its cluster is attributed to more than one catalogue watch.
Page co-membership alone never links anything (site-wide banners would join every page).

Output: photos.csv (one row per catalogue row: shared_<level> flags) and a summary on stdout.
"""
import csv
import json
import os
import subprocess
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
LEVELS = [('exact', set()), ('near', {'near'}), ('dial', {'near', 'dial'}), ('possible', {'near', 'dial', 'possible'})]


class DSU:
    def __init__(self):
        self.p = {}

    def find(self, x):
        self.p.setdefault(x, x)
        while self.p[x] != x:
            self.p[x] = self.p[self.p[x]]
            x = self.p[x]
        return x

    def union(self, a, b):
        a, b = self.find(a), self.find(b)
        if a != b:
            self.p[max(a, b)] = min(a, b)


def main(catalogue, out):
    cat = list(csv.DictReader(open(catalogue)))
    raw = subprocess.check_output(['git', 'show', 'origin/data/harvest:state/images.jsonl'], cwd=HERE).decode()
    images = {i['sha256']: i for i in (json.loads(l) for l in raw.splitlines() if l.strip())}
    flags = {}
    for name, kinds in LEVELS:
        d = DSU()
        for h, im in images.items():
            if im.get('duplicate_of') and im.get('duplicate_kind') in kinds:
                d.union(h, im['duplicate_of'])
        cluster_watches = defaultdict(set)
        for r in cat:
            cluster_watches[d.find(r['sha256'])].add(r['physical_watch_id'])
        flags[name] = {i: len(cluster_watches[d.find(r['sha256'])]) > 1 for i, r in enumerate(cat)}
        shared = [r for i, r in enumerate(cat) if flags[name][i]]
        kept_w = {r['physical_watch_id'] for i, r in enumerate(cat) if not flags[name][i]}
        all_w = {r['physical_watch_id'] for r in cat}
        lost = sorted(all_w - kept_w)
        print(f'{name:9s} shared photos {len(shared):3d}/{len(cat)} across {len({r["physical_watch_id"] for r in shared})} watches; '
              f'watches left {len(kept_w)}/{len(all_w)} (lost entirely: {len(lost)})')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', newline='') as fh:
        w = csv.writer(fh)
        w.writerow(['photo_id', 'physical_watch_id', 'model', 'source', 'sha256'] + [f'shared_{n}' for n, _ in LEVELS])
        for i, r in enumerate(cat):
            w.writerow([r['photo_id'], r['physical_watch_id'], r['model'], r['source'], r['sha256']] +
                       ['1' if flags[n][i] else '0' for n, _ in LEVELS])


if __name__ == '__main__':
    main(*(sys.argv[1:3] if len(sys.argv) > 2 else (os.path.join(HERE, 'catalogue_provenance_strong.csv'),
                                                     os.path.join(HERE, 'results', 'dedup', 'photos.csv'))))
