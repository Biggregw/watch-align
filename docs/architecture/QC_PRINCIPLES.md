# Watch Align QC Principles

Status: governing design direction for new QC work.

## Product objective

Watch Align has two complementary QC jobs:

1. **Automated measurement:** quantify simple physical relationships that experienced human reviewers use to recognise visible defects, preferably as dimensionless local ratios or angular deviations measured against directly observed landmarks.
2. **Human visual assistance:** render an overlay whose construction lines amplify those same defects for the eye. Overlay geometry may deliberately contain global, redundant or long-axis guides even when the automated measurement does not require them.

These jobs share detected landmarks but must not be confused. A useful overlay line is not automatically the best measurement datum, and a simple local automated measurement does not imply that a corresponding global overlay guide should be removed.

The objective is not to reconstruct a theoretically perfect canonical watch unless evidence later shows that reconstruction is necessary for a specific validated QC check.

## Human QC research is the source of feature definitions

For each QC feature, use this sequence:

1. Identify defects that experienced reviewers actually report in real QC discussions and examples.
2. Record how a human recognises the defect by eye, including which nearby features or overlay guides make it obvious.
3. Separate the **measurement relationship** from the **overlay assistance**.
4. Translate the human rule into the minimum physical landmarks and simplest candidate dimensionless ratio/angle.
5. Validate that formulation on suitable frontal images before adding perspective correction or global reconstruction.
6. Establish the genuine distribution and measurement repeatability before defining pass/fail tolerances.
7. Test against known replica defects or controlled deviations.

Do not begin from a mathematical primitive already available in the code and subsequently reinterpret its output as a QC feature.

Real QC research supports this human-first approach. Reviewers routinely describe individual indices as tilted, shifted, too high/low or off relative to minute markers; use alignment overlays as visual aids; compare rehaut engraving with minute markers; and distinguish image-angle artefacts from real defects. These observations should inform candidate checks, but community comments are evidence of human QC practice, not numerical ground truth.

## Minute track as the primary observed dial reference grid

For Rolex-style dial geometry, the minute track is a particularly strong directly observed reference grid. Human reviewers naturally compare applied markers, the 12 marker, rehaut engraving and bezel alignment with minute-track positions.

Where appropriate, automated QC should therefore prefer relationships such as:

- applied marker centre/orientation versus its corresponding and neighbouring minute markers;
- 12 marker centre/orientation/clearance versus the 60 and neighbouring minute markers;
- rehaut engraving versus corresponding minute markers;
- bezel zero/pip versus the 60/dial reference.

A minute-track defect can propagate into multiple dependent relationship failures. That is acceptable. Watch Align should report the measured physical relationship, for example `12 marker is horizontally misaligned with the 60 marker`, rather than over-diagnosing which component was manufactured incorrectly.

Do not add complexity solely to decide which member of an abnormal physical relationship caused the abnormality unless there is a demonstrated product requirement.

## Measurement geometry versus overlay geometry

### Automated measurement

Automated QC answers: **how far does this physical relationship deviate from the genuine population?**

Prefer the simplest directly observed local relationship capable of answering the QC question. Do not introduce dial centre, Hough-circle axes, canonical dials, homographies or global rectification merely because they are available in existing code.

### Human overlay

The overlay answers: **how can this deviation be made immediately obvious to a human eye?**

The overlay may intentionally use strong global construction geometry and visual redundancy. In particular, a precise orthogonal cross anchored to the minute track is high-value visual assistance:

- vertical axis through the 60/top and 30/6 o'clock minute-track positions;
- horizontal axis through the 45/9 and 15/3 o'clock minute-track positions;
- intersection at the visual dial centre.

This cross helps a reviewer perceive displacement, cant and asymmetry at 12, 3, 6 and 9 even when the automated checks for those features use more local relationships. Radial guides, tangent/parallel guides and opposing-position lines may likewise be valuable because they create visual expectations that make small deviations conspicuous.

Therefore: **automated QC quantifies what the human eye sees; overlay QC amplifies what the human eye sees.**

Do not remove useful overlay geometry merely because the automated measurement no longer depends on it. Conversely, do not force automated measurements to depend on overlay construction geometry solely because it is visually useful.

## Current human-defined feature map

The following are current design hypotheses derived from direct human input and QC-community research. They are candidate physical formulations, not frozen numerical tolerances.

### GMT / Rolex-style 12 marker

**Vertical position**

Primary human test: visible clearance between the top edge of the 12 triangle/marker and the inner ends of the minute markers immediately above it. This single local gap is often sufficient for the human judgement.

Candidate automated measurement:

- detect the marker top edge;
- detect the local inner/bottom edge of the minute-track markers above it;
- measure perpendicular/local radial clearance;
- normalise using an appropriate directly observed local scale;
- learn the genuine distribution before defining tolerance.

