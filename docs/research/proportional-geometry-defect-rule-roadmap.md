# Proportional Geometry - Defect Rule Roadmap

Status: live supplemental research

Date: 2026-09-23

Purpose: translate recurring r/RepTimeQC GMT concerns into simple proportional/geometric rules that can be tested with calibration data. This is prioritisation research, not a defect-threshold specification.

## Evidence-driven priority order

Targeted RepTimeQC sampling repeatedly surfaces the following geometry concerns on GMT-Master II QC:

1. 12 triangle left/right shift, clockwise/counter-clockwise tilt, or radial high/low placement,
2. 6 baton tilt/left-right bias,
3. 9 baton upward/sideways bias or tilt,
4. cyclops crookedness/offset relative to the date aperture,
5. date glyph high/low/left/right centring,
6. bezel triangle or colour transition alignment,
7. rehaut alignment.

This ordering should influence research effort. It does not imply prevalence estimates because the Reddit sample is not random and posters disproportionately ask about things they already suspect.

## Tier A - dial marker rules

These are the best fit for the current Watch Align geometry engine because they lie on the dial plane and relate naturally to the existing centre, hour-axis and peer-marker geometry.

### Rule A1 - marker centre radial placement

Question:

`Is the marker centre radially inward/outward relative to where this model's genuine geometry predicts it should be?`

Preferred measurement:

- canonical/projective marker-centre radius,
- expected centre radius from genuine calibration profile,
- signed residual.

For round markers, expected centre should be leave-one-out peer-derived where possible.

For 12/6/9 special markers, expected centre comes from the model/family profile projected into the current image.

Human overlay:

- expected centre gate/target,
- observed centre,
- short local radial displacement cue.

### Rule A2 - marker centre tangential placement

Question:

`Is the marker centre left/right (clockwise/counter-clockwise) of its expected hour line?`

Preferred measurement:

- expected image-space intersection of nominal hour line and expected centre-radius reference,
- signed tangential displacement normalised by local marker width or peer-marker diameter,
- optionally equivalent canonical angular offset where transformation is trustworthy.

Human overlay:

- expected hour axis,
- observed centre point,
- short lateral displacement cue.

### Rule A3 - marker orientation

Question:

`Does the marker's own symmetry/long axis coincide with the predicted image-space hour line?`

Do NOT compare against global vertical/horizontal.

Preferred measurement:

- signed endpoint point-to-line residuals,
- axis-line coincidence residual,
- optional observed-axis vs predicted-axis image-space angle as a secondary descriptor.

Interpretation:

- endpoint offsets same sign -> translation-like,
- endpoint offsets opposite signs -> rotation-like.

This directly targets recurring 6/9 baton and 12 triangle tilt concerns.

### Rule A4 - 12 radial translation vs triangle shape

Use apex, centre and base projective radial residuals.

Define:

- common-mode residual = robust centre of apex/centre/base residuals,
- differential residual = residual spread or base-minus-apex residual.

Interpretation:

- common-mode large, differential small -> whole triangle shifted radially,
- common-mode small, differential large -> size/shape/span difference,
- both large -> compound issue or poor measurement.

This is a better match for the user's known subtle 12 concern than simply testing one base-to-minute-track gap.

### Rule A5 - 12 lateral translation vs rotation

Use signed tangential residuals of apex, centre and base-centre.

Interpretation:

- all similar same sign -> lateral translation,
- apex and base-centre opposite signs around a near-correct centre -> rotation,
- centre offset plus slope -> translation + rotation.

### Rule A6 - 6/9 baton translation vs rotation

Use inner endpoint, centre and outer endpoint.

Radial dimension:

- same radial residual across all -> radial shift,
- changing residual across length -> length/shape difference.

Tangential dimension:

- same-sign endpoint offsets -> sideways translation,
- opposite-sign endpoint offsets -> rotation,
- one endpoint near expected and one displaced -> pivot-like rotation/compound shift.

This rule should be generic to any elongated hour marker, with model-specific expected geometry supplied separately.

### Rule A7 - round-marker leave-one-out anomaly

For round hour markers:

- remove marker under test from peer fits,
- fit peer centre/inner/outer conics,
- intersect nominal hour ray with each,
- compare observed centre and radial edges to the independent prediction.

Useful outputs:

- signed radial centre residual,
- signed tangential centre residual,
- diameter residual,
- radial edge asymmetry.

