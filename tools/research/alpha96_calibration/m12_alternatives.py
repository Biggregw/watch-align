#!/usr/bin/env python3
"""RESEARCH ONLY. Which 12-triangle measure is robust to studio lighting? (offline)

Candidate per-photo measures, all re-centred on the genuine nominal (median over watches; leave-one-watch-out for
genuine scoring):
  offset2d   current: 12 local offset from the nominal (radial + tangential)          [baseline]
  radial     |radial - nominal|      (moved by unequal side vs base edge selection)
  lateral    |tangential - nominal|  (left/right position of the triangle; unaffected by base vs side offsets
                                      and by equal left/right offsets)
  centreline |centreline rotation - nominal|  (angle only; unaffected by any parallel side offset)
  sides      max(|left side err - nominal|, |right side err - nominal|)  (angles only)
  apex       |(right - left)/2 - nominal|  (apex half-angle: shape, angles only)
Scores:
  source shift  (median of source - median of Bob's) / genuine between-watch MAD, on the signed value: how much the
                photographic source alone moves the measure, in units of genuine spread (lower = more robust)
  genuine spread  leave-one-watch-out median / P90 / max over watches
  controls      value and genuine watches at least as far (separation)
  resolution    median over local photos of SD across scales 1.0-0.7 (same photograph), / genuine between-watch MAD
  repeat        median within-watch MAD (watches with >= 3 photos) / genuine between-watch MAD
Optional --diag (M12Diag CSV): per-side consistency gate, i.e. how the source shift of 'radial' changes when photos whose
(long sides - base) edge difference is far from the genuine median are withheld (curve, no limit chosen).
"""
import argparse
import csv
import math
import os
from collections import defaultdict
from statistics import median

HERE = os.path.dirname(os.path.abspath(__file__))
SIGNED = {'radial': 'rad', 'lateral': 'tan', 'centreline': 'rot', 'apex': 'apex'}


def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def mad(v):
    m = median(v); return median(abs(x - m) for x in v)


def q(s, p):
    s = sorted(s); k = (len(s) - 1) * p; lo = int(k); hi = min(lo + 1, len(s) - 1); return s[lo] + (s[hi] - s[lo]) * (k - lo)


def src_of(url):
    h = url.split('/')[2] if '//' in url else ''
    return 'SWE' if 'swisswatchexpo' in h else "Bob's" if 'bobswatches' in h else 'Phillips' if 'phillips' in h else 'other'


def raw(r):
    R = fl(r.get('dial_radius_px'))
    if r.get('status') != 'accepted' or r.get('m12_usable') != 'true' or not R or fl(r.get('m12_local_radial_px')) is None:
        return None
    L, Rt = fl(r['m12_left_side_err_deg']), fl(r['m12_right_side_err_deg'])
    return dict(R=R, rad=fl(r['m12_local_radial_px']) / R, tan=fl(r['m12_local_tangential_px']) / R, rot=fl(r['m12_rotation_deg']),
                left=L, right=Rt, apex=(Rt - L) / 2)


def nominal(ws):
    return {k: median(w[k] for w in ws) for k in ('rad', 'tan', 'rot', 'left', 'right', 'apex')}


def measures(v, n):
    return {'offset2d': math.hypot(v['rad'] - n['rad'], v['tan'] - n['tan']), 'radial': abs(v['rad'] - n['rad']),
            'lateral': abs(v['tan'] - n['tan']), 'centreline': abs(v['rot'] - n['rot']),
            'sides': max(abs(v['left'] - n['left']), abs(v['right'] - n['right'])), 'apex': abs(v['apex'] - n['apex'])}


