"""Research only: the outer-metal fit (metal.py) against the current lume-centre detector (track.py) on identical photos.

  python3 metal_eval.py dump.jsonl image_root out.csv [workers] [--synthetic]

Per marker, both methods' side / in-out (% R) and tilt (deg) against the same minute-track references (track.metrics:
hour lines, leave-one-out ring of the round markers), and whether each could assess it. --synthetic adds, for every
marker both methods assess on a genuine photo: a dark hand-like bar over about a third of its outline (occlusion), and
known moves (track.move_confined: side / in-out 1.5 % R, batons turned 3 deg) re-measured by both.
"""
import csv
import hashlib
import importlib.util
import json
import math
import os
import sys
from multiprocessing import Pool

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import track as tr  # noqa: E402
import metal as mt  # noqa: E402
import overlay_gap as og  # noqa: E402
import gross as gr  # noqa: E402
_s = importlib.util.spec_from_file_location('track_validate', os.path.join(HERE, 'validate.py'))
va = importlib.util.module_from_spec(_s)
_s.loader.exec_module(va)

COLS = ['photo_id', 'watch', 'model', 'rpx', 'hour', 'shape', 'trial', 'amount', 'old_ok', 'old_side', 'old_inout',
        'old_tilt', 'new_ok', 'new_side', 'new_inout', 'new_tilt', 'new_se_pos', 'new_se_rot', 'new_cover', 'new_note']


def new_all(img, d, C, hands, bias=None):
    out = []
    for m in d['markers']:
        f = mt.fit(img, C['H'], m, C['rpx'], hands, m['shape'] != 'round', (bias or {}).get(m['shape'], 0.0))
        f['gate'] = f['note'] or ('' if 'c_obs' in f else 'no fit')
        if C['lines'].get(m['hour'] % 12) is None:
            f['gate'] = f['gate'] or 'minute ticks near it hidden'
        if C['rpx'] < gr.MIN_RPX:
            f['gate'] = f['gate'] or 'photo too small'
        out.append(f)
    return out


def photo_bias(fits):
    """per shape: median edge offset of the fully visible markers (this photo's edge blur)"""
    b = {}
    for sh in ('round', 'baton', 'triangle'):
        v = [f['bias'] for f in fits if f.get('shape') == sh and not f['gate'] and f.get('cover', 0) > 0.9]
        b[sh] = float(np.median(v)) if v else 0.0
    return b


def new_rows(img, d, C):
    hands = C['ctx'].get('hands')
    fits = new_all(img, d, C, hands)
    bias = photo_bias(fits)
    fits = new_all(img, d, C, hands, bias)
    return [one(m, f, C, fits, i) for i, (m, f) in enumerate(zip(d['markers'], fits))], fits, bias


def one(m, f, C, fits, i):
    r = {}
    if 'c_obs' in f:
        r = tr.metrics(dict(f, tilt_deg=f.get('tilt_deg', float('nan'))), m['hour'], C, tr.ring_scale(fits, i))
    return f, r


def refit(img2, d, C, i, fits, bias):
    """marker i measured again on a changed photo; the other markers keep their base fits (ring, bias)"""
    m = d['markers'][i]
    f = new_all(img2, {'markers': [m]}, C, C['ctx'].get('hands'), bias)[0]
    return one(m, f, C, fits[:i] + [f] + fits[i + 1:], i)


def row(d, m, trial, amount, old, new):
    (of, orow), (nf, nrow) = old, new
    g = lambda x: va.fmt(x)  # noqa: E731
    return dict(photo_id=d['photo_id'], watch=d.get('watch', ''), model=d['model'], rpx='%.0f' % d['rpx'], hour=m['hour'],
                shape=m['shape'], trial=trial, amount=amount, old_ok=int(not of), old_side=g(orow.get('side_pct')),
                old_inout=g(orow.get('inout_pct')), old_tilt=g(orow.get('tilt_line_deg')), new_ok=int(not nf['gate']),
                new_side=g(nrow.get('side_pct')), new_inout=g(nrow.get('inout_pct')), new_tilt=g(nrow.get('tilt_line_deg')),
                new_se_pos=g(100 * nf.get('se_pos', float('nan'))), new_se_rot=g(nf.get('se_rot')),
                new_cover=g(nf.get('cover')), new_note=nf['gate'])


