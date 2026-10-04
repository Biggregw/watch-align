# Watch Align handoff — reset baseline

Updated: 2026-10-04

This is the active handoff. Older handoff/roadmap language in historical research notes is superseded.

## What the product is

Watch Align analyses an uploaded dealer/QC watch photo and reports measurable deviations from a proven-genuine visual reference.

It is **not** an authenticity classifier. A replica can legitimately return **no detectable deviation in the assessed features**.

Read before substantial work:

1. `docs/PRODUCT_SCOPE.md`
2. `docs/CALIBRATION_PROTOCOL.md`
3. `AGENTS.md`
4. `docs/architecture/QC_PRINCIPLES.md`

## Current repository baseline

`main` is the restart baseline.

The existing GMT implementation is the known-working control. Do not change its production behaviour while developing the new calibration protocol.

The 124060 code and research remain useful evidence, but Submariner expansion is paused until the calibration protocol proves itself on GMT.

All previously open legacy/research/calibration-stack pull requests were closed during the 2026-10-04 reset. They are history only and must not be resumed merely because they exist.

## Active direction

The research order is now:

**best genuine nominal master -> independent genuine variation -> photo/detector uncertainty -> frozen genuine reference -> real QC validation -> implementation choice**

The immediate experiment is deliberately small: recalibrate **one simple, already-working GMT feature** using existing genuine evidence and compare the result with the established GMT result.

Do not source new images unless the existing project evidence cannot answer a specific question.

## Perspective rule

Do not automatically widen genuine tolerances because an oblique genuine photo produces an outlying value.

Check related/opposing marker residuals first. A coherent pattern such as one side expanding while the opposite side compresses may identify perspective contamination without explicitly solving camera pose.

Where validated, use the relationship to correct, pair or exclude the affected measurement. If the relation is not validated, mark the image/metric unassessable rather than guessing.

## Golden calibration prompt

`docs/CALIBRATION_PROTOCOL.md` is the governing reusable research protocol.

It is improved by using known results as controls:

- measure independently;
- compare afterwards;
- diagnose errors;
- propose a protocol change;
- validate that change on held-out images;
- only then promote a new version.

Every run ends with `LESSONS LEARNED`, but a run must **not** automatically rewrite the governing protocol. This avoids prompt drift and overfitting.

## Validation after genuine calibration

After a feature's genuine envelope is frozen, validate it against independent RepTimeQC examples with known visible defects and accepted/GL controls.

The purpose is to answer: **does the frozen genuine-reference comparison find the same specimen-specific issue?**

Replica data never defines the genuine range.

## Implementation decision

Each feature may end up as:

- deterministic code;
- vision AI;
- hybrid code + AI;
- withheld because it is not reliable enough.

Let validation decide. Do not force every check into OpenCV and do not default every check to AI.

## Existing GMT product control

The Android GMT path already contains mature marker detection, confidence gating, resize-repeatability checks, summaries and measured overlays. Preserve it as the control while research is offline.

The first calibration experiment should preferably use a simple feature with an established result, then compare the new prompt-derived measurement quantitatively with the existing calibration.

If the new protocol cannot reproduce the known GMT answer, improve the protocol before touching production code.

## Submariner status

The 124060 work demonstrated useful genuine geometry, repeatability lessons and the importance of source/photo effects. It also showed that allowing every genuine-photo extreme directly into an envelope can make the envelope partly a **camera-angle/photo-style tolerance**.

That work is preserved but paused. When Submariner resumes, it should use the proven calibration protocol rather than restarting the old broad calibrator/platform programme.

## Build notes

Supported app: `android/`.

Use JDK 17 and the committed Gradle wrapper.

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Do not run CI or build an APK to answer an offline research question. Build only when an approved production change needs validation.

## Do not restart without evidence

The following are not active programmes:

- measurement-contract / coverage-funnel migration;
- broad Submariner-family harvesting;
- projective/homography refinement as a goal in itself;
- universal genuine-vs-replica separation;
- large corpus growth without a named evidence gap.

Historical documents under `docs/research/` are evidence, not instructions.

## Next action

Run **Calibration Protocol v0.1** on one known GMT feature using existing genuine images, compare it with the established GMT calibration, record `LESSONS LEARNED`, and revise the protocol only if held-out evidence shows the revision is better.
