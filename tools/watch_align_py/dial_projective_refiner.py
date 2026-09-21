"""Port of DialProjectiveRefiner.java. Bounded refinement of an ellipse-derived
dial pose using geometry independent of the applied hour markers.

This is the piece under live investigation: on a real, significantly tilted
genuine photo, the search found a correction that improved both fit and
holdout evidence in the right direction, but the holdout improvement (0.0745px)
missed the >=0.08px absolute-improvement acceptance bar by 0.0055px, so the
pose fell back to assuming no perspective distortion at all. Porting this
exactly, unmodified, is deliberate -- the point of this port is to reproduce
that behavior with fast local iteration, not to silently change it.
"""
import math
from dataclasses import dataclass
from typing import List

import cv2
import numpy as np

import master
from geometry import bilinear_sample

OUTER_R = 1.0
TRACK_R = master.MINUTE_TRACK_R
TICK_RADIAL_HALF = 0.025
TICK_ANGULAR_HALF = math.radians(0.34)
LOSS_CAP_PX = 8.0

# Transform relative to H0: four linear nuisance terms, translation, h31 and h32.
LIMIT = [0.08, 0.08, 0.08, 0.08, 0.08, 0.08, 0.32, 0.32]
INITIAL_STEP = [0.025, 0.025, 0.025, 0.025, 0.02, 0.02, 0.055, 0.055]


@dataclass
class Result:
    homography: np.ndarray
    evaluated_homography: np.ndarray
    accepted: bool
    fit_before: float
    fit_after: float
    evaluated_fit_after: float
    holdout_before: float
    holdout_after: float
    evaluated_holdout_after: float


@dataclass
class MatResult:
    homography: np.ndarray
    diagnostics: Result


def _distance_field(edges: np.ndarray) -> np.ndarray:
    _, inverted = cv2.threshold(edges, 0.0, 255.0, cv2.THRESH_BINARY_INV)
    return cv2.distanceTransform(inverted, cv2.DIST_L2, cv2.DIST_MASK_PRECISE)


def _sample(field: np.ndarray, h: np.ndarray, x: float, y: float) -> float:
    w = h[2, 0] * x + h[2, 1] * y + h[2, 2]
    if abs(w) < 1e-8:
        return LOSS_CAP_PX
    px = (h[0, 0] * x + h[0, 1] * y + h[0, 2]) / w
    py = (h[1, 0] * x + h[1, 1] * y + h[1, 2]) / w
    return bilinear_sample(field, px, py, LOSS_CAP_PX)


def _tick_score(field: np.ndarray, h: np.ndarray, angle: float) -> float:
    total = 0.0
    count = 0
    for side in (-TICK_ANGULAR_HALF, TICK_ANGULAR_HALF):
        for i in range(4):
            r = TRACK_R - TICK_RADIAL_HALF + 2.0 * TICK_RADIAL_HALF * i / 3.0
            total += _sample(field, h, r * math.cos(angle + side), r * math.sin(angle + side))
            count += 1
    for r in (TRACK_R - TICK_RADIAL_HALF, TRACK_R + TICK_RADIAL_HALF):
        for i in (-1, 0, 1):
            a = angle + i * TICK_ANGULAR_HALF
            total += _sample(field, h, r * math.cos(a), r * math.sin(a))
            count += 1
    return total / max(1, count)


def _capped_mean(values: List[float]) -> float:
    if not values:
        return LOSS_CAP_PX
    return sum(min(LOSS_CAP_PX, v) for v in values) / len(values)


def _evidence_score(field: np.ndarray, h: np.ndarray, holdout: bool, include_outer: bool) -> float:
    groups: List[float] = []
    if include_outer:
        for block in range(24):
            total = 0.0
            for j in range(4):
                angle = 2.0 * math.pi * (block * 4 + j) / 96.0
                total += _sample(field, h, OUTER_R * math.cos(angle), OUTER_R * math.sin(angle))
            groups.append(total / 4.0)
    for minute in range(60):
        if minute % 5 == 0:
            continue
        this_holdout = (minute % 4 == 2)
        if this_holdout != holdout:
            continue
        angle = math.radians(minute * 6.0 - 90.0)
        groups.append(_tick_score(field, h, angle))
    return _capped_mean(groups)


def _conic_preserving_seed(p: float, q: float) -> List[float]:
    return [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, p, q]


def _conic_preserving_delta(p: float, q: float) -> np.ndarray:
    magnitude2 = p * p + q * q
    gamma = 1.0 / math.sqrt(max(1e-6, 1.0 - magnitude2))
    inverse_gamma = 1.0 / gamma
    blend = (1.0 - inverse_gamma) / magnitude2 if magnitude2 > 1e-12 else 0.0
    return np.array([
        [inverse_gamma + blend * p * p, blend * p * q, p],
        [blend * p * q, inverse_gamma + blend * q * q, q],
        [p, q, 1.0],
    ], dtype=np.float64)


