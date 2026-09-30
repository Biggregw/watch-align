"""Metric definitions and geometry helpers shared by the baseline and the experiments.

All geometry is dimensionless (dial-radius units, degrees, or the app's own ratios), so it does not
depend on photo resolution. Nothing here reads class labels except the explicitly labelled
genuine-vs-replica summaries, which are descriptive only (never used to tune a QC rule)."""
from __future__ import annotations

import json
import math
from collections import defaultdict

import numpy as np

# Raw QC measurements produced by the production analyser (see CorpusQc driver).
QC_METRICS = ("gap", "rot_deg", "base_tilt_deg", "sp59", "sp01", "six_centring", "six_rot", "nine_centring", "nine_rot")
FLAG_FIELDS = ("gap_att", "align_att", "six_att", "nine_att")
FLAGGED = {"CHECK", "STRONG"}
UPPER, LOWER = (10, 11, 1, 2), (4, 5, 7, 8)
LEFT, RIGHT = (7, 8, 10, 11), (1, 2, 4, 5)
ROUND_R = 0.816


def load(path) -> list[dict]:
    return [json.loads(l) for l in open(path) if l.strip()]


def finite(v) -> bool:
    return isinstance(v, (int, float)) and v is not None and math.isfinite(v)


def analysed(r: dict) -> bool:
    return not r.get("error") and bool(r.get("dial_located")) and not r.get("no_readable_dial")


# --------------------------------------------------------------------------- marker geometry
def markers(r: dict) -> list[dict]:
    return [m for m in r.get("round", []) if m.get("found") and finite(m.get("x")) and finite(m.get("y"))]


def dial_radius(r: dict) -> float:
    return math.sqrt(r["dial_a"] * r["dial_b"])


def radial_ratios(r: dict) -> dict[int, float]:
    """Observed distance of each found round marker from the dial centre / expected distance."""
    R = dial_radius(r)
    out = {}
    for m in markers(r):
        out[int(m["hour"])] = math.hypot(m["x"] - r["dial_cx"], m["y"] - r["dial_cy"]) / (ROUND_R * R)
    return out


def bias(r: dict) -> dict:
    """Top/bottom and left/right radial bias of the round-marker layout (dial-radius units)."""
    rr = radial_ratios(r)
    def mean(hs):
        v = [rr[h] for h in hs if h in rr]
        return float(np.mean(v)) if len(v) >= 2 else float("nan")
    return {"tb_bias": mean(UPPER) - mean(LOWER), "lr_bias": mean(LEFT) - mean(RIGHT)}


def _norm_pts(r: dict):
    ms = markers(r)
    R = dial_radius(r)
    src = np.array([[m["mx"], m["my"]] for m in ms], float)                     # master layout (at ROUND_R, dial radius 1)
    dst = np.array([[(m["x"] - r["dial_cx"]) / R, (m["y"] - r["dial_cy"]) / R] for m in ms], float)
    return src, dst, [int(m["hour"]) for m in ms]


def fit_affine(src, dst):
    A = np.hstack([src, np.ones((len(src), 1))])
    P, *_ = np.linalg.lstsq(A, dst, rcond=None)          # 3x2
    return P


def apply_affine(P, pts):
    return np.hstack([pts, np.ones((len(pts), 1))]) @ P


def fit_homography(src, dst):
    """Normalised DLT; None when degenerate (needs >= 5 points to be over-determined)."""
    if len(src) < 5:
        return None
    rows = []
    for (x, y), (u, v) in zip(src, dst):
        rows.append([-x, -y, -1, 0, 0, 0, u * x, u * y, u])
        rows.append([0, 0, 0, -x, -y, -1, v * x, v * y, v])
    _, s, vt = np.linalg.svd(np.array(rows))
    H = vt[-1].reshape(3, 3)
    if abs(H[2, 2]) < 1e-12:
        return None
    return H / H[2, 2]


def apply_h(H, pts):
    p = np.hstack([pts, np.ones((len(pts), 1))]) @ H.T
    return p[:, :2] / p[:, 2:3]


