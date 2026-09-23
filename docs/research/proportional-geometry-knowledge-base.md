# Proportional Geometry Knowledge Base

Status: shared research input for Watch Align geometry experiments

This file is intentionally maintained on the dedicated branch:

`research/proportional-geometry-knowledge-base`

It is a read-only research source for implementation branches. Do not merge this branch into experiment branches merely to consume the content. Fetch and read the file directly from the remote branch so it can continue to evolve independently while experiments run.

## Purpose

The working hypothesis is that watch QC geometry is better represented by stable, dimensionless or projectively normalised relationships than by raw pixel distances or by a visual overlay alone.

The intended architecture is:

1. generic image geometry establishes dial centre, minute-track structure, hour axes and peer-marker evidence,
2. proportional/projective measurements express observed geometry in a scale-independent coordinate system,
3. genuine calibration images define watch-family/model-specific expected distributions,
4. simple rules identify unusual geometric relationships,
5. the visual overlay explains those relationships to a human reviewer,
6. the human remains the final reviewer.

The overlay is therefore an explanation layer, not the source of truth.

## Non-negotiable research rules

- Never derive or tune genuine geometry bands from the user's replica.
- Never use validation watches while selecting features, normalisations, thresholds or profile structure.
- `physical_watch_id` is the independence unit. Repeated photos are repeatability observations, not independent watches.
- `gen_candidate` means source-labelled genuine candidate, not authenticated ground truth.
- Prefer robust distributions (median, MAD, central quantiles) to single reference values.
- Do not force a measurement when geometry or segmentation is unreliable.
- Preserve signed direction where physically meaningful: inward/outward, clockwise/counter-clockwise, left/right.
- Do not create authenticity, factory, GL/RL or combined quality scores from this research.

## Camera-angle principle

Raw radial image distances are not directly comparable across arbitrary photographs.

Existing Watch Align research found radial repeatability degrades strongly with apparent tilt, while angular measurements are materially more stable.

Working research guardrails:

- apparent tilt <= 10 deg: best-supported radial operating area,
- >10 to 15 deg: radial values are advisory/research-only unless a projective normalisation demonstrates stability,
- >15 deg: suppress ordinary radial pass/fail-style interpretation; prefer a straighter image,
- angular measurements may remain usable farther into tilt, but must still pass marker-specific sanity checks.

Any candidate proportional rule must therefore record its tilt dependence explicitly.

## Coordinate systems

### Generic angular coordinate

For an ordinary 12-hour dial, nominal hour directions are separated by 30 degrees.

Candidate angular quantities include:

- marker-centre tangential/angular offset from nominal hour axis,
- marker symmetry-axis angular offset from nominal hour axis,
- baton long-axis offset,
- triangle symmetry-axis offset,
- opposite-marker collinearity,
- bezel/dial angular relationships where appropriate.

Angular quantities should be expressed in degrees or dimensionless tangential displacement after normalisation, never only in pixels.

### Generic radial coordinate

For low-tilt images, a simple dial-radius-normalised radial coordinate can be useful:

`rho = observed radial distance / local dial or minute-track radius`

However this must not be assumed perspective-safe.

### Local 1D projective radial coordinate

A homography restricted to one physical radial line is a one-dimensional projective transformation. This is a preferred research mechanism for angle-resistant radial measurements.

For the current GMT work, peer-derived round-marker envelope intersections along the 12-o'clock axis provide three independent observed correspondences for known canonical radii:

- round-marker inner radius: 0.681
- round-marker centre radius: 0.751
- round-marker outer radius: 0.821

With dial centre constrained as `r=0 -> t=0`, fit a 1D projective mapping such as:

`t(r) = a*r / (c*r + 1)`

or an equivalent mathematically valid parameterisation.

The marker under test must not contribute to the mapping used to judge itself.

Mapping residual and conditioning must be retained as measurement-confidence evidence.

## Shared-rule hierarchy

The intended rule hierarchy is:

1. generic clock geometry,
2. shared watch-family geometry where genuine calibration proves equality,
3. model-specific overrides,
4. exact-reference exceptions only when evidence shows they are necessary.

Do not assume that visually similar models share a proportion. Promote a relation to a shared family rule only after genuine calibration data supports equivalence within measurement/repeatability noise.

Potential future example:

- modern Rolex GMT-Master II and Submariner may share some marker radii, sizes or triangle relationships,
- but this must be measured rather than assumed.

## Candidate proportional features

The list below is intentionally broader than the final production profile. Experiments should reject unstable or redundant features.

### Global dial structure

- minute-track radius / chosen dial-scale reference,
- peer round-marker centre radius,
- peer round-marker inner and outer envelope radii,
- hour-axis angular consistency,
- opposite-marker symmetry,
- global dial roll relative to minute track.

### Round markers

For each round marker where measurable:

- centre canonical/projective radius,
- inner-edge canonical/projective radius,
- outer-edge canonical/projective radius,
- diameter / local dial scale,
- tangential offset from nominal hour axis,
- leave-one-out deviation from peer-marker centre envelope,
- leave-one-out inner/outer edge deviation,
- opposite-marker symmetry where an opposite peer exists.

When a round marker is under test, fit the expected peer geometry without that marker if practical.

### 12 triangle

Current canonical GMT master constants provide the following working geometry values:

- triangle apex radius: 0.565
- triangle centre radius: 0.719
- triangle outward/base-centre radius: 0.815

Candidate features:

