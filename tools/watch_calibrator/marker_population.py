#!/usr/bin/env python3
"""Per-marker genuine calibration populations under eligibility masks (counts only; no limit is computed).

For every layout, hour marker and per-marker quantity, a genuine physical watch counts once: the median of that
marker's readings over the watch's photographs in which the marker is ELIGIBLE. A photograph contributes only
where its validated mask (tools/image_archive/eligibility.py) makes the marker ELIGIBLE, so statistics for the 2
marker include only watches with an eligible 2. Photos shared by several declared watches (stock images) are
identity conflicts and are excluded, never split across watches. Watches are assigned to the locked-split
partitions (split.make_split, stratified by source) as whole watches, so no watch spans two partitions.

This reports independent sample sizes and source diversity. It does not calculate, change or deploy any tolerance.

    python tools/watch_calibrator/marker_population.py docs/image-archive/face-on-evidence.jsonl out.json [out.md]
"""
from __future__ import annotations

import argparse
import json
import re
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path
from urllib.parse import urlparse

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
for p in (str(REPO), str(HERE)):
    if p not in sys.path:
        sys.path.insert(0, p)
import split as locked_split  # noqa: E402
from tools.image_archive import eligibility as el  # noqa: E402

# per-marker readings in the evidence arrays [side, in/out, tilt, position, ...] (% of dial radius; tilt degrees).
# Round markers have no tilt; the triangle has no single position reading.
QUANTITIES = {'round': ('side', 'inout', 'pos'), 'baton': ('side', 'inout', 'tilt', 'pos'),
              'triangle': ('side', 'inout', 'tilt')}
INDEX = {'side': 0, 'inout': 1, 'tilt': 2, 'pos': 3}
# minimum independent genuine watches / sources before a marker metric could be calibrated (genuine_envelope defaults)
MIN_WATCHES, MIN_SOURCES = 8, 3
# one company publishing on two sites is one source
SOURCE_ALIASES = {'images.europeanwatch.com': 'europeanwatch.com',
                  'watches-of-switzerland.co.uk': 'watchesofswitzerland.com'}


def source_of(urls):
    hosts = Counter()
    for u in urls:
        if u and u.startswith('http'):
            h = (urlparse(u).hostname or '').lower()
            h = h[4:] if h.startswith('www.') else h
            hosts[SOURCE_ALIASES.get(h, h)] += 1
    return hosts.most_common(1)[0][0] if hosts else ''


