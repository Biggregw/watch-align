#!/usr/bin/env python3
"""Run the current GMT12 detector+assessment pipeline over a downloaded
image set and print raw results plus base64 overlay previews (log-only,
for visual review -- no measurement decision is made here).
"""
from __future__ import annotations

import base64
import csv
import sys
from pathlib import Path

import cv2

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from gmt12_auto_landmarks import detect_gmt12
from gmt12_landmark_validation import render
from gmt12_qc_assessment import (
    GEN_TOP_CLEARANCE_HIGH,
    GEN_TOP_CLEARANCE_LOW,
    STRONG_HIGH,
    STRONG_LOW,
    Status,
    assess_detection,
)


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: measure_manifest.py <manifest.csv> <images_dir> [source_id,source_id,...]", file=sys.stderr)
        return 2
    manifest_path = Path(sys.argv[1])
    images_dir = Path(sys.argv[2])
    only = set(sys.argv[3].split(",")) if len(sys.argv) > 3 and sys.argv[3] else None
    with manifest_path.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    if only:
        rows = [r for r in rows if r["source_id"] in only]

    print(f"reference band: GEN=[{GEN_TOP_CLEARANCE_LOW:.3f},{GEN_TOP_CLEARANCE_HIGH:.3f}]  "
          f"STRONG=[{STRONG_LOW:.3f},{STRONG_HIGH:.3f}]\n")

    results = []
    for row in rows:
        sid = row["source_id"]
        path = images_dir / f"{sid}.jpg"
        if not path.exists():
            print(f"{sid}: SKIP (no downloaded image at {path})")
            continue
        bgr = cv2.imread(str(path))
        if bgr is None:
            print(f"{sid}: SKIP (unreadable image)")
            continue
        detection = detect_gmt12(bgr)
        assessment = assess_detection(detection)
        if assessment.status == Status.UNASSESSABLE:
            print(f"{sid}: UNASSESSABLE -- {assessment.reason}")
            results.append((sid, row["class_label"], None, "UNASSESSABLE", assessment.reason))
            continue
        m = assessment.measurements
        print(f"{sid}: {assessment.status.value}  top_clearance={m.top_clearance_over_triangle_width:.4f}  "
              f"rotation_deg={m.rotation_deg:.3f}  confidence={detection.confidence:.3f}  "
              f"reason={detection.reason or '(direct landmarks)'}")
        results.append((sid, row["class_label"], m.top_clearance_over_triangle_width, assessment.status.value, ""))

        g = detection.geometry
        print(f"{sid}: raw_landmarks native_size={bgr.shape[1]}x{bgr.shape[0]}  "
              f"tri_top_left=({g.triangle_top_left.x:.1f},{g.triangle_top_left.y:.1f})  "
              f"tri_top_right=({g.triangle_top_right.x:.1f},{g.triangle_top_right.y:.1f})  "
              f"tri_tip=({g.triangle_tip.x:.1f},{g.triangle_tip.y:.1f})  "
              f"minute_inner_left=({g.minute_inner_left.x:.1f},{g.minute_inner_left.y:.1f})  "
              f"minute_inner_right=({g.minute_inner_right.x:.1f},{g.minute_inner_right.y:.1f})  "
              f"minute_60_center=({g.minute_60_center.x:.1f},{g.minute_60_center.y:.1f})")

        overlay = render(bgr, detection.geometry)
        small = cv2.resize(overlay, None, fx=min(1.0, 900 / overlay.shape[1]), fy=min(1.0, 900 / overlay.shape[1]))
        ok, buf = cv2.imencode(".jpg", small, [cv2.IMWRITE_JPEG_QUALITY, 80])
        if ok:
            print(f"---BEGIN-PREVIEW-{sid}---")
            print(base64.b64encode(buf).decode("ascii"))
            print(f"---END-PREVIEW-{sid}---")

        if only:
            # High-quality, tightly-cropped, minimally-annotated zoom of the raw
            # (un-overlaid) pixels around the 12 marker, for verifying exact
            # landmark placement without JPEG-compressed overlay lines/text
            # obscuring the underlying tick dashes.
            cx = (g.triangle_top_left.x + g.triangle_top_right.x) / 2
            cy = g.triangle_top_left.y
            half_w = max(80.0, 2.2 * abs(g.triangle_top_right.x - g.triangle_top_left.x))
            x0, x1 = int(max(0, cx - half_w)), int(min(bgr.shape[1], cx + half_w))
            y0, y1 = int(max(0, cy - 0.9 * half_w)), int(min(bgr.shape[0], cy + 0.6 * half_w))
            crop = bgr[y0:y1, x0:x1].copy()
            scale = 6
            zoom = cv2.resize(crop, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)

            def mark(pt, color):
                px, py = int((pt.x - x0) * scale), int((pt.y - y0) * scale)
                cv2.drawMarker(zoom, (px, py), color, markerType=cv2.MARKER_CROSS,
                                markerSize=18, thickness=1, line_type=cv2.LINE_AA)

            mark(g.minute_inner_left, (255, 0, 255))
            mark(g.minute_inner_right, (255, 0, 255))
            mark(g.minute_60_center, (255, 0, 255))
            mark(g.triangle_top_left, (0, 255, 0))
            mark(g.triangle_top_right, (0, 255, 0))
            ok2, buf2 = cv2.imencode(".jpg", zoom, [cv2.IMWRITE_JPEG_QUALITY, 97])
            if ok2:
                print(f"---BEGIN-ZOOM-{sid}---")
                print(base64.b64encode(buf2).decode("ascii"))
                print(f"---END-ZOOM-{sid}---")

    print("\nsummary:")
    measured = [r for r in results if r[2] is not None]
    measured.sort(key=lambda r: r[2])
    for sid, cls, tc, status, reason in measured:
        print(f"  {tc:.4f}  {status:20s} {sid} ({cls})")
    n_unassessable = len(results) - len(measured)
    print(f"\n{len(measured)}/{len(results)} measured, {n_unassessable} unassessable")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
