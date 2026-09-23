# Proportional Geometry Knowledge Base

Status: shared research input for Watch Align geometry experiments

Branch: `research/proportional-geometry-knowledge-base`

Implementation branches should fetch and read this file directly from the remote research branch. Do not merge or cherry-pick this branch merely to consume the research. The file may evolve while experiments run.

## Working architecture

The working hypothesis is that watch QC geometry is better represented by stable, dimensionless or projectively normalised relationships than by raw pixel measurements or by a visual overlay alone.

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
- Prefer robust distributions such as median, MAD and central quantiles to a single reference image.
- Do not force a measurement when geometry or segmentation is unreliable.
- Preserve signed direction where physically meaningful: inward/outward, clockwise/counter-clockwise, left/right.
- Do not create authenticity, factory, GL/RL or combined quality scores from this research.

## Camera-angle principle

Raw radial image distances are not directly comparable across arbitrary photographs.

Existing Watch Align research found radial repeatability degrades strongly with apparent tilt, while angular measurements are materially more stable.

Working guardrails:

- apparent tilt <= 10 deg: best-supported ordinary radial operating area,
- >10 to 15 deg: radial values are advisory/research-only unless a projective normalisation demonstrates stability,
- >15 deg: suppress ordinary radial pass/fail-style interpretation and prefer a straighter image,
- angular measurements may remain useful farther into tilt, but must still pass marker-specific sanity checks.

Any candidate proportional rule must record its tilt dependence explicitly.

A recurring Reddit QC failure mode is also important: users often apply alignment lines to a crooked image and then mistake the resulting mismatch for a watch defect. This means viewpoint/pose quality is not a secondary concern, it is part of the defect-detection model.

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

# Defect-driven research priorities from RepTimeQC sampling

Updated 2026-09-23.

A targeted search of r/RepTimeQC GMT-Master II QC discussions shows that the same geometric concern types recur repeatedly. This should influence the first proportional features we test. The source posts are community QC evidence, not laboratory ground truth, so use them to prioritise feature design, not to set thresholds.

## Priority 1 - hour-marker placement and orientation

Recurring community concerns include:

- 12 triangle shifted left/right,
- 12 triangle slightly clockwise/counter-clockwise,
- 12 triangle appearing radially high/outward,
- 6 baton canted left/right,
- 6 baton shifted laterally,
- 9 baton shifted upward or appearing slightly crooked,
- combined 12/6/9 concerns on the same watch.

Representative examples:

- 2026-09-06, GMT-Master II 126710 BLNR: user concern that the 12 marker was shifted left and not centred. https://www.reddit.com/r/RepTimeQC/comments/1w947p6/
- 2024-11-08, GMT-Master II 126710 GRNR: 12 triangle discussed as tilted, with a competing comment that the picture itself was not straight. https://www.reddit.com/r/RepTimeQC/comments/1gme7m2/
- 2026-06-13, GMT-Master II 126710 GRNR: 9 marker described as slightly upward-shifted and 6 as having a minor left bias. https://www.reddit.com/r/RepTimeQC/comments/1u4prie/
- 2025-03-16, GMT-Master II 126710 BLRO: 6 marker described as canted left. https://www.reddit.com/r/RepTimeQC/comments/1jcnvu9/
- 2026-06-26, GMT-Master II 126710 BLRO: 12 described as slightly clockwise and 6 shifted left. https://www.reddit.com/r/RepTimeQC/comments/1uftamx/
- 2026-01-05, GMT-Master II 126710 BLRO: user initially thought 6 and 12 were shifted left, while a commenter attributed the apparent error to image tilt. https://www.reddit.com/r/RepTimeQC/comments/1q4ongb/
- 2026-05-20, GMT-Master II BLNR: concern over 6 tilt and possible 9 upward/crooked appearance. https://www.reddit.com/r/RepTimeQC/comments/1tikyg0/

Research consequence: marker placement/orientation should be the first rule family, ahead of text, rehaut or finishing.

### High-priority proportional features for marker defects

For any marker under test, define the expected centre independently of that marker wherever possible.

For round markers:

- nominal hour-axis intersection with leave-one-out peer centre conic,
- signed radial centre residual in a projective/local coordinate,
- signed tangential centre residual relative to the nominal hour axis,
- local marker diameter relative to peer-marker diameter distribution,
- inner-edge and outer-edge residuals relative to leave-one-out peer envelopes.

For 12 triangle:

- apex canonical/projective radius,
- centre canonical/projective radius,
- base canonical/projective radius,
- base-to-minute-track projective gap,
- symmetry-axis angular error relative to the independently predicted 12 axis,
- base-line angular error relative to the expected local tangent,
- signed tangential centre residual,
- base width / height ratio,
- left/right half-width symmetry,
- correlated apex/centre/base residual pattern to distinguish translation from shape error.

For 6 and 9 batons:

