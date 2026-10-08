#!/usr/bin/env python3
"""RESEARCH -> APP. Model-generic genuine reference and single-photo uncertainty for a new model (first: the 124060).

The same definitions as the GMT's build_alpha98_reference.py (photo_features) and measurement_uncertainty.py, with the
batons taken from the model spec instead of the GMT's fixed 6 and 9 (e.g. the 124060's 3, 6 and 9):

  <key>_rot  baton rotation (deg, signed)            <key>_off   baton local offset / R (magnitude)
  rounds_off largest round local offset / R (5+ usable rounds)
  rounds_size median round size error / R (signed)    round_size_rel largest single round's |size - median| / R
  ring_rot   marker ring rotation (deg, signed)       ring_shift  marker ring shift / R (magnitude)

One value per physical watch (median of its photos); shared photos (dedup.csv shared_dial=1) never count; replicas never
enter (group must be genuine_population). Excluded sources (default: SwissWatchExpo, as for the GMT markers) stay out
of the reference and serve as external held-out photos. Signed features: far = |watch - median of the other watches|;
magnitudes: far = |watch|. Uncertainty: pooled within-watch SD over watches with 2+ usable photos, in R and px (positions)
or deg and deg x R (angles); K = 3. The 12 triangle's families and side-agreement limit use the triangle nominal written
by alpha96_calibration/calibrate_m12_nominal.py (run that first, on the same inputs).

Usage: build_sub_reference.py --per-photo per_photo.csv --dedup dedup.csv --spec model.json --out-dir <dir>
Writes alpha98_reference.csv, alpha98_nominal.properties, alpha99_uncertainty.properties and uncertainty.md in out-dir,
the file names export_model_reference.py expects.
"""
import argparse
import csv
import json
import math
import os
from collections import defaultdict
from statistics import median

K = 3.0
RES_MATCHED = ('rounds_off', 'ring_shift', 'rounds_size', 'round_size_rel')


def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def pooled(groups):
    ss = 0.0; dof = 0; ng = 0; nv = 0
    for g in groups:
        g = [x for x in g if x is not None]
        if len(g) < 2:
            continue
        m = sum(g) / len(g); ss += sum((x - m) ** 2 for x in g); dof += len(g) - 1; ng += 1; nv += len(g)
    return (math.sqrt(ss / dof) if dof else float('nan')), dof, ng, nv


def layout(spec):
    batons = [(m['hour'], m['key']) for m in spec['markers'] if m['shape'] == 'baton']
    rounds = [m['hour'] for m in spec['markers'] if m['shape'] == 'round']
    return sorted(batons), sorted(rounds)


