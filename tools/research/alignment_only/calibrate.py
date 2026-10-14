#!/usr/bin/env python3
"""Watch Align alignment-only: genuine luminous reference and bounded validation (one script, two steps).

  calibrate.py insets  PASS1.csv SETUPS_OUT.json
      Pass 1 fits every marker against its OUTER-METAL master with free size and no rejection; the uniform edge offset g
      it measures is how far the luminous edge sits inside the metal outline. The luminous inset per marker type is the
      median over physical watches (each watch = median of its photos and markers) of -g.
  calibrate.py reference PASS2.csv MODEL_ID PROVENANCE OUT.json
      Pass 2 fits the luminous template (inset applied). From it, per marker type: g nominal / spread / limits, per-hour
      genuine nominal positions, and the genuine envelope of each component (largest deviation of any physical watch,
      one value per watch per marker position). Then leave-one-watch-out validation of the app's decision rule, repeat-
      photo consistency and coverage. Prints a markdown report.

Genuine rows only (the caller's manifest). Replica / QC photos never enter. Numbers here are the ones the app uses.
"""
import csv, json, math, sys
from collections import defaultdict
import statistics as st

SHAPES = ('triangle', 'baton', 'round')
MIN_HOUR_WATCHES = 5
MIN_SHAPE_WATCHES = 10
GATE_PCT = 95              # genuine percentile of edge offset beyond which a marker is withheld (fail closed)
MIN_RESOLVABLE_PX = 0.5    # = AlignmentDecision.MIN_RESOLVABLE_PX


def fnum(x):
    try:
        v = float(x)
        return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def rows_of(path):
    return [r for r in csv.DictReader(open(path)) if r.get('outcome') != 'NOPOSE' and r.get('hour')]


def wmedian(values_by_watch):
    """median over watches of each watch's median"""
    per = [st.median(v) for v in values_by_watch.values() if v]
    return st.median(per) if per else None


def pct(values, p):
    v = sorted(values)
    if not v:
        return None
    k = (len(v) - 1) * p / 100
    lo, hi = math.floor(k), math.ceil(k)
    return v[lo] + (v[hi] - v[lo]) * (k - lo)


def insets(pass1, out):
    rows = rows_of(pass1)
    res = {}
    for shape in SHAPES:
        g = defaultdict(list)
        for r in rows:
            if r['shape'] == shape and r['fit_assessed'] == '1' and r['growth_free'] == '1' and fnum(r['growth_R']) is not None:
                g[r['physical_watch_id']].append(fnum(r['growth_R']))
        m = wmedian(g)
        print(f'{shape}: {len(g)} watches, median luminous edge offset from outer-metal master {m}')
        if m is not None:
            res[shape] = {'inset_R': round(-m, 6), 'fixed_growth_R': 0.0, 'growth_nominal_R': 0.0, 'reject': 1.0, 'watches': len(g)}
    json.dump(res, open(out, 'w'), indent=1)
    print(json.dumps(res, indent=1))


def gate(r, ref):
    """the app's measurement gates (LuminousAlignment + AlignmentAnalysis) with the derived limits; True = keep"""
    s = ref['shapes'].get(r['shape'])
    if s is None or r['fit_assessed'] != '1':
        return False
    if r['growth_free'] == '1':
        g, se = fnum(r['growth_R']), fnum(r['se_growth_R']) or 0
        if g is not None and abs(g - s['growth_nominal_R']) > s['growth_limit_R'] + 2 * se:
            return False
        d = fnum(r['photo_growth_diff_R'])
        if d is not None and abs(d) > s['photo_growth_limit_R'] + 2 * se:
            return False
    return True


COMP = (('radial', 'radial_R', 'se_radial_R', 'radial_ok', 'radial_limit_R'),
        ('tangential', 'tangential_R', 'se_tangential_R', 'tangential_ok', 'tangential_limit_R'),
        ('rotation', 'rotation_deg', 'se_rotation_deg', 'rotation_ok', 'rotation_limit_deg'))


