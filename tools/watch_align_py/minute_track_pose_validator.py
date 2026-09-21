"""Port of MinuteTrackPoseValidator.java. Solves dial rotation from minor
minute-track ticks and validates the final pose on a disjoint set of minor
ticks not used by the rotation search. Applied hour markers are excluded
from both phases.
"""
import math
from dataclasses import dataclass
from typing import List

import cv2
import numpy as np

import master
from geometry import percentile_sorted, bilinear_sample

TRACK_R = master.MINUTE_TRACK_R
TICK_RADIAL_HALF = 0.025
TICK_ANGULAR_HALF = math.radians(0.34)
LOSS_CAP_PX = 10.0
FINE_ROTATION_LIMIT_DEG = 2.0


@dataclass
class RotationResult:
    homography: np.ndarray
    delta_deg: float
    fit_median_px: float
    fit_ticks: int


@dataclass
class ValidationResult:
    accepted: bool
    median_px: float
    p90_px: float
    inlier_fraction: float
    holdout_ticks: int
    median_limit_px: float
    p90_limit_px: float
    inlier_limit_px: float


def _distance_field(edges: np.ndarray) -> np.ndarray:
    _, inverted = cv2.threshold(edges, 0.0, 255.0, cv2.THRESH_BINARY_INV)
    return cv2.distanceTransform(inverted, cv2.DIST_L2, cv2.DIST_MASK_PRECISE)


def _sample(distance: np.ndarray, h: np.ndarray, x: float, y: float) -> float:
    w = h[2, 0] * x + h[2, 1] * y + h[2, 2]
    if abs(w) < 1e-8:
        return LOSS_CAP_PX
    px = (h[0, 0] * x + h[0, 1] * y + h[0, 2]) / w
    py = (h[1, 0] * x + h[1, 1] * y + h[1, 2]) / w
    return bilinear_sample(distance, px, py, LOSS_CAP_PX)


def _tick_score(distance: np.ndarray, h: np.ndarray, angle: float) -> float:
    total = 0.0
    count = 0
    for side in (-TICK_ANGULAR_HALF, TICK_ANGULAR_HALF):
        for i in range(4):
            r = TRACK_R - TICK_RADIAL_HALF + 2.0 * TICK_RADIAL_HALF * i / 3.0
            total += _sample(distance, h, r * math.cos(angle + side), r * math.sin(angle + side))
            count += 1
    for r in (TRACK_R - TICK_RADIAL_HALF, TRACK_R + TICK_RADIAL_HALF):
        for i in (-1, 0, 1):
            a = angle + i * TICK_ANGULAR_HALF
            total += _sample(distance, h, r * math.cos(a), r * math.sin(a))
            count += 1
    return total / max(1, count)


def _tick_scores(distance: np.ndarray, h: np.ndarray, holdout: bool, delta_deg: float) -> List[float]:
    out = []
    for minute in range(60):
        if minute % 5 == 0:
            continue
        this_holdout = (minute % 4 == 2)
        if this_holdout != holdout:
            continue
        angle = math.radians(minute * 6.0 - 90.0 + delta_deg)
        out.append(_tick_score(distance, h, angle))
    return out


def _count_ticks(holdout: bool) -> int:
    n = 0
    for minute in range(60):
        if minute % 5 != 0 and (minute % 4 == 2) == holdout:
            n += 1
    return n


def _rotation_score(distance: np.ndarray, h: np.ndarray, delta_deg: float, holdout: bool) -> float:
    scores = _tick_scores(distance, h, holdout, delta_deg)
    if not scores:
        return LOSS_CAP_PX
    scores.sort()
    return percentile_sorted(scores, 0.50, LOSS_CAP_PX)


def _compose_rotation(h: np.ndarray, degrees: float) -> np.ndarray:
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    r = np.eye(3, dtype=np.float64)
    r[0, 0] = c
    r[0, 1] = -s
    r[1, 0] = s
    r[1, 1] = c
    out = h @ r
    scale = out[2, 2]
    if abs(scale) > 1e-9:
        out = out / scale
    return out


def solve_rotation(edges: np.ndarray, base_h: np.ndarray, max_abs_deg: float) -> RotationResult:
    distance = _distance_field(edges)
    best_deg = 0.0
    best = _rotation_score(distance, base_h, 0.0, False)
    d = -max_abs_deg
    while d <= max_abs_deg + 1e-9:
        score = _rotation_score(distance, base_h, d, False)
        if score < best:
            best = score
            best_deg = d
        d += 0.25
    coarse = best_deg
    d = coarse - 0.35
    while d <= coarse + 0.35 + 1e-9:
        if abs(d) <= max_abs_deg:
            score = _rotation_score(distance, base_h, d, False)
            if score < best:
                best = score
                best_deg = d
        d += 0.025
    return RotationResult(_compose_rotation(base_h, best_deg), best_deg, best, _count_ticks(False))


def validate(edges: np.ndarray, h: np.ndarray, dial_radius_px: float) -> ValidationResult:
    distance = _distance_field(edges)
    scores = _tick_scores(distance, h, True, 0.0)
    if not scores:
        return ValidationResult(False, LOSS_CAP_PX, LOSS_CAP_PX, 0.0, 0, 0.0, 0.0, 0.0)
    scores.sort()
    median = percentile_sorted(scores, 0.50, LOSS_CAP_PX)
    p90 = percentile_sorted(scores, 0.90, LOSS_CAP_PX)
    radius = max(60.0, dial_radius_px)
    median_limit = max(1.5, radius * 0.015)
    p90_limit = max(3.0, radius * 0.032)
    inlier_limit = max(2.0, radius * 0.022)
    inliers = sum(1 for v in scores if v <= inlier_limit)
    fraction = inliers / len(scores)
    accepted = len(scores) >= 8 and median <= median_limit and p90 <= p90_limit and fraction >= 0.65
    return ValidationResult(accepted, median, p90, fraction, len(scores), median_limit, p90_limit, inlier_limit)


def fine_tune_rotation(edges: np.ndarray, base_h: np.ndarray) -> RotationResult:
    """Final small correction after projective refinement. The absolute 6-degree
    branch is already fixed by the image-up anchor, so this must never search a
    full tick pitch."""
    return solve_rotation(edges, base_h, FINE_ROTATION_LIMIT_DEG)
