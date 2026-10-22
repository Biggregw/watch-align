"""Competing marker-measurement methods, computed from identical raw measurements (ArchMeasure records).

Every method reads the same outer-metal centre of each marker (MetalFit, production) and differs only in the REFERENCE
it is measured against. Coordinates are canonical: the dial plane registered by the printed minute track (H), R units,
x right, y down. Readings are returned in % R (tangential: + = clockwise; radial: + = outward) and degrees (tilt).

  P    production (ClearlyOff 1.6.x): sideways against the hour line from the minute ticks through the registered
       centre; in/out against the ring of the OTHER markers (yellow reference); tilt = MetalFit tilt (against the
       ring-rotated yellow) minus the deviation of the hour-line pair.
  C    chord: sideways against the straight line through the inner ends of the two opposite hour ticks (does not use
       the registered centre); radial as F.
  F    fixed reference: centre minus the genuine model position at the reference pose (absolute, dial frame).
  FP   F minus the parallax of a raised marker: k * tan(tilt) along the far-side direction, both from H (k: effective
       marker height / R, fitted on OTHER watches).
  LA   production yellow residual: centre minus the yellow reference (the Alpha94 ring of the OTHER markers, from
       Alpha94's own edges).
  LR   leave-one-out ring from the SAME outer-metal centres: a robust similarity (translation, rotation, scale) fitted to
       the other eligible round markers and batons, the marker measured against its prediction.
  LB   as LR with a redescending (Tukey biweight) fit, so one strongly displaced marker cannot move the ring that the
       OTHER markers are measured against (LR uses production's Huber weights, which only bound its pull).
  LT   as LR with translation and rotation only (scale fixed by the minute track).
Ring level (one per photo): the similarity of all eligible markers against the fixed reference; its translation minus
the parallax prediction is the marker set's placement on the dial.
"""
from __future__ import annotations

import math

import numpy as np

from tools.image_archive import eligibility as el

METHODS = ('P', 'C', 'F', 'FP', 'LA', 'LR', 'LB', 'LT')
RING_SHAPES = ('round', 'baton')


def tangent(h):
    a = math.radians(30 * (h % 12))
    return np.array([math.cos(a), math.sin(a)])


def radial(h):
    a = math.radians(30 * (h % 12))
    return np.array([math.sin(a), -math.cos(a)])


def cpt(r, a):
    return np.array([r * math.sin(a), -r * math.cos(a)])


def pose(H):
    """(tilt rad, unit far-side direction) of the dial plane from canonical->image H"""
    H = np.array(H, float).reshape(3, 3)
    H = H / H[2, 2]
    x0, y0 = H[0, 2], H[1, 2]
    J = np.array([[H[0, 0] - x0 * H[2, 0], H[0, 1] - x0 * H[2, 1]], [H[1, 0] - y0 * H[2, 0], H[1, 1] - y0 * H[2, 1]]])
    s = np.linalg.svd(J, compute_uv=False)
    t = math.acos(min(1.0, s[1] / s[0]))
    p = np.array([H[2, 0], H[2, 1]])
    n = np.linalg.norm(p)
    return t, (p / n if n > 0 else np.zeros(2))


def status(m):
    """the archive eligibility status of one ArchMeasure marker (eligibility.status_from_evidence)"""
    cov = m.get('cov')
    ev = {'present': True, 'credible': bool(m.get('cred')), 'reliable': bool(m.get('rel')),
          'withheld': None if m.get('cred') else (m.get('gate') or m.get('state') or 'withheld'),
          'cover': cov, 'partly_hidden': cov is not None and cov < 0.5, 'hand_confirmed': bool(m.get('hand')),
          'soft': m.get('unc') or None}
    return el.status_from_evidence(ev)[0]


