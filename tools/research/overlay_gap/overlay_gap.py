"""Overlay-gap prototype (research only; never used by the app).

Question asked per marker: once the genuine overlay is placed, how far is the watch's marker from it?

  python3 overlay_gap.py dump.jsonl image_root out.csv [render_dir]
  OVERLAY_GAP_PERTURB=1 adds, per measured marker, a known-shift test: the overlay is moved by a known amount (2 or
  4 px in a seeded random direction, plus 1 degree for polygons) and the measurement must report that change.

Inputs come from the desktop-harness driver OverlayDump (the app's own overlay: dial pose H and, per marker, the yellow
outer-metal reference placed by the OTHER markers). Per marker:

 1. Template = the yellow outline (the marker's known outer-metal shape). Sample points along it (corners skipped:
    real corners are rounded).
 2. Along each point's outward normal, find the marker's outer edge: the bright-metal -> dark-dial fall nearest the
    current pose. No fall (a hand or glare lies over the edge, or the edge is not lit) = that point is not seen.
 3. Fit the known shape rigidly (shift, plus rotation for triangles and batons) to the seen points only, robustly
    (Tukey, 1 px): an off-shape point (a hand's edge, a glare blob, printing) gets no weight. Iterate (ICP along
    normals). The fitted outline also gives the hidden part of the marker.
 4. Report only the directions the seen points fix (normal-matrix check); the rest is "not fixed".
 5. Gap = largest displacement of any outline point between the overlay and the fitted marker, in px and % of the dial
    radius.

No thresholds here decide anything: limits come later from verified genuine photos only.
"""
import csv
import json
import math
import os
import sys

import cv2
import numpy as np

STEP_PX = 0.5          # template sampling along the outline, px
CORNER_R = 0.012       # skip outline points this close to a polygon vertex (rounded corners), R
WINDOWS_R = (0.030, 0.015, 0.008, 0.008)  # ICP search half-window along the normal, R, per iteration
PROFILE_STEP_PX = 0.25
TUKEY_PX = 1.5         # robust scale: residuals beyond this weigh nothing
MIN_DROP = 0.35        # a fall must cover this share of the metal-dial contrast
FIX_SE_PX = 0.35       # a direction is fixed when its standard error is below this (px)
MIN_SEEN = 0.25        # share of the outline that must be seen to fit at all


