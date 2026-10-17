"""Ground-truth benchmark for marker-position methods (research only; never used by the app).

  python3 benchmark.py dump.jsonl image_root out.csv [workers]

For every marker of every photo, the marker ITSELF is moved inside the photo by a known amount (image content
resampled through the dial pose, blended into the untouched dial with a feathered edge a few pixels outside the
marker's metal), and each method is asked again. The change it reports must equal the movement made. Movements:
0 (blend-only control), 1, 2, 3, 5 px in seeded random directions, and for batons / the triangle also 1 degree turns.

Error per test = the largest difference, over the marker's outline points, between the reported movement and the
true movement (px). A method may withhold a marker; withheld tests are counted separately, never as errors.

Methods:
  edges   overlay_gap.fit_marker with this photo's marker size (from its cleanest markers of each shape)
  centre  overlay_gap.centroid_fit (centre of the lume, area-checked)
  dense   analysis by synthesis: this photo's own edge profile (lume -> separator -> ring -> dial, learned from its
          clean markers as a function of signed distance to the lume outline) rendered on the known shape and
          registered to every pixel of the marker with a robust loss (hands, glints and lit ring patches that do not
          fit carry ~no weight); exhaustive coarse search, then local refinement
  agree   edges and centre must agree within 1 px (outline-point max), else withheld; reports their mean
"""
import csv
import hashlib
import json
import math
import os
import sys
from multiprocessing import Pool

import cv2
import numpy as np
from scipy.optimize import minimize

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import overlay_gap as og  # noqa: E402

# (px, degrees; degrees for batons / the triangle only). Fractional shifts expose pixel-locking.
MOVES = [(0.0, 0.0), (0.5, 0.0), (1.25, 0.0), (2.0, 1.0), (3.0, -1.0), (5.0, 0.0), (0.0, 1.0)]


# ----------------------------------------------------------------------------------------------- geometry helpers
def apply_T(T, P):
    c, t, th = T
    R = np.array([[math.cos(th), -math.sin(th)], [math.sin(th), math.cos(th)]])
    return c + (P - c) @ R.T + t


def inv_H(H):
    return np.linalg.inv(np.asarray(H, float).reshape(3, 3))


def to_canon(Hinv, px):
    p = np.c_[px, np.ones(len(px))] @ Hinv.T
    return p[:, :2] / p[:, 2:3]


def move_marker(img, H, m, rpx, v, dth, insets):
    """The photo with this marker moved by v (canonical) and turned by dth about its centre; everything more than a
    few px outside its metal is untouched, with a 3 px feathered blend."""
    y = np.asarray(m['yellow'], float)
    c = og.template(m, insets).mean(axis=0) if m['shape'] == 'round' else y.mean(axis=0)
    r1 = 3.0 / rpx
    r2 = 6.0 / rpx
    ext = float(np.max(np.hypot(*(y - c).T))) + r2 + float(np.hypot(*v)) + 0.01
    box = og.project(H, np.array([[c[0] + ext * math.cos(a), c[1] + ext * math.sin(a)] for a in np.linspace(0, 2 * math.pi, 24)]))
    x0, y0 = np.floor(box.min(axis=0)).astype(int) - 2
    x1, y1 = np.ceil(box.max(axis=0)).astype(int) + 2
    x0, y0 = max(x0, 0), max(y0, 0)
    x1, y1 = min(x1, img.shape[1] - 1), min(y1, img.shape[0] - 1)
    XX, YY = np.meshgrid(np.arange(x0, x1 + 1), np.arange(y0, y1 + 1))
    pix = np.c_[XX.ravel(), YY.ravel()].astype(float)
    Hinv = inv_H(H)
    q = to_canon(Hinv, pix)
    # distance outside the metal outline, in the MOVED frame (the marker's new place) and the old one: blend over both
    def outside(qq):
        if m['shape'] == 'round':
            rr = float(np.mean(np.hypot(*(y - y.mean(axis=0)).T)))
            return np.hypot(qq[:, 0] - y.mean(axis=0)[0], qq[:, 1] - y.mean(axis=0)[1]) - rr
        cont = y.astype(np.float32).reshape(-1, 1, 2)
        return -np.array([cv2.pointPolygonTest(cont, (float(a), float(b)), True) for a, b in qq])
    R = np.array([[math.cos(-dth), -math.sin(-dth)], [math.sin(-dth), math.cos(-dth)]])
    src = c + (q - c - v) @ R.T                        # where the content now at q came from
    d = np.minimum(outside(q), outside(src))
    w = np.clip((r2 - d) / (r2 - r1), 0, 1)
    sp = og.project(H, src)
    # Lanczos resampling: a different interpolator from the one any method uses (bilinear), so it cannot hide bias
    moved = sample_lanczos(img, sp)
    out = img.copy()
    blk = out[y0:y1 + 1, x0:x1 + 1].ravel()
    ok = np.isfinite(moved)
    blk[ok] = w[ok] * moved[ok] + (1 - w[ok]) * blk[ok]
    out[y0:y1 + 1, x0:x1 + 1] = blk.reshape(y1 - y0 + 1, x1 - x0 + 1)
    return out, c


