"""Research only (not used by the app): hour markers measured the way the owner judges them by eye (eye test,
17 Oct 2026).

The owner's references, all found in the single photo:
  * hour LINES: from each hour tick of the printed minute track, straight through the centre to the opposite hour
    tick. The hour positions come from the minute ticks themselves (local tick lattice), not from the overlay or the
    markers.
  * the RING through the centres of the round markers, and the CIRCLE where the baton ends (and the triangle's top)
    should sit: the genuine marker geometry scaled by this dial's own ring (the other clean round markers, leave one
    out), so a slight camera angle or scale error that moves every marker cancels.

Per marker (centre of the lume from overlay_gap.centroid_fit; gate from gross.measure_marker: app hand check, Loupe-style
flags, lume area, resolution):
  side   = the marker centre's distance from its hour line (px, % of dial radius; + = clockwise)
  inout  = the marker centre's distance from the ring / circle along the line (+ = outward, towards the minute track)
  tilt   = batons only: the baton's long axis against its hour line (deg; + = clockwise)
Per dial: ring_turn = median side of the clean markers (the whole ring of markers turned against the printed track).
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'gross'))
sys.path.insert(0, os.path.join(HERE, '..', 'overlay_gap'))
import overlay_gap as og  # noqa: E402
import gross as gr  # noqa: E402
import flags as fl  # noqa: E402

R0, R1 = 0.78, 1.03     # radial range searched for the minute track (canonical)
STEP = 0.25             # px
D6 = math.radians(6.0)
MIN_RING = 3            # other clean round markers needed for the leave-one-out ring
MODELS = os.path.join(HERE, '..', '..', '..', 'android', 'app', 'src', 'main', 'assets', 'models')


def track_inner_r(model):
    return float(json.load(open(os.path.join(MODELS, model, 'model.json')))['pose']['minute_track_inner_r'])


def cpt(r, a):
    return np.array([r * math.sin(a), -r * math.cos(a)])


def polar(img, H, radii, ang):
    P = np.stack([np.outer(radii, np.sin(ang)), -np.outer(radii, np.cos(ang))], -1).reshape(-1, 2)
    return og.sample(img, og.project(H, P)).reshape(len(radii), len(ang))


def px_between(H, p, q):
    a, b = og.project(H, np.array([p, q]))
    return float(np.hypot(*(b - a)))


def first_cross(prof, thr, start, step):
    i = start
    while 0 <= i + step < len(prof):
        a, b = prof[i], prof[i + step]
        if (a - thr) * (b - thr) <= 0 and a != b:
            return i + step * (a - thr) / (a - b)
        i += step
    return None


def tick_track(img, H, rpx):
    """The 60 minute ticks: sub-pixel angle, amplitude, width, local background, inner-end radius; ticks under a hand,
    glare or dial text are marked bad (unlike their neighbours)."""
    from scipy.ndimage import percentile_filter
    radii = np.arange(R0, R1, STEP / rpx)
    na = int(round(2 * math.pi * 0.95 * rpx / STEP))
    na -= na % 60
    ang = np.arange(na) * 2 * math.pi / na
    V = polar(img, H, radii, ang)
    V = np.nan_to_num(V, nan=float(np.nanmedian(V)))
    e = np.exp(-1j * 60 * ang)
    A = np.abs((V * e).mean(axis=1)) / (np.std(V, axis=1) + 1e-6)     # 60-fold strength per radius
    j0 = int(np.argmax(A))
    jb = j0
    while jb > 0 and A[jb - 1] > 0.35 * A[j0]:
        jb -= 1
    je = j0
    while je < len(radii) - 1 and A[je + 1] > 0.35 * A[j0]:
        je += 1
    core = [j for j in range(jb, je + 1) if A[j] > 0.6 * A[j0]]
    ca, cb = min(core), max(core)
    cb2 = ca + max(2, int(round((cb - ca) * 0.7)))
    prof = V[ca:cb2 + 1].mean(axis=0)
    # tick 0 is the one nearest 12 o'clock: the dial pose is right to well within half a tick (3 deg), so wrap to
    # (-3, +3] deg (wrapping to 0..6 deg mislabels every tick by one when 12 o'clock sits just anticlockwise of 0)
    th0 = (np.angle((prof * e).sum()) / 60.0 + D6 / 2) % D6 - D6 / 2
    spa = 2 * math.pi / na
    wcol = int(round(3.0 * math.pi / 180 / spa))
    ext = np.r_[prof[-wcol:], prof, prof[:wcol]]
    bg = percentile_filter(ext, 25, size=2 * wcol + 1, mode='wrap')[wcol:-wcol]
    res = prof - bg
    ticks = []
    for k in range(60):
        c = int(round((th0 + k * D6) / spa)) % na
        w = int(round(1.5 * math.pi / 180 / spa))
        idx = np.arange(c - w, c + w + 1) % na
        seg = res[idx]
        ip = int(np.argmax(seg))
        pk = seg[ip]
        lo = ip
        while lo > 0 and seg[lo - 1] > 0.5 * pk:
            lo -= 1
        hi = ip
        while hi < len(seg) - 1 and seg[hi + 1] > 0.5 * pk:
            hi += 1
        ww = np.clip(seg[lo:hi + 1] - 0.5 * pk, 0, None)
        off = (np.arange(lo, hi + 1) * ww).sum() / max(ww.sum(), 1e-9)
        hw = max(1, (hi - lo + 1) // 3)
        cc = c - w + int(round(off))
        rp = V[:, np.arange(cc - hw, cc + hw + 1) % na].mean(axis=1)
        bcols = np.r_[np.arange(cc - wcol, cc - wcol // 2), np.arange(cc + wcol // 2, cc + wcol)] % na
        dd = rp - np.median(V[:, bcols], axis=1)
        dl = np.median(dd[ca:cb2 + 1])
        inner = first_cross(dd, 0.5 * dl, (ca + cb2) // 2, -1) if dl > 0 else None
        ticks.append(dict(k=k, a=(c - w + off) * spa, amp=float(pk), width=(hi - lo + 1) * spa,
                          bg=float(np.median(bg[idx])), hour=(k % 5 == 0),
                          r_in=float(np.interp(inner, np.arange(len(radii)), radii)) if inner is not None else float('nan')))
    for kind in (True, False):
        T = [t for t in ticks if t['hour'] == kind]
        ma = np.median([t['amp'] for t in T])
        mw = np.median([t['width'] for t in T])
        mr = np.nanmedian([t['r_in'] for t in T])
        for t in T:
            bad = []
            na_ = np.median([ticks[(t['k'] + dk) % 60]['amp'] for dk in (-4, -3, -2, -1, 1, 2, 3, 4)
                             if ticks[(t['k'] + dk) % 60]['hour'] == kind] or [ma])
            if not (0.6 * na_ <= t['amp'] <= 1.6 * na_) or t['amp'] < 0.25 * ma:
                bad.append('amp')
            if not (0.6 * mw <= t['width'] <= 1.7 * mw):
                bad.append('width')
            nb = np.median([ticks[(t['k'] + dk) % 60]['bg'] for dk in (-4, -3, -2, 2, 3, 4)])
            if abs(t['bg'] - nb) > max(0.3 * ma, 12):
                bad.append('bg')
            if not np.isfinite(t['r_in']) or abs(t['r_in'] - mr) * rpx > 3.0:
                bad.append('inner')
            t['bad'] = bad
    return ticks


def hour_line(ticks, h, rpx):
    """Hour angle from a robust straight-line fit of tick angle against index over the 5 ticks either side (the hour
    tick itself may be missing, e.g. the crown at 6). None when fewer than 2 clean ticks on either side."""
    k0 = (5 * h) % 60
    ref = math.radians(6 * k0)
    ks, As = [], []
    for dk in range(-5, 6):
        t = ticks[(k0 + dk) % 60]
        if not t['bad']:
            ks.append(dk)
            As.append(ref + (t['a'] - ref + math.pi) % (2 * math.pi) - math.pi)
    ks, As = np.array(ks, float), np.array(As)
    if len(ks) < 5 or (ks < 0).sum() < 2 or (ks > 0).sum() < 2:
        return None
    keep = np.ones(len(ks), bool)
    for _ in range(3):
        p = np.polyfit(ks[keep], As[keep], 1)
        r = As - np.polyval(p, ks)
        keep = np.abs(r) <= 3 * max(1.4826 * np.median(np.abs(r[keep])), 0.3 / rpx / 0.95)
        if keep.sum() < 4:
            return None
    return float(np.polyfit(ks[keep], As[keep], 1)[1])


def context(img, d, insets=None):
    """Everything measured once per photo: gate context, minute ticks, hour lines."""
    H = np.array(d['H'], float).reshape(3, 3)
    rpx = float(d['rpx'])
    insets = insets or og.model_insets(d['model'])
    ctx = fl.photo_context(img, d['H'], d)
    sib = fl.siblings(ctx, d['markers'], insets)
    ticks = tick_track(img, H, rpx)
    lines = {h: hour_line(ticks, h, rpx) for h in range(12)}
    r_in = float(np.nanmedian([t['r_in'] for t in ticks if not t['bad']]))
    return dict(H=H, rpx=rpx, insets=insets, ctx=ctx, sib=sib, ticks=ticks, lines=lines,
                track_scale=r_in / track_inner_r(d['model']), nbad=sum(1 for t in ticks if t['bad']))


def centre_of(img, d, m, C, D=None):
    """Gate + lume centre (gross.measure_marker): adds 'c_obs' (measured centre) and 'c_gen' (genuine centre)."""
    r = gr.measure_marker(img, d, m, C['insets'], C['ctx'], C['sib'], D)
    if 'centre' in r:
        r['c_gen'] = np.asarray(r['centre'], float)
        r['c_obs'] = r['c_gen'] + np.asarray(r['t'], float)
    return r


def ring_scale(base, i):
    """This dial's ring scale from the OTHER clean round markers (observed / genuine centre radius)."""
    s = [np.hypot(*b['c_obs']) / np.hypot(*b['c_gen']) for j, b in enumerate(base)
         if j != i and b.get('shape') == 'round' and not b.get('gate') and 'c_obs' in b]
    return float(np.median(s)) if len(s) >= MIN_RING else None


