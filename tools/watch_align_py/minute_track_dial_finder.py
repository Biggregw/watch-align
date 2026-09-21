"""Port of MinuteTrackDialFinder.java. Finds GMT dial geometry from the minute
track first, then validates the next strong concentric boundary outward as the
dial edge. The minute track owns centre, scale and phase.
"""
import math
from dataclasses import dataclass
from typing import List, Optional

import cv2
import numpy as np

import master
from geometry import RotatedRect, map_point, percentile_sorted, bilinear_sample

TRACK_R = master.MINUTE_TRACK_R
TICK_RADIAL_HALF = 0.025
TICK_ANGULAR_HALF = math.radians(0.34)
LOSS_CAP_PX = 10.0
MAX_SHAPE_CANDIDATES = 14
TOP_PHASE_LIMIT_DEG = 3.10


@dataclass
class Result:
    dial_ellipse: Optional[RotatedRect]
    roll_deg: float
    fit_median_px: float
    outer_boundary_radius: float
    outer_boundary_median_px: float
    outer_boundary_p90_px: float
    candidate_count: int
    boundary_confirmed: bool
    top_phase_error_deg: float
    top_phase_accepted: bool
    usable: bool


@dataclass
class TopPhaseResult:
    anchored_roll_deg: float
    error_deg: float
    accepted: bool


@dataclass
class _ShapeCandidate:
    ellipse: RotatedRect
    rank: float


@dataclass
class _PoseCandidate:
    source: RotatedRect
    scale: float
    roll: float
    score: float


@dataclass
class _BoundaryResult:
    radius: float
    median: float
    p90: float


def _normalize_degrees(degrees: float) -> float:
    d = degrees % 360.0
    if d > 180.0:
        d -= 360.0
    if d <= -180.0:
        d += 360.0
    return d


def anchor_to_image_up(ellipse: Optional[RotatedRect], roll_deg: float) -> TopPhaseResult:
    """Resolve the 6-degree periodic tick ambiguity: the true 12 minute-track tick
    is the tick nearest image-up, since QC photos are upright by contract."""
    if ellipse is None:
        return TopPhaseResult(roll_deg, 180.0, False)
    best_roll = roll_deg
    best_error = math.inf
    best_upper = False
    for k in range(-10, 11):
        candidate = roll_deg + 6.0 * k
        px, py = map_point(ellipse, 1.0, candidate, 0.0, -1.0)
        dx = px - ellipse.cx
        dy = py - ellipse.cy
        length = math.hypot(dx, dy)
        if length < 1e-9:
            continue
        dot = max(-1.0, min(1.0, (-dy) / length))
        error = math.degrees(math.acos(dot))
        upper = py < ellipse.cy
        if error < best_error - 1e-9 or (
            abs(error - best_error) <= 1e-9
            and abs(_normalize_degrees(candidate)) < abs(_normalize_degrees(best_roll))
        ):
            best_error = error
            best_roll = candidate
            best_upper = upper
    best_roll = _normalize_degrees(best_roll)
    accepted = best_upper and math.isfinite(best_error) and best_error <= TOP_PHASE_LIMIT_DEG
    return TopPhaseResult(best_roll, best_error, accepted)


def _angular_coverage(e: RotatedRect, points: np.ndarray, bins: int) -> float:
    hit = [False] * bins
    count = 0
    axis = math.radians(e.angle_deg)
    ca, sa = math.cos(axis), math.sin(axis)
    rx = max(1e-6, e.w / 2.0)
    ry = max(1e-6, e.h / 2.0)
    for p in points.reshape(-1, 2):
        dx = float(p[0]) - e.cx
        dy = float(p[1]) - e.cy
        lx = ca * dx + sa * dy
        ly = -sa * dx + ca * dy
        nx = lx / rx
        ny = ly / ry
        rho = math.hypot(nx, ny)
        if rho < 0.82 or rho > 1.18:
            continue
        angle = math.atan2(ny, nx)
        if angle < 0:
            angle += 2.0 * math.pi
        b = min(bins - 1, int(math.floor(angle / (2.0 * math.pi) * bins)))
        if not hit[b]:
            hit[b] = True
            count += 1
    return count / bins


