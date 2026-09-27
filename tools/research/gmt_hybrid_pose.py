from __future__ import annotations

"""Research-only hybrid pose estimator for watch QC photographs.

Combines two independent cues:

1. visible rehaut depth asymmetry, which is sensitive to direction and severe
   obliqueness;
2. an ellipse fit to a dial/rehaut boundary, which estimates planar
   foreshortening magnitude but has no signed near/far direction.

This module deliberately does NOT alter production QC thresholds or apply a
numeric GMT12 correction. Its current labels are experimental only and exist
so the same-watch control sets can be rerun consistently while we validate the
architecture.
"""

from dataclasses import dataclass
from enum import Enum
import math
from typing import Optional

import cv2
import numpy as np

from gmt12_auto_landmarks import _dial_circle, _triangle_candidate
from gmt_rehaut_perspective import RehautPerspective, analyze_rehaut_perspective


class ExperimentalPoseLabel(str, Enum):
    GOOD = "GOOD"
    CORRECTABLE = "CORRECTABLE"
    RETAKE = "RETAKE"
    UNASSESSABLE = "UNASSESSABLE"


@dataclass(frozen=True)
class EllipsePose:
    axis_ratio: float
    tilt_deg: float
    minor_axis_direction_deg: float
    fit_rms_over_radius: float
    sample_count: int


@dataclass(frozen=True)
class HybridPose:
    label: ExperimentalPoseLabel
    reason: str
    rehaut: Optional[RehautPerspective]
    ellipse: Optional[EllipsePose]
    watch_roll_deg: Optional[float]
    watch_vertical_asymmetry: Optional[float]
    watch_horizontal_asymmetry: Optional[float]
    cue_axis_disagreement_deg: Optional[float]


def _angle_diff_180(a: float, b: float) -> float:
    d = abs((a - b) % 180.0)
    return min(d, 180.0 - d)


