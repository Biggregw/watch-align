"""Research only: validate track.py on genuine photos.

  python3 validate.py dump.jsonl image_root out.csv [workers] [--inject]

As found: every marker of every photo (genuine: the spread of side / inout / tilt on genuine watches).
--inject: every clean marker is also moved INSIDE the photo (track.move_confined: Lanczos, confined to the marker) by
known amounts (signed in the 'amount' column: + = clockwise / outward), one change at a time, as in the owner's eye test: sideways and in/out by 0.5, 1, 1.5, 2 % of the dial
radius (seeded random sign), batons turned by 1, 1.5, 2, 3 deg, plus an untouched control. The marker is re-measured
against the untouched minute track and the untouched other markers.
"""
import csv
import hashlib
import json
import math
import os
import sys
from multiprocessing import Pool

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'overlay_gap'))
sys.path.insert(0, os.path.join(HERE, '..', 'gross'))
import overlay_gap as og  # noqa: E402
import gross as gr  # noqa: E402
import track as tr  # noqa: E402

COLS = ['photo_id', 'watch', 'model', 'rpx', 'hour', 'shape', 'move', 'kind', 'amount', 'gate', 'side_pct', 'inout_pct',
        'tilt_line_deg', 'ring_turn_pct', 'scale', 'nbad_ticks']
SHIFTS = (0.5, 1.0, 1.5, 2.0)
TURNS = (1.0, 1.5, 2.0, 3.0)


def fmt(v):
    return '' if v is None or v != v else '%.4f' % v


def row(d, C, r, move='base', kind='', amount=0.0):
    o = {'photo_id': d['photo_id'], 'watch': d.get('watch', ''), 'model': d['model'], 'rpx': '%.0f' % d['rpx'],
         'hour': r['hour'], 'shape': r['shape'], 'move': move, 'kind': kind, 'amount': amount, 'gate': r.get('gate', ''),
         'nbad_ticks': C['nbad']}
    for k in ('side_pct', 'inout_pct', 'tilt_line_deg', 'ring_turn_pct', 'scale'):
        o[k] = fmt(r.get(k))
    return o


def load(path, d):
    import cv2
    img = og.load_like_app(path)
    if img is None:
        print('UNREADABLE', d['photo_id'], flush=True)
        return None
    if img.shape[1] == d['w'] and img.shape[0] == d['h']:
        return img
    if abs(img.shape[1] / img.shape[0] - d['w'] / d['h']) < 0.01 * d['w'] / d['h']:
        print('RESIZED', d['photo_id'], flush=True)
        return cv2.resize(img, (d['w'], d['h']), interpolation=cv2.INTER_AREA)
    print('SIZE MISMATCH', d['photo_id'], flush=True)
    return None


def work(args):
    try:
        return work1(args)
    except Exception as e:     # research: one bad photo is logged, never stops the run
        print('ERROR', json.loads(args[0]).get('photo_id'), repr(e), flush=True)
        return []


def work1(args):
    line, root, inject = args
    d = json.loads(line)
    if not d.get('pose'):
        return []
    img = load(os.path.join(root, d['path']), d)
    if img is None:
        return []
    rows, C, base = tr.analyse(img, d)
    out = [row(d, C, r) for r in rows]
    if not inject:
        return out
    for i, (m, r) in enumerate(zip(d['markers'], rows)):
        if r['gate'] or 'side_pct' not in r or 'inout_pct' not in r:
            continue
        rng = np.random.default_rng(int(hashlib.sha256(('%s|%d' % (d['photo_id'], m['hour'])).encode()).hexdigest()[:8], 16))
        a = math.radians((m['hour'] % 12) * 30.0)
        er = np.array([math.sin(a), -math.cos(a)])
        et = np.array([math.cos(a), math.sin(a)])
        trials = [('control', 0.0, np.zeros(2), 0.0)]
        sg = lambda: 1 if rng.uniform() < 0.5 else -1      # noqa: E731
        trials += [('side', s, (s / 100.0) * et, 0.0) for s in (x * sg() for x in SHIFTS)]
        trials += [('inout', s, (s / 100.0) * er, 0.0) for s in (x * sg() for x in SHIFTS)]
        if m['shape'] == 'baton':
            trials += [('turn', t, np.zeros(2), math.radians(t)) for t in (x * sg() for x in TURNS)]
        scale = tr.ring_scale(base, i)
        for kind, amount, v, dth in trials:
            img2 = tr.move_confined(img, d, m, C, v, dth) if (amount or kind != 'control') else img
            b2 = tr.centre_of(img2, d, m, C, gr.wedge(img2, C['ctx'], m))
            r2 = dict(hour=m['hour'], shape=m['shape'], gate=b2.get('gate', ''), ring_turn_pct=r['ring_turn_pct'])
            r2.update(tr.metrics(b2, m['hour'], C, scale))
            out.append(row(d, C, r2, 'moved', kind, amount))
    return out


def main():
    dump, root, outp = sys.argv[1], sys.argv[2], sys.argv[3]
    workers = int(sys.argv[4]) if len(sys.argv) > 4 and sys.argv[4].isdigit() else 4
    inject = '--inject' in sys.argv
    lines = [l for l in open(dump) if l.strip()]
    with open(outp, 'w', newline='') as fo, Pool(workers) as pool:
        w = csv.DictWriter(fo, fieldnames=COLS)
        w.writeheader()
        for rows in pool.imap_unordered(work, [(l, root, inject) for l in lines]):
            for r in rows:
                w.writerow(r)
            fo.flush()
            if rows:
                print('done', rows[0]['photo_id'], flush=True)


if __name__ == '__main__':
    main()
