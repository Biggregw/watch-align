#!/usr/bin/env python3
"""Alpha105g: genuine baton long-side statistics from Alpha96Calib per_photo.csv (with m<h>_side_ccw_px / _side_cw_px).
width change = ccw + cw (px, + = wider than the master); a real tangential shift moves both sides together (width
unchanged), an edge read on the wrong facet of the polished surround moves one side (width changes)."""
import csv
import statistics as st
import sys


def q(v, p):
    v = sorted(v)
    return v[min(len(v) - 1, int(p * (len(v) - 1) + 0.5))]


def main(pp, manifest, model):
    man = {r['photo_id']: r for r in csv.DictReader(open(manifest))}
    rows = [r for r in csv.DictReader(open(pp)) if r.get('status') == 'accepted']
    hours = sorted({int(k[1:].split('_')[0]) for k in rows[0] if k.endswith('_side_ccw_px')}) if rows else []
    print(f'{model}: accepted photos {len(rows)}')
    for h in hours:
        W, T, J = [], [], []
        for r in rows:
            try:
                R = float(r['dial_radius_px'])
                a, b = float(r[f'm{h}_side_ccw_px']), float(r[f'm{h}_side_cw_px'])
                t = float(r[f'm{h}_local_tangential_px'])
            except (ValueError, KeyError):
                continue
            w = 100 * (a + b) / R
            W.append(w); T.append(100 * t / R)
            J.append((abs(100 * t / R), w, 100 * (b - a) / 2 / R, r['photo_id'], man.get(r['photo_id'], {}).get('physical_watch_id', ''), R))
        if not W:
            continue
        med = st.median(W)
        dev = [abs(x - med) for x in W]
        print(f'  baton {h}: n {len(W)}  width change %R median {med:+.3f}  |dev| p50 {q(dev,.5):.3f} p90 {q(dev,.9):.3f} '
              f'p95 {q(dev,.95):.3f} p99 {q(dev,.99):.3f} max {max(dev):.3f}  | local tangential %R |t| p95 {q([abs(x) for x in T],.95):.3f} max {max(abs(x) for x in T):.3f}')
        J.sort(reverse=True)
        print('    largest |tangential| genuine photos: |t|%R, width change %R (dev from median), shift from sides %R, R, watch')
        for t, w, sh, pid, wid, R in J[:8]:
            print(f'      {t:.3f}  {w:+.3f} ({w-med:+.3f})  {sh:+.3f}  R {R:.0f}  {wid}')
        D = sorted(((abs(w - med), w - med, t, pid, wid, R) for t, w, sh, pid, wid, R in J), reverse=True)
        print('    largest width deviations: |dev|%R, dev, |t|%R, R, watch')
        for d, s, t, pid, wid, R in D[:8]:
            print(f'      {d:.3f}  {s:+.3f}  {t:.3f}  R {R:.0f}  {wid}')

    # 12 triangle: lateral position = (right - left) / 2; a real lateral shift keeps the two sides' sum (width)
    W, J = [], []
    for r in rows:
        try:
            R = float(r['dial_radius_px'])
            rt, bs, lf = float(r['m12_side_right_px']), float(r['m12_side_base_px']), float(r['m12_side_left_px'])
            lat = float(r['m12_local_tangential_px'])
        except (ValueError, KeyError):
            continue
        w = 100 * (rt + lf) / R
        W.append(w); J.append((abs(100 * lat / R), w, 100 * bs / R, man.get(r['photo_id'], {}).get('physical_watch_id', ''), R))
    if W:
        med = st.median(W)
        dev = [abs(x - med) for x in W]
        print(f'  triangle 12: n {len(W)}  sides-sum %R median {med:+.3f}  |dev| p50 {q(dev,.5):.3f} p90 {q(dev,.9):.3f} '
              f'p95 {q(dev,.95):.3f} p99 {q(dev,.99):.3f} max {max(dev):.3f}  base offset %R median {st.median(x[2] for x in J):+.3f}')
        J.sort(reverse=True)
        for t, w, bs, wid, R in J[:6]:
            print(f'      |lateral| {t:.3f}  sides-sum {w:+.3f} ({w-med:+.3f})  base {bs:+.3f}  R {R:.0f}  {wid}')


if __name__ == '__main__':
    main(*sys.argv[1:4])
