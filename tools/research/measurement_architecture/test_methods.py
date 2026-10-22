"""Geometry tests of the competing methods on synthetic records with exactly known marker positions (no images)."""
import math

import numpy as np
import pytest

from tools.research.measurement_architecture import methods as M

HOURS = {12: 'triangle', 3: 'baton', 6: 'baton', 9: 'baton', 1: 'round', 2: 'round', 4: 'round', 5: 'round',
         7: 'round', 8: 'round', 10: 'round', 11: 'round'}


def record(ring=(0, 0, 0, 0), offsets=None, H=None):
    """markers at r = 0.75 (fixed reference), every marker moved by the ring (tx, ty, scale, rot) plus its own offset"""
    offsets = offsets or {}
    tx, ty, s, ph = ring
    marks = []
    for h, shape in HOURS.items():
        a = math.radians(30 * (h % 12))
        fix = np.array([0.75 * math.sin(a), -0.75 * math.cos(a)])
        rot = np.array([[math.cos(ph), -math.sin(ph)], [math.sin(ph), math.cos(ph)]])
        c = (1 + s) * (rot @ fix) + np.array([tx, ty]) + np.array(offsets.get(h, (0, 0)))
        marks.append({'h': h, 'shape': shape, 'cred': True, 'rel': True, 'cov': 1.0, 'hand': False, 'unc': '',
                      'cO': list(c), 'fix': list(fix), 'cG': list(fix), 'ft': math.degrees(ph) if shape != 'round' else None,
                      'loo': [0, 0, 0, 0], 'side': None, 'inout': None, 'tilt': None})
    H = H if H is not None else [300, 0, 500, 0, 300, 500, 0, 0, 1]
    return {'rpx': 300.0, 'H': H, 'markers': marks, 'lines': {}, 'ticks': None}


def test_single_marker_offset_is_recovered_and_does_not_spill_with_the_biweight_ring():
    t4 = M.tangent(4)
    out, ring = M.measure(record(offsets={4: tuple(0.005 * t4)}))
    for name in ('F', 'LR', 'LB', 'LT'):
        assert out[4][name][0] == pytest.approx(0.5, abs=0.02)
    assert max(abs(out[h]['LB'][0]) for h in out if h != 4) < 0.005
    assert max(abs(out[h]['F'][0]) for h in out if h != 4) < 1e-9


def test_a_whole_ring_move_is_absolute_not_marker_level():
    out, ring = M.measure(record(ring=(0.004, -0.002, 0.003, math.radians(0.4))))
    for h, r in out.items():
        assert abs(r['LR'][0]) < 1e-3 and abs(r['LR'][1]) < 1e-3 and abs(r['LB'][0]) < 1e-3
    assert ring['t'] == pytest.approx([0.4, -0.2], abs=1e-6)
    assert ring['s'] == pytest.approx(0.3, abs=0.005) and ring['rot_deg'] == pytest.approx(0.4, abs=0.005)
    assert max(abs(r['F'][0]) for r in out.values()) > 0.3


def test_parallax_direction_and_tilt_from_the_homography():
    # depth grows towards -y (the 12 side): the 12 side is farther, raised markers shift towards 12
    t, far = M.pose([300, 0, 500, 0, 300, 500, 0, -0.05, 1])
    assert far == pytest.approx([0, -1], abs=1e-9) and t > 0
    out, ring = M.measure(record(ring=(0, -0.002, 0, 0), H=[300, 0, 500, 0, 300, 500, 0, -0.05, 1]), k=0.0215)
    assert ring['t_minus_parallax'] is not None


def test_ineligible_markers_never_shape_the_ring():
    rec = record(offsets={2: (0.02, 0.02)})
    for m in rec['markers']:
        if m['h'] == 2:
            m['hand'] = True
    out, ring = M.measure(rec)
    assert out[2]['status'] == 'OBSTRUCTED'
    assert max(abs(out[h]['LR'][0]) for h in out if h != 2) < 1e-6
