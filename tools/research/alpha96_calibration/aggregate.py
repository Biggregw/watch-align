#!/usr/bin/env python3
"""RESEARCH ONLY. Aggregate Alpha96Calib runner output into per-photo, watch-level and distribution tables.

Inputs: one or more (manifest.csv, runner.csv) pairs, joined on local_path.
Outputs (out dir):
  per_photo.csv      identity/provenance + every runner field + R-normalised positional fields
  watch_level.csv    one row per physical watch and metric: n usable photos, primary photo, median, MAD, min, max
  report.md          inventory, genuine-only distributions (population vs marketplace candidates kept apart),
                     within-watch repeatability, replica regression checks. No thresholds are derived.

Replica rows are reported only as regression controls and never enter any genuine statistic.
"""
import argparse
import csv
import math
import os
from collections import defaultdict
from statistics import median

ROUNDS = (1, 2, 4, 5, 7, 8, 10, 11)
POS_FIELDS = ('right_px', 'down_px', 'raw_px', 'radial_px', 'tangential_px', 'local_px', 'local_radial_px', 'local_tangential_px')

# Metric -> (column, unit). Positional metrics are reported in px and in units of dial radius R.
METRICS = [
    ('ring_shift', 'ring_shift_px', 'px'), ('ring_shift_R', 'ring_shift_R', 'R'),
    ('ring_rotation', 'ring_rotation_deg', 'deg'), ('ring_scale', 'ring_scale_pct', '%'),
    ('m6_rotation', 'm6_rotation_deg', 'deg'), ('m6_local', 'm6_local_px', 'px'), ('m6_local_R', 'm6_local_R', 'R'),
    ('m6_raw', 'm6_raw_px', 'px'), ('m6_raw_R', 'm6_raw_R', 'R'),
    ('m6_local_tangential_R', 'm6_local_tangential_R', 'R'), ('m6_local_radial_R', 'm6_local_radial_R', 'R'),
    ('m9_rotation', 'm9_rotation_deg', 'deg'), ('m9_local', 'm9_local_px', 'px'), ('m9_local_R', 'm9_local_R', 'R'),
    ('m9_raw', 'm9_raw_px', 'px'), ('m9_raw_R', 'm9_raw_R', 'R'),
    ('m9_local_tangential_R', 'm9_local_tangential_R', 'R'), ('m9_local_radial_R', 'm9_local_radial_R', 'R'),
    ('m12_centreline_rotation', 'm12_rotation_deg', 'deg'), ('m12_raw', 'm12_raw_px', 'px'), ('m12_raw_R', 'm12_raw_R', 'R'),
    ('m12_local_R', 'm12_local_R', 'R'), ('m12_right_R', 'm12_right_R', 'R'), ('m12_down_R', 'm12_down_R', 'R'),
    ('m12_left_side_err', 'm12_left_side_err_deg', 'deg'), ('m12_right_side_err', 'm12_right_side_err_deg', 'deg'),
    ('m12_base_tilt', 'm12_base_tilt_deg', 'deg'),
    ('rounds_max_local', 'rounds_max_local_px', 'px'), ('rounds_max_local_R', 'rounds_max_local_R', 'R'),
    ('rounds_usable', 'rounds_usable', 'count'),
]


def f(x):
    try:
        v = float(x)
        return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def mad(v):
    m = median(v)
    return median([abs(x - m) for x in v])


def pct(v, p):
    s = sorted(v)
    if not s:
        return None
    k = (len(s) - 1) * p / 100.0
    lo, hi = math.floor(k), math.ceil(k)
    return s[lo] + (s[hi] - s[lo]) * (k - lo)


def load(manifest, runner):
    man = {r['local_path']: r for r in csv.DictReader(open(manifest))}
    rows = []
    for r in csv.DictReader(open(runner)):
        m = man.get(r['path'])
        if m is None:
            continue
        row = dict(m)
        row.update(r)
        R = f(r.get('dial_radius_px'))
        for mk in ['m12', 'm6', 'm9'] + [f'm{h}' for h in ROUNDS]:
            for fld in POS_FIELDS:
                v = f(r.get(f'{mk}_{fld}'))
                row[f"{mk}_{fld.replace('_px', '')}_R"] = '' if v is None or not R else f'{v / R:.6f}'
        row['ring_shift_R'] = '' if f(r.get('ring_shift_px')) is None or not R else f"{f(r['ring_shift_px']) / R:.6f}"
        locs = [f(r.get(f'm{h}_local_px')) for h in ROUNDS if r.get(f'm{h}_usable') == 'true']
        locs = [x for x in locs if x is not None]
        row['rounds_usable'] = str(sum(r.get(f'm{h}_usable') == 'true' for h in ROUNDS)) if r.get('status') == 'accepted' else ''
        row['rounds_max_local_px'] = f'{max(locs):.5f}' if locs else ''
        row['rounds_max_local_R'] = f'{max(locs) / R:.6f}' if locs and R else ''
        rows.append(row)
    return rows


