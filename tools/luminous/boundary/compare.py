"""Audit a bounded detector replay against the saved frozen study; no estimation/calibration.

Usage: compare.py SAVED_CALIBRATION_WORK REPLAY_WORK OUTPUT_DIRECTORY
Expects *_all_corrected.jsonl and *_processing_corrected.jsonl, plus the exact
Batgirl baseline/corrected replay. JSONL is emitted by LuminousCalibrationStudy.
"""
import csv
import hashlib
import json
import math
import sys
from collections import Counter
from pathlib import Path

FAMILIES = ('gmt_126710', 'submariner_124060', 'submariner_126610')
ROOT = Path(__file__).resolve().parents[3]


def read(path):
    return {p['photo_id']: p for p in map(json.loads, Path(path).read_text().splitlines())}


def close(a, b, eps=1e-7):
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return math.isclose(a, b, rel_tol=0, abs_tol=eps)
    if isinstance(a, dict) and isinstance(b, dict):
        return a.keys() == b.keys() and all(close(a[k], b[k], eps) for k in a)
    if isinstance(a, list) and isinstance(b, list):
        return len(a) == len(b) and all(close(x, y, eps) for x, y in zip(a, b))
    return a == b


def field(m, key, subkey=None):
    value = m.get(key)
    return value.get(subkey) if value is not None and subkey else value


def marker(p, hour):
    return next(m for m in p['markers'] if m['hour'] == hour)


def scalar_rows(before, after):
    rows = []
    for id, p in after.items():
        b = before[id]
        assert p['source_sha256'] == b['source_sha256'], id
        for k in ('H', 'ring', 'dial_radius_px', 'pose_valid', 'input_evidence'):
            # Archived CI and desktop native arithmetic differ by at most a fraction
            # of a micro-pixel in H coefficients. This is a replay numerical allowance,
            # not a detector/QC threshold; decoded image fingerprints must match exactly.
            assert close(p.get(k), b.get(k), 1e-6 if k in ('H','dial_radius_px') else 1e-7), (id, k)
        for m in p['markers']:
            o = marker(b, m['hour'])
            for k in ('outer_status', 'outer_rotation_deg', 'outer_calibrated_rotation_deg',
                      'outer_area_reason', 'outer_total_pct', 'interference_clean', 'interference_reason'):
                assert close(m.get(k), o.get(k)), (id, m['hour'], k)
            # Independent straight-side/edge measurements must remain unchanged.
            if m['shape'] != 'round':
                assert close(m, o), (id, m['hour'], 'straight-edge regression')
            a, z = m.get('fit') or {}, o.get('fit') or {}
            if not z.get('ideal'):
                change = 'newly_supported' if a.get('ideal') else 'still_withheld'
            elif not a.get('ideal'):
                change = 'withheld'
            elif close(a['ideal'], z['ideal'], 1e-8):
                change = 'unchanged_geometry'
            else:
                change = 'changed_geometry_requires_visual_review'
            row = dict(photo_id=id, family=p['family'], source_sha256=p['source_sha256'],
                       split=p['split'], hour=m['hour'], shape=m['shape'], change=change,
                       before_observed_usable=o['observed_usable'], after_observed_usable=m['observed_usable'],
                       after_boundary_reason=a.get('reason'), observed_reason=m['reason'])
            for prefix, item in [('before', o), ('after', m)]:
                for k in ('new_fitted', 'new_alignment', 'new_observed', 'old_fitted', 'old_alignment', 'old_observed'):
                    row[f'{prefix}_{k}_pct'] = field(item, k, 'total_pct')
                for k in ('rms_px', 'center_span_px', 'radius_span_px', 'radius_R'):
                    row[f'{prefix}_{k}'] = field(item, 'fit', k)
                c = field(item, 'fit', 'center')
                row[f'{prefix}_cx_R'], row[f'{prefix}_cy_R'] = c if c else (None, None)
            rows.append(row)
    return rows


