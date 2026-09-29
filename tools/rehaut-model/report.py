"""Inversion/noise analysis, RETAKE table and plots for the rehaut pose study (research, 2026-09-29).
Reads results/sweep.csv and results/real_genuine_pose.csv; writes results/*.csv and results/*.png."""
import csv, math
import numpy as np
import matplotlib; matplotlib.use('Agg')
import matplotlib.pyplot as plt
import rehaut_model as M

rng = np.random.default_rng(1)
sw = list(csv.DictReader(open('results/sweep.csv')))
for r in sw:
    for k in r: r[k] = float(r[k])
real = list(csv.DictReader(open('results/real_genuine_pose.csv')))
def fl(x):
    try: return float(x)
    except Exception: return float('nan')

# 1. Inversion: yaw from H (pitch = 0 slice), with measurement noise and a wrong slope assumption.
def curve(alpha, D=15.0, key='H', axis='yaw'):
    s = sorted([r for r in sw if r['alpha'] == alpha and r['D'] == D and r['pitch' if axis == 'yaw' else 'yaw'] == 0], key=lambda r: r[axis])
    return np.array([r[axis] for r in s]), np.array([r[key] for r in s])
def invert(Hobs, alpha):
    y, h = curve(alpha); o = np.argsort(h)
    return np.interp(Hobs, h[o], y[o])
NOISE_SD = 0.19   # sd of H on WOS near-frontal photos (affine tilt < 4 deg)
rows = []
for a_true in (45, 60, 70, 80):
    for yaw in (0, 5, 10, 15, 20):
        Ht = np.interp(yaw, *curve(a_true))
        est = invert(Ht + rng.normal(0, NOISE_SD, 4000), a_true)
        row = dict(alpha_true=a_true, yaw_true=yaw, H_true=round(Ht, 3),
                   est_sd_deg=round(float(np.std(est)), 1),
                   est_90pct_halfwidth_deg=round(float(np.percentile(np.abs(est - yaw), 90)), 1))
        for a_ass in (45, 60, 70, 80):
            row[f'noiseless_est_if_alpha{a_ass}'] = round(float(invert(Ht, a_ass)), 1)
        rows.append(row)
with open('results/inversion.csv', 'w', newline='') as f:
    w = csv.DictWriter(f, fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)

# 2. Distortion budget vs tilt (alpha-independent; D=15 and D=6).
def pred(pitch, yaw, D):
    o = M.measure(pitch, yaw, D=D)
    return dict(gap_err=o['gap'] - M.TRUE_GAP, rot=o['rot'], max_off=max(abs(o[f'off{h}']) for h in M.HOURS_ROUND))
budget = []
for D in (6.0, 15.0, 50.0):
    for t in (0, 5, 10, 15, 20, 30):
        for name, (p, y) in (('towards 6', (-t, 0)), ('towards 12', (t, 0)), ('towards 3', (0, t))):
            d = pred(p, y, D); budget.append(dict(D=D, tilt=t, direction=name, **{k: round(v, 4) for k, v in d.items()}))
with open('results/distortion_budget.csv', 'w', newline='') as f:
    w = csv.DictWriter(f, fieldnames=list(budget[0])); w.writeheader(); w.writerows(budget)

# 3. The genuine RETAKE photos: independent pose and predicted distortion.
ret = [r for r in real if r['app_pose'] == 'RETAKE']
tab = []
for r in ret:
    tilt = fl(r['aff8_tilt']); minor = fl(r['aff8_minor']); A = fl(r['par_A']); B = fl(r['par_B'])
    # Camera side from the affine minor axis (mod 180) disambiguated by the parallax sign where available.
    yaw_c = tilt * abs(math.sin(math.radians(minor))); pit_c = tilt * abs(math.cos(math.radians(minor)))
    if not math.isnan(A): yaw_c *= (1 if A < 0 else -1)      # A<0: camera towards 3 (model sign)
    if not math.isnan(B): pit_c *= (1 if B > 0 else -1)
    if math.isnan(tilt): d6 = d15 = dict(gap_err=float('nan'), rot=float('nan'), max_off=float('nan'))
    else: d6 = pred(pit_c, yaw_c, 6.0); d15 = pred(pit_c, yaw_c, 15.0)
    ws = [fl(r[k]) for k in ('w12_r', 'w3_r', 'w6_r', 'w9_r')]
    tab.append(dict(photo=r['name'], src=r['src'], w12_w3_w6_w9_over_r='/'.join(f'{x:.3f}' for x in ws),
                    H=round(fl(r['H']), 2), V=round(fl(r['V']), 2), minmean=round(fl(r['minmean']), 2),
                    affine_tilt=round(tilt, 1), affine_minor_clock=round(minor, 0), parallax_A=round(A, 3), parallax_B=round(B, 3),
                    est_pitch=round(pit_c, 1), est_yaw=round(yaw_c, 1),
                    pred_gap_err_D15=round(d15['gap_err'], 3), pred_rot_D6=round(d6['rot'], 2), pred_rot_D15=round(d15['rot'], 2),
                    pred_max_offset=round(d15['max_off'], 3), measured_gap=round(fl(r['gap']), 3), measured_rot=round(fl(r['rot']), 2)))
