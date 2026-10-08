# Claude instructions for Watch Align

Before substantial work read:

- `docs/PRODUCT_SCOPE.md`
- `docs/CALIBRATION_PROTOCOL.md`
- `docs/QC-GUARDRAILS.md`
- `docs/HANDOFF.md`
- `AGENTS.md`

The active project direction is calibration-first and reference-deviation based.

## QC product guardrails

`docs/QC-GUARDRAILS.md` is a governing project document. Read it before changing measurement logic, genuine references, uncertainty, findings/results wording, contamination handling, or adding a watch model.

Its rules are project requirements, not suggestions.

In particular:

- Genuine watches define calibration limits. Replica examples validate usefulness; they never set thresholds.
- "Outside measured genuine range" is not automatically a defect or bad QC.
- Keep genuine manufacturing variation and photo/detector uncertainty separate.
- Hands, glare, reflections, poor angle, insufficient resolution and other material contamination must fail closed for the affected feature.
- CLEAR findings require materially stronger evidence than a marginal numerical excursion.
- Borderline evidence should be presented as WORTH A LOOK, not PASS/FAIL or GL/RL.
- Findings should show visual evidence where practical.
- Do not make authenticity, accept/reject, GL/RL or purchasing decisions for the user.
- Adding a new model must not alter an existing model's measurements, references or behaviour unless independently justified and regression-tested.
- Missing model/reference/uncertainty evidence must fail closed rather than manufacture confidence.

Key rules:

- The product analyses uploaded QC/dealer photos and reports measurable deviations from proven-genuine reference geometry.
- Do not build or describe a genuine/fake classifier.
- A clean replica may correctly return no detectable deviation in assessed features.
- Reuse existing images/artifacts before searching for new data.
- Answer research questions with small offline experiments before touching production code or CI.
- Establish a nominal genuine master first, then genuine variation, while separating photo/detector error.
- Use opposing/shared marker relationships to identify perspective contamination before widening a genuine envelope.
- Replica data validates usefulness; it never defines genuine limits.
- First restart task: reproduce one known-working GMT calibration feature with the reusable protocol and compare quantitatively with the established result.
- Do not resume old stacked calibration-platform, projective-refinement, or broad-family expansion work unless the new evidence specifically requires it.
- Each validated feature may ultimately be implemented as deterministic code, vision AI, hybrid, or withheld. Let the evidence decide.
