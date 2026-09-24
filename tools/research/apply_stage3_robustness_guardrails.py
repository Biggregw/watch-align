#!/usr/bin/env python3
"""Apply research-only Stage 3 robustness fixes to the pinned implementation.

The Actions workflow first checks out the exact frozen Stage 3 source, then this
script patches only that temporary workspace. Production Android QC and the
frozen source commit remain untouched.
"""
from pathlib import Path

# 1. Stabilise triangle segmentation itself. The perturbation experiment uses
# the same source image while varying only supplied pose. Previously Otsu was
# recomputed from the pose-dependent ROI crop, so a sub-pixel pose change could
# alter the crop histogram, threshold and selected contour. Compute the Otsu
# threshold once from the full blurred source image, then apply the independent
# marker-band mask. Pose may move the mask, but cannot change image binarisation.
tp = Path("tools/watch_align_py/triangle_measurement.py")
ts = tp.read_text(encoding="utf-8")
old_threshold = '''    blur = cv2.GaussianBlur(roi_gray, (3, 3), 0.8)\n    _, binary = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)\n    binary = cv2.bitwise_and(binary, binary, mask=roi_mask)\n'''
new_threshold = '''    # Thresholding must not depend on the exact pose-derived ROI crop. A tiny\n    # ellipse/centre perturbation otherwise changes the Otsu histogram and can\n    # switch the selected contour even though the source image is identical.\n    full_blur = cv2.GaussianBlur(gray, (3, 3), 0.8)\n    otsu_level, _ = cv2.threshold(full_blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)\n    blur = full_blur[y0:y1, x0:x1]\n    _, binary = cv2.threshold(blur, otsu_level, 255, cv2.THRESH_BINARY)\n    binary = cv2.bitwise_and(binary, binary, mask=roi_mask)\n'''
if old_threshold not in ts:
    raise SystemExit("triangle threshold patch anchor not found")
ts = ts.replace(old_threshold, new_threshold, 1)

# 2. Stabilise base-corner extraction. Average a small tangential extreme set
# instead of selecting one argmin/argmax contour pixel.
old_corners = '''    left_i = np.argmin(tang_proj)\n    right_i = np.argmax(tang_proj)\n    base_left_xy = tuple(outward_pts[left_i])\n    base_right_xy = tuple(outward_pts[right_i])\n'''
new_corners = '''    # A single argmin/argmax contour sample is discontinuous under tiny ROI /\n    # threshold changes. Use the mean of a small fixed extreme subset on each\n    # side, matching the already-robust apex strategy.\n    n_corner = min(N_EXTREME_SUBSET, max(1, len(outward_pts) // 4))\n    tang_order = np.argsort(tang_proj)\n    base_left_xy = tuple(outward_pts[tang_order[:n_corner]].mean(axis=0))\n    base_right_xy = tuple(outward_pts[tang_order[-n_corner:]].mean(axis=0))\n'''
if old_corners not in ts:
    raise SystemExit("triangle corner patch anchor not found")
ts = ts.replace(old_corners, new_corners, 1)
tp.write_text(ts, encoding="utf-8")

# 3. Fail closed on clearly inconsistent triangle/projective solutions.
p = Path("tools/watch_align_py/gmt_proportional_features.py")
s = p.read_text(encoding="utf-8")
old_tri = '''    diag["triangle_n_contour_points"] = tri.n_contour_points\n\n    # Canonical (affine-normalised, "simple") axis coordinates for every\n'''
new_tri = '''    diag["triangle_n_contour_points"] = tri.n_contour_points\n\n    if tri.axis_agreement_deg is None or tri.axis_agreement_deg > 5.0:\n        diag["triangle_suppressed_reason"] = (\n            "triangle axis disagreement exceeds 5 deg robustness guardrail"\n        )\n        return out\n\n    # Canonical (affine-normalised, "simple") axis coordinates for every\n'''
if old_tri not in s:
    raise SystemExit("triangle guardrail patch anchor not found")
s = s.replace(old_tri, new_tri, 1)

old_fit = '''    if fit is None or not fit.condition_ok:\n        diag["projective_suppressed_reason"] = (\n            "projective fit failed" if fit is None else "projective fit ill-conditioned near observed t range"\n        )\n        return out\n\n    apex_r_proj = pr.invert_projective_1d(fit, apex_r)\n'''
new_fit = '''    if fit is None or not fit.condition_ok:\n        diag["projective_suppressed_reason"] = (\n            "projective fit failed" if fit is None else "projective fit ill-conditioned near observed t range"\n        )\n        return out\n    if fit.max_abs_residual > 0.005:\n        diag["projective_suppressed_reason"] = (\n            "projective fit residual exceeds 0.005 canonical robustness guardrail"\n        )\n        return out\n\n    apex_r_proj = pr.invert_projective_1d(fit, apex_r)\n'''
if old_fit not in s:
    raise SystemExit("projective patch anchor not found")
s = s.replace(old_fit, new_fit, 1)
p.write_text(s, encoding="utf-8")
print("applied Stage 3 research robustness fixes")
