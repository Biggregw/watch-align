#!/usr/bin/env python3
"""
Alpha91 applied-marker residual measurement — offline research layer.

RESEARCH ONLY. Alpha90 production is not touched. No pass/fail tolerances are applied.

Order of operations, per photo:
  1. Recover the dial-plane homography H with the FROZEN minute-lattice solver
     (alpha91_overlay_registration.register). If its fail-closed gates reject the pose, no marker
     is measured (status POSE_REJECTED).
  2. H is then treated as read-only. Applied markers are measured against the bare-dial genuine
     master in H-rectified canonical coordinates. Nothing measured here is ever fed back into H.

Marker measurement (all edges = outer metal outline, bright -> dark going outward):
  - two passes per marker: a wide search (+/-0.035R) around the master outline, robust model fit,
    then a narrow re-search (+/-0.012R) around the FITTED outline and a refit, so the measurement
    follows the real marker rather than the master;
  - round 1,2,4,5,7,8,10,11: robust circle (RANSAC + LSQ) -> centre, radius, circularity (ellipse
    axis ratio of the inlier edge points, perspective-corrected);
  - 6 / 9 batons: 4 freely fitted sides (RANSAC line + TLS) -> polygon area centroid, long-axis
    rotation (mean of the two long sides), length, width;
  - 12 triangle: 3 freely fitted sides -> area centroid, centreline rotation (apex -> base midpoint),
    left/right side angle errors, base tilt, base width, height.
  Occlusion rule: a marker is OCCLUDED / INSUFFICIENT CLEAN EDGE when the inlier coverage of its
  outline is too low (round < 0.60 of rays; any polygon side < 0.50 of samples) or the fitted
  shape is physically implausible for an applied marker (radius/side angles/offsets far outside
  the master). No measurement is forced on an occluded marker.

Reported per usable marker (canonical values converted to photo pixels through the frozen H):
  centre offset (px vector and |px|), offset / R, radial and tangential components (px and R),
  angular position error about the dial centre, shape rotation (deg, + = clockwise on the dial),
  size/shape mismatch, edge coverage, and separately the predicted raised-marker parallax and the
  parallax-adjusted centre offset (parallax height is a single genuine-derived constant; it never
  changes H).

Usage:
  python3 alpha91_marker_measurement.py --inputs <alpha91_marker_measurement_inputs> \
      --bare <bare_genuine_dial_reference_crop.png> --out <dir>
"""
import argparse
import csv
import json
import os
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import alpha91_overlay_registration as REG  # noqa: E402  (frozen solver)

proj, unit_dirs, px_per_R = REG.proj, REG.unit_dirs, REG.px_per_R
ROUND_HOURS = REG.ROUND_HOURS
HOURS = (12, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11)
WIDE, NARROW = 0.035, 0.012


# --------------------------------------------------------------------------- edge sampling
def edge_on_normal(smp, H, p0, n, span, step=0.0005):
    """OUTERMOST significant bright->dark transition along +n within +/-span of p0 (sub-sample
    parabola). Outermost, not strongest: on the shadowed side of a raised marker the inner
    lume/ring edge can be stronger than the true footprint edge."""
    ts = np.arange(-span, span + 1e-12, step)
    v = smp.at(H, p0 + ts[:, None] * n)
    d = -np.gradient(v)
    dmax = d.max()
    if dmax <= 0:
        return None, 0.0
    peaks = [i for i in range(1, len(d) - 1) if d[i] >= d[i - 1] and d[i] >= d[i + 1] and d[i] >= 0.4 * dmax]
    if not peaks:
        return None, 0.0
    i = peaks[-1]
    o = 0.5 * (d[i - 1] - d[i + 1]) / (d[i - 1] - 2 * d[i] + d[i + 1]) if (d[i - 1] - 2 * d[i] + d[i + 1]) != 0 else 0.0
    return p0 + (ts[i] + o * step) * n, float(d[i])


def outline_integrity(smp, H, outline, normals, Rpx, depth_R):
    """Occlusion test independent of the edge fit: along the measured outline the band inside the
    marker (on the lume, depth_R inward) must be bright and the band just outside (2.5 px) must be
    dial-dark. A hand crossing the outline breaks one or the other. Returns the clean fraction."""
    P = np.asarray(outline); nrm = np.asarray(normals)
    inside = smp.at(H, P - depth_R * nrm); outside = smp.at(H, P + 2.5 / Rpx * nrm)
    c = np.median(inside) - np.median(outside)
    if c <= 0:
        return 0.0, np.zeros(len(P), bool)
    ok = (inside - outside > 0.5 * c) & (outside < np.median(outside) + 0.35 * c) & (inside > np.median(inside) - 0.5 * c)
    return float(ok.mean()), ok


