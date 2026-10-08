"""Alpha101 crown-logo alignment: genuine reference, uncertainty and held-out validation (QC guardrails section 8).

Input: results/alpha101/logo_catalogue.csv (CI job alpha101-genuine-print, Alpha101Logo driver) and, for step 6 only,
results/alpha101/logo_local.csv (the local / owner photos). A reading counts only when the logo passed its symmetry
gate AND the 12's Alpha99 hand/glare check was clean (the 12's corridor starts at 0.50 R, inside the logo box).

Features, both signed against the genuine nominal:
  logo_off   sideways offset of the logo's symmetry axis, dial radii (+ = clockwise)
  logo_tilt  tilt of the axis against the 12's radial line, degrees (+ = clockwise)

Steps, fixed before looking at any replica:
  1. nominal = median over physical watches of each watch's median reading;
  2. manufacturing variation = each watch's distance from the median of the other watches (one watch = one sample);
     logo_off is compared only with watches photographed at similar or lower resolution (R_ref <= 1.3 R, 8+ watches),
     as for the round markers;
  3. photo-to-photo uncertainty = pooled within-watch SD over watches with 2+ photos (R and px for the offset,
     deg and deg x R for the tilt; the app would use the larger of the two at the photo's resolution);
  4. the reference is frozen here; CLEAR needs an excess beyond the genuine max of more than K = 3 sigma;
  5. held-out: every genuine photo judged alone against the reference rebuilt without its own watch, and the Swiss
     Watch Expo photos (never in the reference, as for the markers) against the full reference;
  6. local / owner photos, for usefulness only (never used to set anything above).
"""
import csv
import math
import os
from collections import defaultdict
from statistics import median

HERE = os.path.dirname(os.path.abspath(__file__))
K = 3.0
RES_MATCH, MIN_MATCHED = 1.3, 8


def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def pooled(groups):
    ss = 0.0; dof = 0; ng = 0
    for g in groups:
        if len(g) < 2:
            continue
        m = sum(g) / len(g); ss += sum((x - m) ** 2 for x in g); dof += len(g) - 1; ng += 1
    return (math.sqrt(ss / dof) if dof else float('nan')), ng


def readings(path):
    out = {}
    for r in csv.DictReader(open(path)):
        if r['status'] != 'accepted' or r['photo_id'] in out:
            continue
        if r['usable'] != 'true' or r.get('twelve_clean') != 'true':
            out[r['photo_id']] = None; continue
        out[r['photo_id']] = {'R': fl(r['dial_radius_px']), 'logo_off': fl(r['offset_R']), 'logo_tilt': fl(r['tilt_deg'])}
    return out


