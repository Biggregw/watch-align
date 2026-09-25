"""Human-defined QC geometry primitives.

This module deliberately contains no detector, dial fit, homography, or
perspective correction. It converts already-observed physical landmarks into
the measurements a human reviewer actually uses. Detector work is downstream
of this contract, not the other way around.

Image coordinates: x right, y down.

GMT 12-o'clock convention used here:
* triangle_top_left/right are the two OUTER corners nearest the minute track;
* triangle_tip is the INWARD/downward point nearest the printed coronet;
* minute_inner_left/right are the observed inner ends of the immediate 59 and
  1 minute-track ticks respectively;
* minute_60_center is the observed centre of the actual 60/top tick, never a
  fitted dial-centre x coordinate.
"""
from __future__ import annotations

from dataclasses import dataclass
import math


@dataclass(frozen=True)
class Point:
    x: float
    y: float


@dataclass(frozen=True)
class Gmt12Geometry:
    triangle_top_left: Point
    triangle_top_right: Point
    triangle_tip: Point
    minute_inner_left: Point
    minute_inner_right: Point
    minute_60_center: Point


@dataclass(frozen=True)
class Gmt12Measurements:
    # Signed perpendicular gap from local 59-to-1 minute-track line to triangle
    # top midpoint, divided by triangle top width.
    top_clearance_over_triangle_width: float

    # Signed lateral displacement of triangle centreline at the observed 60
    # marker y, divided by triangle top width. Zero means locally centred.
    horizontal_offset_over_triangle_width: float

    # Signed smallest angle between triangle top edge and the local 59-to-1
    # minute-track reference line. Zero means parallel.
    rotation_deg: float

    # Human-style local side-clearance diagnostics. These are the distances
    # from each triangle top corner to the corresponding immediate neighbouring
    # minute tick inner end, normalized by triangle top width.
    left_clearance_over_triangle_width: float
    right_clearance_over_triangle_width: float

    # right - left. Positive means visibly more room on the 1-minute side;
    # negative means more room on the 59-minute side. This is deliberately a
    # diagnostic, not an independent 'tilt' verdict: translation and rotation
    # can both contribute, so rotation_deg remains the direct angular measure.
    side_clearance_asymmetry: float


def _sub(a: Point, b: Point) -> Point:
    return Point(a.x - b.x, a.y - b.y)


def _norm(v: Point) -> float:
    return math.hypot(v.x, v.y)


def _distance(a: Point, b: Point) -> float:
    return _norm(_sub(a, b))


def _mid(a: Point, b: Point) -> Point:
    return Point((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)


def _signed_point_line_distance(p: Point, a: Point, b: Point) -> float:
    v = _sub(b, a)
    length = _norm(v)
    if length <= 1e-9:
        raise ValueError("minute-track reference line is degenerate")
    w = _sub(p, a)
    return (v.x * w.y - v.y * w.x) / length


def _line_angle(a: Point, b: Point) -> float:
    return math.atan2(b.y - a.y, b.x - a.x)


def _parallel_angle_difference_deg(a0: Point, a1: Point, b0: Point, b1: Point) -> float:
    """Signed difference for unoriented lines, constrained to [-90, 90)."""
    d = math.degrees(_line_angle(a0, a1) - _line_angle(b0, b1))
    while d >= 90.0:
        d -= 180.0
    while d < -90.0:
        d += 180.0
    return d


def _x_on_line_at_y(a: Point, b: Point, y: float) -> float:
    dy = b.y - a.y
    if abs(dy) <= 1e-9:
        raise ValueError("triangle centreline is horizontal/degenerate")
    t = (y - a.y) / dy
    return a.x + t * (b.x - a.x)


def measure_gmt12(g: Gmt12Geometry) -> Gmt12Measurements:
    """Measure the agreed human-defined 12-marker QC relationships.

    No pass/fail thresholds live here. Measurements describe the observed local
    geometry only; calibration/validation decides later what is reportable.
    """
    tri_top_mid = _mid(g.triangle_top_left, g.triangle_top_right)
    tri_width = _norm(_sub(g.triangle_top_right, g.triangle_top_left))
    if tri_width <= 1e-9:
        raise ValueError("triangle top edge is degenerate")

    clearance = _signed_point_line_distance(
        tri_top_mid, g.minute_inner_left, g.minute_inner_right
    ) / tri_width

    # The human centring test is triangle centreline vs the ACTUAL 60 marker.
    tri_axis_x_at_60 = _x_on_line_at_y(
        tri_top_mid, g.triangle_tip, g.minute_60_center.y
    )
    horizontal = (tri_axis_x_at_60 - g.minute_60_center.x) / tri_width

    rotation = _parallel_angle_difference_deg(
        g.triangle_top_left,
        g.triangle_top_right,
        g.minute_inner_left,
        g.minute_inner_right,
    )

    # Preserve the extra cue a human gets by expanding attention from 60 to the
    # immediate 59/1 ticks. Do not collapse it into rotation: unequal side
    # clearance can arise from lateral displacement, rotation, or both.
    left_clearance = _distance(g.triangle_top_left, g.minute_inner_left) / tri_width
    right_clearance = _distance(g.triangle_top_right, g.minute_inner_right) / tri_width
    side_asymmetry = right_clearance - left_clearance

    return Gmt12Measurements(
        top_clearance_over_triangle_width=clearance,
        horizontal_offset_over_triangle_width=horizontal,
        rotation_deg=rotation,
        left_clearance_over_triangle_width=left_clearance,
        right_clearance_over_triangle_width=right_clearance,
        side_clearance_asymmetry=side_asymmetry,
    )
