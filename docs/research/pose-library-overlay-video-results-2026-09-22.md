# Pose-library-overlay: same-watch video stress test - 2026-09-22

Addendum to `pose-library-overlay-2026-09-22.md` (design) and
`pose-library-overlay-results-2026-09-22.md` (calibration-corpus result:
**not retained**). Reports the video same-watch stress test the original
brief called the decisive metric.

## Scope actually received (important limitation, stated up front)

The brief described 9 frames spanning near-frontal/medium/strong tilt plus
a named negative-control frame (`pose_08_028.59s.jpg`, included because an
earlier pose pipeline failed to acquire an ellipse there). **Only 3 images
were actually attached**, with generic filenames that don't match the
described set, and no frame matching the negative-control description.
This was flagged to the user before proceeding; the explicit decision was
to continue with the 3 frames available rather than wait. Everything below
is scoped to those 3 frames -- a much thinner test than specified, with no
negative-control frame to report on separately (deliverable 5 of the
original brief is therefore not applicable this round).

All 3 frames are confirmed genuinely distinct (different SHA-256 hashes)
and span a real, useful tilt range even at n=3: baseline-pipeline-estimated
tilt 5.5, 11.9, and 17.1 degrees. Frame order/adjacency in the source video
is not known (filenames carry no timestamp), so the brief's specific
"smooth vs. jumping pose between visually adjacent frames" check could not
be performed -- noted here rather than silently skipped.

## Method

Unlike the calibration corpus, these frames have no pre-existing baseline
row, so `experiment_pose_library_video.py` runs the full production
pipeline (`pipeline.build`) from scratch per frame for method A, then
methods B (multi-radius) and C (pose-library) from the same ellipse/roll,
exactly mirroring the corpus experiments' A/B/C construction. All 3 frames
produced a valid ellipse and a successfully-fitted B and C pose (no
acquisition failures); all 3 were **rejected** by the full pipeline's
stricter production-acceptance gate (`pipeline_outcome=rejected`, but
`failure_reason` empty -- i.e. acquisition succeeded, the rejection is
from downstream trust checks like final top-phase/identity verification,
not a geometry failure). Per the video test's purpose (repeatability under
viewpoint change, not re-validating the production accept/reject gate),
all 3 are treated as usable here; this is a deliberate scope difference
from the corpus experiments, which filtered to `accepted` only, and is
flagged rather than silently applied.

Visual diagnostics for all 3 frames were rendered and sent directly to the
user (not via CI artifact, since these are local files in this session,
unlike the corpus photos) -- confirmed by inspection that the tooling
produces plausible, non-degenerate overlays (ticks track the visible
minute track, the projected master overlay's dial-edge/marker positions
land close to their real counterparts) on all 3 frames, not just on
synthetic/corpus data.

## Per-frame pose result

| frame | tilt (baseline) | theta | phi | psi | k | best score | 2nd-best score |
|---|---|---|---|---|---|---|---|
| frame_01 | 11.9deg | 12.66 | 226.2 | -2.43 | 0.00031 | 1.796px | 1.797px |
| frame_02 | 5.5deg | 7.66 | 318.4 | -1.89 | 0.00019 | 1.398px | 1.399px |
| frame_03 | 17.1deg | 16.48 | 271.1 | 0.00 | 0.00015 | 1.084px | 1.085px |

Two things stand out immediately, both bad for method C and both
independently corroborating the calibration-corpus finding:

1. **Tick-fit residual (1.08-1.80px) is markedly worse than the
   calibration corpus's typical 0.3-0.6px** -- these handheld video frames
   are noisier (focus/motion blur, on-wrist reflections) than the corpus's
   curated photos, a real data-quality difference worth flagging on its
   own.
2. **Best and second-best pose candidates are separated by ~0.001px on
   every single frame** -- an even more extreme version of the corpus's
   `n_near_best=8/8` finding: on real video data, the top-8 refined local
   optima are landing essentially on top of each other in score.

## The ambiguity finding, sharpened: an exact, provable mechanism

The corpus results doc attributed phi's poor identifiability to
`cos(theta)` having zero derivative at theta=0 -- a real but partial
explanation that predicted the ambiguity should shrink as tilt grows. The
video data contradicts that specific prediction (frame_03, tilt 17.1deg,
theta pinned to a 0.05deg range, still shows a 182deg phi range) and
prompted finding the actual, complete mechanism:

**At k=0 (pure orthographic/affine projection), `phi -> phi + 180` is an
exact symmetry of the pose model, for any theta, independent of psi.**
Verified directly, not assumed: `_tilt_xy_block(theta, phi)` and
`_tilt_xy_block(theta, phi+180)` are bit-for-bit identical in the two rows
that matter at k=0 (verified with theta=15deg: max abs difference in
projected tick positions = 0.0, exactly). This is the classic **orthographic
depth-reversal ("bas-relief") ambiguity** from structure-from-motion:
under orthographic projection, tilting a flat surface toward the camera
from one side is visually indistinguishable from tilting it away from the
same amount on the opposite side, for *any* tilt magnitude -- because
orthographic projection discards depth information entirely, and labelled
point correspondences on a single flat plane cannot recover it (every
tick's position is affected identically by the symmetry, so correspondence
labelling doesn't help the way it does for the earlier single-radius conic
degeneracy).