- centre canonical/projective radius,
- signed tangential centre residual,
- long-axis angular error relative to the locally predicted hour-axis direction,
- baton length / local dial scale,
- baton width / local dial scale,
- inner and outer radial clearances,
- end-point symmetry around the expected centre.

### Important viewpoint rule for orientation

Do not compare a marker's raw image angle with a universal vertical/horizontal angle. Under perspective, even a physically radial baton can project at a different image angle.

Instead compare the observed marker axis with the locally predicted image-space direction of the corresponding physical hour radial line. In other words, use expected-vs-observed orientation in the same projected coordinate system.

This is particularly important for 6 and 9 concerns, which Reddit users frequently confuse with camera tilt.

## Priority 2 - cyclops/date-window geometry

Crooked or shifted cyclops concerns recur often in GMT QC threads.

Representative examples:

- 2024-03-26, GMT-Master II 126711: multiple commenters agreed the cyclops appeared crooked and the watch was replaced. https://www.reddit.com/r/RepTimeQC/comments/1bo50g5/
- 2022-10-23, GMT-Master II: multiple commenters independently described the cyclops as crooked while other alignment looked good. https://www.reddit.com/r/RepTimeQC/comments/ybdecz/
- 2024-05-24, GMT-Master II 126710 BLRO: user concern focused on a crooked cyclops. https://www.reddit.com/r/RepTimeQC/comments/1czisly/
- 2026 Sprite QC: user ultimately reported rejecting one watch because the cyclops remained visibly crooked across additional photos/video. https://www.reddit.com/r/RepTimeQC/comments/1qiang1/

Research consequence: cyclops/date geometry is a promising second subsystem because it is a simple relational problem, but it must NOT be mixed blindly with dial-plane homography.

The cyclops is on the crystal, while the date aperture and date wheel lie below it. They are not all on the same physical plane. Therefore a dial homography cannot be assumed to rectify the cyclops correctly.

Candidate local 2D features:

- cyclops long-edge angle relative to date-window long-edge angle,
- cyclops short-edge angle relative to date-window short-edge angle,
- cyclops centre offset from date-window centre, normalised by aperture width/height,
- date glyph bounding-box centre within the date aperture,
- top/bottom and left/right date-glyph margins normalised by aperture dimensions,
- consistency of these relations across multiple dealer views if available.

Initial output should say only that a local cyclops/date relationship is unusual, not that the crystal is definitively installed incorrectly from one oblique image.

## Priority 3 - bezel internal geometry and bezel-to-dial alignment

QC posts frequently mention bezel triangle alignment, colour-transition alignment and engraving alignment. However the bezel rotates and has mechanical play, so bezel-to-dial mismatch in a single photograph is not automatically a manufacturing defect.

Representative examples:

- 2024-01-07, 126710 BLRO: user questioned bezel triangle alignment and the 18 colour transition relative to dial markers. https://www.reddit.com/r/RepTimeQC/comments/190vz9d/
- 2024-10-30, 126710 BLNR: user worried about the colour transition, while commenters noted bezel play and photo angle. https://www.reddit.com/r/RepTimeQC/comments/1gfxgxp/
- 2025-06-07, 126710 BLRO: commenter explicitly noted that apparent bezel triangle mismatch could be corrected using normal bezel play/rotation. https://www.reddit.com/r/RepTimeQC/comments/1l5ryv4/

Research consequence: split bezel measurements into two classes.

### A. Internal bezel geometry - potentially strong

These do not depend on where the bezel happens to be clicked relative to the dial:

- colour-transition angular position relative to the bezel's own engraved reference marks,
- bezel triangle/pip centre relative to the bezel's own 24-hour axis,
- engraving-centre positions relative to the insert circumference,
- opposite transition/engraving symmetry.

These are preferable if the goal is manufacturing QC.

### B. Bezel-to-dial alignment - display-only/advisory

- bezel triangle vs dial 12 axis,
- bezel 6/18 positions vs dial 3/9 axes.

These may be useful to show the human, but should not be called a defect unless the bezel has been deliberately centred and its rotational/play state is controlled.

## Priority 4 - date glyph centring

Several GMT QC posts mention dates sitting high, low or off-centre even when the cyclops itself may be acceptable.

Candidate proportion rules:

- glyph centre x / aperture width,
- glyph centre y / aperture height,
- left/right margin ratio,
- top/bottom margin ratio,
- multi-date consistency across all provided date examples.

This is likely more robust than attempting font-quality classification. It also naturally supports model-specific expected ranges.

## Lower initial priority - rehaut alignment

Rehaut crown/engraving alignment is often discussed, but the rehaut is a curved three-dimensional surface and is particularly sensitive to camera viewpoint, focus and reflection.

Do not make rehaut alignment an early proportional-rule target. It requires its own projection model and probably multiple views. It should not consume effort before dial-marker, cyclops/date and internal bezel geometry have been proven.

## Out of scope for the current proportional geometry engine

The following common QC topics are important to users but are not good first targets for this proportion engine:

