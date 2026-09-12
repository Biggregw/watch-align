from v1_confidence_v110 import gate_measurements, perspective_suitability


def _marker(hour=1, confidence="high"):
    return {"hour": hour, "available": True, "confidence": confidence, "angular_error_deg": 0.2, "radial_error_percent": 0.1}


def test_visual_alignment_stays_high_when_perspective_is_medium():
    metrics = {
        "region_confidence": {"dial": "high", "markers": "high", "bezel": "low", "perspective": "high", "date/cyclops": "low"},
        "perspective": {"candidate": {"available": True, "tilt_deg": 20.7}, "reference": {"available": True, "tilt_deg": 13.8}, "mismatch_deg": 6.9},
        "markers": [_marker(i) for i in range(1, 13)],
        "marker_summary": {"available": True, "overall_rotation_deg": 0.1, "median_abs_marker_error_deg": 0.2, "ring_radius_ratio": 0.7},
        "bezel": {"available": True, "confidence": "low", "offset_deg": 0.4},
        "date_window": {"available": True, "confidence": "low", "x_offset_percent": 1.0, "y_offset_percent": 1.0, "bbox": [1,2,3,4]},
        "base_alignment": {"confidence": "high"},
    }
    gate_measurements(metrics, "gen")
    assert metrics["visual_alignment_confidence"] == "high"
    assert metrics["overall_confidence"] == "high"
    assert metrics["perspective_suitability"] == "medium"
    assert all(m["reliable"] for m in metrics["markers"])
    assert metrics["bezel"]["reliable"] is False
    assert metrics["date_window"]["reliable"] is False


def test_perspective_suitability_is_not_camera_angle_confidence():
    p = {"candidate": {"available": True, "tilt_deg": 20.0, "confidence": "high"}, "reference": {"available": True, "tilt_deg": 14.0, "confidence": "high"}, "mismatch_deg": 6.0}
    assert perspective_suitability(p) == "medium"