def _compose(h0: np.ndarray, p: List[float]) -> np.ndarray:
    delta = _conic_preserving_delta(p[6], p[7])
    delta[0, 0] += p[0]
    delta[0, 1] += p[1]
    delta[1, 0] += p[2]
    delta[1, 1] += p[3]
    delta[0, 2] += p[4]
    delta[1, 2] += p[5]
    out = h0 @ delta
    scale = out[2, 2]
    if abs(scale) < 1e-9:
        return out
    return out / scale


def _finite(h: np.ndarray) -> bool:
    return bool(np.all(np.isfinite(h)))


def _clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def _objective(field: np.ndarray, h0: np.ndarray, p: List[float], limits: List[float]) -> float:
    h = _compose(h0, p)
    if not _finite(h):
        return math.inf
    evidence = _evidence_score(field, h, False, True)
    regularization = 0.0
    for i in range(len(p)):
        normalized = p[i] / limits[i]
        weight = 0.055 if i < 6 else 0.012
        regularization += weight * normalized * normalized
    return evidence + regularization


def refine(field: np.ndarray, h0: np.ndarray, projective_limit: float = LIMIT[6]) -> Result:
    limits = list(LIMIT)
    limits[6] = projective_limit
    limits[7] = projective_limit
    zero = [0.0] * 8
    fit_before = _evidence_score(field, h0, False, True)
    holdout_before = _evidence_score(field, h0, True, False)
    best = list(zero)
    best_objective = _objective(field, h0, best, limits)
    step = list(INITIAL_STEP)

    # Projective displacement can put H0 outside the basin of a pixel-scale edge
    # loss. Seed the local search with a small bounded h31/h32 grid.
    p = -limits[6]
    while p <= limits[6] + 1e-9:
        q = -limits[7]
        while q <= limits[7] + 1e-9:
            candidate = _conic_preserving_seed(p, q)
            value = _objective(field, h0, candidate, limits)
            if value < best_objective:
                best_objective = value
                best = candidate
            q += 0.08
        p += 0.08

    coarse_p, coarse_q = best[6], best[7]
    p = coarse_p - 0.08
    while p <= coarse_p + 0.08 + 1e-9:
        q = coarse_q - 0.08
        while q <= coarse_q + 0.08 + 1e-9:
            if abs(p) <= limits[6] and abs(q) <= limits[7]:
                rotation = -0.06
                while rotation <= 0.06 + 1e-9:
                    candidate = _conic_preserving_seed(p, q)
                    candidate[1] = rotation
                    candidate[2] = -rotation
                    value = _objective(field, h0, candidate, limits)
                    if value < best_objective:
                        best_objective = value
                        best = candidate
                    rotation += 0.02
            q += 0.02
        p += 0.02

    # Deterministic bounded coordinate search. Several shifted sweeps reduce
    # coordinate-order bias without introducing unstable random behaviour.
    for _level in range(8):
        changed = True
        sweeps = 0
        while changed and sweeps < 5:
            changed = False
            for i in range(len(best)):
                original = best[i]
                selected = original
                for direction in (-1, 1):
                    candidate_value = _clamp(original + direction * step[i], -limits[i], limits[i])
                    if candidate_value == original:
                        continue
                    trial = list(best)
                    trial[i] = candidate_value
                    value = _objective(field, h0, trial, limits)
                    if value + 1e-9 < best_objective:
                        best_objective = value
                        selected = candidate_value
                        best = trial
                        changed = True
                best[i] = selected
            sweeps += 1
        step = [s * 0.5 for s in step]

    candidate_h = _compose(h0, best)
    fit_after = _evidence_score(field, candidate_h, False, True)
    holdout_after = _evidence_score(field, candidate_h, True, False)
    has_evidence = fit_before < LOSS_CAP_PX * 0.92 and holdout_before < LOSS_CAP_PX * 0.92
    improves_fit = fit_after < fit_before - 0.08 and fit_after <= fit_before * 0.985
    improves_holdout = holdout_after < holdout_before - 0.08 and holdout_after <= holdout_before * 0.985
    accepted = has_evidence and improves_fit and improves_holdout and _finite(candidate_h)
    return Result(
        candidate_h if accepted else h0.copy(), candidate_h.copy(), accepted,
        fit_before, fit_after if accepted else fit_before, fit_after,
        holdout_before, holdout_after if accepted else holdout_before, holdout_after,
    )


def refine_with_diagnostics(edges: np.ndarray, h0: np.ndarray, projective_limit: float = LIMIT[6]) -> MatResult:
    field = _distance_field(edges)
    result = refine(field, h0, projective_limit)
    homography = h0.copy() if not result.accepted else result.homography
    return MatResult(homography, result)