This provides a generic anomaly rule without a model-specific fake dial template.

## Tier B - date/cyclops geometry

Reddit QC repeatedly identifies crooked cyclops as a visually important defect class. It is geometrically simple, but physically different from dial markers because the cyclops sits on the crystal above the dial/date aperture.

### Rule B1 - cyclops/date-aperture relative rotation

Detect local quadrilaterals/edge directions for:

- date aperture,
- cyclops boundary.

Compare:

- long-edge direction mismatch,
- short-edge direction mismatch.

Because the surfaces are at different depth/planes, do not claim a universal zero-angle relation under arbitrary viewpoint until genuine multi-view calibration confirms it.

Strongest usage may be:

- compare against genuine model distribution at similar apparent viewpoint,
- or require consistency across two or more dealer views.

### Rule B2 - cyclops centre offset

Normalise cyclops centre relative to date aperture:

`dx = (cyclops_cx - aperture_cx) / aperture_width`

`dy = (cyclops_cy - aperture_cy) / aperture_height`

These are simple dimensionless descriptors but still viewpoint-sensitive because of depth difference.

### Rule B3 - date glyph centring

Within the aperture itself, measure:

- glyph centre x/aperture width,
- glyph centre y/aperture height,
- left/right margin ratio,
- top/bottom margin ratio.

If multiple dates are supplied, aggregate per-date rather than assuming one numeral shape represents the full date wheel.

This is likely easier and more defensible than font-shape scoring.

## Tier C - bezel rules

The rotating bezel creates a critical distinction.

### Rule C1 - internal bezel geometry

Potential manufacturing-QC targets independent of bezel click position:

- colour-transition position relative to bezel's own engraved scale,
- bezel triangle/pip position relative to bezel's own 24-hour reference,
- engraving-centre angular regularity,
- opposite-side transition symmetry.

These should be preferred over dial-to-bezel alignment for automatic conclusions.

### Rule C2 - bezel-to-dial displayed alignment

Examples:

- bezel triangle vs dial 12,
- bezel 6/18 vs dial 3/9.

Treat as advisory/display state only unless the bezel's centred click/play position is controlled. Reddit reviewers repeatedly point out that apparent mismatch may be normal bezel play and can be corrected by rotating the bezel.

## Tier D - rehaut

Do not prioritise rehaut alignment in the initial proportional engine.

Reasons:

- curved 3D surface,
- strong perspective sensitivity,
- engraving visibility changes with reflection/focus,
- not on the dial plane,
- requires a different projection model.

Potential future metric: rehaut crown angular relation to the independently established dial 12 axis, but only with viewpoint-aware calibration.

## False-positive guardrails derived from community QC

### Guardrail 1 - crooked source photo

Never use a screen-space alignment grid as evidence without establishing pose. Reddit examples show users incorrectly flagging several markers when the source image itself is tilted.

Required behaviour:

- establish pose/expected projected axes first,
- compare marker geometry against those projected axes,
- suppress or lower confidence when pose support is weak.

### Guardrail 2 - bezel play

Do not turn one photographed dial-to-bezel mismatch into a manufacturing defect.

### Guardrail 3 - cyclops viewpoint/reflection

One oblique photo can make cyclops geometry ambiguous. Prefer multiple views or a model calibrated explicitly for viewpoint.

### Guardrail 4 - marker self-reference

The marker under test should not materially influence the expected reference used to judge it.

### Guardrail 5 - repeated photos are not independent watches

Multiple dealer views strengthen repeatability evidence but must not inflate population sample size.

## Recommended initial implementation sequence

1. prove 12 apex/centre/base projective radii on genuine calibration GMTs,
2. prove 12 common-mode vs differential residual decomposition,
3. prove 12 lateral-translation vs rotation decomposition,
4. extend the same generic residual-vector architecture to 6 and 9 batons,
5. extend leave-one-out centre/radial rules to round markers,
6. only then start cyclops/date local geometry,
7. internal bezel geometry after dial-marker rules are stable,
8. rehaut later.

## Proposed generic rule output schema

A future marker rule can return neutral structured evidence such as:

- marker_id,
- measurement_supported,
- radial_translation_residual,
- tangential_translation_residual,
- orientation_residual,
- size_span_residual,
- asymmetry_residual,
- expected_band_source,
- apparent_tilt,
- measurement_confidence,
- human_review_recommended.

This keeps the engine generic while the watch-specific profile supplies expected geometry and calibration bands.
