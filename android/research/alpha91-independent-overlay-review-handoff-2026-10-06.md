# Alpha91 overlay accuracy handoff for independent review — 2026-10-06

## Purpose

This note is a clean handoff for an independent review of the current GMT overlay problem. The objective is not to add QC scoring. The objective is to recover one perspective transform that makes a fixed genuine mathematical dial master visually coincide with a genuine GMT dial across the complete dial plane.

Treat every conclusion below as evidence to reproduce, not as an instruction to agree. If a result cannot be reproduced from code/data, say so and do not build on it.

## Non-negotiable guardrails

- Alpha90 production is frozen. Do not change or merge production code while investigating.
- Frozen production baseline: `f66acee665a5afb4450fa08f61396c344be43627`.
- Work on a separate research/feature branch only.
- Do not use candidate applied hour-marker positions to fit the perspective transform. They are holdout validation evidence.
- Do not make replica defects pull the overlay into alignment.
- Do not reintroduce 3D bezel depth, sapphire modelling or manual four-point tapping unless the evidence proves a planar homography is insufficient.
- Do not tune thresholds independently per photograph.
- Do not build an APK until the offline solver passes the genuine controls at the agreed accuracy.

## Product success criterion

For an acceptable genuine QC photograph, after perspective correction the fixed genuine master should visually sit on the real dial across the whole dial. The target is approximately <=1–1.5 px mean holdout error at applied marker centres, ideally near 1 px, with no sector showing a large unexplained drift. Visual edge coincidence matters more than a favourable aggregate number.

## Mathematical master

The frozen master is structurally mathematical, not a set of independently placed hour markers.

`Gmt126710BlnrMaster.java` on the frozen baseline defines:

- dial edge radius = 1.000
- minute track inner radius = 0.925
- minute track outer radius = 0.972
- hour-tick sample radius = 0.960
- round-marker centre radius = 0.816
- baton marker centre radius = 0.758
- triangle centre radius = 0.750
- minute angles exactly every 6 degrees
- hour axes exactly every 30 degrees

`BakedDialOutline.java` generates:

- all 60 minute ticks from the same two radii at exact 6-degree spacing;
- round markers at hours 1,2,4,5,7,8,10,11 on the same radius and exact hour axes;
- 6 and 9 batons from the same baton-centre radius on their exact axes;
- the 12 triangle symmetrically about the exact 12 axis;
- a canonical circular dial boundary.

Therefore a local 4/5 template-position error cannot exist independently of the shared mathematical construction. Possible template errors are shared radii/shape dimensions, not arbitrary per-hour coordinates.

Important prior finding: analysis of the supplied bare genuine GMT dial suggested the frozen minute-track geometry is too far inward. A detector-consistent estimate was about centre 0.9598R, with inner about 0.9358R and outer about 0.9837R. Reproduce this before relying on it.

## What the geometry experiments established

A flat 2D planar homography is capable of very accurate alignment when reliable same-plane correspondences are supplied.

On difficult genuine `POOL_GEN_HO_02`, an oracle homography from accurate round-marker anchors produced roughly 0.39 px mean non-triangle holdout error, with most untouched marker centres well below 1 px. Other genuine controls were roughly low-pixel as well. This is strong evidence that the remaining problem is correspondence/registration recovery, not missing 3D watch geometry.

Relevant note: `android/research/alpha91-flat-homography-minimal-oracle-2026-10-05.md`.

## Approaches already tried

### Fully automatic 60-tick assignment

Minute-track-only fitting reached about 1.13 px mean on `POOL_GEN_HO_02`, but did not generalise. Other genuine controls produced sector-dependent failures, including catastrophic multi-pixel drift. The bounded detector experiment was stopped rather than tuned per image.

Read:
- `alpha91-minute-track-only-proof-2026-10-05.md`
- `alpha91-minute-track-subpixel-refinement-2026-10-05.md`
- `alpha91-minute-track-generalisation-2026-10-05.md`
- `alpha91-final-bounded-detector-stop-2026-10-05.md`

### Manual four-cardinal anchors

Mathematically, four exact outer-dial cardinal points determine the homography. However the Android prototype incorrectly treated the user's along-edge finger position as precise evidence. Small tangential tap errors badly distort a four-point homography. This prototype is discarded.

Do not interpret its failure as evidence against the planar model.

### Conic/pinion/12-direction and feature matching

Offline tests of conic + pinion + 12 direction, raw SIFT, and text-only SIFT did not achieve the target. Optical-flow refinement was not consistently beneficial. Do not repeat these blindly unless you can identify a materially different formulation.

