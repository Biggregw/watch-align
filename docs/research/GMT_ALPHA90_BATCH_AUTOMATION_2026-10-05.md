# Alpha90 frozen batch automation checkpoint

Updated: 2026-10-05
Branch: `test/alpha90-batch-validation`

## Purpose

Automate repetitive Alpha90 validation without altering the frozen Alpha90 production implementation.

The branch was created from frozen Alpha90 source commit:

`f66acee665a5afb4450fa08f61396c344be43627`

The workflow contains an explicit guard that fails if `android/app/src/main` differs from that frozen source. Test-only code and CI fixture preparation are allowed; the production overlay path is not modified.

## What the harness does

`Alpha90BatchValidationTest` runs candidate images on an Android 15 emulator through the exact package-private `AutomaticDialOverlay.build(Bitmap)` Alpha90 path.

For each image it records:

- `ACCEPTED` or `UNASSESSABLE`;
- exact rejection reason when unassessable;
- detected minute ticks;
- complete opposing pairs;
- inliers;
- fit-before and fit-after residuals;
- dial centre/radius, ellipse ratio and edge RMS when accepted;
- the candidate image;
- the Alpha90 overlay;
- the candidate+overlay composite;
- candidate-only and overlay-on crops for all 12 hour positions.

It also creates an offline `index.html` review page. Each hour crop has `Blink 4x` and `Stop` controls so the apparent movement/edge-flicker effect observed during manual validation can be reviewed consistently.

The saved blind visual-review prompt is copied into every review artifact as `VISION_REVIEW_PROMPT.md`. It is a second-stage review instruction only and never feeds back into Alpha90 pose.

## Locked real-QC queue

The original ten-case RL/GL queue remains recorded in `android/alpha90_validation_cases.tsv`.

GitHub-hosted runners currently receive HTTP 403 from Reddit JSON for those posts. The harness records those source failures rather than silently replacing the locked cases. This is an acquisition limitation, not an Alpha90 result.

Until direct locked-case image URLs are available, the workflow falls back only for an end-to-end automation smoke test to existing, already-proven Imgur-backed 126710BLNR research sources in the repository dataset.

## First successful automated run

GitHub Actions run: `37293485555`

Artifact: `Alpha90-frozen-batch-validation`, artifact id `11337905430`.

Artifact SHA-256:

`c88b00470246e3104d4bff8a0dd8fb1f3a9016a0f8debd6eaea6abc56cc36ffc`

The instrumentation test completed successfully on Android 15 / API 35 x86_64. The x86_64 ABI is added only in the CI working tree; it is not a committed production Alpha90 change.

Six smoke fixtures were processed:

| Case | Class | Result | ticks | complete pairs | inliers | fit before -> after |
|---|---|---|---:|---:|---:|---:|
| SMOKE_BEZEL1 | borderline | ACCEPTED | 60 | 17 | 34 | 3.24024 -> 0.24932 |
| SMOKE_BORDER1 | borderline | ACCEPTED | 60 | 19 | 38 | 3.92129 -> 0.19266 |
| SMOKE_GEN1 | genuine control | UNASSESSABLE | - | - | - | physical-edge guard rejection |
| SMOKE_GEN2 | genuine control | ACCEPTED | 59 | 17 | 34 | 4.32011 -> 0.26504 |
| SMOKE_RL1 | RL-labelled | ACCEPTED | 60 | 20 | 40 | 3.16217 -> 0.19440 |
| SMOKE_TEXT1 | RL-labelled | ACCEPTED | 60 | 19 | 38 | 4.97814 -> 0.21402 |

`SMOKE_GEN1` was rejected with:

`candidate photo not sufficient for fixed-master overlay: minute fit rejected: projected dial no longer matched physical edge`

This is useful evidence that the batch harness preserves Alpha90's fail-closed behaviour. It is not evidence that a genuine watch is defective.

## Review learning from the enlarged automated pool

The larger automated pool exposed two different phenomena that must not be confused.