def population(photos, masks):
    """photos: [{sha256, physical_watch_ids, class_label, source, model, m}]; masks: sha256 -> validated mask.

    Returns {model: {hour: {quantity: record}}} plus the exclusion accounting.
    """
    excluded = Counter()
    used = []
    for p in photos:
        if p['class_label'] != 'gen':
            excluded['not_genuine'] += 1
            continue
        ids = sorted(set(p['physical_watch_ids']))
        if len(ids) != 1:
            excluded['identity_conflict_or_unknown_watch'] += 1
            continue
        mask = masks.get(p['sha256'])
        if mask is None:
            excluded['no_valid_mask'] += 1
            continue
        if mask['sha256'] != p['sha256'] or mask['model'] != p['model']:
            raise el.MaskError('mask bound to another photograph or layout')
        used.append(dict(p, watch=ids[0], mask=mask))

    # one partition per physical watch (stratified by source), assigned before any statistic
    watch_source = {}
    for p in used:
        watch_source.setdefault(p['watch'], p['source'])
    split_rows = locked_split.make_split([{'physical_watch_id': w, 'class_label': 'gen', 'model': '',
                                           'source_name': s} for w, s in sorted(watch_source.items())])
    partition = {r['physical_watch_id']: r['partition'] for r in split_rows}

    out = defaultdict(dict)
    statuses = defaultdict(Counter)
    for p in used:
        for h, rec in p['mask']['markers'].items():
            if rec['status'] != el.NOT_APPLICABLE:
                statuses[(p['model'], int(h))][rec['status']] += 1
    for model in sorted({p['model'] for p in used}):
        for hour, shape in sorted(el.SHAPES[model].items()):
            per_q = {}
            for q in QUANTITIES[shape]:
                values = defaultdict(list)
                for p in used:
                    if p['model'] != model or not el.eligible(p['mask'], hour):
                        continue
                    v = (p['m'].get(str(hour)) or [None] * 4)[INDEX[q]]
                    if isinstance(v, (int, float)) and v == v:
                        values[p['watch']].append(v)
                medians = {w: statistics.median(v) for w, v in values.items()}
                by_source = Counter(watch_source[w] or 'unknown' for w in medians)
                qualifying = sorted(s for s in by_source if s != 'unknown')
                parts = Counter(partition[w] for w in medians)
                per_q[q] = {'genuine_watches': len(medians), 'photos': sum(len(v) for v in values.values()),
                            'sources': len(qualifying), 'watches_by_source': dict(sorted(by_source.items())),
                            'watches_by_partition': {k: parts.get(k, 0) for k in locked_split.PARTS},
                            'median_of_watch_medians': round(statistics.median(medians.values()), 5) if medians else None,
                            'underrepresented': len(medians) < MIN_WATCHES or len(qualifying) < MIN_SOURCES}
            out[model][hour] = {'shape': shape, 'statuses_in_admitted_photos': dict(statuses[(model, hour)]),
                                'metrics': per_q}
    return {'policy': {'mask_policy_version': el.MASK_POLICY_VERSION, 'max_excluded_markers': el.MAX_EXCLUDED_MARKERS,
                       'min_watches': MIN_WATCHES, 'min_sources': MIN_SOURCES,
                       'aggregation': 'median per physical watch per marker per quantity, eligible photos only'},
            'photos_used': len(used), 'watches_used': len(watch_source),
            'watches_used_by_model': dict(Counter(p['model'] for p in {p['watch']: p for p in used}.values())),
            'photos_excluded': dict(excluded), 'partition_of_watch': partition, 'populations': out}


def from_evidence(path, catalogue=None):
    """Every analysed photograph from the committed evidence (numbers only), with identity, class and source from the
    catalogues, its photo-level outcome and, when photographically suitable, its eligibility mask."""
    from tools.image_archive import analyse_validation as av
    if catalogue is None:
        from tools.image_archive.inventory import catalogue_rows
        catalogue = catalogue_rows(REPO)
    ids, cls, urls = defaultdict(set), defaultdict(set), defaultdict(list)
    for _, r in catalogue:
        s = (r.get('sha256') or '').lower()
        if r.get('physical_watch_id'):
            ids[s].add(r['physical_watch_id'])
        if r.get('class_label'):
            cls[s].add(r['class_label'].lower())
        urls[s] += [r.get(k) or '' for k in ('listing_url', 'source_url', 'url', 'source', 'image_url')]
    out = []
    for e in av.unique([json.loads(line) for line in open(path)]):
        if e.get('error') or e.get('model') not in el.REQUIRED:
            continue
        status, reasons = av.decide(e)
        photo_level = sorted(r for r in reasons if not re.search(r'_marker_\d+$', r))
        c = cls.get(e['sha256'], set()) - {''}
        rec = {'sha256': e['sha256'], 'model': e['model'], 'm': e.get('m') or {},
               'physical_watch_ids': ids.get(e['sha256']) or ({e['physical_watch_id']} if e.get('physical_watch_id') else set()),
               'class_label': next(iter(c)) if len(c) == 1 else 'unknown', 'source': source_of(urls.get(e['sha256'], [])),
               'photo_reasons': photo_level, 'mask': None, 'admitted': False}
        if not photo_level:            # masking never excuses a photo-level failure
            rec['mask'] = el.build(e['sha256'], e['family'], el.marker_statuses(e, e['model']), 'face-on-v3')
            el.validate(rec['mask'], e['sha256'], max_excluded=len(el.REQUIRED[e['model']]))
            rec['admitted'] = len(rec['mask']['eligible_marker_ids']) >= el.min_eligible(e['model'])
        out.append(rec)
    return out