def values(rows, comp):
    """{(watch, hour): [values]}"""
    _, col, _, ok, _ = comp
    out = defaultdict(list)
    for r in rows:
        if r[ok] == '1' and fnum(r[col]) is not None:
            out[(r['physical_watch_id'], int(r['hour']))].append(fnum(r[col]))
    return out


def nominals_and_envelopes(rows, screened=None):
    """per hour nominal (median over watches) and per shape envelope: the largest |watch value - nominal| after a
    measurement-quality gates in the app (merged bevel, edge noise, resolution). Far-out readings (beyond Q3 + 3 IQR) are
    LISTED in `screened` for review only; they are not removed"""
    hours, env, nw = {}, defaultdict(dict), defaultdict(set)
    shape_of = {int(r['hour']): r['shape'] for r in rows}
    for comp in COMP:
        name = comp[0]
        vals = values(rows, comp)
        by_hour = defaultdict(dict)
        for (w, h), v in vals.items():
            by_hour[h][w] = st.median(v)
        for h, ws in by_hour.items():
            if len(ws) >= MIN_HOUR_WATCHES:
                hours.setdefault(h, {})[name] = st.median(ws.values())
                hours[h].setdefault('watches', 0)
                hours[h]['watches'] = max(hours[h]['watches'], len(ws))
        dev = defaultdict(list)
        for h, ws in by_hour.items():
            if h in hours and name in hours[h]:
                for w, v in ws.items():
                    dev[shape_of[h]].append((abs(v - hours[h][name]), w, h))
                    nw[shape_of[h]].add(w)
        for s, d in dev.items():
            a = sorted(x for x, _, _ in d)
            q1, q3 = pct(a, 25), pct(a, 75)
            fence = q3 + 3 * (q3 - q1)
            env[s][name] = a[-1]   # every genuine reading that passed the measurement-quality gates counts
            if screened is not None:
                for x, w, h in d:
                    if x > fence:
                        screened.append((s, name, w, h, x, fence))
    return hours, env, nw


def photo_sd(rows):
    """pooled within-watch standard deviation of each component over genuine watches photographed more than once
    (same watch, same marker, different photos): photographic repeatability, kept separate from manufacturing spread"""
    out = {}
    for comp in COMP:
        name, col, _, ok, _ = comp
        for s in SHAPES:
            g = defaultdict(list)
            for r in rows:
                if r['shape'] == s and r[ok] == '1' and fnum(r[col]) is not None:
                    g[(r['physical_watch_id'], r['hour'])].append(fnum(r[col]))
            ss, dof, n = 0.0, 0, 0
            for v in g.values():
                if len(v) > 1:
                    m = sum(v) / len(v); ss += sum((x - m) ** 2 for x in v); dof += len(v) - 1; n += 1
            out[(s, name)] = (math.sqrt(ss / dof) if dof else None, n)
    return out


def decide(r, hours, env, shape, psd):
    """mirror of AlignmentDecision.decide: list of (component, excess_R)"""
    h = int(r['hour'])
    rpx = fnum(r['dial_r_px'])
    floor = MIN_RESOLVABLE_PX / rpx
    lever = fnum(r['half_len_R'])
    out = []
    for name, col, se, ok, _ in COMP:
        if r[ok] != '1' or h not in hours or name not in hours[h] or name not in env.get(shape, {}):
            continue
        dev = fnum(r[col]) - hours[h][name]
        s = fnum(r[se]) or 0
        if name == 'rotation':
            if not lever:
                continue
            sd = psd.get((shape, name)) or 0
            unc = max(2 * math.hypot(s, sd), math.degrees(floor / lever))
            out.append((name, (abs(dev) - env[shape][name] - unc) * math.radians(1) * lever))
        else:
            sd = psd.get((shape, name)) or 0
            unc = max(2 * math.hypot(s, sd), floor)
            out.append((name, abs(dev) - env[shape][name] - unc))
    return out


