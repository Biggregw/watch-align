"""Resolution sensitivity of the production measurements (read-only research).

    python -m tools.image_archive.precision evidence.jsonl items.csv workdir out.jsonl [--targets 350,300,250,200,175,150] [--max 30]

Takes reviewed face-on photos whose dial radius is at least 400 px with every
required marker measured (credible), resamples each original to lower dial radii
(Lanczos, lossless PNG, so only the pixel count changes), re-runs the same
production analysis and reports how far each marker's numbers move from the
full-resolution reading and how many markers stay measured / reliable. Prints
``PREC`` summary lines. No image is kept or uploaded.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageOps

from .faceon import Driver, summarise

KEYS = ('side_pct', 'inout_pct', 'tilt_deg', 'pos_pct', 'gap_pct')


def _f(v):
    try:
        v = float(v)
        return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def pick(evidence, items, limit):
    paths = {r['photo_id']: r for r in csv.DictReader(open(items))}
    out = []
    for e in evidence:
        if e.get('near_frontal') != 'yes' or e.get('error') or not e.get('rpx'):
            continue
        if e['rpx'] < 400 or set(e.get('credible', [])) != set(e.get('required', [1])):
            continue
        out.append((e, paths[e['photo_id']]['path']))
    out.sort(key=lambda x: x[0]['photo_id'])
    return out[:limit]


def run(evidence_path, items, work, out, targets, limit):
    evidence = [json.loads(l) for l in open(evidence_path)]
    chosen = pick(evidence, items, limit)
    work = Path(work)
    work.mkdir(parents=True, exist_ok=True)
    driver = Driver()
    rows = []
    by_model = defaultdict(list)
    for e, path in chosen:
        with Image.open(path) as im:
            img = ImageOps.exif_transpose(im).convert('RGB')
        k_full = img.size[0] / float(e['work_w'])          # original px per analysed px
        full_rpx = e['rpx'] * k_full                         # dial radius in original pixels
        for t in targets:
            s = t / full_rpx
            if s >= 1:
                continue
            small = img.resize((max(1, round(img.size[0] * s)), max(1, round(img.size[1] * s))), Image.Resampling.LANCZOS)
            pid = '%s_r%d' % (e['photo_id'], t)
            p = work / (pid + '.png')
            small.save(p)
            by_model[e['model']].append((pid, str(p), e, t))
    for model, jobs in by_model.items():
        for i in range(0, len(jobs), 40):
            part = jobs[i:i + 40]
            try:
                ev = driver.run(model, [(pid, p) for pid, p, _, _ in part])
            except Exception as ex:
                print('PREC analysis failed for a chunk:', type(ex).__name__)
                continue
            for pid, p, base, t in part:
                got = ev.get(pid)
                rec = {'photo_id': base['photo_id'], 'model': model, 'target_rpx': t}
                if got is None:
                    rec['error'] = 'no_evidence'
                else:
                    s = summarise(got)
                    rec.update(rpx=s.get('rpx'), pose_valid=s.get('pose_valid'), credible=s.get('credible'),
                               reliable=s.get('reliable'), required=s.get('required'), tilt_deg=s.get('tilt_deg'))
                    d = {}
                    for hr, m in s.get('markers', {}).items():
                        full = base['markers'].get(str(hr)) or base['markers'].get(hr) or {}
                        d[hr] = {k: (None if _f(m.get(k)) is None or _f(full.get(k)) is None else _f(m[k]) - _f(full[k]))
                                 for k in KEYS}
                    rec['delta'] = d
                rows.append(rec)
                Path(p).unlink(missing_ok=True)
    with open(out, 'w') as f:
        for r in rows:
            f.write(json.dumps(r, sort_keys=True) + '\n')
    report(rows, len(chosen))
    return rows


def report(rows, n_photos):
    print('PREC photos %d (reviewed face-on, dial radius >= 400 px, every required marker measured)' % n_photos)
    by_t = defaultdict(list)
    for r in rows:
        by_t[r['target_rpx']].append(r)
    for t in sorted(by_t, reverse=True):
        rs = by_t[t]
        req = sum(len(r.get('required') or []) for r in rs if not r.get('error'))
        cred = sum(len(r.get('credible') or []) for r in rs if not r.get('error'))
        rel = sum(len(r.get('reliable') or []) for r in rs if not r.get('error'))
        line = ['PREC rpx %d: photos %d, pose ok %d, markers measured %d/%d, reliable %d/%d' % (
            t, len(rs), sum(1 for r in rs if r.get('pose_valid')), cred, req, rel, req)]
        for k in KEYS:
            v = sorted(abs(x[k]) for r in rs for x in (r.get('delta') or {}).values() if x.get(k) is not None)
            if v:
                line.append('|d %s| median %.3f p95 %.3f max %.3f (n %d)' % (
                    k, statistics.median(v), v[min(len(v) - 1, int(0.95 * len(v)))], v[-1], len(v)))
        print('; '.join(line))


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('evidence')
    ap.add_argument('items')
    ap.add_argument('work')
    ap.add_argument('out')
    ap.add_argument('--targets', default='350,300,250,200,175,150')
    ap.add_argument('--max', type=int, default=30)
    a = ap.parse_args()
    run(a.evidence, a.items, a.work, a.out, [int(x) for x in a.targets.split(',')], a.max)