def _ellipse_from_annular_edges(gray: np.ndarray, cx: float, cy: float, r: float) -> Optional[EllipsePose]:
    """Fit a physical circular boundary projected as an ellipse.

    The Hough circle is only a centre/scale seed. For each polar angle we take
    the strongest radial gradient in a narrow annulus, then fit an ellipse to
    those angularly-distributed samples. This is preferable to fitting all
    Canny pixels, which overweights numerals, hands, reflections and text.
    """
    if r <= 10:
        return None

    blur = cv2.GaussianBlur(gray, (5, 5), 1.0)
    gx = cv2.Sobel(blur, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(blur, cv2.CV_32F, 0, 1, ksize=3)
    grad = cv2.magnitude(gx, gy)

    pts = []
    strengths = []
    # The dial/rehaut boundary normally lives close to the Hough seed. Keep the
    # annulus deliberately narrow to avoid the bezel insert and inner markers.
    r0, r1 = 0.82 * r, 1.06 * r
    for deg in range(0, 360, 2):
        t = math.radians(deg)
        ct, st = math.cos(t), math.sin(t)
        best = None
        for rr in np.linspace(r0, r1, 36):
            x = int(round(cx + rr * ct))
            y = int(round(cy + rr * st))
            if x < 1 or y < 1 or x >= gray.shape[1] - 1 or y >= gray.shape[0] - 1:
                continue
            g = float(grad[y, x])
            if best is None or g > best[0]:
                best = (g, float(x), float(y))
        if best is not None:
            strengths.append(best[0])
            pts.append((best[1], best[2]))

    if len(pts) < 30:
        return None

    strengths = np.asarray(strengths, float)
    pts = np.asarray(pts, np.float32)
    cut = float(np.percentile(strengths, 35))
    keep = strengths >= cut
    pts = pts[keep]
    if len(pts) < 20:
        return None

    # Initial fit, then trim gross outliers by normalized ellipse radius.
    try:
        (ecx, ecy), (a, b), angle = cv2.fitEllipse(pts.reshape(-1, 1, 2))
    except cv2.error:
        return None
    if min(a, b) <= 1:
        return None

    def residuals(points, ecx, ecy, a, b, angle):
        phi = math.radians(angle)
        c, s = math.cos(phi), math.sin(phi)
        q = points.astype(float) - np.array([ecx, ecy])
        xr = q[:, 0] * c + q[:, 1] * s
        yr = -q[:, 0] * s + q[:, 1] * c
        # fitEllipse returns full diameters
        rho = np.sqrt((xr / (a / 2.0)) ** 2 + (yr / (b / 2.0)) ** 2)
        return np.abs(rho - 1.0)

    for _ in range(2):
        res = residuals(pts, ecx, ecy, a, b, angle)
        med = float(np.median(res))
        mad = float(np.median(np.abs(res - med)))
        lim = med + max(0.015, 3.5 * 1.4826 * mad)
        k = res <= lim
        if k.sum() < 20 or k.sum() == len(pts):
            break
        pts = pts[k]
        try:
            (ecx, ecy), (a, b), angle = cv2.fitEllipse(pts.reshape(-1, 1, 2))
        except cv2.error:
            return None

    major, minor = (float(a), float(b)) if a >= b else (float(b), float(a))
    axis_ratio = max(0.0, min(1.0, minor / major))
    tilt = math.degrees(math.acos(axis_ratio))

    # cv2 angle names one ellipse axis; convert to the minor-axis image angle.
    if a < b:
        minor_dir = float(angle) % 180.0
    else:
        minor_dir = (float(angle) + 90.0) % 180.0

    res = residuals(pts, ecx, ecy, a, b, angle)
    rms = float(np.sqrt(np.mean(res ** 2)))
    return EllipsePose(
        axis_ratio=axis_ratio,
        tilt_deg=tilt,
        minor_axis_direction_deg=minor_dir,
        fit_rms_over_radius=rms,
        sample_count=int(len(pts)),
    )


def _watch_roll_and_projected_rehaut(gray: np.ndarray, p: RehautPerspective):
    seed = _dial_circle(gray)
    if seed is None:
        return None, None, None
    cx, cy, r = map(float, seed)
    tri = _triangle_candidate(gray, cx, cy, r)
    if tri is None:
        return None, None, None
    tl, tr, _ = tri
    tx, ty = (tl.x + tr.x) / 2.0, (tl.y + tr.y) / 2.0
    ux, uy = tx - cx, ty - cy
    n = math.hypot(ux, uy)
    if n <= 1e-9:
        return None, None, None
    ux, uy = ux / n, uy / n  # watch 12 direction in image coordinates
    rx, ry = -uy, ux         # watch 3 direction

    # Image-coordinate signed rehaut vector: +x means more visible at image
    # right; +y means more visible at image bottom. p.vertical_asymmetry is
    # defined as top-bottom, hence y = -V.
    vx = float(p.horizontal_asymmetry)
    vy = -float(p.vertical_asymmetry)
    watch_h = vx * rx + vy * ry
    watch_v = vx * ux + vy * uy
    roll = math.degrees(math.atan2(ux, -uy))
    return roll, watch_v, watch_h


def _experimental_label(p: RehautPerspective, e: Optional[EllipsePose]) -> tuple[ExperimentalPoseLabel, str]:
    """Provisional research gate derived from current same-watch pilots.

    These are NOT production thresholds. They deliberately err toward RETAKE
    when the rehaut collapses or the fit becomes poor, and toward CORRECTABLE
    for intermediate evidence. They are intended to be replaced after the
    known-angle GMT series.
    """
    if p.edge_coverage < 0.20:
        return ExperimentalPoseLabel.UNASSESSABLE, "insufficient rehaut edge coverage"
    if p.normalized_fit_residual > 0.20:
        return ExperimentalPoseLabel.UNASSESSABLE, "rehaut fit residual too large"

    if p.min_width_over_mean < 0.50:
        return ExperimentalPoseLabel.RETAKE, "visible rehaut collapses below 50% of its mean width"
    if p.normalized_fit_residual > 0.16:
        return ExperimentalPoseLabel.RETAKE, "rehaut fit is unstable at this angle/lighting"
    if e is not None and e.tilt_deg >= 14.0:
        return ExperimentalPoseLabel.RETAKE, "planar ellipse implies severe obliqueness"

    if p.min_width_over_mean < 0.80:
        return ExperimentalPoseLabel.CORRECTABLE, "moderate rehaut compression"
    if p.first_harmonic_strength >= 0.15:
        return ExperimentalPoseLabel.CORRECTABLE, "moderate directional rehaut asymmetry"
    if e is not None and e.tilt_deg >= 8.0:
        return ExperimentalPoseLabel.CORRECTABLE, "moderate planar foreshortening"

    return ExperimentalPoseLabel.GOOD, "rehaut and planar cues are within the near-frontal research band"


def analyze_hybrid_pose(bgr: np.ndarray) -> HybridPose:
    if bgr is None or bgr.size == 0:
        return HybridPose(ExperimentalPoseLabel.UNASSESSABLE, "empty image", None, None, None, None, None, None)

    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY) if bgr.ndim == 3 else bgr.copy()
    rd = analyze_rehaut_perspective(bgr)
    if rd.perspective is None:
        return HybridPose(ExperimentalPoseLabel.UNASSESSABLE, rd.reason or "rehaut unavailable", None, None, None, None, None, None)
    p = rd.perspective

    seed = _dial_circle(gray)
    e = None
    if seed is not None:
        e = _ellipse_from_annular_edges(gray, *map(float, seed))

    roll, watch_v, watch_h = _watch_roll_and_projected_rehaut(gray, p)

    disagreement = None
    if e is not None and p.first_harmonic_strength >= 0.08:
        # Rehaut widest-direction is signed; ellipse minor axis is unsigned.
        disagreement = _angle_diff_180(float(p.widest_direction_deg), float(e.minor_axis_direction_deg))

    label, reason = _experimental_label(p, e)
    if disagreement is not None and e is not None and e.tilt_deg >= 8.0 and disagreement > 50.0:
        # Do not pretend two strong contradictory cues are safely correctable.
        label = ExperimentalPoseLabel.UNASSESSABLE
        reason = f"rehaut/ellipse tilt axes disagree by {disagreement:.1f} deg"

    return HybridPose(
        label=label,
        reason=reason,
        rehaut=p,
        ellipse=e,
        watch_roll_deg=roll,
        watch_vertical_asymmetry=watch_v,
        watch_horizontal_asymmetry=watch_h,
        cue_axis_disagreement_deg=disagreement,
    )
