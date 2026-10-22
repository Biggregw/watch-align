"""Research calibrator configs: marker_geometry_v1 under the marker-mask-v1 policy, one per layout.

calibration/models/124060.json (the production calibrator config) does not declare marker_eligibility, so the
calibrator would run it unmasked. A research run must apply the archive masks, so it uses these configs instead; they
are built here, not committed as calibration assets, and nothing is deployed from them.
"""
from __future__ import annotations

from tools.image_archive.faceon import REQUIRED

# the calibrator model key per layout (marker_mask.layout maps it back to the layout)
MODEL_KEY = {'submariner_124060': '124060', 'submariner_126610': '126610', 'gmt_126710': 'gmt_126710'}
RING = ('ring.place_x', 'ring.place_y', 'ring.rot_deg', 'ring.scale_pct')


def metrics(layout):
    out = []
    for h in REQUIRED[layout]:
        out += ['marker.%d.tang' % h, 'marker.%d.rad' % h]
        if h in (3, 6, 9, 12):
            out.append('marker.%d.tilt' % h)
    return out + list(RING)


def config(layout):
    return {'model': MODEL_KEY[layout], 'family': layout, 'measurement_adapter': 'marker_geometry_v1',
            'marker_eligibility': {'policy': 'marker-mask-v1'},
            'calibration_metrics': [{'metric': m, 'sided': 'two'} for m in metrics(layout)],
            'note': 'RESEARCH ONLY (tools/research/genuine_calibration): masked marker_geometry_v1 measurement'}
