"""Reproduce the face-on-v2 validation tables from committed numbers only.

    python -m tools.image_archive.analyse_validation docs/image-archive/face-on-evidence.jsonl

The evidence file holds what the production analysis measured on each photograph
(no image bytes): reviewed labels, dial pose, photo metrics, per-marker numbers.
Prints: agreement with the reviewed labels, the pose sensitivity of genuine
measurements, the leave-one-watch-out envelope check and the historical accounting.
"""
from __future__ import annotations

import argparse
import json
import math
import re
import statistics as st
from collections import Counter, defaultdict

from . import faceon
from .faceon import evaluate
from .screening import REQUIRED_CHECKS

# strict genuine limits, docs/CALIBRATION_BASELINE_2026-10-20.md section 1 (ClearlyOff.Limits.genuine())
STRICT = {'round_pos': 0.671, 'baton_pos': 0.752, 'baton_tilt': 1.096, 'tri_side': 0.437, 'tri_tilt': 1.174}
SHAPES = {'gmt_126710': {12: 't', 3: 'b', 6: 'b', 9: 'b'}, 'submariner_126610': {12: 't', 6: 'b', 9: 'b'},
          'submariner_124060': {12: 't', 3: 'b', 6: 'b', 9: 'b'}}
HIDDEN = 'of its outline is visible, the rest is reconstructed'   # MetalFit text, emitted only below MIN_COVER


def prepare(e):
    if 'partly_hidden' not in e:
        e['partly_hidden'] = [int(h) for h, t in (e.get('soft') or {}).items() if HIDDEN in str(t)]
    e['withheld'] = {int(k): v for k, v in (e.get('withheld') or {}).items()}
    return e


def decide(e):
    if e.get('error'):
        return 'inconclusive', ['inconclusive_' + e['error'].split(':')[0]]
    prepare(e)
    r = []
    if min(e.get('orig_w') or 0, e.get('orig_h') or 0) < 2 * faceon.MIN_DIAL_RADIUS_PX:
        r.append('fail_original_resolution')
    if not e.get('pose_valid'):
        r.append('inconclusive_dial_not_registered')
    checks, markers = evaluate(e)
    for k in REQUIRED_CHECKS:
        if checks.get(k) is not True:
            r.append(('fail_' if checks.get(k) is False else 'inconclusive_') + k)
    for h, v in markers.items():
        if v is not True:
            r.append(('fail_marker_' if v is False else 'inconclusive_marker_') + h)
    if not markers:
        r.append('inconclusive_markers_not_measured')
    return ('fail' if any(x.startswith('fail') for x in r) else 'inconclusive' if r else 'pass'), r


ORDER = ('fail_original_resolution', 'fail_face_on_ellipse', 'fail_marker', 'fail_landmarks', 'fail_complete_dial',
         'fail_complete_minute_track', 'fail_sharp_boundaries', 'fail_exposure', 'fail_glare', 'inconclusive_missing',
         'inconclusive_sha256_mismatch', 'inconclusive_unsupported_layout', 'inconclusive_dial_not_registered',
         'inconclusive_face_on_ellipse', 'inconclusive_face_on_rehaut_vertical', 'inconclusive_face_on_rehaut_horizontal',
         'inconclusive_sharp_boundaries', 'inconclusive_exposure', 'inconclusive_glare', 'inconclusive_marker')


def primary(status, reasons):
    if status == 'pass':
        return 'pass'
    rr = {re.sub(r'_marker_\d+', '_marker', x) for x in reasons}
    return next((k for k in ORDER if k in rr), sorted(rr)[0])


def over_strict(model, hour, m):
    side, inout, tilt, pos = m
    s = SHAPES[model].get(hour, 'r')
    if s == 'r':
        return pos is not None and pos > STRICT['round_pos']
    if s == 'b':
        return (pos is not None and pos > STRICT['baton_pos']) or (tilt is not None and abs(tilt) > STRICT['baton_tilt'])
    return (side is not None and abs(side) > STRICT['tri_side']) or (tilt is not None and abs(tilt) > STRICT['tri_tilt'])


def spearman(x, y):
    def ranks(v):
        order = sorted(range(len(v)), key=v.__getitem__)
        r = [0.0] * len(v)
        for i, j in enumerate(order):
            r[j] = i
        return r
    rx, ry = ranks(x), ranks(y)
    mx, my = st.mean(rx), st.mean(ry)
    return sum((a - mx) * (b - my) for a, b in zip(rx, ry)) / math.sqrt(
        sum((a - mx) ** 2 for a in rx) * sum((b - my) ** 2 for b in ry))


