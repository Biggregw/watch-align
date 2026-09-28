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

Applied baton marker (6/9 o'clock, see docs/research/gmt6_9_baton_scope_2026-09-28.md)
convention used here -- deliberately mirrors the 12-triangle shape above
(outer-edge-pair + one inner point; tick-pair + one tick centre) so the same
measurement primitives apply, but is written orientation-agnostically since
a baton's long axis is roughly vertical at 6 and roughly horizontal at 9:
* baton_outer_left/right are the two OUTER (bezel-facing) corners of the
  baton's outer edge, nearest the minute track;
* baton_inner_mid is the midpoint of the baton's INNER (centre-facing) edge;
* "left"/"right" mean the counter-clockwise-neighbour / clockwise-neighbour
  side of the marker's own position, not image-x -- at 9 o'clock the
  tangential direction is image-y, so image-left-vs-right does not apply;
* minute_inner_left/right are the observed inner ends of the immediate
  counter-clockwise/clockwise-neighbour minute-track ticks;
* minute_marker_center is the observed centre of the actual tick this baton
  should align to (the "30" tick for 6, the "45" tick for 9), never a fitted
  dial-centre coordinate -- same role as minute_60_center above.
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


@dataclass(frozen=True)
class BatonGeometry:
    hour_position: int  # 6 or 9 -- explicit, never inferred from geometry
    baton_outer_left: Point
    baton_outer_right: Point
    baton_inner_mid: Point
    minute_inner_left: Point
    minute_inner_right: Point
    minute_marker_center: Point


@dataclass(frozen=True)
class BatonMeasurements:
    # Signed perpendicular gap from the local tick-pair line to the baton's
    # outer-edge midpoint, divided by baton outer-edge width. Analogue of
    # top_clearance_over_triangle_width.
    radial_clearance_over_baton_width: float

    # Signed perpendicular distance of the observed minute_marker_center from
    # the baton's own long-axis line (outer-edge midpoint to inner-edge
    # midpoint), divided by baton outer-edge width. Zero means the marker's
    # long axis passes exactly through the tick it should align to. Positive
    # means the tick sits on the "right" (clockwise) side of the axis.
    # Analogue of horizontal_offset_over_triangle_width, but orientation-
    # agnostic (does not assume a roughly-vertical axis).
    tangential_offset_over_baton_width: float

    # Signed smallest angle between the baton's outer edge and the local
    # tick-pair reference line. Zero means parallel.
    rotation_deg: float

    # Human-style local side-clearance diagnostics, same pattern as the
    # 12-triangle: distance from each outer corner to its corresponding
    # immediate neighbouring tick inner end, normalized by baton width.
    left_clearance_over_baton_width: float
    right_clearance_over_baton_width: float

    # right - left. Positive means visibly more room on the clockwise-
    # neighbour side. Deliberately a diagnostic, not an independent 'tilt'
    # verdict -- see side_clearance_asymmetry above.
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


def measure_baton(g: BatonGeometry) -> BatonMeasurements:
    """Measure the applied-baton-marker QC relationships from
    docs/architecture/QC_PRINCIPLES.md's "Applied hour markers" section
    (radial height, tangential/centre alignment, rotation/cant, local
    side-clearance), using the same human-defined local references as
    measure_gmt12 -- the observed neighbouring ticks, never a fitted dial
    centre or global axis.

    Written orientation-agnostically (via _signed_point_line_distance for
    both the radial and tangential measurements, rather than measure_gmt12's
    _x_on_line_at_y) because a baton's long axis is roughly vertical at 6
    o'clock but roughly horizontal at 9 o'clock -- an x-at-given-y formula
    would silently break at 9.

    No pass/fail thresholds live here, same as measure_gmt12.
    """
    outer_mid = _mid(g.baton_outer_left, g.baton_outer_right)
    baton_width = _norm(_sub(g.baton_outer_right, g.baton_outer_left))
    if baton_width <= 1e-9:
        raise ValueError("baton outer edge is degenerate")

    clearance = _signed_point_line_distance(
        outer_mid, g.minute_inner_left, g.minute_inner_right
    ) / baton_width

    tangential = _signed_point_line_distance(
        g.minute_marker_center, outer_mid, g.baton_inner_mid
    ) / baton_width

    rotation = _parallel_angle_difference_deg(
        g.baton_outer_left,
        g.baton_outer_right,
        g.minute_inner_left,
        g.minute_inner_right,
    )

    left_clearance = _distance(g.baton_outer_left, g.minute_inner_left) / baton_width
    right_clearance = _distance(g.baton_outer_right, g.minute_inner_right) / baton_width
    side_asymmetry = right_clearance - left_clearance

    return BatonMeasurements(
        radial_clearance_over_baton_width=clearance,
        tangential_offset_over_baton_width=tangential,
        rotation_deg=rotation,
        left_clearance_over_baton_width=left_clearance,
        right_clearance_over_baton_width=right_clearance,
        side_clearance_asymmetry=side_asymmetry,
    )
