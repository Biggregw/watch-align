import cv2
import numpy as np
import pytest

from rotation_alignment import estimate_rotation


def watch_image(size=640, distinctive=True):
    center, radius = size / 2, size * .34
    image = np.full((size, size, 3), 30, np.uint8)
    cv2.circle(image, (round(center), round(center)), round(radius), (170, 170, 170), 4)
    for hour in range(12):
        theta = np.deg2rad(hour * 30)
        x, y = center + radius * .76 * np.sin(theta), center - radius * .76 * np.cos(theta)
        cv2.circle(image, (round(x), round(y)), round(radius * .045), (230, 230, 230), -1)
    if distinctive:
        points = np.int32([[center, center-radius*.86], [center-radius*.08, center-radius*.66], [center+radius*.08, center-radius*.66]])
        cv2.fillPoly(image, [points], (245, 245, 245))
        cv2.putText(image, 'WATCH', (round(center-radius*.29), round(center-radius*.34)), cv2.FONT_HERSHEY_SIMPLEX, .65, (210, 210, 210), 2)
        cv2.rectangle(image, (round(center+radius*.42), round(center-radius*.10)), (round(center+radius*.67), round(center+radius*.10)), (235, 235, 235), -1)
    return image, (center, center, radius)


@pytest.mark.parametrize('angle', [0, 3.7, -8.2, 24, -47, 90, 133, 179, -179])
def test_corrects_both_directions_and_large_rotations(angle):
    reference, circle = watch_image()
    candidate = cv2.warpAffine(reference, cv2.getRotationMatrix2D(circle[:2], angle, 1), reference.shape[1::-1])
    correction, diagnostics = estimate_rotation(reference, candidate, circle, circle)
    assert diagnostics['applied'], diagnostics
    error = (correction + angle + 180) % 360 - 180
    assert abs(error) < .4


def test_repeating_markers_do_not_claim_unique_orientation():
    reference, circle = watch_image(distinctive=False)
    candidate = cv2.warpAffine(reference, cv2.getRotationMatrix2D(circle[:2], 37, 1), reference.shape[1::-1])
    correction, diagnostics = estimate_rotation(reference, candidate, circle, circle)
    assert correction is None
    assert diagnostics['confidence'] == 'low'


def test_featureless_image_does_not_claim_orientation():
    image = np.zeros((640, 640, 3), np.uint8)
    correction, diagnostics = estimate_rotation(image, image, (320, 320, 200), (320, 320, 200))
    assert correction is None
    assert not diagnostics['applied']


def test_handles_different_photo_sizes_and_centers():
    reference, circle = watch_image()
    target_circle = (390., 430., circle[2] * 1.2)
    matrix = cv2.getRotationMatrix2D(circle[:2], 63.2, 1.2)
    matrix[:, 2] += np.array(target_circle[:2]) - np.array(circle[:2])
    candidate = cv2.warpAffine(reference, matrix, (850, 900))
    correction, diagnostics = estimate_rotation(reference, candidate, circle, target_circle)
    assert diagnostics['applied'], diagnostics
    assert abs(correction + 63.2) < .5


def test_different_hands_do_not_override_fixed_dial_geometry():
    reference, circle = watch_image()
    candidate = reference.copy()
    cv2.line(reference, (320, 320), (230, 300), (220, 220, 220), 7)
    cv2.line(candidate, (320, 320), (390, 400), (220, 220, 220), 7)
    candidate = cv2.warpAffine(candidate, cv2.getRotationMatrix2D(circle[:2], -52, 1), (640, 640))
    correction, diagnostics = estimate_rotation(reference, candidate, circle, circle)
    assert diagnostics['applied'], diagnostics
    assert abs(correction - 52) < .5


def test_small_angle_polar_estimator_uses_opencv_correction_sign():
    import main
    reference, circle = watch_image()
    candidate = cv2.warpAffine(reference, cv2.getRotationMatrix2D(circle[:2], 7, 1), (640, 640))
    correction, _ = main.estimate_polar_rotation(reference, candidate, circle, circle)
    assert abs(correction + 7) < .5


def test_rotated_analysis_preserves_radius_and_corrects_marker_frame():
    from rotation_alignment import rotated_analysis_view
    from v1_full import marker_measurements, MODEL_GEOMETRY
    reference, circle = watch_image()
    candidate = cv2.warpAffine(reference, cv2.getRotationMatrix2D(circle[:2], 37, 1), (640, 640))
    corrected, new_circle = rotated_analysis_view(candidate, circle, -37)
    assert corrected.shape[0] > candidate.shape[0]
    assert new_circle[2] == circle[2]
    markers, summary = marker_measurements(corrected, new_circle, MODEL_GEOMETRY['124060'])
    assert summary['available']
    assert summary['median_abs_marker_error_deg'] < .5


@pytest.mark.parametrize('angle', [-47., 28., 90.])
def test_complete_alignment_pipeline_keeps_large_rotation(monkeypatch, angle):
    import main
    import v1_upgrade
    reference, circle = watch_image()
    candidate = cv2.warpAffine(reference, cv2.getRotationMatrix2D(circle[:2], angle, 1), (640, 640))
    monkeypatch.setattr(main, 'detect_watch_circle', lambda image: circle)
    monkeypatch.setattr(main, 'refine_crystal_circle', lambda image, supplied: supplied)
    monkeypatch.setattr(main, 'perspective_dewarp_candidate', lambda image, c: (image, np.eye(2, 3), {'perspective_dewarp_applied': False}))
    monkeypatch.setattr(main, 'refine_watch_alignment_ecc', lambda r, c, m, rc: (m, {'ecc_score': .9}))
    monkeypatch.setattr(main, 'refine_logo_text_alignment', lambda r, c, m, rc: (m, {}))
    monkeypatch.setattr(v1_upgrade, 'stable_ecc_refinement', lambda r, c, m, rc: (m, {'applied': False}))
    matrix, metrics = main.auto_align(reference, candidate)
    expected = cv2.getRotationMatrix2D(circle[:2], -angle, 1)
    np.testing.assert_allclose(matrix, expected, atol=1.0)
    assert metrics['rotation_alignment']['applied']


def test_ambiguous_rotation_prevents_high_visual_confidence():
    from v1_confidence_v110 import visual_alignment_confidence
    metrics = {'base_alignment': {'confidence': 'high', 'rotation_alignment': {'confidence': 'low'}},
               'region_confidence': {'dial': 'high', 'markers': 'high'}}
    assert visual_alignment_confidence(metrics, 'gen') == 'low'
