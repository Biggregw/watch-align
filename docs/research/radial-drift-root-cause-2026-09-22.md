# Radial drift root-cause investigation - 2026-09-22

## Scope

This continues the 2026-09-21 handoff's open question: why does radial marker
localisation move with camera tilt while angular localisation stays
comparatively stable? Investigation only, on `experiment/projective-marker-
normalization`. No pose acquisition, marker detection, master geometry,
Android code or production threshold was changed. Developed and evaluated on
calibration-split watches only; validation watches were not inspected while
selecting or tuning anything below.

Tooling added: `tools/watch_align_py/diagnose_radial_drift.py` (per-marker
diagnostics, reuses the already-accepted ellipse/roll from the committed
`per_image_measurements.csv`, no pose re-search),
`tools/watch_align_py/render_radial_drift_examples.py` (visual ROI crops for
same-watch low-vs-high-tilt pairs), and the CI job
`.github/workflows/radial-drift-diagnostics.yml`, which committed
`datasets/126710BLNR/results/radial_drift_diagnostics.csv` (347
marker-observation rows, 45 calibration-split accepted images, 15 watches)
and uploaded `radial-drift-visuals` as a 14-day GitHub Actions artifact
(8 tilt-pair panels; images are not committed to the repository).

## 1. Root-cause assessment

**The dominant mechanism is a global model mismatch, not a local detection or
linearisation error.** The pipeline fits and scales a single ellipse against
the minute track (canonical radius 0.891), then predicts every marker's
image position by uniformly, radially scaling that *same* ellipse shape down
to the marker's own calibration radius (~0.68-0.75). This is only exact
under an affine (weak-perspective) camera model. Under genuine perspective
projection, concentric circles at different radii do **not** project to
similarly-shaped, concentric ellipses -- their effective eccentricity and
centre shift with radius, and by how much depends on the true tilt and on
camera focal-length/standoff, neither of which the pipeline observes
directly. The mismatch between "true marker position" and "affine
extrapolation from the minute-track ellipse" grows with tilt and is
structured by where the marker sits relative to the ellipse's own
most-foreshortened axis, not just by tilt magnitude.

This was established two ways, and they agree:

- **Code inspection**: `marker_qc._map()`, the function every marker
  coordinate goes through in both directions, is a pure affine transform
  (rotate, anisotropic scale, translate) -- it contains no projective
  (homogeneous-divide) term anywhere. A per-marker local linearisation of
  this function therefore has **zero** curvature by construction; there is
  nothing for a "ROI basis breaks down locally" hypothesis to find.
- **Closed-form perspective simulation** (a tilted-plane pinhole-camera
  homography, not committed -- see Q2 below): projecting the minute track
  and a marker circle through the *same* true perspective transform and
  comparing the marker's true position against the affine-ellipse
  extrapolation reproduces the corpus's own radial-error-vs-tilt magnitudes
  closely (~0.3%R predicted at 5 deg vs 0.20-0.22%R observed at <=10 deg;
  ~1%R at phi=10 vs 0.72-1.13%R observed at 10-15 deg; several %R by
  phi=15-20 vs 2.5-3.2%R observed at >15 deg), and predicts a systematic
  directional structure (largest bias near the ellipse's foreshortened
  axis, smaller near its equator).

## 2. Calibration-only quantitative evidence

All numbers below are from `radial_drift_diagnostics.csv`
(calibration split, 45 accepted images, 15 watches, 347 marker
observations), using within-watch-and-marker MAD against that watch's own
median as the repeatability metric (same method as the 2026-09-21
safe-operating-area note).

### Q1 -- does the image-space detected centroid move under foreshortening?

Yes, substantially, and largely independent of *how* the position is later
normalised:

| Tilt band | Raw pixel displacement (%dial radius) | Basis-local radial (pre ellipse-undo) | Current-production radial (%R) | Current-production angular (deg) |
|---|---:|---:|---:|---:|
| <=10 deg | 0.217 | 0.0021 | 0.198 | 0.240 |
| 10-15 deg | 1.068 | 0.0073 | 0.723 | 0.428 |
| >15 deg | 2.385 | 0.0319 | 3.183 | 0.524 |

