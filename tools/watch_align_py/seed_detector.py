"""Port of WatchAlignCoreV7.detectDial and its helpers (the legacy seed detector).

Only supplies an approximate centre/search scale for MinuteTrackDialFinder --
it cannot set final geometry. Faithful translation of the Java, including the
CLAHE fallback pass when the plain Hough pass doesn't clear quality 0.60.
"""
import math
from dataclasses import dataclass
from typing import Optional

import cv2
import numpy as np


@dataclass
class Circle:
    x: float
    y: float
    r: float
    quality: float
    dark_fraction: float
    boundary_contrast: float


def _sample_dark_fraction(gray: np.ndarray, cx: float, cy: float, radius: float) -> float:
    step = max(2, round(radius / 45.0))
    dark = 0
    total = 0
    x0 = max(0, int(cx - radius))
    x1 = min(gray.shape[1] - 1, int(cx + radius))
    y0 = max(0, int(cy - radius))
    y1 = min(gray.shape[0] - 1, int(cy + radius))
    for y in range(y0, y1 + 1, step):
        for x in range(x0, x1 + 1, step):
            if math.hypot(x - cx, y - cy) > radius:
                continue
            total += 1
            if gray[y, x] < 135:
                dark += 1
    return dark / total if total > 0 else 0.0


def _sample_annulus_dark_fraction(gray: np.ndarray, cx: float, cy: float,
                                   inner: float, outer: float) -> float:
    step = max(2, round(outer / 55.0))
    dark = 0
    total = 0
    x0 = max(0, int(cx - outer))
    x1 = min(gray.shape[1] - 1, int(cx + outer))
    y0 = max(0, int(cy - outer))
    y1 = min(gray.shape[0] - 1, int(cy + outer))
    for y in range(y0, y1 + 1, step):
        for x in range(x0, x1 + 1, step):
            d = math.hypot(x - cx, y - cy)
            if d < inner or d > outer:
                continue
            total += 1
            if gray[y, x] < 135:
                dark += 1
    return dark / total if total > 0 else 0.0


def _sample_ring_edge(gray: np.ndarray, cx: float, cy: float, r: float) -> float:
    total = 0.0
    n = 0
    h, w = gray.shape[:2]
    for deg in range(0, 360, 4):
        a = math.radians(deg)
        best = 0.0
        f = 0.94
        while f <= 1.06 + 1e-9:
            xi = round(cx + math.cos(a) * r * (f - 0.025))
            yi = round(cy + math.sin(a) * r * (f - 0.025))
            xo = round(cx + math.cos(a) * r * (f + 0.025))
            yo = round(cy + math.sin(a) * r * (f + 0.025))
            f += 0.02
            if xi < 0 or xi >= w or yi < 0 or yi >= h or xo < 0 or xo >= w or yo < 0 or yo >= h:
                continue
            best = max(best, abs(float(gray[yo, xo]) - float(gray[yi, xi])) / 255.0)
        total += best
        n += 1
    return total / n if n > 0 else 0.0


def _sample_boundary_contrast(gray: np.ndarray, cx: float, cy: float, r: float) -> float:
    total = 0.0
    n = 0
    h, w = gray.shape[:2]
    for deg in range(0, 360, 5):
        if 70 <= deg <= 110:
            continue
        a = math.radians(deg)
        xi = round(cx + math.cos(a) * r * 0.93)
        yi = round(cy + math.sin(a) * r * 0.93)
        xo = round(cx + math.cos(a) * r * 1.04)
        yo = round(cy + math.sin(a) * r * 1.04)
        if xi < 0 or xi >= w or yi < 0 or yi >= h or xo < 0 or xo >= w or yo < 0 or yo >= h:
            continue
        total += (float(gray[yo, xo]) - float(gray[yi, xi])) / 255.0
        n += 1
    return total / n if n > 0 else 0.0


