from __future__ import annotations

"""Research-only human-style attention model for GMT 12-marker rotation.

Rotation is treated differently from top clearance. The user requirement is
not to infer a Rolex tolerance, but to highlight angular misalignment once it
would plausibly be visible on close inspection so a human can investigate.

Two observables are used:

* the local angular error already measured by ``measure_gmt12`` between the
  triangle top edge and the immediate 59-to-1 minute-track tangent;
* the implied endpoint rise across the observed triangle width in image pixels.

The pixel term matters because a 2 degree skew across a 20 px marker is less
than one pixel and is not reliably visible, while the same 2 degrees across a
70 px marker is about 2.4 px and should be obvious when zoomed.

These are experimental attention thresholds, not manufacturing tolerances and
not an automatic RL/GL decision.
"""

from dataclasses import dataclass
from enum import Enum
import math
from typing import Optional


class RotationAttention(str, Enum):
    CLEAR = "CLEAR"
    CHECK = "CHECK"
    STRONG = "STRONG"
    UNASSESSABLE = "UNASSESSABLE"


@dataclass(frozen=True)
class RotationAssessment:
    attention: RotationAttention
    rotation_deg: Optional[float]
    visible_rise_px: Optional[float]
    reason: str


# Deliberately sensitive because this is an attention/highlight rule, not a
# defect threshold. A couple of degrees may still be acceptable to a human,
# but should be surfaced if the image has enough resolution to make it visible.
ANGLE_ATTENTION_DEG = 2.0
ANGLE_STRONG_DEG = 4.0
VISIBLE_RISE_ATTENTION_PX = 1.5
VISIBLE_RISE_STRONG_PX = 2.5
MIN_RESOLVABLE_RISE_PX = 0.75


def implied_endpoint_rise_px(rotation_deg: float, marker_width_px: float) -> float:
    """Vertical endpoint separation implied by angular skew across marker width."""
    if marker_width_px <= 0:
        raise ValueError("marker width must be positive")
    return abs(math.tan(math.radians(rotation_deg))) * marker_width_px


def assess_rotation_attention(
    rotation_deg: Optional[float],
    marker_width_px: Optional[float],
    *,
    pose_reliable: bool = True,
) -> RotationAssessment:
    """Return a human-review attention level for observed marker rotation.

    ``pose_reliable`` should be false for a severe/contradictory perspective
    estimate. In that case a modest apparent skew is not converted into a
    clean result. A gross visually resolvable skew is still surfaced for human
    inspection, but is explicitly marked pose-limited rather than treated as a
    geometric verdict.
    """
    if rotation_deg is None or not math.isfinite(rotation_deg):
        return RotationAssessment(RotationAttention.UNASSESSABLE, rotation_deg, None, "rotation measurement missing")

    rise = None
    if marker_width_px is not None and math.isfinite(marker_width_px) and marker_width_px > 0:
        rise = implied_endpoint_rise_px(rotation_deg, marker_width_px)

    a = abs(rotation_deg)
    resolvable = rise is None or rise >= MIN_RESOLVABLE_RISE_PX
    strong = resolvable and (
        a >= ANGLE_STRONG_DEG
        or (rise is not None and rise >= VISIBLE_RISE_STRONG_PX and a >= ANGLE_ATTENTION_DEG)
    )
    check = resolvable and (
        a >= ANGLE_ATTENTION_DEG
        or (rise is not None and rise >= VISIBLE_RISE_ATTENTION_PX)
    )

    if not pose_reliable:
        if strong or check:
            return RotationAssessment(
                RotationAttention.CHECK,
                rotation_deg,
                rise,
                "apparent marker skew is visually resolvable, but pose is too severe/contradictory for a precise angular verdict; inspect or retake",
            )
        return RotationAssessment(
            RotationAttention.UNASSESSABLE,
            rotation_deg,
            rise,
            "pose is too severe/contradictory to clear a small apparent rotation",
        )

    if strong:
        return RotationAssessment(
            RotationAttention.STRONG,
            rotation_deg,
            rise,
            "marker rotation is large enough to be conspicuous on close inspection; highlight for human investigation",
        )
    if check:
        return RotationAssessment(
            RotationAttention.CHECK,
            rotation_deg,
            rise,
            "marker rotation is plausibly visible on close inspection; highlight rather than silently pass",
        )

    return RotationAssessment(
        RotationAttention.CLEAR,
        rotation_deg,
        rise,
        "no visually resolvable marker rotation at the current image scale",
    )
