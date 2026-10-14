#!/usr/bin/env python3
"""RESEARCH ONLY - lume-first outer-metal reconstruction of the 12 o'clock triangle, compared with the app's FIXED
genuine reference in the SAME coordinate system.

Input: <id>_export.json and <id>_rect.png written by tools/desktop-harness/drivers/TriangleLumeExport.java (the unchanged
Alpha107 pipeline: frozen dial/minute-track pose H, ring adjustment fitted without the 12, and the exact yellow polygon
ExpectedMarkerOutline.polygon). The rectified crop maps to canonical dial units exactly:
    X = cx - half + (u + 0.5) * s,   Y = cy - half + (v + 0.5) * s,   s = 2 * half / size   (dial radius = 1, 12 up)

Method (one bounded experiment; nothing here is fitted towards the yellow reference):
 1. Luminous fill: brightest connected component inside a search corridor (the reference triangle dilated by a margin;
    the corridor only limits WHERE we look). Threshold = dial + f * (lume - dial), f in {0.5, 0.6, 0.7}.
 2. Lume side lines: robust line fit to the fill contour on the middle 15-85 % of each side (tips/corners excluded).
 3. Outer metal OBSERVATIONS: along the normal of each lume side, the outermost bright-metal -> dark-dial fall within a
    bounded distance; kept only with adequate contrast and a dark run after it (no isolated glints).
 4. Bevel thickness: per side, the widest well-supported value (85th percentile of the longest consistent run).
 5. Hold-out test: thickness from ONE side predicts the observed outer edge of the OTHER sides (never scored against
    the evidence that produced it).
 6. Actual outer boundary per side: observed (cyan) where >= MIN_COVER of the side is observed, else inferred from the
    lume line + anchor thickness (dashed cyan); tips and corners are withheld (grey).
 7. Comparison with the fixed reference: signed normal offsets per side (+ = metal outside the reference = extra),
    side-angle differences, and the rigid translation (tangential / radial) + uniform growth that explain the three
    side offsets; rotation = mean side-angle difference. Orange / magenta only over OBSERVED spans.
Usage: lume_first_compare.py EXPORT_DIR ID OUT_DIR [--original path] [--label text]
"""
import argparse, json, math
from pathlib import Path
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFont

MIN_COVER = 0.40          # observed fraction of a side's middle span needed to call that side "observed"
SPAN = (0.15, 0.85)       # middle part of each side; tips and corners are never measured
N_SAMPLES = 61
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
def font(sz, bold=False):
    try: return ImageFont.truetype(BOLD if bold else FONT, sz)
    except OSError: return ImageFont.load_default()

YELLOW, PINK, CYAN, GREY, ORANGE, MAGENTA, GREEN = (255,214,10), (255,105,200), (40,220,255), (150,150,160), (255,128,31), (230,75,220), (70,230,95)

class Geo:
    def __init__(s, ex):
        c = ex['crop']; s.cx, s.cy, s.half, s.size = c['cx'], c['cy'], c['half'], c['size']
        s.s = 2 * s.half / s.size; s.R = ex['dial_R_px']; s.nat = s.s * s.R     # native image px per crop px
    def to_px(s, P): P = np.asarray(P, float); return np.c_[(P[:,0]-(s.cx-s.half))/s.s-0.5, (P[:,1]-(s.cy-s.half))/s.s-0.5]
    def to_can(s, Q): Q = np.asarray(Q, float); return np.c_[s.cx-s.half+(Q[:,0]+0.5)*s.s, s.cy-s.half+(Q[:,1]+0.5)*s.s]

def sides_of(tri):
    """Expected triangle in crop px -> dict side -> (P0, P1) ordered left->right / top->apex; apex = lowest vertex."""
    tri = np.asarray(tri, float); apex = int(np.argmax(tri[:,1])); others = [i for i in range(3) if i != apex]
    tl, tr = sorted(others, key=lambda i: tri[i,0])
    return {'top': (tri[tl], tri[tr]), 'left': (tri[tl], tri[apex]), 'right': (tri[tr], tri[apex])}, tri.mean(0)

