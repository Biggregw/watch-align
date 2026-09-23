# Proportional Geometry - Targeted RepTimeQC Evidence Refresh

Status: live supplemental research input

Date: 2026-09-23

Purpose: use recurring GMT-Master II QC concerns reported on r/RepTimeQC to prioritise which proportional/geometric rules Watch Align should prove first. This is qualitative prioritisation evidence only. It is not a prevalence study, defect threshold, authenticity signal, or substitute for calibration data.

## Important sampling caveat

Reddit QC posts are strongly selection-biased. Users usually post because they already suspect an issue, and commenters may disagree. Therefore this document records recurring *types of geometric concern*, not defect frequencies.

The useful question is:

> Which visually reported QC concerns can be represented by simple, viewpoint-aware, dimensionless or projective geometry?

## Targeted GMT evidence reviewed

The following GMT-related RepTimeQC threads were reviewed in the current pass:

- `1w947p6` - VSF 126710BLNR: concern that 12 marker is shifted left / not centred.
  https://www.reddit.com/r/RepTimeQC/comments/1w947p6/12_oclock_marker_alignment_concern/

- `1uqwo43` - GMT-Master II 126710: commenter identifies small 12 CCW rotation, 9 CCW rotation, and possible 6 left shift, while explicitly noting perspective may explain part of the 6 observation.
  https://www.reddit.com/r/RepTimeQC/comments/1uqwo43/1st_qc_gmtmaster_ii_126710_rich/

- `1u91yef` - VSF 126710BLNR: 12 marker described as slightly left / counter-clockwise, with uncertainty about photo angle.
  https://www.reddit.com/r/RepTimeQC/comments/1u91yef/qc_request_vsf_batgirl_first_qc_any_help/

- `1w4l2af` - VSF 126710BLRO: specific concern that the 6 baton is rotated/crooked, with uncertainty about perspective.
  https://www.reddit.com/r/RepTimeQC/comments/1w4l2af/qc_vsf_gmtmaster_ii_pepsi_126710blro_first_vsf/

- `1wo4gv3` - VSF 126710BLRO: 6 appears tilted left, cyclops possibly right-shifted, rehaut concern, and commenter also notes slight 6/9 tilt plus possible bezel-12 mismatch.
  https://www.reddit.com/r/RepTimeQC/comments/1wo4gv3/first_qc_vsf_gmtmaster_ii_v3_126710/

- `1ugosh6` - VSF Batgirl: user suspects 12/6/9 rotation and cyclops centring; also notes image tilt may explain apparent marker errors.
  https://www.reddit.com/r/RepTimeQC/comments/1ugosh6/qc_rolex_gmt_master_batgirl_date/

- `1ufx4uq` - VSF 126710: user reports markers looking slightly off but explicitly suspects the watch is tilted in the source photo.
  https://www.reddit.com/r/RepTimeQC/comments/1ufx4uq/qc_first_time_buyer_gmt_master_ii/

- `1u4s05q` - VSF 126710: 12 marker described as shifted clockwise; rehaut coronet and Swiss Made alignment also raised.
  https://www.reddit.com/r/RepTimeQC/comments/1u4s05q/qc_vsf_gmt_master_ii_126710/

- `1gmt2de` - Clean 126711: cyclops appears canted relative to date aperture.
  https://www.reddit.com/r/RepTimeQC/comments/1gmt2de/root_beer_qc_cyclops_issue/

- `1czisly` - Clean 126710BLRO: crooked cyclops / date-aperture relationship raised as the main concern.
  https://www.reddit.com/r/RepTimeQC/comments/1czisly/

- `1sgxgxh` - cyclops alignment discussion: commenters explicitly distinguish true cyclops/crystal rotation from an apparent horizontal offset caused by camera angle and recommend extra views.
  https://www.reddit.com/r/RepTimeQC/comments/1sgxgxh/datejust_misaligned_cyclops/

The last item is not a GMT-only thread, but it is directly relevant to the viewpoint false-positive problem for crystal/cyclops geometry.

## Research conclusion from the refresh

The initial proportional-geometry work should focus first on *dial-plane marker position and orientation*, because this is where recurring QC concerns map most cleanly to the existing Watch Align geometry engine.

The highest-priority recurring patterns are:

1. 12 triangle lateral shift / rotation,
2. 6 baton rotation / lateral shift,
3. 9 baton rotation / lateral shift,
4. 12 triangle radial high/low placement,
5. photo-perspective false positives that mimic 12/6/9 misalignment.

Cyclops/date and bezel concerns are common enough to justify later modules, but they should not distract the first proof because they involve different physical planes or moving parts.

## Priority metric map

### P0 - marker angular/orientation rules

Why first:

- RepTimeQC repeatedly discusses 12, 6 and 9 as rotated/tilted.
- Existing Watch Align research found angular measurements materially more repeatable than radial measurements as tilt increases.
- These rules can be expressed without absolute scale.

For 12 triangle:

- symmetry-axis angular residual relative to the independently predicted 12 hour direction,
- base-line tangent residual,
- apex/centre/base tangential residual pattern.