The divergence from this exact symmetry grows **linearly in k** (checked
numerically: k=0.35 gives 0.127 max point difference, k=0.03 gives 0.011,
k=0.003 gives 0.0011, ... down to k=0.0001 giving 0.00004) -- so the
symmetry is only *exactly* exact at k=0, but is *effectively* exact
(smaller than realistic tick-localisation noise) whenever k is small,
which is exactly the regime every one of these 3 video frames converged
to (k <= 0.00031 in all three, i.e. 3-4 orders of magnitude below the
K_MAX=0.9 search ceiling).

This is a materially better explanation than the corpus doc's original
"shallow near theta=0" framing: it is exact, holds at any theta, and
explains why frame_03's very well-pinned theta (16.4-16.5deg) did nothing
to resolve phi (91-273deg, a 182deg range -- within a few degrees of the
theoretical exact-symmetry partner 271.1+180=91.1, i.e. **matching the
bas-relief prediction almost exactly**: `phi_lo=91.1` and `phi=271.1` are
180.0 degrees apart to one decimal place). The mean-field/synthetic
low-tilt framing in the corpus doc is superseded by this sharper,
verified mechanism and should be read with that correction.

Practical implication: whether real GMT QC photos happen to sit in the
near-orthographic (small-k) regime is not an optimizer artifact to be
tuned away -- it may be a structural property of how these photos are
typically taken (phone camera at a working distance that is large relative
to the ~30-40mm dial, which is close to the textbook condition for weak
perspective). If so, **any method that tries to recover tilt-azimuth from
a single flat ring of correspondences (ticks alone) is fighting an exact
symmetry of the imaging model itself**, not a fixable noise problem.

## Same-watch marker stability, A vs B vs C (n=3 frames, small-sample caveat applies throughout)

Only markers detected in >=2 of the 3 frames are reportable; none reached
the originally-specified ">=3 usable frames" bar for every marker (only
hours 7 and 8 did, at n=3; hours 2, 5, 10, 11 reached n=1-2). Values are
radial_pct_r range (max-min) across the available frames per method,
pooled (mean) across the 5 markers with >=2 observations:

| marker (hour) | n | A range | B range | C range |
|---|---|---|---|---|
| 5 | 2 | 2.840 | 3.024 | 2.369 |
| 7 | 3 | 0.384 | 0.670 | 0.454 |
| 8 | 3 | 1.912 | 1.857 | 2.023 |
| 10 | 2 | 2.670 | 2.944 | 3.391 |
| 11 | 2 | 0.635 | 1.068 | 1.072 |
| **pooled mean** | | **1.688** | **1.913** | **1.862** |

**A (baseline) is again the most stable of the three methods**, exactly as
found on the calibration corpus. B and C are both worse than baseline on
this pooled measure, with C (1.862) slightly better than B (1.913) but
both clearly behind A -- consistent in *direction* with the corpus result
(A best; neither alternative method wins), though at n=3 frames this
cannot be treated as independently statistically decisive on its own. What
it *does* establish is that **the video test does not contradict the
corpus conclusion** -- if anything it reinforces it, and via an
independent data source (this user's own handheld video, not the fetched
corpus).

Per-marker radial values across A/B/C are also strikingly close to each
other on every frame (typically within a few tenths of a percent-R) --
i.e. the three pose-fitting methods largely agree with each other on any
given frame. This points at the *dominant* source of the large
frame-to-frame ranges (hours 5, 8, 10 all exceed 1.9%R range) being
something common to all three methods -- most plausibly per-frame marker
segmentation noise (real handheld-video artifacts: focus, glare, motion
blur differ frame to frame) rather than a difference between the
homography-fitting strategies themselves. This is a useful, honest
observation for any future work on this problem: the pose-fitting method
is not obviously the dominant error source on this particular evidence,
even though C's specific hoped-for improvement (via physical
regularisation) does not materialise here either.

## Conclusion

The video test, despite being far thinner than specified (3 frames, not
9; no negative-control frame delivered), **supports rather than
contradicts** the calibration-corpus verdict:

- Baseline (A) remains the most stable method on pooled same-watch radial
  measurement across viewing angle.
- Method C is not a clear improvement over baseline on this sample either.
- The azimuth-ambiguity mechanism identified on the corpus is not only
  reproduced here but is now understood exactly: a genuine orthographic
  depth-reversal symmetry, not merely correspondence noise, and it shows
  up strongest exactly where the corpus data suggested (near-affine,
  small-k poses), including at real tilt as high as 17 degrees.

**Decision-rule verdict unchanged: not retained.** The video evidence adds
confidence rather than doubt to that conclusion, and sharpens *why* to a
level that would inform any future attempt at this problem: a
single-radius, orthographic-leaning imaging regime cannot resolve
tilt-azimuth from ticks alone, no matter how precisely the ticks are
localised -- the fix (if pursued) has to come from breaking the k~0
degeneracy directly (e.g. a second, well-separated radius, as already
flagged in the corpus doc), not from better point localisation or a wider
search grid.

## What would still improve this test, if more frames become available

Re-run with the originally-described 9-frame set (including the negative-
control frame) once available, specifically to: (1) reach the ">=3 usable
frames per marker" bar for more markers, (2) check pose continuity between
temporally-adjacent frames (needs known frame ordering), (3) see how the
pipeline explicitly handles the negative-control (no-ellipse) case for
methods B and C, which this round could not test.

## Status

Not pursued further without checking in first, consistent with how the
corpus result was handled. No Android, production, master-geometry, or
validation-data changes. Raw video frames and diagnostic overlay images
are not committed to git (kept out of the repository, same convention as
third-party corpus photos, even though these are the user's own images);
they were sent directly to the user. Only this document and the derived
numeric CSV are committed.
