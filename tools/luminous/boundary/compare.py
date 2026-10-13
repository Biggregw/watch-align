"""Audit a bounded detector replay against the saved frozen study; no estimation/calibration.

Usage: compare.py SAVED_CALIBRATION_WORK REPLAY_WORK OUTPUT_DIRECTORY
Expects *_all_corrected.jsonl and *_processing_corrected.jsonl, plus the exact
Batgirl baseline/corrected replay. JSONL is emitted by LuminousCalibrationStudy.
"""
import csv
import json
import sys
from collections import Counter
from pathlib import Path
from checks import (BATGIRL_FROZEN, BATGIRL_SHA, CONTROLS, EVIDENCE_PATHS, FAMILIES,
                    FROZEN_PATHS, REVIEWED, ROOT, SAFE_BOUNDARIES, SOURCE_PATHS,
                    check_control, check_frozen_cases, check_reviewed, check_safe_boundary,
                    close, digest, hashes, marker, read, require)


def field(m, key, subkey=None):
    value = m.get(key)
    return value.get(subkey) if value is not None and subkey else value


def scalar_rows(before, after):
    rows = []
    for id, p in after.items():
        b = before[id]
        require(p['source_sha256'] == b['source_sha256'], f'{id}: original hash changed')
        require([m['hour'] for m in p['markers']] == [m['hour'] for m in b['markers']],
                f'{id}: marker coverage/order changed')
        for k in ('H', 'ring', 'dial_radius_px', 'pose_valid', 'input_evidence'):
            # Archived CI and desktop native arithmetic differ by at most a fraction
            # of a micro-pixel in H coefficients. This is a replay numerical allowance,
            # not a detector/QC threshold; decoded image fingerprints must match exactly.
            require(close(p.get(k), b.get(k), 1e-6 if k in ('H','dial_radius_px') else 1e-7), f'{id}: {k} changed')
        for m in p['markers']:
            o = marker(b, m['hour'])
            for k in ('outer_status', 'outer_rotation_deg', 'outer_calibrated_rotation_deg',
                      'outer_area_reason', 'outer_total_pct', 'interference_clean', 'interference_reason'):
                require(close(m.get(k), o.get(k)), f'{id}/{m["hour"]}: {k} changed')
            # Independent straight-side/edge measurements must remain unchanged.
            if m['shape'] != 'round':
                require(close(m, o), f'{id}/{m["hour"]}: straight-edge regression')
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
        w = csv.DictWriter(f, fieldnames=list(rows[0]), lineterminator="\n"); w.writeheader(); w.writerows(rows)


