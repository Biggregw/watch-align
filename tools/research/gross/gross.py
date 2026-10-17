"""Research only (not used by the app): "clearly off" decision for hour markers, owner goal of 17 Oct 2026.

Per marker:
  1. Gate (fail closed -> "Can't check"): the app's hand check, Loupe-style flags (hand runs from the pivot, blob cut
     by the marker band, blob touching its wedge side, shape unlike its siblings), the centre method's own area check,
     and photo resolution.
  2. Measure: centre of the lume (overlay_gap.centroid_fit): shift (radial, tangential) and, for batons and the
     triangle, tilt from the blob's principal axis.
  3. Two independent votes, both required for "Clearly off":
       absolute: shift beyond T_POS (% of dial radius) or tilt beyond T_ROT, after subtracting the measurement margin;
       relative: the marker is also that far from where the OTHER clean markers put it (similarity fit, leave one out;
                 needs >= MIN_PEERS clean peers), so camera tilt / overlay placement that moves every marker cannot
                 trigger it.
  Otherwise "No visible issue". The numbers are always returned.
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'overlay_gap'))
import overlay_gap as og  # noqa: E402
import flags as fl  # noqa: E402

T_POS = 2.0          # % of dial radius
T_ROT = 4.0          # degrees
MARGIN_PX = 1.0      # measurement margin (ground truth: centre method p99 error ~0.6 px on clean markers)
MARGIN_DEG = 0.5
MIN_PEERS = 5
MIN_RPX = 150        # dial radius below this: the photo is too small to judge


def wedge(img, ctx, m):
    radii, da, nw = ctx['radii'], ctx['da'], ctx['nw']
    a0 = ctx['W'][m['hour']]['a0']
    aa = a0 + np.arange(nw) * da
    Q = np.stack([np.outer(radii, np.sin(aa)), -np.outer(radii, np.cos(aa))], -1).reshape(-1, 2)
    v = og.sample(img, og.project(ctx['H'], Q)).reshape(len(radii), nw)
    return np.nan_to_num(v - ctx['med'][:, None], nan=0.0)


def measure_marker(img, d, m, insets, ctx, sib, D=None, use_app_hand=True):
    rpx = d['rpx']
    out = {'hour': m['hour'], 'shape': m['shape']}
    why = []
    if use_app_hand and not m['hand_clean']:
        why.append('hand (app check)')
    f = fl.measure(ctx, m, insets, D, sib)
    if f.get('flag'):
        why.append(f['flag'])
    elif f.get('status') != 'measured':
        why.append(f.get('status', 'not found'))
    c = og.centroid_fit(img, d['H'], m, rpx, insets)
    if c.get('status') != 'measured':
        why.append('lume not the expected size')
    if rpx < MIN_RPX:
        why.append('photo too small')
    if c.get('status') == 'measured' or '_T' in c:
        cc, t, th = c['_T']
        hour = m['hour']
        a = math.radians((hour % 12) * 30.0)
        er = np.array([math.sin(a), -math.cos(a)])
        et = np.array([math.cos(a), math.sin(a)])
        out.update(centre=cc, t=t, th=th, radial_px=float(t @ er * rpx), tangential_px=float(t @ et * rpx),
                   tilt_deg=math.degrees(th) if m['shape'] != 'round' else float('nan'))
        out['shift_pct'] = 100 * math.hypot(*t)
    out['gate'] = '; '.join(why)
    return out


def similarity(E, M, w=None):
    """M ~ [[a,-b],[b,a]] E + t (least squares). Returns (a, b, tx, ty)."""
    A, y = [], []
    for (ex, ey), (mx, my) in zip(E, M):
        A.append([ex, -ey, 1, 0]); y.append(mx)
        A.append([ey, ex, 0, 1]); y.append(my)
    A, y = np.array(A), np.array(y)
    return np.linalg.lstsq(A, y, rcond=None)[0]


def relative(results, rpx, i):
    """Shift (%R) and tilt (deg) of marker i relative to where its clean peers put it."""
    peers = [r for j, r in enumerate(results) if j != i and not r['gate'] and 't' in r]
    if len(peers) < MIN_PEERS:
        return None
    E = np.array([r['centre'] for r in peers])
    M = E + np.array([r['t'] for r in peers])
    keep = np.ones(len(peers), bool)
    for _ in range(2):    # drop peers far off the common fit (another bad marker must not bend the reference)
        p = similarity(E[keep], M[keep])
        pred = np.c_[p[0] * E[:, 0] - p[1] * E[:, 1] + p[2], p[1] * E[:, 0] + p[0] * E[:, 1] + p[3]]
        res = np.hypot(*(M - pred).T) * rpx
        med = np.median(res[keep])
        mad = np.median(np.abs(res[keep] - med)) + 0.3
        keep = res <= max(med + 3 * mad, 1.5)
        if keep.sum() < MIN_PEERS - 1:
            return None
    r = results[i]
    e = r['centre']
    mi = e + r['t']
    pi = np.array([p[0] * e[0] - p[1] * e[1] + p[2], p[1] * e[0] + p[0] * e[1] + p[3]])
    rot = math.degrees(math.atan2(p[1], p[0]))
    tilt_rel = r['tilt_deg'] - rot if r['tilt_deg'] == r['tilt_deg'] else float('nan')
    return {'rel_pct': 100 * float(np.hypot(*(mi - pi))), 'rel_tilt_deg': tilt_rel, 'peers': int(keep.sum())}


def decide(results, rpx, t_pos=T_POS, t_rot=T_ROT):
    mp = 100 * MARGIN_PX / rpx
    for i, r in enumerate(results):
        if r['gate']:
            r['state'] = "Can't check"
            continue
        rel = relative(results, rpx, i)
        if rel is None:
            r['state'] = "Can't check"
            r['gate'] = 'too few clean markers to compare with'
            continue
        r.update(rel)
        abs_pos = r['shift_pct'] - mp > t_pos
        rel_pos = r['rel_pct'] - mp > t_pos
        abs_rot = r['tilt_deg'] == r['tilt_deg'] and abs(r['tilt_deg']) - MARGIN_DEG > t_rot
        rel_rot = r['rel_tilt_deg'] == r['rel_tilt_deg'] and abs(r['rel_tilt_deg']) - MARGIN_DEG > t_rot
        r['state'] = 'Clearly off' if (abs_pos and rel_pos) or (abs_rot and rel_rot) else 'No visible issue'
    return results


def analyse(img, d, insets=None):
    insets = insets or og.model_insets(d['model'])
    ctx = fl.photo_context(img, d['H'], d)
    sib = fl.siblings(ctx, d['markers'], insets)
    res = [measure_marker(img, d, m, insets, ctx, sib) for m in d['markers']]
    return decide(res, d['rpx']), ctx, sib