def frame(P0, P1, cen):
    d = (P1 - P0) / np.linalg.norm(P1 - P0); n = np.array([d[1], -d[0]])
    if np.dot(n, (P0 + P1) / 2 - cen) < 0: n = -n
    return d, n

def bilinear(g, x, y):
    h, w = g.shape; x = np.clip(x, 0, w-1.001); y = np.clip(y, 0, h-1.001); x0 = np.floor(x).astype(int); y0 = np.floor(y).astype(int)
    fx, fy = x-x0, y-y0
    return g[y0,x0]*(1-fx)*(1-fy)+g[y0,x0+1]*fx*(1-fy)+g[y0+1,x0]*(1-fx)*fy+g[y0+1,x0+1]*fx*fy

def fit_line(pts, iters=3, tol=1.5):
    p = np.asarray(pts, float); keep = np.ones(len(p), bool)
    for _ in range(iters):
        q = p[keep]; c = q.mean(0); u, sv, vt = np.linalg.svd(q - c); d = vt[0]; n = np.array([-d[1], d[0]])
        r = np.abs((p - c) @ n); new = r <= max(tol, 2.5 * np.median(r[keep]) + 1e-9)
        if new.sum() < 5 or (new == keep).all(): break
        keep = new
    return c, d, keep

def line_point_at(c, d, P0, P1, t):
    """Point on line (c,d) nearest to the point at fraction t along segment P0->P1."""
    q = P0 + t * (P1 - P0); return c + np.dot(q - c, d) * d

