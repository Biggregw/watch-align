#!/usr/bin/env python3
"""RESEARCH ONLY. Genuine-calibrated nominal for the 12 triangle, with held-out checks (offline).

The Alpha92 master puts the 12 triangle where genuine watches do not: 58/59 genuine watches read it toward the
dial centre and slightly wider. This derives a genuine nominal correction from per-photo CI data:
  one value per physical watch (median of its photos), shared / stock photos excluded (dedup level),
  nominal = median over watches of each signed quantity:
    radial_R, tangential_R   12 local offset components / dial radius (radial + = outward)
    left_side_deg, right_side_deg, base_tilt_deg, rotation_deg   triangle shape / centreline
and writes it as a properties file read by the harness prototype (Alpha97TriangleNominal).

Checks, reported in markdown:
  in-sample               re-centred genuine spread with the nominal from all watches
  leave-one-watch-out     each watch re-centred with a nominal computed without it (no self-grading)
  leave-one-source-out    each source re-centred with a nominal from the other sources only
  bootstrap (watches)     95% interval of the nominal itself
Replica / candidate photos are compared with the leave-one-watch-out genuine spread and never enter the nominal.
"""
import argparse
import csv
import math
import os
import random
from collections import defaultdict
from statistics import median

HERE = os.path.dirname(os.path.abspath(__file__))
KEYS = [('radial_R', 'm12_local_radial_px', True), ('tangential_R', 'm12_local_tangential_px', True),
        ('left_side_deg', 'm12_left_side_err_deg', False), ('right_side_deg', 'm12_right_side_err_deg', False),
        ('base_tilt_deg', 'm12_base_tilt_deg', False), ('rotation_deg', 'm12_rotation_deg', False)]


def fl(x):
    try:
        v = float(x)
        return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def src_of(url, R):
    h = url.split('/')[2] if '//' in url else ''
    return 'SWE' if 'swisswatchexpo' in h else "Bob's" if 'bobswatches' in h else 'Phillips' if 'phillips' in h else 'other'


def photo_values(r):
    R = fl(r['dial_radius_px'])
    if r['status'] != 'accepted' or r['m12_usable'] != 'true' or not R or fl(r['m12_local_radial_px']) is None:
        return None
    v = {'R': R}
    for k, col, per_r in KEYS:
        x = fl(r[col])
        if x is None:
            return None
        v[k] = x / R if per_r else x
    return v


def nominal(watches):
    return {k: median(w[k] for w in watches) for k, _, _ in KEYS}


def offset(v, nom):
    return math.hypot(v['radial_R'] - nom['radial_R'], v['tangential_R'] - nom['tangential_R'])


