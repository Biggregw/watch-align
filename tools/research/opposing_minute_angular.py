#!/usr/bin/env python3
"""Research-only angular opposing-minute perspective pilot.

This intentionally does NOT reuse the failed radial-inner-end correction as a
pose signal. It reuses only the real-photo dial/ellipse/minute-tick detector
from opposing_minute_real.py, then asks a different question:

After affine-normalising the dial ellipse, do the detected minute-tick ANGLES
contain a stable residual projective mapping?

A true dial has 60 equally spaced tick directions. A projective camera mapping
can disturb their parameterisation around the ellipse even after the ellipse
shape is affine-normalised. We fit a homography from the canonical 60-point
unit circle to detected unit directions.

Cross-validation is strict:
- even opposing pairs train H_even and are evaluated by H_odd;
- odd opposing pairs train H_odd and are evaluated by H_even;
- the score is the held-out 180-degree error of each opposing pair;
- a pair never trains the homography used to score that pair.

No production Android code or thresholds are touched.
"""
from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path

import cv2
import numpy as np

from gmt12_auto_landmarks import _dial_circle
from opposing_minute_real import _fit_outer_ellipse, _find_grid_phase, _detect_tick, _write_csv


def _unit_for_slot(slot: int) -> np.ndarray:
    # Absolute zero is arbitrary. A constant clock rotation is absorbed by H.
    a = math.radians(6.0 * slot)
    return np.array((math.sin(a), -math.cos(a)), dtype=np.float64)


def _unit_for_tick(tick) -> np.ndarray:
    q = np.array((tick.qx, tick.qy), dtype=np.float64)
    n = float(np.linalg.norm(q))
    if not np.isfinite(n) or n < 1e-9:
        raise ValueError("degenerate tick direction")
    return q / n


def _apply_h(H: np.ndarray, p: np.ndarray) -> np.ndarray | None:
    q = H @ np.array((float(p[0]), float(p[1]), 1.0), dtype=np.float64)
    if not np.all(np.isfinite(q)) or abs(float(q[2])) < 1e-9:
        return None
    v = q[:2] / q[2]
    n = float(np.linalg.norm(v))
    if not np.all(np.isfinite(v)) or n < 1e-9:
        return None
    return v / n


def _opposite_error_deg(a: np.ndarray, b: np.ndarray) -> float:
    d = float(np.clip(np.dot(a, b), -1.0, 1.0))
    angle = math.degrees(math.acos(d))
    return abs(180.0 - angle)


def _fit_h(pair_data: list[dict], parity: int) -> tuple[np.ndarray, int, float] | None:
    src, dst = [], []
    for p in pair_data:
        if int(p["pair_index"]) % 2 != parity:
            continue
        for slot, tick in ((int(p["pair_index"]), p["a"]), (int(p["pair_index"]) + 30, p["b"])):
            src.append(_unit_for_slot(slot))
            dst.append(_unit_for_tick(tick))
    if len(src) < 12:
        return None
    src_a = np.asarray(src, dtype=np.float64).reshape(-1, 1, 2)
    dst_a = np.asarray(dst, dtype=np.float64).reshape(-1, 1, 2)
    H, mask = cv2.findHomography(src_a, dst_a, cv2.RANSAC, 0.025, maxIters=4000, confidence=0.995)
    if H is None or not np.all(np.isfinite(H)):
        return None
    inliers = int(mask.sum()) if mask is not None else len(src)
    if inliers < 8:
        return None
    # Report training reprojection only as a diagnostic, never as the success metric.
    errs = []
    for s, d in zip(src, dst):
        pred = _apply_h(H, s)
        if pred is not None:
            errs.append(math.degrees(math.acos(float(np.clip(np.dot(pred, d), -1.0, 1.0)))))
    train_med = float(np.median(errs)) if errs else float("nan")
    return H, inliers, train_med


def _model_disagreement_deg(H0: np.ndarray, H1: np.ndarray) -> float:
    errs = []
    for slot in range(60):
        u = _unit_for_slot(slot)
        a = _apply_h(H0, u)
        b = _apply_h(H1, u)
        if a is None or b is None:
            continue
        errs.append(math.degrees(math.acos(float(np.clip(np.dot(a, b), -1.0, 1.0)))))
    return float(np.median(errs)) if errs else float("nan")


