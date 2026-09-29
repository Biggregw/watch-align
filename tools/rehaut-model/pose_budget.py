"""Per-measurement pose distortion budgets (research, 2026-09-29).

Projects the dial (printed ticks at z=0, applied indices at z=hm) through a pinhole camera at
distance D, displaced by `tilt` towards the clock direction `az` (0 = towards 12, 90 = towards 3),
and measures every QC quantity the way the app does:
  round markers: tangential offset after the DialFrame un-squash (fraction of diameter) and size
                 ratio (surround diameter / median)                       GmtRoundMarkerAnalyzer
  12: gap (outer edge to tick60, / triangle width), axis rotation vs the 59-01 chord, spacing
      asymmetry (01-side minus 59-side corner-to-tick distance, / width)    GmtTwelveLandmarkAnalyzer
  6, 3, 9 batons: centring (outer-edge midpoint vs the tick midpoint, along the chord, / width)
      and rotation (inner->outer axis vs the chord normal)                  GmtSixLandmarkAnalyzer
All measured in the raw image (as the batons and 12 are) except the round-marker offsets.
Writes results/pose_budget.csv and results/pose_budget_limits.csv.
"""
import csv, itertools, math
import numpy as np
import rehaut_model as M

BATON_R, BATON_RH, BATON_TH = 0.758, 0.150, 0.060
TICK = M.TICK_R
THRESH = dict(round_off=0.15, round_size=0.12, gap12=0.094 - 0.070, rot12=2.0, asym12=0.10,
              six_c=0.10, six_r=2.0, three_c=0.10, three_r=2.0, nine_c=0.10, nine_r=2.0)
# rot12: a lean whose top edge does not turn with it is flagged from SKEW_ONLY_MIN_DEG = 2.0 (pose
# turns the axis but not the top edge relative to the chord: see top12); gap12: distance
# from a typical genuine gap (0.094) down to LOW_CLEARANCE_ATTENTION.


def cam_dir(tilt, az, D):
    t, a = math.radians(tilt), math.radians(az)
    C = D * np.array([math.sin(t) * math.sin(a), math.sin(t) * math.cos(a), math.cos(t)])
    fwd = -C / np.linalg.norm(C)
    up0 = np.array([0.0, 1.0, 0.0]) if abs(fwd[1]) < 0.9 else np.array([1.0, 0.0, 0.0])
    right = np.cross(fwd, up0); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    return C, fwd, right, up, D


def P(cam, pts):
    return M.project(np.array(pts, dtype=float), cam)


def clock_xy(deg, r):
    t = math.radians(deg); return r * math.sin(t), r * math.cos(t)


def tick(minute, cam):
    x, y = clock_xy(minute * 6, TICK); return P(cam, [[x, y, 0]])[0]


def baton(hour, cam, hm, T=lambda p: p):
    """centring and rotation of the baton at `hour` against ticks hour*5 -+ 1 (T: image -> measuring frame)."""
    a = math.radians(hour * 30)
    rad = np.array([math.sin(a), math.cos(a)]); tan = np.array([math.cos(a), -math.sin(a)])
    c = BATON_R * rad
    ol, orr = c + BATON_RH * rad - BATON_TH * tan, c + BATON_RH * rad + BATON_TH * tan
    il, ir = c - BATON_RH * rad - BATON_TH * tan, c - BATON_RH * rad + BATON_TH * tan
    q = T(P(cam, [[*ol, hm], [*orr, hm], [*il, hm], [*ir, hm]]))
    tb, ta = T(np.array([tick(hour * 5 - 1, cam), tick(hour * 5 + 1, cam)]))
    s = tb - ta; s /= np.linalg.norm(s)
    ol_, or_, il_, ir_ = q
    if (or_ - ol_) @ s < 0: ol_, or_, il_, ir_ = or_, ol_, ir_, il_
    width = np.linalg.norm(or_ - ol_)
    om, im, tm = (ol_ + or_) / 2, (il_ + ir_) / 2, (ta + tb) / 2
    centring = (om - tm) @ s / width
    ctr = T(P(cam, [[0, 0, 0]]))[0]
    u = np.array([-s[1], s[0]])
    if (tm - ctr) @ u < 0: u = -u
    ax = om - im
    rot = math.degrees(math.atan2(u[0] * ax[1] - u[1] * ax[0], u @ ax))
    rot = (rot + 90) % 180 - 90
    return centring, rot


