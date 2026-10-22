"""Phase A research validation of the recommended measurement (REPORT.md section 10), on ArchMeasure records.

    python -m tools.research.measurement_architecture.phase_a --items items*.csv --base base*.jsonl [--json out.json]

1. Multi-defect robustness and biweight tuning. Known displacements are added to the measured outer-metal centres of
   eligible genuine markers (offline: the image-level tests in REPORT.md 4.2 showed a moved marker's centre is measured
   to 0.02 % R, so the outcome of a ring fit can be studied on the centres directly). Patterns: one marker; two
   adjacent; two opposite; three adjacent; three random; four random. For each ring fit (production Huber, biweight
   with several tuning constants) and each pattern: detection of every displaced marker and false flags on the clean
   ones, at that fit's own genuine p99 threshold.
2. Proposed genuine limits (NOT deployed): watch-level p99 of the recommended readings, pooled by shape across
   families, with a 90 % bootstrap interval over watches; the same after the per-hour reference correction.
3. Per-hour reference correction table per model (mean tangential / radial LB residual over genuine watches).
4. The triangle's clear gap under the minute track against the parallax of the raised triangle.
Replica photographs are never used to set anything here.
"""
from __future__ import annotations

import argparse
import json
import math
import random
from collections import defaultdict

import numpy as np

from tools.image_archive import eligibility as el
from . import methods as M
from .evaluate import genuine_base, load, load_items, parallax_constants, q

FITS = {'huber': None, 'bw0.30': 0.0030, 'bw0.45': 0.0045, 'bw0.60': 0.0060, 'bw0.90': 0.0090}
PATTERNS = ('one', 'two_adjacent', 'two_opposite', 'three_adjacent', 'three_random', 'four_random')
AMOUNT = 0.005


def prepared(gen):
    """per genuine photo: eligible markers with d = centre - fixed reference, their shapes, rpx"""
    out = []
    for r, it in gen:
        if it.get('class_label') != 'gen':
            continue
        res, _ = M.measure(r, None)
        marks = {}
        for m in r['markers']:
            h = int(m['h'])
            if h in res and res[h]['status'] == el.ELIGIBLE and m.get('cO') and m.get('fix') and None not in m['cO']:
                marks[h] = (np.array(m['fix']), np.array(m['cO']) - np.array(m['fix']), m['shape'])
        if sum(1 for v in marks.values() if v[2] != 'triangle') >= 8:
            out.append((it['physical_watch_id'], it['model'], r['rpx'], marks))
    return out


def residuals(marks, rpx, c):
    """{hour: (tang %R, rad %R)} against the leave-one-out ring of the other round markers / batons"""
    ring = [h for h, v in marks.items() if v[2] != 'triangle']
    out = {}
    for h, (fix, d, shape) in marks.items():
        rows = [(marks[g][0][0], marks[g][0][1], marks[g][1][0], marks[g][1][1]) for g in ring if g != h]
        if len(rows) < 5:
            continue
        sol = M.similarity(rows, rpx) if c is None else M.biweight(rows, rpx, c=c)
        e = d - M.predict(sol, fix)
        out[h] = (100 * float(e @ M.tangent(h)), 100 * float(e @ M.radial(h)))
    return out


def choose(pattern, hours, rng):
    hs = sorted(h for h in hours)
    ring = [h for h in hs if h != 12]
    if pattern == 'one':
        return [rng.choice(hs)]
    if pattern in ('two_adjacent', 'three_adjacent'):
        n = 2 if pattern == 'two_adjacent' else 3
        starts = [h for h in ring if all(((h - 1 + i) % 12) + 1 in ring for i in range(n))]
        if not starts:
            return None
        s = rng.choice(starts)
        return [((s - 1 + i) % 12) + 1 for i in range(n)]
    if pattern == 'two_opposite':
        pairs = [h for h in ring if ((h + 5) % 12) + 1 in ring and h < ((h + 5) % 12) + 1]
        if not pairs:
            return None
        h = rng.choice(pairs)
        return [h, ((h + 5) % 12) + 1]
    n = 3 if pattern == 'three_random' else 4
    return rng.sample(ring, n) if len(ring) >= n + 5 else None