- dial printing quality/font shape,
- finishing/polishing,
- SEL gaps,
- hand surface defects,
- timegrapher values,
- colour accuracy,
- movement identity.

They may need separate vision or measurement modules later.

# Candidate proportional features

The list below is intentionally broader than the final production profile. Experiments should reject unstable or redundant features.

## Global dial structure

- minute-track radius / chosen dial-scale reference,
- peer round-marker centre radius,
- peer round-marker inner and outer envelope radii,
- hour-axis angular consistency,
- opposite-marker symmetry,
- global dial roll relative to minute track.

## Round markers

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

## 12 triangle

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

Interpretation patterns for later testing only:

- apex, centre and base all displaced outward together -> candidate outward/radial translation,
- all displaced inward together -> candidate inward/radial translation,
- centre displaced tangentially -> candidate sideways placement,
- centre approximately positioned but symmetry axis rotated -> candidate orientation error,
- centre positioned while apex/base span differs -> candidate size/shape discrepancy.

These patterns are not defect thresholds.

## 6 and 9 batons

Candidate features:

- centre canonical/projective radius,
- inner and outer extents,
- baton length / local dial scale,
- baton width / local dial scale,
- long-axis angular error from the locally projected nominal hour axis,
- centre tangential offset,
- inward/outward clearances relative to peer-derived radial references.

## Minute-track relationships

Potentially useful but perspective-sensitive unless projectively normalised:

- marker outer edge to local minute track,
- triangle base to local minute track,
- baton outer edge to local minute track,
- centre-to-minute-track proportional gap.

These should be compared in canonical/projective coordinates where possible.

# Visual overlay principles

The structural overlay and the measurement engine have different jobs.

## Frozen structural layer

The current generic geometry reticle provides:

- dial centre target,
- minute-track channel,
- 12 nominal hour axes with marker-region gaps,
- peer-derived round-marker inner/centre/outer conics.

This layer should remain visually restrained and should not imply pass/fail.

## Proportion explanation layer

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

For 6/9 baton review, use a short expected centre target and expected local axis rather than a large full-dial warning graphic.

For cyclops/date review, use a separate local rectangle/edge overlay rather than the dial-plane conic system.

# Feature-selection criteria

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

For defect-driven prioritisation, prefer features that map directly to recurring community-observed problems:

1. marker radial/tangential placement,
2. marker orientation,
3. cyclops/date relative geometry,
4. date glyph centring,
5. internal bezel geometry.

# Suggested statistics

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

Also retain covariance between related residuals. For example, strongly correlated apex/centre/base residuals on the 12 triangle may indicate translation, while divergent residuals may indicate shape/orientation error.

# Genuine baseline philosophy

The genuine baseline should be a distribution, not one photograph.

The official Rolex catalogue image is useful as a first-party sanity reference but is not an independent population sample.

Calibration `gen_candidate` physical watches provide population/repeatability evidence, with provenance limitations clearly retained.

Validation genuine candidates must remain held out until the feature set and profile are frozen.

# Current narrow proof target

The first proof target is the 126710BLNR 12-o'clock triangle.

Success would mean:

1. calibration genuine images produce one or more stable proportional/projective 12-marker features,
2. those features remain acceptably stable across ordinary photo-scale changes and supported camera angles,
3. the frozen genuine profile can be applied to an unseen watch image without retuning,
4. the resulting expected-vs-observed geometry can be shown clearly to a human using the overlay.

Automatic warning thresholds are deliberately deferred until this proof is established.

# Open research questions

- Does 1D projective radial normalisation materially outperform simple dial-radius normalisation on the calibration genuine set?
- Which 12-marker feature has the lowest same-watch repeatability error?
- Is base-to-minute-track gap more stable than absolute base canonical radius?
- Are apex, centre and base residuals strongly correlated, indicating translation rather than shape change?
- How stable is triangle width/height under moderate perspective after local rectification?
- Can the round-marker peer system provide a sufficiently stable local coordinate frame at 12 without full pose recovery?
- Which metrics remain usable at 10-15 deg apparent tilt?
- Can 6 and 9 baton orientation be expressed relative to the locally projected nominal radial line robustly enough to remove the common 'crooked photo' false positive?
- Can cyclops rotation be separated from camera perspective using local date-window edge geometry or multiple views?
- Which bezel-internal proportions remain invariant under bezel rotation relative to the dial?
- Which proportions are genuinely shared between GMT and Submariner families?

# Update protocol for parallel work

Implementation agents should treat this file as an evolving research input.

Before each major decision point, especially:

- candidate-feature finalisation,
- feature ranking,
- genuine-profile freeze,
- overlay interpretation design,
- final conclusions,

run a fresh fetch of the remote research branch and re-read:

`origin/research/proportional-geometry-knowledge-base:docs/research/proportional-geometry-knowledge-base.md`

Do not silently merge the research branch into the implementation branch.

If new guidance conflicts with an already-frozen experimental decision, record the conflict rather than retroactively changing completed measurements. This preserves experimental integrity.
