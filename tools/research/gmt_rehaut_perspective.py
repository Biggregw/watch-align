from __future__ import annotations

"""Experimental rehaut-based perspective signal for GMT photographs.

This module is intentionally research-only. It estimates how the *visible*
rehaut width changes around the dial. It does not yet apply a perspective
correction and it does not define GOOD/CORRECTABLE/REJECT thresholds.

The key observable is the first-harmonic asymmetry of the visible rehaut
surface. A tilted camera should make the far side of the sloped/deep rehaut
more visible and the near side less visible. Measuring the complete 360-degree
width profile gives a 2-D direction signal rather than only a top/bottom test.

The existing Hough circle is used only as a centre/scale seed. Its radius is
not assumed to correspond to a specific physical ring.
"""

from dataclasses import dataclass
import math
from typing import Optional

import cv2
import numpy as np

from gmt12_auto_landmarks import _dial_circle


@dataclass(frozen=True)
class RehautPerspective:
    top_width_px: float
    bottom_width_px: float
    left_width_px: float
    right_width_px: float
    mean_width_px: float

    # Positive vertical means more rehaut is visible at 12 than at 6.
    vertical_asymmetry: float
    # Positive horizontal means more rehaut is visible at 3 than at 9.
    horizontal_asymmetry: float

    first_harmonic_strength: float
    widest_direction_deg: float

    # Diagnostics for deciding later whether an image is usable.
    min_width_over_mean: float
    edge_coverage: float
    normalized_fit_residual: float

    inner_radius_seed_px: float
    outer_radius_seed_px: float


@dataclass(frozen=True)
class RehautDetection:
    perspective: Optional[RehautPerspective]
    reason: str = ""


def _polar_edge_map(gray: np.ndarray, cx: float, cy: float, seed_r: float):
    h, w = gray.shape[:2]
    frame_r = min(cx, cy, w - cx, h - cy)
    max_r = int(min(frame_r * 0.98, seed_r * 1.30))
    if max_r < max(40, int(seed_r * 0.70)):
        return None

    polar = cv2.warpPolar(
        gray,
        (max_r, 720),
        (float(cx), float(cy)),
        float(max_r),
        cv2.WARP_POLAR_LINEAR + cv2.INTER_LINEAR,
    )
    smooth = cv2.GaussianBlur(polar, (5, 3), 0)
    radial_grad = np.abs(cv2.Sobel(smooth, cv2.CV_32F, 1, 0, ksize=3))
    persistent_grad = np.median(radial_grad, axis=0)
    radial_mean = np.mean(polar, axis=0)
    return polar, radial_grad, persistent_grad, radial_mean


def _local_peaks(profile: np.ndarray, lo: int, hi: int):
    out = []
    lo = max(1, lo)
    hi = min(len(profile) - 1, hi)
    for i in range(lo, hi):
        if profile[i] >= profile[i - 1] and profile[i] > profile[i + 1]:
            out.append((i, float(profile[i])))
    return out


def _pick_rehaut_edges(
    persistent_grad: np.ndarray,
    radial_mean: np.ndarray,
    seed_r: float,
):
    """Pick a provisional inner/outer rehaut pair from persistent radial edges.

    The inner edge is the first strong persistent transition encountered after
    the dark dial interior. The outer edge is the next sufficiently persistent
    ring shortly outside it. This is deliberately conservative and is exposed
    as a research primitive rather than hidden behind a pass/fail verdict.
    """
    lo = int(0.55 * seed_r)
    hi = min(len(persistent_grad) - 2, int(1.15 * seed_r))
    peaks = [
        (i, g)
        for i, g in _local_peaks(persistent_grad, lo, hi)
        if g >= 4.0
    ]
    if not peaks:
        return None

    max_g = max(g for _, g in peaks)
    inner_candidates = []
    for i, g in peaks:
        a0 = max(0, int(i - 0.10 * seed_r))
        a1 = max(a0 + 1, int(i - 0.03 * seed_r))
        inside = float(np.median(radial_mean[a0:a1]))

        b1 = min(len(radial_mean), int(i + 0.10 * seed_r))
        outside = float(np.median(radial_mean[i:b1])) if b1 > i else inside
        gain = outside - inside

        if g >= 0.28 * max_g and inside < 90.0 and gain > 15.0:
            inner_candidates.append((i, g))

    if not inner_candidates:
        return None

    not_too_inner = [z for z in inner_candidates if z[0] >= 0.62 * seed_r]
    if not_too_inner:
        inner_candidates = not_too_inner

    inner_r, inner_g = min(inner_candidates, key=lambda z: z[0])

    outer_candidates = []
    for j, g in peaks:
        sep = (j - inner_r) / max(seed_r, 1.0)
        if 0.04 <= sep <= 0.095 and g >= 0.22 * max_g:
            outer_candidates.append((j, g))

    if not outer_candidates:
        return None

    outer_r, outer_g = min(outer_candidates, key=lambda z: z[0])
    return float(inner_r), float(outer_r), float(inner_g), float(outer_g)


