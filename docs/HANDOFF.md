# Watch Align handoff - reset baseline

Updated: 2026-10-05

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

### Active GMT perspective-overlay proof

There is now a bounded visual proof on branch `feature/android-gmt-perspective-overlay-poc` testing a stricter architecture:

**fixed genuine master -> minute-track-only camera pose -> one perspective warp -> visual mismatch remains visible**

The governing architecture checkpoint is:

`docs/research/GMT_FIXED_MASTER_PERSPECTIVE_OVERLAY_2026-10-05.md`

The active validation record is:

`docs/research/GMT_ALPHA90_VALIDATION_2026-10-05.md`

The running observed-results log is:

`docs/research/GMT_ALPHA90_VALIDATION_RUNNING_RESULTS_2026-10-05.md`

The consolidated post-validation product and implementation plan is:

`docs/research/GMT_POST_ALPHA90_IMPLEMENTATION_PLAN_2026-10-05.md`

Alpha90 (`1.3.0-alpha90-bezel-ray-proof-arm64`) is frozen for the current validation round at source commit `f66acee665a5afb4450fa08f61396c344be43627`. Immutable validation branch: `freeze/alpha90-validation`.

Do not change the master geometry, minute-pair pose solver, thresholds, rendering or bezel rays while this validation round is underway. Record failures first, then review the complete validation set before changing code.

Initial real-QC results are provisional but informative:

- clear RL Bruce Wayne control: known 6/12 mismatch remains visible -> `PASS-RL`;
- borderline/acceptable VSF Pepsi: poor photo correctly refused, clearer photo shows only small residuals -> `UNASSESSABLE` then `PASS-GL`;
- clean Clean Pepsi V3 control: close agreement across hour markers and minute track -> `PASS-GL`;
- additional canted-6 RL controls include one strong and one weak/visualisation-limited `PASS-RL`;
- one repeated photo/edge-gate case remains `UNASSESSABLE` because the minute-derived fit failed the independent physical-edge plausibility check;
- RL-4 looked nearly coincident in a static screenshot and was provisionally logged `FAIL-FOLLOW`, but the user could clearly see apparent movement when manually toggling the overlay on/off, so this case must be re-reviewed after the frozen round rather than treated as a confirmed architecture failure.

Non-negotiable rule: the applied 12 triangle, round markers, 6/9 batons, date, text, hands and bezel may not steer the pose or post-fit alignment. They are inspection targets only. If the minute evidence is insufficient, reject the photo rather than fit the overlay to judged features.

### Post-Alpha90 product direction already agreed in principle

Do not implement this until the frozen validation round has been reviewed, but preserve the direction:

- keep the main full-watch overlay as the overall perspective/coherence view;
- add a `Closer inspection` section below it for borderline/suspicious features using enlarged crops of the exact same fixed projection, never a local re-fit;
- use blink/toggle inspection because small residuals can appear as a clear edge jump/movement even when a still image looks nearly coincident;
- likely default UX is a short 3-4-cycle blink on a suspicious close-up, then stop with overlay on, with `Blink overlay` / `Stop blinking` available; the full-watch image remains static by default;
- later quantify the same movement using signed edge-normal residuals after pose is frozen, so translation and rotation can be measured without moving the master;
- keep early residual output measured/descriptive rather than automatic RL/GL verdicts until genuine variation and detector uncertainty are calibrated;
- extend the fixed genuine master later with independent dial-text/coronet layers for placement QC such as floating `m`, tilted coronet, baseline/position and group spacing;
- text/coronet remain QC targets only and may never help establish pose;
- treat whole-dial-to-case/rehaut alignment, bezel alignment and date/cyclops as distinct defect classes requiring their own validated references rather than forcing the minute-track method to solve every problem;
- Submariner remains paused until the GMT architecture and measurement sequence are proven.

The implementation order, decision gates and guardrails for these ideas are defined in `docs/research/GMT_POST_ALPHA90_IMPLEMENTATION_PLAN_2026-10-05.md`.

## Perspective rule

Do not automatically widen genuine tolerances because an oblique genuine photo produces an outlying value.

Perspective must be supported by a coherent pattern across opposing **and neighbouring** markers. One marker looking wrong while its neighbours remain normal is not enough to call perspective and must not be corrected away.

On a date GMT, useful clean pairs include 12<->6, 2<->8 and 4<->10, with 1<->7 and 5<->11 as additional corroboration when unobstructed. Where marker shapes differ, use a common physical/radial definition or compare residuals from each position's nominal value rather than equating incompatible raw metrics.

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

## GMT v0.3 control result - completed 2026-10-04

The first restart control has now been run using **existing stored measurements only**. No new image harvesting, CI, APK build or production-code change was used.

Full result: `docs/calibration/GMT_V03_CONTROL_2026-10-04.md`.

Key findings:

- eight independent genuine watches in the stored 2026-09-30 export had stable 12-gap measurements useful for this control;
- their 12-gap median was **0.1037**, range **0.0916-0.1356**;
- centre-based round-marker `inset` is substantially better suited to opposing-pair pose evidence than outer-edge marker `gap`, because it is not dependent on which lume/surround edge was traced;
- greater opposing-pair asymmetry showed a directional tendency toward a smaller apparent 12 gap, supporting the basic perspective hypothesis;
- however, that relationship was **not stable enough across the historical partitions to justify a correction formula**;
- a globally symmetric photo can still have a materially different local 12-gap reading, so global symmetry must not be used to excuse an isolated 12 deviation;
- the existing GMT low-gap attention boundary remains **0.070**. Historical genuine readings down to about 0.081 are not invalidated by this control.

Two candidate master photos also exposed an important limitation: `wos_cpo_40616911` had the lowest partial clean-pair symmetry score (~0.0071) and `bobs_175818` the strongest fully observed four-pair score (~0.0128), yet their production 12 gaps differed materially (0.1011 vs 0.1356). Therefore a globally frontal/symmetric photo alone cannot define the nominal value of a local outer-edge metric.

## Proposed v0.4 lesson - not promoted yet

The v0.3 run proposes, but does not yet promote, these changes:

- use centre-to-track inset (or another centre-based radial measure) as the default opposing-marker pose signal where available;
- for any feature being perspective-corrected, require its **direct opposite measured with the same definition** - for 12 radial gap, measure 12<->6 in common units;
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

Historical documents under `docs/research/` are evidence, not instructions unless the active handoff explicitly promotes them.

## Next action

Keep Alpha90 frozen. Continue the locked RL and GL/borderline GMT examples through the exact frozen Alpha90 build. Prefer independently documented r/RepTimeQC ground truth and accessible direct Reddit-hosted images. Record each result as `PASS-RL`, `PASS-GL`, `UNASSESSABLE`, `FAIL-FOLLOW` or `FAIL-FALSE`.

For each accepted remaining case, preserve the static screenshot **and** record whether manual overlay toggling makes a local residual easier to see. Also record the likely defect class: local marker, whole dial versus case/rehaut, bezel, text/printing, date/cyclops or photo/edge-gate issue. Do not change rendering or automate blinking yet.

Only after the frozen validation round should the project decide whether the next issue is pose contamination, master geometry, photo-quality gating, visualisation sensitivity, independent dial-to-case reference modelling, bezel parallax/reference modelling, or whether the fixed-master principle is strong enough to progress to close-up/blink UX and quantitative edge-normal residual measurements.

For the separate calibration protocol, retain the pending direct same-definition 12<->6 radial-gap experiment described above. Do not let the visual proof silently promote a measurement tolerance without its own validation.
