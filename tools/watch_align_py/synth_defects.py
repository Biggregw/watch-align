"""Synthetic marker-defect illustration, generalised for the Stage 1 AI
A/B batch (experiment/generic-qc-reticle).

No real labelled-defective photos are available for this corpus, so
defects are illustrated by editing real calibration photographs: the
marker is removed from its true position with cv2.inpaint and a modified
copy (rotated in place, or translated tangentially/radially) is blended
back in with a Gaussian-feathered circular mask. This is the same
technique validated earlier in this research phase (rotated-baton,
displaced-marker illustrations); this module only generalises it to a
single reusable function parameterised by hour/mode/magnitude so a batch
of synthetic examples can be generated deterministically and repeatably.

Nothing here decides whether an edit "looks like" a real defect -- it is
a controlled geometric perturbation of a real marker, at a stated
magnitude, nothing more.
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Tuple

import cv2
import numpy as np

import geometry
import master
from geometry import RotatedRect

# Circular edit-mask radius per shape, as a fraction of dial_radius_px --
# generous enough to fully cover the marker's own validated extent
# (master.py radii) plus margin, deliberately not tied to segmentation
# succeeding on any particular photo.
_MASK_FRAC = {"round": 0.11, "baton": 0.17, "triangle": 0.25}


def _shape_for_hour(hour: int) -> str:
    return "triangle" if hour == 12 else ("baton" if hour in (6, 9) else "round")


def _center_canon_r(shape: str) -> float:
    return {"round": master.ROUND_CENTER_R, "baton": master.BATON_CENTER_R,
            "triangle": master.TRI_CENTER_R}[shape]


@dataclass
class SyntheticDefect:
    hour: int
    shape: str
    mode: str            # "rotate" | "tangential" | "radial_in" | "radial_out"
    magnitude: float      # degrees for rotate/tangential; canonical radius fraction for radial
    old_center_xy: Tuple[float, float]
    new_center_xy: Tuple[float, float]
    description: str


def _feather_mask(shape_hw, center, radius) -> np.ndarray:
    mask = np.zeros(shape_hw, dtype=np.uint8)
    cv2.circle(mask, (int(round(center[0])), int(round(center[1]))), int(round(radius)), 255, -1)
    mask = cv2.GaussianBlur(mask, (0, 0), sigmaX=radius * 0.12)
    return mask.astype(np.float32) / 255.0


def apply_synthetic_defect(bgr: np.ndarray, ellipse: RotatedRect, roll: float, dial_radius_px: float,
                            hour: int, mode: str, magnitude: float) -> Tuple[np.ndarray, SyntheticDefect]:
    """Returns (edited_bgr, SyntheticDefect ground-truth record). Does not
    mutate `bgr`. `ellipse`/`roll`/`dial_radius_px` are the ALREADY-detected
    automatic pose of the unedited photo -- reused as-is, never re-derived
    from the edited pixels, so the dial pose (and therefore any downstream
    crop box) never shifts because of the edit."""
    if mode not in ("rotate", "tangential", "radial_in", "radial_out"):
        raise ValueError(f"unknown mode {mode!r}")
    shape = _shape_for_hour(hour)
    r0 = _center_canon_r(shape)
    angle0 = master.angle_for_hour(hour)
    old_center = geometry.map_point(ellipse, 1.0, roll, r0 * math.cos(angle0), r0 * math.sin(angle0))

    if mode == "rotate":
        new_r, new_angle = r0, angle0
        desc = f"{shape} at hour {hour} rotated in place by {magnitude:.1f} deg"
    elif mode == "tangential":
        new_r, new_angle = r0, angle0 + math.radians(magnitude)
        desc = f"{shape} at hour {hour} displaced tangentially by {magnitude:.1f} deg of arc"
    elif mode == "radial_in":
        new_r, new_angle = r0 - magnitude, angle0
        desc = f"{shape} at hour {hour} displaced inward by {magnitude:.3f} canonical radius units"
    else:  # radial_out
        new_r, new_angle = r0 + magnitude, angle0
        desc = f"{shape} at hour {hour} displaced outward by {magnitude:.3f} canonical radius units"
    new_center = geometry.map_point(ellipse, 1.0, roll, new_r * math.cos(new_angle), new_r * math.sin(new_angle))

    mask_radius = _MASK_FRAC[shape] * dial_radius_px
    h, w = bgr.shape[:2]
    pad = int(math.ceil(mask_radius * 1.6))

    def clamp_box(cx, cy):
        x0, y0 = max(0, int(cx - pad)), max(0, int(cy - pad))
        x1, y1 = min(w, int(cx + pad)), min(h, int(cy + pad))
        return x0, y0, x1, y1

    # 1. Remove the marker from its original location via inpainting.
    old_mask = np.zeros((h, w), dtype=np.uint8)
    cv2.circle(old_mask, (int(round(old_center[0])), int(round(old_center[1]))), int(round(mask_radius)), 255, -1)
    base = cv2.inpaint(bgr, old_mask, inpaintRadius=max(3, int(mask_radius * 0.25)), flags=cv2.INPAINT_TELEA)

    # 2. Extract the original marker patch (rotate in place if requested).
    ox0, oy0, ox1, oy1 = clamp_box(*old_center)
    patch = bgr[oy0:oy1, ox0:ox1].copy()
    if mode == "rotate":
        ph, pw = patch.shape[:2]
        pcx, pcy = old_center[0] - ox0, old_center[1] - oy0
        M = cv2.getRotationMatrix2D((pcx, pcy), magnitude, 1.0)
        patch = cv2.warpAffine(patch, M, (pw, ph), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)

    # 3. Paste the (possibly rotated) patch at the new location with a
    # Gaussian-feathered circular blend, on top of the inpainted base.
    nx0, ny0, nx1, ny1 = clamp_box(*new_center)
    ph, pw = patch.shape[:2]
    dst_w, dst_h = nx1 - nx0, ny1 - ny0
    if (ph, pw) != (dst_h, dst_w):
        patch = cv2.resize(patch, (dst_w, dst_h), interpolation=cv2.INTER_LINEAR)
    local_center = (new_center[0] - nx0, new_center[1] - ny0)
    feather = _feather_mask((dst_h, dst_w), local_center, mask_radius)[..., None]
    out = base.copy()
    region = out[ny0:ny1, nx0:nx1].astype(np.float32)
    blended = region * (1.0 - feather) + patch.astype(np.float32) * feather
    out[ny0:ny1, nx0:nx1] = np.clip(blended, 0, 255).astype(np.uint8)

    record = SyntheticDefect(hour=hour, shape=shape, mode=mode, magnitude=magnitude,
                              old_center_xy=old_center, new_center_xy=new_center, description=desc)
    return out, record
