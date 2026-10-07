#!/usr/bin/env python3
"""RESEARCH -> APP. Genuine per-watch reference values for the Alpha98 results screen, and an offline check of what
the 'beyond every genuine reference watch' rule flags on local / owner / replica photos.

Owner decisions (2026-10-07): flag a feature only when it reads further than every genuine reference watch; features
6, 9, 12 (robust), round markers, ring, date-window tilt; technical numbers behind 'Details'.

Reference basis (one value per physical watch = median of its usable photos; shared / stock photos excluded):
  6, 9, rounds, ring   catalogue minus SWE (as the 12) + owner-priority official / CPO photos
  12                   unchanged: m12_genuine_reference.csv (Alpha97b, 45 watches)
  date window tilt     catalogue INCLUDING SWE + owner-priority (owner decision for the date window)
Quantities ('far' = distance from the genuine nominal, nominal = median over watches; each watch's own distance is
computed from a nominal without it):
  six_rot / nine_rot   |rotation - nominal| deg
  six_off / nine_off   local offset magnitude / R (no nominal: magnitude)
  rounds_off           worst round-marker local offset / R
  ring_rot             |ring rotation - nominal| deg
  ring_shift           ring shift / R
  date_tilt            |window tilt - nominal| deg
Resolution: rounds_off and ring_shift inflate on small dials, so a photo is compared only with genuine watches whose
dial radius is at most 1.3 x the photo's (similar or noisier); fewer than 8 such watches -> not assessed.
Writes alpha98_reference.csv (feature, watch, source, value, dial_radius_px) and alpha98_nominal.properties, and prints
the offline check. No thresholds are tuned.
"""
import csv
import math
import os
from collections import defaultdict
from statistics import median

HERE = os.path.dirname(os.path.abspath(__file__))
ROUNDS = (1, 2, 4, 5, 7, 8, 10, 11)
RES_MATCH = 1.3
MIN_MATCHED = 8


def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def photo_features(r):
    """Raw per-photo quantities from an Alpha96 runner / per_photo row (signed where a nominal applies)."""
    if r.get('status') != 'accepted':
        return None
    R = fl(r['dial_radius_px'])
    if not R:
        return None
    f = {'R': R}
    for h, k in ((6, 'six'), (9, 'nine')):
        if r[f'm{h}_usable'] == 'true':
            f[f'{k}_rot'] = fl(r[f'm{h}_rotation_deg'])
            lp = fl(r[f'm{h}_local_px'])
            f[f'{k}_off'] = lp / R if lp is not None else None
    rl = [fl(r[f'm{h}_local_px']) for h in ROUNDS if r[f'm{h}_usable'] == 'true']
    rl = [x for x in rl if x is not None]
    if len(rl) >= 5:
        f['rounds_off'] = max(rl) / R
    if r.get('ring_usable') == 'true':
        f['ring_rot'] = fl(r['ring_rotation_deg'])
        s = fl(r['ring_shift_px'])
        f['ring_shift'] = s / R if s is not None else None
    return {k: v for k, v in f.items() if v is not None}


SIGNED = ('six_rot', 'nine_rot', 'ring_rot', 'date_tilt')
FEATURES = ('six_rot', 'six_off', 'nine_rot', 'nine_off', 'rounds_off', 'ring_rot', 'ring_shift', 'date_tilt')
RES_MATCHED = ('rounds_off', 'ring_shift')


