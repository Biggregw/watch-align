from __future__ import annotations

"""Research-only one-sided reasoning for GMT 12 top clearance.

Human QC is not symmetric here: a slightly generous gap is usually not a
problem, while a near-zero / touching gap is. Perspective should therefore be
used to ask whether it can explain away a small observed gap, not to penalise
large gaps equally.

Let `scale` be the local perspective factor relating physical to observed
normalised clearance:

    observed = true * scale

Given an uncertainty interval [scale_low, scale_high], the physically
plausible true clearance interval is:

    [observed / scale_high, observed / scale_low]

This allows safe one-sided conclusions without pretending the exact
perspective correction is known.
"""

from dataclasses import dataclass
from enum import Enum
import math


class DirectionalClearanceStatus(str, Enum):
    LOW_CONFIRMED = "LOW_CONFIRMED"
    LOW_POSSIBLE = "LOW_POSSIBLE"
    NOT_LOW = "NOT_LOW"
    UNASSESSABLE = "UNASSESSABLE"


@dataclass(frozen=True)
class DirectionalClearanceEvidence:
    status: DirectionalClearanceStatus
    observed: float
    true_low: float | None
    true_high: float | None
    reason: str


def assess_directional_clearance(
    observed: float,
    scale_low: float,
    scale_high: float,
    low_boundary: float,
) -> DirectionalClearanceEvidence:
    """Conservative one-sided low-clearance assessment.

    `scale_low/high` must bound the local perspective factor.  No high-gap
    defect is emitted: values above the low boundary remain NOT_LOW.  Large
    values can still be retained elsewhere as detector diagnostics.
    """
    vals = (observed, scale_low, scale_high, low_boundary)
    if any(not math.isfinite(v) for v in vals):
        return DirectionalClearanceEvidence(
            DirectionalClearanceStatus.UNASSESSABLE,
            observed,
            None,
            None,
            "non-finite input",
        )
    if observed < 0 or low_boundary < 0 or scale_low <= 0 or scale_high <= 0:
        return DirectionalClearanceEvidence(
            DirectionalClearanceStatus.UNASSESSABLE,
            observed,
            None,
            None,
            "invalid clearance/perspective bounds",
        )
    if scale_low > scale_high:
        scale_low, scale_high = scale_high, scale_low

    true_low = observed / scale_high
    true_high = observed / scale_low

    if true_high < low_boundary:
        return DirectionalClearanceEvidence(
            DirectionalClearanceStatus.LOW_CONFIRMED,
            observed,
            true_low,
            true_high,
            "even the perspective correction most favourable to a larger true gap remains below the low-clearance boundary",
        )

    if true_low < low_boundary:
        return DirectionalClearanceEvidence(
            DirectionalClearanceStatus.LOW_POSSIBLE,
            observed,
            true_low,
            true_high,
            "perspective uncertainty straddles the low-clearance boundary; retake/correction is required before calling it low",
        )

    return DirectionalClearanceEvidence(
        DirectionalClearanceStatus.NOT_LOW,
        observed,
        true_low,
        true_high,
        "the entire perspective-corrected interval remains above the low-clearance boundary",
    )
