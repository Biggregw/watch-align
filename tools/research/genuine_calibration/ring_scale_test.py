"""Owner-approved research test (24 Oct 2026): the GMT ring-scale reference correction inside the research comparison only.

    python -m tools.research.genuine_calibration.ring_scale_test GCAL_DIR [--md out.md] [--json out.json]

The thresholds are FIXED: the shared eight-reading candidates (genuine scales and k 3.83 / 4.56) fitted once on all
genuine watches, exactly as ResearchComparison uses them. Nothing is recalibrated, for any family. The only change is that
a GMT photo's ring scale is measured against a GMT centre instead of the pooled genuine centre. To avoid judging a watch
with its own data, each GMT watch's centre is the median of the OTHER GMT watches (leave-one-watch-out); the full-data
GMT centre is reported too. The Submariner families are untouched.

Checks:
  1. genuine whole-watch alarms per family, before and after, at both fixed operating points;
  2. every individual-marker reading and flag is identical before and after (the correction touches the ring scale only);
  3. synthetic ring defects on GMT photos (image-level ring moves, turns and a +0.5 % scale), before and after; and a
     -0.5 % / +-0.3 % scale defect applied to the measured ring scale of the base photos (the ring-scale measurement
     responds 1:1 to an image-level scale: analyse.py synthetic gain), before and after.
"""
from __future__ import annotations

import argparse
import json
from collections import defaultdict

import numpy as np

from . import analyse as A
from .compare_points import POINTS, full_model, new_flags

GMT = 'gmt_126710'


def gmt_centres(fam):
    """leave-one-watch-out GMT ring-scale centre per GMT watch, and the full-data centre"""
    rows = [(p['watch'], A._f(p['row'].get('ring.scale_pct'))) for p in fam[GMT]['photos']]
    rows = [(w, v) for w, v in rows if v is not None]
    full = A.wquantile([v for _, v in rows], A.weights([w for w, _ in rows]), 0.5)
    loo = {}
    for w in {w for w, _ in rows}:
        rest = [(x, v) for x, v in rows if x != w]
        loo[w] = A.wquantile([v for _, v in rest], A.weights([x for x, _ in rest]), 0.5)
    return loo, full


def corrected_ring(ring, layout, watch, loo, pooled_centre):
    """the ring readings with a GMT photo's scale re-centred on the GMT centre (other families unchanged)"""
    if not ring or layout != GMT or watch not in loo:
        return ring
    r = dict(ring)
    r['ring.scale'] = ring['ring.scale'] - (loo[watch] - pooled_centre)
    return r


def run(root):
    fam = A.load(root)
    groups, sig, ks = full_model(fam)
    pooled = groups['ring.scale'].centre
    loo, full = gmt_centres(fam)
    out = {'thresholds': {'k': ks, 'sigma': sig, 'ring_scale_limits_pct': {t: ks[t] * sig['ring.scale'] for t in ks},
                          'pooled_ring_scale_centre': pooled, 'gmt_centre_full_data': full,
                          'gmt_centre_leave_one_out_range': (min(loo.values()), max(loo.values()))}}
    # 1 and 2: genuine photos
    alarms = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    marker_changes = 0
    marker_flags = 0
    compared = 0
    ring_flag_changes = []
    for layout, F in fam.items():
        photos = {p['sha'][:16]: p for p in F['photos']}
        for rec in F['base']:
            p = photos.get(rec.get('photo_id'))
            if p is None or not rec.get('ok'):
                continue
            mk, ring = A.record_values(rec, layout)
            mk = {h: v for h, v in mk.items() if h in p['eligible']}
            cring = corrected_ring(ring, layout, p['watch'], loo, pooled)
            for name, t in POINTS:
                before = new_flags(mk, ring, groups, sig, ks[t])
                after = new_flags(mk, cring, groups, sig, ks[t])
                bm, am = [h for h in before if h != 'ring'], [h for h in after if h != 'ring']
                marker_changes += bm != am
                compared += 1
                marker_flags += len(bm)
                alarms[name][layout]['before'].append((p['watch'], bool(before)))
                alarms[name][layout]['after'].append((p['watch'], bool(after)))
                if ('ring' in before) != ('ring' in after):
                    ring_flag_changes.append((name, layout, p['watch'], 'ring' in before, 'ring' in after,
                                              ring['ring.scale'] if ring else None))
    def wrate(rows):
        by = defaultdict(list)
        for w, f in rows:
            by[w].append(f)
        return float(np.mean([np.mean(v) for v in by.values()])), len(by)
    out['genuine'] = {name: {layout: {k: wrate(v) for k, v in d.items()} for layout, d in per.items()} for name, per in alarms.items()}
    out['individual_markers'] = {'photos_x_points_with_any_marker_flag_change': marker_changes, 'photo_x_point_comparisons': compared, 'marker_flags_before': marker_flags}
    out['ring_flag_changes'] = ring_flag_changes
    # 3: synthetic ring defects on GMT photos
    syn = defaultdict(lambda: defaultdict(lambda: [0, 0]))
    F = fam[GMT]
    watch_of = {p['sha'][:16]: p['watch'] for p in F['photos']}
    base = {r['photo_id']: r for r in F['synth'] if r.get('variant') == 'base' and r.get('ok')}
    for rec in F['synth']:
        kind, moves = A.parse_variant(rec.get('variant', ''))
        if not rec.get('ok') or rec['photo_id'] not in base:
            continue
        w = watch_of.get(rec['photo_id'])
        mk, ring = A.record_values(rec, GMT)
        if kind == 'ring' and ring:
            _, k, a = moves[0]
            key = 'image-level ring %s %s' % ({'t': 'moved', 'rot': 'turned', 'scale': 'scaled'}[k],
                                               '%.1f %% R' % (100 * a) if k == 't' else '%+.1f°' % a if k == 'rot' else '%+.1f %%' % (100 * a))
            for name, t in POINTS:
                for when, rr in (('before', ring), ('after', corrected_ring(ring, GMT, w, loo, pooled))):
                    f = 'ring' in new_flags({}, rr, groups, sig, ks[t])
                    syn[(key, name)][when][0] += f
                    syn[(key, name)][when][1] += 1
                # marker readings on a ring-defect photo: unchanged by the correction
        if kind == 'base' and ring:
            for d in (-0.5, -0.3, +0.3, +0.5):
                rr = dict(ring)
                rr['ring.scale'] = ring['ring.scale'] + d
                key = 'ring scale defect %+.1f %% (applied to the measured base)' % d
                for name, t in POINTS:
                    for when, x in (('before', rr), ('after', corrected_ring(rr, GMT, w, loo, pooled))):
                        f = 'ring' in new_flags({}, x, groups, sig, ks[t])
                        syn[(key, name)][when][0] += f
                        syn[(key, name)][when][1] += 1
            for name, t in POINTS:
                for when, x in (('before', ring), ('after', corrected_ring(ring, GMT, w, loo, pooled))):
                    f = 'ring' in new_flags({}, x, groups, sig, ks[t])
                    syn[('no defect (GMT base photo)', name)][when][0] += f
                    syn[('no defect (GMT base photo)', name)][when][1] += 1
    out['synthetic_gmt'] = {'%s|%s' % k: dict(v) for k, v in syn.items()}
    return out


