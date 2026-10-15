"""Layered analysis-by-synthesis marker localisation (research only; never used by the app).

The marker is rendered from its KNOWN outline at a candidate pose as layers of signed distance sd to the lume outline
(px, + outward), each blurred by this photo's edge spread (normal CDF of width sigma):
  dial level (constant)
  + lume       amplitude x step at sd = g          (lume edge; g = this photo's edge definition)
  + metal ring amplitude per angular sector x band from sd = r0 to sd = r1 (r1 = outer metal edge)
The amplitudes are linear and are solved in closed form at every pose ("projected out"), so a ring that is dark on
one side and lit on another is modelled, not treated as noise. sigma, g, r0, r1 are learned once per photo and shape
from its clean markers. Only the pose (shift, and rotation for batons / the triangle) is searched. Pixels are used at
their own centres (no resampling of the photo). Robust (Geman-McClure) weights give a hand, a glint or printing that
the model cannot explain almost no weight. Precision of each pose component comes from the weighted Jacobian; a
marker is withheld when too little of it fits, when another clearly different pose fits almost as well, or when a
component is not fixed to 0.35 px.
"""
import math

import numpy as np
from scipy.optimize import minimize
from scipy.special import ndtr

import overlay_gap as og
from benchmark import SD

K_SECTORS = 12
KAPPA = 0.15        # robust scale, share of the lume-dial contrast
FIX_SE_PX = 0.35


def setup(img, H, m, rpx, insets):
    shape = m['shape']
    poly = og.template(m, insets)
    if poly is None:
        return None
    inset = insets[shape]
    c = poly.mean(axis=0)
    lv = og.levels(img, H, poly, c, rpx, inset)
    if lv is None or lv[0] - lv[1] < 25:
        return None
    if shape == 'round':
        inr = float(np.mean(np.hypot(*(poly - c).T)))
    else:
        e = np.roll(poly, -1, axis=0)
        area = abs(np.sum((poly[:, 0] - c[0]) * (e[:, 1] - c[1]) - (poly[:, 1] - c[1]) * (e[:, 0] - c[0]))) / 2
        inr = 2 * area / np.sum(np.hypot(*(e - poly).T))
    d_in = min(0.5 * inr, 8.0 / rpx)
    d_out = inset + 8.0 / rpx
    sd = SD(poly, shape, rpx, d_out + 12.0 / rpx)
    # evaluation pixels: the photo's own pixel centres whose canonical position lies in the edge band at the overlay
    Hm = np.asarray(H, float).reshape(3, 3)
    half = float(np.max(np.hypot(*(poly - c).T))) + d_out + 2.0 / rpx
    box = og.project(H, np.array([[c[0] + half * math.cos(a), c[1] + half * math.sin(a)] for a in np.linspace(0, 2 * math.pi, 24)]))
    x0, y0 = np.floor(box.min(axis=0)).astype(int)
    x1, y1 = np.ceil(box.max(axis=0)).astype(int)
    if x0 < 0 or y0 < 0 or x1 >= img.shape[1] or y1 >= img.shape[0]:
        return None
    XX, YY = np.meshgrid(np.arange(x0, x1 + 1), np.arange(y0, y1 + 1))
    pix = np.c_[XX.ravel(), YY.ravel()].astype(float)
    q = np.c_[pix, np.ones(len(pix))] @ np.linalg.inv(Hm).T
    P = q[:, :2] / q[:, 2:3]
    d, vd = sd(P, vertex=True)
    keep = (d > -d_in) & (d < d_out) & (vd > og.CORNER_R)
    P = P[keep]
    v = img[YY.ravel()[keep], XX.ravel()[keep]].astype(float)
    v = (v - lv[1]) / (lv[0] - lv[1])
    return dict(shape=shape, poly=poly, c=c, inset_px=inset * rpx, rpx=rpx, sd=sd, P=P, v=v,
                rot=shape != 'round', halfLen=float(np.max(np.hypot(*(poly - c).T))) * rpx)


