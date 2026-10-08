#!/usr/bin/env python3
"""QC guardrails 8.5 for a new model: each genuine photo judged alone, as the app judges one photo, against a reference
rebuilt WITHOUT its own physical watch (leave-one-watch-out): nominal of the other watches, genuine max over the other
watches (resolution-matched features: only watches photographed at R <= 1.3 x this photo's R, 8+ needed), and the
K x sigma allowance from the frozen uncertainty file. Photos from excluded sources (default SwissWatchExpo, never in the
reference) are judged against the full reference as an external held-out set.

Usage: heldout_genuine.py --per-photo per_photo.csv --dedup dedup.csv --spec model.json --uncertainty alpha99_uncertainty.properties --out heldout.csv
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
RES_MATCH, MIN_MATCHED = 1.3, 8


def main():
    ap = argparse.ArgumentParser()
    for k in ('--per-photo', '--dedup', '--spec', '--uncertainty', '--out'):
        ap.add_argument(k, required=True)
    ap.add_argument('--exclude-host', action='append', default=['swisswatchexpo'])
    a = ap.parse_args()
    spec = json.load(open(a.spec)); batons, rounds = b.layout(spec)
    rm = set(b.RES_MATCHED) | set(spec.get('resolution_matched', []))   # as the app: built-in + the spec's list
    unc = {l.split('=')[0]: float(l.split('=')[1]) for l in open(a.uncertainty) if '=' in l and not l.startswith('#')}
    K = unc['k_sigma']
    shared = {r['photo_id'] for r in csv.DictReader(open(a.dedup)) if r['shared_dial'] == '1'}
    signed = tuple(f'{k}_rot' for _, k in batons) + ('ring_rot', 'rounds_size')
    feats = tuple(x for _, k in batons for x in (f'{k}_rot', f'{k}_off')) + ('rounds_off', 'ring_rot', 'ring_shift', 'rounds_size', 'round_size_rel')
    angles = tuple(f'{k}_rot' for _, k in batons) + ('ring_rot',)
    photos = []
    for r in csv.DictReader(open(a.per_photo)):
        if r['group'] != 'genuine_population' or r['photo_id'] in shared:
            continue
        f = b.photo_features(r, batons, rounds)
        if f:
            photos.append((r['photo_id'], r['physical_watch_id'], f, any(h in r['image_url'] for h in a.exclude_host)))
    pw = defaultdict(lambda: defaultdict(list))
    for pid, w, f, ext in photos:
        if not ext:
            for k, v in f.items():
                pw[w][k].append(v)
    W = {k: {w: median(d[k]) for w, d in pw.items() if d.get(k)} for k in feats}
    WR = {w: median(d['R']) for w, d in pw.items()}

    def sigma(k, R):
        if k in angles:
            return max(unc.get(k + '.deg', float('nan')), unc.get(k + '.degR', float('nan')) / R)
        return max(unc.get(k + '.R', float('nan')), unc.get(k + '.px', float('nan')) / R)

    def judge(k, v, R, excl):
        vals = {w: x for w, x in W[k].items() if w != excl}
        if not vals:
            return 'NOT_ASSESSED', None, None
        if k in signed:
            val = abs(v - median(vals.values()))
            far = {w: abs(x - median([y for ww, y in vals.items() if ww != w])) for w, x in vals.items()}
        else:
            val = abs(v); far = {w: abs(x) for w, x in vals.items()}
        pool = [x for w, x in far.items() if k not in rm or WR[w] <= RES_MATCH * R]
        if len(pool) < (MIN_MATCHED if k in rm else 1):
            return 'NOT_ASSESSED', val, None
        mx = max(pool)
        if val <= mx:
            return 'WITHIN', val, mx
        sg = sigma(k, R)
        return ('CLEAR' if sg == sg and val - mx > K * sg else 'WORTH'), val, mx

    tally = defaultdict(lambda: defaultdict(int)); out = []
    for pid, w, f, ext in photos:
        grp = 'external' if ext else 'lowo'
        for k in feats:
            if k in f:
                st, val, mx = judge(k, f[k], f['R'], None if ext else w)
                tally[(grp, k)][st] += 1
                out.append([grp, pid, w, k, f"{f['R']:.0f}", st, '' if val is None else f'{val:.6f}', '' if mx is None else f'{mx:.6f}'])
    with open(a.out, 'w', newline='') as fh:
        wr = csv.writer(fh); wr.writerow(['set', 'photo_id', 'physical_watch_id', 'feature', 'dial_radius_px', 'status', 'value', 'genuine_max']); wr.writerows(out)
    print('| set | feature | within | worth a look | clear | not assessed |\n|---|---|---:|---:|---:|---:|')
    for (grp, k), t in sorted(tally.items()):
        print(f"| {grp} | {k} | {t['WITHIN']} | {t['WORTH']} | {t['CLEAR']} | {t['NOT_ASSESSED']} |")
    for r in out:
        if r[5] == 'CLEAR':
            print('CLEAR on held-out genuine:', *r)


if __name__ == '__main__':
    main()