### 1. Local marker rotation is the desired signal

A known RL example such as `EXT_RL_BLRO_ARF_00` can have a coherent full-dial projection while the real 6 baton is visibly rotated relative to the fixed yellow genuine baton. That is exactly the behaviour the proof is trying to establish. Do not classify that local disagreement as a pose failure merely because the close-up shows rotation.

### 2. Global clock-phase mismatch is a separate proof-build limitation

Other accepted photographs can be rolled substantially in the image while Alpha90's canonical 12 remains near image-up. `EXT_RL_BLRO_ARF_01` is an example in which the full-watch composite clearly shows the projected 12/6 axis at a different global clock phase from the photographed watch.

This is consistent with the frozen proof implementation: canonical 12 is seeded to image-up and the 60-position minute system is cyclically repetitive. Therefore deterministic `ACCEPTED` is not, by itself, proof that absolute 12-o'clock phase is correct on an arbitrarily rolled source photograph.

The visual review prompt now requires a mandatory phase pre-check before any local marker verdict:

- `PHASE OK`;
- `GLOBAL_PHASE_MISMATCH`;
- `PHASE INDETERMINATE`.

A global-phase mismatch invalidates that image for local-marker conclusions. It does not mean the watch has a defect.

A short-lived attempt to turn this into an automatic phase score was deliberately removed because the repetitive 6-degree minute pattern produced ambiguous competing phase scores. No unvalidated automatic phase threshold is part of the harness.

### 3. The original close-up crop logic was misleading under perspective/roll

The first batch harness placed each close-up using an image-space `hour * 30 degrees` direction from the fitted dial centre. That does not necessarily coincide with the location of the already-projected master marker after the perspective transform. It could therefore make a valid full-watch overlay look absurd in a local crop, for example showing a round marker while the expected 6 baton sat to one side.

This was a test-presentation defect, not an Alpha90 production change.

The harness now finds yellow projected-master marker pixels inside the marker annulus and centres the close-up on that already-projected genuine feature. Candidate pixels are never used to choose the crop centre, so a defective candidate marker cannot pull the crop or the master toward itself. Candidate-only and overlay-on views use the identical crop rectangle.

Current test-only crop-fix commit:

`f86c3685349365af21a92e5fe84583a3863e3f18`

The corresponding CI run is intended only to regenerate a trustworthy review pack. Alpha90 production source remains frozen and unchanged.

## What this proves

The automation itself is now viable:

1. The exact frozen Alpha90 path can be run unattended on many images.
2. Accepted overlays can be exported for review without manual phone screenshots.
3. Rejected images retain the exact Alpha90 reason.
4. Per-hour candidate/overlay crop pairs make the proposed blink review practical.
5. The strong saved QC-review prompt can be applied consistently after the deterministic overlay is produced.
6. The harness does not need to make an automated RL/GL verdict yet.

## What it does not prove

The fallback fixtures and expanded curated pool do not replace the locked Alpha90 validation queue and must not be counted as completion of the frozen 5 RL + 5 GL round unless a fixture is independently promoted under the validation protocol.

`ACCEPTED` means Alpha90 produced a deterministic overlay. It does not automatically mean the absolute clock phase is suitable for local-marker QC. Full-watch phase coherence must be checked first in this proof build.

The direct Reddit acquisition issue must also not be fixed by changing Alpha90. Preferred next options are:

1. supply stable direct image URLs for the locked cases;
2. save the already-used manual validation images as a separate provenance-controlled test bundle where permitted;
3. use other accessible independently documented RL/GL cases selected before Alpha90 review.

## Next implementation step

Keep frozen Alpha90 production code unchanged. Regenerate the enlarged review pack with master-centred close-ups, perform the mandatory full-watch phase pre-check, and only then compare local marker residuals between genuine, GL, borderline and independently known RL controls. After the frozen round, the same review-pack structure can become the prototype for product close-ups and blink inspection, followed later by signed edge-normal residual measurements.