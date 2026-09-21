"""Port of GmtMarkerQcRepair.java. GMT marker QC using the accepted minute-track
pose as the datum. Marker positions are found by projecting a small, shape-specific
ROI from the genuine-derived detection datum and isolating only a connected bright
component that is geometrically plausible there -- never a broad hour-sector average.
"""
import math
from dataclasses import dataclass
from typing import List, Optional

import cv2
import numpy as np

import master
import minute_track_dial_finder as mtdf
import seed_detector
from geometry import RotatedRect

TRI_DETECTION_CENTER_R = master.TRI_DETECTION_CENTER_R


@dataclass
class MarkerDiagnostic:
    hour: int
    angular_deg: float
    radial_pct_r: float
    body_rotation_deg: float
    minute_track_anchored: bool
    measured: bool = True
    # NaN except for hour 12: measured outward-base position vs the genuine reference, in %R.
    triangle_outward_delta_pct_r: float = math.nan


@dataclass
class _Basis:
    center: np.ndarray  # (x, y)
    rx: float
    ry: float
    tx: float
    ty: float
    det: float

    def local(self, x: float, y: float):
        dx = x - self.center[0]
        dy = y - self.center[1]
        return ((dx * self.ty - dy * self.tx) / self.det,
                (-dx * self.ry + dy * self.rx) / self.det)


@dataclass
class _Candidate:
    center: np.ndarray  # (x, y)
    radial_local: float
    tangent_local: float
    area_norm: float
    rotation_deg: float
    anisotropy: float
    outward_extent_local: float


def _wrap90(deg: float) -> float:
    x = deg % 180.0
    if x <= -90.0:
        x += 180.0
    if x > 90.0:
        x -= 180.0
    return x


def _wrap180(deg: float) -> float:
    x = deg % 360.0
    if x <= -180.0:
        x += 360.0
    if x > 180.0:
        x -= 360.0
    return x


def _wrap360(deg: float) -> float:
    x = deg % 360.0
    if x < 0:
        x += 360.0
    return x


def _smallest_axis_error(measured_axis_deg: float, expected_axis_deg: float) -> float:
    return _wrap90(measured_axis_deg - expected_axis_deg)


def _clock_angle_deg(cx: float, cy: float, x: float, y: float) -> float:
    a = math.degrees(math.atan2(x - cx, -(y - cy)))
    if a < 0:
        a += 360.0
    return a


def _hour_angle_deg(hour: int) -> float:
    if hour == 12:
        return 0.0
    return (hour % 12) * 30.0


def expected_radius_ratio(hour: int) -> float:
    """Expected bright-component centroid used for radial QC. Deliberately separate
    from the visual triangle anchor because a triangle's image centroid is not its
    geometric centre. 6/9 use their own baton centre rather than the round-marker centre."""
    if hour == 12:
        return master.TRI_DETECTION_CENTER_R
    if hour in (6, 9):
        return master.BATON_CENTER_R
    return master.ROUND_CENTER_R


def visual_radius_ratio(hour: int) -> float:
    if hour == 12:
        return master.TRI_CENTER_R
    if hour in (6, 9):
        return master.BATON_CENTER_R
    return master.ROUND_CENTER_R


def radial_offset_pct_r(normalized_radius: float, hour: int) -> float:
    return 100.0 * (normalized_radius - expected_radius_ratio(hour))


def expected_triangle_outward_local() -> float:
    """How far outward the 12 triangle's own genuine reference base sits, in the
    detection ROI's local radial units, i.e. relative to TRI_DETECTION_CENTER_R."""
    return (master.TRI_CENTER_R + master.TRI_BASE_OUTWARD) - master.TRI_DETECTION_CENTER_R


def triangle_outward_delta_pct_r(measured_outward_local: float) -> float:
    """Positive = the measured triangle base sits further outward (closer to the minute track)."""
    return 100.0 * (measured_outward_local - expected_triangle_outward_local())


def _undo_ellipse_distortion(ellipse: RotatedRect, dx: float, dy: float):
    axis = math.radians(ellipse.angle_deg)
    ca, sa = math.cos(axis), math.sin(axis)
    rx = max(1e-6, ellipse.w / 2.0)
    ry = max(1e-6, ellipse.h / 2.0)
    local_x = ca * dx + sa * dy
    local_y = -sa * dx + ca * dy
    scaled_x = local_x / rx
    scaled_y = local_y / ry
    return (ca * scaled_x - sa * scaled_y, sa * scaled_x + ca * scaled_y)


