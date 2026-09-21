"""Port of MinuteTrackFirstOverlay.build()'s orchestration and acceptance-gate logic
(automaticAcceptance/identityVerificationRequired/primaryVetoed/finalAcceptance/
confidence/topPhaseErrorDeg/canonicalTwelveIsAboveCentre), assembled with the
already-ported acquisition/refinement/validation/identity/marker modules.

Deliberately NOT ported: renderNative/rectify/drawX (Canvas/Bitmap rendering) and
the full legacy PerspectiveGmtOverlay.build() fallback pipeline used only when
minute-track acquisition cannot even get a legacy seed or an ellipse at all. Those
render pixels for the on-device UI; they carry no measurement logic relevant to
debugging acquisition/threshold behaviour. When that fallback would fire, this
port reports REJECTED with the same reason string instead of running the legacy
pipeline.
"""
import math
from dataclasses import dataclass, field
from typing import List, Optional

import cv2
import numpy as np

import master
import minute_track_dial_finder as mtdf
import minute_track_pose_validator as mtpv
import dial_projective_refiner as dpr
import identity_gate
import marker_qc
import seed_detector
from geometry import RotatedRect, ellipse_cardinal_points

FINAL_TOP_PHASE_LIMIT_DEG = 3.25
SUSPICION_CENTER_DISPLACEMENT_FRACTION = 0.22


@dataclass
class Result:
    accepted: bool
    confidence: float
    reason: str = ""
    # Populated only on the minute-track-first path (reason == "").
    acquisition: Optional[mtdf.Result] = None
    tilt_deg: float = math.nan
    dial_radius_px: float = math.nan
    h0: Optional[np.ndarray] = None
    projective_limit: float = math.nan
    refinement: Optional[dpr.Result] = None
    fine: Optional[mtpv.RotationResult] = None
    H: Optional[np.ndarray] = None
    solved_roll: float = math.nan
    validation: Optional[mtpv.ValidationResult] = None
    final_top_error_deg: float = math.nan
    final_top_accepted: bool = False
    automatic_accepted: bool = False
    reproj: float = math.nan
    center_err: float = math.nan
    identity_required: bool = False
    identity: Optional[identity_gate.Evidence] = None
    vetoed: bool = False
    markers: Optional[List[Optional[marker_qc.MarkerDiagnostic]]] = None
    report: str = ""


def _project(H: np.ndarray, x: float, y: float):
    w = H[2, 0] * x + H[2, 1] * y + H[2, 2]
    if abs(w) < 1e-9:
        w = 1e-9
    return ((H[0, 0] * x + H[0, 1] * y + H[0, 2]) / w,
            (H[1, 0] * x + H[1, 1] * y + H[1, 2]) / w)


def top_phase_error_deg(H: np.ndarray) -> float:
    cx, cy = _project(H, 0.0, 0.0)
    tx, ty = _project(H, 0.0, -1.0)
    dx, dy = tx - cx, ty - cy
    length = math.hypot(dx, dy)
    if length < 1e-9:
        return math.inf
    dot = max(-1.0, min(1.0, (-dy) / length))
    return math.degrees(math.acos(dot))


def canonical_twelve_is_above_centre(H: np.ndarray) -> bool:
    cx, cy = _project(H, 0.0, 0.0)
    tx, ty = _project(H, 0.0, -1.0)
    return ty < cy


def _reprojection_error(H: np.ndarray, expected) -> float:
    canonical = [(0.0, -1.0), (1.0, 0.0), (0.0, 1.0), (-1.0, 0.0)]
    total = 0.0
    for (cx, cy), (ex, ey) in zip(canonical, expected):
        px, py = _project(H, cx, cy)
        total += math.hypot(px - ex, py - ey)
    return total / 4.0


def _homography_from_unit_square(dst) -> Optional[np.ndarray]:
    src = np.array([(0.0, -1.0), (1.0, 0.0), (0.0, 1.0), (-1.0, 0.0)], dtype=np.float64)
    dst = np.array(dst, dtype=np.float64)
    H, _mask = cv2.findHomography(src, dst, 0)
    return H


