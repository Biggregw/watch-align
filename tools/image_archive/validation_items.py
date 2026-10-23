"""Build the item list for face-on validation from committed reviews and fetched originals.

    python -m tools.image_archive.validation_items --fetched NAME=ROOT ... [--historical DIR] --model MODEL out.csv

Reviewed labels (offline contact-sheet review of the plain original photographs,
tools/research/marker_audit/REPORT.md "Matched genuine regression"):
results/{date,gmt,nodate}-suitability.csv (near_frontal, eligible, reason) and
results/date-additional-angle-review.csv (near_frontal, angle_reason). Bytes come
from the sha256-verified manifests fetched by fetch_verified.py; a photo whose
original could not be fetched with its exact hash is listed with path '' so the
gap is counted. --historical adds the recovered SHA-addressed originals (GitHub
artifact of the 124060 calibrator) with their family from the committed inventory.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
from pathlib import Path

from .faceon import MODELS

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'tools/research/marker_audit/results'
REVIEWS = {  # review csv -> (verified manifest, model)
    'date-suitability.csv': ('date-verified.csv', 'submariner_126610'),
    'gmt-suitability.csv': ('gmt-verified.csv', 'gmt_126710'),
    'nodate-suitability.csv': ('nodate-verified.csv', 'submariner_124060'),
    'date-additional-angle-review.csv': ('checkpoint/date-expanded-verified.csv', 'submariner_126610'),
}
SHA = re.compile(r'^[0-9a-f]{64}$')


def build(fetched, historical, model, out):
    rows, seen = [], set()
    for review, (manifest, m) in REVIEWS.items():
        if m != model:
            continue
        man = {r['sha256']: r for r in csv.DictReader(open(RES / manifest))}
        root = Path(fetched.get(manifest, ''))
        for r in csv.DictReader(open(RES / review)):
            src = man.get(r['sha256'])
            path = root / src['local_path'] if src and fetched.get(manifest) else None
            rows.append({'photo_id': r['photo_id'], 'sha256': r['sha256'], 'family': src['model'] if src else model,
                         'path': str(path) if path and path.exists() else '', 'set': 'reviewed:' + review,
                         'near_frontal': r['near_frontal'], 'eligible': r.get('eligible', ''),
                         'review_reason': r.get('reason') or r.get('angle_reason', ''),
                         'physical_watch_id': src.get('physical_watch_id', '') if src else ''})
            seen.add(r['sha256'])
    if historical:
        inv = json.loads((ROOT / 'docs/image-archive/historical-inventory.json').read_text())
        fam = {}
        for d in inv['available_decisions']:
            fam.setdefault(d['sha256'], d['family'])
        for p in sorted(Path(historical).rglob('*')):
            if not p.is_file() or not SHA.match(p.name.split('.')[0]):
                continue
            sha = p.name.split('.')[0]
            f = fam.get(sha, 'unknown')
            # an unsupported or unknown family is listed once (with the 124060 job: the artifact is the 124060
            # calibrator's) so it is counted as not assessable, never analysed as another model
            if MODELS.get(f) != model and not (f not in MODELS and model == 'submariner_124060'):
                continue
            if sha in seen:
                for r in rows:
                    if r['sha256'] == sha:
                        r['set'] += '+historical'
                        if not r['path']:   # the source no longer serves the exact bytes: use the artifact original
                            r['path'] = str(p)
                continue
            seen.add(sha)
            rows.append({'photo_id': sha[:16], 'sha256': sha, 'family': f, 'path': str(p), 'set': 'historical',
                         'near_frontal': '', 'eligible': '', 'review_reason': '', 'physical_watch_id': ''})
    with open(out, 'w', newline='') as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    print('items', model, len(rows), 'with bytes', sum(1 for r in rows if r['path']))


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--fetched', action='append', default=[], help='verified manifest name=fetched image root')
    ap.add_argument('--historical')
    ap.add_argument('--model', required=True)
    ap.add_argument('out')
    a = ap.parse_args()
    build(dict(x.split('=', 1) for x in a.fetched), a.historical, a.model, a.out)