def main():
    C = HERE
    cat = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(C, 'catalogue_provenance_strong.csv')))}
    shared = {r['photo_id'] for r in csv.DictReader(open(os.path.join(C, 'results/dedup/photos.csv'))) if r['shared_dial'] == '1'}
    pri = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(C, 'priority_genuine.csv'))) if r['include'] == 'yes'}
    per_watch = defaultdict(lambda: defaultdict(list)); src = {}
    # markers: catalogue (SWE excluded) + priority
    for r in csv.DictReader(open(os.path.join(C, 'results/ci_run_37500197377/per_photo.csv'))):
        if r['group'] != 'genuine_population' or r['photo_id'] in shared:
            continue
        if 'swisswatchexpo' in cat[r['photo_id']]['image_url']:
            continue
        f = photo_features(r)
        if not f:
            continue
        w = r['physical_watch_id']; src[w] = 'catalogue'
        for k, v in f.items():
            per_watch[w][k].append(v)
    for r in csv.DictReader(open(os.path.join(C, 'results/priority_genuine_runner.csv'))):
        if r['photo_id'] not in pri:
            continue
        f = photo_features(r)
        if not f:
            continue
        w = pri[r['photo_id']]['physical_watch_id']; src[w] = 'owner priority'
        for k, v in f.items():
            if k == 'six_rot' and r['photo_id'] == 'U8_126715CHNR_CPO':
                continue                      # seconds hand over the 6 (documented contamination)
            if k == 'six_off' and r['photo_id'] == 'U8_126715CHNR_CPO':
                continue
            per_watch[w][k].append(v)
    # date window tilt: SWE included (date_reference.py basis)
    import importlib.util
    spec = importlib.util.spec_from_file_location('dr', os.path.join(C, 'date_reference.py'))
    R_ = os.path.join(C, 'results', 'date_window')
    meas = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(R_, 'run3', 'date_window_catalogue.csv'))) if r.get('status') == 'accepted'}
    for r in csv.DictReader(open(os.path.join(R_, 'run1', 'date_window_catalogue.csv'))):
        if r.get('status') == 'accepted' and r['photo_id'] not in meas:
            meas[r['photo_id']] = r
    date_w = defaultdict(list)
    for pid, m in meas.items():
        if m.get('usable') != 'True' or pid in shared or fl(m.get('window_tilt_deg')) is None:
            continue
        date_w[cat[pid]['physical_watch_id']].append(fl(m['window_tilt_deg']))
    local_dw = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(R_, 'date_local_owner.csv')))}
    for stem, ppid in (('b1e67888-image', 'U6_126710BLNR_oyster'), ('4967ffd5-image', 'U7_126710BLNR_jubilee_CPO'),
                       ('b0b0c79c-image', 'U8_126715CHNR_CPO'), ('669b4aea-image', 'U3_126710GRNR_dealer')):
        m = local_dw.get(stem)
        if m and m.get('usable') == 'True' and ppid in pri:
            date_w[pri[ppid]['physical_watch_id']].append(fl(m['window_tilt_deg']))
    # one value per watch
    W = {f: {} for f in FEATURES}; WR = {}
    for w, d in per_watch.items():
        WR[w] = median(d['R']) if d.get('R') else None
        for k in FEATURES:
            if d.get(k):
                W[k][w] = median(d[k])
    for w, v in date_w.items():
        W['date_tilt'][w] = median(v)
    nominal = {k: median(W[k].values()) for k in SIGNED}
    rows = []
    for k in FEATURES:
        for w, v in W[k].items():
            if k in SIGNED:
                others = [x for ww, x in W[k].items() if ww != w]
                far = abs(v - median(others))
            else:
                far = abs(v)
            rows.append((k, w, src.get(w, 'catalogue'), far, WR.get(w)))
    with open(os.path.join(C, 'alpha98_reference.csv'), 'w', newline='') as fh:
        wr = csv.writer(fh); wr.writerow(['feature', 'physical_watch_id', 'source', 'far', 'dial_radius_px'])
        for k, w, s, far, R in rows:
            wr.writerow([k, w, s, f'{far:.6f}', f'{R:.1f}' if R else ''])
    with open(os.path.join(C, 'alpha98_nominal.properties'), 'w') as fh:
        fh.write('# RESEARCH -> APP. Genuine nominals for signed Alpha98 features (median over reference watches).\n')
        for k in SIGNED:
            fh.write(f'{k}={nominal[k]:.6f}\n')
    ref = defaultdict(list)
    for k, w, s, far, R in rows:
        ref[k].append((far, R))
    print('reference watches per feature: ' + ', '.join(f'{k} {len(ref[k])}' for k in FEATURES))
    print('nominals: ' + ', '.join(f'{k} {nominal[k]:+.3f}' for k in SIGNED))
    print('genuine max (far): ' + ', '.join(f'{k} {max(x for x, _ in ref[k]):.4f}' for k in FEATURES))

    # offline check on local / owner photos
    def assess(f, R):
        out = {}
        for k in FEATURES:
            if k not in f:
                continue
            v = abs(f[k] - nominal[k]) if k in SIGNED else abs(f[k])
            pool = [x for x, r in ref[k] if (k not in RES_MATCHED) or (r is not None and r <= RES_MATCH * R)]
            if len(pool) < (MIN_MATCHED if k in RES_MATCHED else 1):
                out[k] = ('n/a', v, len(pool)); continue
            out[k] = ('FLAG' if v > max(pool) else 'ok', v, len(pool))
        return out
    print('\nOffline check (FLAG = beyond every genuine reference watch):')
    exp_rows = []
    tests = []
    for fn, label in ((os.path.join(C, 'results/runner_local.csv'), 'local'), (os.path.join(C, 'results/priority_genuine_runner.csv'), 'owner')):
        for r in csv.DictReader(open(fn)):
            f = photo_features(r)
            if f:
                tests.append((r['photo_id'], f))
    dwt = {'RL_LOCAL_BLNR': 'RL_LOCAL_BLNR', 'RL_THEONE_BLNR': 'RL_THEONE_BLNR', 'USER_BATGIRL_GMT': 'USER_BATGIRL_GMT',
           'U2_116710LN_CPO_a': 'a805c035-image', 'U5_116710LN_CPO_b': 'c743d9fb-image', 'U6_126710BLNR_oyster': 'b1e67888-image',
           'U7_126710BLNR_jubilee_CPO': '4967ffd5-image', 'U8_126715CHNR_CPO': 'b0b0c79c-image', 'U1_16700_CPO': '548a49c1-image',
           'U4_116710LN_listing_screenshot': '12f1d177-image'}
    for pid, f in tests:
        m = local_dw.get(dwt.get(pid, ''), {})
        if m.get('usable') == 'True':
            f['date_tilt'] = fl(m['window_tilt_deg'])
        res = assess(f, f['R'])
        for k, (st, v, n) in res.items():
            exp_rows.append((pid, k, st, f'{v:.6f}', n))
        flags = [f"{k} {v:.4g}" for k, (s, v, n) in res.items() if s == 'FLAG']
        na = [k for k, (s, v, n) in res.items() if s == 'n/a']
        print(f"  {pid:32s} R={f['R']:4.0f}  flagged: {', '.join(flags) or 'none'}" + (f"   (not assessed: {', '.join(na)})" if na else ''))
    os.makedirs(os.path.join(C, 'results', 'alpha98'), exist_ok=True)
    with open(os.path.join(C, 'results', 'alpha98', 'expected_flags_local.csv'), 'w', newline='') as fh:
        wr = csv.writer(fh); wr.writerow(['photo_id', 'feature', 'status', 'value', 'n_reference'])
        wr.writerows(exp_rows)


if __name__ == '__main__':
    main()
