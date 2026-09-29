"""Prototype: dial-plane pose (8-marker affine + index parallax) and per-measurement gating,
compared with the app's current global pose label on the genuine corpus (research, 2026-09-29).

python3 pose_policy.py <batch_dir> [<batch_dir> ...]
  batch_dir: Batch driver output (r*.csv with per-photo verdicts, r*_round.csv with round markers).
Reads results/real_genuine_raw.csv (RehautPose driver: marker centres, rehaut sectors, app pose).
Writes results/pose_policy_photos.csv, results/pose_policy_measurements.csv, results/pose_policy_summary.txt.
Nothing here changes the app.
"""
import csv, glob, math, sys, collections
import numpy as np
import pose_budget as PB

ROUND_R = 0.816
HOURS = [1, 2, 4, 5, 7, 8, 10, 11]
# Nominal camera for the budget: D = 10 dial radii (a phone ~15 cm from a 40 mm watch), applied
# index height 0.02 R (fits the Phillips parallax; see hm_est). Sensitivity: D = 6, hm = 0.03.
NOMINAL = dict(D=10.0, hm=0.02)
STRESS = dict(D=6.0, hm=0.03)
BUDGET_FRACTION = 0.5          # withhold when the pose could induce >= half of the CHECK level
# Affine confidence requirements.
MIN_MARKERS = 6
MIN_QUADRANTS = 4              # markers in each of the four quadrants (1-2, 4-5, 7-8, 10-11)
MAX_RESID = 0.006              # rms residual / dial radius; perspective at 10-15 deg from ~10 R leaves ~0.003-0.005
DROP_RESID = 0.003             # above this, try refitting without the single worst marker
MAX_SCALE_DEV = 0.10           # affine scale vs the fitted dial radius (gross mislabel/misfit only)
MAX_SQUASH_SD = 0.015          # jackknife sd of the squash vector (1 - ratio units); the rest is carried by tilt_hi

MEAS = {  # measurement -> (budget key(s), CHECK level, batch verdict column)
    'round_offset': (['round_off'], 0.15, 'round'),
    'round_size': (['round_size'], 0.12, 'round'),
    'gap12': (['gap12'], 0.024, 'gap_att'),
    'rot12': (['rot12'], 2.0, 'align_att'),
    'offcentre12': (['asym12'], 0.10, 'align_att'),
    'six': (['six_c', 'six_r'], (0.10, 2.0), 'six_att'),
    'side': (None, (0.10, 2.0), 'nine_att'),     # 3 or 9 by layout
}


def fl(x):
    try: return float(x)
    except Exception: return float('nan')


def master(h):
    t = math.radians(h * 30); return np.array([math.sin(t) * ROUND_R, -math.cos(t) * ROUND_R])


def affine(hs, pts):
    S = np.hstack([np.array([master(h) for h in hs]), np.ones((len(hs), 1))])
    A, *_ = np.linalg.lstsq(S, np.array(pts), rcond=None)
    L = A[:2].T
    U, s, Vt = np.linalg.svd(L)
    ratio = s[1] / s[0]
    v = Vt[1]                                  # compressed master direction (x -> 3, y -> 6)
    phi = math.degrees(math.atan2(v[0], -v[1])) % 180
    res = np.sqrt(((S @ A - np.array(pts)) ** 2).sum(axis=1).mean()) / math.sqrt(s[0] * s[1])
    return ratio, phi, res, math.sqrt(s[0] * s[1])


def squash_vec(ratio, phi):
    e = 1 - ratio; return np.array([e * math.cos(math.radians(2 * phi)), e * math.sin(math.radians(2 * phi))])