def analyse(g, geo, ex, f=0.6, margin_R=0.03, pct=85, anchor=None):
    tri = geo.to_px(ex['expected']); S, cen = sides_of(tri); m = margin_R / geo.s
    # corridor = expected triangle dilated by margin (search region only)
    mask_roi = np.zeros(g.shape, np.uint8); cv2.fillPoly(mask_roi, [np.round(tri).astype(np.int32)], 1)
    k = int(round(2*m)) | 1; roi = cv2.dilate(mask_roi, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k)))
    inner = cv2.erode(mask_roi, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k)))
    ring = cv2.dilate(roi, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k))) - roi
    L = float(np.median(g[inner > 0])); D = float(np.median(g[ring > 0]))
    T = D + f * (L - D)
    bw = np.uint8((g > T) & (roi > 0)); n, lab, st, _ = cv2.connectedComponentsWithStats(bw, 8)
    best = max(range(1, n), key=lambda i: ((lab == i) & (inner > 0)).sum()) if n > 1 else None
    out = dict(T=T, L=L, D=D, f=f, sides={}, merged=False)
    if best is None: out['fail'] = 'no luminous component'; return out
    lume = np.uint8(lab == best); out['lume'] = lume
    out['merged'] = bool(lume.sum() > 1.02 * mask_roi.sum())       # fill as large as the whole metal outline: unreliable
    cs, _ = cv2.findContours(lume, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE); cont = max(cs, key=cv2.contourArea).reshape(-1, 2).astype(float)
    out['contour'] = cont
    maxw = 0.028 / geo.s
    for name, (P0, P1) in S.items():
        d, nrm = frame(P0, P1, cen); Lside = np.linalg.norm(P1 - P0)
        rel = cont - P0; tt = rel @ d / Lside; dist = rel @ nrm
        # contour points belonging to this side: middle span, near this side, and nearer to it than to the others
        others = [frame(*S[o], cen) + (S[o][0],) for o in S if o != name]
        near = np.ones(len(cont), bool)
        for d2, n2, Q0 in others: near &= np.abs(dist) <= np.abs((cont - Q0) @ n2) + 1e-6
        sel = (tt > SPAN[0]) & (tt < SPAN[1]) & (np.abs(dist) < m) & near
        if sel.sum() < 8: out['sides'][name] = dict(fail='too few lume contour points'); continue
        c, dl, keep = fit_line(cont[sel])
        if np.dot(dl, d) < 0: dl = -dl
        nl = np.array([dl[1], -dl[0]]);
        if np.dot(nl, nrm) < 0: nl = -nl
        rs = np.arange(-0.004 / geo.s, maxw, 0.25)
        obs = []
        for t in np.linspace(SPAN[0], SPAN[1], N_SAMPLES):
            q = line_point_at(c, dl, P0, P1, t)
            prof = bilinear(g, q[0] + rs * nl[0], q[1] + rs * nl[1])
            i0 = int(np.argmin(np.abs(rs)))
            # lume crossing near 0 (refine): last index where prof >= T before falling
            j = i0
            while j + 1 < len(rs) and prof[j] >= T: j += 1
            r_l = rs[j]
            post = prof[j:];
            if len(post) < 8: obs.append((t, None, None, q)); continue
            # metal band = brightest point after the dark separator that follows the luminous fill
            sep_w = max(3, int(2.5 / geo.nat / 0.25)); smin = j + int(np.argmin(prof[j:j + sep_w]))
            win = max(4, int(0.022 / geo.s / 0.25)); seg = prof[smin:j + win]
            if len(seg) < 2: obs.append((t, None, None, q)); continue
            pk = smin + int(np.argmax(seg)); Pm = prof[pk]; contrast = Pm - D
            if Pm - prof[smin] < 6: pk = j                      # no metal band distinguishable from the separator
            lvl = D + 0.5 * (Pm - D)
            # outermost fall below lvl after the metal peak that stays dark for >= 1.5 native px
            run = max(2, int(math.ceil(1.5 / geo.nat / 0.25))); r_o = None
            for i in range(pk, len(rs) - run):
                if prof[i] >= lvl and prof[i+1] < lvl and (prof[i+1:i+1+run] < lvl).all():
                    a = (prof[i] - lvl) / max(1e-9, prof[i] - prof[i+1]); r_o = rs[i] + a * 0.25; break
            ok = r_o is not None and contrast >= 15 and pk > j and (r_o - r_l) * geo.nat >= 0.7
            obs.append((t, r_o if ok else None, r_l, q))
        w = np.array([(o[1] - o[2]) if o[1] is not None else np.nan for o in obs])
        out['sides'][name] = dict(P0=P0, P1=P1, d=d, n=nrm, Lside=Lside, lume_c=c, lume_d=dl, lume_n=nl, obs=obs, w=w)
    # thickness per side: widest well-supported value on the longest consistent run
    for name, sd in out['sides'].items():
        if 'fail' in sd: continue
        w = sd['w']; good = np.isfinite(w)
        sd['cover'] = float(good.mean())
        if good.sum() < 6: sd['w_est'] = np.nan; continue
        med = np.nanmedian(w); cons = good & (np.abs(w - med) * geo.nat <= 1.5)
        best_run, cur, bs = (0, 0), 0, 0
        for i, v in enumerate(cons):
            cur = cur + 1 if v else 0
            if cur > best_run[0]: best_run = (cur, i)
        lo = best_run[1] - best_run[0] + 1; seg = w[lo:best_run[1]+1]
        sd['run'] = best_run[0] / len(w)
        sd['w_est'] = float(np.nanpercentile(seg, pct)) if best_run[0] >= 6 else np.nan
    return out

def outer_line(sd, w):
    """Inferred outer line: lume line shifted outward by w (crop px)."""
    return sd['lume_c'] + w * sd['lume_n'], sd['lume_d']

def observed_line(sd):
    pts = [o[3] + o[1] * sd['lume_n'] for o in sd['obs'] if o[1] is not None]
    if len(pts) < 6: return None
    c, d, _ = fit_line(pts, tol=1.0)
    return c, d if np.dot(d, sd['d']) > 0 else -d

