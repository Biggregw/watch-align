#!/usr/bin/env python3
"""Part 10 visual diagnostics: fitted conic(s), detected marker centres/
inner/outer points, nominal hour axes, leave-one-out predicted vs actual
position, residual vectors, local minute-track clearance, marker-under-
test highlighted. Research diagnostics -- deliberately more detailed than
any eventual user-facing overlay would be (Part 10 explicitly notes this).
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path

import cv2
import numpy as np

import marker_consensus as mc
import marker_consensus_analysis as mca
import pipeline
from image_io import resize_to_max_dim

GREEN = (60, 200, 60)
YELLOW = (0, 220, 220)
RED = (0, 0, 255)
CYAN = (255, 220, 0)
MAGENTA = (220, 0, 220)
ORANGE = (0, 140, 255)
WHITE = (255, 255, 255)


def _draw_conic(img: np.ndarray, Q: np.ndarray, center: tuple, color, n=180) -> None:
    """Sample the conic along rays from `center` (works for the ellipse-
    like conics this module deals with) and draw the resulting polyline."""
    pts = []
    for i in range(n):
        angle = 2.0 * math.pi * i / n
        p = mc.conic_ray_point(Q, center, angle, 100.0)
        if p is not None and math.isfinite(p[0]) and math.isfinite(p[1]):
            pts.append((int(round(p[0])), int(round(p[1]))))
    if len(pts) > 2:
        cv2.polylines(img, [np.array(pts)], True, color, 1, cv2.LINE_AA)


def render_one(image_path: Path, highlight_hour: int = None) -> tuple:
    raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    if raw is None:
        return None, "undecodable"
    bgr = resize_to_max_dim(raw, 1600)
    result = pipeline.build(bgr)
    if result.reason or result.acquisition is None or result.acquisition.dial_ellipse is None:
        return None, f"acquisition failed: {result.reason}"

    ellipse = result.acquisition.dial_ellipse
    roll = result.acquisition.roll_deg
    dial_radius_px = (max(ellipse.w, ellipse.h) + min(ellipse.w, ellipse.h)) / 4.0
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 1.2)
    edges = cv2.Canny(blur, 55, 145)
    analysis = mca.analyze_frame(gray, edges, ellipse, roll, dial_radius_px)

    out = bgr.copy()
    center = (ellipse.cx, ellipse.cy)
    cv2.drawMarker(out, (int(center[0]), int(center[1])), WHITE, cv2.MARKER_CROSS, 14, 2)

    # full-sample round conics (diagnostic context, not what any marker is judged against)
    for which, color in (("outer", GREEN), ("centre", CYAN), ("inner", ORANGE)):
        summary = analysis.round_conics.get(which)
        if summary is None:
            continue
        hours_present = [h for h in mc.ROUND_HOURS if h in analysis.observations]
        pts = np.array([mca._point_for(analysis.observations[h], which) for h in hours_present])
        Q = mc.fit_conic(pts)
        if Q is not None:
            _draw_conic(out, Q, center, color)

    # nominal hour axes (faint lines from centre outward)
    for h in mc.ALL_MARKER_HOURS:
        u, v = mca._nominal_direction(ellipse, roll, h)
        far = (int(center[0] + u * dial_radius_px * 1.05), int(center[1] + v * dial_radius_px * 1.05))
        cv2.line(out, (int(center[0]), int(center[1])), far, (90, 90, 90), 1, cv2.LINE_AA)

    for h, obs in analysis.observations.items():
        is_highlight = (h == highlight_hour)
        thick = 3 if is_highlight else 1
        r_c = 7 if is_highlight else 4
        cv2.circle(out, (int(obs.centroid_x), int(obs.centroid_y)), r_c, MAGENTA, -1)
        cv2.drawMarker(out, (int(obs.outer_x), int(obs.outer_y)), GREEN, cv2.MARKER_TILTED_CROSS, 10, thick)
        cv2.drawMarker(out, (int(obs.inner_x), int(obs.inner_y)), ORANGE, cv2.MARKER_TILTED_CROSS, 10, thick)

        loo = analysis.outer_loo.get(h)
        if loo is not None and loo.fit_ok:
            p_pred = tuple(int(round(v)) for v in loo.predicted_xy)
            p_act = tuple(int(round(v)) for v in loo.actual_xy)
            cv2.drawMarker(out, p_pred, YELLOW, cv2.MARKER_DIAMOND, 12, 2)
            cv2.arrowedLine(out, p_pred, p_act, RED, 1, cv2.LINE_AA, tipLength=0.3)

        clr = analysis.clearance.get(h)
        verdict = analysis.verdicts.get(h)
        label_color = WHITE
        if verdict is not None:
            label_color = {"NO_ISSUE": GREEN, "POSSIBLE_ISSUE": (0, 200, 255),
                            "CLEAR_ANOMALY": RED, "INSUFFICIENT_CONFIDENCE": (150, 150, 150)}.get(verdict.state, WHITE)
        label = f"{h}"
        if verdict is not None:
            label += f" {verdict.state.replace('_', ' ').title()}"
        cv2.putText(out, label, (int(obs.outer_x) + 8, int(obs.outer_y) - 8),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, label_color, 2 if is_highlight else 1, cv2.LINE_AA)

    legend_y = 26
    for text, color in [
        ("magenta=centroid  green x=outer  orange x=inner", WHITE),
        ("green/cyan/orange ring = full-sample round centre/outer/inner conic", WHITE),
        ("yellow diamond=leave-one-out predicted  red arrow=residual vector", WHITE),
    ]:
        cv2.putText(out, text, (10, legend_y), cv2.FONT_HERSHEY_SIMPLEX, 0.45, color, 1, cv2.LINE_AA)
        legend_y += 20

    return out, analysis


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images-dir", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    args = ap.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    for img in sorted(args.images_dir.glob("*.jpg")):
        out, info = render_one(img)
        if out is None:
            print(f"{img.name}: SKIPPED ({info})")
            continue
        out_path = args.out_dir / f"{img.stem}_consensus_diagnostic.jpg"
        cv2.imwrite(str(out_path), out, [cv2.IMWRITE_JPEG_QUALITY, 92])
        print(f"{img.name}: wrote {out_path.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
