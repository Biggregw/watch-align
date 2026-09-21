"""Port of MinuteTrackIdentityGate.java. Independent candidate-identity evidence
for an already-resolved minute-track pose. Never searches for, fits, moves,
resizes or rotates a pose -- only samples broad, pose-projected regions."""
import math
from dataclasses import dataclass
from enum import Enum
from typing import List, Optional

import cv2
import numpy as np

import master
from geometry import RotatedRect, map_point

DARK_DIAL_MAX = 135.0
MIN_INTERIOR_EDGE_FRACTION = 0.02


class Verdict(Enum):
    PASS = "PASS"
    AMBIGUOUS = "AMBIGUOUS"
    FAIL = "FAIL"


@dataclass
class Evidence:
    verdict: Verdict
    dial_interior_median: float
    markers_found: int
    interior_edge_fraction: float


def _percentile_sorted(sorted_values: List[float], q: float) -> float:
    if not sorted_values:
        return math.nan
    pos = q * (len(sorted_values) - 1)
    f = pos - math.floor(pos)
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return sorted_values[lo]
    return sorted_values[lo] * (1.0 - f) + sorted_values[hi] * f


def _sample_gray(gray: np.ndarray, x: float, y: float) -> float:
    h, w = gray.shape[:2]
    if x < 1 or y < 1 or x >= w - 1 or y >= h - 1:
        return math.nan
    x0 = math.floor(x)
    y0 = math.floor(y)
    fx = x - x0
    fy = y - y0
    a = float(gray[y0, x0])
    b = float(gray[y0, x0 + 1])
    c = float(gray[y0 + 1, x0])
    d = float(gray[y0 + 1, x0 + 1])
    return (a * (1.0 - fx) + b * fx) * (1.0 - fy) + (c * (1.0 - fx) + d * fx) * fy


def _sample_binary_nearest(binary: np.ndarray, x: float, y: float) -> Optional[float]:
    xi = round(x)
    yi = round(y)
    h, w = binary.shape[:2]
    if xi < 0 or yi < 0 or xi >= w or yi >= h:
        return None
    return float(binary[yi, xi])


def dial_interior_median(gray: np.ndarray, ellipse: RotatedRect, roll: float) -> float:
    values = []
    for ri in range(8):
        r = 0.32 + (0.60 - 0.32) * ri / 7.0
        for ai in range(72):
            a = 2.0 * math.pi * ai / 72.0
            px, py = map_point(ellipse, 1.0, roll, r * math.cos(a), r * math.sin(a))
            v = _sample_gray(gray, px, py)
            if math.isfinite(v):
                values.append(v)
    values.sort()
    return math.nan if not values else _percentile_sorted(values, 0.50)


def interior_edge_fraction(edges: np.ndarray, ellipse: RotatedRect, roll: float) -> float:
    if edges is None or edges.size == 0:
        return 0.0
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    dilated = cv2.dilate(edges, kernel)
    hits = 0
    total = 0
    for ri in range(8):
        r = 0.32 + (0.60 - 0.32) * ri / 7.0
        for ai in range(72):
            a = 2.0 * math.pi * ai / 72.0
            px, py = map_point(ellipse, 1.0, roll, r * math.cos(a), r * math.sin(a))
            v = _sample_binary_nearest(dilated, px, py)
            if v is None:
                continue
            total += 1
            if v > 0.0:
                hits += 1
    return 0.0 if total == 0 else hits / total


def marker_good(gray: np.ndarray, ellipse: RotatedRect, roll: float, hour: int) -> bool:
    center_r = 0.729 if hour == 12 else 0.677
    radial_half = 0.15 if hour == 12 else 0.14
    tang_half = 0.11 if hour == 12 else 0.075
    angle = master.angle_for_hour(hour)
    ux, uy = math.cos(angle), math.sin(angle)
    vx, vy = -uy, ux
    cx, cy = center_r * ux, center_r * uy
    radial_extent = radial_half * 1.45
    tang_extent = tang_half * 1.80
    rows = cols = 72
    patch = np.zeros((rows, cols), dtype=np.uint8)
    core: List[float] = []
    surround: List[float] = []
    for y in range(rows):
        u = -radial_extent + 2.0 * radial_extent * y / (rows - 1.0)
        for x in range(cols):
            v = -tang_extent + 2.0 * tang_extent * x / (cols - 1.0)
            px = cx + ux * u + vx * v
            py = cy + uy * u + vy * v
            mx, my = map_point(ellipse, 1.0, roll, px, py)
            value = _sample_gray(gray, mx, my)
            if not math.isfinite(value):
                value = 0.0
            patch[y, x] = np.uint8(value)
            in_core = abs(u) <= radial_half and abs(v) <= tang_half
            (core if in_core else surround).append(value)
    if not core or not surround:
        return False
    core_sorted = sorted(core)
    surround_sorted = sorted(surround)
    bg = _percentile_sorted(surround_sorted, 0.50)
    p90 = _percentile_sorted(core_sorted, 0.90)
    contrast = p90 - bg
    bright_threshold = max(150.0, bg + 45.0)
    bright = sum(1 for v in core if v >= bright_threshold)
    bright_fraction = bright / len(core)

    threshold = max(150.0, min(220.0, bg + 45.0))
    _, bw = cv2.threshold(patch, threshold, 255.0, cv2.THRESH_BINARY)
    num_labels, _labels, stats, _centroids = cv2.connectedComponentsWithStats(bw, connectivity=8)
    largest = 0.0
    for i in range(1, num_labels):
        largest = max(largest, float(stats[i, cv2.CC_STAT_AREA]))
    area_fraction = largest / max(1.0, len(core))
    return p90 >= 165.0 and contrast >= 35.0 and (bright_fraction >= 0.05 or area_fraction >= 0.025)


def evaluate(gray: np.ndarray, edges: np.ndarray, ellipse: RotatedRect, roll: float) -> Evidence:
    """PASS requires a dark, textured dial interior and all three of 12/6/9
    isolated as plausible bright markers. AMBIGUOUS is exactly two of three
    markers found with a dark, textured interior. Anything else is FAIL."""
    dial_median = dial_interior_median(gray, ellipse, roll)
    edge_fraction = interior_edge_fraction(edges, ellipse, roll)
    dark = math.isfinite(dial_median) and dial_median <= DARK_DIAL_MAX
    textured = edge_fraction >= MIN_INTERIOR_EDGE_FRACTION
    found = 0
    if marker_good(gray, ellipse, roll, 12):
        found += 1
    if marker_good(gray, ellipse, roll, 6):
        found += 1
    if marker_good(gray, ellipse, roll, 9):
        found += 1
    if not dark or not textured:
        verdict = Verdict.FAIL
    elif found >= 3:
        verdict = Verdict.PASS
    elif found == 2:
        verdict = Verdict.AMBIGUOUS
    else:
        verdict = Verdict.FAIL
    return Evidence(verdict, dial_median, found, edge_fraction)