def measure(tilt, az, D=15.0, hm=0.02, unsquash=False):
    """unsquash=True measures the 12 and the batons after undoing the dial-edge ellipse (as the
    round markers already are): a candidate correction that needs no pose estimate."""
    cam = cam_dir(tilt, az, D)
    out = {}
    inner = M.project(M.ring(1.0, 0.0), cam)
    c, a_ax, b_ax, ang = M.fit_ellipse(inner)
    ct, st = math.cos(ang), math.sin(ang); rr = math.sqrt(a_ax * b_ax)
    def rect(p2):
        d = p2 - c; u = ct * d[:, 0] + st * d[:, 1]; v = -st * d[:, 0] + ct * d[:, 1]
        return np.stack([u / a_ax * rr, v / b_ax * rr], axis=1)
    offs, diam = [], []
    tt = np.linspace(0, 2 * np.pi, 120, endpoint=False)
    for hr in M.HOURS_ROUND:
        cx, cy = clock_xy(hr * 30, M.ROUND_CENTER_R)
        outline = np.stack([cx + M.ROUND_OUTER_R * np.cos(tt), cy + M.ROUND_OUTER_R * np.sin(tt), np.full(tt.size, hm)], axis=1)
        mc_raw, mr_raw = M.kasa(M.project(outline, cam))
        mc, mr = M.kasa(rect(M.project(outline, cam)))
        ticks = rect(np.array([tick(hr * 5 - 1, cam), tick(hr * 5 + 1, cam)]))
        mid = ticks.mean(axis=0); u = ticks[1] - ticks[0]; u /= np.linalg.norm(u)
        offs.append(float((mc - mid) @ u / (2 * mr))); diam.append(2 * mr_raw)
    diam = np.array(diam); med = np.median(diam)
    out['round_off'] = max(abs(o) for o in offs)
    out['round_size'] = float(np.max(np.abs(diam / med - 1)))
    T = rect if unsquash else (lambda p: p)
    # 12 triangle.
    br = M.TRI_CENTER_R + M.TRI_BASE_OUT; ar = M.TRI_CENTER_R - M.TRI_APEX_IN
    pl, pr, pa = T(P(cam, [[-M.TRI_HALF_BASE, br, hm], [M.TRI_HALF_BASE, br, hm], [0, ar, hm]]))
    p59, p60, p01 = T(np.array([tick(59, cam), tick(0, cam), tick(1, cam)]))
    base = pr - pl; w = np.linalg.norm(base); nb = np.array([-base[1], base[0]]) / w
    if nb @ (p60 - pl) < 0: nb = -nb
    out['gap12'] = float((p60 - pl) @ nb / w) - M.TRUE_GAP
    chord = p01 - p59; cn = np.array([-chord[1], chord[0]]); cn /= np.linalg.norm(cn)
    axis = (pl + pr) / 2 - pa; axis /= np.linalg.norm(axis)
    if axis @ cn < 0: cn = -cn
    out['rot12'] = math.degrees(math.atan2(cn[0] * axis[1] - cn[1] * axis[0], cn @ axis))
    out['top12'] = (math.degrees(math.atan2(base[1], base[0]) - math.atan2(chord[1], chord[0])) + 90) % 180 - 90
    out['asym12'] = float((np.linalg.norm(pr - p01) - np.linalg.norm(pl - p59)) / w)
    for name, hr in (('six', 6), ('three', 3), ('nine', 9)):
        out[name + '_c'], out[name + '_r'] = baton(hr, cam, hm, T)
    return out


