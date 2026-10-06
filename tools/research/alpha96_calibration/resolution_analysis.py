#!/usr/bin/env python3
"""RESEARCH ONLY. Separate resolution / detector noise from geometry in Alpha96 values (offline).

Inputs
  --watch-level   CI watch_level.csv (genuine population). One value per watch is taken from its primary
                  photo (largest dial), whose px and R values come from the same image, so that photo's dial
                  radius is exact: R = px / (px/R).
  --scaled        runner output of make_scaled_set.py (+ its manifest): the same local photos at several
                  scales, each with several resampling variants.
  --local         per_photo.csv of the native local run (replica controls and marketplace candidates).

Outputs (markdown to stdout / --out):
  A. Genuine: does the px value depend on dial radius? (Spearman rho, binned medians)
  B. Scaled experiment: detector noise (SD across resampling variants) per dial radius, and drift of
     the value in R units across scales (geometry stays constant, noise does not).
  C. Each control vs genuine: in px, in R and in degrees, against all genuine watches and against
     genuine watches with a similar dial radius; plus each control value's own resampling noise.
No thresholds are derived. Replica rows never enter a genuine statistic.
"""
import argparse
import csv
import math
import os
from collections import defaultdict
from statistics import median, pstdev

# (label, px metric or None, R metric or None, deg metric or None, local per_photo column, scaled runner column)
FEATURES = [
    ('ring shift', 'ring_shift', 'ring_shift_R', None, 'ring_shift_px', 'ring_shift_px'),
    ('ring rotation', None, None, 'ring_rotation', 'ring_rotation_deg', 'ring_rotation_deg'),
    ('ring scale %', None, None, 'ring_scale', 'ring_scale_pct', 'ring_scale_pct'),
    ('6 rotation', None, None, 'm6_rotation', 'm6_rotation_deg', 'm6_rotation_deg'),
    ('6 local offset', 'm6_local', 'm6_local_R', None, 'm6_local_px', 'm6_local_px'),
    ('9 rotation', None, None, 'm9_rotation', 'm9_rotation_deg', 'm9_rotation_deg'),
    ('9 local offset', 'm9_local', 'm9_local_R', None, 'm9_local_px', 'm9_local_px'),
    ('12 centreline rotation', None, None, 'm12_centreline_rotation', 'm12_rotation_deg', 'm12_rotation_deg'),
    ('12 local offset', None, 'm12_local_R', None, 'm12_local_px', 'm12_local_px'),
    ('rounds max local', 'rounds_max_local', 'rounds_max_local_R', None, 'rounds_max_local_px', None),
]
RADIUS_PAIRS = [('ring_shift', 'ring_shift_R'), ('m6_local', 'm6_local_R'), ('m9_local', 'm9_local_R'),
                ('m6_raw', 'm6_raw_R'), ('m9_raw', 'm9_raw_R'), ('m12_raw', 'm12_raw_R'),
                ('rounds_max_local', 'rounds_max_local_R')]
ROUNDS = (1, 2, 4, 5, 7, 8, 10, 11)


def fl(x):
    try:
        v = float(x)
        return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def rank(xs):
    o = sorted(range(len(xs)), key=lambda i: xs[i])
    r = [0.0] * len(xs)
    i = 0
    while i < len(o):
        j = i
        while j + 1 < len(o) and xs[o[j + 1]] == xs[o[i]]:
            j += 1
        for k in range(i, j + 1):
            r[o[k]] = (i + j) / 2.0
        i = j + 1
    return r


def spearman(x, y):
    if len(x) < 5:
        return None
    rx, ry = rank(x), rank(y)
    mx, my = sum(rx) / len(rx), sum(ry) / len(ry)
    num = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    den = math.sqrt(sum((a - mx) ** 2 for a in rx) * sum((b - my) ** 2 for b in ry))
    return num / den if den else None


