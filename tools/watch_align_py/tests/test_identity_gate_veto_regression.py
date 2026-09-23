"""Regression lock for pipeline.py's identity-veto decision functions
(identity_verification_required / primary_vetoed / final_acceptance).

This file exists because of the research-only bypass added in
tools/research/run_replica_control_set.py (see research_identity_bypass_eligible there
and test_research_identity_bypass.py): that bypass acts entirely OUTSIDE this module, on
an already-computed Result, and must never require or cause any change to these
functions. These tests pin the current, unmodified behaviour with synthetic inputs (no
image needed -- they are pure functions of primitives) so any future change to this
production-mirroring logic (identity_gate.py is a port of MinuteTrackIdentityGate.java)
is caught as an explicit, reviewed decision rather than an accidental side effect.

Run from tools/watch_align_py/:
    pytest tests/
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pipeline
import identity_gate

SUSPICIOUS = pipeline.SUSPICION_CENTER_DISPLACEMENT_FRACTION + 0.01
NOT_SUSPICIOUS = pipeline.SUSPICION_CENTER_DISPLACEMENT_FRACTION - 0.01


def test_identity_not_required_when_automatic_acceptance_failed():
    """An already-rejected primary never needs identity evidence, whatever the centre
    displacement -- rescue logic gets to run instead (see pipeline.py's own docstring)."""
    assert pipeline.identity_verification_required(False, SUSPICIOUS) is False
    assert pipeline.identity_verification_required(False, NOT_SUSPICIOUS) is False


def test_identity_not_required_when_centre_displacement_is_small():
    assert pipeline.identity_verification_required(True, NOT_SUSPICIOUS) is False


def test_identity_required_when_accepted_and_centre_displacement_is_large():
    assert pipeline.identity_verification_required(True, SUSPICIOUS) is True


def test_primary_never_vetoed_when_identity_not_required():
    for verdict in (identity_gate.Verdict.PASS, identity_gate.Verdict.AMBIGUOUS,
                     identity_gate.Verdict.FAIL, None):
        assert pipeline.primary_vetoed(True, NOT_SUSPICIOUS, verdict) is False
        assert pipeline.primary_vetoed(False, SUSPICIOUS, verdict) is False


def test_primary_vetoed_requires_pass_verdict_when_identity_required():
    assert pipeline.primary_vetoed(True, SUSPICIOUS, identity_gate.Verdict.PASS) is False
    assert pipeline.primary_vetoed(True, SUSPICIOUS, identity_gate.Verdict.AMBIGUOUS) is True
    assert pipeline.primary_vetoed(True, SUSPICIOUS, identity_gate.Verdict.FAIL) is True
    assert pipeline.primary_vetoed(True, SUSPICIOUS, None) is True


def test_final_acceptance_matches_automatic_accepted_and_not_vetoed():
    assert pipeline.final_acceptance(True, NOT_SUSPICIOUS, None) is True
    assert pipeline.final_acceptance(True, SUSPICIOUS, identity_gate.Verdict.PASS) is True
    assert pipeline.final_acceptance(True, SUSPICIOUS, identity_gate.Verdict.FAIL) is False
    assert pipeline.final_acceptance(True, SUSPICIOUS, identity_gate.Verdict.AMBIGUOUS) is False
    assert pipeline.final_acceptance(False, SUSPICIOUS, identity_gate.Verdict.FAIL) is False
