"""Face-on photograph evidence from the production analysis (read-only).

Every number here is something the app already computes on the photo
(``tools/desktop-harness/drivers/ArchiveEvidence.java`` runs the production
``Alpha99Pipeline``): the dial-edge ellipse, the minute-tick lattice and the
canonical-dial-to-image homography it registers, and each marker's clearly-off
state with the owner's ``WatchClassification.credible()`` / ``reliable()`` rules.
The only additions are geometry derived from that homography (how strongly the
dial plane is foreshortened and how much its scale changes from one side of the
dial to the other) and the dataset harvester's existing dial pixel metrics.

Nothing here changes a QC measurement, tolerance or verdict.
"""
from __future__ import annotations

import csv
import json
import math
import re
import os
import subprocess
import tempfile
from pathlib import Path

# The same reference families the reviewed marker audit measured with each model (tools/research/marker_audit/results/
# *-verified.csv): every 126710 bezel variant with the GMT dial, every 41 mm Submariner Date reference with the Date dial.
MODELS = {'gmt_126710': 'gmt_126710', '126710BLNR': 'gmt_126710', '126710BLRO': 'gmt_126710', '126710GRNR': 'gmt_126710',
          'GMT-Master II': 'gmt_126710',
          '124060': 'submariner_124060', 'submariner_124060': 'submariner_124060',
          '126610': 'submariner_126610', '126610LN': 'submariner_126610', '126610LV': 'submariner_126610',
          '126610LN/LV': 'submariner_126610', '126613LB': 'submariner_126610', '126613LN': 'submariner_126610',
          '126613': 'submariner_126610', '126618LB': 'submariner_126610', '126618': 'submariner_126610',
          '126618LN': 'submariner_126610', '126619': 'submariner_126610', '126619LB': 'submariner_126610',
          'submariner_126610': 'submariner_126610'}

# 12 hour markers on No-Date dials; Date dials have no 3 (the date window is not a marker)
REQUIRED = {'gmt_126710': tuple(h for h in range(1, 13) if h != 3),
            'submariner_126610': tuple(h for h in range(1, 13) if h != 3),
            'submariner_124060': tuple(range(1, 13))}

MIN_COVER = 0.5   # MetalFit.MIN_COVER

DRIVERS = ('ArchiveEvidence.java', 'HarnessModel.java', 'AlignmentCalib.java', 'Alpha96Calib.java', 'Load.java')


def jacobian(h, x, y):
    """d(image)/d(canonical) of the homography at canonical (x, y)."""
    w = h[6] * x + h[7] * y + h[8]
    u = (h[0] * x + h[1] * y + h[2]) / w
    v = (h[3] * x + h[4] * y + h[5]) / w
    return ((h[0] - u * h[6]) / w, (h[1] - u * h[7]) / w,
            (h[3] - v * h[6]) / w, (h[4] - v * h[7]) / w), (u, v)


def _svd2(j):
    a, b, c, d = j
    s = a * a + b * b + c * c + d * d
    det = a * d - b * c
    disc = math.sqrt(max(s * s - 4 * det * det, 0.0))
    return math.sqrt((s + disc) / 2), math.sqrt(max((s - disc) / 2, 0.0))


def homography_pose(h, r=0.9):
    """Foreshortening and perspective gradient of the registered dial plane.

    axis_ratio: minor/major local scale at the dial centre (1 = face-on; cos of the
    out-of-plane tilt under weak perspective). tilt_deg: acos(axis_ratio).
    v_gradient / h_gradient: local linear scale at 12 over 6 / 3 over 9, at radius r
    (1 = no perspective gradient; a tilted camera enlarges the nearer side).
    """
    if not h or len(h) != 9 or not all(isinstance(v, (int, float)) and math.isfinite(v) for v in h):
        return {}
    j, centre = jacobian(h, 0.0, 0.0)
    s1, s2 = _svd2(j)
    if not s1 > 0:
        return {}

    def scale(x, y):
        jj, _ = jacobian(h, x, y)
        return math.sqrt(abs(jj[0] * jj[3] - jj[1] * jj[2]))
    ratio = s2 / s1
    return {'axis_ratio': ratio, 'tilt_deg': math.degrees(math.acos(min(1.0, ratio))),
            'v_gradient': scale(0, -r) / scale(0, r), 'h_gradient': scale(r, 0) / scale(-r, 0),
            'centre_x': centre[0], 'centre_y': centre[1], 'scale_px': math.sqrt(s1 * s2)}


