#!/usr/bin/env python3
"""RESEARCH ONLY. Summarise the M12Diag per-side triangle edge offsets by source; optional crop montage.

Inputs: M12Diag CSV (desktop harness), the manifest that was run (for group / source), optional dedup photos.csv.
Side offsets are distances of the production edge finder's detected edge from the ring-moved master side line,
+ = outside the master outline, in units of dial radius. The centroid shift they imply is recomputed from the
master triangle (line intersections) and compared with the production 12 local radial offset.

Usage: m12_sides.py <diag.csv> <manifest.csv> [--dedup photos.csv] [--crops DIR --montage OUT.jpg] [--out MD]
"""
import argparse
import base64
import csv
import math
import os
from collections import defaultdict
from statistics import median

A, B, H = 0.5925, 0.9025, 0.12375


def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def centroid_shift(dl, dr, db):
    P = [(0.0, -A), (H, -B), (-H, -B)]
    cx = sum(p[0] for p in P) / 3; cy = sum(p[1] for p in P) / 3
    lines = []
    for i, j, d in ((0, 1, dr), (1, 2, db), (2, 0, dl)):
        (x1, y1), (x2, y2) = P[i], P[j]; dx, dy = x2 - x1, y2 - y1; n = math.hypot(dx, dy); dx /= n; dy /= n
        nx, ny = dy, -dx
        if nx * ((x1 + x2) / 2 - cx) + ny * ((y1 + y2) / 2 - cy) < 0:
            nx, ny = -nx, -ny
        lines.append((x1 + d * nx, y1 + d * ny, dx, dy))
    def inter(a, b):
        x1, y1, dx1, dy1 = a; x2, y2, dx2, dy2 = b; den = dx1 * dy2 - dy1 * dx2
        t = ((x2 - x1) * dy2 - (y2 - y1) * dx2) / den; return x1 + t * dx1, y1 + t * dy1
    V = [inter(lines[2], lines[0]), inter(lines[0], lines[1]), inter(lines[1], lines[2])]
    gx = sum(v[0] for v in V) / 3; gy = sum(v[1] for v in V) / 3
    return -(gy - cy), gx - cx


def group_of(m):
    if m.get('group') != 'genuine_population':
        return m.get('group', '?')
    u = m.get('image_url', '')
    h = u.split('/')[2] if '//' in u else ''
    return 'SWE' if 'swisswatchexpo' in h else "Bob's" if 'bobswatches' in h else 'Phillips' if 'phillips' in h else 'other'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('diag'); ap.add_argument('manifest')
    ap.add_argument('--dedup'); ap.add_argument('--level', default='dial')
    ap.add_argument('--crops'); ap.add_argument('--montage'); ap.add_argument('--per-group', type=int, default=6)
    ap.add_argument('--out')
    a = ap.parse_args()
    meta = {r['photo_id']: r for r in csv.DictReader(open(a.manifest))}
    shared = set()
    if a.dedup:
        shared = {r['photo_id'] for r in csv.DictReader(open(a.dedup)) if r[f'shared_{a.level}'] == '1'}
    rows = []
    for r in csv.DictReader(open(a.diag)):
        if r['status'] != 'accepted' or r['photo_id'] in shared:
            continue
        m = meta.get(r['photo_id'], {})
        d = {k: fl(r[k]) for k in r if k not in ('photo_id', 'status', 'm12_usable')}
        d.update(photo=r['photo_id'], grp=group_of(m), watch=m.get('physical_watch_id', ''), R=fl(r['R']))
        if d['side_left_R'] is not None and d['side_right_R'] is not None and d['side_base_R'] is not None:
            d['pred_rad'], d['pred_tan'] = centroid_shift(d['side_left_R'], d['side_right_R'], d['side_base_R'])
            d['sides_mean'] = (d['side_left_R'] + d['side_right_R']) / 2
            d['sides_minus_base'] = d['sides_mean'] - d['side_base_R']
        if d.get('m12_local_radial_px') is not None and d['R']:
            d['prod_rad'] = d['m12_local_radial_px'] / d['R']
        rows.append(d)
    by = defaultdict(list)
    for d in rows:
        by[d['grp']].append(d)
    keys = [('R', 'dial R px', 0), ('side_left_R', 'left side edge R', 4), ('side_right_R', 'right side edge R', 4),
            ('side_base_R', 'base edge R', 4), ('sides_minus_base', 'long sides minus base R', 4), ('round_edge_R', 'round outline edge R', 4),
            ('lume_minus_dial', 'lume - dial grey', 0), ('surround_minus_dial', 'surround band - dial grey', 0),
            ('pred_rad', '12 radial implied by sides R', 4), ('prod_rad', '12 radial, production R', 4)]
    grps = [g for g in ("Bob's", 'Phillips', 'SWE', 'other', 'gen_candidate', 'rl_control') if by.get(g)]
    def md(ds, k, n):
        v = [d[k] for d in ds if d.get(k) is not None]
        return f'{median(v):+.{n}f}' if v else '–'
    L = ['# 12 triangle: where the production edge finder puts each side (research only)', '',
         'Offsets of the detected edge from the ring-moved Alpha92 master side, + = outside the master outline (units of R).', '',
         '| Quantity | ' + ' | '.join(f'{g} (n={len(by[g])})' for g in grps) + ' |', '|---|' + '---:|' * len(grps)]
    for k, lab, n in keys:
        L.append(f'| {lab} | ' + ' | '.join(md(by[g], k, n) for g in grps) + ' |')
    both = [d for d in rows if d.get('pred_rad') is not None and d.get('prod_rad') is not None]
    if both:
        err = sorted(abs(d['pred_rad'] - d['prod_rad']) for d in both)
        L += ['', f'Implied vs production 12 radial: median |difference| {err[len(err) // 2]:.4f} R over {len(both)} photos.']
    L += ['']
    text = '\n'.join(L) + '\n'
    if a.out:
        open(a.out, 'w').write(text)
    print(text)
    if a.crops and a.montage:
        from PIL import Image
        tiles = []
        for g in ("Bob's", 'SWE', 'Phillips'):
            ds = sorted([d for d in by.get(g, []) if os.path.exists(os.path.join(a.crops, d['photo'] + '.png'))], key=lambda d: d['photo'])
            seen = set(); pick = []
            for d in ds:
                if d['watch'] not in seen:
                    seen.add(d['watch']); pick.append(d)
            tiles.append((g, pick[:a.per_group]))
        w, h = 160, 200
        im = Image.new('L', (w * a.per_group, h * len(tiles)), 0)
        for row, (g, pick) in enumerate(tiles):
            for col, d in enumerate(pick):
                im.paste(Image.open(os.path.join(a.crops, d['photo'] + '.png')).convert('L'), (col * w, row * h))
        im.save(a.montage, quality=85)
        print('MONTAGE rows:', ', '.join(f"{g}: {' '.join(d['photo'] for d in p)}" for g, p in tiles))
        print('MONTAGE_JPEG_BASE64_BEGIN'); print(base64.b64encode(open(a.montage, 'rb').read()).decode()); print('MONTAGE_JPEG_BASE64_END')


if __name__ == '__main__':
    main()
