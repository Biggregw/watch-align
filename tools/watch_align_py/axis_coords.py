"""Shared canonical-axis coordinate helpers for the GMT proportional-
geometry experiment (experiment/gmt-proportional-geometry-v1).

Everything here is a thin, exact wrapper around already-validated
geometry: geometry.map_point / marker_consensus.inverse_map (the tested
algebraic inverse of map_point) and marker_consensus_analysis.
_nominal_direction (the image-space direction for a nominal hour axis,
established elsewhere in this project as reliable for ANGLE while radial
extrapolation is not -- used here for direction only, never to place a
marker).
"""
from __future__ import annotations

import math
from typing import Tuple

import marker_consensus as mc
import marker_consensus_analysis as mca
import master
from geometry import RotatedRect


def nominal_axis_image_angle(ellipse: RotatedRect, roll: float, hour: int) -> float:
    """Image-space angle (radians) of the nominal hour axis, established
    independently of any marker's own detected position."""
    u, v = mca._nominal_direction(ellipse, roll, hour)
    return math.atan2(v, u)


def canonical_axis_components(ellipse: RotatedRect, roll: float, hour: int,
                               x: float, y: float) -> Tuple[float, float]:
    """Map an absolute image point (x, y) to (t_radial, t_tangential) in
    the canonical frame's nominal-hour-axis basis: t_radial is the
    component along the nominal hour direction (canonical units, same
    scale as master.DIAL_EDGE_R / MINUTE_TRACK_R -- 0 at the dial
    centre), t_tangential is the perpendicular component (positive =
    clockwise from the axis). Exact via marker_consensus.inverse_map
    (the tested algebraic inverse of geometry.map_point), not a
    linearisation -- valid anywhere in the image."""
    canon_x, canon_y = mc.inverse_map(ellipse, roll, x, y)
    angle = master.angle_for_hour(hour)
    ca, sa = math.cos(angle), math.sin(angle)
    t_radial = canon_x * ca + canon_y * sa
    t_tangential = -canon_x * sa + canon_y * ca
    return t_radial, t_tangential
