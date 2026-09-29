"""Sweep known camera poses through the 3D model; writes results/sweep.csv (research, 2026-09-29)."""
import csv, itertools, os, sys
import numpy as np
import rehaut_model as M

os.makedirs('results', exist_ok=True)
W0 = float(sys.argv[1]) if len(sys.argv) > 1 else 0.06
HM = float(sys.argv[2]) if len(sys.argv) > 2 else 0.02
angles = np.arange(-30, 30.01, 2.5)
rows = []
for alpha, D in itertools.product((45, 60, 70, 80), (6.0, 15.0, 50.0)):
    for p, y in itertools.product(angles, angles):
        o = M.measure(p, y, D=D, w0=W0, alpha=alpha, hm=HM)
        o.update(alpha=alpha, D=D, pitch=p, yaw=y, tilt=float(np.degrees(np.arccos(np.cos(np.radians(p)) * np.cos(np.radians(y))))),
                 w0=W0, hm=HM, gap_err=o['gap'] - M.TRUE_GAP,
                 max_off=max(abs(o[f'off{h}']) for h in M.HOURS_ROUND))
        rows.append(o)
keys = ['alpha', 'D', 'w0', 'hm', 'pitch', 'yaw', 'tilt', 'w12', 'w3', 'w6', 'w9', 'V', 'H', 'minmean', 'g_mean', 'g_harm', 'g_minmean', 'g_widest',
        'ell_ratio', 'ell_tilt', 'ell_minor_clock', 'gap', 'gap_err', 'rot', 'max_off'] + [f'off{h}' for h in M.HOURS_ROUND]
with open('results/sweep.csv', 'w', newline='') as f:
    w = csv.DictWriter(f, fieldnames=keys, extrasaction='ignore'); w.writeheader()
    for r in rows: w.writerow({k: (round(float(r[k]), 6) if isinstance(r[k], (float, np.floating)) else r[k]) for k in keys})
print(len(rows), 'rows')
