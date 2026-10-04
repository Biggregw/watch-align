# Claude instructions for Watch Align

Before substantial work read:

- `docs/PRODUCT_SCOPE.md`
- `docs/CALIBRATION_PROTOCOL.md`
- `docs/HANDOFF.md`
- `AGENTS.md`

The active project direction is calibration-first and reference-deviation based.

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
