import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from human_qc_geometry import BatonGeometry, Point, measure_baton


def ideal_6oclock():
    # Vertical long axis (radial=image-y), mirrors test_human_qc_geometry's
    # Gmt12Geometry.ideal() shape: outer edge at y=130, inner point above it
    # (inward = smaller y, since the dial centre is above a 6 o'clock
    # marker), ticks below the outer edge (outward = larger y).
    return BatonGeometry(
        hour_position=6,
        baton_outer_left=Point(90, 130),
        baton_outer_right=Point(110, 130),
        baton_inner_mid=Point(100, 100),
        minute_inner_left=Point(80, 140),
        minute_inner_right=Point(120, 140),
        minute_marker_center=Point(100, 145),
    )


def ideal_9oclock():
    # Horizontal long axis (radial=image-x): the case measure_gmt12's
    # _x_on_line_at_y-based approach cannot handle (a horizontal axis line
    # makes dy=0, its explicit degenerate-line guard). Same relative
    # distances as ideal_6oclock (just radial=x, tangential=y instead of
    # radial=y, tangential=x), so it must produce numerically identical
    # measurements -- proving the orientation-agnostic design actually
    # works, not just that it doesn't crash.
    return BatonGeometry(
        hour_position=9,
        baton_outer_left=Point(130, 90),
        baton_outer_right=Point(130, 110),
        baton_inner_mid=Point(160, 100),
        minute_inner_left=Point(120, 80),
        minute_inner_right=Point(120, 120),
        minute_marker_center=Point(115, 100),
    )


def test_ideal_6oclock_local_alignment():
    m = measure_baton(ideal_6oclock())
    assert math.isclose(abs(m.radial_clearance_over_baton_width), 0.5, abs_tol=1e-9)
    assert math.isclose(m.tangential_offset_over_baton_width, 0.0, abs_tol=1e-9)
    assert math.isclose(m.rotation_deg, 0.0, abs_tol=1e-9)
    assert math.isclose(m.left_clearance_over_baton_width, math.sqrt(200) / 20, abs_tol=1e-9)
    assert math.isclose(m.right_clearance_over_baton_width, math.sqrt(200) / 20, abs_tol=1e-9)
    assert math.isclose(m.side_clearance_asymmetry, 0.0, abs_tol=1e-9)


def test_ideal_9oclock_matches_6oclock_numerically():
    # The core orientation-agnostic proof: a horizontal-axis marker in the
    # same relative geometry gives the SAME measurements as the vertical-
    # axis one, not a crash or a silently wrong number.
    m6 = measure_baton(ideal_6oclock())
    m9 = measure_baton(ideal_9oclock())
    assert math.isclose(abs(m9.radial_clearance_over_baton_width), abs(m6.radial_clearance_over_baton_width), abs_tol=1e-9)
    assert math.isclose(m9.tangential_offset_over_baton_width, m6.tangential_offset_over_baton_width, abs_tol=1e-9)
    assert math.isclose(m9.rotation_deg, m6.rotation_deg, abs_tol=1e-9)
    assert math.isclose(m9.left_clearance_over_baton_width, m6.left_clearance_over_baton_width, abs_tol=1e-9)
    assert math.isclose(m9.right_clearance_over_baton_width, m6.right_clearance_over_baton_width, abs_tol=1e-9)


def test_tangential_offset_uses_observed_marker_not_dial_centre():
    g = ideal_9oclock()
    shifted = BatonGeometry(
        g.hour_position, g.baton_outer_left, g.baton_outer_right, g.baton_inner_mid,
        g.minute_inner_left, g.minute_inner_right, Point(115, 104),
    )
    m = measure_baton(shifted)
    assert math.isclose(m.tangential_offset_over_baton_width, 0.2, abs_tol=1e-9)


def test_baton_rotation_is_relative_to_local_minute_track():
    g = ideal_9oclock()
    tilted = BatonGeometry(
        g.hour_position,
        Point(128, 90), Point(132, 110), g.baton_inner_mid,
        g.minute_inner_left, g.minute_inner_right, g.minute_marker_center,
    )
    m = measure_baton(tilted)
    # Check against the primitive both measure_baton and measure_gmt12
    # share: the signed smallest-angle difference between the two lines.
    from human_qc_geometry import _parallel_angle_difference_deg
    expected = _parallel_angle_difference_deg(Point(128, 90), Point(132, 110), g.minute_inner_left, g.minute_inner_right)
    assert math.isclose(m.rotation_deg, expected, abs_tol=1e-9)
    assert abs(m.rotation_deg) > 1.0  # sanity: the tilt must actually register


def test_degenerate_outer_edge_raises():
    g = ideal_6oclock()
    degenerate = BatonGeometry(
        g.hour_position, Point(100, 130), Point(100, 130), g.baton_inner_mid,
        g.minute_inner_left, g.minute_inner_right, g.minute_marker_center,
    )
    try:
        measure_baton(degenerate)
        assert False, "expected ValueError"
    except ValueError:
        pass
