"""Admit verified originals through the image archive and restore them, with their SHA-bound masks, for calibration.

    python -m tools.research.genuine_calibration.prepare items.csv WORK --model submariner_124060 [--synth-max 40]

items.csv is tools.image_archive.validation_items output (exact-SHA-verified originals). Every photograph with bytes and
one unambiguous identity is submitted to a fresh local image archive (tools.image_archive.Archive, production
face-on-v3 screener, purpose 'marker_masked'): the production analysis decides photographic suitability and builds the
per-marker eligibility mask. Admitted genuine photographs are restored with restore_acquired(..., 'marker_masked'),
which writes eligibility_masks.jsonl beside acquired_images.csv, so the watch calibrator's masked path verifies each
photo's bytes against its mask before any value is used. Nothing is uploaded; the archive lives in WORK only.

Writes, under WORK:
  acq/acquired_images.csv, acq/eligibility_masks.jsonl, acq/images/...   the masked acquisition root (genuine)
  acq/locked_split.csv                                                  watch-level split, stratified by source
  admission.json                                                        every decision (numbers and reasons only)
  arch_items.csv                                                        admitted genuine + suitable replica photos
  synth_items.csv                                                       up to --synth-max admitted genuine, one per watch
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
for p in (str(ROOT), str(ROOT / 'tools/watch_calibrator')):
    if p not in sys.path:
        sys.path.insert(0, p)

from tools.image_archive import Archive, eligibility as el  # noqa: E402
from tools.image_archive.faceon import MODELS  # noqa: E402
from tools.image_archive.integration import restore_acquired  # noqa: E402
from tools.image_archive.inventory import catalogue_rows  # noqa: E402
import split as locked_split  # noqa: E402
from marker_population import source_of  # noqa: E402

ACQ_FIELDS = ['sha256', 'physical_watch_id', 'class_label', 'model', 'source_name', 'factory', 'local_path',
              'acquisition_status']
ARCH_FIELDS = ['photo_id', 'path', 'local_path', 'sha256', 'physical_watch_id', 'class_label', 'model', 'source_name']
# reasons that concern the catalogue association, not the photograph (a replica without a recorded factory is still a
# measurable replica photograph)
ASSOCIATION_REASONS = {'inconclusive_source_identity', 'inconclusive_unverified_provenance',
                       'inconclusive_factory_identity', 'inconclusive_classification',
                       'inconclusive_reference_role_unknown', 'inconclusive_sample_role'}


def catalogue(repo=ROOT):
    """sha256 -> identities, classes, urls, provenance and factory over every committed catalogue"""
    cat = defaultdict(lambda: {'ids': set(), 'cls': set(), 'urls': [], 'prov': Counter(), 'factory': Counter()})
    for _, r in catalogue_rows(repo):
        s = (r.get('sha256') or '').lower()
        if not s:
            continue
        c = cat[s]
        if r.get('physical_watch_id'):
            c['ids'].add(r['physical_watch_id'])
        if r.get('class_label'):
            c['cls'].add(r['class_label'].lower())
        c['urls'] += [r.get(k) or '' for k in ('listing_url', 'source_url', 'url', 'source', 'image_url')]
        if r.get('provenance'):
            c['prov'][r['provenance']] += 1
        if r.get('factory'):
            c['factory'][r['factory']] += 1
    return cat


def association(item, cat):
    """(association for Archive.put_verified, why not) from the catalogue; never guessed"""
    c = cat.get(item['sha256'].lower())
    ids = set(c['ids']) if c else set()
    if item.get('physical_watch_id'):
        ids.add(item['physical_watch_id'])
    if len(ids) != 1:
        return None, 'identity_conflict_or_unknown_watch'
    cls = (c['cls'] if c else set()) - {''}
    if len(cls) != 1 or next(iter(cls)) not in ('gen', 'rep'):
        return None, 'class_unknown_or_conflicting'
    urls = [u for u in (c['urls'] if c else []) if u.startswith('http')]
    a = {'family': item['family'], 'model': item['family'], 'physical_watch_id': next(iter(ids)),
         'class_label': next(iter(cls)), 'source_url': urls[0] if urls else '', 'source_name': source_of(urls),
         'sample_role': 'population'}
    if c and c['prov']:
        a['provenance'] = c['prov'].most_common(1)[0][0]
    if c and c['factory'] and a['class_label'] == 'rep':
        a['factory'] = c['factory'].most_common(1)[0][0]
    return a, ''


def prepare(items_csv, work, model, synth_max=40, archive=None, cat=None):
    work = Path(work)
    acq = work / 'acq'
    acq.mkdir(parents=True, exist_ok=True)
    archive = archive or Archive(work / 'archive')          # production face-on-v3 screener, purpose marker_masked
    cat = catalogue() if cat is None else cat
    decisions, admitted, replicas = [], [], []
    seen = set()
    for item in csv.DictReader(open(items_csv, newline='', encoding='utf-8')):
        sha = item['sha256'].lower()
        if sha in seen:
            continue
        seen.add(sha)
        rec = {'sha256': sha, 'family': item['family'], 'set': item.get('set', '')}
        if MODELS.get(item['family']) != model:
            rec['outcome'] = 'other_layout'
            decisions.append(rec)
            continue
        if not item.get('path') or not Path(item['path']).is_file():
            rec['outcome'] = 'no_original_bytes'
            decisions.append(rec)
            continue
        a, why = association(item, cat)
        if a is None:
            rec['outcome'] = why
            decisions.append(rec)
            continue
        rec.update(physical_watch_id=a['physical_watch_id'], class_label=a['class_label'], source_name=a['source_name'])
        result = archive.put_verified(Path(item['path']), sha, a)
        body = result.get('decision')
        if body is None:            # admitted: read the stored decision
            body = json.loads(archive.db.execute('SELECT body FROM decisions WHERE sha=? ORDER BY id DESC',
                                                 (sha,)).fetchone()[0])
        rec['status'] = body['status']
        rec['reasons'] = body['reasons']
        mask = body.get('marker_eligibility') or {}
        if mask:
            rec['eligible_marker_ids'] = mask['eligible_marker_ids']
            rec['marker_status'] = {h: m['status'] for h, m in mask['markers'].items()}
        photo_ok = not [r for r in body['reasons'] if r not in ASSOCIATION_REASONS]
        if result['status'] == 'archived' and a['class_label'] == 'gen':
            rec['outcome'] = 'admitted'
            admitted.append((item, a))
        elif a['class_label'] == 'rep' and photo_ok and mask:
            rec['outcome'] = 'replica_suitable'
            replicas.append((item, a, mask))
        else:
            rec['outcome'] = 'not_admitted'
        decisions.append(rec)

    # restore the admitted genuine originals with their masks (the calibrator's masked acquisition root)
    rows = []
    for item, a in admitted:
        sha = item['sha256'].lower()
        ext = Path(item['path']).suffix.lower() or '.jpg'
        rows.append({'sha256': sha, 'physical_watch_id': a['physical_watch_id'], 'class_label': 'gen',
                     'model': a['model'], 'source_name': a['source_name'], 'factory': 'Rolex',
                     'local_path': f'images/{sha[:2]}/{sha}{ext}', 'acquisition_status': 'acquired'})
    manifest = acq / 'acquired_images.csv'
    with manifest.open('w', newline='', encoding='utf-8') as fh:
        w = csv.DictWriter(fh, fieldnames=ACQ_FIELDS)
        w.writeheader()
        w.writerows(rows)
    restored = restore_acquired(archive, manifest, acq, purpose='marker_masked') if rows else {'restored': 0, 'masks': 0}
    split_path = acq / 'locked_split.csv'
    if split_path.exists():
        split_path.unlink()
    locked_split.create(locked_split.watches_from_acquisition(manifest), split_path)

    # photographs for the research driver (production readings, top edge, synthetic defects): the restored genuine
    # originals and the photographically suitable replica originals
    arch = [{'photo_id': r['sha256'][:16], 'path': str((acq / r['local_path']).resolve()), 'local_path': r['local_path'],
             'sha256': r['sha256'], 'physical_watch_id': r['physical_watch_id'], 'class_label': 'gen',
             'model': model, 'source_name': r['source_name']} for r in rows]
    masks_out = {}
    for item, a, mask in replicas:
        sha = item['sha256'].lower()
        arch.append({'photo_id': sha[:16], 'path': str(Path(item['path']).resolve()), 'local_path': '', 'sha256': sha,
                     'physical_watch_id': a['physical_watch_id'], 'class_label': 'rep', 'model': model,
                     'source_name': a['source_name']})
        masks_out[sha] = mask
    with (work / 'arch_items.csv').open('w', newline='', encoding='utf-8') as fh:
        w = csv.DictWriter(fh, fieldnames=ARCH_FIELDS)
        w.writeheader()
        w.writerows(arch)
    with (work / 'replica_masks.jsonl').open('w', encoding='utf-8') as fh:
        for sha in sorted(masks_out):
            fh.write(json.dumps(masks_out[sha], sort_keys=True) + '\n')
    chosen, watches = [], set()
    for r in sorted((r for r in arch if r['class_label'] == 'gen'),
                    key=lambda r: hashlib.sha256(r['sha256'].encode()).hexdigest()):
        if r['physical_watch_id'] not in watches and len(chosen) < synth_max:
            chosen.append(r)
            watches.add(r['physical_watch_id'])
    with (work / 'synth_items.csv').open('w', newline='', encoding='utf-8') as fh:
        w = csv.DictWriter(fh, fieldnames=ARCH_FIELDS)
        w.writeheader()
        w.writerows(chosen)

    summary = {'model': model, 'items': len(seen), 'outcomes': dict(Counter(d['outcome'] for d in decisions)),
               'admitted_photos': len(rows), 'admitted_watches': len({r['physical_watch_id'] for r in rows}),
               'restored': restored, 'replica_photos': len(replicas),
               'replica_watches': len({a['physical_watch_id'] for _, a, _ in replicas}), 'synthetic_photos': len(chosen),
               'not_admitted_reasons': dict(Counter(r for d in decisions if d['outcome'] == 'not_admitted'
                                                    for r in d.get('reasons', []))),
               'mask_policy_version': el.MASK_POLICY_VERSION}
    (work / 'admission.json').write_text(json.dumps({'summary': summary, 'decisions': decisions}, indent=1) + '\n')
    print(json.dumps(summary, indent=1))
    return summary


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('items')
    ap.add_argument('work')
    ap.add_argument('--model', required=True)
    ap.add_argument('--synth-max', type=int, default=40)
    a = ap.parse_args()
    prepare(a.items, a.work, a.model, a.synth_max)
