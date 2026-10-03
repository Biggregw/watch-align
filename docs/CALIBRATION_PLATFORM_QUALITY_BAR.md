# Calibration platform quality bar

This document supplements `CALIBRATION_PLATFORM_FOUNDATION_PLAN.md` and makes the engineering standard explicit.

The platform is not considered foundationally complete merely because it works for the current Submariner implementation. It must be difficult to misuse, easy to audit, and demonstrably reusable for a materially different future watch family.

## Non-negotiable invariants

- No model may silently route through another model.
- No detector, metric-definition, reliability-policy or adapter change may silently reuse an old calibration.
- No unreliable measurement may disappear without a machine-readable reason.
- No source, physical watch or duplicated/re-rendered image may inflate independent evidence.
- No family pooling may happen by assumption.
- No calibration may become production merely because a calibration run completed successfully.
- Every promoted calibration must be reproducible from a frozen evidence snapshot.
- Every result must be traceable to source provenance, physical-watch identity, model/family config, code version and measurement contract version.
- No important evidence or judgement rule may depend on an undocumented default.

## Adversarial validation

The foundation test suite must deliberately attempt to break the architecture. At minimum it should cover:

- requested model disagrees with config model
- config filename disagrees with config model
- acquired row contains the wrong model
- measurement adapter returns the wrong model or family
- unknown or malformed metric type/sidedness
- duplicate metric ids
- unsupported adapter id/version
- impossible or contradictory layout declaration
- claimed model conflicts with observed layout
- adapter omits required measurement-contract fields
- adapter returns invalid/non-finite values
- source provenance or physical-watch identity is missing
- same image is re-encoded, resized, cropped or rehosted
- one source attempts to dominate a metric despite whole-dataset diversity
- insufficient per-metric watches/sources/repeatability evidence
- frozen evidence replay differs unexpectedly from the baseline
- detector or metric fingerprint changes while an old calibration is presented
- replica evidence attempts to affect genuine-derived boundaries
- partial acquisition, stale URLs and inaccessible media

Failures should be explicit and local. The platform should fail closed rather than silently infer a fallback.

## Replay quality gate

Run 44 and artifact `11268923648` are the initial 124060 baseline.

After foundation refactoring, the frozen run-44 evidence must be replayed through the new platform. The result must either:

1. reproduce the existing numerical calibration within deliberately defined equivalence tolerances, or
2. differ only where an intentional, documented measurement-route improvement explains the change and is backed by image-level evidence.

A cleaner architecture is not acceptable if it silently changes the scientific meaning of the calibration.

## Cross-family proof

Before calling the generic platform complete, create a synthetic or deliberately simple second family that is materially different from Submariner 12-series.

It should use different layout semantics and different metric names. Adding it must not require changes to the generic statistical engine, evidence-snapshot machinery, promotion logic, source-diversity logic, replay system or run-manifest format.

If the second family requires watch-specific branches inside the generic core, the abstraction is not yet good enough.

## Measurement-contract quality

The measurement contract must be schema validated and versioned. A metric result should carry, at minimum:

- metric id
- semantic/version id
- unit
- applicability state
- raw value when available
- reliability state
- rejection/withholding reason
- repeatability or confidence evidence where applicable
- calibrated-eligibility state
- measurement-adapter version
- reliability-policy version

The generic calibration engine should consume this contract without understanding watch-specific concepts such as baton, triangle, cyclops, date aperture or chronograph subdial.

## Definition of done

The foundation is done only when all of the following are true:

- the run-44 frozen evidence replay passes
- all model/config/adapter boundary checks are enforced
- malformed contracts and unsupported metric types fail before calibration
- the full measurement coverage funnel and rejection reasons are preserved
- detector/config/metric changes trigger explicit revalidation
- exact and perceptual duplicate evidence cannot inflate independence
- per-metric evidence sufficiency is explicit and testable
- calibration readiness and QC utility are separate states
- production promotion is an explicit auditable action
- a materially different second family can be added without modifying the generic calibration core

This quality bar is intentionally stricter than the minimum needed to add 126610. The point is to make the calibration platform a durable base for future Watch Align families rather than another family-specific implementation disguised as a framework.
