#!/usr/bin/env python3
"""Visual pose-audit renderer. Produces, for every ACCEPTED image in a corpus:

  - a diagnostic composite (original+detected ellipse, plain rectified dial,
    rectified+minute-track overlay, rectified+full master-marker overlay,
    plus a text header with key diagnostics)

and, for a deduplicated review subset (suspicious cases, high-tilt, high
centre-displacement, worst held-out fit, random controls):

  - paginated contact sheets
  - visual_audit.csv with every existing diagnostic column plus the
    composite filename and which audit categories selected it

This is a READ-ONLY diagnostic tool: it does not change any pose/QC
threshold, master geometry, marker logic or classification logic. It only
renders what the current engine already decided.
"""
from __future__ import annotations

import argparse
import csv
import math
import random
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import cv2
import numpy as np

import master
import pipeline
import seed_detector
from image_io import resize_to_max_dim
from geometry import RotatedRect, map_point

PANEL = 480
HEADER_H = 150
CAPTION_H = 26
GRID = 2  # 2x2 panels


def rectify(bgr: np.ndarray, H: np.ndarray, side: int = 900) -> np.ndarray:
    """Port of MinuteTrackFirstOverlay.rectify(): warp the original image so the
    canonical dial (unit circle, 12 at top) fills a `side`x`side` square."""
    scale = side / 2.15
    S = np.array([[scale, 0.0, side / 2.0],
                  [0.0, scale, side / 2.0],
                  [0.0, 0.0, 1.0]], dtype=np.float64)
    Hinv = np.linalg.inv(H)
    M = S @ Hinv
    return cv2.warpPerspective(bgr, M, (side, side), flags=cv2.INTER_CUBIC,
                                borderMode=cv2.BORDER_CONSTANT, borderValue=(31, 17, 8))


def _canonical_to_px(x: float, y: float, side: int) -> Tuple[int, int]:
    scale = side / 2.15
    return (int(round(side / 2.0 + x * scale)), int(round(side / 2.0 + y * scale)))


def _draw_circle_canonical(img: np.ndarray, side: int, r: float, color, thickness=2):
    pts = []
    for i in range(0, 181):
        a = 2 * math.pi * i / 180.0
        pts.append(_canonical_to_px(r * math.cos(a), r * math.sin(a), side))
    cv2.polylines(img, [np.array(pts, dtype=np.int32)], isClosed=True, color=color, thickness=thickness)


def _draw_round_marker(img: np.ndarray, side: int, hour: int, color, thickness=2):
    a = master.angle_for_hour(hour)
    cx, cy = master.ROUND_CENTER_R * math.cos(a), master.ROUND_CENTER_R * math.sin(a)
    pts = []
    for i in range(0, 49):
        q = 2 * math.pi * i / 48.0
        x = cx + master.ROUND_OUTER_R * math.cos(q)
        y = cy + master.ROUND_OUTER_R * math.sin(q)
        pts.append(_canonical_to_px(x, y, side))
    cv2.polylines(img, [np.array(pts, dtype=np.int32)], isClosed=True, color=color, thickness=thickness)


def _draw_baton_marker(img: np.ndarray, side: int, hour: int, color, thickness=2):
    a = master.angle_for_hour(hour)
    ux, uy = math.cos(a), math.sin(a)
    vx, vy = -uy, ux
    cx, cy = master.BATON_CENTER_R * ux, master.BATON_CENTER_R * uy
    rh, th = master.BATON_RADIAL_HALF, master.BATON_TANGENTIAL_HALF
    pts = [
        (cx - ux * rh - vx * th, cy - uy * rh - vy * th),
        (cx - ux * rh + vx * th, cy - uy * rh + vy * th),
        (cx + ux * rh + vx * th, cy + uy * rh + vy * th),
        (cx + ux * rh - vx * th, cy + uy * rh - vy * th),
    ]
    px = [_canonical_to_px(x, y, side) for x, y in pts]
    cv2.polylines(img, [np.array(px, dtype=np.int32)], isClosed=True, color=color, thickness=thickness)


