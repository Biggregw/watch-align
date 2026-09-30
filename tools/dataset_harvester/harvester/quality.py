"""Automatic photo suitability: the app's own analysis (via the Suit driver) plus a few pixel checks
on the dial region. Each image ends with explicit reason codes; "suitable" means no reject_* and no
inconclusive_* code. Thresholds come from config.THRESHOLDS."""
from __future__ import annotations

import math

import numpy as np
from PIL import Image

from .config import THRESHOLDS, Thresholds

# Reason codes. reject_* = clearly unusable; inconclusive_* = could not be decided automatically.
REJECT_UNREADABLE = "reject_unreadable"
REJECT_LOW_RES = "reject_low_resolution"
REJECT_NO_DIAL = "reject_no_dial"
REJECT_INCOMPLETE = "reject_incomplete_dial"
REJECT_POSE = "reject_pose"
REJECT_BLUR = "reject_blur"
REJECT_OCCLUSION = "reject_occlusion"
REJECT_LANDMARKS = "reject_landmarks_unusable"
REJECT_UNDER = "reject_underexposed"
REJECT_OVER = "reject_overexposed"
REJECT_GLARE = "reject_glare"
REJECT_DUPLICATE = "reject_duplicate"
INCONCLUSIVE_ANALYSIS = "inconclusive_analysis_failed"
INCONCLUSIVE_POSE = "inconclusive_pose"
INCONCLUSIVE_MEASUREMENT = "inconclusive_measurement"


def dial_geometry(suit: dict) -> dict | None:
    """Dial ellipse in ORIGINAL image pixels, from the Suit record (preview coordinates)."""
    if not (suit.get("dial_located") or suit.get("dial_found")):
        return None
    try:
        k = float(suit["orig_w"]) / float(suit["preview_w"])
        cx, cy, a, b = (float(suit[x]) * k for x in ("dial_cx", "dial_cy", "dial_a", "dial_b"))
    except (KeyError, TypeError, ValueError, ZeroDivisionError):
        return None
    return {"cx": cx, "cy": cy, "a": a, "b": b, "angle_deg": float(suit.get("dial_angle_deg") or 0.0),
            "radius": (a + b) / 2, "diameter": a + b, "w": float(suit["orig_w"]), "h": float(suit["orig_h"])}


def dial_inside(g: dict, tol: float) -> bool:
    t = math.radians(g["angle_deg"])
    ex = math.sqrt((g["a"] * math.cos(t)) ** 2 + (g["b"] * math.sin(t)) ** 2)
    ey = math.sqrt((g["a"] * math.sin(t)) ** 2 + (g["b"] * math.cos(t)) ** 2)
    m = tol * g["radius"]
    return g["cx"] - ex >= -m and g["cy"] - ey >= -m and g["cx"] + ex <= g["w"] + m and g["cy"] + ey <= g["h"] + m


def pixel_metrics(img: Image.Image, g: dict) -> dict:
    """Sharpness, clipping and glare inside the dial (0.95 of the radius), resolution-independent."""
    r = g["radius"] * 0.95
    box = (int(round(g["cx"] - r)), int(round(g["cy"] - r)), int(round(g["cx"] + r)), int(round(g["cy"] + r)))
    crop = img.crop(box).convert("RGB").resize((512, 512), Image.Resampling.LANCZOS)
    rgb = np.asarray(crop, dtype=np.float64)
    yy, xx = np.mgrid[0:512, 0:512]
    mask = (xx - 255.5) ** 2 + (yy - 255.5) ** 2 <= 255.5 ** 2
    luma = 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]
    lap = 4 * luma[1:-1, 1:-1] - luma[:-2, 1:-1] - luma[2:, 1:-1] - luma[1:-1, :-2] - luma[1:-1, 2:]
    inner = ((xx - 255.5) ** 2 + (yy - 255.5) ** 2 <= (255.5 * 0.9) ** 2)[1:-1, 1:-1]
    hsv = np.asarray(crop.convert("HSV"), dtype=np.float64)
    m = mask
    n = float(m.sum())
    return {
        "sharpness": float(lap[inner].var()),
        "crushed_fraction": float((luma[m] <= 3).sum() / n),
        "blown_fraction": float((rgb[m].max(axis=1) >= 252).sum() / n),
        "glare_fraction": float(((hsv[..., 2][m] >= 235) & (hsv[..., 1][m] <= 40)).sum() / n),
        "mean_luma": float(luma[m].mean()),
    }


def perspective(suit: dict) -> dict:
    """Pose / perspective diagnostics (recorded, not used to correct anything)."""
    out = {}
    for side in ("top", "bottom", "left", "right"):
        if suit.get(f"rehaut_{side}_px") is not None:
            out[f"rehaut_{side}_px"] = suit[f"rehaut_{side}_px"]
            out[f"rehaut_{side}_coverage"] = suit.get(f"rehaut_{side}_cov")
    try:
        out["rehaut_top_bottom_ratio"] = suit["rehaut_top_px"] / suit["rehaut_bottom_px"]
        out["rehaut_left_right_ratio"] = suit["rehaut_left_px"] / suit["rehaut_right_px"]
        out["rehaut_confidence"] = min(suit[f"rehaut_{s}_cov"] for s in ("top", "bottom", "left", "right"))
    except (KeyError, TypeError, ZeroDivisionError):
        pass
    for k in ("rehaut_v_asym", "rehaut_h_asym", "rehaut_min_over_mean", "ellipse_ratio", "ellipse_tilt_deg",
              "ellipse_minor_axis_clock_deg", "marker_tilt_deg", "marker_pose_valid"):
        if suit.get(k) is not None:
            out[k] = suit[k]
    # Which way the dial is foreshortened: the minor axis of the dial ellipse points along the
    # tilt. Near 12-6 (0/180 deg on the clock) = top/bottom perspective, near 3-9 = left/right.
    ax = suit.get("ellipse_minor_axis_clock_deg")
    if ax is not None and suit.get("ellipse_tilt_deg") is not None:
        a = float(ax) % 180.0
        d = min(a, 180.0 - a)
        out["perspective_axis"] = "top_bottom" if d < 30 else "left_right" if d > 60 else "diagonal"
    return out


