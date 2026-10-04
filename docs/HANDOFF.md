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

Do not source new images unless the existing project evidence cannot answer a specific question.

## Perspective rule

Do not automatically widen genuine tolerances because an oblique genuine photo produces an outlying value.

Perspective must be supported by a coherent pattern across opposing **and neighbouring** markers. One marker looking wrong while its neighbours remain normal is not enough to call perspective and must not be corrected away.

On a date GMT, useful clean pairs include 12↔6, 2↔8 and 4↔10, with 1↔7 and 5↔11 as additional corroboration when unobstructed. Where marker shapes differ, use a common physical/radial definition or compare residuals from each position's nominal value rather than equating incompatible raw metrics.

Where validated, use the relationship to correct, pair or exclude the affected measurement. If the relation is not validated, mark the image/metric unassessable rather than guessing.

## Golden calibration prompt

`docs/CALIBRATION_PROTOCOL.md` is the governing reusable research protocol. Current promoted version: **v0.3**.

It is improved by using known results as controls:

- measure independently;
- compare afterwards;
- diagnose errors;
- propose a protocol change;
- validate that change on held-out/fresh evidence;
- only then promote a new version.

Every run ends with `LESSONS LEARNED`, but a run must **not** automatically rewrite the governing protocol. This avoids prompt drift and overfitting.

## GMT v0.3 control result — completed 2026-10-04

The first restart control has now been run using **existing stored measurements only**. No new image harvesting, CI, APK build or production-code change was used.

Full result: `docs/calibration/GMT_V03_CONTROL_2026-10-04.md`.

Key findings:

- eight independent genuine watches in the stored 2026-09-30 export had stable 12-gap measurements useful for this control;
- their 12-gap median was **0.1037**, range **0.0916–0.1356**;
- centre-based round-marker `inset` is substantially better suited to opposing-pair pose evidence than outer-edge marker `gap`, because it is not dependent on which lume/surround edge was traced;
- greater opposing-pair asymmetry showed a directional tendency toward a smaller apparent 12 gap, supporting the basic perspective hypothesis;
- however, that relationship was **not stable enough across the historical partitions to justify a correction formula**;
- a globally symmetric photo can still have a materially different local 12-gap reading, so global symmetry must not be used to excuse an isolated 12 deviation;
- the existing GMT low-gap attention boundary remains **0.070**. Historical genuine readings down to about 0.081 are not invalidated by this control.

Two candidate master photos also exposed an important limitation: `wos_cpo_40616911` had the lowest partial clean-pair symmetry score (~0.0071) and `bobs_175818` the strongest fully observed four-pair score (~0.0128), yet their production 12 gaps differed materially (0.1011 vs 0.1356). Therefore a globally frontal/symmetric photo alone cannot define the nominal value of a local outer-edge metric.

## Proposed v0.4 lesson — not promoted yet

The v0.3 run proposes, but does not yet promote, these changes:

- use centre-to-track inset (or another centre-based radial measure) as the default opposing-marker pose signal where available;
- for any feature being perspective-corrected, require its **direct opposite measured with the same definition** — for 12 radial gap, measure 12↔6 in common units;
- require at least two clean opposing relationships plus neighbouring regional coherence before applying a perspective correction;
- allow obstructed/low-confidence pairs only as corroboration;
- keep isolated local deviations uncorrected until detector error is ruled out.

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

No production GMT thresholds were changed by the v0.3 control.

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

Run the **direct same-definition 12↔6 radial-gap experiment** using existing GMT evidence before sourcing anything new. Expose/recover the 6-baton gap in the research output and express both 12 and 6 clearances in one common normalization (preferably dial-radius units). Then test whether their residuals are complementary and whether neighbouring markers show the same regional trend.

Only if that improves genuine repeatability on separate evidence should the proposed v0.4 rules be promoted or production GMT calibration be changed.
