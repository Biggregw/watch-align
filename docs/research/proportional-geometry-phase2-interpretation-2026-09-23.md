# Proportional Geometry - Phase 2 interpretation

Status: live supplemental research

Date: 2026-09-23

Purpose: interpret the expanded 126710BLNR calibration-corpus findings without changing any frozen feature definitions or creating defect thresholds.

## Headline

The expanded calibration run materially changes the engineering picture. A single global rule such as "always prefer projective normalisation" is not supported. The evidence now points toward **feature-specific normalisation** with a practical low-tilt primary operating mode.

This is compatible with the product assumption that most dealer QC photos are sufficiently frontal for ordinary inspection.

## Expanded calibration facts to preserve

From the Phase 2 experiment branch:

- 10 independent calibration `gen_candidate` physical watches contribute at least one clean feature measurement,
- 37 clean per-image rows exist,
- 28 images support simple features,
- 20 images support projective features,
- calibration provenance is still concentrated in a limited set of marketplace channels,
- this is feasibility/calibration evidence, not a validated genuine-population distribution.

The current five frozen features were not redesigned during this expansion.

## Feature-specific conclusions

### 1. `apex_r_simple` is currently the strongest candidate low-tilt radial feature

Expanded behaviour:

- median stayed near the original estimate,
- between-watch MAD tightened dramatically,
- simple form is materially tighter than projective form in the expanded sample,
- simple tilt correlation is weak in the current evidence.

Research implication:

Do not projectively correct the triangle apex merely because projective normalisation is mathematically more sophisticated. For this feature, the simple coordinate is currently better supported empirically.

### 2. `centroid_tangential_offset_canonical` improved with population size

The expanded population moved the median close to zero and tightened the spread.

Research implication:

The earlier apparent left/right bias was likely small-sample noise. This feature remains promising as a translation-like descriptor, especially because tangential/angular geometry is less vulnerable to the radial perspective problem.

### 3. `base_r_simple` remains noisy

The simple base-radius feature did not tighten with more watches.

Possible explanations to test rather than assume:

- actual between-watch variation,
- segmentation instability at the triangle base corners,
- viewpoint sensitivity,
- local glare/edge ambiguity,
- the base is simply a poorer Euclidean landmark than the apex.

The projective base feature is much tighter in the expanded sample, so base geometry may be a case where projective normalisation is useful.

### 4. `base_width_over_height` remains broad

This feature did not become sharply clustered with more watches.

Research implication:

Do not promote width/height to a primary defect rule yet. It may remain useful as secondary shape evidence, especially if repeated across multiple photos of one watch, but it is not currently a strong first-line fingerprint.

### 5. `symmetry_axis_angular_deviation_deg` remains noisy

The median moved close to zero but the MAD remains relatively broad.

Research implication:

Prefer image-space incidence/line-consistency residuals against the predicted hour axis over a standalone Euclidean angle where practical. The earlier invariants note already recommends endpoint point-to-line residual patterns because line coincidence is more directly related to the projected geometry.

## Recommended hybrid normalisation policy

Do not select one radial representation globally.

For each feature independently, choose the representation that provides the best combination of:

- between-watch compactness,
- same-watch repeatability,
- weak tilt dependence inside the supported operating range,
- usable-image coverage,
- robust segmentation.

A plausible current direction, to be tested rather than hard-coded, is:

- apex radial position -> simple low-tilt coordinate,
- centre/base radial position -> projective coordinate where peer geometry is sufficiently conditioned,
- tangential placement -> projected hour-axis residual / canonical tangential offset,
- orientation -> line-incidence residual pattern rather than raw angle,
- shape -> secondary evidence only until stability improves.

This hybrid approach is preferable to ideological consistency about one transform.

## Practical viewpoint policy

Primary product research should optimise for ordinary dealer QC views.

Suggested research mode:

- <=10 deg apparent tilt: primary quantitative operating region,
- 10-15 deg: advisory / lower confidence unless a specific feature is demonstrated stable,
- >15 deg: do not force radial conclusions; preserve angular/incidence checks where supported.

The existence of extreme-angle images in the corpus is useful for stress testing but should not dictate the entire product architecture if the intended QC workflow usually supplies near-frontal images.

## Important next statistical cut

Before feature selection is frozen, recompute the expanded calibration statistics with **physical-watch weighting and a <=10 deg primary subset**.

For each candidate feature report both:

1. all-supported-view statistics,
2. <=10 deg primary-operating-region statistics.

A feature that is excellent <=10 deg but poor at 30-40 deg may still be a strong production candidate if the product can request or select a straighter QC photo.

Do not let extreme-view observations dominate a feature intended for normal dealer QC.

## Triangle defect decomposition to preserve

The first useful 12-marker rule should not be a single "high/low" number.

Retain the residual decomposition:

### Radial translation

Use apex, centre and base residuals.

- similar signed residuals -> translation-like shift,
- strong differential between apex and base -> size/span/shape difference or segmentation issue.

### Tangential translation vs rotation

Use apex/centre/base-centre signed tangential residuals against the predicted 12 axis.

- same sign -> sideways translation-like,
- opposite endpoint signs around a near-correct centre -> rotation-like,
- mixed pattern -> compound geometry or measurement uncertainty.

This gives the human-facing overlay a logical explanation rather than merely a numeric anomaly.

## Human-facing overlay implication

The overlay should visualise the selected residual components, not the transform used internally.

For 12:

- expected apex gate,
- observed apex,
- expected centre target,
- observed centre,
- expected base gate,
- observed base,
- expected 12 axis,
- observed symmetry/axis evidence,
- subtle radial displacement cue,
- subtle tangential displacement/rotation cue.

The user should not need to know whether the apex used simple normalisation and the base used projective normalisation. The display should communicate only expected vs observed geometry and confidence.

## Marketplace-source caveat

The expanded sample is numerically much stronger than n=2 but source diversity is still limited.

Do not call its central ranges "genuine tolerances" yet.

Before production thresholding, add at least one materially different provenance channel if possible, for example:

- additional well-documented owner/full-set marketplace sources,
- first-party/AD imagery representing distinct physical watches,
- independently sourced dealer sets.

This is more important than simply adding dozens more photos from the same marketplace.

## Recommended next experiment

Without changing the current feature definitions:

1. refresh all live proportional-geometry research notes,
2. run feature selection on the expanded calibration population,
3. make <=10 deg the primary operating subset,
4. use all-view results as robustness/stress evidence,
5. select per-feature normalisation rather than one global transform,
6. retain 3-6 compact features only,
7. freeze the resulting calibration profile,
8. then re-apply it once to the user's QC image without retuning,
9. render the human explanation overlay,
10. only after that consider held-out validation.

Do not create hard defect thresholds yet.
