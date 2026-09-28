from pathlib import Path
import sys

import numpy as np
import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dial_rotation import rotate_gray_to_top, rotate_point_to_top, unrotate_point_from_top
from gmt12_auto_landmarks import _minute_ticks
from human_qc_geometry import Point

import cv2
import math


ANGLES = (0, 90, 180, 270)


@pytest.mark.parametrize("angle_deg", ANGLES)
def test_round_trip_identity_on_a_nonsquare_shape(angle_deg):
    h, w = 320, 450
    for x, y in [(0.0, 0.0), (449.0, 319.0), (200.0, 100.0), (12.5, 307.3), (440.0, 5.0)]:
        p = Point(x, y)
        forward = rotate_point_to_top(p, angle_deg, (h, w))
        back = unrotate_point_from_top(forward, angle_deg, (h, w))
        assert math.isclose(back.x, x, abs_tol=1e-9)
        assert math.isclose(back.y, y, abs_tol=1e-9)


@pytest.mark.parametrize("angle_deg,expect_swapped_dims", [(0, False), (90, True), (180, False), (270, True)])
def test_rotate_gray_to_top_dimensions(angle_deg, expect_swapped_dims):
    gray = np.zeros((320, 450), dtype=np.uint8)
    rotated = rotate_gray_to_top(gray, angle_deg)
    if expect_swapped_dims:
        assert rotated.shape == (450, 320)
    else:
        assert rotated.shape == (320, 450)


@pytest.mark.parametrize("angle_deg", ANGLES)
def test_rotate_point_to_top_matches_actual_cv2_rotate_pixel_positions(angle_deg):
    # Ground-truth check against cv2.rotate's real behaviour, not just our
    # own formula reasoning: paint a handful of single, uniquely-valued
    # pixels into an otherwise-zero image, rotate the image with cv2, and
    # confirm rotate_point_to_top predicts exactly where each pixel landed.
    h, w = 41, 77
    gray = np.zeros((h, w), dtype=np.uint8)
    sample_points = [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1), (30, 17)]
    for i, (x, y) in enumerate(sample_points, start=1):
        gray[y, x] = i * 40

    rotated = rotate_gray_to_top(gray, angle_deg)
    for i, (x, y) in enumerate(sample_points, start=1):
        p2 = rotate_point_to_top(Point(float(x), float(y)), angle_deg, (h, w))
        rx, ry = int(round(p2.x)), int(round(p2.y))
        assert rotated[ry, rx] == i * 40, f"angle={angle_deg} point=({x},{y}) predicted=({rx},{ry})"


def _draw_ticks(gray, xs, y_top, y_bottom, thickness=3):
    for x in xs:
        cv2.line(gray, (x, y_top), (x, y_bottom), 255, thickness)


def test_minute_ticks_machinery_works_at_6_oclock_via_rotation():
    # Mirrors test_minute_ticks_end_to_end_direct_detection_on_synthetic_dial
    # (gmt12_auto_landmarks.py's own 12-o'clock proof) but with the marker
    # below the dial centre instead of above -- the physically correct
    # arrangement at 6 o'clock, which the unmodified _minute_ticks (band
    # search "above top", inner tick endpoint = bbox bottom) gets backwards
    # if pointed at directly. Rotating 180 degrees first reduces this back
    # to the exact 12-o'clock shape that function already handles.
    h, w = 360, 450
    gray = np.zeros((h, w), dtype=np.uint8)
    tick_xs = (140, 170, 200, 230, 260)
    _draw_ticks(gray, tick_xs, y_top=316, y_bottom=340)  # outward = below the marker
    tl, tr = Point(180.0, 270.0), Point(220.0, 270.0)
    cx, cy, r = 200.0, -130.0, 400.0  # centre ABOVE the marker, matching real 6 o'clock

    rotated = rotate_gray_to_top(gray, 180)
    cx2 = rotate_point_to_top(Point(cx, cy), 180, (h, w)).x
    cy2 = rotate_point_to_top(Point(cx, cy), 180, (h, w)).y
    tl2 = rotate_point_to_top(tl, 180, (h, w))
    tr2 = rotate_point_to_top(tr, 180, (h, w))

    result = _minute_ticks(rotated, cx2, cy2, r, tl2, tr2)
    assert result is not None
    ml2, mr2, m602, inferred, n = result
    assert inferred is False

    ml = unrotate_point_from_top(ml2, 180, (h, w))
    mr = unrotate_point_from_top(mr2, 180, (h, w))
    m60 = unrotate_point_from_top(m602, 180, (h, w))

    # Same tick x-positions/pitch as the 12-o'clock proof test (140/170/
    # 200/230/260), but left/right are swapped in the unrotated result
    # because a 180-degree rotation reverses x-order: the rotated frame's
    # "ml" (its own leftmost tick) is physically the original frame's RIGHT
    # neighbour (x=230), and vice versa. The x+0.5 centroid convention seen
    # in the un-rotated 12-o'clock proof test also shifts to x-0.5 here --
    # a deterministic consequence of this canvas's even width (450, so the
    # w-1-x flip's axis of symmetry sits between pixels, not on one), not a
    # measurement error.
    assert math.isclose(ml.x, 229.5, abs_tol=.5)
    assert math.isclose(m60.x, 199.5, abs_tol=.5)
    assert math.isclose(mr.x, 169.5, abs_tol=.5)
    # And the ticks must land back on the outward (below-marker) side in the
    # original frame, not merely at plausible x positions.
    assert ml.y > tl.y and mr.y > tr.y


