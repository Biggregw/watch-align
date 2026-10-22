"""Per-marker calibration eligibility: one status per hour position, bound to the photograph's SHA-256.

Photographic suitability (the photo-level face-on-v3 checks: pose, perspective registration, complete dial and
minute track, resolution, sharpness, exposure, glare) is decided first and is never excused by masking. Only then is
each hour marker given exactly one status:

  ELIGIBLE        a clean, fully trusted reading: it may enter calibration statistics for this marker
  OBSTRUCTED      a hand (the app's own per-marker hand check or hand gate) or an outline under 80 % visible
  UNRELIABLE      withheld by the app for another reason, or an edge warning that shifts readings (rough,
                  reconstructed, low resolution, unrecognised)
  INCONCLUSIVE    untested evidence: 80-90 % of the outline seen, coverage unknown, or no measurement reported
  NOT_APPLICABLE  not a physical hour marker on this layout (the 3 o'clock date window on Date dials)

The rules are the face-on-v3 marker rules (FACE-ON-VALIDATION.md sections 3-4); this module only names them.
A status is derived from recorded evidence alone (status_from_evidence), so a stored mask can be re-derived and
checked: validate() refuses a mask whose statuses, eligible list, binding or digest disagree.
"""
from __future__ import annotations

import hashlib
import json
import math
import re

from .faceon import COVER_FAIL, COVER_PASS, HARMLESS, MIN_COVER, MODELS, REQUIRED, soft_classes

MASK_POLICY_VERSION = 'marker-mask-v1'
PHOTO_POLICY_VERSIONS = frozenset({'face-on-v3'})
ELIGIBLE, OBSTRUCTED, UNRELIABLE, INCONCLUSIVE, NOT_APPLICABLE = (
    'ELIGIBLE', 'OBSTRUCTED', 'UNRELIABLE', 'INCONCLUSIVE', 'NOT_APPLICABLE')
STATUSES = (ELIGIBLE, OBSTRUCTED, UNRELIABLE, INCONCLUSIVE, NOT_APPLICABLE)
HOURS = tuple(range(1, 13))
# At most this many required markers may be excluded (non-ELIGIBLE) for a photograph to enter masked calibration:
# 10 of 12 on No-Date dials, 9 of 11 on Date and GMT dials. Evidence (FACE-ON-VALIDATION.md section 10): with up to two
# markers excluded the remaining eligible readings match full-watch photographs (photo-median Mann-Whitney p >= 0.16);
# with three or more excluded the remaining markers' own readings shift (p = 0.046), so the photograph is not trusted.
MAX_EXCLUDED_MARKERS = 2
# Marker shapes by layout: 12 triangle, 3/6/9 batons (no 3 on Date dials), the rest round (no tilt is measured).
SHAPES = {model: {h: 'triangle' if h == 12 else 'baton' if h in (3, 6, 9) else 'round' for h in req}
          for model, req in REQUIRED.items()}
EVIDENCE_KEYS = ('credible', 'reliable', 'withheld', 'cover', 'partly_hidden', 'hand_confirmed', 'soft')
HAND = re.compile(r'hand', re.I)


class MaskError(ValueError):
    """A missing, malformed, tampered or unbound eligibility mask. Consumers must stop, never fall back."""


def _num(v):
    return v if isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) else None


def marker_evidence(s, hr):
    """The recorded evidence one marker's status is derived from (from one faceon.summarise() record)."""
    cover = (s.get('cover') or {})
    cov = cover.get(hr, cover.get(str(hr)))
    withheld = s.get('withheld') or {}
    why = withheld.get(hr, withheld.get(str(hr)))
    soft = s.get('soft') or {}
    text = soft.get(hr, soft.get(str(hr)))
    present = hr in (s.get('present') or ()) or str(hr) in (s.get('markers') or {}) or hr in (s.get('markers') or {})
    return {'present': bool(present or why is not None or hr in (s.get('credible') or ())),
            'credible': hr in (s.get('credible') or ()),
            'reliable': hr in (s.get('reliable') or ()),
            'withheld': None if why is None else str(why),
            'cover': _num(cov),
            'partly_hidden': hr in (s.get('partly_hidden') or ()),
            'hand_confirmed': hr in (s.get('hand_confirmed') or ()),
            'soft': None if text is None else str(text)}


def status_from_evidence(ev):
    """(status, reasons) for one marker from its recorded evidence; the face-on-v3 marker rules, named."""
    cov = _num(ev.get('cover'))
    if ev.get('credible'):
        obstructed = []
        if ev.get('hand_confirmed'):
            obstructed.append('hand_confirmed_by_app')
        if ev.get('partly_hidden') or cov is not None and cov < MIN_COVER:
            obstructed.append('outline_mostly_reconstructed')
        if cov is not None and cov < COVER_FAIL:
            obstructed.append('outline_cover_below_%.2f' % COVER_FAIL)
        if obstructed:
            return OBSTRUCTED, obstructed
        if not ev.get('reliable'):
            classes = soft_classes(ev.get('soft'))
            if not classes or not classes <= HARMLESS:
                return UNRELIABLE, ['edge_warning_' + c for c in sorted(classes - HARMLESS)] or ['not_reliable_no_warning']
        if cov is None:
            return INCONCLUSIVE, ['outline_cover_unknown']
        if cov < COVER_PASS:
            return INCONCLUSIVE, ['outline_cover_%.2f_%.2f_untested' % (COVER_FAIL, COVER_PASS)]
        return ELIGIBLE, ['reliable' if ev.get('reliable') else 'harmless_reflection_only']
    if ev.get('withheld') is not None:
        if HAND.search(ev['withheld']):
            return OBSTRUCTED, ['withheld_by_app_hand']
        return UNRELIABLE, ['withheld_by_app']
    return INCONCLUSIVE, ['not_measured']


