import math
from pathlib import Path
import sys

import cv2
import numpy as np
import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from baton_auto_landmarks import _baton_candidate, _minute_ticks_at_angle
from dial_rotation import rotate_point_to_top, unrotate_point_from_top
from human_qc_geometry import BatonGeometry, Point, measure_baton


@pytest.mark.parametrize("angle_deg", [0, 90, 180, 270])
def test_smaller_x_after_rotation_matches_true_counterclockwise_direction(angle_deg):
    # Ground-truth check (independent of _baton_candidate/dial_rotation's
    # own internals) for the labelling claim in baton_auto_landmarks.py's
    # module docstring: evaluate two points a few degrees either side of
    # the marker on the actual clockwise-parameterised dial circle
    # (position(theta) = (cx + r*sin(theta), cy - r*cos(theta)), theta
    # clockwise from 12), then confirm the counter-clockwise one always
    # rotates to the smaller x -- for every supported angle, not just 12.
    cx, cy, r = 500.0, 500.0, 400.0
    shape = (1000, 1000)
    delta = math.radians(5)
    theta = math.radians(angle_deg)

    def pos(t):
        return Point(cx + r * math.sin(t), cy - r * math.cos(t))

    p_cw = rotate_point_to_top(pos(theta + delta), angle_deg, shape)
    p_ccw = rotate_point_to_top(pos(theta - delta), angle_deg, shape)
    assert p_ccw.x < p_cw.x


def _build_scene_in_rotated_frame(angle_deg, h2, w2, draw_fn):
    """Draw a scene in the easier-to-reason-about ROTATED ("marker at top")
    frame using draw_fn(gray2) -> None, then unrotate every lit pixel back
    to the physical ORIGINAL frame (proven correct by
    test_dial_rotation.py's 9-o'clock end-to-end test). Returns
    (gray_original, orig_shape).
    """
    gray2 = np.zeros((h2, w2), dtype=np.uint8)
    draw_fn(gray2)
    orig_shape = (w2, h2) if angle_deg in (90, 270) else (h2, w2)
    h, w = orig_shape
    gray = np.zeros((h, w), dtype=np.uint8)
    ys2, xs2 = np.nonzero(gray2)
    for y2, x2 in zip(ys2, xs2):
        p = unrotate_point_from_top(Point(float(x2), float(y2)), angle_deg, orig_shape)
        gray[int(round(p.y)), int(round(p.x))] = 255
    gray = cv2.dilate(gray, np.ones((3, 3), np.uint8))  # close 1px gaps left by point-wise mapping
    return gray, orig_shape


# Rotated-frame scene shared by the 6 and 9 o'clock tests: a baton
# (outer edge ~0.70r from centre, comfortably inside _baton_candidate's
# .30-.90r ROI band) plus 5 ticks further out still, at 30px pitch --
# realistic proportions, not the exact-r placement that turned out to sit
# outside the ROI (see debugging note in
# docs/research/gmt6_9_baton_scope_2026-09-28.md).
_CX2, _CY2, _R = 300.0, 700.0, 400.0


def _draw_baton_scene(gray2):
    cv2.rectangle(gray2, (285, 420), (315, 460), 255, -1)  # outer y=420, inner y=460
    # Ticks well clear of the baton's outer edge (420) -- too close and the
    # centre tick's dilated bbox (from the pointwise unrotate remap's 1px
    # gaps) merges with the baton blob into one shape the tick-width filter
    # then rejects, silently dropping the centre tick.
    for x in (240, 270, 300, 330, 360):
        cv2.line(gray2, (x, 350), (x, 374), 255, 3)


@pytest.mark.parametrize("angle_deg,hour_position", [(180, 6), (270, 9)])
def test_baton_candidate_finds_the_marker(angle_deg, hour_position):
    gray, orig_shape = _build_scene_in_rotated_frame(angle_deg, 800, 450, _draw_baton_scene)
    c = unrotate_point_from_top(Point(_CX2, _CY2), angle_deg, orig_shape)

    result = _baton_candidate(gray, c.x, c.y, _R, angle_deg)
    assert result is not None
    outer_left, outer_right, inner_mid = result

    # Map the known rotated-frame ground truth back for comparison.
    exp_outer_left = unrotate_point_from_top(Point(285.0, 420.0), angle_deg, orig_shape)
    exp_outer_right = unrotate_point_from_top(Point(315.0, 420.0), angle_deg, orig_shape)
    exp_inner_mid = unrotate_point_from_top(Point(300.0, 460.0), angle_deg, orig_shape)

    assert math.isclose(outer_left.x, exp_outer_left.x, abs_tol=2.0)
    assert math.isclose(outer_left.y, exp_outer_left.y, abs_tol=2.0)
    assert math.isclose(outer_right.x, exp_outer_right.x, abs_tol=2.0)
    assert math.isclose(outer_right.y, exp_outer_right.y, abs_tol=2.0)
    assert math.isclose(inner_mid.x, exp_inner_mid.x, abs_tol=2.0)
    assert math.isclose(inner_mid.y, exp_inner_mid.y, abs_tol=2.0)


@pytest.mark.parametrize("angle_deg,hour_position", [(180, 6), (270, 9)])
def test_detect_baton_pipeline_end_to_end(angle_deg, hour_position):
    # _dial_circle needs a real Hough-detectable circle, which this sparse
    # synthetic drawing does not provide, so this exercises
    # _baton_candidate + _minute_ticks_at_angle + measure_baton directly --
    # the same reason gmt12_auto_landmarks' own synthetic tests exercise
    # _triangle_candidate/_minute_ticks rather than detect_gmt12.
    gray, orig_shape = _build_scene_in_rotated_frame(angle_deg, 800, 450, _draw_baton_scene)
    c = unrotate_point_from_top(Point(_CX2, _CY2), angle_deg, orig_shape)

    baton = _baton_candidate(gray, c.x, c.y, _R, angle_deg)
    assert baton is not None
    outer_left, outer_right, inner_mid = baton

    t = _minute_ticks_at_angle(gray, c.x, c.y, _R, angle_deg, outer_left, outer_right)
    assert t is not None
    ml, mr, mc, inferred, n = t
    assert inferred is False

    g = BatonGeometry(hour_position, outer_left, outer_right, inner_mid, ml, mr, mc)
    m = measure_baton(g)
    # The synthetic scene is an intentionally clean, centred, unrotated
    # baton+tick arrangement, so all three should read near zero/plausible.
    assert m.radial_clearance_over_baton_width > 0  # ticks are outward of the baton, as drawn
    assert abs(m.tangential_offset_over_baton_width) < 0.15
    assert abs(m.rotation_deg) < 3.0
