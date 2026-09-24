#!/usr/bin/env python3
"""Apply research-only Stage 3 robustness fixes to the pinned implementation.

The Actions workflow first checks out the exact frozen Stage 3 source, then this
script patches only that temporary workspace. Production Android QC and the
frozen source commit remain untouched.
"""
from pathlib import Path

# 1. Stabilise triangle base-corner extraction. The discontinuity diagnostics
# showed large incidence jumps even when PCA/symmetry agreement remained small.
# The detector selected each base corner from one argmin/argmax contour point,
# so a one-pixel contour change could replace a corner abruptly. Average a small
# tangential extreme set on each side instead, matching the already-robust apex
# strategy while preserving the same independent, position-free segmentation.
tp = Path("tools/watch_align_py/triangle_measurement.py")
ts = tp.read_text(encoding="utf-8")
old_corners = '''    left_i = np.argmin(tang_proj)\n    right_i = np.argmax(tang_proj)\n    base_left_xy = tuple(outward_pts[left_i])\n    base_right_xy = tuple(outward_pts[right_i])\n'''
new_corners = '''    # A single argmin/argmax contour sample is discontinuous under tiny ROI /\n    # threshold changes.  Use the mean of a small fixed extreme subset on each\n    # side, exactly as the apex estimate already averages its radial extreme.\n    # This changes no search prior and uses only the independently segmented\n    # contour; it merely makes corner estimation sub-pixel stable.\n    n_corner = min(N_EXTREME_SUBSET, max(1, len(outward_pts) // 4))\n    tang_order = np.argsort(tang_proj)\n    base_left_xy = tuple(outward_pts[tang_order[:n_corner]].mean(axis=0))\n    base_right_xy = tuple(outward_pts[tang_order[-n_corner:]].mean(axis=0))\n'''
if old_corners not in ts:
    raise SystemExit("triangle corner patch anchor not found")
ts = ts.replace(old_corners, new_corners, 1)
tp.write_text(ts, encoding="utf-8")

# 2. Keep the fail-closed diagnostics established by run #4. They protect
# against clearly switched triangle/projective solutions rather than emitting
# precise-looking values from a detector failure.
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
