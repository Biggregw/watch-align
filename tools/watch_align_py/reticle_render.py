"""Three visual variants of the generic QC reticle (reticle.py's
geometry), rendered as thin, semi-transparent overlays over the real
photo. No Rolex-specific artwork anywhere -- every element is a plain
geometric reference.

Rendering technique: draw on a copy of the base image, then
cv2.addWeighted(overlay, alpha, base, 1-alpha) -- since overlay starts
identical to base except where something was just drawn, this blends
only the drawn pixels, giving true per-element transparency without a
separate mask.
"""
from __future__ import annotations

import math
from typing import Tuple

import cv2
import numpy as np

import geometry
import reticle as rt

# Palette: desaturated, high-contrast against both black and white dials.
# Pale cyan for minute-track/structural elements, warm gold for marker
# envelope guides (visually distinct "different source of truth" cue),
# soft white for the centre target. No saturated primary colours (reads
# as a debug screen, not an inspection instrument).
CYAN = (235, 220, 140)     # BGR -- pale cyan/blue
GOLD = (110, 190, 235)     # BGR -- warm amber/gold
WHITE = (245, 245, 245)
DIM = (170, 170, 170)


def _blend_line(img: np.ndarray, p0, p1, color, thickness=1, alpha=0.6):
    overlay = img.copy()
    cv2.line(overlay, p0, p1, color, thickness, cv2.LINE_AA)
    cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0, dst=img)


def _blend_polyline(img: np.ndarray, pts, color, thickness=1, alpha=0.55, closed=True):
    if len(pts) < 2:
        return
    overlay = img.copy()
    cv2.polylines(overlay, [np.array(pts, dtype=np.int32)], closed, color, thickness, cv2.LINE_AA)
    cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0, dst=img)


def _blend_circle(img: np.ndarray, center, radius, color, thickness=1, alpha=0.6, fill=False):
    overlay = img.copy()
    cv2.circle(overlay, center, radius, color, -1 if fill else thickness, cv2.LINE_AA)
    cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0, dst=img)


def _blend_marker(img: np.ndarray, center, color, size=6, thickness=1, alpha=0.7):
    overlay = img.copy()
    cv2.drawMarker(overlay, center, color, cv2.MARKER_TILTED_CROSS, size, thickness, cv2.LINE_AA)
    cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0, dst=img)


def _radial_tick_endpoints(geo: rt.ReticleGeometry, angle: float, r_center: float, radial_half_canonical: float):
    """Two endpoints of a short RADIAL stroke at canonical angle/radius,
    forward-mapped through the real ellipse basis -- never approximated
    as a fixed-pixel-length offset along the canonical unit direction,
    which is only exactly right at the image centre and drifts with the
    ellipse's own anisotropic scale/rotation everywhere else."""
    ca, sa = math.cos(angle), math.sin(angle)
    p0 = geometry.map_point(geo.ellipse, 1.0, geo.roll, (r_center - radial_half_canonical) * ca,
                             (r_center - radial_half_canonical) * sa)
    p1 = geometry.map_point(geo.ellipse, 1.0, geo.roll, (r_center + radial_half_canonical) * ca,
                             (r_center + radial_half_canonical) * sa)
    return (int(round(p0[0])), int(round(p0[1]))), (int(round(p1[0])), int(round(p1[1])))


def _centre_target(img: np.ndarray, center, color=WHITE):
    """Small unobtrusive bullseye -- two concentric rings + a tiny dot,
    deliberately far smaller than a full crosshair so it never obscures
    the hands."""
    _blend_circle(img, center, 3, color, thickness=1, alpha=0.75)
    _blend_circle(img, center, 7, color, thickness=1, alpha=0.4)


def _channel(img: np.ndarray, geo: rt.ReticleGeometry, alpha=0.5):
    _blend_polyline(img, geo.channel_inner, CYAN, thickness=1, alpha=alpha)
    _blend_polyline(img, geo.channel_outer, CYAN, thickness=1, alpha=alpha)


def _axes(img: np.ndarray, geo: rt.ReticleGeometry, alpha=0.5):
    strong = {12, 3, 6, 9}
    for hour, segs in geo.axis_segments:
        a = alpha + 0.15 if hour in strong else alpha
        th = 1
        for p0, p1 in segs:
            _blend_line(img, p0, p1, DIM, thickness=th, alpha=a)


def _round_envelope(img: np.ndarray, geo: rt.ReticleGeometry, which_list=("centre", "outer", "inner"), alpha=0.45):
    for which in which_list:
        ring = geo.round_envelope.get(which)
        if ring:
            _blend_polyline(img, ring, GOLD, thickness=1, alpha=alpha)