def genuine_watches(path, exclude=frozenset()):
    """One row per genuine watch: primary-photo values, keyed by primary photo, with exact R per photo.
    Photos in `exclude` (shared / stock photographs) are dropped; a watch whose primary photo is excluded
    is dropped too, because its next photo's values are not in watch_level.csv (conservative)."""
    rows = [r for r in csv.DictReader(open(path)) if r['group'] == 'genuine_population' and r['primary_photo'] not in exclude]
    w = defaultdict(lambda: {'photos': {}, 'model': ''})
    for r in rows:
        g = w[r['physical_watch_id']]
        g['model'] = r['model']
        v = fl(r['primary_value'])
        if v is not None:
            g['photos'].setdefault(r['primary_photo'], {})[r['metric']] = v
    out = []
    for wid, g in w.items():
        for pid, m in g['photos'].items():
            est = [m[a] / m[b] for a, b in RADIUS_PAIRS if a in m and b and m.get(b)]
            if est:
                m['R'] = median(est)
        # The watch's primary photo is the one with the largest R among those with a known radius.
        cands = [(m['R'], pid, m) for pid, m in g['photos'].items() if 'R' in m]
        if not cands:
            continue
        R, pid, m = max(cands)
        rec = {'watch': wid, 'model': g['model'], 'photo': pid, 'R': R}
        for lab, px, rn, deg, _, _ in FEATURES:
            # Take each feature from the primary photo when it is usable there; otherwise from any photo of
            # that watch with a known radius (still one value per watch).
            src = m if any(k and k in m for k in (px, rn, deg)) else next(
                (mm for _, _, mm in sorted(cands, reverse=True) if any(k and k in mm for k in (px, rn, deg))), None)
            if src is None:
                continue
            Rs = src['R']
            if deg and deg in src:
                rec[lab] = ('deg', src[deg], Rs)
            elif px and px in src:
                rec[lab] = ('px', src[px], Rs)
            elif rn and rn in src:
                rec[lab] = ('px', src[rn] * Rs, Rs)
        out.append(rec)
    return out


def local_rows(path):
    out = []
    for r in csv.DictReader(open(path)):
        if r.get('status') != 'accepted':
            continue
        R = fl(r['dial_radius_px'])
        rec = {'photo': r['photo_id'], 'group': r['group'], 'R': R}
        for lab, px, rn, deg, col, _ in FEATURES:
            v = fl(r.get(col))
            if v is not None:
                rec[lab] = ('deg' if deg else 'px', v, R)
        out.append(rec)
    return out


def scaled_rows(manifest, runner):
    meta = {r['local_path']: r for r in csv.DictReader(open(manifest))}
    out = []
    for r in csv.DictReader(open(runner)):
        m = meta.get(r['path'])
        if not m:
            continue
        rec = {'base': m['base_photo_id'], 'group': m['group'], 'scale': float(m['scale']), 'jitter': int(m['jitter']),
               'status': r['status'], 'R': fl(r['dial_radius_px'])}
        if r['status'] == 'accepted':
            for lab, px, rn, deg, _, col in FEATURES:
                if col:
                    v = fl(r.get(col))
                elif lab == 'rounds max local':
                    vs = [fl(r.get(f'm{h}_local_px')) for h in ROUNDS if r.get(f'm{h}_usable') == 'true']
                    vs = [x for x in vs if x is not None]
                    v = max(vs) if vs else None
                else:
                    v = None
                if v is not None:
                    rec[lab] = v
        out.append(rec)
    return out


