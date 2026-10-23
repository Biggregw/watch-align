"""Tests of the research genuine-calibration statistics and data rules (no photographs, no Java)."""
import math
from collections import Counter
from pathlib import Path

import numpy as np
import pytest

from tools.research.genuine_calibration import analyse as A
from tools.research.genuine_calibration import configs, prepare
from tools.research.genuine_calibration import measure as M


def obs(watch, layout='submariner_124060', hour=1, q='tang', v=0.0, sha=None, source='s1', partition='development'):
    shape = A.SHAPES[layout][hour] if hour else 'ring'
    return {'layout': layout, 'sha': sha or watch + '-p', 'watch': watch, 'source': source, 'partition': partition,
            'hour': hour, 'shape': shape, 'q': q, 'v': v}


def test_one_watch_counts_once_however_many_photos_and_markers():
    # watch a: 50 readings of 1.0; watches b..e: one reading of 0.0 each. By watch, 1 in 5 is large.
    o = [obs('a', v=1.0, sha='a%d' % i) for i in range(50)] + [obs(w, v=0.0) for w in 'bcde']
    G = A.Group('round.tang', o)
    assert G.w.sum() == pytest.approx(5.0)
    assert A.wquantile(G.signed, G.w, 0.7) == 0.0          # 80 % of the watch weight is at 0
    assert A.wquantile(G.signed, G.w, 0.9) == 1.0


def test_groups_pool_equivalent_markers_across_families_and_keep_identity():
    o = [obs('a', hour=1), obs('b', layout='gmt_126710', hour=7), obs('c', layout='submariner_126610', hour=11),
         obs('d', hour=6, q='tang'), obs('e', hour=12, q='rad')]
    g = A.build_groups(o)
    assert {x['layout'] for x in g['round.tang'].obs} == {'submariner_124060', 'gmt_126710', 'submariner_126610'}
    assert [x['hour'] for x in g['baton.tang'].obs] == [6]
    assert [x['hour'] for x in g['triangle.rad'].obs] == [12]


def test_positions_join_round_and_baton_only():
    o = [dict(obs('a', hour=4, q='pos'), v=0.3), dict(obs('b', hour=9, q='pos'), v=0.2)]
    assert A.group_of(o[0]) == ['round.pos', 'marker.pos'] and A.group_of(o[1]) == ['baton.pos', 'marker.pos']


def test_ring_placement_is_the_distance_from_the_pooled_centre():
    o = [dict(obs(w, hour=0, q='place_xy'), v=(0.1 + i * 0.01, -0.2)) for i, w in enumerate('abcde')]
    G = A.Group('ring.place', o)
    assert G.centre == pytest.approx((0.12, -0.2))
    assert G.dev((0.12, 0.1)) == pytest.approx(0.3)


def test_the_common_q_meets_the_whole_watch_target_and_alarms_are_per_photo_any_marker():
    rng = np.random.default_rng(1)
    o = []
    for w in range(200):
        for p in range(2):
            for h in (1, 2, 4, 5, 7, 8, 10, 11):
                o.append(obs('w%d' % w, hour=h, v=float(rng.normal(0, 0.1)), sha='w%d-%d' % (w, p)))
    groups = A.build_groups(o)
    scheme = ('round.tang',)
    devs = A.photo_devs(o, groups, scheme)
    q = A.solve_q(groups, scheme, devs, 0.05)
    L = A.limits_at(groups, scheme, q)
    fa, per = A.alarm_rates(devs, L)
    assert fa == pytest.approx(0.05, abs=0.01)
    assert per < fa / 4                       # 8 readings per photo: the per-reading rate is far below the photo rate


def test_a_single_defective_marker_flags_the_watch_and_is_not_averaged_away():
    o = [obs('w%d' % w, hour=h, v=0.01 * ((w * h) % 7 - 3)) for w in range(40) for h in (1, 2, 4, 5)]
    groups = A.build_groups(o)
    L = A.limits_at(groups, ('round.tang',), 0.01)
    clean = {h: {'round.tang': 0.0} for h in (1, 2, 4, 5, 7, 8, 10, 11)}
    assert not any(A.flagged(v, groups, L, ('round.tang',)) for v in clean.values())
    clean[7]['round.tang'] = 0.5                 # one marker 0.5 % R off; seven perfect markers do not dilute it
    assert [h for h, v in clean.items() if A.flagged(v, groups, L, ('round.tang',))] == [7]


