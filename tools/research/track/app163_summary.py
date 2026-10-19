"""Experimental 1.6.3 (outer-metal fit in the app) against 1.6.2 (lume centre), both the app's own code (desktop harness)
on identical photos.

  python3 app163_summary.py app_off.csv app_on.csv qc_off.csv qc_on.csv synth_off.csv synth_on.csv [photo_id_prefix ...]

Genuine photos: coverage (markers given an answer), findings (every one is false on a genuine watch), the remaining
Can't check reasons, and how far the two methods' numbers differ. Synthetic (genuine photos, the photo itself changed):
a hand-like bar (dark / bright) across a third of a marker, the marker moved 1.5 % R sideways / out, turned (batons 3,
triangle 1.5 deg): assessed, measurement error, caught (clearly off or inspect) and lost; for the bars, false findings.
Replica QC photos: every marker whose answer differs. Named photos: every marker with this photo's edge offsets.
"""
import csv
import math
import sys
from collections import Counter

import numpy as np

FLAG = ('CLEARLY_OFF', 'POSSIBLE')


def rows(p):
    try:
        return list(csv.DictReader(open(p)))
    except FileNotFoundError:
        return []


def f(x):
    return float(x) if x not in ('', None) else float('nan')


def coverage(off, on, label):
    print('%s: markers given an answer (not Can\'t check)' % label)
    for sh in ('round', 'baton', 'triangle'):
        a = [r for r in off if r['shape'] == sh]
        b = [r for r in on if r['shape'] == sh]
        if a or b:
            ca = sum(r['state'] != 'CANT_CHECK' for r in a)
            cb = sum(r['state'] != 'CANT_CHECK' for r in b)
            print('  %-8s 1.6.2 %4d/%-4d (%5.1f%%)   1.6.3 %4d/%-4d (%5.1f%%)' % (sh, ca, len(a), 100 * ca / max(1, len(a)), cb, len(b), 100 * cb / max(1, len(b))))


def findings(rs, label):
    c = Counter(r['state'] for r in rs)
    fl = [r for r in rs if r['state'] in FLAG]
    print('  %s: clearly off %d, inspect %d, no visible issue %d, can\'t check %d' % (label, c['CLEARLY_OFF'], c['POSSIBLE'], c['NO_VISIBLE_ISSUE'], c['CANT_CHECK']))
    for r in fl[:20]:
        print('     %s %s %2s %-8s %s side %s inout %s tilt %s gap %s | %s' % (r['photo_id'][:12], r['state'][:8], r['hour'], r['shape'], '',
              r['side_pct'][:6], r['inout_pct'][:6], r['tilt_line_deg'][:6], r['gap_pct'][:5], (r.get('reason') or r.get('uncertain') or '')[:110]))


