"""Per-marker eligibility: statuses, SHA-bound masks, tampering, and the properties on real committed evidence."""
import copy
import hashlib
import json
from pathlib import Path

import pytest
from PIL import Image

from tools.image_archive import eligibility as el
from tools.image_archive import analyse_validation as av
from tools.image_archive.faceon import REQUIRED, evaluate
from tools.image_archive.screening import REQUIRED_CHECKS, Screener

ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / 'docs/image-archive/face-on-evidence.jsonl'
SHA = 'ab' * 32


def summary(model='submariner_126610', **marker):
    """One clean summarise()-style record; `marker` overrides per-marker evidence lists."""
    req = list(REQUIRED[model])
    s = {'model': model, 'required': req, 'present': req, 'credible': req, 'reliable': req,
         'cover': {h: 1.0 for h in req}, 'withheld': {}, 'hand_confirmed': [], 'partly_hidden': [], 'soft': {}}
    s.update(marker)
    return s


def status(s, hour):
    return el.marker_statuses(s, s['model'])[str(hour)]


# --- the face-on-v3 marker protections, named -------------------------------------------------------------------

def test_hand_and_low_coverage_obstruct_80_90_is_inconclusive_90_needed_but_not_sufficient():
    assert status(summary(hand_confirmed=[2]), 2)['status'] == el.OBSTRUCTED
    assert status(summary(cover={**summary()['cover'], 2: 0.79}), 2)['status'] == el.OBSTRUCTED
    assert status(summary(cover={**summary()['cover'], 2: 0.85}), 2)['status'] == el.INCONCLUSIVE
    # >= 90 % coverage but a rough edge: unreliable, not eligible
    rough = summary(reliable=[h for h in REQUIRED['submariner_126610'] if h != 4], cover={**summary()['cover'], 4: 0.95},
                    soft={4: 'its visible edge is rough'})
    assert status(rough, 4)['status'] == el.UNRELIABLE


@pytest.mark.parametrize('text,expected', [
    ('35% of its outline is visible, the rest is reconstructed', el.UNRELIABLE),
    ('its visible edge is rough', el.UNRELIABLE),
    ('resolution is low here', el.UNRELIABLE),
    ('a warning the archive has never seen', el.UNRELIABLE),
    ('12% of its visible edge is inconsistent', el.ELIGIBLE),           # property 7: a harmless reflection
])
def test_edge_warnings(text, expected):
    req = REQUIRED['submariner_126610']
    s = summary(reliable=[h for h in req if h != 5], soft={5: text})
    assert status(s, 5)['status'] == expected


def test_withheld_or_unmeasured_markers_are_never_eligible_and_a_number_alone_is_not_enough():
    req = REQUIRED['submariner_126610']
    assert status(summary(credible=[h for h in req if h != 2], withheld={2: 'a hand covers most of its outline'}), 2)['status'] == el.OBSTRUCTED
    assert status(summary(credible=[h for h in req if h != 7], withheld={7: "Can't check: glare"}), 7)['status'] == el.UNRELIABLE
    assert status(summary(credible=[h for h in req if h != 8], present=[h for h in req if h != 8]), 8)['status'] == el.INCONCLUSIVE
    # credible (a number was reported) but not reliable and no recognised warning: not eligible
    assert status(summary(reliable=[h for h in req if h != 10]), 10)['status'] == el.UNRELIABLE


def test_round_markers_need_no_tilt_and_the_date_window_is_not_a_marker():
    st = el.marker_statuses(summary(), 'submariner_126610')
    assert st['3']['status'] == el.NOT_APPLICABLE and st['3']['evidence'] is None
    assert st['1']['shape'] == 'round' and st['1']['status'] == el.ELIGIBLE      # property 6: no tilt evidence used
    assert st['12']['shape'] == 'triangle' and st['6']['shape'] == 'baton'
    nd = el.marker_statuses(summary('submariner_124060'), 'submariner_124060')
    assert nd['3']['status'] == el.ELIGIBLE and nd['3']['shape'] == 'baton'


def test_statuses_reproduce_face_on_v3_exactly_on_every_committed_photograph():
    n = 0
    for e in av.unique([json.loads(line) for line in EVIDENCE.open()]):
        if e.get('error') or e.get('model') not in REQUIRED:
            continue
        av.prepare(e)
        _, markers = evaluate(e)
        for h, rec in el.marker_statuses(e, e['model']).items():
            if rec['status'] == el.NOT_APPLICABLE:
                assert h not in markers
                continue
            assert markers[h] == {el.ELIGIBLE: True, el.INCONCLUSIVE: None}.get(rec['status'], False)
            n += 1
    assert n > 7000


