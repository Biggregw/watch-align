#!/usr/bin/env python3
"""Research-only real-photo test for opposing minute-marker perspective.

The production Android path is untouched. This script:
1. finds the existing GMT dial seed + 12 triangle;
2. fits an ellipse to annular dial-edge evidence;
3. affine-normalises the ellipse to a unit circle;
4. detects the inner end of the 48 non-hour minute ticks;
5. forms the 24 opposite pairs and robustly fits the residual projective
   vector described in OPPOSING_MINUTE_PERSPECTIVE_2026-10-04.md;
6. reports whether that correction actually makes opposite tick radii more
   self-consistent.

It is deliberately a detector/geometry experiment, not a QC verdict.
"""
from __future__ import annotations

import argparse
import csv
import math
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

from gmt12_auto_landmarks import _dial_circle, _triangle_candidate


@dataclass(frozen=True)
class EllipseFrame:
    cx: float
    cy: float
    rx: float
    ry: float
    angle_deg: float
    fit_rms: float

    def to_normalized(self, x: float, y: float) -> np.ndarray:
        a = math.radians(self.angle_deg)
        c, s = math.cos(a), math.sin(a)
        dx, dy = x - self.cx, y - self.cy
        return np.array(((c * dx + s * dy) / self.rx,
                         (-s * dx + c * dy) / self.ry), dtype=float)

    def to_image(self, qx: float, qy: float) -> tuple[float, float]:
        a = math.radians(self.angle_deg)
        c, s = math.cos(a), math.sin(a)
        return (self.cx + c * self.rx * qx - s * self.ry * qy,
                self.cy + s * self.rx * qx + c * self.ry * qy)


@dataclass(frozen=True)
class Tick:
    minute: int
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
    a = float(gray[y0, x0]); b = float(gray[y0, x0 + 1])
    c = float(gray[y0 + 1, x0]); d = float(gray[y0 + 1, x0 + 1])
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
    vals = np.array([_line_contrast(gray, frame, rr, clock_deg)
                     for rr in np.linspace(0.84, 0.985, 28)], dtype=float)
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
            strengths.append(best[0]); pts.append((best[1], best[2]))
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
        a = math.radians(angle); c, s = math.cos(a), math.sin(a)
        d = pts.astype(float) - np.array([ecx, ecy])
        xr = d[:, 0] * c + d[:, 1] * s
        yr = -d[:, 0] * s + d[:, 1] * c
        rho = np.sqrt((xr / (w / 2.0)) ** 2 + (yr / (h / 2.0)) ** 2)
        res = np.abs(rho - 1.0)
        med = float(np.median(res)); mad = float(np.median(np.abs(res - med)))
        keep = res <= med + max(0.015, 3.5 * 1.4826 * mad)
        if keep.sum() < 25 or keep.sum() == len(pts):
            break
        pts = pts[keep]
        q = fit(pts)
        if q is None:
            return None
        ecx, ecy, w, h, angle = q

    # Preserve cv2's own width-axis convention. The affine normalisation only
    # needs a consistent pair of orthogonal ellipse axes.
    a = math.radians(angle); c, s = math.cos(a), math.sin(a)
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