def design(S, prm, q):
    """Basis columns at pose q = (tx_px, ty_px, deg)."""
    sigma, g, r0, r1 = prm
    c, rpx = S['c'], S['rpx']
    th = math.radians(q[2])
    R = np.array([[math.cos(-th), -math.sin(-th)], [math.sin(-th), math.cos(-th)]])
    Q = c + (S['P'] - c - np.array(q[:2]) / rpx) @ R.T
    d = S['sd'](Q) * rpx
    lume = ndtr(-(d - g) / sigma)
    band = ndtr((d - r0) / sigma) - ndtr((d - r1) / sigma)
    ang = np.arctan2(Q[:, 1] - c[1], Q[:, 0] - c[0])
    cols = [np.ones(len(d)), lume]
    for k in range(K_SECTORS):
        a = 2 * math.pi * k / K_SECTORS
        # smooth sector weights: cos^2 bumps two sectors wide, summing to 1 all round
        dd = np.abs(((ang - a + math.pi) % (2 * math.pi)) - math.pi)
        w = np.where(dd < 2 * math.pi / K_SECTORS, np.cos(dd * K_SECTORS / 4.0) ** 2, 0.0)
        cols.append(band * w)
    return np.c_[tuple(cols)]


def solve(S, prm, q, iters=3, w0=None):
    A = design(S, prm, q)
    v = S['v']
    w = np.ones(len(v)) if w0 is None else w0
    for _ in range(iters):
        sw = np.sqrt(w)
        x, *_ = np.linalg.lstsq(A * sw[:, None], v * sw, rcond=None)
        r = v - A @ x
        w = 1.0 / (1.0 + (r / KAPPA) ** 2) ** 2          # Geman-McClure IRLS weights
    r = v - A @ x
    loss = float(np.sum(r * r / (r * r + KAPPA * KAPPA)))
    return loss, x, r, w


def learn(clean, rpx_inset):
    """Per photo and shape: sigma, g, r0, r1 minimising the robust loss over clean markers at their poses."""
    best = None
    for sigma in (0.7, 1.1, 1.6):
        for g in (-1.0, -0.5, 0.0, 0.5, 1.0):
            for r1 in (rpx_inset - 1.0, rpx_inset, rpx_inset + 1.0):
                for ring in (0.5, 0.75):                # ring width as a share of the lume edge -> outer metal distance
                    r0 = r1 - ring * (r1 - g)
                    prm = (sigma, g, r0, r1)
                    L = sum(solve(S, prm, q, iters=1)[0] / len(S['v']) for S, q in clean)
                    if best is None or L < best[0]:
                        best = (L, prm)
    return best[1]


def fit(S, prm, init=(0.0, 0.0, 0.0), span=6.0):
    rot = S['rot']
    shifts = np.arange(-span, span + 1e-9, 1.0)
    angs = np.arange(-2.0, 2.01, 1.0) if rot else np.array([0.0])
    grid = [(init[0] + x, init[1] + y, (init[2] + a) if rot else 0.0) for a in angs for x in shifts for y in shifts]
    L = np.array([solve(S, prm, g, iters=1)[0] for g in grid])
    q0 = grid[int(np.argmin(L))]
    f = (lambda z: solve(S, prm, (z[0], z[1], z[2]))[0]) if rot else (lambda z: solve(S, prm, (z[0], z[1], 0.0))[0])
    res = minimize(f, np.array(q0 if rot else q0[:2], float), method='Nelder-Mead',
                   options={'xatol': 0.005, 'fatol': 1e-5, 'maxiter': 600})
    q = tuple(res.x) if rot else (res.x[0], res.x[1], 0.0)
    loss, x, r, w = solve(S, prm, q)
    # separation from the best clearly different pose (>= 2 px far-end away)
    far = np.array([math.hypot(g[0] - q[0], g[1] - q[1]) + abs(math.radians(g[2] - q[2])) * S['halfLen'] for g in grid])
    alt = float(L[far >= 2.0].min()) if np.any(far >= 2.0) else float('inf')
    sep = (alt - loss) / max(alt, 1e-9)
    # precision: weighted Jacobian of the model w.r.t. pose (finite differences), residual scale from the inliers
    eps = (0.05, 0.05, 0.05)
    J = []
    for i in range(3 if rot else 2):
        dq = list(q)
        dq[i] += eps[i]
        A2 = design(S, prm, dq)
        J.append((A2 @ x - design(S, prm, q) @ x) / eps[i])
    J = np.array(J).T
    inl = np.abs(r) < 2 * KAPPA
    s2 = float(np.sum(w * r * r) / max(np.sum(w) - J.shape[1], 1))
    try:
        cov = np.linalg.inv((J * w[:, None]).T @ J) * s2
        se = np.sqrt(np.diag(cov))
    except np.linalg.LinAlgError:
        se = np.full(J.shape[1], np.inf)
    se_far = float(se[2] * math.radians(1) * S['halfLen']) if rot else 0.0
    return dict(q=q, loss=loss, sep=sep, inliers=float(np.mean(inl)), se=se, se_far=se_far, amp=x)


