import numpy as np
import cv2

import v1_ux_v120 as ux


def test_v120_ui_has_guided_tasks_and_advanced_reference():
    assert ux.V120_VERSION == "1.2.0"
    assert "Check my watch" in ux.UX_HTML
    assert "Compare with genuine" in ux.UX_HTML
    assert "Manual overlay" in ux.UX_HTML
    assert "Advanced · use my own genuine/reference image" in ux.UX_HTML
    assert "Recent comparisons" in ux.UX_HTML
    assert "Overlay opacity" in ux.UX_HTML
    assert "Blink genuine / watch" in ux.UX_HTML


def test_consensus_requires_repeatability_before_strong_claim():
    base = {
        "metrics": {
            "visual_alignment_confidence": "high",
            "bezel": {"reliable": True, "offset_deg": 0.2},
            "date_window": {"reliable": True, "x_offset_percent": 0.4},
        }
    }
    second = {
        "metrics": {
            "visual_alignment_confidence": "high",
            "bezel": {"reliable": True, "offset_deg": 0.4},
            "date_window": {"reliable": True, "x_offset_percent": 1.0},
        }
    }
    c = ux._consensus([base, second])
    assert c["level"] == "high"
    assert c["bezel_stable"] is True
    assert c["date_stable"] is True
    assert "No defect should be flagged unless it repeats" in c["summary"]


def test_consensus_marks_reference_disagreement_inconclusive():
    a = {"metrics": {"visual_alignment_confidence": "low", "bezel": {"reliable": True, "offset_deg": -2.0}}}
    b = {"metrics": {"visual_alignment_confidence": "medium", "bezel": {"reliable": True, "offset_deg": 2.0}}}
    c = ux._consensus([a, b])
    assert c["level"] == "low"
    assert c["bezel_stable"] is False
    assert "inconclusive" in c["summary"]


def test_blue_bezel_model_suggestion_prefers_blnr():
    image = np.zeros((800, 800, 3), dtype=np.uint8)
    cx = cy = 400
    cv2.circle(image, (cx, cy), 300, (255, 0, 0), 45)
    suggestion, confidence = ux._model_suggestion(image, (cx, cy, 250))
    assert suggestion == "126710BLNR"
    assert confidence in {"medium", "high"}
