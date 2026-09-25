"""Unit tests for the pure ratio-computation logic in phase_a_landmarks.py --
the part most prone to a silent sign/off-by-one error and least likely to be
caught just by eyeballing the diagnostic overlay. Detection itself (Hough
circle, Otsu blob isolation) needs a real photo and is exercised by the CI
harness run against the selected Phase A image instead.

Run from the repository root:
    PYTHONPATH=tools/research pytest tools/research/tests/test_phase_a_landmarks.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import phase_a_landmarks as pal


def lm(name, x, y, assessable=True):
    return pal.Landmark(name, x, y, assessable, 1.0 if assessable else None, "" if assessable else "test-not-assessable")


def test_ratios_on_a_simple_symmetric_triangle():
    landmarks = {
        "minute_track_60": lm("minute_track_60", 100.0, 0.0),
        "coronet": lm("coronet", 100.0, 100.0),
        "triangle_apex": lm("triangle_apex", 100.0, 20.0),
        "triangle_base_left": lm("triangle_base_left", 95.0, 40.0),
        "triangle_base_right": lm("triangle_base_right", 105.0, 40.0),
    }
    ratios, reasons = pal.compute_ratios(landmarks, axis_cx=100.0)
    assert ratios["apex_position_in_interval"] == (20.0 - 0.0) / 100.0
    assert ratios["base_position_in_interval"] == (40.0 - 0.0) / 100.0
    assert ratios["triangle_height_ratio"] == (40.0 - 20.0) / 100.0
    assert ratios["base_width_over_height"] == (105.0 - 95.0) / (40.0 - 20.0)
    # Base midpoint x = 100.0 = axis_cx -> perfectly centred, zero displacement.
    assert ratios["horizontal_displacement_normalized"] == 0.0
    assert all(r == "" for r in reasons.values())


def test_horizontal_displacement_sign_matches_base_offset_direction():
    landmarks = {
        "minute_track_60": lm("minute_track_60", 100.0, 0.0),
        "coronet": lm("coronet", 100.0, 100.0),
        "triangle_apex": lm("triangle_apex", 100.0, 20.0),
        "triangle_base_left": lm("triangle_base_left", 100.0, 40.0),
        "triangle_base_right": lm("triangle_base_right", 110.0, 40.0),
    }
    ratios, _ = pal.compute_ratios(landmarks, axis_cx=100.0)
    # base_mid_x = 105 > axis_cx=100 -> positive displacement.
    assert ratios["horizontal_displacement_normalized"] > 0.0


def test_missing_minute_track_only_blocks_interval_ratios():
    landmarks = {
        "minute_track_60": lm("minute_track_60", None, None, assessable=False),
        "coronet": lm("coronet", 100.0, 100.0),
        "triangle_apex": lm("triangle_apex", 100.0, 20.0),
        "triangle_base_left": lm("triangle_base_left", 95.0, 40.0),
        "triangle_base_right": lm("triangle_base_right", 105.0, 40.0),
    }
    ratios, reasons = pal.compute_ratios(landmarks, axis_cx=100.0)
    assert ratios["apex_position_in_interval"] is None
    assert ratios["base_position_in_interval"] is None
    assert "not assessable" in reasons["apex_position_in_interval"]
    # Shape ratios (independent of the coronet-to-60 interval) must still compute.
    assert ratios["base_width_over_height"] is not None
    assert ratios["horizontal_displacement_normalized"] is not None
    # triangle_height_ratio DOES depend on the interval as its denominator.
    assert ratios["triangle_height_ratio"] is None


def test_missing_triangle_blocks_everything_downstream():
    landmarks = {
        "minute_track_60": lm("minute_track_60", 100.0, 0.0),
        "coronet": lm("coronet", 100.0, 100.0),
        "triangle_apex": lm("triangle_apex", None, None, assessable=False),
        "triangle_base_left": lm("triangle_base_left", 95.0, 40.0),
        "triangle_base_right": lm("triangle_base_right", 105.0, 40.0),
    }
    ratios, reasons = pal.compute_ratios(landmarks, axis_cx=100.0)
    assert ratios["apex_position_in_interval"] is None
    assert ratios["triangle_height_ratio"] is None
    assert ratios["base_width_over_height"] is None
    assert ratios["horizontal_displacement_normalized"] is None
    # base_position_in_interval only needs the base corners, not the apex.
    assert ratios["base_position_in_interval"] is not None


def test_degenerate_interval_reported_not_silently_dropped():
    landmarks = {
        "minute_track_60": lm("minute_track_60", 100.0, 50.0),
        "coronet": lm("coronet", 100.0, 50.0),  # same y as minute_track -> zero interval
        "triangle_apex": lm("triangle_apex", 100.0, 55.0),
        "triangle_base_left": lm("triangle_base_left", 95.0, 60.0),
        "triangle_base_right": lm("triangle_base_right", 105.0, 60.0),
    }
    ratios, reasons = pal.compute_ratios(landmarks, axis_cx=100.0)
    assert ratios["apex_position_in_interval"] is None
    assert "degenerate interval" in reasons["apex_position_in_interval"]


def test_otsu_threshold_separates_two_clear_clusters():
    import numpy as np
    profile = np.array([10.0] * 20 + [240.0] * 20)
    t = pal._otsu_threshold_1d(profile)
    assert 10.0 < t < 240.0