def sample_lanczos(img, xy):
    n = len(xy)
    W = 1024
    rows = (n + W - 1) // W
    x = np.full(rows * W, -1e6, np.float32)
    y = np.full(rows * W, -1e6, np.float32)
    x[:n] = xy[:, 0]
    y[:n] = xy[:, 1]
    return cv2.remap(img, x.reshape(rows, W), y.reshape(rows, W), cv2.INTER_LANCZOS4,
                     borderMode=cv2.BORDER_CONSTANT, borderValue=np.nan).ravel()[:n]


# ----------------------------------------------------------------------------------------------- dense method
class SD:
    """Signed distance (canonical, + outside) to a template outline, for any canonical points, via a fine map."""

    def __init__(self, poly, shape, rpx, margin):
        self.poly = np.asarray(poly, float)
        self.round = shape == 'round'
        self.c = self.poly.mean(axis=0)
        if self.round:
            self.r = float(np.mean(np.hypot(*(self.poly - self.c).T)))
            return
        self.res = 0.25 / rpx
        lo = self.poly.min(axis=0) - margin
        hi = self.poly.max(axis=0) + margin
        self.lo = lo
        n = np.ceil((hi - lo) / self.res).astype(int) + 1
        msk = np.zeros((n[1], n[0]), np.uint8)
        pp = (self.poly - lo) / self.res
        cv2.fillPoly(msk, [np.round(pp * 16).astype(np.int32).reshape(-1, 1, 2)], 1, shift=4)
        din = cv2.distanceTransform(msk, cv2.DIST_L2, cv2.DIST_MASK_PRECISE)
        dout = cv2.distanceTransform(1 - msk, cv2.DIST_L2, cv2.DIST_MASK_PRECISE)
        self.map = ((dout - din) * self.res).astype(np.float32)
        G = np.mgrid[0:n[1], 0:n[0]][::-1].reshape(2, -1).T * self.res + lo          # (N, 2) canonical x, y
        vd = np.min(np.hypot(G[:, None, 0] - self.poly[None, :, 0], G[:, None, 1] - self.poly[None, :, 1]), axis=1)
        self.vmap = vd.reshape(n[1], n[0]).astype(np.float32)

    def __call__(self, P, vertex=False):
        if self.round:
            d = np.hypot(P[..., 0] - self.c[0], P[..., 1] - self.c[1]) - self.r
            return (d, np.full(d.shape, 9.0)) if vertex else d
        g = (P - self.lo) / self.res
        sh = P.shape[:-1]
        gx = g[..., 0].reshape(-1).astype(np.float32)
        gy = g[..., 1].reshape(-1).astype(np.float32)
        W = 1024
        rows = (len(gx) + W - 1) // W
        X = np.full(rows * W, -1e6, np.float32)
        Y = np.full(rows * W, -1e6, np.float32)
        X[:len(gx)] = gx
        Y[:len(gy)] = gy
        d = cv2.remap(self.map, X.reshape(rows, W), Y.reshape(rows, W), cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE).ravel()[:len(gx)].reshape(sh)
        if not vertex:
            return d
        v = cv2.remap(self.vmap, X.reshape(rows, W), Y.reshape(rows, W), cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE).ravel()[:len(gx)].reshape(sh)
        return d, v


def dense_setup(img, H, m, rpx, insets):
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
    d_out = inset + 7.0 / rpx
    sd = SD(poly, shape, rpx, d_out + 10.0 / rpx)
    return dict(shape=shape, poly=poly, c=c, inset=inset, lume=lv[0], dial=lv[1], d_in=d_in, d_out=d_out, sd=sd, rpx=rpx)