def _map(e: RotatedRect, roll_deg: float, x: float, y: float):
    roll = math.radians(roll_deg)
    cr, sr = math.cos(roll), math.sin(roll)
    xr = cr * x - sr * y
    yr = sr * x + cr * y
    axis = math.radians(e.angle_deg)
    ca, sa = math.cos(axis), math.sin(axis)
    local_x = ca * xr + sa * yr
    local_y = -sa * xr + ca * yr
    rx = e.w * 0.5
    ry = e.h * 0.5
    sx = rx * local_x
    sy = ry * local_y
    return (e.cx + ca * sx - sa * sy, e.cy + sa * sx + ca * sy)


def _basis_at(ellipse: RotatedRect, roll: float, radius: float, angle: float) -> Optional[_Basis]:
    ca, sa = math.cos(angle), math.sin(angle)
    center = _map(ellipse, roll, radius * ca, radius * sa)
    eps = 0.04
    r0 = _map(ellipse, roll, (radius - eps) * ca, (radius - eps) * sa)
    r1 = _map(ellipse, roll, (radius + eps) * ca, (radius + eps) * sa)
    tx, ty = -sa, ca
    t0 = _map(ellipse, roll, radius * ca - eps * tx, radius * sa - eps * ty)
    t1 = _map(ellipse, roll, radius * ca + eps * tx, radius * sa + eps * ty)
    rx = (r1[0] - r0[0]) / (2.0 * eps)
    ry = (r1[1] - r0[1]) / (2.0 * eps)
    btx = (t1[0] - t0[0]) / (2.0 * eps)
    bty = (t1[1] - t0[1]) / (2.0 * eps)
    det = rx * bty - ry * btx
    return _Basis(np.array(center, dtype=np.float64), rx, ry, btx, bty, det)


def _max_radial_local(contour: np.ndarray, x0: int, y0: int, basis: _Basis) -> float:
    best = -math.inf
    for p in contour.reshape(-1, 2):
        local_x, _ = basis.local(x0 + float(p[0]), y0 + float(p[1]))
        if local_x > best:
            best = local_x
    return best


def _measure_projected_marker(gray: np.ndarray, ellipse: RotatedRect, roll: float,
                               dial_radius_px: float, hour: int) -> Optional[_Candidate]:
    """Isolate one marker inside a small ROI projected from its genuine-derived
    detection datum. The marker under test never moves the ROI or the pose. Shape/
    area/centroid bounds are detection sanity checks only; they intentionally cause
    a safe "not measurable" result on ambiguous images."""
    radius = expected_radius_ratio(hour)
    angle = master.angle_for_hour(hour)
    basis = _basis_at(ellipse, roll, radius, angle)
    if basis is None or abs(basis.det) < 1e-8:
        return None

    radial_min = -0.17 if hour == 12 else -0.13
    radial_max = 0.11 if hour == 12 else 0.13
    tangent_half = 0.10 if hour == 12 else 0.075
    min_area_norm = 0.010 if hour == 12 else 0.008
    max_area_norm = 0.060 if hour == 12 else 0.040
    target_area_norm = 0.025 if hour == 12 else 0.016

    max_basis = max(math.hypot(basis.rx, basis.ry), math.hypot(basis.tx, basis.ty))
    extent = int(math.ceil(max_basis * 0.20 + 5.0))
    h, w = gray.shape[:2]
    x0 = max(0, int(math.floor(basis.center[0] - extent)))
    x1 = min(w - 1, int(math.ceil(basis.center[0] + extent)))
    y0 = max(0, int(math.floor(basis.center[1] - extent)))
    y1 = min(h - 1, int(math.ceil(basis.center[1] + extent)))
    if x1 <= x0 or y1 <= y0:
        return None

    mask = np.zeros((y1 - y0 + 1, x1 - x0 + 1), dtype=np.uint8)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            local_x, local_y = basis.local(x, y)
            if local_x < radial_min or local_x > radial_max or abs(local_y) > tangent_half:
                continue
            v = float(gray[y, x])
            if v < 150.0:
                continue
            mask[y - y0, x - x0] = 255

    contours, _hierarchy = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    best: Optional[_Candidate] = None
    best_score = math.inf
    for contour in contours:
        area = abs(cv2.contourArea(contour))
        area_norm = area / max(1.0, dial_radius_px * dial_radius_px)
        if area_norm < min_area_norm or area_norm > max_area_norm:
            continue
        m = cv2.moments(contour)
        m00 = m["m00"]
        if not (m00 > 0.0):
            continue
        center = (x0 + m["m10"] / m00, y0 + m["m01"] / m00)
        local_x, local_y = basis.local(center[0], center[1])
        if abs(local_x) > 0.080 or abs(local_y) > 0.060:
            continue

        mu20, mu02, mu11 = m["mu20"], m["mu02"], m["mu11"]
        trace = mu20 + mu02
        disc = math.sqrt(max(0.0, (mu20 - mu02) ** 2 + 4.0 * mu11 * mu11))
        l1 = (trace + disc) / 2.0
        l2 = max(1e-9, (trace - disc) / 2.0)
        anisotropy = l1 / l2
        # Round hour markers (1,2,4,5,7,8,10,11) are approximately circular: they have
        # no well-defined principal axis, so an elongation/rotation check tuned for
        # the batons' long-axis shape is meaningless here and is skipped entirely.
        is_round_marker = hour not in (12, 6, 9)
        axis = math.degrees(0.5 * math.atan2(2.0 * mu11, mu20 - mu02))
        expected_axis = math.degrees(math.atan2(basis.ry, basis.rx))
        rotation = _smallest_axis_error(axis, expected_axis)
        min_anisotropy = 1.35 if hour == 12 else (0.0 if is_round_marker else 2.0)
        if anisotropy < min_anisotropy:
            continue
        if not is_round_marker and abs(rotation) > 25.0:
            continue

        score = 4.0 * math.hypot(local_x, local_y) + 1.5 * abs(area_norm - target_area_norm)
        if score < best_score:
            best_score = score
            outward_extent_local = _max_radial_local(contour, x0, y0, basis) if hour == 12 else math.nan
            best = _Candidate(
                np.array(center, dtype=np.float64), local_x, local_y, area_norm,
                math.nan if is_round_marker else rotation, anisotropy, outward_extent_local,
            )
    return best