def main():
    C = HERE; A = os.path.join(C, 'results', 'alpha101')
    cat = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(C, 'catalogue_provenance_strong.csv')))}
    shared = {r['photo_id'] for r in csv.DictReader(open(os.path.join(C, 'results/dedup/photos.csv'))) if r['shared_dial'] == '1'}
    rd = readings(os.path.join(A, 'logo_catalogue.csv'))
    withheld = sum(1 for v in rd.values() if v is None)
    ref_photos, ext_photos = [], []
    for pid, v in rd.items():
        if v is None or pid in shared or cat[pid]['group'] != 'genuine_population':
            continue
        (ext_photos if 'swisswatchexpo' in cat[pid]['image_url'] else ref_photos).append((pid, cat[pid]['physical_watch_id'], v))
    pw = defaultdict(lambda: defaultdict(list))
    for pid, w, v in ref_photos:
        for k, x in v.items():
            pw[w][k].append(x)
    FEAT = ('logo_off', 'logo_tilt')
    W = {k: {w: median(d[k]) for w, d in pw.items()} for k in FEAT}
    WR = {w: median(d['R']) for w, d in pw.items()}
    sig = {}
    # second unit = reading x R (px for the offset, deg x R for the tilt), so low-resolution photos get their pixel noise
    for k, units in (('logo_off', (('R', False), ('px', True))), ('logo_tilt', (('deg', False), ('degR', True)))):
        for unit, times_r in units:
            sig[(k, unit)] = pooled([[x * (r if times_r else 1.0) for x, r in zip(d[k], d['R'])] for d in pw.values()])

    def sigma(k, R):
        return max(sig[(k, 'R')][0], sig[(k, 'px')][0] / R) if k == 'logo_off' else max(sig[(k, 'deg')][0], sig[(k, 'degR')][0] / R)

    def judge(k, v, R, excl):
        vals = {w: x for w, x in W[k].items() if w != excl}
        nom = median(vals.values())
        far = {w: abs(x - median([y for ww, y in vals.items() if ww != w])) for w, x in vals.items()}
        pool = [x for w, x in far.items() if k != 'logo_off' or WR[w] <= RES_MATCH * R]
        if len(pool) < (MIN_MATCHED if k == 'logo_off' else 1):
            return 'NOT_ASSESSED', abs(v - nom), None
        mx, val = max(pool), abs(v - nom)
        if val <= mx:
            return 'WITHIN', val, mx
        return ('CLEAR' if val - mx > K * sigma(k, R) else 'WORTH'), val, mx

    lines = ['# Alpha101 crown-logo alignment: genuine reference (written by alpha101_logo_reference.py)', '']
    lines.append(f'Catalogue photos withheld by the logo symmetry gate or the 12 hand/glare check: {withheld}')
    lines.append(f'Reference: {len(W["logo_off"])} physical watches, {len(ref_photos)} photos (SWE and shared-dial photos excluded)')
    for k, u in (('logo_off', 'R'), ('logo_tilt', 'deg')):
        nom = median(W[k].values())
        far = sorted(abs(x - median([y for ww, y in W[k].items() if ww != w])) for w, x in W[k].items())
        lines.append(f'{k}: nominal {nom:+.6f} {u}; per-watch distance from nominal median {median(far):.6f}, '
                     f'90% {far[int(0.9 * (len(far) - 1))]:.6f}, max {far[-1]:.6f} {u}')
    for (k, unit), (s, ng) in sig.items():
        lines.append(f'sigma {k}.{unit} = {s:.6f} ({ng} watches with 2+ photos)')
    rows = []; tally = defaultdict(lambda: defaultdict(int))
    for grp, photos in (('lowo', ref_photos), ('swe_external', ext_photos)):
        for pid, w, v in photos:
            for k in FEAT:
                st, val, mx = judge(k, v[k], v['R'], w if grp == 'lowo' else None)
                tally[(grp, k)][st] += 1
                rows.append([grp, pid, w, k, f"{v['R']:.0f}", st, f'{val:.6f}', '' if mx is None else f'{mx:.6f}'])
    local = os.path.join(A, 'logo_local.csv')
    if os.path.exists(local):
        for pid, v in readings(local).items():
            for k in FEAT:
                if v is None:
                    rows.append(['local', pid, '', k, '', 'NOT_ASSESSED', '', '']); tally[('local', k)]['NOT_ASSESSED'] += 1; continue
                st, val, mx = judge(k, v[k], v['R'], None)
                tally[('local', k)][st] += 1
                rows.append(['local', pid, '', k, f"{v['R']:.0f}", st, f'{val:.6f}', '' if mx is None else f'{mx:.6f}'])
    lines += ['', '| set | feature | within | worth a look | clear | not assessed |', '|---|---|---:|---:|---:|---:|']
    for (grp, k), t in sorted(tally.items()):
        lines.append(f"| {grp} | {k} | {t['WITHIN']} | {t['WORTH']} | {t['CLEAR']} | {t['NOT_ASSESSED']} |")
    with open(os.path.join(A, 'logo_heldout.csv'), 'w', newline='') as fh:
        wr = csv.writer(fh); wr.writerow(['set', 'photo_id', 'physical_watch_id', 'feature', 'dial_radius_px', 'status', 'value', 'genuine_max']); wr.writerows(rows)
    with open(os.path.join(A, 'logo_reference.md'), 'w') as fh:
        fh.write('\n'.join(lines) + '\n')
    print('\n'.join(lines))
    for r in rows:
        if r[5] in ('WORTH', 'CLEAR'):
            print(' ', *r)


if __name__ == '__main__':
    main()
