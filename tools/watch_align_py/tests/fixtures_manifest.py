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
    expect_markers_measured_min: Optional[int] = None
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
        expect_markers_measured_min=11,
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
        expect_markers_measured_min=4,
        known_issue=(
            "marker_qc used a fixed brightness threshold (150) that isolated zero of "
            "the twelve hour markers on this photo -- this photo's exposure is dim "
            "enough that no marker's brightest pixels reach 150 (max ~180, and most "
            "top out ~150-165). Fixed 2026-09-21 by switching to a per-marker Otsu "
            "threshold on the local ROI (see README); now isolates 4/11 (hours "
            "4,5,7,8). The 12 marker specifically -- the one the r/RepTimeQC thread "
            "flagged as 'slightly CW tilted / not aligning with the crown' -- is "
            "found but its bright-pixel centroid sits ~0.11 dial-radius-units "
            "inward of the calibrated reference, outside the +/-0.08 sanity window, "
            "so it is still reported as not confidently isolated rather than risking "
            "a wrong angular/radial number. Not yet resolved whether that reflects a "
            "real defect signal or a calibration mismatch for this triangle style; "
            "needs a genuine-reference comparison, not more threshold tuning."
        ),
    ),
]