def dial_pose(markers, r_px):
    """markers: {hour: (x, y)} -> pose dict (tilt, tilt_hi, minor clock, confidence reasons)."""
    hs = sorted(markers)
    out = dict(n=len(hs), valid=False, why='')
    if len(hs) < 5: out['why'] = f'{len(hs)} markers'; return out
    ratio, phi, res, scale = affine(hs, [markers[h] for h in hs])
    dropped = ''
    if res > DROP_RESID and len(hs) > MIN_MARKERS:
        # One misplaced marker (a hand, glare, a lume/surround misfit): refit without the one whose
        # removal helps most, if that removes at least 40% of the residual.
        best = min(hs, key=lambda h: affine([k for k in hs if k != h], [markers[k] for k in hs if k != h])[2])
        hs2 = [k for k in hs if k != best]; fit2 = affine(hs2, [markers[h] for h in hs2])
        if fit2[2] <= 0.6 * res:
            hs = hs2; dropped = str(best); ratio, phi, res, scale = fit2
    sv = squash_vec(ratio, phi)
    jk = []
    for i in range(len(hs)):
        sub = hs[:i] + hs[i + 1:]
        r2, p2, *_ = affine(sub, [markers[h] for h in sub]); jk.append(squash_vec(r2, p2))
    jk = np.array(jk); n = len(hs)
    sd = float(math.sqrt((n - 1) / n * ((jk - jk.mean(axis=0)) ** 2).sum(axis=1).sum()))  # jackknife, vector norm
    quads = len({(h - 1) // 3 for h in hs})
    tilt = math.degrees(math.acos(min(1.0, ratio)))
    e_hi = min(0.5, (1 - ratio) + 2 * sd)
    out.update(n=len(hs), dropped=dropped, ratio=ratio, tilt=tilt, minor=phi, resid=res, scale=scale / r_px, squash_sd=sd, quadrants=quads,
               tilt_hi=math.degrees(math.acos(1 - e_hi)), tilt_lo=math.degrees(math.acos(1 - max(0.0, (1 - ratio) - 2 * sd))))
    why = []
    if n < MIN_MARKERS: why.append(f'{n} markers')
    if quads < MIN_QUADRANTS: why.append(f'{quads} quadrants')
    if res > MAX_RESID: why.append(f'resid {res:.3f}')
    if abs(scale / r_px - 1) > MAX_SCALE_DEV: why.append(f'scale {scale / r_px:.2f}')
    if sd > MAX_SQUASH_SD: why.append(f'squash sd {sd:.4f}')
    out['valid'] = not why; out['why'] = '; '.join(why)
    return out


def parallax(offs):
    hs = sorted(offs)
    if len(hs) < 5: return None
    X = np.array([[1, math.cos(math.radians(30 * h)), math.sin(math.radians(30 * h))] for h in hs])
    y = np.array([offs[h] for h in hs])
    coef, *_ = np.linalg.lstsq(X, y, rcond=None)
    r = y - X @ coef
    sig = math.sqrt((r ** 2).sum() / max(1, len(hs) - 3))
    cov = sig ** 2 * np.linalg.inv(X.T @ X)
    return dict(A=coef[1], B=coef[2], sA=math.sqrt(cov[1, 1]), sB=math.sqrt(cov[2, 2]), n=len(hs))


def camera_side(pose, par):
    """Camera azimuth (clock deg, 0 = towards 12, 90 = towards 3) with the parallax sign, or None."""
    if par is None: return None, 0.0
    ux, uy = -par['A'], par['B']                  # camera direction (x -> 3, y -> 12)
    ph = math.radians(pose['minor'])
    d = np.array([math.sin(ph), math.cos(ph)])   # minor axis as clock direction (x -> 3, y -> 12)
    proj = ux * d[0] + uy * d[1]
    s = math.sqrt((par['sA'] * d[0]) ** 2 + (par['sB'] * d[1]) ** 2)
    z = abs(proj) / s if s > 0 else 0.0
    az = pose['minor'] if proj > 0 else (pose['minor'] + 180) % 360
    return (az if z >= 2.0 else None), z


_cache = {}
def predicted(tilt, az, cam):
    key = (round(tilt, 1), round(az) % 360, cam['D'], cam['hm'])
    if key not in _cache:
        m = PB.measure(tilt, az, cam['D'], cam['hm']); f = PB.measure(0.0, 0.0, cam['D'], cam['hm'])
        _cache[key] = {k: (m[k] - (f[k] if k not in ('round_off', 'round_size') else 0.0)) for k in m}
    return _cache[key]


def worst(tilt, az_candidates, keys, cam):
    return {k: max(abs(predicted(tilt, a, cam)[k]) for a in az_candidates) for k in keys}


def gate(pose, az, cam, side_layout):
    """Per-measurement: predicted pose-induced error (at tilt_hi) as a fraction of its CHECK level."""
    if not pose['valid']: return None
    cands = [az] if az is not None else [pose['minor'], (pose['minor'] + 180) % 360]
    keys = ['round_off', 'round_size', 'gap12', 'rot12', 'asym12', 'six_c', 'six_r', 'three_c', 'three_r', 'nine_c', 'nine_r']
    w = worst(pose['tilt_hi'], cands, keys, cam)
    side = 'three' if side_layout == 'DATE_AT_9' else 'nine'
    frac = dict(round_offset=w['round_off'] / 0.15, round_size=w['round_size'] / 0.12, gap12=w['gap12'] / 0.024,
                rot12=w['rot12'] / 2.0, offcentre12=w['asym12'] / 0.10,
                six=max(w['six_c'] / 0.10, w['six_r'] / 2.0),
                side=max(w[side + '_c'] / 0.10, w[side + '_r'] / 2.0))
    return frac


def _read(files):
    """Batch shards: only shard 0 carries the header."""
    hdr, rows = None, []
    for f in sorted(files):
        lines = list(csv.reader(open(f)))
        if not lines: continue
        if lines[0] and lines[0][0] == 'path': hdr = lines[0]; lines = lines[1:]
        rows += lines
    return [dict(zip(hdr, l)) for l in rows] if hdr else []


def load_batch(dirs):
    main, rnd = {}, collections.defaultdict(dict)
    for d in dirs:
        for r in _read([f for f in glob.glob(d + '/[rs]?.csv')]): main[r['path']] = r
        for r in _read(glob.glob(d + '/[rs]?_round.csv')): rnd[r['path']][int(r['hour'])] = r
    return main, rnd


JUDGED = ('CLEAR', 'CHECK', 'STRONG')


def main():
    dirs = sys.argv[1:]
    bmain, brnd = load_batch(dirs)
    raw = list(csv.DictReader(open('results/real_genuine_raw.csv')))
    photos, meas = [], []
    for r in raw:
        p = r['file']
        if p not in bmain: continue
        b = bmain[p]
        mk = {}
        src_col = 'markers_all' if r.get('markers_all') is not None else 'markers'
        for t in (r.get(src_col) or '').split(';'):
            if t:
                f_ = t.split(':'); mk[int(f_[0])] = (fl(f_[1]), fl(f_[2]))
        rpx = fl(r['r_px'])
        pose = dial_pose(mk, rpx) if mk and math.isfinite(rpx) else dict(n=len(mk), valid=False, why='no dial fit')
        offs = {h: fl(v['offset']) for h, v in brnd.get(p, {}).items()
                if v['found'] == 'true' and v['stable'] == 'true' and v['offset'] not in ('', 'NaN')}
        par = parallax(offs)
        az, z = camera_side(pose, par) if pose.get('valid') else (None, 0.0)
        layout = b.get('layout', '')
        frac = gate(pose, az, NOMINAL, layout)
        frac_s = gate(pose, az, STRESS, layout)
        hm_est = float('nan')
        if par and pose.get('valid') and pose['tilt'] > 6:
            hm_est = math.hypot(par['A'], par['B']) * 2 * 0.088 / math.tan(math.radians(pose['tilt']))
        o = dict(photo=p.split('/')[-2] + '/' + p.split('/')[-1] if 'bw_' not in p else p.split('/')[-1],
                 app_pose=b['pose'], rehaut_minmean=fl(r['s_minmean']), rehaut_H=fl(r['s_H']), rehaut_V=fl(r['s_V']),
                 n_markers=pose['n'], affine_valid=pose['valid'], affine_why=pose['why'],
                 tilt=pose.get('tilt', float('nan')), tilt_hi=pose.get('tilt_hi', float('nan')), minor_clock=pose.get('minor', float('nan')),
                 squash_sd=pose.get('squash_sd', float('nan')), resid=pose.get('resid', float('nan')), quadrants=pose.get('quadrants', 0),
                 par_A=par['A'] if par else float('nan'), par_B=par['B'] if par else float('nan'), side_z=z,
                 camera_az=az if az is not None else '', hm_est=hm_est, layout=layout)
        # Current verdicts per measurement.
        cur = {}
        rh = brnd.get(p, {})
        cur['round_offset'] = [v['att'] for v in rh.values() if v['found'] == 'true']
        cur['gap12'] = b['gap_att']; cur['rot12'] = b['align_att']; cur['six'] = b['six_att']; cur['side'] = b['nine_att']
        poorpose = b['pose'] in ('RETAKE', 'UNASSESSABLE')
        for m in ('round_offset', 'gap12', 'rot12', 'six', 'side'):
            if m == 'round_offset':
                judged = sum(a in JUDGED for a in cur[m]); flagged = sum(a in ('CHECK', 'STRONG') for a in cur[m]); found = len(cur[m])
                cur_state = 'judged' if judged else (('withheld-pose' if poorpose else 'withheld-other') if found else 'not measured')
            else:
                a = cur[m]; judged = a in JUDGED; flagged = a in ('CHECK', 'STRONG')
                cur_state = 'judged' if judged else ('withheld-pose' if poorpose else 'withheld-other')
            if frac is None:
                prop = 'no dial pose'
            else:
                f = frac[m] if m != 'round_offset' else max(frac['round_offset'], frac['round_size'])
                prop = 'allow' if f < BUDGET_FRACTION else 'withhold'
            fr = '' if frac is None else round(frac[m] if m != 'round_offset' else max(frac['round_offset'], frac['round_size']), 2)
            frs = '' if frac_s is None else round(frac_s[m] if m != 'round_offset' else max(frac_s['round_offset'], frac_s['round_size']), 2)
            meas.append(dict(photo=o['photo'], app_pose=b['pose'], measurement=m, current=cur_state, current_flag=bool(flagged),
                             current_att=a if m != 'round_offset' else '/'.join(sorted(set(cur[m]))),
                             proposed=prop, budget_used=fr, budget_used_stress=frs, tilt=round(o['tilt'], 1), tilt_hi=round(o['tilt_hi'], 1),
                             camera_az=o['camera_az'], affine_why=pose['why']))
        photos.append(o)
    with open('results/pose_policy_photos.csv', 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(photos[0])); w.writeheader()
        for o in photos: w.writerow({k: (round(v, 4) if isinstance(v, float) else v) for k, v in o.items()})
    with open('results/pose_policy_measurements.csv', 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(meas[0])); w.writeheader(); w.writerows(meas)
    # Summary.
    L = []
    P = lambda *a: L.append(' '.join(str(x) for x in a))
    P(f'photos: {len(photos)} genuine with batch verdicts; dirs {dirs}')
    lab = collections.Counter(o['app_pose'] for o in photos)
    P('current pose labels:', dict(lab))
    val = [o for o in photos if o['affine_valid']]
    P(f'affine pose valid: {len(val)}/{len(photos)}; by label:', dict(collections.Counter(o['app_pose'] for o in val)))
    P('affine invalid reasons:', dict(collections.Counter((o['affine_why'] or '').split(';')[0].split(' ')[-1] if o['affine_why'] else '' for o in photos if not o['affine_valid'])))
    ts = [o['tilt'] for o in val]; th = [o['tilt_hi'] for o in val]
    if ts: P(f'tilt median {np.median(ts):.1f} (IQR {np.percentile(ts,25):.1f}-{np.percentile(ts,75):.1f}); tilt_hi median {np.median(th):.1f}; squash sd median {np.median([o["squash_sd"] for o in val]):.4f}')
    P('side sign known (|z|>=2):', sum(1 for o in val if o['camera_az'] != ''), 'of', len(val))
    hm = [o['hm_est'] for o in photos if math.isfinite(o['hm_est'])]
    if hm: P(f'index height from parallax/tilt (tilt > 6 deg, n={len(hm)}): median {np.median(hm):.3f} R, IQR {np.percentile(hm,25):.3f}-{np.percentile(hm,75):.3f}')
    for lb in ('GOOD', 'CORRECTABLE', 'RETAKE'):
        sub = [o for o in val if o['app_pose'] == lb]
        if sub: P(f'  {lb}: n={len(sub)} tilt median {np.median([o["tilt"] for o in sub]):.1f}, max {max(o["tilt"] for o in sub):.1f}; tilt_hi median {np.median([o["tilt_hi"] for o in sub]):.1f}')
    P('')
    P('per measurement (rows = current app, cols = proposed):')
    P(f'{"measurement":14s} {"judged&allow":>12s} {"judged&WITHHOLD":>16s} {"poseheld&ALLOW":>15s} {"poseheld&withhold":>18s} {"otherheld":>10s} {"no dial pose":>12s}')
    for m in ('round_offset', 'gap12', 'rot12', 'six', 'side'):
        sub = [x for x in meas if x['measurement'] == m and x['current'] != 'not measured']
        c = lambda cur, prop: sum(1 for x in sub if x['current'] == cur and x['proposed'] == prop)
        P(f'{m:14s} {c("judged","allow"):>12d} {c("judged","withhold"):>16d} {c("withheld-pose","allow"):>15d} {c("withheld-pose","withhold"):>18d} '
          f'{sum(1 for x in sub if x["current"]=="withheld-other" and x["proposed"]!="no dial pose"):>10d} {sum(1 for x in sub if x["proposed"]=="no dial pose"):>12d}')
    open('results/pose_policy_summary.txt', 'w').write('\n'.join(L) + '\n')
    print('\n'.join(L))


if __name__ == '__main__':
    main()
