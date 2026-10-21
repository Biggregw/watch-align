"""face-on-v3 profile: homography geometry and the validated-envelope decision (no native code)."""
import math

import pytest

from tools.image_archive import faceon
from tools.image_archive.faceon import ENVELOPE, REQUIRED, dial_margin, evaluate, homography_pose
from tools.image_archive.screening import REQUIRED_CHECKS, Screener


def H(r=430.0, cx=700.0, cy=1000.0, tilt_deg=0.0, rot_deg=0.0, persp=0.0):
    """canonical (dial radius 1) -> image: in-plane rotation, foreshortening of one axis, optional perspective."""
    c, s = math.cos(math.radians(rot_deg)), math.sin(math.radians(rot_deg))
    f = math.cos(math.radians(tilt_deg))
    a, b, d, e = r * c, -r * f * s, r * s, r * f * c
    return [a, b, cx, d, e, cy, 0.0, persp, 1.0]


def test_a_face_on_dial_has_unit_ratio_whatever_its_in_plane_rotation():
    for rot in (0, 37, 90, 211):
        p = homography_pose(H(rot_deg=rot))
        assert p['axis_ratio'] == pytest.approx(1, abs=1e-9)
        assert p['tilt_deg'] == pytest.approx(0, abs=1e-4)
        assert p['v_gradient'] == pytest.approx(1) and p['h_gradient'] == pytest.approx(1)


def test_a_tilted_dial_reads_its_tilt_and_a_perspective_gradient():
    for t in (5, 10.6, 13.3, 20):
        assert homography_pose(H(tilt_deg=t, rot_deg=25))['tilt_deg'] == pytest.approx(t, abs=1e-6)
    g = homography_pose(H(persp=0.02))
    assert g['v_gradient'] != pytest.approx(1, abs=1e-3)   # the nearer side is larger


def test_dial_margin_is_negative_when_the_frame_cuts_the_dial():
    assert dial_margin(H(), 1400, 2000) == pytest.approx(min(700, 1000) / 430.0 - 1)
    assert dial_margin(H(cy=300), 1400, 2000) < 0


def summary(**kw):
    s = dict(pose_valid=True, projective_accepted=True, tilt_deg=4.0, edge_ellipse_ratio=0.995, v_gradient=0.995,
             h_gradient=1.0, marker_branch_turn=0, hour_lines=12, dial_margin_r=0.48, rpx=430.0, sharpness=3000.0,
             crushed_fraction=0.0, blown_fraction=0.004, glare_fraction=0.02, required=list(REQUIRED['submariner_126610']),
             credible=list(REQUIRED['submariner_126610']), reliable=list(REQUIRED['submariner_126610']), withheld={},
             partly_hidden=[], hand_confirmed=[], soft={})
    s.update(kw)
    return s


def status(s):
    checks, markers = evaluate(s)
    vals = [checks.get(k) for k in REQUIRED_CHECKS] + list(markers.values())
    return 'fail' if False in vals else 'pass' if all(v is True for v in vals) else 'inconclusive'


def test_inside_the_validated_envelope_every_check_passes():
    checks, markers = evaluate(summary())
    assert all(checks[k] is True for k in REQUIRED_CHECKS)
    assert set(markers) == {str(h) for h in REQUIRED['submariner_126610']} and all(markers.values())


@pytest.mark.parametrize('tilt,expected', [(ENVELOPE['tilt_deg_pass'], 'pass'), (11.5, 'inconclusive'),
                                           (ENVELOPE['tilt_deg_fail'], 'fail'), (17.0, 'fail')])
def test_tilt_passes_only_where_it_was_validated_and_fails_where_it_harmed_measurements(tilt, expected):
    assert status(summary(tilt_deg=tilt)) == expected


def test_an_untested_dial_edge_ellipse_or_gradient_is_withheld_not_admitted():
    assert status(summary(edge_ellipse_ratio=0.97)) == 'inconclusive'
    assert status(summary(v_gradient=0.96)) == 'inconclusive'
    assert status(summary(h_gradient=1.03)) == 'inconclusive'


def test_hidden_withheld_or_missing_markers():
    req = REQUIRED['submariner_126610']
    assert status(summary(partly_hidden=[4])) == 'fail'                  # under half its outline seen
    assert status(summary(credible=[h for h in req if h != 2], withheld={2: 'a hand covers most of its outline'})) == 'fail'
    assert status(summary(credible=[h for h in req if h != 2])) == 'inconclusive'   # not reported at all
    glare = summary(credible=[h for h in req if h != 10], withheld={10: 'glare on its outline'})
    assert evaluate(glare)[0]['reflections'] is False


