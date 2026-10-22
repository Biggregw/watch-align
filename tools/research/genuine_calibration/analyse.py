"""Pooled genuine calibration of the MarkerGeometry readings, with defect, production and top-edge comparisons.

    python -m tools.research.genuine_calibration.analyse GCAL_DIR [--json out.json] [--md out.md] [--boot 400]

GCAL_DIR holds one folder per layout (the CI artifact of .github/workflows/genuine-calibration.yml):
  geom/*_photo.csv, *_mask_exclusions.csv   masked GeometryMeasure values (watch calibrator, marker-mask-v1)
  acquired_images.csv, locked_split.csv, eligibility_masks.jsonl, admission.json
  arch_items.csv, arch_base.jsonl, arch_synth.jsonl, replica_masks.jsonl   ArchMeasure (same Java MarkerGeometry)

Statistical unit: the physical watch. Every observation keeps its layout, hour, shape, source, watch and photo. A watch
carries total weight 1 in every statistic, shared equally by its observations, so neither several photos nor several
markers of one watch count as independent watches. Intervals are bootstraps over watches. The genuine readings are
pooled by marker shape and quantity across GMT, Date and No-Date; per-family and per-hour breakdowns test whether that
pooling is supported.

Candidate limits: one common per-marker exceedance probability q is chosen so that the share of genuine watch photos
with ANY reading over its limit (whole-watch false alarm) meets a target; each pooled group's limit is its weighted
(1 - q) quantile of |value - centre|. Out-of-sample false alarms come from 5-fold cross-validation over watches and from
the calibrator's locked holdout watches. Nothing here is deployed.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

from tools.image_archive import eligibility as el
from tools.image_archive.faceon import REQUIRED

SHAPES = el.SHAPES
GROUPS = ('round.tang', 'round.rad', 'round.pos', 'baton.tang', 'baton.rad', 'baton.pos', 'baton.tilt',
          'marker.pos', 'triangle.tang', 'triangle.rad', 'triangle.tilt', 'ring.place', 'ring.rot', 'ring.scale')
UNITS = {'tilt': 'deg', 'rot': 'deg', 'scale': '%', 'tang': '% R', 'rad': '% R', 'pos': '% R', 'place': '% R'}
SCHEMES = {
    'axis': ('round.tang', 'round.rad', 'baton.tang', 'baton.rad', 'baton.tilt', 'triangle.tang', 'triangle.rad',
             'triangle.tilt', 'ring.place', 'ring.rot', 'ring.scale'),
    'position': ('round.pos', 'baton.pos', 'baton.tilt', 'triangle.tang', 'triangle.rad', 'triangle.tilt',
                 'ring.place', 'ring.rot', 'ring.scale'),
    'shared': ('marker.pos', 'baton.tilt', 'triangle.tang', 'triangle.rad', 'triangle.tilt', 'ring.place', 'ring.rot',
               'ring.scale'),
}
TARGETS = (0.05, 0.01)
# frozen production strict-genuine limits (docs/CALIBRATION_BASELINE_2026-10-20.md, 2026-10-21 top edge); % R, deg
PROD = {'roundPos': 0.671, 'batonPos': 0.752, 'batonTilt': 1.096, 'triSide': 0.437, 'triInout': 0.373,
        'triInoutTop': 0.40, 'triTilt': 1.174}
RNG = np.random.default_rng(20261022)


def _f(v):
    try:
        x = float(v)
    except (TypeError, ValueError):
        return None
    return x if math.isfinite(x) else None


def _csv(path):
    with open(path, newline='', encoding='utf-8') as fh:
        return list(csv.DictReader(fh))


def _jsonl(path):
    p = Path(path)
    return [json.loads(x) for x in p.read_text().splitlines() if x.strip()] if p.exists() else []


# ---------------------------------------------------------------------------------------------------------- loading

def load(root):
    """photos (masked GeometryMeasure rows with identity), arch records, masks, admission per layout"""
    root = Path(root)
    fam = {}
    for d in sorted(p for p in root.iterdir() if p.is_dir() and p.name in REQUIRED):
        layout = d.name
        acq = {r['sha256']: r for r in _csv(d / 'acquired_images.csv')} if (d / 'acquired_images.csv').exists() else {}
        part = {r['physical_watch_id']: r['partition'] for r in _csv(d / 'locked_split.csv')} \
            if (d / 'locked_split.csv').exists() else {}
        photos = []
        for f in sorted((d / 'geom').glob('*_gen_photo.csv')):
            for r in _csv(f):
                if not r.get('sha256'):
                    continue
                a = acq.get(r['sha256'], {})
                photos.append({'layout': layout, 'sha': r['sha256'], 'watch': r['physical_watch_id'],
                               'source': a.get('source_name') or 'unknown', 'partition': part.get(r['physical_watch_id'], ''),
                               'eligible': {int(x) for x in (r.get('eligible_marker_ids') or '').split()},
                               'row': r})
        items = {r['photo_id']: r for r in _csv(d / 'arch_items.csv')} if (d / 'arch_items.csv').exists() else {}
        masks = {m['sha256']: m for m in _jsonl(d / 'eligibility_masks.jsonl')}
        masks.update({m['sha256']: m for m in _jsonl(d / 'replica_masks.jsonl')})
        excl = []
        for f in sorted((d / 'geom').glob('*_mask_exclusions.csv')):
            excl += _csv(f)
        adm = json.loads((d / 'admission.json').read_text()) if (d / 'admission.json').exists() else {}
        fam[layout] = {'photos': photos, 'items': items, 'masks': masks, 'exclusions': excl, 'admission': adm,
                       'base': _jsonl(d / 'arch_base.jsonl'), 'synth': _jsonl(d / 'arch_synth.jsonl')}
    return fam


def observations(fam):
    """one record per (photo, marker or ring, quantity): the calibration population"""
    obs = []
    for layout, F in fam.items():
        rpx = {r.get('photo_id'): r.get('rpx') for r in F['base']}
        for p in F['photos']:
            r = p['row']
            key = dict(layout=layout, sha=p['sha'], watch=p['watch'], source=p['source'], partition=p['partition'],
                       rpx=rpx.get(p['sha'][:16]), tilt=_f(r.get('pose_tilt_deg')))
            for h in REQUIRED[layout]:
                shape = SHAPES[layout][h]
                t, rd, tl = (_f(r.get('marker.%d.%s' % (h, q))) for q in ('tang', 'rad', 'tilt'))
                vals = {'tang': t, 'rad': rd}
                if shape != 'round':
                    vals['tilt'] = tl
                if t is not None and rd is not None and shape != 'triangle':
                    vals['pos'] = math.hypot(t, rd)
                for q, v in vals.items():
                    if v is not None:
                        obs.append(dict(key, hour=h, shape=shape, q=q, v=v))
            px, py = _f(r.get('ring.place_x')), _f(r.get('ring.place_y'))
            hours = [int(x) for x in (r.get('ring.markers') or '').split()]
            if px is not None and py is not None:
                obs.append(dict(key, hour=0, shape='ring', q='place_xy', v=(px, py), ring_hours=hours))
            for q, k in (('rot', 'ring.rot_deg'), ('scale', 'ring.scale_pct')):
                v = _f(r.get(k))
                if v is not None:
                    obs.append(dict(key, hour=0, shape='ring', q=q, v=v, ring_hours=hours))
    return obs


def group_of(o):
    """the pooled groups an observation belongs to"""
    s, q = o['shape'], o['q']
    if s == 'ring':
        return ['ring.place'] if q == 'place_xy' else ['ring.' + q]
    out = ['%s.%s' % (s, q)]
    if q == 'pos':
        out.append('marker.pos')
    return out


# ------------------------------------------------------------------------------------------------------- statistics

def wquantile(v, w, p):
    v, w = np.asarray(v, float), np.asarray(w, float)
    if not len(v):
        return float('nan')
    i = np.argsort(v)
    v, c = v[i], np.cumsum(w[i])
    return float(v[min(np.searchsorted(c, p * c[-1]), len(v) - 1)])


def weights(watches):
    """each watch weight 1, shared by its observations"""
    n = Counter(watches)
    return np.array([1.0 / n[w] for w in watches])


class Group:
    """the observations of one pooled group and their deviations d.

    Per-marker readings are deviations from the model's reference geometry (the leave-one-out ring of the other
    markers): d = |value|, and the signed median is reported, never subtracted (a per-position offset would be a change
    to reference geometry). Whole-ring readings are one global constant per measurement, so they are centred on the
    pooled genuine median: d = |value - centre| (placement: distance from the centre point).
    """

    def __init__(self, name, obs):
        self.name, self.obs = name, obs
        self.w = weights([o['watch'] for o in obs])
        self.ring = name.startswith('ring.')
        if name == 'ring.place':
            xy = np.array([o['v'] for o in obs]) if obs else np.zeros((0, 2))
            self.centre = (wquantile(xy[:, 0], self.w, 0.5), wquantile(xy[:, 1], self.w, 0.5)) if obs else (0.0, 0.0)
            self.signed, self.signed_median = None, None
            self.d = np.hypot(xy[:, 0] - self.centre[0], xy[:, 1] - self.centre[1]) if obs else np.zeros(0)
        else:
            self.signed = np.array([o['v'] for o in obs], float)
            self.signed_median = wquantile(self.signed, self.w, 0.5) if obs else 0.0
            self.centre = self.signed_median if self.ring else 0.0
            self.d = np.abs(self.signed - self.centre)

    def dev(self, v):
        if self.name == 'ring.place':
            return math.hypot(v[0] - self.centre[0], v[1] - self.centre[1])
        return abs(v - self.centre)


def build_groups(obs, names=GROUPS):
    by = defaultdict(list)
    for o in obs:
        for g in group_of(o):
            by[g].append(o)
    return {g: Group(g, by.get(g, [])) for g in names}


def boot_quantiles(G, ps, boot):
    """watch-cluster bootstrap of weighted quantiles of d"""
    watches = sorted({o['watch'] for o in G.obs})
    if not watches:
        return {p: (float('nan'),) * 3 for p in ps}
    idx = defaultdict(list)
    for i, o in enumerate(G.obs):
        idx[o['watch']].append(i)
    per = [np.array(idx[w]) for w in watches]
    est = {p: wquantile(G.d, G.w, p) for p in ps}
    draws = {p: [] for p in ps}
    for _ in range(boot):
        pick = RNG.integers(0, len(per), len(per))
        ii = np.concatenate([per[k] for k in pick])
        ww = np.concatenate([np.full(len(per[k]), 1.0 / len(per[k])) for k in pick])
        for p in ps:
            draws[p].append(wquantile(G.d[ii], ww, p))
    return {p: (est[p], float(np.percentile(draws[p], 5)), float(np.percentile(draws[p], 95))) for p in ps}


def describe(G, boot):
    watches = {o['watch'] for o in G.obs}
    src = Counter()
    for w, s in {(o['watch'], o['source']) for o in G.obs}:
        src[s] += 1
    q = boot_quantiles(G, (0.5, 0.95, 0.99), boot)
    med_w = {}
    by = defaultdict(list)
    for o, d in zip(G.obs, G.d):
        by[o['watch']].append(d)
    med_w = np.array([np.median(v) for v in by.values()]) if by else np.zeros(0)
    return {'photos': len({o['sha'] for o in G.obs}), 'watches': len(watches), 'observations': len(G.obs),
            'sources': len([s for s in src if s != 'unknown']), 'watches_by_source': dict(src.most_common()),
            'max_source_share': round(max(src.values()) / len(watches), 3) if watches else None,
            'layouts': dict(Counter(o['layout'] for o in {o['watch']: o for o in G.obs}.values())),
            'centre': G.centre, 'signed_median': G.signed_median, 'median_dev': q[0.5][0], 'robust_sd': float(1.4826 * np.median(med_w)) if len(med_w) else None,
            'p95': q[0.95], 'p99': q[0.99], 'max': float(G.d.max()) if len(G.d) else None}


def components(G):
    """photographic repeatability (same watch, same marker, other photos) against between-watch genuine variation"""
    if G.name == 'ring.place':
        return None
    cell = defaultdict(list)
    for o in G.obs:
        cell[(o['watch'], o['layout'], o['hour'])].append(o['v'])
    within = [x - np.median(v) for v in cell.values() if len(v) >= 2 for x in v]
    meds = defaultdict(list)
    for (w, _, _), v in cell.items():
        meds[w].append(np.median(v))
    between = [np.median(v) for v in meds.values()]
    rs = lambda a: float(1.4826 * np.median(np.abs(np.asarray(a) - np.median(a)))) if len(a) >= 3 else None
    return {'within_watch_photo_sd': rs(within), 'within_cells': sum(1 for v in cell.values() if len(v) >= 2),
            'between_watch_sd': rs(between), 'watches': len(between)}


def by_family(G, boot):
    out = {}
    for layout in sorted({o['layout'] for o in G.obs}):
        sub = Group(G.name, [o for o in G.obs if o['layout'] == layout])
        sub.centre = G.centre
        sub.d = np.array([G.dev(o['v']) for o in sub.obs])
        q = boot_quantiles(sub, (0.95, 0.99), boot)
        out[layout] = {'watches': len({o['watch'] for o in sub.obs}), 'photos': len({o['sha'] for o in sub.obs}),
                       'signed_median': (None if sub.signed is None else wquantile(sub.signed, sub.w, 0.5)),
                       'p95': q[0.95], 'p99': q[0.99]}
    return out


def by_hour(G):
    """per clock position: watches, signed median (watch-weighted) and p95 of the pooled deviation; Kruskal-Wallis
    over hours on the watch-level signed medians"""
    if G.name.startswith('ring') or G.name == 'marker.pos':
        return None
    cells = defaultdict(list)
    for o, d in zip(G.obs, G.d):
        cells[(o['layout'], o['hour'])].append((o['watch'], o['v'], d))
    out, samples = {}, defaultdict(list)
    for (layout, h), rows in sorted(cells.items()):
        w = weights([r[0] for r in rows])
        out['%s/%d' % (layout, h)] = {'watches': len({r[0] for r in rows}),
                                      'signed_median': wquantile([r[1] for r in rows], w, 0.5),
                                      'p95_dev': wquantile([r[2] for r in rows], w, 0.95)}
        per = defaultdict(list)
        for wid, v, _ in rows:
            per[wid].append(v)
        samples[h] += [float(np.median(v)) for v in per.values()]
    kw = None
    try:
        from scipy.stats import kruskal
        s = [v for v in samples.values() if len(v) >= 5]
        if len(s) >= 2:
            kw = float(kruskal(*s).pvalue)
    except Exception:
        kw = None
    return {'cells': out, 'kruskal_p_hours': kw}


def conditions(G):
    """p95 of the deviation by dial radius (px) and by dial-plane tilt (deg): photographic uncertainty"""
    out = {}
    for name, key, bins in (('rpx', 'rpx', ((250, 350), (350, 500), (500, 1e9))),
                            ('tilt', 'tilt', ((0, 3), (3, 6), (6, 11)))):
        for lo, hi in bins:
            keep = [i for i, o in enumerate(G.obs) if o.get(key) is not None and lo <= o[key] < hi]
            if keep:
                w = weights([G.obs[i]['watch'] for i in keep])
                out['%s %g-%g' % (name, lo, hi if hi < 1e8 else float('inf'))] = {
                    'watches': len({G.obs[i]['watch'] for i in keep}), 'p95': wquantile(G.d[keep], w, 0.95)}
    return out


def sensitivity(G):
    """p99 without each source, and the largest p99 change from leaving out one watch"""
    full = wquantile(G.d, G.w, 0.99)
    out = {}
    for s in sorted({o['source'] for o in G.obs}):
        keep = [i for i, o in enumerate(G.obs) if o['source'] != s]
        if keep:
            w = weights([G.obs[i]['watch'] for i in keep])
            out[s] = wquantile(G.d[keep], w, 0.99)
    worst = 0.0
    watches = sorted({o['watch'] for o in G.obs})
    if len(watches) <= 400:
        for wid in watches:
            keep = [i for i, o in enumerate(G.obs) if o['watch'] != wid]
            if keep:
                w = weights([G.obs[i]['watch'] for i in keep])
                worst = max(worst, abs(wquantile(G.d[keep], w, 0.99) - full))
    return {'p99': full, 'p99_without_source': out, 'max_p99_change_one_watch': worst}


# ---------------------------------------------------------------------------------------------- whole-watch alarms

def photo_devs(obs, groups, scheme):
    """sha -> (watch, [(group, deviation, hour)]) over the scheme's groups"""
    out = {}
    for o in obs:
        for g in group_of(o):
            if g in scheme:
                rec = out.setdefault(o['sha'], (o['watch'], []))
                rec[1].append((g, groups[g].dev(o['v']), o['hour']))
    return out


