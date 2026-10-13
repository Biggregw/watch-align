#!/usr/bin/env python3
"""REVIEW ONLY. Per-edge angle of every baton and 12-triangle edge, separated from the marker's rotation.
d_i = a_i - mean(a over the marker's sides): an edge that is crooked relative to the marker's own other edges.
(A rotated marker moves every edge together and leaves d unchanged; one crooked long edge of theta gives about
3/4 theta on that edge and -1/4 theta on the others.) Genuine: per-photo range, between-watch spread of per-watch
medians, and photo-to-photo uncertainty from repeat photos of the same physical watch. QC values are listed against
those numbers. Nothing here is a threshold. Usage: edge_stats.py OUT_DIR (reads edges_genuine_*.csv, edges_qc_*.csv)"""
import csv, sys
from collections import defaultdict
from pathlib import Path
import numpy as np

SIDES = {'baton': ['ccw-long', 'outer-end', 'cw-long', 'inner-end']}
out = Path(sys.argv[1])

def rows(path):
    good = []
    for r in csv.DictReader(open(path)):
        if r['usable'] != 'true' or r['interference_clean'] != 'true':
            continue
        k = 3 if r['kind'] == 'triangle' else 4
        a = np.array([float(r[f'a{i}']) for i in range(k)])
        r['d'] = a - a.mean(); r['a'] = a; r['k'] = k
        if r['kind'] == 'triangle':
            names = []
            for i in range(k):
                v = a[i]
                names.append(min((('left', abs(v - float(r['tri_left']))), ('right', abs(v - float(r['tri_right']))), ('base', abs(v - float(r['tri_base'])))), key=lambda t: t[1])[0])
            r['names'] = names
        else:
            r['names'] = SIDES['baton']
        good.append(r)
    return good

def rsd(v):
    v = np.asarray(v, float); return 1.4826 * np.median(np.abs(v - np.median(v))) if len(v) else float('nan')

for g in sorted(out.glob('edges_genuine_*.csv')):
    model = g.stem[len('edges_genuine_'):]
    G = rows(g)
    key = lambda r, i: (r['hour'], r['names'][i])
    vals, byw = defaultdict(list), defaultdict(lambda: defaultdict(list))
    for r in G:
        for i in range(r['k']):
            vals[key(r, i)].append(r['d'][i]); byw[key(r, i)][r['physical_watch_id']].append(r['d'][i])
    print(f'=== GENUINE {model}: {len({r["photo_id"] for r in G})} photos with clean polygon markers')
    print('   hour edge         n(ph/w)  nominal  photo-range [min,max]   between-watch rSD  photo-to-photo SD (repeat watches, n)')
    ref = {}
    for k in sorted(vals, key=lambda t: (int(t[0]), t[1])):
        v = np.array(vals[k]); wm = [np.median(x) for x in byw[k].values()]
        res = [x - np.median(xs) for xs in byw[k].values() if len(xs) > 1 for x in xs]
        nrep = sum(1 for xs in byw[k].values() if len(xs) > 1)
        ptp = float(np.sqrt(np.mean(np.square(res))) * np.sqrt(len(res) / max(1, len(res) - nrep))) if res else float('nan')
        nom = float(np.median(wm))
        ref[k] = (nom, v.min(), v.max(), rsd(wm), ptp)
        print(f'   {k[0]:>4} {k[1]:11} {len(v):4}/{len(wm):<4} {nom:+7.3f}   [{v.min():+6.2f}, {v.max():+6.2f}]          {rsd(wm):5.3f}             {ptp:5.3f} ({nrep})')
    q = out / f'edges_qc_{model}.csv'
    if not q.exists():
        continue
    print(f'=== QC {model}: each edge d (deg) | genuine nominal, photo range | (d-nominal)/photo-to-photo SD')
    allq = list(csv.DictReader(open(q)))
    Q = {(r['photo_id'], r['hour']): r for r in rows(q)}
    for r0 in allq:
        if not r0['hour']:
            print(f"   {r0['photo_id']}: {r0['reason']}"); continue
        r = Q.get((r0['photo_id'], r0['hour']))
        if r is None:
            print(f"   {r0['photo_id']:30} {r0['hour']:>2} {r0['kind']:8} withheld: {r0['reason'] or 'interference not cleared'} | app {r0['status']}"); continue
        parts = []
        for i in range(r['k']):
            nom, lo, hi, bw, ptp = ref.get(key(r, i), (np.nan,) * 5)
            z = (r['d'][i] - nom) / ptp if ptp and ptp == ptp else float('nan')
            flag = ' OUT' if (r['d'][i] < lo or r['d'][i] > hi) else ''
            parts.append(f"{r['names'][i]} {r['d'][i]:+.2f} ({nom:+.2f},[{lo:+.2f},{hi:+.2f}]) z{z:+.1f}{flag}")
        print(f"   {r['photo_id']:30} {r['hour']:>2} {r['kind']:8} rot {float(r['rotation_deg']):+.2f} R {float(r['dial_R_px']):.0f} | app {r['status']} | " + ' | '.join(parts))
