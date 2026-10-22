"""Opt-in bridges over existing acquisition outputs; never acquires or measures."""
from hashlib import sha256
from pathlib import Path
import csv
import json
from .archive import IntegrityError, _valid_mask, atomic_write
from .screening import POLICY_VERSION

# Written beside acquired_images.csv by a marker_masked restore. The watch calibrator reads masks only from here and
# refuses to run its unmasked legacy path over an acquisition root that holds this file.
MASKS_FILE = 'eligibility_masks.jsonl'


def submit_acquired(archive, root, rows):
    results = []
    for row in rows:
        path = safe_destination(root, row['local_path'])
        if not path.is_file():
            results.append({'status': 'missing', 'sha256': row.get('sha256', '')})
            continue
        results.append(archive.put_verified(path, row['sha256'], row))
    return results


def safe_destination(root, relative):
    root = Path(root).resolve()
    path = (root / relative).resolve()
    if Path(relative).is_absolute() or not path.is_relative_to(root) or path == root:
        raise ValueError('unsafe legacy image path')
    return path


def _admitted(archive, r):
    return (r['status'] == 'pass' and r['archive_policy_version'] == POLICY_VERSION
            and r['profile'] in archive.screener.validated_profiles and bool(r['required_marker_ids']))


def _full_watch(archive, r):
    if not _admitted(archive, r):
        return False
    if r['purpose'] == 'full_watch':
        return set(r['usable_marker_ids']) == set(r['required_marker_ids'])
    return r['purpose'] == 'marker_masked' and _valid_mask(r) and r['marker_eligibility']['full_watch']


def restore_acquired(archive, manifest, root, purpose='full_watch'):
    """Materialize selected legacy rows, preserving the CSV and measurement code unchanged.

    purpose='full_watch' (default, the legacy calibrator): every row must be fully eligible.
    purpose='marker_masked': every row must carry a valid SHA-bound eligibility mask, written to
    root/eligibility_masks.jsonl for the masked calibrator; a partially eligible photograph is never
    restored without its mask.
    """
    if purpose not in ('full_watch', 'marker_masked'):
        raise ValueError('unknown restore purpose')
    rows = list(csv.DictReader(Path(manifest).open(newline='', encoding='utf-8')))
    plan, masks = [], {}
    for row in rows:
        digest = row['sha256']
        records = [json.loads(r[0]) for r in archive.db.execute('SELECT body FROM associations WHERE sha=?', (digest,))]
        records = [r for r in records if r['association'].get('physical_watch_id') == row['physical_watch_id']
                   and r['association'].get('model') == row['model']]
        if not records or digest in archive._identity_conflicts():
            raise IntegrityError('missing or conflicting watch association')
        if purpose == 'full_watch':
            if not any(_full_watch(archive, r) for r in records):
                raise IntegrityError('full-watch marker eligibility required')
        else:
            good = [r for r in records if _admitted(archive, r) and r['purpose'] == 'marker_masked' and _valid_mask(r)]
            if not good or len({r['marker_eligibility']['mask_digest'] for r in good}) != 1:
                raise IntegrityError('a valid, unambiguous eligibility mask is required')
            masks[digest] = good[0]['marker_eligibility']
        dest = safe_destination(root, row['local_path'])
        # Checked for every row before anything is written: a different existing file is never replaced.
        if dest.exists() and (not dest.is_file() or sha256(dest.read_bytes()).hexdigest() != digest):
            raise IntegrityError('restore destination holds different content')
        plan.append((digest, dest))
    if masks:
        path = Path(root) / MASKS_FILE
        existing = {}
        if path.exists():
            for line in path.read_text(encoding='utf-8').splitlines():
                m = json.loads(line)
                existing[m['sha256']] = m
        for digest, m in masks.items():
            if digest in existing and existing[digest]['mask_digest'] != m['mask_digest']:
                raise IntegrityError('a different eligibility mask is already restored for this photograph')
        existing.update(masks)
    for digest, dest in plan:
        archive.restore(digest, dest)
    if masks:
        atomic_write(path, ''.join(json.dumps(existing[d], sort_keys=True) + '\n' for d in sorted(existing)).encode())
    return {'restored': len(plan), 'distinct_watches': len({r['physical_watch_id'] for r in rows}),
            'masks': len(masks)}