def test_variants_are_parsed():
    assert A.parse_variant('mv:4:t:-0.0050000') == ('single', [(4, 't', -0.005)])
    assert A.parse_variant('mv:6:rot:2.0') == ('single', [(6, 'rot', 2.0)])
    assert A.parse_variant('mm:2:t:0.005|7:r:-0.005') == ('multi', [(2, 't', 0.005), (7, 'r', -0.005)])
    k, m = A.parse_variant('ring:t:0.003:0.004')
    assert k == 'ring' and m[0][1] == 't' and m[0][2] == pytest.approx(0.005)
    assert A.parse_variant('base') == ('base', [])


def test_production_strict_genuine_rule_uses_the_frozen_limits_and_the_resolution_allowance():
    rec = {'rpx': 500.0, 'markers': [
        {'h': 1, 'shape': 'round', 'pos': 0.70, 'state': 'NO_VISIBLE_ISSUE'},      # 0.671 + 0.1 allowance: pass
        {'h': 2, 'shape': 'round', 'pos': 0.80, 'state': 'NO_VISIBLE_ISSUE'},      # over
        {'h': 6, 'shape': 'baton', 'pos': 0.1, 'tilt': 1.2, 'state': 'NO_VISIBLE_ISSUE'},
        {'h': 12, 'shape': 'triangle', 'side': 0.0, 'inout': 0.45, 'tilt': 0.0, 'top': True, 'state': 'CLEARLY_OFF'}]}
    f = A.prod_flags(rec, {1, 2, 6, 12})
    assert f == {1: (False, False), 2: (True, False), 6: (True, False), 12: (False, True)}   # 0.45 <= 0.40 + 0.1
    assert A.prod_flags(rec, {1}) == {1: (False, False)}      # ineligible markers are not judged


def test_research_configs_declare_the_mask_policy_and_every_dependency():
    import marker_mask
    for layout in configs.MODEL_KEY:
        c = configs.config(layout)
        assert marker_mask.required(c) and c['measurement_adapter'] == 'marker_geometry_v1'
        assert marker_mask.layout(c) == layout
        for s in c['calibration_metrics']:
            if not s['metric'].startswith('ring.'):
                marker_mask.dependencies(s['metric'], layout)
    assert not any(s['metric'].startswith('marker.3.') for s in configs.config('gmt_126710')['calibration_metrics'])


def test_measure_refuses_to_run_without_restored_masks(tmp_path):
    (tmp_path / 'acq').mkdir()
    with pytest.raises(SystemExit, match='masked'):
        M.measure(tmp_path, 'submariner_124060')


def test_association_never_guesses_identity_or_class():
    cat = {'a' * 64: {'ids': {'w1', 'w2'}, 'cls': {'gen'}, 'urls': ['https://x.test/1'], 'prov': Counter(), 'factory': Counter()},
           'b' * 64: {'ids': {'w3'}, 'cls': {'gen', 'rep'}, 'urls': [], 'prov': Counter(), 'factory': Counter()},
           'c' * 64: {'ids': {'w4'}, 'cls': {'rep'}, 'urls': ['https://www.reddit.com/r/x'], 'prov': Counter({'rep_labelled': 1}),
                      'factory': Counter({'VSF': 2})}}
    item = lambda s: {'sha256': s * 64, 'family': '124060', 'physical_watch_id': ''}
    assert prepare.association(item('a'), cat) == (None, 'identity_conflict_or_unknown_watch')
    assert prepare.association(item('b'), cat) == (None, 'class_unknown_or_conflicting')
    a, _ = prepare.association(item('c'), cat)
    assert a['class_label'] == 'rep' and a['factory'] == 'VSF' and a['source_name'] == 'reddit.com'
