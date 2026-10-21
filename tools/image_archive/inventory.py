"""Read-only historical catalogue inventory. CSV rows never prove byte availability."""
from collections import Counter, defaultdict
from hashlib import sha256
from pathlib import Path
import csv
import io
import json
import re
import subprocess
import zipfile
from .screening import Screener

HASH = re.compile(r'^[0-9a-f]{64}$')
EXTENSIONS = {'.jpg', '.jpeg', '.png', '.webp', '.tif', '.tiff'}


def available_originals(roots):
    """Hash actual files and ZIP members; never extract untrusted paths or refetch URLs."""
    found = {}
    for root in roots:
        for path in sorted(Path(root).rglob('*')):
            if not path.is_file() or '.git' in path.parts:
                continue
            if path.suffix.lower() in EXTENSIONS or HASH.fullmatch(path.name):
                data = path.read_bytes()
                found.setdefault(sha256(data).hexdigest(), (str(path), None))
            elif path.suffix.lower() == '.zip':
                try:
                    with zipfile.ZipFile(path) as z:
                        for member in z.infolist():
                            if (Path(member.filename).suffix.lower() in EXTENSIONS or HASH.fullmatch(Path(member.filename).name)) and member.file_size <= 80_000_000:
                                data = z.read(member)
                                found.setdefault(sha256(data).hexdigest(), (str(path), member.filename))
                except (zipfile.BadZipFile, OSError):
                    continue
    return found


def read_original(location):
    path, member = location
    if member is None:
        return Path(path).read_bytes()
    with zipfile.ZipFile(path) as z:
        return z.read(member)


def catalogue_rows(repo):
    for folder in ('tools/research', 'datasets', 'calibration'):
        for path in sorted((repo / folder).rglob('*.csv')):
            with path.open(newline='', encoding='utf-8-sig') as stream:
                reader = csv.DictReader(stream)
                if 'sha256' in (reader.fieldnames or []):
                    for row in reader:
                        yield str(path.relative_to(repo)), row
    # Branch evidence is read with git show, never checked out over the worktree.
    for branch in ('data/harvest', 'data/collected', 'testset-inbox'):
        ref = 'origin/' + branch
        tree = subprocess.run(['git', 'ls-tree', '-r', '--name-only', ref], cwd=repo, capture_output=True, text=True)
        if tree.returncode:
            continue
        paths = tree.stdout.splitlines()
        if 'state/images.jsonl' in paths:
            images = subprocess.check_output(['git', 'show', ref + ':state/images.jsonl'], cwd=repo, text=True)
            sources = subprocess.check_output(['git', 'show', ref + ':state/sources.jsonl'], cwd=repo, text=True)
            sources = {s['key']: s for s in map(json.loads, sources.splitlines()) if s}
            for image in map(json.loads, images.splitlines()):
                for key in image.get('source_keys', []) or ['']:
                    yield branch + '/state/images.jsonl', {**sources.get(key, {}), **image, 'source_url': key}
        for path in paths:
            if path.endswith('.csv') and ('manifest' in path or 'catalogue' in path):
                content = subprocess.check_output(['git', 'show', ref + ':' + path], cwd=repo, text=True)
                reader = csv.DictReader(io.StringIO(content))
                if 'sha256' in (reader.fieldnames or []):
                    for row in reader:
                        yield branch + '/' + path, row


