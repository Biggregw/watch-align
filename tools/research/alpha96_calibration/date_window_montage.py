#!/usr/bin/env python3
"""RESEARCH ONLY. Small labelled window-only montages of DateCrop crops, printed to stdout as base64 JPEG blocks, so the
date shown in each photo can be labelled by hand (no digit recognition yet). Each tile is the window region only
(about 0.5 x 0.3 R around the measured / expected window), grey, 96 x 60 px, with its index; an index table maps
indices to photo ids. Nothing is written to the repository.

Usage: date_window_montage.py <crops.csv> <crop_dir> <date_window.csv> <tmp_dir> [per_sheet]
"""
import base64
import csv
import os
import sys

import cv2
import numpy as np


def main(crops_csv, crop_dir, dw_csv, tmp, per_sheet='48'):
    per = int(per_sheet)
    dw = {r['photo_id']: r for r in csv.DictReader(open(dw_csv))}
    rows = [r for r in csv.DictReader(open(crops_csv)) if r['status'] == 'accepted']
    tiles = []
    for i, r in enumerate(rows):
        g = cv2.imread(os.path.join(crop_dir, r['crop']), cv2.IMREAD_GRAYSCALE)
        if g is None:
            continue
        x0, y0, st = float(r['x0']), float(r['y0']), float(r['step'])
        d = dw.get(r['photo_id'], {})
        cx = float(d['window_cx']) if d.get('window_cx') else 0.64
        cy = float(d['window_cy']) if d.get('window_cy') else 0.0
        px, py = int((cx - x0) / st), int((cy - y0) / st)
        hw, hh = int(0.26 / st), int(0.16 / st)
        tile = g[max(0, py - hh):py + hh, max(0, px - hw):px + hw]
        tile = cv2.resize(tile, (96, 60))
        cv2.putText(tile, str(i), (1, 9), cv2.FONT_HERSHEY_PLAIN, 0.7, 255, 1)
        tiles.append((i, r['photo_id'], tile))
    print('MONTAGE_INDEX ' + ' '.join(f'{i}={pid}' for i, pid, _ in tiles))
    for s in range(0, len(tiles), per):
        chunk = [t for _, _, t in tiles[s:s + per]]
        while len(chunk) % 8:
            chunk.append(np.zeros((60, 96), np.uint8))
        sheet = np.vstack([np.hstack(chunk[k:k + 8]) for k in range(0, len(chunk), 8)])
        path = os.path.join(tmp, f'date_montage_{s // per}.jpg')
        cv2.imwrite(path, sheet, [cv2.IMWRITE_JPEG_QUALITY, 85])
        print(f'MONTAGE_{s // per}_BEGIN'); print(base64.b64encode(open(path, 'rb').read()).decode()); print(f'MONTAGE_{s // per}_END')


if __name__ == '__main__':
    main(*sys.argv[1:])
