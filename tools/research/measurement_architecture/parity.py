"""Parity of the Java MarkerGeometry stage (Phase B) with methods.py on ArchMeasure records.

    python -m tools.research.measurement_architecture.parity records.jsonl [...] [--fixture out.csv]

Every record carries the Java stage's output beside the raw measurement ('mg' per marker: tangential %R, radial %R,
tilt deg, eligible; 'mg_ring': tx, ty, scale, rotation, placement x, y, n). This recomputes LB and the ring level in
Python from the same raw numbers (methods.measure, k = MarkerGeometry.K_PARALLAX) and reports the largest difference.
Exit status 1 when any difference exceeds the tolerance, or an eligibility decision differs.

--fixture writes the inputs and Python results as the committed JUnit fixture
(android/app/src/test/resources/marker_geometry/parity.csv), so the app's unit tests check the same numbers.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import sys

from tools.image_archive import eligibility as el
from . import methods as M

K_PARALLAX = 0.0228          # MarkerGeometry.K_PARALLAX
TOL = 1e-6                    # % R / deg; the two implementations solve the same equations


def records(paths):
    for p in paths:
        for line in open(p):
            if line.strip():
                r = json.loads(line)
                if r.get('ok') and r.get('markers') and r.get('H'):
                    yield r


def compare(recs):
    worst, n, bad_elig, missing = 0.0, 0, 0, 0
    for r in recs:
        out, ring = M.measure(r, K_PARALLAX)
        for m in r['markers']:
            h = int(m['h'])
            if 'mg' not in m or h not in out:
                continue
            tj, rj, aj, ej = m['mg']
            if ej != (out[h]['status'] == el.ELIGIBLE):
                bad_elig += 1
            lb = out[h].get('LB')
            if lb is None:
                if tj is not None:
                    missing += 1
                continue
            for x, y in ((lb[0], tj), (lb[1], rj), (lb[2], aj)):
                if x is None and y is None:
                    continue
                if x is None or y is None:
                    missing += 1
                    continue
                worst = max(worst, abs(x - y))
                n += 1
        if ring and r.get('mg_ring'):
            j = r['mg_ring']
            py = [ring['t'][0], ring['t'][1], ring['s'], ring['rot_deg'], ring['t_minus_parallax'][0], ring['t_minus_parallax'][1]]
            for x, y in zip(py, j[:6]):
                worst = max(worst, abs(x - y))
                n += 1
        elif bool(ring) != bool(r.get('mg_ring')):
            missing += 1
    return worst, n, bad_elig, missing


def fixture(recs, path):
    """one row per marker: the stage's inputs and the Python results (LB and the photo's ring level)"""
    cols = ['rec', 'H', 'rpx', 'hour', 'shape', 'fx', 'fy', 'ox', 'oy', 'tilt_abs', 'cred', 'rel', 'cov', 'hand', 'soft',
            'eligible', 'tang', 'rad', 'tilt', 'ring_tx', 'ring_ty', 'ring_s', 'ring_rot', 'ring_px', 'ring_py']
    with open(path, 'w', newline='') as fh:
        w = csv.writer(fh)
        w.writerow(cols)
        for i, r in enumerate(recs):
            out, ring = M.measure(r, K_PARALLAX)
            rv = ([ring['t'][0], ring['t'][1], ring['s'], ring['rot_deg'], *ring['t_minus_parallax']] if ring else [''] * 6)
            for m in r['markers']:
                h = int(m['h'])
                if h not in out:
                    continue
                loo = m.get('loo')
                tilt = ''
                if m['shape'] != 'round' and m.get('ft') is not None:
                    tilt = m['ft'] + (math.degrees(loo[3]) if loo and loo[3] is not None else 0.0)
                lb = out[h].get('LB') or (None, None, None)
                w.writerow([i, ';'.join(repr(float(x)) for x in r['H']), repr(float(r['rpx'])), h, m['shape'], repr(m['fix'][0]),
                            repr(m['fix'][1]), repr(m['cO'][0]), repr(m['cO'][1]), '' if tilt == '' else repr(float(tilt)),
                            int(bool(m.get('cred'))), int(bool(m.get('rel'))), '' if m.get('cov') is None else repr(m['cov']),
                            int(bool(m.get('hand'))), (m.get('unc') or '').replace(',', '|'),
                            int(out[h]['status'] == el.ELIGIBLE),
                            *['' if x is None else repr(float(x)) for x in lb], *[repr(float(x)) if x != '' else '' for x in rv]])


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('records', nargs='+')
    ap.add_argument('--fixture')
    a = ap.parse_args(argv)
    recs = list(records(a.records))
    worst, n, bad, missing = compare(recs)
    print('MarkerGeometry parity: %d records, %d values, largest |Java - Python| %.2e; eligibility mismatches %d; '
          'present in one only %d' % (len(recs), n, worst, bad, missing))
    if a.fixture:
        fixture(recs, a.fixture)
    return 0 if worst <= TOL and bad == 0 and missing == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
