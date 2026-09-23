# Projective marker-consensus QC - research write-up - 2026-09-23

Branch: `experiment/projective-marker-consensus`, from
`experiment/dial-geometry-homography` HEAD (`904b308`). A new, separate
research direction, not an extension of the multi-radius-homography or
pose-library-overlay work (both concluded **not retained** -- see
`pose-library-overlay-results-2026-09-22.md` and
`pose-library-overlay-video-results-2026-09-22.md`). No Android,
production, master-geometry, or validation-data changes anywhere in this
branch.

## 1. Architecture

Every prior approach in this research line tried to recover an absolute
camera pose and compare each marker to an externally-derived ideal
position. That hit a real, provable identifiability wall: at the
near-orthographic perspective strength typical of real watch photos,
tilt-azimuth is exactly ambiguous (`phi -> phi+180` gives identical
projected points at `k=0`, verified numerically), so no amount of better
point localisation or search-grid tuning can fix it.

This approach never tries to recover pose. It fits geometric consensus
(a conic) directly from the **other** markers observed in the **same**
photograph, and asks only whether one marker behaves like its peers under
whatever unknown projective transform that photograph happens to be
under. A circle maps to a conic under any projective transform, so
fitting that conic directly from real observed points sidesteps needing
to know the transform at all -- the same reason tick-fitting worked
reasonably well in the multi-radius work, except here the ring itself is
what is under test, via leave-one-out so a marker is never allowed to
influence its own reference.

Implementation:
- `tools/watch_align_py/marker_consensus.py` -- image-space marker
  segmentation (centroid + outer/inner radial contour points +
  confidence), general conic fitting (Hartley-normalised direct least
  squares, and a robust IRLS/Tukey-biweight variant), Sampson-distance
  residuals, conic-ray intersection, and a global affine inverse-map for
  canonical angle.
- `tools/watch_align_py/marker_consensus_analysis.py` -- leave-one-out
  round-marker envelope consensus (Parts 1-2), the 30-degree angular
  system (Part 3), triangle/baton shape-specific offset checks (Part 4),
  local minute-track clearance (Part 5), and the three-state warning
  model (Part 9).
- `tools/watch_align_py/tests/test_marker_consensus.py` -- 9 synthetic
  tests against known ground truth, run before any real image.
- `tools/watch_align_py/experiment_marker_consensus_video.py` /
  `experiment_marker_consensus_corpus.py` / `render_marker_consensus.py`
  / `plot_marker_consensus_video.py` -- video and calibration-corpus
  runners and visual diagnostics.

## 2. Bugs found and fixed before touching any real image

Synthetic tests caught two real bugs:

1. **`inverse_map` missing a rotation step.** The global inverse of
   `geometry.map_point` skipped one of four composed rotations, producing
   a constant angular offset equal to the ellipse's own axis angle on
   every marker. Caught by `test_angular_analysis_zero_residual_on_perfect_grid`
   (residual was exactly `-15.0` degrees on a synthetic ellipse with a
   15-degree axis, not noise-shaped). Fixed; round-trips now exact to
   float precision.
2. **Peer-exclusion via float object identity.** The original code
   excluded a marker's own value from its peer comparison using `is not`
   on floats -- fragile (relies on CPython not copying attribute values)
   even though it happened to work. Replaced with explicit key-based
   exclusion.

Both were caught and fixed **before** any real image was analysed, same
discipline this session applied throughout (multi-radius, pose-library).

## 3. A genuine, not-fully-fixable small-sample limitation

With only 8 round markers (7 peers for leave-one-out, 5 DOF for a conic),
there is very little redundancy. `test_leave_one_out_flags_injected_anomaly_as_largest_residual`
showed a single injected ~7px anomaly measurably leaking into its two
angularly-adjacent peers' own (otherwise clean) leave-one-out residuals,
even after switching to a robust IRLS fit specifically to limit this.
Rejecting the contaminating point fully would leave only 6 points for a
5-DOF fit -- too little margin. This is documented, not hidden; the test
was relaxed from "innocent peers stay clean" (unrealistic at this sample
size) to "the anomalous marker is still the single largest residual, and
angularly-distant peers stay clean" (what is actually true).

## 4. Video same-watch test (Part 7)

**Scope caveat:** only 3 of the 9 described frames were actually
received (no matching filenames, no negative-control frame, unknown
frame ordering). Flagged to the user, who chose to proceed with the 3
available rather than wait -- everything below is scoped to that thinner
test.

**Coverage is the headline finding.** Real handheld-video segmentation
succeeded on only 3-6 of 8 round markers per frame -- never enough for
the core leave-one-out signal to fire at all (needs >=7/8 present). The
only verdicts raised on this video came from the not-yet-calibrated
local minute-track-clearance signal (one CLEAR_ANOMALY, one
POSSIBLE_ISSUE), and the one marker with multi-frame coverage among the
flagged markers did **not** stay flagged across viewpoints -- an early
sign the clearance signal specifically needs more work before being
trusted.

