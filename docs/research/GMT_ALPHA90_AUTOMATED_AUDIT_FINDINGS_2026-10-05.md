# GMT Alpha90 automated audit findings

Updated: 2026-10-05

Frozen production source: `f66acee665a5afb4450fa08f61396c344be43627`
Automation branch: `test/alpha90-batch-validation`

## Scope

This note records findings from the expanded unattended Alpha90 test run. It does not authorise any change to frozen Alpha90 production code.

The purpose remains unchanged: prove whether one fixed genuine GMT dial master, projected from independent pose evidence, exposes local candidate defects without adapting to them.

## Expanded automated run

GitHub Actions run `37309238137` completed successfully from branch commit `5dfaf1f2047e80744ac03b18c62e1cd1c185ee59`.

The workflow guard verified `android/app/src/main` byte-for-byte unchanged from frozen Alpha90 source `f66acee665a5afb4450fa08f61396c344be43627`.

Artifact:

- name: `Alpha90-frozen-batch-validation`
- artifact id: `11345740072`
- SHA-256: `8cdb4617f55c5e9826dd6ded59b838d9e9b831e6363188a086be769629d1d66b`

The downloaded review pack contained 56 runtime candidate images. Deterministic Alpha90 outcomes were:

- 26 `ACCEPTED`
- 30 `UNASSESSABLE`

Those counts are pose/photo outcomes only. They are not watch QC verdicts.

## Critical finding: absolute clock phase is not solved

Reviewing the full generated composites exposed an important limitation that was easy to miss when manual examples were approximately upright.

Several materially rotated candidate photos were deterministically `ACCEPTED`, but the fixed master remained with canonical 12 pointing image-up rather than following the physical watch's nominal 12 direction. In those cases the projected marker grid was globally phase-mismatched even though the minute-pair fit and physical-edge guard passed.

A clear example is `EXT_RL_BLRO_ARF_01`. The watch is materially rotated within the photograph. Alpha90 accepted the image, but the projected 12/6 axis stayed image-up, so the local marker overlay was not valid for QC inspection.

This is not the fixed master following a defect. It is a separate missing degree of freedom: absolute clock phase / in-plane watch orientation.

### Why this happens

The frozen source explicitly states that canonical 12 is locked to image-up.

A 60-position equally spaced minute system is cyclically symmetric. Without an independent phase cue, the minute track can constrain projective shape but cannot identify which one of the otherwise repeating minute positions is absolute 12 o'clock on a freely rotated photograph.

Even if hour-position minute stubs are distinguishable from minor ticks, that only reduces the symmetry. It does not by itself identify which of the 12 hour directions is specifically 12.

Therefore a minute-only system cannot provide absolute clock phase without an additional independent cue.

## Consequence for frozen Alpha90 validation

From this point, deterministic `ACCEPTED` must be interpreted as:

`minute/edge perspective solution accepted`

not:

`projected master is globally phase-valid for local-marker QC`.

Before reviewing 12, 6, 9 or round-marker residuals, a second-stage reviewer must first check whether the projected nominal 12/6 direction is coherent with the watch's nominal 12/6 direction.

Use these review labels:

- `PHASE OK`: local marker inspection may continue.
- `GLOBAL_PHASE_MISMATCH`: do not judge local markers from this overlay.
- `PHASE INDETERMINATE`: insufficient evidence to continue local review.

Do not retune or alter frozen Alpha90 during the validation round. This finding is exactly the kind of limitation the frozen audit is intended to reveal.

## Preferred post-frozen solution

Preserve the separation between pose evidence and QC targets.

The preferred architecture is:

1. physical dial boundary remains a coarse seed/guard;
2. minute-track/opposing-pair geometry supplies projective shape;
3. a separate coarse non-QC phase anchor identifies nominal 12 orientation;
4. freeze the transform;
5. inspect all QC targets without feeding them back into pose.

Preferred independent phase anchors to investigate, in order:

1. **Case winding-crown direction.** On standard 126710 BLNR/BLRO/GRNR the case crown identifies nominal 3 o'clock, from which nominal 12 is determined. This is outside the dial-marker geometry being judged.
2. **Model-specific case orientation.** Sprite/VTNR must use its left-crown architecture explicitly rather than silently inheriting a right-crown assumption.
3. **User coarse orientation input as a robust fallback.** A user can indicate which side of the watch is nominal 12 without tracing or fitting the 12 marker itself. This supplies phase only, not fine alignment.

Avoid using the exact candidate 12 marker, 6/9 markers, date window, bezel triangle or dial text as fine phase/alignment anchors because they are QC targets. A coarse cue may be researched separately, but it must not be allowed to absorb the defect being measured.

## External-source provenance audit

The expanded run also exposed a source-integrity issue in the newly gathered external pool.

`EXT_GL_BLRO_VSF` had been labelled as a 126710BLRO Pepsi from Reddit thread `1wfb0l9` with album `qOu9YiZ`, but the fetched album images visibly showed a green/black left-crown Sprite/VTNR. The source is therefore unsuitable as a Pepsi control regardless of the Reddit post text.

That source has been removed/replaced in the manifest rather than relabelled or silently retained.

Replacement clean VSF Pepsi source:

- Reddit thread: `1si7608`
- Imgur album: `1TiZsQ2`
- independently described by multiple reviewers as a straightforward GL with solid index alignment.

An additional VSF Pepsi local-marker control was also added:

- Reddit thread: `1sm98ck`
- Imgur album: `8kAOARd`
- source specifically flags 6-marker misalignment.

The source pool must continue to be visually provenance-checked. A text label or linked post title is not sufficient if the fetched image content disagrees.

## Review-prompt guardrail added

The preserved `VISION_REVIEW_PROMPT.md` now begins with a mandatory global-phase pre-check. It instructs a reviewer to stop local-marker QC when an accepted overlay is globally phase-mismatched.

This prompt remains second-stage review only. It does not influence Alpha90 pose.

## Next test step

Rerun the corrected external pool through exactly the same frozen Alpha90 code.

For accepted images:

1. perform global-phase triage first;
2. exclude `GLOBAL_PHASE_MISMATCH` images from local-marker evidence;
3. on `PHASE OK` images, inspect known RL, GL and genuine controls using the full overlay plus blink close-ups;
4. record whether known local defects remain separate from the fixed genuine master and whether clean/genuine controls acquire false residuals.

Only after that evidence is collected should an Alpha91 phase solution be designed. Do not solve the new phase problem by allowing QC markers to steer the master.