def multi_defect(photos, seed=7):
    """detection / false-flag rates per (fit, pattern) at each fit's genuine pooled p99 position"""
    thr = {}
    for name, c in FITS.items():
        per = defaultdict(lambda: defaultdict(list))
        for w, model, rpx, marks in photos:
            for h, (t, r) in residuals(marks, rpx, c).items():
                per[marks[h][2]][(w, h)].append(math.hypot(t, r))
        thr[name] = {s: q([np.median(v) for v in vals.values()], 99) for s, vals in per.items()}
    rng = random.Random(seed)
    out = defaultdict(lambda: [0, 0, 0, 0])     # (fit, pattern) -> [detected, displaced, false flags, clean]
    for w, model, rpx, marks in photos:
        for pattern in PATTERNS:
            for _ in range(2):
                chosen = choose(pattern, marks.keys(), rng)
                if not chosen:
                    continue
                moved = {}
                for h, (fix, d, shape) in marks.items():
                    if h in chosen:
                        a = rng.uniform(0, 2 * math.pi)
                        d = d + AMOUNT * np.array([math.cos(a), math.sin(a)])
                    moved[h] = (fix, d, shape)
                for name, c in FITS.items():
                    res = residuals(moved, rpx, c)
                    for h, (t, r) in res.items():
                        flag = math.hypot(t, r) > thr[name][marks[h][2]]
                        k = out[(name, pattern)]
                        if h in chosen:
                            k[0] += flag
                            k[1] += 1
                        else:
                            k[2] += flag
                            k[3] += 1
    return thr, out


def bootstrap_p99(watch_values, n=400, seed=11):
    """p99 of watch-level values with a 90 % bootstrap interval over watches"""
    ws = list(watch_values)
    rng = np.random.default_rng(seed)
    point = q([x for w in ws for x in watch_values[w]], 99)
    boots = []
    for _ in range(n):
        pick = rng.integers(0, len(ws), len(ws))
        boots.append(q([x for i in pick for x in watch_values[ws[i]]], 99))
    return point, q(boots, 5), q(boots, 95)


def limits(gen, k_loo):
    """proposed genuine limits per shape (pooled across families), raw and per-hour corrected (leave-one-watch-out)"""
    vals = defaultdict(lambda: defaultdict(list))      # (reading, shape) -> watch -> one value per marker
    hourly = defaultdict(lambda: defaultdict(dict))    # (model, h) -> watch -> (tang, rad)
    tilt = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))    # reading -> watch -> hour -> photo values
    rings = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))   # reading -> watch -> 0 -> photo values
    photo = defaultdict(lambda: defaultdict(list))                        # (model, h) -> watch -> [(tang, rad)]
    for r, it in gen:
        if it.get('class_label') != 'gen':
            continue
        w = it['physical_watch_id']
        out, ring = M.measure(r, k_loo.get(w))
        for h, x in out.items():
            if x['status'] != el.ELIGIBLE or 'LB' not in x:
                continue
            t, rd, tl = x['LB']
            if t is None or rd is None:
                continue
            photo[(r['model'], h)][w].append((t, rd))
            if x['shape'] == 'baton' and tl is not None:
                tilt['baton tilt (LB, deg)'][w][h].append(abs(tl))
            if x['shape'] == 'triangle' and x['P'][2] is not None:
                tilt['triangle rotation vs ticks 59-1 (production, deg)'][w][h].append(abs(x['P'][2]))
        if ring and ring.get('t_minus_parallax') is not None:
            rings['ring placement |t - parallax| (% R)'][w][0].append(float(np.linalg.norm(ring['t_minus_parallax'])))
            rings['ring rotation |deg|'][w][0].append(abs(ring['rot_deg']))
            rings['ring scale |%|'][w][0].append(abs(ring['s']))
    for key, ws in photo.items():   # one value per watch and marker: the median over the watch's photos
        for w, v in ws.items():
            hourly[key][w] = (float(np.median([a for a, _ in v])), float(np.median([b for _, b in v])))
    table = {}
    shapes = {}
    for (model, h), ws in hourly.items():
        shape = el.SHAPES[model][h]
        shapes[(model, h)] = shape
        for w, (t, rd) in ws.items():
            vals[('position', shape)][w].append(math.hypot(t, rd))
            others = [v for u, v in ws.items() if u != w]
            if len(others) >= 5:
                mt, mr = np.mean([v[0] for v in others]), np.mean([v[1] for v in others])
                vals[('position, per-hour corrected', shape)][w].append(math.hypot(t - mt, rd - mr))
        if len(ws) >= 8:
            tv, rv = [v[0] for v in ws.values()], [v[1] for v in ws.values()]
            table.setdefault(model, {})[h] = {'tang': round(float(np.mean(tv)), 4), 'rad': round(float(np.mean(rv)), 4),
                                              'se_tang': round(float(np.std(tv, ddof=1) / math.sqrt(len(tv))), 4),
                                              'se_rad': round(float(np.std(rv, ddof=1) / math.sqrt(len(rv))), 4),
                                              'watches': len(ws)}
    prop = {}
    flat = {}
    for key, wv in vals.items():
        flat[' '.join(key)] = {w: list(v) for w, v in wv.items()}
    for key, wv in list(tilt.items()) + list(rings.items()):
        flat[key] = {w: [float(np.median(v)) for v in hs.values()] for w, hs in wv.items()}
    for key, wv in flat.items():
        p, lo, hi = bootstrap_p99(wv)
        prop[key] = {'watches': len(wv), 'values': sum(len(v) for v in wv.values()), 'p99': p, 'ci90': [lo, hi],
                     'max': float(max(x for v in wv.values() for x in v))}
    return prop, table