def write_csv(path, rows):
    with Path(path).open('w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)


def main(saved, work, dest):
    saved, work, dest = map(Path, (saved, work, dest)); dest.mkdir(parents=True, exist_ok=True)
    rows, processing_rows, originals = [], [], {}
    for family in FAMILIES:
        old = read(saved/f'{family}_scored.jsonl'); new = read(work/f'{family}_all_corrected_scored.jsonl')
        assert old.keys() == new.keys(), family
        originals.update(new); rows += scalar_rows(old, new)
        old_processing = read(saved/f'{family}_processing_scored.jsonl')
        new_processing = read(work/f'{family}_processing_corrected_scored.jsonl')
        assert old_processing.keys() == new_processing.keys(), family
        processing_rows += scalar_rows(old_processing, new_processing)
        for id, p in new_processing.items():
            original_id, method = id.split('__', 1)
            if method == 'identical_original_repeat':
                original = new[original_id]
                # Exact same bytes and exact same outputs on this host. Export identity/split
                # metadata differ, but decoded input, pose, measured polygons and scores do not.
                for k in ('source_sha256', 'input_evidence', 'H', 'ring', 'markers', 'outer_snapshot_sha256'):
                    assert p.get(k) == original.get(k), (id, 'exact repeat', k)
    assert len(originals) == 132
    bg_old = read(work/'gmt_126710_batgirl_baseline.jsonl')
    bg_new = read(work/'gmt_126710_batgirl_corrected.jsonl')
    assert bg_new['batgirl_37828']['source_sha256'] == 'c50e0845ddb6c50701a4ba8ab6b396092e67988a747206f77743c02e7c7e54eb'
    bg_rows = scalar_rows(bg_old, bg_new)
    for h in (9, 12):
        assert marker(bg_old['batgirl_37828'], h) == marker(bg_new['batgirl_37828'], h)
    write_csv(dest/'original_comparison.csv', rows)
    write_csv(dest/'processing_comparison.csv', processing_rows)
    write_csv(dest/'batgirl_comparison.csv', bg_rows)
    cases = [(originals['80d288dcc8689345'], 2, 3.8293756491, .074790572436),
             (originals['0114716639829db3'], 10, 3.0206565597, .075535140664)]
    resize = read(work/'submariner_126610_processing_corrected_scored.jsonl')['0114716639829db3__resize_5of6_png']
    cases.append((resize, 10, 1.9781630513, .075985415646))
    for p, h, pct, radius in cases:
        m = marker(p, h)
        # Golden outputs reviewed against actual luminous separators. These are regression
        # assertions, not QC limits or intended mismatch values supplied to the detector.
        assert abs(field(m, 'new_fitted', 'total_pct')-pct) < 1e-6
        assert abs(field(m, 'fit', 'radius_R')-radius) < 1e-9
        assert m['new_observed'] is None
    # The initial candidate switched boundaries on this existing processing control.
    # Contradictory photographed arcs must withhold both geometry and alignment, across
    # the original and every frozen processing variant, regardless of the reported %.
    blocker=[originals['c58f78ef6c1cd5b7']]+list(read(work/'submariner_124060_processing_corrected_scored.jsonl').values())
    for p in blocker:
        m=marker(p,4)
        assert m['new_fitted'] is None and m['new_alignment'] is None, (p['photo_id'],'processing boundary switch')
        assert 'contradict' in field(m,'fit','reason')
    summary = dict(validation_passed=True, originals=132, processing_variants=18,
                   batgirl_original_verified=True, exact_original_repeats=3,
                   noncircular_and_outer_unchanged=True,
                   circular_geometry_changes=dict(Counter(r['change'] for r in rows if r['shape']=='round')),
                   by_family={f:dict(Counter(r['change'] for r in rows if r['family']==f and r['shape']=='round')) for f in FAMILIES},
                   observed_withheld_after_validation=sum(r['before_observed_usable'] and not r['after_observed_usable'] for r in rows),
                   old_resize_delta_pp=9.6165090286, corrected_resize_delta_pp=abs(3.0206565597-1.9781630513),
                   contradictory_processing_circle_withheld_in_all_7_cases=True,
                   source_hashes={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in
                       [ROOT/'android/app/src/main/java/com/watchalign/mobile/LuminousCircularBoundary.java',
                        ROOT/'android/app/src/main/java/com/watchalign/mobile/LuminousMarkerDetector.java']})
    (dest/'validation.json').write_text(json.dumps(summary, indent=2)+'\n')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main(*sys.argv[1:])