def _find_concentric_shapes(edges: np.ndarray, seed_x: float, seed_y: float,
                             seed_r: float) -> List[_ShapeCandidate]:
    contours, _ = cv2.findContours(edges.copy(), cv2.RETR_LIST, cv2.CHAIN_APPROX_NONE)
    out: List[_ShapeCandidate] = []
    min_dim = max(1.0, min(edges.shape[1], edges.shape[0]))
    min_diameter = max(70.0, min_dim * 0.10)
    max_diameter = min_dim * 0.94
    centre_tolerance = max(seed_r * 0.48, min_dim * 0.11)
    # The legacy Hough-circle seed is only an approximate starting point and can itself be
    # significantly mis-centred on a real photo (confirmed: >100px off on a genuine, only
    # mildly tilted catalogue-style photo). A hard admission cutoff at centre_tolerance
    # silently discards the true, well-formed minute-track candidate whenever the seed is
    # that far off, leaving only worse concentric contours to be selected -- producing a
    # badly wrong pose (wrong apparent tilt, spurious rejection) despite a good photo. Keep
    # centre_tolerance as the ranking penalty's scale (still prefers candidates close to the
    # seed) but admit candidates out to a wider radius so a mediocre seed cannot exclude the
    # correct answer outright.
    centre_tolerance_admission = centre_tolerance * 2.2
    for contour in contours:
        if contour.shape[0] < 32:
            continue
        try:
            ellipse_cv = cv2.fitEllipse(contour.astype(np.float32))
        except cv2.error:
            continue
        e = RotatedRect.from_cv(ellipse_cv)
        a = max(e.w, e.h)
        b = min(e.w, e.h)
        if a < min_diameter or a > max_diameter or b < min_diameter * 0.70:
            continue
        if a < 0.75 * seed_r or a > 3.20 * seed_r:
            continue
        axis_ratio = b / max(1.0, a)
        if axis_ratio < 0.66:
            continue
        centre_distance = math.hypot(e.cx - seed_x, e.cy - seed_y)
        if centre_distance > centre_tolerance_admission:
            continue
        coverage = _angular_coverage(e, contour, 24)
        if coverage < 0.28:
            continue
        centre_penalty = centre_distance / max(1.0, centre_tolerance)
        shape_penalty = max(0.0, 0.76 - axis_ratio)
        rank = 1.8 * centre_penalty + 1.4 * (1.0 - coverage) + 1.5 * shape_penalty
        out.append(_ShapeCandidate(e, rank))
    out.sort(key=lambda c: c.rank)
    return _deduplicate(out)


def _deduplicate(source: List[_ShapeCandidate]) -> List[_ShapeCandidate]:
    out: List[_ShapeCandidate] = []
    for candidate in source:
        duplicate = False
        for kept in out:
            r = (max(kept.ellipse.w, kept.ellipse.h) + min(kept.ellipse.w, kept.ellipse.h)) / 4.0
            dc = math.hypot(candidate.ellipse.cx - kept.ellipse.cx, candidate.ellipse.cy - kept.ellipse.cy)
            da = abs(max(candidate.ellipse.w, candidate.ellipse.h) - max(kept.ellipse.w, kept.ellipse.h))
            db = abs(min(candidate.ellipse.w, candidate.ellipse.h) - min(kept.ellipse.w, kept.ellipse.h))
            if dc < max(2.0, r * 0.025) and da < max(3.0, r * 0.04) and db < max(3.0, r * 0.04):
                duplicate = True
                break
        if not duplicate:
            out.append(candidate)
        if len(out) >= MAX_SHAPE_CANDIDATES * 2:
            break
    return out


def _sample(distance: np.ndarray, x: float, y: float) -> float:
    return bilinear_sample(distance, x, y, LOSS_CAP_PX)


def _tick_score(distance: np.ndarray, ellipse: RotatedRect, roll: float, angle: float) -> float:
    total = 0.0
    count = 0
    for side in (-TICK_ANGULAR_HALF, TICK_ANGULAR_HALF):
        for i in range(4):
            r = TRACK_R - TICK_RADIAL_HALF + 2.0 * TICK_RADIAL_HALF * i / 3.0
            px, py = map_point(ellipse, 1.0, roll, r * math.cos(angle + side), r * math.sin(angle + side))
            total += _sample(distance, px, py)
            count += 1
    for r in (TRACK_R - TICK_RADIAL_HALF, TRACK_R + TICK_RADIAL_HALF):
        for i in (-1, 0, 1):
            a = angle + i * TICK_ANGULAR_HALF
            px, py = map_point(ellipse, 1.0, roll, r * math.cos(a), r * math.sin(a))
            total += _sample(distance, px, py)
            count += 1
    return total / max(1, count)