def limits_at(groups, scheme, q, watches=None):
    """each group's weighted (1 - q) quantile of d, optionally from a subset of watches"""
    L = {}
    for g in scheme:
        G = groups[g]
        if watches is None:
            L[g] = wquantile(G.d, G.w, 1 - q)
        else:
            keep = [i for i, o in enumerate(G.obs) if o['watch'] in watches]
            L[g] = wquantile(G.d[keep], weights([G.obs[i]['watch'] for i in keep]), 1 - q) if keep else float('nan')
    return L


def alarm_rates(devs, L, watches=None, scale=1.0):
    """(whole-watch photo false-alarm rate, per-marker-reading rate), watch-weighted"""
    pw, mw = defaultdict(list), defaultdict(list)
    for sha, (w, rows) in devs.items():
        if watches is not None and w not in watches:
            continue
        flags = [d > scale * L[g] for g, d, _ in rows]
        pw[w].append(any(flags))
        mw[w] += flags
    if not pw:
        return float('nan'), float('nan')
    return (float(np.mean([np.mean(v) for v in pw.values()])),
            float(np.mean([np.mean(v) for v in mw.values() if v])))


def solve_q(groups, scheme, devs, target, watches=None):
    """the common per-reading exceedance q giving the whole-watch false-alarm target on these watches"""
    lo, hi = 1e-5, 0.2
    for _ in range(40):
        mid = math.sqrt(lo * hi)
        fa, _ = alarm_rates(devs, limits_at(groups, scheme, mid, watches), watches)
        lo, hi = (lo, mid) if fa > target else (mid, hi)
    return math.sqrt(lo * hi)