def _detect_tick(gray: np.ndarray, frame: EllipseFrame, minute: int, twelve_deg: float) -> Tick | None:
    expected = twelve_deg + 6.0 * minute
    coarse = np.arange(expected - 2.4, expected + 2.4001, 0.12)
    scores = np.array([_angle_score(gray, frame, a) for a in coarse])
    if not np.isfinite(scores).any():
        return None
    best = float(coarse[int(np.nanargmax(scores))])
    fine = np.arange(best - 0.18, best + 0.1801, 0.03)
    fs = np.array([_angle_score(gray, frame, a) for a in fine])
    best = float(fine[int(np.nanargmax(fs))])
    angle_score = float(np.nanmax(fs))
    if angle_score < 3.0:
        return None

    radii = np.linspace(0.825, 0.990, 67)
    con = np.array([_line_contrast(gray, frame, rr, best) for rr in radii], dtype=float)
    if not np.isfinite(con).any():
        return None
    con[~np.isfinite(con)] = -1e9
    peak_i = int(np.argmax(con)); peak = float(con[peak_i])
    if peak < 4.0:
        return None
    threshold = max(2.5, 0.30 * peak)
    lo = peak_i
    misses = 0
    while lo > 0:
        if con[lo - 1] >= threshold:
            lo -= 1; misses = 0
        elif misses < 1 and con[lo - 1] >= threshold * 0.55:
            lo -= 1; misses += 1
        else:
            break
    radius = float(radii[lo])
    if not 0.83 <= radius <= 0.965:
        return None
    t = math.radians(best)
    qx, qy = radius * math.sin(t), -radius * math.cos(t)
    score = min(1.0, max(0.0, (angle_score - 3.0) / 15.0)) * min(1.0, peak / 20.0)
    return Tick(minute, best % 360.0, radius, score, qx, qy)


