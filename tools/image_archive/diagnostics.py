"""Read-only GMT legacy diagnostics. Deliberately NOT a validated archive profile.

Reuses existing acquisition quality evidence; does not treat legacy GOOD/suitable
as face-on proof. Other layouts are withheld rather than run through GMT geometry.
"""
import tempfile
from pathlib import Path
from tools.dataset_harvester.harvester import quality
from tools.dataset_harvester.harvester.harness import Harness


class LegacyGMTDiagnostics:
    def __init__(self, harness=None):
        self.harness = harness or Harness(shards=1)

    def __call__(self, img, family, purpose):
        if family not in {'gmt_126710', 'GMT-Master II', '126710BLNR', '126710BLRO'}:
            return {'profile': 'unsupported-layout', 'checks': {}}
        with tempfile.TemporaryDirectory(prefix='watch-align-screen-') as work:
            path = Path(work) / 'analysis.png'
            img.save(path)  # Analysis copy only; archive always retains original bytes.
            suit = self.harness.suitability([str(path)], Path(work) / 'suit').get(str(path), {})
        legacy = quality.assess(img, suit)
        geometry = quality.dial_geometry(suit)
        checks = {}
        if geometry:
            if not quality.dial_inside(geometry, 0):
                checks['complete_dial'] = False
            # Existing reviewed audit floor; necessary rejection bound, NOT sufficient
            # precision-calibration admission resolution. No invented pass threshold.
            if min(geometry['a'], geometry['b']) * 2 < 400:
                checks['original_resolution'] = False
        if suit.get('pose') in {'RETAKE', 'REJECT', 'BAD', 'UNSUITABLE', 'CORRECTABLE'}:
            checks['face_on_marker_lattice'] = False
        failures = {'reject_blur':'sharp_boundaries', 'reject_glare':'glare',
                    'reject_underexposed':'exposure', 'reject_overexposed':'exposure',
                    'reject_incomplete_dial':'complete_dial', 'reject_landmarks_unusable':'landmarks'}
        for reason in legacy['reasons']:
            if reason in failures:
                checks[failures[reason]] = False
        suit.pop('path', None)
        return {'profile': 'legacy-gmt-diagnostics-only', 'checks': checks,
                'legacy': legacy, 'suit': suit, 'required_marker_ids': [1,2,4,5,6,7,8,9,10,11,12],
                'markers': {'12': False} if suit.get('hand_at_twelve') else {}}


class FamilyDiagnostics:
    """Existing model-specific TrackDump measurements, with no threshold changes.

    Positive marker detection is diagnostic, not proof of all archive checks. The
    registry stays unpromoted until rehaut, ellipse, perimeter and precision gates
    have held-out validation. No family is silently analyzed as GMT.
    """
    from .faceon import MODELS  # the reference families the reviewed marker audit measured with each model

    def __init__(self, harness=None):
        self.harness = harness or Harness(shards=1)
        self.compiled = False

    def __call__(self, img, family, purpose):
        import csv
        import os
        import subprocess
        from tools.dataset_harvester.harvester.harness import _tool
        from tools.dataset_harvester.harvester.config import REPO_ROOT, HARNESS_DIR
        model = self.MODELS.get(family)
        if not model:
            return {'profile': 'unsupported-layout', 'checks': {}}
        classes = self.harness.compile()
        cp = str(self.harness.jar) + os.pathsep + str(classes)
        if not self.compiled:
            sources = [HARNESS_DIR / 'shim', REPO_ROOT / 'android/app/src/main/java', HARNESS_DIR / 'drivers']
            result = subprocess.run([_tool('javac'), '-encoding', 'UTF-8', '-cp', cp, '-sourcepath',
                                     os.pathsep.join(map(str, sources)), '-d', str(classes),
                                     *[str(HARNESS_DIR / 'drivers' / name) for name in ('TrackDump.java', 'HarnessModel.java', 'AlignmentCalib.java', 'Alpha96Calib.java')]], capture_output=True)
            if result.returncode:
                raise RuntimeError('TrackDump compilation failed')
            self.compiled = True
        with tempfile.TemporaryDirectory(prefix='watch-align-family-') as work:
            root = Path(work)
            img.save(root / 'analysis.png')
            (root / 'input.csv').write_text('photo_id,local_path,physical_watch_id\nanalysis,analysis.png,diagnostic\n')
            result = subprocess.run([_tool('java'), '-Xmx1500m', '-Dwatchalign.model=' + model, '-cp', cp,
                                     'com.watchalign.mobile.TrackDump', str(root / 'input.csv'), str(root),
                                     str(root / 'output.csv')], cwd=REPO_ROOT, capture_output=True, timeout=180)
            if result.returncode or not (root / 'output.csv').exists():
                raise RuntimeError('TrackDump analysis failed')
            rows = list(csv.DictReader((root / 'output.csv').open()))
        checks, markers = {}, {}
        required = list(range(1,13)) if model == 'submariner_124060' else [1,2,4,5,6,7,8,9,10,11,12]
        if rows:
            radius = float(rows[0]['rpx'])
            if 2 * radius < 400:
                checks['original_resolution'] = False
            markers = marker_eligibility(rows)
        return {'profile': 'model-diagnostics-only/' + model, 'checks': checks,
                'required_marker_ids': required, 'markers': markers, 'track_measurements': rows}


def marker_eligibility(rows):
    """Per-marker availability, separate from QC verdicts (owner rule WatchClassification.credible()).

    False: withheld by the app (a hand, glare or Can't check gate) - the marker is obstructed or unreadable.
    True: a completed measurement. Round markers have no tilt (only position), so a tilt is required only of
    batons and the triangle; a soft edge-fit warning does not make a measured marker unavailable.
    None: not reported or not finite.
    """
    import math
    markers = {}
    for row in rows:
        withheld = bool(row.get('gate')) or row.get('state') in {'CANT_CHECK', 'CANNOT_ASSESS', 'NOT_ASSESSED'}
        keys = ('side_pct', 'inout_pct') + (() if row.get('shape') == 'round' else ('tilt_line_deg',))
        try:
            complete = all(math.isfinite(float(row[k])) for k in keys)
        except (ValueError, KeyError, TypeError):
            complete = False
        markers[row['hour']] = False if withheld else True if complete else None
    return markers