The raw image-space displacement and the final production radial reading
degrade together, at essentially the same rate. That rules out the
coordinate-normalisation arithmetic (`_undo_ellipse_distortion`) as an
*additional* source of error on top of whatever is already present at the
pixel level -- it is an exact, invertible affine map, so it cannot inject
extra error. Angular degrades far more slowly (about 2x from the lowest to
the highest band) than radial (about 16x). This matches the handoff's
qualitative finding with calibration-only data.

### Q2 -- does the ellipse-derived ROI basis itself cause a systematic bias?

Directly measured: comparing the ROI basis Jacobian at finite-difference
step 0.01 against step 0.08, at the *same* reference point, for all 347
observations, gives a relative difference with **median 1.8e-14, max
4.8e-14** -- floating-point noise. There is no local curvature to find,
confirming the code-inspection result above. The bias is not "the local
linear approximation breaks down here"; it is "the whole model is the wrong
global shape for markers away from the calibration radius."

### Q3 -- does marker shape (round / baton / triangle) affect the detected centre?

| Shape | <=10 deg within-watch radial MAD | >10 deg within-watch radial MAD | Ratio |
|---|---:|---:|---:|
| round | 0.218 %R (n=166) | 1.082 %R (n=53) | 5.0x |
| baton | 0.166 %R (n=52) | 0.431 %R (n=14) | 2.6x |
| triangle | 0.169 %R (n=20) | 0.654 %R (n=4, thin) | 3.9x |

All three shapes start from a similar baseline at low tilt. Round markers
degrade *the most* in relative terms at higher tilt, not the least, even
though they are the most symmetric shape. This argues against shape-specific
detection bias (e.g. asymmetric thresholding of the triangle or baton) being
the dominant driver -- it is consistent with the global radius-mismatch
mechanism, which affects any marker away from the calibration radius,
regardless of shape. The triangle row is too thin (n=4 at >10 deg) to treat
as a strong independent confirmation either way.

### Q4 -- does the expected radial centre match the visually meaningful centre?

The codebase already special-cases this for the triangle only:
`expected_radius_ratio(12)` uses `TRI_DETECTION_CENTER_R` (0.739), distinct
from the triangle's own visual centre `TRI_CENTER_R` (0.719), specifically
because "a triangle's image centroid is not its geometric centre" (existing
comment in `marker_qc.py`). Round and baton markers use a single constant
(`ROUND_CENTER_R`, `BATON_CENTER_R`) for both purposes -- the same class of
concern the triangle already accounts for has not been extended to the
baton. The Q3 result above suggests this gap is not currently the dominant
error source (batons were the *least* degraded shape at higher tilt), but it
is a real, documented asymmetry in the code worth noting for anyone working
on marker-centre definitions later.

### Q6 -- does the error depend on camera direction, not just tilt magnitude?

The simulation predicts yes, with a specific structure (largest |bias| near
the foreshortened axis). The real corpus does **not** cleanly confirm that
specific directional structure:

| Alignment to foreshortened axis | n | Median signed radial (%R) | Mean |radial| (%R) |
|---|---:|---:|---:|
| near axis (0-30 deg) | 24 | +0.769 | 2.174 |
| mid (30-60 deg) | 29 | -0.551 | 2.150 |
| near equator (60-90 deg) | 27 | -0.053 | 2.583 |

Mean absolute residual is roughly flat across alignment buckets, not peaked
near the axis as the simple two-parameter simulation predicts. This is a
genuine limitation to report honestly, not a clean confirmation: the >10 deg
calibration sample is dominated by very few watches (`rep_cf_QsJT2wk`
alone supplies most of the >10 deg rows, including its one 45.7 deg photo),
real photos have compound/arbitrary tilt-axis orientations rather than the
simulation's single clean tilt axis, and the accepted pose already passed
through roll-search and (sometimes) projective refinement, both of which
partially reshape whatever clean directional signal a single-parameter tilt
model would predict. The corpus supports "radial error is large and
tilt-driven" strongly; it does not yet support a specific, well-constrained
directional correction term.

## 3. Visual diagnostics