def dense_pixels(img, H, S, step_px):
    """Fixed evaluation pixels (canonical grid) around the marker at the overlay position, and their normalised
    brightness (0 dial, 1 lume)."""
    poly, c, rpx = S['poly'], S['c'], S['rpx']
    half = float(np.max(np.hypot(*(poly - c).T))) + S['d_out'] + 2.0 / rpx
    g = np.arange(-half, half + 1e-12, step_px / rpx)
    X, Y = np.meshgrid(c[0] + g, c[1] + g)
    P = np.c_[X.ravel(), Y.ravel()]
    d, vd = S['sd'](P, vertex=True)
    keep = (d > -S['d_in']) & (d < S['d_out']) & (vd > og.CORNER_R)
    P = P[keep]
    v = og.sample(img, og.project(H, P))
    ok = np.isfinite(v)
    return P[ok], (v[ok] - S['dial']) / (S['lume'] - S['dial'])


def learn_profile(samples):
    """samples: list of (sd_px, value) arrays from clean markers -> median profile on 0.25 px bins."""
    sd = np.concatenate([s[0] for s in samples])
    val = np.concatenate([s[1] for s in samples])
    bins = np.arange(-8.0, 16.0 + 1e-9, 0.25)
    idx = np.digitize(sd, bins)
    prof = np.full(len(bins) + 1, np.nan)
    for k in range(len(prof)):
        sel = idx == k
        if sel.sum() >= 20:
            prof[k] = np.median(val[sel])
    centres = np.r_[bins[0] - 0.125, (bins[:-1] + bins[1:]) / 2, bins[-1] + 0.125]
    ok = np.isfinite(prof)
    return centres[ok], prof[ok]


def model_vals(S, prof, P, T):
    c, t, th = T
    R = np.array([[math.cos(-th), -math.sin(-th)], [math.sin(-th), math.cos(-th)]])
    Q = c + (P - c - t) @ R.T
    d = S['sd'](Q) * S['rpx']
    return np.interp(d, prof[0], prof[1])


def dense_fit(img, H, m, rpx, insets, prof, S=None):
    S = S or dense_setup(img, H, m, rpx, insets)
    if S is None:
        return {'status': 'marker not distinguishable from the dial'}
    rot = S['shape'] != 'round'
    c = S['c']
    kappa = 0.2

    def loss(P, v, q):
        r = v - model_vals(S, prof, P, (c, np.array(q[:2]) / rpx, math.radians(q[2]) if rot else 0.0))
        return float(np.sum(r * r / (r * r + kappa * kappa)))

    Pc, vc = dense_pixels(img, H, S, 1.0)
    if len(Pc) < 50:
        return {'status': 'too close to the photo edge'}
    shifts = np.arange(-7.0, 7.0 + 1e-9, 1.0)
    angs = np.arange(-2.0, 2.0 + 1e-9, 1.0) if rot else np.array([0.0])
    grid = [(x, y, a) for a in angs for x in shifts for y in shifts]
    L = np.array([loss(Pc, vc, q) for q in grid])
    best = grid[int(np.argmin(L))]
    Pf, vf = dense_pixels(img, H, S, 0.5)
    res = minimize(lambda q: loss(Pf, vf, q if rot else (q[0], q[1], 0.0)), np.array(best if rot else best[:2], float),
                   method='Nelder-Mead', options={'xatol': 0.01, 'fatol': 1e-4, 'maxiter': 400})
    q = res.x if rot else np.r_[res.x, 0.0]
    # ambiguity: best coarse loss among poses at least 2 px (far end) away from the optimum, relative to the optimum
    halfLen = float(np.max(np.hypot(*(S['poly'] - c).T))) * rpx
    far = np.array([math.hypot(g[0] - q[0], g[1] - q[1]) + abs(math.radians(g[2] - q[2])) * halfLen for g in grid])
    Lopt = loss(Pc, vc, q)
    alt = float(L[far >= 2.0].min()) if np.any(far >= 2.0) else float('inf')
    r = vf - model_vals(S, prof, Pf, (c, q[:2] / rpx, math.radians(q[2]) if rot else 0.0))
    inl = float(np.mean(np.abs(r) < 0.25))
    hour = m['hour']
    a = math.radians(hour * 30.0)
    er = np.array([math.sin(a), -math.cos(a)]) if hour % 12 else np.array([0.0, -1.0])
    et = np.array([math.cos(a), math.sin(a)]) if hour % 12 else np.array([1.0, 0.0])
    sep = (alt - Lopt) / max(alt, 1e-9)
    ok = inl >= 0.6 and sep >= 0.05
    return {'status': 'measured' if ok else 'not enough of the marker matches its known appearance (%.0f%% fit, separation %.2f)' % (100 * inl, sep),
            'radial_px': float(q[:2] @ er), 'tangential_px': float(q[:2] @ et), 'rotation_deg': float(q[2]) if rot else float('nan'),
            'inliers': inl, 'separation': sep, '_T': (c.copy(), q[:2] / rpx, math.radians(q[2]) if rot else 0.0)}


