from __future__ import annotations

"""Project the experimental rehaut pose vector into watch coordinates.

`gmt_rehaut_perspective` reports first-harmonic direction in image coordinates
(0=right, 90=down). That is sufficient for upright stills, but a wrist/video
capture can contain arbitrary in-plane watch roll. Perspective correction for
the 12 marker must therefore use axes tied to the watch itself, not the frame.

This module remains research-only and does not apply any GMT12 correction or
accept/reject threshold.
"""

from dataclasses import dataclass
import math
from typing import Optional

import cv2

from gmt12_auto_landmarks import _dial_circle, _triangle_candidate
from gmt_rehaut_perspective import RehautPerspective, analyze_rehaut_perspective


@dataclass(frozen=True)
class WatchRelativeRehaut:
    perspective: RehautPerspective
    twelve_direction_deg: float
    # First-harmonic asymmetry along the watch's 12<->6 radial axis.
    radial_12_asymmetry: float
    # First-harmonic asymmetry along the watch's 3<->9 tangential axis.
    tangential_3_asymmetry: float


@dataclass(frozen=True)
class WatchRelativeDetection:
    result: Optional[WatchRelativeRehaut]
    reason: str = ""


def asymmetry_along_direction(p: RehautPerspective, direction_deg: float) -> float:
    """First-harmonic normalized width asymmetry along an arbitrary axis.

    `direction_deg` names the positive end of the axis using image-coordinate
    polar angles: 0=right, 90=down, 180=left, 270=up. The opposite end is
    direction+180. For a pure first harmonic this equals
    (width(direction)-width(opposite))/(width(direction)+width(opposite)).
    """
    return p.first_harmonic_strength * math.cos(
        math.radians(direction_deg - p.widest_direction_deg)
    )


def analyze_watch_relative_rehaut(bgr) -> WatchRelativeDetection:
    pdet = analyze_rehaut_perspective(bgr)
    if pdet.perspective is None:
        return WatchRelativeDetection(None, pdet.reason)

    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY) if bgr.ndim == 3 else bgr.copy()
    circle = _dial_circle(gray)
    if circle is None:
        return WatchRelativeDetection(None, "dial centre/scale seed not found")
    cx, cy, r = map(float, circle)

    tri = _triangle_candidate(gray, cx, cy, r)
    if tri is None:
        return WatchRelativeDetection(None, "12 triangle not found for watch-axis projection")
    tl, tr, _ = tri
    mx = (tl.x + tr.x) / 2.0
    my = (tl.y + tr.y) / 2.0
    twelve = math.degrees(math.atan2(my - cy, mx - cx)) % 360.0

    p = pdet.perspective
    radial = asymmetry_along_direction(p, twelve)
    tangential = asymmetry_along_direction(p, (twelve + 90.0) % 360.0)
    return WatchRelativeDetection(
        WatchRelativeRehaut(
            perspective=p,
            twelve_direction_deg=twelve,
            radial_12_asymmetry=radial,
            tangential_3_asymmetry=tangential,
        ),
        "",
    )
