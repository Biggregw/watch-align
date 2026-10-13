#!/usr/bin/env python3
"""DIAGNOSTIC ONLY (no detector, reference or threshold change). For every genuine 124060 round-marker observation:
radial profiles from the exact rectified grayscale crop (LuminousCircleEvidence, archived pose), the first outward
fall from the lume plateau on each ray, a circle fitted to those first falls, and a comparison with the frozen and the
candidate (e0b0b651) fitted circles. Question: does an accepted fit follow the first fall (the luminous boundary) or a
later transition (the bevel groove / metal)?

Diagnostic definitions (analysis only, stated in the report): plateau P = median grey at 0.35-0.70 r0, dial D = median
at 1.35-1.50 r0 (r0 = reference luminous radius); a ray's first fall = first r >= 0.70 r0 where grey stays below
P - 0.35 (P - D) for two samples; it is at the lume exit when r <= r0 + 0.0018 R. Fits: 'follows first fall' when the
fitted radius is within 0.0012 R of the first-fall circle; 'outside (groove-like)' when 0.0020 R or more larger.

Usage: groove_diag.py FROZEN_SCORED CANDIDATE_SCORED REFERENCES EVIDENCE_DIR OUT_DIR
"""
import csv
import json
import math
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
from PIL import Image

RAYS = 192


def load(path):
    out = {}
    for line in Path(path).read_text().splitlines():
        p = json.loads(line)
        out[p['photo_id']] = p
    return out


def marker(p, hour):
    for m in p.get('markers', []):
        if int(m['hour']) == hour:
            return m
    return None


def ref_radius(refs, hour):
    poly = refs['models']['submariner_124060']['positions'][str(hour)]['geometric_polygon']
    pts = np.array(poly, float)
    c = pts.mean(axis=0)
    return float(np.median(np.hypot(*(pts - c).T)))


def bilinear(img, x, y):
    h, w = img.shape
    x = np.clip(x, 0, w - 1.001); y = np.clip(y, 0, h - 1.001)
    x0 = np.floor(x).astype(int); y0 = np.floor(y).astype(int); fx = x - x0; fy = y - y0
    return (img[y0, x0] * (1 - fx) * (1 - fy) + img[y0, x0 + 1] * fx * (1 - fy)
            + img[y0 + 1, x0] * (1 - fx) * fy + img[y0 + 1, x0 + 1] * fx * fy)


def kasa(x, y):
    A = np.c_[2 * x, 2 * y, np.ones_like(x)]
    sol, *_ = np.linalg.lstsq(A, x * x + y * y, rcond=None)
    cx, cy, c = sol
    return cx, cy, math.sqrt(max(c + cx * cx + cy * cy, 0))