def _design(theta: np.ndarray, harmonics: int):
    cols = [np.ones_like(theta)]
    for h in range(1, harmonics + 1):
        cols.extend((np.cos(h * theta), np.sin(h * theta)))
    return np.column_stack(cols)


def _robust_harmonic_fit(
    theta: np.ndarray,
    values: np.ndarray,
    weights: np.ndarray,
    harmonics: int = 2,
):
    X = _design(theta, harmonics)
    keep = np.isfinite(values) & np.isfinite(weights) & (weights > 0)
    w = weights.astype(float).copy()
    beta = None

    for _ in range(8):
        if keep.sum() < X.shape[1] + 3:
            return None

        sw = np.sqrt(w[keep])
        beta = np.linalg.lstsq(
            X[keep] * sw[:, None],
            values[keep] * sw,
            rcond=None,
        )[0]

        residual = values - X @ beta
        med = float(np.median(residual[keep]))
        mad = float(np.median(np.abs(residual[keep] - med)))
        scale = max(1.0, 1.4826 * mad)

        new_keep = keep & (np.abs(residual - med) <= 3.0 * scale)
        rr = np.abs(residual - med) / scale
        new_w = weights * np.where(
            rr <= 1.5,
            1.0,
            1.5 / np.maximum(rr, 1e-6),
        )

        if np.array_equal(new_keep, keep):
            w = new_w
            break

        keep = new_keep
        w = new_w

    if beta is None:
        return None
    residual = values - X @ beta
    return beta, keep, residual


def _track_boundary(
    radial_grad: np.ndarray,
    radius_seed: float,
    seed_r: float,
    max_window_px: float,
):
    # Smooth over neighbouring angles so isolated engraving/cyclops edges do
    # not dominate one angular sample.
    edge = cv2.GaussianBlur(radial_grad, (3, 9), 0)
    n_angle, n_radius = edge.shape

    win = max(3, int(round(min(0.020 * seed_r, max_window_px))))
    positions = np.empty(n_angle, dtype=float)
    strengths = np.empty(n_angle, dtype=float)

    for a in range(n_angle):
        lo = max(1, int(round(radius_seed - win)))
        hi = min(n_radius - 1, int(round(radius_seed + win + 1)))
        if hi <= lo:
            positions[a] = np.nan
            strengths[a] = 0.0
            continue

        p = edge[a, lo:hi]
        idx = int(np.argmax(p)) + lo
        positions[a] = float(idx)
        strengths[a] = float(edge[a, idx])

    theta = np.linspace(0.0, 2.0 * math.pi, n_angle, endpoint=False)
    strength_cut = max(float(np.percentile(strengths, 35)), 3.0)
    weights = np.where(
        strengths >= strength_cut,
        np.sqrt(np.maximum(strengths, 0.0)),
        0.0,
    )
    fitted = _robust_harmonic_fit(theta, positions, weights, harmonics=2)
    if fitted is None:
        return None

    beta, keep, residual = fitted
    curve = _design(theta, 2) @ beta
    return theta, curve, keep, residual


