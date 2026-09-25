#!/usr/bin/env python3
"""Phase A research harness: frontal GMT 12-triangle landmark measurement.

Research instrumentation only -- does not touch Android production QC. See
docs/architecture/QC_PRINCIPLES.md and
docs/research/GMT_12_TRIANGLE_FRONTAL_MILESTONE.md.

Usage:
    python3 tools/research/phase_a_harness.py \\
        --image path/to/image.jpg \\
        --source-id gen_wex_3KSuGhC \\
        --physical-watch-id gen_wex_3KSuGhC \\
        --out-dir phase_a_output \\
        --runs 5

Outputs into --out-dir:
    measurements.csv      -- one row per run: landmark coords + ratios
    measurements.json      -- same data, structured
    determinism_check.json -- pass/fail + per-run comparison
    overlay.png             -- diagnostic overlay from the FIRST run

Never writes the source image itself anywhere outside --out-dir/source
(and that directory is expected to be gitignored/artifact-only -- see the
calling workflow).
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageOps

import phase_a_landmarks as pal

RATIO_NAMES = [
    "apex_position_in_interval",
    "base_position_in_interval",
    "triangle_height_ratio",
    "base_width_over_height",
    "horizontal_displacement_normalized",
]
LANDMARK_NAMES = [
    "minute_track_60", "triangle_apex", "triangle_base_left",
    "triangle_base_right", "coronet",
]


def load_bgr(path: Path) -> np.ndarray:
    """Decode at full native resolution (already capped at fetch time by
    fetch_images.py's own 1800px thumbnail step). EXIF-transpose so pixel
    (0,0) is always the same physical corner regardless of camera orientation
    metadata -- required for determinism across re-runs and re-fetches."""
    with Image.open(path) as im:
        im = ImageOps.exif_transpose(im).convert("RGB")
        arr = np.array(im)
    return cv2.cvtColor(arr, cv2.COLOR_RGB2BGR)


def result_to_row(result: pal.PhaseAResult, run_index: int, source_id: str,
                   physical_watch_id: str, image_path: str) -> dict:
    row = {
        "run_index": run_index,
        "source_image_id": image_path,
        "source_id": source_id,
        "physical_watch_id": physical_watch_id,
        "dial_cx": result.dial.cx if result.dial else None,
        "dial_cy": result.dial.cy if result.dial else None,
        "dial_r": result.dial.r if result.dial else None,
        "dial_quality": result.dial.quality if result.dial else None,
    }
    for name in LANDMARK_NAMES:
        lm = result.landmarks.get(name)
        prefix = name
        if lm is None:
            row[f"{prefix}_x"] = None
            row[f"{prefix}_y"] = None
            row[f"{prefix}_assessable"] = False
            row[f"{prefix}_confidence"] = None
            row[f"{prefix}_reason"] = "landmark not attempted (no dial reference)"
        else:
            row[f"{prefix}_x"] = lm.x
            row[f"{prefix}_y"] = lm.y
            row[f"{prefix}_assessable"] = lm.assessable
            row[f"{prefix}_confidence"] = lm.confidence
            row[f"{prefix}_reason"] = lm.reason
    for name in RATIO_NAMES:
        row[name] = result.ratios.get(name)
        row[f"{name}_reason"] = result.ratio_reasons.get(name, "")
    return row


def render_overlay(bgr: np.ndarray, result: pal.PhaseAResult) -> np.ndarray:
    """Draws exactly what generated each measurement: the dial reference
    circle (thin, for context only), each search ROI, each landmark point,
    and the ratio lines (interval, height, width, displacement)."""
    img = bgr.copy()
    h, w = img.shape[:2]
    thickness = max(1, round(min(h, w) / 500))
    font_scale = max(0.35, min(h, w) / 1400)

    def put_text(text, x, y, color):
        cv2.putText(img, text, (int(x), int(y)), cv2.FONT_HERSHEY_SIMPLEX,
                    font_scale, (0, 0, 0), thickness + 2, cv2.LINE_AA)
        cv2.putText(img, text, (int(x), int(y)), cv2.FONT_HERSHEY_SIMPLEX,
                    font_scale, color, thickness, cv2.LINE_AA)

    if result.dial is not None:
        d = result.dial
        cv2.circle(img, (int(d.cx), int(d.cy)), int(d.r), (255, 180, 0), thickness, cv2.LINE_AA)
        cv2.line(img, (int(d.cx), 0), (int(d.cx), h), (255, 180, 0), 1, cv2.LINE_AA)
        put_text(f"dial q={d.quality:.2f}", d.cx + d.r * 0.05, d.cy - d.r * 1.02, (255, 180, 0))

    roi_color = (200, 200, 200)
    for name, lm in result.landmarks.items():
        if lm.roi_px is not None:
            x0, y0, x1, y1 = (int(v) for v in lm.roi_px)
            cv2.rectangle(img, (x0, y0), (x1, y1), roi_color, 1, cv2.LINE_AA)

    point_colors = {
        "minute_track_60": (0, 200, 255),
        "triangle_apex": (0, 0, 255),
        "triangle_base_left": (0, 255, 0),
        "triangle_base_right": (0, 255, 0),
        "coronet": (255, 0, 255),
    }
    for name, lm in result.landmarks.items():
        color = point_colors.get(name, (255, 255, 255))
        if lm.assessable and lm.x is not None:
            cv2.drawMarker(img, (int(lm.x), int(lm.y)), color, cv2.MARKER_CROSS, 14, thickness + 1)
            cv2.circle(img, (int(lm.x), int(lm.y)), 4, color, -1, cv2.LINE_AA)
            put_text(name, lm.x + 8, lm.y - 8, color)
        elif lm.roi_px is not None:
            x0, y0, x1, y1 = (int(v) for v in lm.roi_px)
            put_text(f"{name}: NOT ASSESSABLE", x0, max(12, y0 - 6), (0, 0, 255))

    mt = result.landmarks.get("minute_track_60")
    cor = result.landmarks.get("coronet")
    apex = result.landmarks.get("triangle_apex")
    bl = result.landmarks.get("triangle_base_left")
    br = result.landmarks.get("triangle_base_right")

    def pt(lm):
        return (int(lm.x), int(lm.y)) if (lm and lm.assessable) else None

    mt_pt, cor_pt, apex_pt, bl_pt, br_pt = pt(mt), pt(cor), pt(apex), pt(bl), pt(br)

    if mt_pt and cor_pt:
        x = mt_pt[0] + 40
        cv2.line(img, (x, mt_pt[1]), (x, cor_pt[1]), (0, 200, 255), thickness, cv2.LINE_AA)
        put_text("coronet-to-60 interval", x + 6, (mt_pt[1] + cor_pt[1]) // 2, (0, 200, 255))

    if apex_pt and bl_pt and br_pt:
        base_mid = ((bl_pt[0] + br_pt[0]) // 2, (bl_pt[1] + br_pt[1]) // 2)
        cv2.line(img, apex_pt, base_mid, (0, 0, 255), thickness, cv2.LINE_AA)
        cv2.line(img, bl_pt, br_pt, (0, 255, 0), thickness, cv2.LINE_AA)
        put_text("height", apex_pt[0] - 60, (apex_pt[1] + base_mid[1]) // 2, (0, 0, 255))
        put_text("base width", (bl_pt[0] + br_pt[0]) // 2 - 30, br_pt[1] + 16, (0, 255, 0))
        if result.dial is not None:
            axis_top = (int(result.dial.cx), 0)
            axis_bottom = (int(result.dial.cx), h)
            cv2.line(img, axis_top, axis_bottom, (255, 180, 0), 1, cv2.LINE_AA)
            cv2.line(img, (int(result.dial.cx), base_mid[1]), base_mid, (255, 0, 255), thickness, cv2.LINE_AA)
            put_text("displacement", base_mid[0] + 8, base_mid[1] + 20, (255, 0, 255))

    legend_y = 24
    for label, color in [("minute-track ref", (0, 200, 255)), ("triangle apex", (0, 0, 255)),
                          ("triangle base L/R", (0, 255, 0)), ("coronet ref", (255, 0, 255)),
                          ("dial ref / local 12-axis", (255, 180, 0))]:
        put_text(label, 10, legend_y, color)
        legend_y += int(24 * font_scale / 0.5) if font_scale > 0.5 else 24

    return img


def compare_runs(rows: list) -> dict:
    """Byte-for-byte-equivalent numeric comparison of every run against the
    first. Any difference is a determinism FAILURE -- per
    docs/research/GMT_12_TRIANGLE_FRONTAL_MILESTONE.md Phase A requirements,
    this must never be papered over by widening a tolerance."""
    if not rows:
        return {"pass": False, "reason": "no runs recorded"}
    first = rows[0]
    keys = [k for k in first.keys() if k not in ("run_index",)]
    mismatches = []
    for i, row in enumerate(rows[1:], start=1):
        for k in keys:
            if row.get(k) != first.get(k):
                mismatches.append({"run": i, "field": k, "run0_value": first.get(k), "run_value": row.get(k)})
    return {"pass": len(mismatches) == 0, "n_runs": len(rows), "mismatches": mismatches}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", required=True, type=Path)
    ap.add_argument("--source-id", required=True)
    ap.add_argument("--physical-watch-id", required=True)
    ap.add_argument("--out-dir", required=True, type=Path)
    ap.add_argument("--runs", type=int, default=5)
    args = ap.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    first_result = None
    for i in range(args.runs):
        bgr = load_bgr(args.image)  # re-decode from disk every run -- a real
        # end-to-end determinism test, not just re-running on an in-memory array.
        result = pal.measure(bgr)
        if first_result is None:
            first_result = result
        rows.append(result_to_row(result, i, args.source_id, args.physical_watch_id, args.image.name))

    fieldnames = list(rows[0].keys())
    with (args.out_dir / "measurements.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        w.writerows(rows)
    (args.out_dir / "measurements.json").write_text(json.dumps(rows, indent=2), encoding="utf-8")

    determinism = compare_runs(rows)
    (args.out_dir / "determinism_check.json").write_text(json.dumps(determinism, indent=2), encoding="utf-8")

    bgr0 = load_bgr(args.image)
    overlay = render_overlay(bgr0, first_result)
    cv2.imwrite(str(args.out_dir / "overlay.png"), overlay)

    print(f"wrote {args.runs} runs to {args.out_dir}")
    print("determinism:", "PASS" if determinism["pass"] else "FAIL")
    if not determinism["pass"]:
        print(json.dumps(determinism["mismatches"], indent=2))
    print("first-run ratios:", json.dumps({k: rows[0][k] for k in RATIO_NAMES}, indent=2))
    for name in LANDMARK_NAMES:
        print(f"{name}: assessable={rows[0][name + '_assessable']} "
              f"x={rows[0][name + '_x']} y={rows[0][name + '_y']} "
              f"confidence={rows[0][name + '_confidence']} reason={rows[0][name + '_reason']!r}")

    return 0 if determinism["pass"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
