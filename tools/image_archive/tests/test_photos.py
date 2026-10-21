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


def test_reviewed_photographic_controls_have_verified_bytes_and_expected_face_on_v2_decisions():
    data=originals()
    report=json.loads((ROOT/'docs/image-archive/photographic-validation.json').read_text())
    assert len(report)==12
    for r in report:
        d=r['decision']
        assert hashlib.sha256(data[r['photo_id']]).hexdigest()==d['sha256']
        assert r['reviewed_label']['sha256']==d['sha256']
        assert d['archive_policy_version']=='face-on-v2' and d['profile'].startswith('face-on-v2/')
        assert 'inconclusive_analysis_failed' not in d['reasons']
        if d['status']=='pass':
            assert r['reviewed_label']['near_frontal']=='yes' and r['reviewed_label']['eligible']=='yes'
            assert set(d['usable_marker_ids'])==set(d['required_marker_ids'])
    angled=next(r for r in report if r['photo_id'].startswith('94ad'))
    assert angled['reviewed_label']['near_frontal']=='no'
    assert angled['decision']['status']=='fail'
    assert {'fail_original_resolution','fail_face_on_ellipse'} <= set(angled['decision']['reasons'])
    status={r['photo_id'][:8]:r['decision']['status'] for r in report}
    # a hand over most of a marker's outline (2, 12, 2) withholds the full-watch admission
    assert [k for k,v in sorted(status.items()) if v=='fail']==['02d7c97e','22060f9a','800cd260','94ad771e']
    assert sum(v=='pass' for v in status.values())==8


@pytest.mark.skipif(os.environ.get('ARCHIVE_NATIVE_PHOTO_TESTS')!='1',reason='opt-in JDK17/OpenCV photographic analysis')
def test_rerun_real_frontal_and_angled_photos():
    from tools.image_archive.faceon import production_screener
    screen=production_screener()
    data=originals()
    for photo,family,expected in [('49ba843c0a7e19d7.jpg','126710BLNR','pass'),('94ad771e9e2aedde.jpg','126613LB','fail'),
                                  ('2802b4a30cfb277b.jpg','126613LB','pass'),('800cd2600f322aa5.jpg','126613LN','fail')]:
        d=screen.assess_bytes(data[photo],family)
        assert d.status==expected, (photo, d.reasons)
        assert 'inconclusive_analysis_failed' not in d.reasons
