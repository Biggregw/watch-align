# Multi-radius dial-geometry homography experiment - 2026-09-22

Part B of the dial-geometry-recovery investigation (see
`camera-intrinsics-feasibility-2026-09-22.md` for Part A, and
`radial-drift-root-cause-2026-09-22.md` for the underlying problem this is
trying to fix). Question: can a homography fit from features already
present in the dial image itself -- the 60 minute-track ticks plus the
dial/bezel boundary, at two different radii, never the hour-marker-under-
test's own position -- constrain a proper projective mapping well enough to
improve marker radial-offset repeatability, without any camera metadata?

This is a genuinely different architecture from the two previously-rejected
fixes: instead of detecting a marker in the skewed image and transforming
that already-detected point, it fits the homography first from independent
points, warps the whole image to canonical/frontal space, and only then
detects markers -- "rectify-then-detect."

Implementation: `tools/watch_align_py/multi_radius_pose_solver.py` (+
`experiment_multi_radius_homography.py` runner and
`render_multi_radius_overlay.py` visual renderer). Calibration-split only;
validation untouched. CI: `.github/workflows/multi-radius-homography-experiment.yml`,
run [35696009861](https://github.com/Biggregw/watch-align/actions/runs/35696009861),
completed successfully in ~71s.

## Pipeline-level result: works as engineered

- 45/45 calibration-split accepted images produced a fitted homography (no
  failures).
- Mean RANSAC reprojection error among inliers: 0.53px (range 0.25-0.90px)
  -- the fit itself is precise on the points it keeps.
- Marker coverage in rectified space is comparable to baseline (e.g. marker
  7: 32 baseline / 35 new; marker 4: 27 baseline / 26 new) -- rectify-then-
  detect is not losing markers relative to the ellipse-only baseline.
- Visual overlays (15 highest-tilt calibration images, artifact
  `multi-radius-homography-visuals`, ID 10679814148) confirm the mechanics
  are working: ticks and boundary points land where expected on the
  rectified dial.

## Repeatability result: not a clear improvement yet

Within-watch repeatability (std of radial_pct_r / angular_deg across images
of the same physical watch, same marker hour; higher = worse), computed
only over the 80 watch/marker groups with >=2 images present under **both**
methods, so the comparison is apples-to-apples:

| metric | baseline mean std | new (rectify-then-detect) mean std | groups improved |
|---|---|---|---|
| radial_pct_r | 1.598 | 1.735 | 33/80 (41%) |
| angular_deg | 0.840 | 0.945 | 39/80 (49%) |

By this measure the new method is slightly **worse** on average, not
better, and improves fewer than half of the groups it's compared on. This
does not clear the retention bar established after the two earlier rejected
approaches (must materially improve calibration-split repeatability to be
kept).

Two things temper that headline before concluding this approach is a third
failure, though:

1. **RANSAC inlier fraction is surprisingly low: mean 0.50, range 0.29-0.69,
   across all 45 images, including near-frontal ones.** With 40-48 tick/
   boundary correspondences per image and a threshold of 1.5px, a clean
   single-plane fit should keep the large majority of points as inliers,
   not roughly half. This is the most likely root cause of the neutral/
   negative result and the natural next thing to debug -- it suggests the
   correspondence extraction (`_localize_centroid`) or the RANSAC threshold
   itself has a systematic issue not yet isolated, separate from the
   "does rectify-then-detect help" architectural question.
2. **This run's sample is overwhelmingly low-tilt** (median 6.5deg, only
   2/45 images at tilt >=15deg, max 45.7deg) -- i.e. mostly the regime
   where the baseline ellipse-affine model is already known to be
   reasonably accurate. The regime where the root-cause doc showed the
   baseline degrading badly (tilt >15-20deg) is barely represented here, so
   this run is not yet a strong test of the hypothesis that motivated it.

## Conclusion

