# Calibration platform foundation plan

Status: planning baseline, captured while PR #44 calibrator validation is still running.

## Why this matters

The calibration layer is foundational to Watch Align. The goal is not merely to make the existing 124060 calibrator work for the remaining Submariner 12-series models. The goal is to establish a reusable calibration platform that can support future watch designs and families without repeatedly rebuilding acquisition, provenance, reliability, statistics, validation, and promotion logic.

The statistical core already has strong principles that must be preserved:

- Genuine watches define accepted genuine variation.
- Replica data never moves a genuine-derived boundary; it is stress/separation evidence only.
- Each physical watch is the independent unit; multiple photos of one watch do not create multiple watches.
- Every reliable genuine photo can contribute to the production envelope because the app judges individual uploaded photos.
- Only obvious photo-level measurement failures within the same physical watch may be excluded.
- Source diversity is enforced on acquired genuine physical watches, not raw image count or discovery hits.
- Development/validation/holdout remain diagnostics for generalisation, not a mechanism for discarding valid genuine observations.

## Architectural direction

The platform should have a stable generic centre:

`model/family config -> discovery/acquisition adapter -> measurement adapter -> generic calibration engine -> optional family comparison -> explicit production promotion`

The generic calibration engine must not know whether it is processing a Submariner, GMT, Daytona, Omega, Tudor, or another watch. Design-specific knowledge belongs in the measurement adapter and model/family configuration.

## Foundation hardening before adding 126610

### 1. Separate measurement from judgement

A new model must be measurable before it is production-supported.

Calibration must call a measured-only, versioned measurement adapter rather than requiring the model to be enabled in Android production QC.

For every metric, retain the complete measurement state rather than collapsing unreliable observations to a blank value:

`raw value -> reliability state -> reason -> diagnostics -> usable calibrated value`

This is intended to make failures such as the current 3/9 baton issue diagnosable without losing the underlying geometry.

### 2. Introduce a versioned measurement contract

Every measurement adapter should return the same outer contract, including:

- exact model
- family
- expected layout
- observed layout / layout confidence
- photo quality / pose information
- named scalar metrics
- units / semantics
- reliability state per metric
- diagnostic reason per metric
- adapter version
- reliability-policy version

The generic calibrator should not contain watch-specific concepts such as triangle, baton, cyclops, or chronograph subdial logic.

### 3. Add a measurement fingerprint and reproducible run manifest

Every calibration result must be tied to the code and definitions that produced it.

Each run should record at least:

- repository commit SHA
- exact requested model
- config hash and frozen config copy
- family config hash where applicable
- measurement-adapter id/version
- reliability-policy version
- metric-definition version
- calibration-engine version
- acquisition-adapter version
- source manifest / discovered candidate provenance
- detector or measurement fingerprint

If the measurement fingerprint changes, existing calibration should be explicitly marked as requiring revalidation rather than silently reused.

### 4. Enforce a strict model/layout contract

The model config must be more than descriptive metadata.

Examples:

- 124060 expects no date and a baton at 3.
- 126610LN/LV expect a date at 3 and no baton at 3.
- Future families can define their own layout contract.

If the claimed model and observed layout conflict, quarantine the image rather than allowing it to shape calibration.

The first model-identity safeguards should require consistency between:

- CLI requested model
- config model
- discovered candidate model
- acquired image model
- measurement output model

No implicit fallback to another model is permitted.

### 5. Split family configuration from model configuration

Avoid duplicating a large monolithic model config for every reference.

Target structure:

- family config: shared acquisition strategy, measurement adapter, shared metric definitions, family-level rules
- model config: exact reference, expected layout, exact-source overrides, model-specific metrics or exclusions

Example:

- `families/submariner_12.json`
- `models/124060.json`
- `models/126610LN.json`
- `models/126610LV.json`

Family config must not force shared calibration. It only expresses shared architecture.

### 6. Strengthen independence and duplicate handling

Exact SHA duplicate rejection should remain, but it is not enough because the same photo may be resized, recompressed, or rehosted.

Add perceptual duplicate clustering so re-encoded versions of one photograph cannot create artificial evidence or source diversity.

