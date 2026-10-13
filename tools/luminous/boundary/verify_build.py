"""Fail closed unless source, frozen evidence and measured controls match the full audit."""
import argparse
import csv
import json
import subprocess
from collections import Counter
from pathlib import Path
from checks import (BATGIRL_FROZEN, BATGIRL_SHA, CONTROLS, EVIDENCE_PATHS, FAMILIES,
                    FROZEN_PATHS, REVIEWED, ROOT, SAFE_BOUNDARIES, SOURCE_PATHS,
                    close, digest, hashes, require)


def verify_contract(report):
    require(report.get('validation_schema') == 2, 'A strengthened full photographic audit is required')
    require(report.get('validation_passed') is True and report.get('validation_failures') == [],
            'Photographic audit has unresolved failures; preserve the existing APK')
    expected = {'originals':132, 'processing_variants':18, 'exact_original_repeats':3,
                'reviewed_circles_checked':len(REVIEWED), 'unchanged_reviewed_controls_checked':len(CONTROLS),
                'metal_boundary_regressions_checked':len(SAFE_BOUNDARIES), 'batgirl_markers_checked':[9,12]}
    for key, value in expected.items():
        require(report.get(key) == value, f'Incomplete photographic validation: {key}')
    for key in ('noncircular_and_outer_unchanged', 'batgirl_original_verified',
                'contradictory_processing_circle_withheld_in_all_7_cases'):
        require(report.get(key) is True, f'Required photographic check failed: {key}')
    for key, paths in (('source_hashes', SOURCE_PATHS), ('evidence_hashes', EVIDENCE_PATHS),
                       ('frozen_input_hashes', FROZEN_PATHS)):
        require(set(report.get(key, {})) == set(paths), f'Incomplete {key} contract')
    require(close(report.get('old_resize_delta_pp'),9.6165090286,1e-6), 'Frozen resize failure changed')


def verify_csv_constraints(original, processing, batgirl):
    """Recheck photographic gates in signed-off evidence; a report flag is insufficient."""
    def row(rows,id,hour):
        found=[r for r in rows if r['photo_id']==id and float(r['hour'])==hour]
        require(len(found)==1,f'{id}/{hour}: photographic CSV row missing or duplicated')
        return found[0]
    def number(r,key):
        return float(r[key]) if r.get(key) else None
    def observed_withheld(r):
        return r['after_observed_usable'].lower()=='false' and not r['after_new_observed_pct'] and not r['after_old_observed_pct']
    def unchanged(r,keys):
        for key in keys:
            require(close(number(r,'before_'+key),number(r,'after_'+key)),
                    f'{r["photo_id"]}/{r["hour"]}: reviewed boundary {key} changed')
    all_rows=original+processing
    for family,id,hour,center,radius,fitted,alignment in REVIEWED:
        r=row(all_rows,id,hour)
        for key,value,eps in (('cx_R',center[0],1e-9),('cy_R',center[1],1e-9),('radius_R',radius,1e-9),
                              ('new_fitted_pct',fitted,1e-6),('new_alignment_pct',alignment,1e-6)):
            require(close(number(r,'after_'+key),value,eps),f'{id}/{hour}: reviewed CSV {key} changed')
        require(observed_withheld(r),f'{id}/{hour}: OBSERVED was not withheld')
    metrics=('new_fitted_pct','new_alignment_pct','new_observed_pct','old_fitted_pct','old_alignment_pct','old_observed_pct')
    for family,id,hour in CONTROLS:
        r=row(original,id,hour)
        unchanged(r,metrics+('cx_R','cy_R','radius_R'))
        require(r['before_observed_usable']==r['after_observed_usable'],f'{id}/{hour}: control footprint availability changed')
    for family,id,hour in SAFE_BOUNDARIES:
        r=row(original,id,hour)
        if r['after_radius_R']:
            unchanged(r,('cx_R','cy_R','radius_R','new_fitted_pct','new_alignment_pct','old_fitted_pct','old_alignment_pct'))
        else:
            require(all(not r['after_'+key] for key in metrics) and observed_withheld(r),
                    f'{id}/{hour}: unsafe measurement survived abstention')
    for hour in (9,12):
        r=row(batgirl,'batgirl_37828',hour)
        unchanged(r,metrics+('cx_R','cy_R','radius_R'))


def verify(report_dir):
    report_dir=Path(report_dir)
    report=json.loads((report_dir/'validation.json').read_text())
    verify_contract(report)
    require(hashes(SOURCE_PATHS) == report['source_hashes'], 'Measurement/replay/test source changed after validation')
    require(hashes(EVIDENCE_PATHS, report_dir) == report['evidence_hashes'], 'Comparison evidence changed after validation')
    require(digest(ROOT/BATGIRL_FROZEN) == report.get('batgirl_baseline_sha256'), 'Frozen Batgirl measurements changed')
    def rows(name):
        with (report_dir/name).open() as f:
            return list(csv.DictReader(f))
    original=rows('original_comparison.csv')
    require(len({r['photo_id'] for r in original}) == 132, 'Original evidence coverage changed')
    circles=[r for r in original if r['shape']=='round']
    require(len(circles)==1056 and len(original)-len(circles)==442, 'Marker coverage changed')
    require(dict(Counter(r['change'] for r in circles)) == report['circular_geometry_changes'], 'Geometry summary disagrees with evidence')
    for family in FAMILIES:
        counts=dict(Counter(r['change'] for r in circles if r['family']==family))
        require(counts == report['by_family'][family], f'{family}: geometry summary disagrees with evidence')
    originals=rows('original_hashes.csv')
    require(len(originals)==132 and len({r['photo_id'] for r in originals})==132, 'Original hash manifest changed')
    expected_sha={r['photo_id']:r['sha256'] for r in originals}
    require(all(r['source_sha256']==expected_sha[r['photo_id']] for r in original), 'Original comparison hashes disagree')
    processing=rows('processing_comparison.csv')
    require(len({r['photo_id'] for r in processing})==18, 'Processing evidence coverage changed')
    batgirl=rows('batgirl_comparison.csv')
    require(all(r['source_sha256']==BATGIRL_SHA for r in batgirl), 'Batgirl evidence hash changed')
    verify_csv_constraints(original,processing,batgirl)
    allowed={'android/app/build.gradle','android/app/src/main/AndroidManifest.xml',
             'android/app/src/main/java/com/watchalign/mobile/LuminousCircularBoundary.java',
             'android/app/src/main/java/com/watchalign/mobile/LuminousMarkerDetector.java',
             'android/app/src/main/java/com/watchalign/mobile/LuminousMeasurement.java',
             'android/app/src/test/java/com/watchalign/mobile/LuminousCircularBoundaryTest.java',
             'android/app/src/test/java/com/watchalign/mobile/LuminousMeasurementTest.java'}
    changed=set(subprocess.check_output(['git','diff','--name-only','8a53c1e5','--','android'],cwd=ROOT,text=True).splitlines())
    require(changed <= allowed, 'Protected implementation/reference changes: '+str(changed-allowed))
    print('Source-bound photographic validation and frozen outer/reference/pose safeguards verified')


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, default=ROOT/'docs/research/luminous-boundary-review-2026-10-13')
    verify(parser.parse_args().report)
