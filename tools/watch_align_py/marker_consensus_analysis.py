"""Leave-one-out peer-consensus analysis, 30-degree angular system, shape-
specific offsets, local minute-track clearance, and the three-state
warning model, built on top of marker_consensus.py's segmentation and
conic-fitting primitives. See marker_consensus.py's module docstring for
the architecture rationale.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

import numpy as np

import geometry
import marker_consensus as mc
import master
import multi_radius_pose_solver as mrps
from geometry import RotatedRect


def _robust_stats(values: List[float]) -> Tuple[float, float]:
    """(median, MAD-based robust std) of a small sample. With only 6-8 peers
    this is noisy by construction -- callers must not over-interpret it,
    and Part 8/9 explicitly forbid deriving production thresholds from it."""
    if not values:
        return math.nan, math.nan
    arr = np.array(values, dtype=np.float64)
    med = float(np.median(arr))
    mad = float(np.median(np.abs(arr - med)))
    robust_std = 1.4826 * mad
    return med, robust_std


def _point_for(obs: mc.MarkerObservation, which: str) -> Tuple[float, float]:
    if which == "centre":
        return obs.centroid_x, obs.centroid_y
    if which == "outer":
        return obs.outer_x, obs.outer_y
    if which == "inner":
        return obs.inner_x, obs.inner_y
    raise ValueError(which)


def _nominal_direction(ellipse: RotatedRect, roll: float, hour: int) -> Tuple[float, float]:
    """Image-space unit direction corresponding to the nominal (minute-
    track-derived) hour angle -- NOT the marker's own detected position.
    Uses the affine ellipse basis only for DIRECTION (established this
    session as reliable for angle); the peer conic supplies the distance.
    This is what keeps leave-one-out honest: the ray this marker is
    evaluated along never depends on where it was actually detected."""
    angle = master.angle_for_hour(hour)
    center = geometry.map_point(ellipse, 1.0, roll, 0.0, 0.0)
    edge = geometry.map_point(ellipse, 1.0, roll, math.cos(angle), math.sin(angle))
    dx, dy = edge[0] - center[0], edge[1] - center[1]
    norm = math.hypot(dx, dy)
    if norm < 1e-9:
        return 1.0, 0.0
    return dx / norm, dy / norm


# --------------------------------------------------------------------------
# Part 1/2: round-marker conic consensus + leave-one-out
# --------------------------------------------------------------------------

@dataclass
class EnvelopeLOOResult:
    hour: int
    which: str
    fit_ok: bool
    n_peers: int = 0
    predicted_xy: Optional[Tuple[float, float]] = None
    actual_xy: Optional[Tuple[float, float]] = None
    residual_px: float = math.nan
    radial_component_px: float = math.nan
    tangential_component_px: float = math.nan


def leave_one_out_round_envelope(observations: Dict[int, mc.MarkerObservation],
                                  ellipse: RotatedRect, roll: float, which: str
                                  ) -> Dict[int, EnvelopeLOOResult]:
    """which in {"centre","outer","inner"}. For each round marker present,
    fits the conic from its 7 (or fewer, if some are unmeasured) peers
    only, then evaluates that peer-only conic along the NOMINAL hour
    direction (never the marker's own detected angle) to get a predicted
    position, and compares to what was actually detected."""
    center = (ellipse.cx, ellipse.cy)
    hours = [h for h in mc.ROUND_HOURS if h in observations]
    results: Dict[int, EnvelopeLOOResult] = {}
    for h in hours:
        peers = [observations[p] for p in hours if p != h]
        if len(peers) < mc.MIN_PEERS_FOR_LOO:
            results[h] = EnvelopeLOOResult(h, which, fit_ok=False, n_peers=len(peers))
            continue
        pts = np.array([_point_for(p, which) for p in peers])
        Q = mc.fit_conic_robust(pts)
        if Q is None:
            results[h] = EnvelopeLOOResult(h, which, fit_ok=False, n_peers=len(peers))
            continue
        actual = _point_for(observations[h], which)
        u, v = _nominal_direction(ellipse, roll, h)
        angle = math.atan2(v, u)
        expected_dist = math.hypot(actual[0] - center[0], actual[1] - center[1])
        pred = mc.conic_ray_point(Q, center, angle, expected_dist)
        if pred is None:
            results[h] = EnvelopeLOOResult(h, which, fit_ok=False, n_peers=len(peers))
            continue
        dxv, dyv = actual[0] - pred[0], actual[1] - pred[1]
        radial = dxv * u + dyv * v
        tangential = -dxv * v + dyv * u
        results[h] = EnvelopeLOOResult(
            h, which, fit_ok=True, n_peers=len(peers), predicted_xy=pred, actual_xy=actual,
            residual_px=math.hypot(dxv, dyv), radial_component_px=radial, tangential_component_px=tangential,
        )
    return results


@dataclass
class ConicFitSummary:
    which: str
    n_points: int
    in_sample_residuals_px: Dict[int, float]
    median_residual_px: float
    robust_std_px: float


def fit_full_round_conic(observations: Dict[int, mc.MarkerObservation], which: str) -> Optional[ConicFitSummary]:
    """Full (non-leave-one-out) conic fit from ALL available round markers,
    for reporting in-sample residual/spread -- diagnostic only, never used
    to judge an individual marker (that's what leave-one-out is for)."""
    hours = [h for h in mc.ROUND_HOURS if h in observations]
    if len(hours) < mc.MIN_PEERS_FOR_CONIC:
        return None
    pts = np.array([_point_for(observations[h], which) for h in hours])
    Q = mc.fit_conic(pts)
    if Q is None:
        return None
    resids = {h: mc.sampson_residual(Q, *_point_for(observations[h], which)) for h in hours}
    med, std = _robust_stats(list(resids.values()))
    return ConicFitSummary(which, len(hours), resids, med, std)


# --------------------------------------------------------------------------
# Part 3: 30-degree angular system
# --------------------------------------------------------------------------

@dataclass
class AngularResult:
    hour: int
    canonical_angle_deg: float
    nominal_angle_deg: float
    angular_residual_deg: float       # marker centre vs nominal hour axis, in canonical (affine-corrected) space
    principal_axis_residual_deg: float  # shape's own axis vs local radial direction; nan for round
    opposing_hour: Optional[int]
    opposing_consistency_deg: float   # nan if opposing marker unavailable


def angular_analysis(observations: Dict[int, mc.MarkerObservation], ellipse: RotatedRect, roll: float
                      ) -> Dict[int, AngularResult]:
    """Per Part 3: angle is measured in the CANONICAL (affine-corrected)
    frame via marker_consensus.inverse_map, not naively in the raw
    perspective photograph -- reusing the affine ellipse basis this
    session already established as reliable for angle (unlike radial)."""
    results: Dict[int, AngularResult] = {}
    canon_angle: Dict[int, float] = {}
    for h, obs in observations.items():
        cx, cy = mc.inverse_map(ellipse, roll, obs.centroid_x, obs.centroid_y)
        canon_angle[h] = math.degrees(math.atan2(cy, cx))

    for h, obs in observations.items():
        nominal = math.degrees(master.angle_for_hour(h))
        residual = _wrap180(canon_angle[h] - nominal)

        axis_residual = math.nan
        if obs.shape != "round" and math.isfinite(obs.principal_axis_deg):
            u, v = _nominal_direction(ellipse, roll, h)
            expected_axis = math.degrees(math.atan2(v, u))
            axis_residual = _wrap90(obs.principal_axis_deg - expected_axis)

        opposing = (h + 6 - 1) % 12 + 1
        opp_consistency = math.nan
        if opposing in canon_angle:
            # for a truly flat, undistorted dial, two markers 180deg apart
            # subtend nominal angles exactly 180deg apart too; compare how
            # far the OBSERVED pair is from 180deg, in canonical space.
            observed_sep = _wrap180(canon_angle[h] - canon_angle[opposing])
            opp_consistency = _wrap180(abs(observed_sep) - 180.0)

        results[h] = AngularResult(
            hour=h, canonical_angle_deg=canon_angle[h], nominal_angle_deg=nominal,
            angular_residual_deg=residual, principal_axis_residual_deg=axis_residual,
            opposing_hour=opposing if opposing in canon_angle else None,
            opposing_consistency_deg=opp_consistency,
        )
    return results


def _wrap180(deg: float) -> float:
    x = deg % 360.0
    if x > 180.0:
        x -= 360.0
    return x


def _wrap90(deg: float) -> float:
    x = deg % 180.0
    if x > 90.0:
        x -= 180.0
    return x


# --------------------------------------------------------------------------
# Part 4: shape-specific markers (triangle, batons) vs round-envelope conic
# --------------------------------------------------------------------------

@dataclass
class ShapeOffsetResult:
    hour: int
    shape: str
    fit_ok: bool
    expected_xy: Optional[Tuple[float, float]] = None
    actual_xy: Optional[Tuple[float, float]] = None
    residual_px: float = math.nan
    radial_component_px: float = math.nan
    tangential_component_px: float = math.nan


_SHAPE_OUTER_RATIO = {
    "triangle": mc.TRI_OUTER_CANON_R / mc.ROUND_OUTER_CANON_R,
    "baton": mc.BATON_OUTER_CANON_R / mc.ROUND_OUTER_CANON_R,
}


def shape_marker_offset_check(observations: Dict[int, mc.MarkerObservation],
                               ellipse: RotatedRect, roll: float,
                               round_outer_conic: Optional[np.ndarray]
                               ) -> Dict[int, ShapeOffsetResult]:
    """Triangle (12) and batons (6,9) are never part of the round-envelope
    conic's fitting set (Part 1 already excludes them by construction).
    Their expected outer position is derived from that conic's own local
    radial distance at the shape's nominal angle, scaled by the ratio of
    the shape's own known canonical outer radius to the round markers'
    (existing master.py constants, read-only -- not new master geometry),
    since this radial gap (~0.006-0.028 canonical units) is far smaller
    than the minute-track-to-marker gap (~0.14-0.20) that made the
    earlier affine-scaling approach fail, so a local multiplicative
    approximation should hold far better here."""
    results: Dict[int, ShapeOffsetResult] = {}
    if round_outer_conic is None:
        for h in (12, 6, 9):
            if h in observations:
                results[h] = ShapeOffsetResult(h, observations[h].shape, fit_ok=False)
        return results
    center = (ellipse.cx, ellipse.cy)
    for h in (12, 6, 9):
        obs = observations.get(h)
        if obs is None:
            continue
        u, v = _nominal_direction(ellipse, roll, h)
        angle = math.atan2(v, u)
        actual = (obs.outer_x, obs.outer_y)
        expected_dist_round = math.hypot(actual[0] - center[0], actual[1] - center[1]) / _SHAPE_OUTER_RATIO[obs.shape]
        round_pt = mc.conic_ray_point(round_outer_conic, center, angle, expected_dist_round)
        if round_pt is None:
            results[h] = ShapeOffsetResult(h, obs.shape, fit_ok=False)
            continue
        ratio = _SHAPE_OUTER_RATIO[obs.shape]
        expected = (center[0] + (round_pt[0] - center[0]) * ratio,
                    center[1] + (round_pt[1] - center[1]) * ratio)
        dxv, dyv = actual[0] - expected[0], actual[1] - expected[1]
        results[h] = ShapeOffsetResult(
            h, obs.shape, fit_ok=True, expected_xy=expected, actual_xy=actual,
            residual_px=math.hypot(dxv, dyv), radial_component_px=dxv * u + dyv * v,
            tangential_component_px=-dxv * v + dyv * u,
        )
    return results


# --------------------------------------------------------------------------
# Part 5: local minute-track clearance (supporting signal only)
# --------------------------------------------------------------------------

@dataclass
class ClearanceResult:
    hour: int
    clearance_px: float
    local_tick_spacing_px: float
    clearance_normalized: float  # clearance_px / local_tick_spacing_px


def local_minute_track_clearance(observations: Dict[int, mc.MarkerObservation],
                                  edges: np.ndarray, ellipse: RotatedRect, roll: float
                                  ) -> Dict[int, ClearanceResult]:
    corr = mrps.extract_correspondences(edges, ellipse, roll)
    ticks = [c for c in corr if c.kind == "tick"]
    if len(ticks) < 4:
        return {}
    tick_xy = np.array([[c.image_x, c.image_y] for c in ticks])
    tick_angle = np.array([math.atan2(c.canonical_y, c.canonical_x) for c in ticks])

    results: Dict[int, ClearanceResult] = {}
    for h, obs in observations.items():
        nominal = master.angle_for_hour(h)
        d = np.abs(np.angle(np.exp(1j * (tick_angle - nominal))))
        near_idx = np.argsort(d)[:6]
        if len(near_idx) < 2:
            continue
        near_pts = tick_xy[near_idx]
        outer = np.array([obs.outer_x, obs.outer_y])
        dists = np.linalg.norm(near_pts - outer, axis=1)
        clearance = float(dists.min())
        # local tick spacing: median pairwise distance among the same
        # nearby ticks, as a scale-normalising reference.
        pw = []
        for i in range(len(near_pts)):
            for j in range(i + 1, len(near_pts)):
                pw.append(float(np.linalg.norm(near_pts[i] - near_pts[j])))
        spacing = float(np.median(pw)) if pw else math.nan
        results[h] = ClearanceResult(
            hour=h, clearance_px=clearance, local_tick_spacing_px=spacing,
            clearance_normalized=(clearance / spacing if spacing and spacing > 1e-6 else math.nan),
        )
    return results


# --------------------------------------------------------------------------
# Part 9: three-state warning model
# --------------------------------------------------------------------------

@dataclass
class MarkerVerdict:
    hour: int
    state: str  # "NO_ISSUE" | "POSSIBLE_ISSUE" | "CLEAR_ANOMALY" | "INSUFFICIENT_CONFIDENCE"
    message: str
    signals: Dict[str, float] = field(default_factory=dict)  # signal name -> robust z-score


# Thresholds are conventional robust-statistics conventions (modified
# z-score, Iglewicz & Hoaglin 1993 commonly cites 3.5 as an outlier cut),
# NOT derived from this project's own tiny samples -- Part 8/9 explicitly
# forbid that. They are a starting point for further calibration, not a
# claimed-correct production threshold.
POSSIBLE_Z = 2.5
ANOMALY_Z = 4.0
MIN_CONFIDENCE = 0.35

# A z-score alone is unreliable with only 5-7 peers: found on the real
# video test (frame_02), where angular residuals of 0.81-0.94deg --
# unremarkable in absolute terms, well within the ~0.5-1.0deg normal
# angular noise this session established repeatedly on corpus/video data
# earlier -- produced z-scores of 4.4-5.5 purely because the tiny peer
# sample's own spread happened to be small. A signal below its absolute
# floor is never treated as "elevated" regardless of z-score, so small-
# sample MAD instability can no longer manufacture a warning out of
# ordinary noise. Floors come from evidence already established
# elsewhere this session, not fitted to this module's own tiny samples.
# Signals with no established normal range yet (clearance, shape-offset)
# get no floor -- flagged explicitly as calibration-pending instead.
ABSOLUTE_FLOOR = {
    "angular": 1.0,             # degrees
    "outer_envelope_loo": 1.5,  # px
}


def _z(value: float, peer_values: List[float]) -> float:
    if not math.isfinite(value) or len(peer_values) < 3:
        return math.nan
    med, std = _robust_stats(peer_values)
    if not math.isfinite(std) or std < 1e-6:
        return 0.0 if abs(value - med) < 1e-6 else math.inf
    return abs(value - med) / std


def build_verdicts(observations: Dict[int, mc.MarkerObservation],
                    outer_loo: Dict[int, EnvelopeLOOResult],
                    angular: Dict[int, AngularResult],
                    clearance: Dict[int, ClearanceResult],
                    shape_offsets: Dict[int, ShapeOffsetResult],
                    ) -> Dict[int, MarkerVerdict]:
    """Combines leave-one-out envelope residual, angular residual, and
    (as supporting evidence) local minute-track clearance into a per-
    marker three-state verdict. A marker is judged against the PEER
    DISTRIBUTION of the same signal across the other markers on the SAME
    photograph (never a fixed absolute pixel threshold, which would not
    generalise across image resolutions/dial sizes) -- consistent with
    the peer-consensus philosophy throughout this module. Requires
    agreement from more than one signal for CLEAR_ANOMALY, per Part 9."""
    verdicts: Dict[int, MarkerVerdict] = {}
    hours = sorted(set(list(outer_loo.keys()) + list(angular.keys()) + list(shape_offsets.keys())))

    outer_resid_by_hour = {h: r.residual_px for h, r in outer_loo.items() if r.fit_ok}
    angular_resid_by_hour = {h: abs(a.angular_residual_deg) for h, a in angular.items()
                              if math.isfinite(a.angular_residual_deg)}
    clearance_by_hour = {h: c.clearance_normalized for h, c in clearance.items()
                          if math.isfinite(c.clearance_normalized)}
    shape_resid_by_hour = {h: r.residual_px for h, r in shape_offsets.items() if r.fit_ok}

    def _peers_excluding(by_hour: Dict[int, float], self_hour: int) -> List[float]:
        return [v for h, v in by_hour.items() if h != self_hour]

    for h in hours:
        obs = observations.get(h)
        if obs is None or obs.confidence < MIN_CONFIDENCE:
            verdicts[h] = MarkerVerdict(h, "INSUFFICIENT_CONFIDENCE",
                                         "Segmentation confidence too low to evaluate this marker.")
            continue

        signals: Dict[str, float] = {}
        raw_values: Dict[str, float] = {}
        loo = outer_loo.get(h)
        if loo is not None and loo.fit_ok:
            peers_excl = _peers_excluding(outer_resid_by_hour, h)
            signals["outer_envelope_loo"] = _z(loo.residual_px, peers_excl)
            raw_values["outer_envelope_loo"] = loo.residual_px

        ang = angular.get(h)
        if ang is not None and math.isfinite(ang.angular_residual_deg):
            peers_excl = _peers_excluding(angular_resid_by_hour, h)
            signals["angular"] = _z(abs(ang.angular_residual_deg), peers_excl)
            raw_values["angular"] = abs(ang.angular_residual_deg)

        clr = clearance.get(h)
        if clr is not None and math.isfinite(clr.clearance_normalized):
            peers_excl = _peers_excluding(clearance_by_hour, h)
            if peers_excl:
                signals["minute_track_clearance"] = _z(clr.clearance_normalized, peers_excl)
                raw_values["minute_track_clearance"] = clr.clearance_normalized

        shp = shape_offsets.get(h)
        if shp is not None and shp.fit_ok:
            peers_excl = _peers_excluding(shape_resid_by_hour, h)
            if peers_excl:
                signals["shape_offset"] = _z(shp.residual_px, peers_excl)
                raw_values["shape_offset"] = shp.residual_px

        finite_signals = {k: v for k, v in signals.items() if math.isfinite(v)}
        if not finite_signals:
            verdicts[h] = MarkerVerdict(h, "INSUFFICIENT_CONFIDENCE",
                                         "Not enough peer data to evaluate this marker on this photograph.",
                                         signals=signals)
            continue

        # A signal counts toward elevated/anomalous only if it ALSO clears
        # its absolute floor (where one is established) -- see
        # ABSOLUTE_FLOOR's comment: this is what stops small-peer-sample
        # MAD instability from manufacturing a warning out of ordinary,
        # already-characterised measurement noise.
        countable = {k: v for k, v in finite_signals.items()
                     if raw_values.get(k, math.inf) >= ABSOLUTE_FLOOR.get(k, 0.0)}
        n_elevated = sum(1 for v in countable.values() if v >= POSSIBLE_Z)
        n_anomalous = sum(1 for v in countable.values() if v >= ANOMALY_Z)
        if countable:
            max_signal = max(countable, key=lambda k: countable[k])
            max_z = countable[max_signal]
        else:
            max_signal = max(finite_signals, key=lambda k: finite_signals[k])
            max_z = finite_signals[max_signal]

        if n_anomalous >= 2 or (n_anomalous >= 1 and n_elevated >= 2):
            state = "CLEAR_ANOMALY"
            message = (f"Marker {h}: multiple independent signals show a clear geometric "
                       f"anomaly (strongest: {max_signal}, z={max_z:.1f}). Review closely.")
        elif n_elevated >= 1:
            state = "POSSIBLE_ISSUE"
            message = (f"Possible marker {h} position issue ({max_signal} deviates from peer "
                       f"consensus, z={max_z:.1f}). Review highlighted area.")
        else:
            state = "NO_ISSUE"
            message = f"Marker {h}: consistent with peer geometry on this photograph."
        verdicts[h] = MarkerVerdict(h, state, message, signals=signals)

    return verdicts


# --------------------------------------------------------------------------
# Top-level orchestration: analyze one frame end to end
# --------------------------------------------------------------------------

@dataclass
class FrameAnalysis:
    observations: Dict[int, mc.MarkerObservation]
    round_conics: Dict[str, Optional[ConicFitSummary]]  # "centre"/"outer"/"inner" -> in-sample summary
    outer_loo: Dict[int, EnvelopeLOOResult]
    centre_loo: Dict[int, EnvelopeLOOResult]
    angular: Dict[int, AngularResult]
    clearance: Dict[int, ClearanceResult]
    shape_offsets: Dict[int, ShapeOffsetResult]
    verdicts: Dict[int, MarkerVerdict]


def analyze_frame(gray: np.ndarray, edges: np.ndarray, ellipse: RotatedRect, roll: float,
                   dial_radius_px: float) -> FrameAnalysis:
    """Full Part 1-9 pipeline for one already-acquired frame (ellipse/roll
    assumed already solved by the existing, unmodified production
    acquisition path -- this module only adds a NEW analysis layer on top,
    it does not re-derive pose)."""
    observations: Dict[int, mc.MarkerObservation] = {}
    for h in mc.ALL_MARKER_HOURS:
        obs = mc.segment_marker(gray, ellipse, roll, dial_radius_px, h)
        if obs is not None:
            observations[h] = obs

    round_conics = {
        which: fit_full_round_conic(observations, which) for which in ("centre", "outer", "inner")
    }
    outer_loo = leave_one_out_round_envelope(observations, ellipse, roll, "outer")
    centre_loo = leave_one_out_round_envelope(observations, ellipse, roll, "centre")
    angular = angular_analysis(observations, ellipse, roll)
    clearance = local_minute_track_clearance(observations, edges, ellipse, roll)

    round_outer_conic = None
    round_hours_present = [h for h in mc.ROUND_HOURS if h in observations]
    if len(round_hours_present) >= mc.MIN_PEERS_FOR_CONIC:
        pts = np.array([_point_for(observations[h], "outer") for h in round_hours_present])
        round_outer_conic = mc.fit_conic(pts)
    shape_offsets = shape_marker_offset_check(observations, ellipse, roll, round_outer_conic)

    verdicts = build_verdicts(observations, outer_loo, angular, clearance, shape_offsets)

    return FrameAnalysis(
        observations=observations, round_conics=round_conics, outer_loo=outer_loo, centre_loo=centre_loo,
        angular=angular, clearance=clearance, shape_offsets=shape_offsets, verdicts=verdicts,
    )
