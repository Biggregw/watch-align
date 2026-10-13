#!/usr/bin/env python3
"""ANALYSIS ONLY. Can one filled-template mismatch percentage separate genuine markers from defects?
Reads existing MarkerMismatchBatch CSVs (run 38437479608): genuine_<model>.csv, qc_<model>.csv. No new measurement.
Trusted = the app's existing 'measured' (comparison cleared); raw = any computed percentage (exploratory only)."""
import csv, math, sys
from collections import defaultdict, Counter
from pathlib import Path
import numpy as np

D = Path(sys.argv[1]); TH = [2, 3, 4, 5, 6, 8]
def pct(v, q): return float(np.percentile(v, q)) if len(v) else float('nan')
def load(p): return [r for r in csv.DictReader(open(p))]

G = []
for f in sorted(D.glob('genuine_*.csv')):
    for r in load(f): r['model'] = f.stem[8:]; G.append(r)
ok = [r for r in G if r['pose_status'] == 'accepted' and r['model_shape']]
print('== GENUINE INPUT: rows', len(G), '| pose ok markers', len(ok))

def shape_of(r): return r['model_shape']
groups = defaultdict(list)
for r in ok: groups[(r['model'], shape_of(r))].append(r)
print('\n== 1. GENUINE DISTRIBUTION (trusted only; marker = one marker in one photo)')
print('model        shape     watches photos markers(trusted/all)  withheld%  median  p90   p95   p99   max  | watch-weighted p95')
for k in sorted(groups):
    rs = groups[k]; tr = [r for r in rs if r['confidence'] == 'measured' and r['mismatch_pct']]
    v = np.array([float(r['mismatch_pct']) for r in tr])
    w = defaultdict(list)
    for r in tr: w[r['physical_watch_id']].append(float(r['mismatch_pct']))
    # watch-weighted: each watch contributes its markers with total weight 1
    vals, wts = [], []
    for xs in w.values():
        for x in xs: vals.append(x); wts.append(1 / len(xs))
    o = np.argsort(vals); cv = np.cumsum(np.array(wts)[o]) / sum(wts) if wts else []
    wp95 = float(np.array(vals)[o][np.searchsorted(cv, 0.95)]) if len(vals) else float('nan')
    print(f"{k[0]:12} {k[1]:8} {len({r['physical_watch_id'] for r in rs}):6} {len({r['photo_id'] for r in rs}):6}   {len(tr):5}/{len(rs):<5}       {100*(1-len(tr)/len(rs)):5.1f}%  {pct(v,50):5.2f} {pct(v,90):5.2f} {pct(v,95):5.2f} {pct(v,99):5.2f} {v.max() if len(v) else float('nan'):5.2f} | {wp95:5.2f}")
print('\n   withheld reasons (all models, by shape):')
for sh in ('baton', 'round', 'triangle'):
    c = Counter((r['confidence'].split(':')[0] if r['confidence'] != 'measured' else 'measured') for r in ok if shape_of(r) == sh)
    tot = sum(c.values()); print(f"   {sh:8} {tot:5}: " + ', '.join(f'{k} {100*v/tot:.0f}%' for k, v in c.most_common(6)))

print('\n== 2. REPEAT-PHOTO VARIATION (same physical watch, same marker, trusted): pooled SD and median |difference|')
for k in sorted(groups):
    by = defaultdict(list)
    for r in groups[k]:
        if r['confidence'] == 'measured' and r['mismatch_pct']: by[(r['physical_watch_id'], r['hour'])].append(float(r['mismatch_pct']))
    rep = [xs for xs in by.values() if len(xs) > 1]
    if not rep: print(f'   {k[0]:12} {k[1]:8} no repeat photos'); continue
    res = [x - np.mean(xs) for xs in rep for x in xs]; dof = sum(len(xs) - 1 for xs in rep)
    sd = math.sqrt(sum(e * e for e in res) / dof); diffs = [abs(a - b) for xs in rep for i, a in enumerate(xs) for b in xs[i+1:]]
    print(f'   {k[0]:12} {k[1]:8} {len(rep):4} watch-markers repeated | SD {sd:4.2f} pts | median |diff| {np.median(diffs):4.2f}, p90 {pct(diffs,90):4.2f}, max {max(diffs):4.2f}')

print('\n== 3. ONE COMMON THRESHOLD on genuine (trusted markers). photo FP = share of photos with >=1 marker above T;')
print('   watch FP = share of watches with any marker above T in any photo; per-shape marker FP shown')
photos = defaultdict(list); watches = defaultdict(set); wall = set(); pall = set()
for r in ok:
    pall.add(r['photo_id']); wall.add(r['physical_watch_id'])
    if r['confidence'] == 'measured' and r['mismatch_pct']:
        photos[r['photo_id']].append((float(r['mismatch_pct']), shape_of(r), r['physical_watch_id'], r['model']))
print('   T%  | markers flagged (baton / round / all trusted) | photos with >=1 flag | watches with >=1 flag | by model (watch FP)')
for T in TH:
    mk = Counter(); n = Counter()
    for xs in photos.values():
        for x, sh, w, m in xs: n[sh] += 1; mk[sh] += x > T
    pf = sum(any(x > T for x, *_ in xs) for xs in photos.values())
    wf = {w for xs in photos.values() for x, sh, w, m in xs if x > T}
    bym = []
    for m in sorted({r['model'] for r in ok}):
        wm = {r['physical_watch_id'] for r in ok if r['model'] == m}; bym.append(f"{m.split('_')[-1]} {100*len(wf & wm)/len(wm):.0f}%")
    tot = sum(n.values()); fl = sum(mk.values())
    print(f"   {T:>2}  | {mk['baton']}/{n['baton']} ({100*mk['baton']/max(1,n['baton']):.1f}%)  {mk['round']}/{n['round']} ({100*mk['round']/max(1,n['round']):.1f}%)  {fl}/{tot} ({100*fl/tot:.1f}%) | {pf}/{len(pall)} ({100*pf/len(pall):.0f}%) | {len(wf)}/{len(wall)} ({100*len(wf)/len(wall):.0f}%) | {', '.join(bym)}")

