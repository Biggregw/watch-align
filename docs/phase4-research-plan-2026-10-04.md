# Phase 4 research plan — 2026-10-04

## Status

Research-only governing plan. **Do not implement Phase 4 product changes until the experiments below have been completed, reviewed, and explicitly approved.**

This document exists to prevent the project drifting into detector, threshold, UI, APK, or calibration changes before the evidence says what is actually worth building.

### Current checkpoint

- Phase 3 calibration outcome and coverage-funnel work remains the active checkpoint on PR #49.
- The independent governing-snapshot proof has passed, including frozen snapshot identity, exact calibration bytes, v1/v2 equivalence, and Phase 3 reporting.
- Final closeout of the current Phase 3 branch is separate from this research plan and must not be bypassed by Phase 4 work.
- Production behaviour remains unchanged by this document.

## Product constraint

The intended product input is **one externally supplied QC-style photograph** of a watch.

Assume:

- one photo only;
- usually front-on-ish but not perfectly aligned;
- uncontrolled phone/camera, focal length, sharpening, compression, and lighting;
- possible hand occlusion;
- no opportunity to request extra angles, movement shots, caseback shots, or guided recapture;
- the exact watch reference is known for the first research target: Rolex Submariner 124060.

Any idea that requires multiple user photos is out of scope for the core Watch Align analysis path. Multi-photo material may still be used **offline for research, validation, or discovery of factory tells**.

## What Phase 3 taught us

The current calibrated metrics are reproducible and defensible, but the current replica evidence has not shown useful gen-vs-rep separation for the seven existing calibrated metrics.

Therefore Phase 4 must not be another exercise in polishing the same seven measurements. The goal is to find **materially more discriminative evidence** that can be recovered from one QC-style image.

## Governing principles

1. **Research before implementation.** No production changes until an experiment produces a go decision.
2. **One-photo constraint.** Every proposed production feature must ultimately work from one QC-style image.
3. **Reference-specific first.** Solve 124060 rigorously before generalising.
4. **Physical-watch independence.** Train/test/evaluate by independent physical watch, not by image count.
5. **Source independence.** Avoid learning dealer, forum, camera, background, or compression signatures.
6. **Fail closed.** Unsupported or weak evidence must remain unsupported/inconclusive.
7. **Explainable evidence.** Preserve observation, measurement/evidence, confidence, reason, and contribution separately.
8. **No unsupported precision.** Do not report geometry more precisely than pose, optics, and evidence support.
9. **Genuine defines nominal/tolerance.** Replica data may test usefulness or reveal factory signatures but must not define the genuine envelope.
10. **Kill weak ideas quickly.** Each experiment has an explicit go/no-go criterion.

# Research sequence

The following order is mandatory unless this document is deliberately revised with a reason.

## Experiment 1 — single-image rectification benchmark

### Question

Can a mathematically stronger single-image rectification method materially outperform the current ellipse-led correction on 124060 QC-style photos?

### Hypotheses to test

- A projected physical circle becoming an ellipse can shift the apparent ellipse centre away from the true projected physical centre.
- Making one ellipse circular may therefore leave internal dial geometry systematically distorted.
- Multiple circular/conic constraints may recover perspective more accurately than a single outer ellipse.
- Useful constraints may include:
  - dial/minute-track conic;
  - hour-marker-centre locus;
  - additional stable circular/radial structures where observable;
  - structural keypoints such as 12/3/6/9 where confidence is high.
- Iterative rectify → redetect → refine may reduce residual pose error.
- Sapphire/crystal refraction may impose a lower bound on how accurately a planar homography can recover dial geometry from an assembled watch.

### Benchmark

Compare at least:

1. current production/research rectification baseline;
2. multi-conic or multi-constraint projective rectification;
3. structural-keypoint-assisted rectification if justified by the evidence.

Use existing genuine/replica evidence where suitable, plus loose-dial or official planar references where available.

### Metrics

Measure improvement in:

- repeatability under controlled image perturbations;
- recovered 12/3/6/9 symmetry;
- opposite-marker collinearity through the recovered true centre;
- radial consistency of marker centres;
- minute-track circularity after rectification;
- residual error against known/nominal loose-dial geometry;
- sensitivity to modest pitch/yaw/roll;
- failure/abstention rate on normal QC-style photos.

### Go criterion

