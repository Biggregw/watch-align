#!/usr/bin/env python3
"""Research-only measurements for 12-series Submariner source images.

This intentionally does not call GMT QC, GMT pose policy, GMT master geometry, or any
Genuine-vs-replica classifier. It measures image quality, a black-dial ellipse, bright
hour-marker candidates, and a raw outward rehaut/rim edge profile. All values are
observations for later corpus analysis, not production verdicts or thresholds.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageOps

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent.parent
DEFAULT_DATA = REPO_ROOT / "datasets" / "submariner_research"


def wrap_deg(v: float) -> float:
    while v > 180.0:
        v -= 360.0
    while v <= -180.0:
        v += 360.0
    return v


def finite(v) -> bool:
    try:
        return math.isfinite(float(v))
    except (TypeError, ValueError):
        return False


def med_mad(values: list[float]) -> tuple[float, float]:
    a = np.asarray([v for v in values if finite(v)], dtype=np.float64)
    if not len(a):
        return math.nan, math.nan
    med = float(np.median(a))
    return med, float(np.median(np.abs(a - med)))


def clean(v):
    if isinstance(v, float) and not math.isfinite(v):
        return ""
    return v


def expected_marker_kind(model: str, hour: int) -> str:
    if hour == 12:
        return "triangle"
    if hour in (6, 9):
        return "baton"
    if hour == 3:
        return "baton" if model.upper() == "124060" else "date"
    return "round"


@dataclass
class DialFrame:
    cx: float
    cy: float
    a: float
    b: float
    angle_deg: float
    source: str
    rms_px: float = math.nan
    inlier_points: int = 0

    @property
    def radius(self) -> float:
        return math.sqrt(self.a * self.b)

    @property
    def ratio(self) -> float:
        return min(self.a, self.b) / max(self.a, self.b)

    def norm(self, x: float, y: float) -> tuple[float, float]:
        t = math.radians(self.angle_deg)
        c, s = math.cos(t), math.sin(t)
        dx, dy = x - self.cx, y - self.cy
        u = c * dx + s * dy
        v = -s * dx + c * dy
        return u / self.a, v / self.b

    def point(self, clock_deg: float, rho: float) -> tuple[float, float]:
        # clock degrees: 0 at 12, positive clockwise.
        p = math.radians(clock_deg)
        u, v = math.sin(p) * self.a * rho, -math.cos(p) * self.b * rho
        t = math.radians(self.angle_deg)
        c, s = math.cos(t), math.sin(t)
        return self.cx + c * u - s * v, self.cy + s * u + c * v


def read_rgb(path: Path, max_side: int = 1600) -> np.ndarray:
    with Image.open(path) as raw:
        im = ImageOps.exif_transpose(raw).convert("RGB")
        w, h = im.size
        scale = min(1.0, max_side / float(max(w, h)))
        if scale < 1.0:
            im = im.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.Resampling.LANCZOS)
        return np.asarray(im)


def sample_nearest(gray: np.ndarray, x: float, y: float) -> float | None:
    xi, yi = int(round(x)), int(round(y))
    if yi < 0 or xi < 0 or yi >= gray.shape[0] or xi >= gray.shape[1]:
        return None
    return float(gray[yi, xi])


def boundary_stats(gray: np.ndarray, cx: float, cy: float, r: float) -> tuple[float, float]:
    delta = max(2.5, min(9.0, 0.024 * r))
    diffs = []
    positive = 0
    for deg in range(0, 360, 6):
        t = math.radians(deg)
        co, si = math.cos(t), math.sin(t)
        a = sample_nearest(gray, cx + co * (r - delta), cy + si * (r - delta))
        b = sample_nearest(gray, cx + co * (r + delta), cy + si * (r + delta))
        if a is None or b is None:
            continue
        d = (b - a) / 255.0
        diffs.append(d)
        positive += d > 0.02
    if len(diffs) < 24:
        return -1.0, 0.0
    return float(np.median(diffs)), positive / len(diffs)


def dark_score(gray: np.ndarray, cx: float, cy: float, r: float) -> float:
    y0, y1 = max(0, int(cy - .58 * r)), min(gray.shape[0], int(cy + .58 * r) + 1)
    x0, x1 = max(0, int(cx - .58 * r)), min(gray.shape[1], int(cx + .58 * r) + 1)
    if y1 <= y0 or x1 <= x0:
        return 0.0
    yy, xx = np.mgrid[y0:y1, x0:x1]
    m = (xx - cx) ** 2 + (yy - cy) ** 2 <= (.55 * r) ** 2
    vals = gray[y0:y1, x0:x1][m]
    if len(vals) < 50:
        return 0.0
    return max(0.0, min(1.0, (150.0 - float(np.median(vals))) / 120.0))


def marker_hit_count(gray: np.ndarray, cx: float, cy: float, r: float) -> int:
    core = []
    for deg in range(0, 360, 15):
        t = math.radians(deg)
        v = sample_nearest(gray, cx + math.sin(t) * .48 * r, cy - math.cos(t) * .48 * r)
        if v is not None:
            core.append(v)
    base = float(np.median(core)) if core else 50.0
    threshold = max(105.0, base + 45.0)
    hits = 0
    for hour in range(12):
        best = 0.0
        centre = hour * 30.0
        for da in (-7.0, -3.5, 0.0, 3.5, 7.0):
            p = math.radians(centre + da)
            for rho in np.linspace(.62, .86, 9):
                v = sample_nearest(gray, cx + math.sin(p) * rho * r, cy - math.cos(p) * rho * r)
                if v is not None:
                    best = max(best, v)
        hits += best >= threshold
    return hits


def locate_dial(gray: np.ndarray) -> tuple[DialFrame | None, dict]:
    h, w = gray.shape
    short = min(h, w)
    small_scale = min(1.0, 520.0 / short)
    if small_scale < 1.0:
        sm = cv2.resize(gray, (round(w * small_scale), round(h * small_scale)), interpolation=cv2.INTER_AREA)
    else:
        sm = gray
    blur = cv2.GaussianBlur(sm, (7, 7), 1.2)
    sshort = min(sm.shape)
    min_r, max_r = max(20, int(.10 * sshort)), int(.47 * sshort)
    circles = None
    for p2 in (30, 24, 20):
        c = cv2.HoughCircles(blur, cv2.HOUGH_GRADIENT, 1.15, sshort / 9.0,
                             param1=110, param2=p2, minRadius=min_r, maxRadius=max_r)
        if c is not None and c.shape[1]:
            circles = c[0]
            break
    if circles is None:
        return None, {"dial_reason": "no_hough_candidate"}

    best = None
    for scx, scy, scr in circles[:24]:
        cx, cy, cr = scx / small_scale, scy / small_scale, scr / small_scale
        lo, hi = max(.10 * short, .56 * cr), min(.46 * short, 1.04 * cr)
        for r in np.linspace(lo, hi, 26):
            boundary, polarity = boundary_stats(gray, cx, cy, r)
            if boundary < .015 or polarity < .45:
                continue
            dark = dark_score(gray, cx, cy, r)
            hits = marker_hit_count(gray, cx, cy, r)
            score = 2.2 * max(0.0, boundary) + .055 * hits + .42 * dark + .12 * polarity
            q = (score, cx, cy, float(r), boundary, polarity, hits, dark)
            if best is None or q[0] > best[0]:
                best = q
    if best is None:
        return None, {"dial_reason": "no_dark_marker_ring_candidate"}
    score, cx, cy, r, boundary, polarity, hits, dark = best
    seed = DialFrame(cx, cy, r, r, 0.0, "circle_seed")
    edge = fit_dial_ellipse(gray, seed)
    frame = edge or seed
    return frame, {
        "dial_reason": "",
        "seed_score": score,
        "seed_cx": cx, "seed_cy": cy, "seed_r": r,
        "seed_boundary": boundary, "seed_polarity": polarity,
        "seed_marker_hits": hits, "seed_dark_score": dark,
        "ellipse_valid": edge is not None,
    }


def ellipse_residual(frame: DialFrame, x: np.ndarray, y: np.ndarray) -> np.ndarray:
    t = math.radians(frame.angle_deg)
    c, s = math.cos(t), math.sin(t)
    dx, dy = x - frame.cx, y - frame.cy
    u = c * dx + s * dy
    v = -s * dx + c * dy
    q = np.sqrt((u / frame.a) ** 2 + (v / frame.b) ** 2)
    return np.abs(q - 1.0) * frame.radius


def fit_dial_ellipse(gray: np.ndarray, seed: DialFrame) -> DialFrame | None:
    pts = []
    for deg in np.linspace(0.0, 360.0, 180, endpoint=False):
        t = math.radians(float(deg))
        rr = np.linspace(.87 * seed.radius, 1.13 * seed.radius, 70)
        vals = []
        good_rr = []
        for r in rr:
            v = sample_nearest(gray, seed.cx + math.cos(t) * r, seed.cy + math.sin(t) * r)
            if v is not None:
                vals.append(v); good_rr.append(r)
        if len(vals) < 20:
            continue
        vals = np.asarray(vals, dtype=np.float64)
        grad = np.convolve(np.diff(vals), np.ones(5) / 5.0, mode="same")
        i = int(np.argmax(grad))
        if grad[i] < 3.5:
            continue
        r = good_rr[min(i + 1, len(good_rr) - 1)]
        pts.append((seed.cx + math.cos(t) * r, seed.cy + math.sin(t) * r))
    if len(pts) < 70:
        return None
    keep = np.ones(len(pts), dtype=bool)
    p = np.asarray(pts, dtype=np.float32)
    frame = None
    for _ in range(4):
        if keep.sum() < 55:
            return None
        (cx, cy), (ww, hh), angle = cv2.fitEllipse(p[keep].reshape(-1, 1, 2))
        if ww <= 0 or hh <= 0:
            return None
        frame = DialFrame(float(cx), float(cy), float(ww / 2), float(hh / 2), float(angle), "ellipse")
        res = ellipse_residual(frame, p[:, 0], p[:, 1])
        med = float(np.median(res[keep]))
        lim = max(2.5, 3.5 * med)
        new_keep = res <= lim
        if np.array_equal(new_keep, keep):
            break
        keep = new_keep
    if frame is None:
        return None
    res = ellipse_residual(frame, p[:, 0], p[:, 1])
    rms = float(np.sqrt(np.mean(res[keep] ** 2)))
    frame.rms_px = rms
    frame.inlier_points = int(keep.sum())
    if frame.ratio < .68:
        return None
    if not (.84 * seed.radius <= frame.radius <= 1.16 * seed.radius):
        return None
    if math.hypot(frame.cx - seed.cx, frame.cy - seed.cy) > .22 * seed.radius:
        return None
    if rms > max(4.0, .035 * frame.radius):
        return None
    return frame


def image_quality(rgb: np.ndarray, frame: DialFrame | None) -> dict:
    gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    out = {"width": rgb.shape[1], "height": rgb.shape[0]}
    if frame is None:
        return out
    y0, y1 = max(0, int(frame.cy - .9 * frame.radius)), min(gray.shape[0], int(frame.cy + .9 * frame.radius) + 1)
    x0, x1 = max(0, int(frame.cx - .9 * frame.radius)), min(gray.shape[1], int(frame.cx + .9 * frame.radius) + 1)
    yy, xx = np.mgrid[y0:y1, x0:x1]
    nxy = np.array([frame.norm(float(x), float(y)) for x, y in zip(xx.ravel(), yy.ravel())])
    mask = (nxy[:, 0] ** 2 + nxy[:, 1] ** 2 <= .86 ** 2).reshape(xx.shape)
    crop = gray[y0:y1, x0:x1]
    vals = crop[mask]
    if len(vals) < 100:
        return out
    lap = cv2.Laplacian(crop, cv2.CV_64F)
    out.update({
        "sharpness": float(np.var(lap[mask])),
        "mean_luma": float(np.mean(vals)),
        "crushed_fraction": float(np.mean(vals <= 3)),
        "blown_fraction": float(np.mean(vals >= 252)),
    })
    hsv = cv2.cvtColor(rgb[y0:y1, x0:x1], cv2.COLOR_RGB2HSV)
    out["glare_fraction"] = float(np.mean((hsv[..., 2][mask] >= 235) & (hsv[..., 1][mask] <= 40)))
    return out


def contour_candidates(rgb: np.ndarray, frame: DialFrame) -> list[dict]:
    gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    h, w = gray.shape
    yy, xx = np.mgrid[0:h, 0:w]
    t = math.radians(frame.angle_deg); c, s = math.cos(t), math.sin(t)
    dx, dy = xx - frame.cx, yy - frame.cy
    u = c * dx + s * dy; v = -s * dx + c * dy
    rho = np.sqrt((u / frame.a) ** 2 + (v / frame.b) ** 2)
    ann = (rho >= .55) & (rho <= .90)
    vals = gray[ann]
    if len(vals) < 100:
        return []
    threshold = max(105.0, float(np.percentile(vals, 78)))
    bw = ((gray >= threshold) & ann).astype(np.uint8) * 255
    bw = cv2.morphologyEx(bw, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    contours, _ = cv2.findContours(bw, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    out = []
    dial_area = math.pi * frame.radius * frame.radius
    for cnt in contours:
        area = float(cv2.contourArea(cnt))
        area_n = area / dial_area
        if not (.00015 <= area_n <= .026):
            continue
        m = cv2.moments(cnt)
        if abs(m["m00"]) < 1e-9:
            continue
        cx, cy = m["m10"] / m["m00"], m["m01"] / m["m00"]
        xn, yn = frame.norm(cx, cy)
        rr = math.hypot(xn, yn)
        if not (.57 <= rr <= .88):
            continue
        clock = math.degrees(math.atan2(xn, -yn)) % 360.0
        peri = float(cv2.arcLength(cnt, True))
        circ = 4 * math.pi * area / (peri * peri) if peri > 0 else 0.0
        rect = cv2.minAreaRect(cnt)
        rw, rh = rect[1]
        major, minor = max(rw, rh), min(rw, rh)
        aspect = major / minor if minor > 1e-6 else math.inf
        hull = cv2.convexHull(cnt)
        hull_area = float(cv2.contourArea(hull))
        solidity = area / hull_area if hull_area > 0 else 0.0
        out.append({"cx": cx, "cy": cy, "xn": xn, "yn": yn, "rho": rr, "clock_deg": clock,
                    "area_norm": area_n, "circularity": circ, "aspect": aspect,
                    "solidity": solidity, "rect_angle_deg": float(rect[2])})
    return out


def assign_markers(cands: list[dict], model: str) -> list[dict]:
    rows = []
    used = set()
    for hour in range(1, 13):
        kind = expected_marker_kind(model, hour)
        if kind == "date":
            rows.append({"hour": hour, "kind": kind, "found": False, "reason": "date_window"})
            continue
        target = 0.0 if hour == 12 else hour * 30.0
        best = None
        for i, c in enumerate(cands):
            if i in used:
                continue
            err = wrap_deg(c["clock_deg"] - target)
            if abs(err) > 13.0:
                continue
            shape_penalty = 0.0
            if kind == "round":
                shape_penalty = max(0.0, .55 - c["circularity"]) * 1.5 + max(0.0, c["aspect"] - 1.9) * .12
            elif kind == "baton":
                shape_penalty = max(0.0, 1.25 - c["aspect"]) * .5
            score = abs(err) / 13.0 + abs(c["rho"] - .74) / .22 + shape_penalty - min(.25, c["area_norm"] * 12)
            if best is None or score < best[0]:
                best = (score, i, err, c)
        if best is None:
            rows.append({"hour": hour, "kind": kind, "found": False, "reason": "no_bright_contour_candidate"})
        else:
            _, i, err, c = best
            used.add(i)
            q = dict(c)
            q.update({"hour": hour, "kind": kind, "found": True, "reason": "", "angle_error_deg": err})
            rows.append(q)
    found = [r for r in rows if r.get("found")]
    roll, _ = med_mad([r["angle_error_deg"] for r in found])
    for r in found:
        r["angle_residual_after_roll_deg"] = wrap_deg(r["angle_error_deg"] - roll) if finite(roll) else math.nan
    return rows


def rehaut_profile(gray: np.ndarray, frame: DialFrame, clock_deg: float) -> dict:
    rr = np.linspace(1.015, 1.24, 92)
    vals = []
    good = []
    for rho in rr:
        x, y = frame.point(clock_deg, float(rho))
        v = sample_nearest(gray, x, y)
        if v is not None:
            vals.append(v); good.append(float(rho))
    if len(vals) < 30:
        return {"rho": math.nan, "width": math.nan, "gradient": math.nan}
    v = np.asarray(vals, dtype=np.float64)
    grad = np.convolve(np.diff(v), np.ones(5) / 5.0, mode="same")
    # The next physical rim can be bright-to-dark or dark-to-bright depending on lighting.
    i = int(np.argmax(np.abs(grad)))
    rho = good[min(i + 1, len(good) - 1)]
    return {"rho": rho, "width": rho - 1.0, "gradient": float(grad[i])}


def complete_dial(frame: DialFrame, shape: tuple[int, int]) -> bool:
    h, w = shape
    pts = [frame.point(d, 1.03) for d in range(0, 360, 15)]
    return all(0 <= x < w and 0 <= y < h for x, y in pts)


def load_manifest(path: Path) -> list[dict]:
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def write_csv(path: Path, rows: list[dict], fieldnames: list[str] | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if fieldnames is None:
        keys = []
        seen = set()
        for r in rows:
            for k in r:
                if k not in seen:
                    seen.add(k); keys.append(k)
        fieldnames = keys
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: clean(v) for k, v in r.items()})


def measure_one(root: Path, row: dict) -> tuple[dict, list[dict]]:
    ident = {"candidate_id": row["candidate_id"], "class": row.get("class_label", ""),
             "model": row.get("model", ""), "factory": row.get("factory", ""),
             "image_index": row.get("image_index", ""), "local_path": row.get("local_path", ""),
             "sha256": row.get("sha256", "")}
    path = root / row["local_path"]
    out = dict(ident)
    markers_out = []
    try:
        rgb = read_rgb(path)
    except Exception as e:
        out.update({"read_ok": False, "error": f"{type(e).__name__}:{e}"})
        return out, markers_out
    gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    out["read_ok"] = True
    out["error"] = ""
    frame, seed = locate_dial(gray)
    out.update(seed)
    out.update(image_quality(rgb, frame))
    out["dial_found"] = frame is not None
    if frame is None:
        return out, markers_out
    out.update({
        "dial_cx": frame.cx, "dial_cy": frame.cy, "dial_a": frame.a, "dial_b": frame.b,
        "dial_radius": frame.radius, "dial_angle_deg": frame.angle_deg, "ellipse_ratio": frame.ratio,
        "ellipse_rms_px": frame.rms_px, "ellipse_inlier_points": frame.inlier_points,
        "dial_complete": complete_dial(frame, gray.shape),
    })
    cands = contour_candidates(rgb, frame)
    marker_rows = assign_markers(cands, row.get("model", ""))
    found = [m for m in marker_rows if m.get("found")]
    round_found = [m for m in found if m.get("kind") == "round"]
    roll, roll_mad = med_mad([m["angle_error_deg"] for m in found])
    rho_med, rho_mad = med_mad([m["rho"] for m in found])
    out.update({"marker_candidates": len(cands), "markers_found": len(found), "round_markers_found": len(round_found),
                "marker_roll_deg": roll, "marker_roll_mad_deg": roll_mad,
                "marker_center_r_median": rho_med, "marker_center_r_mad": rho_mad})
    for m in marker_rows:
        mr = dict(ident); mr.update(m); markers_out.append(mr)
    prof = {}
    for name, deg in (("top", 0.0), ("right", 90.0), ("bottom", 180.0), ("left", 270.0)):
        p = rehaut_profile(gray, frame, deg)
        for k, v in p.items():
            prof[f"rehaut_{name}_{k}"] = v
    out.update(prof)
    if finite(prof.get("rehaut_top_width")) and finite(prof.get("rehaut_bottom_width")) and float(prof["rehaut_bottom_width"]) != 0:
        out["rehaut_top_bottom_ratio"] = float(prof["rehaut_top_width"]) / float(prof["rehaut_bottom_width"])
    if finite(prof.get("rehaut_left_width")) and finite(prof.get("rehaut_right_width")) and float(prof["rehaut_right_width"]) != 0:
        out["rehaut_left_right_ratio"] = float(prof["rehaut_left_width"]) / float(prof["rehaut_right_width"])
    return out, markers_out


def watch_summary(images: list[dict]) -> list[dict]:
    grouped = defaultdict(list)
    for r in images:
        grouped[r["candidate_id"]].append(r)
    out = []
    metrics = ["ellipse_ratio", "marker_roll_deg", "marker_center_r_median", "marker_center_r_mad",
               "rehaut_top_bottom_ratio", "rehaut_left_right_ratio"]
    for cid, rs in sorted(grouped.items()):
        base = rs[0]
        q = {"candidate_id": cid, "class": base.get("class", ""), "model": base.get("model", ""),
             "factory": base.get("factory", ""), "unique_images": len(rs),
             "dial_found_images": sum(bool(r.get("dial_found")) for r in rs),
             "images_with_6plus_markers": sum(int(r.get("markers_found") or 0) >= 6 for r in rs)}
        for k in metrics:
            med, mad = med_mad([r.get(k) for r in rs if finite(r.get(k))])
            q[f"{k}_median"] = med; q[f"{k}_mad"] = mad
        out.append(q)
    return out


def run(root: Path, manifest: Path, out_dir: Path, limit: int = 0) -> dict:
    rows = load_manifest(manifest)
    # One byte-identical image is measured once. Physical-watch grouping remains candidate_id.
    rows = [r for r in rows if not (r.get("exact_duplicate_of") or "").strip()]
    if limit > 0:
        rows = rows[:limit]
    image_rows, marker_rows = [], []
    for i, row in enumerate(rows, 1):
        im, mk = measure_one(root, row)
        image_rows.append(im); marker_rows.extend(mk)
        if i % 20 == 0:
            print(f"measured {i}/{len(rows)}", flush=True)
    watches = watch_summary(image_rows)
    write_csv(out_dir / "submariner_image_measurements.csv", image_rows)
    write_csv(out_dir / "submariner_marker_measurements.csv", marker_rows)
    write_csv(out_dir / "submariner_watch_repeatability.csv", watches)
    report = {
        "images_considered_unique": len(rows),
        "images_read": sum(bool(r.get("read_ok")) for r in image_rows),
        "images_with_dial": sum(bool(r.get("dial_found")) for r in image_rows),
        "images_with_6plus_markers": sum(int(r.get("markers_found") or 0) >= 6 for r in image_rows),
        "physical_watch_sources": len(watches),
        "physical_watches_with_dial": sum(int(r.get("dial_found_images") or 0) > 0 for r in watches),
        "models": dict(sorted(__import__("collections").Counter(r.get("model", "") for r in watches).items())),
        "classes": dict(sorted(__import__("collections").Counter(r.get("class", "") for r in watches).items())),
        "note": "Research measurements only. No GMT QC thresholds, production QC verdicts, or authenticity classification were applied.",
    }
    (out_dir / "submariner_measurement_report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return report


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--root", type=Path, default=DEFAULT_DATA)
    ap.add_argument("--manifest", type=Path, default=DEFAULT_DATA / "acquired_images.csv")
    ap.add_argument("--out", type=Path, default=DEFAULT_DATA / "measurements")
    ap.add_argument("--limit", type=int, default=0)
    a = ap.parse_args(argv)
    report = run(a.root, a.manifest, a.out, a.limit)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
