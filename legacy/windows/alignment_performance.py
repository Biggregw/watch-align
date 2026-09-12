"""Bound the expensive residual affine search, preserving full-size coordinates."""
import cv2
import numpy as np


def affine_ecc(reference, aligned, mask, max_side=720):
    h, w = reference.shape[:2]
    factor = min(1.0, max_side / max(h, w))
    width, height = max(1, round(w * factor)), max(1, round(h * factor))
    if factor < 1:
        reference = cv2.resize(reference, (width, height), interpolation=cv2.INTER_AREA)
        aligned = cv2.resize(aligned, (width, height), interpolation=cv2.INTER_AREA)
        mask = cv2.resize(mask, (width, height), interpolation=cv2.INTER_NEAREST)
    score, inverse = cv2.findTransformECC(
        reference, aligned, np.eye(2, 3, dtype=np.float32), cv2.MOTION_AFFINE,
        (cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 150, 1e-6),
        inputMask=mask, gaussFiltSize=5,
    )
    # Rounded resize dimensions can have slightly different x/y scales.
    scale = np.diag([width / w, height / h, 1.0])
    full = np.linalg.inv(scale) @ np.vstack([inverse, [0, 0, 1]]) @ scale
    return score, full[:2].astype(np.float32)