def frontal_bias(D, hm):
    return measure(0.0, 0.0, D, hm)


if __name__ == '__main__':
  import sys
  for UNS in (False, True):
    tag = '_unsquash' if UNS else ''
    rows = []
    for D, hm in itertools.product((6.0, 15.0, 50.0), (0.01, 0.02, 0.03)):
        f0 = measure(0.0, 0.0, D, hm, UNS)
        for tilt in (0, 2.5, 5, 7.5, 10, 12.5, 15, 20, 25, 30):
            for az in range(0, 360, 45):
                m = measure(tilt, az, D, hm, UNS)
                r = dict(D=D, hm=hm, tilt=tilt, az=az)
                for k, v in m.items():
                    # Pose-induced part: subtract the straight-on reading (perspective magnification of
                    # the raised index at this distance is present even with no tilt).
                    r[k] = round(v - (f0[k] if k not in ('round_off', 'round_size') else 0.0), 5)
                    r[k + '_raw'] = round(v, 5)
                rows.append(r)
    with open(f'results/pose_budget{tag}.csv', 'w', newline='') as fh:
        wr = csv.DictWriter(fh, fieldnames=list(rows[0])); wr.writeheader(); wr.writerows(rows)
    # Worst case over azimuth; the tilt at which the pose-induced error reaches 25/50/100% of CHECK.
    lim = []
    for D, hm in itertools.product((6.0, 15.0, 50.0), (0.01, 0.02, 0.03)):
        sub = [r for r in rows if r['D'] == D and r['hm'] == hm]
        for k, th in list(THRESH.items()) + [('top12', 0.75)]:
            rec = dict(D=D, hm=hm, measure=k, check=th)
            tilts = sorted({r['tilt'] for r in sub})
            worst = [max(abs(r[k]) for r in sub if r['tilt'] == t) for t in tilts]
            for frac in (0.25, 0.5, 1.0):
                x = next((tilts[i - 1] + (tilts[i] - tilts[i - 1]) * (frac * th - worst[i - 1]) / (worst[i] - worst[i - 1])
                          for i in range(1, len(tilts)) if worst[i] >= frac * th), float('inf'))
                rec[f'tilt_at_{int(frac * 100)}pct'] = round(x, 1) if math.isfinite(x) else '>30'
            # Which camera direction hurts most at 10 deg.
            at10 = [r for r in sub if r['tilt'] == 10]
            wr_ = max(at10, key=lambda r: abs(r[k]))
            rec['worst_az_at10'] = wr_['az']; rec['err_at10'] = wr_[k]
            rec['err_at10_az0'] = next(r[k] for r in at10 if r['az'] == 0)
            rec['err_at10_az90'] = next(r[k] for r in at10 if r['az'] == 90)
            rec['err_at10_az180'] = next(r[k] for r in at10 if r['az'] == 180)
            rec['err_at10_az45'] = next(r[k] for r in at10 if r['az'] == 45)
            rec['frontal_bias'] = round(float(measure(0.0, 0.0, D, hm, UNS)[k]), 4)
            lim.append(rec)
    with open(f'results/pose_budget_limits{tag}.csv', 'w', newline='') as fh:
        wr = csv.DictWriter(fh, fieldnames=list(lim[0])); wr.writeheader(); wr.writerows(lim)
    print('unsquash' if UNS else 'raw')
    for r in lim:
        if r['hm'] == 0.02 and r['D'] == 15.0:
            print('  %-10s check %-6.3g 25%%:%-5s 50%%:%-5s 100%%:%-5s  @10deg: az0 %+.3f az45 %+.3f az90 %+.3f az180 %+.3f  frontal %+.4f' % (
                r['measure'], r['check'], r['tilt_at_25pct'], r['tilt_at_50pct'], r['tilt_at_100pct'], r['err_at10_az0'], r['err_at10_az45'], r['err_at10_az90'], r['err_at10_az180'], r['frontal_bias']))
