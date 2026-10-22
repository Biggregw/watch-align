"""Evaluate the competing measurement methods (methods.py) on ArchMeasure records.

    python -m tools.research.measurement_architecture.evaluate --items items*.csv --base base*.jsonl \
        [--synth synth*.jsonl] [--json summary.json]

items CSVs give photo_id, sha256, physical_watch_id, class_label and model for every measured photo. Statistics count
physical watches: a watch contributes one value per marker (the median over its photos) to population quantiles, and
held-out (leave-one-watch-out) fits are used wherever a constant is learned from genuine photos (the parallax constant).
Only ELIGIBLE markers (tools/image_archive/eligibility.py rules, applied to the marker's own evidence) are scored.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import statistics as st
from collections import Counter, defaultdict

import numpy as np

from tools.image_archive import eligibility as el
from . import methods as M

COMP = ('tang', 'rad', 'tilt')


def load_items(paths):
    out = {}
    for p in paths:
        for r in csv.DictReader(open(p)):
            out[r['photo_id']] = r
    return out


def load(paths):
    recs = []
    for p in paths:
        for line in open(p):
            line = line.strip()
            if line:
                recs.append(json.loads(line))
    return recs


def fit_k(rings):
    """least squares k (R per unit tan(tilt)) through the origin: ring translation = k tan(tilt) far"""
    X, Y = [], []
    for r in rings:
        v = math.tan(math.radians(r['tilt_deg'])) * r['far']
        X += list(v)
        Y += list(r['t'] / 100)
    X, Y = np.array(X), np.array(Y)
    return float(X @ Y / (X @ X)) if len(X) else None


def genuine_base(base, items, max_tilt=10.6, min_rpx=250, rep_min_rpx=200):
    """the usable base records: registered, face-on, resolution (replicas at the app's own 200 px); (record, item)"""
    out = []
    for r in base:
        it = items.get(r['photo_id'])
        if not r.get('ok') or it is None or not r.get('H') or not r.get('markers'):
            continue
        t, _ = M.pose(r['H'])
        floor = rep_min_rpx if it.get('class_label') == 'rep' else min_rpx
        if math.degrees(t) > max_tilt or (r.get('rpx') or 0) < floor:
            continue
        out.append((r, it))
    return out


def parallax_constants(gen):
    """k fitted on all genuine watches, and leave-one-watch-out k per watch"""
    rings = []
    for r, it in gen:
        _, ring = M.measure(r, None)
        if ring and it.get('class_label') == 'gen':
            rings.append((it['physical_watch_id'], ring))
    k_all = fit_k([g for _, g in rings])
    k_loo = {w: fit_k([g for v, g in rings if v != w]) for w in {v for v, _ in rings}}
    return k_all, k_loo, rings


def pos_of(v, shape):
    """the scored size of a reading: radial/tangential combined (round, baton) or both kept (triangle)"""
    t, r, _ = v
    if t is None or r is None:
        return None
    return math.hypot(t, r)


def population(gen, k_loo):
    """per method, shape and family: watch-level tangential, radial, position and tilt values; plus photo tilt"""
    per = defaultdict(lambda: defaultdict(list))   # (method, shape, model, comp) -> watch -> values
    photo_rows = []
    for r, it in gen:
        if it.get('class_label') != 'gen':
            continue
        w = it['physical_watch_id']
        out, ring = M.measure(r, k_loo.get(w))
        tilt = math.degrees(M.pose(r['H'])[0])
        for h, res in out.items():
            if res['status'] != el.ELIGIBLE:
                continue
            for name in M.METHODS:
                if name not in res:
                    continue
                v = res[name]
                key = (name, res['shape'], r['model'])
                for i, c in enumerate(COMP):
                    if v[i] is not None:
                        per[key + (c,)][(w, h)].append(v[i])
                p = pos_of(v, res['shape'])
                if p is not None:
                    per[key + ('pos',)][(w, h)].append(p)
                photo_rows.append((name, res['shape'], r['model'], w, h, tilt, v))
    return per, photo_rows


def q(v, p):
    return float(np.percentile(v, p)) if len(v) else float('nan')


def population_table(per):
    rows = []
    for key, vals in sorted(per.items()):
        name, shape, model, comp = key
        wm = [float(np.median(v)) for v in vals.values()]
        a = np.abs(wm)
        rows.append({'method': name, 'shape': shape, 'model': model, 'comp': comp, 'watch_markers': len(wm),
                     'watches': len({w for w, _ in vals}), 'sd': float(np.std(wm)), 'p95': q(a, 95), 'p99': q(a, 99),
                     'max': float(a.max()) if len(a) else float('nan')})
    return rows


def pooled_table(per):
    """shape-level pooling across hours and families (the shared calibration question)"""
    pool = defaultdict(list)
    fam = defaultdict(lambda: defaultdict(list))
    hours = defaultdict(lambda: defaultdict(list))
    for (name, shape, model, comp), vals in per.items():
        for (w, h), v in vals.items():
            x = float(np.median(v))
            pool[(name, shape, comp)].append(x)
            fam[(name, shape, comp)][model].append(x)
            hours[(name, shape, comp)][(model, h)].append(x)
    out = []
    for key, v in sorted(pool.items()):
        a = np.abs(v)
        f = fam[key]
        sds = {m: float(np.std(x)) for m, x in f.items() if len(x) >= 10}
        means = {m: float(np.mean(x)) for m, x in f.items() if len(x) >= 10}
        hb = []
        for (m, h), x in hours[key].items():
            if len(x) >= 8 and key[2] in ('tang', 'rad', 'tilt'):
                se = np.std(x, ddof=1) / math.sqrt(len(x))
                if se > 0:
                    hb.append((m, h, float(np.mean(x)), float(np.mean(x) / se), len(x)))
        out.append({'method': key[0], 'shape': key[1], 'comp': key[2], 'n': len(v), 'sd': float(np.std(v)),
                    'p95': q(a, 95), 'p99': q(a, 99), 'max': float(a.max()), 'sd_by_family': sds,
                    'mean_by_family': means, 'hour_bias': hb})
    return out


def tilt_dependence(photo_rows):
    out = {}
    by = defaultdict(list)
    for name, shape, model, w, h, tilt, v in photo_rows:
        p = pos_of(v, shape)
        if p is not None:
            by[(name, shape)].append((tilt, p))
    for key, v in by.items():
        t, p = np.array(v).T
        out[key] = float(np.corrcoef(t, p)[0, 1]) if len(v) > 10 else float('nan')
    return out


# ------------------------------------------------------------------------------------------------ synthetic

def truth_of(variant, h, fixed):
    """(tangential %R, radial %R, turn deg) injected at hour h by a variant (None for registration-only or image ones)"""
    p = variant.split(':')
    if p[0] == 'mv':
        if int(p[1]) != h:
            return (0.0, 0.0, 0.0)
        amt = float(p[3])
        return {'t': (100 * amt, 0.0, 0.0), 'r': (0.0, 100 * amt, 0.0), 'rot': (0.0, 0.0, amt)}[p[2]]
    if p[0] == 'ring':
        c = np.array(fixed)
        if p[1] == 't':
            d = np.array([float(p[2]), float(p[3])])
            rot = 0.0
        elif p[1] == 'rot':
            rot = math.radians(float(p[2]))
            d = np.array([math.cos(rot) * c[0] - math.sin(rot) * c[1], math.sin(rot) * c[0] + math.cos(rot) * c[1]]) - c
        else:
            d = float(p[2]) * c
            rot = 0.0
        return (100 * float(d @ M.tangent(h)), 100 * float(d @ M.radial(h)), math.degrees(rot))
    return (0.0, 0.0, 0.0)   # reg / persp / res: the physical geometry did not change


def synthetic(base, synth, items, k_all, k_loo):
    """per variant kind and method: errors of the change in each reading against the injected change"""
    by_photo = {r['photo_id']: r for r in base if r.get('ok')}
    by_photo.update({r['photo_id']: r for r in synth if r['variant'] == 'base' and r.get('ok')})   # same run
    res = defaultdict(list)      # (kind, method, comp, role) -> errors
    ring_err = defaultdict(list)
    fails = Counter()
    for v in synth:
        b = by_photo.get(v['photo_id'])
        it = items.get(v['photo_id'], {})
        if b is None or v['variant'] == 'base':
            continue
        kind = ':'.join(v['variant'].split(':')[:2]) if not v['variant'].startswith('mv') else 'mv:' + v['variant'].split(':')[2]
        if v['variant'].startswith('res') or v['variant'].startswith('persp'):
            kind = v['variant'].split(':')[0] + ':' + v['variant'].split(':')[1]
        if not v.get('ok') or not v.get('markers'):
            fails[kind] += 1
            continue
        k = k_loo.get(it.get('physical_watch_id'), k_all)
        if 'ticks' not in v:
            v = dict(v, ticks=b.get('ticks'))        # the minute track is untouched by marker moves
        ob, rb = M.measure(b, k)
        ov, rv = M.measure(v, k)
        fixed = {int(m['h']): m['fix'] for m in b['markers'] if m.get('fix')}
        moved_hour = int(v['variant'].split(':')[1]) if v['variant'].startswith('mv') else None
        for h in ob:
            if h not in ov or ob[h]['status'] != el.ELIGIBLE:
                continue
            if ov[h]['status'] != el.ELIGIBLE and h != moved_hour:
                continue
            tr = truth_of(v['variant'], h, fixed.get(h, (0, 0)))
            role = 'moved' if h == moved_hour else ('ring' if v['variant'].startswith('ring') else 'other')
            for name in M.METHODS:
                if name not in ob[h] or name not in ov[h]:
                    continue
                for i, c in enumerate(COMP):
                    x0, x1 = ob[h][name][i], ov[h][name][i]
                    if x0 is None or x1 is None:
                        continue
                    if ob[h]['shape'] == 'round' and c == 'tilt':
                        continue
                    res[(kind, name, c, role)].append((x1 - x0) - tr[i])
        if rb and rv and v['variant'].startswith(('ring', 'reg')):
            p = v['variant'].split(':')
            if p[0] == 'ring' and p[1] == 't':
                truth_t = 100 * np.array([float(p[2]), float(p[3])])
            else:
                truth_t = np.zeros(2)
            truth_rot = float(p[2]) if p[0] == 'ring' and p[1] == 'rot' else 0.0
            truth_s = 100 * float(p[2]) if p[0] == 'ring' and p[1] == 'scale' else 0.0
            ring_err[kind].append((np.linalg.norm((rv['t'] - rb['t']) - truth_t), (rv['rot_deg'] - rb['rot_deg']) - truth_rot,
                                   (rv['s'] - rb['s']) - truth_s))
    return res, ring_err, fails


def summarise_errors(res):
    rows = []
    for (kind, name, comp, role), e in sorted(res.items()):
        a = np.abs(e)
        rows.append({'kind': kind, 'method': name, 'comp': comp, 'role': role, 'n': len(e), 'bias': float(np.mean(e)),
                     'sd': float(np.std(e)), 'p95_abs': q(a, 95), 'max_abs': float(a.max())})
    return rows


def detection(per, synth_res_raw, base, synth, items, k_all, k_loo, fp_q=99.0):
    """detection of a 0.5 % R single-marker move at the method's own genuine false-positive threshold (pooled shape)"""
    thr = {}
    pool = defaultdict(list)
    for (name, shape, model, comp), vals in per.items():
        if comp == 'pos':
            pool[(name, shape)] += [float(np.median(v)) for v in vals.values()]
    for key, v in pool.items():
        thr[key] = q(np.abs(v), fp_q)
    by_photo = {r['photo_id']: r for r in base if r.get('ok')}
    by_photo.update({r['photo_id']: r for r in synth if r['variant'] == 'base' and r.get('ok')})
    hit = defaultdict(lambda: [0, 0])
    for v in synth:
        if not v['variant'].startswith('mv') or ':rot:' in v['variant'] or not v.get('ok'):
            continue
        b = by_photo.get(v['photo_id'])
        if b is None:
            continue
        it = items.get(v['photo_id'], {})
        k = k_loo.get(it.get('physical_watch_id'), k_all)
        h = int(v['variant'].split(':')[1])
        ov, _ = M.measure(dict(v, ticks=b.get('ticks')), k)
        ob, _ = M.measure(b, k)
        if h not in ov or h not in ob or ob[h]['status'] != el.ELIGIBLE:
            continue
        for name in M.METHODS:
            if name not in ov[h]:
                continue
            p = pos_of(ov[h][name], ov[h]['shape'])
            t = thr.get((name, ov[h]['shape']))
            if p is None or t is None or not math.isfinite(t):
                continue
            hit[(name, ov[h]['shape'])][1] += 1
            hit[(name, ov[h]['shape'])][0] += p > t
    return thr, hit


# ------------------------------------------------------------------------------------------------ report

def fmt(x, d=3):
    return '-' if x is None or (isinstance(x, float) and not math.isfinite(x)) else ('%.' + str(d) + 'f') % x


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--items', nargs='+', required=True)
    ap.add_argument('--base', nargs='+', required=True)
    ap.add_argument('--synth', nargs='*', default=[])
    ap.add_argument('--json')
    a = ap.parse_args(argv)
    items = load_items(a.items)
    base = load(a.base)
    synth = load(a.synth) if a.synth else []
    gen = genuine_base(base, items)
    k_all, k_loo, rings = parallax_constants(gen)
    print('# base records %d, usable %d (genuine %d photos, %d watches; replica %d photos)' % (
        len(base), len(gen), sum(it.get('class_label') == 'gen' for _, it in gen),
        len({it['physical_watch_id'] for _, it in gen if it.get('class_label') == 'gen'}),
        sum(it.get('class_label') == 'rep' for _, it in gen)))
    summary = {'k_all': k_all}
    if k_all is not None:
        res = []
        for w, g in rings:
            pred = k_loo.get(w, k_all) * math.tan(math.radians(g['tilt_deg'])) * g['far'] * 100
            res.append((g['t'], g['t'] - pred, g['rot_deg'], g['s']))
        t0 = np.array([r[0] for r in res])
        t1 = np.array([r[1] for r in res])
        print('\n## Ring level (genuine): parallax constant k = %.4f R per unit tan(tilt) (%d photos)' % (k_all, len(res)))
        print('ring translation |t| median %.3f p95 %.3f %% R; after parallax (held-out k) median %.3f p95 %.3f; '
              'per-axis sd %.3f/%.3f -> %.3f/%.3f; rotation sd %.3f deg; scale sd %.3f %%' % (
                  np.median(np.linalg.norm(t0, axis=1)), q(np.linalg.norm(t0, axis=1), 95),
                  np.median(np.linalg.norm(t1, axis=1)), q(np.linalg.norm(t1, axis=1), 95),
                  t0[:, 0].std(), t0[:, 1].std(), t1[:, 0].std(), t1[:, 1].std(),
                  np.std([r[2] for r in res]), np.std([r[3] for r in res])))
        summary['ring'] = {'n': len(res), 't_median': float(np.median(np.linalg.norm(t0, axis=1))),
                           't_corrected_p95': q(np.linalg.norm(t1, axis=1), 95)}
    per, photo_rows = population(gen, k_loo)
    pooled = pooled_table(per)
    summary['pooled'] = pooled
    print('\n## Genuine population, pooled by shape (watch-level values, eligible markers; %% R and deg)')
    print('| method | shape | comp | n | sd | p95 | p99 | max | sd by family | hours with |t|>=3 |')
    print('|---|---|---|---:|---:|---:|---:|---:|---|---:|')
    for r in pooled:
        if r['comp'] == 'tilt' and r['shape'] == 'round':
            continue
        print('| %s | %s | %s | %d | %s | %s | %s | %s | %s | %d of %d |' % (
            r['method'], r['shape'], r['comp'], r['n'], fmt(r['sd']), fmt(r['p95']), fmt(r['p99']), fmt(r['max']),
            ' '.join('%s %s' % (m.split('_')[-1], fmt(s)) for m, s in sorted(r['sd_by_family'].items())),
            sum(abs(x[3]) >= 3 for x in r['hour_bias']), len(r['hour_bias'])))
    devs = []
    for r, it in gen:
        if it.get('class_label') != 'gen':
            continue
        out, _ = M.measure(r, None)
        devs += [abs(x['dev']) for x in out.values() if x.get('dev') is not None and x['shape'] != 'round'
                 and x['status'] == el.ELIGIBLE]
    if devs:
        print('\n## Hour-line pair deviation from the registered frame (|dev|, deg; production subtracts it from the '
              'ring-relative tilt): median %.3f p95 %.3f max %.3f (n %d)' % (np.median(devs), q(devs, 95), max(devs), len(devs)))
        summary['hour_line_dev'] = {'median': float(np.median(devs)), 'p95': q(devs, 95), 'max': float(max(devs))}
    td = tilt_dependence(photo_rows)
    print('\n## Position size against photo tilt (Pearson r, genuine photos)')
    print(' '.join('%s/%s %+.2f' % (k[0], k[1], v) for k, v in sorted(td.items())))
    summary['tilt_r'] = {'%s/%s' % k: v for k, v in td.items()}
    print('\n## Per-hour mean (systematic) bias, tangential, |t| >= 3 (method LR and P)')
    for r in pooled:
        if r['method'] in ('P', 'LR', 'FP') and r['comp'] == 'tang':
            s = sorted([x for x in r['hour_bias'] if abs(x[3]) >= 3], key=lambda x: (x[0], x[1]))
            print('%s %s: %s' % (r['method'], r['shape'], ', '.join('%s %d %+.3f (t %.1f)' % (m.split('_')[-1], h, mu, t)
                                                                      for m, h, mu, t, n in s) or 'none'))
    if synth:
        res, ring_err, fails = synthetic(base, synth, items, k_all, k_loo)
        rows = summarise_errors(res)
        summary['synthetic'] = rows
        print('\n## Synthetic: error of the measured change against the injected change (%% R / deg)')
        print('failed (pose lost):', dict(fails))
        print('| kind | role | comp | ' + ' | '.join(M.METHODS) + ' |')
        print('|---|---|---|' + '---:|' * len(M.METHODS))
        keyset = sorted({(r['kind'], r['role'], r['comp']) for r in rows})
        idx = {(r['kind'], r['role'], r['comp'], r['method']): r for r in rows}
        for kind, role, comp in keyset:
            cells = []
            for name in M.METHODS:
                r = idx.get((kind, role, comp, name))
                cells.append('-' if r is None else '%s / %s (%d)' % (fmt(r['bias'], 3), fmt(r['p95_abs'], 3), r['n']))
            print('| %s | %s | %s | %s |' % (kind, role, comp, ' | '.join(cells)))
        print('(cells: mean error / 95th percentile |error| (n))')
        print('\n## Ring-level estimator: error of the recovered change (|translation| %R, rotation deg, scale %)')
        for kind, e in sorted(ring_err.items()):
            e = np.array(e)
            print('%-14s n %3d  |t| p50 %.3f p95 %.3f  rot bias %+.3f sd %.3f  scale bias %+.3f sd %.3f' % (
                kind, len(e), np.median(e[:, 0]), q(e[:, 0], 95), e[:, 1].mean(), e[:, 1].std(), e[:, 2].mean(), e[:, 2].std()))
        thr, hit = detection(per, res, base, synth, items, k_all, k_loo)
        print('\n## Detection of a 0.5 %% R single-marker move at each method\'s genuine 99th-percentile position')
        print('| method | shape | genuine p99 (threshold) | detected |')
        print('|---|---|---:|---:|')
        for (name, shape), (k_, n) in sorted(hit.items()):
            print('| %s | %s | %s | %d / %d (%.0f %%) |' % (name, shape, fmt(thr.get((name, shape))), k_, n, 100 * k_ / max(n, 1)))
        summary['detection'] = {'%s/%s' % k: {'threshold': thr.get(k), 'hit': v[0], 'n': v[1]} for k, v in hit.items()}
    rep = [(r, it) for r, it in gen if it.get('class_label') == 'rep']
    if rep:
        replicas(rep, k_all, per)
    if a.json:
        json.dump(summary, open(a.json, 'w'), indent=1, default=lambda o: o.tolist() if hasattr(o, 'tolist') else str(o))
    return summary


def replicas(rep, k, per):
    """replica photos: per independent photo of a watch, every method's reading of each marker; and agreement between
    independent photos of the same watch (a physical defect repeats, a photographic artefact does not)"""
    print('\n## Replica photographs (%d)' % len(rep))
    by = defaultdict(list)
    for r, it in rep:
        out, ring = M.measure(r, k)
        by[it['physical_watch_id']].append((math.degrees(M.pose(r['H'])[0]), out, r['photo_id']))
    diffs = defaultdict(list)
    for w, ps in sorted(by.items()):
        ps.sort(key=lambda x: x[0])
        groups = []
        for p in ps:   # near-identical photographs (resized copies) collapse into one
            if groups and abs(groups[-1][0] - p[0]) < 0.2:
                continue
            groups.append(p)
        for i in range(len(groups)):
            for j in range(i + 1, len(groups)):
                a, b = groups[i][1], groups[j][1]
                for h in set(a) & set(b):
                    if a[h]['status'] in (el.ELIGIBLE,) and b[h]['status'] in (el.ELIGIBLE,):
                        for name in M.METHODS:
                            if name in a[h] and name in b[h]:
                                pa, pb = pos_of(a[h][name], a[h]['shape']), pos_of(b[h][name], b[h]['shape'])
                                ta, tb = a[h][name][0], b[h][name][0]
                                if ta is not None and tb is not None:
                                    diffs[name].append(abs(ta - tb))
        print('%s: %d photos, %d independent (tilts %s)' % (w, len(ps), len(groups), ', '.join('%.1f' % g[0] for g in groups)))
    print('agreement between independent photos of one replica watch, tangential |difference| (%R):')
    for name, v in sorted(diffs.items()):
        print('  %-3s n %3d median %.3f p90 %.3f' % (name, len(v), np.median(v), q(v, 90)))


if __name__ == '__main__':
    main()
