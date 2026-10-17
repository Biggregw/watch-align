"""Research only: the app's clearly-off check (TrackDump, Java) against this research tool (validate.py, Python) on the
same photos, and the app's answers on genuine photos.

  python3 parity.py java.csv python.csv
"""
import csv
import sys

import numpy as np


def f(x):
    return float(x) if x not in ('', None) else float('nan')


def main(jf, pf):
    J = {(r['photo_id'], r['hour']): r for r in csv.DictReader(open(jf))}
    P = {(r['photo_id'], r['hour']): r for r in csv.DictReader(open(pf)) if r['move'] == 'base'}
    both = [k for k in J if k in P]
    print('markers: java %d, python %d, both %d' % (len(J), len(P), len(both)))
    gate = sum(1 for k in both if bool(J[k]['gate']) != bool(P[k]['gate']))
    print('gate (can\'t check) disagreements: %d' % gate)
    for jk, pk in (('side_pct', 'side_pct'), ('inout_pct', 'inout_pct'), ('tilt_line_deg', 'tilt_line_deg'), ('gap_pct', 'gap_pct')):
        d = [abs(f(J[k][jk]) - f(P[k][pk])) for k in both if J[k].get(jk) and P[k].get(pk)]
        if d:
            print('%-14s |java - python|: p50 %.4f p99 %.4f max %.4f (n %d)' % (jk, np.median(d), np.percentile(d, 99), max(d), len(d)))
    st = {}
    for r in J.values():
        st[r['state']] = st.get(r['state'], 0) + 1
    print('app answers on these (genuine) photos:', st)
    for r in J.values():
        if r['state'] == 'CLEARLY_OFF':
            print('  CLEARLY OFF on a genuine photo: %s %sh %s side %s inout %s tilt %s gap %s rpx %s' % (
                r['photo_id'], r['hour'], r['shape'], r['side_pct'], r['inout_pct'], r['tilt_line_deg'],
                r.get('gap_pct', ''), r['rpx']))


if __name__ == '__main__':
    main(*sys.argv[1:3])