def dial_margin(h, width, height, r=1.0, n=360):
    """Smallest distance (in dial radii) from the canonical circle of radius r to the image border; < 0 = cut off."""
    pose = homography_pose(h)
    if not pose:
        return None
    worst = math.inf
    for i in range(n):
        t = 2 * math.pi * i / n
        x, y = r * math.sin(t), -r * math.cos(t)
        w = h[6] * x + h[7] * y + h[8]
        u = (h[0] * x + h[1] * y + h[2]) / w
        v = (h[3] * x + h[4] * y + h[5]) / w
        worst = min(worst, u, v, width - u, height - v)
    return worst / pose['scale_px']


def summarise(ev, image=None):
    """Flat evidence record from one ArchiveEvidence JSON line (+ the decoded original for pixel metrics)."""
    out = {k: ev.get(k) for k in ('photo_id', 'model', 'work_w', 'work_h', 'pose_valid', 'pose_reason', 'edge_ellipse_ratio',
                                  'edge_rms_px', 'tick_rms_px', 'detected_ticks', 'complete_pairs', 'projective_accepted',
                                  'marker_branch_turn', 'rpx', 'bad_ticks', 'hour_lines', 'ring_turn_pct', 'error',
                                  'clearly_off_error', 'photo_reason')}
    h = ev.get('homography')
    out.update(homography_pose(h) if h else {})
    if h and ev.get('work_w'):
        out['dial_margin_r'] = dial_margin(h, ev['work_w'], ev['work_h'])
    markers = {int(m['hour']): m for m in ev.get('markers', [])}
    required = REQUIRED.get(ev.get('model'), ())
    out['required'] = list(required)
    out['present'] = [hr for hr in required if hr in markers]
    out['credible'] = [hr for hr in required if markers.get(hr, {}).get('credible')]
    out['reliable'] = [hr for hr in required if markers.get(hr, {}).get('reliable')]
    out['withheld'] = {hr: (markers[hr].get('gate') or markers[hr].get('reason') or markers[hr].get('state'))
                       for hr in required if hr in markers and not markers[hr].get('credible')}
    # MetalFit.MIN_COVER (0.5): under half of the marker's outer-metal outline is seen and the rest is reconstructed from
    # its known shape. The app still reports the reading (credible); for the archive the marker is partly hidden.
    out['partly_hidden'] = [hr for hr in required if markers.get(hr, {}).get('credible')
                            and isinstance(markers[hr].get('cover'), (int, float)) and markers[hr]['cover'] < MIN_COVER]
    # outline coverage of each measured marker (MetalFit: share of its outer-metal outline seen)
    out['cover'] = {hr: markers[hr].get('cover') for hr in required if markers.get(hr, {}).get('credible')}
    # the app's independent per-marker hand check (MetalFit.handConfirmed) found a hand at this marker
    out['hand_confirmed'] = [hr for hr in required if markers.get(hr, {}).get('credible') and markers[hr].get('hand_confirmed')]
    out['soft'] = {hr: markers[hr].get('uncertain') for hr in required
                   if hr in markers and markers[hr].get('credible') and not markers[hr].get('reliable')}
    out['markers'] = {hr: {k: markers[hr].get(k) for k in ('state', 'side_pct', 'inout_pct', 'tilt_deg', 'pos_pct', 'gap_pct',
                                                            'cover', 'fit_rms_px', 'bad_frac', 'hand_confirmed', 'uncertain')}
                      for hr in required if hr in markers}
    if image is not None:
        out['orig_w'], out['orig_h'] = image.size
        if ev.get('work_w') and out.get('centre_x') is not None:
            k = image.size[0] / float(ev['work_w'])
            out['orig_rpx'] = (ev.get('rpx') or out['scale_px']) * k
            from tools.dataset_harvester.harvester.quality import pixel_metrics
            g = {'cx': out['centre_x'] * k, 'cy': out['centre_y'] * k, 'radius': out['scale_px'] * k}
            try:
                out.update(pixel_metrics(image, g))
            except Exception as e:  # a dial too close to the border to crop: recorded, never guessed
                out['pixel_metrics_error'] = type(e).__name__
    return out