def automatic_acceptance(acquisition_top_phase_accepted: bool, final_top_accepted: bool,
                          independent_minute_track_accepted: bool) -> bool:
    return acquisition_top_phase_accepted and final_top_accepted and independent_minute_track_accepted


def geometrically_suspicious(center_displacement_fraction: float) -> bool:
    return center_displacement_fraction > SUSPICION_CENTER_DISPLACEMENT_FRACTION


def identity_verification_required(automatic_accepted: bool, center_displacement_fraction: float) -> bool:
    """An already-rejected primary never needs identity evidence; rescue already gets to run."""
    return automatic_accepted and geometrically_suspicious(center_displacement_fraction)


def primary_vetoed(automatic_accepted: bool, center_displacement_fraction: float,
                    identity_verdict: Optional[identity_gate.Verdict]) -> bool:
    """A suspicious accepted primary is vetoed unless independent identity evidence strongly
    confirms it (PASS). AMBIGUOUS and FAIL both veto: neither is strong enough to let a large,
    unexplained centre displacement stand as an automatically-trusted pose."""
    if not identity_verification_required(automatic_accepted, center_displacement_fraction):
        return False
    return identity_verdict != identity_gate.Verdict.PASS


def final_acceptance(automatic_accepted: bool, center_displacement_fraction: float,
                      identity_verdict: Optional[identity_gate.Verdict]) -> bool:
    return automatic_accepted and not primary_vetoed(
        automatic_accepted, center_displacement_fraction, identity_verdict)


def _confidence(q: float, reproj: float, center_err: float, axis_ratio: float, fit: float,
                 validation: Optional[mtpv.ValidationResult], accepted: bool) -> float:
    a = max(0.0, min(1.0, (q - 0.45) / 0.45))
    b = max(0.0, 1 - reproj / 4.0)
    c = max(0.0, 1 - center_err / 0.22)
    d = max(0.0, min(1.0, (axis_ratio - 0.65) / 0.30))
    e = max(0.0, 1 - fit / 5.0)
    f = 0.0 if validation is None else max(0.0, 1 - validation.median_px / max(1.0, validation.median_limit_px * 1.6))
    value = 0.18 * a + 0.18 * b + 0.14 * c + 0.12 * d + 0.18 * e + 0.20 * f
    if not accepted:
        value = min(value, 0.35)
    return max(0.0, min(1.0, value))


