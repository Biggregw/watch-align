"""Measurement-architecture research: compare sideways (tangential) methods on committed genuine evidence.

    python -m tools.research.measurement_architecture.genuine_offline docs/image-archive/face-on-evidence.jsonl

Input: docs/image-archive/face-on-evidence.jsonl (numbers only; the production 1.6.x readings of each marker).
Production sideways (ClearlyOff.metrics) is the marker's outer-metal centre against the hour line drawn from the
minute ticks through the registered dial centre: an ABSOLUTE reading in the dial-plane frame. Production in/out is
already relative to a ring fitted to the OTHER markers (AlignmentAnalysis -> LuminousAlignment.ringWithout), so it is
reported unchanged here.

Methods (tangential component, % R, + = clockwise):
  P   production sideways.
  PC  P minus the parallax of a raised marker: k * tan(tilt) * (unit far-side direction . clockwise tangent), with the
      single constant k (effective marker height / dial radius) fitted on OTHER watches only (leave-one-watch-out).
  L   leave-one-out shared ring: P minus a + dx cos(th) + dy sin(th) fitted on the photo's OTHER eligible markers
      (rotation + translation of the marker ring; the tangential part of the Alpha94 similarity ring).
Only ELIGIBLE markers (tools/image_archive/eligibility.py) on registered photos (tilt <= 10.6 deg, dial radius >= 250
px) are used; photos shared between declared watches are excluded; statistics count physical watches.
"""
from __future__ import annotations

import argparse
import json
import math
import statistics as st
from collections import defaultdict

import numpy as np

from tools.image_archive import analyse_validation as av
from tools.image_archive import eligibility as el

SHAPE = {m: el.SHAPES[m] for m in el.SHAPES}


def far_side(e):
    """unit vector (canonical, x right, y down) towards the part of the dial farther from the camera"""
    g = np.array([-(e['h_gradient'] - 1), e['v_gradient'] - 1])
    n = np.linalg.norm(g)
    return g / n if n > 0 else g


def load(path, max_tilt=10.6, min_rpx=250):
    ids = av.watch_ids()
    out = []
    for e in av.unique([json.loads(line) for line in open(path)]):
        if e.get('error') or e.get('model') not in el.REQUIRED:
            continue
        av.prepare(e)
        if not e.get('pose_valid') or e.get('tilt_deg') is None or e['tilt_deg'] > max_tilt or (e.get('rpx') or 0) < min_rpx:
            continue
        if e.get('h_gradient') is None or e.get('v_gradient') is None:
            continue
        w = ids.get(e['sha256']) or ({e['physical_watch_id']} if e.get('physical_watch_id') else set())
        if len(w) != 1:
            continue
        statuses = el.marker_statuses(e, e['model'])
        marks = {}
        for h, v in statuses.items():
            m = e['m'].get(h)
            if v['status'] == el.ELIGIBLE and m and m[0] is not None:
                marks[int(h)] = {'side': m[0], 'inout': m[1], 'tilt': m[2]}
        if len(marks) < 6:
            continue
        out.append({'sha': e['sha256'], 'model': e['model'], 'watch': next(iter(w)), 'tilt': e['tilt_deg'],
                    'far': far_side(e), 'rpx': e['rpx'], 'marks': marks})
    return out


def tangent(h):
    a = math.radians(30 * h)
    return np.array([math.cos(a), math.sin(a)])


def radial(h):
    a = math.radians(30 * h)
    return np.array([math.sin(a), -math.cos(a)])


def fit_k(photos):
    """least squares k from the per-photo tangential translation (harmonic fit) against tan(tilt) * far-side"""
    X, Y = [], []
    for p in photos:
        hs = sorted(p['marks'])
        th = np.radians([30 * h for h in hs])
        y = np.array([p['marks'][h]['side'] for h in hs])
        c = np.linalg.lstsq(np.c_[np.ones_like(th), np.cos(th), np.sin(th)], y, rcond=None)[0]
        v = math.tan(math.radians(p['tilt'])) * p['far']
        X += [v[0], v[1]]
        Y += [c[1], c[2]]
    X, Y = np.array(X), np.array(Y)
    return float(X @ Y / (X @ X))