def photo_profile(img, H, d, insets, base):
    """Edge profile of this photo from its clean markers (no hand near, centre method measured), at their measured
    positions."""
    samples = []
    for m in d['markers']:
        b = base.get(m['hour'], {})
        bc = b.get('centre', {})
        if not m['hand_clean'] or bc.get('status') != 'measured':
            continue
        S = dense_setup(img, H, m, d['rpx'], insets)
        if S is None:
            continue
        P, v = dense_pixels(img, H, S, 0.5)
        c, t, th = bc['_T']
        R = np.array([[math.cos(-th), -math.sin(-th)], [math.sin(-th), math.cos(-th)]])
        Q = c + (P - c - t) @ R.T
        samples.append((S['sd'](Q) * d['rpx'], v))
    if len(samples) < 3:
        return None
    return learn_profile(samples)


# ----------------------------------------------------------------------------------------------- runner
def outline_err(T_base, T_mod, c_inj, v, dth, outline):
    """Largest outline-point difference (canonical) between the reported change and the true movement."""
    true = c_inj + (outline - c_inj) @ np.array([[math.cos(dth), -math.sin(dth)], [math.sin(dth), math.cos(dth)]]).T + v
    got = apply_T(T_mod, outline) - apply_T(T_base, outline) + outline
    return float(np.max(np.hypot(*(got - true).T)))


def run_methods(img, H, d, m, insets, sizes, prof, lprm=None):
    rpx = d['rpx']
    out = {}
    sz = sizes.get(m['shape'])
    try:
        out['edges'] = og.fit_marker(img, H, m, rpx, insets, None if sz is None else sz[:2])
    except Exception as e:
        out['edges'] = {'status': 'error %s' % e}
    try:
        out['centre'] = og.centroid_fit(img, H, m, rpx, insets)
    except Exception as e:
        out['centre'] = {'status': 'error %s' % e}
    import layered
    try:
        out['layered'] = layered.result(m, layered.setup(img, H, m, rpx, insets), (lprm or {}).get(m['shape']), rpx)
    except Exception as e:
        out['layered'] = {'status': 'error %s' % e}
    e, c = out['edges'], out['centre']
    if e.get('status') == 'measured' and c.get('status') == 'measured':
        y = np.asarray(m['yellow'], float)
        dis = outline_err(e['_T'], c['_T'], y.mean(axis=0), np.zeros(2), 0.0, y) * rpx
        if dis <= 1.0:
            ce, te, he = e['_T']
            cc, tc, hc = c['_T']
            # mean of the two outline placements, expressed about the centre method's centre
            te2 = apply_T(e['_T'], cc[None])[0] - cc
            out['agree'] = {'status': 'measured', '_T': (cc, (te2 + tc) / 2, (he + hc) / 2)}
        else:
            out['agree'] = {'status': 'edges and centre disagree by %.1f px' % dis}
    else:
        out['agree'] = {'status': 'needs both edges and centre'}
    return out


