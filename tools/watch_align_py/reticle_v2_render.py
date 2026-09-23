"""Rendering for the v2 generic geometry reticle (experiment/generic-
geometry-reticle-v2). Two modes only:

- user_mode(): centre target, minute-track channel, 12 gapped hour axes,
  round-marker corridor (inner/centre/outer). No detection points, no
  labels, no confidence text, no warnings -- this is the candidate
  "real product overlay" look.
- diagnostic_mode(): the same geometry plus the round-marker source
  points (centroid/inner/outer) and their hour-number labels, so the
  corridor's honesty can be visually checked against the photo. Fit
  residual numbers are NOT drawn on the photo -- see diagnostic_panel()
  for the separate text panel.

No red or green anywhere. No pass/fail implied by colour or symbol.
"""
from __future__ import annotations

import cv2
import numpy as np

import reticle_v2 as rv2

# BGR. Cyan family = structural (channel/axes/centre), same as v1.
# Gold family = the independently-fit round-marker corridor, same as v1.
# Plain white = diagnostic-only source points/labels, never part of the
# user-mode overlay.
CYAN = (235, 220, 140)
GOLD = (110, 190, 235)
WHITE = (245, 245, 245)
DIM = (170, 170, 170)


def _blend_line(img, p0, p1, color, thickness=1, alpha=0.6):
    overlay = img.copy()
    cv2.line(overlay, p0, p1, color, thickness, cv2.LINE_AA)
    cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0, dst=img)


def _blend_polyline(img, pts, color, thickness=1, alpha=0.55, closed=True, dashed=False):
    if len(pts) < 2:
        return
    overlay = img.copy()
    if dashed:
        for i in range(0, len(pts) - 1, 2):
            cv2.line(overlay, pts[i], pts[i + 1], color, thickness, cv2.LINE_AA)
    else:
        cv2.polylines(overlay, [np.array(pts, dtype=np.int32)], closed, color, thickness, cv2.LINE_AA)
    cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0, dst=img)


def _blend_circle(img, center, radius, color, thickness=1, alpha=0.6, fill=False):
    overlay = img.copy()
    cv2.circle(overlay, center, radius, color, -1 if fill else thickness, cv2.LINE_AA)
    cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0, dst=img)


def _centre_target(img, center, color=WHITE):
    _blend_circle(img, center, 3, color, thickness=1, alpha=0.75)
    _blend_circle(img, center, 7, color, thickness=1, alpha=0.4)


def _channel(img, geo: rv2.ReticleGeometryV2, alpha=0.45):
    _blend_polyline(img, geo.channel_inner, CYAN, thickness=1, alpha=alpha)
    _blend_polyline(img, geo.channel_outer, CYAN, thickness=1, alpha=alpha)


def _axes(img, geo: rv2.ReticleGeometryV2, alpha=0.4):
    strong = {12, 3, 6, 9}
    for hour, segs in geo.axis_segments:
        a = alpha + 0.15 if hour in strong else alpha
        for p0, p1 in segs:
            _blend_line(img, p0, p1, CYAN, thickness=1, alpha=a)


def _corridor(img, geo: rv2.ReticleGeometryV2, alpha=0.5):
    for which in ("inner", "outer"):
        ring = geo.corridor_rings.get(which)
        if ring:
            _blend_polyline(img, ring, GOLD, thickness=1, alpha=alpha)
    centre_ring = geo.corridor_rings.get("centre")
    if centre_ring:
        _blend_polyline(img, centre_ring, GOLD, thickness=1, alpha=alpha * 0.85, dashed=True)


def user_mode(bgr: np.ndarray, geo: rv2.ReticleGeometryV2) -> np.ndarray:
    out = bgr.copy()
    _channel(out, geo)
    _axes(out, geo)
    _corridor(out, geo)
    _centre_target(out, geo.centre_xy)
    return out


def diagnostic_mode(bgr: np.ndarray, geo: rv2.ReticleGeometryV2) -> np.ndarray:
    out = user_mode(bgr, geo)
    for h, obs in sorted(geo.round_observations.items()):
        cx, cy = int(round(obs.centroid_x)), int(round(obs.centroid_y))
        ox, oy = int(round(obs.outer_x)), int(round(obs.outer_y))
        ix, iy = int(round(obs.inner_x)), int(round(obs.inner_y))
        _blend_circle(out, (cx, cy), 2, WHITE, thickness=1, alpha=0.85, fill=True)
        _blend_circle(out, (ox, oy), 2, WHITE, thickness=1, alpha=0.7)
        _blend_circle(out, (ix, iy), 2, WHITE, thickness=1, alpha=0.7)
        overlay = out.copy()
        cv2.putText(overlay, str(h), (cx + 6, cy - 6), cv2.FONT_HERSHEY_SIMPLEX, 0.45, WHITE, 1, cv2.LINE_AA)
        cv2.addWeighted(overlay, 0.85, out, 0.15, 0, dst=out)
    return out


def diagnostic_panel(geo: rv2.ReticleGeometryV2, width: int = 900, extra_lines=None) -> np.ndarray:
    """Fit-residual / provenance text, rendered as its own image panel --
    never overlaid on the dial photo."""
    lines = [
        f"round markers segmented: {geo.n_round_segmented}/8 "
        f"({sorted(geo.round_observations.keys())})",
    ]
    if geo.corridor_suppressed_reason:
        lines.append(f"corridor SUPPRESSED: {geo.corridor_suppressed_reason}")
    else:
        for which in ("inner", "centre", "outer"):
            s = geo.corridor_fit_summary.get(which)
            if s is None:
                lines.append(f"{which}: fit unavailable")
            else:
                lines.append(
                    f"{which}: n={s.n_points} median_residual={s.median_residual_px:.3f}px "
                    f"robust_std={s.robust_std_px:.3f}px"
                )
    if extra_lines:
        lines.append("")
        lines.extend(extra_lines)

    line_h = 24
    height = line_h * (len(lines) + 1) + 16
    panel = np.full((height, width, 3), 255, dtype=np.uint8)
    y = 24
    for line in lines:
        cv2.putText(panel, line, (12, y), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (20, 20, 20), 1, cv2.LINE_AA)
        y += line_h
    return panel


def side_by_side(left: np.ndarray, right: np.ndarray, left_label: str, right_label: str,
                  target_h: int = 1200) -> np.ndarray:
    def fit(img):
        h, w = img.shape[:2]
        scale = target_h / h
        return cv2.resize(img, (round(w * scale), target_h), interpolation=cv2.INTER_AREA)

    l, r = fit(left), fit(right)
    bar_h = 40
    canvas_w = l.shape[1] + r.shape[1] + 12
    canvas = np.full((target_h + bar_h, canvas_w, 3), 255, dtype=np.uint8)
    cv2.putText(canvas, left_label, (10, bar_h - 12), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (20, 20, 20), 2, cv2.LINE_AA)
    cv2.putText(canvas, right_label, (l.shape[1] + 20, bar_h - 12), cv2.FONT_HERSHEY_SIMPLEX, 0.7,
                (20, 20, 20), 2, cv2.LINE_AA)
    canvas[bar_h:bar_h + target_h, 0:l.shape[1]] = l
    canvas[bar_h:bar_h + target_h, l.shape[1] + 12:l.shape[1] + 12 + r.shape[1]] = r
    return canvas