def calibrate(obs, groups, scheme, partition_of, boot, folds=5):
    devs = photo_devs(obs, groups, scheme)
    all_w = sorted({w for w, _ in devs.values()})
    out = {}
    for target in TARGETS:
        q = solve_q(groups, scheme, devs, target)
        L = limits_at(groups, scheme, q)
        fa, ma = alarm_rates(devs, L)
        # watch bootstrap of the limits (same q)
        draws = defaultdict(list)
        per = defaultdict(list)
        for g in scheme:
            for i, o in enumerate(groups[g].obs):
                per[(g, o['watch'])].append(i)
        for _ in range(boot):
            pick = [all_w[k] for k in RNG.integers(0, len(all_w), len(all_w))]
            for g in scheme:
                ii = [i for w in pick for i in per.get((g, w), [])]
                ww = [1.0 / len(per[(g, w)]) for w in pick for _ in per.get((g, w), [])]
                if ii:
                    draws[g].append(wquantile(groups[g].d[ii], ww, 1 - q))
        ci = {g: (float(np.percentile(v, 5)), float(np.percentile(v, 95))) for g, v in draws.items()}
        # 5-fold cross-validation over watches: limits from 4/5 of the watches at the target, alarms on the rest
        order = sorted(all_w, key=lambda w: hashlib.sha256(('fold' + w).encode()).hexdigest())
        cv_flags = {}
        for k in range(folds):
            test = set(order[k::folds])
            train = set(all_w) - test
            qk = solve_q(groups, scheme, devs, target, train)
            Lk = limits_at(groups, scheme, qk, train)
            for sha, (w, rows) in devs.items():
                if w in test:
                    cv_flags.setdefault(w, []).append(any(d > Lk[g] for g, d, _ in rows))
        cv = float(np.mean([np.mean(v) for v in cv_flags.values()])) if cv_flags else float('nan')
        # the calibrator's locked split: limits from development + validation, alarms on holdout watches
        devval = {w for w in all_w if partition_of.get(w) in ('development', 'validation')}
        hold = {w for w in all_w if partition_of.get(w) == 'holdout'}
        qd = solve_q(groups, scheme, devs, target, devval)
        Ld = limits_at(groups, scheme, qd, devval)
        hfa, hma = alarm_rates(devs, Ld, hold)
        out[str(target)] = {'q_per_reading': q, 'limits': L, 'limits_90ci': ci, 'whole_watch_fa': fa,
                            'per_reading_fa': ma, 'cv_whole_watch_fa': cv, 'holdout_watches': len(hold),
                            'holdout_whole_watch_fa': hfa, 'holdout_per_reading_fa': hma,
                            'readings_per_photo': float(np.mean([len(r) for _, r in devs.values()]))}
    out['p99_per_reading'] = {'limits': limits_at(groups, scheme, 0.01),
                              'whole_watch_fa': alarm_rates(devs, limits_at(groups, scheme, 0.01))[0]}
    return out


