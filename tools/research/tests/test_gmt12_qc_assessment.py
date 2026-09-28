from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from gmt12_auto_landmarks import Detection
from gmt12_qc_assessment import Status, assess_detection
from human_qc_geometry import Gmt12Geometry, Point


def geometry_for_gap_ratio(ratio: float) -> Gmt12Geometry:
    y = 100.0 + ratio * 100.0
    return Gmt12Geometry(
        triangle_top_left=Point(100.0, y),
        triangle_top_right=Point(200.0, y),
        triangle_tip=Point(150.0, y + 120.0),
        minute_inner_left=Point(90.0, 100.0),
        minute_inner_right=Point(210.0, 100.0),
        minute_60_center=Point(150.0, 100.0),
    )


def test_missing_landmarks_can_never_pass():
    a = assess_detection(Detection(None, 0.0, "physical 60/neighbour minute ticks not verified"))
    assert a.status is Status.UNASSESSABLE
    assert a.measurements is None


def test_obvious_too_small_top_gap_is_reported():
    a = assess_detection(Detection(geometry_for_gap_ratio(0.04), 1.0, ""))
    assert a.status is Status.REFERENCE_DEVIATION
    assert a.measurements is not None
    assert abs(a.measurements.top_clearance_over_triangle_width - 0.04) < 1e-9
    assert "materially below" in a.reason


def test_observed_clean_0143_gap_is_not_called_defective():
    a = assess_detection(Detection(geometry_for_gap_ratio(0.143), 1.0, ""))
    assert a.status is Status.MEASURED


def test_first_verified_genuine_anchor_is_measured():
    a = assess_detection(Detection(geometry_for_gap_ratio(0.149), 1.0, ""))
    assert a.status is Status.MEASURED


def test_second_verified_genuine_anchor_is_measured():
    a = assess_detection(Detection(geometry_for_gap_ratio(0.169), 1.0, ""))
    assert a.status is Status.MEASURED


def test_large_gap_is_reported_without_calling_it_rolex_tolerance():
    a = assess_detection(Detection(geometry_for_gap_ratio(0.35), 1.0, ""))
    assert a.status is Status.REFERENCE_DEVIATION
    assert "materially above" in a.reason
    assert "not a Rolex tolerance" in a.reason


def test_widened_band_low_edge_from_weaker_provenance_photo_is_measured():
    # observed genuine-claimed measurement, weaker provenance (screenshot)
    a = assess_detection(Detection(geometry_for_gap_ratio(0.114), 1.0, ""))
    assert a.status is Status.MEASURED


def test_verified_126710blro_phillips_232301_is_measured():
    # Phillips auction lot 232301, real provenance -- see
    # docs/research/gmt12_session_findings_2026-09-26.md
    a = assess_detection(Detection(geometry_for_gap_ratio(0.1211), 1.0, ""))
    assert a.status is Status.MEASURED


def test_verified_126710blro_phillips_146213_high_outlier_is_measured():
    # Phillips auction lot 146213, real provenance, the highest observed
    # genuine top_clearance -- visually and subpixel-verified as a correct
    # landmark placement, not detector error
    a = assess_detection(Detection(geometry_for_gap_ratio(0.2552), 1.0, ""))
    assert a.status is Status.MEASURED


def test_just_below_widened_strong_low_is_reference_deviation():
    a = assess_detection(Detection(geometry_for_gap_ratio(0.09), 1.0, ""))
    assert a.status is Status.REFERENCE_DEVIATION


def test_just_above_widened_strong_high_is_reference_deviation():
    a = assess_detection(Detection(geometry_for_gap_ratio(0.28), 1.0, ""))
    assert a.status is Status.REFERENCE_DEVIATION
