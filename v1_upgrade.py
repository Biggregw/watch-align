from __future__ import annotations

import math
from typing import Any

import cv2
import numpy as np

V1_VERSION = "1.0-dev1"

REFERENCE_CATALOG = [
    {"brand": "Rolex", "family": "GMT-Master II", "reference": "126710BLNR", "name": "GMT-Master II 126710BLNR (Batgirl/Batman)", "geometry_profile": "rolex-sports-date", "built_in_images": 0, "status": "geometry-ready; add verified genuine references"},
    {"brand": "Rolex", "family": "Submariner", "reference": "124060", "name": "Submariner 124060 No-Date", "geometry_profile": "rolex-sports-no-date", "built_in_images": 0, "status": "geometry-ready; add verified genuine references"},
]

_V1_JS = r'''(() => {
  const original = window.showMetrics;
  if (typeof original !== "function") return;
  window.showMetrics = function(metrics) {
    original(metrics);
    const box = document.getElementById("metrics");
    if (!box) return;
    const p = metrics.perspective || {};
    const stable = metrics.stable_ecc || {};
    const agreement = metrics.method_agreement || {};
    let html = '<div style="margin-top:.7rem;padding-top:.7rem;border-top:1px solid #253857"><strong>V1 geometry diagnostics</strong></div>';
    if (p.reference || p.candidate) {
      html += `<div><strong>Apparent camera tilt:</strong> ref ${p.reference?.tilt_deg ?? 'n/a'}° · candidate ${p.candidate?.tilt_deg ?? 'n/a'}°</div>`;
      html += `<div><strong>Perspective mismatch:</strong> ${p.mismatch_deg ?? 'n/a'}° ${p.warning ? '⚠ ' + p.warning : ''}</div>`;
    }
    if (agreement.marker_polar_delta_deg != null) html += `<div><strong>Rotation-method agreement:</strong> ${agreement.marker_polar_delta_deg}° (${agreement.rating})</div>`;
    if (stable.attempted) html += `<div><strong>Stable-geometry refinement:</strong> ${stable.applied ? 'applied' : 'not applied'} · ECC ${stable.score ?? 'n/a'} · improvement ${stable.improvement ?? 'n/a'}</div>`;
    if (metrics.v1_confidence_reason) html += `<div><strong>Confidence guard:</strong> ${metrics.v1_confidence_reason}</div>`;
    box.insertAdjacentHTML('beforeend', html);
  };
})();'''


def _angle_delta(a: float | None, b: float | None) -> float | None:
    if a is None or b is None:
        return None
    return abs(((float(a) - float(b) + 180.0) % 360.0) - 180.0)


def _geometry_image(image: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray)
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    mag = np.log1p(cv2.magnitude(gx, gy))
    mag = cv2.GaussianBlur(mag, (0, 0), 0.9)
    mag -= float(mag.mean())
    mag /= float(mag.std()) + 1e-6
    return mag.astype(np.float32)


def _stable_mask(shape: tuple[int, int], circle: tuple[float, float, float]) -> np.ndarray:
    """Keep fixed dial/crystal geometry while excluding hands/date/background."""
    h, w = shape
    cx, cy, radius = circle
    yy, xx = np.indices((h, w), dtype=np.float32)
    dx = xx - cx
    dy = yy - cy
    radial = np.sqrt(dx * dx + dy * dy)
    angle = np.degrees(np.arctan2(dx, -dy)) % 360.0
    mask = (radial >= radius * 0.54) & (radial <= radius * 1.04)
    mask &= ~((angle >= 68.0) & (angle <= 112.0) & (radial >= radius * 0.48))
    mask &= ~((angle >= 82.0) & (angle <= 98.0) & (radial >= radius * 0.88))
    out = mask.astype(np.uint8) * 255
    k = max(3, int(round(radius * 0.012)) | 1)
    return cv2.morphologyEx(out, cv2.MORPH_OPEN, np.ones((k, k), np.uint8))


