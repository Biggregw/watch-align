"""Research only: close-ups and numbers behind the outer-metal fit (metal.py), to see WHY a marker reads as it does.

  python3 metal_diag.py dump.jsonl image_root metal.csv out_dir [app.csv] [--all] [--qc] [--ids=photo:hour,...] [--b64]

Cases: every marker the new fit calls Clearly off or Possible misalignment - inspect, named ones (--ids, photo id
prefix:hour), with --qc every 12 triangle (base rows of metal.csv; with --all every marker of every photo).
Per photo: the registration (minute-tick lattice residuals, tick scale, bad ticks) and this photo's edge correction.
Per marker: old vs new numbers, which edge points were used or rejected and why, the mean residual on each side of
the outline (a side consistently in or out = the genuine outline does not match this marker's shape or size), and a
diagnostic-only free-size fit (never used for measuring). Close-up: photo, genuine overlay (yellow, thin), fitted
outline (green where its edge was seen, dashed magenta where it is reconstructed), edge points by reason, and the
minute-track reference (cyan: the hour line through the marker; batons are compared with it, the triangle's top edge
with its perpendicular). With app.csv (desktop-harness TrackDump) the app's 12 triangle top-edge rotation is compared
with the new fit's triangle rotation. --b64 prints each close-up as base64 (for runners whose files cannot be fetched).
"""
import base64
import csv
import json
import math
import os
import sys

import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import track as tr  # noqa: E402
import metal as mt  # noqa: E402
import metal_eval as me  # noqa: E402
import overlay_gap as og  # noqa: E402

COL = {0: (60, 220, 60), 1: (40, 40, 255), 2: (0, 165, 255), 3: (255, 0, 255), 4: (255, 255, 0), 5: (128, 128, 128)}


def f(x):
    return float(x) if x not in ('', None) else float('nan')


def registration(C):
    """minute-tick lattice: residual of each clean tick's angle from a 6-degree lattice (px at the tick), tick scale"""
    t = [k for k in C['ticks'] if not k['bad']]
    a = np.unwrap(np.array([k['a'] for k in t]))
    idx = np.array([k.get('k', i) for i, k in enumerate(C['ticks']) if not k['bad']], float)
    if len(t) < 10:
        return 'registration: only %d clean ticks' % len(t)
    p = np.polyfit(idx, a, 1)
    res = (a - np.polyval(p, idx)) * 0.93 * C['rpx']
    # a 1st harmonic in the residual is an off-centre / perspective error of the overlay
    A = np.c_[np.cos(np.radians(6 * idx)), np.sin(np.radians(6 * idx))]
    h1 = np.linalg.lstsq(A, res, rcond=None)[0]
    res2 = res - A @ h1
    return ('registration: %d clean ticks (bad %d), tick scale %.4f, lattice residual rms %.2f px (max %.2f), '
            '1st harmonic %.2f px, rms after it %.2f px' % (len(t), C['nbad'], C['track_scale'], np.sqrt(np.mean(res ** 2)),
                                                           np.abs(res).max(), np.hypot(*h1), np.sqrt(np.mean(res2 ** 2))))


def free_size(img, H, m, rpx, hands, f0, bias):
    """diagnostic only: the same fit with a free scale; how much bigger (+) or smaller the visible edge is"""
    poly0 = np.asarray(m['yellow'], float)
    c0 = poly0.mean(axis=0)
    c = np.asarray(f0['c_obs'], float)
    th = math.radians(f0.get('tilt_deg', 0.0)) if f0.get('tilt_deg') == f0.get('tilt_deg') else 0.0
    k = 1.0
    for _ in range(4):
        poly = c + k * (poly0 - c0) @ mt._rot(th).T
        pts, nrm, off = mt.edges(img, H, poly, rpx, m['shape'], hands)
        ok = np.isfinite(off)
        if ok.sum() < mt.MIN_POINTS:
            return float('nan')
        J = np.c_[nrm, np.sum(nrm * (pts - c), axis=1)][ok]
        x = np.linalg.lstsq(J, off[ok] - bias, rcond=None)[0]
        c = c + x[:2]
        k += x[2]
    return 100 * (k - 1)


