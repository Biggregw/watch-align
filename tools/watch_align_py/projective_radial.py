"""Local 1D projective radial mapping (experiment/gmt-proportional-
geometry-v1), per the shared research knowledge base's "local 1D
projective radial coordinate" section.

A homography restricted to one physical radial line is a 1D projective
transformation. Along the 12 axis, the round-marker peer corridor's
inner/centre/outer conics (already fit in reticle_v2.py from the 8 round
markers only -- 12 contributes nothing) supply three known-canonical-
radius correspondences:

    ROUND_INNER_CANON  = 0.681   (master.ROUND_CENTER_R - master.ROUND_OUTER_R)
    ROUND_CENTER_CANON = 0.751   (master.ROUND_CENTER_R)
    ROUND_OUTER_CANON  = 0.821   (master.ROUND_CENTER_R + master.ROUND_OUTER_R)

With the dial centre constrained as r=0 -> t=0, fit:

    t(r) = a*r / (c*r + 1)

then invert it to express an observed 12-triangle point's affine-
normalised axis coordinate (t, from axis_coords.canonical_axis_components)
in projective-corrected canonical radial coordinates. The marker under
test contributes nothing to this fit -- only the three round-marker
corridor correspondences do.
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple

import numpy as np

import axis_coords
import marker_consensus as mc
import reticle_v2 as rv2

ROUND_INNER_CANON = 0.681
ROUND_CENTER_CANON = 0.751
ROUND_OUTER_CANON = 0.821
_CANON_BY_WHICH = {"inner": ROUND_INNER_CANON, "centre": ROUND_CENTER_CANON, "outer": ROUND_OUTER_CANON}


@dataclass
class ProjectiveFit:
    a: float
    c: float
    residuals: Dict[str, float]       # which -> |t_fit(r) - t_observed|, canonical units
    max_abs_residual: float
    condition_ok: bool                # False if a - c*t is ever near-zero across the fit domain
    correspondences: Dict[str, Tuple[float, float]]  # which -> (r_known, t_observed)


def corridor_axis_correspondences(gray_geo: rv2.ReticleGeometryV2, hour: int = 12
                                   ) -> Optional[Dict[str, Tuple[float, float]]]:
    """For each of inner/centre/outer, intersect that round-marker peer
    conic with the nominal `hour`-axis ray (image-space direction
    established independently of any marker's own position) and express
    the intersection in canonical axis coordinates. None if the corridor
    itself was suppressed (insufficient round-marker peer evidence)."""
    if gray_geo.corridor_suppressed_reason is not None:
        return None
    ellipse, roll = gray_geo.ellipse, gray_geo.roll
    angle_img = axis_coords.nominal_axis_image_angle(ellipse, roll, hour)
    center = (ellipse.cx, ellipse.cy)
    out: Dict[str, Tuple[float, float]] = {}
    for which, r_known in _CANON_BY_WHICH.items():
        Q = gray_geo.corridor_conics.get(which)
        if Q is None:
            return None
        # expected_dist disambiguates the two ray/conic roots; the
        # corridor ring's own sampled points give a good in-pixel estimate.
        ring = gray_geo.corridor_rings.get(which) or []
        if not ring:
            return None
        expected_dist_px = float(np.median([math.hypot(px - center[0], py - center[1]) for px, py in ring]))
        pt = mc.conic_ray_point(Q, center, angle_img, expected_dist_px)
        if pt is None:
            return None
        t_radial, t_tangential = axis_coords.canonical_axis_components(ellipse, roll, hour, pt[0], pt[1])
        out[which] = (r_known, t_radial)
    return out


def fit_projective_1d(correspondences: Dict[str, Tuple[float, float]]) -> Optional[ProjectiveFit]:
    """Least-squares fit of t(r) = a*r/(c*r+1) from the 3 (r_known,
    t_observed) correspondences, linearised as a*r - c*(r*t) = t."""
    rs = np.array([r for r, t in correspondences.values()])
    ts = np.array([t for r, t in correspondences.values()])
    if len(rs) < 2:
        return None
    A = np.stack([rs, -rs * ts], axis=1)
    try:
        sol, _, rank, _ = np.linalg.lstsq(A, ts, rcond=None)
    except np.linalg.LinAlgError:
        return None
    if rank < 2:
        return None
    a, c = float(sol[0]), float(sol[1])

    residuals = {}
    for which, (r, t_obs) in correspondences.items():
        denom = c * r + 1.0
        t_fit = (a * r / denom) if abs(denom) > 1e-9 else math.inf
        residuals[which] = abs(t_fit - t_obs)
    max_resid = max(residuals.values()) if residuals else math.inf

    # Conditioning: the inverse r = t / (a - c*t) is unusable if (a - c*t)
    # approaches 0 anywhere near the observed t range -- check across the
    # correspondences' own t span with margin, not just at the 3 points.
    t_lo = min(t for _, t in correspondences.values()) * 0.5
    t_hi = max(t for _, t in correspondences.values()) * 1.5
    probe = np.linspace(min(0.0, t_lo), t_hi, 50)
    denom_vals = a - c * probe
    condition_ok = bool(np.all(np.abs(denom_vals) > 1e-6))

    return ProjectiveFit(a=a, c=c, residuals=residuals, max_abs_residual=max_resid,
                          condition_ok=condition_ok, correspondences=correspondences)


def invert_projective_1d(fit: ProjectiveFit, t_observed: float) -> Optional[float]:
    """r such that t(r) = t_observed, i.e. r = t / (a - c*t). None if the
    fit is ill-conditioned at this specific t (denominator near zero)."""
    denom = fit.a - fit.c * t_observed
    if abs(denom) < 1e-6:
        return None
    return t_observed / denom