# --------------------------------------------------------------------------------------------- synthetic defects

MV = re.compile(r'^mv:(\d+):(t|r|rot):(-?[0-9.eE+-]+)$')


def parse_variant(v):
    """[(hour, kind, amount)] for marker moves; ('ring', kind, amount) for ring moves; [] for base"""
    if v == 'base':
        return 'base', []
    m = MV.match(v)
    if m:
        return 'single', [(int(m.group(1)), m.group(2), float(m.group(3)))]
    if v.startswith('mm:'):
        out = []
        for part in v[3:].split('|'):
            h, k, a = part.split(':')
            out.append((int(h), k, float(a)))
        return 'multi', out
    if v.startswith('ring:'):
        p = v.split(':')
        amt = math.hypot(float(p[2]), float(p[3])) if p[1] == 't' else float(p[2])
        return 'ring', [('ring', p[1], amt)]
    return 'other', []


def record_values(rec, layout):
    """{hour: {group: value}} for eligible markers, and the ring {group: value}, from one ArchMeasure record"""
    mk, ring = {}, {}
    for m in rec.get('markers') or []:
        g = m.get('mg')
        h = int(m['h'])
        if not g or not g[3] or h not in REQUIRED[layout]:
            continue
        shape = SHAPES[layout][h]
        t, r, a = g[0], g[1], g[2]
        vals = {}
        if t is not None:
            vals['%s.tang' % shape] = t
        if r is not None:
            vals['%s.rad' % shape] = r
        if a is not None and shape != 'round':
            vals['%s.tilt' % shape] = a
        if t is not None and r is not None and shape != 'triangle':
            vals['%s.pos' % shape] = vals['marker.pos'] = math.hypot(t, r)
        mk[h] = vals
    j = rec.get('mg_ring')
    if j:
        ring = {'ring.place': (j[4], j[5]), 'ring.rot': j[3], 'ring.scale': j[2]}
    return mk, ring


def prod_flags(rec, eligible):
    """production strict-genuine and replica-QC (Clearly off) flags per eligible marker (frozen limits)"""
    rpx = rec.get('rpx') or float('nan')
    allow = 50.0 / rpx if rpx and rpx > 0 else 0.0
    out = {}
    for m in rec.get('markers') or []:
        h = int(m['h'])
        if h not in eligible:
            continue
        s = m.get('shape')
        side, inout, tilt, pos = (_f(m.get(k)) for k in ('side', 'inout', 'tilt', 'pos'))
        strict = False
        if s == 'round':
            strict = pos is not None and pos > PROD['roundPos'] + allow
        elif s == 'baton':
            strict = (pos is not None and pos > PROD['batonPos'] + allow) or (tilt is not None and abs(tilt) > PROD['batonTilt'])
        elif s == 'triangle':
            lim = PROD['triInoutTop'] if m.get('top') else PROD['triInout']
            strict = ((side is not None and abs(side) > PROD['triSide'] + allow)
                      or (inout is not None and abs(inout) > lim + allow)
                      or (tilt is not None and abs(tilt) > PROD['triTilt']))
        out[h] = (strict, m.get('state') == 'CLEARLY_OFF')
    return out


def flagged(vals, groups, L, scheme):
    return any(g in scheme and g in L and groups[g].dev(v) > L[g] for g, v in vals.items())


