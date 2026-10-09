#!/usr/bin/env python3
"""Mark every marker the app's hand / glare check withholds (Alpha99Interference interference.csv, clean != true) as
unusable in a per_photo.csv, so a genuine reference is built from the readings the app itself would trust (QC guardrails
3: a hand over a marker is contamination, not genuine variation). Model-generic; output keeps the input's columns.

Usage: apply_interference.py --per-photo per_photo.csv --interference interference.csv --out per_photo_clean.csv
"""
import argparse
import csv
from collections import defaultdict


def main():
    ap = argparse.ArgumentParser()
    for k in ('--per-photo', '--interference', '--out'):
        ap.add_argument(k, required=True)
    a = ap.parse_args()
    dirty = defaultdict(set)
    for r in csv.DictReader(open(a.interference)):
        if r['clean'] != 'true':
            dirty[r['photo_id']].add(int(r['hour']))
    rows = list(csv.DictReader(open(a.per_photo, newline='')))
    n = 0
    for r in rows:
        for h in dirty.get(r['photo_id'], ()):
            if r.get(f'm{h}_usable') == 'true':
                r[f'm{h}_usable'] = 'false'; r[f'm{h}_reason'] = 'withheld by the app (hand / glare)'; n += 1
    with open(a.out, 'w', newline='') as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()), lineterminator='\n'); w.writeheader(); w.writerows(rows)
    print(f'markers withheld as in the app: {n} on {sum(1 for p in dirty if any(r["photo_id"] == p for r in rows))} photos')


if __name__ == '__main__':
    main()
