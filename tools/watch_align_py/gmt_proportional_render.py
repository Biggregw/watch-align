"""Human-facing proportion overlay for the GMT 12-triangle
(experiment/gmt-proportional-geometry-v1).

The frozen structural layer (reticle_v2.py: dial centre, minute-track
channel, 12 gapped hour axes, round-marker peer corridor) is reused
UNCHANGED and kept visually subdued here, per the shared research
knowledge base's overlay principles -- this module only ADDS a second,
local proportion-explanation layer at 12:

- expected apex / centre / base gates (short brackets, from the frozen
  calibration profile's median position along the 12 axis -- never a
  full circle around the whole dial),
- a subtle expected-genuine band (from the profile's useful_central_range)
  at the apex and base gates,
- the expected 12 symmetry axis (already drawn by the structural layer;
  not duplicated here),
- the OBSERVED apex/centre/base points and the observed triangle axis,
  from this photo's own independent segmentation,
- a local base-to-minute-track bracket.

No red or green. No PASS/FAIL. No authenticity wording. This is an
expected-vs-observed geometric comparison only -- the human reviewer
draws their own conclusion.
"""
from __future__ import annotations

import math
from typing import Optional

import cv2
import numpy as np

import geometry
import master
import reticle_v2 as rv2
import reticle_v2_render as rr2
from gmt_proportional_features import GmtProportionalResult

# BGR. Reuses reticle_v2_render's structural palette for the frozen
# layer; adds a new muted violet for "expected, from the calibration
# profile" and reuses WHITE for "observed, from this photo" -- matching
# the project-wide convention that white = this photo's own evidence.
EXPECTED = (215, 175, 205)   # muted violet/lilac -- calibration-profile expectation
OBSERVED = rr2.WHITE
STRUCTURAL_ALPHA_SCALE = 0.6  # subdues the frozen structural layer further, per KB overlay principles

HOUR = 12


def _axis_point(ellipse, roll: float, r: float, hour: int = HOUR):
    angle = master.angle_for_hour(hour)
    x, y = geometry.map_point(ellipse, 1.0, roll, r * math.cos(angle), r * math.sin(angle))
    return (int(round(x)), int(round(y)))


def _bracket(img: np.ndarray, ellipse, roll: float, r: float, color, half_width_canon: float = 0.05,
             alpha: float = 0.7, thickness: int = 1):
    """A short tangential tick at canonical radius r along the 12 axis --
    an expected-position "gate", never a full-dial ring."""
    angle = master.angle_for_hour(HOUR)
    ca, sa = math.cos(angle), math.sin(angle)
    tx, ty = -sa, ca
    p0x, p0y = geometry.map_point(ellipse, 1.0, roll, r * ca - half_width_canon * tx, r * sa - half_width_canon * ty)
    p1x, p1y = geometry.map_point(ellipse, 1.0, roll, r * ca + half_width_canon * tx, r * sa + half_width_canon * ty)
    p0 = (int(round(p0x)), int(round(p0y)))
    p1 = (int(round(p1x)), int(round(p1y)))
    rr2._blend_line(img, p0, p1, color, thickness=thickness, alpha=alpha)


def _band(img: np.ndarray, ellipse, roll: float, r_lo: float, r_hi: float, color, alpha: float = 0.25):
    """A subtle short arc band (not a full ring) spanning a small
    tangential width at 12, between r_lo and r_hi -- the expected-genuine
    range, shown only where statistically supported."""
    angle = master.angle_for_hour(HOUR)
    ca, sa = math.cos(angle), math.sin(angle)
    tx, ty = -sa, ca
    half_w = 0.05
    pts = []
    for r in (r_lo, r_hi):
        x, y = geometry.map_point(ellipse, 1.0, roll, r * ca - half_w * tx, r * sa - half_w * ty)
        pts.append((int(round(x)), int(round(y))))
    for r in (r_hi, r_lo):
        x, y = geometry.map_point(ellipse, 1.0, roll, r * ca + half_w * tx, r * sa + half_w * ty)
        pts.append((int(round(x)), int(round(y))))
    overlay = img.copy()
    cv2.fillPoly(overlay, [np.array(pts, dtype=np.int32)], color)
    cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0, dst=img)


def _observed_marker(img: np.ndarray, xy, color, alpha: float = 0.85):
    p = (int(round(xy[0])), int(round(xy[1])))
    overlay = img.copy()
    cv2.drawMarker(overlay, p, color, cv2.MARKER_CROSS, 8, 1, cv2.LINE_AA)
    cv2.addWeighted(overlay, alpha, img, 1 - alpha, 0, dst=img)


def _structural_layer(img: np.ndarray, geo: rv2.ReticleGeometryV2):
    """The frozen v2 structural layer, reused unchanged in content but
    rendered more subdued (lower alpha) so the local 12 proportion layer
    reads as the primary content at this marker."""
    rr2._blend_polyline(img, geo.channel_inner, rr2.CYAN, thickness=1, alpha=0.45 * STRUCTURAL_ALPHA_SCALE)
    rr2._blend_polyline(img, geo.channel_outer, rr2.CYAN, thickness=1, alpha=0.45 * STRUCTURAL_ALPHA_SCALE)
    strong = {12, 3, 6, 9}
    for hour, segs in geo.axis_segments:
        a = (0.4 + 0.15 if hour in strong else 0.4) * STRUCTURAL_ALPHA_SCALE
        for p0, p1 in segs:
            rr2._blend_line(img, p0, p1, rr2.CYAN, thickness=1, alpha=a)
    for which in ("inner", "outer"):
        ring = geo.corridor_rings.get(which)
        if ring:
            rr2._blend_polyline(img, ring, rr2.GOLD, thickness=1, alpha=0.4 * STRUCTURAL_ALPHA_SCALE)
    centre_ring = geo.corridor_rings.get("centre")
    if centre_ring:
        rr2._blend_polyline(img, centre_ring, rr2.GOLD, thickness=1, alpha=0.34 * STRUCTURAL_ALPHA_SCALE, dashed=True)
    rr2._centre_target(img, geo.centre_xy)