Proceed only if the new method produces a **clear and repeatable reduction in internal geometry error** on independent watches without creating unacceptable rejection/failure rates.

If gains are cosmetic, unstable, or only visible on hand-picked images, **NO-GO**.

## Experiment 2 — paired genuine / VSF / Clean tell discovery

### Question

After trustworthy rectification, what visual or geometric differences between genuine 124060, VSF, and Clean actually persist across independent watches?

### Data priority

Prefer paired or controlled comparisons where genuine and replica watches were photographed:

- by the same person;
- with the same camera;
- under the same lighting;
- at approximately the same time/pose.

Sources may include RWI, r/RepTime, r/RepTimeQC, other replica communities, dealer/QC archives, and credible owner comparisons.

### Method

For each independent pair/set:

1. preserve provenance and physical-watch identity;
2. rectify each dial independently;
3. register to a common canonical 124060 coordinate system;
4. compare whole-dial residuals and local regions;
5. measure both known tells and previously unanticipated differences;
6. record whether a candidate tell repeats across independent watches and sources.

### Candidate feature families

Do not limit discovery to the existing seven metrics. Include:

- marker radial placement;
- marker shape and surround morphology;
- 12 triangle geometry;
- 3/6/9 baton geometry;
- minute-track geometry;
- dial text position, baseline, spacing, glyph morphology, and stroke behaviour;
- Rolex coronet geometry;
- `SWISS [coronet] MADE` geometry;
- rehaut engraving/alignment where visible;
- crystal-related appearance only if robust to lighting/processing;
- bezel/SEL/case cues only if they can be recovered reliably from the single QC-style frame.

### Go criterion

A candidate tell must recur across **independent physical watches** and remain measurable after source/camera variation is considered.

Any tell that works only on one batch, one seller, one forum, one camera, or one image is not a production feature.

## Experiment 3 — nominal 124060 master geometry

### Question

Can we reconstruct a reliable nominal 124060 dial specification from physical loose dials plus official/high-confidence genuine imagery?

### Source classes

Rank evidence:

A. physical genuine loose 124060 dial with strong provenance and individually photographed;

B. physical genuine loose dial with weaker provenance but agreement with A-grade examples;

C. official Rolex or authorised high-quality planar/front-on asset;

D. high-confidence genuine assembled-watch photo;

E. replica/aftermarket/custom dial, kept strictly separate for comparison only.

### Source inventory targets

Search specialist Rolex parts dealers, dial retailers, watchmaker parts stores, eBay/sold listings, archived listings, collector forums, auction archives, service-part sellers, and authorised/official media.

Record:

- source URL;
- seller/source identity;
- provenance claim;
- reference/part number;
- image count and resolution;
- front/rear view availability;
- whether a ruler/caliper/known scale exists;
- whether the central hole and full dial boundary are visible;
- duplicate/repost identity.

### Geometry to recover

Build a canonical dial coordinate model containing as much as the evidence supports:

- physical/digital centre;
- outer dial radius;
- centre hole;
- marker centres;
- round-marker diameters;
- baton vertices and dimensions;
- 12 triangle apex/base vertices;
- surround thicknesses;
- minute-track radius and tick coordinates;
- text block bounding boxes and baselines;
- individual glyph/word spacing where resolution permits;
- coronet coordinates and proportions;
- `SWISS [coronet] MADE` coordinates.

### Role of assembled genuine-watch data

Loose dials define nominal geometry. Assembled-watch images are then used primarily to determine:

- recoverability through crystal/rehaut/camera perspective;
- realistic measurement uncertainty;
- genuine manufacturing/printing variation visible in QC-style imagery.

### Go criterion

Proceed only where independently sourced genuine evidence converges tightly enough to justify a nominal parameter or tolerance.

Where sources disagree or provenance is weak, retain uncertainty rather than inventing a master value.

## Experiment 4 — local OCV / anomaly inspection

### Question

Once rectification and nominal geometry are credible, can local appearance analysis detect authentic-vs-replica differences that deterministic geometry misses?

### Preferred approaches

Investigate in increasing complexity:

1. deterministic template/OCV comparison of known dial text and coronet regions;
2. local patch-distance/residual modelling against a genuine canonical atlas;
3. normal-only industrial anomaly detection such as PatchCore/PaDiM-style approaches;
4. supervised genuine-vs-factory classification only if sufficient independent-watch evidence exists.