def _first_harmonic_width(theta: np.ndarray, width: np.ndarray):
    X = _design(theta, 1)
    beta = np.linalg.lstsq(X, width, rcond=None)[0]
    mean_w, cos_w, sin_w = map(float, beta)

    def at(angle):
        return mean_w + cos_w * math.cos(angle) + sin_w * math.sin(angle)

    # cv2.warpPolar uses image coordinates: 0 deg=right, 90 deg=down,
    # 180 deg=left, 270 deg=up.
    right = at(0.0)
    bottom = at(math.pi / 2.0)
    left = at(math.pi)
    top = at(3.0 * math.pi / 2.0)

    amp = math.hypot(cos_w, sin_w)
    widest = math.degrees(math.atan2(sin_w, cos_w)) % 360.0
    return mean_w, amp, widest, top, bottom, left, right


def _safe_asym(a: float, b: float):
    den = a + b
    if abs(den) <= 1e-9:
        return float("nan")
    return (a - b) / den


def analyze_rehaut_perspective(bgr: np.ndarray) -> RehautDetection:
    if bgr is None or bgr.size == 0:
        return RehautDetection(None, "empty image")

    gray = (
        cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
        if bgr.ndim == 3
        else bgr.copy()
    )

    seed = _dial_circle(gray)
    if seed is None:
        return RehautDetection(None, "dial centre/scale seed not found")
    cx, cy, seed_r = map(float, seed)

    polar_data = _polar_edge_map(gray, cx, cy, seed_r)
    if polar_data is None:
        return RehautDetection(None, "insufficient image area around dial")
    polar, radial_grad, persistent_grad, radial_mean = polar_data

    pair = _pick_rehaut_edges(persistent_grad, radial_mean, seed_r)
    if pair is None:
        return RehautDetection(None, "rehaut inner/outer edge pair not sufficiently constrained")
    inner_seed, outer_seed, _, _ = pair

    separation = outer_seed - inner_seed
    if separation <= 2.0:
        return RehautDetection(None, "rehaut edge pair is degenerate")

    # Keep the two local searches disjoint. The pair separation itself is more
    # trustworthy than assuming the Hough seed radius names a particular ring.
    window = max(3.0, 0.35 * separation)

    inner = _track_boundary(radial_grad, inner_seed, seed_r, window)
    outer = _track_boundary(radial_grad, outer_seed, seed_r, window)
    if inner is None or outer is None:
        return RehautDetection(None, "rehaut boundary tracking failed")

    theta, inner_curve, inner_keep, inner_residual = inner
    theta2, outer_curve, outer_keep, outer_residual = outer
    if not np.allclose(theta, theta2):
        return RehautDetection(None, "internal polar sampling mismatch")

    width = outer_curve - inner_curve
    if not np.all(np.isfinite(width)) or float(np.median(width)) <= 1.0:
        return RehautDetection(None, "rehaut width profile is degenerate")

    mean_w, amp, widest, top, bottom, left, right = _first_harmonic_width(
        theta, width
    )
    if mean_w <= 1.0:
        return RehautDetection(None, "mean visible rehaut width is too small")

    both = inner_keep & outer_keep
    coverage = float(np.mean(both))

    inner_mad = float(np.median(np.abs(inner_residual[inner_keep])))
    outer_mad = float(np.median(np.abs(outer_residual[outer_keep])))
    fit_residual = (inner_mad + outer_mad) / mean_w

    p = RehautPerspective(
        top_width_px=float(top),
        bottom_width_px=float(bottom),
        left_width_px=float(left),
        right_width_px=float(right),
        mean_width_px=float(mean_w),
        vertical_asymmetry=float(_safe_asym(top, bottom)),
        horizontal_asymmetry=float(_safe_asym(right, left)),
        first_harmonic_strength=float(amp / mean_w),
        widest_direction_deg=float(widest),
        min_width_over_mean=float(np.min(width) / mean_w),
        edge_coverage=coverage,
        normalized_fit_residual=float(fit_residual),
        inner_radius_seed_px=float(inner_seed),
        outer_radius_seed_px=float(outer_seed),
    )
    return RehautDetection(p, "")
