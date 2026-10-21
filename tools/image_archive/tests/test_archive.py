import csv
import hashlib
import json
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor

import pytest
from PIL import Image

from tools.image_archive import Archive, FilesystemBackend, Screener
from tools.image_archive.screening import REQUIRED_CHECKS
from tools.image_archive.archive import IntegrityError
from tools.image_archive.integration import restore_acquired, safe_destination


@pytest.fixture
def photo(tmp_path):
    path = tmp_path / 'original.png'
    Image.new('RGB', (1000, 1000), 'gray').save(path)
    return path


def evidence(img, family, purpose):
    # Synthetic contract fixture ONLY: never installed in the production registry.
    return {'profile': 'test-only', 'checks': dict.fromkeys(REQUIRED_CHECKS, True),
            'required_marker_ids': list(range(1, 13)), 'markers': {str(m): True for m in range(1, 13)}}


def screener():
    return Screener(evidence, ['test-only'])


def association(**kw):
    return dict({'family': 'test', 'model': 'test_model', 'physical_watch_id': 'watch1',
                 'source_url': 'https://example.test/listing/1', 'provenance': 'owner_verified', 'class_label': 'gen'}, **kw)


def put(archive, photo, **kw):
    return archive.put_verified(photo, hashlib.sha256(photo.read_bytes()).hexdigest(), association(**kw))


@pytest.mark.parametrize('check', REQUIRED_CHECKS)
@pytest.mark.parametrize('value,status', [(False, 'fail'), (None, 'inconclusive')])
def test_every_signal_is_mandatory(photo, check, value, status):
    def analyze(*args):
        doc = evidence(*args)
        doc['checks'][check] = value
        return doc
    decision = Screener(analyze, ['test-only']).assess_candidate(photo, 'test')
    assert decision.status == status
    assert any(check in r for r in decision.reasons)


def test_no_historical_suitable_bypass(photo, tmp_path):
    archive = Archive(tmp_path / 'archive', screener=Screener(lambda *a: {'suitable': True}))
    assert put(archive, photo)['status'] == 'inconclusive'
    assert not list((archive.root / 'storage').rglob('*'))
    assert not (archive.root / 'pending').exists()


def test_unvalidated_complete_evidence_withheld(photo):
    assert Screener(evidence).assess_candidate(photo, 'test').status == 'inconclusive'


def test_corrupt_and_nonfinite(photo):
    assert screener().assess_bytes(b'broken', 'test').status == 'fail'
    def bad(*args):
        doc = evidence(*args)
        doc['tilt'] = float('nan')
        return doc
    assert Screener(bad, ['test-only']).assess_candidate(photo, 'test').status == 'inconclusive'


def test_marker_occlusion_never_silent(photo):
    def analyze(*args):
        doc = evidence(*args)
        doc['markers']['6'] = False
        return doc
    screen = Screener(analyze, ['test-only'])
    decision = screen.assess_candidate(photo, 'test')
    assert decision.status == 'fail'
    assert 6 not in decision.usable_marker_ids
    assert 'inconclusive_partial_marker_use_not_enabled' in screen.assess_candidate(photo, 'test', 'partial').reasons


def test_exact_bytes_dedup_restore_and_watches(photo, tmp_path):
    archive = Archive(tmp_path / 'archive', screener=screener())
    result = put(archive, photo)
    assert result['status'] == 'archived'
    assert put(archive, photo)['status'] == 'archived'
    second = photo.with_name('other.png')
    Image.new('RGB', (1000, 1000), 'blue').save(second)
    put(archive, second)
    assert archive.report()['distinct_watch_ids'] == 1
    assert len(list((archive.root / 'storage' / 'objects').rglob('*.png'))) == 2
    dest = tmp_path / 'restore.png'
    archive.restore(result['sha256'], dest)
    assert dest.read_bytes() == photo.read_bytes()
    put(archive, photo, physical_watch_id='different_watch')
    assert archive.report()['distinct_watch_ids'] == 2
    assert result['sha256'] in archive.report()['shared_photo_conflicts']


def test_snapshot_recovers_fresh_catalogue(photo, tmp_path):
    archive = Archive(tmp_path / 'archive', screener=screener())
    result = put(archive, photo)
    fresh = Archive(tmp_path / 'fresh', backend=archive.backend)
    snapshots = list((archive.root / 'storage' / 'manifests').rglob('*.json'))
    fresh.import_snapshot(str(snapshots[0].relative_to(archive.root / 'storage')))
    fresh.restore(result['sha256'], tmp_path / 'new.png')
    assert (tmp_path / 'new.png').read_bytes() == photo.read_bytes()