def markdown(out):
    th = out['thresholds']
    L = ['# GMT ring-scale reference correction, research comparison only (fixed thresholds)', '',
         'Fixed thresholds (unchanged for every family): k %.2f (5 %%) and %.2f (stricter); ring-scale limit %.3f %% and %.3f %% '
         'around the pooled genuine centre %+.3f %%. GMT centre: %+.3f %% (all GMT watches; each GMT watch is judged with the '
         'median of the other GMT watches, range %+.3f to %+.3f %%).' % (
             th['k'][0.05], th['k'][0.01], th['ring_scale_limits_pct'][0.05], th['ring_scale_limits_pct'][0.01],
             th['pooled_ring_scale_centre'], th['gmt_centre_full_data'], *th['gmt_centre_leave_one_out_range']), '',
         '## Genuine whole-watch alarms (share of genuine watch photos; watches)', '',
         '| Point | Family | Before | After |', '|---|---|---:|---:|']
    for name, per in out['genuine'].items():
        for layout, d in sorted(per.items()):
            L.append('| %s | %s | %.1f %% (%d) | %.1f %% (%d) |' % (name, layout, 100 * d['before'][0], d['before'][1],
                                                               100 * d['after'][0], d['after'][1]))
    im = out['individual_markers']
    L += ['', '## Individual markers', '',
          'Genuine photo x operating-point comparisons whose individual-marker flags changed: **%d of %d** (%d marker flags '
          'before, identical after). The correction touches the ring scale only.' % (
              im['photos_x_points_with_any_marker_flag_change'], im['photo_x_point_comparisons'], im['marker_flags_before']),
          '', 'Ring-flag changes on genuine photos (point, family, watch, before, after, raw ring scale %%): %s' % (
              '; '.join('%s %s %s %s->%s %+.3f' % (a, b, c, d, e, f) for a, b, c, d, e, f in out['ring_flag_changes']) or 'none'),
          '', '## Synthetic ring defects on GMT photos (detected / photos)', '', '| Defect | Point | Before | After |', '|---|---|---:|---:|']
    for key in sorted(out['synthetic_gmt']):
        d, p = key.split('|')
        v = out['synthetic_gmt'][key]
        L.append('| %s | %s | %d/%d | %d/%d |' % (d, p, v['before'][0], v['before'][1], v['after'][0], v['after'][1]))
    return '\n'.join(L) + '\n'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('root')
    ap.add_argument('--md')
    ap.add_argument('--json')
    a = ap.parse_args(argv)
    out = run(a.root)
    md = markdown(out)
    if a.md:
        open(a.md, 'w').write(md)
    if a.json:
        open(a.json, 'w').write(json.dumps(out, indent=1, default=str) + '\n')
    print(md)


if __name__ == '__main__':
    main()
