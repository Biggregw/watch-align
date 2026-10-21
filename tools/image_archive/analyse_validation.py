"""Reproduce the face-on-v3 validation tables from committed numbers only.

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
    # per-marker arrays [side, in/out, tilt, position, coverage, edge RMS, inconsistent share, hand confirmed]
    m = e.get('m') or {}
    if 'cover' not in e and any(len(v) > 4 for v in m.values()):
        e['cover'] = {int(h): v[4] for h, v in m.items() if len(v) > 4}
    if 'hand_confirmed' not in e and any(len(v) > 7 for v in m.values()):
        e['hand_confirmed'] = [int(h) for h, v in m.items() if len(v) > 7 and v[7] and int(h) in (e.get('credible') or [])]
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
    side, inout, tilt, pos = m[:4]
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


def unique(E):
    """one record per photograph: a record with evidence beats one whose bytes were not fetched"""
    best = {}
    for e in E:
        if e['sha256'] not in best or (best[e['sha256']].get('error') and not e.get('error')):
            best[e['sha256']] = e
    return list(best.values())


def watch_ids():
    """sha256 -> declared physical-watch ids from every repository catalogue (aliases are not merged)"""
    from pathlib import Path
    from .inventory import catalogue_rows
    out = defaultdict(set)
    for _, r in catalogue_rows(Path(__file__).resolve().parents[2]):
        if r.get('physical_watch_id'):
            out[(r.get('sha256') or '').lower()].add(r['physical_watch_id'])
    return out


def marker_rule(name):
    """evaluate() with the marker standard swapped (photo-level checks unchanged) for the rule comparison"""
    def ev(s):
        checks, markers = faceon.evaluate(s)
        if name == 'face-on-v3':
            return checks, markers
        req, cred, rel = s.get('required') or [], set(s.get('credible') or []), set(s.get('reliable') or [])
        hidden, w = set(s.get('partly_hidden') or []), s.get('withheld') or {}
        keep = cred if name == 'measured (soft warnings allowed)' else rel
        return checks, {str(h): False if h in hidden else True if h in keep else False if (h in cred or h in w) else None
                        for h in req}
    return ev


def by_family(E, ids):
    photos, watches = Counter(), defaultdict(set)
    for e in E:
        photos[e['model']] += 1
        watches[e['model']].add(min(ids.get(e['sha256']) or {e.get('physical_watch_id') or 'photo:' + e['sha256'][:12]}))
    return photos, {k: len(v) for k, v in watches.items()}


def main(path):
    global evaluate
    E = unique([json.loads(l) for l in open(path)])
    for e in E:
        e['_status'], e['_reasons'] = decide(e)
        e['_primary'] = primary(e['_status'], e['_reasons'])
    ids = watch_ids()
    analysed = [e for e in E if not e.get('error')]
    print('# %d unique photographs, %d with exact original bytes analysed' % (len(E), len(analysed)))

    print('\n# Admitted at the archive 250 px floor, by marker standard (photo-level checks identical)')
    base = evaluate
    try:
        for name in ('measured (soft warnings allowed)', 'every marker reliable (app genuine standard)', 'face-on-v3'):
            evaluate = marker_rule(name)
            ok = [e for e in analysed if decide(e)[0] == 'pass']
            p, w = by_family(ok, ids)
            print('%-46s photos %3d %s; watches %3d %s' % (name, len(ok), dict(p), sum(w.values()), w))
    finally:
        evaluate = base

    print('\n# face-on-v3 outcomes')
    rev = [e for e in E if e['set'].startswith('reviewed') or e['set'] == 'committed']
    groups = defaultdict(Counter)
    for e in rev:
        if e.get('near_frontal'):
            groups['reviewed near_frontal=%s eligible=%s' % (e['near_frontal'], e['eligible'] or '-')][e['_primary']] += 1
    for e in E:
        if e['set'] == 'historical' or 'historical' in e['set']:
            groups['historical artifact'][e['_primary']] += 1
        if e['set'] == 'pilot':
            groups['recovery pilot'][e['_primary']] += 1
    for g, c in sorted(groups.items()):
        print('%-40s %4d  %s' % (g, sum(c.values()), dict(c.most_common())))

    print('\n# Pose sensitivity (reviewed near-frontal genuine, dial radius >= 200 px)')
    good = [e for e in rev if not e.get('error') and e.get('near_frontal') == 'yes' and (e.get('rpx') or 0) >= 200]
    for model in SHAPES:
        g = [e for e in good if e['model'] == model]
        for lo, hi in ((0, 4), (4, 8), (8, 12), (12, 20)):
            b = [e for e in g if lo <= e['tilt_deg'] < hi]
            if b:
                n = sum(1 for e in b for h in e['m'] if int(h) in e['credible'])
                o = sum(over_strict(model, int(h), m) for e in b for h, m in e['m'].items() if int(h) in e['credible'])
                print('%-18s tilt %2d-%2d deg: photos %3d  markers outside strict genuine %3d/%4d' % (model, lo, hi, len(b), o, n))

    print('\n# Marker evidence (reviewed near-frontal genuine, radius >= 250 px, tilt <= 10.6 deg): round-marker position')
    G = [prepare(e) for e in good if (e.get('rpx') or 0) >= 250 and e['tilt_deg'] <= 10.6]
    bands = defaultdict(list)
    for e in G:
        soft = {int(k): v for k, v in (e.get('soft') or {}).items()}
        for h, m in e['m'].items():
            h = int(h)
            if h not in e['credible'] or SHAPES[e['model']].get(h, 'r') != 'r' or m[3] is None or len(m) < 8:
                continue
            warn = '+'.join(sorted(faceon.soft_classes(soft.get(h)))) if h not in e['reliable'] else 'reliable'
            cov = m[4] if m[4] is not None else 0
            band = '>=0.99' if cov >= 0.99 else '0.90-0.99' if cov >= 0.9 else '0.80-0.90' if cov >= 0.8 else '<0.80'
            bands[(warn, band)].append(m[3])
            bands[(warn, 'all')].append(m[3])
            bands[('hand' if m[7] else 'no hand', band)].append(m[3])
    ref = bands[('reliable', '>=0.99')]
    for k in sorted(bands):
        v = sorted(bands[k])
        if len(v) >= 5:
            print('%-32s n %4d median %.3f p95 %.3f  vs reliable fully seen: p=%.2g' % (
                ' / '.join(k), len(v), st.median(v), v[int(.95 * len(v))], mann_whitney(v, ref)))
    return E


def mann_whitney(a, b):
    """two-sided p (normal approximation)"""
    if a is b or not a or not b:
        return 1.0
    pooled = sorted([(x, 0) for x in a] + [(x, 1) for x in b])
    r = sum(i + 1 for i, (_, g) in enumerate(pooled) if g == 0)
    n1, n2 = len(a), len(b)
    z = (r - n1 * (n1 + 1) / 2 - n1 * n2 / 2) / math.sqrt(n1 * n2 * (n1 + n2 + 1) / 12)
    return math.erfc(abs(z) / math.sqrt(2))


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('evidence')
    main(ap.parse_args().evidence)
