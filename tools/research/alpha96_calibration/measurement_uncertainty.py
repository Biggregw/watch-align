#!/usr/bin/env python3
"""RESEARCH -> APP. Alpha99 measurement-uncertainty allowances, from GENUINE photos only.

Question: when a photo's feature reads beyond every genuine reference watch, how much of the excess could be photo /
pose / detector error alone? Answer from the photo-to-photo spread of the SAME genuine watch: every genuine physical
watch with two or more usable, non-shared photos contributes its deviations from its own mean. The pooled within-watch
standard deviation (sigma) of one photo's reading is the per-family uncertainty. No replica, marketplace-candidate or
owner test photo is read here, and no threshold is tuned on any photo.

Basis (the same photos the Alpha98 reference uses):
  6, 9, rounds, ring, 12   catalogue genuine_population (ci_run_37500197377/per_photo.csv), shared photos excluded,
                           SWE excluded (as the 12 / Alpha98 marker reference)
  date window tilt         date_window run3 (run1 fallback) catalogue, shared excluded, SWE INCLUDED (as the date ref.)
Families and the quantity whose spread is pooled:
  six_rot / nine_rot      rotation deg
  six_off / nine_off      local radial and tangential components / R (both components pooled)
  rounds_off              every round marker's local radial and tangential components / R (all hours pooled)
  ring_rot                ring rotation deg
  ring_shift              ring shift x and y / R
  twelve_lateral          12 local tangential / R
  twelve_centreline       12 rotation deg
  twelve_sides            12 left and right side error deg
  date_tilt               window tilt deg
Positional families are also pooled in pixels; the app uses max(sigma_R, sigma_px / R_photo) so a low-resolution
photo is never granted a smaller allowance than its pixel noise implies. Angle families are likewise also pooled as
deg x R (an angle's error is a pixel edge error divided by the feature's length, which scales with R); the app uses
max(sigma_deg, sigma_degR / R_photo).

The Alpha99 rule (fixed before any photo was classified): a reading beyond the genuine maximum is a CLEAR FINDING only
when the excess exceeds K = 3 sigma (three standard deviations of single-photo error); otherwise WORTH A LOOK.
Writes alpha99_uncertainty.properties and results/alpha99/uncertainty.md.
"""
import csv
import math
import os
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROUNDS = (1, 2, 4, 5, 7, 8, 10, 11)
K = 3.0


def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def pooled(groups):
    """Pooled within-group SD over groups with >= 2 values; returns (sigma, dof, n_groups, n_values)."""
    ss = 0.0; dof = 0; ng = 0; nv = 0
    for g in groups:
        g = [x for x in g if x is not None]
        if len(g) < 2:
            continue
        m = sum(g) / len(g)
        ss += sum((x - m) ** 2 for x in g); dof += len(g) - 1; ng += 1; nv += len(g)
    return (math.sqrt(ss / dof) if dof else float('nan')), dof, ng, nv