def _draw_triangle_marker(img: np.ndarray, side: int, color, thickness=2):
    a = master.angle_for_hour(12)
    ux, uy = math.cos(a), math.sin(a)
    vx, vy = -uy, ux
    cx, cy = master.TRI_CENTER_R * ux, master.TRI_CENTER_R * uy
    base_out, apex_in, half_base = master.TRI_BASE_OUTWARD, master.TRI_APEX_INWARD, master.TRI_HALF_BASE
    pts = [
        (cx + ux * base_out + vx * half_base, cy + uy * base_out + vy * half_base),
        (cx + ux * base_out - vx * half_base, cy + uy * base_out - vy * half_base),
        (cx - ux * apex_in, cy - uy * apex_in),
    ]
    px = [_canonical_to_px(x, y, side) for x, y in pts]
    cv2.polylines(img, [np.array(px, dtype=np.int32)], isClosed=True, color=color, thickness=thickness)


def draw_master_overlay(rectified: np.ndarray, side: int, include_markers: bool) -> np.ndarray:
    out = rectified.copy()
    _draw_circle_canonical(out, side, master.DIAL_EDGE_R, (0, 0, 255), 2)
    _draw_circle_canonical(out, side, master.MINUTE_TRACK_R, (0, 128, 255), 2)
    if include_markers:
        for h in (1, 2, 4, 5, 7, 8, 10, 11):
            _draw_round_marker(out, side, h, (0, 255, 0), 2)
        for h in (6, 9):
            _draw_baton_marker(out, side, h, (0, 255, 0), 2)
        _draw_triangle_marker(out, side, (0, 255, 255), 2)
    return out


def draw_pose_on_original(bgr: np.ndarray, ellipse: RotatedRect, solved_roll: float,
                           seed_x: float, seed_y: float, seed_r: float) -> np.ndarray:
    out = bgr.copy()
    cv2.circle(out, (int(seed_x), int(seed_y)), int(seed_r), (0, 165, 255), 2)
    cv2.ellipse(out, ((ellipse.cx, ellipse.cy), (ellipse.w, ellipse.h), ellipse.angle_deg),
                (0, 0, 255), 2)
    tip_x, tip_y = map_point(ellipse, 1.0, solved_roll, 0.0, -1.0)
    cv2.line(out, (int(ellipse.cx), int(ellipse.cy)), (int(tip_x), int(tip_y)), (0, 255, 255), 2)
    cv2.circle(out, (int(ellipse.cx), int(ellipse.cy)), 4, (0, 0, 255), -1)
    return out


def _fit_panel(img: np.ndarray, size: int) -> np.ndarray:
    h, w = img.shape[:2]
    scale = size / max(h, w)
    resized = cv2.resize(img, (max(1, round(w * scale)), max(1, round(h * scale))), interpolation=cv2.INTER_AREA)
    canvas = np.zeros((size, size, 3), dtype=np.uint8)
    y0 = (size - resized.shape[0]) // 2
    x0 = (size - resized.shape[1]) // 2
    canvas[y0:y0 + resized.shape[0], x0:x0 + resized.shape[1]] = resized
    return canvas


