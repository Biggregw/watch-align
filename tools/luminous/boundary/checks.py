"""Photographic regression contracts; these values never enter the detector or QC."""
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
FAMILIES = ('gmt_126710', 'submariner_124060', 'submariner_126610')
BATGIRL_SHA = 'c50e0845ddb6c50701a4ba8ab6b396092e67988a747206f77743c02e7c7e54eb'
# Reviewed raw photographs and the retained frozen boundary, not expected templates.
CONTROLS = (
    ('gmt_126710', 'c652c207a6b82064', 4),
    ('gmt_126710', 'b5a3712a20ede636', 12),
    ('gmt_126710', 'c652c207a6b82064', 9),
    ('submariner_124060', '033133202cf757da', 1),
    ('submariner_124060', '033133202cf757da', 12),
    ('submariner_124060', '033133202cf757da', 3),
    ('submariner_126610', '355d47df5ee1eee0', 1),
    ('submariner_126610', '355d47df5ee1eee0', 12),
    ('submariner_126610', '355d47df5ee1eee0', 9),
)
SAFE_BOUNDARIES = (
    ('submariner_124060', '855041528fc1ac79', 5),
    ('submariner_124060', 'c14f8b3abb49c038', 8),
    ('submariner_126610', '1fb794f0783c0568', 4),
)
REVIEWED = (
    ('submariner_124060', '80d288dcc8689345', 2,
     (0.7047047706567595, -0.4063895155222623), .0747905724361607,
     3.8293756491088162, 1.8300368027192664),
    ('submariner_126610', '0114716639829db3', 10,
     (-0.7045342374083791, -0.4114659292020688), .07553514066392383,
     3.0206565596657944, 2.46739261359871),
    ('submariner_126610', '0114716639829db3__resize_5of6_png', 10,
     (-0.7039286001435094, -0.4126738312848012), .07598541564564357,
     1.9781630513030857, 1.7754802245505141),
    # Published alpha107.3 HAND-filtered geometry must survive alternatives validation.
    ('gmt_126710', '3108248748f8ed97', 10,
     (-0.7023480253466624, -0.41115192295959463), .07728870868528753,
     1.6957720525161855, 1.6654479600618721),
)
SOURCE_PATHS = tuple(sorted(
    ['android/app/src/main/java/com/watchalign/mobile/' + name + '.java' for name in
     ('LuminousCircularBoundary', 'LuminousMarkerDetector', 'LuminousPartialFit',
      'LuminousSeparator', 'LuminousGeometry', 'LuminousMeasurement',
      'LuminousReference', 'MarkerPolygonMismatch')]
    + ['android/app/src/test/java/com/watchalign/mobile/' + name + 'Test.java' for name in
       ('LuminousCircularBoundary', 'LuminousPartialFit', 'LuminousMeasurement',
        'LuminousReference', 'MarkerPolygonMismatch')]
    + ['tools/desktop-harness/drivers/' + name + '.java' for name in
       ('LuminousCalibrationStudy', 'LuminousSyntheticChecks', 'LuminousValidation',
        'Alpha96Calib', 'HarnessModel')]
    + ['tools/desktop-harness/run.sh', 'tools/luminous/prepare_curated.py']
    + ['tools/luminous/boundary/' + name for name in
       ('checks.py', 'compare.py', 'replay.py', 'verify_build.py', 'test_validation.py')]
))
EVIDENCE_PATHS = ('original_comparison.csv', 'processing_comparison.csv',
                  'batgirl_comparison.csv', 'original_hashes.csv')
BATGIRL_FROZEN = 'docs/research/luminous-boundary-fix-2026-10-12/batgirl_frozen_geometry.jsonl.gz'
FROZEN_PATHS = ('calibrated_references.json', 'processing_manifest.csv') + tuple(
    family + suffix for family in FAMILIES
    for suffix in ('_scored.jsonl', '_processing_scored.jsonl', '_extract.csv'))


def require(condition, detail):
    # These scripts are safety gates even when Python runs with -O.
    if not condition:
        raise ValueError(detail)


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def hashes(paths, base=ROOT):
    return {path: digest(Path(base) / path) for path in paths}


def close(a, b, eps=1e-7):
    if isinstance(a, bool) or isinstance(b, bool):
        return type(a) is type(b) and a == b
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return math.isfinite(a) and math.isfinite(b) and math.isclose(a, b, rel_tol=0, abs_tol=eps)
    if isinstance(a, dict) and isinstance(b, dict):
        return a.keys() == b.keys() and all(close(a[k], b[k], eps) for k in a)
    if isinstance(a, (list, tuple)) and isinstance(b, (list, tuple)):
        return len(a) == len(b) and all(close(x, y, eps) for x, y in zip(a, b))
    return a == b


