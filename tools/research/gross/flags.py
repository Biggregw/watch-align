"""Research only: Loupe-style marker checks (hand runs from the pivot, sibling shape, band clipping) used as a GATE.

Ideas taken from the Loupe QC page (read, not copied): per hour a polar wedge around the hour line; brightness relative
to the median of the WHOLE ring at each radius (removes radial lighting and dial colour); the marker's radial band
learned as the median over all hour lines (dial text on a few lines does not count); a blob at 50 % of the wedge peak,
connected, preferring blobs near the hour line and penalising ones touching the wedge side; blob pixels clipped to the
band learned from clean blobs; weighted moments for the centre and principal axis; flags for blobs touching the band
limits (obstructed), touching the wedge side (merged), shapes unlike their siblings (unusual), and hands found as
straight runs from the pivot that reach the marker. A flagged marker is withheld.

Coordinates: the app's canonical dial frame (centre 0,0; radius 1; y down), sampled through the dial pose H.
"""
import math
import sys

import cv2
import numpy as np

sys.path.insert(0, '/home/user/watch-align/tools/research/overlay_gap')
import overlay_gap as og  # noqa: E402

HALF_DEG = 14.0


def hour_angle(h):
    return math.radians((h % 12) * 30.0)       # clockwise from 12, canonical: x = r sin a, y = -r cos a


def photo_context(img, H, d):
    """Per-radius ring medians, the learned band per shape, and hands, for one photo."""
    rpx = d['rpx']
    rs_all = []
    for m in d['markers']:
        y = np.asarray(m['yellow'], float)
        rr = np.hypot(y[:, 0], y[:, 1])
        rs_all += [rr.min(), rr.max()]
    r0 = max(0.2, min(rs_all) - 0.06)
    r1 = min(1.0, max(rs_all) + 0.06)
    dr = 0.5 / rpx
    radii = np.arange(r0, r1 + 1e-12, dr)
    na = 1440
    ang = np.arange(na) * 2 * math.pi / na
    P = np.stack([np.outer(radii, np.sin(ang)), -np.outer(radii, np.cos(ang))], -1).reshape(-1, 2)
    ringv = og.sample(img, og.project(H, P)).reshape(len(radii), na)
    med = np.nanmedian(ringv, axis=1)
    ctx = dict(rpx=rpx, radii=radii, med=med, r0=r0, dr=dr)
    # wedges
    da = (0.5 / rpx) / ((r0 + r1) / 2)
    nw = int(round(2 * math.radians(HALF_DEG) / da)) + 1
    ctx['da'] = da
    ctx['nw'] = nw
    W = {}
    for m in d['markers']:
        a0 = hour_angle(m['hour']) - math.radians(HALF_DEG)
        aa = a0 + np.arange(nw) * da
        Q = np.stack([np.outer(radii, np.sin(aa)), -np.outer(radii, np.cos(aa))], -1).reshape(-1, 2)
        v = og.sample(img, og.project(H, Q)).reshape(len(radii), nw)
        W[m['hour']] = dict(a0=a0, D=np.nan_to_num(v - med[:, None], nan=0.0), shape=m['shape'])
    ctx['W'] = W
    # learned band (bright markers): per shape when >= 3 markers, else shared
    def learn(hours):
        prof = []
        c0, c1 = int(nw * 0.34), int(math.ceil(nw * 0.66))
        for h in hours:
            prof.append(W[h]['D'][:, c0:c1].max(axis=1))
        if len(prof) < 1:
            return None
        mm = np.median(np.array(prof), axis=0)
        pk = mm.max()
        thr = 0.35 * pk
        best, ja, jb, j = 0, -1, -1, 0
        while j < len(mm):
            if mm[j] <= thr:
                j += 1
                continue
            k, I = j, 0.0
            while k < len(mm) and mm[k] > thr:
                I += mm[k]
                k += 1
            if I > best:
                best, ja, jb = I, j, k - 1
            j = k
        return (ja, jb) if ja >= 0 else None
    allh = [m['hour'] for m in d['markers']]
    shared = learn(allh)
    ctx['band'] = {}
    for shape in {m['shape'] for m in d['markers']}:
        hs = [m['hour'] for m in d['markers'] if m['shape'] == shape]
        b = learn(hs)                 # the shape is known from the model: its own band even with 1-2 markers
        pad = int(round((0.025 if len(hs) >= 3 else 0.04) / dr))
        b = b or shared
        if b:
            ctx['band'][shape] = (max(0, b[0] - pad), min(len(radii) - 1, b[1] + pad))
    # hands: straight runs from the pivot (0.1-0.38 R) that differ from the ring median
    rr = np.linspace(0.1, 0.38, 40)
    a2 = np.arange(720) * math.pi / 360
    Q = np.stack([np.outer(rr, np.sin(a2)), -np.outer(rr, np.cos(a2))], -1).reshape(-1, 2)
    hv = og.sample(img, og.project(H, Q)).reshape(len(rr), 720)
    hm = np.nanmedian(hv, axis=1)
    on = np.abs(hv - hm[:, None]) > 0.07 * 255
    frac = on.mean(axis=0)
    hands, run = [], None
    for i in range(721):
        f = frac[i % 720] if i < 720 else 0
        if f >= 0.85:
            run = [i, i] if run is None else [run[0], i]
        elif run is not None:
            hands.append(((run[0] + run[1]) / 2 * 0.5, (run[1] - run[0] + 1) * 0.5))
            run = None
    ctx['hands'] = [hd for hd in hands if hd[1] <= 25]
    ctx['img'], ctx['H'] = img, H
    return ctx


