#!/usr/bin/env python3
"""RESEARCH ONLY. Genuine date-window reference (offline) from the CI date-window run and hand date labels.

Rules: one value per physical watch (median of its usable photos), shared / stock photos excluded (dedup level dial),
SWE INCLUDED (owner decision 2026-10-07 for the date window; --exclude-swe gives the sensitivity without it),
owner-priority official / CPO photos always included.
  window_tilt                 date-independent -> one reference over all dates
  digit_dx, digit_dy, row tilt numeral-dependent -> reference per date (digit_tilt only for two-digit dates)
Repeatability: within-watch spread (watches with >= 2 usable photos of the same date) vs between-watch spread.
Local / owner / replica photos are compared with the matching reference (same date for centring and row tilt) and are
never added to it unless they are owner-priority genuine. No thresholds, no verdicts.

Usage: date_reference.py [--out results/date_window/reference.md]
"""
import argparse
import csv
import math
import os
from collections import defaultdict
from statistics import median

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.join(HERE, 'results', 'date_window')
KEYS = ('window_tilt_deg', 'digit_dx', 'digit_dy', 'digit_tilt_deg')
# owner-priority photos measured locally (upload file stem -> priority_genuine.csv photo_id) and their dates
PRIORITY_LOCAL = {'b1e67888-image': ('U6_126710BLNR_oyster', '28'), '4967ffd5-image': ('U7_126710BLNR_jubilee_CPO', '28'),
                  'b0b0c79c-image': ('U8_126715CHNR_CPO', '28'), '669b4aea-image': ('U3_126710GRNR_dealer', '2')}
LOCAL_DATES = {'RL_LOCAL_BLNR': '11', 'RL_THEONE_BLNR': '25', 'RL_ARF_BLRO_CROOKED6': '7', 'USER_BATGIRL_GMT': '9',
               '25369d47-image': '4', 'POOL_GEN_HO_01': '4', 'POOL_GEN_HO_02': '31', 'EXT_EXT_GEN_BLRO_WEX_01': '13',
               'EXT_EXT_GEN_BLRO_WEX_02': '13', '12f1d177-image': '13', 'a805c035-image': '28', 'c743d9fb-image': '28',
               '548a49c1-image': '28', '4f87a0d3-image': '1'}
LOCAL_NAMES = {'25369d47-image': 'USER_BATGIRL_photo2', '12f1d177-image': 'U4_116710LN_listing', 'a805c035-image': 'U2_116710LN_CPO',
               'c743d9fb-image': 'U5_116710LN_CPO', '548a49c1-image': 'U1_16700_CPO', '4f87a0d3-image': 'U9_126710BLRO_dealer'}


def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def mad(v):
    m = median(v); return median(abs(x - m) for x in v)