class Driver:
    """Compiles and runs ArchiveEvidence once per model over a list of images (one JVM)."""

    def __init__(self, harness=None):
        from tools.dataset_harvester.harvester.harness import Harness
        self.harness = harness or Harness(shards=1)
        self.classes = None

    def _compile(self):
        from tools.dataset_harvester.harvester.harness import _tool
        from tools.dataset_harvester.harvester.config import REPO_ROOT, HARNESS_DIR
        if self.classes:
            return self.classes
        base = self.harness.compile()
        cp = str(self.harness.jar) + os.pathsep + str(base)
        out = Path(tempfile.mkdtemp(prefix='watch-align-evidence-classes-'))
        sources = [HARNESS_DIR / 'shim', REPO_ROOT / 'android/app/src/main/java']
        res = subprocess.run([_tool('javac'), '-encoding', 'UTF-8', '-nowarn', '-cp', cp, '-sourcepath',
                              os.pathsep.join(map(str, sources)), '-d', str(out),
                              *[str(HARNESS_DIR / 'drivers' / n) for n in DRIVERS]], capture_output=True, text=True)
        if res.returncode:
            raise RuntimeError('ArchiveEvidence compilation failed: ' + res.stderr[-800:])
        self.classes = str(self.harness.jar) + os.pathsep + str(out) + os.pathsep + str(base)
        return self.classes

    def run(self, model, items, timeout=None):
        """items: [(photo_id, absolute_path)] -> {photo_id: evidence dict}."""
        from tools.dataset_harvester.harvester.harness import _tool
        from tools.dataset_harvester.harvester.config import REPO_ROOT
        cp = self._compile()
        with tempfile.TemporaryDirectory(prefix='watch-align-evidence-') as work:
            root = Path(work)
            with (root / 'input.csv').open('w', newline='') as f:
                w = csv.writer(f)
                w.writerow(['photo_id', 'local_path', 'physical_watch_id'])
                for pid, path in items:
                    w.writerow([pid, str(Path(path).resolve()), 'diagnostic'])
            res = subprocess.run([_tool('java'), '-Xmx2500m', '-Dwatchalign.model=' + model, '-cp', cp,
                                  'com.watchalign.mobile.ArchiveEvidence', str(root / 'input.csv'), '/',
                                  str(root / 'out.jsonl')], cwd=REPO_ROOT, capture_output=True,
                                 timeout=timeout or 120 + 60 * len(items))
            if res.returncode or not (root / 'out.jsonl').exists():
                raise RuntimeError('ArchiveEvidence analysis failed')
            return {d['photo_id']: d for d in map(json.loads, (root / 'out.jsonl').read_text().splitlines())}


# ---------------------------------------------------------------------------------------------------------------------
# Profile face-on-v3 (21 Oct 2026; v2 + archive 250 px floor, hand check and soft-warning classes). Evidence: docs/image-archive/FACE-ON-VALIDATION.md and face-on-evidence.jsonl
# (CI run 39651765901). A check PASSES only inside the envelope covered by the reviewed near-frontal genuine photographs
# whose production measurements stayed clean (263 photos / 217 watches: reviewed near_frontal=yes, dial radius >= 200
# px, dial tilt <= 10.60 deg). It FAILS where the evidence shows harm (or an existing, documented rejection bound), and
# is INCONCLUSIVE in between: that range was not tested, so it is withheld rather than guessed.
PROFILE = 'face-on-v3'
ENVELOPE = {
    # dial-plane tilt from the minute-track registration (acos of the minor/major local scale at the centre). Largest
    # reviewed near-frontal tilt with clean measurements, and below the smallest reviewed-oblique tilt (10.61 deg).
    'tilt_deg_pass': 10.60,
    # from this tilt every reviewed genuine photo (11 photos, 3 watches) put markers outside the strict genuine limits
    'tilt_deg_fail': 13.29,
    'edge_ellipse_ratio_min': 0.981,        # app's dial-edge ellipse (independent of the minute track)
    'v_gradient': (0.9743, 1.012),          # scale at 12 / at 6 (perspective gradient, top-bottom)
    'h_gradient': (0.9893, 1.017),          # scale at 3 / at 9 (left-right)
    'sharpness_min': 1963.0,                # harvester dial sharpness (Laplacian variance at 512 px)
    'crushed_max': 0.7502, 'blown_max': 0.1004, 'glare_max': 0.1289,
}
# existing documented rejection bounds (tools/dataset_harvester/harvester/config.py Thresholds)
HARVESTER = {'sharpness_min': 25.0, 'crushed_max': 0.95, 'blown_max': 0.25, 'glare_max': 0.30}
APP_MIN_DIAL_RADIUS_PX = 200   # frozen app reliability floor (AlignmentDecision.MIN_DIAL_RADIUS_PX); unchanged
# Archive-only admission floor (owner, 21 Oct 2026): 250 px dial radius. Resampling evidence (FACE-ON-VALIDATION.md):
# 95th-percentile in/out change 0.11-0.13 % R at 250 px against 0.14-0.17 at 200 px. Not an app requirement.
MIN_DIAL_RADIUS_PX = 250

