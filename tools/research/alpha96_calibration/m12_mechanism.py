#!/usr/bin/env python3
"""RESEARCH ONLY. What moves the genuine 12-triangle offset? (offline, per photo)

Inputs: CI per_photo.csv (run 37500197377), dedup photos.csv (shared photos excluded at --level), the
catalogue (source host) and the harvester image state (to pair each SWE full-size photo with SWE's own
900 px copy of the same shot: harvester near-duplicate links).

Per genuine photo:
  edge_R   median round-marker radius error / R  (+ = detected outline outside the master outline)
  ell      ellipse_ratio of the pose (1 = square-on)
  m12_rad  12 local radial offset / R (+ = outward, - = toward the dial centre, i.e. 'down')
  m12_tan  12 local tangential offset / R
Geometry: for the master triangle a uniform outline shift d moves the area centroid toward the centre by
0.23 d (incentre lies 0.019 R outside the centroid), i.e. m12_rad ~ -0.23 * edge_R if edge definition
were the only effect.
"""
import argparse
import csv
import json
import math
import os
import subprocess
from collections import defaultdict
from statistics import median

HERE = os.path.dirname(os.path.abspath(__file__))
ROUNDS = (1, 2, 4, 5, 7, 8, 10, 11)


def fl(x):
    try:
        v = float(x)
        return v if math.isfinite(v) else None
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


def corr(x, y):
    n = len(x)
    if n < 5:
        return None
    mx, my = sum(x) / n, sum(y) / n
    sxy = sum((a - mx) * (b - my) for a, b in zip(x, y)); sx = sum((a - mx) ** 2 for a in x); sy = sum((b - my) ** 2 for b in y)
    return sxy / math.sqrt(sx * sy) if sx and sy else None


def spearman(x, y):
    return corr(rank(x), rank(y))


def ols(X, y):
    """Least squares with intercept; returns (coefs, r2)."""
    n, k = len(y), len(X[0]) + 1
    A = [[1.0] + list(r) for r in X]
    N = [[sum(A[i][a] * A[i][b] for i in range(n)) for b in range(k)] for a in range(k)]
    v = [sum(A[i][a] * y[i] for i in range(n)) for a in range(k)]
    for c in range(k):  # Gauss-Jordan
        p = max(range(c, k), key=lambda r: abs(N[r][c])); N[c], N[p] = N[p], N[c]; v[c], v[p] = v[p], v[c]
        for r in range(k):
            if r != c and N[c][c]:
                f = N[r][c] / N[c][c]; N[r] = [a - f * b for a, b in zip(N[r], N[c])]; v[r] -= f * v[c]
    b = [v[i] / N[i][i] for i in range(k)]
    pred = [sum(bb * aa for bb, aa in zip(b, A[i])) for i in range(n)]
    my = sum(y) / n
    r2 = 1 - sum((y[i] - pred[i]) ** 2 for i in range(n)) / sum((t - my) ** 2 for t in y)
    return b, r2


