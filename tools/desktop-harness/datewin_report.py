"""Date-window research report (2026-09-30). Reads DateWin CSVs; prints coverage, resize-check stability
and genuine-vs-replica spreads for each measurement. Research only.

python3 datewin_report.py <dw.csv>... --classes <batch_dir,...>
"""
import csv, glob, math, sys, collections
import numpy as np

args = sys.argv[1:]
k = args.index('--classes'); files, dirs = args[:k], args[k + 1].split(',')

def read_batch(ds):
    hdr, rows = None, []
    for f in sorted(x for d in ds for x in glob.glob(d + '/*.csv') if not x.endswith('_round.csv')):
        L = list(csv.reader(open(f)))
        if L and L[0] and L[0][0] == 'path': hdr = L[0]; L = L[1:]
        rows += L
    return {r[0]: dict(zip(hdr, r)) for r in rows}

B = read_batch(dirs)
hdr, rows = None, []
for f in files:
    L = list(csv.reader(open(f)))
    if L[0][0] == 'file': hdr = L[0]; L = L[1:]
    rows += L
R = [dict(zip(hdr, r)) for r in rows if len(r) == len(hdr)]
other = [r for r in rows if len(r) != len(hdr)]

def fl(x):
    try: return float(x)
    except Exception: return float('nan')

# Metrics (name, column, description). u = along the dial edge (digit height direction), v = radial.
MET = [
    ('aperture centre vs 15/45 tick', 'ap_u_vs_tick', 'aperture widths'),
    ('numeral across aperture (u)', 'num_u_in_ap', 'aperture widths'),
    ('numeral along aperture (v)', 'num_v_in_ap', 'aperture heights'),
    ('numeral height (u) / dial R', 'num_w', 'dial radii'),
    ('aperture u size / dial R', 'ap_w', 'dial radii'),
    ('aperture v size / dial R', 'ap_h', 'dial radii'),
    ('lens centre vs tick', 'lens_u_vs_tick', 'lens widths'),
]
print(f'rows {len(rows)}; with a full measurement row {len(R)}; skipped {len(other)}')
why = collections.Counter()
for r in rows:
    if len(r) != len(hdr): why[(r[-1] if r else '').strip('"')[:40]] += 1
print('not measured:', dict(why.most_common(6)))

good = []
for r in R:
    c = B.get(r['file'], {}).get('class', '?')
    r['cls'] = c
    ok = all(math.isfinite(fl(r[x])) for x in ('ap_u_vs_tick', 'num_u_in_ap', 'num_w'))
    r['ok'] = ok
    if ok: good.append(r)
cov = collections.Counter((r['cls'], r['ok']) for r in R)
print('measured (tick, aperture, numeral all found):', dict(cov))

print('\nResize-check stability (max-min over 100/94/88%), median and 90th percentile:')
stab = {}
for name, col, unit in MET:
    sp = []
    for r in good:
        v = [fl(r[col]), fl(r['s94_' + col]), fl(r['s88_' + col])]
        if all(math.isfinite(x) for x in v): sp.append(max(v) - min(v))
    if sp:
        stab[col] = np.percentile(sp, 90)
        print(f'  {name:32s} n={len(sp):3d}  median {np.median(sp):.3f}  p90 {np.percentile(sp, 90):.3f} {unit}')

print('\nGenuine vs replica (stable readings only: spread within the p90 above):')
for name, col, unit in MET:
    g, rp = [], []
    for r in good:
        v = [fl(r[col]), fl(r['s94_' + col]), fl(r['s88_' + col])]
        if not all(math.isfinite(x) for x in v) or max(v) - min(v) > stab.get(col, 1e9): continue
        (g if r['cls'] == 'gen' else rp if r['cls'] == 'rep' else []).append(v[0])
    if len(g) < 3: continue
    g, rp = np.array(g), np.array(rp)
    lo, hi = g.min(), g.max()
    out = int(((rp < lo) | (rp > hi)).sum()) if len(rp) else 0
    print(f'  {name:32s} gen n={len(g):3d} median {np.median(g):+.3f} IQR {np.percentile(g,25):+.3f}..{np.percentile(g,75):+.3f} range {lo:+.3f}..{hi:+.3f}'
          f' | rep n={len(rp):3d} median {np.median(rp) if len(rp) else float("nan"):+.3f} range {rp.min() if len(rp) else float("nan"):+.3f}..{rp.max() if len(rp) else float("nan"):+.3f}  outside genuine range: {out}')

print('\nAngle sensitivity (genuine, marker-layout tilt available): correlation with tilt')
for name, col, unit in MET:
    xs = [(fl(r['marker_tilt']), fl(r[col])) for r in good if r['cls'] == 'gen' and math.isfinite(fl(r['marker_tilt'])) and math.isfinite(fl(r[col]))]
    if len(xs) > 5:
        a = np.array(xs); print(f'  {name:32s} n={len(a)} corr(|value - median|, tilt) = {np.corrcoef(a[:,0], np.abs(a[:,1]-np.median(a[:,1])))[0,1]:+.2f}')
