"""Pose-library-overlay experiment (see docs/research/pose-library-overlay-
2026-09-22.md). Separate research direction from multi_radius_pose_solver:
instead of fitting a general, unconstrained 8-DOF homography from noisy
point correspondences (which can fit non-physical distortion, e.g. the
boundary-correspondence noise diagnosed on 2026-09-22), constrain the
search to the 7-dimensional family of homographies a real pinhole camera
viewing a real flat circular dial can actually produce, and search that
family directly: tilt magnitude, tilt azimuth, camera roll, and a single
perspective-strength parameter, with scale and translation solved in
closed form for any candidate. This IS still a full 3x3 homography (any
pinhole-camera-viewing-a-plane relationship is exactly a homography) -- it
is a physically-constrained 7-DOF submanifold of the general 8-DOF
homography group, not a different geometric model. The missing 1 DOF
relative to the unconstrained fit is exactly the kind of freedom (e.g.
independent non-uniform image scale/shear) that a real camera+flat-target
system cannot produce, so constraining the search to this submanifold acts
as a physical regulariser against overfitting measurement noise -- the
central architectural bet of this experiment.

Never uses the marker under test to select or refine the pose: candidate
poses are scored only against minute-track tick correspondences (see
multi_radius_pose_solver.extract_correspondences, kind == "tick"), the
same real-edge-pixel-centroid localisation already used and validated
there. Once solved, the pose collapses to one ordinary 3x3 homography, so
it reuses multi_radius_pose_solver.rectify()/detect_marker_in_rectified()
unchanged for the downstream marker measurement -- the comparison between
this method and the multi-radius method is then isolated to "how was the
homography obtained," not "how are markers measured once rectified."

Pose parameterisation (canonical dial-plane point (x, y, 0) -> image pixel
(u, v)):
  1. P' = R_tilt(theta, phi) @ (x, y, 0)^T = (X, Y, Z)
     R_tilt is a 3D rotation by `theta` about the in-plane axis
     perpendicular to azimuth `phi` (Rodrigues formula about axis
     (-sin(phi), cos(phi), 0)) -- i.e. the direction at azimuth phi is the
     foreshortened ("downhill") direction, the perpendicular direction is
     preserved. theta=0 is always the identity regardless of phi.
  2. proj = (X, Y) / (1 + k*Z) -- central/pinhole projection with a single
     dimensionless perspective-strength parameter k (effectively focal
     length / camera distance, in canonical dial-radius units). k=0
     reduces this exactly to orthographic projection -- i.e. k=0 recovers
     the CURRENT ellipse/affine baseline model exactly, so this pose
     family is a strict generalisation of the baseline, not a different
     model swapped in.
  3. rolled = Rot2D(psi) @ proj -- camera roll, applied post-projection
     (matches the existing codebase's convention: roll is always a 2D
     in-plane rotation, see geometry.map_point).
  4. (u, v) = s * rolled + (tx, ty) -- uniform scale + translation, solved
     in closed form for any (theta, phi, psi, k) rather than searched.

k is restricted to [0, K_MAX] rather than allowing negative values: since
phi already spans the full circle, negating k is equivalent to phi -> phi
+ 180 degrees, so allowing negative k would only duplicate coverage, not
extend it. K_MAX is capped well below where (1 + k*Z) can approach zero
for realistic theta/radius combinations, to keep the projection
non-singular across the whole search grid (see K_VALUES below).
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from typing import List, Optional, Sequence, Tuple

import numpy as np

import master
import marker_qc
import multi_radius_pose_solver as mrps
from geometry import RotatedRect

# --- coarse grid ranges (documented rationale in the research doc) ---
# theta (tilt magnitude): 0-55deg covers the corpus's observed range (max
# seen so far ~46deg) with headroom; 2.5deg step is fine enough that the
# continuous refinement stage only needs to close a sub-step gap.
THETA_DEG = np.arange(0.0, 55.0 + 1e-9, 2.5)  # 23 levels
# phi (tilt azimuth): full circle, since the azimuth is not knowable in
# advance and (per the ambiguity analysis this experiment is required to
# run) may not even be uniquely identifiable from ticks alone at low tilt.
PHI_DEG = np.arange(0.0, 360.0, 15.0)  # 24 levels
# psi (camera roll): searched only in a window around the pose the
# existing, already-validated acquisition/MinuteTrackPoseValidator pipeline
# already solved for this image (acquisition_roll_deg), rather than a full
# blind 360deg search -- a deliberate, documented simplification, not a
# claim that roll is trivially known; the continuous refinement stage can
# still move psi outside this window if the local optimum pulls it there.
PSI_WINDOW_DEG = 15.0
PSI_STEP_DEG = 3.0  # -> 11 levels within the window
# k (perspective strength): 0 (pure affine baseline) up to 0.9. At theta=55,
# radius up to ~1.01 (dial boundary), Z can reach sin(55)*1.01 ~= 0.83, so
# 1+k*Z >= 1-0.9*0.83 ~= 0.25 stays safely positive across the whole grid.
K_VALUES = np.arange(0.0, 0.9 + 1e-9, 0.15)  # 7 levels

TOP_N_REFINE = 8
MIN_TICKS = 12


@dataclass
class Pose:
    theta_deg: float
    phi_deg: float
    psi_deg: float
    k: float
    scale: float
    tx: float
    ty: float
    score_px: float  # robust (median) tick residual, px, against held-out ticks never used to fit


def _tilt_xy_block(theta_deg: np.ndarray, phi_deg: np.ndarray):
    """Rodrigues rotation about axis (-sin(phi),cos(phi),0) by theta.
    Returns the 6 matrix entries (R00,R01,R10,R11,R20,R21) needed to map
    (x,y,0) -> (X,Y,Z), vectorised over arrays of matching shape."""
    theta = np.radians(theta_deg)
    phi = np.radians(phi_deg)
    ax, ay = -np.sin(phi), np.cos(phi)
    c, s = np.cos(theta), np.sin(theta)
    one_c = 1.0 - c
    R00 = c + one_c * ax * ax
    R01 = one_c * ax * ay
    R10 = one_c * ay * ax
    R11 = c + one_c * ay * ay
    R20 = -s * ay
    R21 = s * ax
    return R00, R01, R10, R11, R20, R21


def _raw_projected_points(theta_deg, phi_deg, psi_deg, k, canon_xy: np.ndarray):
    """canon_xy: (n_pts, 2). theta/phi/psi/k: scalars or (G,) arrays.
    Returns raw (unscaled, untranslated) rolled-projected points, shape
    (n_pts,2) if inputs are scalar, or (G, n_pts, 2) if inputs are (G,)
    arrays (grid-batched)."""
    R00, R01, R10, R11, R20, R21 = _tilt_xy_block(np.asarray(theta_deg), np.asarray(phi_deg))
    x = canon_xy[:, 0]
    y = canon_xy[:, 1]
    grid = np.ndim(R00) > 0
    if grid:
        X = R00[:, None] * x[None, :] + R01[:, None] * y[None, :]
        Y = R10[:, None] * x[None, :] + R11[:, None] * y[None, :]
        Z = R20[:, None] * x[None, :] + R21[:, None] * y[None, :]
        denom = 1.0 + np.asarray(k)[:, None] * Z
        px, py = X / denom, Y / denom
        psi = np.radians(np.asarray(psi_deg))
        cp, sp = np.cos(psi)[:, None], np.sin(psi)[:, None]
    else:
        X = R00 * x + R01 * y
        Y = R10 * x + R11 * y
        Z = R20 * x + R21 * y
        denom = 1.0 + k * Z
        px, py = X / denom, Y / denom
        psi = math.radians(psi_deg)
        cp, sp = math.cos(psi), math.sin(psi)
    rx = cp * px - sp * py
    ry = sp * px + cp * py
    return np.stack([rx, ry], axis=-1)


def _solve_scale_translate(raw: np.ndarray, img: np.ndarray):
    """Closed-form isotropic scale + translation least-squares fit
    (rotation already applied via psi, so this is a 3-parameter linear
    solve, not a full similarity/Umeyama fit). raw, img: (..., n_pts, 2).
    Returns (scale, tx, ty), each shaped like raw.shape[:-2]."""
    mean_raw = raw.mean(axis=-2)
    mean_img = img.mean(axis=-2)
    craw = raw - mean_raw[..., None, :]
    cimg = img - mean_img[..., None, :]
    num = (craw * cimg).sum(axis=(-2, -1))
    den = (craw * craw).sum(axis=(-2, -1))
    scale = np.where(den > 1e-9, num / np.maximum(den, 1e-9), 0.0)
    tx = mean_img[..., 0] - scale * mean_raw[..., 0]
    ty = mean_img[..., 1] - scale * mean_raw[..., 1]
    return scale, tx, ty


def _homography_from_pose(theta_deg: float, phi_deg: float, psi_deg: float, k: float,
                           scale: float, tx: float, ty: float) -> np.ndarray:
    R00, R01, R10, R11, R20, R21 = _tilt_xy_block(theta_deg, phi_deg)
    K_tilt = np.array([[R00, R01, 0.0], [R10, R11, 0.0], [k * R20, k * R21, 1.0]])
    psi = math.radians(psi_deg)
    cp, sp = math.cos(psi), math.sin(psi)
    Roll = np.array([[cp, -sp, 0.0], [sp, cp, 0.0], [0.0, 0.0, 1.0]])
    S = np.array([[scale, 0.0, tx], [0.0, scale, ty], [0.0, 0.0, 1.0]])
    return S @ Roll @ K_tilt


def pose_to_homography(pose: Pose) -> np.ndarray:
    return _homography_from_pose(pose.theta_deg, pose.phi_deg, pose.psi_deg, pose.k,
                                  pose.scale, pose.tx, pose.ty)


def project_canonical(pose: Pose, canon_xy: np.ndarray) -> np.ndarray:
    """Forward-project canonical dial-plane points through a solved pose
    into image pixel coordinates. canon_xy: (n,2) -> (n,2) image pixels."""
    raw = _raw_projected_points(pose.theta_deg, pose.phi_deg, pose.psi_deg, pose.k, canon_xy)
    return pose.scale * raw + np.array([pose.tx, pose.ty])


def _coarse_grid_search(canon_xy: np.ndarray, img_xy: np.ndarray, roll_prior_deg: float
                         ) -> List[Pose]:
    psi_vals = np.arange(roll_prior_deg - PSI_WINDOW_DEG, roll_prior_deg + PSI_WINDOW_DEG + 1e-9, PSI_STEP_DEG)
    theta_g, phi_g, psi_g, k_g = np.meshgrid(THETA_DEG, PHI_DEG, psi_vals, K_VALUES, indexing="ij")
    theta_g, phi_g, psi_g, k_g = (a.ravel() for a in (theta_g, phi_g, psi_g, k_g))

    raw = _raw_projected_points(theta_g, phi_g, psi_g, k_g, canon_xy)  # (G, n, 2)
    img_b = np.broadcast_to(img_xy, raw.shape)
    scale, tx, ty = _solve_scale_translate(raw, img_b)  # (G,)
    fitted = scale[:, None, None] * raw + np.stack([tx, ty], axis=-1)[:, None, :]
    resid = np.linalg.norm(fitted - img_b, axis=-1)  # (G, n)
    score = np.median(resid, axis=-1)  # (G,)

    order = np.argsort(score)[:TOP_N_REFINE]
    return [Pose(float(theta_g[i]), float(phi_g[i]), float(psi_g[i]), float(k_g[i]),
                 float(scale[i]), float(tx[i]), float(ty[i]), float(score[i]))
            for i in order]


def _score_scalar(theta_deg: float, phi_deg: float, psi_deg: float, k: float,
                   canon_xy: np.ndarray, img_xy: np.ndarray) -> Pose:
    theta_deg = float(np.clip(theta_deg, 0.0, 65.0))
    k = float(np.clip(k, 0.0, 1.1))
    raw = _raw_projected_points(theta_deg, phi_deg % 360.0, psi_deg, k, canon_xy)
    scale, tx, ty = _solve_scale_translate(raw, img_xy)
    fitted = scale * raw + np.array([tx, ty])
    resid = np.linalg.norm(fitted - img_xy, axis=-1)
    score = float(np.median(resid))
    return Pose(theta_deg, phi_deg % 360.0, psi_deg, k, float(scale), float(tx), float(ty), score)


def _refine(seed: Pose, canon_xy: np.ndarray, img_xy: np.ndarray) -> Pose:
    from scipy.optimize import minimize

    def objective(params):
        theta, phi, psi, k = params
        return _score_scalar(theta, phi, psi, k, canon_xy, img_xy).score_px

    x0 = [seed.theta_deg, seed.phi_deg, seed.psi_deg, seed.k]
    result = minimize(objective, x0, method="Nelder-Mead",
                       options={"xatol": 1e-3, "fatol": 1e-4, "maxiter": 400, "adaptive": True})
    theta, phi, psi, k = result.x
    return _score_scalar(theta, phi, psi, k, canon_xy, img_xy)


@dataclass
class SolveResult:
    pose: Optional[Pose]
    coarse_candidates: List[Pose]
    refined_candidates: List[Pose]
    n_ticks: int


def solve_pose(edges: np.ndarray, ellipse: RotatedRect, roll_prior_deg: float) -> SolveResult:
    """Full coarse-to-fine pose solve. Uses ONLY minute-track tick
    correspondences (never the marker under test, never boundary points in
    this v1) -- reuses mrps.extract_correspondences' real-edge-pixel
    centroid localisation, already validated in the multi-radius-homography
    work, restricted to kind=='tick'."""
    corr = mrps.extract_correspondences(edges, ellipse, roll_prior_deg)
    ticks = [c for c in corr if c.kind == "tick"]
    if len(ticks) < MIN_TICKS:
        return SolveResult(None, [], [], len(ticks))
    canon_xy = np.array([[c.canonical_x, c.canonical_y] for c in ticks])
    img_xy = np.array([[c.image_x, c.image_y] for c in ticks])

    coarse = _coarse_grid_search(canon_xy, img_xy, roll_prior_deg)
    refined = [_refine(seed, canon_xy, img_xy) for seed in coarse]
    refined.sort(key=lambda p: p.score_px)
    return SolveResult(refined[0], coarse, refined, len(ticks))


def project_master_overlay(pose: Pose, n_ticks: int = 60, n_boundary: int = 96
                            ) -> dict:
    """Project the full ideal master geometry (minute track + dial edge +
    every hour marker's expected centre) through a solved pose, for visual
    overlay / diagnostic rendering. Returns image-pixel arrays; does not
    measure or move anything -- purely a rendering/reporting helper."""
    tick_angles = np.radians(np.arange(n_ticks) * (360.0 / n_ticks) - 90.0)
    tick_canon = np.stack([mrps.TRACK_R * np.cos(tick_angles), mrps.TRACK_R * np.sin(tick_angles)], axis=-1)
    boundary_angles = np.linspace(0.0, 2.0 * np.pi, n_boundary, endpoint=False)
    boundary_canon = np.stack([mrps.DIAL_EDGE_R * np.cos(boundary_angles),
                                mrps.DIAL_EDGE_R * np.sin(boundary_angles)], axis=-1)
    marker_hours = mrps.MARKER_HOURS
    marker_canon = np.array([
        [marker_qc.expected_radius_ratio(h) * math.cos(master.angle_for_hour(h)),
         marker_qc.expected_radius_ratio(h) * math.sin(master.angle_for_hour(h))]
        for h in marker_hours
    ])
    return {
        "tick_image_xy": project_canonical(pose, tick_canon),
        "boundary_image_xy": project_canonical(pose, boundary_canon),
        "marker_hours": marker_hours,
        "marker_image_xy": project_canonical(pose, marker_canon),
    }