def _masked_corr(a: np.ndarray, b: np.ndarray, mask: np.ndarray) -> float:
    selected = mask > 0
    if int(np.count_nonzero(selected)) < 500:
        return 0.0
    av = a[selected].astype(np.float32)
    bv = b[selected].astype(np.float32)
    av -= float(av.mean())
    bv -= float(bv.mean())
    return float(np.dot(av, bv) / (float(np.linalg.norm(av) * np.linalg.norm(bv)) + 1e-9))


def _affine_decompose(matrix: np.ndarray) -> tuple[float, float, float, float]:
    linear = matrix[:, :2].astype(np.float64)
    u, s, vh = np.linalg.svd(linear)
    rotation_matrix = u @ vh
    rotation = math.degrees(math.atan2(rotation_matrix[1, 0], rotation_matrix[0, 0]))
    sx, sy = float(abs(s[0])), float(abs(s[1]))
    return rotation, sx, sy, max(sx, sy) / max(1e-6, min(sx, sy))


def _combine_affine(after: np.ndarray, before: np.ndarray) -> np.ndarray:
    return (np.vstack([after, [0.0, 0.0, 1.0]]) @ np.vstack([before, [0.0, 0.0, 1.0]]))[:2]


def perspective_diagnostics(image: np.ndarray, circle: tuple[float, float, float] | None, samples: int = 240) -> dict[str, Any]:
    """Estimate apparent off-axis perspective from radial crystal-edge samples.

    This is deliberately diagnostic only. A watch is not a single flat plane, so
    the result gates confidence rather than automatically projective-warping the
    source and potentially hiding the defect being inspected.
    """
    if circle is None:
        return {"available": False, "reason": "crystal circle unavailable"}
    cx, cy, radius = map(float, circle)
    if radius < 30:
        return {"available": False, "reason": "crystal radius too small"}
    gray = cv2.GaussianBlur(cv2.cvtColor(image, cv2.COLOR_BGR2GRAY), (5, 5), 0)
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    radii = np.linspace(radius * 0.84, radius * 1.18, 80, dtype=np.float32)
    points, strengths = [], []
    h, w = gray.shape
    for theta in np.linspace(0.0, 2.0 * math.pi, samples, endpoint=False):
        sin_t, cos_t = math.sin(theta), math.cos(theta)
        xs, ys = cx + radii * sin_t, cy - radii * cos_t
        xi = np.clip(np.rint(xs).astype(np.int32), 0, w - 1)
        yi = np.clip(np.rint(ys).astype(np.int32), 0, h - 1)
        response = np.abs(gx[yi, xi] * sin_t + gy[yi, xi] * (-cos_t))
        best = int(np.argmax(response))
        points.append((float(xs[best]), float(ys[best])))
        strengths.append(float(response[best]))
    pts = np.asarray(points, dtype=np.float32)
    strength = np.asarray(strengths, dtype=np.float32)
    keep = strength >= np.percentile(strength, 30)
    pts = pts[keep]
    if pts.shape[0] < 20:
        return {"available": False, "reason": "weak crystal boundary"}
    try:
        ellipse = cv2.fitEllipseAMS(pts.reshape(-1, 1, 2)) if hasattr(cv2, "fitEllipseAMS") else cv2.fitEllipse(pts.reshape(-1, 1, 2))
    except cv2.error:
        return {"available": False, "reason": "ellipse fit failed"}
    (ex, ey), (axis_a, axis_b), angle = ellipse
    major, minor = max(float(axis_a), float(axis_b)), min(float(axis_a), float(axis_b))
    if major <= 1.0 or minor <= 1.0:
        return {"available": False, "reason": "invalid ellipse"}
    ratio = float(np.clip(minor / major, 0.0, 1.0))
    tilt = math.degrees(math.acos(ratio))
    centre_error = math.hypot(ex - cx, ey - cy) / max(radius, 1.0)
    confidence = "high" if centre_error <= 0.05 else "medium" if centre_error <= 0.10 else "low"
    return {"available": True, "axis_ratio": round(ratio, 4), "tilt_deg": round(float(tilt), 2), "ellipse_angle_deg": round(float(angle), 2), "centre_offset_ratio": round(float(centre_error), 4), "boundary_strength": round(float(np.median(strength)), 2), "confidence": confidence}