def profile(img, H, m, fit, C):
    """mean brightness across the edge (outside the masked points), px from the FITTED outline (+ = outward), and
    where the raw edges fell (no edge correction): tells a dark bevel, a halo or a second boundary from a clean step"""
    if 'c_obs' not in fit:
        return ''
    poly0 = np.asarray(m['yellow'], float)
    c0 = poly0.mean(axis=0)
    th = math.radians(fit['tilt_deg']) if fit.get('tilt_deg') == fit.get('tilt_deg') else 0.0
    poly = np.asarray(fit['c_obs']) + (poly0 - c0) @ mt._rot(th).T
    pts, nrm, off = mt.edges(img, H, poly, C['rpx'], m['shape'], C['ctx'].get('hands'))
    keep = mt.edges.why != 1
    d = np.arange(-6, 6.01, 0.5)
    P = pts[keep][:, None, :] + (d / C['rpx'])[None, :, None] * nrm[keep][:, None, :]
    v = og.sample(img, og.project(H, P.reshape(-1, 2))).reshape(keep.sum(), len(d))
    prof = np.nanmedian(v, axis=0)
    o = off[np.isfinite(off)] * C['rpx']
    return ('profile px ' + ' '.join('%+g:%.0f' % (a, b) for a, b in zip(d[::2], prof[::2])) +
            ' | raw edge px p10 %+.2f p50 %+.2f p90 %+.2f' % tuple(np.percentile(o, [10, 50, 90])) if len(o) else '')


def side_table(fit, shape):
    if '_resid' not in fit:
        return ''
    r, s, w = fit['_resid'], fit['_side'], fit['_why']
    out = []
    for k in np.unique(s):
        sel = s == k
        used = sel & np.isfinite(r)
        out.append('%s%d: %s seen %d/%d%s' % ('side ' if shape != 'round' else 'arc ', k,
                   ('%+.2f px' % (np.mean(r[used]) * fit['_rpx'])) if used.any() else '   -   ', used.sum(), sel.sum(),
                   '' if used.sum() == sel.sum() else ' (' + ', '.join('%s %d' % (mt.WHY[j], (w[sel] == j).sum())
                                                                     for j in range(1, 6) if (w[sel] == j).any()) + ')'))
    return '; '.join(out)


def ref_dir(C, h):
    """unit direction (dial frame) of the minute-track line from hour tick h to the opposite tick h+6; None if hidden"""
    a1, a2 = C['lines'].get(h % 12), C['lines'].get((h + 6) % 12)
    if a1 is None or a2 is None:
        return None
    v = tr.cpt(0.93, a2) - tr.cpt(0.93, a1)
    return v / np.hypot(*v)


