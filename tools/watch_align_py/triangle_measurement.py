"""Independent 12-triangle segmentation (experiment/gmt-proportional-
geometry-v1).

Deliberately does NOT search only near where the triangle is "expected"
to be by any triangle-specific canonical constant (master.TRI_*). The
search region (a canonical-polar wedge) is sized only from generic,
already-justified peer/structural bounds -- reticle_v2.MARKER_GAP_LO/HI
(0.55-0.86 canonical radius, already checked in that module against the
full extents of ALL three marker shapes so it clears every plausible
marker position, not just the triangle's nominal one) and a wide +/-25
degree angular half-width (nominal hour spacing is 30 degrees, so this
comfortably avoids the 11 and 1 o'clock markers without assuming
anything about where 12's own marker actually sits within that band).
A found candidate is never rejected merely because it differs from any
expected position -- only for implausible area/shape (see
MIN_AREA_NORM/MAX_AREA_NORM below, both generic size sanity bounds, not
triangle-specific).
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from typing import List, Optional, Tuple

import cv2
import numpy as np

import geometry
import reticle_v2 as rv2
from geometry import RotatedRect

ANGLE_HALF_WIDTH_DEG = 25.0
R_LO = rv2.MARKER_GAP_LO   # 0.55 canonical -- generic marker-band inner bound
R_HI = rv2.MARKER_GAP_HI   # 0.86 canonical -- generic marker-band outer bound

# Generic plausible-blob area bounds (fraction of dial_radius_px^2), wide
# enough to admit any marker shape at any reasonable size -- not fit to
# the triangle specifically.
MIN_AREA_NORM = 0.004
MAX_AREA_NORM = 0.080

N_EXTREME_SUBSET = 5  # contour points averaged for the apex estimate (a true single vertex)
OUTWARD_SUBSET_FRACTION = 0.35  # fraction of contour points considered "outward" when
# searching for the base's left/right corners -- must span the whole base edge, see below


@dataclass
class TriangleObservation:
    hour: int
    apex_xy: Tuple[float, float]
    base_centre_xy: Tuple[float, float]
    base_left_xy: Tuple[float, float]
    base_right_xy: Tuple[float, float]
    centroid_xy: Tuple[float, float]
    symmetry_axis_deg: float          # image-space, apex->base direction
    pca_axis_deg: float               # image-space, contour principal axis (mod 180)
    axis_agreement_deg: float         # angular difference between the two axis estimates
    height_px: float
    base_width_px: float
    area_px: float
    solidity: float                   # contour area / convex-hull area
    n_contour_points: int
    confidence: float                 # 0..1 heuristic
    roi_polygon_xy: List[Tuple[int, int]]  # for diagnostic rendering only


def _roi_polygon(ellipse: RotatedRect, roll: float, hour: int, n_per_edge: int = 24
                  ) -> List[Tuple[int, int]]:
    angle0 = math.radians(hour * 30.0 - 90.0 - ANGLE_HALF_WIDTH_DEG)
    angle1 = math.radians(hour * 30.0 - 90.0 + ANGLE_HALF_WIDTH_DEG)

    def pt(r, a):
        x, y = geometry.map_point(ellipse, 1.0, roll, r * math.cos(a), r * math.sin(a))
        return (int(round(x)), int(round(y)))

    pts = []
    for i in range(n_per_edge + 1):
        a = angle0 + (angle1 - angle0) * i / n_per_edge
        pts.append(pt(R_LO, a))
    for i in range(n_per_edge + 1):
        a = angle1
        r = R_LO + (R_HI - R_LO) * i / n_per_edge
        pts.append(pt(r, a))
    for i in range(n_per_edge + 1):
        a = angle1 - (angle1 - angle0) * i / n_per_edge
        pts.append(pt(R_HI, a))
    for i in range(n_per_edge + 1):
        a = angle0
        r = R_HI - (R_HI - R_LO) * i / n_per_edge
        pts.append(pt(r, a))
    return pts


def segment_triangle_independent(gray: np.ndarray, ellipse: RotatedRect, roll: float,
                                  dial_radius_px: float, hour: int = 12
                                  ) -> Optional[TriangleObservation]:
    h, w = gray.shape[:2]
    poly = _roi_polygon(ellipse, roll, hour)
    mask = np.zeros((h, w), dtype=np.uint8)
    cv2.fillPoly(mask, [np.array(poly, dtype=np.int32)], 255)

    xs = [p[0] for p in poly]
    ys = [p[1] for p in poly]
    x0, x1 = max(0, min(xs) - 2), min(w, max(xs) + 2)
    y0, y1 = max(0, min(ys) - 2), min(h, max(ys) + 2)
    if x1 <= x0 or y1 <= y0:
        return None

    roi_gray = gray[y0:y1, x0:x1]
    roi_mask = mask[y0:y1, x0:x1]
    if roi_gray.size == 0 or cv2.countNonZero(roi_mask) < 25:
        return None

    blur = cv2.GaussianBlur(roi_gray, (3, 3), 0.8)
    _, binary = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    binary = cv2.bitwise_and(binary, binary, mask=roi_mask)
    # Bright-on-dark is the expected lume-on-dial polarity; if Otsu picked
    # the majority (background) as foreground instead, invert.
    if cv2.countNonZero(binary) > cv2.countNonZero(roi_mask) * 0.5:
        binary = cv2.bitwise_and(cv2.bitwise_not(binary), cv2.bitwise_not(binary), mask=roi_mask)

    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
    binary = cv2.morphologyEx(binary, cv2.MORPH_OPEN, kernel)
    binary = cv2.morphologyEx(binary, cv2.MORPH_CLOSE, kernel)

    contours, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    if not contours:
        return None
    contour = max(contours, key=cv2.contourArea)
    area_px = cv2.contourArea(contour)
    area_norm = area_px / max(1.0, dial_radius_px ** 2)
    if not (MIN_AREA_NORM <= area_norm <= MAX_AREA_NORM):
        return None
    if len(contour) < 5:
        return None

    pts = contour.reshape(-1, 2).astype(np.float64)
    pts[:, 0] += x0
    pts[:, 1] += y0

    center = np.array([ellipse.cx, ellipse.cy])
    dists = np.linalg.norm(pts - center, axis=1)
    order = np.argsort(dists)
    inward_idx = order[:N_EXTREME_SUBSET]
    apex_xy = tuple(pts[inward_idx].mean(axis=0))

    # The outward (base) subset must be wide enough to contain the WHOLE
    # base edge, not just its radial-distance extreme: a flat outward
    # edge has many contour points at nearly the same radial distance, so
    # a small fixed-N subset can land near the middle of that edge rather
    # than spanning it, corrupting the left/right corner search below.
    # Use a fraction of the contour instead of a fixed small N.
    n_outward = max(N_EXTREME_SUBSET, int(round(len(pts) * OUTWARD_SUBSET_FRACTION)))
    outward_idx = order[-n_outward:]

    # Among the outward subset, split by tangential position (perpendicular
    # to the nominal axis) to get left/right base corners.
    axis_angle = math.radians(hour * 30.0 - 90.0)
    # image-space direction consistent with map_point's own convention
    # (axis established from the ellipse basis, not from the contour).
    ax_u, ax_v = geometry.map_point(ellipse, 1.0, roll, math.cos(axis_angle), math.sin(axis_angle))
    c_u, c_v = geometry.map_point(ellipse, 1.0, roll, 0.0, 0.0)
    dir_x, dir_y = ax_u - c_u, ax_v - c_v
    norm = math.hypot(dir_x, dir_y) or 1.0
    dir_x, dir_y = dir_x / norm, dir_y / norm
    tang_x, tang_y = -dir_y, dir_x

    outward_pts = pts[outward_idx]
    tang_proj = (outward_pts[:, 0] - center[0]) * tang_x + (outward_pts[:, 1] - center[1]) * tang_y
    left_i = np.argmin(tang_proj)
    right_i = np.argmax(tang_proj)
    base_left_xy = tuple(outward_pts[left_i])
    base_right_xy = tuple(outward_pts[right_i])
    # The base centre is the corner midpoint, not the mean of the whole
    # (deliberately broad) outward subset -- that subset spans the base
    # edge for corner-finding but also includes points along the
    # converging side edges near the base, which would bias a plain mean.
    base_centre_xy = ((base_left_xy[0] + base_right_xy[0]) / 2.0, (base_left_xy[1] + base_right_xy[1]) / 2.0)

    M = cv2.moments(contour)
    if M["m00"] > 1e-6:
        centroid_xy = (float(M["m10"] / M["m00"]) + x0, float(M["m01"] / M["m00"]) + y0)
    else:
        centroid_xy = tuple(pts.mean(axis=0))

    sym_dx = base_centre_xy[0] - apex_xy[0]
    sym_dy = base_centre_xy[1] - apex_xy[1]
    symmetry_axis_deg = math.degrees(math.atan2(sym_dy, sym_dx))

    mean_pt = pts.mean(axis=0)
    centered = pts - mean_pt
    cov = np.cov(centered.T)
    eigvals, eigvecs = np.linalg.eigh(cov)
    principal = eigvecs[:, np.argmax(eigvals)]
    pca_axis_deg = math.degrees(math.atan2(principal[1], principal[0])) % 180.0
    sym_mod180 = symmetry_axis_deg % 180.0
    axis_agreement_deg = min(abs(pca_axis_deg - sym_mod180), 180.0 - abs(pca_axis_deg - sym_mod180))

    height_px = math.hypot(sym_dx, sym_dy)
    base_width_px = math.hypot(base_right_xy[0] - base_left_xy[0], base_right_xy[1] - base_left_xy[1])

    hull = cv2.convexHull(contour)
    hull_area = cv2.contourArea(hull)
    solidity = float(area_px / hull_area) if hull_area > 1e-6 else 0.0

    # Confidence: shape plausibility (solidity near a real triangle's
    # ~0.9-1.0, not a fragmented/noisy blob) and axis-estimate agreement
    # (apex-base line vs. independent PCA principal axis) -- both cross-
    # checks internal to this single segmentation, not comparisons to any
    # expected canonical position.
    solidity_score = max(0.0, min(1.0, (solidity - 0.6) / 0.35))
    agreement_score = max(0.0, min(1.0, 1.0 - axis_agreement_deg / 20.0))
    confidence = float(0.5 * solidity_score + 0.5 * agreement_score)

    return TriangleObservation(
        hour=hour, apex_xy=apex_xy, base_centre_xy=base_centre_xy,
        base_left_xy=base_left_xy, base_right_xy=base_right_xy, centroid_xy=centroid_xy,
        symmetry_axis_deg=symmetry_axis_deg, pca_axis_deg=pca_axis_deg,
        axis_agreement_deg=axis_agreement_deg, height_px=height_px, base_width_px=base_width_px,
        area_px=float(area_px), solidity=solidity, n_contour_points=len(pts),
        confidence=confidence, roi_polygon_xy=poly,
    )