def q(s, p):
    s = sorted(s)
    k = (len(s) - 1) * p
    lo = int(math.floor(k)); hi = min(lo + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (k - lo)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--per-photo', default=os.path.join(HERE, 'results/ci_run_37500197377/per_photo.csv'))
    ap.add_argument('--dedup', default=os.path.join(HERE, 'results/dedup/photos.csv'))
    ap.add_argument('--level', default='dial')
    ap.add_argument('--exclude-source', action='append', default=['SWE'],
                    help="sources left out of the genuine reference (default: SWE, whose studio lighting moves the 12 and "
                         "whose catalogued images no longer verify); pass --exclude-source none to keep all")
    ap.add_argument('--catalogue', default=os.path.join(HERE, 'catalogue_provenance_strong.csv'))
    ap.add_argument('--properties', default=os.path.join(HERE, 'm12_nominal.properties'))
    ap.add_argument('--out', default=os.path.join(HERE, 'results/m12_nominal/calibration.md'))
    ap.add_argument('--priority', default=os.path.join(HERE, 'priority_genuine.csv'),
                    help="owner-priority genuine photos (official / Rolex CPO images supplied by the owner); rows with include=yes "
                         "are always part of the reference, labelled 'Owner priority', and never removed by --exclude-source")
    ap.add_argument('--priority-runner', default=os.path.join(HERE, 'results/priority_genuine_runner.csv'))
    ap.add_argument('--no-priority', action='store_true')
    ap.add_argument('--reference', default=os.path.join(HERE, 'm12_genuine_reference.csv'),
                    help='per-watch leave-one-watch-out component values for the angles + lateral prototype')
    a = ap.parse_args()
    excluded = set() if 'none' in a.exclude_source else set(a.exclude_source)
    shared = {r['photo_id'] for r in csv.DictReader(open(a.dedup)) if r[f'shared_{a.level}'] == '1'}
    cat = {r['photo_id']: r for r in csv.DictReader(open(a.catalogue))}
    per_watch = defaultdict(list); others = []
    for r in csv.DictReader(open(a.per_photo)):
        v = photo_values(r)
        if v is None:
            continue
        if r['group'] == 'genuine_population':
            if r['photo_id'] not in shared:
                v['src'] = src_of(cat[r['photo_id']]['image_url'], v['R'])
                if v['src'] not in excluded:
                    per_watch[r['physical_watch_id']].append(v)
        else:
            v['photo'] = r['photo_id']; v['group'] = r['group']
            others.append(v)
    n_priority = 0
    if not a.no_priority and os.path.exists(a.priority) and os.path.exists(a.priority_runner):
        pri = {r['photo_id']: r for r in csv.DictReader(open(a.priority)) if r['include'] == 'yes'}
        for r in csv.DictReader(open(a.priority_runner)):
            if r['photo_id'] not in pri:
                continue
            v = photo_values(r)
            if v is None:
                continue
            v['src'] = 'Owner priority'
            wid = pri[r['photo_id']]['physical_watch_id']
            assert wid not in per_watch, f'priority watch id collides with catalogue: {wid}'
            per_watch[wid].append(v); n_priority += 1
    W = []
    for wid, vs in sorted(per_watch.items()):
        w = {k: median(x[k] for x in vs) for k, _, _ in KEYS}
        w.update(watch=wid, src=vs[0]['src'], n=len(vs))
        W.append(w)
    nom = nominal(W)

    # held-out checks
    master = [math.hypot(w['radial_R'], w['tangential_R']) for w in W]
    insample = [offset(w, nom) for w in W]
    lowo = [offset(w, nominal([x for x in W if x is not w])) for w in W]
    loso = defaultdict(list); loso_nom = {}
    for s in sorted({w['src'] for w in W}):
        rest = [x for x in W if x['src'] != s]
        if not rest:                      # single-source catalogue (e.g. 126610: Bob's only): no leave-one-source-out
            continue
        loso_nom[s] = nominal(rest)
        loso[s] = [offset(w, loso_nom[s]) for w in W if w['src'] == s]
    rnd = random.Random(12)
    boots = [nominal([rnd.choice(W) for _ in W]) for _ in range(2000)]

    # Per-watch genuine context for the angles + lateral prototype: each watch's components re-centred on a nominal
    # computed without that watch (no self-grading). Used only to report how many genuine watches read at least as far.
    with open(a.reference, 'w', newline='') as fh:
        wr = csv.writer(fh)
        wr.writerow(['physical_watch_id', 'source', 'lateral_R', 'radial_R', 'centreline_deg', 'sides_deg'])
        for w in W:
            n = nominal([x for x in W if x is not w])
            wr.writerow([w['watch'], w['src'], f"{abs(w['tangential_R'] - n['tangential_R']):.6f}",
                         f"{abs(w['radial_R'] - n['radial_R']):.6f}", f"{abs(w['rotation_deg'] - n['rotation_deg']):.6f}",
                         f"{max(abs(w['left_side_deg'] - n['left_side_deg']), abs(w['right_side_deg'] - n['right_side_deg'])):.6f}"])
    with open(a.properties, 'w') as fh:
        fh.write('# RESEARCH ONLY. Genuine-calibrated 12-triangle nominal, relative to the Alpha92 master.\n'
                 f'# Written by calibrate_m12_nominal.py from {os.path.relpath(a.per_photo, HERE)}; dedup level {a.level}; '
                 f'{len(W)} physical watches, one value each (median of photos); sources excluded: '
                 f"{', '.join(sorted(excluded)) or 'none'}.\n"
                 '# radial_R / tangential_R: 12 local offset components in units of dial radius (radial + = outward).\n'
                 '# *_deg: triangle side errors, base tilt and centreline rotation (+ = clockwise).\n')
        fh.write(f'n_watches={len(W)}\n')
        for k, _, _ in KEYS:
            fh.write(f'{k}={nom[k]:.6f}\n')

    sl = lambda xs: f'{median(xs):.4f} / {q(xs, .9):.4f} / {max(xs):.4f}'
    L = ['# 12-triangle nominal calibrated on genuine watches (research only)', '',
         f'{len(W)} physical watches (shared / stock photos excluded at level {a.level}; sources excluded: '
         f"{', '.join(sorted(excluded)) or 'none'}), one value per watch. "
         f'Sources: ' + ', '.join(f"{s} {sum(1 for w in W if w['src'] == s)}" for s in sorted({w['src'] for w in W})) + '.', '',
         '## Nominal (correction relative to the Alpha92 master) with bootstrap 95% interval over watches', '',
         '| Quantity | nominal | 95% interval |', '|---|---:|---:|']
    for k, _, per_r in KEYS:
        b = sorted(x[k] for x in boots)
        d = 4 if per_r else 3
        L.append(f'| {k} | {nom[k]:+.{d}f} | {q(b, .025):+.{d}f} .. {q(b, .975):+.{d}f} |')
    L += ['', '## Genuine 12 offset (R units): median / P90 / max over watches', '',
          '| Reference | median / P90 / max |', '|---|---:|',
          f'| Alpha92 master (as shipped) | {sl(master)} |',
          f'| genuine nominal, in-sample | {sl(insample)} |',
          f'| genuine nominal, leave-one-watch-out | {sl(lowo)} |', '',
          '## Leave-one-source-out (nominal from the other sources only)', '',
          '| Held-out source | watches | nominal radial / tangential from the rest | held-out offset median / P90 / max |',
          '|---|---:|---:|---:|']
    for s, xs in sorted(loso.items()):
        L.append(f"| {s} | {len(xs)} | {loso_nom[s]['radial_R']:+.4f} / {loso_nom[s]['tangential_R']:+.4f} | {sl(xs)} |")
    L += ['', '## Shape after re-centring (degrees, median |value| over watches: master -> genuine nominal)', '']
    for k, _, per_r in KEYS:
        if not per_r:
            L.append(f"- {k}: {median(abs(w[k]) for w in W):.3f} -> {median(abs(w[k] - nom[k]) for w in W):.3f}")
    L += ['', '## Local photos against the leave-one-watch-out genuine spread', '',
          '| Photo | group | R px | offset from master R | offset from genuine nominal R | genuine watches at least as far (LOWO) | '
          'centreline rot re-centred deg | left / right side re-centred deg |', '|---|---|---:|---:|---:|---:|---:|---:|']
    for v in others:
        d = offset(v, nom)
        L.append(f"| {v['photo']} | {v['group']} | {v['R']:.0f} | {math.hypot(v['radial_R'], v['tangential_R']):.4f} | {d:.4f} | "
                 f"{sum(1 for x in lowo if x >= d)}/{len(lowo)} | {v['rotation_deg'] - nom['rotation_deg']:+.2f} | "
                 f"{v['left_side_deg'] - nom['left_side_deg']:+.2f} / {v['right_side_deg'] - nom['right_side_deg']:+.2f} |")
    L += ['', 'No limits are derived. The Alpha92 master and production code are unchanged; the nominal is applied only by the '
          'harness prototype (`tools/desktop-harness/drivers/Alpha97TriangleNominal.java`).', '']
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    open(a.out, 'w').write('\n'.join(L) + '\n')
    print('\n'.join(L))


if __name__ == '__main__':
    main()