def stable_ecc_refinement(reference: np.ndarray, candidate: np.ndarray, base_matrix: np.ndarray, reference_circle: tuple[float, float, float] | None) -> tuple[np.ndarray, dict[str, Any]]:
    if reference_circle is None:
        return base_matrix, {"attempted": False, "applied": False, "reason": "reference circle unavailable"}
    h, w = reference.shape[:2]
    aligned = cv2.warpAffine(candidate, base_matrix, (w, h), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=(18, 18, 18))
    ref_geom, cand_geom = _geometry_image(reference), _geometry_image(aligned)
    mask = _stable_mask((h, w), reference_circle)
    before = _masked_corr(ref_geom, cand_geom, mask)
    warp = np.eye(2, 3, dtype=np.float32)
    criteria = (cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 350, 1e-7)
    dual_mask = getattr(cv2, "findTransformECCWithMask", None)
    try:
        if dual_mask is not None:
            score, inverse = dual_mask(ref_geom, cand_geom, mask, mask, warp, cv2.MOTION_AFFINE, criteria, 5)
        else:
            score, inverse = cv2.findTransformECC(ref_geom, cand_geom, warp, cv2.MOTION_AFFINE, criteria, inputMask=mask, gaussFiltSize=5)
    except cv2.error as exc:
        return base_matrix, {"attempted": True, "applied": False, "reason": "ECC failed", "error": str(exc).splitlines()[0][:180], "before_correlation": round(before, 4)}
    correction = cv2.invertAffineTransform(inverse).astype(np.float64)
    rotation, sx, sy, anisotropy = _affine_decompose(correction)
    average_scale = (sx + sy) / 2.0
    translation = math.hypot(float(correction[0, 2]), float(correction[1, 2]))
    corrected = cv2.warpAffine(aligned, correction, (w, h), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=(18, 18, 18))
    after = _masked_corr(ref_geom, _geometry_image(corrected), mask)
    improvement = after - before
    plausible = float(score) >= 0.20 and improvement >= 0.003 and translation <= 0.025 * min(h, w) and abs(rotation) <= 0.65 and 0.992 <= average_scale <= 1.008 and anisotropy <= 1.012
    refined = _combine_affine(correction, base_matrix) if plausible else base_matrix
    return refined, {"attempted": True, "applied": bool(plausible), "reason": "applied" if plausible else "correction rejected by geometry guard", "score": round(float(score), 4), "before_correlation": round(float(before), 4), "after_correlation": round(float(after), 4), "improvement": round(float(improvement), 4), "rotation_correction_deg": round(float(rotation), 4), "scale_correction": round(float(average_scale), 6), "anisotropy": round(float(anisotropy), 6), "translation_px": round(float(translation), 3), "dual_mask_api": bool(dual_mask is not None)}


def _circle_from_metrics(metrics: dict[str, Any], key: str) -> tuple[float, float, float] | None:
    value = metrics.get(key)
    if isinstance(value, (list, tuple)) and len(value) == 3:
        try:
            c = tuple(float(v) for v in value)
            if c[2] > 5:
                return c
        except (TypeError, ValueError):
            pass
    return None


