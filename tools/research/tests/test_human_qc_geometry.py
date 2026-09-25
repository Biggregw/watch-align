import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from human_qc_geometry import Gmt12Geometry, Point, measure_gmt12


def ideal():
    return Gmt12Geometry(
        triangle_top_left=Point(90, 130),
        triangle_top_right=Point(110, 130),
        triangle_tip=Point(100, 160),
        minute_inner_left=Point(80, 120),
        minute_inner_right=Point(120, 120),
        minute_60_center=Point(100, 115),
    )


def test_ideal_local_alignment():
    m = measure_gmt12(ideal())
    assert math.isclose(abs(m.top_clearance_over_triangle_width), 0.5, abs_tol=1e-9)
    assert math.isclose(m.horizontal_offset_over_triangle_width, 0.0, abs_tol=1e-9)
    assert math.isclose(m.rotation_deg, 0.0, abs_tol=1e-9)


def test_horizontal_uses_observed_60_marker_not_dial_centre():
    g = ideal()
    shifted = Gmt12Geometry(
        g.triangle_top_left, g.triangle_top_right, g.triangle_tip,
        g.minute_inner_left, g.minute_inner_right, Point(104, 115)
    )
    m = measure_gmt12(shifted)
    assert math.isclose(m.horizontal_offset_over_triangle_width, -0.2, abs_tol=1e-9)


def test_triangle_rotation_is_relative_to_local_minute_track():
    g = ideal()
    tilted = Gmt12Geometry(
        Point(90, 128), Point(110, 132), Point(100, 160),
        g.minute_inner_left, g.minute_inner_right, g.minute_60_center
    )
    m = measure_gmt12(tilted)
    assert math.isclose(m.rotation_deg, math.degrees(math.atan2(4, 20)), abs_tol=1e-9)


def test_common_image_roll_cancels_from_rotation():
    # Both local reference and triangle top have the same slope.  A global
    # horizontal reference would report a false tilt; the human-local rule
    # correctly reports zero relative rotation.
    g = Gmt12Geometry(
        triangle_top_left=Point(90, 128),
        triangle_top_right=Point(110, 132),
        triangle_tip=Point(94, 160),
        minute_inner_left=Point(80, 116),
        minute_inner_right=Point(120, 124),
        minute_60_center=Point(100, 115),
    )
    m = measure_gmt12(g)
    assert math.isclose(m.rotation_deg, 0.0, abs_tol=1e-9)
