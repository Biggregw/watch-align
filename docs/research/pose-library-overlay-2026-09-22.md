# Pose-library-overlay experiment - 2026-09-22

Separate research direction from `multi-radius-homography-experiment-2026-09-22.md`
and `boundary-correspondence-diagnosis-2026-09-22.md`, not an extension of
either. Branch: `experiment/pose-library-overlay`, from
`experiment/dial-geometry-homography` HEAD (`904b308`). No Android,
production, master-geometry, or validation-data changes.

## Question

Can a coarse-to-fine search over a *physically-constrained* family of
camera poses recover viewing geometry accurately enough that measurements
of the same physical watch stay stable across substantially different
photo angles?

## Why this is architecturally different from the multi-radius approach

`multi_radius_pose_solver.py` (the prior experiment) fits a general,
unconstrained 8-DOF homography via RANSAC over many point correspondences.
That is powerful but can fit non-physical distortion -- exactly what the
boundary-correspondence diagnosis found: noisy, imprecisely-localised
points pulled the fit in directions no real camera-viewing-a-flat-dial
relationship would produce.

This experiment instead constrains the search to the 7-dimensional family
of homographies a real pinhole camera viewing a real flat circular dial
can actually produce, and searches *that* family directly. Any such
camera-plane relationship is still exactly expressible as a 3x3
homography (a pinhole camera viewing a plane is always a homography) --
this is not a different geometric model, it is a physically-regularised
*subset* of the same 8-DOF group, missing exactly the one degree of
freedom (independent non-uniform scale/shear) a real camera+flat-target
system cannot produce. Constraining the search to this submanifold is the
central bet: it should be far less able to overfit correspondence noise
than an unconstrained 8-DOF RANSAC fit, at the cost of needing the right
7 parameters and a search strategy that can actually find them.

## Pose parameterisation

Canonical dial-plane point `(x, y, 0)` -> image pixel `(u, v)`:

1. **Tilt.** `P' = R_tilt(theta, phi) @ (x, y, 0)^T = (X, Y, Z)`. `R_tilt`
   is a 3D (Rodrigues) rotation by `theta` (tilt magnitude) about the
   in-plane axis perpendicular to azimuth `phi` (tilt direction) --
   i.e. the direction at azimuth `phi` is the foreshortened ("downhill")
   direction, the perpendicular direction is preserved length. `theta=0`
   is the identity for every `phi` (no azimuth ambiguity at zero tilt, by
   construction -- and, as the ambiguity analysis below shows, this
   construction-level fact is also what the *data* independently confirms
   at low tilt).
2. **Perspective.** `proj = (X, Y) / (1 + k*Z)`, a single dimensionless
   perspective-strength parameter `k` (effectively focal length / camera
   distance, in canonical dial-radius units). `k=0` reduces this exactly
   to orthographic projection -- **the current ellipse/affine baseline
   model is the `k=0` special case of this pose family**, not a different
   model being swapped in.
3. **Roll.** `rolled = Rot2D(psi) @ proj`, applied post-projection --
   matches the existing codebase's convention (`geometry.map_point`
   already treats roll as a 2D in-plane rotation).
4. **Scale + translation.** `(u, v) = s * rolled + (tx, ty)`, solved in
   closed form (ordinary least squares against detected tick pixels) for
   any candidate `(theta, phi, psi, k)` rather than searched -- exactly as
   specified (translation/scale are not library dimensions).

`k` is restricted to non-negative values: since `phi` already spans the
full circle, negating `k` is equivalent to `phi -> phi + 180`, so allowing
negative `k` would only duplicate coverage, not extend it.

Implementation: `tools/watch_align_py/pose_library.py`. Verified by
construction (not just informally): `pose_to_homography()` produces a
matrix whose homogeneous projection matches the direct forward-projection
function to float precision; `k=0` matches a pure-rotation orthographic
projection exactly; `theta=0` is exactly phi-invariant. A synthetic
ground-truth recovery test (known `theta=28, phi=63, psi=-9, k=0.35`, 0.4px
correspondence noise matching real tick-localisation precision) recovers
the true pose to within the noise floor via the coarse-to-fine search
below (theta 28.05 vs 28.00, phi 63.01 vs 63.00, psi -8.97 vs -9.00,
k 0.351 vs 0.350), confirming the search actually finds the global optimum
on well-posed synthetic data before any real-image test was run.

## Search ranges (coarse grid, documented rationale)

| parameter | range | step | levels |
|---|---|---|---|
| theta (tilt magnitude) | 0-55 deg | 2.5 deg | 23 |
| phi (tilt azimuth) | 0-360 deg | 15 deg | 24 |
| psi (camera roll) | existing pipeline's solved roll +/-15 deg | 3 deg | 11 |
| k (perspective strength) | 0-0.9 | 0.15 | 7 |