def synthetic(fam, groups, calib):
    """detection of known image-level defects, per scheme and false-alarm target, against production"""
    res = defaultdict(lambda: defaultdict(lambda: [0, 0]))     # key -> outcome -> [hits, n]
    gains = defaultdict(list)
    spill = defaultdict(list)
    ring_err = []
    multi_all = defaultdict(lambda: [0, 0])
    for layout, F in fam.items():
        base = {}
        for rec in F['synth']:
            if rec.get('variant') == 'base' and rec.get('ok'):
                base[rec['photo_id']] = rec
        for rec in F['synth']:
            kind, moves = parse_variant(rec.get('variant', ''))
            b = base.get(rec['photo_id'])
            if kind in ('base', 'other') or b is None or not rec.get('ok'):
                continue
            mk, ring = record_values(rec, layout)
            bmk, bring = record_values(b, layout)
            pf = prod_flags(rec, set(mk))
            bpf = prod_flags(b, set(bmk))
            for scheme, cal in calib.items():
                for target in TARGETS:
                    L = cal[str(target)]['limits']
                    S = SCHEMES[scheme]
                    if kind in ('single', 'multi'):
                        moved = {h for h, _, _ in moves}
                        hits = []
                        for h, k, a in moves:
                            shape = SHAPES[layout][h]
                            amt = round(abs(a) * (1 if k == 'rot' else 100), 3)   # % R or deg
                            key = (scheme, target, kind, shape, k, amt)
                            if h not in mk:
                                res[key]['not_assessable'][1] += 1
                                continue
                            hit = flagged(mk[h], groups, L, S)
                            hits.append(hit)
                            res[key]['detected'][0] += hit
                            res[key]['detected'][1] += 1
                            if scheme == 'axis' and target == TARGETS[0]:
                                s, c = pf.get(h, (False, False))
                                res[('production', None, kind, shape, k, amt)]['strict_genuine'][0] += s
                                res[('production', None, kind, shape, k, amt)]['strict_genuine'][1] += 1
                                res[('production', None, kind, shape, k, amt)]['clearly_off'][0] += c
                                res[('production', None, kind, shape, k, amt)]['clearly_off'][1] += 1
                        if kind == 'multi' and hits and len(hits) == len(moves):
                            multi_all[(scheme, target, len(moves))][0] += all(hits)
                            multi_all[(scheme, target, len(moves))][1] += 1
                        new = [h for h in mk if h not in moved and h in bmk and flagged(mk[h], groups, L, S)
                               and not flagged(bmk[h], groups, L, S)]
                        res[(scheme, target, kind, 'other_markers', '', 0)]['new_false_flag'][0] += bool(new)
                        res[(scheme, target, kind, 'other_markers', '', 0)]['new_false_flag'][1] += 1
                    elif kind == 'ring':
                        _, k, a = moves[0]
                        amt = round(a * (1 if k == 'rot' else 100), 3)
                        key = (scheme, target, 'ring', 'ring', k, amt)
                        if not ring:
                            res[key]['not_assessable'][1] += 1
                            continue
                        res[key]['detected'][0] += flagged(ring, groups, L, S)
                        res[key]['detected'][1] += 1
                        new = [h for h in mk if h in bmk and flagged(mk[h], groups, L, S) and not flagged(bmk[h], groups, L, S)]
                        res[(scheme, target, 'ring', 'marker_flags', k, amt)]['new_false_flag'][0] += bool(new)
                        res[(scheme, target, 'ring', 'marker_flags', k, amt)]['new_false_flag'][1] += 1
            # measured response (scheme-independent)
            if kind == 'single':
                h, k, a = moves[0]
                if h in mk and h in bmk:
                    shape = SHAPES[layout][h]
                    q = {'t': 'tang', 'r': 'rad', 'rot': 'tilt'}[k]
                    g = '%s.%s' % (shape, q)
                    if g in mk[h] and g in bmk[h]:
                        applied = a * (1 if k == 'rot' else 100)
                        gains[(shape, k)].append((mk[h][g] - bmk[h][g]) / applied)
                    for o in mk:
                        if o != h and o in bmk:
                            for gg in mk[o]:
                                if gg in bmk[o] and not gg.endswith('pos'):
                                    spill[(shape, k)].append(abs(mk[o][gg] - bmk[o][gg]) / abs(a * (1 if k == 'rot' else 100)))
            if kind == 'ring' and ring and bring:
                _, k, a = moves[0]
                if k == 't':
                    p = rec['variant'].split(':')
                    dx, dy = float(p[2]) * 100, float(p[3]) * 100
                    mx, my = ring['ring.place'][0] - bring['ring.place'][0], ring['ring.place'][1] - bring['ring.place'][1]
                    ring_err.append({'applied': math.hypot(dx, dy), 'error': math.hypot(mx - dx, my - dy),
                                     'hours': rec.get('mg_ring_hours') or []})
    table = []
    for key, outc in sorted(res.items(), key=lambda kv: tuple(str(x) for x in kv[0])):
        row = {'scheme': key[0], 'target': key[1], 'kind': key[2], 'shape': key[3], 'move': key[4], 'amount': key[5]}
        for o, (h, n) in outc.items():
            row[o] = [h, n]
        table.append(row)
    return {'table': table,
            'gain': {'%s/%s' % k: {'n': len(v), 'median': float(np.median(v)), 'p10': float(np.percentile(v, 10)),
                                   'p90': float(np.percentile(v, 90))} for k, v in gains.items() if v},
            'spill_over_per_unit': {'%s/%s' % k: {'n': len(v), 'median': float(np.median(v)), 'p95': float(np.percentile(v, 95))}
                                    for k, v in spill.items() if v},
            'multi_all_detected': {'%s/%s/%d' % k: v for k, v in multi_all.items()},
            'ring_translation_recovery': {'n': len(ring_err),
                                          'median_error': float(np.median([e['error'] for e in ring_err])) if ring_err else None,
                                          'p95_error': float(np.percentile([e['error'] for e in ring_err], 95)) if ring_err else None}}


# ------------------------------------------------------------------------------------ production and genuine base

def genuine_production(fam, obs):
    """production strict-genuine and Clearly-off alarms on the same admitted genuine photos and eligible markers"""
    elig = defaultdict(set)
    for p in (p for F in fam.values() for p in F['photos']):
        elig[p['sha'][:16]] = p['eligible']
    watch_of = {p['sha'][:16]: p['watch'] for F in fam.values() for p in F['photos']}
    per = {'strict': defaultdict(list), 'clearly_off': defaultdict(list)}
    reading = {'strict': defaultdict(list), 'clearly_off': defaultdict(list)}
    for layout, F in fam.items():
        for rec in F['base']:
            pid = rec.get('photo_id')
            if pid not in watch_of or not rec.get('ok'):
                continue
            mk, _ = record_values(rec, layout)
            e = set(mk) & elig[pid]
            pf = prod_flags(rec, e)
            w = watch_of[pid]
            per['strict'][w].append(any(s for s, _ in pf.values()))
            per['clearly_off'][w].append(any(c for _, c in pf.values()))
            reading['strict'][w] += [s for s, _ in pf.values()]
            reading['clearly_off'][w] += [c for _, c in pf.values()]
    out = {}
    for k in per:
        out[k] = {'watches': len(per[k]),
                  'whole_watch_fa': float(np.mean([np.mean(v) for v in per[k].values()])) if per[k] else None,
                  'per_marker_fa': float(np.mean([np.mean(v) for v in reading[k].values() if v])) if reading[k] else None}
    return out


