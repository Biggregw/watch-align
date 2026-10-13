#!/usr/bin/env python3
"""REVIEW ONLY. Per marker, the filled-template mismatch of each QC photo next to the distribution of the SAME marker
(model, hour) on the genuine photos, from MarkerMismatchBatch CSVs. Only rows whose comparison was cleared
('measured') are counted. No tolerance is derived or applied; genuine counts are per photo and per physical watch.
Usage: compare.py OUT_DIR   (reads OUT_DIR/genuine_<model>.csv and OUT_DIR/qc_<model>.csv)"""
import csv, sys
from collections import defaultdict
from pathlib import Path
import numpy as np

out = Path(sys.argv[1])
for g in sorted(out.glob('genuine_*.csv')):
    model = g.stem[len('genuine_'):]
    rows = list(csv.DictReader(open(g)))
    by, watches, reasons = defaultdict(list), defaultdict(set), defaultdict(lambda: defaultdict(int))
    photos = {r['photo_id'] for r in rows}
    for r in rows:
        if r['pose_status'] != 'accepted':
            continue
        reasons[r['hour']][r['confidence'] if r['confidence'] == 'measured' else 'withheld: ' + r['confidence'][:40]] += 1
        if r['confidence'] == 'measured' and r['mismatch_pct']:
            by[r['hour']].append(float(r['mismatch_pct'])); watches[r['hour']].add(r['physical_watch_id'])
    print(f'=== GENUINE {model}: {len(photos)} photos, pose ok {len({r["photo_id"] for r in rows if r["pose_status"]=="accepted"})}')
    print('   hour shape  n(photos/watches)  median  p90  p95  max   | withheld reasons')
    shape = {r['hour']: r['model_shape'] for r in rows if r['model_shape']}
    for h in sorted(by, key=int):
        v = np.array(by[h]); wr = {k: c for k, c in reasons[h].items() if k != 'measured'}
        print(f'   {h:>4} {shape.get(h,""):6} {len(v):4}/{len(watches[h]):<4} {np.median(v):6.2f} {np.percentile(v,90):5.2f} {np.percentile(v,95):5.2f} {v.max():5.2f} | {dict(sorted(wr.items(), key=lambda kv:-kv[1])[:3])}')
    for h in sorted(set(reasons) - set(by), key=int):
        print(f'   {h:>4} {shape.get(h,""):6} none cleared | {dict(reasons[h])}')
    q = out / f'qc_{model}.csv'
    if not q.exists():
        continue
    print(f'=== QC {model} (each marker vs genuine of the same hour; rank = share of genuine readings below it)')
    for r in csv.DictReader(open(q)):
        if r['pose_status'] != 'accepted':
            print(f"   {r['photo_id']}: pose {r['pose_status']} {r['reason']}"); continue
        v = np.array(by.get(r['hour'], []))
        pct = r['mismatch_pct']
        if r['confidence'] == 'measured' and pct and len(v):
            x = float(pct)
            print(f"   {r['photo_id']:32} {r['hour']:>2} {r['model_shape']:6} {x:6.2f}%  genuine max {v.max():5.2f} p95 {np.percentile(v,95):5.2f}  rank {100*(v<x).mean():5.1f}%{'  ABOVE GENUINE MAX' if x>v.max() else ''}")
        else:
            print(f"   {r['photo_id']:32} {r['hour']:>2} {r['model_shape']:6} withheld: {r['confidence'][:70]}{(' (raw '+format(float(pct),'.2f')+'%)') if pct else ''}")