def moments(xs, ys, w):
    W = w.sum()
    mx, my = (w * xs).sum() / W, (w * ys).sum() / W
    cxx = (w * (xs - mx) ** 2).sum() / W
    cyy = (w * (ys - my) ** 2).sum() / W
    cxy = (w * (xs - mx) * (ys - my)).sum() / W
    tr, det = cxx + cyy, cxx * cyy - cxy * cxy
    disc = math.sqrt(max(0, tr * tr / 4 - det))
    l1, l2 = tr / 2 + disc, max(1e-12, tr / 2 - disc)
    ax = 0.5 * math.atan2(2 * cxy, cxx - cyy)
    return mx, my, ax, 2 * math.sqrt(3 * l1), 2 * math.sqrt(3 * l2)


def blob(ctx, m, D=None):
    """The marker's blob in its wedge: (pixels xs, ys, weights, flags)."""
    Wd = ctx['W'][m['hour']]
    D = Wd['D'] if D is None else D
    radii, da, nw = ctx['radii'], ctx['da'], ctx['nw']
    band = ctx['band'].get(m['shape'])
    if band is None:
        return None, 'no marker band'
    lo, hi = band
    Dm = D.copy()
    Dm[:lo] = 0
    Dm[hi + 1:] = 0
    peak = float(np.percentile(Dm[Dm > 0], 98)) if (Dm > 0).any() else 0.0   # robust: a glint is not the peak
    if peak < 0.04 * 255:
        return None, 'no contrast'
    T = max(0.5 * peak, 0.03 * 255)
    n, lab = cv2.connectedComponents((Dm > T).astype(np.uint8), connectivity=4)
    best = None
    jj, ii = np.mgrid[0:D.shape[0], 0:D.shape[1]]
    a = Wd['a0'] + ii * da
    r = radii[jj]
    X, Y = r * np.sin(a), -r * np.cos(a)
    h0 = hour_angle(m['hour'])
    for k in range(1, n):
        sel = lab == k
        if sel.sum() < 24:
            continue
        w = Dm[sel] * r[sel]
        mx, my = (w * X[sel]).sum() / w.sum(), (w * Y[sel]).sum() / w.sum()
        dev = math.degrees((math.atan2(mx, -my) - h0 + math.pi) % (2 * math.pi) - math.pi)
        side = ii[sel].min() == 0 or ii[sel].max() == nw - 1
        inner = jj[sel].min() <= lo or (jj[sel].max() >= hi and hi < D.shape[0] - 1 and (D[hi + 1][ii[sel][jj[sel] == hi]] > T).any())
        sc = w.sum() / (1 + abs(dev) / 4) * (0.08 if side else 1)
        if best is None or sc > best[0]:
            best = (sc, sel, side, inner)
    if best is None:
        return None, 'not found'
    _, sel, side, inner = best
    flag = 'obstructed' if inner else 'merged' if side else None
    return (X[sel], Y[sel], (np.clip(Dm[sel] / peak, 0, 1) * r[sel]).astype(float)), flag


