from __future__ import annotations

"""Run a controlled same-watch rehaut/perspective series.

The manifest deliberately keeps capture metadata separate from detector output.
No GOOD/CORRECTABLE/REJECT threshold is applied here. The purpose is to build
the empirical mapping from rehaut pose signal to GMT12 measurement bias, then
derive a rejection boundary from repeatability failure rather than guess one.

Manifest columns:
    image_id,image_path,watch_id,pitch_deg,yaw_deg,roll_deg,series_role,notes

Angles may be blank when unknown. `series_role` is normally `baseline` or
`tilt`. image_path is resolved relative to --root unless absolute.
"""

import argparse
import csv
from pathlib import Path
from typing import Dict, Iterable

import cv2

from gmt12_auto_landmarks import detect_gmt12
from gmt_rehaut_perspective import analyze_rehaut_perspective
from human_qc_geometry import measure_gmt12


INPUT_FIELDS = [
    "image_id",
    "image_path",
    "watch_id",
    "pitch_deg",
    "yaw_deg",
    "roll_deg",
    "series_role",
    "notes",
]

OUTPUT_FIELDS = INPUT_FIELDS + [
    "rehaut_status",
    "rehaut_reason",
    "top_width_px",
    "bottom_width_px",
    "left_width_px",
    "right_width_px",
    "mean_width_px",
    "vertical_asymmetry",
    "horizontal_asymmetry",
    "first_harmonic_strength",
    "widest_direction_deg",
    "min_width_over_mean",
    "edge_coverage",
    "normalized_fit_residual",
    "gmt12_status",
    "gmt12_reason",
    "raw_top_clearance",
    "raw_horizontal_offset",
    "raw_rotation_deg",
    "raw_left_clearance",
    "raw_right_clearance",
    "raw_side_asymmetry",
]


def _fmt(v):
    if v is None:
        return ""
    if isinstance(v, float):
        return f"{v:.9g}"
    return str(v)


def _resolve(root: Path, raw: str) -> Path:
    p = Path(raw)
    return p if p.is_absolute() else root / p


def analyze_row(row: Dict[str, str], root: Path) -> Dict[str, str]:
    out = {k: row.get(k, "") for k in INPUT_FIELDS}
    image_path = _resolve(root, row.get("image_path", ""))
    bgr = cv2.imread(str(image_path))
    if bgr is None:
        out.update(
            rehaut_status="UNASSESSABLE",
            rehaut_reason=f"image could not be read: {image_path}",
            gmt12_status="UNASSESSABLE",
            gmt12_reason=f"image could not be read: {image_path}",
        )
        return out

    rd = analyze_rehaut_perspective(bgr)
    if rd.perspective is None:
        out["rehaut_status"] = "UNASSESSABLE"
        out["rehaut_reason"] = rd.reason
    else:
        p = rd.perspective
        out.update(
            rehaut_status="MEASURED",
            rehaut_reason=rd.reason,
            top_width_px=_fmt(p.top_width_px),
            bottom_width_px=_fmt(p.bottom_width_px),
            left_width_px=_fmt(p.left_width_px),
            right_width_px=_fmt(p.right_width_px),
            mean_width_px=_fmt(p.mean_width_px),
            vertical_asymmetry=_fmt(p.vertical_asymmetry),
            horizontal_asymmetry=_fmt(p.horizontal_asymmetry),
            first_harmonic_strength=_fmt(p.first_harmonic_strength),
            widest_direction_deg=_fmt(p.widest_direction_deg),
            min_width_over_mean=_fmt(p.min_width_over_mean),
            edge_coverage=_fmt(p.edge_coverage),
            normalized_fit_residual=_fmt(p.normalized_fit_residual),
        )

    gd = detect_gmt12(bgr)
    if gd.geometry is None:
        out["gmt12_status"] = "UNASSESSABLE"
        out["gmt12_reason"] = gd.reason
    else:
        try:
            m = measure_gmt12(gd.geometry)
        except (ValueError, ZeroDivisionError) as exc:
            out["gmt12_status"] = "UNASSESSABLE"
            out["gmt12_reason"] = f"measurement failed: {exc}"
        else:
            out.update(
                gmt12_status="MEASURED",
                gmt12_reason=gd.reason,
                raw_top_clearance=_fmt(m.top_clearance_over_triangle_width),
                raw_horizontal_offset=_fmt(m.horizontal_offset_over_triangle_width),
                raw_rotation_deg=_fmt(m.rotation_deg),
                raw_left_clearance=_fmt(m.left_clearance_over_triangle_width),
                raw_right_clearance=_fmt(m.right_clearance_over_triangle_width),
                raw_side_asymmetry=_fmt(m.side_clearance_asymmetry),
            )
    return out


def run(rows: Iterable[Dict[str, str]], root: Path):
    for row in rows:
        yield analyze_row(row, root)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("manifest", type=Path)
    ap.add_argument("--root", type=Path, default=Path("."))
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()

    with args.manifest.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        missing = [x for x in INPUT_FIELDS if x not in (reader.fieldnames or [])]
        if missing:
            raise SystemExit(f"manifest missing columns: {', '.join(missing)}")
        rows = list(reader)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=OUTPUT_FIELDS)
        writer.writeheader()
        writer.writerows(run(rows, args.root))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
