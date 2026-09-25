"""Controlled defect validation for the human-defined GMT 12 measurements.

This is deliberately a geometry-contract test, not a detector test.  Starting
from one neutral local geometry, introduce exactly one known defect at a time
and prove the corresponding measurement responds in the expected direction
without inventing QC tolerances.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from human_qc_geometry import Gmt12Geometry, Point, measure_gmt12


def control():
    # Triangle top width = 100 units.  Top gap = 16 units, representative only
    # of the already-observed ~0.15-0.17 control relationship.  It is NOT a
    # tolerance or claimed Rolex specification.
    return Gmt12Geometry(
        triangle_top_left=Point(50, 116),
        triangle_top_right=Point(150, 116),
        triangle_tip=Point(100, 216),
        minute_inner_left=Point(20, 100),
        minute_inner_right=Point(180, 100),
        minute_60_center=Point(100, 100),
    )


def translate_triangle(g, dx=0.0, dy=0.0):
    def t(p): return Point(p.x + dx, p.y + dy)
    return Gmt12Geometry(t(g.triangle_top_left), t(g.triangle_top_right),
                         t(g.triangle_tip), g.minute_inner_left,
                         g.minute_inner_right, g.minute_60_center)


def rotate_triangle(g, degrees):
    # Rotate the complete triangle around its top midpoint.  The minute-track
    # reference is untouched, so this is a known marker-rotation defect.
    c = Point((g.triangle_top_left.x + g.triangle_top_right.x) / 2,
              (g.triangle_top_left.y + g.triangle_top_right.y) / 2)
    a = math.radians(degrees); ca, sa = math.cos(a), math.sin(a)
    def r(p):
        x, y = p.x-c.x, p.y-c.y
        return Point(c.x + ca*x - sa*y, c.y + sa*x + ca*y)
    return Gmt12Geometry(r(g.triangle_top_left), r(g.triangle_top_right),
                         r(g.triangle_tip), g.minute_inner_left,
                         g.minute_inner_right, g.minute_60_center)


def test_control_has_expected_local_relationships():
    m = measure_gmt12(control())
    assert math.isclose(m.top_clearance_over_triangle_width, .16, abs_tol=1e-12)
    assert math.isclose(m.horizontal_offset_over_triangle_width, 0, abs_tol=1e-12)
    assert math.isclose(m.rotation_deg, 0, abs_tol=1e-12)


def test_vertical_defect_changes_gap_monotonically_and_not_rotation_or_centring():
    g = control()
    values=[]
    for dy in (-12, -8, -4, 0, 4, 8, 12):
        m=measure_gmt12(translate_triangle(g, dy=dy))
        values.append(m.top_clearance_over_triangle_width)
        assert math.isclose(m.horizontal_offset_over_triangle_width, 0, abs_tol=1e-12)
        assert math.isclose(m.rotation_deg, 0, abs_tol=1e-12)
    assert values == sorted(values)
    # Since width is 100, each 4-unit vertical move must alter the ratio .04.
    for a,b in zip(values, values[1:]):
        assert math.isclose(b-a, .04, abs_tol=1e-12)


def test_horizontal_defect_changes_only_centring_linearly():
    g=control(); values=[]
    for dx in (-12, -8, -4, 0, 4, 8, 12):
        m=measure_gmt12(translate_triangle(g, dx=dx))
        values.append(m.horizontal_offset_over_triangle_width)
        assert math.isclose(m.top_clearance_over_triangle_width, .16, abs_tol=1e-12)
        assert math.isclose(m.rotation_deg, 0, abs_tol=1e-12)
    assert values == sorted(values)
    for a,b in zip(values, values[1:]):
        assert math.isclose(b-a, .04, abs_tol=1e-12)


def test_rotation_defect_is_reported_against_local_minute_track():
    g=control()
    for degrees in (-6, -3, -1, 0, 1, 3, 6):
        m=measure_gmt12(rotate_triangle(g,degrees))
        assert math.isclose(m.rotation_deg, degrees, abs_tol=1e-10)


def test_common_roll_is_not_a_marker_rotation_defect():
    # Rotate every observed physical landmark together, equivalent to camera
    # roll.  Relative geometry must remain unchanged.
    g=control(); c=Point(100,100); degrees=7; a=math.radians(degrees)
    ca,sa=math.cos(a),math.sin(a)
    def r(p):
        x,y=p.x-c.x,p.y-c.y
        return Point(c.x+ca*x-sa*y,c.y+sa*x+ca*y)
    rolled=Gmt12Geometry(*(r(p) for p in (
        g.triangle_top_left,g.triangle_top_right,g.triangle_tip,
        g.minute_inner_left,g.minute_inner_right,g.minute_60_center)))
    a0=measure_gmt12(g); a1=measure_gmt12(rolled)
    assert math.isclose(a1.top_clearance_over_triangle_width,
                        a0.top_clearance_over_triangle_width,abs_tol=1e-10)
    assert math.isclose(a1.horizontal_offset_over_triangle_width,
                        a0.horizontal_offset_over_triangle_width,abs_tol=1e-10)
    assert math.isclose(a1.rotation_deg,a0.rotation_deg,abs_tol=1e-10)
