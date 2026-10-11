# Single-photo inspection evidence review — 11 October 2026

Protocol v0.3. Continuation of draft PR #52 on
`feature/marker-mismatch-overlay-consistency`; before source `b4b21f98`.
Read the supplied `WatchAlign_Codex_Inspection_Evidence_2026-10-11.zip` and
`README_FIRST.md`, the [overlay report](marker-mismatch-overlay-consistency-2026-10-11.md)
and [reference investigation](alpha106-yellow-template-parity-2026-10-11.md).
No photos or derivative crops are committed.

## Review and bounded implementation

Reliable improvements now are **reporting corrections**, not a new detector:

- MINOR readings exceed the observed genuine maximum but were included under
  “Within measured genuine range” and printed as “WITHIN (too small to see)” in
  technical details. They now have a separate **outside genuine / below visibility
  guideline** line. The headline and detail stop asserting that such deviations
  cannot be seen. Status enums, green non-highlighted badges, tile selection,
  comparison maths and the existing visibility bars remain unchanged.
- CLEAR remains CLEAR. The explanation now distinguishes strong measurement
  evidence from a decision that a difference is visually unacceptable. No new
  acceptance bands or GL/RL decision are introduced.
- The opposing-edge check measures fitted separation, not the cause of its error.
  Its former wording asserted one stationary edge, a dark reflection and tracing
  the lume. Now it reports the measured inconsistency and allows **boundary
  localisation or real dimensional variation** as alternatives. Position/area
  withholding and rotation assessment remain unchanged.
- Long-edge diagnostics now say “fitted long-edge angles vs scoring reference,”
  rather than claiming cyan lines are visible when the area overlay is withheld.
- The 12's “track” sentence now describes a **position-based spacing estimate**,
  not a direct visible metal-to-tick gap. The 3% R master gap is labelled as model
  geometry. Its existing genuine comparison, visibility bar and WORTH cap remain.
- Outline text explicitly calls the percentage geometric fit area, with separate
  photographic boundary uncertainty. Scoring polygons, orange extra, magenta
  missing, projection and crop transforms are unchanged.

Better physical edge localisation, independently trustworthy width/length/area,
a directly measured triangle gap, and validated human acceptance tolerances still
lack sufficient evidence. No detector, pose, reference/calibration, uncertainty,
threshold, classification or unrelated feature is changed. One input photograph
remains sufficient; no scores are averaged across markers.

## Inputs and provenance

Three independent genuine watches and one replica were inspected. Synthetic
variants remain repeats of their original watch, never additional genuine samples.
The genuine controls are already in the repository's provenance catalogue; none
is newly inserted into calibration.

