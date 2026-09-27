"""Phase A frontal landmark measurement for the GMT 12 o'clock triangle.

See docs/architecture/QC_PRINCIPLES.md and
docs/research/GMT_12_TRIANGLE_FRONTAL_MILESTONE.md for the governing design.
This module deliberately does NOT do perspective correction, projective radial
fitting, canonical-dial reconstruction, ellipse fitting, roll solving, or
minute-track-consensus fitting. Everything operates in raw image pixel
coordinates (x right, y down, no EXIF ambiguity -- callers must exif_transpose
before calling). The only geometric primitive is a single Hough-circle dial
locator (seed_detector.detect_dial, reused verbatim from the existing
codebase -- it already does only this one minimal thing: "supplies an
approximate centre/search scale ... cannot set final geometry"), used solely
to size and centre local rectangular search windows. It is NOT one of the
required physical landmarks and its output never appears in a reported ratio
denominator.

Landmark definitions (all in raw source-image pixel coordinates):

- Dial reference circle (cx, cy, r_dial): Hough-circle fit to the dial/bezel
  boundary (seed_detector.detect_dial). Support primitive only -- defines the
  local 12 o'clock axis (x = cx) and the search-window scale, nothing else.

- 60/minute-track reference point: within a narrow vertical column centred on
  x = cx, scan the radial band [MINUTE_TRACK_R_LO, MINUTE_TRACK_R_HI] * r_dial
  above the dial centre. Compute the column-averaged grayscale intensity per
  row. Otsu-threshold that 1-D profile. The landmark is the row closest to the
  dial centre (largest y) that is part of a contiguous above-threshold run
  covering at least MIN_RUN_PX rows -- i.e. the INNER edge of the printed
  minute-track tick mark nearest the triangle. x = cx by construction (this is
  a reference point for the interval, not a laterally-measured landmark).

- Triangle apex / base-left / base-right: within a rectangular ROI (the same
  narrow column x [TRIANGLE_R_LO, TRIANGLE_R_HI] * r_dial), Otsu-threshold to
  isolate the triangle's printed/lumed blob against the dial, take the
  largest contour. Apex = mean of the N_EXTREME topmost (smallest-y) contour
  points. Base-left/base-right = mean of the N_EXTREME leftmost/rightmost
  points among the contour's outward half (largest-y OUTWARD_FRACTION of
  points, so a corner is never picked from partway up a sloped edge).

- Triangle base midpoint: arithmetic midpoint of base-left and base-right.
  Not independently detected.

- Coronet reference point: within a rectangular ROI directly below the
  detected triangle base (column x [triangle_base_y, triangle_base_y +
  CORONET_BAND_PX] extended inward), Otsu-threshold to isolate the printed
  coronet logo, take the largest contour, and use the mean of the N_EXTREME
  topmost (smallest-y, i.e. closest to the triangle) contour points -- never
  a centroid or "middle of the logo".

Any landmark whose search band yields no contour, an implausible contour
area, or (for the minute-track point) no sufficiently long above-threshold
run is reported NOT ASSESSABLE with a reason, per QC_PRINCIPLES.md rule 6 --
never a fabricated coordinate.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Optional

import cv2
import numpy as np

import seed_detector

# -- Search-band constants (fraction of r_dial from the dial centre) --------
# These are deliberately named and centralised so Phase B/C can revisit them
# with evidence rather than they being buried magic numbers. They were set by
# inspecting the one Phase A image (see the Phase A report) and are NOT
# claimed to generalise yet.
MINUTE_TRACK_R_LO = 0.83
MINUTE_TRACK_R_HI = 0.99
TRIANGLE_R_LO = 0.50
TRIANGLE_R_HI = 0.78
CORONET_R_LO = 0.12
CORONET_R_HI = 0.45
SEARCH_HALF_WIDTH_FRACTION = 0.22   # of r_dial, i.e. column width = 0.44 * r_dial

MIN_RUN_PX = 3
N_EXTREME = 5
OUTWARD_FRACTION = 0.40
MIN_BLOB_AREA_NORM = 0.0015   # fraction of r_dial^2
MAX_BLOB_AREA_NORM = 0.09


@dataclass
class DialReference:
    cx: float
    cy: float
    r: float
    quality: float


@dataclass
class Landmark:
    name: str
    x: Optional[float]
    y: Optional[float]
    assessable: bool
    confidence: Optional[float]
    reason: str
    roi_px: Optional[tuple] = None  # (x0, y0, x1, y1) for overlay rendering

    def as_dict(self) -> dict:
        return {
            "name": self.name,
            "x": self.x,
            "y": self.y,
            "assessable": self.assessable,
            "confidence": self.confidence,
            "reason": self.reason,
        }


@dataclass
class PhaseAResult:
    dial: Optional[DialReference]
    landmarks: dict = field(default_factory=dict)   # name -> Landmark
    ratios: dict = field(default_factory=dict)       # name -> float or None
    ratio_reasons: dict = field(default_factory=dict)  # name -> str (why None, if applicable)


def _otsu_threshold_1d(profile: np.ndarray) -> float:
    """Otsu's method on a 1-D array, returning the threshold value."""
    hist, bin_edges = np.histogram(profile, bins=256, range=(0.0, 255.0))
    hist = hist.astype(np.float64)
    total = hist.sum()
    if total <= 0:
        return 128.0
    sum_all = np.dot(hist, np.arange(256))
    sum_b, w_b = 0.0, 0.0
    best_thresh, best_var = 0.0, -1.0
    for t in range(256):
        w_b += hist[t]
        if w_b == 0:
            continue
        w_f = total - w_b
        if w_f == 0:
            break
        sum_b += t * hist[t]
        m_b = sum_b / w_b
        m_f = (sum_all - sum_b) / w_f
        var_between = w_b * w_f * (m_b - m_f) ** 2
        # >= (not >): among several thresholds tied for maximum between-class
        # variance (e.g. a flat plateau with no pixels at all), prefer the
        # LAST one. find_minute_track_reference's caller does
        # `row_profile >= thresh`, so the threshold must sit just below the
        # foreground cluster's minimum, not just above the background
        # cluster's minimum -- the first tied t is the latter (wrong: it
        # would classify the whole background cluster as foreground too).
        if var_between >= best_var:
            best_var = var_between
            best_thresh = t
    return float(best_thresh)