def projective(r: dict) -> dict:
    """Affine and projective fits of the round-marker layout to the master layout.

    keystone_x / keystone_y are the homography's perspective terms (h31, h32) in dial-radius
    units: the rate at which apparent scale changes across the dial. Leave-one-out residuals
    compare how well each model predicts a marker it was not fitted to."""
    src, dst, hours = _norm_pts(r)
    out = {"n_markers": len(src)}
    if len(src) < 5:
        return out
    P = fit_affine(src, dst)
    out["affine_resid"] = float(np.sqrt(np.mean(np.sum((apply_affine(P, src) - dst) ** 2, axis=1))))
    H = fit_homography(src, dst)
    if H is not None:
        out["keystone_x"], out["keystone_y"] = float(H[2, 0]), float(H[2, 1])
        out["homog_resid"] = float(np.sqrt(np.mean(np.sum((apply_h(H, src) - dst) ** 2, axis=1))))
    loo_a, loo_h = [], []
    for i in range(len(src)):
        keep = [k for k in range(len(src)) if k != i]
        Pa = fit_affine(src[keep], dst[keep])
        loo_a.append(float(np.linalg.norm(apply_affine(Pa, src[i:i + 1])[0] - dst[i])))
        if len(keep) >= 5:
            Hh = fit_homography(src[keep], dst[keep])
            if Hh is not None:
                loo_h.append(float(np.linalg.norm(apply_h(Hh, src[i:i + 1])[0] - dst[i])))
    out["affine_loo"] = float(np.median(loo_a))
    if loo_h:
        out["homog_loo"] = float(np.median(loo_h))
    return out


def rehaut(r: dict) -> dict:
    """Visible rehaut widths (app's local sector analysis) as dimensionless asymmetries."""
    ks = ("rh_top", "rh_bottom", "rh_left", "rh_right")
    if not all(finite(r.get(k)) for k in ks):
        return {}
    t, b, l, rt = (r[k] for k in ks)
    mean = (t + b + l + rt) / 4
    cov = min(r.get(k + "_cov") or 0 for k in ks)
    return {"rh_tb": (t - b) / mean, "rh_lr": (l - rt) / mean, "rh_cov": cov, "rh_mean_rel": mean / dial_radius(r),
            "rh_ratio_tb": t / b if b else float("nan"), "rh_ratio_lr": l / rt if rt else float("nan")}


def ellipse_components(r: dict) -> dict:
    """Dial-ellipse tilt split along the dial's vertical and horizontal axes (sign unknown: an
    ellipse cannot say which side is nearer the camera)."""
    if not (finite(r.get("ell_tilt")) and finite(r.get("ell_minor_clock"))):
        return {}
    t, c = r["ell_tilt"], math.radians(r["ell_minor_clock"])
    return {"ell_tilt_v": abs(t * math.cos(c)), "ell_tilt_h": abs(t * math.sin(c))}


# --------------------------------------------------------------------------- statistics
def by_watch(recs: list[dict], key: str) -> dict[str, list[float]]:
    d = defaultdict(list)
    for r in recs:
        v = r.get(key)
        if finite(v):
            d[r["watch"]].append(float(v))
    return d


def pooled_within_sd(groups: dict[str, list[float]]) -> tuple[float, int, int]:
    """Pooled within-watch SD over watches with >= 2 views: (sd, watches, df)."""
    ss, df, n = 0.0, 0, 0
    for vals in groups.values():
        if len(vals) >= 2:
            ss += float(np.sum((np.array(vals) - np.mean(vals)) ** 2))
            df += len(vals) - 1
            n += 1
    return (math.sqrt(ss / df) if df else float("nan")), n, df


def spearman(x, y) -> float:
    x, y = np.asarray(x, float), np.asarray(y, float)
    ok = np.isfinite(x) & np.isfinite(y)
    if ok.sum() < 5:
        return float("nan")
    rx = np.argsort(np.argsort(x[ok]))
    ry = np.argsort(np.argsort(y[ok]))
    return float(np.corrcoef(rx, ry)[0, 1])


def auc(a, b) -> float:
    """P(value from b > value from a) + 0.5 ties; 0.5 = no separation."""
    a, b = [x for x in a if finite(x)], [x for x in b if finite(x)]
    if not a or not b:
        return float("nan")
    s = sum((1.0 if y > x else 0.5 if y == x else 0.0) for x in a for y in b)
    return s / (len(a) * len(b))


def q(v, p):
    v = [x for x in v if finite(x)]
    return float(np.percentile(v, p)) if v else float("nan")
