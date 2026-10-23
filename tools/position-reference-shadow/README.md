# Positional reference shadow integration

Local experimental candidate based on main `eae2d9d629c52734131849b5fdae40ba3c42235c`.
No production classification, measurement, rendering, calibration, UI or CI changes.

## Files and execution point

- `android/app/src/main/java/com/watchalign/mobile/PositionReferenceFit.java`: supplied helper,
  copied byte-for-byte (SHA-256 `362efb26e917f6c40171b9a7d27494e66d2e1d74211528591e4a6f02d68d27ca`).
  Its original “offline helper” provenance comment is retained; the separate adapter now invokes it.
- `PositionReferenceShadow.java` in that directory: read-only, exception-isolated adapter.
- `Alpha99Pipeline.java`: four added lines. An internal `Output.positionReferenceShadow` map and
  the guarded call immediately after `AlignmentDecision.applyClearlyOff`, before technical text
  and evidence rendering. No display or classifier reads this map.
- `android/app/src/test/java/com/watchalign/mobile/PositionReferenceShadowTest.java`: integration tests.
- This directory: supplied standalone test unchanged and both numerical fixtures (only CRLF normalized to LF); desktop replay,
  evidence comparison and local test script. No fixture generation or photo downloads.

## Input contract

`ClearlyOff.analyse` already transfers `MetalFit.Fit.cObs` through `metrics` to each marker.
The adapter copies those two scalar coordinates, in canonical dial radii (x right, y down).
It uses the mean of `ExpectedMarkerOutline.polygon(model.atHour(hour), reference, null)` for the
fixed centre. It does not use `cGen` or `yellow`, which may include an earlier ring adjustment.
This coordinate mapping was checked against PR #69's `MarkerGeometry.fromPipeline` read-only;
no research source was merged or used as the branch base.

`originalAssessable` is exactly `WatchClassification.credible(marker)`: the current production
gate requires a non-withheld state, an empty gate list and a finite original position measurement.
Soft uncertainty, low outline coverage and a confirmed nearby hand do not add new filters.
This deliberately follows current production credibility, even when a finite centre exists on a
marker whose final production state is Can't check. It does not broaden production assessability.
Only outer-metal sources with finite centres and matching model shapes are passed. The helper
excludes triangles and each target from its own fit. Five other contributors are required only
for the shadow reading; this never changes production coverage.

Absent or malformed centres are omitted; existing gated markers with valid centres receive an
unavailable reading. Missing analysis/model, invalid radius, photo-wide failure or a helper
exception produces an empty map. Empty/unavailable always means no extra diagnostic evidence.
The helper returns immutable readings and an unmodifiable map. No arrays from production are
passed to it. The pipeline also catches failures at the adapter call boundary.

The original absolute minute-track evidence remains authoritative. Whole-ring translation,
rotation and scale can disappear in this relative fit and must never be interpreted as a pass.

## Local validation

Use JDK 17 plus the existing desktop harness shims and desktop OpenCV 4.9.0-0,
JUnit 4.13.2 and Hamcrest Core 1.3 jars. The script performs no network requests:

```bash
JAVA_HOME=/workspace/test-deps/jdk17 \
SHADOW_TEST_CP='/workspace/test-deps/*' \
bash tools/position-reference-shadow/test.sh
```

Results on this candidate:

- Helper compiles with `javac --release 8`; 25,236 supplied assertions pass across 232
  Python-oracle cases and 232 archived Java parity cases.
- Changed pipeline and its source dependencies compile with JDK 17 / `--release 17` against
  repository desktop shims. No new runtime dependencies; `Double.isFinite` is supported by
  Android API 24+, below this application's minSdk 28.
- 74 JUnit tests pass: new integration tests plus existing `ClearlyOffTest`,
  `WatchClassificationTest`, `AlignmentDecisionTest`, `MetalFitTest` and `TriangleTopEdgeTest`.
- Both fixture suites also replay through the adapter (464 cases); fixed centres agree with
  current model geometry to 1e-12 R and positional outputs with supplied expectations to 3e-8 % R.
- Deep before/after snapshots compare every production result and marker instance field,
  including raw double bits, gates, reason, source, rotation, top edge and overlay arrays;
  alignment decisions, full-watch classification and technical text also remain identical.
- Synthetic cases cover all three models, radial/sideways displacements, unchanged innocent
  neighbours, existing hand/minute-track gates, missing/nonfinite centres, insufficient
  contributors, soft warnings, whole-ring shifts and forced helper failure (duplicate hour).
- All 12 synthetic close-up/evidence renders are byte-identical with and without the shadow
  call, including a measured calibrated triangle top edge.
- `git diff --check` passes. Existing production classes other than the pipeline are unchanged.

The fixtures contain positions and eligibility flags, not the complete original production
results or photographs. Adapter replay reconstructs production records for isolation checks;
it does not establish historical photo verdict parity. No Android SDK build, device/emulator
run, real-photo end-to-end comparison or phone performance/memory profiling was performed.
Desktop shims are not a substitute for Android device validation. No full APK build was run.

## Publication

Candidate branch: `experiment/position-reference-shadow` (local only).
No push, PR, merge, workflow dispatch, image harvesting or calibration run.
The current build workflow triggers on PRs to main and pushes to main / `feature/android-*`.
Opening a PR would therefore run Android unit tests and an APK build; obtain owner approval
before doing so. No CI configuration was changed.
