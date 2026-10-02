"""Paths, limits and quality thresholds for the dataset harvester.

Every threshold that can turn an image or a watch into a REJECT lives here, with the reason it
has the value it has. Nothing in this file changes the production QC in the Android app; these
are acquisition rules for the research test set only.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

from .families import GMT_12

REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_DATA_DIR = REPO_ROOT / "datasets" / "harvest"
HARNESS_DIR = REPO_ROOT / "tools" / "desktop-harness"

USER_AGENT = os.environ.get(
    "HARVEST_USER_AGENT",
    "WatchAlignDatasetHarvester/1.0 (+https://github.com/Biggregw/watch-align; research test set)",
)

# Backwards-compatible aliases for the existing GMT harvester. Submariner acquisition must select
# its own family configuration and must not inherit these GMT geometry/pose assumptions implicitly.
SUPPORTED_MODELS = GMT_12.models
UNSUPPORTED_GMT_MODELS = GMT_12.unsupported_predecessors


@dataclass(frozen=True)
class Thresholds:
    """Quality gates. Values are deliberately conservative: an image is rejected only when the
    defect is clear; anything borderline becomes QUARANTINE (inconclusive), never a silent pass."""

    # --- resolution -------------------------------------------------------------------------
    # Shortest image side, original pixels. The phase B genuine fetcher uses 500 and the
    # 126710BLNR corpus fetcher 300; the harvester takes the corpus value so it can keep the
    # smaller QC photos that are already in the regression set.
    min_image_side_px: int = 300
    # Dial diameter in ORIGINAL pixels (sum of the fitted ellipse semi-axes). The app has its own
    # "too small" flag for the 12 marker, so this only removes dials too small for anything:
    # in the corpus the app still read the 12 on dials of 153-256 px (0th-5th percentile of
    # app-usable photos, 2026-09-30 calibration); below 200 px the 12 triangle is under ~17 px.
    min_dial_diameter_px: float = 200.0

    # --- dial completeness ------------------------------------------------------------------
    # The fitted dial ellipse must lie inside the frame. Tolerance as a fraction of the dial
    # radius (lets a dial that touches the edge through the bezel still count as complete).
    dial_edge_tolerance: float = 0.02

    # --- pose -------------------------------------------------------------------------------
    # The pose label comes from the app's own GmtHumanPosePolicy (GOOD / CORRECTABLE / RETAKE /
    # UNASSESSABLE). RETAKE is rejected; UNASSESSABLE is inconclusive. No harvester-side angle
    # threshold is added on top: the app's pose policy is the authority.
    reject_pose_labels: tuple = ("RETAKE",)
    inconclusive_pose_labels: tuple = ("UNASSESSABLE",)

    # --- markers ----------------------------------------------------------------------------
    # Round hour markers found by the app. Fewer than this and the marker-layout pose cannot be
    # estimated (GmtMarkerPose needs 6): the photo is inconclusive_marker_layout, never a pass.
    min_round_markers_found: int = 6

    # --- sharpness --------------------------------------------------------------------------
    # Variance of the Laplacian inside the dial after resampling the dial to 512 px diameter
    # (so the value does not depend on the photo's resolution). Corpus calibration (112 photos
    # with a located dial): app-usable photos range 52-5100, 5th percentile 257. 25 rejects only
    # photos blurrier than anything the app has read; see docs/research/dataset_harvester.md.
    min_dial_sharpness: float = 25.0

    # --- exposure / glare -------------------------------------------------------------------
    # GMT dials are black and the lume is white, so studio photos legitimately have large crushed
    # (black dial) and clipped (lume) areas: in the corpus, app-usable photos reach crushed 0.73,
    # blown 0.10 and glare 0.16. The limits sit well above that and catch only a dial that is
    # essentially all black (markers invisible) or washed out. Fractions of the dial interior.
    max_crushed_fraction: float = 0.95     # luma <= 3
    max_blown_fraction: float = 0.25       # any channel >= 252
    max_glare_fraction: float = 0.30       # bright and desaturated (V>=235, S<=40)

    # --- duplicates -------------------------------------------------------------------------
    # 64-bit hashes; Hamming distance at or below these counts as the same photograph.
    near_dup_dhash_max: int = 6
    near_dup_phash_max: int = 8
    # Dial-region pHash catches crops of the same photo (whole-image hashes do not).
    near_dup_dial_phash_max: int = 6
    # A hash match is only a candidate. It is confirmed as the same photograph when at most this
    # fraction of pixels differs strongly (hashing.differing_fraction). Measured: resized/re-encoded
    # copies 0.000 (synthetic dial crops of one photo 0.000-0.002); two Bob's Watches catalogue
    # photos of different 126710BLNRs 0.054; synthetic same-set-up photos differing only in hand
    # angle and date 0.010-0.015. 0.005 keeps a margin on both sides.
    same_photo_max_diff_fraction: float = 0.005


@dataclass(frozen=True)
class Limits:
    max_images_per_source: int = 12
    max_download_bytes: int = 25_000_000
    min_download_bytes: int = 5_000
    request_timeout_s: float = 45.0
    # Minimum seconds between requests to one host (politeness, not evasion).
    per_host_interval_s: float = 1.0
    max_retries: int = 3
    # Default bounded run.
    default_max_sources: int = 25
    # Watches per (class, model, factory) group beyond which new watches are low value.
    group_saturation: int = 40


THRESHOLDS = Thresholds()
LIMITS = Limits()


@dataclass
class Paths:
    data_dir: Path = field(default_factory=lambda: Path(os.environ.get("HARVEST_DATA_DIR", DEFAULT_DATA_DIR)))

    @property
    def state_dir(self) -> Path: return self.data_dir / "state"
    @property
    def images_dir(self) -> Path: return self.data_dir / "images"
    @property
    def measurements_dir(self) -> Path: return self.data_dir / "measurements"
    @property
    def reports_dir(self) -> Path: return self.data_dir / "reports"
    @property
    def manifest(self) -> Path: return self.data_dir / "manifest.csv"
    @property
    def watches(self) -> Path: return self.data_dir / "watches.csv"
    @property
    def watch_measurements(self) -> Path: return self.data_dir / "watch_measurements.csv"
    @property
    def work_dir(self) -> Path: return self.data_dir / "work"

    def ensure(self) -> None:
        for d in (self.state_dir, self.images_dir, self.measurements_dir, self.reports_dir, self.work_dir):
            d.mkdir(parents=True, exist_ok=True)
