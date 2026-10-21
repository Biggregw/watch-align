"""Independent fail-closed admission; existing suitable flags are never admission evidence.

No numerical archive profile is promoted yet. A profile needs held-out photographic
validation of every required signal, including per-marker occlusion. Diagnostic-only
adapters can reject obvious failures but cannot manufacture a PASS.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass
from hashlib import sha256
from io import BytesIO
from pathlib import Path
import math
from PIL import Image, ImageOps

POLICY_VERSION = 'face-on-v1-review'
# Frozen app reliability floor (dial radius in analysed pixels); below it every reading is uncertain
MIN_DIAL_RADIUS_PX = 200
REQUIRED_CHECKS = ('face_on_ellipse', 'face_on_rehaut_vertical', 'face_on_rehaut_horizontal',
                   'face_on_marker_lattice', 'complete_dial', 'complete_minute_track',
                   'original_resolution', 'sharp_boundaries', 'exposure', 'glare',
                   'reflections', 'landmarks')


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
        # The dial cannot be wider than the photo: below the frozen 200 px dial-radius reliability floor
        # (AlignmentDecision.MIN_DIAL_RADIUS_PX, docs/CALIBRATION_BASELINE_2026-10-20.md) whatever is in it.
        if min(metrics['width'], metrics['height']) < 2 * MIN_DIAL_RADIUS_PX:
            reasons.append('fail_original_resolution')
        if purpose != 'full_watch':
            reasons.append('inconclusive_partial_marker_use_not_enabled')
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
                        family, purpose, profile, usable, required, metrics)


def _finite(value):
    if isinstance(value, float):
        return math.isfinite(value)
    if isinstance(value, dict):
        return all(_finite(v) for v in value.values())
    if isinstance(value, (list, tuple)):
        return all(_finite(v) for v in value)
    return True