def main(saved, work, dest):
    saved, work, dest = map(Path, (saved, work, dest)); dest.mkdir(parents=True, exist_ok=True)
    rows, processing_rows, originals, baseline, processed = [], [], {}, {}, {}
    exact_repeats = 0
    for family in FAMILIES:
        old = read(saved/f'{family}_scored.jsonl'); new = read(work/f'{family}_all_corrected_scored.jsonl')
        require(old.keys() == new.keys(), f'{family}: original coverage changed')
        originals.update(new); baseline.update(old); rows += scalar_rows(old, new)
        old_processing = read(saved/f'{family}_processing_scored.jsonl')
        new_processing = read(work/f'{family}_processing_corrected_scored.jsonl')
        require(old_processing.keys() == new_processing.keys(), f'{family}: processing coverage changed')
        baseline.update(old_processing); processed.update(new_processing)
        processing_rows += scalar_rows(old_processing, new_processing)
        for id, p in new_processing.items():
            original_id, method = id.split('__', 1)
            if method == 'identical_original_repeat':
                original = new[original_id]
                # Exact same bytes and exact same outputs on this host. Export identity/split
                # metadata differ, but decoded input, pose, measured polygons and scores do not.
                for k in ('source_sha256', 'input_evidence', 'H', 'ring', 'markers', 'outer_snapshot_sha256'):
                    require(p.get(k) == original.get(k), f'{id}: exact repeat {k} changed')
                exact_repeats += 1
    require(len(originals) == 132, 'Expected all 132 original photographs')
    require(len(processed) == 18 and exact_repeats == 3, 'Expected 18 variants and 3 exact repeats')
    bg_old = read(work/'gmt_126710_batgirl_baseline.jsonl')
    bg_new = read(work/'gmt_126710_batgirl_corrected.jsonl')
    require(bg_old.keys() == bg_new.keys() == {'batgirl_37828'}, 'Batgirl replay coverage changed')
    require(bg_new['batgirl_37828']['source_sha256'] == BATGIRL_SHA, 'Batgirl original hash changed')
    bg_rows = scalar_rows(bg_old, bg_new)
    for h in (9, 12):
        require(marker(bg_old['batgirl_37828'], h) == marker(bg_new['batgirl_37828'], h), f'Batgirl / {h} changed')
    write_csv(dest/'original_comparison.csv', rows)
    write_csv(dest/'processing_comparison.csv', processing_rows)
    write_csv(dest/'batgirl_comparison.csv', bg_rows)
    all_new = dict(originals, **processed)
    failures = []
    def check(fn, *args):
        try:
            return fn(*args)
        except (ValueError, KeyError, TypeError) as error:
            failures.append(str(error))
    old_delta = check(check_frozen_cases, baseline)
    for family, id, hour, *expected in REVIEWED:
        check(check_reviewed, all_new[id], hour, *expected)
    for family, id, hour in CONTROLS:
        check(check_control, baseline[id], all_new[id], hour)
    for family, id, hour in SAFE_BOUNDARIES:
        check(check_safe_boundary, baseline[id], all_new[id], hour)
    # The initial candidate switched boundaries on this existing processing control.
    # Contradictory photographed arcs must withhold both geometry and alignment, across
    # the original and every frozen processing variant, regardless of the reported %.
    blocker=[originals['c58f78ef6c1cd5b7']]+list(read(work/'submariner_124060_processing_corrected_scored.jsonl').values())
    for p in blocker:
        m=marker(p,4)
        require(m['new_fitted'] is None and m['new_alignment'] is None and m['new_observed'] is None,
                f'{p["photo_id"]}: processing boundary switch or observed footprint')
        require('contradict' in field(m,'fit','reason'), f'{p["photo_id"]}: contradiction reason absent')
    verified = list(csv.DictReader((work/'verified_inputs.csv').open()))
    fields = ('photo_id','sha256','family','model','physical_watch_id','source_width',
              'source_height','source_url','image_url','exif_orientation')
    write_csv(dest/'original_hashes.csv', [{key:r[key] for key in fields} for r in verified])
    corrected_delta = abs(field(marker(all_new['0114716639829db3'], 10), 'new_fitted', 'total_pct')
                          -field(marker(all_new['0114716639829db3__resize_5of6_png'], 10), 'new_fitted', 'total_pct'))
    summary = dict(validation_schema=2, validation_passed=not failures, validation_failures=failures,
                   originals=len(originals), processing_variants=len(processed),
                   reviewed_circles_checked=len(REVIEWED), unchanged_reviewed_controls_checked=len(CONTROLS),
                   metal_boundary_regressions_checked=len(SAFE_BOUNDARIES), batgirl_markers_checked=[9,12],
                   batgirl_original_verified=True, exact_original_repeats=exact_repeats,
                   noncircular_and_outer_unchanged=True,
                   circular_geometry_changes=dict(Counter(r['change'] for r in rows if r['shape']=='round')),
                   by_family={f:dict(Counter(r['change'] for r in rows if r['family']==f and r['shape']=='round')) for f in FAMILIES},
                   observed_withheld_after_validation=sum(r['before_observed_usable'] and not r['after_observed_usable'] for r in rows),
                   old_resize_delta_pp=old_delta, corrected_resize_delta_pp=corrected_delta,
                   contradictory_processing_circle_withheld_in_all_7_cases=True,
                   source_hashes=hashes(SOURCE_PATHS), evidence_hashes=hashes(EVIDENCE_PATHS, dest),
                   frozen_input_hashes=hashes(FROZEN_PATHS, saved), batgirl_baseline_sha256=digest(ROOT/BATGIRL_FROZEN))
    (dest/'validation.json').write_text(json.dumps(summary, indent=2)+'\n')
    print(json.dumps(summary, indent=2))
    if failures:
        raise SystemExit('Photographic validation failed; comparison artifacts preserved. Do not build an APK.')


if __name__ == '__main__':
    main(*sys.argv[1:])