def measure(ctx, m, insets, D=None, sib=None):
    pix, flag = blob(ctx, m, D)
    if pix is None:
        return {'status': flag}
    xs, ys, w = pix
    mx, my, ax, ln, wd = moments(xs, ys, w)
    rpx = ctx['rpx']
    shape = m['shape']
    if sib and not flag and shape in sib:
        L, Wm = sib[shape]
        if wd > 1.6 * Wm or wd < 0.6 * Wm or ln > 1.3 * L or ln < 0.42 * L:
            flag = 'unusual'
    # hands reaching this marker
    if not flag:
        ang = math.degrees(math.atan2(mx, -my)) % 360
        for a, wdt in ctx['hands']:
            if abs((ang - a + 180) % 360 - 180) <= wdt / 2 + 4:
                flag = 'hand'
    poly = og.template(m, insets)
    if shape == 'round':
        cpoly = poly.mean(axis=0)
    else:
        e = np.roll(poly, -1, axis=0)
        cr = poly[:, 0] * e[:, 1] - e[:, 0] * poly[:, 1]
        A = cr.sum() / 2
        cpoly = np.array([((poly[:, 0] + e[:, 0]) * cr).sum(), ((poly[:, 1] + e[:, 1]) * cr).sum()]) / (6 * A)
    d = np.array([mx, my]) - cpoly
    th = 0.0
    if shape != 'round':
        # template's own principal axis (rasterised in the same way) vs the blob's
        g = np.linspace(-1, 1, 801)
        tm = np.zeros((801, 801), np.uint8)
        half = float(np.max(np.abs(poly - cpoly))) * 1.2
        pp = (poly - cpoly) / half * 400 + 400
        cv2.fillPoly(tm, [np.round(pp * 16).astype(np.int32).reshape(-1, 1, 2)], 1, shift=4)
        jj, ii = np.nonzero(tm)
        _, _, at, _, _ = moments(ii.astype(float), jj.astype(float), np.ones(len(ii)))
        th = (ax - at + math.pi / 2) % math.pi - math.pi / 2
        if abs(math.degrees(th)) > 25:
            flag = flag or 'unusual'
    hour = m['hour']
    a = hour_angle(hour)
    er = np.array([math.sin(a), -math.cos(a)])
    et = np.array([math.cos(a), math.sin(a)])
    r_m = math.hypot(mx, my)
    dev_deg = math.degrees(math.atan2(mx, -my) - a)
    dev_deg = (dev_deg + 180) % 360 - 180
    return {'status': 'measured' if not flag else 'withheld: %s' % flag, 'flag': flag or '',
            'radial_px': float(d @ er * rpx), 'tangential_px': float(d @ et * rpx),
            'rotation_deg': math.degrees(th) if shape != 'round' else float('nan'),
            'dev_deg': dev_deg, 'len': ln, 'width': wd, '_T': (cpoly.copy(), d, th)}


def siblings(ctx, markers, insets):
    """Median length / width per shape from clean blobs (for the 'unusual' check)."""
    by = {}
    for m in markers:
        r = measure(ctx, m, insets)
        if r.get('status') == 'measured':
            by.setdefault(m['shape'], []).append((r['len'], r['width']))
    return {s: (float(np.median([v[0] for v in L])), float(np.median([v[1] for v in L]))) for s, L in by.items() if len(L) >= 3}