def main():
    C = HERE
    cat = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(C, 'catalogue_provenance_strong.csv')))}
    shared = {r['photo_id'] for r in csv.DictReader(open(os.path.join(C, 'results/dedup/photos.csv'))) if r['shared_dial'] == '1'}
    # (family, unit) -> watch -> component -> list
    G = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    for r in csv.DictReader(open(os.path.join(C, 'results/ci_run_37500197377/per_photo.csv'))):
        if r['group'] != 'genuine_population' or r['status'] != 'accepted' or r['photo_id'] in shared:
            continue
        if 'swisswatchexpo' in cat[r['photo_id']]['image_url']:
            continue
        R = fl(r['dial_radius_px'])
        if not R:
            continue
        w = r['physical_watch_id']

        def add(fam, comp, px, per_r=True):
            if px is None:
                return
            if per_r:
                G[(fam, 'R')][w][comp].append(px / R); G[(fam, 'px')][w][comp].append(px)
            else:
                # angles: also pooled as deg x R (pixel edge error / feature length scales as 1 / R)
                G[(fam, 'deg')][w][comp].append(px); G[(fam, 'degR')][w][comp].append(px * R)
        for h, k in ((6, 'six'), (9, 'nine')):
            if r[f'm{h}_usable'] == 'true':
                add(f'{k}_rot', 'rot', fl(r[f'm{h}_rotation_deg']), False)
                add(f'{k}_off', 'rad', fl(r[f'm{h}_local_radial_px'])); add(f'{k}_off', 'tan', fl(r[f'm{h}_local_tangential_px']))
        if sum(r[f'm{h}_usable'] == 'true' for h in ROUNDS) >= 5:
            for h in ROUNDS:
                if r[f'm{h}_usable'] == 'true':
                    add('rounds_off', f'{h}r', fl(r[f'm{h}_local_radial_px'])); add('rounds_off', f'{h}t', fl(r[f'm{h}_local_tangential_px']))
        if r['ring_usable'] == 'true':
            add('ring_rot', 'rot', fl(r['ring_rotation_deg']), False)
            add('ring_shift', 'x', fl(r['ring_shift_x_px'])); add('ring_shift', 'y', fl(r['ring_shift_y_px']))
        if r['m12_usable'] == 'true':
            add('twelve_lateral', 'tan', fl(r['m12_local_tangential_px']))
            add('twelve_centreline', 'rot', fl(r['m12_rotation_deg']), False)
            add('twelve_sides', 'l', fl(r['m12_left_side_err_deg']), False); add('twelve_sides', 'r', fl(r['m12_right_side_err_deg']), False)
    # date window (SWE included)
    D = os.path.join(C, 'results', 'date_window')
    meas = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(D, 'run3', 'date_window_catalogue.csv'))) if r.get('status') == 'accepted'}
    for r in csv.DictReader(open(os.path.join(D, 'run1', 'date_window_catalogue.csv'))):
        if r.get('status') == 'accepted' and r['photo_id'] not in meas:
            meas[r['photo_id']] = r
    radius = {r['photo_id']: fl(r['dial_radius_px']) for r in csv.DictReader(open(os.path.join(C, 'results/ci_run_37500197377/per_photo.csv')))}
    for pid, m in meas.items():
        if m.get('usable') != 'True' or pid in shared or fl(m.get('window_tilt_deg')) is None:
            continue
        w = cat[pid]['physical_watch_id']; t = fl(m['window_tilt_deg'])
        G[('date_tilt', 'deg')][w]['tilt'].append(t)
        if radius.get(pid):
            G[('date_tilt', 'degR')][w]['tilt'].append(t * radius[pid])

    out = {}
    lines = ['# Alpha99 measurement-uncertainty allowances (genuine photos only)', '',
             'Written by `measurement_uncertainty.py`. sigma = pooled within-watch SD of one photo\'s reading, over every',
             'genuine physical watch with 2+ usable non-shared photos (components pooled). Rule fixed in advance: CLEAR',
             f'only when the excess beyond the genuine maximum exceeds K = {K:g} sigma.', '',
             '| family | unit | sigma | K x sigma | watches | photos | dof |', '|---|---|---:|---:|---:|---:|---:|']
    for (fam, unit) in sorted(G):
        groups = [lst for w in G[(fam, unit)].values() for lst in w.values()]
        s, dof, ng, nv = pooled(groups)
        nw = sum(1 for w in G[(fam, unit)].values() if any(len(l) >= 2 for l in w.values()))
        out[f'{fam}.{unit}'] = s
        out[f'{fam}.{unit}.watches'] = nw
        lines.append(f'| {fam} | {unit} | {s:.6f} | {K * s:.6f} | {nw} | {nv} | {dof} |')
    # Alpha101 edge-consistency limit for the 12 triangle: how far its two side angles may disagree on a genuine watch.
    # A real rotation turns both sides together; lighting / blur on one edge moves one side alone. Genuine range = max
    # over watches with 2+ photos of the watch's median |left - right| (single-photo watches excluded: their "median"
    # is that one photo, which may be the very artefact this detects), plus K x the robust (MAD) photo-to-photo spread
    # of the difference (robust because the photos this is meant to catch would inflate a plain SD).
    nom12 = {l.split('=')[0].strip(): float(l.split('=')[1]) for l in open(os.path.join(C, 'm12_nominal.properties'))
             if '=' in l and not l.startswith('#')}
    sd = defaultdict(list)
    for r in csv.DictReader(open(os.path.join(C, 'results/ci_run_37500197377/per_photo.csv'))):
        if (r['group'] != 'genuine_population' or r['status'] != 'accepted' or r['photo_id'] in shared
                or r['m12_usable'] != 'true'):
            continue
        L, Rr = fl(r['m12_left_side_err_deg']), fl(r['m12_right_side_err_deg'])
        if L is None or Rr is None:
            continue
        sd[r['physical_watch_id']].append((L - nom12['left_side_deg']) - (Rr - nom12['right_side_deg']))
    multi = {w: v for w, v in sd.items() if len(v) >= 2}
    meds = {w: sorted(v)[len(v) // 2] if len(v) % 2 else 0.5 * (sorted(v)[len(v) // 2 - 1] + sorted(v)[len(v) // 2]) for w, v in multi.items()}
    devs = sorted(abs(x - meds[w]) for w, v in multi.items() for x in v)
    mad = 1.4826 * (devs[len(devs) // 2] if len(devs) % 2 else 0.5 * (devs[len(devs) // 2 - 1] + devs[len(devs) // 2]))
    agree_limit = max(abs(m) for m in meds.values()) + K * mad
    out['twelve_sides_agreement.limit'] = agree_limit
    out['twelve_sides_agreement.limit.watches'] = len(multi)
    lines += ['', '## 12 triangle side agreement (Alpha101 edge-consistency limit)', '',
              f'Genuine watches with 2+ photos: {len(multi)} ({sum(len(v) for v in multi.values())} photos), SWE included (lighting is',
              f'exactly what this measures). Max per-watch median |left - right| {max(abs(m) for m in meds.values()):.3f} deg;',
              f'robust photo-to-photo spread {mad:.3f} deg; limit = max + {K:g} x spread = {agree_limit:.3f} deg.']
    with open(os.path.join(C, 'alpha99_uncertainty.properties'), 'w') as fh:
        fh.write('# RESEARCH -> APP. Alpha99 single-photo measurement uncertainty (pooled within-watch SD, genuine only).\n'
                 '# Written by measurement_uncertainty.py; see results/alpha99/uncertainty.md.\n')
        fh.write(f'k_sigma={K:g}\n')
        for k in sorted(out):
            v = out[k]
            fh.write(f'{k}={v:.6f}\n' if isinstance(v, float) else f'{k}={v}\n')
    os.makedirs(os.path.join(C, 'results', 'alpha99'), exist_ok=True)
    with open(os.path.join(C, 'results', 'alpha99', 'uncertainty.md'), 'w') as fh:
        fh.write('\n'.join(lines) + '\n')
    print('\n'.join(lines))


if __name__ == '__main__':
    main()
