"""Render and measure explicitly observed GMT 12-o'clock landmarks.

This is a validation harness, not an automatic detector. Its purpose is to
prove that the physical landmark contract and the resulting measurements match
what a human QC reviewer is actually looking at before detector work is trusted.

Input JSON example:
{
  "triangle_top_left": [735, 465],
  "triangle_top_right": [792, 465],
  "triangle_tip": [765, 539],
  "minute_inner_left": [733, 453],
  "minute_inner_right": [795, 453],
  "minute_60_center": [765, 444]
}
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2

from human_qc_geometry import Gmt12Geometry, Point, measure_gmt12


def _point(v):
    return Point(float(v[0]), float(v[1]))


def load_geometry(path: Path) -> Gmt12Geometry:
    raw = json.loads(path.read_text())
    return Gmt12Geometry(
        triangle_top_left=_point(raw["triangle_top_left"]),
        triangle_top_right=_point(raw["triangle_top_right"]),
        triangle_tip=_point(raw["triangle_tip"]),
        minute_inner_left=_point(raw["minute_inner_left"]),
        minute_inner_right=_point(raw["minute_inner_right"]),
        minute_60_center=_point(raw["minute_60_center"]),
    )


def _xy(p: Point):
    return int(round(p.x)), int(round(p.y))


def render(image, g: Gmt12Geometry):
    out = image.copy()
    top_mid = Point(
        (g.triangle_top_left.x + g.triangle_top_right.x) / 2.0,
        (g.triangle_top_left.y + g.triangle_top_right.y) / 2.0,
    )

    # Yellow: the observed local minute-track reference, from physical ticks.
    cv2.line(out, _xy(g.minute_inner_left), _xy(g.minute_inner_right), (0, 255, 255), 3)
    # Red: the observed outer/top edge of the 12 triangle.
    cv2.line(out, _xy(g.triangle_top_left), _xy(g.triangle_top_right), (0, 0, 255), 3)
    # Cyan: the observed triangle centreline, not a fitted dial axis.
    cv2.line(out, _xy(top_mid), _xy(g.triangle_tip), (255, 255, 0), 3)

    points = [
        (g.triangle_top_left, (0, 255, 0), "tri L"),
        (g.triangle_top_right, (0, 255, 0), "tri R"),
        (g.triangle_tip, (0, 0, 255), "tip"),
        (g.minute_inner_left, (0, 255, 255), "min L"),
        (g.minute_inner_right, (0, 255, 255), "min R"),
        (g.minute_60_center, (0, 165, 255), "60"),
    ]
    for p, colour, label in points:
        x, y = _xy(p)
        cv2.circle(out, (x, y), 6, colour, 2)
        cv2.putText(out, label, (x + 7, y - 7), cv2.FONT_HERSHEY_SIMPLEX,
                    0.45, colour, 1, cv2.LINE_AA)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image", type=Path)
    ap.add_argument("landmarks", type=Path)
    ap.add_argument("--overlay", type=Path)
    args = ap.parse_args()

    image = cv2.imread(str(args.image), cv2.IMREAD_COLOR)
    if image is None:
        raise SystemExit(f"cannot read image: {args.image}")
    g = load_geometry(args.landmarks)
    m = measure_gmt12(g)
    print(json.dumps({
        "top_clearance_over_triangle_width": m.top_clearance_over_triangle_width,
        "horizontal_offset_over_triangle_width": m.horizontal_offset_over_triangle_width,
        "rotation_deg": m.rotation_deg,
    }, indent=2))
    if args.overlay:
        cv2.imwrite(str(args.overlay), render(image, g))


if __name__ == "__main__":
    main()
