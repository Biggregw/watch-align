"""Stage 3 human-facing overlay for the GMT 12-triangle
(experiment/gmt-proportional-geometry-v1, phase 3).

Reuses reticle_v2_render's structural palette and gmt_proportional_
render's drawing primitives unchanged (_axis_point/_bracket/_band/
_observed_marker/_structural_layer). What's new here is that this
overlay is DRIVEN by the frozen Stage 3 profile's "role" and
"normalisation" fields rather than hardcoding apex_r_simple/base_r_simple
by name, so it works whether a given radial feature was frozen in
simple (affine-only) or projective (perspective-corrected) form -- the
hybrid per-feature normalisation policy means centre/base may be
projective while apex stays simple.

For a projective-normalised feature, the frozen calibration_median is a
TRUE (projective-corrected) canonical radius. Rendering an expected gate
for it on one specific photo requires that photo's OWN fitted
projective a/c (each photo's round-marker corridor fits its own a/c),
via projective_radial.apply_projective_1d -- the forward direction of
the same fit gmt_proportional_features.py already uses to correct
apex/centre/base_r into their projective form. This never feeds back
into any measurement; it only answers "where would this frozen radius
appear in THIS photo, given this photo's own projective fit".

No red/green, no PASS/FAIL, no authenticity wording -- expected-vs-
observed geometry only, exactly as Stage 1.
"""
from __future__ import annotations

import math
from typing import Optional

import cv2
import numpy as np

import geometry
import master
import projective_radial as pr
import reticle_v2 as rv2
import reticle_v2_render as rr2
from gmt_proportional_features import GmtProportionalResult
from gmt_proportional_render import (
    EXPECTED, OBSERVED, HOUR,
    _axis_point, _bracket, _band, _observed_marker, _structural_layer,
)


def _expected_r_affine(ellipse, roll: float, feature: dict, diag: dict) -> Optional[float]:
    """The affine/canonical r to feed into _axis_point/_bracket for this
    frozen feature's expected position on THIS photo."""
    r_star = feature["calibration_median"]
    if feature["normalisation"] == "simple":
        return r_star
    pf = diag.get("projective_fit")
    if not pf:
        return None
    fit = pr.ProjectiveFit(a=pf["a"], c=pf["c"], residuals={}, max_abs_residual=0.0,
                            condition_ok=pf["condition_ok"], correspondences={})
    return pr.apply_projective_1d(fit, r_star)


def _range_to_affine(ellipse, roll: float, feature: dict, diag: dict, lo_hi) -> Optional[tuple]:
    lo, hi = lo_hi
    if feature["normalisation"] == "simple":
        return (lo, hi)
    pf = diag.get("projective_fit")
    if not pf:
        return None
    fit = pr.ProjectiveFit(a=pf["a"], c=pf["c"], residuals={}, max_abs_residual=0.0,
                            condition_ok=pf["condition_ok"], correspondences={})
    t_lo, t_hi = pr.apply_projective_1d(fit, lo), pr.apply_projective_1d(fit, hi)
    if t_lo is None or t_hi is None:
        return None
    return (min(t_lo, t_hi), max(t_lo, t_hi))


def _extended_axis_line(img: np.ndarray, ellipse, roll: float, apex_xy, base_xy,
                         centre_xy, color, alpha: float = 0.55):
    """Draw the OBSERVED apex-base symmetry line extended a short way
    past the base toward (and a little beyond) the dial centre, so a
    viewer can see directly whether it passes through the independently
    marked centre target -- the visual form of axis_incidence_canonical.
    Never extended using the marker's own position as a reference for
    anything else; this is display only."""
    ax, ay = apex_xy
    bx, by = base_xy
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy)
    if length < 1e-6:
        return
    ux, uy = dx / length, dy / length
    # extend from a bit before the apex to a bit past the base, roughly
    # spanning toward the dial centre for visual comparison.
    cx, cy = centre_xy
    dist_to_centre = math.hypot(cx - bx, cy - by)
    extend = min(dist_to_centre * 1.15, length * 2.5)
    p0 = (int(round(ax - ux * length * 0.15)), int(round(ay - uy * length * 0.15)))
    p1 = (int(round(bx + ux * extend)), int(round(by + uy * extend)))
    rr2._blend_line(img, p0, p1, color, thickness=1, alpha=alpha)


