# Phase 4 Experiment 1 — rectification benchmark — 2026-10-04

## Status

Research only. No Android, detector, calibration, verdict, threshold, or production-path changes.

Decision: **GO to a real-image prototype benchmark; NOT a production implementation decision.**

The experiment was run offline against the completed #73 calibration evidence artifact and a controlled projective simulation designed to match the current 124060 geometry assumptions.

## Why this experiment exists

The current dial frame is explicitly affine-only: it models the dial as an ellipse and removes the axis squash before measuring internal geometry. Perspective beyond that is intentionally left to the photo-angle gate.

The Phase 4 question is whether a stronger single-photo projective correction can materially reduce internal geometry error on tilted QC-style images without damaging near-frontal images.

## Existing evidence used

GitHub Actions run #73 (`watch-calibrator-124060-37189145179`) completed successfully and was used as the frozen evidence source.

Its 124060 photo outputs contain 318 genuine and 55 replica photo rows. A marker-layout tilt estimate is available for 76 genuine and 14 replica photos.

For the genuine photos with a tilt estimate:

- median tilt: 5.83 degrees;
- 65.8% are above 5 degrees;
- 26.3% are above 8 degrees;
- 14.5% are above 10 degrees;
- maximum: 19.55 degrees.

For replica photos with a tilt estimate:

- median tilt: 5.09 degrees;
- 57.1% are above 5 degrees;
- 7.1% are above 8 degrees;
- maximum: 14.34 degrees.

This means the non-affine perspective regime is not an edge case in the existing evidence.

The existing same-watch research also already showed that apparent 12-marker clearance can vary much more than simple cosine foreshortening predicts, while rehaut asymmetry carries real pose information. That makes a stronger rectification experiment justified, but does not prove that perspective is the only source of the detector spread.

## Benchmark design

A canonical circular dial was projected through a pinhole camera using a true planar homography. The projected dial radius was approximately 400 image pixels, representative of a useful QC crop.

Randomized variables per trial:

- tilt direction;
- roll up to +/-15 degrees;
- small image-plane translation;
- tilt magnitudes from 0 to 20 degrees.

Observation noise was deliberately injected:

- outer dial-boundary samples: 0.5 px Gaussian noise;
- structural marker centres: 0.5, 1.0, and 2.0 px Gaussian noise.

### Baseline

The baseline reproduces the current geometric assumption:

1. fit the projected outer dial as an ellipse;
2. translate to the ellipse centre;
3. rotate to the ellipse axes;
4. independently scale the two axes back to a circle;
5. use the 12 direction as the orientation anchor.

This is an affine rectification. It cannot remove general projective eccentricity.

### Research candidate

The candidate is a two-ring / structural-marker projective proxy, not production code.

It starts from the affine baseline and refines a full projective homography so that:

- the outer dial boundary maps to a unit circle;
- the eight round-marker centres at 1, 2, 4, 5, 7, 8, 10 and 11 map to their shared marker-centre radius (`rho = 0.819`);
- the 12 direction fixes in-plane orientation.

This deliberately tests the information content of multiple circular/structural constraints. It does not assume that the current Android detectors can already supply these constraints accurately enough.

### Holdout geometry

The fitted constraints were not used as the sole evaluation target. Error was measured on separate internal points, including a 60-point minute-track ring and inner-dial points.

Primary metrics:

- canonical positional RMSE;
- radial RMSE;
- angular RMSE;
- centre-recovery error;
- error in a synthetic radial marker-to-minute-track gap.

## Main result

At **1 px marker-centre noise**, median canonical positional RMSE was:

| true tilt | affine baseline | projective candidate | improvement |
|---:|---:|---:|---:|
| 0 deg | 0.0013 | 0.0050 | worse |
| 5 deg | 0.0293 | 0.0052 | 82.3% |
| 10 deg | 0.0613 | 0.0050 | 91.9% |
| 15 deg | 0.0908 | 0.0052 | 94.2% |
| 20 deg | 0.1193 | 0.0052 | 95.7% |

At 5 degrees, angular RMSE fell from about **1.82 degrees to 0.32 degrees**.

At 10 degrees it fell from about **3.85 degrees to 0.31 degrees**.

The synthetic radial gap error at 1 px marker noise improved by about:

- 87% at 5 degrees;
- 93% at 10 degrees;
- 96% at 15 degrees;
- 97% at 20 degrees.

The result is robust to noisier structural points. At **2 px marker-centre noise**, the projective candidate still reduced positional error by about:

- 69% at 5 degrees;
- 82% at 10 degrees;
- 88% at 15 degrees;
- 90% at 20 degrees.

## Critical negative result

The projective solution is **not universally better**.

At a true 0-degree view, the affine baseline is already essentially correct. The extra projective degrees of freedom then fit structural-point noise and make the result worse.

A finer 0–6 degree sweep found the crossover depends on structural-point precision:

- with ~1 px structural-point noise, projective correction begins to win consistently around 1.5–2 degrees and is dominant by 3–4 degrees;
- with ~2 px noise, the crossover is closer to 2–3 degrees and only becomes strongly reliable around 4 degrees.

Therefore the research result supports a **gated hybrid**, not replacement of the current affine frame.

Conceptually:

`near frontal + weak projective evidence -> keep affine frame`

`meaningful tilt + high-confidence multi-constraint fit -> allow projective refinement`

`severe/contradictory evidence -> withhold/reject rather than force correction`

## Real-evidence consistency check

The existing frozen 124060 evidence makes this potentially material rather than academic: among genuine photos with a usable marker-pose estimate, roughly two thirds are above 5 degrees tilt.

Within the existing multi-photo genuine groups, `twelve.gap_r` also shows a moderate positive association between tilt and within-watch absolute deviation (Pearson about 0.48, Spearman about 0.39 on the small subset where repeated values and pose were both available). Other current metrics do not show a strong monotonic tilt relationship in this limited sample.

This supports further testing of rectification, but also warns that detector repeatability remains a separate error source.

## Decision

**GO to Experiment 1B: real-image projective prototype benchmark.**

Do not integrate anything into Android yet.

The next test must answer whether the current image evidence can recover the extra projective constraints reliably enough on real QC-style photos.

Minimum requirements for a real-image GO:

1. use existing frozen images only;
2. obtain projective constraints from actual detected dial/marker/rehaut evidence, not ground-truth synthetic points;
3. compare against the current affine frame on same-watch and perturbation evidence;
4. show lower repeatability error on tilted images;
5. do not degrade near-frontal images, via gating/fallback;
6. fail closed when the projective fit is weak or contradictory;
7. no Android or production threshold changes during the benchmark.

If real-image constraints cannot reproduce the controlled gain, mark the approach NO-GO regardless of the synthetic result.

## What this does not prove

- It does not prove that current marker detectors are accurate enough for projective fitting.
- It does not prove that two physical conics can be extracted robustly through the crystal/rehaut in arbitrary QC photos.
- It does not prove that perspective is the main cause of every current measurement error.
- It does not justify changing production behaviour.
- It does not establish a final tilt threshold.

The controlled result proves only that the current affine model leaves large, predictable projective error once tilt is meaningful, and that multi-constraint projective information is theoretically capable of removing most of that error even with modest point noise.