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
import os
import subprocess
import tempfile
from pathlib import Path

MODELS = {'gmt_126710': 'gmt_126710', '126710BLNR': 'gmt_126710', '126710BLRO': 'gmt_126710',
          'GMT-Master II': 'gmt_126710',
          '124060': 'submariner_124060', 'submariner_124060': 'submariner_124060',
          '126610': 'submariner_126610', '126610LN': 'submariner_126610', '126610LV': 'submariner_126610',
          '126610LN/LV': 'submariner_126610', '126613LB': 'submariner_126610', '126613LN': 'submariner_126610',
          '126613': 'submariner_126610', '126618LB': 'submariner_126610', 'submariner_126610': 'submariner_126610'}

# 12 hour markers on No-Date dials; Date dials have no 3 (the date window is not a marker)
REQUIRED = {'gmt_126710': tuple(h for h in range(1, 13) if h != 3),
            'submariner_126610': tuple(h for h in range(1, 13) if h != 3),
            'submariner_124060': tuple(range(1, 13))}

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
    out['soft'] = {hr: markers[hr].get('uncertain') for hr in required
                   if hr in markers and markers[hr].get('credible') and not markers[hr].get('reliable')}
    out['markers'] = {hr: {k: markers[hr].get(k) for k in ('state', 'side_pct', 'inout_pct', 'tilt_deg', 'pos_pct', 'gap_pct')}
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
