"""Bounded, non-destructive recovery pilot of missing catalogued originals (owner-approved: at most 100).

    python -m tools.image_archive.recovery_pilot select OUT_MANIFEST.csv [--limit 100] [--evidence EVIDENCE.jsonl]
    python -m tools.image_archive.recovery_pilot report MANIFEST.csv FETCH_LOG.csv [EVIDENCE.jsonl]

select: SHA-256 identities catalogued in the repository (inventory.catalogue_rows) whose exact
original is not yet in the face-on evidence, with an image URL and a supported dial family,
drawn deterministically (sorted by hash) in turn across (family, host) so no single source
dominates. Writes a fetch_verified.py manifest. Fetching is done by that existing tool: the
dataset harvester's HTTP client (robots.txt respected, per-host interval, no retry around a
refusal); a file is kept only when its SHA-256 equals the catalogued hash.
report: counts exact originals recovered, unavailable, now served with different bytes, and
(with evidence) the face-on-v3 outcome and independent watches, by family and by source.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

from .faceon import MODELS

ROOT = Path(__file__).resolve().parents[2]
SHA = re.compile(r'^[0-9a-f]{64}$')
FIELDS = ['photo_id', 'local_path', 'sha256', 'image_url', 'physical_watch_id', 'model', 'provenance', 'source',
          'listing_url', 'catalogue']


def host(url):
    return re.sub(r'^https?://([^/]+).*', r'\1', url or '')


def select(out, limit=100, evidence=None):
    from .inventory import catalogue_rows
    have = set()
    if evidence:
        for line in open(evidence):
            e = json.loads(line)
            if not e.get('error'):
                have.add(e['sha256'])
    first = {}
    for cat, r in catalogue_rows(ROOT):
        h = (r.get('sha256') or '').lower()
        url = r.get('image_url') or ''
        model = r.get('model') or r.get('family') or ''
        if not SHA.match(h) or h in have or not url.startswith('https://') or model not in MODELS:
            continue
        first.setdefault(h, (cat, r))
    strata = defaultdict(list)
    for h in sorted(first):
        cat, r = first[h]
        strata[(MODELS[r.get('model') or r.get('family')], host(r['image_url']))].append((h, cat, r))
    picked, order = [], sorted(strata)
    while len(picked) < limit and any(strata.values()):
        for k in order:
            if strata[k] and len(picked) < limit:
                picked.append(strata[k].pop(0))
    with open(out, 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        for h, cat, r in picked:
            w.writerow({'photo_id': h[:16], 'local_path': 'pilot/%s/%s' % (h[:2], h), 'sha256': h,
                        'image_url': r['image_url'], 'physical_watch_id': r.get('physical_watch_id', ''),
                        'model': r.get('model') or r.get('family'), 'provenance': r.get('provenance', ''),
                        'source': r.get('source', '') or r.get('source_name', ''), 'listing_url': r.get('listing_url', ''),
                        'catalogue': cat})
    print('PILOT selected %d of %d candidate missing originals; by family/host %s' % (
        len(picked), len(first), dict(Counter((MODELS[r.get('model') or r.get('family')], host(r['image_url'])) for _, _, r in picked))))
    return picked


def report(manifest, log, evidence=None):
    from .analyse_validation import decide
    man = {r['sha256']: r for r in csv.DictReader(open(manifest))}
    logs = {r['sha256_expected']: r for r in csv.DictReader(open(log))}
    ev = {}
    if evidence:
        for line in open(evidence):
            e = json.loads(line)
            ev[e['sha256']] = e
    def outcome(s):
        st = logs.get(s, {}).get('status', 'not_attempted')
        return 'exact_original' if st.startswith('verified') else 'different_bytes' if st == 'hash_mismatch' else 'unavailable:' + st
    rows = []
    for s, r in man.items():
        o = outcome(s)
        face = ''
        if o == 'exact_original' and s in ev:
            face = decide(ev[s])[0]
        rows.append((r, o, face))
    print('PILOT attempted %d' % len(rows))
    print('PILOT outcome %s' % dict(Counter(o.split(':')[0] for _, o, _ in rows)))
    print('PILOT unavailable reasons %s' % dict(Counter(o for _, o, _ in rows if o.startswith('unavailable'))))
    print('PILOT by source %s' % dict(Counter((host(r['image_url']), o.split(':')[0]) for r, o, _ in rows)))
    print('PILOT by family %s' % dict(Counter((MODELS[r['model']], o.split(':')[0]) for r, o, _ in rows)))
    exact = [(r, f) for r, o, f in rows if o == 'exact_original']
    print('PILOT exact originals face-on-v3 %s' % dict(Counter(f or 'not_analysed' for _, f in exact)))
    ok = [r for r, f in exact if f == 'pass']
    print('PILOT suitable %d photographs, %d independent watches (declared ids), by family %s' % (
        len(ok), len({r['physical_watch_id'] for r in ok if r['physical_watch_id']}), dict(Counter(MODELS[r['model']] for r in ok))))
    return rows


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest='cmd', required=True)
    s = sub.add_parser('select')
    s.add_argument('out')
    s.add_argument('--limit', type=int, default=100)
    s.add_argument('--evidence')
    r = sub.add_parser('report')
    r.add_argument('manifest')
    r.add_argument('log')
    r.add_argument('evidence', nargs='?')
    a = ap.parse_args()
    if a.cmd == 'select':
        if a.limit > 100:
            ap.error('the approved pilot is at most 100 images')
        select(a.out, a.limit, a.evidence)
    else:
        report(a.manifest, a.log, a.evidence)
