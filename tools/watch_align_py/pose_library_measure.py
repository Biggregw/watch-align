"""Marker measurement + ambiguity analysis for the pose-library-overlay
method (method C in the video/corpus A/B/C comparison). Downstream marker
detection reuses multi_radius_pose_solver.rectify()/detect_marker_in_rectified()
unchanged -- the solved pose collapses to one ordinary 3x3 homography (see
pose_library.pose_to_homography), so the only difference from method B
(multi-radius RANSAC) is how that homography was obtained. Never uses the
marker under test to select or refine the pose.
"""
from __future__ import annotations

from typing import Tuple

import cv2
import numpy as np

import marker_qc
import master
import multi_radius_pose_solver as mrps
import pose_library as pl
from geometry import RotatedRect


def measure_all_markers(bgr: np.ndarray, edges: np.ndarray, ellipse: RotatedRect, roll: float
                         ) -> Tuple[pl.SolveResult, dict]:
    solve = pl.solve_pose(edges, ellipse, roll)
    results = {h: None for h in mrps.MARKER_HOURS}
    if solve.pose is None:
        return solve, results
    H = pl.pose_to_homography(solve.pose)
    rectified = mrps.rectify(bgr, H)
    rectified_gray = cv2.cvtColor(rectified, cv2.COLOR_BGR2GRAY)
    for hour in mrps.MARKER_HOURS:
        results[hour] = mrps.detect_marker_in_rectified(rectified_gray, mrps.RECTIFY_SIDE, hour)
    return solve, results


def _marker_canon_points() -> np.ndarray:
    return np.array([
        [marker_qc.expected_radius_ratio(h) * np.cos(master.angle_for_hour(h)),
         marker_qc.expected_radius_ratio(h) * np.sin(master.angle_for_hour(h))]
        for h in mrps.MARKER_HOURS
    ])


def ambiguity_report(solve: pl.SolveResult) -> dict:
    """Quantify whether materially different poses among the refined top-N
    candidates fit the minute-track ticks almost equally well (within a
    fixed, pre-declared margin of the single best-scoring candidate) but
    predict different marker locations. The "best" pose is always the one
    with the lowest tick-fit residual, selected before and independently
    of any marker measurement -- this function never re-picks a candidate
    based on how its predicted markers look, it only reports how much
    those predictions disagree across the near-tied set."""
    if not solve.refined_candidates:
        return {"n_near_best": 0, "n_total_candidates": 0}
    cands = sorted(solve.refined_candidates, key=lambda p: p.score_px)
    best = cands[0]
    # Margin is fixed and declared up front (not tuned per-image): either
    # 0.15px absolute or 50% relative to the best score, whichever is
    # larger, so a very tight best-fit still gets a workable margin.
    margin_px = max(0.15, best.score_px * 1.5)
    near_best = [p for p in cands if p.score_px <= best.score_px + margin_px]

    marker_canon = _marker_canon_points()
    best_marker_img = pl.project_canonical(best, marker_canon)

    disagreements = []
    for p in near_best:
        img = pl.project_canonical(p, marker_canon)
        disagreements.append(np.linalg.norm(img - best_marker_img, axis=-1))
    disagreements = np.array(disagreements) if disagreements else np.zeros((0, len(mrps.MARKER_HOURS)))

    return {
        "n_near_best": len(near_best),
        "n_total_candidates": len(cands),
        "margin_px": margin_px,
        "best_score_px": best.score_px,
        "worst_near_best_score_px": max((p.score_px for p in near_best), default=best.score_px),
        "max_marker_disagreement_px": float(disagreements.max()) if disagreements.size else 0.0,
        "mean_marker_disagreement_px": float(disagreements.mean()) if disagreements.size else 0.0,
        "theta_lo": min((p.theta_deg for p in near_best), default=best.theta_deg),
        "theta_hi": max((p.theta_deg for p in near_best), default=best.theta_deg),
        "phi_lo": min((p.phi_deg for p in near_best), default=best.phi_deg),
        "phi_hi": max((p.phi_deg for p in near_best), default=best.phi_deg),
        "k_lo": min((p.k for p in near_best), default=best.k),
        "k_hi": max((p.k for p in near_best), default=best.k),
    }