def build(bgr: np.ndarray, model_ref: str = "126710BLNR") -> Result:
    """Headless port of MinuteTrackFirstOverlay.build(). `bgr` should already reflect
    MainActivity.readBitmap's 1600px-longest-side cap for behavioural fidelity with
    the real app."""
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 1.2)
    edges = cv2.Canny(blur, 55, 145)

    seed = seed_detector.detect_dial(bgr)
    if seed is None or not (seed.r > 40.0):
        return Result(False, 0.0,
                      "Minute-track-first acquisition unavailable: no stable approximate dial centre was found.")

    acquisition = mtdf.find(edges, seed.x, seed.y, seed.r)
    if acquisition is None or acquisition.dial_ellipse is None:
        return Result(False, 0.0,
                      "Minute-track-first acquisition unavailable: minor ticks could not establish a dial ellipse.")

    ellipse = acquisition.dial_ellipse
    major = max(ellipse.w, ellipse.h)
    minor = min(ellipse.w, ellipse.h)
    axis_ratio = minor / max(1.0, major)
    tilt_deg = math.degrees(math.acos(max(0.0, min(1.0, axis_ratio))))
    dial_radius_px = (major + minor) / 4.0

    base_card = ellipse_cardinal_points(ellipse, acquisition.roll_deg)
    h0 = _homography_from_unit_square(base_card)
    if h0 is None or h0.size == 0:
        return Result(False, 0.0, "Homography solve from ellipse cardinal points failed.")

    projective_limit = max(0.015, min(0.32, 0.45 * math.sin(math.radians(tilt_deg))))
    refinement_mat = dpr.refine_with_diagnostics(edges, h0, projective_limit)
    refinement = refinement_mat.diagnostics

    fine = mtpv.fine_tune_rotation(edges, refinement_mat.homography)
    H = fine.homography
    solved_roll = acquisition.roll_deg + fine.delta_deg

    validation = mtpv.validate(edges, H, dial_radius_px)
    final_top_error = top_phase_error_deg(H)
    final_top_accepted = math.isfinite(final_top_error) and final_top_error <= FINAL_TOP_PHASE_LIMIT_DEG \
        and canonical_twelve_is_above_centre(H)

    automatic_accepted = automatic_acceptance(
        acquisition.top_phase_accepted, final_top_accepted, validation.accepted)

    expected_card = ellipse_cardinal_points(ellipse, solved_roll)
    reproj = _reprojection_error(H, expected_card)
    center_err = math.hypot(ellipse.cx - seed.x, ellipse.cy - seed.y) / max(1.0, dial_radius_px)

    identity_required = identity_verification_required(automatic_accepted, center_err)
    identity = identity_gate.evaluate(gray, edges, ellipse, solved_roll) if identity_required else None
    identity_verdict = identity.verdict if identity is not None else None
    vetoed = primary_vetoed(automatic_accepted, center_err, identity_verdict)
    accepted = final_acceptance(automatic_accepted, center_err, identity_verdict)

    confidence = _confidence(seed.quality, reproj, center_err, axis_ratio,
                              acquisition.fit_median_px, validation, accepted)

    markers = marker_qc.measure(gray, bgr, edges)

    report = _format_report(
        acquisition, tilt_deg, major, minor, dial_radius_px, h0, projective_limit,
        refinement_mat, refinement, fine, H, solved_roll, validation, final_top_error,
        final_top_accepted, automatic_accepted, reproj, center_err, identity_required,
        identity, vetoed, accepted, confidence)

    return Result(
        accepted, confidence, "", acquisition, tilt_deg, dial_radius_px, h0, projective_limit,
        refinement, fine, H, solved_roll, validation, final_top_error, final_top_accepted,
        automatic_accepted, reproj, center_err, identity_required, identity, vetoed, markers, report)


def _matrix_terms(h: np.ndarray):
    return h.flatten()


def _normalized_term_flat(h_flat, i: int) -> float:
    return h_flat[i] / h_flat[8]


def _normalized_term_mat(h: np.ndarray, r: int, c: int) -> float:
    return h[r, c] / h[2, 2]


