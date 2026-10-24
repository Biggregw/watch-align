"""Focused tests for unreviewed backup; no OneDrive, credential or network calls."""
import hashlib
import json
from unittest.mock import patch

from tools.image_archive import unreviewed


def sample_image():
    return b'\xff\xd8\xff' + b'photo-original-bytes' * 20


def test_image_magic_rejects_nonphotos():
    assert unreviewed.image_extension(sample_image()) == 'jpg'
    assert unreviewed.image_extension(b'not a photo') is None
    assert unreviewed.image_extension(b'RIFF1234WEBPsource') == 'webp'


def test_candidates_include_unreviewed_even_without_metadata(tmp_path):
    root = tmp_path / 'repo'
    root.mkdir()
    originals = tmp_path / 'download'
    originals.mkdir()
    raw = sample_image()
    digest = hashlib.sha256(raw).hexdigest()
    (originals / digest).write_bytes(raw)
    with patch.object(unreviewed, 'catalogue_rows', return_value=iter([])):
        records = unreviewed.candidates(root, [originals])
    assert len(records) == 1
    assert records[0]['sha256'] == digest
    assert records[0]['review_status'] == 'unreviewed'
    assert records[0]['calibration_approved'] is False
    assert records[0]['associations'] == []
    assert records[0]['key'].startswith('unreviewed/objects/')
    assert 'local_location' not in unreviewed.public_record(records[0])


def test_backup_verifies_bytes_and_persists_manifest(tmp_path):
    raw = sample_image()
    digest = hashlib.sha256(raw).hexdigest()
    file = tmp_path / digest
    file.write_bytes(raw)
    record = {'sha256': digest, 'key': f'unreviewed/objects/{digest[:2]}/{digest}.jpg',
              'format': 'jpg', 'size': len(raw), 'review_status': 'unreviewed',
              'calibration_approved': False, 'associations': [],
              'physical_watch_identity_conflict': False, 'local_location': (str(file), None)}
    class FakeDrive:
        def __init__(self): self.saved = {}
        def put(self, key, data):
            if key in self.saved:
                assert self.saved[key] == data
            self.saved[key] = data
    drive = FakeDrive()
    result = unreviewed.backup(drive, [record], batch_size=1)
    assert result['uploaded_or_verified'] == 1
    manifest = json.loads(drive.saved[result['manifest_keys'][0]])
    assert manifest['purpose'] == 'unaltered_original_backup_only'
    assert manifest['calibration_approved'] is False
    assert manifest['items'][0]['sha256'] == digest
    assert 'local_location' not in manifest['items'][0]
    assert drive.saved[record['key']] == raw
    file.write_bytes(raw + b'changed')
    try:
        unreviewed.backup(drive, [record])
        assert False, 'expected original change to abort'
    except ValueError as exc:
        assert 'changed' in str(exc)


def test_dry_run_never_connects_to_onedrive(tmp_path):
    repo = tmp_path / 'repo'; repo.mkdir()
    (repo / 'photo.jpg').write_bytes(sample_image())
    with patch.object(unreviewed, 'catalogue_rows', return_value=iter([])), \
         patch.object(unreviewed, 'client', side_effect=AssertionError('should never connect')):
        result = unreviewed.main(['--repo', str(repo)])
    assert result['mode'] == 'dry_run'
    assert result['unique_originals'] == 1


def test_device_login_uses_microsoft_issued_url():
    from tools.image_archive.auth import device_login
    class App:
        def initiate_device_flow(self, scopes):
            return {'user_code': 'test-only', 'verification_uri': 'https://www.microsoft.com/link'}
        def acquire_token_by_device_flow(self, flow):
            return {'access_token': 'not-for-output'}
        def get_accounts(self): return [{'username': 'owner@example.test'}]
    class Cache:
        def serialize(self): return 'not-for-output'
    printed = []
    device_login(App(), Cache(), lambda _: None, printed.append)
    assert len(printed) == 1
    assert 'https://www.microsoft.com/link' in printed[0]
    assert 'not-for-output' not in printed[0]


def test_codespace_verify_is_read_only_and_reports_orphans(tmp_path):
    import json as _json
    from unittest.mock import patch
    from tools.image_archive import unreviewed
    from tools.image_archive.tests.fake_drive import FakeDrive
    raw = b"\xff\xd8\xff" + b"photo" * 40
    digest = hashlib.sha256(raw).hexdigest()
    drive = FakeDrive()
    key = f"unreviewed/objects/{digest[:2]}/{digest}.jpg"
    drive.files[key] = raw
    drive.files["unreviewed/manifests/runs/x-0000.json"] = _json.dumps({
        "schema_version": 1, "purpose": "unaltered_original_backup_only", "calibration_approved": False,
        "review_status": "unreviewed", "items": [{"sha256": digest, "key": key, "size": len(raw), "format": "jpg",
                                                   "review_status": "unreviewed", "calibration_approved": False}]}).encode()
    orphan = b"\xff\xd8\xff" + b"orphan" * 40
    od = hashlib.sha256(orphan).hexdigest()
    drive.files[f"unreviewed/objects/{od[:2]}/{od}.jpg"] = orphan
    (tmp_path / "repo").mkdir()
    cache = tmp_path / "private" / "cache.json"
    cache.parent.mkdir()
    cache.write_text("{}")
    before = dict(drive.files)
    with patch.object(unreviewed, "OneDriveBackend", return_value=drive), \
         patch.object(unreviewed, "client", return_value=(None, None)), \
         patch.object(unreviewed, "token_provider", return_value=lambda: "t"):
        result = unreviewed.main(["--repo", str(tmp_path / "repo"), "--verify", "--authorized", "--client-id", "id",
                                  "--cache", str(cache)])
    assert result["all_indexed_verified"] and result["indexed_originals"] == 1
    assert result["orphans"] == 1 and result["repaired"] == 0
    assert drive.files == before