def reference(pass2, model, provenance, out):
    rows = rows_of(pass2)
    ref = {'model': model, 'provenance': provenance, 'method': (
        'LuminousAlignment rigid fit (translation + rotation; circle translation only) of the genuine-measured luminous '
        'template; reference ring from the other markers only. Per marker type: inset = median luminous inset from the '
        'outer-metal master; g = photographic edge offset (nominal, robust sd, p95 absolute and photo-relative limits); '
        'envelopes = largest deviation of any physical watch (one value per watch per marker position) from the per-hour '
        'genuine nominal. Genuine photos only; no replica or QC photo used.'), 'shapes': {}, 'hours': {}}
    s1 = json.load(open(sys.argv[6])) if len(sys.argv) > 6 else {}
    for shape in SHAPES:
        g, d = defaultdict(list), []
        for r in rows:
            if r['shape'] == shape and r['fit_assessed'] == '1' and r['growth_free'] == '1' and fnum(r['growth_R']) is not None:
                g[(r['physical_watch_id'], r['hour'])].append(fnum(r['growth_R']))
                if fnum(r['photo_growth_diff_R']) is not None:
                    d.append(abs(fnum(r['photo_growth_diff_R'])))
        per = [st.median(v) for v in g.values()]
        if not per:
            continue
        nom = st.median(per)
        mad = st.median([abs(x - nom) for x in per])
        ref['shapes'][shape] = {'inset_R': s1.get(shape, {}).get('inset_R'), 'growth_nominal_R': nom,
                                'growth_sd_R': 1.4826 * mad, 'growth_limit_R': pct([abs(x - nom) for x in per], GATE_PCT),
                                'photo_growth_limit_R': pct(d, GATE_PCT) if d else None}
    kept = [r for r in rows if gate(r, ref)]
    screened = []
    hours, env, nw = nominals_and_envelopes(kept, screened)
    sds = photo_sd(kept)
    psd = {}
    for shape in SHAPES:
        for name in ('radial', 'tangential', 'rotation'):
            v, n = sds[(shape, name)]
            if v is None or n < 5:      # too few repeat pairs for this type: the largest of the model's other types
                cand = [sds[(x, name)][0] for x in SHAPES if sds[(x, name)][0] is not None and sds[(x, name)][1] >= 5]
                v = max(cand) if cand else None
            psd[(shape, name)] = v
    for shape, s in ref['shapes'].items():
        e = env.get(shape, {})
        s.update({'radial_photo_sd_R': psd.get((shape, 'radial')), 'tangential_photo_sd_R': psd.get((shape, 'tangential')),
                  'rotation_photo_sd_deg': psd.get((shape, 'rotation')) if shape != 'round' else None,
                  'repeat_pairs': {n: sds[(shape, n)][1] for n in ('radial', 'tangential', 'rotation')},
                  'radial_limit_R': e.get('radial'), 'tangential_limit_R': e.get('tangential'),
                  'rotation_limit_deg': e.get('rotation'), 'watches': len(nw.get(shape, ())),
                  'supported': len(nw.get(shape, ())) >= MIN_SHAPE_WATCHES and e.get('radial') is not None and e.get('tangential') is not None})
        if not s['supported']:
            s['note'] = f'fewer than {MIN_SHAPE_WATCHES} verified genuine watches measured: every marker of this type is "Cannot assess"'
    for h, v in sorted(hours.items()):
        ref['hours'][str(h)] = {'radial_R': v.get('radial'), 'tangential_R': v.get('tangential'),
                                'rotation_deg': v.get('rotation'), 'watches': v['watches']}
    json.dump(ref, open(out, 'w'), indent=1)
    ref['screened_genuine_readings'] = [{'shape': a, 'component': b, 'watch': w, 'hour': h, 'deviation': x, 'fence': f} for a, b, w, h, x, f in screened]
    json.dump(ref, open(out, 'w'), indent=1)
    report(rows, kept, ref, hours, env, psd, screened)


