#!/usr/bin/env python3
"""Held-out check of a reference that includes low-resolution (shrunk-photo) rows: every REAL genuine photo is judged,
as the app judges one photo, on the resolution-matched features against the reference rows of the OTHER physical
watches photographed at R_ref <= 1.3 x its R (8+ distinct watches needed, else not assessed), with the frozen
uncertainty allowance (none -> at most worth a look). Real photos below the full-resolution coverage (default
R < 181 px, which only the shrunk rows make assessable) are reported separately: they are the held-out test.

Usage: heldout_lowres.py --per-photo per_photo.csv --dedup dedup.csv --spec model.json --reference alpha98_reference.csv
                         --nominal alpha98_nominal.properties --uncertainty alpha99_uncertainty.properties --out out.csv
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


def props(p):
    return {l.split('=')[0]: float(l.split('=')[1]) for l in open(p) if '=' in l and not l.startswith('#')}


def main():
    ap = argparse.ArgumentParser()
    for k in ('--per-photo', '--dedup', '--spec', '--reference', '--nominal', '--uncertainty', '--out'):
        ap.add_argument(k, required=True)
    ap.add_argument('--low-below', type=float, default=181.0)
    ap.add_argument('--interference', help="Alpha99Interference interference.csv: markers it does not clear are dropped, as the "
                    "app withholds them (hands / glare)")
    a = ap.parse_args()
    spec = json.load(open(a.spec)); batons, rounds = b.layout(spec)
    rm = list(b.RES_MATCHED) + list(spec.get('resolution_matched', []))
    nom, unc = props(a.nominal), props(a.uncertainty); K = unc['k_sigma']
    shared = {r['photo_id'] for r in csv.DictReader(open(a.dedup)) if r['shared_dial'] == '1'}
    ref = defaultdict(list)
    for r in csv.DictReader(open(a.reference)):
        if r['feature'] in rm:
            ref[r['feature']].append((r['physical_watch_id'], float(r['far']), float(r['dial_radius_px'])))
    dirty = defaultdict(set)
    if a.interference:
        for r in csv.DictReader(open(a.interference)):
            if r['clean'] != 'true':
                dirty[r['photo_id']].add(int(r['hour']))
    tally = defaultdict(lambda: defaultdict(int)); out = []
    for r in csv.DictReader(open(a.per_photo)):
        if r['group'] != 'genuine_population' or r['photo_id'] in shared:
            continue
        for h in dirty.get(r['photo_id'], ()):
            r[f'm{h}_usable'] = 'false'
        f = b.photo_features(r, batons, rounds)
        if not f:
            continue
        R, w = f['R'], r['physical_watch_id']
        band = 'low (R<%d)' % a.low_below if R < a.low_below else 'full'
        for k in rm:
            if k not in f:
                continue
            val = abs(f[k] - nom[k]) if k in nom else abs(f[k])
            rows = [(ww, far) for ww, far, rr in ref[k] if ww != w and rr <= 1.3 * R]
            if len({ww for ww, _ in rows}) < 8:
                st, mx = 'NOT_ASSESSED', None
            else:
                mx = max(far for _, far in rows)
                if val <= mx:
                    st = 'WITHIN'
                else:
                    sg = max(unc.get(k + '.R', float('nan')), unc.get(k + '.px', float('nan')) / R)
                    st = 'CLEAR' if sg == sg and val - mx > K * sg else 'WORTH'
            tally[(band, k)][st] += 1
            out.append([band, r['photo_id'], w, k, f'{R:.0f}', st, f'{val:.6f}', '' if mx is None else f'{mx:.6f}'])
    with open(a.out, 'w', newline='') as fh:
        wr = csv.writer(fh); wr.writerow(['band', 'photo_id', 'physical_watch_id', 'feature', 'dial_radius_px', 'status', 'value', 'genuine_max']); wr.writerows(out)
    print('| band | feature | within | worth a look | clear | not assessed |\n|---|---|---:|---:|---:|---:|')
    for (band, k), t in sorted(tally.items()):
        print(f"| {band} | {k} | {t['WITHIN']} | {t['WORTH']} | {t['CLEAR']} | {t['NOT_ASSESSED']} |")
    for row in out:
        if row[5] == 'CLEAR':
            print('CLEAR on held-out genuine:', *row)


if __name__ == '__main__':
    main()
