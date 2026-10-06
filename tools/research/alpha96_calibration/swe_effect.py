#!/usr/bin/env python3
"""RESEARCH ONLY. Why do SWE photos read the 12 further toward the dial centre? (offline, per photo)

Uses the CI per-photo CSV (genuine, shared photos excluded) and the master geometry. A photographic cause that
acts radially (lens distortion, marker-height parallax / crystal refraction with a close camera) should leave a
pattern across ALL markers that depends on their radius or direction, not on the 12 alone. Per photo:
  ring_scale        ring model scale, % (markers vs minute lattice)
  edge_ratio        detected dial-edge radius / lattice dial radius (an independent radial-scale probe)
  baton_rad_R       mean local radial of the 6 and 9 batons (centre r = 0.755) / R
  round_rad_by_hour local radial of each round (r = 0.813) / R; anisotropy = vertical-ish (1,5,7,11) minus
                    horizontal-ish (2,4,8,10) rounds
  top_bottom        rounds near 12 (11,1) minus rounds near 6 (5,7): a residual vertical gradient
  m12_rad_R         12 local radial / R (+ outward)
  round_err_R       median round radius error / R (edge definition)
Reports medians by source, SWE-minus-Bob's differences, and correlations with the 12 radial across photos.
"""
import argparse
import csv
import math
import os
from collections import defaultdict
from statistics import median

HERE = os.path.dirname(os.path.abspath(__file__))
ROUNDS = (1, 2, 4, 5, 7, 8, 10, 11)


def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def rank(xs):
    o = sorted(range(len(xs)), key=lambda i: xs[i]); r = [0.0] * len(xs); i = 0
    while i < len(o):
        j = i
        while j + 1 < len(o) and xs[o[j + 1]] == xs[o[i]]:
            j += 1
        for k in range(i, j + 1):
            r[o[k]] = (i + j) / 2.0
        i = j + 1
    return r


def spearman(x, y):
    if len(x) < 6:
        return None
    rx, ry = rank(x), rank(y); n = len(x); mx, my = sum(rx) / n, sum(ry) / n
    s = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    d = math.sqrt(sum((a - mx) ** 2 for a in rx) * sum((b - my) ** 2 for b in ry))
    return s / d if d else None


def source(url, R):
    h = url.split('/')[2] if '//' in url else ''
    if 'swisswatchexpo' in h:
        return 'SWE full' if R > 500 else 'SWE 900'
    return "Bob's" if 'bobswatches' in h else 'Phillips' if 'phillips' in h else 'other'


