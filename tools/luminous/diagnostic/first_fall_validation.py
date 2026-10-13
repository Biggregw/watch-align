#!/usr/bin/env python3
"""VALIDATION ONLY (one bounded cycle). Compares, for every genuine 124060 round-marker observation, the frozen
calibration fits, the candidate detector (e0b0b651) and the experimental first-significant-fall selection
(exp/luminous-first-fall-20261013), against the independent first-fall analysis of groove_diag.py on the exact
rectified crops. Also checks that outer-metal evidence and all noncircular luminous results are byte-identical
between candidate and experimental, tabulates the hour-2 offset by hand contamination, and lists 855041 reasons.

Classes are NUMERICAL (relative to the diagnostic first-fall circle, which uses the reference radius only as an
analysis window and has no hand mask). Visual verdicts are made separately from the close-ups.

Usage: first_fall_validation.py FROZEN CAND EXP REFERENCES EVIDENCE_DIR OUT_DIR
"""
import csv
import json
import math
import sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from groove_diag import RAYS, analyse, classify, fit_px, load, marker, ref_radius  # noqa: E402

CONTROLS = ([('c14f8b3abb49c038', h) for h in (1, 2, 4, 5, 7, 8, 10, 11)] + [('80d288dcc8689345', 2)]
            + [('855041528fc1ac79', h) for h in (1, 2, 4, 5, 7, 8, 10, 11)])
OUTER_KEYS = ('outer_status', 'outer_rotation_deg', 'outer_calibrated_rotation_deg', 'outer_area_reason',
              'outer_total_pct', 'interference_clean', 'interference_reason')


def total(m):
    v = (m or {}).get('new_fitted')
    return None if not v else v.get('total_pct')


def geom(m):
    f = (m or {}).get('fit') or {}
    if total(m) is None or not f.get('center'):
        return None
    return (f['center'][0], f['center'][1], f['radius_R'])


