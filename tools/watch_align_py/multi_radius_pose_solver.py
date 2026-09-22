"""Experimental: fit a properly-constrained dial homography from many
independent point correspondences at TWO different canonical radii (minute-
track ticks at 0.891, dial boundary at ~1.0), then rectify the image into
canonical/frontal space and detect markers there -- instead of detecting in
the original skewed image and transforming the already-detected centroid
afterward (the approach already shown, twice, to make repeatability worse).

Why this is a different bet than the two already-rejected approaches:
  - it never touches the noisy, narrowly-bounded final accepted homography
    from DialProjectiveRefiner, and never needs camera intrinsics/standoff;
  - it fits a general 8-DOF homography via a standard, well-conditioned
    direct linear transform + RANSAC over dozens of real point
    correspondences spanning two radii, rather than a 4-point exact fit
    (radius 1.0 only) or a hand-derived closed-form correction that turned
    out to depend on an unobservable camera parameter;
  - it rectifies FIRST and detects SECOND, so marker segmentation happens in
    a frame where circular/symmetric assumptions actually hold (if the fit
    is good), rather than being measured in the still-skewed original and
    only having its coordinates transformed afterward.

Never uses the marker under test to move the pose: every correspondence
used to fit the homography comes from minute-track ticks (all minutes not a
multiple of 5, i.e. never an hour-marker angle) or the outer dial boundary,
both independent of any specific hour marker's own detected blob.
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from typing import List, Optional, Tuple

import cv2
import numpy as np

import master
import marker_qc
from geometry import RotatedRect

TRACK_R = master.MINUTE_TRACK_R
DIAL_EDGE_R = master.DIAL_EDGE_R
LOSS_CAP_PX = 10.0
# Point localisation: a 1D radial-only search along a fixed assumed angular line
# was tried first and rejected -- even a homography fit purely to 48 same-radius
# tick points had ~3.9px in-sample residual on its own fitting points (it should
# be near-zero for points that truly lie on a common conic). The fix is a small
# 2D window (radial x tangential) around the expected position, localising each
# point as the centroid of real edge pixels found inside it, which brought the
# same in-sample check down to ~0.5px median. Single-radius point sets are also
# a known degenerate configuration for general homography estimation (a
# conic-preserving family of homographies maps a circle to the same ellipse) --
# this is exactly why the two radii (ticks + boundary) are combined, mirroring
# why DialProjectiveRefiner's own evidence score already needs both.
TICK_RADIAL_HALF = 0.02
TICK_TANGENTIAL_HALF_DEG = 1.0
TICK_GRID_N = 9
BOUNDARY_SEARCH_LO = 0.94
BOUNDARY_SEARCH_HI = 1.08
BOUNDARY_RADIAL_HALF = (BOUNDARY_SEARCH_HI - BOUNDARY_SEARCH_LO) / 2.0
BOUNDARY_TANGENTIAL_HALF_DEG = 2.0
BOUNDARY_GRID_N = 9
BOUNDARY_ANGLES_N = 48
MIN_EDGE_PIXELS = 3
MIN_CORRESPONDENCES = 12
RECTIFY_SIDE = 900
RECTIFY_SCALE = RECTIFY_SIDE / 2.15

MARKER_HOURS = [1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12]


@dataclass
class Correspondence:
    kind: str  # "tick" or "boundary"
    canonical_x: float
    canonical_y: float
    image_x: float
    image_y: float
    n_edge_pixels: int


@dataclass
class SolveResult:
    homography: Optional[np.ndarray]
    correspondences: List[Correspondence]
    n_ticks: int
    n_boundary: int
    reproj_error_px: float
    inlier_fraction: float


def _localize_centroid(edges: np.ndarray, ellipse: RotatedRect, roll: float,
                        nominal_radius: float, angle: float,
                        radial_half: float, tangential_half_deg: float, grid_n: int
                        ) -> Optional[Tuple[float, float, int]]:
    """Localise a point as the centroid of real edge pixels inside a small 2D
    (radial x tangential) canonical window around the expected position.
    Returns (image_x, image_y, n_edge_pixels_found) or None if no edge pixel
    fell inside the window."""
    tangential_half = math.radians(tangential_half_deg)
    h, w = edges.shape[:2]
    xs: List[float] = []
    ys: List[float] = []
    for i in range(grid_n):
        r = nominal_radius - radial_half + 2.0 * radial_half * i / max(1, grid_n - 1)
        for j in range(grid_n):
            da = -tangential_half + 2.0 * tangential_half * j / max(1, grid_n - 1)
            a = angle + da
            x, y = marker_qc._map(ellipse, roll, r * math.cos(a), r * math.sin(a))
            xi, yi = int(round(x)), int(round(y))
            if 0 <= yi < h and 0 <= xi < w and edges[yi, xi] > 0:
                xs.append(x)
                ys.append(y)
    if len(xs) < MIN_EDGE_PIXELS:
        return None
    return sum(xs) / len(xs), sum(ys) / len(ys), len(xs)


def extract_correspondences(edges: np.ndarray, ellipse: RotatedRect, roll: float) -> List[Correspondence]:
    out: List[Correspondence] = []

    for minute in range(60):
        if minute % 5 == 0:
            continue  # hour-marker angle; never used to constrain the pose
        angle = math.radians(minute * 6.0 - 90.0)
        loc = _localize_centroid(edges, ellipse, roll, TRACK_R, angle,
                                  TICK_RADIAL_HALF, TICK_TANGENTIAL_HALF_DEG, TICK_GRID_N)
        if loc is None:
            continue
        img_x, img_y, n_px = loc
        out.append(Correspondence("tick", TRACK_R * math.cos(angle), TRACK_R * math.sin(angle),
                                   img_x, img_y, n_px))

    boundary_mid = (BOUNDARY_SEARCH_LO + BOUNDARY_SEARCH_HI) / 2.0
    for i in range(BOUNDARY_ANGLES_N):
        angle = 2.0 * math.pi * i / BOUNDARY_ANGLES_N
        loc = _localize_centroid(edges, ellipse, roll, boundary_mid, angle,
                                  BOUNDARY_RADIAL_HALF, BOUNDARY_TANGENTIAL_HALF_DEG, BOUNDARY_GRID_N)
        if loc is None:
            continue
        img_x, img_y, n_px = loc
        out.append(Correspondence("boundary", DIAL_EDGE_R * math.cos(angle), DIAL_EDGE_R * math.sin(angle),
                                   img_x, img_y, n_px))

    return out


def fit_homography(correspondences: List[Correspondence]) -> SolveResult:
    n_ticks = sum(1 for c in correspondences if c.kind == "tick")
    n_boundary = sum(1 for c in correspondences if c.kind == "boundary")
    if len(correspondences) < MIN_CORRESPONDENCES:
        return SolveResult(None, correspondences, n_ticks, n_boundary, math.nan, 0.0)

    src = np.array([[c.canonical_x, c.canonical_y] for c in correspondences], dtype=np.float64)
    dst = np.array([[c.image_x, c.image_y] for c in correspondences], dtype=np.float64)
    H, mask = cv2.findHomography(src, dst, cv2.RANSAC, ransacReprojThreshold=1.5)
    if H is None:
        return SolveResult(None, correspondences, n_ticks, n_boundary, math.nan, 0.0)

    inliers = mask.ravel().astype(bool) if mask is not None else np.ones(len(correspondences), dtype=bool)
    errs = []
    for c, is_in in zip(correspondences, inliers):
        if not is_in:
            continue
        v = H @ np.array([c.canonical_x, c.canonical_y, 1.0])
        if abs(v[2]) < 1e-9:
            continue
        px, py = v[0] / v[2], v[1] / v[2]
        errs.append(math.hypot(px - c.image_x, py - c.image_y))
    reproj = float(np.median(errs)) if errs else math.nan
    inlier_frac = float(inliers.sum()) / max(1, len(correspondences))
    return SolveResult(H, correspondences, n_ticks, n_boundary, reproj, inlier_frac)


def rectify(bgr: np.ndarray, H: np.ndarray, side: int = RECTIFY_SIDE) -> np.ndarray:
    scale = side / 2.15
    S = np.array([[scale, 0.0, side / 2.0], [0.0, scale, side / 2.0], [0.0, 0.0, 1.0]])
    Hinv = np.linalg.inv(H)
    M = S @ Hinv
    return cv2.warpPerspective(bgr, M, (side, side), flags=cv2.INTER_CUBIC,
                                borderMode=cv2.BORDER_CONSTANT, borderValue=(31, 17, 8))


def _canonical_to_rect_px(x: float, y: float, side: int) -> Tuple[float, float]:
    scale = side / 2.15
    return side / 2.0 + x * scale, side / 2.0 + y * scale


def detect_marker_in_rectified(rectified_gray: np.ndarray, side: int, hour: int
                                ) -> Optional[Tuple[float, float, float, float]]:
    """Detect a marker directly in rectified/canonical space. Returns
    (radial_pct_r, angular_deg, area_norm, anisotropy) or None. Mirrors
    marker_qc's detection logic (Otsu threshold + contour + moments) but the
    ROI is now a fixed-scale rectangle -- no per-image ellipse basis needed,
    since rectification already removed the perspective distortion."""
    radius = marker_qc.expected_radius_ratio(hour)
    angle = master.angle_for_hour(hour)
    cx, cy = radius * math.cos(angle), radius * math.sin(angle)
    radial_min = -0.17 if hour == 12 else -0.13
    radial_max = 0.11 if hour == 12 else 0.13
    tangent_half = 0.10 if hour == 12 else 0.075
    min_area_norm = 0.010 if hour == 12 else 0.008
    max_area_norm = 0.060 if hour == 12 else 0.040
    target_area_norm = 0.025 if hour == 12 else 0.016

    ux, uy = math.cos(angle), math.sin(angle)
    vx, vy = -uy, ux
    scale = side / 2.15
    extent_canon = max(abs(radial_min), radial_max, tangent_half) * 1.3
    extent_px = int(math.ceil(extent_canon * scale))
    px_c, py_c = _canonical_to_rect_px(cx, cy, side)
    x0, x1 = max(0, int(px_c - extent_px)), min(side - 1, int(px_c + extent_px))
    y0, y1 = max(0, int(py_c - extent_px)), min(side - 1, int(py_c + extent_px))
    if x1 <= x0 or y1 <= y0:
        return None

    eligible = []
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            cx_i = (x - side / 2.0) / scale
            cy_i = (y - side / 2.0) / scale
            dx, dy = cx_i - cx, cy_i - cy
            local_r = dx * ux + dy * uy
            local_t = dx * vx + dy * vy
            if local_r < radial_min or local_r > radial_max or abs(local_t) > tangent_half:
                continue
            eligible.append((x, y, int(rectified_gray[y, x])))
    if not eligible:
        return None
    values = np.array([v for _x, _y, v in eligible], dtype=np.uint8)
    threshold, _ = cv2.threshold(values.reshape(-1, 1), 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    mask = np.zeros((y1 - y0 + 1, x1 - x0 + 1), dtype=np.uint8)
    for x, y, v in eligible:
        if v >= threshold:
            mask[y - y0, x - x0] = 255

    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    dial_radius_px = scale  # radius 1.0 in canonical == `scale` px in rectified frame
    best = None
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
        centroid_x = x0 + m["m10"] / m00
        centroid_y = y0 + m["m01"] / m00
        ccx = (centroid_x - side / 2.0) / scale
        ccy = (centroid_y - side / 2.0) / scale
        dx, dy = ccx - cx, ccy - cy
        local_r = dx * ux + dy * uy
        local_t = dx * vx + dy * vy
        if abs(local_r) > 0.080 or abs(local_t) > 0.060:
            continue
        mu20, mu02, mu11 = m["mu20"], m["mu02"], m["mu11"]
        trace = mu20 + mu02
        disc = math.sqrt(max(0.0, (mu20 - mu02) ** 2 + 4.0 * mu11 * mu11))
        l1, l2 = (trace + disc) / 2.0, max(1e-9, (trace - disc) / 2.0)
        anisotropy = l1 / l2
        is_round_marker = hour not in (12, 6, 9)
        min_anisotropy = 1.35 if hour == 12 else (0.0 if is_round_marker else 2.0)
        if anisotropy < min_anisotropy:
            continue
        score = 4.0 * math.hypot(local_r, local_t) + 1.5 * abs(area_norm - target_area_norm)
        if score < best_score:
            best_score = score
            radius_final = math.hypot(ccx, ccy)
            radial_pct_r = marker_qc.radial_offset_pct_r(radius_final, hour)
            clock_angle = math.degrees(math.atan2(ccx, -ccy))
            if clock_angle < 0:
                clock_angle += 360.0
            expected_clock = (math.degrees(angle) + 90.0) % 360.0
            angular_deg = ((clock_angle - expected_clock + 180.0) % 360.0) - 180.0
            best = (radial_pct_r, angular_deg, area_norm, anisotropy)
    return best


def measure_all_markers(bgr: np.ndarray, edges: np.ndarray, ellipse: RotatedRect, roll: float
                         ) -> Tuple[SolveResult, dict]:
    """Full pipeline: extract tick/boundary correspondences, fit H_new,
    rectify, detect every marker in rectified space. Returns (solve_result,
    {hour: (radial_pct_r, angular_deg, area_norm, anisotropy) or None})."""
    correspondences = extract_correspondences(edges, ellipse, roll)
    solve = fit_homography(correspondences)
    results = {}
    if solve.homography is None:
        for hour in MARKER_HOURS:
            results[hour] = None
        return solve, results
    rectified = rectify(bgr, solve.homography)
    rectified_gray = cv2.cvtColor(rectified, cv2.COLOR_BGR2GRAY)
    for hour in MARKER_HOURS:
        results[hour] = detect_marker_in_rectified(rectified_gray, RECTIFY_SIDE, hour)
    return solve, results