def load(per_photo, dedup, level, catalogue):
    shared = {r['photo_id'] for r in csv.DictReader(open(dedup)) if r[f'shared_{level}'] == '1'}
    cat = {r['photo_id']: r for r in csv.DictReader(open(catalogue))}
    out = []
    for r in csv.DictReader(open(per_photo)):
        if r['group'] != 'genuine_population' or r['status'] != 'accepted' or r['photo_id'] in shared or r['ring_usable'] != 'true':
            continue
        R = fl(r['dial_radius_px'])
        d = dict(photo=r['photo_id'], watch=r['physical_watch_id'], model=r['model'], R=R,
                 src=source(cat[r['photo_id']]['image_url'], R), ell=fl(r['ellipse_ratio']),
                 ring_scale=fl(r['ring_scale_pct']), tick=fl(r['tick_rms_px']))
        e = fl(r['dial_radius_px_edge'])
        d['edge_ratio'] = e / R if e and R else None
        rr = {}
        for h in ROUNDS:
            v = fl(r[f'm{h}_local_radial_px']) if r[f'm{h}_usable'] == 'true' else None
            if v is not None:
                rr[h] = v / R
        d['rounds'] = rr
        errs = [fl(r[f'm{h}_radius_err_px']) for h in ROUNDS if r[f'm{h}_usable'] == 'true']
        errs = [x for x in errs if x is not None]
        d['round_err_R'] = median(errs) / R if errs else None
        b = [fl(r[f'm{h}_local_radial_px']) / R for h in (6, 9) if r[f'm{h}_usable'] == 'true' and fl(r[f'm{h}_local_radial_px']) is not None]
        d['baton_rad_R'] = sum(b) / len(b) if b else None
        bt = [fl(r[f'm{h}_local_tangential_px']) / R for h in (6, 9) if r[f'm{h}_usable'] == 'true' and fl(r[f'm{h}_local_tangential_px']) is not None]
        v1 = [rr[h] for h in (1, 5, 7, 11) if h in rr]; h1 = [rr[h] for h in (2, 4, 8, 10) if h in rr]
        d['aniso_R'] = (sum(v1) / len(v1) - sum(h1) / len(h1)) if v1 and h1 else None
        t = [rr[h] for h in (11, 1) if h in rr]; bo = [rr[h] for h in (5, 7) if h in rr]
        d['top_bottom_R'] = (sum(t) / len(t) - sum(bo) / len(bo)) if t and bo else None
        if r['m12_usable'] == 'true' and fl(r['m12_local_radial_px']) is not None:
            d['m12_rad_R'] = fl(r['m12_local_radial_px']) / R
            d['m12_apex'] = (fl(r['m12_right_side_err_deg']) - fl(r['m12_left_side_err_deg'])) / 2
        out.append(d)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--per-photo', default=os.path.join(HERE, 'results/ci_run_37500197377/per_photo.csv'))
    ap.add_argument('--dedup', default=os.path.join(HERE, 'results/dedup/photos.csv'))
    ap.add_argument('--level', default='dial')
    ap.add_argument('--catalogue', default=os.path.join(HERE, 'catalogue_provenance_strong.csv'))
    ap.add_argument('--out', default=os.path.join(HERE, 'results/swe_effect/analysis.md'))
    a = ap.parse_args()
    P = load(a.per_photo, a.dedup, a.level, a.catalogue)
    srcs = ("Bob's", 'Phillips', 'SWE 900', 'SWE full')
    by = defaultdict(list)
    for d in P:
        by[d['src']].append(d)
    keys = [('R', 'dial R px', 0), ('ell', 'ellipse ratio', 4), ('tick', 'tick RMS px', 3), ('edge_ratio', 'dial-edge / lattice R', 4),
            ('ring_scale', 'ring scale %', 3), ('round_err_R', 'round radius err R', 4), ('baton_rad_R', 'baton (6/9) radial R', 4),
            ('aniso_R', 'round anisotropy R', 4), ('top_bottom_R', 'rounds top - bottom R', 4), ('m12_rad_R', '12 radial R', 4),
            ('m12_apex', '12 apex half-angle err deg', 3)]
    def md(ds, k, n):
        v = [d[k] for d in ds if d.get(k) is not None]
        return f'{median(v):+.{n}f}' if v else '–'
    L = ['# SWE photography effect (research only, per photo, offline)', '',
         f'Genuine accepted photos with a fitted ring, shared photos excluded (level {a.level}): {len(P)}.', '',
         '## Medians by source', '', '| Quantity | ' + ' | '.join(f'{s} (n={len(by[s])})' for s in srcs) + ' |',
         '|---|' + '---:|' * len(srcs)]
    for k, lab, n in keys:
        L.append(f'| {lab} | ' + ' | '.join(md(by[s], k, n) for s in srcs) + ' |')
    L += ['', '## Round markers by hour: local radial R (median)', '', '| Hour | ' + ' | '.join(srcs) + ' |', '|---|' + '---:|' * len(srcs)]
    for h in ROUNDS:
        cells = []
        for s in srcs:
            v = [d['rounds'][h] for d in by[s] if h in d['rounds']]
            cells.append(f'{median(v):+.4f}' if v else '–')
        L.append(f'| {h} | ' + ' | '.join(cells) + ' |')
    m12 = [d for d in P if 'm12_rad_R' in d]
    L += ['', '## Spearman correlation with the 12 radial (all photos; Bob\'s only; SWE only)', '',
          '| Predictor | all | Bob\'s | SWE (both sizes) |', '|---|---:|---:|---:|']
    swe = [d for d in m12 if d['src'].startswith('SWE')]; bob = [d for d in m12 if d['src'] == "Bob's"]
    for k, lab, _ in keys:
        if k in ('m12_rad_R', 'm12_apex'):
            continue
        def sp(ds):
            v = [(d[k], d['m12_rad_R']) for d in ds if d.get(k) is not None]
            s = spearman([x for x, _ in v], [y for _, y in v])
            return f'{s:+.2f} (n={len(v)})' if s is not None else '–'
        L.append(f'| {lab} | {sp(m12)} | {sp(bob)} | {sp(swe)} |')
    L += ['']
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    open(a.out, 'w').write('\n'.join(L) + '\n')
    print('\n'.join(L))


if __name__ == '__main__':
    main()
