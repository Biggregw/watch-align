"""Research only: summarise track/validate.py output.

  python3 summary.py out.csv [...]
Genuine photos: as found = the genuine spread; moved = recovery of known moves and detection at candidate limits.
"""
import collections
import csv
import sys

import numpy as np


def f(x):
    return float(x) if x not in ('', None) else float('nan')


def pct(v, qs=(50, 95, 99, 100)):
    v = np.asarray([x for x in v if x == x])
    return ('n %4d ' % len(v) + ' '.join('p%d %.2f' % (q, np.percentile(v, q)) for q in qs)) if len(v) else 'n 0'


def main(files):
    R = [r for fn in files for r in csv.DictReader(open(fn))]
    base = [r for r in R if r['move'] == 'base']
    clean = [r for r in base if not r['gate']]
    print('photos %d, markers %d, clean %d (can\'t check %d)' % (len({r['photo_id'] for r in base}), len(base), len(clean),
                                                                  len(base) - len(clean)))
    print("can't check reasons:", collections.Counter(r['gate'].split(';')[0] for r in base if r['gate']).most_common(5))
    turn = {r['photo_id']: f(r['ring_turn_pct']) for r in base}
    print('\nGENUINE, as found (absolute values, % of dial radius / deg)')
    print('  ring turn per dial      ', pct([abs(x) for x in turn.values()]))
    for shape in ('round', 'baton', 'triangle'):
        c = [r for r in clean if r['shape'] == shape]
        print('  %-8s sideways       ' % shape, pct([abs(f(r['side_pct'])) for r in c]))
        print('  %-8s side - ring turn' % shape, pct([abs(f(r['side_pct']) - f(r['ring_turn_pct'])) for r in c]))
        print('  %-8s in/out         ' % shape, pct([abs(f(r['inout_pct'])) for r in c]))
        if shape != 'round':
            print('  %-8s tilt vs line   ' % shape, pct([abs(f(r['tilt_line_deg'])) for r in c]))
    for key, shape, lab in (('side_pct', None, 'sideways'), ('inout_pct', None, 'in/out'), ('tilt_line_deg', 'baton', 'tilt')):
        top = sorted([r for r in clean if (shape is None or r['shape'] == shape) and r[key]], key=lambda r: -abs(f(r[key])))[:5]
        print('  largest %-8s: %s' % (lab, ', '.join('%s %sh %s %+.2f (rpx %s)' % (r['photo_id'][:12], r['hour'], r['shape'][:3], f(r[key]), r['rpx']) for r in top)))

    moved = [r for r in R if r['move'] == 'moved']
    if not moved:
        return
    ctrl = {(r['photo_id'], r['hour']): r for r in moved if r['kind'] == 'control'}
    print('\nKNOWN MOVES: recovery (measured change vs injected), median |error| and p95')
    key = {'side': 'side_pct', 'inout': 'inout_pct', 'turn': 'tilt_line_deg'}
    for kind in ('side', 'inout', 'turn'):
        for amt in sorted({abs(f(r['amount'])) for r in moved if r['kind'] == kind}):
            rs = [r for r in moved if r['kind'] == kind and abs(f(r['amount'])) == amt]
            err = []
            lost = 0
            for r in rs:
                c = ctrl.get((r['photo_id'], r['hour']))
                if r['gate'] or not c or not r[key[kind]] or not c[key[kind]]:
                    lost += 1
                    continue
                err.append(abs(f(r[key[kind]]) - f(c[key[kind]]) - f(r['amount'])))
            print('  %-5s %4.1f: n %4d  err p50 %.3f p95 %.3f  lost to can\'t check %d' % (
                kind, amt, len(rs), np.median(err) if err else float('nan'), np.percentile(err, 95) if err else float('nan'), lost))

    print('\nCANDIDATE LIMITS: genuine flagged (as found) | moved markers flagged, by size')

    def flag(r, ts, ti, tt):
        if r['gate']:
            return None
        return (r['side_pct'] and abs(f(r['side_pct'])) > ts) or (r['inout_pct'] and abs(f(r['inout_pct'])) > ti) or \
            (r['shape'] == 'baton' and r['tilt_line_deg'] and abs(f(r['tilt_line_deg'])) > tt)
    for ts, ti, tt in ((0.6, 0.8, 1.25), (0.75, 1.0, 1.5), (0.75, 1.25, 1.5), (1.0, 1.25, 1.5), (1.0, 1.5, 2.0), (1.0, 1.5, 2.5)):
        g = sum(1 for r in clean if flag(r, ts, ti, tt))
        cf = sum(1 for r in moved if r['kind'] == 'control' and flag(r, ts, ti, tt))
        cells = []
        for kind, amts in (('side', (0.5, 1.0, 1.5, 2.0)), ('inout', (0.5, 1.0, 1.5, 2.0)), ('turn', (1.0, 1.5, 2.0, 3.0))):
            for amt in amts:
                rs = [r for r in moved if r['kind'] == kind and abs(f(r['amount'])) == amt]
                dec = [flag(r, ts, ti, tt) for r in rs]
                cells.append('%s%g:%3.0f%%' % (kind[0], amt, 100.0 * sum(1 for x in dec if x) / max(len(rs), 1)))
        print('  side>%.2f inout>%.2f tilt>%.2f | genuine %d/%d, controls %d | %s' % (ts, ti, tt, g, len(clean), cf, ' '.join(cells)))


if __name__ == '__main__':
    main(sys.argv[1:])
