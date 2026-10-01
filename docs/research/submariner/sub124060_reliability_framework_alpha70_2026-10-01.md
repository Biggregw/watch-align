# 124060 reliability framework reuse, alpha70

Date: 2026-10-01
Branch: `feature/android-sub124060-reliability-framework`
Base: `feature/android-sub124060-qc` at `fb317f877349e2785f0b118da833a0bdc629e5f0`

## Purpose

Reuse the reliability lessons that made the mature GMT path trustworthy without importing GMT model tolerances or replacing the evidence-backed 124060 triangle geometry.

This is still an experimental measurement build. It adds no 124060 QC tolerance, pass/fail verdict, score or authenticity rule.

## Lessons reused

- Re-detecting the same marker is weaker than re-measuring the actual quantity. The 12 rotation, gap and centring are now measured at 100%, 94% and 88%.
- Fine 12 measurements are reported only when their resize movement is about one source pixel or less. This is a measurement-quality gate, not a watch tolerance.
- `GmtSixLandmarkAnalyzer.measureStability` now runs on the 3/6/9 batons.
- `GmtRoundMarkerAnalyzer.measureStability` now runs on all eight round markers.
- A round marker that changes between lume and surround edge under resize keeps its centre measurement but loses the like-for-like size comparison.
- `GmtMarkerPose` is reused as a round-marker-layout pose diagnostic.
- The existing 124060 radial rotation remains primary, while the local symmetric-tick chord and triangle base-edge angles are retained in the report as independent diagnostics.

## Deliberate divergences from GMT

- The GMT 12 triangle detector and its 44.3 degree apex prior are not used.
- The GMT 59/01 chord does not replace the 124060 radial rotation reference because the 124060 development study found the radial reference more repeatable.
- The GMT 5 degree near-frontal pose threshold is not applied to the 124060. Marker pose is diagnostic only.
- GMT baton/round QC thresholds are not imported. Their resize measurements are collected and shown diagnostically, but do not change an otherwise stable Sub marker status.
- The strict one-pixel 12 repeatability gate is retained because B2 directly showed that unstable numeric 12 readings can survive same-outline detection and would otherwise be presented as precise measurements.

## Development-only check

A bounded run used 39 existing development photos from 13 physical 124060 watches. Validation and holdout were not acquired or inspected.

Among 25 photos where the triangle was large enough, the same outline repeated and the dial fit was reproducible:

- rotation repeated within about one pixel on 21/25;
- gap repeated within about one pixel on 18/25;
- centring repeated within about one pixel on 21/25;
- median pixel movement was about 0.32 px rotation, 0.41 px gap and 0.34 px centring;
- worst observed movement was 1.51 px rotation-tip travel, 4.07 px gap and 1.36 px centring.

The intermediate experiment also showed why marker repeatability must remain diagnostic until model-specific marker tolerances exist. A strict one-pixel gate would have demoted 44 of 83 otherwise stable/found baton readings and 60 of 232 otherwise stable/found round-marker readings. That is not justified by a 124060 calibration study, so the final alpha70 implementation records those resize diagnostics without changing marker status. Physical-edge identity still suppresses round-marker size comparison because comparing different concentric edges is not like-for-like.

## Tests and build

The Android unit-test suite passes with the reuse framework. The alpha70 CI build also validates the exact-model genuine fixtures, builds the standalone APK and verifies its signing certificate.

## New reusable lesson

Reuse a mature confidence mechanism at the same semantic level at which it was proved. Measurement repeatability can be generic; a rule that turns repeatability into a user-facing QC/status decision may depend on model-specific tolerances. When that calibration is absent, collect the diagnostic first rather than silently importing another family's decision boundary.
