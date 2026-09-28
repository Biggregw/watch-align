"""Generalize the 12-o'clock-shaped tick-search machinery to other cardinal
marker positions (6, 9, and later 3) via an exact, lossless 90-degree-multiple
image rotation, instead of rewriting that machinery's direction assumptions.

Why rotation instead of parameterizing every function with signs: the
existing, already-validated tick pipeline (`_tick_masks` / `_ticks` /
`_direct` / `_sequence` / `_robust_line` / `_circle_tangent_landmarks` in
gmt12_auto_landmarks.py) bakes in two direction assumptions that are only
true at 12 o'clock: ticks are taller-than-wide (radial direction is image-y),
and a tick's "inner" (centre-facing) endpoint is the bottom of its bounding
box (`_ticks`' `y0+y+h-1`) because the dial centre sits below the marker.
At 6 both of those still hold for tick *shape* but the inner/outer direction
is flipped (centre is above); at 9 the tick shape assumption itself is wrong
(radial direction is image-x, ticks are wider-than-tall). Rewriting every
function to carry a direction/axis parameter risks subtly breaking the
real-photo-tuned thresholds inside them. Rotating the whole image by an exact
90-degree multiple is lossless (no interpolation, so the sharp edges those
Otsu/tophat thresholds depend on are unaffected) and reduces every cardinal
position to the exact 12-o'clock configuration those functions already
handle correctly.

Convention: `angle_deg` is clockwise degrees from 12 to the target marker
(12=0, 3=90, 6=180, 9=270). Only these four values are supported -- this
module intentionally does not attempt arbitrary-angle rotation (that would
reintroduce interpolation and is not needed for cardinal marker positions).
"""
from __future__ import annotations

import cv2

from human_qc_geometry import Point

_ROTATION_CODE = {
    0: None,
    90: cv2.ROTATE_90_COUNTERCLOCKWISE,
    180: cv2.ROTATE_180,
    270: cv2.ROTATE_90_CLOCKWISE,
}


def _check_angle(angle_deg):
    if angle_deg not in _ROTATION_CODE:
        raise ValueError(f"angle_deg must be one of {sorted(_ROTATION_CODE)}, got {angle_deg}")


def rotate_gray_to_top(gray, angle_deg):
    """Rotate a grayscale image so the marker at `angle_deg` clockwise from
    12 lands at the top (12 o'clock) position. Exact for the four supported
    angles -- no interpolation, so pixel intensities are unchanged, only
    relocated."""
    _check_angle(angle_deg)
    code = _ROTATION_CODE[angle_deg]
    return gray if code is None else cv2.rotate(gray, code)


def rotate_point_to_top(pt, angle_deg, orig_shape):
    """Map a Point from the original (pre-rotation) frame into the frame
    produced by rotate_gray_to_top for the same angle_deg. orig_shape is the
    ORIGINAL image's (h, w), i.e. gray.shape[:2] before rotation."""
    _check_angle(angle_deg)
    h, w = orig_shape
    x, y = pt.x, pt.y
    if angle_deg == 0:
        return Point(x, y)
    if angle_deg == 90:
        return Point(y, w - 1 - x)
    if angle_deg == 180:
        return Point(w - 1 - x, h - 1 - y)
    return Point(h - 1 - y, x)  # angle_deg == 270


def unrotate_point_from_top(pt, angle_deg, orig_shape):
    """Inverse of rotate_point_to_top: map a Point found in the rotated
    ("marker at top") frame back to the original image's frame. orig_shape
    is still the ORIGINAL (pre-rotation) image's (h, w) -- the same value
    passed to rotate_point_to_top, not the rotated image's shape."""
    _check_angle(angle_deg)
    h, w = orig_shape
    x, y = pt.x, pt.y
    if angle_deg == 0:
        return Point(x, y)
    if angle_deg == 90:
        return Point(w - 1 - y, x)
    if angle_deg == 180:
        return Point(w - 1 - x, h - 1 - y)
    return Point(y, h - 1 - x)  # angle_deg == 270