def assess(img: Image.Image | None, suit: dict | None, t: Thresholds = THRESHOLDS) -> dict:
    """{suitable, reasons, quality, perspective, details}."""
    reasons: list[str] = []
    details: list[str] = []
    q: dict = {}
    if img is None:
        return {"suitable": False, "reasons": [REJECT_UNREADABLE], "quality": q, "perspective": {}, "details": details}
    w, h = img.size
    q["width"], q["height"] = w, h
    if min(w, h) < t.min_image_side_px:
        reasons.append(REJECT_LOW_RES)
        details.append(f"shortest side {min(w, h)} px < {t.min_image_side_px}")
    if not suit or suit.get("error"):
        reasons.append(INCONCLUSIVE_ANALYSIS)
        details.append((suit or {}).get("error", "no analysis result"))
        return {"suitable": False, "reasons": reasons, "quality": q, "perspective": {}, "details": details}
    persp = perspective(suit)
    g = dial_geometry(suit)
    if g is None:
        reasons.append(REJECT_NO_DIAL)
        if suit.get("no_readable_dial"):
            details.append("app: no readable dial (outlines found, not judged a dial; nothing measured)")
        return {"suitable": False, "reasons": reasons, "quality": q, "perspective": persp, "details": details}
    q["dial_diameter_px"] = round(g["diameter"], 1)
    if suit.get("no_readable_dial"):
        # The analyser located a dial but judged its landmarks unreadable (few markers, no stable
        # minute frame). The phone calls this "no dial found"; it is a landmark failure here.
        reasons.append(REJECT_LANDMARKS)
        details.append("app: dial located but its landmarks are not readable")
    if g["diameter"] < t.min_dial_diameter_px and REJECT_LOW_RES not in reasons:
        reasons.append(REJECT_LOW_RES)
        details.append(f"dial diameter {g['diameter']:.0f} px < {t.min_dial_diameter_px:.0f}")
    if not dial_inside(g, t.dial_edge_tolerance):
        reasons.append(REJECT_INCOMPLETE)
        details.append("dial ellipse extends beyond the frame")
    else:
        pm = pixel_metrics(img, g)
        q.update({k: round(v, 4) for k, v in pm.items()})
        if pm["sharpness"] < t.min_dial_sharpness:
            reasons.append(REJECT_BLUR)
            details.append(f"dial sharpness {pm['sharpness']:.1f} < {t.min_dial_sharpness}")
        if pm["crushed_fraction"] > t.max_crushed_fraction:
            reasons.append(REJECT_UNDER)
            details.append(f"{pm['crushed_fraction']:.0%} of the dial crushed to black")
        if pm["blown_fraction"] > t.max_blown_fraction:
            reasons.append(REJECT_OVER)
            details.append(f"{pm['blown_fraction']:.0%} of the dial clipped white")
        if pm["glare_fraction"] > t.max_glare_fraction:
            reasons.append(REJECT_GLARE)
            details.append(f"{pm['glare_fraction']:.0%} of the dial under veiling glare")
    q["round_markers_found"] = suit.get("round_found")
    q["round_markers_total"] = suit.get("round_total")
    if (suit.get("round_found") or 0) < t.min_round_markers_found:
        details.append(f"only {suit.get('round_found')} round markers found (marker-layout pose unavailable)")
    if not suit.get("twelve_found") and not suit.get("no_readable_dial"):
        if suit.get("hand_at_twelve"):
            reasons.append(REJECT_OCCLUSION)
            details.append("a hand covers the 12 marker")
        elif suit.get("too_small"):
            if REJECT_LOW_RES not in reasons:
                reasons.append(REJECT_LOW_RES)
            details.append("12 marker too small to measure")
        else:
            reasons.append(REJECT_LANDMARKS)
            details.append("12 marker not found" + (f": {suit.get('twelve_not_judged')}" if suit.get("twelve_not_judged") else ""))
    pose = suit.get("pose", "")
    q["pose"] = pose
    if pose in t.reject_pose_labels:
        reasons.append(REJECT_POSE)
        details.append(f"app pose {pose}" + (f" ({persp['perspective_axis'].replace('_', '/')} perspective)" if persp.get("perspective_axis") else ""))
    elif pose in t.inconclusive_pose_labels and suit.get("twelve_found"):
        reasons.append(INCONCLUSIVE_POSE)
        details.append("app could not assess the pose")
    return {"suitable": not reasons, "reasons": reasons, "quality": q, "perspective": persp, "details": details}
