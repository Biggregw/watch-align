# Boundary correspondence diagnosis - 2026-09-22

Follow-up to `multi-radius-homography-experiment-2026-09-22.md`, which found
the multi-radius solver's overall RANSAC inlier fraction was only ~50% even
on low-tilt images and flagged it as the likely cause of that experiment's
neutral/negative repeatability result -- and, per explicit instruction,
investigated *why* before touching the RANSAC threshold. In particular:
challenge the assumption that "boundary" correspondences (canonical radius
0.94-1.08, searched around the whole circumference and labelled as exactly
radius 1.000) are all observations of one coplanar physical circle -- they
could instead be locking onto different structures at different points
around the circumference (dial edge, rehaut, crystal/bezel boundary,
reflections), which cannot share a single homography with the minute-track
ticks.

Method: `diagnose_boundary_correspondences.py`, run via CI
([35714784677](https://github.com/Biggregw/watch-align/actions/runs/35714784677))
against all 45 calibration-split accepted images. Fits two homographies per
image -- the shipped ticks+boundary RANSAC fit (`H_full`), and an
independent ticks-only RANSAC fit (`H_ticks`) using only the 0.891-radius
tick correspondences -- then reports inlier fraction/residual split by
correspondence kind, and every boundary point's residual and implied
canonical radius (via `H_ticks^-1`) against the independent tick-only
reference.

## 1-2: RANSAC stats split by kind

| kind | inlier_fraction (mean / median) | residual_px, inliers only (mean / median) |
|---|---|---|
| tick | 0.838 / 0.896 | 0.532 / 0.464 |
| boundary | **0.138 / 0.104** | 0.858 / 0.899 |

Confirms the earlier ~50% combined figure was almost entirely a boundary
problem: ticks are well-behaved (comparable to the ticks-only fit below),
boundary correspondences are rejected roughly 6x more often.

## 6: ticks-only stability (single-radius degeneracy)

| | inlier_fraction (mean / median) | residual_px, inliers only (mean / median) |
|---|---|---|
| ticks-only (H_ticks) | 0.843 / 0.896 | 0.484 / 0.411 |

Essentially identical to ticks' behaviour inside the combined fit. The
theoretical single-radius homography-family degeneracy does not appear to
destabilise the practical RANSAC fit here: with ~40-48 real, noisy tick
correspondences, RANSAC converges to one consistent, tight (sub-pixel
median residual, ~84-90% inlier) solution whether or not boundary points
are also supplied. No further action taken on this point; it does not
block using ticks-only as a reliable reference homography for the rest of
this diagnosis.

## 3-4: what is the boundary extractor locking onto?

Two independent numeric tests, both against the boundary correspondences
(n=1924, all of them, not just RANSAC inliers):

- **Residual vs H_ticks:** mean 5.92px, median 4.87px, std 4.36px, range
  0.07-26.77px -- much worse than ticks' own ~0.5px, confirming boundary
  points are not well explained by the flat-dial-plane homography as
  currently labelled/localised.
- **Implied canonical radius via `H_ticks^-1`:** mean 1.0108, median
  1.0113, std 0.0291, range 0.9226-1.0879.

The implied-radius distribution (histogrammed at 20 bins) is **unimodal**
and roughly bell-shaped, centred close to the nominal 1.000 label (about
1% high) -- not bimodal or multi-modal, which is what "consistently
grabbing two or three distinct physical rings" (e.g. dial rim in some
images/angles, crystal bezel edge in others) would be expected to produce.
Breaking down by 30-degree angle bins (13 bins around the full
circumference) shows no localised structure either: implied radius stays
in 1.007-1.014 and residual in 5.2-6.5px in every bin, with no angular
region standing out the way a crown, date window, or lug structure at a
fixed clock position would if it were being caught systematically.

Two further checks argue against an off-dial-plane (3D, tilt-dependent)
explanation specifically: implied radius and residual are both essentially
flat across tilt (`corr(tilt, implied_radius) = -0.015`,
`corr(tilt, residual) = -0.004`; tilt-banded means/stds also flat:
radius std 0.027/0.030/0.029/0.034 across 0-6/6-9/9-13/13-46 degree bands).
A real 3D height offset between the boundary feature and the dial plane
would be expected to produce a growing parallax-like displacement with
tilt -- structurally the same mechanism as the original marker radial-drift
problem. That signature is not present here.

What *does* explain the spread: residual correlates with how many edge
pixels were found in the window (`corr(n_edge_pixels, residual) = -0.31`;
mean residual 9.84px when n_edge_pixels<=4 vs 4.52px when n_edge_pixels>=15
-- roughly half). And the implied-radius spread is dominated by
**within-image** variation (mean per-image std 0.0247) rather than
between-image/between-watch variation (overall std 0.0291) -- i.e. the
noise is mostly point-to-point on a single photo, not a systematic
per-image or per-watch offset.

## Conclusion: reliable/coplanar, imprecisely localised and slightly mislabelled -- not a different surface

Per the decision rule for this diagnosis: the evidence supports "roughly
coplanar, unreliable due to localisation imprecision," not "locking onto a
genuinely different, non-coplanar physical structure." Two concrete,
fixable defects, not a wrong-surface problem:

1. **Localisation imprecision.** The boundary search window (canonical
   radius 0.94-1.08, half-width 0.07) is 3.5x wider radially than the tick
   window (half-width 0.02) at the same 9x9 grid density -- 3.5x coarser
   sampling, and "centroid of every edge pixel found in a wide window" is
   inherently noisy when the window spans more of the image than the real
   edge's own width/blur/antialiasing.
2. **Mislabelling.** The window is already correctly *centred* empirically
   (its midpoint 1.01 matches the observed implied-radius mean almost
   exactly), but every correspondence found anywhere in that 0.94-1.08 band
   was labelled, for homography-fitting purposes, as exactly canonical
   radius 1.000 (`DIAL_EDGE_R`) -- a ~1% systematic label error on every
   boundary point regardless of where in the band it actually landed.

## Fix applied (confined to the experimental module, not master geometry)

In `multi_radius_pose_solver.py`: narrowed the boundary search window from
`[0.94, 1.08]` (half-width 0.07) to `[0.975, 1.045]` (half-width 0.035,
still centred at the same empirical 1.01 midpoint, about half the old
extent -- generous vs the observed within-image std of 0.0247, while being
substantially tighter than before), and introduced a local `BOUNDARY_LABEL_R
= 1.01` used to label boundary correspondences' canonical position instead
of `master.DIAL_EDGE_R`. `master.py` (production/master geometry) is not
touched -- `DIAL_EDGE_R` remains 1.000 everywhere else. This is a
calibration-only change to an experimental research module; nothing in the
shipped Android or Python production pipeline changed.

## Next step

Re-run both the boundary diagnosis (to confirm inlier fraction improves)
and the full within-watch repeatability experiment against the corrected
solver, per the original request: decide whether rectify-then-detect works
only after fixing the correspondence source, not before.
