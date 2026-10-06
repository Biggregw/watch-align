#!/usr/bin/env python3
"""
Alpha91 GMT overlay registration — reproducible offline research script.

RESEARCH ONLY. Alpha90 production (f66acee) is not touched.

Pipeline (one fixed method, no per-photo parameters; FROZEN 2026-10-06):

  0. Master from the bare genuine dial: homography fitted only to the exact 6-degree minute
     lattice + circular dial edge; minute-track and applied-marker geometry measured rectified.
  1. Coarse pose/localisation: SIFT+MAGSAC (degenerate solutions rejected) then masked ECC on
     gradient magnitude in the canonical frame (basin only).
  2. 12 direction / minute-lattice branch: circular correlation of the marker-annulus polar band
     with the bare dial (unique over 360 deg). The start is rotated onto that branch. This cue
     never enters the homography fit.
  3. Refine H from the tick lattice, single global rule for all photos:
       a. per-tick 2D intensity centroid stage (exact 6-degree lattice predicted by current H);
       b. final full-resolution stage: tangential centreline from the always-visible inner part
          of each tick + radial tick INNER-END edge (immune to the rehaut hiding outer tick ends).
     Sector-balanced (12 x 30 deg) Huber least squares over the 8 free parameters.
     Masked: ticks 11-19 (date/cyclops), 29-31 (SWISS MADE), everything inside 0.918R.
     Hands crossing ticks are gated out per tick.
  4. Fail closed: reject if the 12 cue is not distinctive, if refinement changes lattice phase
     by > 2 deg, if the result lands on a +/-6 deg branch, if < 8 tick sectors were used, or if the
     tick-lattice residual is not clean (median > 0.30 px, rms > 0.60 px after setting aside gross
     single-tick outliers such as a hand tip crossing one tick, or > 10% gross outliers).
     (2026-10-06: rms made robust to a single hand-crossed tick after a reproducible false reject on
     RL_ARF_BLRO_CROOKED6; gate statistics only, H unchanged.)

  Holdouts (never used in fitting): applied-marker outlines by sub-pixel edge profiles; markers
  whose outline is mostly hidden (hands, adjacent print) are excluded as invalid holdouts.
  Raised-marker parallax is reported SEPARATELY and never applied to H.
  Tested and rejected earlier: one-coefficient radial lens distortion (unstable, <0.02 px gain).

Usage:
  python3 alpha91_overlay_registration.py --inputs <alpha91_claude_overlay_inputs> --out <dir> [--robustness]
"""
import argparse
import json
import os

import cv2
import numpy as np
from scipy.ndimage import map_coordinates, spline_filter
from scipy.optimize import least_squares

NAMES = ['EXT_EXT_GEN_BLRO_WEX_01', 'EXT_EXT_GEN_BLRO_WEX_02', 'POOL_GEN_HO_01', 'POOL_GEN_HO_02']
FIT_EXCLUDED_TICKS = set(range(11, 20)) | {29, 30, 31}
ROUND_HOURS = (1, 2, 4, 5, 7, 8, 10, 11)


# --------------------------------------------------------------------------- geometry
def proj(H, P):
    q = np.c_[P, np.ones(len(P))] @ H.T
    return q[:, :2] / q[:, 2:]


def unit_dirs(h_or_minute_deg):
    a = np.radians(h_or_minute_deg)
    return np.array([np.sin(a), -np.cos(a)]), np.array([np.cos(a), np.sin(a)])


def jac(H, p):
    e = 1e-4
    f = lambda q: proj(H, np.array([q]))[0]
    return np.c_[(f(p + [e, 0]) - f(p - [e, 0])) / (2 * e), (f(p + [0, e]) - f(p - [0, e])) / (2 * e)]


def px_per_R(H):
    return float(np.hypot(*(proj(H, np.array([[1., 0]])) - proj(H, np.zeros((1, 2))))[0]))


def circle_lsq(P):
    A = np.c_[2 * P, np.ones(len(P))]
    s = np.linalg.lstsq(A, (P ** 2).sum(1), rcond=None)[0]
    return s[0], s[1], np.sqrt(s[2] + s[0] ** 2 + s[1] ** 2)


class Sampler:
    def __init__(self, gray):
        self.c = spline_filter(gray.astype(np.float32), order=3)

    def at(self, H, Q):
        Q = np.asarray(Q)
        q = proj(H, Q.reshape(-1, 2))
        return map_coordinates(self.c, [q[:, 1], q[:, 0]], order=3, prefilter=False).reshape(Q.shape[:-1])


