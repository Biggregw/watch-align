# Watch Align research handoff - 2026-09-21

## Scope

This note records the current evidence before any further production Android or Python QC changes. The active research branch is `experiment/projective-marker-normalization`. The Android implementation remains frozen while the Python research path is used to test geometry and marker ideas against the real-photo corpus.

## Corpus and current baseline

The 126710BLNR research corpus contains 252 fetched images from 31 independent physical watches/sources, plus the official Rolex reference image. Repeated photos of the same watch are not treated as independent watches.

The current Python pipeline accepted 69 of 252 images, about 27%. The dominant limitation is still pose acquisition on casual marketplace/QC photos, not an observed epidemic of accepted grossly-wrong poses.

A 50-image visual audit of accepted poses included:
- the two previously suspicious high-tilt cases,
- every accepted image selected by the high-tilt/high-centre-displacement criteria,
- the worst held-out fits,
- random controls.

The accepted poses were broadly locked to the correct dial and orientation. The master minute-track generally followed the real minute track. The main visible weakness at steep camera angles was residual radial distortion, especially noticeable near the upper dial in the two named extreme-tilt examples.

## Repeatability findings

Angular marker position remains the more promising measurement. Earlier corpus analysis showed sub-degree within-watch MAD for most markers.

Radial marker position is substantially more tilt-sensitive. The local safe-operating-area analysis below shows a clear deterioration above about 10 degrees apparent tilt, and a much larger deterioration above 15 degrees.

The visual top-sector pattern in the two extreme examples is real, but the broader corpus does not support treating this as only a 1/2/10/11 problem. Above 10 degrees, radial instability also appears materially at markers 5, 6 and 8. This points to a broader perspective/localisation limitation rather than a single bad master-geometry sector.

## Projective marker-normalisation A/B test

A read-only A/B experiment was run on the same detected marker blobs:
- baseline: current ellipse-only coordinate normalisation,
- projective: map the same detected marker centre through the inverse accepted final homography.

The projective method was worse overall:
- radial pooled within-watch MAD: 0.2917 %R -> 0.3567 %R, 22.3% worse,
- angular pooled within-watch MAD: 0.2446 deg -> 0.2605 deg, 6.5% worse,
- high-tilt radial median residual: 2.0276 %R -> 2.2935 %R,
- high-tilt angular median residual: 0.6453 deg -> 0.9136 deg,
- paired high-tilt residuals: projective better 11, worse 18, equal 0.

Conclusion: do not switch marker QC to the final projective homography as a simple fix. The current final homography is useful for pose/visual rectification, but it is not a better marker-coordinate normaliser in this corpus.

## Fast local experiment path

A local cache has been built from the 69 currently accepted images. It contains the current pose/marker diagnostics plus the projective A/B outputs, 184 fields per image.

On the local research environment:
- cache load is about 0.006 s,
- a full marker repeatability sweep is about 0.05 s.

The reusable builder is:
`tools/watch_align_py/build_fast_experiment_cache.py`

Use the cache for downstream scoring, filtering, marker-selection and threshold experiments. Re-run the expensive image pipeline only when changing pose acquisition, the projective refiner, image-space marker detection, or any other upstream geometry.

## Current working rules

1. Do not change production marker thresholds, master geometry or Android QC from the projective A/B result.
2. Treat apparent tilt as a measurement-quality variable, not proof that a pose is wrong.
3. Do not use `center_displacement_frac` alone as a rejection rule. The visual audit contained large-displacement examples whose final rectification was still sensible.
4. Keep calibration and validation watches separate. Do not tune on held-out validation watches.
5. Continue to aggregate by `physical_watch_id`, not image count.
6. Any proposed production change should first beat the current baseline on within-watch repeatability and then survive visual review and held-out validation.

## Recommended next investigation

The next useful research target is not a lower projective-refinement threshold. It is to understand why radial marker localisation changes with camera tilt while angular localisation is relatively more stable.

Candidate investigations should isolate one variable at a time:
- image-space marker centroid bias under foreshortening,
- ROI basis construction from the ellipse,
- radial expected-centre definition for each marker shape,
- whether a local marker-specific correction can be derived without using the marker under test to move the pose,
- whether radial QC should simply be suppressed when photo geometry is outside the validated operating area.

No production change is justified yet.
