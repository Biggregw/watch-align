"""Research only: the owner's gap (marker outer edge to the minute-track tick ends, track.outer_gap) on many photos.

  python3 gap.py dump.jsonl image_root out.csv [workers]

Prints, per shape and hour, the spread of the gap on the measurable (ungated) markers.
"""
import csv
import json
import os
import sys
from multiprocessing import Pool

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import track as tr  # noqa: E402
import importlib.util  # noqa: E402
_s = importlib.util.spec_from_file_location('track_validate', os.path.join(HERE, 'validate.py'))
va = importlib.util.module_from_spec(_s)
_s.loader.exec_module(va)     # this folder's validate.py (another one sits on the research path)

COLS = ['photo_id', 'model', 'rpx', 'hour', 'shape', 'gate', 'gap_px', 'gap_pct', 'gap_gen_pct', 'edge_out_pct', 'inout_pct', 'side_pct']


def work(line):
    d = json.loads(line)
    try:
        if not d.get('pose'):
            return []
        img = va.load(os.path.join(ROOT, d['path']), d)
        if img is None:
            return []
        rows, _, _ = tr.analyse(img, d)
    except Exception as e:      # research: one bad photo is logged, never stops the run
        print('ERROR', d.get('photo_id'), repr(e), flush=True)
        return []
    return [dict(photo_id=d['photo_id'], model=d['model'], rpx='%.0f' % d['rpx'], hour=r['hour'], shape=r['shape'],
                 gate=r['gate'], **{k: va.fmt(r.get(k)) for k in COLS[6:]}) for r in rows]


def summary(rows):
    print('shape hour  n  min   p1   p5  p25  med  | gap minus genuine-drawing gap: min p1 p5 med  (% R)')
    keys = sorted({(r['shape'], int(r['hour'])) for r in rows})
    for shape in sorted({k[0] for k in keys}) :
        for h in [None] + sorted(k[1] for k in keys if k[0] == shape):
            g = [(float(r['gap_pct']), float(r['gap_pct']) - float(r['gap_gen_pct']),
                  float(r['edge_out_pct']) if r['edge_out_pct'] else np.nan) for r in rows
                 if r['shape'] == shape and (h is None or int(r['hour']) == h) and not r['gate'] and r['gap_pct']]
            if not g:
                continue
            a, b, c = np.array(g).T
            c = c[np.isfinite(c)]
            print('%-8s %4s %4d %5.2f %5.2f %5.2f %5.2f %5.2f | %5.2f %5.2f %5.2f %5.2f | edge out: n %d med %5.2f p95 %5.2f '
                  'p99 %5.2f max %5.2f' % (shape, 'all' if h is None else h, len(a), a.min(), *np.percentile(a, [1, 5, 25, 50]),
                                          b.min(), *np.percentile(b, [1, 5, 50]), len(c),
                                          *(np.percentile(c, [50, 95, 99]) if len(c) else [np.nan] * 3),
                                          c.max() if len(c) else np.nan))


def main():
    global ROOT
    dump, ROOT, outp = sys.argv[1], sys.argv[2], sys.argv[3]
    workers = int(sys.argv[4]) if len(sys.argv) > 4 else 4
    lines = [l for l in open(dump) if l.strip()]
    rows = []
    with Pool(workers) as pool:
        for rr in pool.imap_unordered(work, lines):
            rows += rr
    with open(outp, 'w', newline='') as fo:
        w = csv.DictWriter(fo, fieldnames=COLS)
        w.writeheader()
        w.writerows(rows)
    summary(rows)


if __name__ == '__main__':
    main()
