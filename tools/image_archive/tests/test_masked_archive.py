"""The archive stores originals once with versioned per-marker eligibility masks; restores hand the right mask on."""
import hashlib
import json

import pytest
from PIL import Image

from tools.image_archive import Archive, Screener, eligibility as el
from tools.image_archive.archive import IntegrityError
from tools.image_archive.faceon import REQUIRED
from tools.image_archive.integration import MASKS_FILE, restore_acquired
from tools.image_archive.screening import REQUIRED_CHECKS

MODEL = 'submariner_126610'


class Analyzer:
    """Synthetic evidence; `hands` can change between calls to model a changed reassessment."""
    def __init__(self, hands=()):
        self.hands = list(hands)

    def __call__(self, img, family, purpose):
        req = list(REQUIRED[MODEL])
        s = {'required': req, 'present': req, 'credible': req, 'reliable': req, 'cover': {h: 1.0 for h in req},
             'withheld': {}, 'hand_confirmed': self.hands, 'soft': {}}
        st = el.marker_statuses(s, MODEL)
        return {'profile': 'test-only', 'checks': dict.fromkeys(REQUIRED_CHECKS, True), 'required_marker_ids': req,
                'markers': {h: v['status'] == el.ELIGIBLE for h, v in st.items() if v['status'] != el.NOT_APPLICABLE},
                'marker_status': st}


@pytest.fixture
def photo(tmp_path):
    p = tmp_path / 'original.png'
    Image.new('RGB', (1000, 1000), 'gray').save(p)
    return p


def put(archive, photo, **kw):
    assoc = dict({'family': '126610LN', 'model': '126610LN', 'physical_watch_id': 'w1', 'class_label': 'gen',
                  'source_url': 'https://example.test/1', 'provenance': 'established_dealer'}, **kw)
    return archive.put_verified(photo, hashlib.sha256(photo.read_bytes()).hexdigest(), assoc)


def body(archive):
    return json.loads(archive.db.execute('SELECT body FROM associations').fetchone()[0])


def test_partly_obstructed_photo_is_archived_once_with_its_mask(photo, tmp_path):
    archive = Archive(tmp_path / 'a', screener=Screener(Analyzer([2]), ['test-only']))
    assert put(archive, photo)['status'] == 'archived'
    assert put(archive, photo)['status'] == 'archived'                                 # SHA dedup: one object
    assert len(list((archive.root / 'storage/objects').rglob('*.png'))) == 1
    b = body(archive)
    m = el.validate(b['marker_eligibility'], b['sha256'])
    assert b['purpose'] == 'marker_masked' and 2 not in m['eligible_marker_ids'] and not m['full_watch']
    assert archive.report()['archived_by_eligibility'] == {'marker_masked': 1}
    # the original bytes are stored unaltered
    assert next((archive.root / 'storage/objects').rglob('*.png')).read_bytes() == photo.read_bytes()
    # the strict standard is still available and still refuses it
    strict = Archive(tmp_path / 's', screener=Screener(Analyzer([2]), ['test-only']), purpose='full_watch')
    assert put(strict, photo)['status'] == 'fail'


def test_masked_archive_sync_reassesses_the_mask(photo, tmp_path):
    analyzer = Analyzer([2])
    archive = Archive(tmp_path / 'a', screener=Screener(analyzer, ['test-only']))
    digest = hashlib.sha256(photo.read_bytes()).hexdigest()
    archive.backend.put = lambda key, data: (_ for _ in ()).throw(OSError())           # first upload fails
    assert put(archive, photo)['status'] == 'pending'
    del archive.backend.put
    analyzer.hands = [4]            # the bytes now produce a different mask: the stored one is not trusted
    assert archive.sync() == [{'sha256': digest, 'status': 'withheld_reassessment'}]
    analyzer.hands = [2]
    assert archive.sync()[0]['status'] == 'archived'


def test_snapshot_with_a_tampered_mask_is_refused(photo, tmp_path):
    archive = Archive(tmp_path / 'a', screener=Screener(Analyzer([2]), ['test-only']))
    put(archive, photo)
    snap = next((archive.root / 'storage/manifests').rglob('*.json'))
    doc = json.loads(snap.read_text())
    doc['associations'][0]['marker_eligibility']['markers']['2']['status'] = el.ELIGIBLE
    doc['associations'][0]['marker_eligibility']['eligible_marker_ids'] = list(REQUIRED[MODEL])
    forged = archive.root / 'storage/manifests/runs/forged.json'
    forged.write_text(json.dumps(doc))
    fresh = Archive(tmp_path / 'f', backend=archive.backend, screener=Screener(Analyzer([2]), ['test-only']))
    with pytest.raises(IntegrityError):
        fresh.import_snapshot('manifests/runs/forged.json')
    fresh.import_snapshot(str(snap.relative_to(archive.root / 'storage')))           # the genuine one is fine


def manifest(tmp_path, digest, wid='w1'):
    p = tmp_path / 'acquired_images.csv'
    p.write_text('sha256,physical_watch_id,model,local_path\n%s,%s,126610LN,images/c/0.png\n' % (digest, wid))
    return p


def test_restore_hands_the_mask_to_the_calibrator_and_legacy_restore_refuses_partial(photo, tmp_path):
    archive = Archive(tmp_path / 'a', screener=Screener(Analyzer([2]), ['test-only']))
    digest = put(archive, photo)['sha256']
    acq = tmp_path / 'acq'
    with pytest.raises(IntegrityError):
        restore_acquired(archive, manifest(tmp_path, digest), acq)                      # legacy: full watch only
    assert not acq.exists()
    out = restore_acquired(archive, manifest(tmp_path, digest), acq, purpose='marker_masked')
    assert out['masks'] == 1 and (acq / 'images/c/0.png').read_bytes() == photo.read_bytes()
    masks = [json.loads(line) for line in (acq / MASKS_FILE).read_text().splitlines()]
    assert len(masks) == 1 and masks[0]['sha256'] == digest and el.validate(masks[0], digest)
    # a second restore of the same photograph is idempotent; a different mask for it is refused
    assert restore_acquired(archive, manifest(tmp_path, digest), acq, purpose='marker_masked')['masks'] == 1
    other = dict(masks[0], mask_digest='0' * 64)
    (acq / MASKS_FILE).write_text(json.dumps(other) + '\n')
    with pytest.raises(IntegrityError):
        restore_acquired(archive, manifest(tmp_path, digest), acq, purpose='marker_masked')


def test_restore_refuses_identity_conflicts_and_unknown_purposes(photo, tmp_path):
    archive = Archive(tmp_path / 'a', screener=Screener(Analyzer([2]), ['test-only']))
    digest = put(archive, photo)['sha256']
    put(archive, photo, physical_watch_id='w2')                                         # one photo, two watches
    with pytest.raises(IntegrityError):
        restore_acquired(archive, manifest(tmp_path, digest), tmp_path / 'acq', purpose='marker_masked')
    with pytest.raises(ValueError):
        restore_acquired(archive, manifest(tmp_path, digest), tmp_path / 'acq', purpose='anything')


def test_a_fully_eligible_masked_admission_still_serves_the_legacy_restore(photo, tmp_path):
    archive = Archive(tmp_path / 'a', screener=Screener(Analyzer([]), ['test-only']))
    digest = put(archive, photo)['sha256']
    assert restore_acquired(archive, manifest(tmp_path, digest), tmp_path / 'acq')['restored'] == 1
    assert not (tmp_path / 'acq' / MASKS_FILE).exists()
