#!/usr/bin/env python3
"""Research-only real-photo test for opposing minute-marker perspective.

The production Android path is untouched.

This revision deliberately removes the 12-triangle dependency. The minute
track is 60-fold periodic, so its phase can be estimated directly modulo six
degrees; that is sufficient because a point and the point 30 slots later are
opposite regardless of which slot is called minute zero.

The projective fit is also cross-validated. Even opposite pairs fit one pose
vector and predict odd pairs, while odd pairs fit a second vector and predict
even pairs. Therefore the reported correction gain is not measured on the
same pairs used to fit it.
"""
from __future__ import annotations

import argparse
import csv
import math
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

from gmt12_auto_landmarks import _dial_circle


@dataclass(frozen=True)
class EllipseFrame:
    cx: float
    cy: float
    rx: float
    ry: float
    angle_deg: float
    fit_rms: float

    def to_image(self, qx: float, qy: float) -> tuple[float, float]:
        a = math.radians(self.angle_deg)
        c, s = math.cos(a), math.sin(a)
        return (
            self.cx + c * self.rx * qx - s * self.ry * qy,
            self.cy + s * self.rx * qx + c * self.ry * qy,
        )


@dataclass(frozen=True)
class Tick:
    slot: int
    clock_deg: float
    radius: float
    score: float
    qx: float
    qy: float


def _bilinear(gray: np.ndarray, x: float, y: float) -> float:
    h, w = gray.shape[:2]
    if x < 0 or y < 0 or x >= w - 1 or y >= h - 1:
        return float("nan")
    x0, y0 = int(math.floor(x)), int(math.floor(y))
    fx, fy = x - x0, y - y0
    a = float(gray[y0, x0])
    b = float(gray[y0, x0 + 1])
    c = float(gray[y0 + 1, x0])
    d = float(gray[y0 + 1, x0 + 1])
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def _sample(gray: np.ndarray, frame: EllipseFrame, radius: float, clock_deg: float) -> float:
    t = math.radians(clock_deg)
    qx, qy = radius * math.sin(t), -radius * math.cos(t)
    x, y = frame.to_image(qx, qy)
    return _bilinear(gray, x, y)


def _line_contrast(gray: np.ndarray, frame: EllipseFrame, radius: float, clock_deg: float) -> float:
    c = _sample(gray, frame, radius, clock_deg)
    l = _sample(gray, frame, radius, clock_deg - 0.72)
    r = _sample(gray, frame, radius, clock_deg + 0.72)
    if not (np.isfinite(c) and np.isfinite(l) and np.isfinite(r)):
        return float("nan")
    return c - 0.5 * (l + r)


