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
        print("usage: measure_manifest.py <manifest.csv> <images_dir>", file=sys.stderr)
        return 2
    manifest_path = Path(sys.argv[1])
    images_dir = Path(sys.argv[2])
    with manifest_path.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

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

        overlay = render(bgr, detection.geometry)
        small = cv2.resize(overlay, None, fx=min(1.0, 900 / overlay.shape[1]), fy=min(1.0, 900 / overlay.shape[1]))
        ok, buf = cv2.imencode(".jpg", small, [cv2.IMWRITE_JPEG_QUALITY, 80])
        if ok:
            print(f"---BEGIN-PREVIEW-{sid}---")
            print(base64.b64encode(buf).decode("ascii"))
            print(f"---END-PREVIEW-{sid}---")

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