def photo_features(r, batons, rounds):
    """build_alpha98_reference.photo_features, batons from the spec."""
    if r.get('status') != 'accepted':
        return None
    R = fl(r['dial_radius_px'])
    if not R:
        return None
    f = {'R': R}
    for h, k in batons:
        if r[f'm{h}_usable'] == 'true':
            f[f'{k}_rot'] = fl(r[f'm{h}_rotation_deg'])
            lp = fl(r[f'm{h}_local_px'])
            f[f'{k}_off'] = lp / R if lp is not None else None
    rl = [fl(r[f'm{h}_local_px']) for h in rounds if r[f'm{h}_usable'] == 'true']
    rl = [x for x in rl if x is not None]
    if len(rl) >= 5:
        f['rounds_off'] = max(rl) / R
    rs = [fl(r[f'm{h}_radius_err_px']) for h in rounds if r[f'm{h}_usable'] == 'true']
    rs = [x / R for x in rs if x is not None]
    if len(rs) >= 5:
        ms = median(rs); f['rounds_size'] = ms; f['round_size_rel'] = max(abs(x - ms) for x in rs)
    if r.get('ring_usable') == 'true':
        f['ring_rot'] = fl(r['ring_rotation_deg'])
        s = fl(r['ring_shift_px'])
        f['ring_shift'] = s / R if s is not None else None
    return {k: v for k, v in f.items() if v is not None}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--per-photo', required=True)
    ap.add_argument('--dedup', required=True)
    ap.add_argument('--spec', required=True)
    ap.add_argument('--out-dir', required=True)
    ap.add_argument('--exclude-host', action='append', default=['swisswatchexpo'])
    ap.add_argument('--no-allowance', action='append', default=[],
                    help='feature families written without an uncertainty allowance, so they can be at most WORTH A LOOK '
                         '(QC guardrails 11: downgrade a feature whose held-out genuine evidence conflicts)')
    ap.add_argument('--lowres', help='per_photo CSV of shrunk genuine photos (sub124060-lowres job): adds one row per '
                    'physical watch per shrink level for the resolution-matched features, at that level\'s dial radius')
    ap.add_argument('--lowres-level', action='append', default=[], help='shrink levels (target dial radius px) to use, e.g. 170')
    ap.add_argument('--lowres-feature', action='append', default=[], help='features that get low-resolution rows')
    a = ap.parse_args()
    spec = json.load(open(a.spec)); batons, rounds = layout(spec)
    shared = {r['photo_id'] for r in csv.DictReader(open(a.dedup)) if r['shared_dial'] == '1'}
    rows = [r for r in csv.DictReader(open(a.per_photo))
            if r['group'] == 'genuine_population' and r['photo_id'] not in shared
            and not any(h in r['image_url'] for h in a.exclude_host)]
    signed = tuple(f'{k}_rot' for _, k in batons) + ('ring_rot', 'rounds_size')
    feats = tuple(x for _, k in batons for x in (f'{k}_rot', f'{k}_off')) + ('rounds_off', 'ring_rot', 'ring_shift',
                                                                             'rounds_size', 'round_size_rel')
    per_watch = defaultdict(lambda: defaultdict(list)); src = {}
    for r in rows:
        f = photo_features(r, batons, rounds)
        if not f:
            continue
        w = r['physical_watch_id']; src[w] = r['source']
        for k, v in f.items():
            per_watch[w][k].append(v)
    W = {k: {w: median(d[k]) for w, d in per_watch.items() if d.get(k)} for k in feats}
    WR = {w: median(d['R']) for w, d in per_watch.items()}
    nominal = {k: median(W[k].values()) for k in signed if W[k]}
    out_rows = []
    for k in feats:
        for w, v in W[k].items():
            far = abs(v - median([x for ww, x in W[k].items() if ww != w])) if k in signed else abs(v)
            out_rows.append((k, w, src[w], far, WR[w]))
    # Low-resolution rows (resolution-matched features only): the same genuine watches, their photos shrunk and
    # re-measured. Each watch still counts once in the app (matched counts are distinct watches). A signed feature's far
    # is taken against the full-resolution nominal of the other watches, exactly what the app compares a photo with.
    if a.lowres:
        rm = set(a.lowres_feature) or (set(RES_MATCHED) | set(spec.get('resolution_matched', [])))
        levels = set(a.lowres_level)
        lw = defaultdict(lambda: defaultdict(list))
        for r in csv.DictReader(open(a.lowres)):
            if r['group'] != 'genuine_population' or r['source_photo_id'] in shared or any(h in r['image_url'] for h in a.exclude_host):
                continue
            if levels and r['target_r'] not in levels:
                continue
            f = photo_features(r, batons, rounds)
            if not f:
                continue
            for k, v in f.items():
                lw[(r['physical_watch_id'], r['target_r'])][k].append(v)
        n_low = 0
        # full-resolution coverage: smallest photo R with 8+ full-resolution reference watches at R_ref <= 1.3 R; a
        # shrunk row is used only for photos below it, so the limits for photos the full reference covers never change
        cover = {}
        for k in feats:
            rs = sorted(WR[w] for w in W[k])
            cover[k] = rs[7] / 1.3 if len(rs) >= 8 else float('inf')
        for (w, T), d in sorted(lw.items()):
            Rl = median(d['R'])
            for k in feats:
                if k not in rm or not d.get(k):
                    continue
                v = median(d[k])
                far = abs(v - median([x for ww, x in W[k].items() if ww != w])) if k in signed else abs(v)
                out_rows.append((k, w, src.get(w, ''), far, Rl, cover[k])); n_low += 1
        print(f'low-resolution rows added: {n_low} ({len({w for w, _ in lw})} watches)')
    os.makedirs(a.out_dir, exist_ok=True)
    with open(os.path.join(a.out_dir, 'alpha98_reference.csv'), 'w', newline='') as fh:
        low = any(len(x) > 5 for x in out_rows)
        wr = csv.writer(fh); wr.writerow(['feature', 'physical_watch_id', 'source', 'far', 'dial_radius_px'] + (['max_photo_r'] if low else []))
        for x in out_rows:
            k, w, s, far, R = x[:5]
            wr.writerow([k, w, s, f'{far:.6f}', f'{R:.1f}'] + ([f'{x[5]:.1f}' if len(x) > 5 else ''] if low else []))
    with open(os.path.join(a.out_dir, 'alpha98_nominal.properties'), 'w') as fh:
        fh.write(f'# RESEARCH -> APP. Genuine nominals for signed features of {spec["id"]} (median over reference watches).\n')
        for k in signed:
            if k in nominal:
                fh.write(f'{k}={nominal[k]:.6f}\n')

    # single-photo uncertainty (measurement_uncertainty.py definitions)
    G = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    for r in rows:
        if r['status'] != 'accepted':
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
                G[(fam, 'deg')][w][comp].append(px); G[(fam, 'degR')][w][comp].append(px * R)
        for h, k in batons:
            if r[f'm{h}_usable'] == 'true':
                add(f'{k}_rot', 'rot', fl(r[f'm{h}_rotation_deg']), False)
                add(f'{k}_off', 'rad', fl(r[f'm{h}_local_radial_px'])); add(f'{k}_off', 'tan', fl(r[f'm{h}_local_tangential_px']))
        if sum(r[f'm{h}_usable'] == 'true' for h in rounds) >= 5:
            for h in rounds:
                if r[f'm{h}_usable'] == 'true':
                    add('rounds_off', f'{h}r', fl(r[f'm{h}_local_radial_px'])); add('rounds_off', f'{h}t', fl(r[f'm{h}_local_tangential_px']))
        rs = [fl(r[f'm{h}_radius_err_px']) for h in rounds if r[f'm{h}_usable'] == 'true']
        rs = [x for x in rs if x is not None]
        if len(rs) >= 5:
            ms = median(rs); add('rounds_size', 'size', ms); add('round_size_rel', 'rel', max(abs(x - ms) for x in rs))
        if r['ring_usable'] == 'true':
            add('ring_rot', 'rot', fl(r['ring_rotation_deg']), False)
            add('ring_shift', 'x', fl(r['ring_shift_x_px'])); add('ring_shift', 'y', fl(r['ring_shift_y_px']))
        if r['m12_usable'] == 'true':
            add('twelve_lateral', 'tan', fl(r['m12_local_tangential_px']))
            add('twelve_centreline', 'rot', fl(r['m12_rotation_deg']), False)
            add('twelve_sides', 'l', fl(r['m12_left_side_err_deg']), False); add('twelve_sides', 'r', fl(r['m12_right_side_err_deg']), False)
    out = {}
    lines = [f'# {spec["id"]} measurement-uncertainty allowances (genuine photos only)', '',
             'Written by `build_sub_reference.py` (measurement_uncertainty.py definitions). sigma = pooled within-watch SD of',
             'one photo\'s reading over every genuine physical watch with 2+ usable non-shared photos. CLEAR only when the excess',
             f'beyond the genuine maximum exceeds K = {K:g} sigma.', '',
             '| family | unit | sigma | K x sigma | watches | photos | dof |', '|---|---|---:|---:|---:|---:|---:|']
    for (fam, unit) in sorted(G):
        groups = [lst for w in G[(fam, unit)].values() for lst in w.values()]
        s, dof, ng, nv = pooled(groups)
        nw = sum(1 for w in G[(fam, unit)].values() if any(len(l) >= 2 for l in w.values()))
        if not math.isfinite(s):
            continue                                   # no repeat photos: no allowance (the feature can never be CLEAR)
        if fam in a.no_allowance:
            lines.append(f'| {fam} | {unit} | withheld (downgraded: at most worth a look) | | {nw} | {nv} | {dof} |')
            continue
        out[f'{fam}.{unit}'] = s; out[f'{fam}.{unit}.watches'] = nw
        lines.append(f'| {fam} | {unit} | {s:.6f} | {K * s:.6f} | {nw} | {nv} | {dof} |')
    # 12 triangle side agreement (Alpha101 edge-consistency limit), relative to the triangle nominal
    nom12p = os.path.join(a.out_dir, 'm12_nominal.properties')
    if os.path.exists(nom12p):
        nom12 = {l.split('=')[0].strip(): float(l.split('=')[1]) for l in open(nom12p) if '=' in l and not l.startswith('#')}
        sd = defaultdict(list)
        for r in csv.DictReader(open(a.per_photo)):
            if r['group'] != 'genuine_population' or r['status'] != 'accepted' or r['photo_id'] in shared or r['m12_usable'] != 'true':
                continue
            L, Rr = fl(r['m12_left_side_err_deg']), fl(r['m12_right_side_err_deg'])
            if L is None or Rr is None:
                continue
            sd[r['physical_watch_id']].append((L - nom12['left_side_deg']) - (Rr - nom12['right_side_deg']))
        multi = {w: v for w, v in sd.items() if len(v) >= 2}
        if multi:
            meds = {w: median(v) for w, v in multi.items()}
            devs = [abs(x - meds[w]) for w, v in multi.items() for x in v]
            mad = 1.4826 * median(devs)
            lim = max(abs(m) for m in meds.values()) + K * mad
            out['twelve_sides_agreement.limit'] = lim; out['twelve_sides_agreement.limit.watches'] = len(multi)
            lines += ['', f'12 side agreement: {len(multi)} watches with 2+ photos; max per-watch median |left - right| '
                      f'{max(abs(m) for m in meds.values()):.3f} deg; robust spread {mad:.3f} deg; limit {lim:.3f} deg.']
    with open(os.path.join(a.out_dir, 'alpha99_uncertainty.properties'), 'w') as fh:
        fh.write(f'# RESEARCH -> APP. {spec["id"]} single-photo measurement uncertainty (pooled within-watch SD, genuine only).\n'
                 '# Written by tools/research/sub124060/build_sub_reference.py.\n')
        fh.write(f'k_sigma={K:g}\n')
        for k in sorted(out):
            v = out[k]; fh.write(f'{k}={v:.6f}\n' if isinstance(v, float) else f'{k}={v}\n')
    summary = ['', '## Genuine reference', '', '| feature | watches | genuine max (far) |', '|---|---:|---:|']
    for k in feats:
        fs = [x[3] for x in out_rows if x[0] == k and len(x) == 5]
        if fs:
            summary.append(f'| {k} | {len(fs)} | {max(fs):.4f} |')
    summary += ['', 'Nominals: ' + ', '.join(f'{k} {v:+.4f}' for k, v in nominal.items())]
    with open(os.path.join(a.out_dir, 'uncertainty.md'), 'w') as fh:
        fh.write('\n'.join(lines + summary) + '\n')
    print('\n'.join(lines + summary))


if __name__ == '__main__':
    main()