def _marker_ring_hits(gray: np.ndarray, cx: float, cy: float, r: float) -> int:
    hits = 0
    h, w = gray.shape[:2]
    for hour in range(1, 13):
        a = math.radians(0 if hour == 12 else hour * 30.0)
        best = 0.0
        best_dark = 255.0
        rf = 0.70
        while rf <= 0.88 + 1e-9:
            da = -4.0
            while da <= 4.0 + 1e-9:
                aa = a + math.radians(da)
                x = round(cx + math.sin(aa) * r * rf)
                y = round(cy - math.cos(aa) * r * rf)
                xi = round(cx + math.sin(aa) * r * max(0.55, rf - 0.10))
                yi = round(cy - math.cos(aa) * r * max(0.55, rf - 0.10))
                da += 2.0
                if x < 0 or x >= w or y < 0 or y >= h or xi < 0 or xi >= w or yi < 0 or yi >= h:
                    continue
                best = max(best, float(gray[y, x]))
                best_dark = min(best_dark, float(gray[yi, xi]))
            rf += 0.03
        if best >= 165 and best_dark <= 145:
            hits += 1
    return hits


def _score_dial_candidate(gray: np.ndarray, cx: float, cy: float, r: float,
                           min_dim: int) -> Optional[Circle]:
    rn = r / min_dim
    if rn < 0.105 or rn > 0.28:
        return None
    nx = cx / gray.shape[1]
    ny = cy / gray.shape[0]
    if nx < 0.10 or nx > 0.90 or ny < 0.08 or ny > 0.88:
        return None
    center_dist = math.hypot(nx - 0.5, ny - 0.46)
    if center_dist > 0.44:
        return None
    dark_core = _sample_dark_fraction(gray, cx, cy, r * 0.58)
    dark_wide = _sample_dark_fraction(gray, cx, cy, r * 0.91)
    annulus_dark = _sample_annulus_dark_fraction(gray, cx, cy, r * 0.66, r * 0.90)
    ring_edge = _sample_ring_edge(gray, cx, cy, r)
    contrast = _sample_boundary_contrast(gray, cx, cy, r)
    marker_hits = _marker_ring_hits(gray, cx, cy, r)
    if dark_core < 0.50 or dark_wide < 0.43 or annulus_dark < 0.38 or marker_hits < 7 or ring_edge < 0.05:
        return None
    center_fit = 1.0 - min(1.0, center_dist / 0.44)
    size_fit = 1.0 - min(1.0, abs(rn - 0.145) / 0.12)
    marker_fit = min(1.0, marker_hits / 11.0)
    dark_fit = min(1.0, (0.45 * dark_core + 0.35 * dark_wide + 0.20 * annulus_dark) / 0.72)
    edge_fit = min(1.0, ring_edge / 0.18)
    contrast_fit = max(0.0, min(1.0, (contrast + 0.02) / 0.20))
    q = (0.27 * dark_fit + 0.24 * marker_fit + 0.17 * contrast_fit
         + 0.13 * edge_fit + 0.10 * center_fit + 0.09 * size_fit)
    if contrast < 0.025:
        q *= 0.82
    return Circle(cx, cy, r, q, dark_wide, contrast)


def _hough_pass(gray_for_hough: np.ndarray, min_dim: int, param2: float,
                 scoring_gray: np.ndarray) -> Optional[Circle]:
    circles = cv2.HoughCircles(
        gray_for_hough, cv2.HOUGH_GRADIENT, dp=1.12, minDist=min_dim / 12.0,
        param1=120, param2=param2,
        minRadius=int(min_dim * 0.105), maxRadius=int(min_dim * 0.28),
    )
    best = None
    best_q = -1.0
    if circles is not None:
        for c in circles[0]:
            scored = _score_dial_candidate(scoring_gray, float(c[0]), float(c[1]), float(c[2]), min_dim)
            if scored is not None and scored.quality > best_q:
                best = scored
                best_q = scored.quality
    return best


def detect_dial(bgr: np.ndarray) -> Optional[Circle]:
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (7, 7), 0)
    min_dim = min(bgr.shape[1], bgr.shape[0])

    best = _hough_pass(blur, min_dim, 24, blur)

    if best is None or best.quality < 0.60:
        clahe = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8))
        clahe_mat = clahe.apply(gray)
        clahe_mat = cv2.GaussianBlur(clahe_mat, (7, 7), 0)
        candidate = _hough_pass(clahe_mat, min_dim, 22, clahe_mat)
        if candidate is not None and (best is None or candidate.quality > best.quality):
            best = candidate

    return best