def main(path):
    E = [json.loads(l) for l in open(path)]
    for e in E:
        e['_status'], e['_reasons'] = decide(e)
        e['_primary'] = primary(e['_status'], e['_reasons'])
    rev = [e for e in E if e['set'].startswith('reviewed')]
    print('# Agreement with the reviewed labels (offline contact-sheet review)')
    groups = defaultdict(Counter)
    for e in rev:
        groups['near_frontal=%s eligible=%s' % (e['near_frontal'], e['eligible'] or '-')][e['_primary']] += 1
    for g, c in sorted(groups.items()):
        print('%-34s %4d  %s' % (g, sum(c.values()), dict(c.most_common())))

    print('\n# Pose sensitivity: reviewed near-frontal genuine photos, dial radius >= 200 px')
    good = [e for e in rev if not e.get('error') and e['near_frontal'] == 'yes' and (e.get('rpx') or 0) >= 200]
    for model in SHAPES:
        g = [e for e in good if e['model'] == model]
        if not g:
            continue
        for lo, hi in ((0, 4), (4, 8), (8, 12), (12, 20)):
            b = [e for e in g if lo <= e['tilt_deg'] < hi]
            if not b:
                continue
            n = sum(1 for e in b for h in e['m'] if int(h) in e['credible'])
            o = sum(over_strict(model, int(h), m) for e in b for h, m in e['m'].items() if int(h) in e['credible'])
            p = sum(any(over_strict(model, int(h), m) for h, m in e['m'].items() if int(h) in e['credible']) for e in b)
            w = len({e.get('physical_watch_id') or e['photo_id'] for e in b})
            print('%-18s tilt %2d-%2d deg: photos %3d (watches %3d)  markers outside strict genuine %3d/%4d  photos with any %3d'
                  % (model, lo, hi, len(b), w, o, n, p))
        xs, ys = [], []
        for e in g:
            v = [m[3] for h, m in e['m'].items() if SHAPES[model].get(int(h), 'r') == 'r' and int(h) in e['credible'] and m[3] is not None]
            if v:
                xs.append(e['tilt_deg'])
                ys.append(st.median(v))
        print('%-18s Spearman(tilt, median round-marker position error) = %.2f over %d photos' % (model, spearman(xs, ys), len(xs)))

    print('\n# Leave-one-watch-out envelope (reviewed near-frontal photos with bytes)')
    pos = [e for e in rev if not e.get('error') and e['near_frontal'] == 'yes']
    base = dict(faceon.ENVELOPE)

    def envelope(train):
        ok = [e for e in train if (e.get('rpx') or 0) >= 200 and e.get('pose_valid') and e['tilt_deg'] < 10.611]
        col = lambda k: [e[k] for e in ok]
        d = dict(base, tilt_deg_pass=max(col('tilt_deg')), edge_ellipse_ratio_min=min(col('edge_ellipse_ratio')),
                 v_gradient=(min(col('v_gradient')), max(col('v_gradient'))),
                 h_gradient=(min(col('h_gradient')), max(col('h_gradient'))), sharpness_min=min(col('sharpness')),
                 crushed_max=max(col('crushed_fraction')), blown_max=max(col('blown_fraction')),
                 glare_max=max(col('glare_fraction')))
        return d
    watches = defaultdict(list)
    for e in pos:
        watches[e.get('physical_watch_id') or e['photo_id']].append(e)
    held = Counter()
    try:
        for w, es in watches.items():
            faceon.ENVELOPE.clear()
            faceon.ENVELOPE.update(envelope([e for e in pos if (e.get('physical_watch_id') or e['photo_id']) != w]))
            for e in es:
                held[decide(e)[0]] += 1
    finally:
        faceon.ENVELOPE.clear()
        faceon.ENVELOPE.update(base)
    print('in-sample %s; held out %s; %d photos / %d watches' % (
        dict(Counter(e['_status'] for e in pos)), dict(held), len(pos), len(watches)))

    print('\n# Historical originals (calibrator artifact 11300604104 and committed public originals)')
    hist = {}
    for e in E:
        if 'historical' in e['set'] or e['set'] == 'committed':
            hist.setdefault(e['sha256'], e)
    print('unique originals %d: %s' % (len(hist), dict(Counter(e['_status'] for e in hist.values()))))
    print('  ', dict(Counter(e['_primary'] for e in hist.values()).most_common()))
    return E


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('evidence')
    main(ap.parse_args().evidence)
