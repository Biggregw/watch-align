"""Research only (experimental, alongside the current lume-centre detector): a marker's position and rotation from its
visible OUTER-METAL edge.

The genuine outer-metal outline (the overlay's yellow polygon) is fixed in shape and size: only X/Y (circles) or
X/Y and rotation (batons, triangle) are fitted, never a stretch. Along the outline's normals the outermost bright-to-
dark crossing (metal to dial) is found; points under a hand, without contrast, or with a bright outside (reflection,
a neighbouring hand edge) are masked, and the rest are fitted robustly (Tukey). A reading is kept only when what is
visible constrains it at all (enough clean points, standard errors under CANT_SE_POS / CANT_SE_ROT); otherwise a reason
(Can't check). A kept reading whose edge is rough, inconsistent, mostly reconstructed or loosely fixed is marked
uncertain: over a limit it becomes 'Possible misalignment - inspect' (verdict), never a silent pass.
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
# edge reliability (how far the visible metal can be trusted; never a defect limit)
EDGE_RMS_OK = 0.5      # px: fit roughness of a clean rim is about 0.1-0.2 px
EDGE_BAD_PX = 1.0      # px: an edge point this far from the fitted outline is inconsistent (shadowed bevel, reflection)
EDGE_BAD_FRAC = 0.15   # share of inconsistent points above which the edge is unreliable
MIN_COVER = 0.5        # below this share of the outline seen, the rest is mostly reconstructed
CANT_SE_POS = 0.01     # R: a position this loosely fixed decides nothing against the 1 % R limit -> Can't check
CANT_SE_ROT = 1.0      # deg: likewise for rotation (about the triangle limit)
# existing limits (ClearlyOff.Limits defaults): position 1.0 % R (+0.5 px), baton tilt 2.25 deg, triangle rotation 1.2 deg
POS_LIMIT_PCT, ALLOW_PX, TILT_LIMIT = 1.0, 0.5, {'baton': 2.25, 'triangle': 1.2}
CLEARLY_OFF, INSPECT, NO_ISSUE, CANT = 'Clearly off', 'Possible misalignment - inspect', 'No visible issue', "Can't check"
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
    seen = np.isfinite(out['_resid'])
    bad = seen & (np.abs(out['_resid']) * rpx > EDGE_BAD_PX)
    out['bad_frac'] = float(bad.sum()) / max(1, seen.sum())
    out['_bad'] = bad
    unc = []
    if out['rms_px'] > EDGE_RMS_OK:
        unc.append('its visible edge is rough (fit %.2f px)' % out['rms_px'])
    if out['bad_frac'] > EDGE_BAD_FRAC:
        unc.append('%.0f %% of its visible edge is inconsistent (shadowed or reflecting metal)' % (100 * out['bad_frac']))
    if out['cover'] < MIN_COVER:
        unc.append('only %.0f %% of its outline is visible, the rest is reconstructed' % (100 * out['cover']))
    if se_pos > MAX_SE_POS:
        unc.append('its visible edge only loosely fixes its position')
    if rotate and se_rot > MAX_SE_ROT:
        unc.append('its visible edge only loosely fixes its rotation')
    out['uncertain'] = '; '.join(unc)
    if se_pos > CANT_SE_POS:
        out['note'] = 'its visible edge does not fix its position'
    elif rotate and se_rot > CANT_SE_ROT:
        out['note'] = 'its visible edge does not fix its rotation'
    return out


def verdict(fit, row, rpx):
    """(state, reason) of one fitted marker against the existing limits; row: track.metrics of the fit (side_pct,
    inout_pct, tilt_line_deg). Over a limit with an unreliable edge is 'Possible misalignment - inspect', never a pass."""
    if fit.get('gate') or fit.get('note') or 'c_obs' not in fit:
        return CANT, fit.get('gate') or fit.get('note') or 'no fit'
    s, io = row.get('side_pct', float('nan')), row.get('inout_pct', float('nan'))
    if s != s and io != io:
        return CANT, 'its minute-track references are hidden'
    pos = math.hypot(0 if s != s else s, 0 if io != io else io)
    over = []
    if pos > POS_LIMIT_PCT + 100 * ALLOW_PX / rpx:
        over.append('moved %.2f %% R' % pos)
    lim = TILT_LIMIT.get(fit['shape'])
    t = row.get('tilt_line_deg', float('nan'))
    if lim is not None and t == t and abs(t) > lim:
        over.append('turned %+.2f deg' % t)
    if not over:
        return NO_ISSUE, fit.get('uncertain', '')
    if fit.get('uncertain'):
        return INSPECT, ', '.join(over) + '; but ' + fit['uncertain']
    return CLEARLY_OFF, ', '.join(over)