The marker-to-dial-coronet gap may be secondary corroboration, but must not be forced into the primary vertical-position metric unless evidence shows that it adds value.

**Horizontal alignment**

Primary human test: the 12 marker centreline should align with the centre of the 60/top-middle minute marker.

Candidate automated measurement: lateral displacement of the marker centreline relative to the 60-marker centre, normalised locally. Do not substitute a fitted dial-centre X coordinate for the actually observed 60-marker centre.

**Rotation / cant**

Primary human test: imagine a local line through the inner ends of the minute markers immediately above the 12 marker. The relevant top/base orientation of the 12 marker should be parallel to that local reference, and its centreline should therefore be perpendicular to it.

Candidate automated measurement: angular difference between the marker orientation and the directly observed local minute-track orientation.

**Secondary coronet relationship**

A human may extend the 12 marker centreline downward and judge where it points on the printed Rolex coronet. QC-community examples also use the coronet as a visual corroboration for 12-marker alignment. The exact coronet target is not yet frozen and should not be invented without evidence.

### Applied hour markers

Human review distinguishes at least three different geometric problems and they must not be collapsed into one generic alignment score:

1. **Tangential/centre alignment:** the centre of an hour marker should align with its corresponding minute-track marker.
2. **Local centring/symmetry:** widening the view to neighbouring minute markers should show the hour marker sitting symmetrically within the local minute-track pattern.
3. **Radial height:** especially for round markers, the marker should have the expected clearance/height relative to an imaginary local line through the inner ends of the surrounding minute markers.
4. **Rotation/cant:** elongated/baton markers should have the expected orientation relative to the local minute-track geometry.

Candidate automated formulations should initially use those local references. Opposing markers or global axes may be tested as corroborating measurements but should not be required merely because they make useful overlay guides.

### Rehaut alignment

Rehaut alignment is a separate QC feature and must not contaminate the 12-marker result.

At 12, the engraved rehaut coronet can be compared with the centre of the 60 minute marker. Around the dial, rehaut engraving can be compared with corresponding minute-track positions. Community QC discussions explicitly use minute-marker alignment when assessing rehaut engraving.

### Bezel, date, cyclops and hands

Use the strongest immediate physical reference for each feature rather than forcing the minute track everywhere:

- bezel zero/pip: compare with the dial 60/12 reference;
- date horizontal/vertical position: compare numeral clearances within the date aperture;
- cyclops position/rotation: compare with the date aperture and appropriate local dial reference;
- hand alignment: evaluate hands against the relevant hour/minute references at a known display state.

These remain candidate feature definitions until human rules and validation evidence are sufficiently complete.

## First principle: frontal measurement before perspective

Perspective correction is downstream and is explicitly gated behind successful measurement of a high-quality, essentially frontal image.

If Watch Align cannot repeatedly recover the correct physical landmark relationships from a well-aligned image, no perspective model can make the QC system trustworthy. Development must therefore prove the uncorrected frontal measurement system first.

The mandatory order is:

1. Perfect/frontal image measurement: reliably detect the required physical landmarks on carefully selected near-frontal images and calculate the simplest local ratios.
2. Frontal repeatability: prove that the same image is deterministic and that multiple suitable images of the same physical watch give sufficiently consistent ratios.
3. Genuine baseline: measure independent genuine watches to establish manufacturing variation for each proven ratio.
4. Defect sensitivity: demonstrate that known replica defects or controlled geometric deviations move the relevant ratio by more than genuine variation plus measurement uncertainty.
5. Perspective challenge: only after steps 1-4 pass, introduce progressively off-axis images and quantify how much the proven ratios deteriorate.
6. Perspective correction: add correction only where step 5 shows a material problem, and accept the correction only if it restores the measurement towards the already-proven frontal reference.

A perspective-corrected image looking visually convincing is not success. The success criterion is recovery of the validated physical landmark ratios.

Until the frontal gate passes, development effort should not be spent improving perspective correction for that feature.

## Image suitability is an input contract

Watch Align's production QC flow receives constrained QC-style photographs: a user deliberately photographing a watch dial for inspection. Replica QC photographs are normally reasonably frontal, show the relevant dial clearly, and expose the features being assessed. That is the input population this system must serve, not arbitrary found photography.

The intended pipeline is:

QC-style image -> suitability/assessability gate -> physical landmark detection -> local dimensionless measurements -> genuine reference/tolerance comparison -> defect findings + human-assist overlay

An image outside the acceptable pose/visibility envelope is not a harder measurement problem to solve with more sophisticated geometry. It is an input to reject as `not assessable / retake photograph`.

