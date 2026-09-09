"""Estimate a full-turn in-plane correction from fixed watch geometry.

Polar correlation preserves radial detail, so the 12 o'clock marker, dial
printing and date aperture can disambiguate the repeating hour markers. Work
is bounded to a small normalized dial; originals are never rotated in place.
"""
from __future__ import annotations

import cv2
import numpy as np

ANGLE_SAMPLES = 1440


def rotated_analysis_view(image, circle, correction_deg):
    """Rotate only (no perspective/scale/appearance changes) for measurements.

    Expand the canvas to keep the complete source photo, and transform the
    known circle center analytically instead of detecting it again.
    """
    if abs(correction_deg) < .01:
        return image, circle
    h, w = image.shape[:2]
    matrix = cv2.getRotationMatrix2D((w / 2, h / 2), correction_deg, 1.0)
    cosine, sine = abs(matrix[0, 0]), abs(matrix[0, 1])
    width, height = int(np.ceil(w * cosine + h * sine)), int(np.ceil(h * cosine + w * sine))
    matrix[0, 2] += width / 2 - w / 2
    matrix[1, 2] += height / 2 - h / 2
    rotated = cv2.warpAffine(image, matrix, (width, height), flags=cv2.INTER_LINEAR,
                             borderMode=cv2.BORDER_CONSTANT, borderValue=(18, 18, 18))
    cx, cy = matrix @ np.array([circle[0], circle[1], 1.0])
    return rotated, (float(cx), float(cy), float(circle[2]))


def _polar_geometry(image, circle):
    cx, cy, radius = map(float, circle)
    side = 384
    extent = 1.12
    axis = np.linspace(-extent, extent, side, dtype=np.float32)
    xx, yy = np.meshgrid(axis, axis)
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    normalized = cv2.remap(gray, cx + xx * radius, cy + yy * radius,
                           cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=0)
    normalized = cv2.GaussianBlur(normalized.astype(np.float32), (0, 0), .8)
    gx = cv2.Sobel(normalized, cv2.CV_32F, 1, 0)
    gy = cv2.Sobel(normalized, cv2.CV_32F, 0, 1)
    geometry = np.log1p(cv2.magnitude(gx, gy))
    radii = np.linspace(.25, 1.04, 128, dtype=np.float32)
    angles = np.arange(ANGLE_SAMPLES, dtype=np.float32) * (2 * np.pi / ANGLE_SAMPLES)
    xs = np.cos(angles[:, None]) * radii[None, :]
    ys = np.sin(angles[:, None]) * radii[None, :]
    factor = (side - 1) / (2 * extent)
    polar = cv2.remap(geometry, (xs + extent) * factor, (ys + extent) * factor,
                      cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=0)
    centered = polar - polar.mean(axis=0, keepdims=True)
    deviation = centered.std(axis=0)
    textured = deviation > max(.15, float(deviation.max()) * .15)
    centered /= np.maximum(deviation, .15)[None, :]
    return centered, textured, radii


def estimate_rotation(reference, candidate, reference_circle, candidate_circle):
    """Return an OpenCV correction angle or None when orientation is ambiguous."""
    ref, ref_valid, radii = _polar_geometry(reference, reference_circle)
    cand, cand_valid, _ = _polar_geometry(candidate, candidate_circle)
    valid = ref_valid & cand_valid
    if np.count_nonzero(valid) < 12:
        return None, {'confidence': 'low', 'applied': False, 'reason': 'Not enough dial detail to determine rotation.'}
    # Include inner dial detail to distinguish otherwise identical 30-degree
    # marker rotations, while giving the stable outer dial most of the weight.
    correlation = np.zeros(ANGLE_SAMPLES, dtype=np.float64)
    weight_sum = 0.0
    for low, high, weight in ((.25, .60, .30), (.60, .90, .55), (.90, 1.05, .15)):
        selected = valid & (radii >= low) & (radii < high)
        if np.count_nonzero(selected) < 4:
            continue
        spectrum = np.fft.fft(ref[:, selected], axis=0) * np.conj(np.fft.fft(cand[:, selected], axis=0))
        band = np.fft.ifft(spectrum, axis=0).real.mean(axis=1) / ANGLE_SAMPLES
        correlation += weight * band
        weight_sum += weight
    if not weight_sum:
        return None, {'confidence': 'low', 'applied': False, 'reason': 'Not enough stable dial detail.'}
    correlation /= weight_sum
    peak = int(np.argmax(correlation))
    distances = np.abs((np.arange(ANGLE_SAMPLES) - peak + ANGLE_SAMPLES // 2) % ANGLE_SAMPLES - ANGLE_SAMPLES // 2)
    other = float(np.max(correlation[distances > ANGLE_SAMPLES * 8 / 360]))
    score = float(correlation[peak])
    margin = score - other
    previous, following = correlation[(peak - 1) % ANGLE_SAMPLES], correlation[(peak + 1) % ANGLE_SAMPLES]
    curvature = previous - 2 * score + following
    offset = float(np.clip(.5 * (previous - following) / curvature, -.5, .5)) if curvature < -1e-9 else 0.0
    # Polar rows increase clockwise; OpenCV's positive rotation is anticlockwise.
    angle = float((-(peak + offset) * 360 / ANGLE_SAMPLES + 180) % 360 - 180)
    reliable = score >= .16 and margin >= .035
    confidence = 'high' if score >= .50 and margin >= .08 else 'medium' if reliable else 'low'
    diagnostics = {'applied': reliable, 'confidence': confidence, 'correction_deg': round(angle, 3),
                   'score': round(score, 4), 'alternative_score': round(other, 4), 'margin': round(margin, 4),
                   'reason': 'Dial orientation matched.' if reliable else 'Dial orientation is ambiguous; check the overlay manually.'}
    return angle if reliable else None, diagnostics
