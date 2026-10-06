#!/usr/bin/env python3
"""RESEARCH ONLY. Parity: Java Alpha97TwelveProto CSV vs the same angles + lateral readout computed in Python from the
Alpha96 runner CSV, m12_nominal.properties and m12_genuine_reference.csv. Exits non-zero on any mismatch.
Usage: check_m12_angles_parity.py <proto.csv> <runner.csv> <properties> <reference.csv>"""
import csv
import math
import sys


def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def main(proto, runner, props, ref, tol=2e-4):
    nom = {k.strip(): float(v) for k, v in (l.split('=', 1) for l in open(props) if '=' in l and not l.startswith('#'))}
    R_ = list(csv.DictReader(open(ref)))
    lat = [float(r['lateral_R']) for r in R_]; cen = [float(r['centreline_deg']) for r in R_]; sid = [float(r['sides_deg']) for r in R_]
    run = {r['photo_id']: r for r in csv.DictReader(open(runner))}
    bad = n = 0
    for p in csv.DictReader(open(proto)):
        r = run.get(p['photo_id'])
        if r is None or p['status'] != 'accepted':
            continue
        R = fl(r['dial_radius_px']); rad, tan = fl(r['m12_local_radial_px']), fl(r['m12_local_tangential_px'])
        usable = r['m12_usable'] == 'true' and rad is not None
        if (p['usable'] == 'true') != usable:
            print('usable mismatch', p['photo_id']); bad += 1; continue
        n += 1
        if not usable:
            continue
        c = fl(r['m12_rotation_deg']) - nom['rotation_deg']
        L = fl(r['m12_left_side_err_deg']) - nom['left_side_deg']; Rt = fl(r['m12_right_side_err_deg']) - nom['right_side_deg']
        s = max(abs(L), abs(Rt)); lp = tan - nom['tangential_R'] * R
        exp = {'centreline_deg': c, 'left_side_deg': L, 'right_side_deg': Rt, 'sides_deg': s, 'lateral_px': lp, 'lateral_R': lp / R,
               'radial_px_note': rad - nom['radial_R'] * R}
        for k, v in exp.items():
            if abs(fl(p[k]) - v) > tol:
                print('mismatch', p['photo_id'], k, p[k], round(v, 5)); bad += 1
        cnt = {'at_least_centreline': sum(1 for x in cen if x >= abs(c) - 1e-12), 'at_least_sides': sum(1 for x in sid if x >= s - 1e-12),
               'at_least_lateral': sum(1 for x in lat if x >= abs(lp / R) - 1e-12)}
        for k, v in cnt.items():
            if int(p[k]) != v:
                print('count mismatch', p['photo_id'], k, p[k], v); bad += 1
    print(f'parity: {n} photos checked, {bad} mismatches')
    sys.exit(1 if bad or not n else 0)


if __name__ == '__main__':
    main(*sys.argv[1:5])