`radial-drift-visuals` (GitHub Actions artifact, run
[35693717663](https://github.com/Biggregw/watch-align/actions/runs/35693717663),
14-day retention) contains 8 side-by-side low-tilt-vs-high-tilt panels for
calibration watches with the largest tilt spread across their own accepted
photos, including `gen_wex_3KSuGhC` (3.7 -> 20.7 deg) and `rep_cf_QsJT2wk`
(11.0 -> 45.7 deg, the same watch as one of the two previously-flagged
suspicious images). Each panel shows every marker's ROI crop at both tilts
with the expected centre (cyan cross), detected blob centroid (yellow cross)
and the connecting displacement line. This session's environment cannot
fetch GitHub Actions artifact storage directly (a standing sandbox network
restriction, unrelated to this repository), so these panels have not been
visually reviewed by Claude; a human reviewer should open the artifact to
confirm the quantitative pattern above against the actual images before
treating it as fully settled.

## 4. Candidate fix attempted and rejected

Given the root cause (global affine extrapolation from the calibration
radius), a natural candidate is a closed-form correction using only stable,
non-detected quantities: the accepted ellipse's own axis ratio, each
marker's fixed calibration-radius ratio to the minute track, and the
marker's angular position relative to the ellipse's foreshortened axis. This
is deliberately different from the already-rejected approach (mapping the
detected marker centre through the inverse final homography): it never
touches the noisy per-photo homography or the detected pixel at all, only
smooth, already-known geometry.

**Before testing it, a further check ruled out clean generalisation**: the
predicted bias at a *fixed* axis ratio still varies materially with the
camera's implicit focal-length/standoff ratio, which is not observable from
a single photo. At axis ratio 0.90, for example, the predicted equator-line
bias ranges from +3.5%R to +7.0%R depending on that unobservable parameter
-- roughly a 2x spread from the same measured axis ratio alone.

Tested anyway, on calibration data, using a fixed representative camera
model: within-watch radial MAD got **slightly worse**, not better:

| Tilt band | Median within-watch radial MAD, before | after | Change |
|---|---:|---:|---:|
| <=10 deg | 0.1951 %R | 0.2036 %R | -4.4% (worse) |
| 10-15 deg | 0.7802 %R | 0.8334 %R | -6.8% (worse) |
| >15 deg | no within-watch pairs in this band | -- | -- |

This does not meet the retention bar (radial repeatability must materially
*improve*). **Rejected.** This is the second independently-designed
correction to fail on this corpus (after the inverse-homography approach in
the 2026-09-21 handoff), for a specific, understood reason each time: the
homography approach amplifies per-pixel detection noise through a
sometimes-underdetermined homography, and this approach is accurate in
direction but not well-constrained in magnitude without knowing the
camera's focal-length/standoff ratio. Per the research process, held-out
validation was not run, since no candidate was frozen.

## 5. Recommendation

**Radial QC should remain gated at high apparent tilt rather than rescued
via a geometric correction, on current evidence.** The mechanism is now
well understood and doubly confirmed (code inspection + simulation +
real-corpus numbers all agree on magnitude by tilt band), which is valuable
in its own right -- it rules out "it's a detection bug" or "it's a
local-linearisation bug" and correctly locates the issue as a structural
limitation of the single-ellipse affine marker model. But two different,
well-motivated correction strategies have now failed empirically for two
different, specific, understood reasons, and the second failure was
predicted in advance by identifying the unobservable camera parameter
before testing it -- this is not "we haven't found the right correction
yet," it is "a single photo does not carry enough information to correct
this reliably." Any future attempt should assume that finding, rather than
re-deriving it: a workable fix would need either an independent estimate of
camera focal length/standoff (not currently collected), or substantially
more high-tilt calibration coverage across independent watches to fit a
data-driven (rather than purely geometric) correction, which the current
corpus does not yet have (>10 deg calibration coverage is 6-7 watches,
dominated by one watch's photos).

The existing provisional operating policy (<=10 deg green, 10-15 deg amber/
advisory-only, >15 deg red/suppress) remains the best-supported production
guidance from this corpus. Nothing here changes that policy or any
production code/threshold; it explains why the policy is the right call
rather than a placeholder.