def work(args):
    line, root = args
    d = json.loads(line)
    rows = []
    if not d.get('pose'):
        return rows
    img = og.load_like_app(os.path.join(root, d['path']))
    if img is None or img.shape[1] != d['w'] or img.shape[0] != d['h']:
        return rows
    H, rpx = d['H'], d['rpx']
    insets = og.model_insets(d['model'])
    # photo-level: marker sizes (edges) from a first pass, edge profile (dense) from the centre method's clean markers
    first = [og.fit_marker(img, H, m, rpx, insets) for m in d['markers']]
    sizes = og.photo_sizes(first, d['markers'])
    base_c = {m['hour']: {'centre': og.centroid_fit(img, H, m, rpx, insets)} for m in d['markers']}
    import layered
    _, lprm = layered.measure_photo(img, H, d, insets, {h: v['centre'] for h, v in base_c.items()})
    prof = None
    for m in d['markers']:
        base = run_methods(img, H, d, m, insets, sizes, prof, lprm)
        outline = np.asarray(m['yellow'], float)
        seed = int(hashlib.sha256(('%s|%d' % (d['photo_id'], m['hour'])).encode()).hexdigest()[:8], 16)
        rng = np.random.default_rng(seed)
        moves = [(0.0, 0.0)] if os.environ.get('BENCH_BASE_ONLY') else MOVES
        if os.environ.get('BENCH_BASE_ONLY'):
            for meth in ('edges', 'centre', 'layered', 'agree'):
                b = base.get(meth, {})
                row = {'photo_id': d['photo_id'], 'watch': d.get('watch', ''), 'model': d['model'], 'hour': m['hour'],
                       'shape': m['shape'], 'rpx': '%.0f' % rpx, 'hand_clean': int(m['hand_clean']),
                       'app_outcome': m['outcome'], 'method': meth, 'move_px': 'base', 'move_deg': '',
                       'base_status': b.get('status', '')[:60], 'status': b.get('status', '')[:60]}
                if b.get('status') == 'measured':
                    row['base_gap_px'] = '%.3f' % (outline_err((np.zeros(2), np.zeros(2), 0.0), b['_T'], np.zeros(2), np.zeros(2), 0.0, outline) * rpx)
                rows.append(row)
            continue
        for k, (mag, deg) in enumerate(moves):
            if m['shape'] == 'round':
                if mag == 0 and deg != 0:
                    continue
                deg = 0.0
            ang = rng.uniform(0, 2 * math.pi)
            v = (mag / rpx) * np.array([math.cos(ang), math.sin(ang)])
            dth = math.radians(deg)
            img2, c_inj = move_marker(img, H, m, rpx, v, dth, insets)
            mod = run_methods(img2, H, d, m, insets, sizes, prof, lprm)
            for meth in ('edges', 'centre', 'layered', 'agree'):
                b, r = base.get(meth, {}), mod.get(meth, {})
                row = {'photo_id': d['photo_id'], 'watch': d.get('watch', ''), 'model': d['model'], 'hour': m['hour'],
                       'shape': m['shape'], 'rpx': '%.0f' % rpx, 'hand_clean': int(m['hand_clean']),
                       'app_outcome': m['outcome'], 'method': meth, 'move_px': mag, 'move_deg': deg,
                       'base_status': b.get('status', '')[:60], 'status': r.get('status', '')[:60]}
                if b.get('status') == 'measured':
                    row['base_gap_px'] = '%.3f' % (outline_err((np.zeros(2), np.zeros(2), 0.0), b['_T'], np.zeros(2), np.zeros(2), 0.0, outline) * rpx)
                if b.get('status') == 'measured' and r.get('status') == 'measured':
                    row['err_px'] = '%.3f' % (outline_err(b['_T'], r['_T'], c_inj, v, dth, outline) * rpx)
                rows.append(row)
    return rows


def main():
    dump, root, out = sys.argv[1], sys.argv[2], sys.argv[3]
    workers = int(sys.argv[4]) if len(sys.argv) > 4 else 4
    lines = [l for l in open(dump) if l.strip()]
    cols = ['photo_id', 'watch', 'model', 'hour', 'shape', 'rpx', 'hand_clean', 'app_outcome', 'method', 'move_px',
            'move_deg', 'base_status', 'status', 'base_gap_px', 'err_px']
    with open(out, 'w', newline='') as fo, Pool(workers) as pool:
        w = csv.DictWriter(fo, fieldnames=cols)
        w.writeheader()
        for rows in pool.imap_unordered(work, [(l, root) for l in lines]):
            for r in rows:
                w.writerow(r)
            fo.flush()
            if rows:
                print('done', rows[0]['photo_id'], flush=True)


if __name__ == '__main__':
    main()
