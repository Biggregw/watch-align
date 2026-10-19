"""Experimental 1.6.3: the same photos measured again after downscaling (lower resolution is uncertainty, not rejection).

  python3 app163_scale.py full.csv label:scaled.csv [label:scaled.csv ...]

Per scale, against the full-size reading of the same marker (app code, desktop harness): dial size, markers answered,
how far the readings moved (side / in-out % R, tilt deg), findings that appear only at the lower resolution (false on
genuine photos), and answers that change.
"""
import csv
import sys
from collections import Counter

import numpy as np

FLAG = ('CLEARLY_OFF', 'POSSIBLE')


def f(x):
    return float(x) if x not in ('', None) else float('nan')


def main():
    full = list(csv.DictReader(open(sys.argv[1])))
    k = {(r['photo_id'], r['hour']): r for r in full}
    print('full size: dial %.0f-%.0f px across (median %.0f), answered %d/%d, findings %s' % (
        2 * min(f(r['rpx']) for r in full), 2 * max(f(r['rpx']) for r in full), 2 * np.median([f(r['rpx']) for r in full]),
        sum(r['state'] != 'CANT_CHECK' for r in full), len(full), dict(Counter(r['state'] for r in full if r['state'] in FLAG))))
    for arg in sys.argv[2:]:
        lab, p = arg.split(':', 1)
        try:
            rs = list(csv.DictReader(open(p)))
        except FileNotFoundError:
            continue
        if not rs:
            continue
        ans = sum(r['state'] != 'CANT_CHECK' for r in rs)
        print('\nscale %s: dial median %.0f px across; answered %d/%d (%.1f%%); can\'t check reasons %s' % (
            lab, 2 * np.median([f(r['rpx']) for r in rs]), ans, len(rs), 100 * ans / len(rs),
            dict(Counter((r['gate'] or r['reason'])[:60] for r in rs if r['state'] == 'CANT_CHECK').most_common(4))))
        for q in ('side_pct', 'inout_pct', 'tilt_line_deg'):
            d = []
            for r in rs:
                o = k.get((r['photo_id'], r['hour']))
                if o and r['state'] != 'CANT_CHECK' and o['state'] != 'CANT_CHECK':
                    d.append(abs(f(r[q]) - f(o[q])))
            d = np.array([x for x in d if x == x])
            if len(d):
                big = (d > (0.5 if q != 'tilt_line_deg' else 1.0)).sum()
                print('  moved vs full size  %-13s n %4d  p50 %.2f  p95 %.2f  max %.2f   over %s: %d' % (
                    q, len(d), np.median(d), np.percentile(d, 95), d.max(), '0.5 %R' if q != 'tilt_line_deg' else '1 deg', big))
        new = [r for r in rs if r['state'] in FLAG and (k.get((r['photo_id'], r['hour'])) or {}).get('state') not in FLAG]
        lost = [r for r in rs if r['state'] not in FLAG and (k.get((r['photo_id'], r['hour'])) or {}).get('state') in FLAG]
        print('  findings only at this size: %d (clearly off %d, inspect %d); findings lost: %d' % (
            len(new), sum(r['state'] == 'CLEARLY_OFF' for r in new), sum(r['state'] == 'POSSIBLE' for r in new), len(lost)))
        for r in (new + lost)[:12]:
            o = k.get((r['photo_id'], r['hour'])) or {}
            print('     %-28s %2s %-8s full %-16s -> %-16s side %s inout %s tilt %s gap %s | %s' % (
                r['photo_id'][:28], r['hour'], r['shape'], o.get('state', '-'), r['state'], r['side_pct'][:6], r['inout_pct'][:6],
                r['tilt_line_deg'][:6], r['gap_pct'][:5], (r.get('reason') or r.get('uncertain') or '')[:70]))


if __name__ == '__main__':
    main()
