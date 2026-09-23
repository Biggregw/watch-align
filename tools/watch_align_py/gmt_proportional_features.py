"""12-triangle proportional/projective feature computation
(experiment/gmt-proportional-geometry-v1).

Orchestrates, in this fixed order, for one photo:

1. The FROZEN structural geometry (reticle_v2.build_geometry, unchanged)
   -- dial centre, minute-track channel, 12 gapped axes, and the round-
   marker peer corridor (inner/centre/outer conics fit ONLY from the 8
   round markers: 1,2,4,5,7,8,10,11). 6, 9, 12 and the date area
   contribute nothing to this step by construction (reticle_v2.py never
   segments them).
2. Independent 12-triangle segmentation (triangle_measurement.py) --
   does not use the round-marker corridor or any triangle-specific
   canonical constant to decide where to look, only a generic marker-
   band ROI.
3. The local 1D projective radial mapping along the 12 axis
   (projective_radial.py), fit ONLY from the round-marker corridor's
   inner/centre/outer conic intersections with the 12 axis -- the
   triangle contributes nothing to this fit either.
4. Both a simple affine-normalised ("simple") and a projective-corrected
   ("projective") radial coordinate for every triangle point, so the two
   representations can be compared per photo and in aggregate.

If any upstream step is unavailable (round-marker corridor suppressed,
projective fit ill-conditioned, triangle segmentation failed), the
features that depend on it are omitted (None), never fabricated, and the
reason is recorded in `diagnostics`.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Dict, Optional

import numpy as np

import axis_coords
import master
import projective_radial as pr
import reticle_v2 as rv2
import triangle_measurement as tm

HOUR = 12


@dataclass
class GmtProportionalResult:
    features: Dict[str, Optional[float]] = field(default_factory=dict)
    diagnostics: Dict[str, object] = field(default_factory=dict)


def _canon(ellipse, roll, hour, xy):
    return axis_coords.canonical_axis_components(ellipse, roll, hour, xy[0], xy[1])


def compute(gray: np.ndarray, ellipse, roll: float, dial_radius_px: float,
            tilt_deg: float) -> GmtProportionalResult:
    out = GmtProportionalResult()
    diag = out.diagnostics
    feat = out.features

    diag["tilt_deg"] = tilt_deg
    diag["dial_radius_px"] = dial_radius_px

    geo = rv2.build_geometry(gray, ellipse, roll, dial_radius_px)
    diag["n_round_segmented"] = geo.n_round_segmented
    diag["round_hours_contributing"] = sorted(geo.round_observations.keys())
    diag["corridor_suppressed_reason"] = geo.corridor_suppressed_reason
    diag["corridor_fit_residuals_px"] = {
        which: (s.median_residual_px if s else None) for which, s in geo.corridor_fit_summary.items()
    }

    tri = tm.segment_triangle_independent(gray, ellipse, roll, dial_radius_px, hour=HOUR)
    diag["triangle_segmented"] = tri is not None
    if tri is None:
        diag["triangle_suppressed_reason"] = "independent triangle segmentation failed (ROI blob absent/implausible)"
        return out
    diag["triangle_confidence"] = tri.confidence
    diag["triangle_solidity"] = tri.solidity
    diag["triangle_axis_agreement_deg"] = tri.axis_agreement_deg
    diag["triangle_n_contour_points"] = tri.n_contour_points

    # Canonical (affine-normalised, "simple") axis coordinates for every
    # measured triangle point.
    apex_r, apex_t = _canon(ellipse, roll, HOUR, tri.apex_xy)
    base_r, base_t = _canon(ellipse, roll, HOUR, tri.base_centre_xy)
    centroid_r, centroid_t = _canon(ellipse, roll, HOUR, tri.centroid_xy)
    left_r, left_t = _canon(ellipse, roll, HOUR, tri.base_left_xy)
    right_r, right_t = _canon(ellipse, roll, HOUR, tri.base_right_xy)

    feat["apex_r_simple"] = apex_r
    feat["centre_r_simple"] = centroid_r
    feat["base_r_simple"] = base_r
    feat["apex_to_base_span_simple"] = base_r - apex_r
    feat["base_to_minute_track_gap_simple"] = master.MINUTE_TRACK_R - base_r
    feat["centre_to_minute_track_gap_simple"] = master.MINUTE_TRACK_R - centroid_r

    # Canonical 2D (radial, tangential) geometry -- affine-normalised
    # only; the projective correction below is explicitly a 1D radial-
    # axis construction and is not extended to off-axis 2D distances.
    def dist2(pr_, pt_):
        return math.hypot(pr_[0] - pt_[0], pr_[1] - pt_[1])

    apex_pt, base_pt, left_pt, right_pt = (apex_r, apex_t), (base_r, base_t), (left_r, left_t), (right_r, right_t)
    height_canon = dist2(apex_pt, base_pt)
    base_width_canon = dist2(left_pt, right_pt)
    feat["triangle_height_over_minute_track_r"] = height_canon / master.MINUTE_TRACK_R if height_canon else None
    feat["base_width_over_height"] = (base_width_canon / height_canon) if height_canon > 1e-9 else None
    left_half = dist2(left_pt, base_pt)
    right_half = dist2(right_pt, base_pt)
    feat["base_half_width_symmetry"] = (
        (left_half - right_half) / base_width_canon if base_width_canon > 1e-9 else None
    )
    span = base_r - apex_r
    feat["centroid_position_within_span"] = ((centroid_r - apex_r) / span) if abs(span) > 1e-9 else None

    # Angular features, all exact within the affine model (no projective
    # correction defined for tangential/angular quantities in this design
    # -- see module docstring).
    feat["centroid_tangential_offset_canonical"] = centroid_t
    feat["centroid_angular_offset_deg"] = math.degrees(math.atan2(centroid_t, centroid_r)) if centroid_r else None
    sym_dr, sym_dt = base_r - apex_r, base_t - apex_t
    feat["symmetry_axis_angular_deviation_deg"] = math.degrees(math.atan2(sym_dt, sym_dr))
    base_dr, base_dt = right_r - left_r, right_t - left_t
    # expected base-line direction is purely tangential (dr=0); deviation
    # from that:
    feat["base_line_angular_deviation_deg"] = math.degrees(math.atan2(base_dr, base_dt)) if (base_dr or base_dt) else None

    diag["apex_xy_px"] = tri.apex_xy
    diag["base_centre_xy_px"] = tri.base_centre_xy
    diag["base_left_xy_px"] = tri.base_left_xy
    diag["base_right_xy_px"] = tri.base_right_xy
    diag["centroid_xy_px"] = tri.centroid_xy

    # Projective radial correction, fit from the round-marker corridor
    # only.
    corr = pr.corridor_axis_correspondences(geo, hour=HOUR)
    diag["projective_correspondences_available"] = corr is not None
    if corr is None:
        diag["projective_suppressed_reason"] = (
            geo.corridor_suppressed_reason or "round-marker corridor conic(s) unavailable along the 12 axis"
        )
        return out
    fit = pr.fit_projective_1d(corr)
    diag["projective_fit"] = None if fit is None else {
        "a": fit.a, "c": fit.c, "max_abs_residual_canon": fit.max_abs_residual,
        "condition_ok": fit.condition_ok, "residuals_canon": fit.residuals,
    }
    if fit is None or not fit.condition_ok:
        diag["projective_suppressed_reason"] = (
            "projective fit failed" if fit is None else "projective fit ill-conditioned near observed t range"
        )
        return out

    apex_r_proj = pr.invert_projective_1d(fit, apex_r)
    centre_r_proj = pr.invert_projective_1d(fit, centroid_r)
    base_r_proj = pr.invert_projective_1d(fit, base_r)
    feat["apex_r_projective"] = apex_r_proj
    feat["centre_r_projective"] = centre_r_proj
    feat["base_r_projective"] = base_r_proj
    if apex_r_proj is not None and base_r_proj is not None:
        feat["apex_to_base_span_projective"] = base_r_proj - apex_r_proj
    if base_r_proj is not None:
        feat["base_to_minute_track_gap_projective"] = master.MINUTE_TRACK_R - base_r_proj
    if centre_r_proj is not None:
        feat["centre_to_minute_track_gap_projective"] = master.MINUTE_TRACK_R - centre_r_proj

    # Relational: triangle point vs. its own-shape peer round-marker
    # corridor radius, in the SAME projective coordinate the corridor was
    # fit in (non-circular: the fit used only round markers; here it is
    # applied to the independently-segmented triangle).
    if apex_r_proj is not None:
        feat["apex_r_projective_over_round_inner"] = apex_r_proj / pr.ROUND_INNER_CANON
    if centre_r_proj is not None:
        feat["centre_r_projective_over_round_centre"] = centre_r_proj / pr.ROUND_CENTER_CANON
    if base_r_proj is not None:
        feat["base_r_projective_over_round_outer"] = base_r_proj / pr.ROUND_OUTER_CANON

    return out
