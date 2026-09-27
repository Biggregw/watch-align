# Submariner 124060 marker-rotation attention pilot — 2026-09-26

Source: the same 11 user-supplied Submariner stills `34094.jpg`–`34104.jpg` used for the rehaut study. Images are not committed.

## Current branch logic exercised
The existing GMT12 geometry defines `rotation_deg` as the signed angle between the detected triangle top edge and the local 59-to-1 minute-track tangent. The new research-only attention model then uses both absolute angle and the implied endpoint rise across the detected triangle width.

A consistent watch crop was used for this pilot so full-phone-frame Hough scale selection did not dominate the result.

| image | rotation deg | triangle width px | implied endpoint rise px | attention/result |
|---|---:|---:|---:|---|
| 34094 | -8.99 | 28.44 | 4.50 | STRONG |
| 34095 | -1.79 | 34.06 | 1.06 | CLEAR |
| 34096 | -1.63 | 35.06 | 1.00 | CLEAR |
| 34097 | -2.56 | 32.06 | 1.43 | CHECK |
| 34098 | -0.65 | 28.02 | 0.32 | CLEAR |
| 34099 | — | — | — | UNASSESSABLE: triangle not constrained |
| 34100 | +1.15 | 26.00 | 0.52 | CLEAR |
| 34101 | -3.39 | 30.02 | 1.78 | CHECK, but previous hybrid-pose study marks this frame severe/RETAKE so angle is pose-limited |
| 34102 | — on the fixed crop | — | — | UNASSESSABLE on the consistent crop; an alternate crop can produce a roughly +3.1° result, exposing ROI/seed sensitivity |
| 34103 | -1.50 | 31.02 | 0.81 | CLEAR |
| 34104 | +1.09 | 27.02 | 0.51 | CLEAR |

## Interpretation
Because this is one unchanged physical marker, the spread cannot be real marker rotation. The `-8.99° STRONG` result on 34094 is therefore a false-positive measurement caused by the current top-edge landmark definition/extraction. The current edge-only rotation metric is not yet safe to use as a high-sensitivity production flag.

A second, whole-marker orientation check was evaluated from the triangle symmetry axis (base midpoint to tip) against the local minute-track normal. It is materially more stable and gives an aggregate central tendency of roughly `-1.8°` across the usable series, suggesting at most a very slight counter-clockwise apparent rotation. That is in the user's stated "couple of degrees / close-inspection" zone, not a confidently conspicuous defect from this set.

## Required refinement
Rotation should become a dual-evidence assessment:

1. whole-marker symmetry axis vs local minute-track normal as the primary orientation measurement;
2. triangle-base edge vs minute-track tangent as a supporting measurement;
3. pose gate from the hybrid rehaut/ellipse estimator;
4. detector/ROI stability gate;
5. highlight small (~2°) deviations for human inspection only when the measurement is resolved and the independent orientation cues agree sufficiently.

Do not promote the current `STRONG/CHECK/CLEAR` edge-only outputs to production until this refinement is validated on same-watch repeats and known deliberately-rotated synthetic/annotated controls.
