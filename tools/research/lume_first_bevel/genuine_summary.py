#!/usr/bin/env python3
"""RESEARCH ONLY. Aggregates lume_first_compare.py results over verified genuine 126710-family photos, one value per
PHYSICAL WATCH (median of its photos), in units of dial radius (R) so photographs of different resolution compare.
Prints coverage, genuine distributions, hold-out thickness accuracy and sensitivity, and where given QC values fall.
Usage: genuine_summary.py RESULT_DIR MANIFEST.csv [QC_METRICS_JSON ...]"""
import csv, json, sys
from collections import defaultdict
from pathlib import Path
import numpy as np

res_dir, manifest = Path(sys.argv[1]), sys.argv[2]
watch = {r['photo_id']: r['physical_watch_id'] for r in csv.DictReader(open(manifest))}
M = {}
for f in sorted(res_dir.glob('*_result.json')):
    d = json.load(open(f)); pid = d['id']
    if pid in watch: M[pid] = d['metrics']
print(f'== GENUINE 126710 lume-first results: {len(M)} photos with a pose, {len({watch[p] for p in M})} physical watches '
      f'(manifest {len(watch)} photos / {len(set(watch.values()))} watches)')

def pr(m, key): return m[key] / m['R'] * 100 if m.get(key) == m.get(key) and m.get(key) is not None else np.nan   # % of R
ok = {p: m for p, m in M.items() if not m.get('fail') and not m.get('merged') and m['rotation_deg'] == m['rotation_deg']}
strict = {p: m for p, m in ok.items() if m['sens'].get('merged', 0) == 0}
print(f'   of those, luminous fill never merged in any sensitivity variant: {len(strict)} photos')
print(f'   complete three-side comparison: {len(ok)} photos; luminous fill merged with metal: {sum(m.get("merged", False) for m in M.values())}; '
      f'no comparison: {len(M) - len(ok)}')
for side in ('top', 'left', 'right'):
    st = [m['sides'][side]['state'] for m in M.values()]
    print(f'   {side:5}: observed {st.count("observed")}, inferred {st.count("inferred")}, withheld {st.count("withheld")}')

by = defaultdict(list)
for p, m in ok.items(): by[watch[p]].append(m)
def per_watch(fn):
    v = []
    for ms in by.values():
        x = [fn(m) for m in ms]; x = [y for y in x if y == y]
        if x: v.append(float(np.median(x)))
    return np.array(v)
rows = [('rotation (deg, + clockwise)', lambda m: m['rotation_deg']),
        ('tangential shift (%R, + right)', lambda m: pr(m, 'tangential_px')),
        ('radial shift (%R, + towards track)', lambda m: pr(m, 'radial_px')),
        ('uniform outline growth (%R)', lambda m: pr(m, 'growth_px')),
        ('left side shape angle (deg)', lambda m: m['sides']['left']['shape_angle_deg']),
        ('right side shape angle (deg)', lambda m: m['sides']['right']['shape_angle_deg']),
        ('top side shape angle (deg)', lambda m: m['sides']['top']['shape_angle_deg'])]
print('\n== GENUINE DISTRIBUTION, one value per physical watch')
print('   measure                              watches  median    p5      p95    max|x|')
dist = {}
for name, fn in rows:
    v = per_watch(fn); dist[name] = v
    if len(v): print(f'   {name:36} {len(v):5}  {np.median(v):+6.3f}  {np.percentile(v,5):+6.3f}  {np.percentile(v,95):+6.3f}  {np.max(np.abs(v)):6.3f}')

print('\n== REPEAT PHOTOS of the same physical watch (spread = max - min across its photos)')
for name, fn in rows[:4]:
    sp = []
    for ms in by.values():
        x = [fn(m) for m in ms]; x = [y for y in x if y == y]
        if len(x) > 1: sp.append(np.ptp(x))
    if sp: print(f'   {name:36} watches {len(sp):3}  median spread {np.median(sp):.3f}  max {np.max(sp):.3f}')
print('   per repeat watch (photo: R px, rotation deg, tangential %R, radial %R):')
for w, ms in by.items():
    if len(ms) > 1:
        ids = [p for p in ok if watch[p] == w]
        print(f'     {w}: ' + '; '.join(f"{p[:6]} R{ok[p]['R']:.0f} {ok[p]['rotation_deg']:+.2f} {pr(ok[p],'tangential_px'):+.3f} {pr(ok[p],'radial_px'):+.3f}" for p in ids))

print('\n== HOLD-OUT: thickness from one side predicting another side\'s observed outer edge (median |error| in %R, per photo)')
ho = defaultdict(list)
for m in ok.values():
    for h in m['holdout']: ho[(h['src'], h['dst'])].append(h['mad_px'] / m['R'] * 100)
for k in sorted(ho): print(f'   {k[0]:5} -> {k[1]:5}  photos {len(ho[k]):3}  median {np.median(ho[k]):.3f}%R  p90 {np.percentile(ho[k],90):.3f}%R')

print('\n== SENSITIVITY (per photo range over threshold / percentile / corridor / anchor variants), median and p90 across photos')
for key, unit in (('tangential_px', '%R'), ('radial_px', '%R'), ('growth_px', '%R'), ('rotation_deg', 'deg')):
    w = []
    for m in ok.values():
        r = m['sens'].get(key)
        if r: w.append((r[1] - r[0]) / (m['R'] / 100 if unit == '%R' else 1))
    if w: print(f'   {key:14} median range {np.median(w):.3f} {unit}, p90 {np.percentile(w,90):.3f} {unit}')

for q in sys.argv[3:]:
    m = json.load(open(q))['metrics'] if q.endswith('.json') and Path(q).exists() else None
    if m is None: continue
    print(f'\n== QC {Path(q).stem}: value | share of genuine watches with |value| >= this')
    for name, fn in rows:
        x = fn(m); v = dist.get(name, np.array([]))
        if x == x and len(v): print(f'   {name:36} {x:+7.3f} | {100 * np.mean(np.abs(v) >= abs(x)):5.1f}% of {len(v)} watches')
