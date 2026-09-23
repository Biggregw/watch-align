"""Research-only identity-veto bypass decision for run_replica_control_set.py.

Kept as a separate, dependency-free module (no cv2/numpy/PIL/pipeline import) so the
decision logic is testable in any environment without needing the frozen Stage-3
Python measurement implementation (tools/watch_align_py/gmt_proportional_features.py,
only available after checking out origin/experiment/gmt-proportional-geometry-v1 -- see
run_replica_control_set.py's own module docstring).

See run_replica_control_set.py's module docstring and
docs/research/gmt-genuine-baseline-integration-validation-2026-09-23-identity-bypass.md
for the full rationale. In short: a control explicitly marked provenance_verified in the
manifest may still be measured when the ONLY reason the frozen pipeline did not accept
its pose is the independent identity gate. This never modifies, weakens, or reimplements
identity_gate.py or pipeline.py, never applies to Android production capture, is never
granted globally, and never depends on a control's defect label.
"""
from __future__ import annotations


def is_provenance_verified(ctrl: dict) -> bool:
    return (ctrl.get("provenance_verified") or "").strip().lower() in ("1", "true", "yes")


def research_identity_bypass_eligible(automatic_accepted: bool, vetoed: bool,
                                       provenance_verified: bool) -> bool:
    """Whether THIS RESEARCH RUNNER may measure a control despite pipeline.build() not
    accepting its pose, because the only reason it was not accepted is the identity gate.

    This function is deliberately pure and label-blind: it takes no control_id, no defect
    label, and no image data, and it never calls into identity_gate.py or pipeline.py --
    it only interprets three already-computed booleans from a Result that has already been
    built by the unmodified frozen pipeline.

    - ``automatic_accepted`` is True only when every actual geometric pose gate already
      passed on its own frozen terms (tilt/top-phase/minute-track holdout -- see
      pipeline.automatic_acceptance). If it is False, this always returns False: a fixture
      that fails real pose validation is never rescued by this mechanism, regardless of
      ``provenance_verified``. (When automatic_accepted is False, pipeline.py's own
      identity_verification_required never requires -- and primary_vetoed never sets --
      vetoed True either, so a caller cannot accidentally satisfy this function's condition
      by miscomputing vetoed for a real pose failure.)
    - ``vetoed`` True together with ``automatic_accepted`` True fully explains why
      ``accepted`` is False (pipeline.py: ``accepted = automatic_accepted and not vetoed``)
      -- i.e. the identity gate is the *only* reason this pose was not accepted.
    - ``provenance_verified`` is an explicit, per-control manifest declaration (see
      ``is_provenance_verified``) that a human has committed this exact image to the
      repository and vouches for its provenance. It is never inferred from a defect label
      or control_id, and defaults to False for every control that does not explicitly set it
      (including every Reddit-sourced control, whose image bytes this project never
      controls or commits).

    This never disables, weakens, or reimplements the identity gate itself, is never
    global, and has no effect on Android production capture (that code path never calls
    this research-only script).
    """
    return automatic_accepted and vetoed and provenance_verified