def report(rows, kept, ref, hours, env, psd, screened):
    shape_of = {int(r['hour']): r['shape'] for r in rows}
    photos = {r['photo_id'] for r in rows}
    watches = {r['physical_watch_id'] for r in rows}
    print(f"\n## {ref['model']}: {len(photos)} genuine photos with a dial pose, {len(watches)} physical watches\n")
    print('| Marker type | Inset (%R) | g sd (%R) | g limit p95 (%R) | photo-g limit p95 (%R) | Watches | Radial env (%R) | Tangential env (%R) | Rotation env (deg) | Supported |')
    print('|---|---:|---:|---:|---:|---:|---:|---:|---:|---|')
    for s, v in ref['shapes'].items():
        f = lambda x, k=100: '-' if x is None else f'{k * x:.3f}'
        print(f"| {s} | {f(v.get('inset_R'))} | {f(v.get('growth_sd_R'))} | {f(v.get('growth_limit_R'))} | {f(v.get('photo_growth_limit_R'))} | "
              f"{v.get('watches')} | {f(v.get('radial_limit_R'))} | {f(v.get('tangential_limit_R'))} | {f(v.get('rotation_limit_deg'), 1)} | {v.get('supported')} |")
    print('\nPhotographic repeatability (pooled within-watch SD over genuine watches photographed more than once): ' + '; '.join(
        f"{s} {n} {('-' if psd.get((s, n)) is None else (f'{psd[(s, n)]:.2f} deg' if n == 'rotation' else f'{100 * psd[(s, n)]:.3f} %R'))} ({ref['shapes'].get(s, {}).get('repeat_pairs', {}).get(n, 0)} pairs)"
        for s in SHAPES for n in ('radial', 'tangential', 'rotation') if not (s == 'round' and n == 'rotation')))
    print(f'\nFar-out genuine readings (beyond Q3 + 3 IQR), listed for review, still counted in the envelopes: {len(screened)}')
    for a, b, w, h, x, f in screened:
        k = 1 if b == 'rotation' else 100
        print(f'  {a} {b} watch {w} {h}h: {k*x:.3f} (fence {k*f:.3f})')
    # coverage, per marker type, over posed photos
    print('\n### Coverage (every marker of every posed genuine photo)\n')
    print('| Marker type | Markers | Measured and passed gates | Cannot assess | Main reasons |')
    print('|---|---:|---:|---:|---|')
    keptset = {(r['photo_id'], r['hour']) for r in kept}
    for s in SHAPES:
        rs = [r for r in rows if r['shape'] == s]
        if not rs:
            continue
        reasons = defaultdict(int)
        for r in rs:
            if (r['photo_id'], r['hour']) not in keptset:
                why = r['reason'] or ('edge offset outside the genuine range (bevel / glare)' if r['fit_assessed'] == '1' else 'other')
                reasons[why.split('(')[0].strip()[:60]] += 1
        top = '; '.join(f'{k} ({n})' for k, n in sorted(reasons.items(), key=lambda x: -x[1])[:4])
        print(f'| {s} | {len(rs)} | {sum((r["photo_id"], r["hour"]) in keptset for r in rs)} | {len(rs) - sum((r["photo_id"], r["hour"]) in keptset for r in rs)} | {top} |')
    # leave-one-watch-out false-defect validation of the decision rule
    print('\n### Leave-one-watch-out: genuine markers reported as "Defect detected"\n')
    by_w = defaultdict(list)
    for r in kept:
        by_w[r['physical_watch_id']].append(r)
    flagged, total, fl_photos, examples = defaultdict(int), defaultdict(int), set(), []
    outcome = {}
    for w, rs in by_w.items():
        others = [r for r in kept if r['physical_watch_id'] != w]
        h2, e2, _ = nominals_and_envelopes(others)   # photo sd from all watches (repeatability, not a limit)
        for r in rs:
            s = r['shape']
            if not ref['shapes'].get(s, {}).get('supported'):
                continue
            ex = decide(r, h2, e2, s, psd)
            if not ex:
                continue
            total[s] += 1
            bad = [c for c, e in ex if e > 0]
            outcome[(r['photo_id'], r['hour'])] = 'DEFECT' if bad else 'NO_DEFECT'
            if bad:
                flagged[s] += 1
                fl_photos.add(r['photo_id'])
                examples.append(f"{r['photo_id']} {r['hour']}h {','.join(bad)}")
    print('| Marker type | Judged genuine markers | False "Defect detected" | Rate |')
    print('|---|---:|---:|---:|')
    for s in SHAPES:
        if total[s]:
            print(f'| {s} | {total[s]} | {flagged[s]} | {100 * flagged[s] / total[s]:.1f}% |')
    print(f'\nGenuine photos with at least one false defect (held-out): {len(fl_photos)} of {len({r["photo_id"] for r in kept})}')
    if examples:
        print('Examples: ' + '; '.join(examples[:12]))
    # repeat photos: the same watch and marker across photos
    print('\n### Repeat photos of the same genuine watch (held-out outcomes per marker)\n')
    rep = defaultdict(list)
    for r in kept:
        k = (r['physical_watch_id'], r['hour'])
        if (r['photo_id'], r['hour']) in outcome:
            rep[k].append((r, outcome[(r['photo_id'], r['hour'])]))
    multi = {k: v for k, v in rep.items() if len({x[0]['photo_id'] for x in v}) > 1}
    agree = sum(len({o for _, o in v}) == 1 for v in multi.values())
    print(f'Watch/marker pairs seen in 2+ photos: {len(multi)}; same outcome in every photo: {agree}')
    spreads = defaultdict(list)
    for (w, h), v in multi.items():
        for name, col, _, ok, _ in COMP:
            xs = [fnum(r[col]) for r, _ in v if r[ok] == '1' and fnum(r[col]) is not None]
            if len(xs) > 1:
                spreads[(shape_of[int(h)], name)].append(max(xs) - min(xs))
    for (s, name), sp in sorted(spreads.items()):
        unit = 'deg' if name == 'rotation' else '%R'
        k = 1 if name == 'rotation' else 100
        print(f'- {s} {name}: {len(sp)} pairs, median spread {k * st.median(sp):.3f} {unit}, max {k * max(sp):.3f} {unit}')
    # what sets each envelope: the largest genuine deviations with their measurement-quality fields
    print('\n### Genuine distribution and the watches that set each envelope\n')
    contrib = []
    for comp in COMP:
        name, col, _, ok, _ = comp
        for s in SHAPES:
            devs = []
            for r in kept:
                h = int(r['hour'])
                if r['shape'] != s or r[ok] != '1' or h not in hours or name not in hours[h] or fnum(r[col]) is None:
                    continue
                devs.append((abs(fnum(r[col]) - hours[h][name]), r))
            if not devs:
                continue
            k = 1 if name == 'rotation' else 100
            a = sorted(d for d, _ in devs)
            print(f'- {s} {name}: n={len(a)} photo-markers, p50 {k*pct(a,50):.3f} p90 {k*pct(a,90):.3f} p95 {k*pct(a,95):.3f} p99 {k*pct(a,99):.3f} max {k*a[-1]:.3f}')
            for d, r in sorted(devs, key=lambda x: -x[0])[:4]:
                print(f'    {k*d:.3f} {r["photo_id"]} {r["hour"]}h watch={r["physical_watch_id"]} R={r["dial_r_px"]} inferred={r["inferred"]} '
                      f'growth_free={r["growth_free"]} merged={r["merged_share"]} noise={r["noise_px"]} samples={r["samples"]} '
                      f'g={r["growth_R"]} photo_g_diff={r["photo_growth_diff_R"]} radial_ok={r["radial_ok"]} tang_ok={r["tangential_ok"]}')
                contrib.append(f'{r["photo_id"]} {r["hour"]}')
    open(sys.argv[5].replace('.json', '_contributors.txt'), 'w').write('\n'.join(sorted(set(contrib))) + '\n')


if __name__ == '__main__':
    if sys.argv[1] == 'insets':
        insets(sys.argv[2], sys.argv[3])
    else:
        reference(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