def detect_dial_reference(bgr: np.ndarray) -> Optional[DialReference]:
    circle = seed_detector.detect_dial(bgr)
    if circle is None:
        return None
    return DialReference(cx=circle.x, cy=circle.y, r=circle.r, quality=circle.quality)


def find_minute_track_reference(gray: np.ndarray, dial: DialReference) -> Landmark:
    half_w = SEARCH_HALF_WIDTH_FRACTION * dial.r
    x0 = int(round(dial.cx - half_w))
    x1 = int(round(dial.cx + half_w))
    y_top = int(round(dial.cy - MINUTE_TRACK_R_HI * dial.r))
    y_bottom = int(round(dial.cy - MINUTE_TRACK_R_LO * dial.r))
    h, w = gray.shape[:2]
    x0, x1 = max(0, x0), min(w, x1)
    y_top, y_bottom = max(0, y_top), min(h, y_bottom)
    roi = (x0, y_top, x1, y_bottom)
    if x1 <= x0 or y_bottom <= y_top:
        return Landmark("minute_track_60", None, None, False, None,
                         "search band fell outside image bounds", roi)

    band = gray[y_top:y_bottom, x0:x1].astype(np.float64)
    row_profile = band.mean(axis=1)
    thresh = _otsu_threshold_1d(row_profile)
    above = row_profile >= thresh
    if not above.any():
        return Landmark("minute_track_60", None, None, False, None,
                         "no row in the search band exceeded the Otsu threshold", roi)

    # Find the above-threshold run closest to the dial centre (largest local
    # row index -> largest image y), require it to be at least MIN_RUN_PX rows.
    runs = []
    start = None
    for i, v in enumerate(above):
        if v and start is None:
            start = i
        elif not v and start is not None:
            runs.append((start, i - 1))
            start = None
    if start is not None:
        runs.append((start, len(above) - 1))
    runs = [r for r in runs if (r[1] - r[0] + 1) >= MIN_RUN_PX]
    if not runs:
        return Landmark("minute_track_60", None, None, False, None,
                         f"no above-threshold run reached the minimum {MIN_RUN_PX}px length", roi)
    run = max(runs, key=lambda r: r[1])  # innermost (largest row index) run
    inner_row = run[1]
    landmark_y = float(y_top + inner_row)
    landmark_x = float(dial.cx)
    separation = float(row_profile[run[0]:run[1] + 1].mean() - row_profile.min())
    max_possible = float(row_profile.max() - row_profile.min()) or 1.0
    confidence = max(0.0, min(1.0, separation / max_possible))
    return Landmark("minute_track_60", landmark_x, landmark_y, True, confidence, "", roi)