print('\n== 4. LEAVE-ONE-WATCH-OUT: threshold = max trusted genuine reading of all OTHER watches (per shape, or one universal);')
print('   share of held-out watches with >=1 marker above it (an honest out-of-sample genuine false-positive rate)')
for mode in ('universal', 'per-shape'):
    flagged = 0; W = sorted({w for xs in photos.values() for _, _, w, _ in xs})
    allm = [(x, sh, w) for xs in photos.values() for x, sh, w, _ in xs]
    for w in W:
        others = [(x, sh) for x, sh, ww in allm if ww != w]
        mine = [(x, sh) for x, sh, ww in allm if ww == w]
        if mode == 'universal':
            t = max(x for x, _ in others); flagged += any(x > t for x, _ in mine)
        else:
            t = {}
            for x, sh in others: t[sh] = max(t.get(sh, 0), x)
            flagged += any(x > t.get(sh, 1e9) for x, sh in mine)
    print(f'   {mode:10}: {flagged}/{len(W)} held-out watches ({100*flagged/len(W):.1f}%)')

print('\n== 5. SHAPE / SIZE / RESOLUTION: perimeter-to-area (canonical) and % per 1 px of uniform boundary error at R=400 px;')
print('   trusted median by dial radius tercile')
for k in sorted(groups):
    rs = [r for r in groups[k] if r['confidence'] == 'measured' and r['mismatch_pct']]
    pa = []
    for r in rs:
        A = float(r['expected_area']) if r['expected_area'] else float('nan')
        if r['expected_vertices']:
            P = np.array([[float(c) for c in p.split(':')] for p in r['expected_vertices'].split(';')])
            per = float(np.sum(np.hypot(*(np.roll(P, -1, 0) - P).T)))
        else:
            per = 2 * math.sqrt(math.pi * A)
        if A == A and A > 0: pa.append(per / A)
    Rs = np.array([float(r['dial_R_px']) for r in rs]); v = np.array([float(r['mismatch_pct']) for r in rs])
    if not len(rs): continue
    q1, q2 = np.percentile(Rs, [33, 67])
    t = [np.median(v[Rs <= q1]), np.median(v[(Rs > q1) & (Rs <= q2)]), np.median(v[Rs > q2])]
    papa = np.median(pa) if pa else float('nan')
    print(f'   {k[0]:12} {k[1]:8} P/A {papa:6.1f} /R -> {100*papa/400:4.1f}% per px | median by R tercile (<{q1:.0f}, <{q2:.0f}, >): {t[0]:.2f} {t[1]:.2f} {t[2]:.2f}')

print('\n== 6. QC PHOTOS (existing reviewer labels name only the 6 on 1s4cszq/18vdgh0 and the 12 on 1wgfy7k/1w848xb/1kqn8ub)')
LAB = {('GMT_crooked6_1s4cszq', '6'): 'REJECTED (crooked 6)', ('GMT_accepted6_18vdgh0', '6'): 'accepted', ('GMT_accepted6_18vdgh0_photo2', '6'): 'accepted',
       ('GMT_accepted6_18vdgh0_photo3', '6'): 'accepted', ('SUB_rejected12_1wgfy7k', '12'): 'REJECTED (12)', ('SUB_accepted12_1w848xb', '12'): 'accepted',
       ('SUB_accepted12_1w848xb_photo5', '12'): 'accepted', ('SUB_control12_1kqn8ub', '12'): 'accepted (control)', ('SUB_control12_1kqn8ub_photo2', '12'): 'accepted (control)'}
Q = []
for f in sorted(D.glob('qc_*.csv')): Q += load(f)
print('   photo                          marker label                 area (trusted / withheld raw)     above T for T in 2,3,4,5,6,8')
for (p, h), lab in LAB.items():
    r = next((x for x in Q if x['photo_id'] == p and x['hour'] == h), None)
    if r is None or r['pose_status'] != 'accepted': print(f'   {p:30} {h:>2} {lab:20} no pose'); continue
    tr = r['confidence'] == 'measured'; x = float(r['mismatch_pct']) if r['mismatch_pct'] else None
    flags = ' '.join(('Y' if x > T else '.') for T in TH) if (tr and x is not None) else 'n/a (withheld)'
    print(f"   {p:30} {h:>2} {lab:20} {('%.2f%% trusted' % x) if tr else ('withheld' + (' (raw %.2f%%)' % x if x is not None else '') + ': ' + r['confidence'][:38])}  {flags}")
un = [x for x in Q if x['pose_status'] == 'accepted' and (x['photo_id'], x['hour']) not in LAB and x['confidence'] == 'measured' and x['mismatch_pct']]
print(f'   unlabelled trusted QC markers: {len(un)}; above T: ' + ', '.join(f'{T}%: {sum(float(x["mismatch_pct"]) > T for x in un)}' for T in TH))
qall = [x for x in Q if x['pose_status'] == 'accepted' and x['model_shape']]
print(f"   QC withheld: {sum(x['confidence'] != 'measured' for x in qall)}/{len(qall)} markers")
