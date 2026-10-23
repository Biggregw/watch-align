"""Public photographic controls; optional native rerun, always verify committed evidence."""
import hashlib
import json
import os
from pathlib import Path
import zipfile
import pytest
from tools.image_archive.screening import Screener

ROOT=Path(__file__).resolve().parents[3]


def originals():
    base=ROOT/'tools/research/marker_audit'
    data={p.name:p.read_bytes() for p in (base/'fixtures').glob('*.jpg')}
    with zipfile.ZipFile(base/'review-bundle.zip') as z:
        data.update({Path(n).name:z.read(n) for n in z.namelist() if n.startswith('originals/')})
    return data


def test_reviewed_photographic_controls_have_verified_bytes_and_expected_face_on_v3_decisions():
    data=originals()
    report=json.loads((ROOT/'docs/image-archive/photographic-validation.json').read_text())
    assert len(report)==12
    for r in report:
        d=r['decision']
        assert hashlib.sha256(data[r['photo_id']]).hexdigest()==d['sha256']
        assert r['reviewed_label']['sha256']==d['sha256']
        assert d['archive_policy_version']=='face-on-v3' and d['profile'].startswith('face-on-v3/')
        assert 'inconclusive_analysis_failed' not in d['reasons']
        if d['status']=='pass':
            assert r['reviewed_label']['near_frontal']=='yes' and r['reviewed_label']['eligible']=='yes'
            assert set(d['usable_marker_ids'])==set(d['required_marker_ids'])
    angled=next(r for r in report if r['photo_id'].startswith('94ad'))
    assert angled['reviewed_label']['near_frontal']=='no'
    assert {'fail_original_resolution','fail_face_on_ellipse'} <= set(angled['decision']['reasons'])
    # 10:10 dealer photos: the minute hand lies across the 2 (the app's own hand check), so no full-watch admission
    gmt=next(r for r in report if r['photo_id'].startswith('49ba'))['decision']
    assert gmt['status']=='fail' and gmt['reasons']==['fail_marker_2_obstructed_or_unreliable']
    assert all(r['decision']['status']=='fail' for r in report)
    # per-marker masking: the same photographs, photographic suitability first, then a SHA-bound mask
    masked={r['photo_id'][:4]:r['masked_decision'] for r in report}
    assert masked['49ba']['status']=='pass' and 2 not in masked['49ba']['usable_marker_ids']
    assert masked['94ad']['status']=='fail'                    # the angled photograph: masking excuses nothing
    for r in report:
        m=r['masked_decision']
        assert m['sha256']==r['decision']['sha256'] and m['purpose']=='marker_masked'
        if m['status']=='pass':
            assert r['reviewed_label']['near_frontal']=='yes'
            assert m['marker_eligibility']['sha256']==m['sha256']
            assert m['usable_marker_ids']==m['marker_eligibility']['eligible_marker_ids']
    assert sum(m['status']=='pass' for m in masked.values())==8


@pytest.mark.skipif(os.environ.get('ARCHIVE_NATIVE_PHOTO_TESTS')!='1',reason='opt-in JDK17/OpenCV photographic analysis')
def test_rerun_real_frontal_and_angled_photos():
    from tools.image_archive.faceon import production_screener
    screen=production_screener()
    data=originals()
    for photo,family,reasons in [('49ba843c0a7e19d7.jpg','126710BLNR',{'fail_marker_2_obstructed_or_unreliable'}),
                                 ('94ad771e9e2aedde.jpg','126613LB',{'fail_original_resolution','fail_face_on_ellipse'}),
                                 ('ee07ceedbb6f0bd3.jpg','126610',{'fail_marker_6_obstructed_or_unreliable'})]:
        d=screen.assess_bytes(data[photo],family)
        assert d.status=='fail' and reasons <= set(d.reasons), (photo, d.reasons)
        assert 'inconclusive_analysis_failed' not in d.reasons
        m=screen.assess_bytes(data[photo],family,'marker_masked')
        if photo.startswith('94ad'):
            assert m.status=='fail' and 'fail_face_on_ellipse' in m.reasons
        else:   # only the obstructed marker is excluded; the photograph is kept
            hour=int(next(iter(reasons)).split('_')[2])
            assert m.status=='pass' and hour not in m.usable_marker_ids and len(m.usable_marker_ids)==len(m.required_marker_ids)-1
