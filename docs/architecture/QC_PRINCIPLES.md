# Watch Align QC Principles

Status: **governing design principles**

## Product objective

Watch Align answers:

> **How does this watch differ from the proven-genuine reference in the features that can be assessed reliably from this photo?**

It does not decide whether a watch is genuine or replica.

A result of **no detectable deviation in the assessed features** is valid, including for a very accurate replica.

## Reference hierarchy

For each feature keep these concepts separate:

1. **Nominal genuine geometry** — the best estimate of the intended visual geometry.
2. **Genuine watch-to-watch variation** — differences among independent genuine physical watches.
3. **Photo/detector uncertainty** — variation caused by pose, lighting, resolution, sharpening, compression, obstruction and measurement instability.
4. **Submitted-watch deviation** — how far the specimen differs from the frozen genuine reference after uncertainty is accounted for.

Do not combine these into one broad tolerance.

## Nominal master first

Start with the best available proven-genuine near-frontal image for the feature. A clean loose dial can be an especially strong nominal source, but is not mandatory when existing images are adequate.

The nominal master defines the zero point. It does not define manufacturing tolerance by itself.

## Genuine variation second

Measure independent genuine physical watches around the nominal master.

Repeated photos of the same watch estimate measurement/photo uncertainty, not manufacturing variation.

Source diversity matters. Dealer style, sharpening and camera angle must not be mistaken for watch variation.

## Perspective is contamination until proved otherwise

An outlying measurement on a genuine photo must not automatically widen the genuine envelope.

Before accepting it as watch variation, inspect related/opposing geometry. Coherent opposite-signed residuals can indicate pose: for example one side of the dial appears expanded while the opposite side appears compressed.

Where this relationship is validated, use it to correct, pair or exclude the contaminated measurement. Otherwise return unassessable rather than inventing certainty.

Prefer simple relational evidence over explicit camera-pose reconstruction when it performs as well or better.

## Local physical relationships are preferred

Use the simplest directly observed relationship that corresponds to the visible QC feature.

Examples include:

- marker centre relative to the corresponding minute marker;
- marker rotation relative to local minute-track orientation;
- marker clearance relative to the minute track;
- opposing-marker residual relationships;
- date numeral clearances inside the aperture;
- bezel zero relative to the 12/60 dial reference.

Global rectification, homographies and inferred canonical geometry are supporting tools only. They must demonstrate better held-out accuracy before becoming required.

## Measurement and visual overlay are separate

Automated measurement asks **how far the physical relationship deviates from genuine reference**.

The overlay asks **how to make that deviation easy for a human to see**.

They may use different geometry. A useful crosshair or guide line does not have to be the numerical datum, and a good numerical datum does not have to be drawn as the only guide.

## Do not let edge ambiguity contaminate unrelated metrics

Marker position, lume boundary, white-gold surround, size and shape are different measurements.

If marker size is sensitive to lighting/thresholding but marker centre is stable, retain the centre measurement and withhold size. Do not invalidate all outputs from one detector simply because one edge definition is unstable.

## Genuine reference is frozen before replica validation

Replica evidence never moves the genuine reference range.

After the genuine envelope is frozen, validate it using independent known-defect QC examples and accepted/GL controls.

Success means useful specimen-specific defect detection with low false flags and sensible abstention. It does **not** require population-level separation of genuine and replica watches.

## Implementation is feature-specific

After validation, classify each feature as:

- deterministic code;
- vision AI;
- hybrid code + AI;
- not reliable enough.

A clean geometric relationship should normally become deterministic code. A contextual visual feature may be better served by AI. Do not choose the technology before evidence exists.

## Research discipline

1. Use existing images/artifacts first.
2. Work on one feature at a time.
3. Prefer a small offline experiment to CI, APK builds or new infrastructure.
4. Compare new methods against a known result where possible.
5. Use held-out images when refining the protocol.
6. Record exclusions and uncertainty.
7. End every run with `LESSONS LEARNED`.
8. Proposed prompt/protocol changes are validated separately before promotion.
9. Do not silently tune the method until it matches the known answer.
10. Production code changes happen only after the research rule proves useful.

## Current control experiment

GMT is the control because it is already known to work.

Recalibrate one simple existing GMT feature with `docs/CALIBRATION_PROTOCOL.md` using existing proven-genuine images. Compare the independently derived result with the current GMT calibration. Do not change production GMT behaviour during this experiment.

Only after the protocol reproduces the control reliably should it expand to more GMT features and then to Submariner.

## Governing output language

Prefer feature-level statements such as:

- `within the proven-genuine reference`;
- `measurable deviation from genuine reference`;
- `photo does not support a reliable judgement`;
- `no detectable deviation in the assessed features`.

Do not output `genuine`, `fake`, `perfect replica`, or an authenticity probability.
