"""Systematic offsets in the MarkerGeometry readings: their cause, and held-out tests of corrections (research only).

    python -m tools.research.genuine_calibration.offsets GCAL_DIR [--json out.json] [--md out.md]

1. GMT ring scale. Is the -0.19 % a reference error, a photographic bias or a genuine model difference? Evidence: the
   offset per variant, dealer and photo; its dependence on tilt and resolution; and the same quantity measured against an
   independent reference (the Alpha94 luminous ring fit against the luminous master).
2. Per-hour marker offsets (sideways / in-out of each clock position against the leave-one-out ring). Reproducibility
   between disjoint halves of the watches, between dealers and between low- and high-tilt photos.
3. Corrections, tested only on watches they were not estimated from (5-fold over watches, and the locked holdout):
   ring-scale centre per family, per-hour offsets, and both. The outcome is the held-out spread and the whole-watch false
   alarms of the shared eight-reading structure at the 5 % and 1 % operating points, k and scales from training only.

Nothing is changed in the app or its reference geometry.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from collections import defaultdict

import numpy as np

from tools.image_archive.faceon import REQUIRED
from tools.research.measurement_architecture import methods as MM
from . import analyse as A

SCHEME = A.SCHEMES['shared']
RNG = np.random.default_rng(20261023)


def tilt_of(H):
    return math.degrees(MM.pose(H)[0]) if H else None


# ------------------------------------------------------------------------------------------------- corrected data

def observations(fam, hour_off=None, scale_centre=None):
    """analyse.observations with corrections: per-(layout, hour, quantity) offsets subtracted from tang / rad (position
    recomputed from the corrected pair), and a per-layout ring-scale centre subtracted from ring.scale"""
    hour_off, scale_centre = hour_off or {}, scale_centre or {}
    obs = []
    for o in A.observations(fam):
        o = dict(o)
        if o['shape'] == 'ring':
            if o['q'] == 'scale' and o['layout'] in scale_centre:
                o['v'] = o['v'] - scale_centre[o['layout']]
            obs.append(o)
            continue
        if o['q'] in ('tang', 'rad'):
            o['v'] = o['v'] - hour_off.get((o['layout'], o['hour'], o['q']), 0.0)
        obs.append(o)
    # positions from the corrected pairs
    pair = defaultdict(dict)
    for o in obs:
        if o['q'] in ('tang', 'rad'):
            pair[(o['sha'], o['hour'])][o['q']] = o['v']
    for o in obs:
        if o['q'] == 'pos':
            p = pair.get((o['sha'], o['hour']), {})
            if 'tang' in p and 'rad' in p:
                o['v'] = math.hypot(p['tang'], p['rad'])
    return obs


def wmedian(vals, watches):
    return A.wquantile(vals, A.weights(watches), 0.5) if vals else 0.0


def hour_offsets(obs, watches):
    cell = defaultdict(lambda: ([], []))
    for o in obs:
        if o['shape'] != 'ring' and o['q'] in ('tang', 'rad') and o['watch'] in watches:
            cell[(o['layout'], o['hour'], o['q'])][0].append(o['v'])
            cell[(o['layout'], o['hour'], o['q'])][1].append(o['watch'])
    return {k: wmedian(v, w) for k, (v, w) in cell.items() if len(set(w)) >= 5}


def scale_centres(obs, watches):
    by = defaultdict(lambda: ([], []))
    for o in obs:
        if o['shape'] == 'ring' and o['q'] == 'scale' and o['watch'] in watches:
            by[o['layout']][0].append(o['v'])
            by[o['layout']][1].append(o['watch'])
    return {l: wmedian(v, w) for l, (v, w) in by.items()}


# ------------------------------------------------------------------------------------------------- held-out tests

def evaluate(fam, variant, train, test, targets=A.TARGETS):
    """offsets, scales and k from the training watches; spread and whole-watch false alarms on the test watches"""
    base = A.observations(fam)
    ho = hour_off = sc = None
    if variant in ('hour', 'both'):
        ho = hour_offsets(base, train)
    if variant in ('scale', 'both'):
        sc = scale_centres(base, train)
    obs = observations(fam, ho, sc)
    tr = [o for o in obs if o['watch'] in train]
    groups = A.build_groups(tr)
    sig = {g: A.scale(groups[g]) for g in SCHEME}
    devs_tr = A.photo_devs(tr, groups, SCHEME)
    te = [o for o in obs if o['watch'] in test]
    devs_te = A.photo_devs(te, groups, SCHEME)
    out = {}
    pm_tr = A.photo_max_z(devs_tr, sig)
    pm_te = A.photo_max_z(devs_te, sig)
    for t in targets:
        k = A.k_at(pm_tr, t)
        out['fa@%g' % t] = (pm_te, k)
    gte = A.build_groups(te)
    for g in ('marker.pos', 'ring.scale', 'ring.place'):
        G = gte[g]
        d = np.array([groups[g].dev(o['v']) for o in G.obs])
        out['d:' + g] = list(zip([o['watch'] for o in G.obs], [o['layout'] for o in G.obs], d))
    out['sigma'] = sig
    return out


def cross_validate(fam, variants=('none', 'scale', 'hour', 'both'), folds=5):
    watches = sorted({p['watch'] for F in fam.values() for p in F['photos']})
    part = {p['watch']: p['partition'] for F in fam.values() for p in F['photos']}
    order = sorted(watches, key=lambda w: hashlib.sha256(('offsets' + w).encode()).hexdigest())
    splits = [(set(watches) - set(order[f::folds]), set(order[f::folds])) for f in range(folds)]
    hold = ({w for w in watches if part.get(w) in ('development', 'validation')}, {w for w in watches if part.get(w) == 'holdout'})
    res = {}
    for v in variants:
        pooled = defaultdict(list)
        sigs = []
        for train, test in splits:
            e = evaluate(fam, v, train, test)
            sigs.append(e['sigma'])
            for t in A.TARGETS:
                pm, k = e['fa@%g' % t]
                pooled['fa@%g' % t] += [(w, z > k) for w, z, _ in pm]
            for g in ('marker.pos', 'ring.scale', 'ring.place'):
                pooled['d:' + g] += e['d:' + g]
        r = {}
        for t in A.TARGETS:
            by = defaultdict(list)
            for w, f in pooled['fa@%g' % t]:
                by[w].append(f)
            r['cv_whole_watch_fa@%g' % t] = float(np.mean([np.mean(x) for x in by.values()]))
        for g in ('marker.pos', 'ring.scale', 'ring.place'):
            rows = pooled['d:' + g]
            r['cv_p95:' + g] = A.wquantile([d for _, _, d in rows], A.weights([w for w, _, _ in rows]), 0.95)
            for lay in sorted({l for _, l, _ in rows}):
                sub = [(w, d) for w, l, d in rows if l == lay]
                r['cv_p95:%s:%s' % (g, lay)] = A.wquantile([d for _, d in sub], A.weights([w for w, _ in sub]), 0.95)
        r['sigma_mean'] = {g: float(np.mean([s[g] for s in sigs])) for g in SCHEME}
        e = evaluate(fam, v, *hold)
        for t in A.TARGETS:
            pm, k = e['fa@%g' % t]
            r['holdout_fa@%g' % t] = A.fa_at(pm, k)
        r['holdout_watches'] = len(hold[1])
        res[v] = r
    return res


# ------------------------------------------------------------------------------------------------- cause analysis

def ring_scale_cause(fam):
    out = {}
    for layout, F in fam.items():
        model = {r['sha256']: r.get('model', '') for r in A._csv(_acq(fam, layout))} if _acq(fam, layout) else {}
        wid = {p['sha'][:16]: p for p in F['photos']}
        rows = []
        for rec in F['base']:
            p = wid.get(rec.get('photo_id'))
            if p is None or not rec.get('ok') or not rec.get('mg_ring'):
                continue
            a94 = rec.get('a94_ring')
            rows.append({'watch': p['watch'], 'source': p['source'], 'variant': model.get(p['sha'], ''),
                         'scale': rec['mg_ring'][2], 'a94': 100 * a94[2] if a94 else None, 'tilt': tilt_of(rec.get('H')),
                         'rpx': rec.get('rpx')})
        if not rows:
            continue
        s = np.array([r['scale'] for r in rows])
        t = np.array([r['tilt'] for r in rows])
        X = np.vstack([np.ones_like(t), t]).T
        coef = np.linalg.lstsq(X, s, rcond=None)[0]
        boot = []
        ws = sorted({r['watch'] for r in rows})
        for _ in range(1000):
            pick = set(ws[i] for i in RNG.integers(0, len(ws), len(ws)))
            sub = [r['scale'] for r in rows if r['watch'] in pick]
            boot.append(np.median(sub))
        from scipy.stats import spearmanr
        out[layout] = {
            'photos': len(rows), 'watches': len(ws), 'median': float(np.median(s)),
            'median_90ci': (float(np.percentile(boot, 5)), float(np.percentile(boot, 95))),
            'share_negative': float(np.mean(s < 0)), 'p10_p90': (float(np.percentile(s, 10)), float(np.percentile(s, 90))),
            'by_variant': {v: (sum(1 for r in rows if r['variant'] == v), float(np.median([r['scale'] for r in rows if r['variant'] == v])))
                           for v in sorted({r['variant'] for r in rows})},
            'by_source': {v: (sum(1 for r in rows if r['source'] == v), float(np.median([r['scale'] for r in rows if r['source'] == v])))
                          for v in sorted({r['source'] for r in rows})},
            'luminous_reference_median': float(np.median([r['a94'] for r in rows if r['a94'] is not None])),
            'spearman_with_luminous': float(spearmanr([r['scale'] for r in rows if r['a94'] is not None],
                                                      [r['a94'] for r in rows if r['a94'] is not None]).correlation),
            'spearman_tilt': float(spearmanr(s, t).correlation), 'spearman_rpx': float(spearmanr(s, [r['rpx'] for r in rows]).correlation),
            'fit_at_zero_tilt': float(coef[0]), 'slope_per_deg': float(coef[1]),
            'low_tilt_median': float(np.median(s[t < 3])) if np.any(t < 3) else None, 'low_tilt_photos': int(np.sum(t < 3)),
        }
    return out


def _acq(fam, layout):
    return fam[layout].get('acq_path')


def hour_offset_cause(fam, splits=200):
    """reproducibility of per-hour offsets: disjoint watch halves, dealers, low against higher tilt"""
    obs = A.observations(fam)
    tilt = {}
    for F in fam.values():
        for p in F['photos']:
            tilt[p['sha']] = A._f(p['row'].get('pose_tilt_deg'))
    out = {}
    for layout in sorted(fam):
        lo = [o for o in obs if o['layout'] == layout and o['shape'] != 'ring' and o['q'] in ('tang', 'rad')]
        ws = sorted({o['watch'] for o in lo})
        full = hour_offsets(lo, set(ws))
        keys = sorted(full)
        rs = []
        for _ in range(splits):
            perm = RNG.permutation(len(ws))
            a, b = {ws[i] for i in perm[: len(ws) // 2]}, {ws[i] for i in perm[len(ws) // 2:]}
            oa, ob = hour_offsets(lo, a), hour_offsets(lo, b)
            kk = [k for k in keys if k in oa and k in ob]
            if len(kk) >= 6:
                rs.append(np.corrcoef([oa[k] for k in kk], [ob[k] for k in kk])[0, 1])
        lowt = {o['watch'] for o in lo if (tilt.get(o['sha']) or 99) < 3}
        hight = {o['watch'] for o in lo if (tilt.get(o['sha']) or 0) >= 3}
        ol, oh = hour_offsets([o for o in lo if (tilt.get(o['sha']) or 99) < 3], lowt), hour_offsets([o for o in lo if (tilt.get(o['sha']) or 0) >= 3], hight)
        kt = [k for k in keys if k in ol and k in oh]
        src = defaultdict(set)
        for o in lo:
            src[o['source']].add(o['watch'])
        big = [s for s, w in sorted(src.items(), key=lambda kv: -len(kv[1])) if len(w) >= 15][:2]
        srcr = None
        if len(big) == 2:
            o1, o2 = hour_offsets([o for o in lo if o['source'] == big[0]], src[big[0]]), hour_offsets([o for o in lo if o['source'] == big[1]], src[big[1]])
            ks = [k for k in keys if k in o1 and k in o2]
            srcr = {'sources': big, 'r': float(np.corrcoef([o1[k] for k in ks], [o2[k] for k in ks])[0, 1]), 'cells': len(ks)}
        out[layout] = {'watches': len(ws), 'offsets': {'%d.%s' % (k[1], k[2]): round(full[k], 4) for k in keys},
                       'max_abs_offset': float(max(abs(v) for v in full.values())),
                       'rms_offset': float(np.sqrt(np.mean([v * v for v in full.values()]))),
                       'split_half_r_median': float(np.median(rs)) if rs else None,
                       'split_half_r_p05_p95': (float(np.percentile(rs, 5)), float(np.percentile(rs, 95))) if rs else None,
                       'low_vs_high_tilt_r': float(np.corrcoef([ol[k] for k in kt], [oh[k] for k in kt])[0, 1]) if len(kt) >= 6 else None,
                       'low_vs_high_tilt_cells': len(kt), 'between_dealers': srcr}
    return out


def markdown(out):
    L = ['# Systematic offsets: cause and held-out correction tests (research; no geometry changed)', '',
         '## 1. Ring scale by family', '',
         '| Family | Watches | Median % [90 % CI] | Negative | p10 / p90 | By variant | By dealer | Luminous-reference median (r) | At zero tilt / per deg | Low-tilt (<3°) median (photos) | r tilt / rpx |',
         '|---|---:|---|---:|---|---|---|---|---|---|---|']
    for l, c in out['ring_scale'].items():
        L.append('| %s | %d | %.3f [%.3f, %.3f] | %.0f %% | %.3f / %.3f | %s | %s | %.3f (%.2f) | %.3f / %+.3f | %s (%d) | %.2f / %.2f |' % (
            l, c['watches'], c['median'], *c['median_90ci'], 100 * c['share_negative'], *c['p10_p90'],
            ', '.join('%s %.3f (%d)' % (k, v[1], v[0]) for k, v in c['by_variant'].items()),
            ', '.join('%s %.3f (%d)' % (k, v[1], v[0]) for k, v in c['by_source'].items()),
            c['luminous_reference_median'], c['spearman_with_luminous'], c['fit_at_zero_tilt'], c['slope_per_deg'],
            '—' if c['low_tilt_median'] is None else '%.3f' % c['low_tilt_median'], c['low_tilt_photos'], c['spearman_tilt'], c['spearman_rpx']))
    L += ['', '## 2. Per-hour offsets (watch-weighted median, % R)', '',
          '| Family | Watches | Largest | RMS | Split-half r (median, 5-95 %) | Low vs higher tilt r | Between dealers r |', '|---|---:|---:|---:|---|---|---|']
    for l, c in out['hour'].items():
        sr = c['split_half_r_p05_p95']
        bd = c['between_dealers']
        L.append('| %s | %d | %.3f | %.3f | %s (%s) | %s | %s |' % (
            l, c['watches'], c['max_abs_offset'], c['rms_offset'], A._r(c['split_half_r_median'], 2),
            '—' if not sr else '%.2f to %.2f' % sr, A._r(c['low_vs_high_tilt_r'], 2),
            '—' if not bd else '%s vs %s: %.2f (%d cells)' % (bd['sources'][0], bd['sources'][1], bd['r'], bd['cells'])))
    for l, c in out['hour'].items():
        L.append('')
        L.append('%s: %s' % (l, ', '.join('%s %+.3f' % kv for kv in c['offsets'].items())))
    L += ['', '## 3. Corrections tested on held-out watches (shared eight-reading structure)', '',
          '| Correction | CV whole-watch FA @5 % | @1 % | Holdout FA @5 % | @1 % | CV p95 marker position | CV p95 ring scale (GMT / 124060 / 126610) | CV p95 ring placement | σ position / ring scale |',
          '|---|---:|---:|---:|---:|---:|---|---:|---|']
    for v, r in out['corrections'].items():
        L.append('| %s | %s | %s | %s | %s | %s | %s / %s / %s | %s | %s / %s |' % (
            v, A._r(r['cv_whole_watch_fa@0.05']), A._r(r['cv_whole_watch_fa@0.01']), A._r(r['holdout_fa@0.05']),
            A._r(r['holdout_fa@0.01']), A._r(r['cv_p95:marker.pos']), A._r(r.get('cv_p95:ring.scale:gmt_126710')),
            A._r(r.get('cv_p95:ring.scale:submariner_124060')), A._r(r.get('cv_p95:ring.scale:submariner_126610')),
            A._r(r['cv_p95:ring.place']), A._r(r['sigma_mean']['marker.pos']), A._r(r['sigma_mean']['ring.scale'])))
    return '\n'.join(L) + '\n'


def run(root):
    fam = A.load(root)
    from pathlib import Path
    for l in fam:
        p = Path(root) / l / 'acquired_images.csv'
        fam[l]['acq_path'] = p if p.exists() else None
    return {'ring_scale': ring_scale_cause(fam), 'hour': hour_offset_cause(fam), 'corrections': cross_validate(fam)}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('root')
    ap.add_argument('--json')
    ap.add_argument('--md')
    a = ap.parse_args(argv)
    out = run(a.root)
    md = markdown(out)
    if a.json:
        open(a.json, 'w').write(json.dumps(out, indent=1, default=float) + '\n')
    if a.md:
        open(a.md, 'w').write(md)
    print(md)


if __name__ == '__main__':
    main()
