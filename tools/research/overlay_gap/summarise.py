"""Summaries of benchmark.py output (research only).

  python3 summarise.py accuracy bench.csv [...]   ground-truth accuracy per method and shape
  python3 summarise.py genuine  bench.csv [...]   how far GENUINE markers sit from the overlay, per method and shape
"""
import csv
import sys

import numpy as np

METHODS = ('edges', 'centre', 'layered', 'agree')


def accuracy(R):
    print('photos', len({r['photo_id'] for r in R}), 'markers', len({(r['photo_id'], r['hour']) for r in R}))
    for meth in METHODS:
        for shape in ('round', 'baton', 'triangle', 'ALL'):
            for hand in ('clear', 'hand', 'any'):
                rs = [r for r in R if r['method'] == meth and r['move_px'] != 'base' and (shape == 'ALL' or r['shape'] == shape)
                      and (hand == 'any' or (r['hand_clean'] == '1') == (hand == 'clear'))]
                if not rs:
                    continue
                e = np.array([float(r['err_px']) for r in rs if r['err_px']])
                if len(e) == 0:
                    print('%-8s %-8s %-5s tests %5d answered 0' % (meth, shape, hand, len(rs)))
                    continue
                print('%-8s %-8s %-5s tests %5d answered %5d (%3.0f%%)  err p50 %.2f p90 %.2f p99 %.2f max %.2f  >0.5px %4d  >1px %4d'
                      % (meth, shape, hand, len(rs), len(e), 100 * len(e) / len(rs), np.median(e), np.percentile(e, 90),
                         np.percentile(e, 99), e.max(), (e > 0.5).sum(), (e > 1).sum()))


def genuine(R):
    base = {}
    for r in R:
        if r['move_px'] in ('base', '0.0') and (r['move_deg'] in ('', '0.0')):
            base[(r['photo_id'], r['hour'], r['method'])] = r
    print('photos', len({k[0] for k in base}), 'markers', len({k[:2] for k in base}))
    for meth in METHODS:
        for shape in ('round', 'baton', 'triangle'):
            rs = [r for k, r in base.items() if k[2] == meth and r['shape'] == shape]
            if not rs:
                continue
            g = np.array([float(r['base_gap_px']) / float(r['rpx']) * 100 for r in rs if r['base_gap_px']])  # % of dial radius
            gp = np.array([float(r['base_gap_px']) for r in rs if r['base_gap_px']])
            if len(g) == 0:
                continue
            w = {}
            for r in rs:
                if r['base_gap_px']:
                    w[r['watch']] = max(w.get(r['watch'], 0), float(r['base_gap_px']) / float(r['rpx']) * 100)
            print('%-8s %-8s markers %5d answered %5d (%3.0f%%)  gap %%R p50 %.2f p95 %.2f p99 %.2f max %.2f | px p50 %.2f p95 %.2f max %.2f | watches %d'
                  % (meth, shape, len(rs), len(g), 100 * len(g) / len(rs), np.median(g), np.percentile(g, 95),
                     np.percentile(g, 99), g.max(), np.median(gp), np.percentile(gp, 95), gp.max(), len(w)))
        worst = sorted(((float(r['base_gap_px']) / float(r['rpx']) * 100, r['photo_id'], r['hour'], r['shape'])
                        for k, r in base.items() if k[2] == meth and r['base_gap_px']), reverse=True)[:5]
        print('   largest:', ', '.join('%s %sh %s %.2f%%R' % (p, h, s, v) for v, p, h, s in worst))


if __name__ == '__main__':
    R = [r for f in sys.argv[2:] for r in csv.DictReader(open(f))]
    {'accuracy': accuracy, 'genuine': genuine}[sys.argv[1]](R)
