# GMT fixed-master perspective overlay checkpoint

Updated: 2026-10-05
Branch: `feature/android-gmt-perspective-overlay-poc`

## Objective

Prove or disprove a simple QC model for current Rolex GMT-Master II 126710 dial geometry:

1. Build one immutable canonical overlay from proven genuine-watch geometry.
2. Infer only the camera pose/perspective from the candidate photograph.
3. Apply that one camera transform to the untouched genuine master.
4. Draw the transformed genuine master over the candidate.
5. If the candidate is geometrically correct, the master should naturally coincide with it.
6. If the candidate contains a defect, the fixed genuine master must remain at the genuine position and the candidate defect must remain visibly different.

The overlay must never be reshaped, rotated, translated or locally adjusted to fit a judged candidate feature after pose has been estimated.

This is an inspection proof, not an authenticity classifier. A replica with no detectable deviation in the inspected geometry may legitimately align with the genuine master.

## Non-negotiable separation of responsibilities

### Genuine template geometry

The canonical master is fixed and comes only from genuine-reference geometry. Candidate-image features must not alter it.

Current master source: `Gmt126710BlnrMaster`.

Current master geometry includes:

- physical dial edge;
- 60 exact minute positions at 6 degree intervals;
- eight round hour-marker surrounds;
- 6 and 9 baton surrounds;
- 12 triangle surround;
- canonical hour axes/radial construction rays used only for visual proof.

The current marker geometry was measured from the official current-generation 126710BLNR image and cross-checked against an independent genuine-watch photograph. It is a Watch Align inspection master, not Rolex factory CAD.

### Candidate pose evidence

The candidate photograph may supply only camera-pose evidence.

The intended primary pose evidence is the printed minute track:

- 60 canonical minute positions;
- exact 6 degree spacing in the canonical dial;
- exactly 30 opposing pairs: `0<->30`, `1<->31`, ... `29<->59`;
- normal minor ticks and hour-position printed tick/stub geometry are different detector classes;
- only clearly detected real ticks are used;
- a missing or uncertain tick is never invented;
- only complete opposing pairs enter the solve;
- pairs must be distributed around the dial;
- weak/dirty pairs are rejected as whole pairs;
- if evidence is insufficient, the photo must be rejected rather than using a weaker fallback.

The physical black-dial boundary may be used as a coarse seed and physical plausibility guard. It must not use hour-marker brightness or another judged dial feature to choose the dial.

### Features forbidden from steering the fit

These features are independent QC checks and must not influence pose or post-fit alignment:

- 12 applied triangle;
- eight round hour markers;
- 6 and 9 applied batons;
- date/cyclops;
- text;
- hands;
- bezel geometry.

There must be no manual nudge in the proof build and no post-fit optimisation that uses these features to reduce residual error.

## Why opposing minute pairs matter

The canonical dial has 30 exact diameter pairs. On the flat genuine master, each pair lies on one diameter through the dial centre.

Perspective changes apparent spacing and apparent angles in the photograph, so the image itself should not be tested by assuming visible 6 degree gaps or visible 180 degree angles around an estimated centre. Instead the correct model is:

`canonical genuine 60-point dial -> one camera/projective transform -> candidate image`

The pairing still supplies powerful structure:

- corresponding minute positions are known exactly;
- each opposite pair gives two linked observations;
- complete-pair rejection avoids keeping one good side of a contaminated pair and silently using the bad side;
- many pairs provide redundancy when hands, cyclops, glare or reflections obscure part of the dial;
- fit/holdout validation can test whether the transform learned from some pairs predicts unseen pairs.

The current proof solver requires enough clean, well-spread complete pairs and fails closed if that requirement is not met.

## Hour-position minute marks

The 12 positions coincident with hour markers still contain printed minute-track evidence in the outer annulus. They are not treated identically to the 48 normal minor ticks.

Rules:

- detector samples only the outer minute-track annulus, away from the applied hour marker;
- hour-position tick/stub evidence has its own confidence handling;
- this evidence is valid pose evidence only if it is actually visible and passes confidence checks;
- the applied marker itself must never contribute to the pose estimate.

Future refinement should measure and freeze the exact genuine hour-position tick profile/length separately from the normal minor-tick profile rather than assuming one common tick length.

## Fail-closed rule

This proof is useful only if it can refuse unsuitable photographs.

Reject rather than guess when:

- too few complete opposing pairs are found;
- pairs are concentrated in too narrow an angular sector;
- weak-pair rejection leaves inadequate evidence;
- RANSAC/robust fitting does not establish a stable transform;
- held-out pairs do not confirm the transform;
- the minute-derived transform becomes physically implausible relative to the independently fitted dial boundary.

No fallback overlay should be shown as though it were a valid perspective solution.

## Important failures and lessons from the Alpha81-Alpha89 proof sequence

### Early photographic/edge overlays

Early builds mixed photographic edge extraction and candidate-driven geometry. They produced noisy overlays and made it difficult to tell whether a mismatch was template error, perspective error or candidate defect.

Lesson: use a clean measured genuine master rather than tracing arbitrary photographic edges.

### Minute tick geometry error

One build treated `MINUTE_TRACK_R = 0.925R` as a tick centre rather than the measured inner end, making tick overlays extend too far inward.

Lesson: template definitions must be physically explicit. A visual proof is invalid if the master geometry itself is wrong.

### Candidate 12-marker contamination

Alpha84-era code used candidate 12-landmark information to establish clock phase/orientation. That meant a defect near 12 could influence the overlay pose.

