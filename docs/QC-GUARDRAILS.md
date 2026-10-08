# Watch Align QC guardrails

These rules apply to all future calibration, model work, measurement changes, evidence classification and results UI.

They are product requirements, not suggestions.

## Product purpose

Watch Align compares measurable features in an uploaded QC/dealer photo with proven-genuine reference geometry.

It is not:
- a genuine/fake classifier;
- an automatic GL/RL decision;
- a guarantee that a watch is good or bad.

A clean replica may legitimately show no detectable deviation in the features that can be assessed.

## 1. Genuine watches define calibration

- Genuine watches define nominal geometry, genuine manufacturing variation and genuine photo-to-photo measurement uncertainty.
- Replica/QC examples are validation evidence only. They must never set, widen or narrow genuine limits.
- One physical genuine watch is one independent manufacturing sample. Multiple photos of the same watch are useful for repeatability and uncertainty, not as extra manufacturing samples.
- A valid genuine outlier must not be discarded merely because it widens the genuine envelope. Exclude only for a documented provenance, pose, contamination, resolution or measurement-quality reason.
- Keep manufacturing variation and measurement uncertainty as separate quantities.

## 2. "Outside genuine" is not automatically a defect

A numerical excursion beyond the measured genuine reference does not by itself mean bad QC.

Use evidence strength:

- **WITHIN RANGE**: the measurement is within the applicable genuine reference.
- **WORTH A LOOK**: the measurement is outside the measured genuine range, but the excess is small enough that photo/detector uncertainty or limited reference coverage could plausibly explain it.
- **CLEAR FINDING**: the excursion is materially beyond the genuine reference and the relevant genuine-derived measurement uncertainty.
- **NOT ASSESSED**: the image does not support a trustworthy measurement.

Do not collapse these states into PASS/FAIL, GL/RL or genuine/fake.

## 3. Image validity comes before judgement

Before a feature can become a finding, confirm that the feature is actually measurable in that photo.

Hands, reflections, glare, dust, poor perspective, low resolution, ambiguous edges or failed registration must cause the affected feature to fail closed when they can materially influence the measurement.

Prefer **NOT ASSESSED** over a confident but contaminated result.

Withhold only the affected feature where possible. Do not throw away clean independent features because one marker is obstructed.

## 4. Present practical QC significance, not mathematical perfection

Watch Align may measure deviations that are invisible in normal wear.

- A tiny numerical exceedance must not receive the same prominence as a large, repeatable deviation.
- One marginal marker must not imply that the whole watch is poor.
- Several independent strong findings are more meaningful than one borderline measurement.
- The more forensic zoom or annotation is required merely to notice a deviation, the more cautiously it should be presented. This is a presentation principle, not a substitute for calibration.
- If a borderline result depends heavily on one photo, prefer recommending another clean photo rather than escalating severity.

## 5. Findings must show their evidence

Where practical, every reported finding should have visual evidence:
- the full registered overlay for context;
- a close-up of the affected feature;
- the expected/reference outline or equivalent visual comparison;
- concise plain-English wording.

The user should be able to inspect why the app raised the finding.

Technical values may be available as secondary detail, but the main result should remain human-readable.

## 6. Keep model-specific evidence isolated

Every model/family must have its own validated geometry, genuine reference data and uncertainty evidence where those quantities differ.

A new model must not alter an existing model's:
- measurements;
- pose behaviour;
- genuine references;
- uncertainty allowances;
- classifications;
- UI wording or output;

unless that existing-model change is independently justified and regression-tested.

Missing evidence must fail closed. If a model or feature has no defensible genuine reference or uncertainty evidence, it cannot be promoted to a CLEAR finding.

## 7. Adding models must not weaken existing models

The intended architecture is:

**shared photo/pose pipeline -> model specification -> model-specific analyzers/reference data -> generic findings/results UI**

Adding a model should primarily mean adding:
- a model specification;
- model geometry;
- supported feature definitions;
- genuine reference data;
- uncertainty data;
- model-specific analyzer code only when a genuinely new shape/feature requires it.

Do not duplicate or fork the whole GMT pipeline for each new model.

Before adding a new model, the current model set must pass unchanged regression tests through any architecture refactor.

## 8. Validation order

For any new feature or model:

1. Establish a defensible genuine nominal/master.
2. Measure independent genuine manufacturing variation.
3. Quantify photo/detector uncertainty using repeated genuine photos.
4. Freeze the genuine reference and uncertainty rules.
5. Validate against held-out genuine photos.
6. Only then test replica/QC examples for usefulness.
7. Only then promote the feature into the product.

Do not use replica failures to tune genuine thresholds after step 4.

## 9. Regression is a product requirement

Architecture work that claims no functional change must prove no functional change.

For an existing model, require as applicable:
- zero numeric measurement differences on the established regression set;
- identical feature classifications;
- identical contamination/withholding behaviour;
- no unexpected new genuine findings;
- passing unit tests and build.

A refactor is not complete merely because it compiles.

## 10. Wording rules

Preferred language:
- "within measured genuine range";
- "worth a look";
- "clear finding";
- "not assessed";
- "measurement comparison";
- "this photo".

Avoid:
- "fake";
- "authentic";
- "bad watch";
- "reject";
- "RL";
- "GL";
- claims that a single measurement proves manufacturing quality.

The app provides measured evidence for human QC judgement. It does not make the purchasing decision.

## 11. When evidence conflicts

Do not force a verdict.

If genuine evidence, repeatability, visual inspection and detector output disagree:
- preserve the raw evidence;
- identify the conflict;
- run a bounded experiment;
- withhold or downgrade the feature until the conflict is understood.

Do not solve uncertainty by silently widening limits, adding arbitrary tolerances or fitting the pose to the feature being judged.

## Governing principle

**The app should be more willing to say "not assessed" or "worth a look" than to manufacture confidence.**

Accurate restraint is part of the product.
