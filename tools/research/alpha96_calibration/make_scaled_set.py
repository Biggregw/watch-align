#!/usr/bin/env python3
"""RESEARCH ONLY. Build a resolution / resampling test set from local photos (offline).

For every photo in a manifest, writes lossless PNG copies at several scales, each with several
resampling variants: jitter 0 is a plain INTER_AREA resize; jitters 1..N first apply a tiny rotation
(<= 0.2 deg, about the image centre) and a 0-3 px crop offset at the original resolution, so the dial
lands on a different pixel grid. The physical geometry is identical across all copies, so:
  - spread across jitters at one scale  = photo/detector noise at that dial radius;
  - trend across scales (in R units)     = whether a value is geometry (constant) or noise (grows as R falls).
A global rotation cancels out of every Alpha96 field because each is measured relative to the fitted lattice.

Usage: make_scaled_set.py <manifest.csv> <root> <out_root> <out_manifest.csv> [scales] [n_jitter]
Images go to <out_root> (scratch space; never commit them).
"""
import csv
import os
import sys

import cv2

JITTER = [(0.0, 0, 0), (0.2, 1, 2), (-0.2, 2, 1), (0.1, 3, 3), (-0.1, 0, 3), (0.15, 3, 0)]


def variant(img, scale, j):
    rot, ox, oy = JITTER[j]
    if j:
        h, w = img.shape[:2]
        m = cv2.getRotationMatrix2D((w / 2.0, h / 2.0), rot, 1.0)
        img = cv2.warpAffine(img, m, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)
        img = img[oy:, ox:]
    if scale != 1.0:
        img = cv2.resize(img, (round(img.shape[1] * scale), round(img.shape[0] * scale)), interpolation=cv2.INTER_AREA)
    return img


def main(manifest, root, out_root, out_manifest, scales='1.0,0.85,0.7,0.55,0.45', n_jitter='5'):
    scales = [float(s) for s in scales.split(',')]
    rows = list(csv.DictReader(open(manifest)))
    cols = list(rows[0].keys()) + ['base_photo_id', 'scale', 'jitter']
    out = []
    for r in rows:
        img = cv2.imread(os.path.join(root, r['local_path']), cv2.IMREAD_COLOR)
        if img is None:
            print('unreadable', r['local_path']); continue
        for s in scales:
            for j in range(int(n_jitter)):
                rel = f"scaled/{r['photo_id']}_s{int(round(s * 100)):03d}_j{j}.png"
                os.makedirs(os.path.join(out_root, 'scaled'), exist_ok=True)
                cv2.imwrite(os.path.join(out_root, rel), variant(img, s, j))
                o = dict(r); o.update(photo_id=f"{r['photo_id']}_s{int(round(s * 100)):03d}_j{j}", local_path=rel,
                                      base_photo_id=r['photo_id'], scale=s, jitter=j)
                out.append(o)
    with open(out_manifest, 'w', newline='') as fh:
        w = csv.DictWriter(fh, fieldnames=cols); w.writeheader(); w.writerows(out)
    print(len(out), 'images')


if __name__ == '__main__':
    main(*sys.argv[1:])
