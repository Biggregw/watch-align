import math

import cv2
import numpy as np

from v1_full import MODEL_GEOMETRY, bezel_top_measurement, marker_measurements, model_info


def synthetic_watch(size=720, marker_rotation=0.0):
    image = np.full((size, size, 3), 28, dtype=np.uint8)
    c = size // 2
    r = size * 0.27
    cv2.circle(image, (c, c), int(r), (205, 205, 205), 4, cv2.LINE_AA)
    cv2.circle(image, (c, c), int(r * 0.88), (65, 65, 65), -1, cv2.LINE_AA)
    for hour in range(12):
        a = math.radians(hour * 30.0 + marker_rotation)
        x = int(round(c + math.sin(a) * r * 0.72))
        y = int(round(c - math.cos(a) * r * 0.72))
        cv2.circle(image, (x, y), 10, (245, 245, 245), -1, cv2.LINE_AA)
    # Strong upper bezel triangle-like feature at 12.
    pts = np.array([[c, int(c-r*1.16)], [c-12, int(c-r*1.05)], [c+12, int(c-r*1.05)]], np.int32)
    cv2.polylines(image, [pts], True, (250, 250, 250), 5, cv2.LINE_AA)
    return image, (float(c), float(c), float(r))


def test_catalog_has_first_two_reference_models():
    assert set(MODEL_GEOMETRY) >= {"126710BLNR", "124060"}
    assert model_info("126710BLNR")["has_date"] is True
    assert model_info("124060")["has_date"] is False


def test_marker_measurements_remove_global_rotation():
    image, circle = synthetic_watch(marker_rotation=0.8)
    markers, summary = marker_measurements(image, circle, MODEL_GEOMETRY["124060"])
    assert summary["available"]
    available = [m for m in markers if m["available"]]
    assert len(available) >= 10
    median_error = float(np.median(np.abs([m["angular_error_deg"] for m in available])))
    assert median_error < 0.8
    assert abs(summary["overall_rotation_deg"]) < 2.0


def test_bezel_top_estimate_is_near_twelve_on_synthetic_watch():
    image, circle = synthetic_watch()
    result = bezel_top_measurement(image, circle, MODEL_GEOMETRY["124060"])
    assert result["available"]
    assert abs(result["offset_deg"]) < 3.0