# --- masks: binding, completeness and tampering ---------------------------------------------------------------

def mask(**kw):
    return el.build(SHA, '126610LN', el.marker_statuses(summary(**kw), 'submariner_126610'), 'face-on-v3', 'p')


def redigest(m):
    m['mask_digest'] = hashlib.sha256(el._canonical(m)).hexdigest()
    return m


def test_a_valid_mask_is_bound_and_versioned():
    m = el.validate(mask(hand_confirmed=[2]), SHA)
    assert m['eligible_marker_ids'] == [1, 4, 5, 6, 7, 8, 9, 10, 11, 12] and not m['full_watch']
    assert m['photo_policy_version'] == 'face-on-v3' and m['mask_policy_version'] == el.MASK_POLICY_VERSION
    with pytest.raises(el.MaskError):
        el.validate(m, 'cd' * 32)                                         # another photograph's mask


@pytest.mark.parametrize('edit', [
    lambda m: m['markers']['2'].update(status=el.ELIGIBLE),                # flip a status
    lambda m: m['eligible_marker_ids'].append(2),                          # widen the eligible list
    lambda m: m.update(full_watch=True),
    lambda m: m.update(mask_policy_version='marker-mask-v0'),
    lambda m: m.update(photo_policy_version='face-on-v2'),
    lambda m: m.update(sha256='not-a-sha'),
    lambda m: m['markers'].pop('7'),                                       # incomplete
    lambda m: m['markers']['3'].update(status=el.ELIGIBLE),                # date window as a marker
    lambda m: m['markers']['5'].update(status=el.NOT_APPLICABLE),          # a real marker hidden as not applicable
    lambda m: m['markers']['2']['evidence'].update(hand_confirmed=False),  # evidence edited, status left
    lambda m: m.update(model='submariner_124060'),
    lambda m: m['markers']['1']['evidence'].update(cover=float('nan')),
])
@pytest.mark.parametrize('recompute_digest', [False, True])
def test_tampered_masks_are_refused_even_with_a_recomputed_digest(edit, recompute_digest):
    m = mask(hand_confirmed=[2])
    edit(m)
    if recompute_digest:
        try:
            redigest(m)
        except ValueError:          # NaN cannot even be serialised
            pass
    with pytest.raises(el.MaskError):
        el.validate(m, SHA)


def test_rewriting_evidence_and_status_together_is_caught_by_the_digest_and_reassessment():
    # The only consistent forgery rewrites the evidence too. The digest then differs from the archived one, and the
    # archive reassesses the bytes on every sync (test_masked_archive_sync_reassesses_the_mask).
    m = mask(hand_confirmed=[2])
    forged = copy.deepcopy(m)
    forged['markers']['2'] = mask()['markers']['2']
    forged['eligible_marker_ids'] = list(REQUIRED['submariner_126610'])
    forged['full_watch'] = True
    with pytest.raises(el.MaskError):
        el.validate(forged, SHA)
    assert redigest(forged)['mask_digest'] != m['mask_digest']


def test_empty_or_too_small_masks_never_enter_calibration():
    with pytest.raises(el.MaskError):
        el.validate({}, SHA)
    with pytest.raises(el.MaskError):
        el.validate(None, SHA)
    three = mask(hand_confirmed=[2, 4, 5])
    with pytest.raises(el.MaskError):
        el.validate(three, SHA)                                           # 3 excluded > MAX_EXCLUDED_MARKERS
    assert el.validate(mask(hand_confirmed=[2, 4]), SHA)
    assert el.min_eligible('submariner_126610') == 9 and el.min_eligible('submariner_124060') == 10


# --- the screener: photographic suitability first, then the mask -----------------------------------------------

@pytest.fixture
def photo(tmp_path):
    p = tmp_path / 'o.png'
    Image.new('RGB', (1000, 1000), 'gray').save(p)
    return p


def analyzer(checks=None, **marker):
    def analyze(img, family, purpose):
        s = summary(**marker)
        st = el.marker_statuses(s, 'submariner_126610')
        return {'profile': 'test-only', 'checks': dict(dict.fromkeys(REQUIRED_CHECKS, True), **(checks or {})),
                'required_marker_ids': list(REQUIRED['submariner_126610']),
                'markers': {h: {el.ELIGIBLE: True, el.INCONCLUSIVE: None}.get(v['status'], False)
                            for h, v in st.items() if v['status'] != el.NOT_APPLICABLE},
                'marker_status': st}
    return Screener(analyze, ['test-only'])