def gap_parallax(gen, k_loo):
    """triangle clear gap (production, % R) against the parallax of the raised triangle along the 12 axis"""
    g, pred = [], []
    for r, it in gen:
        if it.get('class_label') != 'gen':
            continue
        k = k_loo.get(it['physical_watch_id'])
        if k is None:
            continue
        tri = next((m for m in r['markers'] if int(m['h']) == 12), None)
        if tri is None or tri.get('gap') is None:
            continue
        out, _ = M.measure(r, k)
        if 12 not in out or out[12]['status'] != el.ELIGIBLE:
            continue
        t, far = M.pose(r['H'])
        shift = 100 * k * math.tan(t) * float(far @ M.radial(12))     # outward shift of the triangle (% R)
        g.append(tri['gap'])
        pred.append(shift)
    if len(g) < 10:
        return None
    g, pred = np.array(g), np.array(pred)
    corr = g + pred
    return {'n': len(g), 'r': float(np.corrcoef(g, pred)[0, 1]), 'slope': float(np.polyfit(pred, g, 1)[0]),
            'sd': float(g.std()), 'sd_corrected': float(corr.std()), 'p1': q(g, 1), 'min': float(g.min()),
            'p1_corrected': q(corr, 1), 'min_corrected': float(corr.min())}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--items', nargs='+', required=True)
    ap.add_argument('--base', nargs='+', required=True)
    ap.add_argument('--json')
    a = ap.parse_args(argv)
    items = load_items(a.items)
    gen = genuine_base(load(a.base), items)
    k_all, k_loo, _ = parallax_constants(gen)
    photos = prepared(gen)
    print('# Phase A: %d genuine photos with >= 8 eligible ring markers (%d watches)' % (
        len(photos), len({p[0] for p in photos})))
    thr, md = multi_defect(photos)
    print('\n## 1. Multi-defect robustness: displaced markers 0.5 % R in random directions (offline, on measured centres)')
    print('genuine p99 thresholds (%% R): %s' % '; '.join('%s %s' % (n, ', '.join('%s %.3f' % (s, v) for s, v in sorted(t.items())))
                                                     for n, t in thr.items()))
    print('| fit | ' + ' | '.join(PATTERNS) + ' |')
    print('|---|' + '---|' * len(PATTERNS))
    for name in FITS:
        cells = []
        for p in PATTERNS:
            d, n, f, c = md[(name, p)]
            cells.append('%.0f%% det / %.1f%% false (%d)' % (100 * d / max(n, 1), 100 * f / max(c, 1), n))
        print('| %s | %s |' % (name, ' | '.join(cells)))
    prop, table = limits(gen, k_loo)
    print('\n## 2. Proposed genuine limits (NOT deployed): watch-level p99, 90 % bootstrap interval over watches')
    print('| reading | watches | values | p99 | 90 % interval | max |')
    print('|---|---:|---:|---:|---|---:|')
    for kk, v in sorted(prop.items()):
        print('| %s | %d | %d | %.3f | %.3f - %.3f | %.3f |' % (kk, v['watches'], v['values'], v['p99'], v['ci90'][0],
                                                              v['ci90'][1], v['max']))
    print('\n## 3. Per-hour reference correction (mean LB residual over genuine watches, %% R; |mean| / se >= 3 marked *)')
    for model, hs in sorted(table.items()):
        print('%s: %s' % (model, ', '.join('%d %+.3f/%+.3f%s (%d)' % (
            h, v['tang'], v['rad'], '*' if abs(v['tang']) >= 3 * v['se_tang'] or abs(v['rad']) >= 3 * v['se_rad'] else '',
            v['watches']) for h, v in sorted(hs.items()))))
    gp = gap_parallax(gen, k_loo)
    print('\n## 4. Triangle clear gap against parallax along the 12 axis')
    if gp:
        print('n %d; r(gap, predicted outward shift) %+.2f, slope %+.2f; sd %.3f -> %.3f corrected; p1 %.3f -> %.3f; '
              'min %.3f -> %.3f %% R' % (gp['n'], gp['r'], gp['slope'], gp['sd'], gp['sd_corrected'], gp['p1'],
                                          gp['p1_corrected'], gp['min'], gp['min_corrected']))
    if a.json:
        json.dump({'thresholds': thr, 'multi_defect': {'%s/%s' % k: v for k, v in md.items()}, 'limits': prop,
                   'per_hour': table, 'gap': gp, 'k': k_all}, open(a.json, 'w'), indent=1)


if __name__ == '__main__':
    main()
