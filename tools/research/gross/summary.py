"""Research only: summarise validate.py output, with a threshold sweep.

  python3 summary.py out.csv [...]
Genuine photos: every "Clearly off" on an unmoved marker is a false alarm.
"""
import collections
import csv
import sys

import numpy as np

MARGIN_PX = 1.0
MARGIN_DEG = 0.5


def off(r, tp, tr):
    if r['state'] == "Can't check" or not r['shift_pct']:
        return None
    mp = 100 * MARGIN_PX / float(r['rpx'])
    pos = float(r['shift_pct']) - mp > tp and r['rel_pct'] and float(r['rel_pct']) - mp > tp
    rot = r['tilt_deg'] and r['rel_tilt_deg'] and abs(float(r['tilt_deg'])) - MARGIN_DEG > tr and abs(float(r['rel_tilt_deg'])) - MARGIN_DEG > tr
    return bool(pos or rot)


def main(files):
    R = [r for f in files for r in csv.DictReader(open(f))]
    base = [r for r in R if r['move'] == 'base']
    moved = [r for r in R if r['move'] == 'moved']
    print('photos %d, markers %d, watches %d' % (len({r['photo_id'] for r in base}), len(base), len({r['watch'] for r in base})))
    st = collections.Counter(r['state'] for r in base)
    print('as found:', dict(st))
    print("can't check reasons:", collections.Counter(r['gate'] for r in base if r['gate']).most_common(6))
    m = [r for r in base if r['state'] != "Can't check" and r['shift_pct']]
    for k in ('shift_pct', 'rel_pct'):
        v = np.array([float(r[k]) for r in m if r[k]])
        print('%-9s %% of dial: p50 %.2f p95 %.2f p99 %.2f max %.2f' % (k, *np.percentile(v, [50, 95, 99, 100])))
    t = np.array([abs(float(r['tilt_deg'])) for r in m if r['tilt_deg']])
    if len(t):
        print('|tilt| deg (batons, triangle): p50 %.2f p95 %.2f p99 %.2f max %.2f' % tuple(np.percentile(t, [50, 95, 99, 100])))
    worst = sorted(m, key=lambda r: -min(float(r['shift_pct']), float(r['rel_pct'] or 0)))[:6]
    print('largest (abs/rel %):', ', '.join('%s %sh %s %.2f/%.2f' % (r['photo_id'][:16], r['hour'], r['shape'], float(r['shift_pct']), float(r['rel_pct'] or 0)) for r in worst))
    fa = [r for r in base if r['state'] == 'Clearly off']
    for r in fa:
        print('AS-FOUND CLEARLY OFF: %s %sh %s rpx %s abs %s rel %s tilt %s rel_tilt %s peers %s' % (
            r['photo_id'], r['hour'], r['shape'], r['rpx'], r['shift_pct'], r['rel_pct'], r['tilt_deg'], r['rel_tilt_deg'], r['peers']))
    tl = sorted([r for r in m if r['tilt_deg']], key=lambda r: -abs(float(r['tilt_deg'])))[:5]
    print('largest tilts:', ', '.join('%s %sh %s %+.2f' % (r['photo_id'][:16], r['hour'], r['shape'], float(r['tilt_deg'])) for r in tl))
    print('\nthreshold sweep (as found = false alarms on genuine; detection on moved markers)')
    print('  T_pos  T_rot | as-found clearly off | moved 0%% (control) | 1%%  2%%  3%%  4%% | tilt 2  4  6 deg')
    for tp in (1.0, 1.25, 1.5, 2.0):
        for tr in (2.0, 3.0, 4.0):
            fa = sum(1 for r in m if off(r, tp, tr))
            cells = []
            for mp in ('0.0', '1.0', '2.0', '3.0', '4.0'):
                rs = [r for r in moved if r['move_pct'] == mp and r['move_deg'] == '0.0']
                dec = [off(r, tp, tr) for r in rs]
                cells.append('%3d/%-3d' % (sum(1 for x in dec if x), len(rs)))
            tl = []
            for a in ('2.0', '4.0', '6.0'):
                rs = [r for r in moved if r['move_pct'] == '0.0' and r['move_deg'].lstrip('-') == a]
                dec = [off(r, tp, tr) for r in rs]
                tl.append('%3d/%-3d' % (sum(1 for x in dec if x), len(rs)))
            print('  %4.2f  %4.1f | %4d / %-5d | %s | %s' % (tp, tr, fa, len(m), cells[0], ' '.join(cells[1:]) + ' | ' + ' '.join(tl)))
    if moved:
        cc = collections.Counter((r['move_pct'], r['move_deg'], r['state'] == "Can't check") for r in moved)
        lost = sum(v for k, v in cc.items() if k[2])
        print("\nmoved markers that became Can't check: %d / %d" % (lost, len(moved)))


if __name__ == '__main__':
    main(sys.argv[1:])