def accounting(records):
    """Per family: photographic suitability, masks, admission and the reasons markers were not eligible (genuine)."""
    from tools.image_archive.analyse_validation import primary
    out = {}
    for model in sorted({r['model'] for r in records}):
        R = [r for r in records if r['model'] == model and r['class_label'] == 'gen']
        suitable = [r for r in R if r['mask']]
        admitted = [r for r in suitable if r['admitted']]
        reasons = defaultdict(Counter)
        eligible_photos = Counter()
        for r in suitable:
            for h, m in r['mask']['markers'].items():
                if m['status'] == el.ELIGIBLE:
                    eligible_photos[int(h)] += r['admitted']
                elif m['status'] != el.NOT_APPLICABLE:
                    for why in m['reasons']:
                        reasons[int(h)][m['status'] + ':' + why] += 1
        out[model] = {
            'genuine_photos_analysed': len(R),
            'photographic_suitability_pass': len(suitable),
            'photo_level_rejections': dict(Counter(primary('fail' if any(x.startswith('fail') for x in r['photo_reasons'])
                                                           else 'inconclusive', r['photo_reasons'])
                                                   for r in R if not r['mask']).most_common()),
            'with_at_least_one_eligible_marker': sum(bool(r['mask']['eligible_marker_ids']) for r in suitable),
            'admitted_for_masked_calibration': len(admitted),
            'rejected_too_many_excluded_markers': len(suitable) - len(admitted),
            'full_watch_photos': sum(r['mask']['full_watch'] for r in suitable),
            'eligible_photos_by_marker_in_admitted': {h: eligible_photos.get(h, 0) for h in el.REQUIRED[model]},
            'non_eligible_reasons_by_marker_in_suitable': {h: dict(reasons[h].most_common()) for h in el.REQUIRED[model]},
        }
    return out


def markdown(full, masked):
    lines = ['| Layout | Marker | Shape | Quantity | Watches: full-watch | Watches: masked | Sources (masked) | '
             'Dev/Val/Hold watches | Underrepresented |', '|---|---:|---|---|---:|---:|---:|---|---|']
    for model, hours in masked['populations'].items():
        for hour, rec in hours.items():
            for q, r in rec['metrics'].items():
                f = full['populations'].get(model, {}).get(hour, {}).get('metrics', {}).get(q, {})
                parts = '/'.join(str(r['watches_by_partition'][k]) for k in locked_split.PARTS)
                lines.append('| %s | %d | %s | %s | %d | %d | %d | %s | %s |' % (
                    model, hour, rec['shape'], q, f.get('genuine_watches', 0), r['genuine_watches'], r['sources'], parts,
                    'yes' if r['underrepresented'] else ''))
    return '\n'.join(lines) + '\n'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('evidence')
    ap.add_argument('out_json')
    ap.add_argument('out_md', nargs='?')
    a = ap.parse_args(argv)
    from tools.image_archive.inventory import catalogue_rows
    catalogue = list(catalogue_rows(REPO))
    result = {}
    records = from_evidence(a.evidence, catalogue)
    result['accounting'] = accounting(records)
    for name, keep in (('full_watch', lambda r: r['admitted'] and r['mask']['full_watch']),
                       ('marker_masked', lambda r: r['admitted'])):
        chosen = [r for r in records if keep(r)]
        result[name] = population(chosen, {r['sha256']: r['mask'] for r in chosen})
    Path(a.out_json).write_text(json.dumps(result, indent=1, sort_keys=True) + '\n', encoding='utf-8')
    if a.out_md:
        Path(a.out_md).write_text(markdown(result['full_watch'], result['marker_masked']), encoding='utf-8')
    for name in ('full_watch', 'marker_masked'):
        pop = result[name]
        print(name, 'photos', pop['photos_used'], 'watches', pop['watches_used'], pop['watches_used_by_model'],
              'excluded', pop['photos_excluded'])
    print(json.dumps(result['accounting'], indent=1))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