def fmt(v, d=3):
    return '–' if v is None else f'{v:.{d}f}'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--watch-level', required=True)
    ap.add_argument('--local', required=True)
    ap.add_argument('--scaled-manifest', required=True)
    ap.add_argument('--scaled', required=True)
    ap.add_argument('--match', type=float, default=0.30, help='R-matched genuine: |R/Rc - 1| <= match')
    ap.add_argument('--out')
    ap.add_argument('--dedup', help='dedup_units.py photos.csv: exclude photos shared between catalogue watches')
    ap.add_argument('--dedup-level', default='dial', choices=['exact', 'near', 'dial', 'possible'])
    a = ap.parse_args()
    exclude = frozenset()
    if a.dedup:
        exclude = frozenset(r['photo_id'] for r in csv.DictReader(open(a.dedup)) if r[f'shared_{a.dedup_level}'] == '1')
    gen = genuine_watches(a.watch_level, exclude)
    loc = local_rows(a.local)
    sc = scaled_rows(a.scaled_manifest, a.scaled)
    L = ['# Alpha96: pixel and dial-radius comparison (research only, offline)', '',
         'Genuine: one primary photo per provenance-strong watch (CI run 37500197377), with the exact dial radius R of that photo.',
         'Scaled experiment: the 8 local photos re-measured at several scales, with resampling variants at each scale.',
         'No thresholds are derived; replica rows never enter a genuine statistic.', '']
    if a.dedup:
        L += [f'De-duplicated at level **{a.dedup_level}**: {len(exclude)} shared / stock photos excluded '
              f'(`{os.path.basename(a.dedup)}`).', '']
    Rs = sorted(g['R'] for g in gen)
    L += [f'Genuine watches: {len(gen)}; dial radius R px: min {Rs[0]:.0f}, median {median(Rs):.0f}, max {Rs[-1]:.0f}; '
          f'{sum(r <= 240 for r in Rs)} watches with R <= 240 px.', '']

    # ---- A. genuine dependence on R
    L += ['## A. Genuine: does the value depend on dial radius?', '',
          'Spearman rho of |value| against R (one photo per watch). Negative rho in px would mean more detector noise on '
          'smaller dials; rho near 0 in px means the px value is resolution-independent, so R units penalise small dials.', '',
          '| Feature | unit | n | rho(|value|, R) | median |v| R<=240 | median |v| R 240-360 | median |v| R>360 |', '|---|---|---:|---:|---:|---:|---:|']
    for lab, *_ in FEATURES:
        pts = [(g[lab][2], abs(g[lab][1]), g[lab][0]) for g in gen if lab in g]
        if not pts:
            continue
        unit = '%' if lab == 'ring scale %' else pts[0][2]
        rho = spearman([p[0] for p in pts], [p[1] for p in pts])
        bins = [[p[1] for p in pts if lo < p[0] <= hi] for lo, hi in ((0, 240), (240, 360), (360, 1e9))]
        L.append(f'| {lab} | {unit} | {len(pts)} | {fmt(rho, 2)} | ' + ' | '.join(
            f'{fmt(median(b))} (n={len(b)})' if b else '–' for b in bins) + ' |')
        if unit == 'px':
            ptsR = [(p[0], p[1] / p[0]) for p in pts]
            rhoR = spearman([p[0] for p in ptsR], [p[1] for p in ptsR])
            binsR = [[p[1] for p in ptsR if lo < p[0] <= hi] for lo, hi in ((0, 240), (240, 360), (360, 1e9))]
            L.append(f'| {lab} | R | {len(pts)} | {fmt(rhoR, 2)} | ' + ' | '.join(
                f'{fmt(median(b), 4)} (n={len(b)})' if b else '–' for b in binsR) + ' |')

    # ---- B. scaled experiment
    by = defaultdict(list)
    for r in sc:
        by[(r['base'], r['scale'])].append(r)
    bases = sorted({r['base'] for r in sc})
    scales = sorted({r['scale'] for r in sc}, reverse=True)
    L += ['', '## B. Scaled experiment: detector noise and drift with dial radius', '',
          'Pose acceptance by scale (all 8 photos x variants): ' + ', '.join(
              f"{s:.2f}: {sum(1 for r in sc if r['scale'] == s and r['status'] == 'accepted')}/{sum(1 for r in sc if r['scale'] == s)}"
              for s in scales), '',
          'Noise = SD of the value across resampling variants of the same photo at the same scale (same physical geometry). '
          'Median over photos. Drift = median over photos of |value(smallest accepted scale) - value(native)| in R units for '
          'offsets, degrees for rotations, against the native noise.', '',
          '| Feature | unit | noise at native (median R ' + f"{median([r['R'] for r in sc if r['scale'] == 1.0 and r['R']]):.0f} px)" +
          ' | noise at 0.7 | noise at 0.45 | noise in R units native | noise in R units 0.45 | drift (R units or deg) |', '|---|---|---:|---:|---:|---:|---:|---:|']
    noise_native = {}
    for lab, px, rn, deg, _, _ in FEATURES:
        unit = '%' if lab == 'ring scale %' else ('deg' if deg else 'px')
        per_scale = {}
        per_scale_R = {}
        for s in scales:
            sds, sdsR = [], []
            for b in bases:
                vals = [(r[lab], r['R']) for r in by[(b, s)] if lab in r]
                if len(vals) >= 3:
                    sds.append(pstdev([v for v, _ in vals]))
                    sdsR.append(pstdev([v / R for v, R in vals]) if unit == 'px' else None)
                    if s == 1.0:
                        noise_native[(b, lab)] = pstdev([v for v, _ in vals])
            per_scale[s] = median(sds) if sds else None
            per_scale_R[s] = median([x for x in sdsR if x is not None]) if sdsR and unit == 'px' else None
        drifts = []
        for b in bases:
            nat = [r[lab] / (r['R'] if unit == 'px' else 1) for r in by[(b, 1.0)] if lab in r]
            acc = [s for s in scales if sum(1 for r in by[(b, s)] if lab in r) >= 3]
            if nat and acc and min(acc) < 1.0:
                lo = [r[lab] / (r['R'] if unit == 'px' else 1) for r in by[(b, min(acc))] if lab in r]
                drifts.append(abs(median(lo) - median(nat)))
        L.append(f"| {lab} | {unit} | {fmt(per_scale.get(1.0))} | {fmt(per_scale.get(0.7))} | {fmt(per_scale.get(0.45))} | "
                 f"{fmt(per_scale_R.get(1.0), 4)} | {fmt(per_scale_R.get(0.45), 4)} | "
                 f"{fmt(median(drifts), 4 if unit == 'px' else 3) if drifts else '–'} |")

    L += ['', 'Per-photo median value at each scale (R units for offsets, degrees or % otherwise; – = fewer than 3 '
          'variants usable). Geometry stays constant down the row; resolution artefacts drift.', '']
    for lab, px, rn, deg, _, _ in FEATURES:
        unit = '%' if lab == 'ring scale %' else ('deg' if deg else 'R')
        L += [f'**{lab}** ({unit})', '', '| Photo | ' + ' | '.join(f'{s:.2f}' for s in scales) + ' |',
              '|---|' + '---:|' * len(scales)]
        for b in bases:
            cells = []
            for s in scales:
                v = [r[lab] / (r['R'] if unit == 'R' else 1) for r in by[(b, s)] if lab in r]
                cells.append(f'{median(v):+.4f}' if len(v) >= 3 else '–')
            L.append(f'| {b} | ' + ' | '.join(cells) + ' |')
        L.append('')

    # ---- C. controls vs genuine
    L += ['', '## C. Each local photo against genuine', '',
          'Exceedance = share of genuine watches whose |value| is at least the photo\'s |value| (0% = beyond every genuine watch). '
          f'Matched = genuine watches with dial radius within ±{a.match:.0%} of the photo\'s. SNR = |value| / resampling noise of '
          'this photo at native scale.', '']
    for grp, title in (('rl_control', 'Replica regression controls (validation only)'),
                       ('gen_candidate', 'Marketplace genuine candidates (descriptive only)')):
        L += [f'### {title}', '', '| Photo | R px | Feature | value | unit | all genuine: exceed (n) | R-matched: exceed (n) | '
              'in R units: all / matched | SNR |', '|---|---:|---|---:|---|---:|---:|---:|---:|']
        for c in [x for x in loc if x['group'] == grp]:
            for lab, *_ in FEATURES:
                if lab not in c:
                    continue
                unit, v, R = c[lab]
                pts = [(g[lab][1], g[lab][2]) for g in gen if lab in g]
                mt = [p for p in pts if abs(p[1] / R - 1) <= a.match]
                ex = lambda ps, f: (sum(1 for p in ps if f(p) >= f((v, R)) - 1e-12) / len(ps), len(ps)) if ps else (None, 0)
                ea, na = ex(pts, lambda p: abs(p[0]))
                em, nm = ex(mt, lambda p: abs(p[0]))
                if unit == 'px':
                    eaR, _ = ex(pts, lambda p: abs(p[0]) / p[1])
                    emR, _ = ex(mt, lambda p: abs(p[0]) / p[1])
                    rcol = f"{fmt(eaR * 100 if eaR is not None else None, 0)}% / {fmt(emR * 100 if emR is not None else None, 0)}%"
                else:
                    rcol = 'n/a'
                nz = noise_native.get((c['photo'], lab))
                snr = abs(v) / nz if nz else None
                flag = ' **' if (em is not None and em == 0 and nm >= 5) else ''
                L.append(f"| {c['photo']} | {R:.0f} | {lab}{flag} | {v:+.3f} | {unit if lab != 'ring scale %' else '%'} | "
                         f"{fmt(ea * 100 if ea is not None else None, 0)}% ({na}) | {fmt(em * 100 if em is not None else None, 0)}% ({nm}) | "
                         f"{rcol} | {fmt(snr, 1)} |")
        L.append('')
    L += ['** = beyond every R-matched genuine watch (at least 5 matched watches).', '']
    text = '\n'.join(L) + '\n'
    if a.out:
        open(a.out, 'w').write(text)
    print(text)


if __name__ == '__main__':
    main()