- apex canonical/projective radius,
- centre canonical/projective radius,
- base canonical/projective radius,
- base-to-minute-track gap in canonical/projective coordinates,
- centre-to-minute-track gap,
- apex-to-base span,
- triangle height / local dial scale,
- base width / triangle height,
- centroid position within apex-to-base span,
- symmetry-axis angular error from 12 axis,
- base-line angular error from expected tangent,
- left/right half-width symmetry,
- tangential centre offset from 12 axis.

Interpretation patterns, for later testing only:

- apex, centre and base all displaced outward together -> candidate outward/radial translation,
- all displaced inward together -> candidate inward/radial translation,
- centre displaced tangentially -> candidate sideways placement,
- centre approximately positioned but symmetry axis rotated -> candidate orientation error,
- centre positioned while apex/base span differs -> candidate size/shape discrepancy.

These patterns are not defect thresholds.

### 6 and 9 batons

Candidate features:

- centre canonical/projective radius,
- inner and outer extents,
- baton length / local dial scale,
- baton width / local dial scale,
- long-axis angular error from nominal hour axis,
- centre tangential offset,
- inward/outward clearances relative to peer-derived radial references.

### Minute-track relationships

Potentially useful but perspective-sensitive unless projectively normalised:

- marker outer edge to local minute track,
- triangle base to local minute track,
- baton outer edge to local minute track,
- centre-to-minute-track proportional gap.

These should be compared in canonical/projective coordinates where possible.

## Visual overlay principles

The structural overlay and the measurement engine have different jobs.

### Frozen structural layer

The current generic geometry reticle provides:

- dial centre target,
- minute-track channel,
- 12 nominal hour axes with marker-region gaps,
- peer-derived round-marker inner/centre/outer conics.

This layer should remain visually restrained and should not imply pass/fail.

### Proportion explanation layer

For a marker under inspection, prefer local expected-vs-observed cues rather than adding more full-dial circles.

For the GMT 12 triangle, candidate human-facing elements are:

- expected apex gate,
- expected centre target,
- expected base gate,
- expected 12 symmetry axis,
- observed apex/centre/base points,
- observed triangle axis,
- subtle expected genuine band around supported expected positions,
- local bracket indicating base-to-minute-track relationship.

The actual marker should remain visible. Do not draw a fake Rolex marker over it.

The user overlay should avoid numeric clutter. Diagnostic mode may show values and residuals in a side panel.

## Feature-selection criteria

A candidate rule should be retained only if it performs well on calibration genuines according to most of the following:

- low same-watch repeatability error,
- tight between-watch genuine distribution,
- low correlation with apparent tilt within its supported range,
- stable behaviour across multiple independent physical watches,
- clear physical meaning,
- reliable segmentation/detection coverage,
- no dependence on the marker under test for its own expected reference,
- incremental information beyond already-retained features.

Prefer a compact profile of a few strong measurements over a large set of weak or correlated ones.

## Suggested statistics

For each candidate feature report at minimum:

- independent physical-watch count,
- image count,
- median,
- MAD or equivalent robust spread,
- p10/p90,
- min/max for context,
- within-watch repeatability,
- apparent-tilt correlation,
- <=10 deg subgroup,
- >10 deg subgroup where sample size permits,
- measurement/segmentation failure rate.

Where appropriate, compare simple radius normalisation with projective radial normalisation and prefer the version with lower repeatability error and weaker tilt dependence.

## Genuine baseline philosophy

The genuine baseline should be a distribution, not one photograph.

The official Rolex catalogue image is useful as a first-party sanity reference but is not an independent population sample.

Calibration `gen_candidate` physical watches provide population/repeatability evidence, with provenance limitations clearly retained.

Validation genuine candidates must remain held out until the feature set and profile are frozen.

## Current narrow proof target

The first proof target is the 126710BLNR 12-o'clock triangle.

Success would mean:

1. calibration genuine images produce one or more stable proportional/projective 12-marker features,
2. those features remain acceptably stable across ordinary photo-scale changes and supported camera angles,
3. the frozen genuine profile can be applied to an unseen watch image without retuning,
4. the resulting expected-vs-observed geometry can be shown clearly to a human using the overlay.

Automatic warning thresholds are deliberately deferred until this proof is established.

## Open research questions

- Does 1D projective radial normalisation materially outperform simple dial-radius normalisation on the calibration genuine set?
- Which 12-marker feature has the lowest same-watch repeatability error?
- Is base-to-minute-track gap more stable than absolute base canonical radius?
- Are apex, centre and base residuals strongly correlated, indicating translation rather than shape change?
- How stable is triangle width/height under moderate perspective after local rectification?
- Can the round-marker peer system provide a sufficiently stable local coordinate frame at 12 without full pose recovery?
- Which metrics remain usable at 10-15 deg apparent tilt?
- Which proportions are genuinely shared between GMT and Submariner families?

## Update protocol for parallel work

Implementation agents should treat this file as an evolving research input.

Before each major decision point, especially:

- candidate-feature finalisation,
- feature ranking,
- genuine-profile freeze,
- overlay interpretation design,

run a fresh fetch of the remote research branch and re-read:

`origin/research/proportional-geometry-knowledge-base:docs/research/proportional-geometry-knowledge-base.md`

Do not silently merge the research branch into the implementation branch.

If new guidance conflicts with an already-frozen experimental decision, record the conflict rather than retroactively changing completed measurements. This preserves experimental integrity.
