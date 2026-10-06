#!/usr/bin/env python3
"""RESEARCH ONLY. Evaluate the inner-lume-edge 12 prototype (M12Inner CSV) against the outermost-edge rule (offline).
Usage: m12_inner_eval.py <M12Inner local CSV> <M12Inner scaled CSV> <scaled manifest>
Reports: share of sides where the inner edge is separable (>= 15/21 points with two distinct peaks), and, for the
same photographs across scales 1.0-0.7, the SD of the 12 radial / tangential implied by outer vs inner side offsets."""
import csv
import math
import sys
from collections import defaultdict
from statistics import median
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from m12_sides import centroid_shift

SIDES = ('left', 'right', 'base')


def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def implied(r, kind):
    v = [fl(r[f'{s}_{kind}_R']) for s in SIDES]
    return None if None in v else centroid_shift(*v)


def main(local, scaled, manifest):
    rows = [r for r in csv.DictReader(open(local)) if r['status'] == 'accepted']
    sides = [(r, s) for r in rows for s in SIDES]
    sep = [int(r[f'{s}_two_peaks'] or 0) >= 15 for r, s in sides]
    print(f'Local photos: inner edge separable on {sum(sep)}/{len(sep)} sides; all three sides separable on '
          f"{sum(1 for r in rows if all(int(r[f'{s}_two_peaks'] or 0) >= 15 for s in SIDES))}/{len(rows)} photos.")
    gaps = [fl(r[f'{s}_gap_R']) for r, s in sides if fl(r[f'{s}_gap_R'])]
    print(f'Seen surround width (inner-outer gap): median {median(gaps):.4f} R, range {min(gaps):.4f}..{max(gaps):.4f} R')
    meta = {r['local_path']: r for r in csv.DictReader(open(manifest))}
    by = defaultdict(lambda: defaultdict(list)); sepsc = defaultdict(list)
    for r in csv.DictReader(open(scaled)):
        m = meta.get(r['photo_id']) or next((x for x in meta.values() if x['photo_id'] == r['photo_id']), None)
        if not m or r['status'] != 'accepted':
            continue
        sc = float(m['scale'])
        sepsc[sc].append(sum(int(r[f'{s}_two_peaks'] or 0) >= 15 for s in SIDES) / 3)
        if sc < 0.7:
            continue
        for kind in ('outer', 'inner'):
            c = implied(r, kind)
            if c:
                by[kind][m['base_photo_id']].append(c)
    print('Inner edge separable, share of sides by scale: ' + ', '.join(f'{s:.2f}: {sum(v) / len(v):.2f}' for s, v in sorted(sepsc.items(), reverse=True)))
    for kind in ('outer', 'inner'):
        sr, st = [], []
        for b, cs in by[kind].items():
            if len(cs) >= 5:
                for idx, acc in ((0, sr), (1, st)):
                    xs = [c[idx] for c in cs]; mu = sum(xs) / len(xs); acc.append(math.sqrt(sum((x - mu) ** 2 for x in xs) / len(xs)))
        print(f'{kind:5s} edge: same-photo SD across scales 1.0-0.7 and resampling: radial {median(sr):.4f} R, tangential {median(st):.4f} R ({len(sr)} photos)')


if __name__ == '__main__':
    main(*sys.argv[1:4])