def occlude(img, H, m, rpx, rng):
    """a dark bar (hand-like, 3 % R wide) across the marker at a random angle, covering about a third of its outline"""
    import cv2
    y = np.asarray(m['yellow'], float)
    c = y.mean(axis=0)
    a = rng.uniform(0, math.pi)
    u = np.array([math.cos(a), math.sin(a)])
    v = np.array([-u[1], u[0]])
    ext = np.max(np.abs((y - c) @ v))
    off = 0.45 * ext
    bar = np.array([c + off * v - 0.3 * u, c + off * v + 0.3 * u, c + (off + 0.03) * v + 0.3 * u, c + (off + 0.03) * v - 0.3 * u])
    big = np.array([c + (off - 0.02) * v - 0.3 * u, c + (off - 0.02) * v + 0.3 * u, c + (off + 0.2) * v + 0.3 * u, c + (off + 0.2) * v - 0.3 * u])
    out = img.copy()
    mk = np.zeros(img.shape, np.uint8)
    cv2.fillPoly(mk, [np.round(og.project(H, big)).astype(np.int32)], 1)
    dial = float(np.median(img[(mk == 0)]))
    out[mk > 0] = min(dial, 30)
    return out


def work(args):
    line, root, synth = args
    d = json.loads(line)
    try:
        if not d.get('pose'):
            return []
        img = va.load(os.path.join(root, d['path']), d)
        if img is None:
            return []
        orows, C, base = tr.analyse(img, d)
        news, fits, bias = new_rows(img, d, C)
        out = []
        for m, o, b, n in zip(d['markers'], orows, base, news):
            out.append(row(d, m, 'base', 0, (o['gate'], o), n))
        if not synth:
            return out
        for i, (m, o, n) in enumerate(zip(d['markers'], orows, news)):
            if o['gate'] or n[0]['gate'] or 'side_pct' not in o or 'side_pct' not in n[1]:
                continue
            rng = np.random.default_rng(int(hashlib.sha256(('%s|%d' % (d['photo_id'], m['hour'])).encode()).hexdigest()[:8], 16))
            a = math.radians((m['hour'] % 12) * 30.0)
            er, et = np.array([math.sin(a), -math.cos(a)]), np.array([math.cos(a), math.sin(a)])
            trials = [('occlude', 0.0, None, 0.0), ('side', 1.5, 0.015 * et, 0.0), ('inout', 1.5, 0.015 * er, 0.0)]
            if m['shape'] == 'baton':
                trials.append(('turn', 3.0, np.zeros(2), math.radians(3.0)))
            for kind, amount, v, dth in trials:
                img2 = occlude(img, C['H'], m, C['rpx'], rng) if kind == 'occlude' else tr.move_confined(img, d, m, C, v, dth)
                ob = tr.centre_of(img2, d, m, C, gr.wedge(img2, C['ctx'], m))
                orow = tr.metrics(ob, m['hour'], C, tr.ring_scale(base, i))
                out.append(row(d, m, kind, amount, (ob.get('gate', ''), orow), refit(img2, d, C, i, fits, bias)))
        return out
    except Exception as e:     # research: one bad photo is logged, never stops the run
        print('ERROR', d.get('photo_id'), repr(e), flush=True)
        return []


def f(x):
    return float(x) if x not in ('', None) else float('nan')


