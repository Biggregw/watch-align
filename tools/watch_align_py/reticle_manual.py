"""Manual-correction prototype for the generic QC reticle
(experiment/generic-qc-reticle, Part 9).

Automatic placement (minute-track acquisition -> ellipse + roll) is the
default and primary path; this module prototypes the small set of simple,
composable adjustments a user could apply on top of that automatic fit if
it needs a manual correction -- recentre (drag), scale, and rotate.

It deliberately does NOT add independent per-corner perspective handles.
The automatic ellipse+roll basis is already a single coherent affine model
for the whole reticle (see reticle.py's module docstring on why the two
geometric sources are kept explicitly separate but each internally
coherent). Nothing found in this research line justifies four
independently-draggable corners on top of that -- the failed assumption
this whole line has repeatedly disproven is bending geometry piecewise
without justification, and four free corners is exactly that. If real-
world use later shows genuine skew/keystone error that translate+scale+
rotate cannot correct, that would be evidence for a proper projective (4-
point homography) control -- it is not assumed here just because document-
scanner apps commonly have one.

Adjustments are expressed as a small, order-independent AdjustParams
struct applied to the RotatedRect + roll pair that reticle.build_geometry
already consumes, so no other code needs to change to support manual
correction -- the same automatic pipeline renders either the raw automatic
geometry or the manually-adjusted geometry.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Tuple

from geometry import RotatedRect


@dataclass
class AdjustParams:
    """A single manual-correction gesture, in the units a UI control would
    naturally produce: image-pixel drag, a uniform scale multiplier, and a
    rotation in degrees."""
    dx: float = 0.0          # px, image space -- recentre / drag
    dy: float = 0.0          # px, image space -- recentre / drag
    scale: float = 1.0       # multiplies both ellipse axes uniformly
    rotate_deg: float = 0.0  # added to roll -- spins the whole reticle


def apply(ellipse: RotatedRect, roll_deg: float, adj: AdjustParams
          ) -> Tuple[RotatedRect, float]:
    """Returns (new_ellipse, new_roll_deg).

    Order: recentre, then scale, then rotate. Rotation is applied to roll
    (not the ellipse's own axis angle) so it spins the whole reticle
    pattern as a rigid body rather than warping the anisotropic minute-
    track ellipse fit itself -- consistent with geometry.map_point's own
    convention, where roll is applied before the ellipse's axis/scale.
    """
    e = ellipse.recentered(ellipse.cx + adj.dx, ellipse.cy + adj.dy)
    e = e.scaled(adj.scale)
    return e, roll_deg + adj.rotate_deg


def compose(*adjustments: AdjustParams) -> AdjustParams:
    """Combine several small gestures (e.g. a drag followed by a scale
    pinch) into one. Composable and order-independent for this simple
    parameter set (translate/scale/rotate all commute with each other at
    this level -- there is no shear/perspective term to worry about
    ordering against)."""
    dx = sum(a.dx for a in adjustments)
    dy = sum(a.dy for a in adjustments)
    scale = 1.0
    for a in adjustments:
        scale *= a.scale
    rotate_deg = sum(a.rotate_deg for a in adjustments)
    return AdjustParams(dx=dx, dy=dy, scale=scale, rotate_deg=rotate_deg)
