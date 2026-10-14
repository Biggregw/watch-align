#!/usr/bin/env python3
"""Watch Align metal alignment: the 12 triangle's RADIAL photo-to-photo uncertainty, the one 12 position family Alpha99
never derived (its "track" reading therefore had no allowance and was capped at worth a look).

Same definition and the same genuine photos as the existing allowances (measurement_uncertainty.py /
build_sub_reference.py): pooled within-watch SD of one photo's m12_local_radial reading over every genuine physical watch
with 2+ usable, accepted, non-shared photos, SWE excluded; in R and in px. No replica/QC photo is read; nothing tuned.
Prints properties lines twelve_radial.R / .px / .watches for each model."""
import csv, math, sys
from collections import defaultdict

def fl(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None

def pooled(groups):
    ss = dof = ng = 0
    for g in groups:
        if len(g) < 2: continue
        m = sum(g) / len(g); ss += sum((x - m) ** 2 for x in g); dof += len(g) - 1; ng += 1
    return (math.sqrt(ss / dof) if dof else float('nan')), ng

def run(label, per_photo, shared_csv, url_of):
    shared = {r['photo_id'] for r in csv.DictReader(open(shared_csv)) if r.get('shared_dial') == '1'}
    gR, gp = defaultdict(list), defaultdict(list)
    for r in csv.DictReader(open(per_photo)):
        if r['group'] != 'genuine_population' or r['status'] != 'accepted' or r['photo_id'] in shared or r['m12_usable'] != 'true':
            continue
        if 'swisswatchexpo' in url_of(r): continue
        R, v = fl(r['dial_radius_px']), fl(r['m12_local_radial_px'])
        if not R or v is None: continue
        gR[r['physical_watch_id']].append(v / R); gp[r['physical_watch_id']].append(v)
    sR, n = pooled(gR.values()); sp, _ = pooled(gp.values())
    print(f'# {label}: {n} genuine watches with 2+ photos')
    print(f'twelve_radial.R={sR:.6f}\ntwelve_radial.R.watches={n}\ntwelve_radial.px={sp:.6f}\ntwelve_radial.px.watches={n}')

if __name__ == '__main__':
    C = 'tools/research/alpha96_calibration'
    cat = {r['photo_id']: r for r in csv.DictReader(open(f'{C}/catalogue_provenance_strong.csv'))}
    run('gmt_126710', f'{C}/results/ci_run_37500197377/per_photo.csv', f'{C}/results/dedup/photos.csv',
        lambda r: cat.get(r['photo_id'], {}).get('image_url', ''))
    S = 'tools/research/sub124060/results/runner'
    # 124060: its 12 allowances are the more cautious of filtered / unfiltered inputs (edge_safe_uncertainty.py), so the
    # larger value is kept here as well (per_photo.csv: 0.000984 R over 22 watches; edge-consistent: 0.000665 over 19)
    run('submariner_124060', f'{S}/per_photo.csv', f'{S}/dedup.csv', lambda r: r.get('image_url', ''))
