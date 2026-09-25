import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from gmt12_auto_landmarks import _circle_tangent_landmarks
from human_qc_geometry import Gmt12Geometry, Point, measure_gmt12


def angle_deg(a, b):
    return math.degrees(math.atan2(b[1]-a[1], b[0]-a[0]))


def test_glare_shortened_tick_does_not_manufacture_eight_degree_rotation():
    # Real failure shape observed during blind QC: raw detected 59/1 endpoint
    # heights implied ~8.63 degrees although the physical 12 marker was upright.
    l=(316.0,497.2); c=(336.0,498.4); r=(356.0,503.27)
    assert abs(angle_deg(l,r)) > 8.0
    fixed=_circle_tangent_landmarks(l,c,r,335.0,700.0)
    assert fixed is not None
    fl,fc,fr=fixed
    assert abs(angle_deg(fl,fr)) < 1.0
    assert fc == c
    assert fl[0] == l[0] and fr[0] == r[0]


def test_common_camera_roll_is_preserved_by_circle_tangent_reference():
    # A rolled watch must not be flattened to horizontal. Put 60 on a radius
    # whose local tangent is +7 degrees and verify that orientation survives.
    cx,cy=300.0,700.0
    y60=500.0
    slope=math.tan(math.radians(7.0))
    x60=cx-slope*(y60-cy)
    l=(x60-20,y60-9); c=(x60,y60); r=(x60+20,y60+11)
    fixed=_circle_tangent_landmarks(l,c,r,cx,cy)
    assert fixed is not None
    fl,_,fr=fixed
    assert math.isclose(angle_deg(fl,fr),7.0,abs_tol=.05)


def test_implausible_circle_to_60_geometry_is_rejected():
    # Bad dial-circle/60 associations should become unassessable, not a large
    # confident marker-rotation finding.
    assert _circle_tangent_landmarks((0,0),(100,100),(200,200),0,99) is None


def _geometry_with_triangle_rotation(rotation_deg, triangle_shift_x=0.0, watch_roll_deg=0.0):
    """Synthetic local geometry with independent marker rotation, translation and camera roll."""
    cx,cy=300.0,700.0
    y60=500.0
    tangent=math.radians(watch_roll_deg)
    slope=math.tan(tangent)
    x60=cx-slope*(y60-cy)
    pitch=20.0
    raw_l=(x60-pitch,y60-5.0)
    raw_c=(x60,y60)
    raw_r=(x60+pitch,y60+7.0)
    fixed=_circle_tangent_landmarks(raw_l,raw_c,raw_r,cx,cy)
    assert fixed is not None
    ml,m60,mr=fixed

    width=60.0
    tri_angle=tangent+math.radians(rotation_deg)
    ux,uy=math.cos(tri_angle),math.sin(tri_angle)
    midx=x60+triangle_shift_x
    midy=540.0+slope*(midx-x60)
    tl=Point(midx-.5*width*ux,midy-.5*width*uy)
    tr=Point(midx+.5*width*ux,midy+.5*width*uy)
    # Triangle axis is perpendicular to its top edge and points inward/downward.
    nx,ny=-uy,ux
    tip=Point(midx+70.0*nx,midy+70.0*ny)
    return Gmt12Geometry(tl,tr,tip,Point(*ml),Point(*mr),Point(*m60))


def test_true_two_degree_marker_rotation_survives_tick_regularisation():
    m=measure_gmt12(_geometry_with_triangle_rotation(2.0))
    assert math.isclose(m.rotation_deg,2.0,abs_tol=.05)


def test_marker_rotation_is_relative_to_watch_roll_not_image_horizontal():
    m=measure_gmt12(_geometry_with_triangle_rotation(2.0,watch_roll_deg=7.0))
    assert math.isclose(m.rotation_deg,2.0,abs_tol=.05)


def test_lateral_translation_does_not_turn_into_rotation():
    m=measure_gmt12(_geometry_with_triangle_rotation(0.0,triangle_shift_x=12.0,watch_roll_deg=-5.0))
    assert math.isclose(m.rotation_deg,0.0,abs_tol=.05)
    assert abs(m.horizontal_offset_over_triangle_width) > .10
