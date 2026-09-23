"""Generic geometric inspection reticle, v2 (experiment/generic-geometry-
reticle-v2).

VISUAL GEOMETRY ONLY. This module does not decide whether anything is
defective, does not compute a marker-12 pass/fail, does not add
manufacturing tolerances, and does not extrapolate a triangle guide from
round-marker geometry. It draws exactly three families of geometry, each
from its own independently justified source, and nothing else:

1. Minute-track-anchored structure (centre target, minute-track channel,
   12 hour axes) -- the existing validated affine ellipse+roll basis
   (geometry.map_point / master.py radii), reused unchanged from the v1
   reticle prototype (reticle.py on experiment/generic-qc-reticle).

2. The round-marker corridor (inner/centre/outer conics) -- fit ONLY from
   the 8 ROUND hour markers (1,2,4,5,7,8,10,11), using
   marker_consensus.fit_conic and marker_consensus_analysis.
   fit_full_round_conic (the same honest, non-leave-one-out fit already
   used for in-sample residual reporting elsewhere in this project).
   Markers 6, 9 (batons) and 12 (triangle) contribute NOTHING to these
   fits -- they are never segmented by this module at all, so there is
   no code path by which they could leak in. If fewer than
   marker_consensus.MIN_PEERS_FOR_CONIC (5) round markers segment, the
   corridor is omitted entirely and the reason is recorded, never
   fabricated.

3. Diagnostic-only source points (round-marker centroids, inner/outer
   edge evidence, which hours contributed) -- present only in diagnostic-
   mode renders, never in user-mode renders.

No triangle-12 guide, no baton guide, no 12-specific geometry of any
kind, and no colour or symbol implies pass/fail.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

import numpy as np

import geometry
import marker_consensus as mc
import marker_consensus_analysis as mca
import master
from geometry import RotatedRect

CHANNEL_HALF_WIDTH = 0.028  # canonical units -- unchanged from v1, see reticle.py

# Axis inner/outer radius and the marker-clearance gap -- unchanged from
# v1 (reticle.py), where the gap band 0.55-0.86 was checked against the
# full canonical extents of all three marker shapes (round outer 0.821,
# baton inward/outward 0.581/0.793, triangle apex-inward/base-outward
# 0.565/0.815) so the axes never clip any marker shape even though only
# the round markers feed the corridor fit below.
AXIS_INNER_R = 0.16
AXIS_OUTER_R = 0.965
MARKER_GAP_LO = 0.55
MARKER_GAP_HI = 0.86

CORRIDOR_WHICH = ("inner", "centre", "outer")
_ATTR = {"centre": ("centroid_x", "centroid_y"), "outer": ("outer_x", "outer_y"), "inner": ("inner_x", "inner_y")}


@dataclass
class ReticleGeometryV2:
    ellipse: RotatedRect
    roll: float
    channel_inner: List[Tuple[int, int]]
    channel_outer: List[Tuple[int, int]]
    axis_segments: List[Tuple[int, List[Tuple[Tuple[int, int], Tuple[int, int]]]]]
    centre_xy: Tuple[int, int]
    round_observations: Dict[int, mc.MarkerObservation]          # ROUND_HOURS only, ever
    corridor_rings: Dict[str, Optional[List[Tuple[int, int]]]]    # "inner"/"centre"/"outer" -> polyline or None
    corridor_conics: Dict[str, Optional[np.ndarray]]
    corridor_fit_summary: Dict[str, Optional[mca.ConicFitSummary]]
    n_round_segmented: int
    corridor_suppressed_reason: Optional[str]


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
                    dial_radius_px: float) -> ReticleGeometryV2:
    channel_r = master.MINUTE_TRACK_R
    channel_inner = _sample_ellipse_ring(ellipse, roll, channel_r - CHANNEL_HALF_WIDTH)
    channel_outer = _sample_ellipse_ring(ellipse, roll, channel_r + CHANNEL_HALF_WIDTH)
    axis_segments = [(h, _axis_segments_with_gap(ellipse, roll, h)) for h in range(1, 13)]
    centre_xy = (int(round(ellipse.cx)), int(round(ellipse.cy)))

    # Only the 8 round markers are ever segmented by this module. Batons
    # (6, 9), the triangle (12), and the date area are never touched, so
    # they cannot contribute to the corridor fit by construction.
    round_observations: Dict[int, mc.MarkerObservation] = {}
    for h in mc.ROUND_HOURS:
        obs = mc.segment_marker(gray, ellipse, roll, dial_radius_px, h)
        if obs is not None:
            round_observations[h] = obs
    n_round = len(round_observations)

    corridor_rings: Dict[str, Optional[List[Tuple[int, int]]]] = {w: None for w in CORRIDOR_WHICH}
    corridor_conics: Dict[str, Optional[np.ndarray]] = {w: None for w in CORRIDOR_WHICH}
    corridor_fit_summary: Dict[str, Optional[mca.ConicFitSummary]] = {w: None for w in CORRIDOR_WHICH}
    suppressed_reason = None

    if n_round < mc.MIN_PEERS_FOR_CONIC:
        suppressed_reason = (
            f"only {n_round} of 8 round hour markers (1,2,4,5,7,8,10,11) segmented "
            f"in this photo -- marker_consensus.MIN_PEERS_FOR_CONIC requires at least "
            f"{mc.MIN_PEERS_FOR_CONIC} to fit a 5-degree-of-freedom conic; corridor omitted, not fabricated"
        )
    else:
        center = (ellipse.cx, ellipse.cy)
        for which in CORRIDOR_WHICH:
            ax, ay = _ATTR[which]
            hours = sorted(round_observations.keys())
            pts = np.array([[getattr(round_observations[h], ax), getattr(round_observations[h], ay)] for h in hours])
            Q = mc.fit_conic(pts)
            summary = mca.fit_full_round_conic(round_observations, which)
            corridor_fit_summary[which] = summary
            if Q is None:
                continue
            approx_r = float(np.mean(np.linalg.norm(pts - np.array(center), axis=1)))
            ring = _sample_conic_ring(Q, center, approx_r)
            if ring:
                corridor_rings[which] = ring
                corridor_conics[which] = Q

    return ReticleGeometryV2(
        ellipse=ellipse, roll=roll, channel_inner=channel_inner, channel_outer=channel_outer,
        axis_segments=axis_segments, centre_xy=centre_xy, round_observations=round_observations,
        corridor_rings=corridor_rings, corridor_conics=corridor_conics, corridor_fit_summary=corridor_fit_summary,
        n_round_segmented=n_round, corridor_suppressed_reason=suppressed_reason,
    )