def source(url, R):
    h = url.split('/')[2] if '//' in url else ''
    if 'swisswatchexpo' in h:
        return 'SWE full-size' if R > 500 else 'SWE 900px'
    if 'bobswatches' in h:
        return "Bob's"
    if 'phillips' in h:
        return 'Phillips'
    return 'other'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--per-photo', default=os.path.join(HERE, 'results/ci_run_37500197377/per_photo.csv'))
    ap.add_argument('--dedup', default=os.path.join(HERE, 'results/dedup/photos.csv'))
    ap.add_argument('--level', default='dial')
    ap.add_argument('--catalogue', default=os.path.join(HERE, 'catalogue_provenance_strong.csv'))
    ap.add_argument('--out')
    a = ap.parse_args()
    shared = {r['photo_id'] for r in csv.DictReader(open(a.dedup)) if r[f'shared_{a.level}'] == '1'}
    cat = {r['photo_id']: r for r in csv.DictReader(open(a.catalogue))}
    rows = []
    for r in csv.DictReader(open(a.per_photo)):
        if r['group'] != 'genuine_population' or r['status'] != 'accepted' or r['photo_id'] in shared:
            continue
        R = fl(r['dial_radius_px'])
        errs = [fl(r[f'm{h}_radius_err_px']) for h in ROUNDS if r[f'm{h}_usable'] == 'true']
        errs = [e for e in errs if e is not None]
        d = dict(photo=r['photo_id'], watch=r['physical_watch_id'], model=r['model'], R=R, ell=fl(r['ellipse_ratio']),
                 tick=fl(r['tick_rms_px']), src=source(cat.get(r['photo_id'], {}).get('image_url', ''), R),
                 edge_R=median(errs) / R if errs else None, ring_scale=fl(r['ring_scale_pct']),
                 sha=cat.get(r['photo_id'], {}).get('sha256', ''))
        if r['m12_usable'] == 'true' and fl(r['m12_local_radial_px']) is not None:
            d['m12_rad'] = fl(r['m12_local_radial_px']) / R
            d['m12_tan'] = fl(r['m12_local_tangential_px']) / R
            d['m12_loc'] = fl(r['m12_local_px']) / R
            d['m12_down'] = fl(r['m12_down_px']) / R
            for k in ('left_side_err_deg', 'right_side_err_deg', 'base_tilt_deg', 'rotation_deg'):
                d['m12_' + k] = fl(r['m12_' + k])
        for h in (6, 9):
            if r[f'm{h}_usable'] == 'true' and fl(r[f'm{h}_local_radial_px']) is not None:
                d[f'm{h}_rad'] = fl(r[f'm{h}_local_radial_px']) / R
        rows.append(d)
    m12 = [d for d in rows if 'm12_rad' in d and d['edge_R'] is not None]
    L = ['# What moves the genuine 12-triangle offset? (per photo, offline)', '',
         f'Genuine accepted photos after removing shared photos (level {a.level}): {len(rows)}; with a usable 12 and round '
         f'radius errors: {len(m12)}. Sign: 12 radial + = outward, - = toward the dial centre ("down").', '',
         '## By source (medians over photos)', '',
         '| Source | photos | R px | ellipse ratio | edge (round radius err) R | ring scale % | 12 radial R | 12 tangential R | 12 local R | 6 radial R | 9 radial R |',
         '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
    by = defaultdict(list)
    for d in m12:
        by[d['src']].append(d)
    def md(ds, k, f=4):
        v = [d[k] for d in ds if d.get(k) is not None]
        return f'{median(v):+.{f}f}' if v else '–'
    for s in ("Bob's", 'Phillips', 'SWE 900px', 'SWE full-size', 'other'):
        ds = by.get(s, [])
        if ds:
            L.append(f"| {s} | {len(ds)} | {md(ds, 'R', 0)} | {md(ds, 'ell', 3)} | {md(ds, 'edge_R')} | {md(ds, 'ring_scale', 3)} | "
                     f"{md(ds, 'm12_rad')} | {md(ds, 'm12_tan')} | {md(ds, 'm12_loc')} | {md(ds, 'm6_rad')} | {md(ds, 'm9_rad')} |")
    L += ['', '## Correlations with the 12 radial offset (all photos, then within sources with >= 8 photos)', '',
          '| Predictor | Spearman (all) | ' + ' | '.join(f'{s}' for s in ("Bob's", 'SWE full-size', 'Phillips')) + ' |', '|---|---:|---:|---:|---:|']
    for k, lab in (('edge_R', 'edge (round radius err / R)'), ('ell', 'ellipse ratio'), ('R', 'dial radius px'),
                   ('ring_scale', 'ring scale %'), ('tick', 'tick RMS px')):
        def sp(ds):
            v = [(d[k], d['m12_rad']) for d in ds if d.get(k) is not None]
            return f'{spearman([x for x, _ in v], [y for _, y in v]):+.2f} (n={len(v)})' if len(v) >= 8 else '–'
        L.append(f"| {lab} | {sp(m12)} | " + ' | '.join(sp(by.get(s, [])) for s in ("Bob's", 'SWE full-size', 'Phillips')) + ' |')
    # Regression
    srcs = sorted({d['src'] for d in m12} - {"Bob's"})
    X = [[d['edge_R'], d['ell'] - 1.0] + [1.0 if d['src'] == s else 0.0 for s in srcs] for d in m12]
    b, r2 = ols(X, [d['m12_rad'] for d in m12])
    X0 = [[1.0 if d['src'] == s else 0.0 for s in srcs] for d in m12]
    b0, r20 = ols(X0, [d['m12_rad'] for d in m12])
    Xe = [[d['edge_R'], d['ell'] - 1.0] for d in m12]
    be, r2e = ols(Xe, [d['m12_rad'] for d in m12])
    L += ['', '## Linear model of the 12 radial offset (R units; baseline source = Bob\'s)', '',
          f'- source only: R² = {r20:.2f}; offsets vs Bob\'s: ' + ', '.join(f'{s} {c:+.4f}' for s, c in zip(srcs, b0[1:])),
          f'- edge + ellipse only: R² = {r2e:.2f}; edge coefficient {be[1]:+.3f} (pure outline-shift geometry predicts -0.23), '
          f'ellipse coefficient {be[2]:+.4f} per unit (ellipse ratio - 1)',
          f'- edge + ellipse + source: R² = {r2:.2f}; edge {b[1]:+.3f}, ellipse {b[2]:+.4f}; source offsets left: ' +
          ', '.join(f'{s} {c:+.4f}' for s, c in zip(srcs, b[3:])), '']
    # Same-photograph pairs (SWE full-size vs SWE's own 900 px copy)
    raw = subprocess.check_output(['git', 'show', 'origin/data/harvest:state/images.jsonl'], cwd=HERE).decode()
    dup = {}
    for l in raw.splitlines():
        if l.strip():
            i = json.loads(l)
            if i.get('duplicate_of') and i.get('duplicate_kind') in ('near', 'dial'):
                dup[i['sha256']] = i['duplicate_of']
    bysha = {d['sha']: d for d in rows}
    pairs = []
    for d in rows:
        o = bysha.get(dup.get(d['sha'], ''))
        if o and o['watch'] == d['watch']:
            big, small = (o, d) if o['R'] > d['R'] else (d, o)
            if big['R'] / small['R'] > 1.5:
                pairs.append((big, small))
    L += ['## Same photograph at two resolutions (harvester near/dial copy inside one watch)', '',
          '| Watch | source | R big / small | edge R big / small | ellipse big / small | 12 radial R big / small | 12 tangential R big / small |',
          '|---|---|---:|---:|---:|---:|---:|']
    dr = []
    for big, small in sorted(pairs, key=lambda p: p[0]['watch']):
        f = lambda d, k, n=4: f"{d[k]:+.{n}f}" if d.get(k) is not None else '–'
        L.append(f"| {big['watch']} | {big['src']} | {big['R']:.0f} / {small['R']:.0f} | {f(big, 'edge_R')} / {f(small, 'edge_R')} | "
                 f"{f(big, 'ell', 3)} / {f(small, 'ell', 3)} | {f(big, 'm12_rad')} / {f(small, 'm12_rad')} | {f(big, 'm12_tan')} / {f(small, 'm12_tan')} |")
        if big.get('m12_rad') is not None and small.get('m12_rad') is not None:
            dr.append(big['m12_rad'] - small['m12_rad'])
    if dr:
        L += ['', f'Same-photo change in 12 radial (big - small): median {median(dr):+.4f} R over {len(dr)} pairs '
                  f'(range {min(dr):+.4f}..{max(dr):+.4f}).', '']
    L += recentred(a.per_photo, a.dedup, a.level, a.catalogue)
    text = '\n'.join(L) + '\n'
    if a.out:
        open(a.out, 'w').write(text)
    print(text)



def recentred(per_photo, dedup, level='dial', catalogue=None):
    """RESEARCH: 12 offset measured from the genuine nominal (median signed radial / tangential over watches)
    instead of from the Alpha92 master position. Returns markdown lines. One value per watch (median of its
    photos); replica and candidate photos are compared with that genuine spread, never added to it."""
    shared = {r['photo_id'] for r in csv.DictReader(open(dedup)) if r[f'shared_{level}'] == '1'}
    cat = {r['photo_id']: r for r in csv.DictReader(open(catalogue))}
    W = defaultdict(list); others = []
    for r in csv.DictReader(open(per_photo)):
        if r['status'] != 'accepted' or r['m12_usable'] != 'true' or fl(r['m12_local_radial_px']) is None:
            continue
        R = fl(r['dial_radius_px'])
        v = (fl(r['m12_local_radial_px']) / R, fl(r['m12_local_tangential_px']) / R, R)
        if r['group'] == 'genuine_population':
            if r['photo_id'] not in shared:
                src = source(cat[r['photo_id']]['image_url'], R).replace(' full-size', '').replace(' 900px', '')
                W[(r['physical_watch_id'], src)].append(v)
        else:
            others.append((r['photo_id'], r['group'], v))
    wv = {k: (median(x[0] for x in vs), median(x[1] for x in vs)) for k, vs in W.items()}
    c_rad, c_tan = median(v[0] for v in wv.values()), median(v[1] for v in wv.values())
    dist = sorted(math.hypot(v[0] - c_rad, v[1] - c_tan) for v in wv.values())
    raw = sorted(math.hypot(*v) for v in wv.values())
    q = lambda s, p: s[min(len(s) - 1, int(round(p * (len(s) - 1))))]
    L = ['', '## 12 offset re-centred on the genuine nominal (research; the Alpha92 master is unchanged)', '',
         f'Genuine nominal over {len(wv)} watches: radial {c_rad:+.4f} R (toward the centre), tangential {c_tan:+.4f} R. '
         f'{sum(1 for v in wv.values() if v[0] < 0)}/{len(wv)} watches read the 12 toward the centre.', '',
         '| Genuine per-watch 12 offset | median | P90 | max |', '|---|---:|---:|---:|',
         f'| from the Alpha92 master | {median(raw):.4f} R | {q(raw, .9):.4f} R | {raw[-1]:.4f} R |',
         f'| from the genuine nominal | {median(dist):.4f} R | {q(dist, .9):.4f} R | {dist[-1]:.4f} R |', '',
         '| Photo | group | R px | radial R | tangential R | offset from nominal R | genuine watches at least as far |',
         '|---|---|---:|---:|---:|---:|---:|']
    for pid, g, (rad, tan, R) in others:
        d = math.hypot(rad - c_rad, tan - c_tan)
        ex = sum(1 for x in dist if x >= d)
        L.append(f'| {pid} | {g} | {R:.0f} | {rad:+.4f} | {tan:+.4f} | {d:.4f} | {ex}/{len(dist)} |')
    by = defaultdict(list)
    for (w, s), v in wv.items():
        by[s].append(math.hypot(v[0] - c_rad, v[1] - c_tan))
    L += ['', 'Per source, offset from the pooled genuine nominal (median over watches): ' +
          ', '.join(f'{s} {median(v):.4f} R (n={len(v)})' for s, v in sorted(by.items())), '']
    return L


if __name__ == '__main__':
    main()
