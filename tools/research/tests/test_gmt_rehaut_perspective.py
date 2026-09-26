import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from gmt_rehaut_perspective import (
    _first_harmonic_width,
    _pick_rehaut_edges,
    _safe_asym,
)


def test_first_harmonic_recovers_vertical_and_horizontal_rehaut_signal():
    theta = np.linspace(0.0, 2.0 * math.pi, 720, endpoint=False)
    # 0 deg=right, 90 deg=bottom, 180 deg=left, 270 deg=top.
    width = 20.0 + 4.0 * np.cos(theta) + 6.0 * np.sin(theta)

    mean_w, amp, widest, top, bottom, left, right = _first_harmonic_width(
        theta, width
    )

    assert math.isclose(mean_w, 20.0, abs_tol=1e-9)
    assert math.isclose(right, 24.0, abs_tol=1e-9)
    assert math.isclose(left, 16.0, abs_tol=1e-9)
    assert math.isclose(bottom, 26.0, abs_tol=1e-9)
    assert math.isclose(top, 14.0, abs_tol=1e-9)
    assert math.isclose(amp, math.hypot(4.0, 6.0), abs_tol=1e-9)
    assert math.isclose(widest, math.degrees(math.atan2(6.0, 4.0)), abs_tol=1e-9)

    assert math.isclose(_safe_asym(top, bottom), -0.30, abs_tol=1e-9)
    assert math.isclose(_safe_asym(right, left), 0.20, abs_tol=1e-9)


def test_rehaut_edge_pair_prefers_first_strong_transition_after_dark_dial():
    seed_r = 200.0
    n = 260
    grad = np.zeros(n, dtype=float)

    # Persistent physical transitions. Inner rehaut edge at 150, outer edge at
    # 163. A later, stronger crystal/bezel edge at 178 must not replace the
    # first valid outer edge.
    grad[150] = 60.0
    grad[163] = 75.0
    grad[178] = 130.0

    radial_mean = np.full(n, 35.0, dtype=float)
    radial_mean[150:] = 115.0

    pair = _pick_rehaut_edges(grad, radial_mean, seed_r)
    assert pair is not None
    inner, outer, _, _ = pair
    assert inner == 150.0
    assert outer == 163.0


def test_asymmetry_is_scale_invariant():
    assert math.isclose(_safe_asym(100.0, 50.0), 1.0 / 3.0)
    assert math.isclose(_safe_asym(200.0, 100.0), 1.0 / 3.0)
