"""
Minimal 3D geometric model of a GMT dial, its rehaut and a perspective camera (research, 2026-09-29).

Units: dial radius R = 1 (the rehaut foot / dial edge). Watch frame: +x towards 3, +y towards 12,
+z out of the dial towards the viewer. Nothing is photorealistic: only projected geometry.

Geometry
- Dial plane z = 0. Printed minute-tick inner ends at r = TICK_R (z = 0).
- Rehaut: a conical ring from (r = 1, z = 0) out and up to (r = 1 + w0, z = h), h = w0 * tan(alpha),
  alpha = wall slope from the dial plane. Frontal (straight-on) visible width = w0.
- Applied indices (round markers, 12 triangle) stand hm above the dial; their outline is at z = hm.
- Optional bezel/crystal lip: a ring edge at (r = 1 + w0, z = h + lip) that can hide the near-side rehaut.

Camera: pinhole at distance D from the watch centre, looking at the centre, displaced towards 12 by
`pitch` and towards 3 by `yaw` (degrees). Image axes: +u to the right, +v up; frontal image of the
dial has scale ~1 (focal length = D).

Measurements emulate the app (GmtRehautSectorAnalyzer, GmtRehautPoseAnalyzer, GmtRoundMarkerAnalyzer,
GmtTwelveLandmarkAnalyzer) on the projected geometry.
"""
import numpy as np

# Master dial geometry (Gmt126710BlnrMaster, dial radii)
TICK_R = 0.925          # minute-track tick inner ends
ROUND_CENTER_R = 0.816
ROUND_OUTER_R = 0.088
TRI_CENTER_R, TRI_BASE_OUT, TRI_APEX_IN, TRI_HALF_BASE = 0.750, 0.152, 0.150, 0.123
HOURS_ROUND = [1, 2, 4, 5, 7, 8, 10, 11]


def camera(pitch_deg, yaw_deg, D):
    p, q = np.radians(pitch_deg), np.radians(yaw_deg)
    d = np.array([np.sin(q) * np.cos(p), np.sin(p), np.cos(q) * np.cos(p)])
    C = D * d / np.linalg.norm(d)
    fwd = -C / np.linalg.norm(C)
    upw = np.array([0.0, 1.0, 0.0])
    right = np.cross(fwd, upw); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    return C, fwd, right, up, D


def project(P, cam):
    C, fwd, right, up, f = cam
    X = np.atleast_2d(P) - C
    z = X @ fwd
    return np.stack([f * (X @ right) / z, f * (X @ up) / z], axis=1)


def ring(r, z, n=1440):
    t = np.linspace(0, 2 * np.pi, n, endpoint=False)
    # clock angle t: 0 at 12, clockwise -> x = sin t, y = cos t
    return np.stack([r * np.sin(t), r * np.cos(t), np.full(n, z)], axis=1)


def fit_ellipse(pts):
    """Least-squares conic (smallest singular vector, normalised coordinates). Exact for points on a conic.
    Returns centre, semi-axes (a >= b), major-axis angle (rad, image u axis towards v)."""
    m = pts.mean(axis=0); sc = np.sqrt(((pts - m) ** 2).sum(axis=1).mean())
    q = (pts - m) / sc
    x, y = q[:, 0], q[:, 1]
    Dm = np.stack([x * x, x * y, y * y, x, y, np.ones_like(x)], axis=1)
    A, B, Cc, Dd, E, F = np.linalg.svd(Dm, full_matrices=False)[2][-1]
    M = np.array([[A, B / 2], [B / 2, Cc]])
    c = np.linalg.solve(2 * M, [-Dd, -E])
    Fc = F + 0.5 * (Dd * c[0] + E * c[1])
    ev, evec = np.linalg.eigh(M)
    axes = np.sqrt(-Fc / ev) * sc
    i_major = int(np.argmax(axes))
    ang = np.arctan2(evec[1, i_major], evec[0, i_major])
    return c * sc + m, axes.max(), axes.min(), ang


class Polar:
    """A star-shaped closed curve about a centre, as radius against image clock angle (sorted once)."""
    def __init__(self, curve, centre):
        d = curve - centre
        th = np.arctan2(d[:, 0], d[:, 1]); rr = np.hypot(d[:, 0], d[:, 1])
        o = np.argsort(th); th, rr = th[o], rr[o]
        self.th = np.concatenate([th - 2 * np.pi, th, th + 2 * np.pi]); self.rr = np.concatenate([rr, rr, rr])
    def at(self, ang_img):
        a = (np.asarray(ang_img) + np.pi) % (2 * np.pi) - np.pi
        return np.interp(a, self.th, self.rr)


def polar_radius_at(curve, centre, ang_img):
    return Polar(curve, centre).at(ang_img)


def kasa(pts):
    x, y = pts[:, 0], pts[:, 1]
    A = np.stack([x, y, np.ones_like(x)], axis=1)
    b = -(x * x + y * y)
    s = np.linalg.lstsq(A, b, rcond=None)[0]
    cx, cy = -s[0] / 2, -s[1] / 2
    return np.array([cx, cy]), np.sqrt(cx * cx + cy * cy - s[2])