def main():
    a = sys.argv[1:]
    off, on, qoff, qon, soff, son = (rows(p) for p in a[:6])
    named = a[6:]
    print('=== GENUINE photos (%d photos)' % len({r['photo_id'] for r in on}))
    coverage(off, on, 'coverage')
    print('findings on genuine watches (all false):')
    findings(off, '1.6.2')
    findings(on, '1.6.3')
    cc = Counter(r.get('reason') or r['gate'] for r in on if r['state'] == 'CANT_CHECK')
    print('1.6.3 remaining Can\'t check reasons: %s' % dict(cc.most_common(8)))
    unc = [r for r in on if r['state'] == 'NO_VISIBLE_ISSUE' and r.get('uncertain')]
    print('1.6.3 no visible issue with an uncertain edge (noted): %d of %d answered' % (len(unc), sum(r['state'] != 'CANT_CHECK' for r in on)))
    k = {(r['photo_id'], r['hour']): r for r in off}
    for q in ('side_pct', 'inout_pct', 'tilt_line_deg'):
        d = [abs(f(r[q]) - f(k[(r['photo_id'], r['hour'])][q])) for r in on if (r['photo_id'], r['hour']) in k
             and r['state'] != 'CANT_CHECK' and k[(r['photo_id'], r['hour'])]['state'] != 'CANT_CHECK']
        d = np.array([x for x in d if x == x])
        if len(d):
            print('  |1.6.3 - 1.6.2| %-13s n %4d  p50 %.2f  p95 %.2f  max %.2f' % (q, len(d), np.median(d), np.percentile(d, 95), d.max()))
    for nm, rs in (('1.6.2', off), ('1.6.3', on)):
        for q in ('side_pct', 'inout_pct'):
            v = np.array([abs(f(r[q])) for r in rs if r['state'] != 'CANT_CHECK' and r['shape'] != 'triangle' and r[q]])
            if len(v):
                print('  %s genuine |%s| p95 %.2f max %.2f' % (nm, q, np.percentile(v, 95), v.max()))
        t = np.array([abs(f(r['tilt_line_deg'])) for r in rs if r['state'] != 'CANT_CHECK' and r['shape'] == 'triangle' and r['tilt_line_deg']])
        if len(t):
            print('  %s genuine triangle |rotation| p50 %.2f p95 %.2f max %.2f (n %d)' % (nm, np.median(t), np.percentile(t, 95), t.max(), len(t)))

    print('\n=== SYNTHETIC (genuine photos changed in the photo; app code)')
    for nm, S in (('1.6.2', soff), ('1.6.3', son)):
        for trial in ('occlude_dark', 'occlude_bright', 'side', 'inout', 'turn'):
            T = [r for r in S if r['trial'] == trial]
            if not T:
                continue
            ok = [r for r in T if r['state'] not in ('CANT_CHECK', 'ERROR')]
            err = []
            for r in ok:
                if trial.startswith('occlude'):
                    err.append(math.hypot(f(r['side']) - f(r['base_side']), f(r['inout']) - f(r['base_inout'])))
                else:
                    q = {'side': 'side', 'inout': 'inout', 'turn': 'tilt'}[trial]
                    err.append(abs(f(r[q]) - f(r['base_' + q]) - f(r['amount'])))
            e = np.array([x for x in err if x == x])
            line = '  %s %-14s assessed %4d/%-4d' % (nm, trial, len(ok), len(T))
            if len(e):
                line += '  |error| p50 %.2f p95 %.2f max %.2f' % (np.median(e), np.percentile(e, 95), e.max())
            fl = [r for r in T if r['state'] in FLAG]
            if trial.startswith('occlude'):
                new = [r for r in fl if r['base_state'] not in FLAG]
                line += '  false findings %d (clearly off %d, inspect %d)' % (len(new), sum(r['state'] == 'CLEARLY_OFF' for r in new), sum(r['state'] == 'POSSIBLE' for r in new))
            else:
                line += '  caught %d (clearly off %d, inspect %d)  lost %d' % (len(fl), sum(r['state'] == 'CLEARLY_OFF' for r in fl), sum(r['state'] == 'POSSIBLE' for r in fl), len(T) - len(fl))
            print(line)

    print('\n=== REPLICA QC photos: answers that differ (1.6.2 -> 1.6.3)')
    k = {(r['photo_id'], r['hour']): r for r in qoff}
    for r in qon:
        o = k.get((r['photo_id'], r['hour']))
        if o is None or o['state'] != r['state'] or r['state'] in FLAG:
            print('  %-30s %2s %-8s %-16s -> %-16s side %s inout %s tilt %s gap %s | %s' % (r['photo_id'][:30], r['hour'], r['shape'], o['state'] if o else '-', r['state'],
                  r['side_pct'][:6], r['inout_pct'][:6], r['tilt_line_deg'][:6], r['gap_pct'][:5], (r.get('reason') or r.get('uncertain') or '')[:90]))
    if named:
        print('\n=== NAMED photos (1.6.3; edge offset = this photo\'s outer-edge correction for the shape, px)')
        for r in on:
            if any(r['photo_id'].startswith(n) for n in named):
                o = k2 = {(x['photo_id'], x['hour']): x for x in off}.get((r['photo_id'], r['hour']))
                print('  %s %2s %-8s 1.6.2 %-16s 1.6.3 %-16s side %s inout %s tilt %s cover %s rms %s bad %s edge offset %s ring turn %s bad ticks %s | %s' % (
                    r['photo_id'][:12], r['hour'], r['shape'], o['state'] if o else '-', r['state'], r['side_pct'][:6], r['inout_pct'][:6], r['tilt_line_deg'][:6],
                    r['cover'][:4], r['fit_rms_px'][:4], r['bad_frac'][:4], r['edge_bias_px'][:5], r['ring_turn_pct'][:5], r['bad_ticks'], (r.get('reason') or r.get('uncertain') or '')[:80]))


if __name__ == '__main__':
    main()