UNIT = {'offset2d': 'R', 'radial': 'R', 'lateral': 'R', 'centreline': 'deg', 'sides': 'deg', 'apex': 'deg'}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--per-photo', default=os.path.join(HERE, 'results/ci_run_37500197377/per_photo.csv'))
    ap.add_argument('--dedup', default=os.path.join(HERE, 'results/dedup/photos.csv'))
    ap.add_argument('--catalogue', default=os.path.join(HERE, 'catalogue_provenance_strong.csv'))
    ap.add_argument('--local', default=os.path.join(HERE, 'results/runner_local.csv'))
    ap.add_argument('--local-manifest', default=os.path.join(HERE, 'manifest_local.csv'))
    ap.add_argument('--scaled', default=os.path.join(HERE, 'results/resolution/runner_scaled.csv'))
    ap.add_argument('--scaled-manifest', default=os.path.join(HERE, 'results/resolution/manifest_scaled.csv'))
    ap.add_argument('--diag', default=os.path.join(HERE, 'results/swe_effect/ci_run_37508028297/m12diag.csv'))
    ap.add_argument('--out', default=os.path.join(HERE, 'results/m12_alternatives/report.md'))
    a = ap.parse_args()
    shared = {r['photo_id'] for r in csv.DictReader(open(a.dedup)) if r['shared_dial'] == '1'}
    cat = {r['photo_id']: r for r in csv.DictReader(open(a.catalogue))}
    photos = defaultdict(list)
    for r in csv.DictReader(open(a.per_photo)):
        if r['group'] != 'genuine_population' or r['photo_id'] in shared:
            continue
        v = raw(r)
        if v:
            v['src'] = src_of(cat[r['photo_id']]['image_url']); v['photo'] = r['photo_id']
            photos[r['physical_watch_id']].append(v)
    W = []
    for wid, vs in photos.items():
        w = {k: median(x[k] for x in vs) for k in ('rad', 'tan', 'rot', 'left', 'right', 'apex')}
        w.update(watch=wid, src=vs[0]['src'], photos=vs); W.append(w)
    nom = nominal(W)
    lowo = {w['watch']: measures(w, nominal([x for x in W if x is not w])) for w in W}
    names = list(UNIT)
    # source shift on signed values (per watch), in units of between-watch MAD
    sig = {}
    for m, k in SIGNED.items():
        allv = [w[k] for w in W]; s = mad(allv) or 1e-12
        bob = median(w[k] for w in W if w['src'] == "Bob's")
        sig[m] = {src: (median(w[k] for w in W if w['src'] == src) - bob) / s for src in ('Phillips', 'SWE')}
    # magnitude measures: source shift of medians, in units of genuine MAD of the measure
    mag_shift = {}
    for m in names:
        allv = [lowo[w['watch']][m] for w in W]; s = mad(allv) or 1e-12
        bob = median(lowo[w['watch']][m] for w in W if w['src'] == "Bob's")
        mag_shift[m] = {src: (median(lowo[w['watch']][m] for w in W if w['src'] == src) - bob) / s for src in ('Phillips', 'SWE')}
    # within-watch repeatability
    rep = {}
    for m in names:
        within = [mad([measures(p, nom)[m] for p in w['photos']]) for w in W if len(w['photos']) >= 3]
        between = mad([lowo[w['watch']][m] for w in W]) or 1e-12
        rep[m] = (median(within) / between if within else None, len(within))
    # resolution stability (same photograph, scales 1.0..0.7)
    smeta = {r['local_path']: r for r in csv.DictReader(open(a.scaled_manifest))}
    per = defaultdict(lambda: defaultdict(list))
    for r in csv.DictReader(open(a.scaled)):
        m = smeta.get(r['path'])
        if not m or float(m['scale']) < 0.7:
            continue
        v = raw(r)
        if v:
            for k, x in measures(v, nom).items():
                per[k][m['base_photo_id']].append(x)
    res = {}
    for m in names:
        sds = []
        for b, xs in per[m].items():
            if len(xs) >= 5:
                mu = sum(xs) / len(xs); sds.append(math.sqrt(sum((x - mu) ** 2 for x in xs) / len(xs)))
        between = mad([lowo[w['watch']][m] for w in W]) or 1e-12
        res[m] = (median(sds) / between if sds else None, len(sds))
    # controls
    lm = {r['photo_id']: r for r in csv.DictReader(open(a.local_manifest))}
    ctrl = []
    for r in csv.DictReader(open(a.local)):
        v = raw(r)
        if v:
            ctrl.append((r['photo_id'], lm[r['photo_id']]['group'], measures(v, nom)))
    L = ['# Lighting-robust 12-triangle measures (research only, offline)', '',
         f'Genuine: {len(W)} physical watches (shared photos excluded), sources ' +
         ', '.join(f"{s} {sum(1 for w in W if w['src'] == s)}" for s in ("Bob's", 'Phillips', 'SWE', 'other')) +
         '. Values re-centred on the genuine nominal; genuine scored leave-one-watch-out. No limits are derived.', '',
         '## Robustness scores', '',
         '| Measure | unit | source shift vs Bob\'s, signed (Phillips / SWE), in genuine MADs | source shift of magnitude (Phillips / SWE) | '
         'resolution SD / genuine MAD (photos) | within-watch MAD / between-watch MAD (watches) | genuine median / P90 / max |',
         '|---|---|---:|---:|---:|---:|---:|']
    for m in names:
        g = sorted(lowo[w['watch']][m] for w in W); d = 4 if UNIT[m] == 'R' else 2
        sg = f"{sig[m]['Phillips']:+.2f} / {sig[m]['SWE']:+.2f}" if m in sig else 'n/a (magnitude only)'
        L.append(f"| {m} | {UNIT[m]} | {sg} | {mag_shift[m]['Phillips']:+.2f} / {mag_shift[m]['SWE']:+.2f} | "
                 f"{res[m][0]:.2f} ({res[m][1]}) | {rep[m][0]:.2f} ({rep[m][1]}) | "
                 f"{median(g):.{d}f} / {q(g, .9):.{d}f} / {g[-1]:.{d}f} |")
    L += ['', '## Local photos: value and genuine watches at least as far (leave-one-watch-out spread)', '',
          '| Photo | group | ' + ' | '.join(names) + ' |', '|---|---|' + '---:|' * len(names)]
    for pid, grp, ms in ctrl:
        cells = []
        for m in names:
            g = [lowo[w['watch']][m] for w in W]; d = 4 if UNIT[m] == 'R' else 2
            cells.append(f"{ms[m]:.{d}f} ({sum(1 for x in g if x >= ms[m])}/{len(g)})")
        L.append(f'| {pid} | {grp} | ' + ' | '.join(cells) + ' |')
    # per-side consistency gate (M12Diag)
    if a.diag and os.path.exists(a.diag):
        D = []
        for r in csv.DictReader(open(a.diag)):
            if r['status'] != 'accepted' or r['photo_id'] in shared or not r['side_left_R'] or not r['m12_local_radial_px']:
                continue
            smb = (float(r['side_left_R']) + float(r['side_right_R'])) / 2 - float(r['side_base_R'])
            D.append(dict(src=src_of(cat[r['photo_id']]['image_url']), smb=smb, lr=float(r['side_left_R']) - float(r['side_right_R']),
                          rad=float(r['m12_local_radial_px']) / float(r['R'])))
        c = median(d['smb'] for d in D); s = mad([d['smb'] for d in D])
        L += ['', '## Per-side consistency gate (M12Diag, CI run 37508028297; curve only, no gate chosen)', '',
              f'Inconsistency = |(long sides - base) - genuine median {c:+.4f} R| in units of its genuine MAD ({s:.4f} R). '
              'Withholding photos above k MADs: share of each source kept, and the remaining radial-offset source shift.', '',
              '| k | kept: all / Bob\'s / Phillips / SWE | 12 radial median: Bob\'s / Phillips / SWE | genuine 12 radial MAD |', '|---:|---:|---:|---:|']
        for k in (99, 3, 2, 1.5, 1):
            kept = [d for d in D if abs(d['smb'] - c) <= k * s]
            def share(src):
                n = [d for d in D if d['src'] == src]; return f"{sum(1 for d in kept if d['src'] == src)}/{len(n)}"
            def mrad(src):
                v = [d['rad'] for d in kept if d['src'] == src]; return f'{median(v):+.4f}' if v else '–'
            L.append(f"| {k if k < 99 else 'none'} | {len(kept)}/{len(D)} / {share(chr(66) + 'ob' + chr(39) + 's')} / {share('Phillips')} / {share('SWE')} | "
                     f"{mrad(chr(66) + 'ob' + chr(39) + 's')} / {mrad('Phillips')} / {mrad('SWE')} | {mad([d['rad'] for d in kept]):.4f} |")
    L += ['']
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    open(a.out, 'w').write('\n'.join(L) + '\n')
    print('\n'.join(L))


if __name__ == '__main__':
    main()
