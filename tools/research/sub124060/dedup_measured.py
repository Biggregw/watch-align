#!/usr/bin/env python3
"""Shared / stock photos by measurement: two accepted photos of DIFFERENT physical watches whose 8+ common markers all
agree within 0.0005 R (local radial and tangential offset) are the same dial photographed once and re-used, so neither
may count as an independent genuine watch. Whole-image hashes over-flag studio photos of different watches on the same
background; measured offsets do not.

Usage: dedup_measured.py --per-photo per_photo.csv --catalogue catalogue.csv --out dedup_measured.csv
"""
import argparse
import csv
import itertools

H = [12, 3, 6, 9, 1, 2, 4, 5, 7, 8, 10, 11]
TOL, MIN_COMMON = 0.0005, 8


def vec(r):
    R = float(r['dial_radius_px']); v = {}
    for h in H:
        if r.get(f'm{h}_usable') == 'true' and r.get(f'm{h}_local_radial_px') not in ('', None):
            v[h] = (float(r[f'm{h}_local_radial_px']) / R, float(r[f'm{h}_local_tangential_px']) / R)
    return v


def main():
    ap = argparse.ArgumentParser()
    for k in ('--per-photo', '--catalogue', '--out'):
        ap.add_argument(k, required=True)
    a = ap.parse_args()
    rows = [r for r in csv.DictReader(open(a.per_photo)) if r['status'] == 'accepted']
    V = {r['photo_id']: vec(r) for r in rows}; W = {r['photo_id']: r['physical_watch_id'] for r in rows}
    pairs, best = [], []
    for x, y in itertools.combinations(sorted(V), 2):
        if W[x] == W[y]:
            continue
        c = [h for h in V[x] if h in V[y]]
        if len(c) < MIN_COMMON:
            continue
        d = max(max(abs(V[x][h][0] - V[y][h][0]), abs(V[x][h][1] - V[y][h][1])) for h in c)
        best.append((d, x, y))
        if d < TOL:
            pairs.append((x, y, d))
    best.sort()
    print('closest cross-watch pairs (max marker offset difference, R):')
    for d, x, y in best[:8]:
        print(f'  {d:.5f} {x} {W[x]} {y} {W[y]}')
    shared = {p for x, y, _ in pairs for p in (x, y)}
    print('shared photos', len(shared))
    cat = [r['photo_id'] for r in csv.DictReader(open(a.catalogue))]
    with open(a.out, 'w', newline='') as fh:
        w = csv.writer(fh); w.writerow(['photo_id', 'shared_dial'])
        for p in cat:
            w.writerow([p, '1' if p in shared else '0'])


if __name__ == '__main__':
    main()