def measure_photo(img, H, d, insets, centre_results):
    """All markers of one photo. centre_results: hour -> centroid_fit result (used only to choose clean markers and
    their approximate poses for learning this photo's edge layers)."""
    rpx = d['rpx']
    Ss = {m['hour']: setup(img, H, m, rpx, insets) for m in d['markers']}
    prms = {}
    for shape in {m['shape'] for m in d['markers']}:
        clean = []
        for m in d['markers']:
            c = centre_results.get(m['hour'], {})
            S = Ss[m['hour']]
            if m['shape'] == shape and S is not None and m['hand_clean'] and c.get('status') == 'measured':
                t = c['_T']
                # centre method pose expressed about this setup's centre
                cc, tt, th = t
                Rr = np.array([[math.cos(th), -math.sin(th)], [math.sin(th), math.cos(th)]])
                tc = cc + (S['c'] - cc) @ Rr.T + tt - S['c']
                clean.append((S, (tc[0] * rpx, tc[1] * rpx, math.degrees(th))))
        if clean:
            prms[shape] = learn(clean[:5], insets[shape] * rpx)
    if prms and any(s not in prms for s in {m['shape'] for m in d['markers']}):
        donor = next(iter(prms.values()))
        for s in {m['shape'] for m in d['markers']}:
            if s not in prms:
                g = donor[1]
                r1 = insets[s] * rpx
                prms[s] = (donor[0], g, r1 - 0.6 * (r1 - g), r1)
    return Ss, prms


def result(m, S, prm, rpx):
    if S is None:
        return {'status': 'marker not distinguishable from the dial or too close to the photo edge'}
    if prm is None:
        return {'status': 'no clean marker in this photo to learn its edge appearance from'}
    f = fit(S, prm)
    q = f['q']
    rot = S['rot']
    hour = m['hour']
    a = math.radians(hour * 30.0)
    er = np.array([math.sin(a), -math.cos(a)]) if hour % 12 else np.array([0.0, -1.0])
    et = np.array([math.cos(a), math.sin(a)]) if hour % 12 else np.array([1.0, 0.0])
    fixed = f['se'][0] < FIX_SE_PX and f['se'][1] < FIX_SE_PX and (not rot or f['se_far'] < FIX_SE_PX)
    ok = f['inliers'] >= 0.6 and f['sep'] >= 0.05 and fixed
    why = ('measured' if ok else
           'too little of the marker matches its known appearance (%.0f%%)' % (100 * f['inliers']) if f['inliers'] < 0.6 else
           'another position fits almost as well (separation %.2f)' % f['sep'] if f['sep'] < 0.05 else
           'its position is not fixed precisely enough')
    t = np.array(q[:2]) / rpx
    return {'status': why, 'radial_px': float(np.array(q[:2]) @ er), 'tangential_px': float(np.array(q[:2]) @ et),
            'rotation_deg': float(q[2]) if rot else float('nan'), 'inliers': f['inliers'], 'separation': f['sep'],
            'se_px': float(max(f['se'][0], f['se'][1], f['se_far'])),
            '_T': (S['c'].copy(), t, math.radians(q[2]) if rot else 0.0)}