For 6/9 batons:

- long-axis residual relative to independently predicted 6/9 hour direction,
- inner-end tangential residual,
- centre tangential residual,
- outer-end tangential residual.

Interpretation decomposition:

- all endpoint residuals same sign and similar magnitude -> lateral translation-like,
- endpoints opposite sign around near-correct centre -> rotation-like,
- centre shifted plus endpoint slope -> compound translation + rotation.

This decomposition should be generic for any elongated marker shape.

### P1 - 12 radial placement rules

Why next:

- subtle high/low 12 placement is visually important but much more perspective-sensitive.
- the current user watch is a useful later test case, but must not define the baseline.

Candidate robust measurements:

- apex canonical/projective radius,
- centre canonical/projective radius,
- base canonical/projective radius,
- common-mode radial residual of apex/centre/base,
- differential radial residual across apex/centre/base,
- base-to-minute-track relationship in a projectively or locally normalised coordinate.

Interpretation decomposition:

- apex/centre/base all shifted outward similarly -> whole-marker outward translation candidate,
- all inward similarly -> whole-marker inward translation candidate,
- centre near expected but apex/base span different -> size/shape candidate,
- mixed pattern -> compound issue or poor segmentation.

### P2 - round-marker anomaly rules

Even though Reddit discussions concentrate on 12/6/9, round markers can provide a strong generic self-consistency check.

For each round marker under test:

- exclude it from peer fit,
- predict centre/inner/outer radial position from remaining round markers,
- compare observed centre, diameter and edge positions,
- retain signed tangential offset from nominal hour axis.

This can expose isolated shifted round markers without a model-specific artwork template.

### P3 - date/cyclops local geometry

Community QC repeatedly raises:

- canted cyclops,
- cyclops centre offset,
- date glyph high/low centring.

However the cyclops sits above the dial plane and may be attached to a crystal that itself is rotated. Camera angle and refraction therefore matter.

Initial candidate descriptors for a later module:

- cyclops long-edge angle minus date-aperture long-edge angle,
- cyclops centre offset normalised by aperture width/height,
- date glyph centre and margin ratios inside the aperture.

Do not assume the expected cyclops/date angular difference is exactly zero in arbitrary oblique views until genuine multi-view calibration demonstrates it.

### P4 - bezel geometry

Community QC also raises bezel 12/pip alignment and colour-transition alignment.

Separate two questions:

1. internal bezel geometry - triangle/pip/engraving/colour transition relative to the bezel's own 24-hour scale,
2. displayed bezel-to-dial alignment - bezel 12 relative to dial 12.

The first is a manufacturing geometry question. The second is partly a bezel-position/play question and should remain advisory unless centred click position is controlled.

## False-positive controls that should be explicit in every marker rule

### F1 - source-photo tilt

Several reviewed threads explicitly contain uncertainty of the form "marker looks off, but the photo/watch may be tilted".

Therefore:

- never compare markers to screen vertical/horizontal,
- derive expected projected hour directions from the dial geometry,
- measure residuals against those projected directions,
- retain apparent tilt and pose confidence with every result.

### F2 - marker self-reference

The marker under test must not materially define the expected geometry used to judge itself.

For 12/6/9, expected geometry comes from independent peer/global references plus the model profile.

For round markers, use leave-one-out peer fitting where practical.

### F3 - shape vs position

Do not collapse all visible mismatch into one "misalignment" score.

For every special marker keep separate components for:

- radial translation,
- tangential translation,
- orientation,
- size/span,
- asymmetry.

A good human-facing overlay should show which component differs.

### F4 - perspective-sensitive size measures

Width/height and raw radial gaps may change with viewpoint. Use them only after local rectification or empirical tilt-stability testing.

## Recommended first research sequence after corpus expansion

Once enough independent calibration genuine GMTs are available:

1. prove 12 symmetry-axis and tangential residual repeatability,
2. prove 6 baton long-axis / endpoint residual repeatability,
3. prove 9 baton long-axis / endpoint residual repeatability,
4. compare simple vs projective 12 radial common-mode residual,
5. test 12 span/shape residual separately from translation,
6. test round-marker leave-one-out residuals,
7. only after those succeed, begin cyclops/date local geometry.

The reason for this order is empirical risk: angular marker rules directly target recurring community concerns while being less vulnerable to the known radial/perspective failure mode.

## Suggested feature-priority flag for experiments

Candidate-feature output should include a research-priority tag:

- `P0_marker_orientation`
- `P1_special_marker_radial`
- `P2_round_marker_peer_anomaly`
- `P3_date_cyclops`
- `P4_bezel`

This makes it easier to retain a broad feature catalogue while concentrating validation effort on defects users actually report.

## What this evidence does NOT justify

This Reddit review does not justify:

- prevalence estimates,
- pass/fail thresholds,
- authenticity judgments,
- factory rankings,
- treating a single comment as ground truth,
- tuning geometry to reproduce a Reddit opinion.

The value of the Reddit evidence is only to prioritise *which geometric hypotheses deserve calibration testing first*.