# --------------------------------------------------------------------------- 0. master
def build_master(bare_path):
    g = cv2.cvtColor(cv2.imread(bare_path), cv2.COLOR_BGR2GRAY).astype(np.float32)
    m = (g < 128).astype(np.uint8)
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((25, 25), np.uint8))
    n, lab, st, _ = cv2.connectedComponentsWithStats(m)
    k = 1 + np.argmax(st[1:, 4])
    cs, _ = cv2.findContours((lab == k).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    (cx, cy), (a, b), _ = cv2.fitEllipse(max(cs, key=len))
    R = (a + b) / 4
    # dial edge points (sub-pixel radial gradient maxima)
    E = []
    rs = np.arange(0.95 * R, 1.05 * R, 0.1)
    for t in np.radians(np.arange(0, 360, 1.0)):
        p = map_coordinates(g, [cy - rs * np.cos(t), cx + rs * np.sin(t)], order=1)
        d = np.diff(p); i = int(np.argmax(d))
        o = 0.5 * (d[i - 1] - d[i + 1]) / (d[i - 1] - 2 * d[i] + d[i + 1]) if 0 < i < len(d) - 1 else 0
        r = rs[i] + 0.05 + o * 0.1
        E.append((cx + r * np.sin(t), cy - r * np.cos(t)))
    E = np.array(E)
    # tick angles and half-max ends (polar about the provisional centre)
    A = np.radians(np.arange(0, 360, 0.05))
    prof = np.mean([map_coordinates(g, [cy - r * np.cos(A), cx + r * np.sin(A)], order=1)
                    for r in np.linspace(0.945, 0.965, 9) * R], 0)
    ticks = []
    for mnt in range(60):
        a0 = mnt * 6
        sel = np.abs(((np.degrees(A) - a0 + 180) % 360) - 180) < 2.0
        aa = (np.degrees(A[sel]) - a0 + 180) % 360 - 180 + a0; p = prof[sel]
        bg = np.percentile(p, 20); w = np.clip(p - bg - (p.max() - bg) * 0.3, 0, None)
        ac = np.radians((aa * w).sum() / w.sum())
        rr = np.arange(0.88, 1.0, 0.0005) * R
        pr = np.mean([map_coordinates(g, [cy - rr * np.cos(ac) + o * np.sin(ac), cx + rr * np.sin(ac) + o * np.cos(ac)], order=1)
                      for o in (-0.5, 0, 0.5)], 0)
        bgv = np.median(pr[(rr < 0.905 * R) | (rr > 0.99 * R)]); pk = np.percentile(pr[(rr > 0.94 * R) & (rr < 0.97 * R)], 50)
        half = bgv + 0.5 * (pk - bgv); i0 = int(np.argmin(abs(rr - 0.955 * R))); lo = hi = i0
        while lo > 0 and pr[lo - 1] > half: lo -= 1
        while hi < len(pr) - 1 and pr[hi + 1] > half: hi += 1
        f = lambda i, j: rr[i] + (half - pr[i]) / (pr[j] - pr[i]) * (rr[j] - rr[i])
        ticks.append((ac, f(lo - 1, lo), f(hi, hi + 1)))
    T = np.array(ticks)
    inner = np.c_[cx + T[:, 1] * np.sin(T[:, 0]), cy - T[:, 1] * np.cos(T[:, 0])]
    outer = np.c_[cx + T[:, 2] * np.sin(T[:, 0]), cy - T[:, 2] * np.cos(T[:, 0])]
    minor = np.array([i % 5 != 0 for i in range(60)])
    ang = np.radians(np.arange(60) * 6)

    def res(p):
        Hh = np.append(p[:8], 1).reshape(3, 3); rin, rout = p[8], p[9]
        r1 = (proj(Hh, np.c_[rin * np.sin(ang), -rin * np.cos(ang)][minor]) - inner[minor]).ravel()
        r2 = (proj(Hh, np.c_[rout * np.sin(ang), -rout * np.cos(ang)]) - outer).ravel()
        ce = proj(np.linalg.inv(Hh), E)
        return np.r_[r1, r2, (np.hypot(ce[:, 0], ce[:, 1]) - 1.0) * R]

    s = least_squares(res, np.array([R, 0, cx, 0, R, cy, 0, 0, 0.933, 0.98]), loss='soft_l1',
                      x_scale=np.r_[R, R, R, R, R, R, 1e-3, 1e-3, 0.01, 0.01])
    Hb = np.append(s.x[:8], 1).reshape(3, 3)
    rin, rout = float(s.x[8]), float(s.x[9])
    mid = proj(np.linalg.inv(Hb), (inner + outer) / 2)
    tick_ang_res = (np.degrees(np.arctan2(mid[:, 0], -mid[:, 1])) - np.arange(60) * 6 + 180) % 360 - 180

    # rectified canonical bare image
    S, C = 400.0, 450
    K = np.array([[1 / S, 0, -C / S], [0, 1 / S, -C / S], [0, 0, 1]])
    W = cv2.warpPerspective(g, Hb @ K, (2 * C, 2 * C), flags=cv2.INTER_CUBIC | cv2.WARP_INVERSE_MAP)
    b = (W > 110).astype(np.uint8)
    n, lab, st, cen = cv2.connectedComponentsWithStats(b)
    markers = {}
    for i in range(1, n):
        x, y = (cen[i] - C) / S
        if not (0.6 < np.hypot(x, y) < 0.9 and st[i, 4] > 2000):
            continue
        hh = int(round((np.degrees(np.arctan2(x, -y)) % 360) / 30)) % 12 or 12
        cs, _ = cv2.findContours((lab == i).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
        c = max(cs, key=len)
        if hh in ROUND_HOURS:
            X, Y, r = circle_lsq((c.reshape(-1, 2) - C) / S)
            markers[hh] = dict(r=float(np.hypot(X, Y)), radius=float(r))
        elif hh in (3,):
            continue
        else:
            M = cv2.moments((lab == i).astype(np.uint8))
            X = (M['m10'] / M['m00'] - C) / S; Y = (M['m01'] / M['m00'] - C) / S
            ap = (cv2.approxPolyDP(c, 4, True).reshape(-1, 2) - C) / S
            markers[hh] = dict(r=float(np.hypot(X, Y)), vertices=ap.tolist())
    round_r = float(np.mean([markers[h]['r'] for h in ROUND_HOURS]))
    round_rad = float(np.mean([markers[h]['radius'] for h in ROUND_HOURS]))
    v6 = np.array(markers[6]['vertices']); v9 = np.array(markers[9]['vertices']); v12 = np.array(markers[12]['vertices'])
    baton_r = float((markers[6]['r'] + markers[9]['r']) / 2)
    # extents by projection onto each marker's own radial / tangential axes (not corner radii)
    e6r, e6t = unit_dirs(180); e9r, e9t = unit_dirs(270); e12r, e12t = unit_dirs(0)
    baton_rh = float((np.ptp(v6 @ e6r) + np.ptp(v9 @ e9r)) / 4)
    baton_th = float((np.ptp(v6 @ e6t) + np.ptp(v9 @ e9t)) / 4)
    rad12 = v12 @ e12r; base = np.argsort(rad12)[-2:]
    tri_apex = float(rad12.min()); tri_base = float(rad12[base].mean())
    tri_hb = float(np.ptp(v12[base] @ e12t) / 2)
    master = dict(
        bare_R_px=float(R), tick_inner_R=rin, tick_outer_R=rout,
        tick_lattice_rms_inner_px=None,
        tick_angle_residual_deg_maxabs=float(np.abs(tick_ang_res).max()),
        round_center_R=round_r, round_outer_radius_R=round_rad,
        round_center_R_by_hour={h: markers[h]['r'] for h in ROUND_HOURS},
        baton_center_R=baton_r, baton_center_R_6=markers[6]['r'], baton_center_R_9=markers[9]['r'],
        baton_radial_half=baton_rh, baton_tangential_half=baton_th,
        triangle_apex_R=tri_apex, triangle_base_R=tri_base, triangle_half_base=tri_hb,
        triangle_area_centroid_R=float(tri_apex + 2 / 3 * (tri_base - tri_apex)),
        frozen_master_for_comparison=dict(MINUTE_TRACK_R=0.925, MINUTE_TRACK_OUTER_R=0.972, ROUND_CENTER_R=0.816,
                                          ROUND_OUTER_R=0.088, MARKER_CENTER_R=0.758, TRI_CENTER_R=0.750),
    )
    return master, W, (S, C)


# --------------------------------------------------------------------------- 1. coarse
def coarse(ref_canon, SC, photo_gray):
    S, C = SC
    K = np.array([[S, 0, C], [0, S, C], [0, 0, 1]])
    yy, xx = np.mgrid[:ref_canon.shape[0], :ref_canon.shape[1]]
    mref = ((np.hypot(xx - C, yy - C) / S) < 0.99).astype(np.uint8) * 255
    sift = cv2.SIFT_create(nfeatures=4000)
    kr, dr = sift.detectAndCompute(np.clip(np.round(ref_canon), 0, 255).astype(np.uint8), mref)
    kp, dp = sift.detectAndCompute(np.clip(np.round(photo_gray), 0, 255).astype(np.uint8), None)
    good = [a for a, b in cv2.BFMatcher().knnMatch(dr, dp, k=2) if a.distance < 0.8 * b.distance]
    src = np.float32([kr[a.queryIdx].pt for a in good]); dst = np.float32([kp[a.trainIdx].pt for a in good])
    for method, thr in ((cv2.USAC_MAGSAC, 3.0), (cv2.RANSAC, 3.0), (cv2.RANSAC, 6.0)):
        Hm, inl = cv2.findHomography(src, dst, method, thr)
        if Hm is None:
            continue
        H = Hm @ K; H = H / H[2, 2]
        # reject degenerate solutions: the unit dial must map to a plausible, non-collapsed ellipse
        ring = proj(H, np.c_[np.cos(np.linspace(0, 2 * np.pi, 16)), np.sin(np.linspace(0, 2 * np.pi, 16))])
        r = np.hypot(*(ring - proj(H, np.zeros((1, 2)))).T)
        if 40 < r.min() and r.max() < 0.6 * max(photo_gray.shape) and r.min() / r.max() > 0.5:
            return H, int(inl.sum())
    raise RuntimeError('coarse registration failed (no plausible dial homography)')


# --------------------------------------------------------------------------- 2/3. fine
def _sector_weights(mm, ok):
    sec = mm // 5; w = np.zeros(len(mm))
    for s_ in np.unique(sec[ok]):
        w[(sec == s_) & ok] = 1.0 / np.sum((sec == s_) & ok)
    return np.sqrt(w)


def _solve(H, Pc, obs, sw, f_scale):
    def res(p):
        return ((proj(np.append(p, 1).reshape(3, 3), Pc) - obs) * sw[:, None]).ravel()
    s = least_squares(res, (H / H[2, 2]).ravel()[:8], loss='huber', f_scale=f_scale, x_scale='jac')
    return np.append(s.x, 1).reshape(3, 3)


def fine_centroid(smp, H, M, iters=8):
    rmid = (M['tick_inner_R'] + M['tick_outer_R']) / 2
    ang = np.arange(60) * 6
    mids = np.array([unit_dirs(a)[0] * rmid for a in ang])
    use = [m for m in range(60) if m not in FIT_EXCLUDED_TICKS]
    for _ in range(iters):
        obs, meta = [], []
        for m in use:
            er, et = unit_dirs(ang[m])
            RR, TT = np.meshgrid(np.arange(0.918, 0.996, 0.002), np.radians(np.arange(-2.5, 2.51, 0.1)) * rmid, indexing='ij')
            I = smp.at(H, RR[..., None] * er + TT[..., None] * et)
            bg = np.median(I, axis=1, keepdims=True); noise = 1.4826 * np.median(np.abs(I - bg))
            w = np.clip(I - bg - 2 * noise, 0, None)
            if w.sum() <= 0:
                continue
            dr = (w * RR).sum() / w.sum() - rmid; dt = (w * TT).sum() / w.sum()
            sp = np.sqrt(max((w * TT ** 2).sum() / w.sum() - dt ** 2, 0))
            obs.append(proj(H, (mids[m] + dr * er + dt * et)[None])[0]); meta.append((m, w.sum(), sp))
        obs = np.array(obs); meta = np.array(meta); mm = meta[:, 0].astype(int)
        ok = (meta[:, 1] > 0.4 * np.median(meta[:, 1])) & (meta[:, 1] < 2.5 * np.median(meta[:, 1])) & (meta[:, 2] < 2 * np.median(meta[:, 2]))
        H = _solve(H, mids[mm], obs, _sector_weights(mm, ok), 0.3 * np.sqrt(1 / 5))
    return H


def tick_inner_observations(smp, H, M):
    RIN = M['tick_inner_R']
    out = []
    for m in range(60):
        er, et = unit_dirs(6 * m)
        RR, TT = np.meshgrid(np.arange(RIN + 0.004, RIN + 0.026, 0.002), np.arange(-0.04, 0.0401, 0.001), indexing='ij')
        I = smp.at(H, RR[..., None] * er + TT[..., None] * et)
        bg = np.median(I, axis=1, keepdims=True); w = I - bg
        w = np.clip(w - 2 * 1.4826 * np.median(np.abs(w)), 0, None)
        if w.sum() <= 0:
            continue
        dt = (w * TT).sum() / w.sum(); sp = np.sqrt(max((w * TT ** 2).sum() / w.sum() - dt ** 2, 0))
        rr = np.arange(RIN - 0.025, RIN + 0.02, 0.0005)
        R2, T2 = np.meshgrid(rr, np.arange(-0.004, 0.0041, 0.002) + dt, indexing='ij')
        v = smp.at(H, R2[..., None] * er + T2[..., None] * et).mean(1)
        d = np.gradient(v); i = int(np.argmax(d))
        if i in (0, len(d) - 1):
            continue
        o = 0.5 * (d[i - 1] - d[i + 1]) / (d[i - 1] - 2 * d[i] + d[i + 1])
        dr = rr[i] + o * 0.0005 - RIN
        out.append((m, dr, dt, w.sum() / RR.shape[0], sp))
    return np.array(out)


def fine_inner(smp, H, M, iters=6, excluded=FIT_EXCLUDED_TICKS):
    RIN = M['tick_inner_R']
    for _ in range(iters):
        T = tick_inner_observations(smp, H, M)
        T = T[[int(m) not in excluded for m in T[:, 0]]]
        mm = T[:, 0].astype(int)
        P0 = np.array([unit_dirs(6 * m)[0] * RIN for m in mm])
        obs = np.array([proj(H, (P0[k] + T[k, 1] * unit_dirs(6 * mm[k])[0] + T[k, 2] * unit_dirs(6 * mm[k])[1])[None])[0]
                        for k in range(len(mm))])
        ok = (T[:, 3] > 0.4 * np.median(T[:, 3])) & (T[:, 3] < 2.5 * np.median(T[:, 3])) & \
             (T[:, 4] < 2 * np.median(T[:, 4])) & (np.abs(T[:, 1]) < 0.02)
        H = _solve(H, P0, obs, _sector_weights(mm, ok), 0.15)
    e = np.hypot(*(proj(H, P0) - obs).T)
    # gate statistics only (H above is unchanged): a hand tip can cross a tick without tripping the
    # mass/width gating; such gross outliers are counted separately instead of inflating the RMS.
    med = float(np.median(e[ok])); gross = ok & (e > max(1.0, 6 * med))
    return H, dict(ticks_used=int(ok.sum()), sectors_used=int(len(np.unique(mm[ok] // 5))),
                   tick_rms_px=float(np.sqrt(np.mean(e[ok] ** 2))), tick_median_px=med,
                   tick_rms_ex_gross_px=float(np.sqrt(np.mean(e[ok & ~gross] ** 2))),
                   gross_outlier_ticks=[int(m) for m in mm[gross]],
                   rejected_ticks=[int(m) for m in mm[~ok]]), (mm, ok, P0, obs)


def coarse_ecc(ref_canon, SC, photo_gray, H, S2=160.0):
    """Basin-only refinement: masked ECC on blurred gradient magnitude in a common canonical frame."""
    S, C = SC
    C2 = int(1.05 * S2)
    K2 = np.array([[1 / S2, 0, -C2 / S2], [0, 1 / S2, -C2 / S2], [0, 0, 1]])
    Kref = np.array([[S, 0, C], [0, S, C], [0, 0, 1]])

    def grad(img):
        img = cv2.GaussianBlur(img.astype(np.float32), (0, 0), 1.2)
        return cv2.magnitude(cv2.Sobel(img, cv2.CV_32F, 1, 0), cv2.Sobel(img, cv2.CV_32F, 0, 1))
    ref = cv2.warpPerspective(ref_canon.astype(np.float32), Kref @ K2, (2 * C2, 2 * C2), flags=cv2.INTER_LINEAR | cv2.WARP_INVERSE_MAP)
    yy, xx = np.mgrid[:2 * C2, :2 * C2]; rr = np.hypot(xx - C2, yy - C2) / S2
    mask = ((rr < 0.99) & (rr > 0.2)).astype(np.uint8)
    W = np.eye(3, dtype=np.float32)
    for sigma in (3.0, 1.5):
        a = cv2.GaussianBlur(grad(ref), (0, 0), sigma)
        cur = cv2.warpPerspective(photo_gray.astype(np.float32), H @ K2, (2 * C2, 2 * C2), flags=cv2.INTER_LINEAR | cv2.WARP_INVERSE_MAP)
        b = cv2.GaussianBlur(grad(cur), (0, 0), sigma)
        try:
            _, W = cv2.findTransformECC(a, b, np.eye(3, dtype=np.float32), cv2.MOTION_HOMOGRAPHY,
                                        (cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 200, 1e-6), mask, 5)
        except cv2.error:
            break
        # ECC: b(W x) ~ a(x)  =>  canonical point u maps to photo via H K2 W K2^-1
        H = H @ K2 @ W.astype(np.float64) @ np.linalg.inv(K2); H = H / H[2, 2]
    return H


def rot3(phi_deg):
    """Canonical clock rotation: a point at clock angle a moves to a + phi."""
    c, s_ = np.cos(np.radians(phi_deg)), np.sin(np.radians(phi_deg))
    return np.array([[c, -s_, 0], [s_, c, 0], [0, 0, 1.0]])


def relative_roll_deg(Ha, Hb):
    """Mean clock-angle change of canonical points when re-expressed from Ha's frame in Hb's frame."""
    a = np.radians(np.arange(0, 360, 30)); P = 0.95 * np.c_[np.sin(a), -np.cos(a)]
    Q = proj(np.linalg.inv(Hb) @ Ha, P)
    d = np.degrees(np.arctan2(Q[:, 0], -Q[:, 1])) - np.degrees(a)
    return float(np.mean((d + 180) % 360 - 180))


def twelve_template(ref_canon, SC, rs, step=0.25):
    """Bare-dial polar band (all 360 degrees) of the applied-marker annulus."""
    S, C = SC
    th = np.radians(np.arange(0, 360, step))
    RR, TT = np.meshgrid(rs, th, indexing='ij')
    return map_coordinates(ref_canon.astype(np.float32), [C - S * RR * np.cos(TT), C + S * RR * np.sin(TT)], order=1)


def twelve_cue(smp, H, M, tmpl, rs, step=0.25):
    """12-direction cue: circular normalised correlation of the photo's marker-annulus polar band with
    the bare genuine dial's band. The layout (12 triangle, 6/9 batons, 3 date window) is unique over
    360 degrees and survives one hand crossing a marker. Returns the clock angle of 12 in the current
    frame. Used ONLY to choose the 6-degree minute-lattice branch and to fail closed; never as a
    homography correspondence, so no single marker can move the transform."""
    th = np.radians(np.arange(0, 360, step))
    RR, TT = np.meshgrid(rs, th, indexing='ij')
    P = smp.at(H, np.stack([RR * np.sin(TT), -RR * np.cos(TT)], -1))
    z = lambda X: (X - X.mean(1, keepdims=True)) / (X.std(1, keepdims=True) + 1e-6)
    a, b = z(P), z(tmpl)
    sc = np.real(np.fft.ifft(np.fft.fft(a, axis=1) * np.conj(np.fft.fft(b, axis=1)), axis=1)).sum(0) / a.size
    i = int(np.argmax(sc)); n = len(sc)
    y0, y1, y2 = sc[i - 1], sc[i], sc[(i + 1) % n]
    o = 0.5 * (y0 - y2) / (y0 - 2 * y1 + y2) if (y0 - 2 * y1 + y2) != 0 else 0.0
    ang = ((i + o) * step + 180) % 360 - 180
    far = np.abs(((np.arange(n) - i + n / 2) % n) - n / 2) * step > 15
    return float(ang), float(y1 / max(sc[far].max(), 1e-6)), float(y1)


CUE_RS = np.arange(0.56, 0.93, 0.005)


def lattice_fit_gate(stats):
    """Residual gate: a homography that has slid onto a wrong local fit of the 6-degree lattice
    leaves ~1 px median / ~2 px rms tick residuals; correct fits are ~0.1-0.16 px median, <0.4 px rms."""
    r = []
    if stats['sectors_used'] < 8: r.append('fewer than 8 tick sectors')
    if stats['tick_median_px'] > 0.30: r.append('tick-lattice median residual > 0.30 px')
    if stats['tick_rms_ex_gross_px'] > 0.60: r.append('tick-lattice rms residual > 0.60 px')
    if len(stats['gross_outlier_ticks']) > 0.10 * stats['ticks_used']: r.append('more than 10% gross tick outliers')
    return r


def register(photo_gray, M, ref_canon, SC, start_roll_deg=0.0):
    """coarse -> 12-direction lattice branch lock -> tick-lattice refinement -> branch/phase check.
    start_roll_deg injects a deliberate start error (robustness test only)."""
    smp = Sampler(photo_gray)
    tmpl = twelve_template(ref_canon, SC, CUE_RS)
    H0, n_inl = coarse(ref_canon, SC, photo_gray)
    H0 = coarse_ecc(ref_canon, SC, photo_gray, H0)
    H0 = H0 @ rot3(start_roll_deg)
    cue0, dist0, sc0 = twelve_cue(smp, H0, M, tmpl, CUE_RS)
    Hlock = H0 @ rot3(cue0)                       # put the 12 triangle on the canonical 12 axis
    H1 = fine_centroid(smp, Hlock, M)
    H2, stats, _ = fine_inner(smp, H1, M)
    cue_f, dist_f, sc_f = twelve_cue(smp, H2, M, tmpl, CUE_RS)
    phase_change = relative_roll_deg(Hlock, H2)
    branch = int(np.round(cue_f / 6.0))
    stats.update(coarse_inliers=n_inl, start_roll_injected_deg=start_roll_deg,
                 twelve_cue_start_deg=cue0, twelve_cue_distinctiveness=dist0, twelve_cue_score=sc0,
                 refinement_phase_change_deg=phase_change, twelve_cue_after_refinement_deg=cue_f,
                 lattice_branch=branch)
    reasons = []
    if dist0 < 1.02: reasons.append('12 cue not distinctive from 30-degree alternatives')
    if abs(phase_change) > 2.0: reasons.append('refinement changed lattice phase > 2 deg')
    if branch != 0 or abs(cue_f) > 3.0: reasons.append('landed on a +/-6 deg lattice branch')
    reasons += lattice_fit_gate(stats)
    stats['accepted'] = not reasons; stats['reject_reasons'] = reasons
    return H2, Hlock, smp, stats


def tick_sector_crossval(smp, H, M):
    """Leave-one-sector-out: refit H without one 30-degree sector of ticks, then measure how far that
    sector's ticks are from the refitted prediction. Independent dial-plane residual per sector."""
    out = {}
    for sec in range(12):
        ticks = set(range(5 * sec, 5 * sec + 5)) - FIT_EXCLUDED_TICKS
        if len(ticks) < 3:
            continue
        Hs, _, _ = fine_inner(smp, H, M, iters=3, excluded=FIT_EXCLUDED_TICKS | ticks)
        T = tick_inner_observations(smp, Hs, M)
        T = T[[int(m) in ticks for m in T[:, 0]]]
        ok = np.abs(T[:, 1]) < 0.02
        if not ok.any():
            continue
        e = []
        for m, dr, dt in T[ok, :3]:
            er, et = unit_dirs(6 * m); p0 = er * M['tick_inner_R']
            e.append(np.hypot(*(proj(Hs, (p0 + dr * er + dt * et)[None]) - proj(Hs, p0[None]))[0]))
        e = np.array(e)
        out[sec] = dict(n=int(ok.sum()), mean_px=float(e.mean()), max_px=float(e.max()))
    return out


# --------------------------------------------------------------------------- holdout
def marker_polygon(M, h):
    if h == 12:
        return np.array([[0, -M['triangle_apex_R']], [M['triangle_half_base'], -M['triangle_base_R']],
                         [-M['triangle_half_base'], -M['triangle_base_R']]])
    er, et = unit_dirs(h * 30); c = M['baton_center_R'] * er
    return np.array([c + s * M['baton_radial_half'] * er + u * M['baton_tangential_half'] * et
                     for s, u in ((-1, -1), (1, -1), (1, 1), (-1, 1))])


def marker_master_point(M, h):
    if h in (6, 9):
        return unit_dirs(h * 30)[0] * M['baton_center_R']
    if h == 12:
        return np.array([0, -M['triangle_area_centroid_R']])
    return unit_dirs(h * 30)[0] * M['round_center_R']


def _edge(smp, H, p0, nrm, span, step=0.0005):
    ts = np.arange(-span, span, step)
    v = smp.at(H, p0 + ts[:, None] * nrm); d = -np.gradient(v); i = int(np.argmax(d))
    if i in (0, len(d) - 1):
        return None, 0
    o = 0.5 * (d[i - 1] - d[i + 1]) / (d[i - 1] - 2 * d[i] + d[i + 1])
    return ts[i] + o * step, d[i]


def measure_marker(smp, H, M, h, span):
    tol = 0.6 / px_per_R(H)
    if h in ROUND_HOURS:
        c0 = marker_master_point(M, h); r0 = M['round_outer_radius_R']; pts, st = [], []
        ths = np.radians(np.arange(0, 360, 5))
        for t in ths:
            n = np.array([np.cos(t), np.sin(t)]); e, s = _edge(smp, H, c0 + r0 * n, n, span)
            if e is not None:
                pts.append(c0 + (r0 + e) * n); st.append(s)
        pts = np.array(pts).reshape(-1, 2); st = np.array(st); good = st > 0.35 * np.median(st)
        if good.sum() < 6:
            return c0, 0.0
        P = pts[good]; best = None; rng = np.random.default_rng(0)
        for _ in range(300):
            s3 = P[rng.choice(len(P), 3, replace=False)]
            try:
                x = np.linalg.solve(np.c_[2 * s3, np.ones(3)], (s3 ** 2).sum(1))
            except np.linalg.LinAlgError:
                continue
            c = x[:2]; r = np.sqrt(x[2] + c @ c)
            if abs(r - r0) > 0.15 * r0:
                continue
            inl = np.abs(np.hypot(*(P - c).T) - r) < tol
            if best is None or inl.sum() > best.sum():
                best = inl
        X, Y, _ = circle_lsq(P[best])
        return np.array([X, Y]), float(best.sum() / len(ths))
    poly = marker_polygon(M, h); lines, cov = [], []
    for k in range(len(poly)):
        a, b = poly[k], poly[(k + 1) % len(poly)]; L = np.linalg.norm(b - a); d = (b - a) / L
        n = np.array([d[1], -d[0]])
        if np.dot(n, (a + b) / 2 - poly.mean(0)) < 0:
            n = -n
        pts, ss = [], []
        for u in np.linspace(0.15, 0.85, 15):
            e, s = _edge(smp, H, a + u * L * d, n, span)
            if e is not None:
                pts.append(a + u * L * d + e * n); ss.append(s)
        pts = np.array(pts).reshape(-1, 2); ss = np.array(ss)
        ok = ss > 0.35 * np.median(ss) if len(ss) else np.zeros(0, bool)
        if ok.sum() < 3:
            cov.append(0.0); lines.append((a, d)); continue
        off = (pts[ok] - a) @ n; med = np.median(off); inl = np.abs(off - med) < tol
        for _ in range(3):
            med = np.mean(off[inl]); inl = np.abs(off - med) < tol
        cov.append(inl.sum() / 15); lines.append((a + med * n, d))
    V = []
    for k in range(len(lines)):
        p1, d1 = lines[k - 1]; p2, d2 = lines[k]
        t = np.linalg.solve(np.c_[d1, -d2], p2 - p1); V.append(p1 + t[0] * d1)
    V = np.array(V); x, y = V[:, 0], V[:, 1]; x1, y1 = np.roll(x, -1), np.roll(y, -1); cr = x * y1 - x1 * y
    A = cr.sum() / 2
    return np.array([((x + x1) * cr).sum(), ((y + y1) * cr).sum()]) / (6 * A), float(min(cov))


def holdouts(smp, H, M, span=0.013, cov_min=0.6, manual=None):
    Rpx = px_per_R(H); rows = []
    for h in (12, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11):
        cobs, cov = measure_marker(smp, H, M, h, span); cm = marker_master_point(M, h)
        pp = proj(H, cm[None])[0]; po = proj(H, cobs[None])[0]; er, et = unit_dirs(h * 30)
        row = dict(hour=h, resid_px=(po - pp).tolist(), err_px=float(np.hypot(*(po - pp))),
                   canon_radial_R=float((cobs - cm) @ er), canon_tangential_R=float((cobs - cm) @ et),
                   radial_px=float((cobs - cm) @ er * Rpx), tangential_px=float((cobs - cm) @ et * Rpx),
                   angle_err_deg=float(np.degrees(np.arctan2((cobs @ et), (cobs @ er)))),
                   radius_R=float(np.hypot(*cobs)), edge_coverage=cov, usable=bool(cov >= cov_min),
                   predicted_px=pp.tolist(), observed_px=po.tolist())
        if manual and h in manual:
            row['old_manual_label_resid_px'] = (np.array(manual[h]) - pp).tolist()
        rows.append(row)
    return rows


def summarise(rows):
    E = np.array([r['err_px'] for r in rows if r['usable']]); D = np.array([r['resid_px'] for r in rows if r['usable']])
    return dict(n=int(len(E)), mean=float(E.mean()), median=float(np.median(E)), max=float(E.max()),
                mean_vector=D.mean(0).tolist())


# --------------------------------------------------------------------------- parallax
def raise_dir(H, p):
    """Image displacement per 1.0R raise above the dial plane at p (f-free, local affine tilt;
    sign from the projective row: raised points shift toward the far, foreshortened side)."""
    A = jac(H, p); U, S, _ = np.linalg.svd(A)
    sin = np.sqrt(max(0.0, 1 - (S[1] / S[0]) ** 2)); u2 = U[:, 1]
    if (A @ np.array([H[2, 0], H[2, 1]])) @ u2 < 0:
        u2 = -u2
    return S[0] * sin * u2


def parallax_loo(results, M):
    data = {}
    for n, r in results.items():
        H = np.array(r['H']); ok = [x for x in r['holdouts'] if x['usable']]
        J = np.array([raise_dir(H, marker_master_point(M, x['hour'])) for x in ok]); D = np.array([x['resid_px'] for x in ok])
        data[n] = (J, D, ok)
    out = {}
    for n in data:
        J_ = np.vstack([data[m][0] for m in data if m != n]); D_ = np.vstack([data[m][1] for m in data if m != n])
        hgt = float((J_ * D_).sum() / (J_ * J_).sum())
        J, D, ok = data[n]; own = float((J * D).sum() / (J * J).sum()); R_ = D - hgt * J; E = np.hypot(*R_.T)
        out[n] = dict(loo_height_R=hgt, own_best_height_R=own, mean=float(E.mean()), median=float(np.median(E)),
                      max=float(E.max()), mean_vector=R_.mean(0).tolist(),
                      per_marker={x['hour']: v.tolist() for x, v in zip(ok, R_)})
    J = np.vstack([d[0] for d in data.values()]); D = np.vstack([d[1] for d in data.values()])
    return dict(pooled_height_R=float((J * D).sum() / (J * J).sum()), per_photo=out)


# --------------------------------------------------------------------------- render
def _draw_overlay(img, H, M, x0, y0, Z, fit_info, holdout_rows):
    used, rejected = fit_info
    def pl(pts, col, closed=True, th=1):
        q = (proj(H, pts) - [x0, y0] + 0.5) * Z - 0.5
        cv2.polylines(img, [np.round(q * 16).astype(np.int32)], closed, col, th, cv2.LINE_AA, shift=4)
    for m in range(60):
        er, _ = unit_dirs(6 * m)
        col = (255, 0, 255) if m in FIT_EXCLUDED_TICKS else ((0, 140, 255) if m in rejected else (0, 255, 255))
        pl(np.array([er * M['tick_inner_R'], er * M['tick_outer_R']]), col, False)
    t = np.radians(np.arange(0, 361, 3))
    status = {r['hour']: r['usable'] for r in holdout_rows}
    for h in (12, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11):
        col = (0, 255, 0) if status.get(h, True) else (0, 0, 255)
        if h in ROUND_HOURS:
            pl(marker_master_point(M, h) + M['round_outer_radius_R'] * np.c_[np.cos(t), np.sin(t)], col)
        else:
            P = marker_polygon(M, h)
            pl(np.vstack([P[k] + np.linspace(0, 1, 20)[:, None] * (P[(k + 1) % len(P)] - P[k]) for k in range(len(P))]), col)


def render_full(bgr, H, M, path, fit_info, rows, Z=4):
    c = proj(H, np.zeros((1, 2)))[0]; Rpx = px_per_R(H)
    x0 = int(c[0] - 1.1 * Rpx); y0 = int(c[1] - 1.1 * Rpx); w = int(2.2 * Rpx)
    crop = cv2.resize(bgr[y0:y0 + w, x0:x0 + w], None, fx=Z, fy=Z, interpolation=cv2.INTER_CUBIC)
    _draw_overlay(crop, H, M, x0, y0, Z, fit_info, rows)
    cv2.imwrite(path, crop, [cv2.IMWRITE_JPEG_QUALITY, 90])


def render_sectors(bgr, H, M, path, fit_info, rows, title, Z=10, tile=380):
    Rpx = px_per_R(H); tiles = []
    err = {r['hour']: r for r in rows}
    for clock in (12, 1.5, 3, 4.5, 6, 7.5, 9, 10.5):
        cpt = proj(H, (unit_dirs(clock * 30)[0] * 0.86)[None])[0]; half = 0.21 * Rpx
        x0 = int(cpt[0] - half); y0 = int(cpt[1] - half); w = int(2 * half)
        crop = cv2.resize(bgr[y0:y0 + w, x0:x0 + w], None, fx=Z, fy=Z, interpolation=cv2.INTER_CUBIC)
        _draw_overlay(crop, H, M, x0, y0, Z, fit_info, rows)
        crop = cv2.resize(crop, (tile, tile), interpolation=cv2.INTER_AREA)
        lab = f"{clock:g} o'clock"
        hrs = [h for h in err if abs(((h % 12) * 30 - clock * 30 + 180) % 360 - 180) <= 16]
        for h in hrs:
            r = err[h]; lab += f"  h{h}: {r['err_px']:.2f}px" if r['usable'] else f"  h{h}: excluded"
        cv2.rectangle(crop, (0, 0), (tile, 22), (0, 0, 0), -1)
        cv2.putText(crop, lab, (4, 16), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (255, 255, 255), 1, cv2.LINE_AA)
        tiles.append(crop)
    sheet = np.vstack([np.hstack(tiles[:4]), np.hstack(tiles[4:])])
    bar = np.zeros((30, sheet.shape[1], 3), np.uint8)
    cv2.putText(bar, title, (6, 21), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 1, cv2.LINE_AA)
    cv2.imwrite(path, np.vstack([bar, sheet]), [cv2.IMWRITE_JPEG_QUALITY, 92])


# --------------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--inputs', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--robustness', action='store_true', help='also rerun each photo from injected start rolls')
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    import sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import alpha91_fairscan_pose_proof as legacy

    M, ref_canon, SC = build_master(os.path.join(args.inputs, 'reference', 'bare_genuine_dial_reference_crop.png'))
    print('MASTER', json.dumps({k: (round(v, 4) if isinstance(v, float) else v) for k, v in M.items()
                                if k not in ('round_center_R_by_hour', 'tick_lattice_rms_inner_px')}, default=str))
    results = {}
    for n in NAMES:
        bgr = cv2.imread(os.path.join(args.inputs, 'genuine_controls', n + '.png'))
        g = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY).astype(np.float32)
        H, Hlock, smp, stats = register(g, M, ref_canon, SC)
        rows = holdouts(smp, H, M, span=0.013, manual=legacy.MARKERS[n])
        cv = tick_sector_crossval(smp, H, M)
        _, _, (mm, ok, _, _) = fine_inner(smp, H, M, iters=1)
        fit_info = (set(int(m) for m in mm[ok]), set(int(m) for m in mm[~ok]))
        results[n] = dict(H=H.tolist(), R_px=px_per_R(H), fit=stats, tick_sector_crossval=cv,
                          holdouts=rows, summary_clean=summarise(rows))
        cvm = np.array([v['mean_px'] for v in cv.values()])
        print(f"\n{n}  R={px_per_R(H):.1f}px  ACCEPTED={stats['accepted']} {stats['reject_reasons']}")
        print(f"  ticks used {stats['ticks_used']} in {stats['sectors_used']} sectors; masked ticks {sorted(FIT_EXCLUDED_TICKS)}; "
              f"gated out {stats['rejected_ticks']}")
        print(f"  dial-plane fit residual: rms {stats['tick_rms_px']:.3f}px median {stats['tick_median_px']:.3f}px")
        print(f"  leave-one-sector-out tick residual: mean over sectors {cvm.mean():.3f}px, worst sector "
              f"{max(cv, key=lambda k: cv[k]['mean_px'])} {cvm.max():.3f}px  " +
              ' '.join(f"s{k}:{v['mean_px']:.2f}" for k, v in cv.items()))
        print(f"  12 cue: start {stats['twelve_cue_start_deg']:+.2f}deg (margin {stats['twelve_cue_distinctiveness']:.3f}), "
              f"after refinement {stats['twelve_cue_after_refinement_deg']:+.2f}deg, phase change "
              f"{stats['refinement_phase_change_deg']:+.3f}deg, branch {stats['lattice_branch']}")
        for r in rows:
            tag = 'clean' if r['usable'] else 'EXCLUDED (outline occluded/unmeasurable)'
            print(f"  h{r['hour']:2d} resid=({r['resid_px'][0]:+.2f},{r['resid_px'][1]:+.2f}) |{r['err_px']:.2f}|px "
                  f"radial {r['radial_px']:+.2f} tangential {r['tangential_px']:+.2f}px ({r['angle_err_deg']:+.2f}deg) "
                  f"r={r['radius_R']:.4f}R cov={r['edge_coverage']:.2f}  {tag}")
        sm = results[n]['summary_clean']
        print(f"  CLEAN HOLDOUTS n={sm['n']}: mean {sm['mean']:.2f}  median {sm['median']:.2f}  max {sm['max']:.2f}px  "
              f"mean vector ({sm['mean_vector'][0]:+.2f},{sm['mean_vector'][1]:+.2f})")
        render_full(bgr, H, M, os.path.join(args.out, f'overlay_{n}.jpg'), fit_info, rows)
        render_sectors(bgr, H, M, os.path.join(args.out, f'sectors_{n}.jpg'), fit_info, rows,
                       f"{n}  clean holdouts mean {sm['mean']:.2f}px  |  yellow=fitted tick  magenta=masked  "
                       f"orange=gated  green=master marker (clean)  red=excluded holdout")
        if args.robustness:
            rob = []
            for r0 in (-20.0, -9.0, -6.0, 6.0, 9.0, 20.0):
                Hr, _, _, st = register(g, M, ref_canon, SC, start_roll_deg=r0)
                a = np.radians(np.arange(0, 360, 15)); G = 0.95 * np.c_[np.sin(a), -np.cos(a)]
                d = float(np.hypot(*(proj(Hr, G) - proj(H, G)).T).max())
                rob.append(dict(start_roll_deg=r0, max_diff_px=d, accepted=st['accepted']))
            results[n]['robustness'] = rob
            print('  robustness (injected start roll -> max dial difference vs baseline):',
                  ' '.join(f"{x['start_roll_deg']:+.0f}deg->{x['max_diff_px']:.3f}px{'' if x['accepted'] else '(REJ)'}" for x in rob))
    par = parallax_loo({n: dict(H=r['H'], holdouts=r['holdouts']) for n, r in results.items()}, M)
    print('\nSEPARATE (not applied to H): raised applied-marker parallax, one shared height, leave-one-photo-out')
    for n, v in par['per_photo'].items():
        print(f"  {n}: h={v['loo_height_R']:.4f}R -> clean marker mean {v['mean']:.2f} median {v['median']:.2f} max {v['max']:.2f}px")
    with open(os.path.join(args.out, 'results.json'), 'w') as f:
        json.dump(dict(master=M, photos=results, marker_parallax_separate=par), f, indent=1, default=str)


if __name__ == '__main__':
    main()