def inventory(repo, roots=(), screener=None, decision_cache=None):
    repo = Path(repo)
    screener = screener or Screener()
    found = available_originals([repo, *roots])
    decisions = dict(decision_cache or {})
    datasets, groups = defaultdict(list), defaultdict(list)
    for dataset, row in catalogue_rows(repo):
        digest = row.get('sha256', '').lower()
        status, reasons = 'missing', ['original_bytes_not_found']
        family = row.get('model') or row.get('family') or 'unknown'
        if HASH.fullmatch(digest) and digest in found:
            key = (digest, family)
            if key not in decisions:
                data = read_original(found[digest])
                decisions[key] = screener.assess_bytes(data, family)
            d = decisions[key]
            status = {'pass':'suitable', 'fail':'unsuitable', 'inconclusive':'inconclusive'}[d.status]
            reasons = list(d.reasons)
        record = {'sha256': digest, 'physical_watch_id': row.get('physical_watch_id', ''),
                  'model': row.get('model') or 'unknown', 'provenance': row.get('provenance') or 'unknown',
                  'source': row.get('source_name') or row.get('source') or row.get('adapter') or row.get('source_type') or 'unknown',
                  'status': status, 'reasons': reasons}
        datasets[dataset].append(record)
        groups[(record['model'], record['provenance'], record['source'])].append(record)
    def summary(rows):
        identities = defaultdict(set)
        for r in rows:
            if HASH.fullmatch(r['sha256']) and r['physical_watch_id']:
                identities[r['sha256']].add(r['physical_watch_id'])
        hash_status = {}
        rank = {'missing': 0, 'suitable': 1, 'inconclusive': 2, 'unsuitable': 3}
        for row in rows:
            h = row['sha256']
            if HASH.fullmatch(h) and rank[row['status']] >= rank.get(hash_status.get(h), -1):
                hash_status[h] = row['status']
        return {'rows': len(rows), 'unique_sha256': len({r['sha256'] for r in rows if HASH.fullmatch(r['sha256'])}),
                'declared_watch_ids': len({r['physical_watch_id'] for r in rows if r['physical_watch_id']}),
                'available_unique_originals': len({r['sha256'] for r in rows if r['status'] != 'missing'}),
                'row_status': dict(Counter(r['status'] for r in rows)),
                'unique_hash_status': {s: sum(value == s for value in hash_status.values())
                                       for s in ('suitable', 'unsuitable', 'inconclusive', 'missing')},
                'suitable_watch_ids': len({r['physical_watch_id'] for r in rows if r['status'] == 'suitable' and r['physical_watch_id']}),
                'shared_photo_identity_conflicts': sum(len(ids) > 1 for ids in identities.values())}
    all_rows = [row for rows in datasets.values() for row in rows]
    return {'schema_version': 1, 'dry_run': True,
            'notes': ['Dataset counts overlap; do not sum them.', 'Declared identities are not validated independent watch counts.',
                      'Missing means exact original hash unavailable in scanned locations; no source refetch attempted.',
                      'Suitable = PASS under the validated face-on-v2 profile (docs/image-archive/FACE-ON-VALIDATION.md).'],
            'total': summary(all_rows), 'datasets': {name: summary(rows) for name, rows in datasets.items()},
            'by_model_provenance_source': [{'model': k[0], 'provenance': k[1], 'source': k[2], **summary(rows)} for k, rows in groups.items()],
            'available_decisions': [{'sha256': h, 'family': f, **d.record()} for (h, f), d in decisions.items()]}


def native_inventory(repo, roots=(), workers=3):
    """Read-only native inspection of each available exact original/model association."""
    from concurrent.futures import ThreadPoolExecutor
    from .faceon import MODELS, production_screener
    repo = Path(repo)
    found = available_originals([repo, *roots])
    keys = sorted({(r['sha256'].lower(), r.get('model') or r.get('family') or 'unknown')
                   for _, r in catalogue_rows(repo) if r.get('sha256', '').lower() in found})
    screen = production_screener()
    cache = {}
    # Compile once before concurrent readers use the same immutable native classes.
    first = next((k for k in keys if k[1] in MODELS), None)
    if first:
        cache[first] = screen.assess_bytes(read_original(found[first[0]]), first[1])
    def assess(key):
        h, family = key
        return key, screen.assess_bytes(read_original(found[h]), family)
    with ThreadPoolExecutor(max(1, min(3, workers))) as pool:
        cache.update(pool.map(assess, [k for k in keys if k not in cache]))
    report = inventory(repo, roots, decision_cache=cache)
    return compact_report(report)


def compact_report(report):
    """Retain screening evidence without duplicating whole measurement tables."""
    for decision in report['available_decisions']:
        analysis = decision['metrics'].get('analysis', {})
        rows = analysis.pop('track_measurements', [])
        if rows:
            analysis['analysis_dial_radius_px'] = rows[0]['rpx']
            analysis['marker_observations'] = [{k: r.get(k) for k in
                ('hour', 'state', 'gate', 'uncertain', 'hand_confirmed', 'cover', 'fit_rms_px')} for r in rows]
    return report


def write_report(path, report):
    """One catalogue/group/decision per line keeps large audit diffs reviewable."""
    dump = lambda value: json.dumps(value, ensure_ascii=False, allow_nan=False)
    sections = []
    for key, value in report.items():
        if isinstance(value, list):
            body = '[\n' + ',\n'.join('    ' + dump(item) for item in value) + '\n  ]'
        elif isinstance(value, dict):
            body = '{\n' + ',\n'.join('    ' + dump(k) + ': ' + dump(v) for k, v in value.items()) + '\n  }'
        else:
            body = dump(value)
        sections.append('  ' + dump(key) + ': ' + body)
    Path(path).write_text('{\n' + ',\n'.join(sections) + '\n}\n', encoding='utf-8')
