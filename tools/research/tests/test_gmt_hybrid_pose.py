import math
from types import SimpleNamespace

from gmt_hybrid_pose import (
    EllipsePose,
    ExperimentalPoseLabel,
    _angle_diff_180,
    _experimental_label,
)


def p(**kw):
    base = dict(
        top_width_px=20.0,
        bottom_width_px=20.0,
        left_width_px=20.0,
        right_width_px=20.0,
        mean_width_px=20.0,
        vertical_asymmetry=0.0,
        horizontal_asymmetry=0.0,
        first_harmonic_strength=0.05,
        widest_direction_deg=0.0,
        min_width_over_mean=0.90,
        edge_coverage=0.40,
        normalized_fit_residual=0.04,
        inner_radius_seed_px=100.0,
        outer_radius_seed_px=120.0,
    )
    base.update(kw)
    return SimpleNamespace(**base)


def e(tilt):
    return EllipsePose(
        axis_ratio=math.cos(math.radians(tilt)),
        tilt_deg=tilt,
        minor_axis_direction_deg=0.0,
        fit_rms_over_radius=0.01,
        sample_count=100,
    )


def test_angle_diff_is_unoriented():
    assert _angle_diff_180(5, 175) == 10
    assert _angle_diff_180(10, 190) == 0


def test_near_frontal_is_good():
    label, _ = _experimental_label(p(), e(4))
    assert label == ExperimentalPoseLabel.GOOD


def test_intermediate_rehaut_compression_is_correctable():
    label, _ = _experimental_label(p(min_width_over_mean=0.63, first_harmonic_strength=0.26), e(9))
    assert label == ExperimentalPoseLabel.CORRECTABLE


def test_collapsed_rehaut_is_retake():
    label, _ = _experimental_label(p(min_width_over_mean=0.47, first_harmonic_strength=0.30), e(12))
    assert label == ExperimentalPoseLabel.RETAKE


def test_severe_ellipse_tilt_is_retake_even_if_rehaut_width_survives():
    label, _ = _experimental_label(p(min_width_over_mean=0.82), e(15.5))
    assert label == ExperimentalPoseLabel.RETAKE


def test_poor_coverage_is_unassessable():
    label, _ = _experimental_label(p(edge_coverage=0.15), e(4))
    assert label == ExperimentalPoseLabel.UNASSESSABLE