def _triangle_guide(img: np.ndarray, geo: rt.ReticleGeometry, alpha=0.6):
    if geo.triangle_outer_point is None:
        return
    tx, ty = geo.triangle_outer_point
    overlay = img.copy()
    cv2.drawMarker(overlay, (tx, ty), GOLD, cv2.MARKER_TRIANGLE_UP, 10, 1, cv2.LINE_AA)
    cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0, dst=img)


def variant_a_classic(bgr: np.ndarray, geo: rt.ReticleGeometry) -> np.ndarray:
    """A: classic radial reticle -- thin channel, short radial tick marks
    (not full brackets), full 12 axes with marker gaps, small centre
    target. No marker-envelope guides -- the most minute-track-centric,
    "traditional reticle" of the three."""
    out = bgr.copy()
    _channel(out, geo, alpha=0.5)
    channel_r = rt.master.MINUTE_TRACK_R
    for minute, (u, v), (x, y) in geo.tick_positions:
        is_hour = (minute % 5 == 0)
        radial_half = 0.016 if is_hour else 0.009  # canonical units, forward-mapped (see _radial_tick_endpoints)
        angle = math.radians(minute * 6.0 - 90.0)
        p0, p1 = _radial_tick_endpoints(geo, angle, channel_r, radial_half)
        _blend_line(out, p0, p1, DIM, thickness=1, alpha=0.45 if is_hour else 0.3)
    _axes(out, geo, alpha=0.45)
    _centre_target(out, geo.centre_xy)
    return out


def variant_b_vernier(bgr: np.ndarray, geo: rt.ReticleGeometry) -> np.ndarray:
    """B: split-line / Vernier-style -- each minute position is a small
    open GATE (two short tangential brackets flanking the expected
    position, radially offset inward/outward of the channel wall)
    instead of a line sitting on top of the tick. A correctly-placed
    real tick should visually sit centred in the gap between the two
    bracket strokes; a scale or centring error shows as the real tick
    drifting toward one side of its gate."""
    out = bgr.copy()
    _channel(out, geo, alpha=0.45)
    gate_half = 0.010    # canonical radial half-offset of each bracket from the channel wall
    tangent_half = 0.012  # canonical ANGULAR half-width of each bracket stroke -- forward-mapped
    # through the real ellipse basis (geometry.map_point), never approximated in raw image
    # pixels: the canonical tangential direction is NOT perpendicular-to-radial in image
    # space in general once the ellipse's anisotropic scale + rotation is applied.
    from master import MINUTE_TRACK_R as _CHANNEL_R
    for minute, (u, v), (x, y) in geo.tick_positions:
        is_hour = (minute % 5 == 0)
        angle = math.radians(minute * 6.0 - 90.0)
        for r in (_CHANNEL_R - rt.CHANNEL_HALF_WIDTH - gate_half, _CHANNEL_R + rt.CHANNEL_HALF_WIDTH + gate_half):
            p0 = geometry.map_point(geo.ellipse, 1.0, geo.roll, r * math.cos(angle - tangent_half), r * math.sin(angle - tangent_half))
            p1 = geometry.map_point(geo.ellipse, 1.0, geo.roll, r * math.cos(angle + tangent_half), r * math.sin(angle + tangent_half))
            p0i = (int(round(p0[0])), int(round(p0[1])))
            p1i = (int(round(p1[0])), int(round(p1[1])))
            _blend_line(out, p0i, p1i, DIM, thickness=1, alpha=0.55 if is_hour else 0.35)
    _axes(out, geo, alpha=0.45)
    _centre_target(out, geo.centre_xy)
    return out


def variant_c_minimal(bgr: np.ndarray, geo: rt.ReticleGeometry) -> np.ndarray:
    """C: minimal minute-channel + marker-envelope design -- the channel
    and 12 axes only (no per-minute tick marks at all, or only at hour
    positions), with the round-marker envelope guides and triangle
    outer-relationship guide as the main event. Cleanest, most
    "inspection instrument" look; leans most heavily on the marker-
    envelope geometry this session actually validated for sensitivity to
    radial displacement."""
    out = bgr.copy()
    _channel(out, geo, alpha=0.4)
    channel_r = rt.master.MINUTE_TRACK_R
    for minute, (u, v), (x, y) in geo.tick_positions:
        if minute % 5 != 0:
            continue
        angle = math.radians(minute * 6.0 - 90.0)
        p0, p1 = _radial_tick_endpoints(geo, angle, channel_r, 0.014)
        _blend_line(out, p0, p1, DIM, thickness=1, alpha=0.35)
    _axes(out, geo, alpha=0.4)
    _round_envelope(out, geo, alpha=0.5)
    _triangle_guide(out, geo, alpha=0.6)
    _centre_target(out, geo.centre_xy)
    return out


VARIANTS = {
    "A_classic": variant_a_classic,
    "B_vernier": variant_b_vernier,
    "C_minimal": variant_c_minimal,
}