def q(s, p):
    s = sorted(s); k = (len(s) - 1) * p; lo = int(k); hi = min(lo + 1, len(s) - 1); return s[lo] + (s[hi] - s[lo]) * (k - lo)


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--out', default=os.path.join(R, 'reference.md'))
    ap.add_argument('--exclude-swe', action='store_true', help='sensitivity: drop SWE photos (owner decision includes them)')
    a = ap.parse_args()
    cat = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(HERE, 'catalogue_provenance_strong.csv')))}
    shared = {r['photo_id'] for r in csv.DictReader(open(os.path.join(R, '..', 'dedup', 'photos.csv'))) if r['shared_dial'] == '1'}
    labels = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(R, 'date_labels_catalogue.csv')))}
    # run 2 (fixed row tilt) first; photos that verified only in run 1 contribute window tilt and centring (unchanged by the
    # fix) but not row tilt
    meas = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(R, 'run3', 'date_window_catalogue.csv')))
            if r.get('status') == 'accepted'}
    for r in csv.DictReader(open(os.path.join(R, 'run1', 'date_window_catalogue.csv'))):
        if r.get('status') == 'accepted' and r['photo_id'] not in meas:
            r = dict(r); r['digit_tilt_deg'] = ''; r['run'] = '1'; meas[r['photo_id']] = r
    pri = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(HERE, 'priority_genuine.csv')))}
    photos = []   # (watch, source, date, values)
    excl = defaultdict(int)
    for pid, m in meas.items():
        if m.get('status') != 'accepted':
            continue
        if m.get('usable') != 'True':
            excl['withheld by the measurement'] += 1; continue
        c = cat[pid]; host = c['image_url'].split('/')[2]
        if 'swisswatchexpo' in host and a.exclude_swe:
            excl['SWE (sensitivity run)'] += 1; continue
        if pid in shared:
            excl['shared / stock photo'] += 1; continue
        d = labels.get(pid, {}).get('date', '')
        if not d or labels[pid]['label_note']:
            excl['date unlabelled / uncertain'] += 1; continue
        src = "Bob's" if 'bobswatches' in host else 'Phillips' if 'phillips' in host else 'SWE' if 'swisswatchexpo' in host else 'other'
        photos.append((c['physical_watch_id'], src, d, {k: fl(m.get(k)) for k in KEYS}))
    local = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(R, 'date_local_owner.csv')))}
    for stem, (ppid, d) in PRIORITY_LOCAL.items():
        m = local.get(stem)
        if m and m.get('usable') == 'True' and pri.get(ppid, {}).get('include') == 'yes':
            photos.append((pri[ppid]['physical_watch_id'], 'Owner priority', d, {k: fl(m.get(k)) for k in KEYS}))
    # one value per watch (per date)
    by = defaultdict(list)
    for w, s, d, v in photos:
        by[(w, s, d)].append(v)
    W = [dict(watch=w, src=s, date=d, n=len(vs), **{k: (median([v[k] for v in vs if v[k] is not None]) if any(v[k] is not None for v in vs) else None) for k in KEYS})
         for (w, s, d), vs in by.items()]
    L = ['# Genuine date-window reference (research only; no limits, no verdicts)', '',
         f"CI runs 37664650850 (row tilt fixed, glyph-height check) and 37662260742 (window tilt / centring only, for photos that verified only "
         f"then) + owner-priority photos; SWE {'excluded (sensitivity)' if a.exclude_swe else 'included (owner decision)'}. Photos used: {len(photos)}; physical watches: "
         f'{len(W)}. Excluded: ' + ', '.join(f'{k} {v}' for k, v in sorted(excl.items())) + '.', '',
         'Sources (watches): ' + ', '.join(f"{s} {sum(1 for w in W if w['src'] == s)}" for s in sorted({w['src'] for w in W})), '']
    wt = [w['window_tilt_deg'] for w in W if w['window_tilt_deg'] is not None]
    L += ['## Window tilt (all dates; deg, + = clockwise)', '',
          f'{len(wt)} watches: median {median(wt):+.2f}, MAD {mad(wt):.2f}, P10 {q(wt, .1):+.2f}, P90 {q(wt, .9):+.2f}, '
          f'min {min(wt):+.2f}, max {max(wt):+.2f}', '']
    L += ['## Per date (one value per watch): digit centring (fraction of window) and row tilt (two-digit dates)', '',
          '| date | watches | dx median [min, max] | dy median [min, max] | row tilt median [min, max] |', '|---:|---:|---:|---:|---:|']
    dates = sorted({w['date'] for w in W}, key=int)
    ref = {}
    for d in dates:
        ws = [w for w in W if w['date'] == d]; ref[d] = ws
        def cell(k, f='+.3f'):
            v = [w[k] for w in ws if w[k] is not None]
            return f'{median(v):{f}} [{min(v):{f}}, {max(v):{f}}]' if v else '–'
        L.append(f"| {d} | {len(ws)} | {cell('digit_dx')} | {cell('digit_dy')} | {cell('digit_tilt_deg', '+.2f')} |")
    # repeatability
    L += ['', '## Repeatability: within-watch vs between-watch', '',
          '| quantity | watches with >= 2 photos | median within-watch spread (max-min) | between-watch MAD (same date, date 9) |', '|---|---:|---:|---:|']
    for k in KEYS:
        spreads = []
        for (w, s, d), vs in by.items():
            v = [x[k] for x in vs if x[k] is not None]
            if len(v) >= 2:
                spreads.append(max(v) - min(v))
        b = [w[k] for w in ref.get('9', []) if w[k] is not None] if k != 'window_tilt_deg' else wt
        L.append(f"| {k} | {len(spreads)} | {median(spreads):.3f} | {mad(b) if len(b) >= 3 else float('nan'):.3f} |" if spreads else f'| {k} | 0 | – | – |')
    # comparisons
    L += ['', '## Local, owner and replica photos against the genuine reference', '',
          'Counts: genuine watches at least as far from the reference median (window tilt: all watches; centring / row tilt: '
          'same-date watches only, shown when the date has >= 3 genuine watches).', '',
          '| photo | date | window tilt (count) | dx (count) | dy (count) | row tilt (count) |', '|---|---:|---:|---:|---:|---:|']
    def far(v, pool):
        mpool = median(pool); return sum(1 for x in pool if abs(x - mpool) >= abs(v - mpool) - 1e-12), len(pool)
    for stem, r in local.items():
        name = LOCAL_NAMES.get(stem, PRIORITY_LOCAL.get(stem, (stem,))[0])
        d = LOCAL_DATES.get(stem, PRIORITY_LOCAL.get(stem, ('', ''))[1])
        if r.get('usable') != 'True':
            L.append(f"| {name} | {d} | withheld: {r['reason'][:45]} | | | |"); continue
        cells = []
        v = fl(r['window_tilt_deg']); c, n = far(v, wt); cells.append(f'{v:+.2f} ({c}/{n})')
        same = ref.get(d, [])
        for k, f in (('digit_dx', '+.3f'), ('digit_dy', '+.3f'), ('digit_tilt_deg', '+.2f')):
            v = fl(r.get(k)); pool = [w[k] for w in same if w[k] is not None]
            if v is None:
                cells.append('–')
            elif len(pool) >= 3:
                c, n = far(v, pool); cells.append(f'{v:{f}} ({c}/{n})')
            else:
                cells.append(f'{v:{f}} (no date ref)')
        L.append(f"| {name} | {d} | " + ' | '.join(cells) + ' |')
    L += ['', 'Owner-priority photos are part of the reference, so their own counts include themselves.', '']
    open(a.out, 'w').write('\n'.join(L) + '\n'); print('\n'.join(L))


if __name__ == '__main__':
    main()
