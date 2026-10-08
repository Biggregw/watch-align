#!/usr/bin/env python3
"""QC guardrails 1 (documented measurement-quality exclusion): a genuine photo whose 12-triangle sides disagree by more
than the edge-consistency limit is a reading the app itself would not trust (Alpha101: one edge moved by lighting or
blur; the 12 is then at most worth a look). Such a 12 reading must not define the genuine 12 reference or its
uncertainty. This marks m12_usable=false (reason recorded) on those photos only; every other marker is untouched.

Usage: edge_filter.py --per-photo per_photo.csv --nominal m12_nominal.properties --uncertainty alpha99_uncertainty.properties --out per_photo_edge.csv
The limit is the one written by build_sub_reference.py from the unfiltered data (one pass, no iteration).
"""
import argparse
import csv


def props(p):
    return {l.split('=')[0].strip(): float(l.split('=')[1]) for l in open(p) if '=' in l and not l.startswith('#')}


def main():
    ap = argparse.ArgumentParser()
    for k in ('--per-photo', '--nominal', '--uncertainty', '--out'):
        ap.add_argument(k, required=True)
    a = ap.parse_args()
    nom, lim = props(a.nominal), props(a.uncertainty)['twelve_sides_agreement.limit']
    rows = list(csv.DictReader(open(a.per_photo))); n = 0
    for r in rows:
        if r['status'] == 'accepted' and r['m12_usable'] == 'true' and r['m12_left_side_err_deg'] and r['m12_right_side_err_deg']:
            d = abs((float(r['m12_left_side_err_deg']) - nom['left_side_deg']) - (float(r['m12_right_side_err_deg']) - nom['right_side_deg']))
            if d > lim:
                r['m12_usable'] = 'false'; r['m12_reason'] = f'sides disagree by {d:.2f} deg (> {lim:.2f}): edge-affected'; n += 1
                print('excluded 12 of', r['photo_id'], r['physical_watch_id'], f'{d:.2f} deg')
    with open(a.out, 'w', newline='') as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys())); w.writeheader(); w.writerows(rows)
    print(f'{n} photo(s) excluded from the 12 reference; limit {lim:.3f} deg')


if __name__ == '__main__':
    main()