def _caption(size: int, text: str) -> np.ndarray:
    bar = np.zeros((CAPTION_H, size, 3), dtype=np.uint8)
    cv2.putText(bar, text, (6, CAPTION_H - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)
    return bar


def compose_diagnostic(bgr: np.ndarray, result, seed, row: Dict[str, Any]) -> np.ndarray:
    ellipse = result.acquisition.dial_ellipse
    panel_original = _fit_panel(
        draw_pose_on_original(bgr, ellipse, result.solved_roll, seed.x, seed.y, seed.r), PANEL)
    rectified = rectify(bgr, result.H, side=900)
    panel_plain = _fit_panel(rectified, PANEL)
    panel_track = _fit_panel(draw_master_overlay(rectified, 900, include_markers=False), PANEL)
    panel_full = _fit_panel(draw_master_overlay(rectified, 900, include_markers=True), PANEL)

    panels = [panel_original, panel_plain, panel_track, panel_full]
    captions = ["original + detected ellipse/pose", "rectified (plain)",
                "rectified + minute-track/edge", "rectified + full master markers"]
    tiles = []
    for panel, cap in zip(panels, captions):
        tiles.append(np.vstack([panel, _caption(PANEL, cap)]))
    tile_h = PANEL + CAPTION_H
    grid = np.zeros((tile_h * GRID, PANEL * GRID, 3), dtype=np.uint8)
    for i, tile in enumerate(tiles):
        r, c = divmod(i, GRID)
        grid[r * tile_h:(r + 1) * tile_h, c * PANEL:(c + 1) * PANEL] = tile

    header = np.zeros((HEADER_H, grid.shape[1], 3), dtype=np.uint8)
    lines = [
        f"source_id={row['source_id']}  physical_watch_id={row['physical_watch_id']}  "
        f"class={row['class_label']}/{row['provenance']}  factory={row.get('factory','')}",
        f"confidence={float(row['pose_confidence']):.2f}  tilt_deg={float(row['tilt_deg']):.1f}  "
        f"center_disp_frac={float(row['center_displacement_frac']):.3f}",
        f"acquisition_fit_median_px={float(row['acquisition_fit_median_px']):.2f}  "
        f"holdout_median_px={float(row['holdout_median_px']):.2f}  "
        f"candidate_count={row.get('candidate_count','')}",
        f"local_path={row['local_path']}",
    ]
    for i, line in enumerate(lines):
        cv2.putText(header, line, (8, 26 + i * 30), cv2.FONT_HERSHEY_SIMPLEX, 0.55,
                    (255, 255, 255), 1, cv2.LINE_AA)
    return np.vstack([header, grid])


def build_contact_sheets(entries: List[Dict[str, Any]], composites_dir: Path,
                          out_dir: Path, per_page: int = 6, cols: int = 3,
                          thumb: int = 480) -> List[Path]:
    pages = []
    rows_per_page = max(1, per_page // cols)
    for page_idx in range(0, len(entries), per_page):
        chunk = entries[page_idx:page_idx + per_page]
        canvas = np.full((rows_per_page * (thumb + 60), cols * thumb, 3), 30, dtype=np.uint8)
        for i, entry in enumerate(chunk):
            r, c = divmod(i, cols)
            img = cv2.imread(str(composites_dir / entry["composite_filename"]))
            if img is None:
                continue
            img = img[HEADER_H:, :]  # drop the baked-in text header; caption below covers it
            thumb_img = _fit_panel(img, thumb)
            y0 = r * (thumb + 60)
            canvas[y0:y0 + thumb, c * thumb:(c + 1) * thumb] = thumb_img
            label1 = entry["source_id"][:28]
            label2 = f"t={float(entry['tilt_deg']):.0f} d={float(entry['center_displacement_frac']):.2f} h={float(entry['holdout_median_px']):.2f}"
            label3 = entry["audit_categories"][:34]
            cv2.putText(canvas, label1, (c * thumb + 4, y0 + thumb + 16),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.42, (255, 255, 255), 1, cv2.LINE_AA)
            cv2.putText(canvas, label2, (c * thumb + 4, y0 + thumb + 33),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.42, (0, 220, 255), 1, cv2.LINE_AA)
            cv2.putText(canvas, label3, (c * thumb + 4, y0 + thumb + 50),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.4, (150, 220, 150), 1, cv2.LINE_AA)
        page_num = page_idx // per_page + 1
        path = out_dir / f"contact_sheet_page_{page_num:02d}.jpg"
        cv2.imwrite(str(path), canvas, [cv2.IMWRITE_JPEG_QUALITY, 90])
        pages.append(path)
    return pages


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--per-image-csv", required=True, type=Path,
                         help="Already-produced per_image_measurements.csv")
    parser.add_argument("--images-root", required=True, type=Path)
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--worst-holdout-n", type=int, default=20)
    parser.add_argument("--random-controls-n", type=int, default=15)
    parser.add_argument("--random-seed", type=int, default=42)
    parser.add_argument("--suspicious", action="append", default=[],
                         help="source_id:local_path pairs to force-include, repeatable")
    args = parser.parse_args()

    with args.per_image_csv.open(newline="", encoding="utf-8") as f:
        all_rows = list(csv.DictReader(f))
    accepted = [r for r in all_rows if r["pipeline_outcome"] == "accepted"]
    print(f"accepted images to render: {len(accepted)}")

    composites_dir = args.out_dir / "composites"
    composites_dir.mkdir(parents=True, exist_ok=True)

    rendered: List[Dict[str, Any]] = []
    for i, row in enumerate(accepted, start=1):
        local_path = args.images_root / row["local_path"]
        print(f"[{i}/{len(accepted)}] {row['source_id']} {row['local_path']}")
        raw = cv2.imread(str(local_path), cv2.IMREAD_COLOR)
        if raw is None:
            print(f"  SKIP: could not decode {local_path}")
            continue
        bgr = resize_to_max_dim(raw, 1600)
        result = pipeline.build(bgr)
        if result.reason or not result.accepted:
            print(f"  SKIP: re-run did not reproduce acceptance (non-determinism or drift): {result.reason}")
            continue
        seed = seed_detector.detect_dial(bgr)
        composite = compose_diagnostic(bgr, result, seed, row)
        fname = f"{row['source_id']}__{Path(row['local_path']).name}.jpg"
        cv2.imwrite(str(composites_dir / fname), composite, [cv2.IMWRITE_JPEG_QUALITY, 90])
        row = dict(row)
        row["composite_filename"] = fname
        rendered.append(row)

    print(f"rendered {len(rendered)} composites")

    by_key = {(r["source_id"], r["local_path"]): r for r in rendered}

    def key(sid, path):
        return (sid, path)

    suspicious_keys = set()
    for spec in args.suspicious:
        sid, path = spec.split(":", 1)
        suspicious_keys.add((sid, path))

    high_tilt_keys = {k for k, r in by_key.items() if float(r["tilt_deg"]) > 25}
    high_disp_keys = {k for k, r in by_key.items() if float(r["center_displacement_frac"]) > 0.20}
    worst_holdout_keys = set(
        key(r["source_id"], r["local_path"])
        for r in sorted(rendered, key=lambda r: -float(r["holdout_median_px"]))[:args.worst_holdout_n]
    )
    rng = random.Random(args.random_seed)
    random_keys = set(key(r["source_id"], r["local_path"]) for r in rng.sample(rendered, min(args.random_controls_n, len(rendered))))

    categories = {
        "suspicious": suspicious_keys,
        "high_tilt": high_tilt_keys,
        "high_center_displacement": high_disp_keys,
        "worst_holdout_fit": worst_holdout_keys,
        "random_control": random_keys,
    }

    audit_union = set()
    for keys in categories.values():
        audit_union |= keys

    audit_entries = []
    for k in audit_union:
        r = dict(by_key[k])
        cats = [name for name, keys in categories.items() if k in keys]
        r["audit_categories"] = ";".join(cats)
        audit_entries.append(r)

    def sort_key(r):
        order = {"suspicious": 0, "high_tilt": 1, "high_center_displacement": 2,
                 "worst_holdout_fit": 3, "random_control": 4}
        return min(order.get(c, 99) for c in r["audit_categories"].split(";"))
    audit_entries.sort(key=sort_key)

    print(f"visual-audit union: {len(audit_entries)} unique images "
          f"(suspicious={len(suspicious_keys)}, high_tilt={len(high_tilt_keys)}, "
          f"high_center_displacement={len(high_disp_keys)}, "
          f"worst_holdout_fit={len(worst_holdout_keys)}, random_control={len(random_keys)})")

    if audit_entries:
        fieldnames = list(audit_entries[0].keys())
        for r in audit_entries[1:]:
            for k in r.keys():
                if k not in fieldnames:
                    fieldnames.append(k)
        with (args.out_dir / "visual_audit.csv").open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames, restval="")
            writer.writeheader()
            writer.writerows(audit_entries)
        print(f"wrote {args.out_dir / 'visual_audit.csv'}")

    pages = build_contact_sheets(audit_entries, composites_dir, args.out_dir)
    for p in pages:
        print(f"wrote {p}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