## Current strongest research direction

A fully automatic dense genuine-reference registration experiment was run using the supplied front-on bare genuine GMT dial as the fixed reference.

Pipeline summarized in `android/research/alpha91-dense-genuine-reference-registration-2026-10-06.md`:

1. coarse black-dial localisation;
2. coarse dial ellipse;
3. automatic coarse 12-sector orientation cue;
4. warp candidate and genuine reference into a common canonical dial frame;
5. refine one unrestricted planar homography using masked dense registration on trusted dial-plane texture;
6. second small refinement in the minor-minute annulus with hour sectors masked;
7. applied marker centres remain holdout only.

Reported unchanged holdout means before residual diagnosis:

- `EXT_EXT_GEN_BLRO_WEX_01`: 2.57 px
- `EXT_EXT_GEN_BLRO_WEX_02`: 1.94 px
- `POOL_GEN_HO_01`: 2.48 px (contains a previously known hour-2 holdout anomaly)
- `POOL_GEN_HO_02`: 1.68 px

These numbers and the exact implementation should be independently reproduced. The research note records the result, but not every exploratory script used to obtain it.

## Residual-vector diagnosis

`android/research/alpha91-residual-vector-diagnosis-2026-10-06.md` records an important observation from the same controls:

- residuals appeared to contain a strong common translation component;
- robust pooled observed-minus-projected offset was approximately +1.32 px x and +1.04 px y;
- after a single fixed candidate-independent correction, WEX2 and HO2 were reported around 0.6 px mean, HO1 around 0.8–0.9 px excluding its known hour-2 outlier, and WEX1 about 1.3 px overall;
- WEX1's remaining error was concentrated around its 4/5 sector while most other markers were reported around 0.6 px;
- a leave-one-photo-out check reportedly retained the same sub-pixel/near-pixel behaviour.

Again: reproduce this independently. If the common offset is real, determine its physical/software cause rather than simply hard-coding a magic translation unless a fixed genuine-derived calibration is demonstrably the correct representation.

## Key question now

Can the residual perspective/registration error be reduced to the target across genuine controls with one fixed method, without using candidate applied markers?

Before inventing another solver, inspect the current residual field in canonical coordinates:

1. Inverse-project every validated genuine marker centre through the recovered H.
2. For round markers, compare radius and angle against the exact mathematical model: one common radius, exact 30-degree hour axes.
3. Check 6/9 baton centres against their exact axes and shared baton radius.
4. Check the 12 triangle centreline against the exact 12 axis.
5. Check all reliably observable minute strokes against the exact 6-degree lattice and common inner/outer radii.
6. Separate common translation, scale/radius, roll, projective residual and local image/annotation anomalies.

This should tell us whether WEX1 4/5 is:

- a holdout annotation problem;
- a local image/reflection/refraction issue;
- a remaining homography/registration bias;
- or evidence that a shared master dimension is wrong.

## Preferred next experiment

Start from the current best homography and perform a bounded, sector-balanced refinement against trusted dial-plane edge evidence. Do not assign individual tick identities globally from scratch. The current transform should already be close enough that the exact 6-degree minute lattice gives strong local predictions.

One plausible formulation:

- build a distance transform / gradient representation in the candidate;
- project the mathematical minute-track strokes and other non-QC trusted geometry through H;
- mask hour-marker bodies, centre/hands, date/cyclops and any feature intended for later QC judgement;
- split the trusted evidence into angular sectors and weight sectors equally so one bright/reflected region cannot dominate;
- robustly optimise only the 8 free homography parameters from the current H;
- accept a refinement only if it improves both fitting evidence and independent genuine holdouts across the control set;
- run the precision pass at full source resolution even if coarse localisation uses a resized image.

If a consistent radial residual remains after a correct homography, test one mild radial-distortion coefficient as a controlled hypothesis. Remove it if it does not improve all/most genuine controls unchanged.

## Required deliverable

Do not return only a plan. Produce an evidence-backed conclusion:

1. reproduce or challenge the current best results;
2. identify the dominant remaining error source;
3. implement the smallest offline research change needed;
4. run it unchanged on the genuine controls;
5. report per-photo mean/median/max holdout errors and residual pattern;
6. show whether the target is met;
7. if met, only then propose the smallest Android integration path;
8. keep Alpha90 untouched.

Do not spend hours tuning one photograph. If a proposed change fails to generalise after a bounded test, stop and report that clearly.