def _format_report(acquisition, tilt_deg, major, minor, dial_radius_px, h0, projective_limit,
                    refinement_mat, refinement, fine, H, solved_roll, validation, final_top_error,
                    final_top_accepted, automatic_accepted, reproj, center_err, identity_required,
                    identity, vetoed, accepted, confidence) -> str:
    master_name = master.ID
    h0_flat = _matrix_terms(h0)
    return (
        "\n\nVISUAL QC MASTER\n"
        "Pose source: MINUTE TRACK FIRST. The legacy detector supplies only an approximate centre/search scale; it cannot set final geometry.\n"
        f"Inspection geometry: {master_name}. Red outlines are the fixed master; white outlines are lume references.\n"
        f"Minute-track acquisition: {acquisition.candidate_count} concentric ellipse candidates evaluated; "
        f"minor-tick fit median {acquisition.fit_median_px:.2f} px; anchored roll {acquisition.roll_deg:+.2f}°. Minute track sets centre and scale.\n"
        f"Top-phase anchor: {'ACCEPTED' if acquisition.top_phase_accepted else 'REJECTED'}; "
        f"canonical 12 axis {acquisition.top_phase_error_deg:.2f}° from image-up. QC photos are required to be upright with the 12 minute-track tick nearest the top.\n"
        f"Next outward ring: {'CONFIRMED' if acquisition.boundary_confirmed else 'NOT CONFIRMED'} at canonical radius "
        f"{acquisition.outer_boundary_radius:.4f} (expected 1.0000); boundary median {acquisition.outer_boundary_median_px:.2f} px, "
        f"p90 {acquisition.outer_boundary_p90_px:.2f} px. ADVISORY ONLY: it does not resize or veto the automatic master. "
        f"Track/dial master ratio {master.MINUTE_TRACK_R:.4f}.\n"
        f"Selected dial ellipse: {major:.1f} × {minor:.1f} px; apparent tilt {tilt_deg:.1f}°; "
        f"centre moved {center_err * 100.0:.2f}% of dial radius from the legacy seed.\n"
        f"Final rotation correction: {fine.delta_deg:+.2f}° (bounded to ±{mtpv.FINE_ROTATION_LIMIT_DEG:.2f}°); "
        f"final 12-axis error {final_top_error:.2f}° ({'ACCEPTED' if final_top_accepted else 'REJECTED'}).\n"
        f"Independent minute-track validation: {'ACCEPTED' if validation.accepted else 'REJECTED'}; "
        f"{validation.holdout_ticks} held-out ticks; median {validation.median_px:.2f} px (limit {validation.median_limit_px:.2f}), "
        f"p90 {validation.p90_px:.2f} px (limit {validation.p90_limit_px:.2f}), "
        f"inliers {validation.inlier_fraction * 100.0:.0f}% at {validation.inlier_limit_px:.2f} px.\n"
        f"Automatic decision gates: initial top-phase {'PASS' if acquisition.top_phase_accepted else 'FAIL'}; "
        f"final 12-axis {'PASS' if final_top_accepted else 'FAIL'}; "
        f"held-out minute track {'PASS' if validation.accepted else 'FAIL'}; "
        f"outward ring advisory {'CONFIRMED' if acquisition.boundary_confirmed else 'NOT CONFIRMED'}.\n"
        f"Primary geometry accepted (pre-identity-check): {'ACCEPTED' if automatic_accepted else 'REJECTED'}. "
        f"Independent identity verification required: {'YES' if identity_required else 'NO'} "
        f"(centre displacement {center_err * 100.0:.2f}% of dial radius vs {SUSPICION_CENTER_DISPLACEMENT_FRACTION * 100.0:.0f}% suspicion threshold).\n"
        f"Independent identity evidence: {'NOT EVALUATED' if identity is None else identity.verdict.value}"
        + ("" if identity is None else
           f" (dial interior median {identity.dial_interior_median:.1f}, interior edge fraction {identity.interior_edge_fraction:.3f}, "
           f"{identity.markers_found}/3 markers found)") + ". This check samples dial-interior darkness and 12/6/9 marker "
        "presence at the resolved pose; it never fits, moves or resizes the candidate.\n"
        f"Primary vetoed by identity gate: {'YES' if vetoed else 'NO'}.\n"
        f"Automatic master: {'ACCEPTED' if accepted else 'REJECTED'}. "
        f"{'Safe to show automatically.' if accepted else 'Hidden from the main QC view; use Native Template for manual alignment.'}\n"
        f"Pose residual: {reproj:.2f} px. Confidence: {confidence * 100.0:.0f}%.\n"
        f"Projective refinement: {'ACCEPTED' if refinement.accepted else 'REJECTED'}.\n"
        f"H0 projective terms: h31={_normalized_term_flat(h0_flat, 6):+.6f}, h32={_normalized_term_flat(h0_flat, 7):+.6f}.\n"
        f"Refined candidate terms: h31={_normalized_term_mat(refinement.evaluated_homography, 2, 0):+.6f}, "
        f"h32={_normalized_term_mat(refinement.evaluated_homography, 2, 1):+.6f}.\n"
        f"Fit evidence: {refinement.fit_before:.4f} before, {refinement.evaluated_fit_after:.4f} after. "
        f"Holdout evidence: {refinement.holdout_before:.4f} before, {refinement.evaluated_holdout_after:.4f} after.\n"
        f"H0 fallback used: {'NO' if refinement.accepted else 'YES'}.\n"
        "If automatic validation rejects the pose, automated geometric QC is suppressed and Native Template remains available for manual move/resize/rotate.\n"
    )