def measure(gray: np.ndarray, bgr: np.ndarray, edges: np.ndarray) -> Optional[List[Optional[MarkerDiagnostic]]]:
    """Returns a list indexed 0..12 (index 0 unused) of MarkerDiagnostic or None,
    mirroring GmtMarkerQcRepair.measure's Bitmap entry point split into pre-computed
    gray/bgr/edges (blur=GaussianBlur(gray,(5,5),1.2); edges=Canny(blur,55,145))."""
    dial = seed_detector.detect_dial(bgr)
    if dial is None:
        return None
    seed_x, seed_y, seed_r = dial.x, dial.y, dial.r
    if not (seed_r > 40.0):
        return None

    pose = mtdf.find(edges, seed_x, seed_y, seed_r)
    if pose is None or pose.dial_ellipse is None or not pose.top_phase_accepted:
        return None
    ellipse = pose.dial_ellipse
    roll = pose.roll_deg
    dial_radius_px = (max(ellipse.w, ellipse.h) + min(ellipse.w, ellipse.h)) / 4.0

    out: List[Optional[MarkerDiagnostic]] = [None] * 13
    for hour in range(1, 13):
        # 3 o'clock has no applied hour marker on this model: the date window sits there
        # instead. Measuring it anyway picks up the date numeral/aperture edge as a false
        # "marker" (observed on a real photo: a spurious but plausible-looking blob).
        if hour == 3:
            continue
        c = _measure_projected_marker(gray, ellipse, roll, dial_radius_px, hour)
        if c is None:
            out[hour] = MarkerDiagnostic(hour, math.nan, math.nan, math.nan, True, False)
            continue

        corrected = _undo_ellipse_distortion(
            ellipse, c.center[0] - ellipse.cx, c.center[1] - ellipse.cy)
        normalized_radius = math.hypot(corrected[0], corrected[1])
        corrected_clock = _clock_angle_deg(0, 0, corrected[0], corrected[1])
        expected_clock = _wrap360(_hour_angle_deg(hour) + roll)
        angular = _wrap180(corrected_clock - expected_clock)
        radial = radial_offset_pct_r(normalized_radius, hour)

        # These are detector sanity limits, not QC tolerances. Anything outside them is
        # overwhelmingly more likely to be the wrong image structure than a watch defect.
        if abs(angular) > 4.0 or abs(radial) > 8.0:
            out[hour] = MarkerDiagnostic(hour, math.nan, math.nan, math.nan, True, False)
            continue

        body = c.rotation_deg if (abs(c.rotation_deg) <= 12.0 and dial_radius_px >= 55.0) else math.nan
        outward_delta = (triangle_outward_delta_pct_r(c.outward_extent_local)
                          if (hour == 12 and math.isfinite(c.outward_extent_local)) else math.nan)
        out[hour] = MarkerDiagnostic(hour, angular, radial, body, True, True, outward_delta)
    return out


def measure_from_bgr(bgr: np.ndarray) -> Optional[List[Optional[MarkerDiagnostic]]]:
    """Convenience wrapper matching GmtMarkerQcRepair.measure(Bitmap)'s preprocessing:
    GaussianBlur(gray,(5,5),1.2) then Canny(blur,55,145)."""
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 1.2)
    edges = cv2.Canny(blur, 55, 145)
    return measure(gray, bgr, edges)