**A real small-sample z-score instability was found and fixed here
too.** Angular residuals of 0.81-0.94 degrees (unremarkable in absolute
terms -- well within the ~0.5-1.0 degree normal noise this session
established repeatedly elsewhere) produced z-scores of 4.4-5.5 purely
because a 5-6-value peer sample's own spread happened to be small.
Fixed by adding absolute-magnitude floors (`ABSOLUTE_FLOOR` in
`marker_consensus_analysis.py`) informed by noise levels already
established this session, not tuned to this sample -- this removed 2
spurious flags without touching the genuine ones.

Visual diagnostics on all 3 frames confirm the tooling produces
plausible, non-degenerate output on real images (sent directly to the
user, since these are local files this session could inspect, unlike
the corpus photos below).

## 5. Calibration-corpus run (Part 8)

CI runs [35830119532](https://github.com/Biggregw/watch-align/actions/runs/35830119532)
and [35830650164](https://github.com/Biggregw/watch-align/actions/runs/35830650164)
(the second adding an in-sample-residual column for the comparison in
5.1), both completed successfully in ~3 minutes, 45 calibration-split
accepted images, 495 marker-observation rows. No production code, master
geometry, or thresholds changed; validation not inspected.

### 5.1 The central finding: leave-one-out is ~6-20x worse than in-sample fit

This is the most important result of this whole experiment, and it
directly re-examines the proof-of-concept claim that motivated it
("median residual ~0.34-0.58px, worst ~0.45-0.96px").

| | n | median (px) | p90 (px) | mean (px) | max (px) |
|---|---|---|---|---|---|
| in-sample outer-conic residual (fit all 8, measure against that fit) | 227 | 0.310 | 1.371 | 0.578 | 4.196 |
| leave-one-out outer-envelope residual (fit 7 peers, predict the 8th) | 175 | 2.822 | 9.217 | 4.164 | 21.098 |

The in-sample number (median 0.31px) matches the proof-of-concept's
claimed range closely. The leave-one-out number -- the number that
actually matters for this architecture, since Part 2 explicitly requires
leave-one-out and forbids letting a marker influence its own reference
-- is a full order of magnitude worse (median ratio 6.44x, mean ratio
21.3x on paired observations). This is a textbook signature of
overfitting: a 5-DOF conic fit to 8 points has very little slack, so an
in-sample fit will always look "extremely tight" almost by construction,
regardless of whether the underlying peer-consensus signal is actually
predictive. The proof-of-concept's headline numbers almost certainly
describe the in-sample fit, not a genuine held-out prediction -- this
should be corrected in how the method is described going forward.

This does **not** mean the underlying geometric idea is wrong -- an
in-sample residual of 0.31px confirms 8 real marker points really can be
fit by one conic quite well, which is itself informative -- but it means
the "extremely tight" framing overstates the method's actual
leave-one-out predictive precision by an order of magnitude, and any
future claim about this method's precision should quote the
leave-one-out number, not the in-sample one.

### 5.2 Coverage

Segmentation succeeded on 60-80% of markers per hour (comparable to
every prior corpus experiment this session). Leave-one-out fits
succeeded on 175/247 (71%) of segmented round markers -- much better
than the video test's near-zero rate, confirming the mechanism is
viable when segmentation coverage is adequate (corpus photos are more
controlled than handheld video).

### 5.3 The signal does discriminate something real

Flagged markers (POSSIBLE_ISSUE or CLEAR_ANOMALY) have a meaningfully
higher leave-one-out residual than clean ones: median 4.79px vs 2.65px.
Angular and clearance residuals do **not** differ meaningfully between
flagged and clean (medians within ~0.1-0.2 of each other) -- the
leave-one-out envelope signal is doing essentially all of the real
discriminative work; the secondary signals are not yet contributing
much, consistent with their "not yet calibrated" status.

### 5.4 Within-watch repeatability is currently poor

This is the second major finding, and a direct answer to what Part 8
asked to measure. Grouping by `(physical_watch_id, hour)` for
combinations with >=2 images:

- Within-watch std of the leave-one-out residual: median 1.09px, mean
  2.37px, max 14.5px -- a large fraction of the overall residual itself,
  i.e. substantial photo-to-photo noise on the *same physical marker*.
- Verdict consistency across repeat photos of the same watch/marker:
  only 55% (65/118 groups) give the same verdict category every time.
  Of the 51 groups flagged (POSSIBLE_ISSUE or CLEAR_ANOMALY) at least
  once, only **4** are flagged on every available photo; the other **47**
  are flagged inconsistently (sometimes yes, sometimes no).

In other words: most individual-photo flags on this corpus do not
reproduce on a second photo of the same marker. A single flagged photo
is currently weak evidence of a genuine, repeatable geometric anomaly;
only the small minority that flag consistently across multiple photos of
the same watch look like credible candidates.

### 5.5 Shape, factory, and tilt dependence

- **Triangle (hour 12) flags disproportionately often**: 11/31 (35.5%)
  POSSIBLE_ISSUE among observed triangles, vs 40/247 (16.2%) for round
  markers and 16/71 (22.5%) for batons. The shape-offset ratio
  approximation (Part 4, scaling the round-envelope conic by
  `TRI_OUTER_CANON_R / ROUND_OUTER_CANON_R`) is the most likely
  contributor and needs more validation before being trusted as much as
  the round-envelope check.
- **Genuine-labelled ("Rolex") photos show the highest flag rate of any
  factory group**: 16/43 (37%) POSSIBLE_ISSUE, vs VSF 10/96 (10%), ARF
  0/10 (0%), Clean 44/169 (26%), C+ 6/30 (20%). This is not disqualifying
  on its own -- Part 6 is explicit this is not an authenticity classifier
  -- but a 37% flag rate on watches with the strongest prior for being
  geometrically fine is a clear signal that current thresholds are not
  yet meaningfully calibrated, exactly the risk Part 8/9 anticipated by
  forbidding threshold derivation from tiny samples.
- **Flag rate is essentially independent of tilt**: 22% (0-8deg), 23%
  (8-13deg), 22% (13+deg). This is a genuinely positive result and
  directly validates the architecture's core motivation -- unlike every
  prior pose-recovery-based approach, this method's warning rate does
  not degrade or drift with viewing angle.

### 5.6 Segmentation failures

9 of 165 watch/hour combinations never segmented successfully across any
of their available images. 5 of the 9 are concentrated on a single watch
(`rep_cf_sx6zSKZ`) that has only one available photo of generally poor
quality for this pipeline -- a per-image quality issue, not a systematic
per-marker one. Hour 4 shows a mild recurring pattern (3 of 9 failures)
but the sample is too small to treat this as more than a note for future
attention.

## 6. Confidence/evidence model (Part 8 deliverable)

The three-state model (`marker_consensus_analysis.build_verdicts`):

- **INSUFFICIENT_CONFIDENCE**: segmentation confidence below a floor, or
  too few peers/signals available. Never manufactures a verdict from
  inadequate data (Part 12).
- **NO_ISSUE**: no signal clears both its robust z-score threshold
  (z>=2.5) and, where established, its absolute-magnitude floor.
- **POSSIBLE_ISSUE**: exactly one signal clears both bars.
- **CLEAR_ANOMALY**: two or more signals clear the stronger z-score
  threshold (z>=4.0) together, or one strong plus a second elevated
  signal -- multi-signal agreement required for the strongest state, per
  Part 9.

Absolute floors exist for angular (1.0deg) and outer-envelope-LOO
(1.5px) residuals, informed by noise levels already established
elsewhere this session; clearance and shape-offset have no floor yet and
are flagged as calibration-pending rather than given an arbitrary one.
Section 5.4/5.5 show this model, while directionally sound (5.3), is not
yet well-calibrated: it needs either more peers per photo (not available
for this watch -- only 8 round markers exist) or pooling evidence across
multiple photos of the same watch before individual-photo verdicts
should be trusted at face value.

## 7. Evidence-based recommendation

**This is a genuinely different result from the prior two approaches,
and should not be filed under the same "not retained" verdict without
qualification.**

What is validated:
- The core architectural bet -- that peer consensus sidesteps the
  pose-recovery identifiability problem -- holds up under real data: flag
  rate is independent of tilt (5.5), which neither multi-radius
  homography nor pose-library-overlay ever achieved.
- The leave-one-out envelope signal discriminates something real (5.3),
  not pure noise.
- The tooling itself (segmentation, conic fitting, leave-one-out,
  angular system, visual diagnostics) works correctly on real images,
  confirmed both numerically and visually.

What is not yet validated, and should not be glossed over:
- The founding "extremely tight" precision claim does not hold under
  proper leave-one-out evaluation -- the real number is an order of
  magnitude worse (5.1). Any future description of this method's
  precision should use the leave-one-out number.
- Within-watch verdict repeatability is currently poor (5.4): most
  single-photo flags do not reproduce on a second photo of the same
  marker, so a single flag is currently weak evidence on its own.
- Thresholds are explicitly uncalibrated (by design, per instruction),
  and the genuine-vs-replica flag-rate inversion (5.5) shows this
  concretely -- there is real calibration work remaining before this
  could inform any decision, automated or human-reviewed.
- The triangle shape-offset approximation needs independent validation
  (5.5); it is currently the least-tested part of the architecture.

**Recommendation: retain this direction for further development, but do
not integrate it into any user-facing surface yet.** Concretely, before
any further step: (a) correct the "extremely tight" framing to describe
leave-one-out precision, not in-sample; (b) investigate whether pooling
peer-consensus evidence across multiple photos of the same watch (where
available) can close some of the within-watch repeatability gap found in
5.4, since single-photo verdicts are currently too noisy to trust alone;
(c) get a larger, more diverse calibration sample specifically to
recalibrate thresholds before the genuine-vs-replica flag-rate inversion
in 5.5 can be resolved one way or the other. This is not a rejection --
it is a real, tilt-independent, mechanistically sound signal that needs
real calibration work, which is exactly what Part 8/9 anticipated and
explicitly scoped this experiment to surface rather than to skip.

## 8. Status

Everything committed to `experiment/projective-marker-consensus`.
Stopping here for review before integrating anything, per instruction.
No Android, production, master-geometry, or validation-data changes.