def _robust_fit(A: np.ndarray, b: np.ndarray, base_w: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    c = np.linalg.lstsq(A * np.sqrt(base_w)[:, None], b * np.sqrt(base_w), rcond=None)[0]
    w = base_w.copy()
    for _ in range(25):
        residual = b - A @ c
        med = float(np.median(residual))
        mad = float(np.median(np.abs(residual - med)))
        scale = max(1.4826 * mad, 5e-4)
        delta = 1.5 * scale
        huber = np.ones_like(residual)
        bad = np.abs(residual - med) > delta
        huber[bad] = delta / np.abs(residual[bad] - med)
        w = base_w * huber
        sw = np.sqrt(np.maximum(w, 1e-6))
        nxt = np.linalg.lstsq(A * sw[:, None], b * sw, rcond=None)[0]
        if np.linalg.norm(nxt - c) < 1e-10:
            c = nxt; break
        c = nxt
    return c, b - A @ c


def _analyse_image(path: Path, metadata: dict) -> tuple[dict, list[dict]]:
    bgr = cv2.imread(str(path), cv2.IMREAD_COLOR)
    base = {k: metadata.get(k, "") for k in ("source_id", "split", "physical_watch_id", "local_path")}
    if bgr is None:
        return {**base, "status": "UNASSESSABLE", "reason": "image decode failed"}, []
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    seed = _dial_circle(gray)
    if seed is None:
        return {**base, "status": "UNASSESSABLE", "reason": "dial seed not found"}, []
    tri = _triangle_candidate(gray, *map(float, seed))
    if tri is None:
        return {**base, "status": "UNASSESSABLE", "reason": "12 triangle not found"}, []
    frame = _fit_outer_ellipse(gray, seed)
    if frame is None:
        return {**base, "status": "UNASSESSABLE", "reason": "outer ellipse not stable"}, []

    tl, tr, _ = tri
    tx, ty = (tl.x + tr.x) / 2.0, (tl.y + tr.y) / 2.0
    tq = frame.to_normalized(tx, ty)
    twelve_deg = math.degrees(math.atan2(tq[0], -tq[1])) % 360.0

    ticks: dict[int, Tick] = {}
    # Detect all positions so the index remains tied to the physical 12. Hour
    # positions are then excluded from the fit, not silently re-indexed.
    for i in range(60):
        t = _detect_tick(gray, frame, i, twelve_deg)
        if t is not None:
            ticks[i] = t

    rows, vals, weights, pair_rows = [], [], [], []
    for i in range(30):
        if i % 5 == 0:
            continue
        a, b = ticks.get(i), ticks.get(i + 30)
        if a is None or b is None:
            continue
        k = a.radius / b.radius
        v = (1.0 - k) / (1.0 + k)
        theta = math.radians(twelve_deg + 6.0 * i)
        # clock angle basis in the affine-normalised ellipse frame
        u = np.array((math.sin(theta), -math.cos(theta)), dtype=float)
        rows.append(u); vals.append(v); weights.append(max(0.05, min(a.score, b.score)))
        pair_rows.append({**base, "pair": f"{i:02d}<->{i+30:02d}", "minute_a": i,
                          "minute_b": i + 30, "radius_a": a.radius, "radius_b": b.radius,
                          "pair_signal": v, "weight": weights[-1]})

    if len(rows) < 8:
        return {**base, "status": "UNASSESSABLE", "reason": "fewer than 8 usable opposing minor-tick pairs",
                "valid_ticks": len(ticks), "usable_pairs": len(rows), "ellipse_rms": frame.fit_rms}, pair_rows

    A = np.asarray(rows, float); y = np.asarray(vals, float); w = np.asarray(weights, float)
    c, residual = _robust_fit(A, y, w)
    amp = float(np.linalg.norm(c))
    rms = float(np.sqrt(np.average(residual ** 2, weights=w)))
    mad = float(np.median(np.abs(residual - np.median(residual))))

    raw_asym, corrected_asym = [], []
    for pr in pair_rows:
        i = int(pr["minute_a"]); j = int(pr["minute_b"])
        ta, tb = ticks[i], ticks[j]
        qa = np.array((ta.qx, ta.qy)); qb = np.array((tb.qx, tb.qy))
        ra, rb = np.linalg.norm(qa), np.linalg.norm(qb)
        raw_asym.append(abs(ra - rb) / max(1e-9, 0.5 * (ra + rb)))
        da, db = 1.0 - float(qa @ c), 1.0 - float(qb @ c)
        if abs(da) < 0.2 or abs(db) < 0.2:
            continue
        xa, xb = qa / da, qb / db
        rca, rcb = np.linalg.norm(xa), np.linalg.norm(xb)
        corrected_asym.append(abs(rca - rcb) / max(1e-9, 0.5 * (rca + rcb)))

    raw_med = float(np.median(raw_asym))
    corr_med = float(np.median(corrected_asym)) if corrected_asym else float("nan")
    improvement = 100.0 * (1.0 - corr_med / raw_med) if raw_med > 1e-9 and np.isfinite(corr_med) else float("nan")
    direction = math.degrees(math.atan2(c[0], -c[1])) % 360.0

    status = "MEASURED"
    reason = ""
    if rms > 0.012:
        status = "LOW_COHERENCE"; reason = "opposing-pair residual too large for a smooth projective field"

    summary = {**base, "status": status, "reason": reason, "valid_ticks": len(ticks),
               "usable_pairs": len(rows), "ellipse_rms": frame.fit_rms,
               "ellipse_axis_ratio": min(frame.rx, frame.ry) / max(frame.rx, frame.ry),
               "twelve_norm_deg": twelve_deg, "projective_cx": float(c[0]),
               "projective_cy": float(c[1]), "projective_magnitude": amp,
               "projective_direction_deg": direction, "fit_residual_rms": rms,
               "fit_residual_mad": mad, "raw_pair_asym_median": raw_med,
               "corrected_pair_asym_median": corr_med, "pair_asym_improvement_pct": improvement}
    for pr, rr in zip(pair_rows, residual):
        pr["model_residual"] = float(rr)
    return summary, pair_rows


def _write_csv(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        path.write_text("", encoding="utf-8"); return
    keys = []
    for row in rows:
        for k in row:
            if k not in keys:
                keys.append(k)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader(); w.writerows(rows)


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
        s, p = _analyse_image(root / row["local_path"], row)
        summaries.append(s); pairs.extend(p)
        print(row.get("source_id"), Path(row["local_path"]).name, s.get("status"),
              "pairs", s.get("usable_pairs", 0), "improvement", s.get("pair_asym_improvement_pct", ""))
    _write_csv(Path(args.summary), summaries)
    _write_csv(Path(args.pairs), pairs)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