with open('results/retake_photos.csv', 'w', newline='') as f:
    w = csv.DictWriter(f, fieldnames=list(tab[0])); w.writeheader(); w.writerows(tab)
for t in tab: print(t)

# 4. Plots.
fig, ax = plt.subplots(1, 2, figsize=(11, 4.2))
for a in (45, 60, 70, 80):
    y, h = curve(a); ax[0].plot(y, h, label=f'slope {a}°')
    y, mm = curve(a, key='minmean'); ax[1].plot(y, mm, label=f'slope {a}°')
ax[0].set(xlabel='camera yaw (deg, + towards 3)', ylabel='H = (w3-w9)/(w3+w9)', title='Model: horizontal rehaut asymmetry'); ax[0].axhspan(-NOISE_SD, NOISE_SD, color='grey', alpha=.2, label='±1 sd real noise')
ax[1].axhline(0.45, color='r', ls='--', label='RETAKE (<0.45)'); ax[1].axhline(0.75, color='orange', ls='--', label='CORRECTABLE (<0.75)')
ax[1].set(xlabel='camera yaw (deg)', ylabel='sector min/mean', title='Model: min/mean width vs yaw')
for a_ in ax: a_.legend(fontsize=7); a_.grid(alpha=.3)
fig.tight_layout(); fig.savefig('results/model_asymmetry_vs_yaw.png', dpi=120)

fig, ax = plt.subplots(1, 3, figsize=(13, 4))
for D, ls in ((6.0, ':'), (15.0, '-'), (50.0, '--')):
    for name, c in (('towards 6', 'C0'), ('towards 12', 'C1'), ('towards 3', 'C2')):
        b = [x for x in budget if x['D'] == D and x['direction'] == name]
        t = [x['tilt'] for x in b]
        ax[0].plot(t, [x['gap_err'] for x in b], ls=ls, color=c, label=f'{name}, D={D:g}R')
        ax[1].plot(t, [x['rot'] for x in b], ls=ls, color=c)
        ax[2].plot(t, [x['max_off'] for x in b], ls=ls, color=c)
ax[0].axhline(0.070 - M.TRUE_GAP, color='r', lw=.8); ax[0].set(xlabel='tilt (deg)', ylabel='12 gap error (triangle widths)', title='12 gap (red: LOW_CLEARANCE level)')
ax[1].set(xlabel='tilt (deg)', ylabel='12 axis rotation (deg)', title='12 rotation')
ax[2].set(xlabel='tilt (deg)', ylabel='max |offset| (diameters)', title='round-marker centring'); ax[2].axhline(0.15, color='r', lw=.8)
ax[0].legend(fontsize=6)
for a_ in ax: a_.grid(alpha=.3)
fig.tight_layout(); fig.savefig('results/model_distortion_vs_tilt.png', dpi=120)

fig, ax = plt.subplots(1, 2, figsize=(11, 4.2))
cols = {'GOOD': 'C2', 'CORRECTABLE': 'C1', 'RETAKE': 'C3'}
for r in real:
    t = fl(r['aff8_tilt']); H = fl(r['H']); A = fl(r['par_A'])
    ax[0].scatter(t, H, s=18, c=cols.get(r['app_pose'], 'k'), marker={'phillips': 'o', 'wos': 's', 'render': '^'}.get(r['src'], 'x'))
    if not math.isnan(A): ax[1].scatter(A, H, s=18, c=cols.get(r['app_pose'], 'k'))
ax[0].set(xlabel='independent tilt from 8-marker affine (deg)', ylabel='app sector H', title='Real genuine photos: rehaut H vs dial-plane tilt\n(o Phillips, □ WOS, △ render, x other; colour = app label)')
ax[1].set(xlabel='index-vs-print parallax A (− = camera towards 3)', ylabel='app sector H', title='H vs parallax sign cue')
for a_ in ax: a_.grid(alpha=.3); a_.axhline(0, color='k', lw=.5)
fig.tight_layout(); fig.savefig('results/real_H_vs_independent_pose.png', dpi=120)
print('inversion rows', len(rows))
for r in rows:
    if r['alpha_true'] == 45: print(r)