def summary(rows):
    POS, TILT = 1.0, 2.25
    allow = lambda r: 100 * 0.5 / float(r['rpx'])  # noqa: E731

    def off(r, k):
        s, io, t = f(r[k + '_side']), f(r[k + '_inout']), f(r[k + '_tilt'])
        if r['shape'] == 'triangle':
            return False
        pos = math.hypot(0 if s != s else s, 0 if io != io else io)
        return pos > POS + allow(r) or (r['shape'] == 'baton' and abs(t) > TILT)
    base = [r for r in rows if r['trial'] == 'base']
    print('marker coverage (assessed / all), base photos:')
    for sh in ('round', 'baton', 'triangle'):
        b = [r for r in base if r['shape'] == sh]
        if b:
            print('  %-8s n %4d  old %5.1f%%  new %5.1f%%  new-only %d  old-only %d' % (sh, len(b), 100 * np.mean([int(r['old_ok']) for r in b]),
                  100 * np.mean([int(r['new_ok']) for r in b]), sum(1 for r in b if r['new_ok'] == 1 and r['old_ok'] == 0),
                  sum(1 for r in b if r['old_ok'] == 1 and r['new_ok'] == 0)))
    for k in ('old', 'new'):
        fl = [r for r in base if int(r[k + '_ok']) and off(r, k)]
        print('%s method: clearly off on these photos: %d %s' % (k, len(fl), [(r['photo_id'][:10], r['hour']) for r in fl][:10]))
    both = [r for r in base if int(r['old_ok']) and int(r['new_ok']) and r['shape'] != 'triangle']
    for q in ('side', 'inout', 'tilt'):
        a = np.array([f(r['old_' + q]) for r in both])
        b = np.array([f(r['new_' + q]) for r in both])
        k = np.isfinite(a) & np.isfinite(b)
        if k.sum():
            print('  %-5s both assessed n %4d: |old| p95 %.2f max %.2f   |new| p95 %.2f max %.2f   |new-old| p50 %.2f p95 %.2f' % (
                q, k.sum(), np.percentile(np.abs(a[k]), 95), np.abs(a[k]).max(), np.percentile(np.abs(b[k]), 95), np.abs(b[k]).max(),
                np.median(np.abs(b[k] - a[k])), np.percentile(np.abs(b[k] - a[k]), 95)))
    # repeatability: same physical watch, same marker, several photos
    for k in ('old', 'new'):
        sd = []
        g = {}
        for r in both:
            g.setdefault((r['watch'], r['hour']), []).append(f(r[k + '_side']))
        for v in g.values():
            v = [x for x in v if x == x]
            if len(v) >= 2:
                sd.append(np.std(v, ddof=1))
        if sd:
            print('repeatability %s: sideways SD between photos of one watch, median %.2f %% R (n %d groups)' % (k, np.median(sd), len(sd)))
    base_by = {(r['photo_id'], r['hour']): r for r in base}
    for kind in ('occlude', 'side', 'inout', 'turn'):
        T = [r for r in rows if r['trial'] == kind]
        if not T:
            continue
        for k in ('old', 'new'):
            okk = [r for r in T if int(r[k + '_ok'])]
            err = []
            for r in okk:
                b0 = base_by[(r['photo_id'], r['hour'])]
                q = 'tilt' if kind == 'turn' else ('side' if kind == 'side' else 'inout' if kind == 'inout' else None)
                if q is None:
                    err.append(math.hypot(f(r[k + '_side']) - f(b0[k + '_side']), f(r[k + '_inout']) - f(b0[k + '_inout'])))
                else:
                    err.append(abs(f(r[k + '_' + q]) - f(b0[k + '_' + q]) - float(r['amount']) * (1 if q != 'tilt' else 1)))
            err = np.array([e for e in err if e == e])
            caught = sum(1 for r in okk if off(r, k))
            line = '%-7s %s: assessed %d/%d' % (kind, k, len(okk), len(T))
            if len(err):
                line += '  |error| p50 %.2f p95 %.2f' % (np.median(err), np.percentile(err, 95))
            if kind != 'occlude':
                line += '  clearly off %d (lost %d)' % (caught, len(T) - caught)
            else:
                line += '  false clearly off %d' % caught
            print(line)


def main():
    dump, root, outp = sys.argv[1], sys.argv[2], sys.argv[3]
    workers = int(sys.argv[4]) if len(sys.argv) > 4 and sys.argv[4].isdigit() else 4
    synth = '--synthetic' in sys.argv
    lines = [l for l in open(dump) if l.strip()]
    rows = []
    with Pool(workers) as pool:
        for rr in pool.imap_unordered(work, [(l, root, synth) for l in lines]):
            rows += rr
    with open(outp, 'w', newline='') as fo:
        w = csv.DictWriter(fo, fieldnames=COLS)
        w.writeheader()
        w.writerows(rows)
    summary(rows)


if __name__ == '__main__':
    main()