Lesson: judged features cannot position the reference used to judge them.

### Over-flexible projective refinement

Earlier annular/projective refiners could reduce edge residuals without preserving the intended QC independence. A flexible homography can make a defective candidate look better if it is allowed to optimise against the same geometry later being judged.

Lesson: the pose evidence, fit degrees of freedom and validation evidence must be explicitly bounded.

### Strict independent proof

The design was reset around a fixed genuine master plus opposing-minute pose evidence. Candidate features such as triangle, circles and batons were removed from pose fitting.

### Known-defect regression

A Pepsi QC photograph with a visibly tilted 6 baton became the key regression image. The purpose is not to decide authenticity, but to check one invariant:

- the candidate baton is allowed to be wrong;
- the yellow genuine baton must stay where the genuine master predicts;
- the pose solver must not chase that candidate baton.

This test exposed that thick marker outlines can visually hide small angular deviations even when the overlay axis is not actually identical to the candidate marker.

### Pre-warp proof construction lines

To prove marker outlines are not drawn after the perspective step, radial construction rays were added to the same canonical bitmap as all marker outlines before the single warp.

This creates an important invariant:

- baton sides;
- marker outlines;
- minute ticks;
- dial edge;
- radial construction rays

all undergo the exact same single perspective transform.

Nothing from this fixed master is painted onto the candidate afterwards.

### Alpha89 visualisation

Alpha89 made inspection deliberately obvious:

- genuine master outlines and minute ticks: bold yellow;
- 12 canonical hour radial rays: bold white;
- full opacity;
- same blink/overlay-off control remains available;
- no fitting change from the strict independent pose architecture.

This was substantially easier to inspect than the earlier thin-line builds.

## Current code architecture

Relevant proof files on this branch:

- `android/app/src/main/java/com/watchalign/mobile/Gmt126710BlnrMaster.java`
  - immutable genuine geometry and canonical angles.
- `android/app/src/main/java/com/watchalign/mobile/BakedDialOutline.java`
  - renders the complete flat master before perspective is applied.
- `android/app/src/main/java/com/watchalign/mobile/OpposingMinuteHomographyFitter.java`
  - detects minute evidence and fits/validates the camera transform from complete opposing pairs.
- `android/app/src/main/java/com/watchalign/mobile/StrictDialBoundarySeedAnalyzer.java`
  - coarse dial location using the physical dial boundary rather than hour-marker brightness.
- `android/app/src/main/java/com/watchalign/mobile/AutomaticDialOverlay.java`
  - obtains the candidate pose, applies physical guards, and warps the fixed canonical master once.

## Bezel visual-check extension

The next proof extension is visual only. It must not affect the minute-pair pose solution.

Plan:

1. Keep the existing 12 bold white canonical hour rays.
2. Extend those 12 rays from the dial centre through the dial and into the bezel region.
3. Add 12 additional white bezel-only rays halfway between the hour rays, creating 24 visual GMT positions at exact 15 degree intervals.
4. The intermediate rays begin outside the dial so they do not clutter the dial QC view unnecessarily.
5. All 24 rays are rendered into the same canonical bitmap before the single perspective warp.
6. No bezel evidence is used for pose fitting.
7. The existing overlay/blink control lets the user remove the construction overlay when required.

Purpose:

- visually assess gross bezel rotation/alignment against the same dial-centred geometry;
- make the 12/24-hour relationship easy to inspect;
- provide an additional visual check without contaminating dial pose estimation.

Limitation:

The bezel insert is physically above the dial plane. On sufficiently oblique photographs, true parallax between dial and bezel planes can make a dial-plane ray differ slightly from the apparent bezel feature position. Therefore these extended rays are a visual alignment aid only, not a calibrated bezel verdict or pose input.

## Regression requirements before trusting the proof

The following tests should be retained:

1. Straight/near-frontal genuine or high-confidence correct watch: full genuine master should naturally align.
2. Oblique but usable correct watch: minute-pair solver should establish perspective and the master should remain coherent around the dial.
3. Known tilted 6-baton QC image: yellow genuine baton and white genuine radial axis must not rotate to chase the defective baton.
4. Synthetic defect control: digitally rotate or translate only one applied marker while leaving the minute track untouched. The calculated overlay must remain effectively unchanged.
5. Poor photo: insufficient or badly distributed minute evidence must produce an unsuitable-photo failure instead of a plausible-looking fallback overlay.
6. Bezel extension: verify that changing bezel geometry cannot affect dial overlay pose because bezel evidence is not read by the solver.

## What would constitute success

The concept is considered proven only if:

- good watches naturally align without nudge;
- known/synthetic marker defects remain visibly different from the genuine master;
- candidate judged features cannot steer the pose;
- the same minute-derived camera transform governs every master element;
- unsuitable images fail closed;
- results remain stable under reasonable image resizing/re-rendering.

Only after this visual proof is convincing should numerical defect measurements/tolerances be layered on top.

## What is deliberately not being done yet

- no automatic authenticity verdict;
- no replica-derived genuine tolerance;
- no marker-specific post-fit correction;
- no bezel-based perspective correction;
- no automatic bezel pass/fail threshold;
- no Submariner expansion from this branch;
- no broad dataset expansion unless a specific evidence gap requires it.

## Immediate next action

Build the bezel-ray extension on top of the Alpha89 strict fixed-master proof without changing pose estimation. Then test the same known tilted-baton image and at least one image where bezel alignment is easy to inspect. If the 24 rays are useful, keep them as an optional visual aid while preserving the minute-pair-only pose architecture.