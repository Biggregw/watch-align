#!/usr/bin/env python3
"""Three-option regression: each marker of each photo, before (committed results, main) and after (this branch).

Usage: compare_options.py before.csv after.csv [label]
Both files are TrackDump CSVs. Prints the state transitions, the size of the measurement changes and every marker
whose state changed, so a reviewer can look at each one. Exit code 1 if a photo or marker went missing.
"""
import csv
import math
import sys
from collections import Counter


def f(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return float('nan')


def key(r):
    return (r['photo_id'], r['hour'])


def main(before, after, label=''):
    b = {key(r): r for r in csv.DictReader(open(before))}
    a = {key(r): r for r in csv.DictReader(open(after))}
    missing = sorted(set(b) - set(a))
    print('== %s: %d markers before, %d after, %d missing after, %d new' % (label, len(b), len(a), len(missing), len(set(a) - set(b))))
    trans = Counter((b[k]['state'], a[k]['state']) for k in b if k in a)
    for (s0, s1), n in sorted(trans.items()):
        print('  %-17s -> %-17s %5d' % (s0, s1, n))
    for col, name in (('side_pct', 'sideways %R'), ('inout_pct', 'in/out %R'), ('tilt_line_deg', 'tilt deg'), ('gap_pct', 'gap %R')):
        for shape in ('round', 'baton', 'triangle'):
            d = [f(a[k][col]) - f(b[k][col]) for k in b if k in a and b[k]['shape'] == shape]
            d = [x for x in d if math.isfinite(x)]
            if d:
                d.sort()
                print('  change %-11s %-8s n=%4d median %+.3f  min %+.3f  max %+.3f' % (name, shape, len(d), d[len(d) // 2], d[0], d[-1]))
    print('  markers whose state changed:')
    for k in sorted(b):
        if k in a and b[k]['state'] != a[k]['state']:
            r0, r1 = b[k], a[k]
            print('   %s h%-2s %-8s %s -> %s | side %s->%s inout %s->%s tilt %s->%s gap %s->%s | %s' % (
                k[0], k[1], r0['shape'], r0['state'], r1['state'], r0['side_pct'], r1['side_pct'], r0['inout_pct'], r1['inout_pct'],
                r0['tilt_line_deg'], r1['tilt_line_deg'], r0['gap_pct'], r1['gap_pct'], (r1['uncertain'] or r1['reason'])[:60]))
    for k in missing:
        print('   MISSING', k)
    return 1 if missing else 0


if __name__ == '__main__':
    sys.exit(main(*sys.argv[1:4]))