def crosscheck(fam):
    """GeometryMeasure (calibrator adapter) against ArchMeasure (research driver) on the same photographs, and the
    MarkerGeometry eligibility against the archive mask"""
    diffs, agree = [], Counter()
    for layout, F in fam.items():
        rows = {p['sha'][:16]: p for p in F['photos']}
        for rec in F['base']:
            p = rows.get(rec.get('photo_id'))
            if p is None or not rec.get('ok'):
                continue
            mask = F['masks'].get(p['sha'])
            for m in rec.get('markers') or []:
                h = int(m['h'])
                g = m.get('mg')
                if not g or h not in REQUIRED[layout]:
                    continue
                if mask:
                    agree[(bool(g[3]), el.eligible(mask, h))] += 1
                for i, q in enumerate(('tang', 'rad', 'tilt')):
                    v = _f(p['row'].get('marker.%d.%s' % (h, q)))
                    if v is not None and g[i] is not None:
                        diffs.append(abs(v - g[i]))
    d = np.array(diffs)
    return {'values_compared': len(d), 'max_abs_diff': float(d.max()) if len(d) else None,
            'p99_abs_diff': float(np.percentile(d, 99)) if len(d) else None,
            'eligibility_mg_vs_mask': {'%s/%s' % k: v for k, v in sorted(agree.items())}}


def top_edge(fam, groups):
    """the approved top-edge radial (production, calibrated) against the MarkerGeometry triangle radial"""
    pairs, syn = [], defaultdict(list)
    watch_of = {p['sha'][:16]: p['watch'] for F in fam.values() for p in F['photos']}
    for layout, F in fam.items():
        for rec in F['base']:
            if rec.get('photo_id') not in watch_of or not rec.get('ok'):
                continue
            for m in rec.get('markers') or []:
                if int(m['h']) == 12 and m.get('top') and m.get('mg') and m['mg'][3] and m['mg'][1] is not None:
                    pairs.append((watch_of[rec['photo_id']], _f(m.get('inout')), m['mg'][1], rec.get('rpx')))
        base = {r['photo_id']: r for r in F['synth'] if r.get('variant') == 'base' and r.get('ok')}
        for rec in F['synth']:
            kind, moves = parse_variant(rec.get('variant', ''))
            if kind != 'single' or moves[0][0] != 12 or moves[0][1] != 'r' or rec['photo_id'] not in base:
                continue
            a = moves[0][2] * 100
            def get(r):
                for m in r.get('markers') or []:
                    if int(m['h']) == 12:
                        return m
            m, b = get(rec), get(base[rec['photo_id']])
            if m and b and m.get('mg') and b.get('mg') and m['mg'][3] and m['mg'][1] is not None and b['mg'][1] is not None:
                syn['mg'].append((m['mg'][1] - b['mg'][1]) / a)
                if m.get('top') and b.get('top') and _f(m.get('inout')) is not None and _f(b.get('inout')) is not None:
                    syn['top_edge'].append((_f(m['inout']) - _f(b['inout'])) / a)
    pairs = [p for p in pairs if p[1] is not None]
    if not pairs:
        return {'pairs': 0}
    w = weights([p[0] for p in pairs])
    te, mg = np.array([p[1] for p in pairs]), np.array([p[2] for p in pairs])
    diff = mg - te
    from scipy.stats import spearmanr
    return {'pairs': len(pairs), 'watches': len({p[0] for p in pairs}),
            'spearman': float(spearmanr(te, mg).correlation),
            'median_diff_mg_minus_top': wquantile(diff, w, 0.5),
            'robust_sd_diff': float(1.4826 * np.median(np.abs(diff - np.median(diff)))),
            'top_edge_p99_abs': wquantile(np.abs(te), w, 0.99), 'mg_p99_abs_centred': wquantile(np.abs(mg - groups['triangle.rad'].centre), w, 0.99),
            'top_edge_over_its_limit': float(np.sum(w * (np.abs(te) > PROD['triInoutTop'])) / np.sum(w)),
            'synthetic_gain': {k: {'n': len(v), 'median': float(np.median(v))} for k, v in syn.items() if v}}


def ring_quality(obs, groups):
    """angular distribution of each photo's ring markers against the spread of its ring placement"""
    G = groups['ring.place']
    rows = []
    for o, d in zip(G.obs, G.d):
        hs = o.get('ring_hours') or []
        if len(hs) < 5:
            continue
        ang = sorted(math.radians(30 * (h % 12)) for h in hs)
        gaps = [(ang[(i + 1) % len(ang)] - ang[i]) % (2 * math.pi) or 2 * math.pi for i in range(len(ang))]
        pts = [(math.sin(a), -math.cos(a)) for a in ang]
        A = []
        for x, y in pts:
            A += [[1, 0, x, -y], [0, 1, y, x]]
        A = np.array(A, float)
        cov = np.linalg.inv(A.T @ A)
        rows.append({'watch': o['watch'], 'gap': math.degrees(max(gaps)), 'dop': math.sqrt(cov[0, 0] + cov[1, 1]),
                     'n': len(hs), 'd': d})
    out = {}
    for label, sel in (('max_gap<=60', lambda r: r['gap'] <= 60), ('max_gap_90', lambda r: 60 < r['gap'] <= 90),
                       ('max_gap>=120', lambda r: r['gap'] > 90)):
        s = [r for r in rows if sel(r)]
        if s:
            w = weights([r['watch'] for r in s])
            out[label] = {'photos': len(s), 'watches': len({r['watch'] for r in s}),
                          'p95_place': wquantile([r['d'] for r in s], w, 0.95), 'median_dop': float(np.median([r['dop'] for r in s]))}
    return {'bins': out, 'markers_used': dict(Counter(r['n'] for r in rows))}


def withheld(fam):
    out = {}
    for layout, F in fam.items():
        st = defaultdict(Counter)
        for p in F['photos']:
            m = F['masks'].get(p['sha'])
            if m:
                for h, rec in m['markers'].items():
                    if rec['status'] != el.NOT_APPLICABLE:
                        st[SHAPES[layout][int(h)]][rec['status']] += 1
        ex = Counter()
        for r in F['exclusions']:
            ex[(r['metric'].split('.')[0] if r['metric'].startswith('ring') else 'marker', r['statuses'].split(':')[-1] if ':' in r['statuses'] else r['statuses'])] += 1
        adm = F['admission'].get('summary', {})
        out[layout] = {'admission': {k: adm.get(k) for k in ('items', 'outcomes', 'admitted_photos', 'admitted_watches',
                                                              'replica_photos', 'replica_watches', 'not_admitted_reasons')},
                       'marker_status_in_admitted_photos': {k: dict(v) for k, v in st.items()},
                       'mask_exclusions_after_measurement': {'%s:%s' % k: v for k, v in ex.items()}}
    return out


