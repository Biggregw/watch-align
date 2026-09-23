"""Tests for research_identity_bypass.py, run_replica_control_set.py's research-only
identity-veto bypass decision.

These test the bypass DECISION function in isolation -- pure booleans in, pure boolean
out, no image, no pipeline call, no control_id or defect label anywhere in the inputs.
research_identity_bypass.py is deliberately dependency-free (no cv2/numpy/PIL/pipeline
import), so these tests need none of the frozen Stage-3 Python measurement
implementation that run_replica_control_set.py itself requires (see that file's module
docstring) -- they run in any environment. The pipeline/identity-gate behaviour these
booleans are computed FROM is regression-locked separately in
tools/watch_align_py/tests/test_identity_gate_veto_regression.py; these tests only
cover what the research runner decides to do with that outcome.

Run from the repository root:
    pytest tools/research/tests/
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import research_identity_bypass as runner


def test_ordinary_unverified_control_still_vetoed():
    """An ordinary (non-provenance-verified) control whose only rejection reason is the
    identity gate must remain rejected -- the default (provenance_verified=False, the
    value every Reddit-sourced control CSV row has) must never grant the bypass."""
    assert runner.research_identity_bypass_eligible(
        automatic_accepted=True, vetoed=True, provenance_verified=False) is False


def test_provenance_verified_control_bypasses_identity_veto_only():
    """A control explicitly marked provenance_verified may proceed when every actual
    geometric pose gate already passed (automatic_accepted) and the identity gate is the
    only reason it was not accepted (vetoed)."""
    assert runner.research_identity_bypass_eligible(
        automatic_accepted=True, vetoed=True, provenance_verified=True) is True


def test_provenance_verified_control_still_rejected_if_geometric_pose_gates_failed():
    """provenance_verified must never rescue a fixture that fails an actual geometric pose
    gate (tilt/top-phase/minute-track holdout, reflected here by automatic_accepted=False).
    When automatic_accepted is False, pipeline.py's own identity_verification_required
    never requires identity evidence and primary_vetoed never sets vetoed True either, so
    this also covers the realistic (automatic_accepted=False, vetoed=False) combination."""
    assert runner.research_identity_bypass_eligible(
        automatic_accepted=False, vetoed=False, provenance_verified=True) is False
    assert runner.research_identity_bypass_eligible(
        automatic_accepted=False, vetoed=True, provenance_verified=True) is False


def test_provenance_verified_control_not_bypassed_when_not_vetoed():
    """A provenance-verified control that was accepted outright (no veto at all) needs no
    bypass -- the function must not report eligibility for a pose that was never vetoed."""
    assert runner.research_identity_bypass_eligible(
        automatic_accepted=True, vetoed=False, provenance_verified=True) is False


def test_is_provenance_verified_parses_manifest_values():
    assert runner.is_provenance_verified({"provenance_verified": "true"}) is True
    assert runner.is_provenance_verified({"provenance_verified": "TRUE"}) is True
    assert runner.is_provenance_verified({"provenance_verified": "yes"}) is True
    assert runner.is_provenance_verified({"provenance_verified": "1"}) is True
    assert runner.is_provenance_verified({"provenance_verified": ""}) is False
    assert runner.is_provenance_verified({"provenance_verified": "false"}) is False
    assert runner.is_provenance_verified({}) is False


def test_bypass_function_signature_is_label_blind():
    """The bypass decision must be structurally incapable of depending on a defect label
    or control_id -- assert its parameter names are exactly the three documented booleans,
    so a future edit that threads a label/control_id into the decision fails this test."""
    import inspect
    params = list(inspect.signature(runner.research_identity_bypass_eligible).parameters)
    assert params == ["automatic_accepted", "vetoed", "provenance_verified"]
