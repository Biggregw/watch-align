"""Deterministic dial crop for the AI-reviewer blind batch
(experiment/generic-qc-reticle, Stage 1 batch).

The crop box is a pure function of Watch Align's own automatically
detected dial geometry (ellipse centre + measured dial radius) -- never
of where any marker or suspected defect happens to be. It is a square
region centred on the detected dial centre, sized to comfortably contain
the full minute track and dial edge with a small margin, then resized to
a fixed target resolution so every reviewer receives comparable
inspection detail regardless of the source photo's native resolution.
"""
from __future__ import annotations

from typing import Tuple

import cv2
import numpy as np

from geometry import RotatedRect

CROP_MARGIN = 1.08     # x dial_radius_px -- clears the full minute track + a hair of bezel
CROP_TARGET_DIM = 1400  # every crop is resized so its longer side is this many pixels


def compute_crop_box(cx: float, cy: float, dial_radius_px: float, img_shape) -> Tuple[int, int, int, int]:
    """Square box centred on (cx, cy), independent of any marker position."""
    h, w = img_shape[:2]
    half = dial_radius_px * CROP_MARGIN
    x0 = max(0, int(round(cx - half)))
    y0 = max(0, int(round(cy - half)))
    x1 = min(w, int(round(cx + half)))
    y1 = min(h, int(round(cy + half)))
    return x0, y0, x1, y1


def dial_crop(bgr: np.ndarray, ellipse: RotatedRect, dial_radius_px: float) -> np.ndarray:
    x0, y0, x1, y1 = compute_crop_box(ellipse.cx, ellipse.cy, dial_radius_px, bgr.shape)
    crop = bgr[y0:y1, x0:x1]
    h, w = crop.shape[:2]
    if h == 0 or w == 0:
        raise ValueError("dial crop box degenerate -- ellipse centre or radius invalid")
    scale = CROP_TARGET_DIM / max(h, w)
    interp = cv2.INTER_CUBIC if scale > 1.0 else cv2.INTER_AREA
    return cv2.resize(crop, (max(1, round(w * scale)), max(1, round(h * scale))), interpolation=interp)
