"""Real genuine photos: rehaut asymmetry vs independent dial-plane pose (research, 2026-09-29).

Inputs: results/real_genuine_raw.csv (tools/desktop-harness/drivers/RehautPose.java) and the app's
round-marker offsets from the harness batch runs (paths given on the command line).
Writes results/real_genuine_pose.csv.
"""
import csv, glob, math, sys, collections
import numpy as np

ROUND_CENTER_R = 0.816
TICK_R = 0.925 - 0.010   # as used by GmtRoundMarkerAnalyzer.seeds for the 60 tick


def master(h, rho):
    t = math.radians(h * 30)
    return np.array([math.sin(t) * rho, -math.cos(t) * rho])   # +x -> 3, +y -> 6 (image-like)


def affine_pose(src, dst):
    S = np.hstack([np.array(src), np.ones((len(src), 1))])
    A, res, *_ = np.linalg.lstsq(S, np.array(dst), rcond=None)
    L = A[:2].T                                    # image = L @ master + t
    U, s, Vt = np.linalg.svd(L)
    ratio = s[1] / s[0]
    v = Vt[1]                                      # master direction that is compressed (x->3, y->6)
    clock = math.degrees(math.atan2(v[0], -v[1])) % 180
    resid = np.sqrt(((S @ A - np.array(dst)) ** 2).sum(axis=1).mean()) / math.sqrt(s[0] * s[1])
    return ratio, math.degrees(math.acos(min(1.0, ratio))), clock, resid


def fl(x):
    try: return float(x)
    except Exception: return float('nan')


def load_offsets(paths):
    off = collections.defaultdict(dict)
    for p in paths:
        for f in glob.glob(p):
            for r in csv.DictReader(open(f)):
                if r['found'] == 'true' and r['stable'] == 'true' and r['offset'] not in ('', 'NaN'):
                    off[r['path'].split('/')[-1] + '|' + r['path'].split('/')[-2]][int(r['hour'])] = fl(r['offset'])
    return off


def parallax_fit(offs):
    """off(h) = c + A cos(30h) + B sin(30h)  (A: yaw term, B: pitch term; see the write-up)."""
    hs = sorted(offs)
    if len(hs) < 5: return float('nan'), float('nan'), float('nan')
    X = np.array([[1, math.cos(math.radians(30 * h)), math.sin(math.radians(30 * h))] for h in hs])
    y = np.array([offs[h] for h in hs])
    coef, *_ = np.linalg.lstsq(X, y, rcond=None)
    r = y - X @ coef
    return coef[1], coef[2], float(np.sqrt((r ** 2).sum() / max(1, len(hs) - 3)))


def source(fn):
    f = fn.lower()
    for k in ('phillips', 'wos_cpo', 'rolex_official', 'bw_', 'elegantswiss'):
        if k in f: return {'bw_': 'bobs', 'wos_cpo': 'wos', 'rolex_official': 'render'}.get(k, k)
    return 'reddit'


if __name__ == '__main__':
    rows = list(csv.DictReader(open('results/real_genuine_raw.csv')))
    offs = load_offsets(sys.argv[1:])
    out = []
    for r in rows:
        if r.get('s_valid') != 'true' or not r.get('markers'): continue
        mk = {}
        for t in r['markers'].split(';'):
            if t:
                h, x, y, rad = t.split(':'); mk[int(h)] = (fl(x), fl(y))
        o = dict(file=r['file'], name=r['file'].split('/')[-1], src=source(r['file']), app_pose=r['app_pose'],
                 r_px=fl(r['r_px']), n_markers=len(mk))
        rpx = fl(r['r_px'])
        for k in ('w12', 'w3', 'w6', 'w9'): o[k + '_r'] = fl(r[k]) / rpx
        o['V'] = fl(r['s_V']); o['H'] = fl(r['s_H']); o['minmean'] = fl(r['s_minmean'])
        o['edge_ratio'] = fl(r['edge_ratio']); o['gap'] = fl(r['gap']); o['rot'] = fl(r['rot_axis'])
        if len(mk) >= 5:
            src = [master(h, ROUND_CENTER_R) for h in mk]; dst = [mk[h] for h in mk]
            o['aff8_ratio'], o['aff8_tilt'], o['aff8_minor'], o['aff8_resid'] = affine_pose(src, dst)
            if r['t60x'] not in ('', 'NaN'):
                src2 = src + [master(0, TICK_R)]; dst2 = dst + [(fl(r['t60x']), fl(r['t60y']))]
                o['aff9_ratio'], o['aff9_tilt'], o['aff9_minor'], o['aff9_resid'] = affine_pose(src2, dst2)
        key = r['file'].split('/')[-1] + '|' + r['file'].split('/')[-2]
        if key in offs:
            o['par_A'], o['par_B'], o['par_rms'] = parallax_fit(offs[key]); o['n_off'] = len(offs[key])
        out.append(o)
    keys = sorted({k for o in out for k in o}, key=lambda k: (k not in ('name', 'src', 'app_pose'), k))
    with open('results/real_genuine_pose.csv', 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=keys); w.writeheader(); w.writerows(out)
    print(len(out), 'photos with valid sectors and markers')
