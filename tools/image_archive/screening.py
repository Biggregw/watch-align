"""Independent fail-closed admission; existing suitable flags are never admission evidence.

The production profile is face-on-v3 (faceon.py; evidence in
docs/image-archive/FACE-ON-VALIDATION.md). Two purposes share the same photo-level checks:

  full_watch     every required hour marker must be ELIGIBLE (the strict face-on-v3 standard)
  marker_masked  photographic suitability is decided first; then each marker gets a status
                 (eligibility.py) and the photograph carries a SHA-bound eligibility mask. At most
                 eligibility.MAX_EXCLUDED_MARKERS required markers may be excluded. A mask never
                 excuses a photo-level failure.
 Every required signal must be affirmatively
true inside its validated envelope; a known failure rejects; anything untested,
missing or non-finite is inconclusive. Diagnostic-only adapters can reject obvious
failures but cannot manufacture a PASS.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass, field
from hashlib import sha256
from io import BytesIO
from pathlib import Path
import math
from PIL import Image, ImageOps

from . import eligibility

POLICY_VERSION = 'face-on-v3'
# Archive admission floor (dial radius, px; faceon.MIN_DIAL_RADIUS_PX). The app's own frozen 200 px floor is unchanged.
MIN_DIAL_RADIUS_PX = 250
REQUIRED_CHECKS = ('face_on_ellipse', 'face_on_rehaut_vertical', 'face_on_rehaut_horizontal',
                   'face_on_marker_lattice', 'complete_dial', 'complete_minute_track',
                   'original_resolution', 'sharp_boundaries', 'exposure', 'glare',
                   'reflections', 'landmarks')
PURPOSES = ('full_watch', 'marker_masked')


@dataclass(frozen=True)
class Decision:
    sha256: str
    status: str
    reasons: tuple[str, ...]
    archive_policy_version: str
    family: str
    purpose: str
    profile: str
    usable_marker_ids: tuple[int, ...]
    required_marker_ids: tuple[int, ...]
    metrics: dict
    # the SHA-bound per-marker eligibility mask (eligibility.build); empty when no marker statuses were produced
    marker_eligibility: dict = field(default_factory=dict)

    def record(self):
        return asdict(self)


class Screener:
    """Analyzer receives decoded original and family/purpose, returns evidence.

    `validated_profiles` is application-controlled, never taken from a candidate,
    manifest, environment variable or harvester suitable flag. Production registry
    is empty pending empirical validation. Dependency injection supports tests and
    future reviewed model-specific adapters without editing production geometry.
    """
    def __init__(self, analyzer=None, validated_profiles=()):
        self.analyzer = analyzer
        self.validated_profiles = frozenset(validated_profiles)

    def assess_candidate(self, path, family, purpose='full_watch'):
        return self.assess_bytes(Path(path).read_bytes(), family, purpose)

    def assess_bytes(self, data, family, purpose='full_watch'):
        digest = sha256(data).hexdigest()
        reasons, metrics, usable, required = [], {}, (), ()
        profile = 'unvalidated'
        try:
            with Image.open(BytesIO(data)) as check:
                check.verify()
            with Image.open(BytesIO(data)) as source:
                source.load()
                metrics.update(width=source.width, height=source.height, format=source.format)
                if source.format not in {'JPEG', 'PNG', 'WEBP', 'TIFF'} or getattr(source, 'n_frames', 1) != 1:
                    reasons.append('fail_unsupported_image_format')
                img = ImageOps.exif_transpose(source).convert('RGB')
        except Exception:
            return Decision(digest, 'fail', ('fail_image_integrity',), POLICY_VERSION,
                            family, purpose, profile, (), (), metrics)
        # The dial cannot be wider than the photo: below the archive's 250 px dial-radius floor whatever is in it.
        if min(metrics['width'], metrics['height']) < 2 * MIN_DIAL_RADIUS_PX:
            reasons.append('fail_original_resolution')
        if purpose not in PURPOSES:
            reasons.append('inconclusive_unknown_purpose')
        mask = {}
        try:
            evidence = self.analyzer(img, family, purpose) if self.analyzer else {}
            profile = evidence.get('profile', 'unvalidated')
            metrics['analysis'] = evidence
            # Even a diagnostic-only adapter may report independently observable failures.
            checks = evidence.get('checks', {})
            for name in REQUIRED_CHECKS:
                value = checks.get(name)
                if value is False:
                    reasons.append('fail_' + name)
                elif value is not True:
                    reasons.append('inconclusive_' + name)
            required = tuple(evidence.get('required_marker_ids', ()))
            markers = evidence.get('markers', {})
            if (set(required) not in (set(range(1, 13)), set(range(1, 13)) - {3})
                    or len(required) != len(set(required))
                    or any(type(m) is not int or m not in range(1, 13) for m in required)):
                reasons.append('inconclusive_marker_layout')
                required = ()
            if 'marker_status' in evidence and required:
                mask, why = _mask(digest, family, evidence['marker_status'], profile, required)
                reasons += why if purpose == 'marker_masked' else []
                if mask and [m for m in required if markers.get(str(m)) is True] != mask['eligible_marker_ids']:
                    reasons.append('inconclusive_marker_status_disagrees')
            elif purpose == 'marker_masked':
                reasons.append('inconclusive_marker_status_missing')
            if purpose == 'marker_masked':
                usable = tuple(mask.get('eligible_marker_ids', ()))
            else:
                usable = tuple(m for m in required if markers.get(str(m)) is True)
                for m in required:
                    if markers.get(str(m)) is False:
                        reasons.append(f'fail_marker_{m}_obstructed_or_unreliable')
                    elif markers.get(str(m)) is not True:
                        reasons.append(f'inconclusive_marker_{m}')
            if profile not in self.validated_profiles:
                reasons.append('inconclusive_profile_not_validated')
            if not _finite(evidence):
                reasons.append('inconclusive_nonfinite_evidence')
        except Exception:
            reasons.append('inconclusive_analysis_failed')
        status = 'fail' if any(r.startswith('fail_') for r in reasons) else 'inconclusive' if reasons else 'pass'
        return Decision(digest, status, tuple(sorted(set(reasons))), POLICY_VERSION,
                        family, purpose, profile, usable, required, metrics, mask)


def _mask(digest, family, statuses, profile, required):
    """(mask, reasons): build and verify the SHA-bound mask, then apply the excluded-marker limit."""
    try:
        mask = eligibility.build(digest, family, statuses, POLICY_VERSION, profile)
        eligibility.validate(mask, digest, max_excluded=len(required))   # structure and evidence; count below
    except (eligibility.MaskError, KeyError, TypeError, ValueError):
        return {}, ['inconclusive_marker_mask_invalid']
    if tuple(mask['required_marker_ids']) != tuple(required):
        return {}, ['inconclusive_marker_layout']
    need = eligibility.min_eligible(mask['model'])
    have = len(mask['eligible_marker_ids'])
    open_ = sum(mask['markers'][str(m)]['status'] == eligibility.INCONCLUSIVE for m in required)
    if have >= need:
        return mask, []
    # fewer eligible markers than the evidence supports; untested markers could still make up the shortfall
    return mask, ['inconclusive_too_many_excluded_markers' if have + open_ >= need else 'fail_too_many_excluded_markers']


def _finite(value):
    if isinstance(value, float):
        return math.isfinite(value)
    if isinstance(value, dict):
        return all(_finite(v) for v in value.values())
    if isinstance(value, (list, tuple)):
        return all(_finite(v) for v in value)
    return True
