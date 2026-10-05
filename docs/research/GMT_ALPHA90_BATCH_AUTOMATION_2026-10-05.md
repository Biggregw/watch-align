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

## What this proves

The automation itself is now viable:

1. The exact frozen Alpha90 path can be run unattended on many images.
2. Accepted overlays can be exported for review without manual phone screenshots.
3. Rejected images retain the exact Alpha90 reason.
4. Per-hour candidate/overlay crop pairs make the proposed blink review practical.
5. The strong saved QC-review prompt can be applied consistently after the deterministic overlay is produced.
6. The harness does not need to make an automated RL/GL verdict yet.

## What it does not prove

The six fallback fixtures are only an automation smoke test. They do not replace the locked Alpha90 validation queue and must not be counted as completion of the frozen 5 RL + 5 GL round unless a fixture is independently promoted under the validation protocol.

The direct Reddit acquisition issue must also not be fixed by changing Alpha90. Preferred next options are:

1. supply stable direct image URLs for the locked cases;
2. save the already-used manual validation images as a separate provenance-controlled test bundle where permitted;
3. use other accessible independently documented RL/GL cases selected before Alpha90 review.

## Next implementation step

Keep frozen Alpha90 production code unchanged. Use this harness for the remainder of validation once stable source images are available. After the frozen round, the same review-pack structure can become the prototype for product close-ups and blink inspection, followed later by signed edge-normal residual measurements.
