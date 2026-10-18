"""Research only (experimental, alongside the current lume-centre detector): a marker's position and rotation from its
visible OUTER-METAL edge.

The genuine outer-metal outline (the overlay's yellow polygon) is fixed in shape and size: only X/Y (circles) or
X/Y and rotation (batons, triangle) are fitted, never a stretch. Along the outline's normals the outermost bright-to-
dark crossing (metal to dial) is found; points under a hand, without contrast, or with a bright outside (reflection,
a neighbouring hand edge) are masked, and the rest are fitted robustly (Tukey). A reading is kept only when what is
visible constrains it: enough clean points and standard errors under MAX_SE_POS / MAX_SE_ROT; otherwise a reason.
"""
import math
import os

import numpy as np

import overlay_gap as og

SPAN = 0.02            # R searched either side of the expected edge
MIN_CONTRAST = 30.0
MIN_POINTS = 24
MAX_SE_POS = 0.0025    # R
MAX_SE_ROT = 0.35      # deg
ITER = 4
OLD_EDGE = os.environ.get('METAL_EDGE') == 'half'   # the first rule (half of lume contrast), for comparison only
RIM_FRAC = 0.25        # of lume-to-dial contrast: where the rim starts, scanning in from the dial
RIM_PX = 1.5           # px inside that start over which the rim's own level is taken
WHY = ('used', 'under a hand', 'low contrast', 'no dark dial outside', 'bright again outside', 'outside the photo')


def _rot(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s], [s, c]])


def edges(img, H, poly, rpx, shape, hands):
    """Edge offsets (R, + = outward) of the outline poly along its normals; NaN where masked. Returns pts, nrm, off;
    edges.why holds each point's reason (WHY) and edges.side its outline side."""
    pts, nrm, c = og.outline_points(poly, rpx, shape)
    s = np.arange(-SPAN, SPAN + 1e-12, 0.25 / rpx)
    P = pts[:, None, :] + s[None, :, None] * nrm[:, None, :]
    v = og.sample(img, og.project(H, P.reshape(-1, 2))).reshape(len(pts), len(s))
    off = np.full(len(pts), np.nan)
    ang = np.degrees(np.arctan2(pts[:, 0], -pts[:, 1]))
    rad = np.hypot(pts[:, 0], pts[:, 1])
    side = og.outline_points.side
    why = np.zeros(len(pts), int)
    masked = np.zeros(len(pts), bool)
    for a, w, reach in hands or []:
        da = np.abs((ang - a + 180) % 360 - 180)
        masked |= (da <= w / 2 + 1.5) & (rad <= reach + 0.02)
    inner = s <= 0.004
    for i in range(len(pts)):
        p = v[i]
        if masked[i] or np.isnan(p).any():
            why[i] = 1 if masked[i] else 5
            continue
        top = p[inner].max()
        dial = np.percentile(p, 10)
        if top - dial < MIN_CONTRAST:
            why[i] = 2
            continue
        # the metal's own level just inside its outer edge (a shadowed rim is darker than the lume): the edge is
        # half-way between the dial and THAT level, so a dark rim is not skipped for the lume boundary inside it
        j = len(p) - 1
        while j >= 0 and p[j] < dial + RIM_FRAC * (top - dial):
            j -= 1
        if OLD_EDGE:
            thr = dial + 0.5 * (top - dial)
            j = len(p) - 1
            while j >= 0 and p[j] < thr:
                j -= 1
        elif j >= 0:
            rim = p[max(0, j - int(round(RIM_PX * 4))):j + 1].max()
            thr = dial + 0.5 * (rim - dial)
            j = len(p) - 1
            while j >= 0 and p[j] < thr:
                j -= 1
        if j < 0 or j >= len(p) - 2:
            why[i] = 3
            continue                                   # no dark dial outside the edge: reflection or a hand
        e = j + (p[j] - thr) / (p[j] - p[j + 1])
        if p[j + 2:].mean() > thr:                     # bright again outside: not a clean boundary
            why[i] = 4
            continue
        off[i] = np.interp(e, np.arange(len(s)), s)
    edges.why, edges.side = why, side
    return pts, nrm, off


def fit(img, H, m, rpx, hands, rotate, bias=0.0):
    """{'c_obs', 'c_gen', 'tilt_deg', 'se_pos', 'se_rot', 'n', 'cover', 'bias', 'note'} for one marker. bias: this photo's
    edge offset of the shape (R, from its fully visible markers), removed so a partly hidden marker is not pulled towards
    its visible side by edge blur; the outline itself is never resized."""
    poly0 = np.asarray(m['yellow'], float)
    c0 = poly0.mean(axis=0)
    t = np.zeros(2)
    th = 0.0
    out = {'c_gen': c0, 'shape': m['shape']}
    for _ in range(ITER):
        poly = c0 + (poly0 - c0) @ _rot(th).T + t
        pts, nrm, off = edges(img, H, poly, rpx, m['shape'], hands)
        ok = np.isfinite(off)
        if ok.sum() < MIN_POINTS:
            out.update(note='too little of its edge is visible (%d clean points)' % ok.sum(), n=int(ok.sum()), cover=ok.mean())
            return out
        cc = poly.mean(axis=0)
        J = np.c_[nrm, (nrm[:, 0] * -(pts[:, 1] - cc[1]) + nrm[:, 1] * (pts[:, 0] - cc[0]))] if rotate else nrm
        J, r = J[ok], off[ok] - bias
        w = np.ones(len(r))
        for _ in range(5):
            x = np.linalg.lstsq(J * w[:, None] ** 0.5, r * w ** 0.5, rcond=None)[0]
            res = r - J @ x
            sc = max(1.4826 * np.median(np.abs(res)), 0.3 / rpx)
            u = res / (4.685 * sc)
            w = np.where(np.abs(u) < 1, (1 - u * u) ** 2, 0.0)
        t = t + x[:2]
        if rotate:
            th += x[2]
    keep = w > 0
    sig = math.sqrt(np.sum(w * res ** 2) / max(1, keep.sum() - J.shape[1]))
    cov = sig ** 2 * np.linalg.pinv((J * w[:, None]).T @ J)
    se_pos = math.sqrt(max(cov[0, 0], cov[1, 1]))
    se_rot = math.degrees(math.sqrt(cov[2, 2])) if rotate else float('nan')
    out.update(c_obs=c0 + t, tilt_deg=math.degrees(th) if rotate else float('nan'), se_pos=se_pos, se_rot=se_rot,
               n=int(keep.sum()), cover=float(keep.sum()) / len(off), rms_px=sig * rpx,
               bias=float(np.sum(w * res) / max(1e-9, np.sum(w))) + bias, note='',
               _poly=poly, _pts=pts, _off=off, _why=edges.why.copy(), _side=edges.side.copy(), _w=np.where(ok, 0.0, np.nan),
               _resid=np.full(len(off), np.nan))
    out['_w'][np.flatnonzero(ok)] = w
    out['_resid'][np.flatnonzero(ok)] = res
    if se_pos > MAX_SE_POS:
        out['note'] = 'its visible edge does not fix its position'
    elif rotate and se_rot > MAX_SE_ROT:
        out['note'] = 'its visible edge does not fix its rotation'
    return out
