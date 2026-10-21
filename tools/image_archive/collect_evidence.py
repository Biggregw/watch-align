"""Collect read-only face-on evidence for a list of local original photographs.

    python -m tools.image_archive.collect_evidence items.csv out.jsonl [--log]

items.csv columns: photo_id, sha256, path, family, plus any label columns
(kept verbatim, e.g. near_frontal, eligible, review_reason, set). Every photo is
re-hashed; a mismatch is recorded, never analysed. Writes one JSON record per
photo (numbers and labels only, no image bytes); --log also prints a compact
``EVJ`` line per photo for CI logs. No network, no archive writes.
"""
from __future__ import annotations

import argparse
import csv
import json
from collections import defaultdict
from hashlib import sha256
from pathlib import Path

from PIL import Image, ImageOps

from .faceon import MODELS, Driver, summarise

COMPACT_DROP = ('markers',)


def collect(items, out, log=False, chunk=40):
    rows = list(csv.DictReader(open(items)))
    driver = Driver()
    by_model = defaultdict(list)
    records = []
    for r in rows:
        rec = dict(r)
        p = Path(r['path'])
        if not p.exists():
            rec['error'] = 'missing'
        else:
            data = p.read_bytes()
            got = sha256(data).hexdigest()
            rec['sha256_ok'] = got == r['sha256'].lower()
            if not rec['sha256_ok']:
                rec['error'] = 'sha256_mismatch'
            model = MODELS.get(r['family'])
            if not model:
                rec['error'] = rec.get('error') or 'unsupported_layout'
            if 'error' not in rec:
                by_model[model].append(rec)
        records.append(rec)
    for model, recs in by_model.items():
        for i in range(0, len(recs), chunk):
            part = recs[i:i + chunk]
            try:
                ev = driver.run(model, [(r['photo_id'], r['path']) for r in part])
            except Exception as e:  # recorded as an analysis failure for these photos, never guessed
                for r in part:
                    r['error'] = 'analysis_failed: ' + type(e).__name__
                continue
            for r in part:
                e = ev.get(r['photo_id'])
                if e is None:
                    r['error'] = 'no_evidence'
                    continue
                try:
                    with Image.open(r['path']) as im:
                        img = ImageOps.exif_transpose(im).convert('RGB')
                except Exception:
                    img = None
                r.update({k: v for k, v in summarise(e, img).items() if k not in ('photo_id',)})
                r['model'] = model
    with open(out, 'w') as f:
        for r in records:
            r.pop('path', None)
            f.write(json.dumps(r, sort_keys=True) + '\n')
            if log:
                print('EVJ ' + json.dumps(_round({k: v for k, v in r.items() if k not in COMPACT_DROP}), sort_keys=True,
                                          separators=(',', ':')))
    return records


def _round(v):
    if isinstance(v, float):
        return float('%.5g' % v)
    if isinstance(v, dict):
        return {k: _round(x) for k, x in v.items()}
    if isinstance(v, list):
        return [_round(x) for x in v]
    return v


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('items')
    ap.add_argument('out')
    ap.add_argument('--log', action='store_true')
    a = ap.parse_args()
    collect(a.items, a.out, a.log)
