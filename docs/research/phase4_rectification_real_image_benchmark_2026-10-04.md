# Phase 4 Experiment 1B — real-image rectification benchmark — 2026-10-04

## Status

Research only. No Android, detector, calibration, verdict, threshold, or production-path changes.

Decision: **NO-GO for adding projective rectification from the currently recoverable one-photo dial/marker cues. Keep the existing affine frame and move to the next Phase 4 research experiment.**

This is the required real-image follow-up to `phase4_rectification_benchmark_2026-10-04.md`.

## Governing question

The controlled synthetic benchmark showed that a full projective correction can remove large geometric error once true tilt reaches roughly 5 degrees or more.

The real-image question was stricter:

> Can a single normal QC-style 124060 image supply projective constraints reliably enough to reproduce a material part of that theoretical gain, while preserving independent dial evidence and falling back safely when evidence is weak?

The answer from the frozen evidence is **no with the currently recoverable cues**.

## Evidence source

Only the already-completed frozen evidence from GitHub Actions run #73 was used. No network acquisition and no new CI run were needed.

Input pool for this benchmark:

- 76 genuine 124060 photos with an existing production marker-layout tilt estimate;
- 58/76 yielded at least seven usable round-marker centres in the independent research detector;
- 57/76 also yielded at least 15 independent minor minute-track ticks for holdout evaluation.

The image pixels came directly from the frozen `evidence_snapshot_v1` objects in the run #73 artifact.

## Why an independent holdout was necessary

A projective transform fitted to hour-marker centres can appear excellent if it is evaluated on the same marker ring used to fit it.

That happened here.

A free homography fitted from real detected round-marker centres reduced leave-out marker-ring positional error dramatically:

- median held-out marker error fell from about `0.0227 R` to `0.00315 R` across the usable images;
- nominal improvement was about 86%.

That initially looked similar to the controlled synthetic result.

However, this was **not** sufficient evidence of a better rectification. The same transform was therefore evaluated on a geometrically independent structure: the 48 minor minute-track ticks, which were not used to fit the homography.

That independent test exposed the apparent gain as mostly fitting the marker observations rather than recovering the true dial plane.

## Research detector and baseline

The benchmark deliberately used a separate offline detector rather than changing or instrumenting Android production code.

Per image it:

1. detected and robustly fitted the dark-dial / bright-rehaut ellipse;
2. reproduced the current affine idea by circularising that ellipse while preserving image orientation;
3. detected the round hour-marker bright regions in the corrected frame;
4. fitted their 30-degree structural layout and removed watch roll;
5. independently detected minor minute-track ticks in the outer dial annulus.

The current Android artifact does not expose raw production marker coordinates, so this prototype is not claimed to be bit-identical to the production detector. Its purpose was to answer whether the actual image evidence contains a stable projective signal strong enough to justify a production implementation experiment.

## Variant A — free marker homography

A full homography was fitted from the detected round-marker centres to the canonical round-marker positions.

### Marker-ring cross-validation

This looked very strong:

- 58 images supplied at least seven marker centres;
- median leave-out marker error improved by about 86% overall;
- even with a strict outer-circle preservation gate, the apparent marker-ring improvement remained large.

### Independent minute-track result

On the 57 images with sufficient independent minor ticks:

- baseline minor-tick angular RMS median: **1.478 deg**;
- projective candidate median: **1.501 deg**;
- candidate improved only 27/57 images (**47.4%**);
- paired median relative change: **0.6% worse**.

The failure became clearer as tilt increased:

- at `tilt >= 5 deg`: 16/37 wins, median relative result about **3.6% worse**;
- at `tilt >= 8 deg`: 4/16 wins, median angular RMS about **1.847 -> 2.048 deg**.

The high-tilt images are exactly where the synthetic benchmark predicted the largest benefit, so this is a material contradiction of the hoped-for real-image behaviour.

A very strict gate (`outer-circle RMSE < 0.015` and all 8 markers present) produced 6/7 apparent wins with a median improvement around 6.7%, but this tiny subset is not enough to justify implementation and the gain is an order of magnitude smaller than the controlled prediction.

## Variant B — constrained two-ring / multi-conic proxy

To reduce point-wise overfitting, a two-degree-of-freedom projective term was fitted only to make the detected marker-centre ring concentric and radially consistent while re-normalising the outer dial boundary back to a circle.

This is closer to the intended multi-conic concept than the free point homography.

It did improve its own marker-ring objective, but failed the independent minute-track test badly:

- baseline minor-tick angular RMS median: **1.478 deg**;
- candidate median: **2.032 deg**;
- only 7/57 images improved;
- paired median relative change: about **17.3% worse**;
- for `tilt >= 8 deg`, 0/16 images improved.

Minor-tick radial consistency also failed to show a material benefit.

## Variant C — ellipse-axis-constrained projective term

A final bounded variant reduced the projective correction to one scalar along the fitted ellipse minor-axis direction. This tested whether the free solutions were simply inventing the wrong projective direction.

It also failed independent validation:

- baseline minor-tick angular RMS median: **1.478 deg**;
- candidate median: **1.863 deg**;
- only 7/57 images improved;
- paired median relative change: about **9.4% worse**;
- for `tilt >= 8 deg`, 0/16 images improved.

Again, the worst behaviour occurred where a real perspective correction should have been most useful.

## Rehaut observation

A simple research rehaut-width extractor was also tried as a possible signed pose-direction cue. On uncontrolled dealer/QC imagery it was strongly contaminated by bezel numerals, specular reflections, crystal/rehaut highlights and partial occlusion. It did not pass a sufficiently trustworthy confidence gate to use as a correction driver in this benchmark.

This is consistent with the earlier controlled rehaut work: rehaut asymmetry is useful as a pose-quality/direction diagnostic, but was never validated as a correction magnitude by itself.

## Interpretation

The controlled synthetic result remains mathematically valid: if the true projective constraints are known accurately, full rectification can remove most of the affine residual.

The real-image benchmark shows a different limitation:

**the extra projective degrees of freedom are not recoverable reliably enough from the current single-photo visual evidence to improve independent dial geometry.**

The round-marker ring can be made to look much more canonical, but doing so does not make the independent minute track more canonical. In the tilted images it usually makes it worse.

This is the exact failure mode the Phase 4 gating rule was designed to catch.

## Decision

**NO-GO for production projective rectification using the current dial / marker / ellipse evidence.**

Do not:

- replace the current affine dial frame;
- add a marker-fitted homography;
- add a two-ring projective refinement;
- infer projective magnitude from rehaut width;
- tune Android thresholds around this experiment.

The existing affine frame plus pose/reliability gating remains the safer production behaviour.

The negative result does **not** say that projective geometry is unimportant. It says that forcing a correction from noisy one-photo cues creates more error than it removes.

## Phase 4 consequence

Experiment 1 is complete and should not consume more implementation time now.

Proceed to **Experiment 2: paired genuine / VSF / Clean residual analysis**.

That experiment has a higher chance of producing a material QC gain because it asks a different question:

> After using the current safe normalization, which local visual or geometric differences between genuine and replica watches actually persist across controlled or near-controlled paired photographs?

No Android change is authorised by this benchmark.