def holdout(res, geo):
    rows = []
    for a, sa in res['sides'].items():
        if 'fail' in sa or not np.isfinite(sa.get('w_est', np.nan)): continue
        for b, sb in res['sides'].items():
            if b == a or 'fail' in sb: continue
            c, d = outer_line(sb, sa['w_est']); err = []
            for t, r_o, r_l, q in sb['obs']:
                if r_o is None: continue
                p = q + r_o * sb['lume_n']; err.append(np.dot(p - c, sb['lume_n']))
            if len(err) >= 6:
                e = np.array(err) * geo.nat; rows.append((a, b, len(err), float(np.median(e)), float(np.median(np.abs(e)))))
    return rows

def compare(res, geo, ex, anchor_w):
    """Per side: actual outer line (observed if covered, else inferred) vs the fixed expected side."""
    rep = {}
    for name, sd in res['sides'].items():
        if 'fail' in sd: rep[name] = dict(state='withheld', why=sd['fail']); continue
        ol = observed_line(sd) if sd['cover'] >= MIN_COVER else None
        if ol is not None: state = 'observed'; c, d = ol
        elif np.isfinite(anchor_w): state = 'inferred'; c, d = outer_line(sd, anchor_w)
        else: rep[name] = dict(state='withheld', why='no observed outer edge and no thickness anchor'); continue
        P0, P1, n = sd['P0'], sd['P1'], sd['n']
        offs = []
        for t in np.linspace(SPAN[0], SPAN[1], 15):
            e = P0 + t * (P1 - P0)
            # intersection of the expected normal through e with the actual line
            denom = np.dot(n, np.array([-d[1], d[0]]));
            k = np.dot(c - e, np.array([-d[1], d[0]])) / denom if abs(denom) > 1e-9 else np.nan
            offs.append(k)
        offs = np.array(offs); L = sd['Lside'] * (SPAN[1] - SPAN[0])
        ang = math.degrees(math.atan2(offs[-1] - offs[0], L))      # + = actual turned outward towards the side's end
        rep[name] = dict(state=state, line=(c, d), offsets=offs, mean=float(np.mean(offs)), ang=ang, cover=sd['cover'])
    # rigid translation + uniform growth from the three side offsets (needs all three sides)
    if all(rep.get(k, {}).get('state') in ('observed', 'inferred') for k in ('top', 'left', 'right')):
        A = np.array([[*res['sides'][k]['n'], 1.0] for k in ('top', 'left', 'right')]); b = np.array([rep[k]['mean'] for k in ('top', 'left', 'right')])
        tx, ty, grow = np.linalg.solve(A, b)
        rep['_rigid'] = dict(tangential=tx, radial=-ty, grow=grow)   # crop px; radial + = towards the minute track (12 up)
    # rotation: a rigid rotation turns every side by the same angle (image coordinates, + = clockwise)
    angs = {}
    for k in ('top', 'left', 'right'):
        r = rep.get(k, {})
        if 'line' in r:
            d = r['line'][1]; de = res['sides'][k]['d']
            a = math.degrees(math.atan2(de[0]*d[1]-de[1]*d[0], np.dot(de, d))); angs[k] = a
    rep['_angles'] = angs
    if angs: rep['_rotation'] = float(np.mean(list(angs.values())))
    return rep

# ---------------------------------------------------------------------------------------------------------- drawing
def dashed(dr, a, b, col, w=2, dash=7, gap=5):
    a, b = np.asarray(a, float), np.asarray(b, float); L = np.linalg.norm(b - a); t = 0
    while t < L:
        e = min(L, t + dash); p = a + (b - a) * t / L; q = a + (b - a) * e / L; dr.line([tuple(p), tuple(q)], fill=col, width=w); t += dash + gap

