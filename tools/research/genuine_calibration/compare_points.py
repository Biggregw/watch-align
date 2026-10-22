"""Three operating points on identical photographs and markers (research only).

    python -m tools.research.genuine_calibration.compare_points GCAL_DIR [--qc QC_RECORDS.jsonl ...] [--cases qc_cases.csv]
                                                                [--json out.json] [--md out.md]

  existing   the app's frozen strict-genuine standard (and its replica-QC "Clearly off"), as it reads these photos
  new 5 %    the shared eight-reading structure at k 3.83 (whole-watch genuine false alarms about 5 %)
  stricter   the same at k 4.56 (about 1 %; 2.4 % cross-validated)

Genuine false alarms: every genuine photo is judged by scales and k fitted WITHOUT its watch (5-fold over watches), and
reported by family, dealer and physical watch with the markers that alarmed. Only markers eligible under the archive
mask are judged, for all three points. Defect detection: the synthetic image-level defects (by family) and the reviewed
real QC cases (qc_cases.csv labels, kept separate: uncertain claims, acceptable imperfections, unlabelled).
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict

import numpy as np

from tools.image_archive.faceon import REQUIRED
from . import analyse as A

SCHEME = A.SCHEMES['shared']
POINTS = (('new 5 %', 0.05), ('stricter', 0.01))
# research correction (offsets.py; NOT approved, not applied in the app): ring scale centred per family
CORRECTED = 'new 5 % + family ring-scale centre (research)'


def fold_models(fam, folds=5, corrected=False):
    """per watch: (sigma, k per target, groups) fitted on the other folds; corrected: ring scale centred per family on
    the training watches (offsets.py)"""
    obs = A.observations(fam)
    if corrected:
        from . import offsets as OF
    watches = sorted({o['watch'] for o in obs})
    order = sorted(watches, key=lambda w: hashlib.sha256(('points' + w).encode()).hexdigest())
    model = {}
    for f in range(folds):
        test = set(order[f::folds])
        tr = [o for o in obs if o['watch'] not in test]
        if corrected:
            sc = OF.scale_centres(tr, {o['watch'] for o in tr})
            tr = OF.observations(fam, None, sc)
            tr = [o for o in tr if o['watch'] not in test]
        groups = A.build_groups(tr)
        sig = {g: A.scale(groups[g]) for g in SCHEME}
        pm = A.photo_max_z(A.photo_devs(tr, groups, SCHEME), sig)
        ks = {t: A.k_at(pm, t) for _, t in POINTS}
        for w in test:
            model[w] = (groups, sig, ks, sc if corrected else {})
    return obs, model


def full_model(fam):
    obs = A.observations(fam)
    groups = A.build_groups(obs)
    sig = {g: A.scale(groups[g]) for g in SCHEME}
    pm = A.photo_max_z(A.photo_devs(obs, groups, SCHEME), sig)
    return groups, sig, {t: A.k_at(pm, t) for _, t in POINTS}


def new_flags(mk, ring, groups, sig, k):
    """hours (and 'ring') whose readings exceed k sigma; the triangle in/out is not judged (top edge stays)"""
    out = []
    for h, vals in sorted(mk.items()):
        for g, v in vals.items():
            if g in SCHEME and g != 'triangle.rad' and groups[g].dev(v) > k * sig[g]:
                out.append(h)
                break
    if ring and any(groups[g].dev(v) > k * sig[g] for g, v in ring.items() if g in SCHEME):
        out.append('ring')
    return out


def genuine(fam):
    obs, model = fold_models(fam)
    _, cmodel = fold_models(fam, corrected=True)
    rows = []
    for layout, F in fam.items():
        photos = {p['sha'][:16]: p for p in F['photos']}
        for rec in F['base']:
            p = photos.get(rec.get('photo_id'))
            if p is None or not rec.get('ok') or p['watch'] not in model:
                continue
            groups, sig, ks, _ = model[p['watch']]
            mk, ring = A.record_values(rec, layout)
            mk = {h: v for h, v in mk.items() if h in p['eligible']}
            pf = A.prod_flags(rec, set(mk))
            r = {'layout': layout, 'source': p['source'], 'watch': p['watch'], 'photo': p['sha'][:16],
                 'existing strict': sorted(h for h, (s, _) in pf.items() if s),
                 'existing Clearly off': sorted(h for h, (_, c) in pf.items() if c)}
            for name, t in POINTS:
                r[name] = new_flags(mk, ring, groups, sig, ks[t])
            cg, cs, ck, cc = cmodel[p['watch']]
            cring = dict(ring)
            if cring and layout in cc:
                cring['ring.scale'] = cring['ring.scale'] - cc[layout]
            r[CORRECTED] = new_flags(mk, cring, cg, cs, ck[0.05])
            rows.append(r)
    return rows


def rate(rows, key, by=None):
    """watch-weighted share of genuine photos with any alarm, overall or per group value"""
    groups = defaultdict(lambda: defaultdict(list))
    for r in rows:
        groups[r[by] if by else 'all'][r['watch']].append(bool(r[key]))
    return {g: (float(np.mean([np.mean(v) for v in ws.values()])), len(ws)) for g, ws in groups.items()}


def synthetic(fam, full):
    groups, sig, ks = full
    res = defaultdict(lambda: defaultdict(lambda: [0, 0]))
    for layout, F in fam.items():
        base = {r['photo_id']: r for r in F['synth'] if r.get('variant') == 'base' and r.get('ok')}
        for rec in F['synth']:
            kind, moves = A.parse_variant(rec.get('variant', ''))
            if kind != 'single' or rec['photo_id'] not in base or not rec.get('ok'):
                continue
            h, k, a = moves[0]
            mk, ring = A.record_values(rec, layout)
            if h not in mk:
                continue
            shape = A.SHAPES[layout][h]
            amt = round(abs(a) * (1 if k == 'rot' else 100), 3)
            key = (layout, shape, {'rot': 'turn', 't': 'sideways', 'r': 'in/out'}[k], amt)
            pf = A.prod_flags(rec, set(mk))
            res[key]['existing strict'][0] += pf.get(h, (False, False))[0]
            res[key]['existing Clearly off'][0] += pf.get(h, (False, False))[1]
            for name, t in POINTS:
                res[key][name][0] += h in new_flags({h: mk[h]}, None, groups, sig, ks[t])
            for name in ('existing strict', 'existing Clearly off') + tuple(n for n, _ in POINTS):
                res[key][name][1] += 1
    return res


def real_cases(qc_records, cases, full):
    groups, sig, ks = full
    lab = defaultdict(list)
    for c in cases:
        lab[c['photo_id']].append(c)
    out = []
    for layout, rec in qc_records:
        cs = lab.get(rec['photo_id']) or lab.get(rec['photo_id'][:16])
        if not cs or not rec.get('ok'):
            continue
        c = cs[0]
        mk, ring = A.record_values(rec, layout)
        # the existing answer judges credible readings only (WatchClassification.credible)
        pf = A.prod_flags(rec, {int(m['h']) for m in rec.get('markers') or [] if m.get('cred')})
        row = {'case': c['case_id'], 'photo': rec['photo_id'], 'layout': layout, 'rpx': rec.get('rpx'),
               'label_status': c['label_status'], 'claimed_marker': c['marker'], 'verdict': c['reviewer_verdict'],
               'eligible_new': sorted(mk), 'existing strict': sorted(h for h, (s, _) in pf.items() if s),
               'existing Clearly off': sorted(h for h, (_, cc) in pf.items() if cc)}
        for name, t in POINTS:
            row[name] = new_flags(mk, ring, groups, sig, ks[t])
        out.append(row)
    return out


def run(root, qc=(), cases_path=None):
    fam = A.load(root)
    full = full_model(fam)
    g = genuine(fam)
    keys = ('existing strict', 'existing Clearly off') + tuple(n for n, _ in POINTS) + (CORRECTED,)
    out = {'k': full[2], 'sigma': full[1],
           'genuine': {k: {'all': rate(g, k)['all'], 'family': rate(g, k, 'layout'), 'dealer': rate(g, k, 'source')} for k in keys},
           'genuine_watches_flagged': {k: sorted({(r['layout'], r['source'], r['watch'], ' '.join(map(str, r[k]))) for r in g if r[k]})
                                       for k in keys},
           'synthetic': {'%s|%s|%s|%s' % key: {n: v for n, v in d.items()} for key, d in synthetic(fam, full).items()}}
    if qc and cases_path:
        cases = list(csv.DictReader(open(cases_path)))
        recs = []
        for path, layout in qc:
            for line in open(path):
                line = line.strip()
                if line:
                    r = json.loads(line[6:] if line.startswith('QCREC ') else line)
                    recs.append((layout or r.get('model'), r))
        out['real_cases'] = real_cases(recs, cases, full)
    return out


def markdown(out):
    keys = ('existing strict', 'existing Clearly off') + tuple(n for n, _ in POINTS) + (CORRECTED,)
    L = ['# Three operating points on identical photographs (research)', '',
         'k: new 5 %% = %.2f, stricter = %.2f (all genuine watches; the genuine alarms below use out-of-fold k and scales).'
         % (out['k'][0.05], out['k'][0.01]), '', '## Genuine false alarms (share of genuine watch photos with any alarm; watches)', '',
         '| Group | ' + ' | '.join(keys) + ' |', '|---|' + '---:|' * len(keys)]
    g = out['genuine']
    L.append('| **All** | ' + ' | '.join('%.1f %% (%d)' % (100 * g[k]['all'][0], g[k]['all'][1]) for k in keys) + ' |')
    for dim in ('family', 'dealer'):
        for name in sorted(g[keys[0]][dim]):
            L.append('| %s | %s |' % (name, ' | '.join('%.1f %% (%d)' % (100 * g[k][dim][name][0], g[k][dim][name][1]) for k in keys)))
    L += ['', '## Genuine watches flagged (family, dealer, watch: markers)', '']
    for k in keys:
        L.append('- **%s** (%d): %s' % (k, len(out['genuine_watches_flagged'][k]),
                                         '; '.join('%s %s %s: %s' % x for x in out['genuine_watches_flagged'][k]) or 'none'))
    keys = ('existing strict', 'existing Clearly off') + tuple(n for n, _ in POINTS)
    L += ['', '## Synthetic defects detected (moved marker; family, shape, kind, size % R or deg)', '',
          '| Family | Shape | Defect | Size | ' + ' | '.join(keys) + ' |', '|---|---|---|---:|' + '---:|' * len(keys)]
    for key in sorted(out['synthetic'], key=lambda s: (s.split('|')[0], s.split('|')[1], s.split('|')[2], float(s.split('|')[3]))):
        d = out['synthetic'][key]
        L.append('| %s | %s |' % (' | '.join(key.split('|')), ' | '.join('%.2f (%d)' % (d[k][0] / d[k][1], d[k][1]) if d[k][1] else '—' for k in keys)))
    if out.get('real_cases'):
        L += ['', '## Reviewed real QC cases (markers flagged; eligible markers only for the new points)', '',
              '| Case | Photo | Dial px | Label | Claimed | Reviewer | New eligible | ' + ' | '.join(keys) + ' |',
              '|---|---|---:|---|---|---|---|' + '---|' * len(keys)]
        for r in out['real_cases']:
            L.append('| %s | %s | %.0f | %s | %s | %s | %s | %s |' % (
                r['case'], r['photo'][:24], r['rpx'] or 0, r['label_status'], r['claimed_marker'] or '—', r['verdict'],
                ' '.join(map(str, r['eligible_new'])) or '—', ' | '.join(' '.join(map(str, r[k])) or '—' for k in keys)))
    return '\n'.join(L) + '\n'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('root')
    ap.add_argument('--qc', nargs=2, action='append', default=[], metavar=('RECORDS', 'LAYOUT'))
    ap.add_argument('--cases')
    ap.add_argument('--json')
    ap.add_argument('--md')
    a = ap.parse_args(argv)
    out = run(a.root, [(p, l if l != '-' else None) for p, l in a.qc], a.cases)
    md = markdown(out)
    if a.json:
        open(a.json, 'w').write(json.dumps(out, indent=1, default=str) + '\n')
    if a.md:
        open(a.md, 'w').write(md)
    print(md)


if __name__ == '__main__':
    main()
