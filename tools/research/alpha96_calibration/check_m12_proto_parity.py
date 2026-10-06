#!/usr/bin/env python3
"""RESEARCH ONLY. Parity check: the Java harness prototype (Alpha97TriProto CSV) against the same re-centring
computed in Python from the Alpha96 runner CSV and m12_nominal.properties. Exits non-zero on any mismatch."""
import csv
import math
import sys


def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def main(proto_csv, runner_csv, props, tol=2e-4):
    nom = {}
    for line in open(props):
        if '=' in line and not line.startswith('#'):
            k, v = line.split('=', 1); nom[k.strip()] = float(v)
    run = {r['photo_id']: r for r in csv.DictReader(open(runner_csv))}
    bad = n = 0
    for p in csv.DictReader(open(proto_csv)):
        r = run.get(p['photo_id'])
        if r is None or p['status'] != 'accepted':
            continue
        R = fl(r['dial_radius_px'])
        rad, tan = fl(r['m12_local_radial_px']), fl(r['m12_local_tangential_px'])
        want_usable = r['m12_usable'] == 'true' and rad is not None
        if (p['ref_usable'] == 'true') != want_usable:
            print('usable mismatch', p['photo_id']); bad += 1; continue
        if not want_usable:
            n += 1; continue
        rr, tt = rad - nom['radial_R'] * R, tan - nom['tangential_R'] * R
        exp = {'ref_right_px': tt, 'ref_down_px': -rr, 'ref_offset_px': math.hypot(rr, tt), 'ref_offset_R': math.hypot(rr, tt) / R,
               'ref_rotation_deg': fl(r['m12_rotation_deg']) - nom['rotation_deg'],
               'ref_left_side_deg': fl(r['m12_left_side_err_deg']) - nom['left_side_deg'],
               'ref_right_side_deg': fl(r['m12_right_side_err_deg']) - nom['right_side_deg'],
               'ref_base_tilt_deg': fl(r['m12_base_tilt_deg']) - nom['base_tilt_deg']}
        for k, v in exp.items():
            if abs(fl(p[k]) - v) > tol:
                print('mismatch', p['photo_id'], k, p[k], round(v, 5)); bad += 1
        n += 1
    print(f'parity: {n} photos checked, {bad} mismatches')
    sys.exit(1 if bad or not n else 0)


if __name__ == '__main__':
    main(*sys.argv[1:4])