def _strong(points, strengths):
    P = np.array([p for p in points if p is not None]).reshape(-1, 2)
    s = np.array([x for p, x in zip(points, strengths) if p is not None])
    if len(s) == 0:
        return P, s
    keep = s > 0.35 * np.median(s)
    return P[keep], s[keep]


# --------------------------------------------------------------------------- round markers
def _ransac_circle(P, r0, tol, iters=400, seed=0):
    rng = np.random.default_rng(seed); best = None
    for _ in range(iters):
        s = P[rng.choice(len(P), 3, replace=False)]
        try:
            x = np.linalg.solve(np.c_[2 * s, np.ones(3)], (s ** 2).sum(1))
        except np.linalg.LinAlgError:
            continue
        c = x[:2]; r = np.sqrt(max(x[2] + c @ c, 0))
        if not (0.7 * r0 < r < 1.3 * r0):
            continue
        inl = np.abs(np.hypot(*(P - c).T) - r) < tol
        if best is None or inl.sum() > best.sum():
            best = inl
    if best is None or best.sum() < 6:
        return None
    X, Y, r = REG.circle_lsq(P[best])
    return np.array([X, Y]), r, best


def measure_round(smp, H, M, h, tol):
    c0 = REG.marker_master_point(M, h); r0 = M['round_outer_radius_R']
    ths = np.radians(np.arange(0, 360, 5)); n_rays = len(ths)
    centre, radius = c0, r0
    for span in (WIDE, NARROW):
        pts, st = [], []
        for t in ths:
            n = np.array([np.cos(t), np.sin(t)])
            p, s = edge_on_normal(smp, H, centre + radius * n, n, span)
            pts.append(p); st.append(s)
        P, _ = _strong(pts, st)
        if len(P) < 12:
            return dict(status='OCCLUDED', reason='insufficient clean edge', coverage=len(P) / n_rays)
        fit = _ransac_circle(P, r0, tol)
        if fit is None:
            return dict(status='OCCLUDED', reason='no consistent circular outline', coverage=0.0)
        centre, radius, inl = fit
    cov = float(inl.sum() / n_rays)
    Q = P[inl]
    # angular spread of inliers: a hand can leave one long clean arc; require the outline all round
    ang = np.degrees(np.arctan2(Q[:, 1] - centre[1], Q[:, 0] - centre[0])) % 360
    sectors = len(np.unique((ang // 45).astype(int)))
    out = dict(centre=centre, radius_R=float(radius), coverage=cov, sectors45=int(sectors), inliers=Q)
    if len(Q) >= 6:
        (_, _), (a, b), _ = cv2.fitEllipse((Q * 1e4).astype(np.float32))
        out['circularity'] = float(min(a, b) / max(a, b))
    t = np.radians(np.arange(0, 360, 2))
    nr = np.c_[np.cos(t), np.sin(t)]
    integ, _ = outline_integrity(smp, H, centre + radius * nr, nr, px_per_R(H), 0.035)
    out['outline_integrity'] = integ
    if integ < 0.80:
        out.update(status='OCCLUDED', reason=f'hand/occluder crosses outline (clean perimeter {integ:.2f})')
    elif cov < 0.60 or sectors < 7:
        out.update(status='OCCLUDED', reason=f'outline coverage {cov:.2f}, {sectors}/8 octants clean')
    elif abs(radius - r0) > 0.15 * r0:
        out.update(status='OCCLUDED', reason='implausible radius (likely hand edge)')
    else:
        out['status'] = 'OK'
    return out


# --------------------------------------------------------------------------- polygon markers
def _ransac_line(P, tol, iters=300, seed=0):
    rng = np.random.default_rng(seed); best = None
    if len(P) < 4:
        return None
    for _ in range(iters):
        i, j = rng.choice(len(P), 2, replace=False)
        d = P[j] - P[i]; L = np.linalg.norm(d)
        if L < 1e-6:
            continue
        n = np.array([-d[1], d[0]]) / L
        inl = np.abs((P - P[i]) @ n) < tol
        if best is None or inl.sum() > best.sum():
            best = inl
    Q = P[best]; c = Q.mean(0)
    _, _, Vt = np.linalg.svd(Q - c); d = Vt[0]
    return c, d, best


def _intersect(p1, d1, p2, d2):
    t = np.linalg.solve(np.c_[d1, -d2], p2 - p1)
    return p1 + t[0] * d1


def _poly_centroid(V):
    x, y = V[:, 0], V[:, 1]; x1, y1 = np.roll(x, -1), np.roll(y, -1); cr = x * y1 - x1 * y
    A = cr.sum() / 2
    return np.array([((x + x1) * cr).sum(), ((y + y1) * cr).sum()]) / (6 * A), abs(A)


def _line_angle_deg(d):
    return np.degrees(np.arctan2(d[1], d[0]))


def _ang_diff_line(a, b):
    """signed difference between two undirected line angles, in (-90, 90]; + = clockwise on dial."""
    return (a - b + 90) % 180 - 90


def measure_polygon(smp, H, M, h, tol):
    poly = REG.marker_polygon(M, h); k = len(poly); cen = poly.mean(0)
    sides = []
    for i in range(k):
        a, b = poly[i], poly[(i + 1) % k]; L = np.linalg.norm(b - a); d = (b - a) / L
        n = np.array([d[1], -d[0]])
        if np.dot(n, (a + b) / 2 - cen) < 0:
            n = -n
        sides.append((a, b, d, n, L))
    fitted = [(s[0], s[2]) for s in sides]           # start from master lines
    covs = [0.0] * k
    for span in (WIDE, NARROW):
        new = []
        for i, (a, b, d_m, n_m, L) in enumerate(sides):
            p_line, d_line = fitted[i]
            n = np.array([d_line[1], -d_line[0]])
            if np.dot(n, n_m) < 0:
                n = -n
            us = np.linspace(0.12, 0.88, 21)
            base_pts = [a + u * L * d_m for u in us]
            # project master sample positions onto the current fitted line, search along its normal
            pts, st = [], []
            for q in base_pts:
                q_on = p_line + np.dot(q - p_line, d_line) * d_line
                p, s = edge_on_normal(smp, H, q_on, n, span)
                pts.append(p); st.append(s)
            P, _ = _strong(pts, st)
            fit = _ransac_line(P, tol)
            if fit is None or fit[2].sum() < 5:
                new.append((p_line, d_line)); covs[i] = 0.0; continue
            c, d, inl = fit
            if np.dot(d, d_m) < 0:
                d = -d
            new.append((c, d)); covs[i] = float(inl.sum() / len(us))
        fitted = new
    V = np.array([_intersect(fitted[i - 1][0], fitted[i - 1][1], fitted[i][0], fitted[i][1]) for i in range(k)])
    centre, area = _poly_centroid(V)
    ang_err = [float(_ang_diff_line(_line_angle_deg(fitted[i][1]), _line_angle_deg(sides[i][2]))) for i in range(k)]
    side_off = [float(np.dot(fitted[i][0] - sides[i][0], sides[i][3])) for i in range(k)]
    out = dict(centre=centre, vertices=V, side_coverage=covs, side_angle_err_deg=ang_err,
               side_offset_R=side_off, coverage=float(min(covs)), area_R2=float(area))
    integ_sides = []
    for i in range(k):
        a, b = V[i], V[(i + 1) % k]   # fitted side i runs from vertex i to vertex i+1
        seg = a + np.linspace(0.15, 0.85, 30)[:, None] * (b - a)
        dd = (b - a) / np.linalg.norm(b - a); nn = np.array([dd[1], -dd[0]])
        if np.dot(nn, (a + b) / 2 - centre) < 0:
            nn = -nn
        integ_sides.append(outline_integrity(smp, H, seg, np.tile(nn, (len(seg), 1)), px_per_R(H),
                                             0.02 if h == 12 else 0.03)[0])
    out['outline_integrity_sides'] = integ_sides; out['outline_integrity'] = float(min(integ_sides))
    if min(integ_sides) < 0.80:
        out.update(status='OCCLUDED', reason=f'hand/occluder crosses outline (clean side fraction {[round(x, 2) for x in integ_sides]})')
    elif min(covs) < 0.50:
        out.update(status='OCCLUDED', reason=f'side edge coverage {[round(c, 2) for c in covs]}')
    elif max(abs(x) for x in ang_err) > 12 or max(abs(x) for x in side_off) > 0.04:
        out.update(status='OCCLUDED', reason='implausible side fit (likely hand edge)')
    else:
        out['status'] = 'OK'
    er, et = unit_dirs(h * 30)
    if h in (6, 9):
        # sides: 0 inner end, 1 long side, 2 outer end, 3 long side (see REG.marker_polygon ordering)
        long_idx = [i for i in range(4) if abs(np.dot(sides[i][2], er)) > 0.7]
        end_idx = [i for i in range(4) if i not in long_idx]
        out['long_axis_rotation_deg'] = float(np.mean([ang_err[i] for i in long_idx]))
        out['long_side_angle_diff_deg'] = float(ang_err[long_idx[0]] - ang_err[long_idx[1]])
        out['end_rotation_deg'] = float(np.mean([ang_err[i] for i in end_idx]))
        out['length_R'] = float(abs(np.dot(fitted[end_idx[0]][0] - fitted[end_idx[1]][0],
                                           np.array([fitted[end_idx[0]][1][1], -fitted[end_idx[0]][1][0]]))))
        out['width_R'] = float(abs(np.dot(fitted[long_idx[0]][0] - fitted[long_idx[1]][0],
                                          np.array([fitted[long_idx[0]][1][1], -fitted[long_idx[0]][1][0]]))))
    else:
        apex_i = int(np.argmin(np.hypot(*V.T)))          # vertex nearest the dial centre
        base = [i for i in range(3) if i != apex_i]
        apex = V[apex_i]; bmid = V[base].mean(0)
        axis = bmid - apex
        out['centreline_rotation_deg'] = float(_ang_diff_line(_line_angle_deg(axis), _line_angle_deg(er)))
        # side i joins vertex i and i+1: the base side has no apex vertex
        base_side = [i for i in range(3) if apex_i not in (i, (i + 1) % 3)][0]
        slanted = [i for i in range(3) if i != base_side]
        # left/right in dial terms (left = smaller x at 12)
        sl = sorted(slanted, key=lambda i: (sides[i][0] + sides[i][1])[0])
        out['left_side_angle_err_deg'] = ang_err[sl[0]]
        out['right_side_angle_err_deg'] = ang_err[sl[1]]
        out['left_right_mismatch_deg'] = float(ang_err[sl[0]] - ang_err[sl[1]])
        out['base_tilt_deg'] = ang_err[base_side]
        out['base_width_R'] = float(np.linalg.norm(V[base[0]] - V[base[1]]))
        out['height_R'] = float(abs(np.dot(apex - V[base[0]], np.array([-fitted[base_side][1][1], fitted[base_side][1][0]]))))
    return out


# --------------------------------------------------------------------------- per photo
def measure_photo(name, bgr, M, ref, SC, parallax_h=None):
    g = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY).astype(np.float32)
    H, Hlock, smp, stats = REG.register(g, M, ref, SC)
    H.setflags(write=False)                                  # frozen from here on
    Rpx = px_per_R(H); tol = 0.5 / Rpx
    res = dict(photo=name, R_px=Rpx, pose=stats, H=H.tolist(), markers=[])
    if not stats['accepted']:
        res['status'] = 'POSE_REJECTED'
        return res, H, smp
    res['status'] = 'OK'
    for h in HOURS:
        m = measure_round(smp, H, M, h, tol) if h in ROUND_HOURS else measure_polygon(smp, H, M, h, tol)
        row = dict(photo=name, hour=h, kind='round' if h in ROUND_HOURS else ('triangle' if h == 12 else 'baton'),
                   status=m['status'], reason=m.get('reason', ''), coverage=m.get('coverage', 0.0))
        if m['status'] == 'OK':
            cm = REG.marker_master_point(M, h); co = m['centre']; er, et = unit_dirs(h * 30)
            d_px = proj(H, co[None])[0] - proj(H, cm[None])[0]
            row.update(dx_px=d_px[0], dy_px=d_px[1], offset_px=float(np.hypot(*d_px)),
                       offset_R=float(np.hypot(*(co - cm))),
                       radial_R=float((co - cm) @ er), tangential_R=float((co - cm) @ et),
                       radial_px=float((co - cm) @ er * Rpx), tangential_px=float((co - cm) @ et * Rpx),
                       angular_position_err_deg=float(np.degrees(np.arctan2(co @ et, co @ er))))
            if h in ROUND_HOURS:
                row.update(radius_R=m['radius_R'], radius_err_R=m['radius_R'] - M['round_outer_radius_R'],
                           radius_err_px=(m['radius_R'] - M['round_outer_radius_R']) * Rpx,
                           circularity=m.get('circularity', np.nan), octants=m['sectors45'])
            elif h in (6, 9):
                row.update(rotation_deg=m['long_axis_rotation_deg'], long_side_parallelism_deg=m['long_side_angle_diff_deg'],
                           end_rotation_deg=m['end_rotation_deg'],
                           length_err_px=(m['length_R'] - 2 * M['baton_radial_half']) * Rpx,
                           width_err_px=(m['width_R'] - 2 * M['baton_tangential_half']) * Rpx)
            else:
                row.update(rotation_deg=m['centreline_rotation_deg'],
                           left_side_angle_err_deg=m['left_side_angle_err_deg'],
                           right_side_angle_err_deg=m['right_side_angle_err_deg'],
                           left_right_mismatch_deg=m['left_right_mismatch_deg'], base_tilt_deg=m['base_tilt_deg'],
                           base_width_err_px=(m['base_width_R'] - 2 * M['triangle_half_base']) * Rpx,
                           height_err_px=(m['height_R'] - (M['triangle_base_R'] - M['triangle_apex_R'])) * Rpx)
            pr = REG.raise_dir(H, cm)
            row.update(parallax_unit_dx=pr[0], parallax_unit_dy=pr[1])
        row['outline_integrity'] = m.get('outline_integrity')
        row['_m'] = m
        res['markers'].append(row)
    return res, H, smp


def apply_parallax(res, h_R):
    for r in res['markers']:
        if r['status'] != 'OK':
            continue
        px = h_R * np.array([r['parallax_unit_dx'], r['parallax_unit_dy']])
        r['parallax_height_R'] = h_R; r['parallax_dx_px'], r['parallax_dy_px'] = px
        r['adj_dx_px'] = r['dx_px'] - px[0]; r['adj_dy_px'] = r['dy_px'] - px[1]
        r['adj_offset_px'] = float(np.hypot(r['adj_dx_px'], r['adj_dy_px']))


def fit_parallax_h(results):
    J, D = [], []
    for res in results:
        for r in res['markers']:
            if r['status'] == 'OK' and r['kind'] == 'round':        # rounds: cleanest centres
                J.append([r['parallax_unit_dx'], r['parallax_unit_dy']]); D.append([r['dx_px'], r['dy_px']])
    J, D = np.array(J), np.array(D)
    return float((J * D).sum() / (J * J).sum()) if len(J) else 0.0


def ring_decomposition(res, M, H):
    """Separate whole-marker-ring terms from local marker residuals (measurement only; H unchanged).
    Canonical offsets of all clean marker centres are fitted, robustly, with one common translation,
    one uniform scale about the dial centre and one rotation about the dial centre (= the marker ring
    relative to the minute track). Local residual = raw offset - ring model."""
    rows = [r for r in res['markers'] if r['status'] == 'OK']
    if len(rows) < 5:
        res['ring'] = dict(status='INSUFFICIENT_MARKERS', n=len(rows))
        return
    P = np.array([REG.marker_master_point(M, r['hour']) for r in rows])
    D = np.array([r['_m']['centre'] for r in rows]) - P
    A = np.zeros((2 * len(P), 4)); b = D.ravel()
    A[0::2, 0] = 1; A[1::2, 1] = 1
    A[0::2, 2] = P[:, 0]; A[1::2, 2] = P[:, 1]            # scale - 1
    A[0::2, 3] = -P[:, 1]; A[1::2, 3] = P[:, 0]           # rotation (rad, + = clockwise on dial)
    w = np.ones(len(b)); Rpx = px_per_R(H)
    for _ in range(10):
        x = np.linalg.lstsq(A * w[:, None], b * w, rcond=None)[0]
        r_ = (b - A @ x) * Rpx; c = 1.0                    # Huber, 1 px
        w = np.sqrt(np.where(np.abs(r_) <= c, 1.0, c / np.maximum(np.abs(r_), 1e-9)))
    t_px = proj(H, x[:2][None])[0] - proj(H, np.zeros((1, 2)))[0]
    res['ring'] = dict(status='OK', n=len(rows), translation_px=t_px.tolist(), translation_R=x[:2].tolist(),
                       scale_pct=100 * x[2], scale_px_at_round_radius=x[2] * M['round_center_R'] * Rpx,
                       rotation_deg=float(np.degrees(x[3])))
    model = (A @ x).reshape(-1, 2)
    for r, p0, dm in zip(rows, P, model):
        co = r['_m']['centre'] - dm                        # centre with ring terms removed
        d_px = proj(H, co[None])[0] - proj(H, p0[None])[0]; er, et = unit_dirs(r['hour'] * 30)
        r.update(local_dx_px=d_px[0], local_dy_px=d_px[1], local_offset_px=float(np.hypot(*d_px)),
                 local_radial_px=float((co - p0) @ er * Rpx), local_tangential_px=float((co - p0) @ et * Rpx))


# --------------------------------------------------------------------------- rendering
def _pl(img, pts_canon, H, x0, y0, Z, col, closed=True, th=1):
    q = (proj(H, np.asarray(pts_canon)) - [x0, y0] + 0.5) * Z - 0.5
    cv2.polylines(img, [np.round(q * 16).astype(np.int32)], closed, col, th, cv2.LINE_AA, shift=4)


def _cross(img, p_canon, H, x0, y0, Z, col, s=6):
    q = (proj(H, np.asarray(p_canon)[None])[0] - [x0, y0] + 0.5) * Z - 0.5
    x, y = int(round(q[0])), int(round(q[1]))
    cv2.line(img, (x - s, y), (x + s, y), col, 1, cv2.LINE_AA); cv2.line(img, (x, y - s), (x, y + s), col, 1, cv2.LINE_AA)


def master_outline(M, h):
    if h in ROUND_HOURS:
        t = np.radians(np.arange(0, 361, 3))
        return REG.marker_master_point(M, h) + M['round_outer_radius_R'] * np.c_[np.cos(t), np.sin(t)]
    P = REG.marker_polygon(M, h)
    return np.vstack([P[k] + np.linspace(0, 1, 20)[:, None] * (P[(k + 1) % len(P)] - P[k]) for k in range(len(P))])


def measured_outline(m, h):
    if h in ROUND_HOURS:
        t = np.radians(np.arange(0, 361, 3))
        return m['centre'] + m['radius_R'] * np.c_[np.cos(t), np.sin(t)]
    V = m['vertices']
    return np.vstack([V[k] + np.linspace(0, 1, 20)[:, None] * (V[(k + 1) % len(V)] - V[k]) for k in range(len(V))])


def marker_crop(bgr, H, M, row, Z=12, tile=300):
    h = row['hour']; m = row['_m']; Rpx = px_per_R(H)
    c = proj(H, REG.marker_master_point(M, h)[None])[0]
    half = (0.21 if h in (6, 9, 12) else 0.13) * Rpx
    x0 = int(c[0] - half); y0 = int(c[1] - half); w = int(2 * half)
    crop = cv2.resize(bgr[y0:y0 + w, x0:x0 + w], None, fx=Z, fy=Z, interpolation=cv2.INTER_CUBIC)
    _pl(crop, master_outline(M, h), H, x0, y0, Z, (0, 255, 0))
    _cross(crop, REG.marker_master_point(M, h), H, x0, y0, Z, (0, 255, 0), 10)
    if row['status'] == 'OK':
        _pl(crop, measured_outline(m, h), H, x0, y0, Z, (255, 0, 255))
        _cross(crop, m['centre'], H, x0, y0, Z, (255, 0, 255), 10)
    crop = cv2.resize(crop, (tile, tile), interpolation=cv2.INTER_AREA)
    lines = [f"h{h} {row['status']}"]
    if row['status'] == 'OK':
        lines.append(f"d=({row['dx_px']:+.2f},{row['dy_px']:+.2f})px |{row['offset_px']:.2f}|")
        lines.append(f"rad {row['radial_px']:+.2f} tan {row['tangential_px']:+.2f}px")
        if 'rotation_deg' in row:
            lines.append(f"rot {row['rotation_deg']:+.2f}deg")
        if 'radius_err_px' in row:
            lines.append(f"radius {row['radius_err_px']:+.2f}px")
    else:
        lines.append(row['reason'][:40])
    cv2.rectangle(crop, (0, 0), (tile, 14 * len(lines) + 4), (0, 0, 0), -1)
    for i, s in enumerate(lines):
        cv2.putText(crop, s, (3, 13 + 14 * i), cv2.FONT_HERSHEY_SIMPLEX, 0.4,
                    (0, 0, 255) if (i == 0 and row['status'] != 'OK') else (255, 255, 255), 1, cv2.LINE_AA)
    return crop


def render_photo(bgr, H, M, res, out_dir):
    tiles = [marker_crop(bgr, H, M, r) for r in res['markers']]
    while len(tiles) % 6:
        tiles.append(np.zeros_like(tiles[0]))
    sheet = np.vstack([np.hstack(tiles[i:i + 6]) for i in range(0, len(tiles), 6)])
    bar = np.zeros((26, sheet.shape[1], 3), np.uint8)
    cv2.putText(bar, f"{res['photo']}  green = genuine master (through frozen H)   magenta = measured marker   "
                     f"+ = centres", (5, 18), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)
    cv2.imwrite(os.path.join(out_dir, f"markers_{res['photo']}.jpg"), np.vstack([bar, sheet]), [cv2.IMWRITE_JPEG_QUALITY, 92])
    # full dial: master (green), measured (magenta), residual vectors x10 (cyan), occluded markers red
    Z = 3; c = proj(H, np.zeros((1, 2)))[0]; Rpx = px_per_R(H)
    x0 = int(c[0] - 1.1 * Rpx); y0 = int(c[1] - 1.1 * Rpx); w = int(2.2 * Rpx)
    img = cv2.resize(bgr[y0:y0 + w, x0:x0 + w], None, fx=Z, fy=Z, interpolation=cv2.INTER_CUBIC)
    for mnt in range(60):
        er, _ = unit_dirs(6 * mnt)
        _pl(img, [er * M['tick_inner_R'], er * M['tick_outer_R']], H, x0, y0, Z, (0, 255, 255), False)
    for r in res['markers']:
        h = r['hour']
        _pl(img, master_outline(M, h), H, x0, y0, Z, (0, 255, 0) if r['status'] == 'OK' else (0, 0, 255))
        if r['status'] == 'OK':
            _pl(img, measured_outline(r['_m'], h), H, x0, y0, Z, (255, 0, 255))
            p = (proj(H, REG.marker_master_point(M, h)[None])[0] - [x0, y0] + 0.5) * Z - 0.5
            q = p + 10 * Z * np.array([r['dx_px'], r['dy_px']])
            cv2.arrowedLine(img, tuple(np.round(p).astype(int)), tuple(np.round(q).astype(int)), (255, 255, 0), 2,
                            cv2.LINE_AA, tipLength=0.25)
    cv2.putText(img, "residual arrows x10 (cyan); green master; magenta measured; red = occluded", (10, 25),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2, cv2.LINE_AA)
    cv2.imwrite(os.path.join(out_dir, f"residuals_{res['photo']}.jpg"), img, [cv2.IMWRITE_JPEG_QUALITY, 90])


# --------------------------------------------------------------------------- summary
CSV_COLS = ['photo', 'group', 'hour', 'kind', 'status', 'reason', 'coverage', 'dx_px', 'dy_px', 'offset_px', 'offset_R',
            'radial_px', 'tangential_px', 'radial_R', 'tangential_R', 'angular_position_err_deg', 'rotation_deg',
            'left_side_angle_err_deg', 'right_side_angle_err_deg', 'left_right_mismatch_deg', 'base_tilt_deg',
            'base_width_err_px', 'height_err_px', 'long_side_parallelism_deg', 'end_rotation_deg', 'length_err_px',
            'width_err_px', 'radius_err_px', 'radius_err_R', 'circularity', 'octants', 'parallax_height_R',
            'parallax_dx_px', 'parallax_dy_px', 'adj_dx_px', 'adj_dy_px', 'adj_offset_px', 'local_dx_px', 'local_dy_px',
            'local_offset_px', 'local_radial_px', 'local_tangential_px', 'outline_integrity']


def _clean(v):
    if isinstance(v, (np.floating, float)):
        return None if not np.isfinite(v) else round(float(v), 5)
    if isinstance(v, np.integer):
        return int(v)
    return v


def genuine_ranges(rows):
    metrics = ['offset_px', 'adj_offset_px', 'local_offset_px', 'local_radial_px', 'local_tangential_px', 'radial_px', 'tangential_px', 'angular_position_err_deg', 'rotation_deg',
               'radius_err_px', 'circularity', 'left_right_mismatch_deg', 'base_tilt_deg', 'long_side_parallelism_deg',
               'length_err_px', 'width_err_px', 'base_width_err_px', 'height_err_px']
    out = {}
    for kind in ('round', 'baton', 'triangle'):
        sel = [r for r in rows if r['kind'] == kind and r['status'] == 'OK']
        out[kind] = {'n': len(sel)}
        for k in metrics:
            v = np.array([r[k] for r in sel if r.get(k) is not None and np.isfinite(r[k])])
            if len(v):
                out[kind][k] = dict(mean=float(v.mean()), sd=float(v.std(ddof=1)) if len(v) > 1 else 0.0,
                                    min=float(v.min()), max=float(v.max()), max_abs=float(np.abs(v).max()))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--inputs', required=True, help='dir with genuine/ and defect_cases/')
    ap.add_argument('--bare', required=True)
    ap.add_argument('--out', required=True)
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    M, ref, SC = REG.build_master(args.bare)
    photos = []
    for grp in ('genuine', 'defect_cases'):
        d = os.path.join(args.inputs, grp)
        for f in sorted(os.listdir(d)):
            if f.lower().endswith(('.png', '.jpg', '.jpeg')):
                photos.append((grp, os.path.splitext(f)[0], os.path.join(d, f)))
    results, frames = [], {}
    for grp, name, path in photos:
        bgr = cv2.imread(path)
        res, H, smp = measure_photo(name, bgr, M, ref, SC)
        res['group'] = grp
        for r in res['markers']:
            r['group'] = grp
        results.append(res); frames[name] = (bgr, H)
        st = res['pose']
        print(f"{name} [{grp}] R={res['R_px']:.1f}px pose {'ACCEPTED' if st['accepted'] else 'REJECTED ' + str(st['reject_reasons'])}"
              f"  tick median {st['tick_median_px']:.3f} rms(ex gross) {st['tick_rms_ex_gross_px']:.3f}px, "
              f"ticks {st['ticks_used']} in {st['sectors_used']} sectors, gross {st['gross_outlier_ticks']}")
    # raised-marker parallax: one genuine-derived constant, leave-one-photo-out for the genuine photos
    gen = [r for r in results if r['group'] == 'genuine' and r['status'] == 'OK']
    h_pool = fit_parallax_h(gen)
    for res in results:
        if res['status'] != 'OK':
            continue
        h = fit_parallax_h([g for g in gen if g['photo'] != res['photo']]) if res['group'] == 'genuine' else h_pool
        res['parallax_height_R'] = h
        apply_parallax(res, h)
        ring_decomposition(res, M, frames[res['photo']][1])
    all_rows = [r for res in results for r in res['markers']]
    for res in results:
        print(f"\n{res['photo']} ({res['group']})  status {res['status']}  parallax h={res.get('parallax_height_R', float('nan')):.4f}R")
        rg = res.get('ring', {})
        if rg.get('status') == 'OK':
            print(f"  RING (all clean markers vs minute track, n={rg['n']}): translation ({rg['translation_px'][0]:+.2f},"
                  f"{rg['translation_px'][1]:+.2f})px  scale {rg['scale_pct']:+.2f}% ({rg['scale_px_at_round_radius']:+.2f}px at 0.813R)"
                  f"  rotation {rg['rotation_deg']:+.2f}deg")
        for r in res['markers']:
            if r['status'] != 'OK':
                print(f"  h{r['hour']:2d} {r['kind']:8s} OCCLUDED / INSUFFICIENT CLEAN EDGE  ({r['reason']})")
                continue
            extra = ''
            if r['kind'] == 'round':
                extra = f"radius {r['radius_err_px']:+.2f}px circ {r['circularity']:.3f}"
            elif r['kind'] == 'baton':
                extra = f"rot {r['rotation_deg']:+.2f}deg  len {r['length_err_px']:+.2f} wid {r['width_err_px']:+.2f}px"
            else:
                extra = (f"centreline rot {r['rotation_deg']:+.2f}deg  L/R side {r['left_side_angle_err_deg']:+.2f}/"
                         f"{r['right_side_angle_err_deg']:+.2f}deg  base tilt {r['base_tilt_deg']:+.2f}deg")
            print(f"  h{r['hour']:2d} {r['kind']:8s} d=({r['dx_px']:+.2f},{r['dy_px']:+.2f}) |{r['offset_px']:.2f}|px "
                  f"({r['offset_R']*1000:.1f}e-3R) rad {r['radial_px']:+.2f} tan {r['tangential_px']:+.2f}px "
                  f"ang {r['angular_position_err_deg']:+.2f}deg | par-adj |{r['adj_offset_px']:.2f}| | local ({r.get('local_dx_px', np.nan):+.2f},"
                  f"{r.get('local_dy_px', np.nan):+.2f}) rad {r.get('local_radial_px', np.nan):+.2f} tan {r.get('local_tangential_px', np.nan):+.2f}px | {extra}")
        if res['status'] == 'OK':
            render_photo(frames[res['photo']][0], frames[res['photo']][1], M, res, args.out)
    ranges = genuine_ranges([r for r in all_rows if r['group'] == 'genuine'])
    with open(os.path.join(args.out, 'marker_residuals.csv'), 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=CSV_COLS, extrasaction='ignore'); w.writeheader()
        for r in all_rows:
            w.writerow({k: _clean(r.get(k)) for k in CSV_COLS})
    for res in results:
        with open(os.path.join(args.out, f"marker_residuals_{res['photo']}.csv"), 'w', newline='') as f:
            w = csv.DictWriter(f, fieldnames=CSV_COLS, extrasaction='ignore'); w.writeheader()
            for r in res['markers']:
                w.writerow({k: _clean(r.get(k)) for k in CSV_COLS})
        with open(os.path.join(args.out, f"marker_residuals_{res['photo']}.json"), 'w') as f:
            json.dump({**{k: v for k, v in res.items() if k != 'markers'},
                       'markers': [{k: _clean(v) for k, v in r.items() if k != '_m'} for r in res['markers']]},
                      f, indent=1, default=lambda o: _clean(o) if not isinstance(o, np.ndarray) else o.tolist())
    with open(os.path.join(args.out, 'genuine_ranges.json'), 'w') as f:
        json.dump(dict(parallax_height_R_pooled=h_pool, n_genuine_photos=len(gen), ranges=ranges,
                       ring={r['photo']: r.get('ring') for r in results}), f, indent=1, default=_clean)
    print('\nGENUINE RANGES (4 controls, clean markers only; descriptive, not tolerances)')
    for kind, d in ranges.items():
        print(f"  {kind} n={d['n']}: " + '; '.join(f"{k} mean {v['mean']:+.2f} sd {v['sd']:.2f} max|.| {v['max_abs']:.2f}"
                                                   for k, v in d.items() if k != 'n'))


if __name__ == '__main__':
    main()