def read(path):
    result = {}
    for line in Path(path).read_text().splitlines():
        photo = json.loads(line)
        require(photo['photo_id'] not in result, f'Duplicate photo: {photo["photo_id"]}')
        result[photo['photo_id']] = photo
    return result


def marker(photo, hour):
    found = [m for m in photo['markers'] if m['hour'] == hour]
    require(len(found) == 1, f'{photo["photo_id"]}: expected one marker at {hour}')
    return found[0]


def check_reviewed(photo, hour, center, radius, fitted, alignment):
    m = marker(photo, hour)
    require(bool((m.get('fit') or {}).get('ideal')), f'{photo["photo_id"]}/{hour}: fit withheld')
    require(close(m['fit']['center'], center, 1e-9), f'{photo["photo_id"]}/{hour}: center changed')
    require(close(m['fit']['radius_R'], radius, 1e-9), f'{photo["photo_id"]}/{hour}: radius changed')
    require(all(close(math.hypot(p[0]-center[0], p[1]-center[1]), radius, 1e-9)
                for p in m['fit']['ideal']), f'{photo["photo_id"]}/{hour}: reconstructed circle changed')
    for key, expected in (('new_fitted', fitted), ('new_alignment', alignment)):
        require(m.get(key) is not None and close(m[key]['total_pct'], expected, 1e-6),
                f'{photo["photo_id"]}/{hour}: {key} changed')
    require(not m['observed_usable'] and m.get('observed_polygon') is None
            and m.get('new_observed') is None and m.get('old_observed') is None,
            f'{photo["photo_id"]}/{hour}: reconstructed geometry became OBSERVED footprint')


def check_control(before, after, hour):
    b, a = marker(before, hour), marker(after, hour)
    for key in ('observed_usable', 'observed_polygon', 'new_fitted', 'new_alignment',
                'new_observed', 'old_fitted', 'old_alignment', 'old_observed'):
        require(close(b.get(key), a.get(key)), f'{after["photo_id"]}/{hour}: control {key} changed')
    for key in ('ideal', 'center', 'radius_R', 'angle_rad', 'width_R', 'height_R'):
        require(close((b.get('fit') or {}).get(key), (a.get('fit') or {}).get(key)),
                f'{after["photo_id"]}/{hour}: control fit {key} changed')


def check_safe_boundary(before, after, hour):
    """A reviewed correct circle may remain identical or abstain; false metal fits fail."""
    a = marker(after, hour)
    if (a.get('fit') or {}).get('ideal'):
        b = marker(before, hour)
        for key in ('new_fitted', 'new_alignment', 'old_fitted', 'old_alignment'):
            require(close(b.get(key), a.get(key)), f'{after["photo_id"]}/{hour}: reviewed fit {key} changed')
        for key in ('ideal', 'center', 'radius_R'):
            require(close((b.get('fit') or {}).get(key), a['fit'].get(key), 1e-8),
                    f'{after["photo_id"]}/{hour}: circle expanded onto metal ({key})')
        if a['observed_usable']:
            require(close(b.get('observed_polygon'), a.get('observed_polygon')),
                    f'{after["photo_id"]}/{hour}: observed boundary changed')
    else:
        for key in ('new_fitted', 'new_alignment', 'new_observed', 'old_fitted',
                    'old_alignment', 'old_observed', 'observed_polygon'):
            require(a.get(key) is None, f'{after["photo_id"]}/{hour}: unsafe {key} survived abstention')
        require(not a['observed_usable'], f'{after["photo_id"]}/{hour}: unsafe footprint remains usable')


def check_frozen_cases(photos):
    """Verify the documented old failure fixture, rather than hard-coding its delta."""
    failure = marker(photos['80d288dcc8689345'], 2)
    original = marker(photos['0114716639829db3'], 10)
    resize = marker(photos['0114716639829db3__resize_5of6_png'], 10)
    require(close(failure['new_fitted']['total_pct'], 60.569019, 1e-6), 'Old interior-circle failure changed')
    require(close(failure['fit']['radius_R'], .046346543, 1e-9), 'Old interior-circle radius changed')
    delta = abs(original['new_fitted']['total_pct'] - resize['new_fitted']['total_pct'])
    require(close(delta, 9.6165090286, 1e-6), 'Archived processing failure changed')
    return delta


def check_frozen_inputs(saved, validation):
    expected = validation.get('frozen_input_hashes', {})
    require(set(expected) == set(FROZEN_PATHS), 'Frozen input hash contract is incomplete')
    require(hashes(FROZEN_PATHS, saved) == expected, 'Archived study inputs changed')