def analyse(ev, gray, r0):
    """Rays about the expected (ring-moved master) centre: independent of any fitted circle."""
    n, step, half = ev['n'], ev['step'], ev['half']
    c0 = (n - 1) / 2.0                              # crop pixel of the expected centre
    r0p = r0 / step
    rs = np.arange(0.35 * r0p, 1.5 * r0p, 0.1)
    angles = np.arange(RAYS) * 2 * math.pi / RAYS
    prof = np.array([bilinear(gray, c0 + rs * math.cos(a), c0 + rs * math.sin(a)) for a in angles])
    P = float(np.median(prof[:, (rs >= 0.35 * r0p) & (rs <= 0.70 * r0p)]))
    D = float(np.median(prof[:, (rs >= 1.35 * r0p) & (rs <= 1.50 * r0p)]))
    level = P - 0.35 * (P - D)
    first = np.full(RAYS, np.nan)
    start = np.searchsorted(rs, 0.70 * r0p)
    for k in range(RAYS):
        v = prof[k]
        below = v < level
        for i in range(start, len(rs) - 1):
            if below[i] and below[i + 1]:
                first[k] = rs[i]; break
    exit_lim = (r0 + 0.0018) / step
    resolved = np.isfinite(first) & (first <= exit_lim)
    late = np.isfinite(first) & (first > exit_lim)
    out = dict(P=P, D=D, contrast=P - D, resolved=int(resolved.sum()), late=int(late.sum()),
               none=int((~np.isfinite(first)).sum()), sectors=0, ff_radius_R=math.nan, ff_cx=math.nan, ff_cy=math.nan,
               ff_rms_px=math.nan, late_dir_deg=math.nan, late_resultant=math.nan)
    sect = set(int(a / (2 * math.pi) * 12) for a, ok in zip(angles, resolved) if ok)
    out['sectors'] = len(sect)
    if resolved.sum() >= 12:
        x = c0 + first[resolved] * np.cos(angles[resolved]); y = c0 + first[resolved] * np.sin(angles[resolved])
        cx, cy, r = kasa(x, y)
        out.update(ff_radius_R=r * step, ff_cx=cx, ff_cy=cy,
                   ff_rms_px=float(np.sqrt(np.mean((np.hypot(x - cx, y - cy) - r) ** 2))))
    if late.sum() >= 3:
        vx, vy = np.cos(angles[late]).sum(), np.sin(angles[late]).sum()
        out['late_dir_deg'] = math.degrees(math.atan2(vy, vx)) % 360   # crop frame: 0 = 3 o'clock side, 90 = towards 6
        out['late_resultant'] = math.hypot(vx, vy) / late.sum()
    out['_first'] = first; out['_angles'] = angles; out['_prof'] = prof; out['_rs'] = rs; out['_level'] = level
    return out


def fit_px(fit, ev):
    """Fitted circle (canonical dial units) -> crop pixels of the evidence crop."""
    if not fit or not fit.get('center') or fit.get('radius_R') in (None, ''):
        return None
    step, half, c = ev['step'], ev['half'], ev['center']
    x = (fit['center'][0] - (c[0] - half)) / step - 0.5
    y = (fit['center'][1] - (c[1] - half)) / step - 0.5
    return x, y, fit['radius_R'] / step


def classify(fpx, a, ev):
    if fpx is None:
        return 'no fit', math.nan, math.nan
    if not math.isfinite(a['ff_radius_R']) or a['sectors'] < 7:
        return 'first fall not identifiable', math.nan, math.nan
    dr = fpx[2] * ev['step'] - a['ff_radius_R']
    dc = math.hypot(fpx[0] - a['ff_cx'], fpx[1] - a['ff_cy']) * ev['step']
    if dr >= 0.0020:
        return 'outside first fall (groove-like)', dr, dc
    if abs(dr) <= 0.0012:
        return 'follows first fall', dr, dc
    return 'ambiguous', dr, dc


