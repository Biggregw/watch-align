"""Immutable original blobs and transactional local catalogue; no deletion/migration."""
from __future__ import annotations

from collections import Counter
from hashlib import sha256
from pathlib import Path
import json
import os
import re
import sqlite3
import tempfile
import uuid
from .screening import POLICY_VERSION, Screener


class IntegrityError(ValueError):
    pass


def verify(data, digest):
    if not re.fullmatch(r'[0-9a-f]{64}', digest) or sha256(data).hexdigest() != digest:
        raise IntegrityError('SHA-256 mismatch')


def atomic_write(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(dir=path.parent, prefix='.archive-')
    try:
        with os.fdopen(fd, 'wb') as out:
            out.write(data)
            out.flush()
            os.fsync(out.fileno())
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


class FilesystemBackend:
    def __init__(self, root):
        self.root = Path(root)

    def _path(self, key):
        path = (self.root / key).resolve()
        if not path.is_relative_to(self.root.resolve()):
            raise ValueError('unsafe object key')
        return path

    def get(self, key):
        return self._path(key).read_bytes()

    def list_objects(self):
        root = self._path('objects')
        return [str(p.relative_to(self.root.resolve())) for p in root.rglob('*') if p.is_file()]

    def put(self, key, data):
        path = self._path(key)
        path.parent.mkdir(parents=True, exist_ok=True)
        # Publish a fully fsynced temporary file with create-only link semantics.
        fd, temp = tempfile.mkstemp(dir=path.parent)
        try:
            with os.fdopen(fd, 'wb') as out:
                out.write(data)
                out.flush()
                os.fsync(out.fileno())
            try:
                os.link(temp, path)
            except FileExistsError:
                if path.read_bytes() != data:
                    raise IntegrityError('immutable object conflict')
        finally:
            os.unlink(temp)
        if path.read_bytes() != data:
            raise IntegrityError('stored object differs')


class Archive:
    def __init__(self, root, backend=None, screener=None):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self.backend = backend or FilesystemBackend(self.root / 'storage')
        if screener is None:
            from .diagnostics import FamilyDiagnostics
            screener = Screener(FamilyDiagnostics())
        self.screener = screener
        self.db = sqlite3.connect(self.root / 'catalogue.sqlite', timeout=30)
        self.db.executescript('''
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS decisions(id INTEGER PRIMARY KEY, sha TEXT, body TEXT);
            CREATE TABLE IF NOT EXISTS objects(sha TEXT PRIMARY KEY, key TEXT, status TEXT, error TEXT);
            CREATE TABLE IF NOT EXISTS associations(sha TEXT, body TEXT, PRIMARY KEY(sha,body));
        ''')

    def put_verified(self, original_file, expected_sha256, association):
        # Read once: the decision and storage see exactly the same bytes (no TOCTOU).
        data = Path(original_file).read_bytes()
        verify(data, expected_sha256)
        association = dict(association)
        if not association.get('provenance'):
            # Reuse the harvester's audited source-host rules, without re-inferring model geometry.
            from tools.dataset_harvester.harvester.metadata import infer
            inferred = infer('', association.get('source_url', ''))
            if inferred.class_label == association.get('class_label'):
                association['provenance'] = inferred.provenance
                association['provenance_evidence'] = inferred.evidence
        decision = self.screener.assess_bytes(data, association.get('model') or association.get('family', ''), 'full_watch')
        reasons = list(decision.reasons)
        if not all(association.get(k) for k in ('model', 'physical_watch_id', 'source_url', 'provenance')):
            reasons.append('inconclusive_source_identity')
        if association.get('provenance') not in {'official', 'established_dealer', 'rep_labelled', 'owner_verified', 'auction_house', 'rolex_cpo'}:
            reasons.append('inconclusive_unverified_provenance')
        if association.get('class_label') == 'gen':
            association.setdefault('factory', 'Rolex')
        elif association.get('class_label') == 'rep' and not association.get('factory'):
            reasons.append('inconclusive_factory_identity')
        if association.get('class_label') not in {'gen', 'rep'}:
            reasons.append('inconclusive_classification')
        if association.get('provenance') == 'official' and not association.get('sample_role'):
            reasons.append('inconclusive_reference_role_unknown')
        if association.get('sample_role', 'population') not in {'population', 'reference_only'}:
            reasons.append('inconclusive_sample_role')
        body = decision.record()
        body['association'] = association
        body['reasons'] = sorted(set(reasons))
        if reasons and body['status'] == 'pass':
            body['status'] = 'inconclusive'
        with self.db:
            self.db.execute('INSERT INTO decisions(sha,body) VALUES (?,?)', (expected_sha256, json.dumps(body)))
        if body['status'] != 'pass':
            return {'status': body['status'], 'decision': body}
        # Canonical format extension makes byte dedup independent of source filename.
        ext = {'JPEG': 'jpg', 'PNG': 'png', 'WEBP': 'webp', 'TIFF': 'tiff'}[decision.metrics['format']]
        key = f'objects/{expected_sha256[:2]}/{expected_sha256}.{ext}'
        body['original_extension'] = Path(original_file).suffix
        assoc = json.dumps(body, sort_keys=True)
        pending = self.root / 'pending' / expected_sha256
        atomic_write(pending, data)
        with self.db:
            self.db.execute('INSERT OR REPLACE INTO objects VALUES (?,?,?,?)', (expected_sha256, key, 'pending', ''))
            self.db.execute('INSERT OR IGNORE INTO associations VALUES (?,?)', (expected_sha256, assoc))
        return self._upload(expected_sha256, key, data)

    def _upload(self, digest, key, data):
        try:
            self.backend.put(key, data)
            verify(self.backend.get(key), digest)
            # Immutable unique snapshots avoid lost updates from concurrent Actions jobs.
            rows = [json.loads(r[0]) for r in self.db.execute('SELECT body FROM associations WHERE sha=?', (digest,))]
            manifest = json.dumps({'schema_version': 1, 'sha256': digest, 'key': key, 'associations': rows}, sort_keys=True).encode()
            manifest_key = f'manifests/runs/{uuid.uuid4().hex}.json'
            self.backend.put(manifest_key, manifest)
            if self.backend.get(manifest_key) != manifest:
                raise IntegrityError('manifest verification failed')
        except Exception as exc:
            # Never persist exception messages: HTTP errors can contain credentials/URLs.
            code = getattr(exc, 'code', type(exc).__name__)
            if not isinstance(code, str) or not re.fullmatch(r'[A-Za-z0-9_]+', code):
                code = type(exc).__name__
            with self.db:
                self.db.execute('UPDATE objects SET status=?,error=? WHERE sha=?', ('pending', code, digest))
            return {'status': 'pending', 'sha256': digest, 'reason': code}
        with self.db:
            self.db.execute('UPDATE objects SET status=?,error=? WHERE sha=?', ('archived', '', digest))
        # Keep pending originals for now: no source/pending pruning without explicit action.
        return {'status': 'archived', 'sha256': digest, 'key': key}

    def sync(self):
        results = []
        for digest, key in self.db.execute("SELECT sha,key FROM objects WHERE status='pending'").fetchall():
            try:
                data = (self.root / 'pending' / digest).read_bytes()
                verify(data, digest)
            except (OSError, IntegrityError) as exc:
                results.append({'sha256': digest, 'status': 'pending', 'reason': type(exc).__name__})
                continue
            rows = [json.loads(r[0]) for r in self.db.execute('SELECT body FROM associations WHERE sha=?', (digest,))]
            if not rows or any(r['archive_policy_version'] != POLICY_VERSION or r['profile'] not in self.screener.validated_profiles for r in rows):
                results.append({'sha256': digest, 'status': 'withheld_policy_changed'})
                continue
            # Reassess bytes on every retry, rather than trusting stale PASS metadata.
            decisions = [self.screener.assess_bytes(data, r['family'], r['purpose']) for r in rows]
            if any(d.status != 'pass' for d in decisions):
                results.append({'sha256': digest, 'status': 'withheld_reassessment'})
                continue
            results.append(self._upload(digest, key, data))
        return results

    def restore(self, digest, destination):
        row = self.db.execute('SELECT key,status FROM objects WHERE sha=?', (digest,)).fetchone()
        if not row or row[1] != 'archived':
            raise ValueError('no verified archived object')
        data = self.backend.get(row[0])
        verify(data, digest)
        atomic_write(destination, data)
        return Path(destination)

    def import_snapshot(self, key):
        """Rebuild catalogue on a new runner from a selected immutable remote snapshot."""
        doc = json.loads(self.backend.get(key))
        digest, object_key = doc['sha256'], doc['key']
        verify(self.backend.get(object_key), digest)
        if doc['schema_version'] != 1 or not object_key.startswith(f'objects/{digest[:2]}/{digest}.'):
            raise IntegrityError('invalid snapshot')
        for body in doc['associations']:
            if body['sha256'] != digest or body['status'] != 'pass':
                raise IntegrityError('invalid admission record')
        with self.db:
            self.db.execute('INSERT OR REPLACE INTO objects VALUES (?,?,?,?)', (digest, object_key, 'archived', ''))
            for body in doc['associations']:
                self.db.execute('INSERT OR IGNORE INTO associations VALUES (?,?)', (digest, json.dumps(body, sort_keys=True)))

    def reconcile(self, dry_run=True):
        if not dry_run:
            raise ValueError('reconciliation is report-only; use sync for pending objects')
        result = []
        for digest, key, status in self.db.execute('SELECT sha,key,status FROM objects'):
            try:
                verify(self.backend.get(key), digest)
                observed = 'verified'
            except FileNotFoundError:
                observed = 'missing'
            except IntegrityError:
                observed = 'corrupt'
            except Exception:
                observed = 'unavailable'
            result.append({'sha256': digest, 'status': status, 'observed': observed})
        indexed = {row[0] for row in self.db.execute('SELECT key FROM objects')}
        try:
            for key in self.backend.list_objects():
                if key not in indexed:
                    result.append({'key': key, 'observed': 'unlinked'})
        except Exception:
            result.append({'observed': 'object_listing_unavailable'})
        return result

    def report(self):
        events = [json.loads(r[0]) for r in self.db.execute('SELECT body FROM decisions ORDER BY id')]
        latest = {}
        for event in events:
            a = event['association']
            latest[(event['sha256'], a.get('model'), a.get('physical_watch_id'), a.get('source_url'))] = event
        decisions = list(latest.values())
        associations = [json.loads(r[0])['association'] for r in self.db.execute("SELECT body FROM associations JOIN objects USING(sha) WHERE status='archived'")]
        watches = {a['physical_watch_id'] for a in associations if a.get('sample_role', 'population') == 'population'}
        return {'assessment_events': len(events), 'decisions': dict(Counter(d['status'] for d in decisions)),
                'unique_photographs_by_status': {status: len({d['sha256'] for d in decisions if d['status'] == status})
                                                for status in ('pass', 'fail', 'inconclusive')},
                'reasons': dict(Counter(r for d in decisions for r in d['reasons'])),
                'objects': dict(Counter(r[0] for r in self.db.execute('SELECT status FROM objects'))),
                'distinct_watch_ids': len(watches),
                'identity_note': 'Explicit identities only; shared photos are conflicts, not independent watch evidence.',
                'shared_photo_conflicts': self._identity_conflicts(),
                'unconflicted_watch_ids': len(watches - {w for ids in self._identity_conflicts().values() for w in ids}),
                'by_model': dict(Counter(d['association'].get('model', 'unknown') for d in decisions)),
                'by_provenance': dict(Counter(d['association'].get('provenance', 'unknown') for d in decisions)),
                'by_source': dict(Counter(d['association'].get('source_name') or d['association'].get('source_url', 'unknown') for d in decisions))}

    def _identity_conflicts(self):
        out = {}
        for digest, in self.db.execute('SELECT sha FROM objects'):
            ids = {json.loads(r[0])['association']['physical_watch_id'] for r in self.db.execute('SELECT body FROM associations WHERE sha=?', (digest,))}
            if len(ids) > 1:
                out[digest] = sorted(ids)
        return out
