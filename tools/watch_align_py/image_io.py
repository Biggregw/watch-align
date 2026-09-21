"""Shared image loading helper. Mirrors MainActivity.readBitmap / the Android
androidTest decode() helper: decode at full resolution, then if the longest
side exceeds max_dim, scale down with bilinear filtering (matching
Bitmap.createScaledBitmap(..., true)). Used by run.py and analyze_corpus.py so
every entry point sees the same production-fidelity input.
"""
import cv2
import numpy as np


def resize_to_max_dim(bgr: np.ndarray, max_dim: int = 1600) -> np.ndarray:
    h, w = bgr.shape[:2]
    current_max = max(w, h)
    if current_max <= max_dim:
        return bgr
    scale = max_dim / current_max
    new_w = round(w * scale)
    new_h = round(h * scale)
    return cv2.resize(bgr, (new_w, new_h), interpolation=cv2.INTER_LINEAR)


def decode_capped(path: str, max_dim: int = 1600) -> np.ndarray:
    raw = cv2.imread(path, cv2.IMREAD_COLOR)
    if raw is None:
        raise ValueError(f"could not decode {path}")
    return resize_to_max_dim(raw, max_dim)