def main(frozen_p, cand_p, refs_p, evdir, outdir):
    frozen, cand, refs = load(frozen_p), load(cand_p), json.loads(Path(refs_p).read_text())
    evdir, outdir = Path(evdir), Path(outdir); outdir.mkdir(parents=True, exist_ok=True)
    rows = []; keep = {}
    for jf in sorted(evdir.glob('*.json')):
        ev = json.loads(jf.read_text()); m = ev['marker']; hour = int(m['hour']); pid = jf.stem.rsplit('_', 1)[0]
        gray = np.asarray(Image.open(evdir / f'{pid}_{hour}_gray.png').convert('L'), float)
        r0 = ref_radius(refs, hour)
        a = analyse(ev, gray, r0)
        fm, cm = marker(frozen.get(pid, {}), hour), marker(cand.get(pid, {}), hour)
        ffit = (fm or {}).get('fit') or {}; cfit = (cm or {}).get('fit') or {}
        f_acc = fm is not None and fm.get('new_fitted') is not None
        c_acc = cm is not None and cm.get('new_fitted') is not None
        fpx, cpx = fit_px(ffit if f_acc else None, ev), fit_px(cfit if c_acc else None, ev)
        fcls, fdr, fdc = classify(fpx, a, ev); ccls, cdr, cdc = classify(cpx, a, ev)
        row = dict(photo_id=pid, hour=hour, r0_R=round(r0, 5), plateau=round(a['P'], 1), dial=round(a['D'], 1),
                   rays_resolved=a['resolved'], rays_late=a['late'], rays_none=a['none'], sectors_resolved=a['sectors'],
                   first_fall_radius_R=round(a['ff_radius_R'], 5) if math.isfinite(a['ff_radius_R']) else '',
                   first_fall_rms_px=round(a['ff_rms_px'], 3) if math.isfinite(a['ff_rms_px']) else '',
                   late_dir_deg=round(a['late_dir_deg'], 1) if math.isfinite(a['late_dir_deg']) else '',
                   late_resultant=round(a['late_resultant'], 2) if math.isfinite(a['late_resultant']) else '',
                   frozen_accepted=f_acc, frozen_radius_R=ffit.get('radius_R', '') if f_acc else '', frozen_fitted_pct=(fm or {}).get('new_fitted', ''),
                   frozen_class=fcls, frozen_dr_R=round(fdr, 5) if math.isfinite(fdr) else '',
                   cand_accepted=c_acc, cand_radius_R=cfit.get('radius_R', '') if c_acc else '', cand_fitted_pct=(cm or {}).get('new_fitted', ''),
                   cand_class=ccls, cand_dr_R=round(cdr, 5) if math.isfinite(cdr) else '',
                   cand_reason=str(cfit.get('reason', ''))[:90], outer_status=(cm or {}).get('outer_status', ''),
                   outer_total_pct=(cm or {}).get('outer_total_pct', ''), interference=(cm or {}).get('interference_reason', ''))
        rows.append(row); keep[(pid, hour)] = (a, ev, gray, fpx, cpx)
    with open(outdir / 'groove_diagnostic.csv', 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)

    def tally(key, acc):
        t = defaultdict(int)
        for r in rows:
            if r[acc]:
                t[r[key]] += 1
        return dict(t)
    print('OBSERVATIONS', len(rows), 'photos', len({r['photo_id'] for r in rows}))
    print('FROZEN accepted fits by class:', tally('frozen_class', 'frozen_accepted'))
    print('CANDIDATE accepted fits by class:', tally('cand_class', 'cand_accepted'))
    ident = [r for r in rows if r['first_fall_radius_R'] != '' and r['sectors_resolved'] >= 7]
    rr = np.array([r['first_fall_radius_R'] for r in ident], float)
    print(f'FIRST-FALL circle identifiable (>=7 of 12 sectors): {len(ident)} of {len(rows)}; radius_R median {np.median(rr):.5f} '
          f'p10 {np.percentile(rr,10):.5f} p90 {np.percentile(rr,90):.5f}; rms_px median {np.median([r["first_fall_rms_px"] for r in ident]):.3f}')
    cand_acc_unident = [r for r in rows if r['cand_accepted'] and r['cand_class'] == 'first fall not identifiable']
    print('Candidate-accepted fits where the first fall is NOT identifiable:', len(cand_acc_unident))
    # lighting: per photo, agreement of late-ray directions across markers
    by = defaultdict(list)
    for r in rows:
        if r['late_dir_deg'] != '' and r['rays_late'] >= 8:
            by[r['photo_id']].append(math.radians(float(r['late_dir_deg'])))
    print('PHOTO-WIDE LIGHTING (markers with >=8 late rays): photo, markers, resultant of their late directions (1 = same side), mean dir deg')
    for pid, v in sorted(by.items(), key=lambda kv: -len(kv[1])):
        if len(v) >= 3:
            res = math.hypot(sum(map(math.cos, v)), sum(map(math.sin, v))) / len(v)
            print(f'  {pid} {len(v)} {res:.2f} {math.degrees(math.atan2(sum(map(math.sin, v)), sum(map(math.cos, v)))) % 360:.0f}')
    gro = [r for r in rows if r['cand_accepted'] and r['cand_class'].startswith('outside')]
    print('GROOVE-LIKE candidate-accepted fits: photo hour cand_R first_fall_R dr fitted_pct late_rays outer_status outer_total_pct')
    for r in sorted(gro, key=lambda r: (r['photo_id'], r['hour'])):
        print(f"  {r['photo_id']} {r['hour']} {r['cand_radius_R']:.5f} {r['first_fall_radius_R']} {r['cand_dr_R']} {r['cand_fitted_pct']} {r['rays_late']} {r['outer_status']} {r['outer_total_pct']}")
    print('PHOTOS with groove-like accepted fits:', len({r['photo_id'] for r in gro}))
    for r in rows:
        print('ROW', ','.join(str(r[k]) for k in ('photo_id', 'hour', 'rays_resolved', 'rays_late', 'sectors_resolved', 'first_fall_radius_R',
                                                'frozen_class', 'frozen_dr_R', 'cand_class', 'cand_dr_R', 'cand_fitted_pct', 'outer_status')))
    return keep


