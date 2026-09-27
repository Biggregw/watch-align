"""Fail-closed assessment for the human-defined GMT 12 QC measurements.

Landmark detection and measurement happen upstream. Missing landmarks can
never become a pass.

The two genuine top-clearance values are verified observations, not Rolex
factory tolerances. A small excursion beyond either observation is therefore
not a defect. We keep the raw value and only flag a *strong* empirical
reference deviation once it is separated from the observed genuine interval
by a deliberately conservative margin.

The margin is provisional validation policy, not a manufacturing tolerance.
It exists to distinguish the clearly reduced-gap cases we are validating from
normal measurement / watch-to-watch variation while more labelled examples
are collected.
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Optional

from gmt12_auto_landmarks import Detection
from human_qc_geometry import Gmt12Measurements, measure_gmt12

GEN_TOP_CLEARANCE_LOW = 0.149
GEN_TOP_CLEARANCE_HIGH = 0.169
REFERENCE_MARGIN = 0.020
STRONG_LOW = GEN_TOP_CLEARANCE_LOW - REFERENCE_MARGIN   # 0.129
STRONG_HIGH = GEN_TOP_CLEARANCE_HIGH + REFERENCE_MARGIN # 0.189


class Status(str, Enum):
    UNASSESSABLE = "UNASSESSABLE"
    REFERENCE_DEVIATION = "REFERENCE_DEVIATION"
    MEASURED = "MEASURED"


@dataclass(frozen=True)
class Assessment:
    status: Status
    measurements: Optional[Gmt12Measurements]
    reason: str


def assess_detection(detection: Detection) -> Assessment:
    """Assess a detector result without converting missing evidence to pass."""
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
    if gap < STRONG_LOW:
        return Assessment(
            Status.REFERENCE_DEVIATION,
            m,
            f"12 top clearance {gap:.3f} is materially below the verified genuine observations "
            f"({GEN_TOP_CLEARANCE_LOW:.3f}-{GEN_TOP_CLEARANCE_HIGH:.3f}); provisional strong-deviation boundary is <{STRONG_LOW:.3f}, not a Rolex tolerance",
        )
    if gap > STRONG_HIGH:
        return Assessment(
            Status.REFERENCE_DEVIATION,
            m,
            f"12 top clearance {gap:.3f} is materially above the verified genuine observations "
            f"({GEN_TOP_CLEARANCE_LOW:.3f}-{GEN_TOP_CLEARANCE_HIGH:.3f}); provisional strong-deviation boundary is >{STRONG_HIGH:.3f}, not a Rolex tolerance",
        )

    return Assessment(
        Status.MEASURED,
        m,
        f"12 geometry measured successfully; top clearance {gap:.3f}. Genuine observations "
        f"are {GEN_TOP_CLEARANCE_LOW:.3f}-{GEN_TOP_CLEARANCE_HIGH:.3f}; no hard Rolex tolerance is inferred. "
        "Centring, rotation and side asymmetry remain reported as raw diagnostics until calibrated.",
    )