Pipeline mechanics work; the repeatability outcome is inconclusive-to-
negative on this run, not a validated win. Per the established bar, this
candidate is **not retained/promoted** as-is. It is also not written off as
a third failed approach the way the previous two were -- unlike those, this
result comes with a specific, plausible, unexplained defect (the low
inlier fraction) rather than a clean negative result, so the honest
conclusion is "needs debugging before the underlying question is actually
answered," not "rectify-then-detect doesn't work." No thresholds, master
geometry, or production code were changed. Validation split remains
untouched.

## Addendum 2026-09-22 (later same day): after fixing the boundary correspondence defect

The suggested next step above was carried out. Full diagnosis in
`boundary-correspondence-diagnosis-2026-09-22.md`: the low inlier fraction
was almost entirely a boundary-correspondence problem (tick inlier fraction
0.838, boundary 0.138), and the evidence pointed at localisation
imprecision and a ~1% systematic radius mislabel -- not a genuinely
different, non-coplanar physical structure. Fixed by narrowing the
boundary search window (half-width 0.07 -> 0.035, same empirical centre)
and labelling boundary correspondences at that empirical centre
(`BOUNDARY_LABEL_R=1.01`) instead of `master.DIAL_EDGE_R`, confined to the
experimental module. RANSAC threshold (1.5px) was not touched, per
instruction.

The fix worked as a fix: boundary inlier fraction rose from 0.138 to 0.318
(mean), residual against the independent ticks-only reference fell from
5.92px to 3.21px (mean), and the implied-radius std nearly halved (0.029 ->
0.014) -- all confirming the localisation-precision diagnosis was correct.
Boundary correspondences are still notably less reliable than ticks
(31.8% inlier vs 84.3%), but are meaningfully better than before.

Re-running the same within-watch repeatability comparison against the
fixed solver's output
(run [35715326976](https://github.com/Biggregw/watch-align/actions/runs/35715326976),
commit `bdb7657`), now over 83 common watch/marker groups (up from 80, since
the fix also let a few previously-undetected markers through):

| metric | baseline mean std | new mean std | baseline median std | new median std | groups improved |
|---|---|---|---|---|---|
| radial_pct_r | 1.624 | 1.705 | 0.668 | 0.622 | 40/83 (48.2%) |
| angular_deg | 0.816 | 0.898 | 0.537 | 0.689 | 48/83 (57.8%) |

Closer to neutral than the pre-fix run (radial improved-group share rose
41%->48%, angular 49%->58%, and angular now improves in a majority of
groups), but still **not a clear win**: mean std is still slightly worse
than baseline for both metrics, and the radial median (which did improve,
0.668->0.622) tells a different story from the radial mean (still worse,
1.624->1.705) -- a heavy-tailed distribution, not a uniform improvement.
Inspecting the worst individual regressions shows a small number of
specific watch/marker groups with large single-group blowups (e.g.
`rep_cf_f71234_Sw2D1Bt` hour 8: baseline std 0.196 -> new std 3.174;
`rep_vsf_KfRKFwA` hour 7: 0.339 -> 2.319) alongside many groups with small,
genuine improvements -- consistent with occasional catastrophic single-
image misdetections (likely in the rectified-space marker segmentation,
not the homography fit itself, since the fit's own reprojection error
stayed low) rather than a systematic bias affecting every image.

## Conclusion after the fix

The specific defect this addendum investigated (boundary correspondence
reliability) was real, diagnosed correctly, and meaningfully improved --
that hypothesis is confirmed. But fixing it did not flip the overall
verdict: calibration-split within-watch repeatability is still roughly a
wash against baseline (slightly negative on aggregate mean, mixed on
median, a minority-to-bare-majority of groups improving depending on
metric), not the "materially improves" result needed to retain/promote
this candidate. This is the decision point the investigation was scoped to
reach; per instruction, no further tuning was attempted past this point
without checking in first. No thresholds, master geometry, or production
code changed; validation split untouched.

A plausible next lead, not yet investigated, is the handful of large
single-group regressions above -- they look like occasional bad marker
segmentation in rectified space on specific images, not a homography
problem (reprojection error stayed low), and might be a smaller, more
tractable target than the original "does rectify-then-detect work"
question.
