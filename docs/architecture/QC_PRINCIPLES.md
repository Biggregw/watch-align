# Watch Align QC Principles

Status: governing design direction for new QC work.

## Product objective

Watch Align should identify visible geometric QC deviations by comparing dimensionless relationships between physical dial landmarks with distributions measured from independent genuine watches.

The primary question is not whether a photographed watch can be reconstructed into a theoretically perfect canonical dial. The primary question is whether a marker occupies the same proportional relationship to nearby, independently detected landmarks as it does on genuine watches.

Example for the GMT 12 o'clock triangle:

- identify a stable reference on the Rolex coronet below the triangle;
- identify the triangle apex, base and centre as required;
- identify the 60/minute-track reference above the triangle;
- measure local, dimensionless ratios such as the triangle position within the coronet-to-minute-track interval;
- compare those ratios with the genuine-watch population;
- flag a QC issue only when the observed deviation is outside a justified genuine tolerance and is materially larger than the measurement uncertainty.

Exact feature definitions and tolerances must come from evidence. The examples above are design examples, not frozen constants.

## Human-defined QC geometry before mathematical formulation

For each QC check, first document how an experienced human reviewer actually judges the physical defect from a representative QC photograph. Only then translate that visual rule into landmarks and the simplest measurement that expresses it. Do not start from an available mathematical primitive and subsequently try to interpret its output as a QC defect.

For the GMT 12 o'clock triangle, the first human-defined local reference is the minute track immediately above the triangle:

- imagine a local reference line through the inner/bottom ends of the minute markers above the triangle;
- the triangle's top/base edge should be parallel to this local minute-track reference line;
- equivalently, the triangle centreline should be perpendicular to that local reference line;
- the triangle centreline should intersect the centre of the 60/top-middle minute marker, providing a direct local horizontal-alignment check;
- extending that same triangle centreline downward provides an independent physical check against a defined area of the Rolex coronet below the triangle.

### GMT 12 triangle vertical-position rule

The primary human judgement of whether the 12 triangle sits too high or too low is the visible clearance between the triangle's top edge and the inner/bottom ends of the minute markers immediately above it. An experienced reviewer can recognise whether this gap has the expected proportion without first referencing the coronet below.

Therefore the primary machine formulation for vertical position should mimic that single local test:

- detect the triangle top edge;
- detect the local inner/bottom edge of the minute-track markers immediately above it;
- measure the perpendicular/local vertical clearance between those two observed features;
- normalise that clearance using an appropriate directly observed local scale so the result is dimensionless;
- establish the genuine distribution before defining any numerical tolerance.

The triangle-to-coronet gap may later be retained as secondary corroborating evidence, but it must not be made part of the primary vertical-position measurement merely because it is available. Additional geometry must demonstrate that it improves discrimination or reliability before being required.

These are local physical relationships. They do not require a fitted dial centre, Hough-circle axis, canonical dial, homography, or other inferred global geometry. A global reference must not replace these directly observed neighbouring references unless an experiment demonstrates that the global reference is necessary and improves the QC measurement.

The exact coronet target area, the exact local normalising scale for vertical clearance, and any numerical tolerances remain deliberately undefined until human review and genuine-reference evidence establish them. The rules above define what is being observed, not pass/fail thresholds.

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

## Second principle: image suitability is an input contract, not a problem for the measurement engine to solve

Watch Align's production QC flow only ever receives constrained, QC-style photographs: a user deliberately photographing a watch dial for inspection. Replica QC photographs of this kind are normally reasonably frontal, show the complete dial clearly, and expose the relevant markers -- that is the actual input population this system must serve, not arbitrary found photography.

The intended pipeline is:

QC-style image -> suitability/assessability gate -> physical landmark detection -> local dimensionless ratios -> genuine reference/tolerance comparison -> defect flagging

An image outside the acceptable pose/visibility envelope -- auction photography, wrist shots, presentation-box shots, strongly oblique views, heavily cropped images, and similar -- is not a harder measurement problem to solve with more sophisticated geometry. It is an input the system must reject as "not assessable / retake photograph". Development effort must never be spent trying to rescue measurements from photographs Watch Align would never accept as a QC input in production; such images have value only as negative examples the suitability gate should reject.

Perspective correction (see the first principle above) may eventually improve measurements *within* the accepted QC pose envelope. It must never be used to justify accepting photographs that would otherwise never be valid Watch Align QC inputs -- that would silently widen the input contract through the back door of "better correction" rather than through an evidenced decision about what pose envelope production actually needs to support.

Practical consequence for image selection: when assembling any experimental corpus (frontal proof, genuine baseline, replica controls), judge each candidate image against this input contract first, independent of whether the measurement engine could technically produce some numbers on it. Reject unsuitable images explicitly, with a stated reason, rather than omitting them silently or spending effort trying to make them work.

