"""Fail-closed assessment for the human-defined GMT 12 QC measurements.

This is intentionally downstream of landmark detection and measurement.  It
must never turn an unavailable measurement into a pass.

The two genuine top-clearance values below are the currently verified control
anchors (33459 and 33461).  They are reference observations, NOT Rolex factory
tolerances.  Values outside them are reported as reference deviations so that
we can validate the checker against real QC failures without pretending we
have established manufacturing limits.
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Optional

from gmt12_auto_landmarks import Detection
from human_qc_geometry import Gmt12Measurements, measure_gmt12

GEN_TOP_CLEARANCE_LOW = 0.149
GEN_TOP_CLEARANCE_HIGH = 0.169


class Status(str, Enum):
    UNASSESSABLE = "UNASSESSABLE"
    REFERENCE_DEVIATION = "REFERENCE_DEVIATION"
    WITHIN_CURRENT_REFERENCES = "WITHIN_CURRENT_REFERENCES"


@dataclass(frozen=True)
class Assessment:
    status: Status
    measurements: Optional[Gmt12Measurements]
    reason: str


def assess_detection(detection: Detection) -> Assessment:
    """Assess a detector result, failing closed on any missing landmark.

    A positive result is only possible after the physical triangle and the
    59/60/1 minute ticks have all been observed and every agreed measurement
    has been calculated.  Detector failure is therefore UNASSESSABLE, never a
    silent pass.
    """
    if detection.geometry is None:
        return Assessment(
            Status.UNASSESSABLE,
            None,
            detection.reason or "required 12-marker landmarks were not verified",
        )

    try:
        m = measure_gmt12(detection.geometry)
    except (ValueError, ZeroDivisionError) as exc:
        return Assessment(Status.UNASSESSABLE, None, f"12-marker measurement failed: {exc}")

    required = (
        m.top_clearance_over_triangle_width,
        m.horizontal_offset_over_triangle_width,
        m.rotation_deg,
        m.left_clearance_over_triangle_width,
        m.right_clearance_over_triangle_width,
        m.side_clearance_asymmetry,
    )
    if any(v is None for v in required):
        return Assessment(Status.UNASSESSABLE, None, "one or more required 12-marker measurements are missing")

    gap = m.top_clearance_over_triangle_width
    if gap < GEN_TOP_CLEARANCE_LOW:
        return Assessment(
            Status.REFERENCE_DEVIATION,
            m,
            f"12 top clearance {gap:.3f} is below both verified genuine controls "
            f"({GEN_TOP_CLEARANCE_LOW:.3f}-{GEN_TOP_CLEARANCE_HIGH:.3f}); this is a reference deviation, not a Rolex tolerance verdict",
        )
    if gap > GEN_TOP_CLEARANCE_HIGH:
        return Assessment(
            Status.REFERENCE_DEVIATION,
            m,
            f"12 top clearance {gap:.3f} is above both verified genuine controls "
            f"({GEN_TOP_CLEARANCE_LOW:.3f}-{GEN_TOP_CLEARANCE_HIGH:.3f}); this is a reference deviation, not a Rolex tolerance verdict",
        )

    return Assessment(
        Status.WITHIN_CURRENT_REFERENCES,
        m,
        f"12 top clearance {gap:.3f} lies between the two currently verified genuine controls; other 12 metrics remain descriptive until separately calibrated",
    )