def _segment_blob(gray: np.ndarray, dial: DialReference, r_lo: float, r_hi: float,
                   half_width: float) -> tuple:
    """Otsu-threshold + largest-contour isolation within a rectangular ROI.
    Returns (contour_points_xy_abs, roi_bounds) or (None, roi_bounds)."""
    x0 = int(round(dial.cx - half_width))
    x1 = int(round(dial.cx + half_width))
    y_top = int(round(dial.cy - r_hi * dial.r))
    y_bottom = int(round(dial.cy - r_lo * dial.r))
    h, w = gray.shape[:2]
    x0, x1 = max(0, x0), min(w, x1)
    y_top, y_bottom = max(0, y_top), min(h, y_bottom)
    roi = (x0, y_top, x1, y_bottom)
    if x1 <= x0 or y_bottom <= y_top:
        return None, roi

    patch = gray[y_top:y_bottom, x0:x1]
    blur = cv2.GaussianBlur(patch, (3, 3), 0.8)
    _, binary = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    # Bright-on-dark polarity (lume/paint on black dial); invert if Otsu
    # picked the majority (background) as foreground.
    if cv2.countNonZero(binary) > binary.size * 0.5:
        binary = cv2.bitwise_not(binary)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
    binary = cv2.morphologyEx(binary, cv2.MORPH_OPEN, kernel)
    binary = cv2.morphologyEx(binary, cv2.MORPH_CLOSE, kernel)

    contours, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    if not contours:
        return None, roi
    contour = max(contours, key=cv2.contourArea)
    area = cv2.contourArea(contour)
    area_norm = area / max(1.0, dial.r ** 2)
    if not (MIN_BLOB_AREA_NORM <= area_norm <= MAX_BLOB_AREA_NORM):
        return None, roi
    if len(contour) < N_EXTREME:
        return None, roi
    pts = contour.reshape(-1, 2).astype(np.float64)
    pts[:, 0] += x0
    pts[:, 1] += y_top
    return pts, roi


def find_triangle_landmarks(gray: np.ndarray, dial: DialReference) -> dict:
    half_w = SEARCH_HALF_WIDTH_FRACTION * dial.r
    pts, roi = _segment_blob(gray, dial, TRIANGLE_R_LO, TRIANGLE_R_HI, half_w)
    if pts is None:
        reason = "no plausible triangle blob found in the search band"
        return {
            "triangle_apex": Landmark("triangle_apex", None, None, False, None, reason, roi),
            "triangle_base_left": Landmark("triangle_base_left", None, None, False, None, reason, roi),
            "triangle_base_right": Landmark("triangle_base_right", None, None, False, None, reason, roi),
        }

    order = np.argsort(pts[:, 1])  # ascending y: smallest y (outward/apex) first
    apex_pts = pts[order[:N_EXTREME]]
    apex_xy = apex_pts.mean(axis=0)

    n_outward = max(N_EXTREME, int(round(len(pts) * OUTWARD_FRACTION)))
    outward_pts = pts[order[-n_outward:]]  # largest-y subset (base side)
    left_order = np.argsort(outward_pts[:, 0])
    left_pts = outward_pts[left_order[:N_EXTREME]]
    right_pts = outward_pts[left_order[-N_EXTREME:]]
    left_xy = left_pts.mean(axis=0)
    right_xy = right_pts.mean(axis=0)

    area_conf = 1.0  # plausibility already gated in _segment_blob; kept simple for Phase A
    apex = Landmark("triangle_apex", float(apex_xy[0]), float(apex_xy[1]), True, area_conf, "", roi)
    base_left = Landmark("triangle_base_left", float(left_xy[0]), float(left_xy[1]), True, area_conf, "", roi)
    base_right = Landmark("triangle_base_right", float(right_xy[0]), float(right_xy[1]), True, area_conf, "", roi)
    return {"triangle_apex": apex, "triangle_base_left": base_left, "triangle_base_right": base_right}


def find_coronet_reference(gray: np.ndarray, dial: DialReference) -> Landmark:
    half_w = SEARCH_HALF_WIDTH_FRACTION * dial.r
    pts, roi = _segment_blob(gray, dial, CORONET_R_LO, CORONET_R_HI, half_w)
    if pts is None:
        return Landmark("coronet", None, None, False, None,
                         "no plausible coronet blob found in the search band", roi)
    order = np.argsort(pts[:, 1])
    top_pts = pts[order[:N_EXTREME]]
    top_xy = top_pts.mean(axis=0)
    return Landmark("coronet", float(top_xy[0]), float(top_xy[1]), True, 1.0, "", roi)