def marker_statuses(s, model):
    """{hour: {status, reasons, shape, evidence}} for all 12 positions of `model` from one summarise() record."""
    if model not in REQUIRED:
        raise MaskError('unsupported layout')
    out = {}
    for hr in HOURS:
        if hr not in REQUIRED[model]:
            out[str(hr)] = {'status': NOT_APPLICABLE, 'reasons': ['date_window_not_an_hour_marker'], 'shape': None,
                            'evidence': None}
            continue
        ev = marker_evidence(s, hr)
        status, reasons = status_from_evidence(ev)
        out[str(hr)] = {'status': status, 'reasons': reasons, 'shape': SHAPES[model][hr], 'evidence': ev}
    return out


def _canonical(mask):
    body = {k: v for k, v in mask.items() if k != 'mask_digest'}
    return json.dumps(body, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def build(sha256, family, statuses, photo_policy_version, profile=''):
    """The SHA-bound, versioned eligibility mask record for one photograph."""
    model = MODELS.get(family)
    if model is None:
        raise MaskError('unsupported family')
    eligible = [h for h in HOURS if statuses[str(h)]['status'] == ELIGIBLE]
    mask = {'schema_version': 1, 'sha256': sha256, 'family': family, 'model': model,
            'photo_policy_version': photo_policy_version, 'mask_policy_version': MASK_POLICY_VERSION,
            'profile': profile, 'required_marker_ids': list(REQUIRED[model]),
            'eligible_marker_ids': eligible, 'full_watch': eligible == list(REQUIRED[model]),
            'markers': statuses}
    mask['mask_digest'] = hashlib.sha256(_canonical(mask)).hexdigest()
    return mask


def validate(mask, sha256=None, max_excluded=MAX_EXCLUDED_MARKERS):
    """Return the mask unchanged if it is complete, bound, internally consistent and re-derivable; else MaskError."""
    if not isinstance(mask, dict) or mask.get('schema_version') != 1:
        raise MaskError('missing or unknown eligibility mask')
    if mask.get('mask_policy_version') != MASK_POLICY_VERSION:
        raise MaskError('unknown mask policy version')
    if mask.get('photo_policy_version') not in PHOTO_POLICY_VERSIONS:
        raise MaskError('unknown photo policy version')
    digest = mask.get('sha256')
    if not isinstance(digest, str) or not re.fullmatch(r'[0-9a-f]{64}', digest):
        raise MaskError('mask not bound to a SHA-256')
    if sha256 is not None and digest != sha256:
        raise MaskError('mask belongs to a different photograph')
    model = mask.get('model')
    if MODELS.get(mask.get('family')) != model or model not in REQUIRED:
        raise MaskError('family and layout disagree')
    if mask.get('required_marker_ids') != list(REQUIRED[model]):
        raise MaskError('required markers do not match the layout')
    markers = mask.get('markers')
    if not isinstance(markers, dict) or set(markers) != {str(h) for h in HOURS}:
        raise MaskError('mask must give a status for every hour position')
    for h in HOURS:
        rec = markers[str(h)]
        if not isinstance(rec, dict) or rec.get('status') not in STATUSES:
            raise MaskError('invalid status at %d' % h)
        if (rec['status'] == NOT_APPLICABLE) != (h not in REQUIRED[model]):
            raise MaskError('NOT_APPLICABLE only where the layout has no hour marker (%d)' % h)
        if h in REQUIRED[model]:
            ev = rec.get('evidence')
            if not isinstance(ev, dict) or set(ev) != set(EVIDENCE_KEYS) | {'present'}:
                raise MaskError('marker %d has no evidence' % h)
            if (rec['status'], rec.get('reasons')) != status_from_evidence(ev):
                raise MaskError('marker %d status does not follow from its evidence' % h)
            if rec.get('shape') != SHAPES[model][h]:
                raise MaskError('marker %d shape does not match the layout' % h)
    eligible = [h for h in HOURS if markers[str(h)]['status'] == ELIGIBLE]
    if mask.get('eligible_marker_ids') != eligible:
        raise MaskError('eligible marker list disagrees with the statuses')
    if mask.get('full_watch') is not (eligible == list(REQUIRED[model])):
        raise MaskError('full-watch flag disagrees with the statuses')
    try:
        canonical = _canonical(mask)
    except ValueError:
        raise MaskError('non-finite value in mask')
    if mask.get('mask_digest') != hashlib.sha256(canonical).hexdigest():
        raise MaskError('mask digest mismatch (edited after it was built)')
    if len(eligible) < min_eligible(model, max_excluded):
        raise MaskError('fewer than %d eligible markers' % min_eligible(model, max_excluded))
    return mask


def min_eligible(model, max_excluded=MAX_EXCLUDED_MARKERS):
    return len(REQUIRED[model]) - max_excluded


def eligible(mask, hour):
    """True only for a marker this (already validated) mask makes ELIGIBLE."""
    return (mask.get('markers') or {}).get(str(hour), {}).get('status') == ELIGIBLE
