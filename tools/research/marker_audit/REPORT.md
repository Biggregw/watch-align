# Rolex outer-metal marker investigation — 19 October 2026

**Handover checkpoint:** the owner paused further work. This report contains the
completed ten-case investigation and initial matched cohorts. The expanded Date
comparison and final No-Date memory-parity check are incomplete; exact saved
progress, remaining manifests and continuation instructions are in [HANDOVER.md](HANDOVER.md).

## Conclusion

The seconds-hand hypothesis is confirmed for both highlighted batons. The
production fitter accepted observations on the shaft while its independent hand
mask stopped short of the marker. A focused correction excludes those observed
crossings using Alpha99's independently detected seconds direction. The remaining
metal sides and short ends still determine position and rotation. The readings
change from **+2.0221° to +0.4303°** at 9 and **−2.1010° to −0.0716°** at 6;
both retain usable measurements and lose their edge-uncertainty warnings.

This does **not** explain all ten findings. Partly masked triangles expose a
second problem: a fixed outline fitted to asymmetric, systematically offset
metal edges can trade edge-size/visibility error for apparent rotation or
position. One triangle rejects every observed top-edge point. The low-resolution,
oblique round-marker photograph is unsuitable for the owner's expected QC input.
There is no independently established genuine manufacturing defect among these
ten photographs; several physical deviations remain unresolved.

No thresholds, genuine references, model routing, pose calculation or Date
visibility settings were changed. PR #62 was examined as evidence and not merged.
The Date family is **not ready to enable**. Android execution and the final merge
recommendation are recorded in the validation section below.

## Reproduction and completeness

