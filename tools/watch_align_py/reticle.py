"""Generic QC alignment reticle (experiment/generic-qc-reticle).

Product-philosophy shift from every prior experiment in this research
line: those tried to automatically score or flag defects. This one does
not score anything. The goal is a trustworthy geometric overlay that
makes departures from normal dial geometry visually obvious to a human,
using only geometry this project has actually validated -- never a
reproduction of any specific watch's dial artwork.

Two geometrically DIFFERENT things are drawn, from two DIFFERENT
sources, and this module keeps them explicitly separate rather than
forcing them into one fabricated "exact perspective" -- doing that (i.e.
treating the minute-track's own affine ellipse basis as if it also told
you exactly where a marker ring sits) is precisely the failed assumption
this whole research line spent weeks disproving (see docs/research/
radial-drift-root-cause-2026-09-22.md and the two rejected correction
attempts that followed it):

1. **Minute-track-anchored elements** (centre target, minute-track
   channel, 60 tick gates, 12 radial axes) use the existing, validated
   affine ellipse+roll basis (geometry.map_point / master.py radii).
   This basis is well-established as reliable for the minute track's own
   radius and for ANGLE generally (this session repeatedly found angular
   measurement far more robust than radial extrapolation) -- it is used
   here only within the regime it was actually validated for.

2. **Marker envelope guides** (round-marker centre/outer/inner rings,
   the triangle's expected outer relationship) use a conic fit directly
   from THIS photo's own detected round-marker points (marker_consensus.
   fit_conic), never the affine ellipse extrapolated inward -- exactly
   the projective-marker-consensus architecture, reused here for display
   rather than for a leave-one-out anomaly score. If fewer than 5 round
   markers segment on a given photo, there is not enough data to fit a
   trustworthy 5-DOF conic, and this module draws NOTHING for that guide
   rather than fabricate one -- "the overlay must never look precise
   while being geometrically unjustified" is enforced here as an
   explicit code path, not just a design intention.

Nothing here draws any Rolex-specific artwork (text, hour-marker shapes,
lume plots, bezel graphics) -- every element is a plain geometric
reference (points, thin lines, arcs) that this project can actually
justify.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

import cv2
import numpy as np

import geometry
import marker_consensus as mc
import master
from geometry import RotatedRect

# Minute-track channel half-width, in canonical (dial-radius-normalised)
# units. Not a precision claim -- a legibility choice wide enough that a
# correctly-aligned real minute track visibly sits inside it, narrow
# enough that a real scale/centring error visibly does not. Documented
# here as tunable, not derived from any measurement.
CHANNEL_HALF_WIDTH = 0.028

# Radial extent kept clear of the 12 axes / 60 tick gates so the real
# printed marker artwork is never covered. Covers the round/baton/
# triangle centre-radius band with margin: round centre 0.751 (own
# radius 0.070 -> outer 0.821), baton centre 0.687 +/-0.106 (inward
# 0.581, outward 0.793), triangle apex-inward 0.719-0.154=0.565 to
# base-outward 0.719+0.096=0.815 -- 0.55-0.86 clears all three shapes'
# full extents with margin.
MARKER_GAP_LO = 0.55
MARKER_GAP_HI = 0.86

AXIS_INNER_R = 0.16   # leave the hands/centre clear too
AXIS_OUTER_R = 0.965  # stop short of the dial edge / bezel


@dataclass
class ReticleGeometry:
    """All geometric primitives for one photo, computed once and shared
    across whichever visual variant renders them."""
    ellipse: RotatedRect
    roll: float
    channel_inner: List[Tuple[int, int]]
    channel_outer: List[Tuple[int, int]]
    tick_positions: List[Tuple[int, Tuple[float, float], Tuple[float, float]]]  # (minute, radial_unit_dir, image_xy)
    axis_segments: List[Tuple[int, List[Tuple[Tuple[int, int], Tuple[int, int]]]]]  # (hour, [(p0,p1), ...] gap-split)
    centre_xy: Tuple[int, int]
    round_envelope: Dict[str, Optional[List[Tuple[int, int]]]]  # "centre"/"outer"/"inner" -> polyline or None
    round_envelope_conic: Dict[str, Optional[np.ndarray]]
    triangle_outer_point: Optional[Tuple[int, int]]
    n_round_segmented: int


def _sample_ellipse_ring(ellipse: RotatedRect, roll: float, radius: float, n: int = 240) -> List[Tuple[int, int]]:
    pts = []
    for i in range(n + 1):
        a = 2.0 * math.pi * i / n
        x, y = geometry.map_point(ellipse, 1.0, roll, radius * math.cos(a), radius * math.sin(a))
        pts.append((int(round(x)), int(round(y))))
    return pts


def _sample_conic_ring(Q: np.ndarray, center: Tuple[float, float], approx_r: float, n: int = 240
                        ) -> List[Tuple[int, int]]:
    pts = []
    for i in range(n + 1):
        a = 2.0 * math.pi * i / n
        p = mc.conic_ray_point(Q, center, a, approx_r)
        if p is None or not (math.isfinite(p[0]) and math.isfinite(p[1])):
            return []
        pts.append((int(round(p[0])), int(round(p[1]))))
    return pts


def _axis_segments_with_gap(ellipse: RotatedRect, roll: float, hour: int
                             ) -> List[Tuple[Tuple[int, int], Tuple[int, int]]]:
    angle = master.angle_for_hour(hour)
    ca, sa = math.cos(angle), math.sin(angle)

    def pt(r):
        x, y = geometry.map_point(ellipse, 1.0, roll, r * ca, r * sa)
        return (int(round(x)), int(round(y)))

    return [(pt(AXIS_INNER_R), pt(MARKER_GAP_LO)), (pt(MARKER_GAP_HI), pt(AXIS_OUTER_R))]


def build_geometry(gray: np.ndarray, ellipse: RotatedRect, roll: float,
                    dial_radius_px: float) -> ReticleGeometry:
    channel_r = master.MINUTE_TRACK_R
    channel_inner = _sample_ellipse_ring(ellipse, roll, channel_r - CHANNEL_HALF_WIDTH)
    channel_outer = _sample_ellipse_ring(ellipse, roll, channel_r + CHANNEL_HALF_WIDTH)

    tick_positions = []
    for minute in range(60):
        angle = math.radians(minute * 6.0 - 90.0)
        u, v = math.cos(angle), math.sin(angle)
        xy = geometry.map_point(ellipse, 1.0, roll, channel_r * u, channel_r * v)
        tick_positions.append((minute, (u, v), (int(round(xy[0])), int(round(xy[1])))))

    axis_segments = [(h, _axis_segments_with_gap(ellipse, roll, h)) for h in range(1, 13)]
    centre_xy = (int(round(ellipse.cx)), int(round(ellipse.cy)))

    # marker envelope: fit directly from THIS photo's own round-marker
    # observations, never from the affine ellipse extrapolated inward.
    observations: Dict[int, mc.MarkerObservation] = {}
    for h in mc.ALL_MARKER_HOURS:
        obs = mc.segment_marker(gray, ellipse, roll, dial_radius_px, h)
        if obs is not None:
            observations[h] = obs
    round_present = [h for h in mc.ROUND_HOURS if h in observations]

    round_envelope: Dict[str, Optional[List[Tuple[int, int]]]] = {"centre": None, "outer": None, "inner": None}
    round_envelope_conic: Dict[str, Optional[np.ndarray]] = {"centre": None, "outer": None, "inner": None}
    round_outer_approx_r = None
    if len(round_present) >= mc.MIN_PEERS_FOR_CONIC:
        for which, attr_x, attr_y in (("centre", "centroid_x", "centroid_y"),
                                       ("outer", "outer_x", "outer_y"),
                                       ("inner", "inner_x", "inner_y")):
            pts = np.array([[getattr(observations[h], attr_x), getattr(observations[h], attr_y)]
                             for h in round_present])
            Q = mc.fit_conic(pts)
            if Q is None:
                continue
            approx_r = float(np.mean(np.linalg.norm(pts - np.array([ellipse.cx, ellipse.cy]), axis=1)))
            if which == "outer":
                round_outer_approx_r = approx_r
            ring = _sample_conic_ring(Q, (ellipse.cx, ellipse.cy), approx_r)
            if ring:
                round_envelope[which] = ring
                round_envelope_conic[which] = Q

    # The triangle guide deliberately does NOT depend on marker 12's own
    # detection succeeding -- the most useful case for showing "where it
    # should be" is exactly when the marker might be geometrically
    # anomalous enough that automated segmentation is uncertain. The
    # round-envelope conic's own mean observed radius (already computed
    # above, independent of marker 12) supplies the root-disambiguation
    # distance instead of marker 12's own detected point.
    triangle_outer_point = None
    if round_envelope_conic["outer"] is not None and round_outer_approx_r is not None:
        import marker_consensus_analysis as mca
        u, v = mca._nominal_direction(ellipse, roll, 12)
        angle12 = math.atan2(v, u)
        center = (ellipse.cx, ellipse.cy)
        round_pt = mc.conic_ray_point(round_envelope_conic["outer"], center, angle12, round_outer_approx_r)
        if round_pt is not None:
            ratio = mc.TRI_OUTER_CANON_R / mc.ROUND_OUTER_CANON_R
            tx = center[0] + (round_pt[0] - center[0]) * ratio
            ty = center[1] + (round_pt[1] - center[1]) * ratio
            triangle_outer_point = (int(round(tx)), int(round(ty)))

    return ReticleGeometry(
        ellipse=ellipse, roll=roll, channel_inner=channel_inner, channel_outer=channel_outer,
        tick_positions=tick_positions, axis_segments=axis_segments, centre_xy=centre_xy,
        round_envelope=round_envelope, round_envelope_conic=round_envelope_conic,
        triangle_outer_point=triangle_outer_point, n_round_segmented=len(round_present),
    )