def test_a_date_photo_with_the_2_obstructed_is_admitted_with_the_2_excluded(photo):
    screen = analyzer(hand_confirmed=[2])
    full = screen.assess_candidate(photo, '126610LN', 'full_watch')
    assert full.status == 'fail' and full.reasons == ('fail_marker_2_obstructed_or_unreliable',)
    d = screen.assess_candidate(photo, '126610LN', 'marker_masked')
    assert d.status == 'pass' and 2 not in d.usable_marker_ids and len(d.usable_marker_ids) == 10
    assert el.validate(d.marker_eligibility, d.sha256)['markers']['2']['reasons'] == ['hand_confirmed_by_app']


@pytest.mark.parametrize('check', REQUIRED_CHECKS)
@pytest.mark.parametrize('value,expected', [(False, 'fail'), (None, 'inconclusive')])
def test_masking_never_excuses_a_photo_level_failure(photo, check, value, expected):
    d = analyzer(checks={check: value}).assess_candidate(photo, '126610LN', 'marker_masked')
    assert d.status == expected


def test_too_many_excluded_markers_fail_or_stay_inconclusive(photo):
    assert analyzer(hand_confirmed=[2, 4, 5]).assess_candidate(photo, '126610LN', 'marker_masked').reasons == \
        ('fail_too_many_excluded_markers',)
    cover = {**summary()['cover'], 8: 0.85}
    d = analyzer(hand_confirmed=[2, 4], cover=cover).assess_candidate(photo, '126610LN', 'marker_masked')
    assert d.status == 'inconclusive' and 'inconclusive_too_many_excluded_markers' in d.reasons


def test_missing_or_forged_marker_status_is_never_admitted(photo):
    def no_status(img, family, purpose):
        return {'profile': 'test-only', 'checks': dict.fromkeys(REQUIRED_CHECKS, True),
                'required_marker_ids': list(REQUIRED['submariner_126610']),
                'markers': {str(h): True for h in REQUIRED['submariner_126610']}}
    d = Screener(no_status, ['test-only']).assess_candidate(photo, '126610LN', 'marker_masked')
    assert d.status == 'inconclusive' and 'inconclusive_marker_status_missing' in d.reasons

    def forged(img, family, purpose):
        doc = analyzer(hand_confirmed=[2]).analyzer(img, family, purpose)
        doc['marker_status']['2']['status'] = el.ELIGIBLE                 # status contradicts its own evidence
        return doc
    d = Screener(forged, ['test-only']).assess_candidate(photo, '126610LN', 'marker_masked')
    assert d.status == 'inconclusive' and 'inconclusive_marker_mask_invalid' in d.reasons and not d.usable_marker_ids


# --- properties on real, exact-hash-verified photographs (committed evidence and public originals) ---------------

def records():
    return [e for e in av.unique([json.loads(line) for line in EVIDENCE.open()]) if not e.get('error')]


def test_property_1_and_10_the_strict_13_are_unchanged_and_fully_eligible():
    full = []
    for e in records():
        status, reasons = av.decide(e)
        if status == 'pass':
            m = el.validate(el.build(e['sha256'], e['family'], el.marker_statuses(e, e['model']), 'face-on-v3'), e['sha256'])
            assert m['full_watch'] and m['eligible_marker_ids'] == list(REQUIRED[e['model']])
            full.append(e['model'])
    assert len(full) == 13 and full.count('submariner_124060') == 6 and full.count('submariner_126610') == 7


def test_property_2_3_and_5_real_photographs_with_the_2_obstructed():
    """Public originals re-measured natively (photographic-validation.json): the 10:10 minute hand covers the 2."""
    report = {r['photo_id'][:4]: r for r in json.loads((ROOT / 'docs/image-archive/photographic-validation.json').read_text())}
    gmt = report['49ba']
    assert gmt['decision']['reasons'] == ['fail_marker_2_obstructed_or_unreliable']          # full-watch unchanged
    m = gmt['masked_decision']
    assert m['status'] == 'pass' and m['reasons'] == []
    mask = el.validate(m['marker_eligibility'], m['sha256'])
    assert mask['markers']['2']['status'] == el.OBSTRUCTED and 2 not in mask['eligible_marker_ids']
    assert mask['eligible_marker_ids'] == [1, 4, 5, 6, 7, 8, 9, 10, 11, 12]
    # inconclusive markers stay out of the mask, and with too many exclusions the photograph is withheld
    six = report['6f07']['masked_decision']
    assert six['status'] == 'inconclusive' and 10 not in six['usable_marker_ids']
    assert six['marker_eligibility']['markers']['10']['status'] == el.INCONCLUSIVE
    # the reviewed angled photograph is still rejected at photo level
    assert report['94ad']['masked_decision']['status'] == 'fail'
    assert 'fail_face_on_ellipse' in report['94ad']['masked_decision']['reasons']
