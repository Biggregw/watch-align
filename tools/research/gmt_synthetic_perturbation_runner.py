#!/usr/bin/env python3
"""Deterministic input-geometry sensitivity experiment for frozen GMT Stage 3.

This deliberately perturbs the measured pose supplied to the pinned Stage 3
implementation and recomputes features from the original image. It does NOT
perturb already-computed feature values and does not alter production QC.
"""
from __future__ import annotations

import csv
import math
import sys
from pathlib import Path

import cv2

ROOT = Path(__file__).resolve().parents[2]
PYTOOLS = ROOT / "tools" / "watch_align_py"
sys.path.insert(0, str(PYTOOLS))
sys.path.insert(0, str(ROOT / "tools" / "research"))

import geometry
import gmt_proportional_features as gpf
import pipeline
from build_gmt_genuine_baseline import read_manifest, extract_image_urls, download_image, image_fingerprint

OUT = ROOT / "docs" / "research" / "gmt-genuine-baseline-results" / "synthetic-perturbation.csv"
FROZEN = {
    "h12.stage3_apex_r_simple",
    "h12.stage3_centre_r_projective",
    "h12.stage3_base_r_projective",
    "h12.stage3_axis_incidence_canonical",
    "h12.stage3_centroid_tangential_offset_canonical",
}
# Small, realistic pose/detection perturbations. Pixel translations are scaled by
# detected dial radius, so the experiment is resolution-independent.
SCENARIOS = (
    ("baseline", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    ("cx_minus_0p25pct_r", -0.0025, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    ("cx_plus_0p25pct_r", 0.0025, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    ("cy_minus_0p25pct_r", 0.0, -0.0025, 0.0, 0.0, 0.0, 0.0, 0.0),
    ("cy_plus_0p25pct_r", 0.0, 0.0025, 0.0, 0.0, 0.0, 0.0, 0.0),
    ("width_minus_0p5pct", 0.0, 0.0, -0.005, 0.0, 0.0, 0.0, 0.0),
    ("width_plus_0p5pct", 0.0, 0.0, 0.005, 0.0, 0.0, 0.0, 0.0),
    ("height_minus_0p5pct", 0.0, 0.0, 0.0, -0.005, 0.0, 0.0, 0.0),
    ("height_plus_0p5pct", 0.0, 0.0, 0.0, 0.005, 0.0, 0.0, 0.0),
    ("ellipse_angle_minus_0p5deg", 0.0, 0.0, 0.0, 0.0, -0.5, 0.0, 0.0),
    ("ellipse_angle_plus_0p5deg", 0.0, 0.0, 0.0, 0.0, 0.5, 0.0, 0.0),
    ("roll_minus_0p5deg", 0.0, 0.0, 0.0, 0.0, 0.0, -0.5, 0.0),
    ("roll_plus_0p5deg", 0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 0.0),
    ("radius_minus_0p5pct", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -0.005),
    ("radius_plus_0p5pct", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.005),
    ("combined_negative", -0.0025, -0.0025, -0.005, -0.005, -0.5, -0.5, -0.005),
    ("combined_positive", 0.0025, 0.0025, 0.005, 0.005, 0.5, 0.5, 0.005),
)


def finite(v):
    try:
        return v is not None and math.isfinite(float(v))
    except Exception:
        return False


def perturbed_pose(e, radius, scenario):
    _, dcx, dcy, dw, dh, da, droll, dr = scenario
    ep = geometry.RotatedRect(
        e.cx + dcx * radius,
        e.cy + dcy * radius,
        e.w * (1.0 + dw),
        e.h * (1.0 + dh),
        e.angle_deg + da,
    )
    return ep, droll, radius * (1.0 + dr)


def stage3(gray, ellipse, roll, radius, tilt):
    result = gpf.compute(gray, ellipse, roll, radius, tilt)
    return {"h12.stage3_" + k: float(v) for k, v in result.features.items() if finite(v)}


def main():
    rows = []
    seen = set()
    watches_done = set()
    # One accepted low-tilt image per physical watch. This preserves the physical
    # watch as the independent unit and bounds CI/network cost deterministically.
    for src in read_manifest():
        wid = src["physical_watch_id"]
        if wid in watches_done:
            continue
        urls, page_status = extract_image_urls(src["source_url"])
        accepted = False
        for image_index, url in enumerate(urls[:12]):
            bgr = download_image(url)
            if bgr is None:
                continue
            fp = image_fingerprint(bgr)
            if fp in seen:
                continue
            seen.add(fp)
            try:
                pose = pipeline.build(bgr)
            except Exception:
                continue
            if pose.reason or not pose.accepted or pose.acquisition is None or float(pose.tilt_deg) > 10.0:
                continue
            gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
            e = pose.acquisition.dial_ellipse
            radius = float(pose.dial_radius_px)
            roll = float(pose.solved_roll)
            tilt = float(pose.tilt_deg)
            base = stage3(gray, e, roll, radius, tilt)
            if not FROZEN.issubset(base):
                continue
            for scenario in SCENARIOS:
                name = scenario[0]
                ep, droll, rp = perturbed_pose(e, radius, scenario)
                try:
                    vals = stage3(gray, ep, roll + droll, rp, tilt)
                except Exception:
                    vals = {}
                for feature in sorted(FROZEN):
                    bv = base[feature]
                    pv = vals.get(feature)
                    delta = (pv - bv) if pv is not None else None
                    rows.append({
                        "physical_watch_id": wid,
                        "source_id": src["source_id"],
                        "page_status": page_status,
                        "image_index": image_index,
                        "scenario": name,
                        "feature": feature,
                        "baseline": bv,
                        "perturbed": pv,
                        "absolute_delta": delta,
                        "relative_delta": (delta / max(abs(bv), 1e-12)) if delta is not None else None,
                        "measurement_survived": pv is not None,
                    })
            watches_done.add(wid)
            accepted = True
            print(f"{wid}: measured {len(SCENARIOS)} pose scenarios from {url}")
            break
        if not accepted:
            print(f"{wid}: no usable low-tilt image in bounded acquisition")

    if not rows:
        raise SystemExit("No usable genuine images; cannot run input-geometry perturbation experiment")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    print(f"wrote {len(rows)} recomputed Stage 3 rows across {len(watches_done)} physical watches to {OUT}")


if __name__ == "__main__":
    main()