## Governing rules

1. Prefer local landmark ratios over absolute pixel distances.
2. Prefer directly observed neighbouring landmarks over inferred global geometry when both can answer the QC question.
3. Every feature must correspond to a comprehensible physical QC claim.
4. Establish the genuine distribution before defining a pass/fail tolerance.
5. Measurement repeatability must be substantially tighter than the defect/tolerance the feature is intended to detect.
6. If acquisition or detection uncertainty is too large, return not-assessable for that feature rather than manufacture a pass/fail result.
7. Do not add projective correction, conic fitting, global rectification or other mathematical machinery unless an experiment demonstrates that it improves repeatability or cross-pose invariance for the specific feature.
8. Do not rescue an unstable measurement by merely widening thresholds or suppressing inconvenient results. Diagnose the landmark detector or abandon that measurement formulation.
9. Keep genuine manufacturing variation, measurement error and genuine-vs-replica separation as three distinct quantities.
10. Do not treat repeated photographs of one physical watch as independent examples.
11. Frontal landmark measurement is a hard gate. Perspective work cannot substitute for failure at this stage.
12. Do not use perspective or projective correction to accept photographs outside the QC-representative pose envelope. That envelope is decided by evidence about what production QC input actually looks like, not by what correction can technically recover.
13. Reject unsuitable candidate images explicitly, with a stated reason, rather than omitting them silently or spending effort trying to measure them. An unsuitable image is evidence for the suitability gate, not a measurement task.
14. Define each QC feature from the human visual rule first. The mathematical formulation must express that rule, not redefine it for computational convenience.
15. When a directly observed local datum can answer the QC question, do not substitute an inferred global datum merely because existing code already provides it.
16. If a single local visual relationship is sufficient for the human QC judgement, treat that as the primary candidate measurement. Do not automatically combine additional landmarks into the metric; use them only if evidence shows that they add useful independent information.

## Required evidence for a QC feature

A feature is eligible for production QC only after all of the following are demonstrated:

- Landmark definition: the physical points/edges being measured are explicit and reproducible.
- Frontal measurement proof: the feature can be measured reliably without relying on perspective correction.
- Genuine baseline: multiple independent genuine watches establish the feature's distribution.
- Repeatability: the same physical watch under bounded realistic capture variation produces a sufficiently small measurement spread.
- Discrimination: known replica defects or controlled geometric deviations produce changes distinguishable from genuine variation plus measurement error.
- Explainability: a reported failure can be expressed in physical terms, for example '12 triangle sits proportionally too close to the minute track relative to genuine references.'

## Validation philosophy

Repeated tests are required to validate a measurement system, not to compensate for a basic ratio that cannot be measured consistently.

For a local ratio feature, the expected development sequence is:

1. Prove reliable landmark detection on representative frontal images.
2. Define the simplest dimensionless ratio that expresses the physical relationship.
3. Prove frontal repeatability.
4. Measure independent genuine watches.
5. Test known replica defects or controlled deviations.
6. Challenge the proven feature with perspective.
7. Introduce pose correction only if the perspective challenge proves it necessary and the correction measurably recovers the frontal result.

Complexity must earn its place through measured improvement.

## GMT 12-triangle reset

The current Stage 3 proportional/projective research remains useful evidence, particularly because perturbation testing exposed detector and projective-fit discontinuities. It is not automatically the production architecture.

The immediate GMT milestone is:

'Can Watch Align accurately and repeatably measure the 12-triangle relationships on a high-quality, essentially frontal GMT image without perspective correction?'

Until that is demonstrated, GMT perspective/rectification development is paused for this QC feature.

Candidate local relationships should include, where reliably detectable:

- coronet reference to triangle apex;
- triangle base to 60/minute-track reference;
- triangle position within the coronet-to-minute-track interval;
- triangle height relative to that local interval;
- triangle base width relative to triangle height;
- left/right symmetry and local 12-axis displacement.

The first implementation should use the minimum geometry necessary to locate and measure those landmarks. Global dial radius or rectification may later be evaluated as secondary evidence, but should not be the default denominator when a stronger local reference is available.

## What is explicitly not frozen

The following must not be treated as established simply because earlier experiments used them:

- the current Stage 3 projective feature set;
- the three-point projective radial correction as a required production step;
- existing experimental guardrail thresholds;
- any numerical genuine tolerance not supported by an adequate independent genuine sample;
- any assumption that more mathematical correction necessarily produces a more accurate QC measurement.

Historical experiments should be preserved because they contain useful negative and positive evidence. New work should be evaluated against the principles in this document.