def render(rgb, res, rep, geo, ex, scale, label, numbers):
    H, W = rgb.shape[:2]; img = Image.fromarray(rgb).resize((W * scale, H * scale), Image.Resampling.LANCZOS)
    ov = Image.new('RGBA', img.size, (0, 0, 0, 0)); dr = ImageDraw.Draw(ov)
    sc = lambda p: (float(p[0]) * scale + scale / 2, float(p[1]) * scale + scale / 2)
    # orange / magenta between the expected side and the OBSERVED actual line, middle span only
    for name, r in rep.items():
        if name.startswith('_') or r.get('state') != 'observed': continue
        sd = res['sides'][name]; P0, P1, n = sd['P0'], sd['P1'], sd['n']
        ts = np.linspace(SPAN[0], SPAN[1], len(r['offsets']))
        for i in range(len(ts) - 1):
            e0 = P0 + ts[i] * (P1 - P0); e1 = P0 + ts[i+1] * (P1 - P0); o0, o1 = r['offsets'][i], r['offsets'][i+1]
            col = ORANGE if (o0 + o1) > 0 else MAGENTA
            dr.polygon([sc(e0), sc(e1), sc(e1 + o1 * n), sc(e0 + o0 * n)], fill=col + (150,))
    tri = geo.to_px(ex['expected']); dr.line([sc(p) for p in np.vstack([tri, tri[:1]])], fill=YELLOW + (255,), width=2)
    if 'contour' in res: dr.line([sc(p) for p in np.vstack([res['contour'], res['contour'][:1]])], fill=PINK + (255,), width=1)
    for name, r in rep.items():
        if name.startswith('_'): continue
        sd = res['sides'].get(name, {})
        if r.get('state') == 'withheld' or 'P0' not in sd: continue
        c, d = r['line']; P0, P1 = sd['P0'], sd['P1']
        a = line_point_at(c, d, P0, P1, SPAN[0]); b = line_point_at(c, d, P0, P1, SPAN[1])
        if r['state'] == 'observed':
            # solid where an observation exists, dashed between observations
            for (t, ro, rl, q), nxt in zip(sd['obs'][:-1], sd['obs'][1:]):
                p = line_point_at(c, d, P0, P1, t); p2 = line_point_at(c, d, P0, P1, nxt[0])
                if ro is not None and nxt[1] is not None: dr.line([sc(p), sc(p2)], fill=CYAN + (255,), width=3)
                else: dashed(dr, sc(p), sc(p2), CYAN + (255,), 2, 4, 4)
            for t, ro, rl, q in sd['obs']:
                if ro is not None: x, y = sc(q + ro * sd['lume_n']); dr.ellipse([x-1.5, y-1.5, x+1.5, y+1.5], fill=GREEN + (255,))
        else:
            dashed(dr, sc(a), sc(b), CYAN + (255,), 2)
        # withheld ends (tips and corners): grey dashed continuation of the line towards the reference vertices
        for t0, t1 in ((0.0, SPAN[0]), (SPAN[1], 1.0)):
            dashed(dr, sc(line_point_at(c, d, P0, P1, t0)), sc(line_point_at(c, d, P0, P1, t1)), GREY + (220,), 2, 3, 5)
    img = Image.alpha_composite(img.convert('RGBA'), ov).convert('RGB')
    head = 96 + 20 * len(numbers); canvas = Image.new('RGB', (img.width, img.height + head), (13, 20, 32)); canvas.paste(img, (0, head))
    d = ImageDraw.Draw(canvas); d.text((12, 8), label, font=font(19, True), fill=(255, 255, 255))
    d.text((12, 36), 'yellow: fixed genuine reference   pink: luminous fill   cyan: actual outer metal (solid observed, dashed inferred)', font=font(12), fill=(200, 210, 225))
    d.text((12, 54), 'grey: tips/corners withheld   green: outer-edge observations   orange/magenta: observed metal outside/inside reference', font=font(12), fill=(200, 210, 225))
    for i, line in enumerate(numbers): d.text((12, 78 + 20 * i), line, font=font(14), fill=(235, 238, 245))
    return canvas, img