| Input | Physical watch / source | SHA256 |
| --- | --- | --- |
| Pepsi `3cf5442ab9c87b9c` | Phillips CH080120 lot 75, `gen_126710BLRO_phillips_CH080120_75`; [source](https://phillips.com/detail/rolex/CH080120/75) | `3cf5442ab9c87b9c0b9481fd0eb2b92316a0287581635c49fc9cf6cdc4f31a00` |
| Batgirl `batgirl_37828` | Owner's original `37828.jpg`, replica; no independent acceptance label | `c50e0845ddb6c50701a4ba8ab6b396092e67988a747206f77743c02e7c7e54eb` |
| WOS `a5431af6379b78a5` | Rolex CPO `wos_cpo_40616911`; [source](https://watchesofswitzerland.com/collections/rolex-certified-pre-owned/products/gmt-ii-m126710blnr-0002-40616911) | `a5431af6379b78a5233303aeec379b30ef8a5c46ffa70be67a55a1126de8c138` |
| Held-out Phillips `22075296f80ce8ce` | NY080121 lot 35, `gen_126710BLRO_phillips_NY080121_35`; [source](https://phillips.com/detail/rolex/NY080121/35) | `22075296f80ce8cea1d8e555a8ad866bf1148ba8e2d68f1d7ccc7c1833327446` |

All genuine original hashes match the recorded catalogue. NY lot 35 and WOS
were in the prior **9-marker shape research hold-out**, grouped by physical watch
with the `mask-v1:` split. This is not a claim that they were absent from every
historical production calibration. Other resolutions of lot 35 or lot 75 are not
counted separately. An attempted restoration of dealer photo `71bd35628e4ed6ac`
failed with HTTP error and was excluded, not replaced with an unverified image.

Case B's 8.039% / 2.823% extra / 5.216% missing and approximately 1.3° CW are
**screenshot observations only**. The original is missing; no independent rerun,
proven source identity or human-acceptance validation is claimed. Case D's 558-px
observation is absent from the pack and has unverified provenance; it contributes
no genuine sample or numerical test.

## Preserved numbers and component reliability

[Exact before/after exports](inspection-reliability-results-2026-10-11.csv) include
every marker, vertices where exported, canonical areas, raw rotation, position,
ring-adjusted score and withholding reason. All **154 rows / 14 inputs** are
identical before/after at exported precision, including all fields beyond scores.
Four inputs are originals and ten are synthetic variants. Values with a withheld
reason are offline diagnostics, never displayed defect percentages.

Representative filled-template results (before = after):

| Watch / marker | Extra % | Missing % | Total % | UI area |
| --- | ---: | ---: | ---: | --- |
| Pepsi 9 baton | 0.54416046 | 1.87711801 | **2.42127847** | measured |
| Pepsi 12 triangle | 14.90874048 | 0.00000000 | 14.90874048 | withheld |
| WOS 6 baton | 2.36665621 | 0.00000000 | 2.36665621 | measured |
| WOS 9 baton | 1.73731739 | 0.52804111 | 2.26535850 | measured |
| WOS 1 round | 0.12162987 | 1.36377514 | 1.48540501 | measured |
| NY lot 35 9 baton | 1.03889229 | 1.37062670 | 2.40951900 | measured |
| NY lot 35 1 round | 0.61729944 | 0.55317588 | 1.17047532 | measured |
| Batgirl 9 baton | 4.21495091 | 7.22044186 | 11.43539277 | **withheld** |
| Batgirl 12 triangle | 17.74491831 | 0.00000000 | 17.74491831 | **withheld** |
| Batgirl 1 round | 3.72282027 | 0.00000000 | 3.72282027 | measured, no severity |

The Pepsi's raw 9 rotation remains **−0.19115108°**. Its genuine-nominal-adjusted
comparison is −0.24912108°, also WITHIN; these are different existing readouts,
not a correction. The 2.421% area result is not forced toward zero.
The Pepsi 12 screenshot records +0.13% R closer to the track; this original's
current frozen-pipeline radial comparison is −0.037135% R (further direction,
not assessed). Its angular/lateral finding remains WITHIN and area withheld.
The screenshot's 12 spacing input is not independently reproduced here; no fit
or reference is changed to reproduce it.

The supplied Batgirl original is 1152 × 1536, R **269.63581426 px**, whereas the
app screenshot records R about 225 px. The app and harness retain originals below
a 3200-px long side; this photograph is not automatically reduced to 225 px by
that rule. The exact screenshot input/decoding path is not established.
Its recorded **1.37° CCW / CLEAR** remains valid screenshot evidence and is pinned
as such in a regression. It is not relabelled from a rerun of different pixels.

For the original's fit, raw 9 rotation is −1.11663532°, the genuine-adjusted
comparison **1.17460532° CCW**, WORTH under the unchanged allowance. Both scoring-
relative long-edge angles are CCW: upper **1.17°**, lower **1.25°**. Position and
area remain withheld. This is directionally credible tilt evidence; neither
agreement nor exceeding the genuine maximum proves normal-view unacceptability.
The original's 6 raw rotation is +0.96085677° (genuine-adjusted +0.92022677°),
outside the genuine maximum but below the 1° visibility guideline. Its MINOR
classification is preserved and its reporting now states the outside comparison.

### Boundary audit

The production detector searches for the **outermost significant** falling
transition (not simply the strongest gradient): local peaks at least 0.4 of the
largest gradient, followed by wide/narrow searches, RANSAC/TLS lines or circle fit,
coverage and inside/outside integrity tests. Consistency checks then independently
withhold boundary-dependent QC components. Rounds with partial arcs retain the
existing fixed-radius position fit and never obtain an area score from that fit.
Triangles retain robust angular/lateral evidence while radial/area evidence is
lighting-sensitive. No new measurement-confidence threshold is invented.

On original Batgirl 9, mean fitted length/width are approximately **85.050 / 30.063
canonical px**, versus scoring reference **82.261 / 32.041 px**. Relative to the
genuine width-consistency nominal, the deficit is **2.430 px**, beyond the existing
**1.602 px** limit. This check does not identify one physical edge independently.
Width and position are unreliable; area inherits that uncertainty. Length also
depends on the two short-side fits and has no independent accuracy validation.

Raw crops and five intensity profiles per side were inspected without outlines.
The lower edge's dominant transition is within −0.10 to +0.25 px of the fitted
line; weaker further-out peaks lie at +3.15 to +4.05 px, roughly 11–21% of the
dominant gradient, below the detector's existing significance criterion. The upper
edge's main transition is within −0.20 to +0.15 px, with distinct inner transitions
around −5.25 to −5.75 px. A faint outer polished cue is plausible on the lower
side; these samples cannot distinguish a physical boundary from reflection/halo
with quantified accuracy. Selecting those weak peaks or padding the fit would
not be independently justified.

Profiles use the existing spline sampler and frozen H, so they audit detector
selection but are **not independent metrology ground truth**. The crop inspection
is also not a manually traced boundary with subpixel uncertainty. Original Batgirl
round 1 visually follows an outer circular transition, with a distinct inner lume
edge; this does not establish the accuracy of its residual 3.723% area. Genuine
Pepsi, WOS and NY controls also retain nonzero baton/round mismatches. No validated
replacement edge method or global undersizing correction follows from this run.

## 12 spacing: actual evidence and limits

The original Batgirl's genuine-adjusted radial shift is **0.68478985% R closer**
to the track, versus the unchanged recorded maximum **0.3179% R**. The estimate is
WORTH, never CLEAR; its allowance is unavailable. Neighbouring tick ends in the
raw 12 crop help a human inspect clearance, but the bright polished upper band
still has multiple plausible boundaries. The user finds it ambiguous; no rejection
label is assigned.

An offline check of the fitted **base midpoint** to the **nominal circular track
inner radius** gives **1.35265889% R** (about 3.647 px); the scoring-reference base
gives **2.93967430% R** (about 7.927 px). These are unvalidated geometric gaps to a
model circle, not directly detected physical metal-to-tick clearances. Their
difference is not the centroid-based 0.6848% metric: side fit, shape and reference
placement matter. Therefore subtracting 0.6848% from the 3% master gap and calling
the result the actual physical gap would be unjustified.

The Pepsi's corresponding fitted/reference base gaps are **2.64393728% /
3.32331812% R**, also unvalidated; its area is withheld. No genuine absolute-gap
distribution using this base-to-detected-ticks definition exists here. The existing
radial comparison is preserved, the model 3% gap is labelled correctly, and a
validated actual physical gap is not claimed. Future work needs independently
resolved outer bases and local tick endpoints on held-out genuine references.
Gap evidence never enters filled-marker area mismatch.

## Single-original repeatability checks

Each original was independently rerun after Pillow BOX resizing to widths 960
and 720, brightness ×0.92 / ×1.08, and Gaussian blur radius 0.4; transformed files
were saved losslessly as PNG. Brightness is a limited synthetic stress, not a
simulation of changing the direction of polished reflections. All ten variants
passed pose. They reuse their original physical-watch ID.

- Pepsi 9 raw angles span −0.2555 to −0.1800°; measured filled-area mismatch spans
  **2.23977862–2.43943634%**, versus original 2.42127847%. This narrower chosen
  perturbation set does not supersede the prior 2.240–3.036% stress evidence.
- Batgirl 9 raw angles span **−1.55224946 to −1.11775844°**; all remain CCW and all
  keep position/area withheld. Brightness alone changes the raw angle by less than
  0.002°; resizing changes it appreciably. Raw withheld areas span approximately
  11.425–12.601%; repeatability cannot make these reliable physical scores.
- Batgirl's 12 radial estimate spans **0.678–1.014% R** after subtracting the same
  genuine nominal; all area scores remain withheld. Resolution sensitivity and
  ambiguous boundary location prohibit a confirmed physical gap defect.
- Two supplementary 960 × 1280 **BILINEAR** copies, lossless PNG and JPEG quality
  95, give R 224.760 / 224.761 px and raw 9 angles −1.13947441 / −1.16219510°.
  These do not reproduce the screenshot's exact 1.37° input and are not substituted
  for it. They are supplementary observations outside the 154-row paired comparison.

These are repeatability observations, not physical truth, between-watch tolerances
or evidence for an acceptable/unacceptable threshold. Case B's original and
independent human decisions, and a reliably traced physical boundary, remain gaps.

## Tests and reproduction

- JDK 17, committed wrapper, `:app:testDebugUnitTest`: **360 tests, zero failures
  or errors**. The full suite includes existing GMT and Submariner regressions.
- Six reporting tests cover outside/subtle wording, separate summary lists,
  preserved screenshot CLEAR rotation, exact original Batgirl fit / retained
  rotation / area withholding, position-based spacing wording and technical text.
- One additional corner test retains subpixel extra/missing areas without padding.
  Existing genuine/round/triangle polygon and area-partition tests remain green.
- Production renderer through the desktop Canvas shim: **12 checks passed**.
  Real raw/outlined Batgirl 9, 12 and round 1, Pepsi 9/12 and NY 9 crops were
  inspected locally. Mathematical colour placement is exact for the fitted
  polygons; it cannot establish the photographic physical edge.
- All **154 exported marker rows** identical before/after. All **42 QC statuses**
  for original Pepsi, Batgirl and WOS identical before/after. Wording changes only;
  the Batgirl screenshot result is a distinct input observation.
- No APK, merge, Android device navigation or new acceptance calibration.

Reproduce on locally restored originals with `MarkerMismatchBatch <manifest.csv>
<image-root> <out.csv>` and `Alpha99Preview <manifest.csv> <image-root> <outdir>
--no-images` through `tools/desktop-harness/run.sh`. Manifests need `photo_id`,
`physical_watch_id`, `local_path`. The results CSV records each input SHA256.
`MarkerEdgeAudit <original.jpg> <outdir> [hour]` now supports batons, triangles and
rounds and exports fitted geometry, retained component measures, raw/scoring crops
and intensity profiles. Its triangle base-gap values are explicitly unvalidated.
Use `MarkerMismatchVisualCheck <outdir>` for the rendering/store assertions.
All drivers run the existing single-photo pipeline and frozen model assets.

## LESSONS LEARNED

The clearest demonstrable fault was presentation: “no highlighted finding” and
“within genuine distribution” were conflated. Correcting that requires no new
threshold. The boundary-consistency rule also inferred a specific lighting cause
that its scalar width test cannot prove. Parallel edges can retain tilt evidence
while translation, size and area remain uncertain.

The 12 radial statistic is not the physical base-to-tick gap. Nearby ticks help
inspection but do not remove polished-boundary ambiguity. Resolution and
resampling must be recorded before claiming a screenshot was independently
reproduced. Neither this small repeatability set nor the existing visibility
guideline establishes wrist-view acceptability. A detector replacement needs
independent boundary accuracy on held-out genuine and replica controls; a severity
rule needs independent acceptance/rejection evidence. No protocol/reference is
promoted or changed in this run.
