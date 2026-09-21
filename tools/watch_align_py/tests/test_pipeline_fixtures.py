"""Regression/parity harness for the Python pose-acquisition pipeline against
real photo fixtures shared with the (frozen) Android implementation.

Run from tools/watch_align_py/:
    pip install -r requirements.txt
    pytest tests/
"""
import math
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pipeline
from run import decode_capped
from fixtures_manifest import CASES


@pytest.mark.parametrize("case", CASES, ids=[c.name for c in CASES])
def test_fixture_pose(case):
    if not case.path.exists():
        pytest.skip(f"fixture not present: {case.path}")
    bgr = decode_capped(str(case.path))
    result = pipeline.build(bgr)

    assert result.reason == "", f"acquisition failed outright: {result.reason}"

    if case.expect_pose_accepted is not None:
        assert result.accepted == case.expect_pose_accepted, (
            f"{case.name}: expected accepted={case.expect_pose_accepted}, "
            f"got {result.accepted}\n{result.report}"
        )

    if case.expect_tilt_deg_range is not None:
        lo, hi = case.expect_tilt_deg_range
        assert lo <= result.tilt_deg <= hi, (
            f"{case.name}: tilt {result.tilt_deg:.2f} deg outside expected "
            f"range [{lo}, {hi}]"
        )

    if case.expect_confidence_min is not None:
        assert result.confidence >= case.expect_confidence_min, (
            f"{case.name}: confidence {result.confidence:.2f} below "
            f"expected minimum {case.expect_confidence_min}"
        )


def test_no_regression_on_blank_image():
    """A structureless image must fail safe (no dial found), never crash."""
    import numpy as np
    bgr = np.zeros((400, 400, 3), dtype="uint8")
    result = pipeline.build(bgr)
    assert not result.accepted
    assert result.reason != ""
