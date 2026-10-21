"""Public photographic controls; optional native rerun, always verify committed evidence."""
import hashlib
import json
import os
from pathlib import Path
import zipfile
import pytest
from tools.image_archive.screening import Screener
from tools.image_archive.diagnostics import FamilyDiagnostics

ROOT=Path(__file__).resolve().parents[3]


def originals():
    base=ROOT/'tools/research/marker_audit'
    data={p.name:p.read_bytes() for p in (base/'fixtures').glob('*.jpg')}
    with zipfile.ZipFile(base/'review-bundle.zip') as z:
        data.update({Path(n).name:z.read(n) for n in z.namelist() if n.startswith('originals/')})
    return data


def test_reviewed_photographic_controls_have_verified_bytes_and_no_false_accepts():
    data=originals()
    report=json.loads((ROOT/'docs/image-archive/photographic-validation.json').read_text())
    assert len(report)==12
    for r in report:
        d=r['decision']
        assert hashlib.sha256(data[r['photo_id']]).hexdigest()==d['sha256']
        assert r['reviewed_label']['sha256']==d['sha256']
        assert d['status']!='pass'
        assert 'inconclusive_analysis_failed' not in d['reasons']
    angled=next(r for r in report if r['photo_id'].startswith('94ad'))
    assert angled['reviewed_label']['near_frontal']=='no'
    assert angled['decision']['status']=='fail'
    assert 'fail_original_resolution' in angled['decision']['reasons']
    frontal=[r for r in report if r['reviewed_label']['near_frontal']=='yes']
    assert len(frontal)==11
    assert any(any('obstructed_or_unreliable' in reason for reason in r['decision']['reasons']) for r in frontal)


@pytest.mark.skipif(os.environ.get('ARCHIVE_NATIVE_PHOTO_TESTS')!='1',reason='opt-in JDK17/OpenCV photographic analysis')
def test_rerun_real_frontal_and_angled_photos():
    screen=Screener(FamilyDiagnostics())
    data=originals()
    for photo,family in [('49ba843c0a7e19d7.jpg','126710BLNR'),('94ad771e9e2aedde.jpg','126613LB')]:
        d=screen.assess_bytes(data[photo],family)
        assert d.status=='fail'
        assert 'inconclusive_analysis_failed' not in d.reasons