def metrics(r, h, C, scale):
    """side / inout / tilt of one measured marker, given the dial context and ring scale."""
    H, rpx, L = C['H'], C['rpx'], C['lines']
    out = {}
    if 'c_obs' not in r:
        return out
    c, g = r['c_obs'], r['c_gen']
    rc = float(np.hypot(*c))
    ac = math.atan2(c[0], -c[1])
    a = L.get(h % 12)
    if a is not None:
        da = (ac - a + math.pi) % (2 * math.pi) - math.pi
        out['side_px'] = math.copysign(px_between(H, cpt(rc, a), cpt(rc, ac)), da)
        out['side_pct'] = 100 * out['side_px'] / rpx
    if scale is not None:
        re = scale * float(np.hypot(*g))
        out['inout_px'] = math.copysign(px_between(H, cpt(re, ac), cpt(rc, ac)), rc - re)
        out['inout_pct'] = 100 * out['inout_px'] / rpx
        out['scale'] = scale
    if r['shape'] != 'round' and r.get('tilt_deg') == r.get('tilt_deg'):
        a1, a2 = L.get(h % 12), L.get((h + 6) % 12)
        if a1 is not None and a2 is not None:
            line = cpt(0.93, a2) - cpt(0.93, a1)
            nom = cpt(0.93, math.radians(30 * ((h + 6) % 12))) - cpt(0.93, math.radians(30 * (h % 12)))
            dev = (math.atan2(line[1], line[0]) - math.atan2(nom[1], nom[0]) + math.pi / 2) % math.pi - math.pi / 2
            out['tilt_line_deg'] = r['tilt_deg'] - math.degrees(dev)
    return out