def main(frozen_p, cand_p, exp_p, refs_p, evdir, outdir):
    frozen, cand, exp, refs = load(frozen_p), load(cand_p), load(exp_p), json.loads(Path(refs_p).read_text())
    evdir, outdir = Path(evdir), Path(outdir); outdir.mkdir(parents=True, exist_ok=True)
    rows, keep = [], {}
    for jf in sorted(evdir.glob('*.json')):
        ev = json.loads(jf.read_text()); hour = int(ev['marker']['hour']); pid = jf.stem.rsplit('_', 1)[0]
        gray = np.asarray(Image.open(evdir / f'{pid}_{hour}_gray.png').convert('L'), float)
        a = analyse(ev, gray, ref_radius(refs, hour))
        fm, cm, em = (marker(s.get(pid, {}), hour) for s in (frozen, cand, exp))
        px = {k: fit_px(((m or {}).get('fit') or {}) if total(m) is not None else None, ev) for k, m in (('f', fm), ('c', cm), ('e', em))}
        cls = {k: classify(px[k], a, ev) for k in px}
        gc, ge = geom(cm), geom(em)
        if gc is None and ge is None: change = 'withheld both'
        elif gc is None: change = 'newly accepted'
        elif ge is None: change = 'newly withheld'
        elif max(abs(x - y) for x, y in zip(gc, ge)) < 1e-12: change = 'unchanged geometry'
        else: change = 'geometry changed'
        hand = not (em or {}).get('interference_clean', True)
        row = dict(photo_id=pid, hour=hour, hand=int(hand), interference=(em or {}).get('interference_reason', ''),
                   ff_R=round(a['ff_radius_R'], 5) if math.isfinite(a['ff_radius_R']) else '', sectors=a['sectors'],
                   resolved=a['resolved'], late=a['late'],
                   frozen_pct=total(fm), cand_pct=total(cm), exp_pct=total(em),
                   cand_R=gc[2] if gc else '', exp_R=ge[2] if ge else '',
                   frozen_class=cls['f'][0], cand_class=cls['c'][0], exp_class=cls['e'][0],
                   cand_dr=round(cls['c'][1], 5) if math.isfinite(cls['c'][1]) else '',
                   exp_dr=round(cls['e'][1], 5) if math.isfinite(cls['e'][1]) else '',
                   change=change, outer_status=(em or {}).get('outer_status', ''),
                   cand_code=(cm or {}).get('failure_code', ''), exp_code=(em or {}).get('failure_code', ''),
                   cand_reason=str(((cm or {}).get('fit') or {}).get('reason', ''))[:110],
                   exp_reason=str(((em or {}).get('fit') or {}).get('reason', ''))[:110])
        rows.append(row); keep[(pid, hour)] = (a, ev, gray, px['c'], px['e'])
    with open(outdir / 'first_fall_validation.csv', 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)

    # ---- outer-metal and noncircular invariance (candidate vs experimental, every marker of every photo)
    outer_diff, noncirc_diff, snap_diff, photos = [], [], [], sorted(set(cand) | set(exp))
    for pid in photos:
        pc, pe = cand.get(pid, {}), exp.get(pid, {})
        if pc.get('outer_snapshot_sha256') != pe.get('outer_snapshot_sha256'): snap_diff.append(pid)
        for mc in pc.get('markers', []):
            me = marker(pe, int(mc['hour'])) or {}
            for k in OUTER_KEYS:
                if mc.get(k) != me.get(k): outer_diff.append((pid, mc['hour'], k))
            if mc.get('shape') != 'round' and json.dumps(mc, sort_keys=True) != json.dumps(me, sort_keys=True):
                noncirc_diff.append((pid, mc['hour']))
    frozen_outer = sum(1 for pid in photos for mc in exp.get(pid, {}).get('markers', [])
                       if (marker(frozen.get(pid, {}), int(mc['hour'])) or {}).get('outer_status') != mc.get('outer_status'))

    # ---- visual review selection (controls + representative accepted, highest mismatch first)
    ctrl = set(CONTROLS)
    acc = [r for r in rows if r['exp_pct'] is not None and (r['photo_id'], r['hour']) not in ctrl]
    high = sorted(acc, key=lambda r: -r['exp_pct'])[:14]
    changed = sorted([r for r in rows if r['change'] == 'geometry changed' and (r['photo_id'], r['hour']) not in ctrl],
                     key=lambda r: -abs((r['exp_R'] or 0) - (r['cand_R'] or 0)))[:6]
    new_acc = [r for r in rows if r['change'] == 'newly accepted' and (r['photo_id'], r['hour']) not in ctrl][:6]
    new_wh = [r for r in rows if r['change'] == 'newly withheld' and r['cand_class'] == 'follows first fall'
              and (r['photo_id'], r['hour']) not in ctrl][:5]
    review = []
    for tag, group in (('high-mismatch', high), ('changed', changed), ('newly-accepted', new_acc), ('newly-withheld', new_wh)):
        for r in group:
            if (r['photo_id'], r['hour']) not in {(x, y) for _, x, y in review}: review.append((tag, r['photo_id'], r['hour']))
    with open(outdir / 'select.csv', 'w') as f:
        for pid, h in CONTROLS: f.write(f'{pid},{h}\n')
        for _, pid, h in review: f.write(f'{pid},{h}\n')
    with open(outdir / 'select_controls.csv', 'w') as f:
        for pid, h in CONTROLS: f.write(f'{pid},{h}\n')
    plots(keep, CONTROLS + [(p, h) for _, p, h in review], outdir)

    # ---- compact rows first, summary last (the log tail must hold the summary)
    for r in rows:
        print('ROW', ','.join(str(r[k]) for k in ('photo_id', 'hour', 'hand', 'ff_R', 'sectors', 'late', 'cand_class', 'cand_dr',
                                                'exp_class', 'exp_dr', 'cand_pct', 'exp_pct', 'change', 'outer_status', 'exp_code')))
    print('=' * 30, 'SUMMARY')
    print('OBSERVATIONS', len(rows), 'photos', len({r['photo_id'] for r in rows}))

    def tally(key, pct, sub=rows):
        return dict(Counter(r[key] for r in sub if r[pct] is not None))
    for name, key, pct in (('FROZEN', 'frozen_class', 'frozen_pct'), ('CANDIDATE', 'cand_class', 'cand_pct'), ('EXPERIMENTAL', 'exp_class', 'exp_pct')):
        n = sum(r[pct] is not None for r in rows)
        print(f'{name} accepted {n} withheld {len(rows) - n} | all: {tally(key, pct)}')
        print(f'   clean-only (no hand flag): {tally(key, pct, [r for r in rows if not r["hand"]])}')
    print('CHANGE candidate -> experimental:', dict(Counter(r['change'] for r in rows)))
    print('CHANGE by candidate class:', dict(Counter((r['cand_class'], r['change']) for r in rows if r['change'] != 'withheld both')))
    print('EXPERIMENTAL accepted mismatch by class (median / max total %):')
    for c in sorted({r['exp_class'] for r in rows}):
        t = [r['exp_pct'] for r in rows if r['exp_class'] == c and r['exp_pct'] is not None]
        if t: print(f'   {c}: n {len(t)} median {np.median(t):.2f} max {max(t):.2f}')
    print('EXPERIMENTAL withheld reasons (fit reason, top):')
    for k, v in Counter(r['exp_reason'][:80] for r in rows if r['exp_pct'] is None).most_common(8): print(f'   {v} x {k}')
    print('EXPERIMENTAL withheld failure codes:', dict(Counter(r['exp_code'] for r in rows if r['exp_pct'] is None)))
    print('GROOVE-LIKE experimental accepted:')
    for r in rows:
        if r['exp_pct'] is not None and r['exp_class'].startswith('outside'):
            print(f"   {r['photo_id']} {r['hour']} hand {r['hand']} exp_R {r['exp_R']} ff {r['ff_R']} dr {r['exp_dr']} pct {r['exp_pct']:.2f} cand {r['cand_class']}")
    with_sha = sum(1 for p in photos if cand.get(p, {}).get('outer_snapshot_sha256'))
    print(f'OUTER-METAL invariance: snapshot sha present on {with_sha}, differs on {len(snap_diff)} of {len(photos)} photos {snap_diff[:5]}; '
          f'marker outer/interference fields differ {len(outer_diff)} {outer_diff[:5]}; '
          f'noncircular luminous records differ {len(noncirc_diff)} {noncirc_diff[:5]}; '
          f'outer_status frozen-vs-experimental differs on {frozen_outer} markers')
    print('HOUR table: hour n hand-flagged | ff_R median clean / hand | exp accepted clean / hand | cand accepted')
    for h in (1, 2, 4, 5, 7, 8, 10, 11):
        sub = [r for r in rows if r['hour'] == h]
        ffc = [r['ff_R'] for r in sub if r['ff_R'] != '' and r['sectors'] >= 7 and not r['hand']]
        ffh = [r['ff_R'] for r in sub if r['ff_R'] != '' and r['sectors'] >= 7 and r['hand']]
        md = lambda v: f'{np.median(v):.5f}({len(v)})' if v else '-'
        print(f'   {h:2d} {len(sub)} {sum(r["hand"] for r in sub)} | {md(ffc)} / {md(ffh)} | '
              f'{sum(r["exp_pct"] is not None and not r["hand"] for r in sub)} / {sum(r["exp_pct"] is not None and r["hand"] for r in sub)} | '
              f'{sum(r["cand_pct"] is not None for r in sub)}')
    print('HOUR-2 interference reasons:', dict(Counter(r['interference'] for r in rows if r['hour'] == 2)))
    for pid in ('855041528fc1ac79', '80d288dcc8689345', 'c14f8b3abb49c038'):
        print(f'CONTROL {pid}: hour cand(pct code | reason) -> exp(pct code | reason) ff_R sectors')
        for r in sorted((r for r in rows if r['photo_id'] == pid), key=lambda r: r['hour']):
            fmt = lambda v: '-' if v is None else f'{v:.2f}'
            print(f"   {r['hour']:2d} hand {r['hand']} cand {fmt(r['cand_pct'])} {r['cand_code']} | {r['cand_reason'][:70]}")
            print(f"           exp  {fmt(r['exp_pct'])} {r['exp_code']} | {r['exp_reason'][:70]} | ff {r['ff_R']} s{r['sectors']} {r['exp_class']}")
    print('REVIEW selection:', review)


