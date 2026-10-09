#!/usr/bin/env python3
"""CI harvest (harvest-ci.yml): turn the harvester's own output on the runner (accepted.csv + accepted_images/) into a
photo list and the runner image tree that harvest_report.py expects, print the photo list to the log (links and sha256
only) and a log-only contact sheet of the accepted photos. Images stay on the runner.

Usage: harvest_ci_prepare.py <harvest_run_dir> <lists_dir> <image_root>
"""
import base64
import csv
import glob
import hashlib
import os
import shutil
import sys

import cv2
import numpy as np


def main(run, lists, root):
    os.makedirs(lists, exist_ok=True)
    rows = [r for r in csv.DictReader(open(os.path.join(run, 'accepted.csv'), newline='')) if r.get('sha256')]
    files = {os.path.basename(p): p for p in glob.glob(os.path.join(run, 'files', 'accepted_images', '*', '*'))}
    keep, tiles = [], []
    for r in rows:
        src = files.get(r['file'])
        if not src or hashlib.sha256(open(src, 'rb').read()).hexdigest() != r['sha256']:
            continue
        s = r['sha256']; dst = os.path.join(root, 'images', s[:2], s + '.jpg')
        os.makedirs(os.path.dirname(dst), exist_ok=True); shutil.copyfile(src, dst); keep.append(r)
        if len(tiles) < 48:
            im = cv2.imread(src)
            if im is not None:
                k = 200 / max(im.shape[:2]); im = cv2.resize(im, (int(im.shape[1] * k), int(im.shape[0] * k)))
                t = np.full((225, 200, 3), 255, np.uint8); t[:im.shape[0], :im.shape[1]] = im
                cv2.putText(t, f"{r['reference']} {r['sku']}", (2, 220), cv2.FONT_HERSHEY_SIMPLEX, .4, (0, 0, 255), 1); tiles.append(t)
    with open(os.path.join(lists, 'ci_harvest.csv'), 'w', newline='') as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()) if rows else ['reference']); w.writeheader(); w.writerows(keep)
    print(f'accepted photos: {len(rows)}, on the runner and verified: {len(keep)}')
    print('=== PHOTO LIST BEGIN')
    print(open(os.path.join(lists, 'ci_harvest.csv')).read(), end='')
    print('=== PHOTO LIST END')
    if tiles:
        while len(tiles) % 8:
            tiles.append(np.full((225, 200, 3), 255, np.uint8))
        img = np.vstack([np.hstack(tiles[i:i + 8]) for i in range(0, len(tiles), 8)])
        print('=== CONTACT SHEET BASE64 JPEG BEGIN')
        print(base64.b64encode(cv2.imencode('.jpg', img, [cv2.IMWRITE_JPEG_QUALITY, 60])[1].tobytes()).decode())
        print('=== END')


if __name__ == '__main__':
    main(*sys.argv[1:4])
