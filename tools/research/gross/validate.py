"""Research only: validate the "clearly off" decision.

  python3 validate.py dump.jsonl image_root out.csv [workers] [--inject]

Without --inject: every marker of every photo as found (genuine photos: any "Clearly off" is a false alarm).
With --inject: on each photo, every marker that is checkable is moved INSIDE the photo (Lanczos resampling, feathered
blend; same as overlay_gap/benchmark.move_marker) by 0, 1, 2, 3, 4 % of the dial radius in a seeded random direction,
and batons / the triangle are also turned by 2, 4, 6 degrees; the marker is re-measured and re-decided against its
untouched peers. Raw values are written so thresholds can be swept afterwards.
"""
import copy
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
import overlay_gap as og  # noqa: E402
import benchmark as bm  # noqa: E402
import gross as gr  # noqa: E402

COLS = ['photo_id', 'watch', 'model', 'rpx', 'hour', 'shape', 'move', 'move_pct', 'move_deg', 'state', 'gate',
        'shift_pct', 'rel_pct', 'tilt_deg', 'rel_tilt_deg', 'peers', 'radial_px', 'tangential_px']


def row(d, r, move='base', mp=0.0, md=0.0):
    o = {'photo_id': d['photo_id'], 'watch': d.get('watch', ''), 'model': d['model'], 'rpx': '%.0f' % d['rpx'],
         'hour': r['hour'], 'shape': r['shape'], 'move': move, 'move_pct': mp, 'move_deg': md,
         'state': r.get('state', ''), 'gate': r.get('gate', '')}
    for k in ('shift_pct', 'rel_pct', 'tilt_deg', 'rel_tilt_deg', 'radial_px', 'tangential_px'):
        v = r.get(k)
        o[k] = '' if v is None or v != v else '%.4f' % v
    o['peers'] = r.get('peers', '')
    return o


def load(path, d):
    """The photo at the size the app analysed. The Python and Java decoders can scale a large JPEG slightly
    differently; when only the scale differs (same aspect within 1 %) the image is resized to the app's size."""
    import cv2
    img = og.load_like_app(path)
    if img is None:
        print('UNREADABLE', d['photo_id'], flush=True)
        return None
    if img.shape[1] == d['w'] and img.shape[0] == d['h']:
        return img
    if abs(img.shape[1] / img.shape[0] - d['w'] / d['h']) < 0.01 * d['w'] / d['h']:
        print('RESIZED', d['photo_id'], img.shape[1], img.shape[0], '->', d['w'], d['h'], flush=True)
        return cv2.resize(img, (d['w'], d['h']), interpolation=cv2.INTER_AREA)
    print('SIZE MISMATCH', d['photo_id'], img.shape[1], img.shape[0], d['w'], d['h'], flush=True)
    return None


def work(args):
    line, root, inject = args
    d = json.loads(line)
    if not d.get('pose'):
        return []
    img = load(os.path.join(root, d['path']), d)
    if img is None:
        return []
    insets = og.model_insets(d['model'])
    base, ctx, sib = gr.analyse(img, d, insets)
    rows = [row(d, r) for r in base]
    if not inject:
        return rows
    rpx = d['rpx']
    for i, m in enumerate(d['markers']):
        if base[i]['state'] != 'No visible issue':
            continue
        seed = int(hashlib.sha256(('%s|%d' % (d['photo_id'], m['hour'])).encode()).hexdigest()[:8], 16)
        rng = np.random.default_rng(seed)
        moves = [(p, 0.0) for p in (0.0, 1.0, 2.0, 3.0, 4.0)]
        if m['shape'] != 'round':
            moves += [(0.0, a * (1 if rng.uniform() < 0.5 else -1)) for a in (2.0, 4.0, 6.0)]
        for mp, md in moves:
            ang = rng.uniform(0, 2 * math.pi)
            v = (mp / 100.0) * np.array([math.cos(ang), math.sin(ang)])
            img2, _ = bm.move_marker(img, d['H'], m, rpx, v, math.radians(md), insets)
            res = copy.deepcopy(base)
            res[i] = gr.measure_marker(img2, d, m, insets, ctx, sib, gr.wedge(img2, ctx, m))
            for r in res:
                r.pop('state', None)
                if r.get('gate') == 'too few clean markers to compare with':
                    r['gate'] = ''
            gr.decide(res, rpx)
            rows.append(row(d, res[i], 'moved', mp, md))
    return rows


def main():
    dump, root, out = sys.argv[1], sys.argv[2], sys.argv[3]
    workers = int(sys.argv[4]) if len(sys.argv) > 4 and sys.argv[4].isdigit() else 4
    inject = '--inject' in sys.argv
    lines = [l for l in open(dump) if l.strip()]
    with open(out, 'w', newline='') as fo, Pool(workers) as pool:
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