Do not delete source evidence. Record duplicate-cluster identity and exclude duplicate renditions from independent evidence counts.

### 7. Separate calibration from production promotion

A successful calibration run should produce a candidate calibration package, not automatically become production QC.

Production promotion should be a separate explicit step requiring evidence such as:

- genuine envelope complete enough
- source diversity satisfied
- measurement reliability acceptable
- expected/observed layout validated
- known genuine regression fixtures clear
- replica utility/separation reported
- detector fingerprint current
- product-owner acceptance

This creates an auditable promotion history for each model/family.

## Family calibration rule

Never begin by pooling exact references.

For related models:

1. Calibrate each exact reference independently.
2. Compare genuine distributions metric by metric.
3. For each metric report:
   - exact-model envelopes
   - proposed family-union envelope
   - additional width introduced by sharing
   - source/watch counts
   - pose/repeatability behaviour
   - replica utility before and after sharing
4. Share a family envelope only when evidence supports it.
5. If sharing makes a metric too broad to be useful, retain model-specific calibration or suppress the metric.

For Submariner 12-series this means separate 124060, 126610LN and 126610LV evidence first. The 3-to-9 baton metric is No-Date-only. Date-side metrics are Date-only. Other metrics may become family-shared only after comparison proves compatibility.

## Current known 124060 reliability issue

The current 3/9 problem should be fixed before expanded corpus work.

The analyser treats resize repeatability as diagnostic, but the calibration pair metric currently promotes the strict individual baton repeatability test into a hard measurement gate. This can make genuine watches show `reading not steady enough` even when both batons were found.

Do not simply remove the gate and reuse the old calibration limits. If the production measurement route changes, recalibrate the affected relational metrics with the same reliability route that production will use.

Preferred direction:

- judge relational metrics using pair-level repeatability of the actual relational quantity
- do not gate a relational measurement solely on unrelated one-pixel individual-edge movement
- rerun genuine calibration after changing the route
- keep hand/obscuration/wrong-place/outline-confidence gates intact

## Planned sequence

1. Let the current PR #44 run finish unchanged and preserve it as the baseline.
2. Inspect the run and confirm 124060 foundation behaviour is intact.
3. Add strict requested-model/config-model/acquired-model/output-model validation.
4. Add config validation and a reproducible run manifest / measurement fingerprint.
5. Refactor the measurement path into a proper measured-only adapter contract while proving 124060 output equivalence.
6. Generalise acquisition so the central calibrator no longer assumes `SUBMARINER_12`.
7. Fix the 124060 baton relational reliability path and rerun its calibration.
8. Add measured-only 126610LN and 126610LV adapters/configs without enabling Android production judgement.
9. Gather source-diverse genuine and replica stress evidence for both Date references.
10. Produce a 12-series family compatibility report metric by metric.
11. Adopt shared family envelopes only where supported; keep model-specific or unavailable checks elsewhere.
12. Only then generalise the Android Submariner production QC path.

## Acceptance criteria for the platform foundation

Before using the platform as the basis for future watch families, it should demonstrate all of the following:

- A new exact model cannot silently route through another model.
- A claimed model/layout conflict is quarantined.
- Measurements can run before production QC is enabled.
- Raw measurement and reliability diagnostics are preserved.
- Adding a new family does not require editing the generic statistical engine.
- Config/model/measurement versions are frozen into every calibration artifact.
- Detector or metric-definition changes invalidate/revalidate affected calibration explicitly.
- Physical-watch identity and source provenance survive every stage.
- Exact and perceptual duplicate evidence cannot inflate independence.
- Replica data cannot move genuine-derived boundaries.
- Family pooling is a separate evidence-based decision rather than an assumption.
- Production promotion is explicit and auditable.
- Existing 124060 calibration can be reproduced or differences can be explained by an intentional, evidenced measurement-route change.

## Scope guardrail

This foundation work exists to support the product roadmap, beginning with the remaining Submariner 12-series references. It should remain bounded and testable. Avoid open-ended framework work that does not directly improve calibration correctness, reproducibility, family expansion, or production safety.
