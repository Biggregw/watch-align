#!/usr/bin/env python3
"""Add low-resolution genuine rows to an existing model reference (any model), as build_sub_reference.py --lowres does
for the 124060, without rebuilding the reference.

Rows come from shrunk copies of the genuine dial photos (a *-lowres CI job: per_photo_lowres.csv with source_photo_id
and target_r). Per physical watch and level: the median feature value. far = |value - nominal| for signed features (the
nominal the app compares a photo with), |value| otherwise. Each row carries max_photo_r = the full-resolution coverage
of its feature (smallest photo R with 8+ distinct full-resolution reference watches at R_ref <= 1.3 R), so it is used
only for photos the full-resolution reference cannot cover: limits for every other photo never change. Existing rows
are copied unchanged. Shared photos and excluded hosts (default SwissWatchExpo) never contribute.

Usage: add_lowres_rows.py --reference genuine_reference.csv --nominal nominal.properties --lowres per_photo_lowres.csv
                          --dedup dedup.csv --spec model.json --level 170 --feature rounds_off ... --out out.csv
"""
import argparse
import csv
import importlib.util
import json
import os
from collections import defaultdict
from statistics import median

HERE = os.path.dirname(os.path.abspath(__file__))
s = importlib.util.spec_from_file_location('b', os.path.join(HERE, 'build_sub_reference.py')); b = importlib.util.module_from_spec(s); s.loader.exec_module(b)


def main():
    ap = argparse.ArgumentParser()
    for k in ('--reference', '--nominal', '--lowres', '--dedup', '--spec', '--out'):
        ap.add_argument(k, required=True)
    ap.add_argument('--level', action='append', required=True, help='shrink level(s) (target dial radius px), e.g. 170')
    ap.add_argument('--feature', action='append', required=True, help='features that get low-resolution rows')
    ap.add_argument('--exclude-host', action='append', default=['swisswatchexpo'])
    a = ap.parse_args()
    spec = json.load(open(a.spec)); batons, rounds = b.layout(spec)
    signed = {f'{k}_rot' for _, k in batons} | {'ring_rot', 'rounds_size'}
    nom = {l.split('=')[0].strip(): float(l.split('=')[1]) for l in open(a.nominal) if '=' in l and not l.startswith('#')}
    shared = {r['photo_id'] for r in csv.DictReader(open(a.dedup)) if r['shared_dial'] == '1'}
    rows = list(csv.DictReader(open(a.reference)))
    if any(r.get('max_photo_r') for r in rows):
        raise SystemExit('reference already has low-resolution rows')
    full = defaultdict(dict)
    for r in rows:
        if r['dial_radius_px']:
            full[r['feature']][r['physical_watch_id']] = float(r['dial_radius_px'])
    cover = {}
    for k in a.feature:
        rs = sorted(full[k].values())
        cover[k] = rs[7] / 1.3 if len(rs) >= 8 else float('inf')
    lw = defaultdict(lambda: defaultdict(list))
    for r in csv.DictReader(open(a.lowres)):
        if r['group'] != 'genuine_population' or r['source_photo_id'] in shared or any(h in r['image_url'] for h in a.exclude_host):
            continue
        if r['target_r'] not in a.level:
            continue
        f = b.photo_features(r, batons, rounds)
        if f:
            for k, v in f.items():
                lw[(r['physical_watch_id'], r['target_r'])][k].append(v)
    src = {r['physical_watch_id']: r['source'] for r in rows}
    out = [dict(r, max_photo_r='') for r in rows]; n = 0
    for (w, T), d in sorted(lw.items()):
        if w not in src:
            continue                                   # only watches already in the reference
        Rl = median(d['R'])
        for k in a.feature:
            if not d.get(k):
                continue
            v = median(d[k]); far = abs(v - nom[k]) if k in signed else abs(v)
            out.append({'feature': k, 'physical_watch_id': w, 'source': src[w], 'far': f'{far:.6f}', 'dial_radius_px': f'{Rl:.1f}',
                        'max_photo_r': f'{cover[k]:.1f}'}); n += 1
    with open(a.out, 'w', newline='') as fh:
        wr = csv.DictWriter(fh, fieldnames=list(rows[0].keys()) + ['max_photo_r']); wr.writeheader(); wr.writerows(out)
    print(f'low-resolution rows added: {n} ({len({w for w, _ in lw if w in src})} watches); coverage', {k: round(v, 1) for k, v in cover.items()})


if __name__ == '__main__':
    main()