def describe(rep, geo):
    nat = geo.nat; R = geo.R; lines = []
    for k in ('top', 'left', 'right'):
        r = rep.get(k, {})
        if r.get('state') in ('observed', 'inferred'):
            lines.append(f"{k:5}: {r['state']:8} cover {100*r['cover']:3.0f}%  offset {r['mean']*nat:+5.2f} px ({100*r['mean']*geo.s:+.2f}%R)  angle {r['ang']:+5.2f} deg")
        else: lines.append(f"{k:5}: withheld ({r.get('why', '')})")
    if '_rigid' in rep:
        g = rep['_rigid']
        lines.append(f"shift {g['tangential']*nat:+.2f} px tangential (+ right), {g['radial']*nat:+.2f} px radial (+ towards track); outline growth {g['grow']*nat:+.2f} px  [R = {R:.0f} px]")
    if '_rotation' in rep: lines.append(f"rotation (mean side-angle difference) {rep['_rotation']:+.2f} deg (+ clockwise)")
    return lines

def main():
    ap = argparse.ArgumentParser(); ap.add_argument('export_dir'); ap.add_argument('id'); ap.add_argument('out_dir')
    ap.add_argument('--label', default=None); ap.add_argument('--original', default=None)
    a = ap.parse_args(); E = Path(a.export_dir); O = Path(a.out_dir); O.mkdir(parents=True, exist_ok=True)
    ex = json.load(open(E / f'{a.id}_export.json')); bgr = cv2.imread(str(E / f'{a.id}_rect.png'))
    rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB); g = cv2.GaussianBlur(cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY).astype(float), (0, 0), 0.6)
    geo = Geo(ex)
    # ---- baseline run
    res = analyse(g, geo, ex)
    ho = holdout(res, geo)
    ws = {k: v.get('w_est', np.nan) for k, v in res['sides'].items() if 'fail' not in v}
    anchor = max((k for k in ws if np.isfinite(ws[k])), key=lambda k: res['sides'][k].get('run', 0), default=None)
    anchor_w = ws[anchor] if anchor else np.nan
    rep = compare(res, geo, ex, anchor_w)
    # ---- sensitivity: threshold, thickness percentile, corridor margin, anchor side
    var = []
    for f in (0.5, 0.6, 0.7):
        for pct in (50, 85):
            for marg in (0.025, 0.035):
                r2 = analyse(g, geo, ex, f=f, margin_R=marg, pct=pct)
                w2 = {k: v.get('w_est', np.nan) for k, v in r2['sides'].items() if 'fail' not in v}
                for anc in [k for k in w2 if np.isfinite(w2[k])] or [None]:
                    rp = compare(r2, geo, ex, w2[anc] if anc else np.nan)
                    var.append(dict(f=f, pct=pct, margin=marg, anchor=anc, merged=r2['merged'], rep=rp))
    def spread(key):
        v = [x['rep'][key[0]][key[1]] for x in var if key[0] in x['rep'] and key[1] in x['rep'][key[0]]]
        return (min(v) * geo.nat, max(v) * geo.nat) if v else (np.nan, np.nan)
    def spread_side(k):
        v = [x['rep'][k]['mean'] for x in var if x['rep'].get(k, {}).get('state') in ('observed', 'inferred')]
        return (min(v) * geo.nat, max(v) * geo.nat) if v else (np.nan, np.nan)
    rot = [x['rep']['_rotation'] for x in var if '_rotation' in x['rep']]
    lines = describe(rep, geo)
    sens = [f"sensitivity over {len(var)} variants (threshold 0.5/0.6/0.7, thickness p50/p85, corridor 0.025/0.035 R, every anchor side):"]
    for k in ('top', 'left', 'right'):
        lo, hi = spread_side(k); sens.append(f"  {k:5} offset range {lo:+.2f} .. {hi:+.2f} px")
    for key, nm in ((('_rigid', 'tangential'), 'tangential'), (('_rigid', 'radial'), 'radial'), (('_rigid', 'grow'), 'growth')):
        lo, hi = spread(key); sens.append(f"  {nm:10} range {lo:+.2f} .. {hi:+.2f} px")
    if rot: sens.append(f"  rotation range {min(rot):+.2f} .. {max(rot):+.2f} deg")
    sens.append(f"  luminous fill merged with metal in {sum(x['merged'] for x in var)} / {len(var)} variants")
    hol = [f"hold-out: thickness from one side predicting another side's OBSERVED outer edge (median error / median |error|, native px):"]
    for aa, bb, n, med, mad in ho: hol.append(f"  {aa:5} -> {bb:5}  n={n:2d}  {med:+.2f} / {mad:.2f}")
    if not ho: hol.append('  not possible: fewer than two sides with a thickness estimate and observations')
    thick = 'thickness (native px): ' + ', '.join(f"{k} {v*geo.nat:.2f}" + (f" (run {100*res['sides'][k].get('run',0):.0f}%)") for k, v in ws.items() if np.isfinite(v)) + (f"; anchor = {anchor}" if anchor else '; no anchor')
    app = ex.get('finding', {}); a94 = ex.get('alpha94', {})
    appline = 'existing app: ' + '; '.join(m['text'].split('.')[0] + f" [{m['status']}]" for m in app.get('measures', [])) + f"  | Alpha94 tangential {a94.get('localTangentialPx', float('nan')):+.2f} px, radial {a94.get('localRadialPx', float('nan')):+.2f} px"
    label = a.label or a.id
    scale = max(1, int(round(1200 / rgb.shape[1])))
    canvas, big = render(rgb, res, rep, geo, ex, scale, label, lines + [thick])
    canvas.save(O / f'{a.id}_compare.png')
    # enlargements: top, left, right and tip
    tri = geo.to_px(ex['expected']); S, cen = sides_of(tri); zooms = []
    for name, pt in (('top', (S['top'][0] + S['top'][1]) / 2), ('left', (S['left'][0] + S['left'][1]) / 2), ('right', (S['right'][0] + S['right'][1]) / 2), ('tip', S['left'][1])):
        hw = 0.05 / geo.s * scale; x, y = pt * scale
        z = big.crop((int(x - hw), int(y - hw), int(x + hw), int(y + hw))).resize((380, 380), Image.Resampling.NEAREST)
        zz = Image.new('RGB', (380, 404), (13, 20, 32)); zz.paste(z, (0, 24)); ImageDraw.Draw(zz).text((8, 4), name, font=font(15, True), fill='white'); zooms.append(zz)
    zs = Image.new('RGB', (380 * 4 + 30, 404), (13, 20, 32))
    for i, z in enumerate(zooms): zs.paste(z, (i * 390, 0))
    zs.save(O / f'{a.id}_zoom.png')
    # side by side with the app's own (yellow-only) close-up
    appimg = E / f'{a.id}_app_closeup.png'
    if appimg.exists():
        ai = Image.open(appimg).convert('RGB'); h = 560; ai = ai.resize((h, h)); nb = big.resize((h, h))
        sb = Image.new('RGB', (2 * h + 20, h + 40), (13, 20, 32)); sb.paste(ai, (0, 40)); sb.paste(nb, (h + 20, 40)); dd = ImageDraw.Draw(sb)
        dd.text((8, 10), 'Alpha107 close-up (yellow only)', font=font(16, True), fill='white'); dd.text((h + 28, 10), 'lume-first reconstruction, same reference', font=font(16, True), fill='white')
        sb.save(O / f'{a.id}_vs_app.png')
    # original photograph overlay (reference and reconstruction projected through the frozen pose H)
    if a.original:
        orig = cv2.cvtColor(cv2.imread(a.original), cv2.COLOR_BGR2RGB); Hm = np.array(ex['H']).reshape(3, 3)
        def proj(P):
            P = np.asarray(P, float); q = np.c_[P, np.ones(len(P))] @ Hm.T; return q[:, :2] / q[:, 2:3]
        trc = proj(ex['expected']); x0, y0 = trc.min(0) - 25; x1, y1 = trc.max(0) + 25
        crop = Image.fromarray(orig[int(y0):int(y1), int(x0):int(x1)]); k2 = max(1, int(600 / crop.width)); crop = crop.resize((crop.width * k2, crop.height * k2), Image.Resampling.NEAREST)
        dr = ImageDraw.Draw(crop); off = lambda P: [((p[0] - int(x0)) * k2 + k2 / 2, (p[1] - int(y0)) * k2 + k2 / 2) for p in P]
        dr.line(off(np.vstack([trc, trc[:1]])), fill=YELLOW, width=2)
        for name, r in rep.items():
            if name.startswith('_') or r.get('state') not in ('observed', 'inferred'): continue
            sd = res['sides'][name]; c, d = r['line']
            pa = geo.to_can([line_point_at(c, d, sd['P0'], sd['P1'], SPAN[0]), line_point_at(c, d, sd['P0'], sd['P1'], SPAN[1])])
            pp = off(proj(pa))
            if r['state'] == 'observed': dr.line(pp, fill=CYAN, width=3)
            else: dashed(dr, pp[0], pp[1], CYAN, 2)
        if 'contour' in res: dr.line(off(proj(geo.to_can(res['contour']))), fill=PINK, width=1)
        crop.save(O / f'{a.id}_original_overlay.png')
    nat = geo.nat; rg = rep.get('_rigid', {})
    def rng(vals): vals = [v for v in vals if v == v]; return [min(vals), max(vals)] if vals else None
    metrics = dict(R=geo.R, merged=bool(res['merged']), fail=res.get('fail'),
                   tangential_px=rg.get('tangential', np.nan) * nat, radial_px=rg.get('radial', np.nan) * nat, growth_px=rg.get('grow', np.nan) * nat,
                   rotation_deg=rep.get('_rotation', np.nan),
                   sides={k: dict(state=rep.get(k, {}).get('state', 'withheld'), cover=rep.get(k, {}).get('cover', 0.0),
                                  offset_px=rep.get(k, {}).get('mean', np.nan) * nat, angle_deg=rep.get(k, {}).get('ang', np.nan),
                                  shape_angle_deg=(rep['_angles'][k] - rep['_rotation']) if k in rep.get('_angles', {}) and '_rotation' in rep else np.nan,
                                  thickness_px=res['sides'].get(k, {}).get('w_est', np.nan) * nat if 'fail' not in res['sides'].get(k, {'fail': 1}) else np.nan) for k in ('top', 'left', 'right')},
                   holdout=[dict(src=x[0], dst=x[1], n=x[2], median_px=x[3], mad_px=x[4]) for x in ho],
                   sens=dict(n=len(var), merged=sum(x['merged'] for x in var),
                             tangential_px=rng([x['rep']['_rigid']['tangential'] * nat for x in var if '_rigid' in x['rep']]),
                             radial_px=rng([x['rep']['_rigid']['radial'] * nat for x in var if '_rigid' in x['rep']]),
                             growth_px=rng([x['rep']['_rigid']['grow'] * nat for x in var if '_rigid' in x['rep']]),
                             rotation_deg=rng([x['rep']['_rotation'] for x in var if '_rotation' in x['rep']])),
                   app={m['name']: dict(value=m['value'], status=m['status']) for m in app.get('measures', [])}, app_status=app.get('status'))
    summary = dict(id=a.id, metrics=metrics, R=geo.R, native_px_per_crop_px=geo.nat, lines=lines, thickness=thick, holdout=ho, sensitivity=sens, app=appline,
                   rep={k: ({kk: (vv.tolist() if isinstance(vv, np.ndarray) else vv) for kk, vv in v.items() if kk not in ('line',)} if isinstance(v, dict) else v) for k, v in rep.items()})
    json.dump(summary, open(O / f'{a.id}_result.json', 'w'), indent=1, default=lambda o: float(o) if isinstance(o, (np.floating,)) else str(o))
    print(f'== {label}'); [print('  ' + l) for l in lines + [thick] + hol + sens + [appline]]

if __name__ == '__main__':
    main()