def measure(pitch, yaw, D=15.0, w0=0.06, alpha=60.0, hm=0.02, lip=0.0):
    cam = camera(pitch, yaw, D)
    h = w0 * np.tan(np.radians(alpha))
    inner = project(ring(1.0, 0.0), cam)
    outer = project(ring(1.0 + w0, h), cam)
    # Dial outline as the app fits it (the dial edge / rehaut foot).
    c, a_ax, b_ax, ang = fit_ellipse(inner)
    ratio = b_ax / a_ax
    # Watch orientation from the 60 tick, as the app does.
    t60 = project(np.array([[0.0, TICK_R, 0.0]]), cam)[0]
    roll = np.arctan2(t60[0] - c[0], t60[1] - c[1])
    # Optional lip: the visible outer boundary is the nearer (to the centre) of the rehaut top and the lip edge.
    if lip > 0:
        lipc = project(ring(1.0 + w0, h + lip), cam)
    out = {}
    P_in, P_out = Polar(inner, c), Polar(outer, c)
    P_lip = Polar(lipc, c) if lip > 0 else None
    def width_at(clock_deg):
        a = roll + np.radians(np.asarray(clock_deg, dtype=float))
        s_out = P_out.at(a)
        if P_lip is not None: s_out = np.minimum(s_out, P_lip.at(a))
        return s_out - P_in.at(a)
    # Sector widths: median over +-18 deg in 2 deg steps (GmtRehautSectorAnalyzer).
    for name, cd in (('w12', 0), ('w3', 90), ('w6', 180), ('w9', 270)):
        out[name] = float(np.median(np.maximum(0.0, width_at(cd + np.arange(-18, 19, 2)))))
    w12, w3, w6, w9 = out['w12'], out['w3'], out['w6'], out['w9']
    out['V'] = (w12 - w6) / (w12 + w6) if w12 + w6 > 0 else np.nan
    out['H'] = (w3 - w9) / (w3 + w9) if w3 + w9 > 0 else np.nan
    mean4 = (w12 + w3 + w6 + w9) / 4
    out['minmean'] = min(w12, w3, w6, w9) / mean4 if mean4 > 0 else np.nan
    # Global first-harmonic (GmtRehautPoseAnalyzer), watch frame.
    th = np.radians(np.arange(0, 360, 2))
    ws = np.maximum(0.0, width_at(np.degrees(th)))
    m = ws.mean(); cc = 2 * np.mean(ws * np.cos(th)); ss = 2 * np.mean(ws * np.sin(th))
    out['g_mean'] = m; out['g_harm'] = np.hypot(cc, ss) / m
    out['g_minmean'] = (m - np.hypot(cc, ss)) / m
    out['g_widest'] = np.degrees(np.arctan2(ss, cc)) % 360
    # Dial ellipse (planar cue).
    out['ell_ratio'] = ratio
    out['ell_tilt'] = np.degrees(np.arccos(min(1.0, ratio)))
    minor_img = ang + np.pi / 2
    minor_clock = (np.degrees(np.arctan2(np.cos(minor_img), np.sin(minor_img))) - np.degrees(roll)) % 180
    out['ell_minor_clock'] = minor_clock
    out['roll_deg'] = np.degrees(roll)
    # DialFrame rect (GmtRoundMarkerAnalyzer.DialFrame.rect).
    ct, st = np.cos(ang), np.sin(ang)
    rr = np.sqrt(a_ax * b_ax)
    def rect(P2):
        d = P2 - c
        u = ct * d[:, 0] + st * d[:, 1]; v = -st * d[:, 0] + ct * d[:, 1]
        return np.stack([u / a_ax * rr, v / b_ax * rr], axis=1)
    # Round-marker offsets (fraction of diameter, + clockwise) after the squash is undone.
    for hr in HOURS_ROUND:
        t = np.radians(hr * 30)
        cen = np.array([ROUND_CENTER_R * np.sin(t), ROUND_CENTER_R * np.cos(t)])
        tt = np.linspace(0, 2 * np.pi, 180, endpoint=False)
        outline = np.stack([cen[0] + ROUND_OUTER_R * np.cos(tt), cen[1] + ROUND_OUTER_R * np.sin(tt), np.full(180, hm)], axis=1)
        mc, mr = kasa(rect(project(outline, cam)))
        tb = np.radians(hr * 30 - 6); ta = np.radians(hr * 30 + 6)
        ticks = rect(project(np.array([[TICK_R * np.sin(tb), TICK_R * np.cos(tb), 0], [TICK_R * np.sin(ta), TICK_R * np.cos(ta), 0]]), cam))
        mid = ticks.mean(axis=0); u = ticks[1] - ticks[0]; u /= np.linalg.norm(u)
        out[f'off{hr}'] = float((mc - mid) @ u / (2 * mr))
    # 12 triangle: gap (normalised by width) and axis rotation vs the 59-01 chord, in the image.
    base_r = TRI_CENTER_R + TRI_BASE_OUT; apex_r = TRI_CENTER_R - TRI_APEX_IN
    L = np.array([-TRI_HALF_BASE, base_r, hm]); Rr = np.array([TRI_HALF_BASE, base_r, hm]); Ap = np.array([0, apex_r, hm])
    t59, t01 = np.radians(-6), np.radians(6)
    pts = project(np.array([L, Rr, Ap, [TICK_R * np.sin(t59), TICK_R * np.cos(t59), 0], [0, TICK_R, 0], [TICK_R * np.sin(t01), TICK_R * np.cos(t01), 0]]), cam)
    pl, pr, pa, p59, p60, p01 = pts
    base = pr - pl; wbase = np.linalg.norm(base); nb = np.array([-base[1], base[0]]) / wbase
    if nb @ (p60 - pl) < 0: nb = -nb
    out['gap'] = float(((p60 - pl) @ nb) / wbase)
    chord = p01 - p59; cn = np.array([-chord[1], chord[0]]); cn /= np.linalg.norm(cn)
    axis = (pl + pr) / 2 - pa; axis /= np.linalg.norm(axis)
    if axis @ cn < 0: cn = -cn
    out['rot'] = float(np.degrees(np.arctan2(cn[0] * axis[1] - cn[1] * axis[0], cn @ axis)))
    return out


TRUE_GAP = (TICK_R - (TRI_CENTER_R + TRI_BASE_OUT)) / (2 * TRI_HALF_BASE)