# The app's soft edge-fit warnings (MetalFit, ClearlyOff uncertain text), as classes. Evidence on reviewed genuine
# markers (FACE-ON-VALIDATION.md, section "Marker reliability"): 'inconsistent' alone (part of the edge shadowed or
# reflecting) measures like a reliable marker (Mann-Whitney p = 0.55, 0 of 43 outside the strict genuine limits);
# 'rough' shifts the readings (p = 0.001) and rough + inconsistent puts 17 % outside them. Only 'inconsistent' is
# harmless; every other or unknown warning makes the marker unreliable for the calibration archive.
SOFT_CLASSES = (('hidden', re.compile(r'of its outline is visible, the rest is reconstructed')),
                ('rough', re.compile(r'its visible edge is rough')),
                ('inconsistent', re.compile(r'\d+% of its visible edge is inconsistent')),
                ('low_resolution', re.compile(r"resolution is low")))
HARMLESS = {'inconsistent'}

# Marker outline coverage envelope (reliable genuine round markers, CI run 39673334481; FACE-ON-VALIDATION.md): readings
# with >= 90 % of the outline seen are indistinguishable from fully seen ones (Mann-Whitney p = 0.45 / 0.19); below
# 80 % they shift (p = 0.014 at 70-80 %, 4e-6 at 50-70 %; almost all the minute hand at 10:10); 80-90 % is a weak,
# not significant trend (p = 0.14) and is withheld as untested. The app's own 50 % reconstruction bound
# (MetalFit.MIN_COVER) let a bar leaving 50-52 % of the outline visible through.
COVER_PASS, COVER_FAIL = 0.90, 0.80


def soft_classes(text):
    """Classes of one marker's soft warnings; an unrecognised clause is 'other' (never harmless)."""
    out = set()
    for clause in filter(None, (c.strip() for c in str(text or '').split(';'))):
        hit = [name for name, rx in SOFT_CLASSES if rx.search(clause)]
        out.update(hit or ['other'])
    return out


def _num(v):
    return v if isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) else None


def _band(v, ok, bad):
    """True inside the validated envelope, False beyond a failure bound, None (untested) otherwise or when missing."""
    if v is None:
        return None
    if ok(v):
        return True
    return False if bad(v) else None