### Why OCV rather than generic OCR

The expected 124060 text is already known. The useful task is verification of:

- position;
- baseline;
- spacing;
- stroke shape/width;
- local deformation;
- missing/excess print;
- glyph/coronet morphology.

### Dataset discipline

- split by physical watch;
- keep source domains separated where possible;
- deduplicate exact and near-duplicate images;
- prevent multiple photos of one watch crossing train/test boundaries;
- report abstention when local image quality cannot support the feature.

### Go criterion

The model must outperform the existing deterministic feature set on **blind independent watches** and retain useful performance across sources.

If it merely learns backgrounds, sellers, image processing, or one replica batch, **NO-GO**.

# Supporting research tracks

These are useful but must not distract from Experiments 1–4.

## Competitor clean-room teardown

Completed initial static inspection of three publicly downloadable apps supplied by the project owner:

- Legit Check: Authenticator AI;
- Watch Authenticator: AI check;
- WatchAuth.

Current conclusion:

- no evidence of a bundled sophisticated Rolex-specific geometric engine;
- systems appear primarily server/LLM driven, with structured checklists, confidence/scoring, and reporting;
- useful product ideas include per-feature evidence ledgers, image-quality gating, explicit inconclusive states, and known-replica signature reporting;
- these are product/evidence-management ideas, not proof of better underlying Rolex metrology.

Do not copy proprietary code/assets or bypass protections. Only clean-room public/static behavioural ideas may influence Watch Align.

## Reddit/community knowledge

Continue to use replica communities for:

- controlled gen/rep comparison discovery;
- repeated human observations that can be turned into testable hypotheses;
- factory/batch labels;
- known QC failure modes;
- identifying where humans confuse perspective with real marker error.

Community claims are hypotheses until independently measured.

## Material/reflectance cues

White-gold surrounds, ceramic/bezel appearance, crystal behaviour, AR, and lume may contain useful information, but uncontrolled lighting makes these lower priority for the one-photo product.

Do not prioritise until geometry/registration and local structural analysis have been exhausted.

# Evidence architecture to preserve for future product work

If a future experiment earns implementation, keep evidence separate rather than collapsing immediately to a single score.

For every candidate feature preserve:

- `observation`;
- `measurement_or_evidence`;
- `confidence`;
- `confidence_reason`;
- `supported / unsupported / inconclusive`;
- `reference_or_factory_signature`;
- `contribution_to_conclusion`.

Possible future component groups:

- image quality / pose;
- dial geometry;
- marker morphology;
- dial printing / coronet;
- rehaut evidence;
- bezel/case/SEL evidence if robust;
- known factory signatures;
- overall evidence fusion.

A future overall score/verdict must never hide unsupported component evidence.

# Explicitly out of scope until research gates pass

Do **not**:

- change Android production code for Phase 4 ideas;
- add or tune product thresholds based on current hypotheses;
- add more calibrated ratios simply because they are easy to measure;
- alter existing detector/reliability logic;
- make the known 3/9 fix as part of this research phase;
- change GMT behaviour;
- switch live calibration input to v2;
- merge ML/anomaly models into the APK;
- build guided multi-photo capture as a requirement;
- let replica data define the genuine envelope;
- treat an LLM authenticity opinion as ground truth;
- report precision unsupported by pose/optical uncertainty.

# Decision gates

At the end of each experiment record exactly one state:

- `GO` — evidence supports the idea and defines the next bounded implementation/research step;
- `HOLD` — promising but evidence/data are insufficient;
- `NO-GO` — insufficient material improvement or unacceptable failure/confounding risk.

No experiment advances to production simply because it is technically interesting.

# Phase 4 success definition

Phase 4 research is successful only if it identifies at least one method that materially improves the ability to distinguish genuine 124060 evidence from replica evidence using **one ordinary QC-style photograph**, while remaining explainable and robust on independent watches and sources.

If none of the four experiments clears that standard, the correct outcome is to document that result and stop rather than force a feature into the product.

# Resume protocol

Whenever this project is resumed:

1. read this document first;
2. confirm current Phase 3/branch state;
3. identify the active experiment and its last evidence checkpoint;
4. do not start implementation unless the relevant experiment has a recorded `GO` decision;
5. update this document (or a linked experiment report) with evidence and the decision before moving to the next stage.