Theta's range covers the corpus's observed tilt range (max seen so far
~46 deg) with headroom. Phi is searched over the full circle since azimuth
is not knowable in advance -- and per the ambiguity analysis requirement,
whether it is even identifiable from ticks alone is one of this
experiment's own questions, not an assumption. Psi is searched only in a
window around the roll the existing, already-validated acquisition/
`MinuteTrackPoseValidator` pipeline already solved for -- a deliberate,
documented simplification (not a claim roll is trivially known); the
continuous refinement stage below can still move psi outside this window
if the true local optimum pulls it there. K's cap of 0.9 keeps
`1 + k*Z > 0` safely positive across the whole grid even at theta=55,
radius~1.01 (`Z <= sin(55) * 1.01 ~= 0.83`, so `1 - 0.9*0.83 ~= 0.25`).

Grid size: 23 x 24 x 11 x 7 = 42,504 candidates per image, fully
vectorised via numpy broadcasting (~1.1s/image on a GitHub Actions
runner). Every candidate is scored **only** against minute-track tick
correspondences (`multi_radius_pose_solver.extract_correspondences`,
`kind=="tick"` -- the same real-edge-pixel-centroid localisation already
validated in the multi-radius work), never the marker under test and
never boundary points in this v1 (boundary correspondences, while now
meaningfully improved, remain noisier than ticks per the 2026-09-22
diagnosis; left as a possible future addition rather than diluting the
primary signal here).

## Continuous refinement

The top 8 coarse candidates (by median tick-residual score) are each
locally refined via Nelder-Mead (`scipy.optimize.minimize`) over
`(theta, phi, psi, k)`, with scale/translation re-solved in closed form on
every evaluation. The best-scoring refined candidate is the final pose
(~0.7s/image for all 8 refinements). Reusing multiple coarse seeds rather
than refining only the single best grid point is what lets the ambiguity
analysis below actually detect near-tied local optima, not just report a
single answer.

## Downstream marker measurement

Once solved, the pose collapses to one ordinary 3x3 homography
(`pose_library.pose_to_homography`). Marker measurement reuses
`multi_radius_pose_solver.rectify()` / `detect_marker_in_rectified()`
**unchanged** (`pose_library_measure.measure_all_markers`) -- the same
single global homography is used to rectify the whole image and every
marker is then detected in that one canonical frame; individual markers
are never independently warped. This also means the A/B/C comparison is
isolated to "how was the homography obtained," since B and C share
identical downstream detection code.

## Ambiguity analysis methodology

`pose_library_measure.ambiguity_report()`: among the refined top-N
candidates, compares every candidate within a fixed, pre-declared margin
of the single best-tick-fit-scoring pose (margin = max(0.15px, 1.5x the
best score) -- chosen before seeing results, not tuned per image) and
reports how much their PREDICTED marker positions disagree, plus the
theta/phi/k range spanned by that near-tied set. The "best" pose is always
selected purely by tick-fit residual, before and independently of any
marker measurement -- this explicitly never re-picks whichever candidate
happens to give the best-looking marker result, per instruction.

Validated on synthetic data before any real-image use: a near-frontal
synthetic case (theta=3 deg true) shows a ~15 deg phi range among
near-tied candidates (genuinely underdetermined azimuth, as expected --
theta=0 is phi-invariant by construction, and the same instability
persists at low but nonzero theta); a high-tilt synthetic case (theta=40
deg true) shows a ~0 deg phi range (azimuth well-identified once there is
real foreshortening to disambiguate it). This matches the theoretically
expected pattern and gives confidence the metric is measuring a real
effect, not a methodology artifact -- the real-image results (below, once
available) are read against this synthetic baseline.

## Decision rule (unchanged from instruction, restated here for the record)

Retained only if: same-watch radial measurements become materially more
stable across angle; angular measurements are at least as stable as
baseline; high-tilt behaviour improves; catastrophic failures are not
increased; fitting remains independent of the marker-under-test; it works
without EXIF/camera metadata; it is viable for imported dealer QC photos.
Not optimised to make the method win.

## Status

Design, implementation, and synthetic validation complete. Real-image
results (video same-watch A/B/C comparison, calibration-corpus
repeatability, visual diagnostics, ambiguity analysis on real images) to
follow in addenda to this document as they complete -- the video test is
currently blocked on the user attaching the frame source (not present
anywhere in repo history, confirmed by search before asking); the
calibration-corpus run does not depend on that and is run first.