Baseline is `cf373d205a873648fde46c9704aba177284bc920` (production 1.6.3).
The focused measurement change is `c812a83fb0f8c0823e654620f1eea7faf08eb082`.
Android execution subsequently exposed a pre-existing allocation failure;
`29c2403d6f2900c671b154482e9a32c14f62c806` corrects temporary storage without
changing the sampling coordinates or measurement definitions. Research branch:
`research/metal-marker-evidence`; [draft PR #63](https://github.com/Biggregw/watch-align/pull/63).

All **ten original photographs were recovered with their catalogued full
SHA-256 hashes**. There are no unavailable original cases in this audit.
The unchanged pipeline reproduced **every field of all 110 marker rows** from
[the original Date run](https://github.com/Biggregw/watch-align/actions/runs/39271752978).
See `results/original-ten-markers.csv`, `ten-baseline.csv`, `ten-after.csv`,
`ten.csv`, acquisition logs and `cases-summary.json`.

The initial ordinary download recovered only two of ten; explicit JPEG format
negotiation recovered the other eight. Hash mismatches were never accepted.
The old diagnostic recovered only three and its Java failure was hidden by a
pipeline ending in `tee`. This runner uses `set -euo pipefail`, mandatory image
hash checks, mandatory target coverage, and numerical trace/production parity.
Tests deliberately verify that a green-looking summary with a missing case,
missing marker, partial matched run, or conflicting duplicate fails.

`prepare_trace.py` instruments a renamed fitter, without editing its math.
`MarkerAudit.java` checks the actual polygon, rotation and coverage against the
production result to 1e-10. Every one of the four iterations records search
origins, normals, measured offsets, exclusion reasons, Jacobians, robust weights
used in the solve, subsequent quality weights, residuals and parameter updates.
The two weight arrays are deliberately separate. The annotated close-ups plot
the **observed endpoint** `origin + offset × normal`, not the search origin or
an illustrative marker polygon. They include the real reference, reconstructed
polygon, both hand detectors, accepted observations and exclusions. Missing
observations are marked only at their search origins.

The complete before/after annotated gallery and photographs are in the shared
workspace evidence bundle. Committed `results/target-traces-{before,after}.json.gz`
retain all target-marker geometry and all iterations without embedding images.
Bulk images remain outside Git; four explicitly documented public originals are
pinned as Android test fixtures because CDN responses were not reproducible on CI.

## Where the detectors disagree

1. `ClearlyOff.context` detects pivot-connected bright runs near 0.10–0.38 dial
   radii, groups directions, and follows them outward to estimate reach. It can
   stop at a dark shaft, a gap, or a low-contrast section. `MetalFit.underHand`
   applies this mask to the **nominal profile origin**, with its own width,
   reach, margin and widened tip rules.
2. `Alpha99MarkerInterference` examines structures continuing inward beyond a
   marker, connected foreign components, glare, and the seconds hand's lume dot
   and shaft evidence. Its seconds direction is determined outside the judged
   metal edge. It reports hand interference for nine of the ten targets; the
   round 5 is clean. Its generic “hand” result means possible local contact,
   not that every edge of the marker is unusable.
3. Before this correction, `Alpha99Pipeline` passed only Alpha99's clean/reason
   fields into `ClearlyOff.Input`. The outer-metal path uses `metalBase`, not
   the older path's blanket `handClean` gate. It intentionally reconstructs
   geometry from surviving edges but did not receive the seconds direction.
4. The metal profile searches ±0.02 R and selects an outer bright-to-dark
   crossing. A shaft can lie inside that search even when the nominal marker
   origin is clear. The baton shaft crossings receive positive robust weights:
   a coherent wrong edge is not automatically an outlier. “55% inconsistent”
   or “80% inconsistent” is a residual-quality warning, **not** the fraction
   removed from the calculation.

For the 9 baton, Alpha99 detects **275.532°**, while the relevant pivot mask is
278.5° with reach **0.69 R**. For the 6 baton, these are **174.186°** and 176.5°
with reach **0.58 R**. Both pivot masks end before the contaminated observations.

## The ten cases

Angles below are the app's marker rotation relative to its local reference
line. Positions are percent of dial radius. “Shaft observations” counts final
solve observations with positive weight inside the independently detected thin
shaft corridor; the count is a geometric diagnostic, not a claim that every
point was individually labelled by a human. Exact per-side counts, complete
pivot masks, Alpha99 decisions and quality metrics are in `cases-summary.json`.

| Original photo / marker | Alpha99 seconds direction | Original hand-excluded profiles / positive-weight observations | Positive observations in shaft corridor | Rotation before → after |
|---|---:|---:|---:|---:|
| 6f071d93608b9081 / 12 triangle | 2.378° | 193 / 546 | 53 | −1.2193° → −0.3589° |
| 22060f9a8f867d35 / 12 triangle | 1.197° | 359 / 334 | 0 | +0.1242° → +0.1242° |
| 800cd2600f322aa5 / 12 triangle | 5.266° | 204 / 516 | 1 | −1.4377° → −1.4372° |
| 02d7c97e429c6984 / 12 triangle | 355.958° | 235 / 498 | 0 | −1.3817° → −1.3817° |
| 482e344ce9c60e61 / 12 triangle | 4.771° | 223 / 507 | 0 | −1.6315° → −1.6315° |
| e418538d0e9af0bf / 12 triangle | 6.689° | 108 / 609 | 13 | −1.2042° → −1.3949° |
| 94ad771e9e2aedde / 5 round | none; Alpha99 clean | 0 / 128 | 0 | not applicable |
| 2802b4a30cfb277b / 9 baton | 275.532° | 92 / 532 | 131 | +2.0221° → +0.4303° |
| c11761654b519222 / 12 triangle | 355.494° | 254 / 483 | 0 | −1.5331° → −1.5331° |
| ee07ceedbb6f0bd3 / 6 baton | 174.186° | 0 / 660 | 247 | −2.1010° → −0.0716° |

**2802b4a30cfb277b — confirmed detection error.** The seconds shaft runs above
the 9 baton; accepted samples lie on its upper edge. The lower long metal edge
and short ends remain visible. Hour and minute hands do not contact this marker.
After excluding observed shaft crossings, coverage is 60.55%, the weighted
system has full rank 3, RMS is 0.172 px and inconsistent fraction is zero.
Side displacement changes +0.5246% → +0.2269%; radial +0.0478% → +0.0496%.
This is a recovered measurement, not a new “Can't check”.

**ee07ceedbb6f0bd3 — confirmed detection error.** The seconds shaft beside the
right long edge contributes 247 positive-weight observations, essentially an
entire false side. The left edge and end fragments constrain the remaining
three parameters. Hour and minute hands are elsewhere. Corrected coverage is
62.24%, rank 3, RMS 0.259 px, inconsistent fraction 14.64%, rotation SE 0.026°.
Side changes −0.8051% → −0.0397%; radial −0.0020% → +0.0638%. The measured
boundary no longer follows the seconds shaft. Both position and angle remain
available, without an edge-uncertainty warning.

**6f071d93608b9081 — confirmed contamination; residual geometry unresolved.**
The seconds hand crosses the triangle. The two ordinary hands are away from 12.
The independent masks leave 53 positively weighted observations in the shaft
corridor. Their removal changes rotation enough to remove the warning, but the
surviving fit still has 2.057 px RMS and 100% inconsistent samples. Coverage is
67.52%, rank 3. This is **not counted as a newly reliable measurement**. Metal
rim/bevel visibility and the rigid outline remain in disagreement.

**22060f9a8f867d35 — genuinely uncertain reconstruction.** The seconds hand
crosses 12 and the broad pivot mask hides much of the apex/top. Hour/minute hands
do not contact 12. The warning is **−1.4220% radial displacement**, not rotation.
Of 376 available observations, all 42 top-edge observations receive zero solve
weight; only 157 and 177 observations on the two sloping sides determine the
result. The top metal boundary is about 8 px away from that reconstruction.
Coverage is only 45.44%. The equations have rank 3 conditional on the fixed
outline, but do not independently validate its radial position against the
visible top. No accepted shaft observations explain the warning. Boundary-size,
bevel and local projection effects cannot be separated from actual position
with this single partial fit; do not label it a manufacturing defect.

**800cd2600f322aa5 — genuinely uncertain photograph/outline fit.** The seconds
hand crosses the right side, already substantially excluded by the pivot mask;
hour/minute hands are away. Only one final positive observation lies in the
additional corridor, so removing it changes rotation by only 0.0005°. Coverage
70.07%, rank 3, RMS 1.371 px, 56.12% inconsistent. Surviving metal observations
on multiple sides do not agree with one rigid outline. Shaft fitting is not
the primary explanation for this warning.

**02d7c97e429c6984 — genuinely uncertain photograph/outline fit.** Seconds
crosses the left portion; hour/minute hands do not contact 12. Existing mask
excludes 235 origins, with no positive observations left in the new corridor.
The correction is exactly neutral. Coverage 67.30%, rank 3, RMS 1.578 px,
69.68% inconsistent. Uneven visible-side support and systematic metal boundary
offset, rather than a demonstrated accepted shaft edge, explain why the angle
cannot be treated as reliable ground truth.

**482e344ce9c60e61 — genuinely uncertain photograph/outline fit.** Seconds is
along the right slope; hour/minute hands are elsewhere. Existing masking leaves
no positive observations in the additional corridor. Result unchanged;
coverage 68.70%, rank 3, RMS 1.523 px, 67.06% inconsistent. A real deviation
cannot be distinguished from the asymmetric boundary/reconstruction error in
this photograph alone.

**e418538d0e9af0bf — mixed contamination and uncertain boundary.** Seconds lies
along the right slope; hour/minute hands do not contact 12. Removing the small
remaining contaminated portion makes rotation **more negative**, not closer to
zero. Coverage becomes 81.28%, rank 3, RMS 1.885 px, 98.50% inconsistent.
This directly disproves treating all triangle warnings as seconds-shaft errors
that should disappear. The metal outline mismatch remains the dominant issue.

**c11761654b519222 — genuinely uncertain photograph/outline fit.** Seconds
crosses the left slope; hour/minute hands are elsewhere. The broad pivot mask
excludes 254 profiles and leaves no positive observations in the extra corridor.
The result is unchanged, with 65.54% coverage, rank 3, 2.190 px RMS and 80.54%
inconsistent. The left metal edge is visibly rough/unevenly illuminated and the
other sides have systematic offsets; accepted shaft fitting is not established.

**94ad771e9e2aedde — unsuitable QC input; physical deviation unresolved.**
The minute hand approaches above the 5 marker but does not intersect its fitted
outer boundary. The hour hand and seconds hand are elsewhere (seconds points
down-left). Alpha99 is clean and MetalFit masks zero profiles. The full circle
has rank 2, 100% coverage, 0.476 px RMS and 4.69% inconsistent samples, but the
dial is only **255 px across** and visibly oblique. Side displacement +1.2058%
is unchanged. Perspective/local projection and diffuse metal-edge localisation
cannot be separated at this resolution. Per the owner's review, this photo is
excluded from primary QC accuracy evidence, while retained in the original-ten
audit. It is not an Android regression fixture.

## Alternative causes and geometric sufficiency

The before/after homography is bit-for-bit identical for every original case.
Thus the two baton improvements are caused by sample selection, not an altered
pose or reference. That does not certify the original pose as physically exact.
Obliquity is a material alternative for the excluded round-marker photo.

All target normal systems have full algebraic rank (3 for triangles/batons,
2 for circles), including after exclusion. Rank alone is insufficient: a rigid
shape can be numerically identifiable while the observed metal does not conform
to that shape. Likewise hundreds of half-pixel-spaced samples on a single edge
are correlated, and small formal standard errors do not cover systematic bevel,
reflection, shape-size or perspective error.

The production fitter estimates a per-photo edge offset from >90%-visible
markers of the same shape. A partially masked single 12 triangle has no such
fully visible peer and its shape offset is zero. The traces show coherent
positive normal residuals on multiple triangle sides, rather than just isolated
rough pixels. A controlled local sensitivity calculation retains the actual
Jacobian/weights and applies a uniform **2 px outward normal offset**: the
02d7 triangle geometry converts it into **−1.35°** apparent rotation; 800cd
converts it into −1.02°; 482e into −1.00°. For 22060 it becomes **+1.24% R in y**
with almost no rotation. These are diagnostic perturbations, not estimates of
the true physical defect. They demonstrate the mechanism by which partial
visibility plus fixed shape can produce the observed type of warning.

This evidence does not justify changing genuine references, adding a scale/pose
parameter, raising thresholds, or automatically clearing all rough triangles.
A separate boundary/shape-validity investigation is needed for those residual
cases. No reference was silently substituted between model families.

## Focused correction and tests

`Alpha99Pipeline` now passes the finite seconds direction into `ClearlyOff.Input`.
`MetalFit` tests the actual interpolated crossing against a forward ray from the
dial centre, with half-width **0.006 R + 0.75 px**: the existing Alpha99 shaft
half-width plus its strip sampling width. The direction requires independent
dot/shaft evidence outside the marker. A radial baton edge alone cannot create
this evidence. No new general-purpose hand detector was added.

Only contaminated observed crossings are excluded. There is no blanket rejection
when Alpha99 reports a nearby hand, no broad reference-origin seconds mask, and
no hour/minute tip widening applied to this thin shaft. Existing fit sufficiency
and uncertainty rules decide whether the remaining geometry can be measured.
This preserves the short-edge fragments erased by the experiment in PR #62.

Ten focused unit tests cover an aligned marker with an adjacent shaft, a real
3° baton defect beside the shaft, known translation, a central overlapping
shaft, the exact previously failing broad-mask fixture, a nearby noncontacting
line with identical output, a single insufficient surviving edge, a genuinely
rotated triangle, a translated round marker and absent/opposite-ray evidence.
The 1.75° baton and 1.2° triangle production limits remain unchanged.

## Matched genuine regression

Every image in each frozen cohort is rechecked against SHA-256. Comparison keys
are identical `(photo_id, marker_hour)` pairs, with exactly the selected model's
expected hours. Both runner logs must account for every input as measured or
explicit `NOPOSE`. A duplicated GMT image listed under two watch aliases is
counted once, with aliases retained. Missing runs, missing hours, differing
availability or conflicting duplicates are fatal.

Following the owner's angle correction, all 328 assessed originals were reviewed
as plain-image contact sheets without overlays or measurement labels. Obviously
oblique product/handheld views were excluded from primary QC results. The existing
400 px dial-diameter quality floor was also applied. The complete decisions and
reasons are in `results/*-suitability.csv`; this is an offline dataset review,
not a new application gate. In-plane rotation alone is not a poor camera angle.
Normal photos with local hand contact or reflective edges remain in the primary
set, so the difficult measurement problem is still tested.

Primary suitable-photo comparison (warnings = Possible + Clearly off):

| Family | Photos / independent watches | Markers | Warnings before → after | Possible / Clear after | New Can't check | Without uncertainty before → after / retained |
|---|---:|---:|---:|---:|---:|---:|
| GMT 126710 | 50 / 33 | 550 | 0 → 0 | 0 / 0 | 0 | 474 → 474 / 474 |
| No-Date 124060 | 112 / 75 | 1,344 | 4 → 4 | 3 / 1 | 0 | 1,241 → 1,241 / 1,241 |
| Date 126610 | 124 / 124 | 1,364 | 9 → 6 | 6 / 0 | 0 | 1,281 → 1,284 / 1,281 |

“Without uncertainty” means the existing app reports no gate, no uncertainty and
not Can't check. It is an operational metric, **not independently certified
measurement accuracy**; the existing No-Date clear finding is included in it.
All **2,996** previously uncertainty-free measurements remain so, and no primary
marker becomes Can't check. Of the three Date warning removals, only the two
batons become uncertainty-free. The third is the still-rough 6f071 triangle.

The additional uncertainty-free Date marker is the 9 baton in ee07: removing the
contaminated 6 from the existing >90%-visible bias pool changes the same-shape
edge offset from 1.4553 to 0.3224 px. Its RMS falls 1.1133 → 0.1186 px; it was
already passing. Its rotation changes only −0.0743° → −0.0751°, radial position
+0.1165% → +0.1016%. This is the existing per-photo edge-offset calculation,
not averaging defect decisions across a watch.

| Family / primary shape | Marker count | Warnings before → after | Uncertainty-free before → after |
|---|---:|---:|---:|
| GMT baton / round / triangle | 100 / 400 / 50 | 0 / 0 / 0 → unchanged | 98 / 326 / 50 → unchanged |
| No-Date baton | 336 | 0 → 0 | 329 → 329 |
| No-Date round | 896 | 3 → 3 | 801 → 801 |
| No-Date triangle | 112 | 1 → 1 | 111 → 111 |
| Date baton | 248 | 2 → 0 | 231 → 234 |
| Date round | 992 | 0 → 0 | 938 → 938 |
| Date triangle | 124 | 7 → 6 | 112 → 112 |

Maximum absolute position changes in the primary set (percentage points of R):
GMT side 0.1735, radial 0.0360; No-Date side 0.0159, radial 0.0123; Date side
0.7654, radial 0.0658. Maximum rotation changes: GMT zero, No-Date 0.0849°,
Date 2.0294°. The two Date baton corrections account for the large rotation and
side changes. Exact deltas, largest changes and per-shape counts are recorded
in the comparison JSON files, not inferred from warning counts.

Full recovered-cohort results, including unsuitable photographs:

| Family | Catalogue rows / verified unique photos / assessed photos | Markers | Pass / Possible / Clear / Can't check before → after |
|---|---:|---:|---|
| GMT | 126 / 76 / 66 | 726 | 720 / 2 / 0 / 4 → 721 / 1 / 0 / 4 |
| No-Date | 410 / 294 / 135 | 1,620 | 1,606 / 4 / 1 / 9 → unchanged |
| Date | 358 / 128 / 127 | 1,397 | 1,386 / 10 / 0 / 1 → 1,389 / 7 / 0 / 1 |

There are zero new Can't-check classifications and zero lost uncertainty-free
measurements even in the full cohort. The GMT warning that disappears belongs
to the excluded oblique, 241 px dial image `66c4527d45cf91b0`; its remaining
edge uncertainty worsens, so it is not claimed as an accuracy gain. The Date
initial comparison is smaller than the original 219 assessed photos; **all original ten
targets are present**, but this is not a claim to have rerun all 219. Subsequent
recovery obtained 356 verified Date images, including all original 219; the
expanded rerun was checkpointed at the owner's request. See HANDOVER.md rather
than treating the initial cohort as the final available dataset. All results
compare identical bytes and marker hours; there is no cross-run denominator swap.

## Defect controls and outstanding discrepancies

All seven existing SHA-pinned public QC examples in `metal-163.yml` were recovered
and run before/after with their specified family. All 81 marker classifications
are unchanged. The rejected No-Date 12 remains Possible at +1.2577°. Existing
findings in the control images also remain; control/accepted labels are not
treated as metrological ground truth for every other marker.

The labelled `GMT_crooked6_1s4cszq` example is **not detected by either version**:
both measure +1.4134°, below the unchanged 1.75° baton threshold. This is an
existing limitation, not evidence that this correction preserves detection of
every labelled manufacturing defect. The synthetic 3° baton and 2° triangle
tests establish preservation of independently known rotations with nearby hands.
There is no recovered Date-specific labelled real-defect control; its defect
preservation evidence is synthetic and shared-code evidence only.

Remaining genuine discrepancies and Android execution details are completed in
`VALIDATION.md`. Neither a green workflow nor reduced warning counts alone is a
merge/enablement criterion.