def similarity(rows, rpx, iters=10):
    """robust (Huber, 1 px) similarity fit: rows [(ref_x, ref_y, dx, dy)] -> (tx, ty, s, phi) as production fitRing"""
    if len(rows) < 3:
        return None
    A, b = [], []
    for x, y, dx, dy in rows:
        A += [[1, 0, x, -y], [0, 1, y, x]]
        b += [dx, dy]
    A, b = np.array(A), np.array(b)
    w = np.ones(len(b))
    sol = None
    for _ in range(iters):
        W = np.sqrt(w)
        sol = np.linalg.lstsq(A * W[:, None], b * W, rcond=None)[0]
        r = np.abs(b - A @ sol) * rpx
        w = np.where(r <= 1, 1.0, 1.0 / np.maximum(r, 1e-9))
    return sol


# Tukey biweight tuning (R): about 4.7 x the genuine per-marker noise (sd ~0.0009 R, ring-relative)
BIWEIGHT_C = 0.0045


def biweight(rows, rpx, iters=20, c=BIWEIGHT_C):
    """similarity fit with Tukey biweight on each marker's 2-D residual, started from the Huber fit"""
    sol = similarity(rows, rpx)
    if sol is None:
        return None
    A, b = [], []
    for x, y, dx, dy in rows:
        A += [[1, 0, x, -y], [0, 1, y, x]]
        b += [dx, dy]
    A, b = np.array(A), np.array(b)
    for _ in range(iters):
        r = (b - A @ sol).reshape(-1, 2)
        u = np.linalg.norm(r, axis=1) / c
        wm = np.where(u < 1, (1 - u ** 2) ** 2, 0.0)
        if (wm > 0).sum() < 4:
            return sol
        W = np.sqrt(np.repeat(wm, 2))
        sol = np.linalg.lstsq(A * W[:, None], b * W, rcond=None)[0]
    return sol


def translation_rotation(rows, rpx, iters=10):
    """as similarity() with the scale fixed at 0"""
    if len(rows) < 3:
        return None
    A, b = [], []
    for x, y, dx, dy in rows:
        A += [[1, 0, -y], [0, 1, x]]
        b += [dx, dy]
    A, b = np.array(A), np.array(b)
    w = np.ones(len(b))
    for _ in range(iters):
        W = np.sqrt(w)
        s3 = np.linalg.lstsq(A * W[:, None], b * W, rcond=None)[0]
        r = np.abs(b - A @ s3) * rpx
        w = np.where(r <= 1, 1.0, 1.0 / np.maximum(r, 1e-9))
    return np.array([s3[0], s3[1], 0.0, s3[2]])


def predict(sol, ref):
    tx, ty, s, ph = sol
    return np.array([tx + s * ref[0] - ph * ref[1], ty + s * ref[1] + ph * ref[0]])


def chord_side(rec, h, c):
    """signed distance (R) of centre c from the line through the inner ends of the hour ticks h and h+6 (+ = clockwise)"""
    ticks = rec.get('ticks')
    lines = rec.get('lines') or {}
    if not ticks:
        return None
    a1, a2 = lines.get(str(h % 12)), lines.get(str((h + 6) % 12))
    if a1 is None or a2 is None:
        return None
    rin = [t[1] for t in ticks if t[2] == 0 and t[1] is not None]
    if len(rin) < 20:
        return None
    rt = float(np.median(rin))
    p1, p2 = cpt(rt, a1), cpt(rt, a2)
    u = (p1 - p2) / np.linalg.norm(p1 - p2)          # from the opposite tick towards this hour's tick (outward)
    n = np.array([-u[1], u[0]])                       # clockwise side of the outward line
    if n @ tangent(h) < 0:
        n = -n
    return float((c - p2) @ n)