def analyse(img, d, insets=None):
    """All markers of one photo. Returns (rows, C, base)."""
    C = context(img, d, insets)
    base = [centre_of(img, d, m, C) for m in d['markers']]
    rows = []
    for i, (m, b) in enumerate(zip(d['markers'], base)):
        row = dict(hour=m['hour'], shape=m['shape'], gate=b.get('gate', ''))
        if C['lines'].get(m['hour'] % 12) is None:
            row['gate'] = '; '.join(x for x in (row['gate'], 'minute ticks near it hidden') if x)
        row.update(metrics(b, m['hour'], C, ring_scale(base, i)))
        rows.append(row)
    clean = [r['side_pct'] for r in rows if not r['gate'] and 'side_pct' in r]
    ring_turn = float(np.median(clean)) if len(clean) >= 3 else float('nan')
    for r in rows:
        r['ring_turn_pct'] = ring_turn
    return rows, C, base


def move_confined(img, d, m, C, v, dth):
    """The photo with this marker moved by v (canonical) and turned by dth about its centre (Lanczos, as
    benchmark.move_marker), but confined to the marker: its new footprint takes the moved marker, the place it left
    takes the plain dial around it, and nothing else (the minute track above all) changes."""
    import cv2
    import benchmark as bm
    H, rpx = C['H'], C['rpx']
    moved, _ = bm.move_marker(img, d['H'], m, rpx, v, dth, C['insets'])
    y = np.asarray(m['yellow'], float)
    yc = y.mean(axis=0)
    cu, su = math.cos(dth), math.sin(dth)
    y2 = yc + (y - yc) @ np.array([[cu, -su], [su, cu]]).T + v
    k = max(3, int(0.012 * rpx)) | 1

    def footprint(poly):
        mk = np.zeros(img.shape, np.uint8)
        cv2.fillPoly(mk, [np.round(og.project(H, poly)).astype(np.int32)], 1)
        return cv2.GaussianBlur(cv2.dilate(mk, np.ones((k, k), np.uint8)).astype(np.float32), (k, k), 0)
    new, old = footprint(y2), footprint(y)
    around = (cv2.dilate((old > 0).astype(np.uint8), np.ones((3 * k, 3 * k), np.uint8)) > 0) & (old == 0)
    dial = float(np.median(img[around]))
    vacated = (1 - old) * img + old * dial
    return (new * moved + (1 - new) * vacated).astype(img.dtype)
