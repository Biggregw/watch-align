#!/usr/bin/env python3
"""RESEARCH. Summarises an Alpha99 run over genuine photos (Alpha99Preview findings.csv + Alpha99Interference
interference.csv): how often each marker is withheld by the interference check, and every Alpha98 -> Alpha99 status
change. Checks the Alpha99 promise on genuine photos: no feature is outside (clear / worth a look) unless Alpha98 already
reported it outside, i.e. the evidence layer adds no findings, it only grades or withholds them.

Usage: alpha99_genuine_summary.py <findings.csv> <interference.csv> [catalogue.csv]
"""
import csv
import sys
from collections import Counter, defaultdict


def main():
    f = list(csv.DictReader(open(sys.argv[1])))
    it = list(csv.DictReader(open(sys.argv[2])))
    src = {}
    if len(sys.argv) > 3:
        for r in csv.DictReader(open(sys.argv[3])):
            u = r.get('image_url', '')
            src[r['photo_id']] = 'SWE' if 'swisswatchexpo' in u else "Bob's" if 'bobswatches' in u else 'Phillips' if 'phillips' in u else 'other'
    photos = sorted({r['photo_id'] for r in it})
    print(f'photos with a pose: {len(photos)}')
    print('\nInterference check - markers withheld (hand / glare) per hour:')
    by = defaultdict(Counter)
    for r in it:
        by[r['hour']]['n'] += 1
        if r['clean'] != 'true':
            by[r['hour']]['withheld'] += 1
            by[r['hour']]['also_alpha94'] += r['alpha94_usable'] != 'true'
    tot = Counter()
    for h in sorted(by, key=int):
        c = by[h]; tot.update(c)
        print(f"  {h:>2}: {c['withheld']:3d}/{c['n']:3d} withheld ({100 * c['withheld'] / c['n']:4.1f}%), of which Alpha94 also withheld {c['also_alpha94']}")
    print(f"  all: {tot['withheld']}/{tot['n']} ({100 * tot['withheld'] / max(1, tot['n']):.1f}%), Alpha94 also withheld {tot['also_alpha94']}")
    trans = Counter((r['alpha98_status'], r['alpha99_status']) for r in f)
    print('\nAlpha98 -> Alpha99 status (all features, all photos):')
    for (a, b), n in sorted(trans.items()):
        print(f'  {a:13s} -> {b:13s} {n}')
    new_only = [r for r in f if r['alpha99_status'] in ('CLEAR', 'WORTH') and r.get('alpha101_only') == 'true']
    bad = [r for r in f if r['alpha99_status'] in ('CLEAR', 'WORTH') and r['alpha98_status'] != 'OUTSIDE'
           and r.get('alpha101_only') != 'true']
    print(f'\nnew findings (Alpha99 outside where Alpha98 was not): {len(bad)}')
    for r in bad:
        print('  ', r['photo_id'], src.get(r['photo_id'], ''), r['feature'], r['alpha98_status'], '->', r['alpha99_status'], r['short_line'])
    print(f'\nAlpha101 checks (no Alpha98 counterpart) outside: {len(new_only)} '
          f'(CLEAR {sum(r["alpha99_status"] == "CLEAR" for r in new_only)})')
    for r in new_only:
        print('  ', r['photo_id'], src.get(r['photo_id'], ''), r['feature'], r['alpha99_status'], r['short_line'])
    out = [r for r in f if r['alpha99_status'] in ('CLEAR', 'WORTH')]
    print(f'\nphotos with any Alpha99 finding: {len({r["photo_id"] for r in out})} (features: {len(out)})')
    for r in out:
        print('  ', r['photo_id'], src.get(r['photo_id'], ''), r['feature'], r['alpha99_status'], r['short_line'])
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