def replicas(fam, groups, calib):
    out = []
    for layout, F in fam.items():
        for rec in F['base']:
            it = F['items'].get(rec.get('photo_id'), {})
            if it.get('class_label') != 'rep' or not rec.get('ok'):
                continue
            mask = F['masks'].get(it.get('sha256'))
            mk, ring = record_values(rec, layout)
            if mask:
                mk = {h: v for h, v in mk.items() if el.eligible(mask, h)}
            row = {'layout': layout, 'watch': it.get('physical_watch_id'), 'photo': rec['photo_id'], 'eligible_markers': len(mk)}
            for scheme, cal in calib.items():
                for target in TARGETS:
                    L = cal[str(target)]['limits']
                    row['%s@%g' % (scheme, target)] = sorted(h for h, v in mk.items() if flagged(v, groups, L, SCHEMES[scheme])) + \
                        (['ring'] if ring and flagged(ring, groups, L, SCHEMES[scheme]) else [])
            pf = prod_flags(rec, set(mk))
            row['production_strict'] = sorted(h for h, (s, _) in pf.items() if s)
            row['production_clearly_off'] = sorted(h for h, (_, c) in pf.items() if c)
            out.append(row)
    return out


def parity_check(fam):
    from tools.research.measurement_architecture import parity
    recs = [r for F in fam.values() for r in F['base'] + F['synth'] if r.get('ok') and r.get('markers') and r.get('H')]
    worst, n, bad, missing = parity.compare(recs)
    return {'records': len(recs), 'values': n, 'max_abs_diff': worst, 'eligibility_mismatches': bad, 'one_side_only': missing,
            'pass': worst <= parity.TOL and bad == 0 and missing == 0}


# --------------------------------------------------------------------------------------------------------- report

def run(root, boot=400):
    fam = load(root)
    obs = observations(fam)
    groups = build_groups(obs)
    partition_of = {p['watch']: p['partition'] for F in fam.values() for p in F['photos']}
    out = {'layouts': {l: {'photos': len(F['photos']), 'watches': len({p['watch'] for p in F['photos']}),
                           'sources': dict(Counter(s for _, s in {(p['watch'], p['source']) for p in F['photos']}).most_common())}
                       for l, F in fam.items()},
           'groups': {}, 'withheld': withheld(fam)}
    for g, G in groups.items():
        if not G.obs:
            continue
        out['groups'][g] = {'summary': describe(G, boot), 'components': components(G), 'by_family': by_family(G, boot),
                            'by_hour': by_hour(G), 'sensitivity': sensitivity(G), 'conditions': conditions(G)}
    calib = {s: calibrate(obs, groups, SCHEMES[s], partition_of, boot) for s in SCHEMES}
    out['calibration'] = calib
    out['production_on_genuine'] = genuine_production(fam, obs)
    out['synthetic'] = synthetic(fam, groups, calib)
    out['replicas'] = replicas(fam, groups, calib)
    out['top_edge'] = top_edge(fam, groups)
    out['ring_quality'] = ring_quality(obs, groups)
    out['crosscheck'] = crosscheck(fam)
    out['parity'] = parity_check(fam)
    return out


def _r(x, n=3):
    if x is None or (isinstance(x, float) and not math.isfinite(x)):
        return '—'
    return ('%.' + str(n) + 'f') % x