def test_failure_pending_retry_and_policy_change(photo, tmp_path):
    class Offline(FilesystemBackend):
        def put(self, *a):
            raise OSError('secret must not appear')
    archive = Archive(tmp_path / 'archive', backend=Offline(tmp_path / 'remote'), screener=screener())
    assert put(archive, photo)['status'] == 'pending'
    assert 'secret' not in json.dumps(archive.report())
    assert list((archive.root / 'pending').iterdir())[0].read_bytes() == photo.read_bytes()
    archive.backend = FilesystemBackend(tmp_path / 'remote')
    archive.screener = Screener()
    assert archive.sync()[0]['status'] == 'withheld_policy_changed'
    archive.screener = screener()
    assert archive.sync()[0]['status'] == 'archived'
    assert archive.sync() == []


def test_corrupt_restore_does_not_replace_destination(photo, tmp_path):
    archive = Archive(tmp_path / 'archive', screener=screener())
    result = put(archive, photo)
    (archive.root / 'storage' / result['key']).write_bytes(b'corrupt')
    dest = tmp_path / 'dest'
    dest.write_bytes(b'existing')
    with pytest.raises(IntegrityError):
        archive.restore(result['sha256'], dest)
    assert dest.read_bytes() == b'existing'
    assert archive.reconcile()[0]['observed'] == 'corrupt'


def test_concurrent_create_is_immutable(tmp_path):
    backend = FilesystemBackend(tmp_path)
    with ThreadPoolExecutor(4) as pool:
        list(pool.map(lambda _: backend.put('objects/abc', b'original'), range(12)))
    with pytest.raises(IntegrityError):
        backend.put('objects/abc', b'changed')
    assert backend.get('objects/abc') == b'original'


def test_restore_calibrator_paths_and_manifest_unchanged(photo, tmp_path):
    archive = Archive(tmp_path / 'archive', screener=screener())
    result = put(archive, photo)
    manifest = tmp_path / 'acquired_images.csv'
    manifest.write_text('sha256,physical_watch_id,model,local_path\n' + result['sha256'] + ',watch1,test_model,images/candidate/0_original.png\n')
    before = manifest.read_bytes()
    restored = restore_acquired(archive, manifest, tmp_path / 'acquisition')
    assert restored == {'restored': 1, 'distinct_watches': 1}
    assert manifest.read_bytes() == before
    assert (tmp_path / 'acquisition/images/candidate/0_original.png').read_bytes() == photo.read_bytes()
    put(archive, photo, physical_watch_id='another')
    with pytest.raises(IntegrityError):
        restore_acquired(archive, manifest, tmp_path / 'acquisition')


@pytest.mark.parametrize('path', ['../outside', '/etc/passwd', 'images/../../outside'])
def test_path_traversal(tmp_path, path):
    with pytest.raises(ValueError):
        safe_destination(tmp_path, path)


def test_unverified_provenance_never_stored(photo, tmp_path):
    archive = Archive(tmp_path / 'archive', screener=screener())
    assert put(archive, photo, provenance='seller_claim')['status'] == 'inconclusive'
    assert archive.report()['objects'] == {}


def test_full_watch_cannot_mean_only_twelve(photo):
    def analyze(*args):
        doc=evidence(*args)
        doc['required_marker_ids']=[12]
        return doc
    d=Screener(analyze,['test-only']).assess_candidate(photo,'test')
    assert d.status=='inconclusive'
    assert 'inconclusive_marker_layout' in d.reasons


def test_reference_only_excluded_from_watch_population(photo,tmp_path):
    archive=Archive(tmp_path/'archive',screener=screener())
    assert put(archive,photo,sample_role='reference_only',provenance='official')['status']=='archived'
    assert archive.report()['distinct_watch_ids']==0


def test_bobs_acquisition_reuses_source_provenance(photo,tmp_path):
    archive=Archive(tmp_path/'archive',screener=screener())
    assoc=association(source_url='https://www.bobswatches.com/listing/1')
    del assoc['provenance']
    result=archive.put_verified(photo,hashlib.sha256(photo.read_bytes()).hexdigest(),assoc)
    assert result['status']=='archived'
    body=json.loads(archive.db.execute('SELECT body FROM associations').fetchone()[0])
    assert body['association']['provenance']=='established_dealer'


def test_bytes_changed_since_expected_hash_are_refused(photo,tmp_path):
    archive=Archive(tmp_path/'archive',screener=screener())
    with pytest.raises(IntegrityError):
        archive.put_verified(photo,'0'*64,association())
    assert archive.report()['objects']=={}


def test_reconcile_reports_unlinked_without_deleting(tmp_path):
    backend=FilesystemBackend(tmp_path/'remote')
    backend.put('objects/ab/unlinked.jpg',b'unlinked')
    archive=Archive(tmp_path/'catalogue',backend=backend)
    assert archive.reconcile()==[{'key':'objects/ab/unlinked.jpg','observed':'unlinked'}]
    assert backend.get('objects/ab/unlinked.jpg')==b'unlinked'


def test_repeated_assessment_is_not_another_image(photo,tmp_path):
    archive=Archive(tmp_path/'archive',screener=screener())
    put(archive,photo)
    put(archive,photo)
    report=archive.report()
    assert report['assessment_events']==2
    assert report['decisions']=={'pass':1}
    assert report['unique_photographs_by_status']['pass']==1
