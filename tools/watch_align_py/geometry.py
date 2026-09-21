"""Shared RotatedRect representation + point mapping, mirroring the small
private helpers duplicated across several of the Java classes (MinuteTrackDialFinder.map,
MinuteTrackIdentityGate.map, GmtMarkerQcRepair.map, PerspectiveGmtOverlay.ellipseCardinalPoints).
"""
import math
from dataclasses import dataclass
from typing import Tuple


@dataclass
class RotatedRect:
    cx: float
    cy: float
    w: float
    h: float
    angle_deg: float  # OpenCV fitEllipse convention

    @staticmethod
    def from_cv(ellipse) -> "RotatedRect":
        (cx, cy), (w, h), angle = ellipse
        return RotatedRect(cx, cy, w, h, angle)

    def scaled(self, scale: float) -> "RotatedRect":
        return RotatedRect(self.cx, self.cy, self.w * scale, self.h * scale, self.angle_deg)

    def recentered(self, cx: float, cy: float) -> "RotatedRect":
        return RotatedRect(cx, cy, self.w, self.h, self.angle_deg)


def map_point(e: RotatedRect, scale: float, roll_deg: float, x: float, y: float) -> Tuple[float, float]:
    """Map a normalized (radial-basis) point through roll, then the ellipse's own axis
    rotation, then its half-axes scaled by `scale`, then translate to the ellipse centre.
    Exact port of the `map(...)` helper duplicated in several Java classes."""
    roll = math.radians(roll_deg)
    cr, sr = math.cos(roll), math.sin(roll)
    xr = cr * x - sr * y
    yr = sr * x + cr * y
    axis = math.radians(e.angle_deg)
    ca, sa = math.cos(axis), math.sin(axis)
    local_x = ca * xr + sa * yr
    local_y = -sa * xr + ca * yr
    rx = e.w * 0.5 * scale
    ry = e.h * 0.5 * scale
    sx = rx * local_x
    sy = ry * local_y
    return (e.cx + ca * sx - sa * sy, e.cy + sa * sx + ca * sy)


def local_point(e: RotatedRect, x: float, y: float) -> Tuple[float, float]:
    """Inverse-ish helper: express an absolute (x,y) in the ellipse's own radial/tangential
    basis, without any roll applied (used by GmtMarkerQcRepair's Basis.local)."""
    dx = x - e.cx
    dy = y - e.cy
    axis = math.radians(e.angle_deg)
    ca, sa = math.cos(axis), math.sin(axis)
    local_x = ca * dx + sa * dy
    local_y = -sa * dx + ca * dy
    rx = max(1e-6, e.w * 0.5)
    ry = max(1e-6, e.h * 0.5)
    return (local_x / rx, local_y / ry)


def ellipse_cardinal_points(e: RotatedRect, roll_deg: float):
    """Port of PerspectiveGmtOverlay.ellipseCardinalPoints: the 4 points at
    canonical (0,-1),(1,0),(0,1),(-1,0) after roll then ellipse axis/scale."""
    canonical = [(0.0, -1.0), (1.0, 0.0), (0.0, 1.0), (-1.0, 0.0)]
    return [map_point(e, 1.0, roll_deg, x, y) for x, y in canonical]


def percentile_sorted(sorted_values, q: float, cap: float) -> float:
    if not sorted_values:
        return cap
    pos = q * (len(sorted_values) - 1)
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return sorted_values[lo]
    f = pos - lo
    return sorted_values[lo] * (1.0 - f) + sorted_values[hi] * f


def bilinear_sample(field, x: float, y: float, cap: float) -> float:
    """Bilinear sample of a single-channel float field at (x,y), matching the Java
    Mat.get(row,col) = field[y,x] convention. Returns `cap` out of bounds."""
    h, w = field.shape[:2]
    if x < 1 or y < 1 or x >= w - 1 or y >= h - 1:
        return cap
    x0 = math.floor(x)
    y0 = math.floor(y)
    fx = x - x0
    fy = y - y0
    d00 = float(field[y0, x0])
    d10 = float(field[y0, x0 + 1])
    d01 = float(field[y0 + 1, x0])
    d11 = float(field[y0 + 1, x0 + 1])
    d = (d00 * (1.0 - fx) + d10 * fx) * (1.0 - fy) + (d01 * (1.0 - fx) + d11 * fx) * fy
    return min(cap, max(0.0, d))