def evaluate(s):
    """Archive checks and per-marker availability from one summarise() record."""
    e = ENVELOPE
    checks = {}
    pose = bool(s.get('pose_valid')) and bool(s.get('projective_accepted'))
    tilt, ell = _num(s.get('tilt_deg')), _num(s.get('edge_ellipse_ratio'))
    vg, hg = _num(s.get('v_gradient')), _num(s.get('h_gradient'))
    if not pose:
        # the production analysis could not register the dial: nothing about the pose is known
        for k in ('face_on_ellipse', 'face_on_rehaut_vertical', 'face_on_rehaut_horizontal', 'face_on_marker_lattice', 'landmarks'):
            checks[k] = None
    else:
        tilt_ok = _band(tilt, lambda v: v <= e['tilt_deg_pass'], lambda v: v >= e['tilt_deg_fail'])
        if tilt_ok is False:
            checks['face_on_ellipse'] = False
        elif tilt_ok is None or ell is None:
            checks['face_on_ellipse'] = None
        else:   # the independent dial-edge ellipse must agree; outside its envelope it is untested
            checks['face_on_ellipse'] = True if ell >= e['edge_ellipse_ratio_min'] else None
        checks['face_on_rehaut_vertical'] = _band(vg, lambda v: e['v_gradient'][0] <= v <= e['v_gradient'][1], lambda v: False)
        checks['face_on_rehaut_horizontal'] = _band(hg, lambda v: e['h_gradient'][0] <= v <= e['h_gradient'][1], lambda v: False)
        # the hour markers confirmed the 12 direction the minute-track registration chose
        checks['face_on_marker_lattice'] = s.get('marker_branch_turn') == 0
        checks['landmarks'] = s.get('hour_lines') == 12 and s.get('marker_branch_turn') == 0
    margin = _num(s.get('dial_margin_r'))
    checks['complete_dial'] = None if margin is None else margin >= 0
    checks['complete_minute_track'] = None if margin is None or s.get('hour_lines') is None else \
        (margin >= 0 and s.get('hour_lines') == 12)
    rpx = _num(s.get('rpx'))
    checks['original_resolution'] = None if rpx is None else rpx >= MIN_DIAL_RADIUS_PX   # archive floor, 250 px
    h = HARVESTER
    checks['sharp_boundaries'] = _band(_num(s.get('sharpness')), lambda v: v >= e['sharpness_min'], lambda v: v < h['sharpness_min'])
    crushed, blown = _num(s.get('crushed_fraction')), _num(s.get('blown_fraction'))
    if crushed is None or blown is None:
        checks['exposure'] = None
    elif crushed > h['crushed_max'] or blown > h['blown_max']:
        checks['exposure'] = False
    else:
        checks['exposure'] = True if crushed <= e['crushed_max'] and blown <= e['blown_max'] else None
    checks['glare'] = _band(_num(s.get('glare_fraction')), lambda v: v <= e['glare_max'], lambda v: v > h['glare_max'])
    # reflections on the markers: the app withholds a marker it sees glare or a reflection on (Alpha99 interference)
    withheld = s.get('withheld') or {}
    glary = [hr for hr, why in withheld.items() if any(w in str(why).lower() for w in ('glare', 'reflect', 'bright'))]
    checks['reflections'] = None if not s.get('required') else not glary
    markers = {}
    hidden = set(s.get('partly_hidden') or ())
    hands = set(s.get('hand_confirmed') or ())
    reliable = set(s.get('reliable') or ())
    soft = {int(k): v for k, v in (s.get('soft') or {}).items()}
    for hr in s.get('required') or ():
        cov = _num((s.get('cover') or {}).get(hr, (s.get('cover') or {}).get(str(hr))))
        if hr in hidden or hr in hands or (cov is not None and cov < COVER_FAIL):
            # outline mostly hidden, or the app's own hand check found a hand at it: obstructed
            markers[str(hr)] = False
        elif hr in (s.get('credible') or ()):
            # reliable, or its only soft warning is the harmless 'inconsistent' (reflection) class
            ok = hr in reliable or bool(soft.get(hr)) and soft_classes(soft[hr]) <= HARMLESS
            # a clean reading still needs its outline inside the validated coverage envelope (untested otherwise)
            markers[str(hr)] = (None if cov is None or cov < COVER_PASS else True) if ok else False
        else:
            markers[str(hr)] = False if (hr in withheld or str(hr) in withheld) else None
    return checks, markers


class FaceOnAnalyzer:
    """Screener analyzer for profile face-on-v3: the production analysis of the decoded original (read-only)."""

    def __init__(self, driver=None):
        self.driver = driver or Driver()

    def __call__(self, img, family, purpose):
        model = MODELS.get(family)
        if not model:
            return {'profile': 'unsupported-layout', 'checks': {}}
        with tempfile.TemporaryDirectory(prefix='watch-align-faceon-') as work:
            p = Path(work) / 'analysis.png'
            img.save(p)  # analysis copy only (lossless); the archive keeps the original bytes
            ev = self.driver.run(model, [('analysis', str(p))]).get('analysis')
        if ev is None:
            raise RuntimeError('no evidence')
        s = summarise(ev, img)
        checks, markers = evaluate(s)
        return {'profile': PROFILE + '/' + model, 'checks': checks, 'required_marker_ids': list(REQUIRED[model]),
                'markers': markers, 'evidence': s}


# Profiles promoted after the validation in docs/image-archive/FACE-ON-VALIDATION.md. Application-controlled: never
# taken from a candidate, manifest, environment variable or harvester flag.
VALIDATED_PROFILES = frozenset(PROFILE + '/' + m for m in REQUIRED)


def production_screener(driver=None):
    from .screening import Screener
    return Screener(FaceOnAnalyzer(driver), VALIDATED_PROFILES)
