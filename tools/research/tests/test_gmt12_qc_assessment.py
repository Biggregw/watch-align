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


def test_observed_bad_0121_gap_is_reported():
    a = assess_detection(Detection(geometry_for_gap_ratio(0.121), 1.0, ""))
    assert a.status is Status.REFERENCE_DEVIATION


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
    a = assess_detection(Detection(geometry_for_gap_ratio(0.25), 1.0, ""))
    assert a.status is Status.REFERENCE_DEVIATION
    assert "materially above" in a.reason
    assert "not a Rolex tolerance" in a.reason