def measure(rec, k=None, ring_without_triangle=True):
    """{hour: {'shape', 'status', method: (tang %R, radial %R, tilt deg)}} and the photo's ring-level estimate.

    rec: one ArchMeasure JSON record (needs ticks for C; a base record carries them). k: parallax constant (R per unit
    tan(tilt)); None leaves FP out.
    """
    rpx = rec.get('rpx') or float('nan')
    tilt, far = pose(rec['H']) if rec.get('H') else (float('nan'), np.zeros(2))
    para = (k * math.tan(tilt) * far) if k is not None and math.isfinite(tilt) else None
    lines = rec.get('lines') or {}
    marks = {}
    for m in rec.get('markers') or ():
        if m.get('cO') is None or m.get('fix') is None or None in m['cO'] or None in m['fix']:
            continue
        marks[int(m['h'])] = m
    st = {h: status(m) for h, m in marks.items()}
    elig = [h for h in marks if st[h] == el.ELIGIBLE]
    d = {h: np.array(marks[h]['cO']) - np.array(marks[h]['fix']) for h in marks}
    ring_hours = [h for h in elig if marks[h]['shape'] in RING_SHAPES or (not ring_without_triangle)]
    out = {}
    for h, m in marks.items():
        c = np.array(m['cO'])
        t_, r_ = tangent(h), radial(h)
        loo = m.get('loo')
        tilt_abs = None
        if m['shape'] != 'round' and m.get('ft') is not None:
            tilt_abs = m['ft'] + (math.degrees(loo[3]) if loo and loo[3] is not None else 0.0)
        res = {'shape': m['shape'], 'status': st[h]}
        res['P'] = (m.get('side'), m.get('inout'), m.get('tilt'))
        cs = chord_side(rec, h, c)
        res['C'] = (100 * cs if cs is not None else None, 100 * float(d[h] @ r_), None)
        # tilt against the fixed reference: the hour-line pair deviation is removed for the absolute frame
        dev = None
        a1, a2 = lines.get(str(h % 12)), lines.get(str((h + 6) % 12))
        if a1 is not None and a2 is not None:
            p1, p2 = cpt(0.93, a1), cpt(0.93, a2)
            n1, n2 = cpt(0.93, math.radians(30 * (h % 12))), cpt(0.93, math.radians(30 * ((h + 6) % 12)))
            dev = math.degrees(((math.atan2(p2[1] - p1[1], p2[0] - p1[0]) - math.atan2(n2[1] - n1[1], n2[0] - n1[0])
                                 + math.pi / 2) % math.pi) - math.pi / 2)
        res['F'] = (100 * float(d[h] @ t_), 100 * float(d[h] @ r_), tilt_abs)
        if para is not None:
            e = d[h] - para
            res['FP'] = (100 * float(e @ t_), 100 * float(e @ r_), tilt_abs)
        if m.get('cG') is not None and None not in m['cG']:
            e = c - np.array(m['cG'])
            res['LA'] = (100 * float(e @ t_), 100 * float(e @ r_), m.get('ft'))
        others = [g for g in ring_hours if g != h]
        rows = [(marks[g]['fix'][0], marks[g]['fix'][1], d[g][0], d[g][1]) for g in others]
        for name, fit in (('LR', similarity), ('LB', biweight), ('LT', translation_rotation)):
            if len(rows) >= 5:
                sol = fit(rows, rpx)
                if sol is not None:
                    e = d[h] - predict(sol, m['fix'])
                    res[name] = (100 * float(e @ t_), 100 * float(e @ r_),
                                 tilt_abs - math.degrees(sol[3]) if tilt_abs is not None else None)
        res['dev'] = dev
        out[h] = res
    ring = None
    rows = [(marks[g]['fix'][0], marks[g]['fix'][1], d[g][0], d[g][1]) for g in ring_hours]
    if len(rows) >= 5:
        sol = similarity(rows, rpx)
        ring = {'t': sol[:2] * 100, 's': sol[2] * 100, 'rot_deg': math.degrees(sol[3]), 'n': len(rows),
                'tilt_deg': math.degrees(tilt), 'far': far,
                't_minus_parallax': (sol[:2] - para) * 100 if para is not None else None}
    return out, ring
