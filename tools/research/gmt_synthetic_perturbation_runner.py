#!/usr/bin/env python3
"""Deterministic input-geometry sensitivity experiment for frozen GMT Stage 3.

Perturbs measured pose supplied to the pinned Stage 3 implementation and
recomputes features from the original image. Also records Stage 3 diagnostics
needed to distinguish smooth feature sensitivity from upstream solution
switching. Production QC and the pinned Stage 3 implementation are untouched.
"""
from __future__ import annotations

import csv
import json
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
    ("combined_positive", 0.0025, 0.0025, 0.005, 0.005, 0.5, 0.5, 0.5 * 0.01),
)


def finite(v):
    try:
        return v is not None and math.isfinite(float(v))
    except Exception:
        return False


def perturbed_pose(e, radius, scenario):
    _, dcx, dcy, dw, dh, da, droll, dr = scenario
    ep = geometry.RotatedRect(e.cx + dcx * radius, e.cy + dcy * radius,
                              e.w * (1.0 + dw), e.h * (1.0 + dh), e.angle_deg + da)
    return ep, droll, radius * (1.0 + dr)


def stage3(gray, ellipse, roll, radius, tilt):
    result = gpf.compute(gray, ellipse, roll, radius, tilt)
    features = {"h12.stage3_" + k: float(v) for k, v in result.features.items() if finite(v)}
    return features, result.diagnostics


def enc(v):
    if v is None:
        return ""
    if isinstance(v, (list, tuple, dict)):
        return json.dumps(v, sort_keys=True, separators=(",", ":"))
    return v


def diagnostic_fields(diag):
    fit = diag.get("projective_fit") or {}
    return {
        "triangle_segmented": diag.get("triangle_segmented"),
        "triangle_confidence": diag.get("triangle_confidence"),
        "triangle_solidity": diag.get("triangle_solidity"),
        "triangle_axis_agreement_deg": diag.get("triangle_axis_agreement_deg"),
        "triangle_n_contour_points": diag.get("triangle_n_contour_points"),
        "apex_xy_px": enc(diag.get("apex_xy_px")),
        "base_centre_xy_px": enc(diag.get("base_centre_xy_px")),
        "base_left_xy_px": enc(diag.get("base_left_xy_px")),
        "base_right_xy_px": enc(diag.get("base_right_xy_px")),
        "centroid_xy_px": enc(diag.get("centroid_xy_px")),
        "n_round_segmented": diag.get("n_round_segmented"),
        "round_hours_contributing": enc(diag.get("round_hours_contributing")),
        "corridor_suppressed_reason": diag.get("corridor_suppressed_reason"),
        "corridor_fit_residuals_px": enc(diag.get("corridor_fit_residuals_px")),
        "projective_correspondences_available": diag.get("projective_correspondences_available"),
        "projective_fit_a": fit.get("a"),
        "projective_fit_c": fit.get("c"),
        "projective_fit_max_abs_residual_canon": fit.get("max_abs_residual_canon"),
        "projective_fit_condition_ok": fit.get("condition_ok"),
        "projective_fit_residuals_canon": enc(fit.get("residuals_canon")),
        "projective_suppressed_reason": diag.get("projective_suppressed_reason"),
    }


def main():
    rows, seen, watches_done = [], set(), set()
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
            e, radius, roll, tilt = pose.acquisition.dial_ellipse, float(pose.dial_radius_px), float(pose.solved_roll), float(pose.tilt_deg)
            base, base_diag = stage3(gray, e, roll, radius, tilt)
            if not FROZEN.issubset(base):
                continue
            for scenario in SCENARIOS:
                name = scenario[0]
                ep, droll, rp = perturbed_pose(e, radius, scenario)
                try:
                    vals, diag = stage3(gray, ep, roll + droll, rp, tilt)
                except Exception as exc:
                    vals, diag = {}, {"exception": repr(exc)}
                dfields = diagnostic_fields(diag)
                for feature in sorted(FROZEN):
                    bv, pv = base[feature], vals.get(feature)
                    signed_delta = (pv - bv) if pv is not None else None
                    row = {
                        "physical_watch_id": wid, "source_id": src["source_id"], "page_status": page_status,
                        "image_index": image_index, "scenario": name, "feature": feature,
                        "baseline": bv, "perturbed": pv,
                        "signed_delta": signed_delta,
                        "absolute_delta": abs(signed_delta) if signed_delta is not None else None,
                        "relative_delta": (signed_delta / max(abs(bv), 1e-12)) if signed_delta is not None else None,
                        "measurement_survived": pv is not None,
                    }
                    row.update(dfields)
                    rows.append(row)
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
        w.writeheader(); w.writerows(rows)
    print(f"wrote {len(rows)} recomputed Stage 3 rows across {len(watches_done)} physical watches to {OUT}")


if __name__ == "__main__":
    main()
