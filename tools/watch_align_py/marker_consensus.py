"""Projective marker-consensus QC (experiment/projective-marker-consensus).

Core idea, deliberately different from every prior approach in this
research line (multi-radius homography, pose-library-overlay): those all
tried to recover an absolute camera pose and then compare each marker to
an externally-derived ideal position -- which hit a real, provable
identifiability wall (the orthographic depth-reversal ambiguity documented
in docs/research/pose-library-overlay-video-results-2026-09-22.md).

This approach never tries to recover pose at all. It fits geometric
consensus (conics, angular structure) directly from the OTHER markers
observed in the SAME photograph, and asks only: "does this one marker
behave like its peers, under whatever unknown projective transform this
photograph happens to be under?" A circle (the ring traced by the 8 round
markers' outer edges, or centres, or inner edges) maps to a conic under
ANY projective transform, so fitting that conic directly from real
observed points sidesteps needing to know the transform at all -- exactly
the same reason multi-radius homography fitting worked reasonably well
for TICKS (a real, labelled ring of points), except here the ring itself
is the thing under test, evaluated via leave-one-out so a marker is never
allowed to influence its own reference.

This is explicitly NOT an authenticity classifier and NOT specific to any
one marker (least of all hour 12) -- it is a general peer-consistency
signal, meant to draw attention, not to issue a verdict.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

import cv2
import numpy as np

import master
from geometry import RotatedRect

ROUND_HOURS = [1, 2, 4, 5, 7, 8, 10, 11]
SHAPE_HOURS = {12: "triangle", 6: "baton", 9: "baton"}
ALL_MARKER_HOURS = ROUND_HOURS + [6, 9, 12]

# Canonical (dial-radius-normalised) outer-edge radii, derived from the
# EXISTING master geometry constants (master.py, untouched) -- not new
# master geometry, just reading the relationships already implied by it.
ROUND_OUTER_CANON_R = master.ROUND_CENTER_R + master.ROUND_OUTER_R      # 0.821
TRI_OUTER_CANON_R = master.TRI_CENTER_R + master.TRI_BASE_OUTWARD       # 0.815
BATON_OUTER_CANON_R = master.BATON_CENTER_R + master.BATON_RADIAL_HALF  # 0.793

MIN_PEERS_FOR_CONIC = 5  # a conic has 5 DOF; below this, fitting is meaningless
MIN_PEERS_FOR_LOO = 6    # leave-one-out needs the 5-DOF fit to remain well-posed after removal


# --------------------------------------------------------------------------
# Marker segmentation (image-space, with outer/inner radial contour points)
# --------------------------------------------------------------------------

@dataclass
class MarkerObservation:
    hour: int
    shape: str  # "round" | "triangle" | "baton"
    centroid_x: float
    centroid_y: float
    outer_x: float
    outer_y: float
    inner_x: float
    inner_y: float
    area_norm: float
    anisotropy: float
    diameter_px: float
    principal_axis_deg: float  # image-space blob orientation; nan for round (no defined axis)
    confidence: float          # 0..1 heuristic, see _confidence()


def _basis_at(ellipse: RotatedRect, roll: float, radius: float, angle: float):
    """Local radial/tangential differential basis at a canonical (radius,
    angle), exact duplicate of marker_qc._basis_at's math (not imported --
    marker_qc.py is left untouched per instruction; this module stands
    alone). Returns (center_xy, rx, ry, tx, ty, det) or None."""
    ca, sa = math.cos(angle), math.sin(angle)

    def _map(x, y):
        roll_r = math.radians(roll)
        cr, sr = math.cos(roll_r), math.sin(roll_r)
        xr = cr * x - sr * y
        yr = sr * x + cr * y
        axis = math.radians(ellipse.angle_deg)
        cax, sax = math.cos(axis), math.sin(axis)
        local_x = cax * xr + sax * yr
        local_y = -sax * xr + cax * yr
        rx = ellipse.w * 0.5
        ry = ellipse.h * 0.5
        sx = rx * local_x
        sy = ry * local_y
        return (ellipse.cx + cax * sx - sax * sy, ellipse.cy + sax * sx + cax * sy)

    center = _map(radius * ca, radius * sa)
    eps = 0.04
    r0 = _map((radius - eps) * ca, (radius - eps) * sa)
    r1 = _map((radius + eps) * ca, (radius + eps) * sa)
    tx_dir, ty_dir = -sa, ca
    t0 = _map(radius * ca - eps * tx_dir, radius * sa - eps * ty_dir)
    t1 = _map(radius * ca + eps * tx_dir, radius * sa + eps * ty_dir)
    rx = (r1[0] - r0[0]) / (2.0 * eps)
    ry = (r1[1] - r0[1]) / (2.0 * eps)
    btx = (t1[0] - t0[0]) / (2.0 * eps)
    bty = (t1[1] - t0[1]) / (2.0 * eps)
    det = rx * bty - ry * btx
    return center, rx, ry, btx, bty, det


def inverse_map(ellipse: RotatedRect, roll_deg: float, x: float, y: float) -> Tuple[float, float]:
    """Global inverse of geometry.map_point: absolute image (x,y) -> the
    canonical (radial-basis) coordinates that would forward-map to it.
    Algebraic inverse of map_point's composition (roll -> axis rotate ->
    anisotropic scale -> axis rotate back -> translate), not a local
    linearisation -- valid anywhere in the image, unlike _basis_at above."""
    dx, dy = x - ellipse.cx, y - ellipse.cy
    axis = math.radians(ellipse.angle_deg)
    ca, sa = math.cos(axis), math.sin(axis)
    # undo the trailing rotate-by-axis (map_point's last step)
    sx = ca * dx + sa * dy
    sy = -sa * dx + ca * dy
    rx = max(1e-6, ellipse.w * 0.5)
    ry = max(1e-6, ellipse.h * 0.5)
    local_x, local_y = sx / rx, sy / ry
    # undo the anisotropic-scale's PRECEDING rotate-by-axis (map_point's
    # local_x/local_y step) -- this step was missing in an earlier version
    # of this function, which silently produced a constant angular offset
    # equal to the ellipse's own axis angle; caught by
    # tests/test_marker_consensus.py::test_angular_analysis_zero_residual_on_perfect_grid
    # before any real image was analysed.
    xr = ca * local_x - sa * local_y
    yr = sa * local_x + ca * local_y
    # undo the leading rotate-by-roll
    roll = math.radians(roll_deg)
    cr, sr = math.cos(roll), math.sin(roll)
    canon_x = cr * xr + sr * yr
    canon_y = -sr * xr + cr * yr
    return canon_x, canon_y


def segment_marker(gray: np.ndarray, ellipse: RotatedRect, roll: float, dial_radius_px: float,
                    hour: int) -> Optional[MarkerObservation]:
    """Segment one marker in native image space, matching marker_qc's ROI/
    Otsu/contour logic (same window sizing and gates, reimplemented here
    rather than importing marker_qc's private helpers, so this experiment
    never touches or depends on internal changes to production QC code),
    but additionally returns the outer/inner radial contour points needed
    for conic-consensus fitting, which marker_qc's own return type
    (aggregate MarkerDiagnostic) does not carry."""
    shape = "triangle" if hour == 12 else ("baton" if hour in (6, 9) else "round")
    if hour == 12:
        radius = master.TRI_DETECTION_CENTER_R
    elif hour in (6, 9):
        radius = master.BATON_CENTER_R
    else:
        radius = master.ROUND_CENTER_R
    angle = master.angle_for_hour(hour)
    basis = _basis_at(ellipse, roll, radius, angle)
    if basis is None:
        return None
    center, rx, ry, btx, bty, det = basis
    if abs(det) < 1e-8:
        return None

    radial_min = -0.17 if hour == 12 else -0.13
    radial_max = 0.11 if hour == 12 else 0.13
    tangent_half = 0.10 if hour == 12 else 0.075
    min_area_norm = 0.010 if hour == 12 else 0.008
    max_area_norm = 0.060 if hour == 12 else 0.040
    target_area_norm = 0.025 if hour == 12 else 0.016

    max_basis = max(math.hypot(rx, ry), math.hypot(btx, bty))
    extent = int(math.ceil(max_basis * 0.20 + 5.0))
    h, w = gray.shape[:2]
    x0 = max(0, int(math.floor(center[0] - extent)))
    x1 = min(w - 1, int(math.ceil(center[0] + extent)))
    y0 = max(0, int(math.floor(center[1] - extent)))
    y1 = min(h - 1, int(math.ceil(center[1] + extent)))
    if x1 <= x0 or y1 <= y0:
        return None

    def local_of(px, py):
        ddx, ddy = px - center[0], py - center[1]
        return ((ddx * bty - ddy * btx) / det, (-ddx * ry + ddy * rx) / det)

    eligible = []
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            lx, ly = local_of(x, y)
            if lx < radial_min or lx > radial_max or abs(ly) > tangent_half:
                continue
            eligible.append((x, y, float(gray[y, x])))
    if not eligible:
        return None
    values = np.array([v for _x, _y, v in eligible], dtype=np.uint8)
    threshold, _ = cv2.threshold(values.reshape(-1, 1), 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    mask = np.zeros((y1 - y0 + 1, x1 - x0 + 1), dtype=np.uint8)
    for x, y, v in eligible:
        if v >= threshold:
            mask[y - y0, x - x0] = 255

    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
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
        cx, cy = x0 + m["m10"] / m00, y0 + m["m01"] / m00
        lx, ly = local_of(cx, cy)
        if abs(lx) > 0.080 or abs(ly) > 0.060:
            continue
        mu20, mu02, mu11 = m["mu20"], m["mu02"], m["mu11"]
        trace = mu20 + mu02
        disc = math.sqrt(max(0.0, (mu20 - mu02) ** 2 + 4.0 * mu11 * mu11))
        l1, l2 = (trace + disc) / 2.0, max(1e-9, (trace - disc) / 2.0)
        anisotropy = l1 / l2
        is_round = shape == "round"
        min_anisotropy = 1.35 if hour == 12 else (0.0 if is_round else 2.0)
        if anisotropy < min_anisotropy:
            continue
        axis_deg = math.degrees(0.5 * math.atan2(2.0 * mu11, mu20 - mu02))
        score = 4.0 * math.hypot(lx, ly) + 1.5 * abs(area_norm - target_area_norm)
        if score < best_score:
            best_score = score
            best = (contour, (cx, cy), area_norm, anisotropy, axis_deg)
    if best is None:
        return None

    contour, centroid, area_norm, anisotropy, axis_deg = best
    # outer/inner radial contour points: project every contour pixel into
    # the same local (radial,tangential) basis and take a small robust
    # average (top-3) at each extreme, rather than a single noisy pixel.
    pts = contour.reshape(-1, 2).astype(np.float64)
    pts[:, 0] += x0
    pts[:, 1] += y0
    locals_ = np.array([local_of(px, py) for px, py in pts])
    order = np.argsort(locals_[:, 0])
    k = min(3, len(order))
    outer_idx = order[-k:]
    inner_idx = order[:k]
    outer_xy = pts[outer_idx].mean(axis=0)
    inner_xy = pts[inner_idx].mean(axis=0)

    diameter_px = 2.0 * math.sqrt(max(0.0, area_norm) * dial_radius_px * dial_radius_px / math.pi)
    hull = cv2.convexHull(contour)
    hull_area = abs(cv2.contourArea(hull))
    solidity = (area_norm * dial_radius_px * dial_radius_px) / max(1.0, hull_area)
    confidence = _confidence(anisotropy, area_norm, target_area_norm, solidity, shape)

    return MarkerObservation(
        hour=hour, shape=shape,
        centroid_x=centroid[0], centroid_y=centroid[1],
        outer_x=float(outer_xy[0]), outer_y=float(outer_xy[1]),
        inner_x=float(inner_xy[0]), inner_y=float(inner_xy[1]),
        area_norm=area_norm, anisotropy=anisotropy, diameter_px=diameter_px,
        principal_axis_deg=(axis_deg if shape != "round" else math.nan),
        confidence=confidence,
    )


def _confidence(anisotropy: float, area_norm: float, target_area_norm: float,
                 solidity: float, shape: str) -> float:
    """Heuristic 0..1 segmentation-quality confidence, NOT a calibrated
    probability. Round markers should be close to isotropic (anisotropy
    near 1); triangle/baton are expected elongated so are not penalised
    for that. Documented as a heuristic requiring real calibration before
    any production use -- see Part 12 (failure safety)."""
    if shape == "round":
        roundness = max(0.0, 1.0 - abs(anisotropy - 1.0) / 2.0)
    else:
        roundness = 1.0  # elongation is expected/required for these shapes, already gated on above
    size_score = max(0.0, 1.0 - abs(area_norm - target_area_norm) / max(1e-6, target_area_norm))
    solidity_score = max(0.0, min(1.0, solidity))
    return max(0.0, min(1.0, 0.4 * roundness + 0.3 * size_score + 0.3 * solidity_score))


# --------------------------------------------------------------------------
# General conic fitting (direct least squares, Hartley-normalised)
# --------------------------------------------------------------------------

def fit_conic(points_xy: np.ndarray) -> Optional[np.ndarray]:
    """Fit Ax^2+Bxy+Cy^2+Dx+Ey+F=0 to points via normalised direct least
    squares (SVD on the design matrix, smallest singular vector). Returns
    the conic as a 3x3 symmetric matrix Q such that [x,y,1] Q [x,y,1]^T = 0,
    or None if under-determined/degenerate. Hartley (centre+isotropic-
    scale) normalisation before the SVD, matching standard practice for
    numerically stable conic/homography fitting -- without it the design
    matrix's columns span wildly different magnitudes (x^2 vs x vs 1) at
    real image-pixel scale and the fit is poorly conditioned."""
    pts = np.asarray(points_xy, dtype=np.float64)
    if len(pts) < MIN_PEERS_FOR_CONIC:
        return None
    mean = pts.mean(axis=0)
    centered = pts - mean
    avg_dist = float(np.mean(np.linalg.norm(centered, axis=1)))
    if avg_dist < 1e-9:
        return None
    scale = math.sqrt(2.0) / avg_dist
    T = np.array([[scale, 0.0, -scale * mean[0]],
                  [0.0, scale, -scale * mean[1]],
                  [0.0, 0.0, 1.0]])
    pn = centered * scale
    x, y = pn[:, 0], pn[:, 1]
    Dmat = np.stack([x * x, x * y, y * y, x, y, np.ones_like(x)], axis=1)
    try:
        _, _, Vt = np.linalg.svd(Dmat)
    except np.linalg.LinAlgError:
        return None
    a, b, c, d, e, f = Vt[-1]
    Qn = np.array([[a, b / 2.0, d / 2.0],
                   [b / 2.0, c, e / 2.0],
                   [d / 2.0, e / 2.0, f]])
    Q = T.T @ Qn @ T
    return Q


def fit_conic_robust(points_xy: np.ndarray, n_iter: int = 5, tukey_c: float = 4.685
                      ) -> Optional[np.ndarray]:
    """Iteratively-reweighted-least-squares conic fit (Tukey biweight),
    to limit how much a single contaminating point can distort the fit
    used to judge its PEERS. Plain least-squares fit_conic() distributes
    a single true anomaly's influence across the whole conic -- caught by
    tests/test_marker_consensus.py::test_leave_one_out_flags_injected_anomaly_only_on_that_marker,
    which showed an injected single-marker anomaly measurably inflating
    its two angularly-nearest PEERS' own (uncontaminated) leave-one-out
    residuals, since with only 7 peers for a 5-DOF conic there is very
    little redundancy to absorb one bad point. RANSAC was considered and
    rejected for this specific use: with only 7-8 total points, minimal-
    subset RANSAC has too few independent combinations to be meaningful;
    IRLS degrades more gracefully at this sample size."""
    pts = np.asarray(points_xy, dtype=np.float64)
    if len(pts) < MIN_PEERS_FOR_CONIC:
        return None
    weights = np.ones(len(pts))
    Q = fit_conic(pts)
    if Q is None:
        return None
    for _ in range(n_iter):
        Q = _fit_conic_weighted(pts, weights)
        if Q is None:
            return None
        resid = np.array([sampson_residual(Q, x, y) for x, y in pts])
        med = float(np.median(resid))
        mad = float(np.median(np.abs(resid - med))) or 1e-6
        robust_std = 1.4826 * mad
        u = resid / (tukey_c * max(robust_std, 1e-6))
        weights = np.where(u < 1.0, (1.0 - u ** 2) ** 2, 0.0)
        if weights.sum() < MIN_PEERS_FOR_CONIC:
            break  # degenerate: too many points downweighted to (near) zero
    return Q


def _fit_conic_weighted(points_xy: np.ndarray, weights: np.ndarray) -> Optional[np.ndarray]:
    pts = points_xy
    mean = np.average(pts, axis=0, weights=weights)
    centered = pts - mean
    avg_dist = float(np.average(np.linalg.norm(centered, axis=1), weights=weights))
    if avg_dist < 1e-9:
        return None
    scale = math.sqrt(2.0) / avg_dist
    T = np.array([[scale, 0.0, -scale * mean[0]],
                  [0.0, scale, -scale * mean[1]],
                  [0.0, 0.0, 1.0]])
    pn = centered * scale
    x, y = pn[:, 0], pn[:, 1]
    Dmat = np.stack([x * x, x * y, y * y, x, y, np.ones_like(x)], axis=1)
    Wd = np.sqrt(np.maximum(weights, 0.0))[:, None]
    try:
        _, _, Vt = np.linalg.svd(Dmat * Wd)
    except np.linalg.LinAlgError:
        return None
    a, b, c, d, e, f = Vt[-1]
    Qn = np.array([[a, b / 2.0, d / 2.0],
                   [b / 2.0, c, e / 2.0],
                   [d / 2.0, e / 2.0, f]])
    return T.T @ Qn @ T


def sampson_residual(Q: np.ndarray, x: float, y: float) -> float:
    """Approximate Euclidean (pixel) distance from (x,y) to the conic Q,
    via the first-order Sampson approximation: |F(p)| / |grad F(p)|."""
    p = np.array([x, y, 1.0])
    Fv = float(p @ Q @ p)
    grad = 2.0 * (Q @ p)[:2]
    gnorm = float(np.hypot(grad[0], grad[1]))
    if gnorm < 1e-9:
        return math.inf
    return abs(Fv) / gnorm


def conic_ray_point(Q: np.ndarray, center_xy: Tuple[float, float], angle_rad: float,
                     expected_dist: float) -> Optional[Tuple[float, float]]:
    """Intersect the conic Q with the ray from center_xy at angle_rad,
    returning the intersection point closest to expected_dist (there are
    generically two real roots for an ellipse not containing the centre;
    expected_dist -- e.g. the peer median distance -- disambiguates which
    one is the physically relevant marker-side intersection)."""
    cx, cy = center_xy
    u, v = math.cos(angle_rad), math.sin(angle_rad)
    p0 = np.array([cx, cy, 1.0])
    d = np.array([u, v, 0.0])
    alpha = float(d @ Q @ d)
    beta = 2.0 * float(d @ Q @ p0)
    gamma = float(p0 @ Q @ p0)
    if abs(alpha) < 1e-12:
        if abs(beta) < 1e-12:
            return None
        t = -gamma / beta
        return (cx + t * u, cy + t * v)
    disc = beta * beta - 4.0 * alpha * gamma
    if disc < 0.0:
        return None
    sq = math.sqrt(disc)
    t1 = (-beta + sq) / (2.0 * alpha)
    t2 = (-beta - sq) / (2.0 * alpha)
    best_t = min((t1, t2), key=lambda t: abs(t - expected_dist))
    return (cx + best_t * u, cy + best_t * v)
