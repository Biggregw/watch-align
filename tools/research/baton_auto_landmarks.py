"""Applied baton marker (6/9 o'clock) detection.

Reuses gmt12_auto_landmarks.py's dial-seed detection and tick-fitting
machinery unmodified (via dial_rotation.py's exact 90-degree rotation, see
that module's docstring for why rotation was chosen over rewriting those
functions' direction assumptions). The only genuinely new piece here is
_baton_candidate: a baton has no apex, so its outer-edge corners and
inner-edge midpoint come from cv2.minAreaRect on the bright blob instead of
gmt12_auto_landmarks._polygon_to_triangle_corners.

Left/right convention: "left" = counter-clockwise-neighbour side, "right" =
clockwise-neighbour side (same meaning as gmt12_auto_landmarks' tl/tr, which
are respectively the 59-tick side and the 1-tick side of 12). This is
derived, not assumed: in the rotated ("marker at top") frame, smaller-x is
by construction the counter-clockwise side (mirrors the established 12
o'clock convention, where the 59-tick sits at smaller x than the 1-tick).
Because a rotation (unlike a reflection) preserves orientation, this
labelling survives unrotation back to the original frame for all four
supported angles -- confirmed against the clockwise-tangent-direction
formula (d/dtheta of a clockwise-parameterised circle) for each angle, and
covered by test_baton_geometry_matches_true_clockwise_direction in
tools/research/tests/test_baton_auto_landmarks.py.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

import cv2
import numpy as np

from dial_rotation import rotate_gray_to_top, rotate_point_to_top, unrotate_point_from_top
from gmt12_auto_landmarks import (
    _bright_components,
    _dial_circle,
    _minute_ticks,
    _touches_roi_edge,
)
from human_qc_geometry import BatonGeometry, Point, measure_baton

_ANGLE_FOR_HOUR = {6: 180, 9: 270}


@dataclass(frozen=True)
class BatonDetection:
    geometry: Optional[BatonGeometry]
    confidence: float
    reason: str = ""


def _baton_candidate(gray, cx, cy, r, angle_deg):
    """Locate a baton's outer-edge corners and inner-edge midpoint, in the
    image's ORIGINAL frame. Mirrors gmt12_auto_landmarks._triangle_candidate's
    role (same ROI shape/margins, same bright-component search, same
    ROI-edge safety net) but derives corners from cv2.minAreaRect instead of
    triangle-specific polygon logic.

    ROI size gates (xs/ys bounds below) are untuned placeholders carried
    over from the triangle's own real-photo-tuned values -- step 3 of
    docs/research/gmt6_9_baton_scope_2026-09-28.md (real photos) is where
    these get validated/adjusted, same as the triangle's ROI margin was
    tuned against real failures, not guessed once and left alone.
    """
    orig_shape = gray.shape[:2]
    rotated = rotate_gray_to_top(gray, angle_deg)
    c2 = rotate_point_to_top(Point(cx, cy), angle_deg, orig_shape)
    cx2, cy2 = c2.x, c2.y

    roi = (
        max(0, int(cx2 - .18 * r)), max(0, int(cy2 - .90 * r)),
        min(rotated.shape[1], int(cx2 + .18 * r)), min(rotated.shape[0], int(cy2 - .30 * r)),
    )
    best = None
    for area, p in _bright_components(rotated, roi):
        if _touches_roi_edge(p, roi):
            continue
        xs, ys = float(np.ptp(p[:, 0])), float(np.ptp(p[:, 1]))
        if xs < .06 * r or xs > .26 * r or ys < .10 * r or ys > .34 * r:
            continue
        rect = cv2.minAreaRect(p.astype(np.float32))
        box = cv2.boxPoints(rect)
        if best is None or area > best[0]:
            best = (area, box)
    if best is None:
        return None

    _, box = best
    by_y = sorted(box.tolist(), key=lambda pt: pt[1])
    outer_pair = sorted(by_y[:2], key=lambda pt: pt[0])   # smaller y = further from centre in the rotated "top" frame
    inner_pair = by_y[2:]
    outer_left2 = Point(float(outer_pair[0][0]), float(outer_pair[0][1]))   # smaller x = counter-clockwise side
    outer_right2 = Point(float(outer_pair[1][0]), float(outer_pair[1][1]))
    inner_mid2 = Point(
        float((inner_pair[0][0] + inner_pair[1][0]) / 2.0),
        float((inner_pair[0][1] + inner_pair[1][1]) / 2.0),
    )

    outer_left = unrotate_point_from_top(outer_left2, angle_deg, orig_shape)
    outer_right = unrotate_point_from_top(outer_right2, angle_deg, orig_shape)
    inner_mid = unrotate_point_from_top(inner_mid2, angle_deg, orig_shape)
    return outer_left, outer_right, inner_mid


def _minute_ticks_at_angle(gray, cx, cy, r, angle_deg, outer_left, outer_right):
    """Run gmt12_auto_landmarks._minute_ticks (completely unmodified) at an
    arbitrary cardinal marker position, via dial_rotation.py. Returns
    (minute_left, minute_right, minute_marker_center, inferred, n) in the
    ORIGINAL frame, or None -- same shape as _minute_ticks' own return."""
    orig_shape = gray.shape[:2]
    rotated = rotate_gray_to_top(gray, angle_deg)
    c2 = rotate_point_to_top(Point(cx, cy), angle_deg, orig_shape)
    ol2 = rotate_point_to_top(outer_left, angle_deg, orig_shape)
    or2 = rotate_point_to_top(outer_right, angle_deg, orig_shape)

    result = _minute_ticks(rotated, c2.x, c2.y, r, ol2, or2)
    if result is None:
        return None
    ml2, mr2, mc2, inferred, n = result
    ml = unrotate_point_from_top(ml2, angle_deg, orig_shape)
    mr = unrotate_point_from_top(mr2, angle_deg, orig_shape)
    mc = unrotate_point_from_top(mc2, angle_deg, orig_shape)
    return ml, mr, mc, inferred, n


def detect_baton(bgr, hour_position: int) -> BatonDetection:
    if hour_position not in _ANGLE_FOR_HOUR:
        raise ValueError(f"hour_position must be one of {sorted(_ANGLE_FOR_HOUR)}, got {hour_position}")
    angle_deg = _ANGLE_FOR_HOUR[hour_position]

    if bgr is None or bgr.size == 0:
        return BatonDetection(None, 0, "empty image")
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY) if bgr.ndim == 3 else bgr.copy()

    circle = _dial_circle(gray)
    if circle is None:
        return BatonDetection(None, 0, "dial search seed not found")
    cx, cy, r = circle

    baton = _baton_candidate(gray, cx, cy, r, angle_deg)
    if baton is None:
        return BatonDetection(None, 0, f"{hour_position}-o'clock baton physical contour not found")
    outer_left, outer_right, inner_mid = baton

    t = _minute_ticks_at_angle(gray, cx, cy, r, angle_deg, outer_left, outer_right)
    if t is None:
        return BatonDetection(None, 0, "minute-track sequence not sufficiently constrained")
    ml, mr, mc, inf, n = t

    g = BatonGeometry(hour_position, outer_left, outer_right, inner_mid, ml, mr, mc)
    m = measure_baton(g)
    conf = max(0.0, min(1.0, 1 - .5 * abs(m.tangential_offset_over_baton_width) - (.10 * n if inf else 0)))
    reason = f"minute landmarks reconstructed from robust 6-degree sequence ({n} inferred)" if inf else ""
    return BatonDetection(g, float(conf), reason)
