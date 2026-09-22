# Pose-library-overlay experiment - results and conclusion - 2026-09-22

Addendum to `pose-library-overlay-2026-09-22.md` (design doc: parameterisation,
search ranges, architecture). Reports the calibration-corpus run and the
ambiguity analysis, and gives the decision-rule verdict. The video same-watch
stress test (the decisive test per the original instruction) has **not**
run yet -- the referenced frame pack is not present anywhere in this repo's
history (confirmed by search) and remains blocked on the user attaching it.
The calibration-corpus run below was run ahead of it because it does not
depend on that blocker and the result is already decisive enough to report;
the video test, once available, will still be run and reported as a further
check, per instruction, since it is a cleaner controlled source (one
physical watch, camera-only viewpoint change).

## Pipeline-level result: works as engineered

CI run [35719182274](https://github.com/Biggregw/watch-align/actions/runs/35719182274),
completed successfully in ~135s total (37s of actual pose-search compute
across 45 images -- well under the budgeted 2-4s/image). 45/45 calibration-
split accepted images produced a fitted pose (no failures). Mean tick-fit
residual 0.633px (range 0.166-1.504px). Visual overlays (15 images spanning
near-frontal/medium/highest-tilt, artifact `pose-library-overlay-visuals`,
ID 10690906111) were rendered successfully; this sandbox cannot download
GitHub Actions artifacts (established, categorical network restriction --
the same constraint noted throughout this session's prior CI-based
experiments), so they have not been visually inspected by me, only by the
numeric diagnostics below and, if the user opens the artifact URL, directly.

## Repeatability result: not retained

Within-watch repeatability (std of radial_pct_r / angular_deg across images
of the same physical watch, same marker hour; higher = worse), computed
over the 80 watch/marker groups with >=2 images present under **all three**
methods (A=baseline ellipse/affine, B=multi-radius RANSAC homography,
C=pose-library), so the three-way comparison is apples-to-apples:

| metric | A (baseline) | B (multi-radius) | C (pose-library) |
|---|---|---|---|
| radial mean std | 1.534 | 1.615 | **1.671** |
| radial median std | 0.668 | 0.622 | 0.748 |
| angular mean std | 0.828 | 0.910 | **0.935** |
| angular median std | 0.568 | 0.689 | 0.692 |

C improves over A in 30/80 (38%) radial groups, 42/80 (53%) angular groups.
C improves over B in 39/80 (49%) radial groups, 35/80 (44%) angular groups.
By mean, C is the **worst of the three methods on both metrics** -- not a
close call, and not the outcome the physical-regularisation architecture
was betting on.

Tilt-band breakdown (radial, matched groups) is the most damaging result
for this candidate specifically because the method's whole motivation was
improving high-tilt behaviour:

| tilt band | n groups | A | B | C |
|---|---|---|---|---|
| 0-8 deg | 46 | 1.259 | 1.329 | 1.359 |
| 8-13 deg | 26 | 1.326 | 1.485 | 1.478 |
| 13-50 deg | 8 | 3.787 | 3.678 | **4.091** |

C is the worst-performing method in the high-tilt band -- the opposite of
what would be needed to retain it per the decision rule ("high-tilt
behaviour improves").

## Ambiguity analysis: a real, quantified, and now-understood cause

This is the substantive finding of this experiment, and the reason the
repeatability result above is not a mystery. `ambiguity_report()` was run
on every one of the 45 real images (never just synthetic data), comparing
the 8 refined local-optimum candidates against the single best-tick-fit-
residual pose (always selected by tick fit alone, never re-picked by
marker outcome, per instruction):

- **All 45 images have all 8 refined candidates within the near-tied
  margin** (`n_near_best = 8/8` on every single image).
- **Tilt-azimuth (phi) range among those near-tied candidates: mean 207.5
  degrees, several images hitting the full 360-degree search bound.**
  Tilt-magnitude (theta) range among the same candidates: mean only 2.6
  degrees, max 7.3 degrees -- theta is comparatively well-constrained,
  phi is not.
- **`corr(tilt_deg, phi_ambiguity_range) = 0.085`** -- essentially zero.
  This contradicts the synthetic-data expectation documented in the design
  doc (phi should sharpen with tilt) and is the real-image finding that
  needed explaining, not assumed away.
- Consequence: predicted marker positions vary by a mean of 0.956px (max
  2.709px) across the near-tied candidate set for a single image -- i.e.
  a real, non-negligible source of marker-position noise baked into the
  method's own pose selection, independent of tick-localisation quality.

**Mechanism, checked rather than assumed:** the first hypothesis (an exact
algebraic gauge symmetry between phi and psi at low perspective strength
k, since many solved poses have k at or near 0) was tested numerically and
**rejected** -- holding `phi + psi` constant while varying phi does *not*
reproduce identical projected tick positions (verified directly: varying
phi from 0 to 270 degrees at fixed `phi+psi` sum gives four visibly
different tick coordinate sets, not one). So this is not a literal
non-identifiability in the strict sense.

What the data does support: `cos(theta)` (the quantity that actually
determines the ellipse's eccentricity, and by extension how strongly phi
is pinned down) has **zero derivative at theta=0** -- its sensitivity to
theta is second-order near zero tilt. Phi's own effect on the fit is
weaker still in that regime (theta=0 is exactly phi-invariant by
construction). The corpus is dominated by low-to-moderate tilt (mean
solved theta 7.05 degrees, median well below that), and realistic tick
localisation noise (~0.3-0.5px, consistent with everything measured
earlier in this session's diagnostics) is enough to make many
substantially different phi values fit the ticks almost equally well in
that regime. This is **not** primarily a "poor pose scoring" or
"optimizer" bug (the coarse-to-fine search was validated to find the true
global optimum on clean synthetic data), and it is **not** primarily
"marker segmentation" (rectify-then-detect is unchanged from the
already-validated method B code). It is the specific failure mode the
original brief explicitly flagged as a risk to check for: **insufficient
information in the image to uniquely select the pose** -- specifically its
azimuth component -- when the pose is constrained by minute-track ticks
alone, at the tilt magnitudes that actually dominate real QC photos.

This also explains why theta being reasonably well-recovered (a real,
positive result on its own) does not translate into better marker
repeatability: the marker prediction depends on the *whole* pose, and an
under-constrained phi injects noise the ellipse/affine baseline's simpler
model never had to resolve in the first place.

## Decision-rule verdict

| criterion | result |
|---|---|
| same-watch radial measurements materially more stable across angle | **No** -- worse than baseline on this corpus |
| angular measurements at least as stable as baseline | **No** -- worse than baseline |
| high-tilt behaviour improves | **No** -- worst of the three methods in the 13-50deg band |
| catastrophic failures not increased | Yes -- 45/45 pose fits succeeded, no crashes; but ambiguity-driven marker disagreement up to 2.7px is a real, quantified instability source |
| fitting independent of marker-under-test | Yes -- ticks only, verified in code and by construction |
| works without EXIF/camera metadata | Yes |
| viable for imported dealer QC photos | Yes -- ran on the same real corpus as every other method here |

**Not retained.** Two of the four substantive stability criteria fail
outright, and the failure is not the two previously-rejected approaches'
failure mode (noise-driven overfitting, or a wrong-surface correspondence
problem) -- it is a specific, quantified, mechanistically-understood
identifiability limit: a single-radius tick ring under-constrains tilt
azimuth at real-world tilt magnitudes, badly enough to erase whatever
benefit the physically-constrained model's noise-resistance was supposed
to provide. This is a genuine negative result with a genuine, useful
finding attached, not an inconclusive one.

## What would plausibly fix the identified cause (not pursued here)

The design doc already anticipated needing a second radius to break
single-conic degeneracies (the same reasoning that motivated combining
ticks + boundary in the multi-radius work). Boundary correspondences are
now meaningfully improved (2026-09-22 diagnosis + fix) and, being at a
different, well-separated radius, would add real leverage on phi that
ticks alone cannot provide. This experiment deliberately used ticks only
in v1 to isolate the pose-search architecture's own behaviour from
boundary-correspondence quality; the natural next step is re-scoring
candidates against ticks + boundary jointly. This is **not** pursued in
this pass -- flagging it as the evidence-based next direction rather than
acting on it without checking in first, consistent with how the previous
boundary-correspondence fix was handled.

## Status

Calibration-corpus evidence is decisive: not retained as-is. Video same-
watch stress test remains pending (blocked on the frame source). No
Android, production, master-geometry, or validation-data changes; nothing
merged to `main`.
