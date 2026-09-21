"""Manifest of real-photo fixtures used for Python-engine regression/parity
tracking. Fixtures are shared with the (frozen) Android implementation --
they live under android/app/src/androidTest/assets/debug/ so both engines
are validated against the exact same input files, which is what makes a
parity comparison meaningful.

Each entry pins the CURRENT known-good Python output (as a regression
baseline, not a claim of ground truth) plus any independently-known human/
community ground truth for context. When a deliberate algorithm change
moves these numbers, update the entry in the same commit as the change and
explain why in the commit message -- an unexplained manifest change is a
regression until proven otherwise.
"""
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

REPO_ROOT = Path(__file__).resolve().parents[3]
DEBUG_FIXTURE_DIR = REPO_ROOT / "android/app/src/androidTest/assets/debug"


@dataclass
class FixtureCase:
    name: str
    path: Path
    note: str
    expect_pose_accepted: Optional[bool] = None
    expect_tilt_deg_range: Optional[tuple] = None
    expect_confidence_min: Optional[float] = None
    known_issue: str = ""


CASES = [
    FixtureCase(
        name="community-tilted-126710blnr-01",
        path=DEBUG_FIXTURE_DIR / "community-tilted-126710blnr-01.jpg",
        note=(
            "Marketplace listing photo, close to front-on despite the filename. "
            "Was previously REJECTED (bogus ~35-37deg apparent tilt) due to the "
            "seed-tolerance acquisition bug fixed 2026-09-21 (see README)."
        ),
        expect_pose_accepted=True,
        expect_tilt_deg_range=(2.0, 9.0),
        expect_confidence_min=0.60,
    ),
    FixtureCase(
        name="community-vsf-batgirl-crooked12-01",
        path=DEBUG_FIXTURE_DIR / "community-vsf-batgirl-crooked12-01.jpg",
        note=(
            "r/RepTimeQC 'VSF Batgirl please help QC for wedding GL?' thread photo. "
            "Community consensus leaned GL (4-2); several commenters flagged the "
            "12 o'clock triangle as slightly CW-tilted/crooked, one flagged 9."
        ),
        expect_pose_accepted=True,
        expect_tilt_deg_range=(2.0, 9.0),
        expect_confidence_min=0.60,
        known_issue=(
            "marker_qc.measure() isolates zero of the twelve hour markers on this "
            "photo even though pose acquisition succeeds -- not yet root-caused. "
            "Tracked as an open item; do not assert marker results for this case."
        ),
    ),
]