def markdown(out):
    L = ['# Genuine calibration of the MarkerGeometry readings (research; nothing deployed)', '']
    L += ['## Populations', '', '| Layout | Photos | Watches | Watches by source |', '|---|---:|---:|---|']
    for l, v in out['layouts'].items():
        L.append('| %s | %d | %d | %s |' % (l, v['photos'], v['watches'], ', '.join('%s %d' % kv for kv in v['sources'].items())))
    L += ['', '## Pooled genuine distributions (watch-weighted; |value - centre|; 90 % watch-bootstrap intervals)', '',
          '| Group | Unit | Watches | Photos | Sources (max share) | Centre | Median | Robust SD | p95 | p99 | Max | Within-watch SD | Between-watch SD |',
          '|---|---|---:|---:|---|---:|---:|---:|---|---|---:|---:|---:|']
    for g, v in out['groups'].items():
        s, c = v['summary'], v['components'] or {}
        cen = s['centre']
        cen = '(%s, %s)' % (_r(cen[0]), _r(cen[1])) if isinstance(cen, (list, tuple)) else _r(cen)
        L.append('| %s | %s | %d | %d | %d (%s) | %s | %s | %s | %s [%s, %s] | %s [%s, %s] | %s | %s | %s |' % (
            g, UNITS[g.split('.')[1]], s['watches'], s['photos'], s['sources'], _r(s['max_source_share'], 2), cen,
            _r(s['median_dev']), _r(s['robust_sd']), _r(s['p95'][0]), _r(s['p95'][1]), _r(s['p95'][2]), _r(s['p99'][0]),
            _r(s['p99'][1]), _r(s['p99'][2]), _r(s['max']), _r(c.get('within_watch_photo_sd')), _r(c.get('between_watch_sd'))))
    L += ['', '## By family (p95 / p99 of the pooled deviation, 90 % intervals)', '', '| Group | Layout | Watches | Signed median | p95 | p99 |', '|---|---|---:|---:|---|---|']
    for g, v in out['groups'].items():
        for l, f in v['by_family'].items():
            L.append('| %s | %s | %d | %s | %s [%s, %s] | %s [%s, %s] |' % (g, l, f['watches'], _r(f['signed_median']),
                     _r(f['p95'][0]), _r(f['p95'][1]), _r(f['p95'][2]), _r(f['p99'][0]), _r(f['p99'][1]), _r(f['p99'][2])))
    L += ['', '## By clock position (signed watch-weighted median; p95 of the pooled deviation)', '']
    for g in ('round.tang', 'round.rad', 'baton.tang', 'baton.rad', 'baton.tilt'):
        v = out['groups'].get(g, {}).get('by_hour')
        if not v:
            continue
        L.append('**%s** (Kruskal-Wallis over hours on watch medians: p = %s)' % (g, _r(v['kruskal_p_hours'], 4)))
        L.append('')
        L.append(' · '.join('%s: n %d, med %s, p95 %s' % (k, c['watches'], _r(c['signed_median']), _r(c['p95_dev'])) for k, c in v['cells'].items()))
        L.append('')
    L += ['## Photographic conditions (p95 of the deviation; watches)', '']
    for g, v in out['groups'].items():
        L.append('- **%s**: %s' % (g, '; '.join('%s: %s (%d)' % (k, _r(c['p95']), c['watches']) for k, c in v['conditions'].items())))
    L += ['']
    L += ['## Source and watch sensitivity of p99', '', '| Group | p99 | p99 without each source | Largest change from one watch |', '|---|---:|---|---:|']
    for g, v in out['groups'].items():
        s = v['sensitivity']
        L.append('| %s | %s | %s | %s |' % (g, _r(s['p99']), ', '.join('%s %s' % (k, _r(x)) for k, x in s['p99_without_source'].items()),
                                             _r(s['max_p99_change_one_watch'])))
    L += ['', '## Candidate limits and false alarms', '']
    for scheme, cal in out['calibration'].items():
        L.append('### Scheme `%s` (%s)' % (scheme, ', '.join(SCHEMES[scheme])))
        L.append('')
        L.append('Per-reading p99 limits alone: whole-watch false alarms %s.' % _r(cal['p99_per_reading']['whole_watch_fa']))
        L.append('')
        L.append('| Target whole-watch FA | q per reading | In-sample FA | 5-fold CV FA | Holdout FA (watches) | Per-reading FA | ' +
                 ' | '.join(SCHEMES[scheme]) + ' |')
        L.append('|---|---:|---:|---:|---:|---:|' + '---|' * len(SCHEMES[scheme]))
        for t in TARGETS:
            c = cal[str(t)]
            L.append('| %g | %.5f | %s | %s | %s (%d) | %s | %s |' % (t, c['q_per_reading'], _r(c['whole_watch_fa']),
                     _r(c['cv_whole_watch_fa']), _r(c['holdout_whole_watch_fa']), c['holdout_watches'], _r(c['per_reading_fa'], 4),
                     ' | '.join('%s [%s, %s]' % (_r(c['limits'][g]), _r(c['limits_90ci'].get(g, (None,))[0]),
                                                 _r(c['limits_90ci'].get(g, (None, None))[1])) for g in SCHEMES[scheme])))
        L.append('')
    p = out['production_on_genuine']
    L += ['Production on the same admitted genuine photos and eligible markers: strict genuine whole-watch FA %s '
          '(per marker %s); Clearly off whole-watch %s (per marker %s).' % (
              _r(p['strict']['whole_watch_fa']), _r(p['strict']['per_marker_fa'], 4), _r(p['clearly_off']['whole_watch_fa']),
              _r(p['clearly_off']['per_marker_fa'], 4)), '']
    L += ['## Synthetic defects (image-level moves on admitted genuine photos; detected / assessable)', '']
    cols = ['%s@%g' % (sc, t) for sc in SCHEMES for t in TARGETS] + ['production strict', 'production Clearly off']
    piv = defaultdict(dict)
    na = Counter()
    for r in out['synthetic']['table']:
        key = (r['kind'], r['shape'], r['move'], r['amount'])
        if r['scheme'] == 'production':
            piv[key]['production strict'] = r.get('strict_genuine')
            piv[key]['production Clearly off'] = r.get('clearly_off')
        else:
            piv[key]['%s@%g' % (r['scheme'], r['target'])] = r.get('detected') or r.get('new_false_flag')
            if r['scheme'] == 'axis' and r['target'] == TARGETS[0]:
                na[key] = (r.get('not_assessable') or [0, 0])[1]
    L.append('| Kind | Shape | Move | Amount | Not assessable | ' + ' | '.join(cols) + ' |')
    L.append('|---|---|---|---:|---:|' + '---:|' * len(cols))
    frac = lambda x: '%s (%d)' % (_r(x[0] / x[1], 2), x[1]) if x and x[1] else '—'
    for key in sorted(piv, key=lambda k: (k[0], k[1], k[2], float(k[3]))):
        L.append('| %s | %s | %s | %s | %d | %s |' % (key[0], key[1].replace('other_markers', 'other markers: new false flag')
                 .replace('marker_flags', 'markers: new false flag'), key[2], key[3], na.get(key, 0),
                 ' | '.join(frac(piv[key].get(c)) for c in cols)))
    s = out['synthetic']
    L += ['', 'Measured response (reading change / applied move): ' +
          '; '.join('%s %s (n %d, p10-p90 %s-%s)' % (k, _r(v['median']), v['n'], _r(v['p10']), _r(v['p90'])) for k, v in s['gain'].items()),
          '', 'Spill-over onto other markers (|change| / applied): ' +
          '; '.join('%s median %s, p95 %s' % (k, _r(v['median']), _r(v['p95'])) for k, v in s['spill_over_per_unit'].items()),
          '', 'Every moved marker detected in multi-marker cases: ' +
          '; '.join('%s %d/%d' % (k, v[0], v[1]) for k, v in s['multi_all_detected'].items()),
          '', 'Ring translation recovery error: median %s, p95 %s %% R (n %d)' % (
              _r(s['ring_translation_recovery']['median_error']), _r(s['ring_translation_recovery']['p95_error']),
              s['ring_translation_recovery']['n']), '']
    L += ['## Replica photographs', '', '| Layout | Watch | Photo | Eligible | ' + ' | '.join('%s@%g' % (sc, t) for sc in SCHEMES for t in TARGETS) +
          ' | Production strict | Production Clearly off |', '|---|---|---|---:|' + '---|' * (len(SCHEMES) * len(TARGETS) + 2)]
    for r in out['replicas']:
        L.append('| %s | %s | %s | %d | %s | %s | %s |' % (r['layout'], r['watch'], r['photo'], r['eligible_markers'],
                 ' | '.join(' '.join(map(str, r['%s@%g' % (sc, t)])) or '—' for sc in SCHEMES for t in TARGETS),
                 ' '.join(map(str, r['production_strict'])) or '—', ' '.join(map(str, r['production_clearly_off'])) or '—'))
    t = out['top_edge']
    L += ['', '## Triangle: approved top edge against MarkerGeometry radial', '', '```', json.dumps(t, indent=1), '```',
          '', '## Ring geometry quality', '', '```', json.dumps(out['ring_quality'], indent=1), '```',
          '', '## Cross-checks', '', '```', json.dumps({'crosscheck': out['crosscheck'], 'parity': out['parity']}, indent=1), '```',
          '', '## Withheld', '', '```', json.dumps(out['withheld'], indent=1), '```']
    return '\n'.join(L) + '\n'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('root')
    ap.add_argument('--json')
    ap.add_argument('--md')
    ap.add_argument('--boot', type=int, default=400)
    a = ap.parse_args(argv)
    out = run(a.root, a.boot)
    md = markdown(out)
    if a.json:
        Path(a.json).write_text(json.dumps(out, indent=1, default=float) + '\n')
    if a.md:
        Path(a.md).write_text(md)
    print(md)


if __name__ == '__main__':
    main()
