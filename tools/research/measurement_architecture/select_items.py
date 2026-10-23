"""Choose the photographs for the measurement-architecture runs (CI) from the fetched, SHA-verified item list.

    python -m tools.research.measurement_architecture.select_items items.csv base.csv synth.csv [--synth-max 30]

base: every photograph with exact original bytes whose committed evidence (docs/image-archive/face-on-evidence.jsonl)
shows a registered dial (tilt <= 13.3 deg, dial radius >= 200 px), genuine or replica, with one unambiguous physical-watch
identity (shared stock photos are left out). synth: up to --synth-max photographically suitable genuine photographs
(face-on-v3 photo-level checks, at most 2 markers excluded), one per watch, in a fixed order (sha256 of the photo hash).
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path

from tools.image_archive import analyse_validation as av
from tools.image_archive import eligibility as el
from tools.image_archive.inventory import catalogue_rows

ROOT = Path(__file__).resolve().parents[3]
FIELDS = ['photo_id', 'path', 'local_path', 'sha256', 'physical_watch_id', 'class_label', 'model']


def identities():
    ids, cls = defaultdict(set), defaultdict(set)
    for _, r in catalogue_rows(ROOT):
        s = (r.get('sha256') or '').lower()
        if r.get('physical_watch_id'):
            ids[s].add(r['physical_watch_id'])
        if r.get('class_label'):
            cls[s].add(r['class_label'].lower())
    return ids, cls


def main(items, base_out, synth_out, synth_max=30):
    ev = {e['sha256']: e for e in av.unique([json.loads(line) for line in open(ROOT / 'docs/image-archive/face-on-evidence.jsonl')])}
    ids, cls = identities()
    base, synth = [], []
    seen = set()
    for r in csv.DictReader(open(items)):
        sha = r['sha256']
        e = ev.get(sha)
        if not r.get('path') or sha in seen or e is None or e.get('error') or e.get('model') not in el.REQUIRED:
            continue
        seen.add(sha)
        av.prepare(e)
        if not e.get('pose_valid') or e.get('tilt_deg') is None or e['tilt_deg'] > 13.3 or (e.get('rpx') or 0) < 200:
            continue
        w = ids.get(sha) or ({r['physical_watch_id']} if r.get('physical_watch_id') else set())
        c = cls.get(sha, set()) - {''}
        if len(w) != 1 or len(c) != 1:
            continue
        row = {'photo_id': r['photo_id'], 'path': r['path'], 'local_path': r['path'], 'sha256': sha,
               'physical_watch_id': next(iter(w)), 'class_label': next(iter(c)), 'model': e['model']}
        base.append(row)
        status, reasons = av.decide(e)
        if row['class_label'] == 'gen' and not [x for x in reasons if not re.search(r'_marker_\d+$', x)]:
            mask = el.build(sha, e['family'], el.marker_statuses(e, e['model']), 'face-on-v3')
            if len(mask['eligible_marker_ids']) >= el.min_eligible(e['model']):
                synth.append(row)
    chosen, watches = [], set()
    for row in sorted(synth, key=lambda r: hashlib.sha256(r['sha256'].encode()).hexdigest()):
        if row['physical_watch_id'] not in watches and len(chosen) < synth_max:
            chosen.append(row)
            watches.add(row['physical_watch_id'])
    for path, rows in ((base_out, base), (synth_out, chosen)):
        with open(path, 'w', newline='') as fh:
            w = csv.DictWriter(fh, fieldnames=FIELDS)
            w.writeheader()
            w.writerows(rows)
    print('base %d (genuine %d, replica %d); synthetic %d' % (
        len(base), sum(r['class_label'] == 'gen' for r in base), sum(r['class_label'] == 'rep' for r in base), len(chosen)))


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('items')
    ap.add_argument('base')
    ap.add_argument('synth')
    ap.add_argument('--synth-max', type=int, default=30)
    a = ap.parse_args()
    main(a.items, a.base, a.synth, a.synth_max)