def _stage3_local_layer(img: np.ndarray, ellipse, roll: float, gpf_result: GmtProportionalResult,
                         profile: dict, show_band: bool):
    diag = gpf_result.diagnostics
    by_role = {f["role"]: f for f in profile["selected_core_features"] if f["role"] in
               ("apex_radial", "centre_radial", "base_radial")}

    for role in ("apex_radial", "centre_radial", "base_radial"):
        feature = by_role.get(role)
        if feature is None:
            continue
        r_exp = _expected_r_affine(ellipse, roll, feature, diag)
        if r_exp is None:
            continue
        if role == "centre_radial":
            p = _axis_point(ellipse, roll, r_exp)
            rr2._blend_circle(img, p, 4, EXPECTED, thickness=1, alpha=0.6)
        else:
            _bracket(img, ellipse, roll, r_exp, EXPECTED, alpha=0.75)
        if show_band:
            rng = _range_to_affine(ellipse, roll, feature, diag, feature["useful_central_range"])
            if rng is not None:
                _band(img, ellipse, roll, rng[0], rng[1], EXPECTED, alpha=0.18)

    # Local base-to-minute-track bracket (unchanged reference cue).
    _bracket(img, ellipse, roll, master.MINUTE_TRACK_R, rr2.CYAN, half_width_canon=0.06, alpha=0.5)

    apex_xy = diag.get("apex_xy_px")
    base_xy = diag.get("base_centre_xy_px")
    centroid_xy = diag.get("centroid_xy_px")
    centre_xy = (float(ellipse.cx), float(ellipse.cy))
    if apex_xy is not None:
        _observed_marker(img, apex_xy, OBSERVED)
    if base_xy is not None:
        _observed_marker(img, base_xy, OBSERVED)
    if centroid_xy is not None:
        _observed_marker(img, centroid_xy, OBSERVED)
    if apex_xy is not None and base_xy is not None:
        _extended_axis_line(img, ellipse, roll, apex_xy, base_xy, centre_xy, OBSERVED)


def user_mode(bgr: np.ndarray, ellipse, roll: float, geo: rv2.ReticleGeometryV2,
              gpf_result: GmtProportionalResult, profile: dict) -> np.ndarray:
    out = bgr.copy()
    _structural_layer(out, geo)
    if gpf_result.diagnostics.get("triangle_segmented"):
        _stage3_local_layer(out, ellipse, roll, gpf_result, profile, show_band=True)
    return out


def diagnostic_mode(bgr: np.ndarray, ellipse, roll: float, geo: rv2.ReticleGeometryV2,
                     gpf_result: GmtProportionalResult, profile: dict) -> np.ndarray:
    return user_mode(bgr, ellipse, roll, geo, gpf_result, profile)


def _verdict(obs: float, lo: float, hi: float, med: float, mad: float) -> str:
    if lo <= obs <= hi:
        # inside the central range -- distinguish "near median" from "toward an edge"
        span_lo, span_hi = med - lo, hi - med
        frac = ((obs - lo) / span_lo) if obs <= med and span_lo > 1e-9 else \
               ((hi - obs) / span_hi) if span_hi > 1e-9 else 1.0
        return "consistent with the central calibration distribution" if frac > 0.35 else \
               "toward an edge of the central calibration distribution"
    return "outside the central calibration distribution"


def diagnostic_panel(gpf_result: GmtProportionalResult, profile: dict, label: str,
                      width: int = 1050) -> np.ndarray:
    diag = gpf_result.diagnostics
    feat = gpf_result.features
    lines = [
        f"[{label}] Stage 3 -- GMT 12 triangle, hybrid per-feature normalisation, <=10deg primary profile",
        f"tilt_deg={diag.get('tilt_deg'):.2f}" if diag.get("tilt_deg") is not None else "tilt_deg=n/a",
        f"triangle segmented: {diag.get('triangle_segmented')}  projective fit available: "
        f"{diag.get('projective_correspondences_available')}",
        "",
    ]
    for f in profile["selected_core_features"]:
        name = f["name"]
        obs = feat.get(name)
        med = f["calibration_median"]
        lo, hi = f["useful_central_range"]
        if obs is None:
            lines.append(f"{name} [{f['role']}/{f['normalisation']}]: OBSERVED=n/a  "
                          f"calibration_median={med:.4f}  range=[{lo:.4f},{hi:.4f}]")
            continue
        diff = obs - med
        verdict = _verdict(obs, lo, hi, med, f["robust_spread_mad"])
        lines.append(f"{name} [{f['role']}/{f['normalisation']}]: observed={obs:.4f}  median={med:.4f}  "
                      f"range=[{lo:.4f},{hi:.4f}]  signed_diff={diff:+.4f}  -> {verdict}")
    lines.append("")
    for f in profile.get("secondary_reported_not_core", []):
        name = f["name"] if isinstance(f, dict) else f
        if isinstance(f, dict):
            obs = feat.get(name)
            lines.append(f"(secondary, not part of the frozen verdict set) {name}: "
                          f"observed={obs:.4f}" if obs is not None else
                          f"(secondary) {name}: observed=n/a")

    line_h = 22
    height = line_h * (len(lines) + 1) + 16
    panel = np.full((height, width, 3), 255, dtype=np.uint8)
    y = 22
    for line in lines:
        cv2.putText(panel, line, (10, y), cv2.FONT_HERSHEY_SIMPLEX, 0.42, (20, 20, 20), 1, cv2.LINE_AA)
        y += line_h
    return panel