def test_the_apps_hand_check_and_unreliable_edges_withhold_a_marker_but_a_reflection_does_not():
    req = REQUIRED['submariner_126610']
    rest = [h for h in req if h != 4]
    assert status(summary(hand_confirmed=[4])) == 'fail'                 # MetalFit.handConfirmed at the marker
    refl = summary(reliable=rest, soft={4: '18% of its visible edge is inconsistent (shadowed or reflecting metal)'})
    assert status(refl) == 'pass'                                        # harmless: reflection only
    rough = summary(reliable=rest, soft={4: 'its visible edge is rough (1.6 px)'})
    assert status(rough) == 'fail'
    both = summary(reliable=rest, soft={4: 'its visible edge is rough (2.2 px); 73% of its visible edge is inconsistent (shadowed or reflecting metal)'})
    assert status(both) == 'fail'
    assert status(summary(reliable=rest, soft={4: 'something the archive does not know'})) == 'fail'
    assert status(summary(reliable=rest, soft={})) == 'fail'             # unreliable without a stated reason


def test_soft_warning_classes():
    from tools.image_archive.faceon import soft_classes
    assert soft_classes('18% of its visible edge is inconsistent (shadowed or reflecting metal)') == {'inconsistent'}
    assert soft_classes('its visible edge is rough (1.5 px); 67% of its visible edge is inconsistent (x)') == {'rough', 'inconsistent'}
    assert soft_classes('only 46% of its outline is visible, the rest is reconstructed') == {'hidden'}
    assert soft_classes("the photo's resolution is low (the dial is 255 px across)") == {'low_resolution'}
    assert soft_classes('new wording') == {'other'}


def test_photo_quality_bounds():
    assert status(summary(rpx=199.0)) == 'fail'                          # below the app's frozen 200 px floor
    assert status(summary(rpx=249.0)) == 'fail'                          # archive-only 250 px floor
    assert status(summary(rpx=250.0)) == 'pass'
    assert status(summary(dial_margin_r=-0.01)) == 'fail'
    assert status(summary(hour_lines=11)) == 'fail'
    assert status(summary(sharpness=20.0)) == 'fail'                     # existing harvester blur bound
    assert status(summary(sharpness=800.0)) == 'inconclusive'            # sharper than that, but untested
    assert status(summary(glare_fraction=0.35)) == 'fail'
    assert status(summary(glare_fraction=0.2)) == 'inconclusive'
    assert status(summary(blown_fraction=0.3)) == 'fail'
    assert status(summary(marker_branch_turn=2)) == 'fail'               # hour markers disagree with the pose


def test_no_registered_dial_is_never_admitted():
    assert status(summary(pose_valid=False)) == 'inconclusive'
    assert status(summary(tilt_deg=None)) == 'inconclusive'
    assert status(summary(tilt_deg=float('nan'))) == 'inconclusive'


def test_the_production_registry_is_the_validated_profile_and_admits_through_the_screener(tmp_path):
    from PIL import Image
    assert faceon.VALIDATED_PROFILES == {'face-on-v3/gmt_126710', 'face-on-v3/submariner_124060', 'face-on-v3/submariner_126610'}
    assert faceon.APP_MIN_DIAL_RADIUS_PX == 200 and faceon.MIN_DIAL_RADIUS_PX == 250

    class Stub(faceon.FaceOnAnalyzer):
        def __init__(self, s):
            self.s = s

        def __call__(self, img, family, purpose):
            checks, markers = evaluate(self.s)
            return {'profile': faceon.PROFILE + '/' + faceon.MODELS[family], 'checks': checks,
                    'required_marker_ids': list(REQUIRED[faceon.MODELS[family]]), 'markers': markers}
    p = tmp_path / 'p.png'
    Image.new('RGB', (1000, 1000), 'gray').save(p)
    ok = Screener(Stub(summary()), faceon.VALIDATED_PROFILES).assess_candidate(p, '126610LN')
    assert ok.status == 'pass' and set(ok.usable_marker_ids) == set(REQUIRED['submariner_126610'])
    # the same evidence under a profile that is not registered is withheld
    assert Screener(Stub(summary()), ()).assess_candidate(p, '126610LN').status == 'inconclusive'
    assert Screener(Stub(summary(tilt_deg=15)), faceon.VALIDATED_PROFILES).assess_candidate(p, '126610LN').status == 'fail'
