"""Opt-in bridges over existing acquisition outputs; never acquires or measures."""
from hashlib import sha256
from pathlib import Path
import csv
import json
from .archive import IntegrityError
from .screening import POLICY_VERSION


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


def restore_acquired(archive, manifest, root):
    """Materialize selected legacy rows, preserving CSV and measurement code unchanged.

    Require full-watch eligibility. Partial-marker experiments need a consumer that
    enforces marker masks; the current calibrator is deliberately not such a consumer.
    """
    rows = list(csv.DictReader(Path(manifest).open(newline='', encoding='utf-8')))
    plan = []
    for row in rows:
        digest = row['sha256']
        records = [json.loads(r[0]) for r in archive.db.execute('SELECT body FROM associations WHERE sha=?', (digest,))]
        records = [r for r in records if r['association'].get('physical_watch_id') == row['physical_watch_id']
                   and r['association'].get('model') == row['model']]
        if not records or digest in archive._identity_conflicts():
            raise IntegrityError('missing or conflicting watch association')
        if not any(r['status'] == 'pass' and r['archive_policy_version'] == POLICY_VERSION
                   and r['profile'] in archive.screener.validated_profiles
                   and r['purpose'] == 'full_watch' and r['required_marker_ids']
                   and set(r['usable_marker_ids']) == set(r['required_marker_ids']) for r in records):
            raise IntegrityError('full-watch marker eligibility required')
        dest = safe_destination(root, row['local_path'])
        # Checked for every row before anything is written: a different existing file is never replaced.
        if dest.exists() and (not dest.is_file() or sha256(dest.read_bytes()).hexdigest() != digest):
            raise IntegrityError('restore destination holds different content')
        plan.append((digest, dest))
    for digest, dest in plan:
        archive.restore(digest, dest)
    return {'restored': len(plan), 'distinct_watches': len({r['physical_watch_id'] for r in rows})}