def _local_proportion_layer(img: np.ndarray, ellipse, roll: float, gpf_result: GmtProportionalResult,
                             profile: dict, show_band: bool):
    core = {f["name"]: f for f in profile["selected_core_features"]}
    apex_med = core["apex_r_simple"]["calibration_median"]
    base_med = core["base_r_simple"]["calibration_median"]
    centre_med = 0.719  # not in the core set (secondary/redundant, see profile) -- shown for the centre gate only

    # Expected gates.
    _bracket(img, ellipse, roll, apex_med, EXPECTED, alpha=0.75)
    _bracket(img, ellipse, roll, base_med, EXPECTED, alpha=0.75)
    p_centre_expected = _axis_point(ellipse, roll, centre_med)
    rr2._blend_circle(img, p_centre_expected, 4, EXPECTED, thickness=1, alpha=0.6)

    if show_band:
        lo, hi = core["apex_r_simple"]["useful_central_range"]
        _band(img, ellipse, roll, lo, hi, EXPECTED, alpha=0.18)
        lo, hi = core["base_r_simple"]["useful_central_range"]
        _band(img, ellipse, roll, lo, hi, EXPECTED, alpha=0.18)

    # Local base-to-minute-track bracket.
    _bracket(img, ellipse, roll, master.MINUTE_TRACK_R, rr2.CYAN, half_width_canon=0.06, alpha=0.5)

    diag = gpf_result.diagnostics
    apex_xy = diag.get("apex_xy_px")
    base_xy = diag.get("base_centre_xy_px")
    centroid_xy = diag.get("centroid_xy_px")
    if apex_xy is not None:
        _observed_marker(img, apex_xy, OBSERVED)
    if base_xy is not None:
        _observed_marker(img, base_xy, OBSERVED)
    if centroid_xy is not None:
        _observed_marker(img, centroid_xy, OBSERVED)
    if apex_xy is not None and base_xy is not None:
        p0 = (int(round(apex_xy[0])), int(round(apex_xy[1])))
        p1 = (int(round(base_xy[0])), int(round(base_xy[1])))
        rr2._blend_line(img, p0, p1, OBSERVED, thickness=1, alpha=0.6)


def user_mode(bgr: np.ndarray, ellipse, roll: float, geo: rv2.ReticleGeometryV2,
              gpf_result: GmtProportionalResult, profile: dict) -> np.ndarray:
    out = bgr.copy()
    _structural_layer(out, geo)
    if gpf_result.diagnostics.get("triangle_segmented"):
        _local_proportion_layer(out, ellipse, roll, gpf_result, profile, show_band=True)
    return out


def diagnostic_mode(bgr: np.ndarray, ellipse, roll: float, geo: rv2.ReticleGeometryV2,
                     gpf_result: GmtProportionalResult, profile: dict) -> np.ndarray:
    return user_mode(bgr, ellipse, roll, geo, gpf_result, profile)


def diagnostic_panel(gpf_result: GmtProportionalResult, profile: dict, width: int = 1000) -> np.ndarray:
    diag = gpf_result.diagnostics
    feat = gpf_result.features
    lines = [
        f"tilt_deg={diag.get('tilt_deg'):.2f}  dial_radius_px={diag.get('dial_radius_px'):.1f}"
        if diag.get("tilt_deg") is not None else "tilt_deg=n/a",
        f"round markers segmented: {diag.get('n_round_segmented')}/8  hours={diag.get('round_hours_contributing')}",
        f"corridor suppressed: {diag.get('corridor_suppressed_reason') or 'no'}",
        f"triangle segmented: {diag.get('triangle_segmented')}  confidence={diag.get('triangle_confidence')}  "
        f"solidity={diag.get('triangle_solidity')}  axis_agreement_deg={diag.get('triangle_axis_agreement_deg')}",
        f"projective fit available: {diag.get('projective_correspondences_available')}"
        + (f"  suppressed: {diag.get('projective_suppressed_reason')}" if not diag.get("projective_correspondences_available") else ""),
    ]
    pf = diag.get("projective_fit")
    if pf:
        lines.append(f"projective fit: a={pf['a']:.4f} c={pf['c']:.4f} "
                      f"max_abs_residual_canon={pf['max_abs_residual_canon']:.5f} condition_ok={pf['condition_ok']}")
    lines.append("")
    for f in profile["selected_core_features"]:
        name = f["name"]
        obs = feat.get(name)
        med = f["calibration_median"]
        lo, hi = f["useful_central_range"]
        if obs is None:
            lines.append(f"{name}: OBSERVED=n/a (suppressed)  calibration_median={med:.4f}  range=[{lo:.4f},{hi:.4f}]")
            continue
        diff = obs - med
        status = "within central range" if lo <= obs <= hi else "OUTSIDE central range (n=2 watches -- illustrative only)"
        lines.append(f"{name}: observed={obs:.4f}  median={med:.4f}  range=[{lo:.4f},{hi:.4f}]  "
                      f"signed_diff={diff:+.4f}  {status}")

    line_h = 22
    height = line_h * (len(lines) + 1) + 16
    panel = np.full((height, width, 3), 255, dtype=np.uint8)
    y = 22
    for line in lines:
        cv2.putText(panel, line, (10, y), cv2.FONT_HERSHEY_SIMPLEX, 0.42, (20, 20, 20), 1, cv2.LINE_AA)
        y += line_h
    return panel
