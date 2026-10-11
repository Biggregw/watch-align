#!/usr/bin/env python3
"""Research-only genuine shape calibration, held out by physical watch.

Input: per_marker.csv emitted by MarkerMismatchBatch, based on the app's frozen
pose and fitted outer boundaries. Calibrate the ENTIRE triangle and baton shape
against genuine training watches, and then count symmetric-difference area on
previously unseen genuine watches. Never promote these exploratory templates
into production without source and photo-repeatability review.

Usage: python calibrate_shapes.py per_marker.csv output_directory
Dependencies: shapely, numpy
"""
import csv
import hashlib
import math
import os
import statistics
import sys
from collections import defaultdict

import numpy as np
from shapely.geometry import Polygon


def vertices(s):
    try:
        return np.array([[float(x) for x in xy.split(':')] for xy in s.split(';')], dtype=float)
    except (TypeError, ValueError):
        return None


def overlap(expected, detected):
    a, b = Polygon(expected), Polygon(detected)
    if not a.is_valid or not b.is_valid or a.area <= 0 or b.area <= 0:
        return math.nan
    union = a.union(b).area
    return 100.0 * a.symmetric_difference(b).area / union


def percent(values, q):
    return float(np.percentile(values, q))


def main(source, destination):
    os.makedirs(destination, exist_ok=True)
    with open(source, newline='') as f:
        rows = list(csv.DictReader(f))

    by_marker = defaultdict(list)
    for r in rows:
        if r.get('pose_status') != 'accepted':
            continue
        if r['model_shape'] not in ('baton', 'triangle'):
            continue
        if r['confidence'] not in ('measured',
            'provisional: radial edges of 12 vary with polished-surround lighting'):
            continue
        a, b = vertices(r.get('expected_vertices')), vertices(r.get('actual_vertices'))
        if a is None or b is None or a.shape != b.shape or a.shape[1] != 2:
            continue
        by_marker[(r['model'], r['model_shape'], int(r['hour']))].append((r, a, b))

    report, deltas, details = [], [], []
    for (model, shape, hour), group in sorted(by_marker.items()):
        watches = sorted({r['physical_watch_id'] for r, a, b in group},
            key=lambda w: hashlib.sha256(('mask-v1:' + w).encode()).hexdigest())
        train = set(watches[:max(1, int(len(watches) * .6))])
        training_by_watch = defaultdict(list)
        holdout_by_watch = defaultdict(list)
        for r, expected, detected in group:
            wid = r['physical_watch_id']
            if wid in train:
                training_by_watch[wid].append(detected - expected)
            else:
                holdout_by_watch[wid].append((r, expected, detected))
        if not training_by_watch:
            continue
        delta = np.median(np.stack([
            np.median(np.stack(values), axis=0)
            for values in training_by_watch.values()
        ]), axis=0)
        deltas.append(dict(model=model, shape=shape, hour=hour,
            training_watches=len(training_by_watch),
            residual_vertices=';'.join(f'{x:.8f}:{y:.8f}' for x, y in delta)))
        before, after = [], []
        for wid, observed in holdout_by_watch.items():
            b = statistics.median(overlap(expected, detected)
                for r, expected, detected in observed)
            a = statistics.median(overlap(expected + delta, detected)
                for r, expected, detected in observed)
            if not math.isfinite(a) or not math.isfinite(b):
                continue
            before.append(b)
            after.append(a)
            details.append(dict(model=model, shape=shape, hour=hour,
                physical_watch_id=wid, old_pct=f'{b:.5f}', calibrated_pct=f'{a:.5f}',
                photo_count=len(observed)))
        if not after:
            continue
        vals = np.array(after)
        report.append(dict(model=model, shape=shape, hour=hour,
            train_watches=len(training_by_watch), heldout_watches=len(vals),
            original_median=f'{statistics.median(before):.3f}',
            calibrated_median=f'{statistics.median(after):.3f}',
            calibrated_p90=f'{percent(vals,90):.3f}',
            calibrated_p95=f'{percent(vals,95):.3f}',
            calibrated_p99=f'{percent(vals,99):.3f}',
            calibrated_max=f'{max(after):.3f}',
            above_4_percent=sum(x > 4.0 for x in after),
            above_5_percent=sum(x > 5.0 for x in after),
            above_6_percent=sum(x > 6.0 for x in after),
            caution='triangle polished-surround variation' if shape=='triangle'
                else ('small heldout sample' if len(vals)<20 else 'review source effects')))
    for name, values in [('heldout_stats.csv', report),
                          ('genuine_vertex_calibration.csv', deltas),
                          ('heldout_watch_scores.csv', details)]:
        if values:
            with open(os.path.join(destination,name),'w',newline='') as f:
                w=csv.DictWriter(f,fieldnames=list(values[0]))
                w.writeheader();w.writerows(values)
    print('Source photo rows:',len(rows),'genuine marker families:',len(by_marker))
    print('HELD-OUT RESULTS, each physical watch counted once:')
    for r in report:
        print(r['model'],r['shape'],r['hour'],'n',r['heldout_watches'],
            'median',r['calibrated_median'],'p95',r['calibrated_p95'],
            'above 4%',r['above_4_percent'],'above 5%',r['above_5_percent'])
    print('Research only: 4% is NOT an adopted QC threshold.')


if __name__ == '__main__':
    main(*sys.argv[1:3])
