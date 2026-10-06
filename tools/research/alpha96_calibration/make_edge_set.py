#!/usr/bin/env python3
"""RESEARCH ONLY. Edge-definition test set: shift every bright outline in or out by a known amount (offline).

Grey-level dilation by k px moves every bright-to-dark edge outward by about k px (lume, surrounds and
minute ticks alike); erosion moves it inward. Tick centres do not move, so the frozen pose is unaffected
to first order. The round markers' radius error then reports the outline shift directly, and the
response of the 12 triangle's offset shows how much a change in edge definition (lighting, surround
reflections, bloom) alone moves it.

Usage: make_edge_set.py <manifest.csv> <root> <out_root> <out_manifest.csv> [shifts_px]
"""
import csv
import os
import sys

import cv2


def shifted(img, k):
    if k == 0:
        return img
    ker = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * abs(k) + 1, 2 * abs(k) + 1))
    return cv2.dilate(img, ker) if k > 0 else cv2.erode(img, ker)


def main(manifest, root, out_root, out_manifest, shifts='-2,-1,0,1,2'):
    rows = list(csv.DictReader(open(manifest)))
    cols = list(rows[0].keys()) + ['base_photo_id', 'edge_shift_px']
    out = []
    os.makedirs(os.path.join(out_root, 'edge'), exist_ok=True)
    for r in rows:
        img = cv2.imread(os.path.join(root, r['local_path']), cv2.IMREAD_COLOR)
        if img is None:
            continue
        for k in (int(x) for x in shifts.split(',')):
            rel = f"edge/{r['photo_id']}_k{k:+d}.png"
            cv2.imwrite(os.path.join(out_root, rel), shifted(img, k))
            o = dict(r); o.update(photo_id=f"{r['photo_id']}_k{k:+d}", local_path=rel, base_photo_id=r['photo_id'], edge_shift_px=k)
            out.append(o)
    with open(out_manifest, 'w', newline='') as fh:
        w = csv.DictWriter(fh, fieldnames=cols); w.writeheader(); w.writerows(out)
    print(len(out), 'images')


if __name__ == '__main__':
    main(*sys.argv[1:])