def closeup(gray, img, H, m, fit, C, path, lines, bias=0.0):
    """left: the photo; right: genuine overlay (thin yellow), fitted outer-metal outline (green = its edge seen and
    consistent, orange = seen but inconsistent: shadowed bevel or reflection, dashed magenta = reconstructed: hidden or
    no edge), and the minute-track reference (cyan) against the fitted direction (white): batons' long axis vs the
    parallel h to h+6 line, the triangle's top edge vs the perpendicular to the 0-30 line, circles' hour line."""
    poly0 = np.asarray(m['yellow'], float)
    c0 = poly0.mean(axis=0)
    P0 = og.project(H, poly0)
    lo, hi = P0.min(axis=0), P0.max(axis=0)
    pad = 0.6 * (hi - lo).max() + 8
    x0, y0 = np.floor(lo - pad).astype(int)
    x1, y1 = np.ceil(hi + pad).astype(int)
    x0, y0 = max(0, x0), max(0, y0)
    crop = img[y0:y1, x0:x1]
    crop = cv2.cvtColor(crop, cv2.COLOR_GRAY2BGR) if crop.ndim == 2 else crop.copy()
    Z = float(np.clip(260.0 / max(1, crop.shape[0]), 2, 6))
    big = cv2.resize(crop, None, fx=Z, fy=Z, interpolation=cv2.INTER_CUBIC)
    plain = big.copy()
    tf = lambda P: np.round((og.project(H, np.asarray(P, float)) - [x0, y0]) * Z + (Z - 1) / 2).astype(np.int32)  # noqa: E731
    cv2.polylines(big, [tf(poly0)], True, (0, 230, 255), 1, cv2.LINE_AA)
    rpx = C['rpx']
    if 'c_obs' in fit:
        c = np.asarray(fit['c_obs'], float)
        th = math.radians(fit['tilt_deg']) if fit.get('tilt_deg') == fit.get('tilt_deg') else 0.0
        poly = c + (poly0 - c0) @ mt._rot(th).T
        pts, nrm, off = mt.edges(gray, H, poly, rpx, m['shape'], C['ctx'].get('hands'))
        why = mt.edges.why
        seen = np.isfinite(off)
        bad = seen & (np.abs(np.where(seen, off, 0) - bias) * rpx > mt.EDGE_BAD_PX)
        Q = tf(pts)
        E = tf(pts + np.where(seen, off, 0)[:, None] * nrm)
        for i in range(0, len(pts), 3):
            cv2.circle(big, tuple(E[i] if seen[i] else Q[i]), 1, (0, 140, 255) if bad[i] else COL[int(why[i])], -1)
        for i in range(len(pts) - 1):
            if np.hypot(*(pts[i + 1] - pts[i])) > 3 * og.STEP_PX / rpx:
                continue
            if seen[i] and seen[i + 1]:
                col = (0, 140, 255) if (bad[i] or bad[i + 1]) else (60, 220, 60)
                cv2.line(big, tuple(Q[i]), tuple(Q[i + 1]), col, 2, cv2.LINE_AA)
            elif (i // 4) % 2 == 0:
                cv2.line(big, tuple(Q[i]), tuple(Q[i + 1]), (255, 0, 255), 1, cv2.LINE_AA)
        u = ref_dir(C, m['hour'])
        if m['shape'] == 'baton' and u is not None:
            q = poly - c
            ax = np.linalg.svd(q - q.mean(axis=0))[2][0]
            L = 0.75 * np.abs(q @ ax).max()
            cv2.line(big, *[tuple(x) for x in tf([c - 1.4 * L * u, c + 1.4 * L * u])], (255, 255, 0), 1, cv2.LINE_AA)
            cv2.line(big, *[tuple(x) for x in tf([c - L * ax, c + L * ax])], (255, 255, 255), 1, cv2.LINE_AA)
        elif m['shape'] == 'triangle' and u is not None:
            er = c / np.hypot(*c)
            et = np.array([-er[1], er[0]])
            rad = poly @ er
            top = poly[rad > rad.max() - 0.004]
            pa, pb = top[np.argmin(top @ et)], top[np.argmax(top @ et)]
            mid, w = (pa + pb) / 2, np.hypot(*(pb - pa))
            v = np.array([-u[1], u[0]])
            cv2.line(big, *[tuple(x) for x in tf([mid - 0.75 * w * v, mid + 0.75 * w * v])], (255, 255, 0), 1, cv2.LINE_AA)
            cv2.line(big, *[tuple(x) for x in tf([pa, pb])], (255, 255, 255), 1, cv2.LINE_AA)
            cv2.line(big, *[tuple(x) for x in tf([mid - 0.12 * u, mid + 0.02 * u])], (255, 255, 0), 1, cv2.LINE_AA)
        else:
            a = C['lines'].get(m['hour'] % 12)
            if a is not None:
                r0 = float(np.hypot(*c0))
                cv2.line(big, *[tuple(x) for x in tf([tr.cpt(r0 - 0.1, a), tr.cpt(r0 + 0.07, a)])], (255, 255, 0), 1, cv2.LINE_AA)
    row = np.hstack([plain, big])
    wrap = []
    for t in lines:
        n = max(20, row.shape[1] // 7)
        wrap += [t[i:i + n] for i in range(0, len(t), n)]
    cap = np.full((8 + 16 * len(wrap), row.shape[1], 3), 255, np.uint8)
    for k, t in enumerate(wrap):
        cv2.putText(cap, t, (4, 14 + 16 * k), cv2.FONT_HERSHEY_SIMPLEX, 0.42, (0, 0, 0) if k else (0, 0, 160), 1, cv2.LINE_AA)
    cv2.imwrite(path, np.vstack([row, cap]), [cv2.IMWRITE_JPEG_QUALITY, 82])
    return path


def triangle_compare(rows, app):
    a = {(r['photo_id'], int(r['hour'])): r for r in csv.DictReader(open(app))}
    pairs = []
    for r in rows:
        if r['trial'] != 'base' or r['shape'] != 'triangle':
            continue
        k = a.get((r['photo_id'], int(r['hour'])))
        old = f(k['rot_vs_line_deg']) if k else float('nan')
        new = f(r['new_tilt']) if int(r['new_ok']) else float('nan')
        pairs.append((r['photo_id'], old, new, (k or {}).get('rot_note', ''), r['new_note'], f(r['new_se_rot'])))
    old = np.array([p[1] for p in pairs])
    new = np.array([p[2] for p in pairs])
    print('12 triangle rotation, n %d photos (old = app top edge vs ticks 59-1; new = whole outer-metal outline vs the 0-30 '
          'line, i.e. its top edge vs the perpendicular):' % len(pairs))
    for nm, v in (('old', old), ('new', new)):
        k = np.isfinite(v)
        if k.any():
            print('  %s measured %d  |rot| p50 %.2f p95 %.2f max %.2f  over 1.0 deg: %d %s' % (
                nm, k.sum(), np.median(np.abs(v[k])), np.percentile(np.abs(v[k]), 95), np.abs(v[k]).max(), (np.abs(v[k]) > 1).sum(),
                [(p[0][:10], round(p[1 if nm == 'old' else 2], 2)) for p in pairs if abs(p[1 if nm == 'old' else 2]) > 1][:8]))
    k = np.isfinite(old) & np.isfinite(new)
    if k.sum() > 2:
        print('  both %d: |new-old| p50 %.2f p95 %.2f, corr %.2f' % (k.sum(), np.median(np.abs(new[k] - old[k])),
              np.percentile(np.abs(new[k] - old[k]), 95), np.corrcoef(old[k], new[k])[0, 1]))
    print('  new measured where old withheld: %d of %d' % ((np.isfinite(new) & ~np.isfinite(old)).sum(), (~np.isfinite(old)).sum()))
    se = np.array([p[5] for p in pairs])
    if np.isfinite(se).any():
        print('  new rotation standard error p50 %.2f p95 %.2f deg' % (np.nanmedian(se), np.nanpercentile(se, 95)))
    for p in pairs:
        if p[0] in ('cb5175ff2c02a6b1',) or abs(p[1]) > 1 or abs(p[2]) > 1:
            print('   %s old %+.2f (%s) new %+.2f (%s)' % (p[0], p[1], p[3] or 'ok', p[2], p[4] or 'ok'))


def main():
    dump, root, mcsv, outd = sys.argv[1:5]
    app = next((a for a in sys.argv[5:] if a.endswith('.csv')), None)
    every, b64, qc = '--all' in sys.argv, '--b64' in sys.argv, '--qc' in sys.argv
    os.makedirs(outd, exist_ok=True)
    rows = list(csv.DictReader(open(mcsv)))
    if app and os.path.exists(app):
        triangle_compare(rows, app)
    ids = {}
    for a in sys.argv[5:]:
        if a.startswith('--ids='):
            for x in a[6:].split(','):
                pid, h = x.split(':')
                ids.setdefault(pid, set()).add(int(h))
    want = {}
    for r in rows:
        named = any(r['photo_id'].startswith(k) and int(r['hour']) in v for k, v in ids.items())
        if r['trial'] == 'base' and (every or named or r.get('new_state') in (mt.CLEARLY_OFF, mt.INSPECT) or
                                     (qc and r['shape'] == 'triangle')):
            want.setdefault(r['photo_id'], set()).add(int(r['hour']))
    if not want:
        print('no flagged or named markers to inspect')
    for line in open(dump):
        d = json.loads(line)
        if d['photo_id'] not in want or not d.get('pose'):
            continue
        img = me.va.load(os.path.join(root, d['path']), d)
        color = cv2.imread(os.path.join(root, d['path']))
        if color is None or color.shape[:2] != img.shape[:2]:
            color = img
        orows, C, base = tr.analyse(img, d)
        news, fits, bias = me.new_rows(img, d, C)
        print('== %s (%s) rpx %.0f  %s' % (d['photo_id'], d['model'], C['rpx'], registration(C)))
        print('   edge correction this photo (median over fully seen markers, px): %s; hands %s' % (
            ', '.join('%s %+.2f' % (k, v * C['rpx']) for k, v in bias.items()),
            [(round(a), round(w, 1), round(rr, 2)) for a, w, rr in (C['ctx'].get('hands') or [])]))
        for i, m in enumerate(d['markers']):
            if m['hour'] not in want[d['photo_id']]:
                continue
            fit, nr = news[i]
            o = orows[i]
            fit['_rpx'] = C['rpx']
            ks = free_size(img, C['H'], m, C['rpx'], C['ctx'].get('hands'), fit, bias.get(m['shape'], 0.0)) if 'c_obs' in fit else float('nan')
            print('  %2d %-8s old %s side %s inout %s tilt %s | new side %s inout %s tilt %s se %.2f%% cover %.0f%% rms %.2f px '
                  'bias-own %+.2f px; free-size fit says %+.1f %% %s' % (
                      m['hour'], m['shape'], 'gate:' + o['gate'] if o['gate'] else 'ok', me.va.fmt(o.get('side_pct')),
                      me.va.fmt(o.get('inout_pct')), me.va.fmt(o.get('tilt_line_deg')), me.va.fmt(nr.get('side_pct')),
                      me.va.fmt(nr.get('inout_pct')), me.va.fmt(nr.get('tilt_line_deg')), 100 * fit.get('se_pos', float('nan')),
                      100 * fit.get('cover', float('nan')), fit.get('rms_px', float('nan')),
                      (fit.get('bias', float('nan')) - bias.get(m['shape'], 0.0)) * C['rpx'], ks, fit.get('gate', '')))
            print('     ' + side_table(fit, m['shape']))
            print('     ' + profile(img, C['H'], m, fit, C))
            st, why = mt.verdict(fit, nr, C['rpx'])
            print('     verdict (new): %s%s' % (st, ' - ' + why if why else ''))
            lab = ['%s %d o\'clock: %s' % (d['photo_id'][:12], m['hour'], st),
                   'side %+.2f  in/out %+.2f %% R%s  (limits 1.0 %% R%s)' % (
                       nr.get('side_pct', float('nan')), nr.get('inout_pct', float('nan')),
                       '' if m['shape'] == 'round' else '  turn %+.2f deg vs %s' % (
                           nr.get('tilt_line_deg', float('nan')), 'the parallel %d-%d line' % (
                               (5 * m['hour']) % 60, (5 * m['hour'] + 30) % 60) if m['shape'] == 'baton' else
                           'the perpendicular to the 0-30 line'),
                       '' if m['shape'] == 'round' else ', %.2f deg' % mt.TILT_LIMIT[m['shape']])]
            lab += ['uncertain: ' + x for x in fit.get('uncertain', '').split('; ') if x]
            p = closeup(img, color, C['H'], m, fit, C, os.path.join(outd, '%s_%02d.jpg' % (d['photo_id'][:16], m['hour'])),
                        lab, bias.get(m['shape'], 0.0))
            if b64:
                s = base64.b64encode(open(p, 'rb').read()).decode()
                print('IMG %s %s' % (os.path.basename(p), s))


if __name__ == '__main__':
    main()