def load_like_app(path):
    """Same scaling as the desktop harness / app (Alpha96Calib.loadAlpha96), grey."""
    full = cv2.imread(path, cv2.IMREAD_COLOR)
    if full is None:
        return None
    mx = max(full.shape[0], full.shape[1])
    s = 1
    while mx // (s * 2) >= 3200:
        s *= 2
    m = full
    if s > 1 and not path.lower().endswith('.png'):
        flag = {2: cv2.IMREAD_REDUCED_COLOR_2, 4: cv2.IMREAD_REDUCED_COLOR_4}.get(s, cv2.IMREAD_REDUCED_COLOR_8)
        m = cv2.imread(path, flag)
    elif s > 1:
        m = cv2.resize(full, (full.shape[1] // s, full.shape[0] // s), interpolation=cv2.INTER_AREA)
    cur = max(m.shape[0], m.shape[1])
    if cur > 3200:
        k = 3200.0 / cur
        m = cv2.resize(m, (round(m.shape[1] * k), round(m.shape[0] * k)), interpolation=cv2.INTER_LINEAR)
    return cv2.cvtColor(m, cv2.COLOR_BGR2GRAY).astype(np.float32)


def project(H, pts):
    H = np.asarray(H, float).reshape(3, 3)
    p = np.c_[pts, np.ones(len(pts))] @ H.T
    return p[:, :2] / p[:, 2:3]


def sample(img, xy):
    """Bilinear grey values at pixel coordinates xy (N,2); NaN outside."""
    n = len(xy)
    W = 1024
    rows = (n + W - 1) // W
    x = np.full(rows * W, -1e6, np.float32)
    y = np.full(rows * W, -1e6, np.float32)
    x[:n] = xy[:, 0]
    y[:n] = xy[:, 1]
    v = cv2.remap(img, x.reshape(rows, W), y.reshape(rows, W), cv2.INTER_LINEAR,
                  borderMode=cv2.BORDER_CONSTANT, borderValue=np.nan).ravel()[:n]
    return v


def outline_points(poly, rpx, shape):
    """Points along the closed polygon (canonical) every STEP_PX, outward normals, skipping corners for polygons."""
    poly = np.asarray(poly, float)
    c = poly.mean(axis=0)
    pts, nrm, side = [], [], []
    n = len(poly)
    step = STEP_PX / rpx
    for i in range(n):
        a, b = poly[i], poly[(i + 1) % n]
        L = np.hypot(*(b - a))
        if L <= 0:
            continue
        t = (b - a) / L
        nn = np.array([t[1], -t[0]])
        if np.dot(nn, (a + b) / 2 - c) < 0:
            nn = -nn
        k = max(1, int(L / step))
        for j in range(k):
            u = (j + 0.5) / k * L
            if shape != 'round' and (u < CORNER_R or L - u < CORNER_R):
                continue
            pts.append(a + t * u)
            nrm.append(nn)
            side.append(i if shape != 'round' else 0)
    outline_points.side = np.array(side)
    return np.array(pts), np.array(nrm), c


def levels(img, H, poly, c, rpx, outside=0.0):
    """Metal/lume level inside the template, dial level in a band just outside it."""
    poly = np.asarray(poly, float)
    inner = c + 0.6 * (poly - c)
    lo, hi = poly.min(axis=0) - 0.05 - outside, poly.max(axis=0) + 0.05 + outside
    g = np.mgrid[lo[0]:hi[0]:1.0 / rpx, lo[1]:hi[1]:1.0 / rpx].reshape(2, -1).T
    path_in = cv2.convexHull(inner.astype(np.float32))
    path_out1 = cv2.convexHull((c + (poly - c) * 1.0).astype(np.float32))
    d_out = np.array([cv2.pointPolygonTest(path_out1, (float(p[0]), float(p[1])), True) for p in g[::7]])
    pin = np.array([cv2.pointPolygonTest(path_in, (float(p[0]), float(p[1])), False) for p in g[::7]])
    gg = g[::7]
    vals = sample(img, project(H, gg))
    inside = vals[(pin > 0) & np.isfinite(vals)]
    band = vals[(d_out < -0.008 - outside) & (d_out > -0.025 - outside) & np.isfinite(vals)]
    if len(inside) < 10 or len(band) < 10:
        return None
    return float(np.percentile(inside, 60)), float(np.percentile(band, 30))


def find_candidates(img, H, pts, nrm, lo, hi, rpx, contrast):
    """Every supported bright->dark fall along each normal with offset in [lo, hi] (canonical, + outward)."""
    pad = 2.5 / rpx                                 # profile extends past the range so each drop can be judged
    steps = np.arange(lo - pad, hi + pad + 1e-12, PROFILE_STEP_PX / rpx)
    P = pts[:, None, :] + steps[None, :, None] * nrm[:, None, :]
    v = sample(img, project(H, P.reshape(-1, 2))).reshape(len(pts), len(steps))
    d = np.diff(v, axis=1)  # outward derivative per step
    k = int(round(2.0 / PROFILE_STEP_PX))           # drop judged across +-2 px
    out = []
    ds = steps[1] - steps[0]
    for i in range(len(pts)):
        row = v[i]
        cand = []
        if np.all(np.isfinite(row)):
            di = d[i]
            for j in range(k, len(row) - k - 1):
                if steps[j] < lo or steps[j] > hi:
                    continue
                if di[j] < 0 and di[j] <= di[j - 1] and di[j] <= di[j + 1]:
                    drop = row[j - k] - row[j + 1 + k]
                    if drop >= MIN_DROP * contrast:
                        a, b, cc = di[j - 1], di[j], di[j + 1]
                        den = a - 2 * b + cc
                        sub = 0.5 * (a - cc) / den if den != 0 else 0.0
                        cand.append(steps[j] + (0.5 + sub) * ds)
        out.append(np.array(cand))
    return out


def choose(cands, metal_at, win):
    """Per point, the fall nearest either expected edge: the lume edge (offset 0) or the outer metal edge (metal_at).
    Returns offsets relative to the expected edge, and labels (0 lume, 1 metal); NaN where nothing is within win."""
    off = np.full(len(cands), np.nan)
    lab = np.zeros(len(cands), int)
    for i, c in enumerate(cands):
        if len(c) == 0:
            continue
        d0 = np.abs(c)
        d1 = np.abs(c - metal_at)
        j0, j1 = int(np.argmin(d0)), int(np.argmin(d1))
        if min(d0[j0], d1[j1]) > win:
            continue
        if d0[j0] <= d1[j1]:
            off[i], lab[i] = c[j0], 0
        else:
            off[i], lab[i] = c[j1] - metal_at, 1
    return off, lab


def inset_polygon(poly, d):
    """Every side moved inward by d (canonical); same construction as LuminousAlignment.inset."""
    poly = np.asarray(poly, float)
    n = len(poly)
    c = poly.mean(axis=0)
    N, O = [], []
    for i in range(n):
        a, b = poly[i], poly[(i + 1) % n]
        t = (b - a) / np.hypot(*(b - a))
        nn = np.array([t[1], -t[0]])
        if np.dot(nn, (a + b) / 2 - c) < 0:
            nn = -nn
        N.append(nn)
        O.append(nn @ a - d)
    out = []
    for i in range(n):
        p = (i + n - 1) % n
        A = np.array([N[p], N[i]])
        out.append(np.linalg.solve(A, [O[p], O[i]]))
    return np.array(out)


def template(m, insets):
    """Luminous outline expected inside the yellow outer-metal reference: the yellow inset by the model's genuine
    luminous inset (luminous_alignment.json). The lume-to-dark fall is the marker's strongest, most reliable edge."""
    poly = np.asarray(m['yellow'], float)
    d = insets.get(m['shape'])
    if d is None or len(poly) < 3:
        return None
    if m['shape'] == 'round':
        c = poly.mean(axis=0)
        r = np.mean(np.hypot(*(poly - c).T))
        return c + (poly - c) * ((r - d) / r)
    return inset_polygon(poly, d)


def fall_score(img, H, P, N, contrast, rpx, side=None, offs_px=(-1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5), eps_px=0.75):
    """Mean outward bright->dark step strength over the points of one edge family at each pose (P: poses x points x 2,
    canonical; N normals), as a share of the lume-dial contrast, each point clipped to [0, 1]. The edge may sit up
    to 1.5 px further in or out than nominal, but UNIFORMLY for the whole family (photo edge definition): the best
    common offset is taken, never a separate one per point."""
    best = np.zeros(P.shape[0], np.float32)
    e = eps_px / rpx
    for o in offs_px:
        q = P + (o / rpx) * N
        a = sample(img, project(H, (q - e * N).reshape(-1, 2))).reshape(P.shape[:2])
        b = sample(img, project(H, (q + e * N).reshape(-1, 2))).reshape(P.shape[:2])
        v = np.clip(np.nan_to_num((a - b) / contrast, nan=0.0), 0, 1)
        if side is None:
            v = v.mean(1)
        else:   # every side weighs the same (a baton's short ends fix its position along the axis)
            v = np.mean([v[:, side == k].mean(1) for k in np.unique(side)], axis=0)
        best = np.maximum(best, v)
    return best


def global_pose(img, H, lume_pts, lume_nrm, lume_side, c0, rot, rpx, contrast, metal=None, merge_px=0.0,
                span_px=10.0, step_px=1.0, rot_deg=3.0, rot_step=1.0, centre=(0.0, 0.0, 0.0)):
    """Exhaustive search of shift (and rotation) maximising how well BOTH known edges (lume edge, outer metal edge)
    sit on real bright->dark steps. Returns best (tx, ty, th), its score and the best score of any pose at least 2 px
    (far-end) away from it (ambiguity)."""
    sub = max(1, len(lume_pts) // 160)
    allP, allN, SD = lume_pts[::sub], lume_nrm[::sub], lume_side[::sub]
    nl = len(allP)
    if metal is not None:   # the outer metal outline (same rigid pose), half weight: breaks the lume/metal alias
        mp, mn, ms = metal
        subm = max(1, len(mp) // 160)
        allP, allN, MS = np.r_[allP, mp[::subm]], np.r_[allN, mn[::subm]], ms[::subm]
    lev = allP - c0
    halfLen = float(np.max(np.hypot(*lev.T)))
    g = np.arange(-span_px, span_px + 1e-9, step_px) / rpx
    ths = np.radians(np.arange(-rot_deg, rot_deg + 1e-9, rot_step)) if rot else np.array([0.0])
    poses = np.array([(centre[0] + x, centre[1] + y, centre[2] + t) for t in ths for x in g for y in g])
    out = np.zeros(len(poses))
    B = 400
    for k in range(0, len(poses), B):
        ps = poses[k:k + B]
        c, s_ = np.cos(ps[:, 2]), np.sin(ps[:, 2])
        R = np.stack([np.stack([c, -s_], -1), np.stack([s_, c], -1)], 1)          # poses x 2 x 2
        P = c0 + np.einsum('pij,nj->pni', R, lev) + ps[:, None, :2]
        N = np.einsum('pij,nj->pni', R, allN)
        # the lume edge may sit anywhere from 1.5 px inside nominal to the metal edge (a separator blurred away or a
        # lit bevel merged all round), uniformly
        offs = tuple(np.arange(-1.5, merge_px + 1.5 + 1e-9, 0.5))
        out[k:k + B] = fall_score(img, H, P[:, :nl], N[:, :nl], contrast, rpx, SD, offs_px=offs)
        if metal is not None:
            out[k:k + B] += 0.5 * fall_score(img, H, P[:, nl:], N[:, nl:], contrast, rpx, MS)
    i = int(np.argmax(out))
    best = poses[i]
    far = np.hypot((poses[:, 0] - best[0]) * rpx, (poses[:, 1] - best[1]) * rpx) + np.abs(poses[:, 2] - best[2]) * halfLen * rpx
    alt = float(out[far >= 2.0].max()) if np.any(far >= 2.0) else 0.0
    return best, float(out[i]), alt


def fit_marker(img, H, m, rpx, insets):
    """Known shape, two concentric edges: the lume edge (template) and the outer metal edge a known inset outside it.
    Each outline point uses whichever of the two it shows (a lit bevel merged with the lume only shows the outer one).
    Unknowns: shift, rotation (polygons), uniform lume-edge offset G1 and metal-edge offset G2 (photo edge definition;
    never part of the gap)."""
    shape = m['shape']
    poly = template(m, insets)
    if poly is None:
        return {'status': 'no overlay'}
    inset = insets[shape]
    pts0, nrm0, c0 = outline_points(poly, rpx, shape)
    lv = levels(img, H, poly, c0, rpx, inset)
    if lv is None:
        return {'status': 'too close to the photo edge'}
    lume, dial = lv
    contrast = lume - dial
    if contrast < 25:
        return {'status': 'marker not distinguishable from the dial'}
    rot = shape != 'round'
    tx = ty = th = 0.0
    G1 = G2 = 0.0
    lever = pts0 - c0
    hour = m['hour']
    a = math.radians(hour * 30.0)
    er = np.array([math.sin(a), -math.cos(a)]) if hour % 12 else np.array([0.0, -1.0])
    et = np.array([math.cos(a), math.sin(a)]) if hour % 12 else np.array([1.0, 0.0])
    halfLen = float(np.max(np.hypot(*lever.T)))

    def design(pts, nrm, lab, use_g2):
        lev = pts - (c0 + [tx, ty])
        cols = [nrm[:, 0], nrm[:, 1]]
        if rot:
            cols.append(-nrm[:, 0] * lev[:, 1] + nrm[:, 1] * lev[:, 0])
        cols.append(np.ones(len(nrm)))
        if use_g2:
            cols.append(lab.astype(float))
        return np.c_[tuple(cols)]

    # coarse: global search over shift (and rotation) of both known edges; no dependence on a starting pose
    side0 = outline_points.side
    ypts, ynrm, _ = outline_points(np.asarray(m['yellow'], float), rpx, shape)
    metal_family = (ypts, ynrm, outline_points.side)
    best, score, alt = global_pose(img, H, pts0, nrm0, side0, c0, rot, rpx, contrast, metal_family, inset * rpx)
    best, score, alt2 = global_pose(img, H, pts0, nrm0, side0, c0, rot, rpx, contrast, metal_family, inset * rpx,
                                    span_px=1.0, step_px=0.25, rot_deg=0.75, rot_step=0.25, centre=best)
    tx, ty, th = float(best[0]), float(best[1]), float(best[2])
    ambiguity = alt / score if score > 0 else 1.0
    spacing = inset
    stages = [('pair', min(0.45 * spacing, 0.008), TUKEY_PX), ('pair', min(0.30 * spacing, 0.006), TUKEY_PX),
              ('pair', min(0.30 * spacing, 0.006), TUKEY_PX)]
    if os.environ.get('OVERLAY_GAP_NOREFINE'):
        stages = []
    for mode, win, tukey in stages:
        cth, sth = math.cos(th), math.sin(th)
        R = np.array([[cth, -sth], [sth, cth]])
        pts = c0 + lever @ R.T + [tx, ty]
        nrm = nrm0 @ R.T
        w = max(win, 1.5 / rpx)
        if mode == 'first':
            cands = find_candidates(img, H, pts + G1 * nrm, nrm, -depth, w, rpx, contrast)
            off = np.array([c[0] if len(c) else np.nan for c in cands])
            lab = np.zeros(len(cands), int)
        else:
            cands = find_candidates(img, H, pts + G1 * nrm, nrm, -w, inset + G2 + w, rpx, contrast)
            off, lab = choose(cands, inset + G2, w)
        seen = np.isfinite(off)
        if seen.mean() < MIN_SEEN:
            return {'status': 'too little of its outline is visible (%.0f%%)' % (100 * seen.mean()), 'seen': float(seen.mean())}
        idx = np.where(seen)[0]
        use_g2 = mode == 'pair' and 0.1 <= lab[idx].mean() <= 0.9
        A = design(pts, nrm, lab, use_g2)
        wi = np.ones(len(idx))
        for _ in range(8):
            x, *_ = np.linalg.lstsq(A[idx] * wi[:, None], off[idx] * wi, rcond=None)
            r = (off[idx] - A[idx] @ x) * rpx
            u = r / tukey
            wi = np.sqrt(np.where(np.abs(u) < 1, (1 - u ** 2) ** 2, 0.0))
        tx += x[0]
        ty += x[1]
        k = 2
        if rot:
            th += x[2]
            k = 3
        G1 += x[k]
        if use_g2:
            G2 += x[k + 1]
    # final evaluation at the converged pose
    cth, sth = math.cos(th), math.sin(th)
    R = np.array([[cth, -sth], [sth, cth]])
    pts = c0 + lever @ R.T + [tx, ty]
    nrm = nrm0 @ R.T
    w = max(min(0.30 * inset, 0.006), 1.5 / rpx)
    cands = find_candidates(img, H, pts + G1 * nrm, nrm, -w, inset + G2 + w, rpx, contrast)
    off, lab = choose(cands, inset + G2, w)
    seen = np.isfinite(off)
    r = np.where(seen, off * rpx, np.nan)
    inl = seen & (np.abs(np.nan_to_num(r, nan=99)) < TUKEY_PX)
    use_g2 = inl.sum() > 0 and 0.1 <= lab[inl].mean() <= 0.9
    # precision of each reported direction (px): radial, tangential, far-end shift from rotation
    lev = pts - (c0 + [tx, ty])
    cols = [nrm @ er, nrm @ et]
    if rot:
        cols.append((-nrm[:, 0] * lev[:, 1] + nrm[:, 1] * lev[:, 0]) / halfLen)
    cols.append(np.ones(len(nrm)))
    if use_g2:
        cols.append(lab.astype(float))
    A = np.c_[tuple(cols)][inl]
    sig = max(float(np.std(r[inl])) if inl.sum() > 5 else float('nan'), 0.3)
    try:
        se = np.sqrt(np.diag(np.linalg.inv(A.T @ A))) * sig
    except np.linalg.LinAlgError:
        se = np.full(A.shape[1], np.inf)
    nfix = 3 if rot else 2
    fixed = [bool(s < FIX_SE_PX) for s in se[:nfix]]
    dt = np.array([tx, ty])
    use_t = dt.copy()
    if not fixed[0]:
        use_t -= (dt @ er) * er
    if not fixed[1]:
        use_t -= (dt @ et) * et
    use_th = th if (rot and fixed[2]) else 0.0
    cu, su = math.cos(use_th), math.sin(use_th)
    yellow = np.asarray(m['yellow'], float)
    moved = c0 + (yellow - c0) @ np.array([[cu, -su], [su, cu]]).T + use_t
    gap_px = float(np.max(np.hypot(*(moved - yellow).T)) * rpx)
    return {
        'status': 'measured' if any(fixed) else 'the visible edges do not fix its position',
        'seen': float(seen.mean()), 'inliers': float(inl.mean()),
        'metal_share': float(lab[inl].mean()) if inl.sum() else float('nan'),
        'noise_px': float(np.std(r[inl])) if inl.sum() > 5 else float('nan'),
        'radial_px': float(dt @ er * rpx), 'tangential_px': float(dt @ et * rpx),
        'rotation_deg': math.degrees(th) if rot else float('nan'),
        'se_radial_px': float(se[0]), 'se_tangential_px': float(se[1]), 'se_far_end_px': float(se[2]) if rot else float('nan'),
        'fixed': ''.join('rtθ'[i] for i, f in enumerate(fixed) if f),
        'edge_offset_px': float(G1 * rpx), 'metal_offset_px': float(G2 * rpx),
        'gap_px': gap_px, 'gap_R': gap_px / rpx, 'contrast': contrast, 'match': score, 'ambiguity': ambiguity,
        '_lume': poly, '_pts': pts + G1 * nrm, '_nrm': nrm, '_off': off + lab * (inset + G2), '_inl': inl,
        '_fit': c0 + (yellow - c0) @ R.T + dt,
    }


def render(img_color, H, m, res, rpx, path):
    """Close-up: yellow = overlay, cyan = fitted marker (solid where seen, thin where inferred), red dots = off-shape
    edge points ignored, green dots = edge points used."""
    poly = np.asarray(m['yellow'], float)
    lume = res.get('_lume')
    c = poly.mean(axis=0)
    half = max(np.max(np.abs(poly - c)), 0.03) + 0.03
    S = 8  # display px per image px
    n = int(2 * half * rpx * S)
    g = np.linspace(-half, half, n)
    X, Y = np.meshgrid(c[0] + g, c[1] + g)
    P = project(H, np.c_[X.ravel(), Y.ravel()])
    mx = P[:, 0].reshape(n, n).astype(np.float32)
    my = P[:, 1].reshape(n, n).astype(np.float32)
    crop = cv2.remap(img_color, mx, my, cv2.INTER_CUBIC)

    def to_px(q):
        q = np.asarray(q, float)
        return np.c_[(q[:, 0] - (c[0] - half)) / (2 * half) * n, (q[:, 1] - (c[1] - half)) / (2 * half) * n]

    cv2.polylines(crop, [to_px(poly).astype(np.int32).reshape(-1, 1, 2)], True, (0, 220, 255), 2, cv2.LINE_AA)
    if lume is not None:
        cv2.polylines(crop, [to_px(lume).astype(np.int32).reshape(-1, 1, 2)], True, (200, 120, 255), 1, cv2.LINE_AA)
    if '_fit' in res:
        cv2.polylines(crop, [to_px(res['_fit']).astype(np.int32).reshape(-1, 1, 2)], True, (255, 255, 0), 1, cv2.LINE_AA)
        pts, nrm, off, inl = res['_pts'], res['_nrm'], res['_off'], res['_inl']
        for i in range(0, len(pts), 2):
            if np.isfinite(off[i]):
                q = to_px([pts[i] + off[i] * nrm[i]])[0]
                cv2.circle(crop, (int(q[0]), int(q[1])), 2, (0, 200, 0) if inl[i] else (0, 0, 255), -1, cv2.LINE_AA)
    txt = '%dh %s gap %.1f px (%.2f%% R) seen %.0f%% fixed %s' % (
        m['hour'], res.get('status', '')[:20], res.get('gap_px', float('nan')), 100 * res.get('gap_R', float('nan')),
        100 * res.get('seen', 0), res.get('fixed', ''))
    cv2.putText(crop, txt, (6, 18), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)
    cv2.imwrite(path, crop)


def zone_fit(img, H, m, rpx, insets):
    """Tolerance-zone method (owner's suggestion, 15 Oct 2026). The overlay stays FIXED where the app places it.
    From well inside the marker, each ray along an outline normal follows the marker's bright fill outward to where
    it ends (first drop below half-way between lume and dial). That boundary's offset from the lume outline, side by
    side, is how far the marker spills past (+) or falls short of (-) the overlay there.

    The marker's shift is the part of those offsets that differs between opposite sides (a uniform halo or edge
    softness moves every side the same way and is removed as one common offset). Rays that start dark (a hand over
    the marker) or never end within reach (a hand or glare continuing outward) are not used; points well off the
    common pattern (a lit bevel merged on part of the outline) are ignored by the robust fit. No search: the result
    is a fixed function of the photo and the overlay."""
    shape = m['shape']
    poly = template(m, insets)
    if poly is None:
        return {'status': 'no overlay'}
    inset = insets[shape]
    pts, nrm, c0 = outline_points(poly, rpx, shape)
    lv = levels(img, H, poly, c0, rpx, inset)
    if lv is None:
        return {'status': 'too close to the photo edge'}
    lume, dial = lv
    if lume - dial < 25:
        return {'status': 'marker not distinguishable from the dial'}
    T = dial + 0.5 * (lume - dial)
    rot = shape != 'round'
    if shape == 'round':
        inr = float(np.mean(np.hypot(*(pts - c0).T)))
    else:
        e = np.roll(poly, -1, axis=0)
        area = abs(np.sum((poly[:, 0] - c0[0]) * (e[:, 1] - c0[1]) - (poly[:, 1] - c0[1]) * (e[:, 0] - c0[0]))) / 2
        inr = 2 * area / np.sum(np.hypot(*(e - poly).T))
    depth = min(0.45 * inr, 0.025)
    reach = inset + 3.0 / rpx
    steps = np.arange(-depth, reach + 1e-12, PROFILE_STEP_PX / rpx)
    P = pts[:, None, :] + steps[None, :, None] * nrm[:, None, :]
    v = sample(img, project(H, P.reshape(-1, 2))).reshape(len(pts), len(steps))
    ext = np.full(len(pts), np.nan)
    for i in range(len(pts)):
        row = v[i]
        if not np.all(np.isfinite(row)):
            continue
        k0 = int(0.25 * len(steps) * depth / (depth + reach))   # the ray must start inside bright fill
        if np.median(row[:max(3, k0)]) < T:
            continue
        below = np.where(row < T)[0]
        if len(below) == 0:
            continue                                   # bright all the way out: a hand or glare, not used
        j = below[0]
        if j == 0:
            continue
        f = (row[j - 1] - T) / (row[j - 1] - row[j])   # sub-sample crossing
        ext[i] = steps[j - 1] + f * (steps[1] - steps[0])
    seen = np.isfinite(ext)
    if seen.mean() < MIN_SEEN:
        return {'status': 'too little of its outline is visible (%.0f%%)' % (100 * seen.mean()), 'seen': float(seen.mean())}
    hour = m['hour']
    a = math.radians(hour * 30.0)
    er = np.array([math.sin(a), -math.cos(a)]) if hour % 12 else np.array([0.0, -1.0])
    et = np.array([math.cos(a), math.sin(a)]) if hour % 12 else np.array([1.0, 0.0])
    lev = pts - c0
    halfLen = float(np.max(np.hypot(*lev.T)))
    cols = [nrm @ er, nrm @ et]
    if rot:
        cols.append((-nrm[:, 0] * lev[:, 1] + nrm[:, 1] * lev[:, 0]) / halfLen)
    cols.append(np.ones(len(pts)))                     # common offset (halo / edge softness), never a shift
    A = np.c_[tuple(cols)]
    idx = np.where(seen)[0]
    y = ext * rpx
    wi = np.ones(len(idx))
    for _ in range(10):
        x, *_ = np.linalg.lstsq(A[idx] * wi[:, None], y[idx] * wi, rcond=None)
        r = y[idx] - A[idx] @ x
        u = r / TUKEY_PX
        wi = np.sqrt(np.where(np.abs(u) < 1, (1 - u ** 2) ** 2, 0.0))
    r_all = np.where(seen, y - A @ x, np.nan)
    inl = seen & (np.abs(np.nan_to_num(r_all, nan=99)) < TUKEY_PX)
    sig = max(float(np.std(r_all[inl])) if inl.sum() > 5 else float('nan'), 0.3)
    try:
        se = np.sqrt(np.diag(np.linalg.inv(A[inl].T @ A[inl]))) * sig
    except np.linalg.LinAlgError:
        se = np.full(A.shape[1], np.inf)
    nfix = 3 if rot else 2
    fixed = [bool(q < FIX_SE_PX) for q in se[:nfix]]
    rad, tan = float(x[0]), float(x[1])
    far = float(x[2]) if rot else 0.0
    # the marker's outline moved by (rad, tan) and turned so its far end moves by `far`: largest movement of any point
    use_r = rad if fixed[0] else 0.0
    use_t = tan if fixed[1] else 0.0
    use_f = far if (rot and fixed[2]) else 0.0
    yellow = np.asarray(m['yellow'], float)
    th = use_f / (halfLen * rpx) if rot else 0.0
    cu, su = math.cos(th), math.sin(th)
    moved = c0 + (yellow - c0) @ np.array([[cu, -su], [su, cu]]).T + (use_r * er + use_t * et) / rpx
    gap_px = float(np.max(np.hypot(*(moved - yellow).T)) * rpx)
    return {
        'status': 'measured' if any(fixed) else 'the visible edges do not fix its position',
        'seen': float(seen.mean()), 'inliers': float(inl.mean()),
        'noise_px': float(np.std(r_all[inl])) if inl.sum() > 5 else float('nan'),
        'radial_px': rad, 'tangential_px': tan, 'rotation_deg': math.degrees(th) if rot else float('nan'),
        'se_radial_px': float(se[0]), 'se_tangential_px': float(se[1]), 'se_far_end_px': float(se[2]) if rot else float('nan'),
        'fixed': ''.join('rtθ'[i] for i, f in enumerate(fixed) if f),
        'edge_offset_px': float(x[-1]), 'gap_px': gap_px, 'gap_R': gap_px / rpx,
        # containment (pass/fail gauge): how far the marker's bright shape reaches past the OUTER outline (yellow),
        # as the 90th percentile over the rays that saw an edge (a whole side spilling, not one stray ray)
        'spill_px': float(np.percentile((ext[seen] - inset) * rpx, 90)),
        '_lume': poly, '_pts': pts, '_nrm': nrm, '_off': ext, '_inl': inl,
        '_fit': c0 + (yellow - c0) @ np.array([[math.cos(math.radians(math.degrees(th) if rot else 0)), -math.sin(th)], [math.sin(th), math.cos(th)]]).T + (rad * er + tan * et) / rpx,
    }


def measure(img, H, m, rpx, insets):
    if os.environ.get('OVERLAY_GAP_METHOD', 'zone') == 'zone':
        return zone_fit(img, H, m, rpx, insets)
    return fit_marker(img, H, m, rpx, insets)


def perturb_test(img, d, m, rpx, insets, base):
    """Move the overlay by a known shift (and rotation for polygons) and compare the measured change with it."""
    import hashlib
    seed = int(hashlib.sha256(('%s|%d' % (d['photo_id'], m['hour'])).encode()).hexdigest()[:8], 16)
    rng = np.random.default_rng(seed)
    mag = (2.0, 4.0)[seed % 2] / rpx
    ang = rng.uniform(0, 2 * math.pi)
    v = mag * np.array([math.cos(ang), math.sin(ang)])
    rot = m['shape'] != 'round'
    dth = math.radians(1.0) * (1 if seed % 3 else -1) if rot else 0.0
    y = np.asarray(m['yellow'], float)
    c = y.mean(axis=0)
    R = np.array([[math.cos(dth), -math.sin(dth)], [math.sin(dth), math.cos(dth)]])
    m2 = dict(m)
    m2['yellow'] = (c + (y - c) @ R.T + v).tolist()
    res = measure(img, d['H'], m2, rpx, insets)
    if res.get('status') != 'measured':
        return {'inj_px': '%.2f' % (mag * rpx), 'rec_err_px': 'lost'}
    a = math.radians(m['hour'] * 30.0)
    er = np.array([math.sin(a), -math.cos(a)]) if m['hour'] % 12 else np.array([0.0, -1.0])
    et = np.array([math.cos(a), math.sin(a)]) if m['hour'] % 12 else np.array([1.0, 0.0])
    # the marker did not move: the fit relative to the moved overlay must change by -v (rotation about the template
    # centroid moves the centroid by a negligible amount for these shapes; compared in the overlay's own frame)
    want = -v * rpx
    got = np.array([res['radial_px'] - base['radial_px'], res['tangential_px'] - base['tangential_px']])
    want_rt = np.array([want @ er, want @ et])
    out = {'inj_px': '%.2f' % (mag * rpx), 'rec_err_px': '%.3f' % float(np.hypot(*(got - want_rt)))}
    if 'spill_px' in res:
        out['spill_moved_px'] = '%.2f' % res['spill_px']
    if rot:
        out['inj_deg'] = '%.2f' % math.degrees(dth)
        out['rec_err_deg'] = '%.3f' % abs((res['rotation_deg'] - base['rotation_deg']) + math.degrees(dth))
    return out


_INSETS = {}


def model_insets(model):
    if model not in _INSETS:
        here = os.path.dirname(os.path.abspath(__file__))
        f = os.path.join(here, '..', '..', '..', 'android', 'app', 'src', 'main', 'assets', 'models', model, 'alignment',
                         'luminous_alignment.json')
        j = json.load(open(f))
        _INSETS[model] = {k: v['inset_R'] for k, v in j['shapes'].items() if v.get('supported', True)}
    return _INSETS[model]


def main():
    dump, root, out = sys.argv[1], sys.argv[2], sys.argv[3]
    rdir = sys.argv[4] if len(sys.argv) > 4 else None
    if rdir:
        os.makedirs(rdir, exist_ok=True)
    cols = ['photo_id', 'watch', 'model', 'hour', 'shape', 'rpx', 'app_outcome', 'app_reason', 'app_primary', 'app_excess_px',
            'hand_clean', 'status', 'seen', 'inliers', 'noise_px', 'radial_px', 'tangential_px', 'rotation_deg',
            'se_radial_px', 'se_tangential_px', 'se_far_end_px', 'fixed', 'edge_offset_px', 'metal_offset_px', 'metal_share', 'gap_px', 'gap_R',
            'match', 'ambiguity', 'spill_px', 'spill_moved_px', 'inj_px', 'inj_deg', 'rec_err_px', 'rec_err_deg']
    with open(out, 'w', newline='') as fo:
        w = csv.DictWriter(fo, fieldnames=cols, extrasaction='ignore')
        w.writeheader()
        for line in open(dump):
            d = json.loads(line)
            if not d.get('pose'):
                continue
            path = os.path.join(root, d['path'])
            img = load_like_app(path)
            if img is None or img.shape[1] != d['w'] or img.shape[0] != d['h']:
                print('SIZE MISMATCH', d['photo_id'], None if img is None else img.shape, d['w'], d['h'])
                continue
            color = None
            if rdir:
                color = cv2.cvtColor(img.astype(np.uint8), cv2.COLOR_GRAY2BGR)
            rpx = d['rpx']
            insets = model_insets(d['model'])
            for m in d['markers']:
                try:
                    res = measure(img, d['H'], m, rpx, insets)
                except Exception as e:  # research: record, never hide
                    res = {'status': 'error %s' % e}
                row = {'photo_id': d['photo_id'], 'watch': d['watch'], 'model': d['model'], 'hour': m['hour'],
                       'shape': m['shape'], 'rpx': '%.1f' % rpx, 'app_outcome': m['outcome'], 'app_reason': m['reason'],
                       'app_primary': m['primary'],
                       'app_excess_px': '' if m['excess_R'] is None else '%.2f' % (m['excess_R'] * rpx),
                       'hand_clean': int(m['hand_clean'])}
                for k, v in res.items():
                    if not k.startswith('_'):
                        row[k] = ('%.4g' % v) if isinstance(v, float) else v
                if os.environ.get('OVERLAY_GAP_PERTURB') and res.get('status') == 'measured':
                    row.update(perturb_test(img, d, m, rpx, insets, res))
                w.writerow(row)
                if rdir:
                    render(color, d['H'], m, res, rpx, os.path.join(rdir, '%s_%02d.png' % (d['photo_id'], m['hour'])))
            print('done', d['photo_id'])


if __name__ == '__main__':
    main()