def _analyse(path: Path, metadata: dict) -> tuple[dict, list[dict]]:
    base = {k: metadata.get(k, "") for k in ("source_id", "split", "physical_watch_id", "local_path")}
    bgr = cv2.imread(str(path), cv2.IMREAD_COLOR)
    if bgr is None:
        return {**base, "status": "UNASSESSABLE", "reason": "image decode failed"}, []
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    seed = _dial_circle(gray)
    if seed is None:
        return {**base, "status": "UNASSESSABLE", "reason": "dial seed not found"}, []
    frame = _fit_outer_ellipse(gray, seed)
    if frame is None:
        return {**base, "status": "UNASSESSABLE", "reason": "outer ellipse not stable"}, []
    phase_result = _find_grid_phase(gray, frame)
    if phase_result is None:
        return {**base, "status": "UNASSESSABLE", "reason": "60-fold minute-track phase not found", "ellipse_rms": frame.fit_rms}, []
    phase, phase_score, phase_contrast = phase_result

    ticks = {}
    for i in range(60):
        t = _detect_tick(gray, frame, i, phase)
        if t is not None:
            ticks[i] = t

    pair_data = []
    pair_rows = []
    for i in range(30):
        a, b = ticks.get(i), ticks.get(i + 30)
        if a is None or b is None:
            continue
        ua, ub = _unit_for_tick(a), _unit_for_tick(b)
        raw = _opposite_error_deg(ua, ub)
        pair_data.append({"pair_index": i, "a": a, "b": b, "raw": raw})
        pair_rows.append({
            **base,
            "pair_index": i,
            "slot_a": i,
            "slot_b": i + 30,
            "angle_a_deg": a.clock_deg,
            "angle_b_deg": b.clock_deg,
            "raw_opposite_error_deg": raw,
            "score_a": a.score,
            "score_b": b.score,
        })

    if len(pair_data) < 12:
        return {**base, "status": "UNASSESSABLE", "reason": "fewer than 12 complete opposing pairs",
                "valid_ticks": len(ticks), "usable_pairs": len(pair_data), "ellipse_rms": frame.fit_rms,
                "phase_score": phase_score, "phase_contrast": phase_contrast}, pair_rows

    even = _fit_h(pair_data, 0)
    odd = _fit_h(pair_data, 1)
    if even is None or odd is None:
        return {**base, "status": "UNASSESSABLE", "reason": "independent angular homography fit failed",
                "valid_ticks": len(ticks), "usable_pairs": len(pair_data), "ellipse_rms": frame.fit_rms,
                "phase_score": phase_score, "phase_contrast": phase_contrast}, pair_rows
    H_even, in_even, train_even = even
    H_odd, in_odd, train_odd = odd
    try:
        H_even_inv = np.linalg.inv(H_even)
        H_odd_inv = np.linalg.inv(H_odd)
    except np.linalg.LinAlgError:
        return {**base, "status": "UNASSESSABLE", "reason": "angular homography singular",
                "valid_ticks": len(ticks), "usable_pairs": len(pair_data)}, pair_rows

    raw_errors, corrected_errors = [], []
    for p, row in zip(pair_data, pair_rows):
        ua, ub = _unit_for_tick(p["a"]), _unit_for_tick(p["b"])
        raw_errors.append(float(p["raw"]))
        # Cross-fit: opposite parity model only.
        inv = H_odd_inv if int(p["pair_index"]) % 2 == 0 else H_even_inv
        ca = _apply_h(inv, ua)
        cb = _apply_h(inv, ub)
        if ca is None or cb is None:
            row["crossfit_opposite_error_deg"] = ""
            continue
        corr = _opposite_error_deg(ca, cb)
        corrected_errors.append(corr)
        row["crossfit_opposite_error_deg"] = corr

    raw_med = float(np.median(raw_errors)) if raw_errors else float("nan")
    corr_med = float(np.median(corrected_errors)) if corrected_errors else float("nan")
    improvement = 100.0 * (1.0 - corr_med / raw_med) if raw_med > 1e-9 and np.isfinite(corr_med) else float("nan")
    disagreement = _model_disagreement_deg(H_even, H_odd)

    # Descriptive only. The watch-level/partition result decides the research outcome.
    coherent = np.isfinite(improvement) and improvement > 10.0 and np.isfinite(disagreement) and disagreement < 1.0
    status = "COHERENT" if coherent else "MEASURED"
    reason = "" if coherent else "held-out angular correction or even/odd model agreement is insufficient"

    return {
        **base,
        "status": status,
        "reason": reason,
        "valid_ticks": len(ticks),
        "usable_pairs": len(pair_data),
        "ellipse_rms": frame.fit_rms,
        "ellipse_axis_ratio": min(frame.rx, frame.ry) / max(frame.rx, frame.ry),
        "phase_deg_mod6": phase,
        "phase_score": phase_score,
        "phase_contrast": phase_contrast,
        "even_ransac_inliers": in_even,
        "odd_ransac_inliers": in_odd,
        "even_train_median_deg": train_even,
        "odd_train_median_deg": train_odd,
        "model_disagreement_median_deg": disagreement,
        "raw_opposite_error_median_deg": raw_med,
        "crossfit_opposite_error_median_deg": corr_med,
        "crossfit_improvement_pct": improvement,
    }, pair_rows


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--resolved", required=True)
    ap.add_argument("--root", required=True)
    ap.add_argument("--summary", required=True)
    ap.add_argument("--pairs", required=True)
    args = ap.parse_args()
    root = Path(args.root)
    with Path(args.resolved).open(newline="", encoding="utf-8") as f:
        records = list(csv.DictReader(f))
    summaries, pairs = [], []
    for row in records:
        if row.get("class_label") != "gen":
            continue
        s, p = _analyse(root / row["local_path"], row)
        summaries.append(s)
        pairs.extend(p)
        print(row.get("source_id"), Path(row["local_path"]).name, s.get("status"),
              "pairs", s.get("usable_pairs", 0), "angular-crossfit", s.get("crossfit_improvement_pct", ""))
    _write_csv(Path(args.summary), summaries)
    _write_csv(Path(args.pairs), pairs)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