def compute_ratios(landmarks: dict, axis_cx: Optional[float]) -> tuple:
    """Returns (ratios, reasons) -- ratios[name] is a float or None;
    reasons[name] explains a None (missing landmark, or a degenerate
    denominator), never silently omitted. axis_cx is the local 12 o'clock
    axis x-coordinate (the dial reference circle's centre), used only for
    horizontal_displacement_normalized."""
    ratios, reasons = {}, {}

    def get(name):
        lm = landmarks.get(name)
        return lm if (lm is not None and lm.assessable) else None

    mt = get("minute_track_60")
    cor = get("coronet")
    apex = get("triangle_apex")
    bl = get("triangle_base_left")
    br = get("triangle_base_right")

    names = ["apex_position_in_interval", "base_position_in_interval",
             "triangle_height_ratio", "base_width_over_height",
             "horizontal_displacement_normalized"]
    for n in names:
        ratios[n] = None
        reasons[n] = "not computed"

    if mt is None or cor is None:
        reason = "minute-track and/or coronet reference not assessable"
        for n in ("apex_position_in_interval", "base_position_in_interval"):
            reasons[n] = reason
    else:
        interval = cor.y - mt.y
        if interval <= 0:
            for n in ("apex_position_in_interval", "base_position_in_interval"):
                reasons[n] = f"degenerate interval (coronet.y={cor.y} <= minute_track.y={mt.y})"
        else:
            if apex is not None:
                ratios["apex_position_in_interval"] = (apex.y - mt.y) / interval
                reasons["apex_position_in_interval"] = ""
            else:
                reasons["apex_position_in_interval"] = "triangle apex not assessable"
            if bl is not None and br is not None:
                base_mid_y = (bl.y + br.y) / 2.0
                ratios["base_position_in_interval"] = (base_mid_y - mt.y) / interval
                reasons["base_position_in_interval"] = ""
            else:
                reasons["base_position_in_interval"] = "triangle base corners not assessable"

    if apex is not None and bl is not None and br is not None:
        base_mid_y = (bl.y + br.y) / 2.0
        base_mid_x = (bl.x + br.x) / 2.0
        height = base_mid_y - apex.y
        if height <= 0:
            reasons["triangle_height_ratio"] = f"degenerate height (base_mid_y={base_mid_y} <= apex.y={apex.y})"
            reasons["base_width_over_height"] = reasons["triangle_height_ratio"]
            reasons["horizontal_displacement_normalized"] = reasons["triangle_height_ratio"]
        else:
            if mt is not None and cor is not None and (cor.y - mt.y) > 0:
                ratios["triangle_height_ratio"] = height / (cor.y - mt.y)
                reasons["triangle_height_ratio"] = ""
            else:
                reasons["triangle_height_ratio"] = "minute-track/coronet interval not assessable"
            width = br.x - bl.x
            ratios["base_width_over_height"] = width / height
            reasons["base_width_over_height"] = ""
            if axis_cx is not None:
                ratios["horizontal_displacement_normalized"] = (base_mid_x - axis_cx) / height
                reasons["horizontal_displacement_normalized"] = ""
            else:
                reasons["horizontal_displacement_normalized"] = "dial reference centre not available"
    else:
        missing = "triangle apex and/or base corners not assessable"
        reasons["triangle_height_ratio"] = missing
        reasons["base_width_over_height"] = missing
        reasons["horizontal_displacement_normalized"] = missing

    return ratios, reasons


def measure(bgr: np.ndarray) -> PhaseAResult:
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    dial = detect_dial_reference(bgr)
    if dial is None:
        return PhaseAResult(dial=None, landmarks={}, ratios={}, ratio_reasons={
            "all": "dial reference circle not found; no landmark search possible"})

    landmarks = {}
    landmarks["minute_track_60"] = find_minute_track_reference(gray, dial)
    landmarks.update(find_triangle_landmarks(gray, dial))
    landmarks["coronet"] = find_coronet_reference(gray, dial)

    ratios, reasons = compute_ratios(landmarks, axis_cx=dial.cx)
    return PhaseAResult(dial=dial, landmarks=landmarks, ratios=ratios, ratio_reasons=reasons)