def methods(photos):
    """{method: [(model, watch, hour, shape, value)]} for every eligible marker; k is fitted leave-one-watch-out"""
    watches = sorted({p['watch'] for p in photos})
    k_of = {}
    by_watch = defaultdict(list)
    for p in photos:
        by_watch[p['watch']].append(p)
    # leave-one-watch-out k (one fit per family would leak nothing either; one pooled constant is the physical claim)
    total = fit_k(photos)
    for w in watches:
        k_of[w] = fit_k([p for p in photos if p['watch'] != w])
    out = defaultdict(list)
    for p in photos:
        k = k_of[p['watch']]
        shift = k * math.tan(math.radians(p['tilt'])) * p['far']
        for h, m in p['marks'].items():
            shape = SHAPE[p['model']][h]
            rec = (p['model'], p['watch'], h, shape, p['sha'], p['tilt'])
            out['P'].append(rec + (m['side'],))
            out['PC'].append(rec + (m['side'] - float(shift @ tangent(h)),))
            others = [g for g in p['marks'] if g != h]
            if len(others) >= 5:
                th = np.radians([30 * g for g in others])
                y = np.array([p['marks'][g]['side'] for g in others])
                c = np.linalg.lstsq(np.c_[np.ones_like(th), np.cos(th), np.sin(th)], y, rcond=None)[0]
                a = math.radians(30 * h)
                out['L'].append(rec + (m['side'] - (c[0] + c[1] * math.cos(a) + c[2] * math.sin(a)),))
            if m['inout'] is not None:
                out['inout (production, ring-relative)'].append(rec + (m['inout'],))
    return out, total


def summary(rows):
    """photo-level and watch-level spread, repeatability and tilt dependence of one method"""
    vals = np.array([r[-1] for r in rows])
    per_watch_marker = defaultdict(list)
    for r in rows:
        per_watch_marker[(r[0], r[1], r[2])].append(r[-1])
    within = [np.var(v, ddof=1) for v in per_watch_marker.values() if len(v) >= 2]
    wmeans = [np.mean(v) for v in per_watch_marker.values()]
    wmax = defaultdict(float)
    for r in rows:
        wmax[(r[0], r[1])] = max(wmax[(r[0], r[1])], abs(r[-1]))
    tilt = np.array([r[5] for r in rows])
    return {'markers': len(vals), 'watches': len({(r[0], r[1]) for r in rows}),
            'sd': float(vals.std()), 'p95_abs': float(np.percentile(np.abs(vals), 95)),
            'p99_abs': float(np.percentile(np.abs(vals), 99)), 'max_abs': float(np.abs(vals).max()),
            'within_watch_sd': float(math.sqrt(np.mean(within))) if within else None,
            'within_pairs': len(within),
            'between_watch_sd': float(np.std(wmeans)),
            'watch_max_p95': float(np.percentile(list(wmax.values()), 95)),
            'abs_vs_tilt_r': float(np.corrcoef(np.abs(vals), tilt)[0, 1])}


def hour_bias(rows):
    """mean reading per (family, hour) over watch means, with a t statistic (reference-geometry check)"""
    per = defaultdict(lambda: defaultdict(list))
    for r in rows:
        per[(r[0], r[2])][r[1]].append(r[-1])
    out = {}
    for key, ws in sorted(per.items()):
        m = [np.mean(v) for v in ws.values()]
        if len(m) >= 5:
            se = np.std(m, ddof=1) / math.sqrt(len(m))
            out[key] = (float(np.mean(m)), float(np.mean(m) / se) if se > 0 else float('nan'), len(m))
    return out


def main(path):
    photos = load(path)
    out, k = methods(photos)
    print('# photos %d, watches %d; pooled parallax constant k = %.3f %% R per unit tan(tilt)' % (
        len(photos), len({p['watch'] for p in photos}), k))
    for name, rows in out.items():
        print('\n## %s' % name)
        for shape in ('round', 'baton', 'triangle'):
            for model in sorted(SHAPE):
                sel = [r for r in rows if r[3] == shape and r[0] == model]
                if len(sel) < 20:
                    continue
                s = summary(sel)
                print('%-8s %-18s markers %4d watches %3d | sd %.3f p95|x| %.3f p99 %.3f max %.3f | within-watch sd %s (%d) '
                      'between-watch sd %.3f | r(|x|,tilt) %+.2f' % (
                          shape, model, s['markers'], s['watches'], s['sd'], s['p95_abs'], s['p99_abs'], s['max_abs'],
                          '%.3f' % s['within_watch_sd'] if s['within_watch_sd'] is not None else '-', s['within_pairs'],
                          s['between_watch_sd'], s['abs_vs_tilt_r']))
        bias = hour_bias(rows)
        strong = {k_: v for k_, v in bias.items() if abs(v[1]) >= 3}
        print('per-hour mean bias with |t| >= 3: %d of %d: %s' % (len(strong), len(bias), ', '.join(
            '%s %d: %+.3f (t %.1f, n %d)' % (m[11:], h, v[0], v[1], v[2]) for (m, h), v in sorted(strong.items()))))
    return out


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('evidence')
    main(ap.parse_args().evidence)
