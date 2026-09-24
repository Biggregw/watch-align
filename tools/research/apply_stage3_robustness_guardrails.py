#!/usr/bin/env python3
"""Apply research-only robustness guardrails to the pinned Stage 3 module.

This deliberately patches the checked-out pinned implementation only inside the
Actions workspace. It does not modify the frozen Stage 3 source commit or Android
production QC. The thresholds are derived from the deterministic perturbation
corpus: the observed triangle branch switch reached 7.03 deg axis disagreement,
and the bad projective branch had 0.0066-0.0068 canonical fit residual while all
accepted non-switched corpus fits remained below 0.005.
"""
from pathlib import Path

p = Path("tools/watch_align_py/gmt_proportional_features.py")
s = p.read_text(encoding="utf-8")

old_tri = '''    diag["triangle_n_contour_points"] = tri.n_contour_points\n\n    # Canonical (affine-normalised, "simple") axis coordinates for every\n'''
new_tri = '''    diag["triangle_n_contour_points"] = tri.n_contour_points\n\n    # Fail closed when the independently segmented triangle has switched to a\n    # geometrically implausible axis solution. The perturbation corpus exposed\n    # a discrete branch switch at 7.03 deg; do not turn that detector failure\n    # into apparently precise QC features.\n    if tri.axis_agreement_deg is None or tri.axis_agreement_deg > 5.0:\n        diag["triangle_suppressed_reason"] = (\n            "triangle axis disagreement exceeds 5 deg robustness guardrail"\n        )\n        return out\n\n    # Canonical (affine-normalised, "simple") axis coordinates for every\n'''
if old_tri not in s:
    raise SystemExit("triangle patch anchor not found")
s = s.replace(old_tri, new_tri, 1)

old_fit = '''    if fit is None or not fit.condition_ok:\n        diag["projective_suppressed_reason"] = (\n            "projective fit failed" if fit is None else "projective fit ill-conditioned near observed t range"\n        )\n        return out\n\n    apex_r_proj = pr.invert_projective_1d(fit, apex_r)\n'''
new_fit = '''    if fit is None or not fit.condition_ok:\n        diag["projective_suppressed_reason"] = (\n            "projective fit failed" if fit is None else "projective fit ill-conditioned near observed t range"\n        )\n        return out\n    # Conditioning alone does not catch a wrong corridor/projective branch.\n    # The deterministic perturbation corpus showed the switched WOS branch at\n    # 0.0066-0.0068 canonical residual, versus <0.005 for non-switched fits.\n    # Reject the transform rather than propagate a discontinuous correction.\n    if fit.max_abs_residual > 0.005:\n        diag["projective_suppressed_reason"] = (\n            "projective fit residual exceeds 0.005 canonical robustness guardrail"\n        )\n        return out\n\n    apex_r_proj = pr.invert_projective_1d(fit, apex_r)\n'''
if old_fit not in s:
    raise SystemExit("projective patch anchor not found")
s = s.replace(old_fit, new_fit, 1)
p.write_text(s, encoding="utf-8")
print("applied Stage 3 research robustness guardrails")