def plots(keep, cases, outdir):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    for pid, hour in cases:
        if (pid, hour) not in keep:
            print('PLOT missing', pid, hour); continue
        a, ev, gray, cpx, epx = keep[(pid, hour)]
        fig, ax = plt.subplots(1, 2, figsize=(11, 4.6))
        ax[0].imshow(gray, cmap='gray', vmin=0, vmax=255); t = np.linspace(0, 2 * math.pi, 300)
        for c, col, lab in ((cpx, 'orange', 'candidate e0b0b651'), (epx, 'magenta', 'experimental first-fall')):
            if c: ax[0].plot(c[0] + c[2] * np.cos(t), c[1] + c[2] * np.sin(t), color=col, lw=1, label=lab)
        if math.isfinite(a['ff_radius_R']):
            r = a['ff_radius_R'] / ev['step']; ax[0].plot(a['ff_cx'] + r * np.cos(t), a['ff_cy'] + r * np.sin(t), color='lime', lw=1, ls='--', label='diagnostic first-fall (no hand mask)')
        ax[0].legend(fontsize=7, loc='lower right'); ax[0].set_title(f'{pid} / {hour}', fontsize=9); ax[0].axis('off')
        rs = a['_rs'] * ev['step']
        for k, lab in ((RAYS * 3 // 4, 'up'), (0, 'right'), (RAYS // 4, 'down'), (RAYS // 2, 'left')):
            ax[1].plot(rs, a['_prof'][k], lw=1, label=lab)
        for c, col in ((cpx, 'orange'), (epx, 'magenta')):
            if c: ax[1].axvline(c[2] * ev['step'], color=col, lw=1)
        if math.isfinite(a['ff_radius_R']): ax[1].axvline(a['ff_radius_R'], color='lime', ls='--', lw=1)
        ax[1].set_xlabel('radius from expected centre (R)'); ax[1].legend(fontsize=7)
        fig.tight_layout(); fig.savefig(Path(outdir) / f'profile_{pid}_{hour}.png', dpi=72); plt.close(fig)


if __name__ == '__main__':
    main(*sys.argv[1:7])