def _angle_score(gray: np.ndarray, frame: EllipseFrame, clock_deg: float) -> float:
    vals = np.array(
        [_line_contrast(gray, frame, rr, clock_deg) for rr in np.linspace(0.835, 0.985, 29)],
        dtype=float,
    )
    vals = vals[np.isfinite(vals)]
    if len(vals) < 10:
        return -1e9
    vals.sort()
    return float(np.mean(vals[-max(5, len(vals) // 3):]))


def _fit_outer_ellipse(gray: np.ndarray, seed: tuple[float, float, float]) -> EllipseFrame | None:
    cx, cy, r = map(float, seed)
    blur = cv2.GaussianBlur(gray, (5, 5), 1.0)
    gx = cv2.Sobel(blur, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(blur, cv2.CV_32F, 0, 1, ksize=3)
    grad = cv2.magnitude(gx, gy)

    pts, strengths = [], []
    for deg in range(0, 360, 2):
        t = math.radians(deg)
        ct, st = math.cos(t), math.sin(t)
        best = None
        for rr in np.linspace(0.82 * r, 1.06 * r, 42):
            x, y = int(round(cx + rr * ct)), int(round(cy + rr * st))
            if x < 1 or y < 1 or x >= gray.shape[1] - 1 or y >= gray.shape[0] - 1:
                continue
            g = float(grad[y, x])
            if best is None or g > best[0]:
                best = (g, float(x), float(y))
        if best is not None:
            strengths.append(best[0])
            pts.append((best[1], best[2]))
    if len(pts) < 40:
        return None

    pts = np.asarray(pts, np.float32)
    strengths = np.asarray(strengths, float)
    pts = pts[strengths >= np.percentile(strengths, 35)]
    if len(pts) < 30:
        return None

    def fit(p):
        try:
            (ecx, ecy), (w, h), angle = cv2.fitEllipse(p.reshape(-1, 1, 2))
        except cv2.error:
            return None
        if min(w, h) <= 1:
            return None
        return float(ecx), float(ecy), float(w), float(h), float(angle)

    q = fit(pts)
    if q is None:
        return None
    ecx, ecy, w, h, angle = q

    for _ in range(2):
        a = math.radians(angle)
        c, s = math.cos(a), math.sin(a)
        d = pts.astype(float) - np.array([ecx, ecy])
        xr = d[:, 0] * c + d[:, 1] * s
        yr = -d[:, 0] * s + d[:, 1] * c
        rho = np.sqrt((xr / (w / 2.0)) ** 2 + (yr / (h / 2.0)) ** 2)
        res = np.abs(rho - 1.0)
        med = float(np.median(res))
        mad = float(np.median(np.abs(res - med)))
        keep = res <= med + max(0.015, 3.5 * 1.4826 * mad)
        if keep.sum() < 25 or keep.sum() == len(pts):
            break
        pts = pts[keep]
        q = fit(pts)
        if q is None:
            return None
        ecx, ecy, w, h, angle = q

    a = math.radians(angle)
    c, s = math.cos(a), math.sin(a)
    d = pts.astype(float) - np.array([ecx, ecy])
    xr = d[:, 0] * c + d[:, 1] * s
    yr = -d[:, 0] * s + d[:, 1] * c
    rho = np.sqrt((xr / (w / 2.0)) ** 2 + (yr / (h / 2.0)) ** 2)
    rms = float(np.sqrt(np.mean((rho - 1.0) ** 2)))
    axis_ratio = min(w, h) / max(w, h)
    if axis_ratio < 0.70 or rms > 0.08:
        return None
    if math.hypot(ecx - cx, ecy - cy) > 0.20 * r:
        return None
    return EllipseFrame(ecx, ecy, w / 2.0, h / 2.0, angle, rms)


def _phase_objective(gray: np.ndarray, frame: EllipseFrame, phase: float) -> float:
    scores = np.array([_angle_score(gray, frame, phase + 6.0 * i) for i in range(60)], dtype=float)
    scores = scores[np.isfinite(scores) & (scores > -1e8)]
    if len(scores) < 45:
        return -1e9
    # A true minute-grid phase should make evidence broadly strong around the
    # whole dial, not merely hit a few hands or hour markers. The median is
    # deliberately resistant to those local bright structures.
    return float(np.median(scores))


def _find_grid_phase(gray: np.ndarray, frame: EllipseFrame) -> tuple[float, float, float] | None:
    coarse = np.arange(0.0, 6.0, 0.12)
    vals = np.array([_phase_objective(gray, frame, p) for p in coarse], dtype=float)
    if not np.isfinite(vals).any():
        return None
    p0 = float(coarse[int(np.nanargmax(vals))])
    fine = np.arange(p0 - 0.16, p0 + 0.1601, 0.02)
    fvals = np.array([_phase_objective(gray, frame, p % 6.0) for p in fine], dtype=float)
    phase = float(fine[int(np.nanargmax(fvals))] % 6.0)
    best = float(np.nanmax(fvals))
    trough = _phase_objective(gray, frame, (phase + 3.0) % 6.0)
    contrast = best - trough
    if best < 3.0:
        return None
    return phase, best, contrast


def _detect_tick(gray: np.ndarray, frame: EllipseFrame, slot: int, phase: float) -> Tick | None:
    expected = phase + 6.0 * slot
    coarse = np.arange(expected - 1.55, expected + 1.5501, 0.10)
    scores = np.array([_angle_score(gray, frame, a) for a in coarse], dtype=float)
    if not np.isfinite(scores).any():
        return None
    best = float(coarse[int(np.nanargmax(scores))])
    fine = np.arange(best - 0.14, best + 0.1401, 0.02)
    fs = np.array([_angle_score(gray, frame, a) for a in fine], dtype=float)
    best = float(fine[int(np.nanargmax(fs))])
    angle_score = float(np.nanmax(fs))
    if angle_score < 3.0:
        return None

    radii = np.linspace(0.820, 0.990, 69)
    con = np.array([_line_contrast(gray, frame, rr, best) for rr in radii], dtype=float)
    if not np.isfinite(con).any():
        return None
    con[~np.isfinite(con)] = -1e9
    peak_i = int(np.argmax(con))
    peak = float(con[peak_i])
    if peak < 4.0:
        return None

    threshold = max(2.5, 0.30 * peak)
    lo = peak_i
    soft = 0
    while lo > 0:
        if con[lo - 1] >= threshold:
            lo -= 1
            soft = 0
        elif soft < 1 and con[lo - 1] >= threshold * 0.55:
            lo -= 1
            soft += 1
        else:
            break
    radius = float(radii[lo])
    if not 0.825 <= radius <= 0.970:
        return None

    t = math.radians(best)
    qx, qy = radius * math.sin(t), -radius * math.cos(t)
    angular_error = abs(((best - expected + 180.0) % 360.0) - 180.0)
    score = (
        min(1.0, max(0.0, (angle_score - 3.0) / 15.0))
        * min(1.0, peak / 20.0)
        * max(0.25, 1.0 - angular_error / 2.0)
    )
    return Tick(slot, best % 360.0, radius, score, qx, qy)


def _robust_fit(A: np.ndarray, b: np.ndarray, base_w: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    sw = np.sqrt(np.maximum(base_w, 1e-6))
    c = np.linalg.lstsq(A * sw[:, None], b * sw, rcond=None)[0]
    for _ in range(25):
        residual = b - A @ c
        med = float(np.median(residual))
        mad = float(np.median(np.abs(residual - med)))
        scale = max(1.4826 * mad, 5e-4)
        delta = 1.5 * scale
        huber = np.ones_like(residual)
        bad = np.abs(residual - med) > delta
        huber[bad] = delta / np.maximum(np.abs(residual[bad] - med), 1e-9)
        w = base_w * huber
        sw = np.sqrt(np.maximum(w, 1e-6))
        nxt = np.linalg.lstsq(A * sw[:, None], b * sw, rcond=None)[0]
        if np.linalg.norm(nxt - c) < 1e-10:
            c = nxt
            break
        c = nxt
    return c, b - A @ c


def _corrected_pair_asym(a: Tick, b: Tick, c: np.ndarray) -> float | None:
    qa = np.array((a.qx, a.qy), dtype=float)
    qb = np.array((b.qx, b.qy), dtype=float)
    da = 1.0 - float(qa @ c)
    db = 1.0 - float(qb @ c)
    if abs(da) < 0.2 or abs(db) < 0.2:
        return None
    xa, xb = qa / da, qb / db
    ra, rb = np.linalg.norm(xa), np.linalg.norm(xb)
    return abs(ra - rb) / max(1e-9, 0.5 * (ra + rb))


def _raw_pair_asym(a: Tick, b: Tick) -> float:
    ra, rb = a.radius, b.radius
    return abs(ra - rb) / max(1e-9, 0.5 * (ra + rb))


def _fit_subset(pair_data: list[dict], parity: int) -> tuple[np.ndarray, np.ndarray] | None:
    subset = [p for p in pair_data if int(p["pair_index"]) % 2 == parity]
    if len(subset) < 6:
        return None
    A = np.asarray([p["basis"] for p in subset], dtype=float)
    y = np.asarray([p["pair_signal"] for p in subset], dtype=float)
    w = np.asarray([p["weight"] for p in subset], dtype=float)
    return _robust_fit(A, y, w)


def _analyse_image(path: Path, metadata: dict) -> tuple[dict, list[dict]]:
    bgr = cv2.imread(str(path), cv2.IMREAD_COLOR)
    base = {k: metadata.get(k, "") for k in ("source_id", "split", "physical_watch_id", "local_path")}
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

    ticks: dict[int, Tick] = {}
    for i in range(60):
        t = _detect_tick(gray, frame, i, phase)
        if t is not None:
            ticks[i] = t

    pair_data: list[dict] = []
    pair_rows: list[dict] = []
    for i in range(30):
        a, b = ticks.get(i), ticks.get(i + 30)
        if a is None or b is None:
            continue
        k = a.radius / b.radius
        signal = (1.0 - k) / (1.0 + k)
        theta = math.radians(phase + 6.0 * i)
        basis = np.array((math.sin(theta), -math.cos(theta)), dtype=float)
        weight = max(0.03, min(a.score, b.score))
        raw = _raw_pair_asym(a, b)
        d = {
            "pair_index": i,
            "a": a,
            "b": b,
            "basis": basis,
            "pair_signal": signal,
            "weight": weight,
            "raw_asym": raw,
        }
        pair_data.append(d)
        pair_rows.append({
            **base,
            "pair_index": i,
            "slot_a": i,
            "slot_b": i + 30,
            "radius_a": a.radius,
            "radius_b": b.radius,
            "pair_signal": signal,
            "weight": weight,
            "raw_asym": raw,
        })

    even_fit = _fit_subset(pair_data, 0)
    odd_fit = _fit_subset(pair_data, 1)
    if even_fit is None or odd_fit is None:
        return {
            **base,
            "status": "UNASSESSABLE",
            "reason": "insufficient independent even/odd opposing-pair coverage",
            "valid_ticks": len(ticks),
            "usable_pairs": len(pair_data),
            "ellipse_rms": frame.fit_rms,
            "phase_score": phase_score,
            "phase_contrast": phase_contrast,
        }, pair_rows

    c_even, r_even = even_fit
    c_odd, r_odd = odd_fit
    c_mean = 0.5 * (c_even + c_odd)
    vector_disagreement = float(np.linalg.norm(c_even - c_odd))
    even_mag = float(np.linalg.norm(c_even))
    odd_mag = float(np.linalg.norm(c_odd))

    raw_all, corrected_cross = [], []
    for p, row in zip(pair_data, pair_rows):
        raw_all.append(float(p["raw_asym"]))
        # Crucial: each pair is corrected only by the model fit to the opposite
        # parity, so the evaluation pair never trained its own correction.
        model = c_odd if int(p["pair_index"]) % 2 == 0 else c_even
        corr = _corrected_pair_asym(p["a"], p["b"], model)
        row["crossfit_corrected_asym"] = "" if corr is None else corr
        if corr is not None:
            corrected_cross.append(corr)

    raw_med = float(np.median(raw_all)) if raw_all else float("nan")
    corr_med = float(np.median(corrected_cross)) if corrected_cross else float("nan")
    improvement = (
        100.0 * (1.0 - corr_med / raw_med)
        if raw_med > 1e-9 and np.isfinite(corr_med)
        else float("nan")
    )

    rms_even = float(np.sqrt(np.mean(r_even ** 2)))
    rms_odd = float(np.sqrt(np.mean(r_odd ** 2)))
    mean_mag = 0.5 * (even_mag + odd_mag)
    direction = math.degrees(math.atan2(c_mean[0], -c_mean[1])) % 360.0

    # Descriptive status only. This is intentionally not a production gate.
    coherent = (
        np.isfinite(improvement)
        and improvement > 0.0
        and vector_disagreement <= max(0.015, 0.75 * max(mean_mag, 1e-6))
    )
    status = "COHERENT" if coherent else "MEASURED"
    reason = "" if coherent else "independent half-pair fits do not yet show a stable common projective field"

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
        "projective_cx": float(c_mean[0]),
        "projective_cy": float(c_mean[1]),
        "projective_magnitude": float(np.linalg.norm(c_mean)),
        "projective_direction_deg": direction,
        "even_fit_magnitude": even_mag,
        "odd_fit_magnitude": odd_mag,
        "vector_disagreement": vector_disagreement,
        "even_fit_residual_rms": rms_even,
        "odd_fit_residual_rms": rms_odd,
        "raw_pair_asym_median": raw_med,
        "crossfit_corrected_asym_median": corr_med,
        "crossfit_improvement_pct": improvement,
    }, pair_rows


def _write_csv(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    keys: list[str] = []
    for row in rows:
        for k in row:
            if k not in keys:
                keys.append(k)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        w.writerows(rows)


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

    summaries: list[dict] = []
    pairs: list[dict] = []
    for row in records:
        if row.get("class_label") != "gen":
            continue
        summary, pair_rows = _analyse_image(root / row["local_path"], row)
        summaries.append(summary)
        pairs.extend(pair_rows)
        print(
            row.get("source_id"),
            Path(row["local_path"]).name,
            summary.get("status"),
            "ticks", summary.get("valid_ticks", 0),
            "pairs", summary.get("usable_pairs", 0),
            "crossfit", summary.get("crossfit_improvement_pct", ""),
        )

    _write_csv(Path(args.summary), summaries)
    _write_csv(Path(args.pairs), pairs)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
