"""Research only: finer threshold sweep on validate.py output (owner feedback, 17 Oct 2026).

  python3 calibrate.py out.csv [...]
Prints the largest genuine (unmoved) readings after the margin, per shape, and detection for a finer grid, so the
limits can be set just above genuine and checked against markers the owner calls clearly off.
"""
import csv
import sys

import numpy as np

MARGIN_PX = 1.0
MARGIN_DEG = 0.5


def pos(r):
    mp = 100 * MARGIN_PX / float(r['rpx'])
    return min(float(r['shift_pct']), float(r['rel_pct'])) - mp


def rot(r):
    if not r['tilt_deg'] or not r['rel_tilt_deg']:
        return float('nan')
    return min(abs(float(r['tilt_deg'])), abs(float(r['rel_tilt_deg']))) - MARGIN_DEG


def main(files):
    R = [r for f in files for r in csv.DictReader(open(f))
         if r['state'] != "Can't check" and r['shift_pct'] and r['rel_pct']]
    base = [r for r in R if r['move'] == 'base']
    moved = [r for r in R if r['move'] == 'moved']
    print('genuine markers measured:', len(base))
    for shape in ('round', 'baton', 'triangle'):
        b = [r for r in base if r['shape'] == shape]
        p = np.sort([pos(r) for r in b])
        print('%-8s n %4d  position after margin: p99 %.2f  top5 %s' % (shape, len(b), np.percentile(p, 99), ' '.join('%.2f' % x for x in p[-5:])))
        t = np.sort([x for x in (rot(r) for r in b) if x == x])
        if len(t):
            print('%-8s         tilt after margin:     p99 %.2f  top5 %s' % ('', np.percentile(t, 99), ' '.join('%.2f' % x for x in t[-5:])))
    for shape in ('triangle', 'baton', 'round'):
        b = [r for r in base if r['shape'] == shape and r['radial_px']]
        for k in ('radial_px', 'tangential_px'):
            v = np.sort([100 * float(r[k]) / float(r['rpx']) for r in b])
            print('%-8s %-13s %% R: min %.2f p1 %.2f p50 %.2f p99 %.2f max %.2f  top5 %s' % (
                shape, k[:-3], v[0], np.percentile(v, 1), np.median(v), np.percentile(v, 99), v[-1],
                ' '.join('%.2f' % x for x in v[-5:])))
    t = [r for r in base if r['shape'] == 'triangle']
    v = np.sort([min(float(r['shift_pct']), float(r['rel_pct'])) for r in t])
    print('triangle, no pixel margin: min(abs, rel) %% R top8 %s' % ' '.join('%.2f' % x for x in v[-8:]))
    print('  lowest rpx among the top8: %s' % ' '.join(sorted({r['rpx'] for r in t if min(float(r['shift_pct']), float(r['rel_pct'])) >= v[-8]})))
    print('  triangle limit (no margin) | genuine flagged | moved 0.5%%  1%%  2%%')
    for tp in (0.4, 0.45, 0.5, 0.55, 0.6, 0.7):
        cells = []
        for mp in ('0.5', '1.0', '2.0'):
            rs = [r for r in moved if r['shape'] == 'triangle' and r['move_pct'] == mp and r['move_deg'] == '0.0']
            cells.append('%5.1f%%' % (100.0 * sum(1 for r in rs if min(float(r['shift_pct']), float(r['rel_pct'])) > tp) / len(rs)) if rs else '  n/a')
        print('  %.2f %% R                  | %3d             | %s' % (tp, sum(1 for x in v if x > tp), ' '.join(cells)))
    print('\nposition limit (after margin) | genuine flagged | moved 1%  1.5%  2% (all shapes)')
    for tp in (0.5, 0.6, 0.7, 0.75, 0.8, 0.9, 1.0, 1.25):
        fa = sum(1 for r in base if pos(r) > tp)
        cells = []
        for mp in ('1.0', '1.5', '2.0'):
            rs = [r for r in moved if r['move_pct'] == mp and r['move_deg'] == '0.0']
            cells.append('%5.1f%%' % (100.0 * sum(1 for r in rs if pos(r) > tp) / max(len(rs), 1)) if rs else '   n/a')
        print('  %.2f %% R                     | %4d            | %s' % (tp, fa, ' '.join(cells)))
    print('\nbaton tilt limit (after margin) | genuine batons flagged | turned 2  4 deg')
    for tr in (1.0, 1.25, 1.5, 1.75, 2.0, 2.5):
        fa = sum(1 for r in base if r['shape'] == 'baton' and rot(r) > tr)
        cells = []
        for a in ('2.0', '4.0'):
            rs = [r for r in moved if r['shape'] == 'baton' and r['move_pct'] == '0.0' and r['move_deg'].lstrip('-') == a]
            cells.append('%5.1f%%' % (100.0 * sum(1 for r in rs if rot(r) > tr) / max(len(rs), 1)))
        print('  %.2f deg                       | %4d                   | %s' % (tr, fa, ' '.join(cells)))


if __name__ == '__main__':
    main(sys.argv[1:])