def test_minute_ticks_machinery_works_at_9_oclock_via_rotation():
    # At 9 o'clock the radial direction is image-x, not image-y: physical
    # ticks are wider-than-tall and arranged along y, which _ticks' own
    # shape filter (tall-than-wide) would reject if searched directly.
    # Build the scenario in the already-known-good ROTATED ("marker at
    # top") frame -- i.e. exactly the 12-o'clock tick arrangement used
    # elsewhere -- then unrotate it through this module to get the
    # physically-correct 9-o'clock ORIGINAL-frame picture, draw that, and
    # confirm rotating it forward again reproduces a result _minute_ticks
    # can solve, mapping back to the expected original-frame positions.
    h2, w2 = 320, 450  # the "marker at top" (rotated) frame's own shape
    angle_deg = 270
    # original (pre-rotation) shape, per rotate_gray_to_top's dimension swap
    orig_shape = (w2, h2)

    tick_xs2 = (140, 170, 200, 230, 260)
    gray2 = np.zeros((h2, w2), dtype=np.uint8)
    _draw_ticks(gray2, tick_xs2, y_top=200, y_bottom=224)
    tl2, tr2 = Point(180.0, 270.0), Point(220.0, 270.0)
    cx2, cy2 = 200.0, 670.0

    # Map every known-good rotated-frame point/pixel back to the physical
    # original (9-o'clock) frame, then draw an image from scratch there --
    # this proves the round trip on real pixel data, not just coordinates.
    orig_h, orig_w = orig_shape
    gray = np.zeros((orig_h, orig_w), dtype=np.uint8)
    ys2, xs2 = np.nonzero(gray2)
    for y2, x2 in zip(ys2, xs2):
        p = unrotate_point_from_top(Point(float(x2), float(y2)), angle_deg, orig_shape)
        gray[int(round(p.y)), int(round(p.x))] = 255
    # Thicken back up (nearest-neighbour point mapping can leave 1px gaps
    # along a rotated line) so _ticks' connected-component logic sees solid
    # tick shapes, matching what a real rotated photo's own pixels would be.
    gray = cv2.dilate(gray, np.ones((3, 3), np.uint8))

    tl = unrotate_point_from_top(tl2, angle_deg, orig_shape)
    tr = unrotate_point_from_top(tr2, angle_deg, orig_shape)
    cx, cy = unrotate_point_from_top(Point(cx2, cy2), angle_deg, orig_shape).x, \
        unrotate_point_from_top(Point(cx2, cy2), angle_deg, orig_shape).y
    r = 400.0

    rotated = rotate_gray_to_top(gray, angle_deg)
    assert rotated.shape == (h2, w2)
    tl2r = rotate_point_to_top(tl, angle_deg, orig_shape)
    tr2r = rotate_point_to_top(tr, angle_deg, orig_shape)
    cx2r = rotate_point_to_top(Point(cx, cy), angle_deg, orig_shape).x
    cy2r = rotate_point_to_top(Point(cx, cy), angle_deg, orig_shape).y

    result = _minute_ticks(rotated, cx2r, cy2r, r, tl2r, tr2r)
    assert result is not None
    ml2, mr2, m602, inferred, n = result
    assert inferred is False

    ml = unrotate_point_from_top(ml2, angle_deg, orig_shape)
    mr = unrotate_point_from_top(mr2, angle_deg, orig_shape)
    m60 = unrotate_point_from_top(m602, angle_deg, orig_shape)

    # In the physical 9-o'clock original frame the tangential axis is
    # image-y, so the three ticks must be distinct, evenly spaced along y
    # (not x), each near the same x (the radial position) -- the mirror
    # image of the 12/6-o'clock assertions on x.
    ys = sorted([ml.y, m60.y, mr.y])
    assert math.isclose(ys[1] - ys[0], ys[2] - ys[1], abs_tol=1.0)
    xs = [ml.x, m60.x, mr.x]
    assert max(xs) - min(xs) < 3.0