def upgrade_auto_align(original):
    def auto_align_v1(reference: np.ndarray, candidate: np.ndarray):
        matrix, metrics = original(reference, candidate)
        metrics = dict(metrics)
        ref_circle = _circle_from_metrics(metrics, "reference_crystal_circle") or _circle_from_metrics(metrics, "reference_circle")
        cand_circle = _circle_from_metrics(metrics, "candidate_crystal_circle") or _circle_from_metrics(metrics, "candidate_circle")
        ref_p, cand_p = perspective_diagnostics(reference, ref_circle), perspective_diagnostics(candidate, cand_circle)
        mismatch = abs(float(ref_p["tilt_deg"]) - float(cand_p["tilt_deg"])) if ref_p.get("available") and cand_p.get("available") else None
        warning = None
        if mismatch is not None and mismatch >= 6.0:
            warning = "large perspective mismatch; precision comparison is limited"
        elif mismatch is not None and mismatch >= 3.0:
            warning = "moderate perspective mismatch; inspect small differences cautiously"
        elif max(float(ref_p.get("tilt_deg", 0.0)), float(cand_p.get("tilt_deg", 0.0))) >= 12.0:
            warning = "source image is strongly off-axis"
        marker_polar = _angle_delta(metrics.get("initial_marker_rotation_deg"), metrics.get("initial_polar_rotation_deg"))
        agreement_rating = "unavailable" if marker_polar is None else "excellent" if marker_polar <= 0.6 else "good" if marker_polar <= 1.5 else "weak" if marker_polar <= 3.0 else "poor"
        if warning is None or (mismatch is not None and mismatch < 3.0):
            matrix2, stable = stable_ecc_refinement(reference, candidate, matrix, ref_circle)
        else:
            matrix2, stable = matrix, {"attempted": False, "applied": False, "reason": "skipped because perspective mismatch is too large"}
        old_score = float(metrics.get("confidence_score", 0.0) or 0.0)
        perspective_factor = 1.0 if mismatch is None or mismatch <= 2.0 else 0.90 if mismatch <= 4.0 else 0.72 if mismatch <= 7.0 else 0.55
        agreement_factor = 1.0 if marker_polar is None or marker_polar <= 1.5 else 0.88 if marker_polar <= 3.0 else 0.70
        guarded_score = float(np.clip(old_score * perspective_factor * agreement_factor, 0.0, 1.0))
        confidence = "high" if guarded_score >= 0.72 else "medium" if guarded_score >= 0.48 else "low"
        reasons = []
        if perspective_factor < 1.0: reasons.append("perspective mismatch reduced confidence")
        if agreement_factor < 1.0: reasons.append("rotation estimators disagree")
        if stable.get("applied"): reasons.append("stable-geometry ECC refinement applied")
        if not reasons: reasons.append("independent geometry checks agree")
        metrics.update({"alignment_method": str(metrics.get("alignment_method", "automatic")) + " + V1 perspective guard + stable masked ECC", "confidence_score_pre_guard": round(old_score, 3), "confidence_score": round(guarded_score, 3), "confidence": confidence, "perspective": {"reference": ref_p, "candidate": cand_p, "mismatch_deg": round(float(mismatch), 2) if mismatch is not None else None, "warning": warning}, "method_agreement": {"marker_polar_delta_deg": round(float(marker_polar), 3) if marker_polar is not None else None, "rating": agreement_rating}, "stable_ecc": stable, "v1_confidence_reason": "; ".join(reasons), "v1_version": V1_VERSION})
        return matrix2.astype(np.float64), metrics
    return auto_align_v1


def install(backend) -> None:
    """Install V1 improvements without altering the frozen v0.9.4 engine file."""
    if getattr(backend, "_watch_align_v1_installed", False):
        return
    backend._watch_align_v1_installed = True
    backend.app.version = V1_VERSION
    backend.auto_align = upgrade_auto_align(backend.auto_align)

    @backend.app.get("/api/v1/models")
    def v1_models():
        return {"version": V1_VERSION, "models": REFERENCE_CATALOG}

    @backend.app.get("/api/v1/capabilities")
    def v1_capabilities():
        return {"version": V1_VERSION, "perspective_diagnostics": True, "stable_masked_ecc": True, "dual_mask_ecc_available": bool(hasattr(cv2, "findTransformECCWithMask")), "reference_catalog": True, "built_in_reference_images": False, "principle": "optimise geometric truth, not visual deformation"}

    static_dir = backend.STATIC_DIR
    (static_dir / "v1.js").write_text(_V1_JS, encoding="utf-8")
    index_path = static_dir / "index.html"
    html = index_path.read_text(encoding="utf-8")
    marker = '<script src="/static/v1.js" defer></script>'
    if marker not in html:
        index_path.write_text(html.replace("</body>", f"  {marker}\n</body>"), encoding="utf-8")