def watch_level(rows):
    out = []
    by = defaultdict(list)
    for r in rows:
        if r['class_label'] == 'gen' and r.get('status') == 'accepted':
            by[(r['group'], r['physical_watch_id'])].append(r)
    for (grp, wid), rs in sorted(by.items()):
        # primary photo: largest dial radius among accepted photos (most pixels on the dial), then lowest tick rms
        prim = sorted(rs, key=lambda r: (-(f(r['dial_radius_px']) or 0), f(r['tick_rms_px']) or 9))[0]
        for name, col, unit in METRICS:
            v = [f(r.get(col)) for r in rs]
            v = [x for x in v if x is not None]
            out.append(dict(group=grp, physical_watch_id=wid, model=rs[0]['model'], provenance=rs[0]['provenance'],
                            metric=name, unit=unit, photos_accepted=len(rs), photos_usable=len(v),
                            primary_photo=prim['photo_id'],
                            primary_value='' if f(prim.get(col)) is None else f"{f(prim.get(col)):.5f}",
                            median='' if not v else f'{median(v):.5f}', mad='' if not v else f'{mad(v):.5f}',
                            min='' if not v else f'{min(v):.5f}', max='' if not v else f'{max(v):.5f}'))
    return out


def dist_table(rows, group):
    """Between-watch distribution: one value per watch (median of its photos) per metric."""
    lines = ['| Metric | unit | watches | photos usable | photos withheld | median | MAD | min | max | P10 | P90 |',
             '|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
    acc = [r for r in rows if r['group'] == group and r.get('status') == 'accepted']
    for name, col, unit in METRICS:
        per_w = defaultdict(list); withheld = 0
        for r in acc:
            v = f(r.get(col))
            if v is None:
                withheld += 1
            else:
                per_w[r['physical_watch_id']].append(v)
        wv = [median(v) for v in per_w.values()]
        n_ph = sum(len(v) for v in per_w.values())
        if not wv:
            lines.append(f'| {name} | {unit} | 0 | 0 | {withheld} | – | – | – | – | – | – |'); continue
        p10 = pct(wv, 10) if len(wv) >= 10 else None
        p90 = pct(wv, 90) if len(wv) >= 10 else None
        fmt = lambda x: '–' if x is None else (f'{x:.4f}' if unit == 'R' else f'{x:.3f}')
        lines.append(f'| {name} | {unit} | {len(wv)} | {n_ph} | {withheld} | {fmt(median(wv))} | {fmt(mad(wv))} | '
                     f'{fmt(min(wv))} | {fmt(max(wv))} | {fmt(p10)} | {fmt(p90)} |')
    return '\n'.join(lines)


REGRESSION = {
    # photo_id: {column: expected value as shown on the Alpha96 phone}
    'RL_THEONE_BLNR': {'ring_shift_px': 0.53, 'ring_scale_pct': 0.19, 'ring_rotation_deg': 0.06,
                       'm12_right_px': -0.91, 'm12_down_px': 1.20, 'm12_rotation_deg': 0.96,
                       'm12_left_side_err_deg': 0.99, 'm12_right_side_err_deg': 0.89,
                       'm6_right_px': -1.24, 'm6_down_px': 0.83, 'm6_rotation_deg': 0.33, 'm6_local_px': 0.78,
                       'm9_right_px': -0.55, 'm9_down_px': -0.23, 'm9_rotation_deg': -0.85, 'm9_local_px': 0.28,
                       'rounds_usable': 8, 'rounds_max_local_px': 0.66},
    'RL_USER_BATGIRL': {'ring_shift_px': 0.71, 'ring_scale_pct': 0.06, 'ring_rotation_deg': 0.11, 'm12_usable': 'false',
                        'm6_right_px': -0.80, 'm6_down_px': -0.43, 'm6_rotation_deg': 0.78, 'm6_local_px': 0.54,
                        'm9_right_px': 0.13, 'm9_down_px': -1.92, 'm9_rotation_deg': -1.52, 'm9_local_px': 0.93,
                        'rounds_usable': 8, 'rounds_max_local_px': 0.90},
}


def regression(rows):
    lines = ['| Control | field | phone (Alpha96) | runner | match (display rounding ±0.02) |', '|---|---|---:|---:|---|']
    by = {r['photo_id']: r for r in rows}
    allok = True
    for pid, exp in REGRESSION.items():
        r = by.get(pid)
        for col, e in exp.items():
            got = r.get(col) if r else None
            if isinstance(e, str):
                ok = got == e
            else:
                g = f(got); ok = g is not None and abs(g - e) <= 0.02 + 1e-9
            allok &= ok
            lines.append(f"| {pid} | {col} | {e} | {got if got not in (None, '') else '–'} | {'yes' if ok else 'NO'} |")
    return '\n'.join(lines), allok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--pair', nargs=2, action='append', metavar=('MANIFEST', 'RUNNER_CSV'), required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--fetch-log', help='fetch_verified.py log: adds a sha256 restore summary to the report')
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    rows = []
    for m, r in a.pair:
        rows += load(m, r)
    cols = list(rows[0].keys())
    for r in rows:
        for k in r:
            if k not in cols:
                cols.append(k)
    with open(os.path.join(a.out, 'per_photo.csv'), 'w', newline='') as fh:
        w = csv.DictWriter(fh, fieldnames=cols); w.writeheader(); w.writerows(rows)
    wl = watch_level(rows)
    with open(os.path.join(a.out, 'watch_level.csv'), 'w', newline='') as fh:
        w = csv.DictWriter(fh, fieldnames=list(wl[0].keys()) if wl else ['group']); w.writeheader(); w.writerows(wl)

    def count(group, status=None):
        rs = [r for r in rows if r['group'] == group and (status is None or r.get('status') == status)]
        return len(rs), len({r['physical_watch_id'] for r in rs})
    rep = ['# Alpha96 genuine calibration run (research only)', '',
           'Measurement code: the production `AutomaticDialOverlay.build` and `Alpha94MarkerMeasurement.analyse`, run through the',
           'desktop harness (`tools/desktop-harness/run.sh Alpha96Calib`). No measurement maths is duplicated. No thresholds are derived.', '',
           '## Inventory of this run', '',
           '| Group | photos listed | watches | photos present | accepted by Alpha96 pose |', '|---|---:|---:|---:|---:|']
    for g in ('genuine_population', 'gen_candidate', 'rl_control'):
        n, wn = count(g)
        present = sum(1 for r in rows if r['group'] == g and r.get('status') not in ('missing', None))
        acc, _ = count(g, 'accepted')
        rep.append(f'| {g} | {n} | {wn} | {present} | {acc} |')
    if a.fetch_log and os.path.exists(a.fetch_log):
        fl = list(csv.DictReader(open(a.fetch_log)))
        st = defaultdict(lambda: [0, set()])
        for x in fl:
            st[(x['host'], x['status'])][0] += 1
            st[(x['host'], x['status'])][1].add(x['physical_watch_id'])
        rep += ['', '## sha256-verified restore of catalogued photos', '',
                'Only files whose sha256 equals the catalogued value are measured; changed or unreachable listings are not replaced.', '',
                '| host | status | photos | watches |', '|---|---|---:|---:|']
        rep += [f'| {h} | {s_} | {v[0]} | {len(v[1])} |' for (h, s_), v in sorted(st.items())]
    rep += ['', '## Genuine population (provenance-strong: established dealer, auction house, Rolex CPO)', '',
            dist_table(rows, 'genuine_population'), '',
            '## Marketplace genuine candidates (seller-asserted; descriptive only, NOT population evidence)', '',
            dist_table(rows, 'gen_candidate'), '',
            '## Within-watch repeatability (watches with more than one accepted photo)', '']
    rep_lines = []
    for r in wl:
        if r['photos_usable'] > 1 and r['metric'] in ('ring_rotation', 'm6_rotation', 'm6_local_R', 'm9_rotation',
                                                      'm9_local_R', 'rounds_max_local_R', 'm12_centreline_rotation'):
            rep_lines.append(f"- {r['group']} / {r['physical_watch_id']} / {r['metric']}: n={r['photos_usable']} "
                             f"median {r['median']} MAD {r['mad']} range {r['min']}..{r['max']} {r['unit']}")
    rep += rep_lines or ['- none: no genuine watch has more than one accepted photo in this run']
    tab, ok = regression(rows)
    rep += ['', '## Replica regression controls (validation only; never used for genuine statistics)', '',
            f"All phone values reproduced: **{'yes' if ok else 'NO'}**", '', tab, '']
    open(os.path.join(a.out, 'report.md'), 'w').write('\n'.join(rep) + '\n')
    print('\n'.join(rep))


if __name__ == '__main__':
    main()
