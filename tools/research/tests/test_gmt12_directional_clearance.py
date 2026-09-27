from gmt12_directional_clearance import (
    DirectionalClearanceStatus,
    assess_directional_clearance,
)


def test_low_remains_confirmed_when_perspective_can_only_make_true_gap_smaller():
    # observed=true*scale; scale > 1 means the image inflates the gap.
    r = assess_directional_clearance(
        observed=0.110,
        scale_low=1.05,
        scale_high=1.15,
        low_boundary=0.129,
    )
    assert r.status is DirectionalClearanceStatus.LOW_CONFIRMED
    assert r.true_high < 0.129


def test_small_gap_is_ambiguous_when_perspective_may_have_compressed_it():
    r = assess_directional_clearance(
        observed=0.110,
        scale_low=0.80,
        scale_high=0.95,
        low_boundary=0.129,
    )
    assert r.status is DirectionalClearanceStatus.LOW_POSSIBLE
    assert r.true_low < 0.129 < r.true_high


def test_large_gap_is_not_treated_as_a_defect():
    r = assess_directional_clearance(
        observed=0.220,
        scale_low=0.90,
        scale_high=1.10,
        low_boundary=0.129,
    )
    assert r.status is DirectionalClearanceStatus.NOT_LOW


def test_zero_gap_cannot_be_rescued_by_finite_perspective_scale():
    r = assess_directional_clearance(
        observed=0.0,
        scale_low=0.50,
        scale_high=1.50,
        low_boundary=0.129,
    )
    assert r.status is DirectionalClearanceStatus.LOW_CONFIRMED