def _fit_median(distance: np.ndarray, ellipse: RotatedRect, roll: float, holdout: bool) -> float:
    scores = []
    for minute in range(60):
        if minute % 5 == 0:
            continue
        this_holdout = (minute % 4 == 2)
        if this_holdout != holdout:
            continue
        angle = math.radians(minute * 6.0 - 90.0)
        scores.append(_tick_score(distance, ellipse, roll, angle))
    scores.sort()
    return percentile_sorted(scores, 0.50, LOSS_CAP_PX)


def _fit_median_fast(distance: np.ndarray, ellipse: RotatedRect, scale: float, roll: float) -> float:
    scores = []
    for minute in range(60):
        if minute % 5 == 0 or minute % 4 == 2:
            continue
        angle = math.radians(minute * 6.0 - 90.0)
        total = 0.0
        n = 0
        for r in (TRACK_R - TICK_RADIAL_HALF, TRACK_R + TICK_RADIAL_HALF):
            px, py = map_point(ellipse, scale, roll, r * math.cos(angle), r * math.sin(angle))
            total += _sample(distance, px, py)
            n += 1
        for a in (angle - TICK_ANGULAR_HALF, angle + TICK_ANGULAR_HALF):
            px, py = map_point(ellipse, scale, roll, TRACK_R * math.cos(a), TRACK_R * math.sin(a))
            total += _sample(distance, px, py)
            n += 1
        scores.append(total / max(1, n))
    scores.sort()
    return percentile_sorted(scores, 0.50, LOSS_CAP_PX)


def _scaled_ellipse(e: RotatedRect, scale: float) -> RotatedRect:
    return e.scaled(scale)


def _coarse_search(distance: np.ndarray, ellipse: RotatedRect) -> Optional[_PoseCandidate]:
    best = None
    scale = 0.68
    while scale <= 1.32 + 1e-9:
        roll = -15.0
        while roll <= 15.0 + 1e-9:
            score = _fit_median_fast(distance, ellipse, scale, roll)
            if best is None or score < best.score:
                best = _PoseCandidate(ellipse, scale, roll, score)
            roll += 1.0
        scale += 0.04
    return best


def _fine_search(distance: np.ndarray, coarse: _PoseCandidate) -> _PoseCandidate:
    coarse_ellipse = _scaled_ellipse(coarse.source, coarse.scale)
    best = _PoseCandidate(coarse.source, coarse.scale, coarse.roll,
                           _fit_median(distance, coarse_ellipse, coarse.roll, False))
    min_scale = max(0.62, coarse.scale - 0.06)
    max_scale = min(1.38, coarse.scale + 0.06)
    scale = min_scale
    while scale <= max_scale + 1e-9:
        roll = coarse.roll - 1.5
        while roll <= coarse.roll + 1.5 + 1e-9:
            if abs(roll) <= 16.0:
                e = _scaled_ellipse(coarse.source, scale)
                score = _fit_median(distance, e, roll, False)
                if score < best.score:
                    best = _PoseCandidate(coarse.source, scale, roll, score)
            roll += 0.10
        scale += 0.005
    return best


def _refine_centre(distance: np.ndarray, ellipse: RotatedRect, roll: float) -> RotatedRect:
    best = ellipse
    best_score = _fit_median(distance, ellipse, roll, False)
    radius = (max(ellipse.w, ellipse.h) + min(ellipse.w, ellipse.h)) / 4.0
    step = max(0.75, radius * 0.008)
    for ix in range(-2, 3):
        for iy in range(-2, 3):
            if ix == 0 and iy == 0:
                continue
            candidate = RotatedRect(ellipse.cx + ix * step, ellipse.cy + iy * step, ellipse.w, ellipse.h, ellipse.angle_deg)
            score = _fit_median(distance, candidate, roll, False)
            if score < best_score:
                best_score = score
                best = candidate
    return best


