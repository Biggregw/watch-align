"""Research only: shape-specific limits on track/validate.py output.

  python3 limits.py out.csv [...]
For each candidate set: genuine markers flagged (listed), untouched controls flagged, and detection of known moves by
kind and size. Limits apply to absolute values: sideways (% R), in/out (% R; the triangle has its own), baton tilt (deg).
"""
import csv
import itertools
import sys

import numpy as np


def f(x):
    return float(x) if x not in ('', None) else float('nan')


def off(r, L):
    if r['gate']:
        return None
    s, i, t = abs(f(r['side_pct'])), abs(f(r['inout_pct'])), abs(f(r['tilt_line_deg']))
    ti = L['inout_tri'] if r['shape'] == 'triangle' else L['inout']
    why = []
    if s > L['side']:
        why.append('side')
    if i > ti:
        why.append('inout')
    if r['shape'] == 'baton' and t > L['tilt']:
        why.append('tilt')
    return why


def main(files):
    R = [r for fn in files for r in csv.DictReader(open(fn))]
    clean = [r for r in R if r['move'] == 'base' and not r['gate']]
    moved = [r for r in R if r['move'] == 'moved']
    print('genuine clean markers %d, photos %d' % (len(clean), len({r['photo_id'] for r in clean})))
    for side, inout, tri, tilt in itertools.product((1.0, 1.25), (1.0, 1.25), (1.25, 1.5), (2.0, 2.25, 2.5)):
        L = dict(side=side, inout=inout, inout_tri=tri, tilt=tilt)
        g = [(r, off(r, L)) for r in clean]
        g = [(r, w) for r, w in g if w]
        c = sum(1 for r in moved if r['kind'] == 'control' and off(r, L))
        cells = []
        for kind, amts in (('side', (1.0, 1.5, 2.0)), ('inout', (1.0, 1.5, 2.0)), ('turn', (1.5, 2.0, 3.0))):
            for a in amts:
                rs = [r for r in moved if r['kind'] == kind and abs(f(r['amount'])) == a]
                cells.append('%s%g %3.0f%%' % (kind[0], a, 100.0 * sum(1 for r in rs if off(r, L)) / max(len(rs), 1)))
        print('side %.2f inout %.2f tri %.2f tilt %.2f | genuine %d controls %d | %s' % (side, inout, tri, tilt, len(g), c, ' '.join(cells)))
        for r, w in g[:4]:
            print('     genuine flagged: %s %sh %s rpx %s %s (side %s inout %s tilt %s)' % (
                r['photo_id'][:12], r['hour'], r['shape'], r['rpx'], '+'.join(w), r['side_pct'][:5], r['inout_pct'][:5], r['tilt_line_deg'][:5]))


if __name__ == '__main__':
    main(sys.argv[1:])