def plots(keep, cases, outdir):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    outdir = Path(outdir)
    for pid, hour in cases:
        if (pid, hour) not in keep:
            print('PLOT missing', pid, hour); continue
        a, ev, gray, fpx, cpx = keep[(pid, hour)]
        fig, ax = plt.subplots(1, 2, figsize=(12, 5.2))
        ax[0].imshow(gray, cmap='gray', vmin=0, vmax=255)
        t = np.linspace(0, 2 * math.pi, 300)
        for c, col, lab in ((fpx, 'red', 'frozen fit'), (cpx, 'orange', 'candidate fit')):
            if c:
                ax[0].plot(c[0] + c[2] * np.cos(t), c[1] + c[2] * np.sin(t), color=col, lw=1, label=lab)
        if math.isfinite(a['ff_radius_R']):
            r = a['ff_radius_R'] / ev['step']; ax[0].plot(a['ff_cx'] + r * np.cos(t), a['ff_cy'] + r * np.sin(t), color='lime', lw=1, ls='--', label='first-fall circle')
        c0 = (ev['n'] - 1) / 2
        ok = np.isfinite(a['_first'])
        ax[0].scatter(c0 + a['_first'][ok] * np.cos(a['_angles'][ok]), c0 + a['_first'][ok] * np.sin(a['_angles'][ok]), s=3, c='cyan')
        ax[0].legend(fontsize=7, loc='lower right'); ax[0].set_title(f'{pid} / {hour}: rectified crop', fontsize=9); ax[0].axis('off')
        rs = a['_rs'] * ev['step']
        for k, lab in ((RAYS * 3 // 4, 'towards 12 (up)'), (0, 'towards 3'), (RAYS // 4, 'towards 6 (down)'), (RAYS // 2, 'towards 9')):
            ax[1].plot(rs, a['_prof'][k], lw=1, label=lab)
        ax[1].axhline(a['_level'], color='k', ls=':', lw=1, label='first-fall level')
        for c, col in ((fpx, 'red'), (cpx, 'orange')):
            if c:
                ax[1].axvline(c[2] * ev['step'], color=col, lw=1)
        if math.isfinite(a['ff_radius_R']):
            ax[1].axvline(a['ff_radius_R'], color='lime', ls='--', lw=1)
        ax[1].set_xlabel('radius from expected centre (R)'); ax[1].set_ylabel('grey'); ax[1].legend(fontsize=7)
        ax[1].set_title(f"resolved rays {a['resolved']}/{RAYS}, late {a['late']}, sectors {a['sectors']}/12", fontsize=9)
        fig.tight_layout(); fig.savefig(outdir / f'profile_{pid}_{hour}.png', dpi=90); plt.close(fig)


if __name__ == '__main__':
    k = main(*sys.argv[1:6])
    cases = [('c14f8b3abb49c038', h) for h in (1, 2, 4, 5, 7, 8, 10, 11)] + [('80d288dcc8689345', 2), ('855041528fc1ac79', 5)]
    plots(k, cases, sys.argv[5])