Do not diagnose downstream detector or ratio behaviour from an image that has already failed the input contract. First establish that the actual source image, not merely a presentation crop or contact-sheet thumbnail, violates the contract.

Perspective correction may eventually improve measurements within the accepted QC pose envelope. It must never be used to justify accepting photographs that production QC would otherwise reject.

## Governing rules

1. Define each QC feature from the human visual rule first.
2. Prefer local landmark ratios/angles over absolute pixel distances.
3. Prefer directly observed neighbouring landmarks over inferred global geometry when both answer the measurement question.
4. Treat the minute track as a primary observed dial reference grid where it matches human QC practice.
5. Keep automated measurement geometry and human overlay geometry conceptually separate.
6. Permit overlay redundancy and global construction guides when they materially improve human perception of defects.
7. Every automated feature must correspond to a comprehensible physical QC claim.
8. Report observed relationship failures rather than over-diagnosing which physical component caused them.
9. Establish genuine distributions before defining pass/fail tolerances.
10. Measurement repeatability must be substantially tighter than the defect/tolerance the feature is intended to detect.
11. If acquisition or detection uncertainty is too large, return not-assessable rather than manufacture a pass/fail result.
12. Do not add projective correction, conic fitting, global rectification or other mathematical machinery unless an experiment demonstrates useful improvement for the specific feature.
13. Do not rescue an unstable measurement merely by widening thresholds or suppressing inconvenient results.
14. Keep genuine manufacturing variation, measurement error and genuine-vs-replica separation as distinct quantities.
15. Do not treat repeated photographs of one physical watch as independent genuine examples.
16. Frontal landmark measurement is a hard gate. Perspective work cannot substitute for failure at this stage.
17. Reject unsuitable candidate images explicitly and record the reason.
18. If a single local visual relationship is sufficient for the human QC judgement, use it as the primary candidate measurement. Additional landmarks must earn their place through evidence.
19. Do not modify detector or geometry code merely because an individual numerical result looks unusual. Confirm input suitability and physical landmark correctness first, then look for repeatable population-level evidence of a failure mode.
20. Preserve historical experiments as evidence, but do not let historical implementation choices define new QC features.

## Required evidence for a production QC feature

A feature is eligible for production only after all of the following are demonstrated:

- **Human rule:** what experienced reviewers actually perceive is explicit.
- **Landmark definition:** the physical points/edges being measured are explicit and reproducible.
- **Measurement formulation:** the simplest ratio/angle corresponding to that human rule is defined.
- **Frontal measurement proof:** the feature can be measured reliably without relying on perspective correction.
- **Repeatability:** bounded realistic captures of the same physical watch produce sufficiently small measurement spread.
- **Genuine baseline:** multiple independent genuine watches establish manufacturing variation.
- **Discrimination:** known replica defects or controlled deviations are distinguishable from genuine variation plus measurement error.
- **Overlay assistance:** where useful, define which visual guides best expose the same defect to a human and validate that they are accurately anchored.
- **Explainability:** a failure can be reported as an observed physical relationship, for example `12 marker sits proportionally too close to the minute track`.

## Immediate development plan

Do not resume broad detector/perspective development yet.

1. Finish the human-QC feature catalogue using direct human input plus research of real QC discussions. Mark each proposed relationship as `human-confirmed`, `community-supported`, or `candidate/inferred` so hypotheses are not mistaken for established rules.
2. For each feature, document separately:
   - defect humans report;
   - human visual rule;
   - physical landmarks required;
   - candidate automated ratio/angle;
   - best overlay assistance;
   - source/evidence status;
   - unresolved questions.
3. Use the 12 marker as the first implementation proof because its human rules are already comparatively well defined.
4. Rework the 12-marker experimental measurements around the agreed minute-track-local relationships rather than the old Hough-centre-derived horizontal axis.
5. Validate on carefully selected frontal inputs, then same-watch repeatability, then an independent genuine population.
6. Only after the measurement is proven, compare known replica defects and then challenge perspective.
7. Separately revisit the visual overlay using the same feature catalogue. Preserve strong aids such as the 12-6 / 3-9 cross even when automated measurements use local geometry.

The next implementation must not run ahead of this sequence.

## What is explicitly not frozen

The following must not be treated as established simply because earlier experiments used them:

- current Stage 3 projective feature set;
- three-point projective radial correction as a required production step;
- existing experimental guardrail thresholds;
- old Hough-derived dial-centre axis as the correct datum for local marker alignment;
- any numerical genuine tolerance not supported by an adequate independent genuine sample;
- any assumption that more mathematical correction necessarily produces more accurate QC;
- any assumption that automated measurement and visual overlay must use identical geometry.

Historical experiments should be preserved because they contain useful negative and positive evidence. New work should be evaluated against the principles in this document.
