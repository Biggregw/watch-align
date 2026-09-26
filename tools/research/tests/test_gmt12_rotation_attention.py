import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from gmt12_rotation_attention import (
    RotationAttention,
    assess_rotation_attention,
    implied_endpoint_rise_px,
)


def test_two_degrees_across_large_marker_is_highlighted():
    r = assess_rotation_attention(2.0, 70.0)
    assert r.attention == RotationAttention.CHECK
    assert r.visible_rise_px > 2.0


def test_same_angle_at_tiny_scale_is_not_overclaimed():
    r = assess_rotation_attention(2.0, 20.0)
    assert r.visible_rise_px < 0.75
    assert r.attention == RotationAttention.CLEAR


def test_one_degree_can_still_be_visible_when_marker_is_large():
    r = assess_rotation_attention(1.0, 100.0)
    assert r.visible_rise_px > 1.5
    assert r.attention == RotationAttention.CHECK


def test_four_degrees_is_strong_when_resolved():
    r = assess_rotation_attention(-4.0, 50.0)
    assert r.attention == RotationAttention.STRONG


def test_bad_pose_never_silently_clears_small_rotation():
    r = assess_rotation_attention(0.8, 70.0, pose_reliable=False)
    assert r.attention == RotationAttention.UNASSESSABLE


def test_bad_pose_still_surfaces_gross_visible_skew_for_inspection():
    r = assess_rotation_attention(5.0, 70.0, pose_reliable=False)
    assert r.attention == RotationAttention.CHECK
    assert "pose" in r.reason


def test_endpoint_rise_matches_geometry():
    got = implied_endpoint_rise_px(2.0, 60.0)
    assert math.isclose(got, math.tan(math.radians(2.0)) * 60.0, rel_tol=1e-9)