def _find_next_outer_boundary(distance: np.ndarray, track_based: RotatedRect, roll: float) -> Optional[_BoundaryResult]:
    best = None
    best_objective = math.inf
    radius = 0.960
    while radius <= 1.040 + 1e-9:
        groups = []
        for block in range(24):
            total = 0.0
            for j in range(4):
                angle = 2.0 * math.pi * (block * 4 + j) / 96.0
                px, py = map_point(track_based, 1.0, roll, radius * math.cos(angle), radius * math.sin(angle))
                total += _sample(distance, px, py)
            groups.append(total / 4.0)
        groups.sort()
        median = percentile_sorted(groups, 0.50, LOSS_CAP_PX)
        p90 = percentile_sorted(groups, 0.90, LOSS_CAP_PX)
        dial_radius = (max(track_based.w, track_based.h) + min(track_based.w, track_based.h)) / 4.0
        expected_penalty = abs(radius - 1.0) * max(20.0, dial_radius) * 0.40
        objective = median + 0.30 * p90 + expected_penalty
        if objective < best_objective:
            best_objective = objective
            best = _BoundaryResult(radius, median, p90)
        radius += 0.0025
    return best


def _distance_field(edges: np.ndarray) -> np.ndarray:
    _, inverted = cv2.threshold(edges, 0.0, 255.0, cv2.THRESH_BINARY_INV)
    return cv2.distanceTransform(inverted, cv2.DIST_L2, cv2.DIST_MASK_PRECISE)


def final_ellipse_from_track(track_based: Optional[RotatedRect]) -> Optional[RotatedRect]:
    """The outward boundary is validation only and must not perturb minute-track scale."""
    if track_based is None:
        return None
    return RotatedRect(track_based.cx, track_based.cy, track_based.w, track_based.h, track_based.angle_deg)


def find(edges: np.ndarray, seed_x: float, seed_y: float, seed_r: float) -> Optional[Result]:
    if edges is None or edges.size == 0 or not (seed_r > 20.0):
        return None
    shapes = _find_concentric_shapes(edges, seed_x, seed_y, seed_r)
    if not shapes:
        return None

    distance = _distance_field(edges)
    best: Optional[_PoseCandidate] = None
    count = min(MAX_SHAPE_CANDIDATES, len(shapes))
    for i in range(count):
        candidate = _coarse_search(distance, shapes[i].ellipse)
        if candidate is not None and (best is None or candidate.score < best.score):
            best = candidate
    if best is None:
        return None

    best = _fine_search(distance, best)
    track_based = _scaled_ellipse(best.source, best.scale)

    top_phase = anchor_to_image_up(track_based, best.roll)
    track_based = _refine_centre(distance, track_based, top_phase.anchored_roll_deg)
    top_phase = anchor_to_image_up(track_based, top_phase.anchored_roll_deg)
    track_median = _fit_median(distance, track_based, top_phase.anchored_roll_deg, False)

    boundary = _find_next_outer_boundary(distance, track_based, top_phase.anchored_roll_deg)
    if boundary is None:
        return Result(track_based, top_phase.anchored_roll_deg, track_median, math.nan,
                      LOSS_CAP_PX, LOSS_CAP_PX, count, False, top_phase.error_deg, top_phase.accepted, False)

    dial_radius = (max(track_based.w, track_based.h) + min(track_based.w, track_based.h)) / 4.0
    boundary_median_limit = max(2.8, dial_radius * 0.020)
    boundary_p90_limit = max(5.0, dial_radius * 0.040)
    boundary_confirmed = (abs(boundary.radius - 1.0) <= 0.035
                          and boundary.median <= boundary_median_limit
                          and boundary.p90 <= boundary_p90_limit)

    final_ellipse = final_ellipse_from_track(track_based)
    final_median = _fit_median(distance, final_ellipse, top_phase.anchored_roll_deg, False)
    final_radius = (max(final_ellipse.w, final_ellipse.h) + min(final_ellipse.w, final_ellipse.h)) / 4.0
    tick_limit = max(2.2, final_radius * 0.020)
    usable = top_phase.accepted and boundary_confirmed and final_median <= tick_limit

    return Result(final_ellipse, top_phase.anchored_roll_deg, final_median, boundary.radius,
                  boundary.median, boundary.p90, count, boundary_confirmed,
                  top_phase.error_deg, top_phase.accepted, usable)
