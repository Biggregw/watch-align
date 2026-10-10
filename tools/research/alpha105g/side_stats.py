#!/usr/bin/env python3
"""Alpha105g: genuine edge-consistency statistics from Alpha96Calib per_photo.csv (with the baton / 12 side columns).

A marker's two opposite sides are fitted to the outermost bright-to-dark edge. When lighting darkens one side's
polished bevel, that side lands on the lume edge (about one rim width inside) and the centre moves by half a rim width.
A real displacement moves both sides together and keeps the width; the artefact changes the width. Per family:
  b<h>    baton width change (ccw + cw long-side offsets) / R
  t12     12 triangle sides-sum (left + right side offsets) / R
  rsize   round marker size vs the photo's median round size / R (a marker fitted on its lume circle reads small)
deviation = value - model median; limit = 3 x robust spread (1.4826 x median |deviation|), the existing 3-sigma policy
with a robust spread (the artefact photos must not set their own tolerance, as for twelve_sides_agreement).
Prints the limits, every genuine photo outside them (FLAG lines) and the genuine maxima of the matching position
measure (per-watch median, as the references are built) with and without the flagged photos."""
import csv
import statistics as st
import sys
from collections import defaultdict

K = 3.0
ROUNDS = (1, 2, 4, 5, 7, 8, 10, 11)


def fl(x):
    try:
        v = float(x)
        return v if v == v else None
    except (TypeError, ValueError):
        return None


def robust(vals):
    med = st.median(vals)
    mad = 1.4826 * st.median([abs(v - med) for v in vals])
    return med, mad


def watch_max(items):
    """items: (watch, value) -> max over watches of the per-watch median value."""
    g = defaultdict(list)
    for w, v in items:
        g[w].append(v)
    return max(st.median(v) for v in g.values()) if g else float('nan'), len(g)


def main(pp, manifest, model):
    man = {r['photo_id']: r for r in csv.DictReader(open(manifest))}
    rows = [r for r in csv.DictReader(open(pp)) if r.get('status') == 'accepted']
    hours = sorted({int(k[1:].split('_')[0]) for k in rows[0] if k.endswith('_side_ccw_px')}) if rows else []
    print(f'{model}: accepted genuine photos {len(rows)}')
    fams = {}
    for r in rows:
        R = fl(r['dial_radius_px'])
        wid = man.get(r['photo_id'], {}).get('physical_watch_id', r['photo_id'])
        for h in hours:
            a, b, t = fl(r.get(f'm{h}_side_ccw_px')), fl(r.get(f'm{h}_side_cw_px')), fl(r.get(f'm{h}_local_px'))
            if None not in (a, b, t) and r.get(f'm{h}_usable') == 'true':
                fams.setdefault(f'b{h}', []).append((r['photo_id'], wid, (a + b) / R, t / R, R))
        lt, rt, lat = fl(r.get('m12_side_left_px')), fl(r.get('m12_side_right_px')), fl(r.get('m12_local_tangential_px'))
        if None not in (lt, rt, lat) and r.get('m12_usable') == 'true':
            fams.setdefault('t12', []).append((r['photo_id'], wid, (lt + rt) / R, abs(lat) / R, R))
        sz = {h: fl(r.get(f'm{h}_radius_err_px')) for h in ROUNDS if r.get(f'm{h}_usable') == 'true'}
        sz = {h: v for h, v in sz.items() if v is not None}
        if len(sz) >= 3:
            med = st.median(sz.values())
            for h, v in sz.items():
                off = fl(r.get(f'm{h}_local_px'))
                fams.setdefault('rsize', []).append((f"{r['photo_id']}#{h}", wid, (v - med) / R, (off or 0) / R, R))
    for fam, items in fams.items():
        med, mad = robust([x[2] for x in items])
        lim = K * mad
        flagged = [x for x in items if abs(x[2] - med) > lim]
        devs = sorted(abs(x[2] - med) for x in items)
        q = lambda p: devs[min(len(devs) - 1, int(p * (len(devs) - 1) + 0.5))]
        all_max, nw = watch_max([(x[1], x[3]) for x in items])
        keep = [x for x in items if abs(x[2] - med) <= lim]
        kept_max, nk = watch_max([(x[1], x[3]) for x in keep])
        print(f'  {fam}: n {len(items)} median {100*med:+.3f}%R robust spread {100*mad:.3f}%R -> limit {100*lim:.3f}%R | |dev| p90 {100*q(.9):.3f} '
              f'p99 {100*q(.99):.3f} max {100*devs[-1]:.3f} | outside: {len(flagged)} photos ({100*len(flagged)/len(items):.1f}%)')
        print(f'      position genuine max (per-watch median): all {100*all_max:.3f}%R ({nw} watches) -> consistent only {100*kept_max:.3f}%R ({nk} watches)')
        for pid, wid, v, t, R in sorted(flagged, key=lambda x: -abs(x[2] - med))[:12]:
            print(f'      FLAG,{model},{fam},{pid},{wid},dev {100*(v-med):+.3f}%R,position {100*t:.3f}%R,R {R:.0f}')
        for pid, wid, v, t, R in flagged:
            print(f'FLAGALL,{model},{fam},{pid},{100*(v-med):+.4f}')


if __name__ == '__main__':
    main(*sys.argv[1:4])
