# Proportional Geometry - Invariants and Defect Decomposition

Status: live supplemental research for `research/proportional-geometry-knowledge-base`

Date: 2026-09-23

This note develops generic mathematical rules that can convert expected-vs-observed marker geometry into simple, interpretable defect categories while minimising sensitivity to image scale and viewpoint.

## 1. Cross-ratio as a projective radial invariant

The current 1D radial mapping idea can be expressed more directly using the projective cross-ratio.

Perspective projection restricted to one physical radial line is a 1D projective transformation. For four collinear physical points A, B, C, D and their image points a, b, c, d, the cross-ratio is invariant:

`CR(A,B;C,D) = CR(a,b;c,d)`

For scalar coordinates x on a line:

`CR(a,b;c,d) = ((c-a)/(c-b)) / ((d-a)/(d-b))`

(up to equivalent convention/order; implementation must use one convention consistently).

### GMT 12 use

Along the independently established 12 radial line we already have four strong reference locations:

- dial centre, canonical r = 0,
- round-marker inner envelope intersection, r = 0.681,
- round-marker centre envelope intersection, r = 0.751,
- round-marker outer envelope intersection, r = 0.821.

These four references provide a projective consistency check before the 12 triangle is considered.

For each observed triangle point (apex, centre, base), its projective radial coordinate can be obtained either by:

1. fitting the current 1D projective map and inverting it, or
2. solving from an observed cross-ratio against known canonical reference points.

These are mathematically related approaches.

### Why cross-ratio is useful

- it is inherently invariant to 1D projective distortion,
- it avoids treating Euclidean radial spacing as perspective-preserved,
- it provides a diagnostic check independent of a particular parameterisation,
- multiple reference triplets can produce redundant estimates; disagreement between them is evidence that the peer geometry or line intersection is unstable.

### Recommended experiment

For each calibration-genuine image, calculate each selected 12 radial feature by both:

- fitted 1D projective inverse,
- cross-ratio-derived coordinate.

They should agree numerically within fitting noise. If they do not, investigate the geometry rather than averaging blindly.

Record cross-ratio conditioning. Closely spaced or poorly localised reference intersections can amplify image noise.

## 2. Prefer incidence/collinearity over raw angles where possible

A projective transform maps lines to lines but does not preserve Euclidean angles.

Therefore the strongest statement for a baton or triangle axis is not:

`the marker is 0.8 degrees from vertical`

but:

`the observed marker symmetry axis does or does not coincide with the independently predicted image-space hour line`.

Coincidence/collinearity is projectively meaningful.

### Generic line-consistency residual

Given predicted hour line L and two observed marker-axis points p_inner and p_outer:

- compute signed point-to-line residuals in image space,
- normalise residuals by a local image scale such as marker width or local peer-marker diameter,
- preserve the signs.

Exact zero remains zero under viewpoint changes when both expected and observed geometry are projected into the same image.

The magnitude is not a strict projective invariant, so it still requires calibration, but it avoids comparing against an arbitrary global vertical/horizontal angle.

## 3. Signed endpoint residual patterns can classify translation vs rotation

This is a potentially high-value generic logical rule for elongated markers such as 6/9 batons and for the 12 triangle symmetry axis.

Let the predicted marker axis be the local radial line.

For observed inner and outer axis points, compute signed tangential residuals:

`t_inner`
`t_outer`

relative to the predicted line.

Interpretation pattern:

- `t_inner` and `t_outer` similar magnitude and same sign -> candidate lateral/tangential translation,
- opposite signs -> candidate marker rotation about an approximately correct centre,
- one near zero and one displaced -> candidate pivot/rotation combined with translation,
- both near zero -> axis placement/orientation consistent.

This is more informative than a single orientation angle and naturally explains the defect to a human.

For the 12 triangle use apex and base-centre as the two axis points.

For a baton use inner and outer endpoint centres.

## 4. Radial common-mode vs differential residuals

For expected radial positions r_apex, r_centre, r_base and observed projective/canonical positions R_apex, R_centre, R_base, define signed residuals:

`e_apex = R_apex - r_apex`
`e_centre = R_centre - r_centre`
`e_base = R_base - r_base`

Then decompose them.

### Common-mode radial shift

A robust mean/median of the three residuals estimates marker radial translation.

If apex, centre and base all move outward together, that is much stronger evidence of placement shift than one edge alone.

### Differential radial residual

Differences such as:

`e_base - e_apex`

or the residual of observed apex-to-base span relative to expected span measure size/shape change rather than simple translation.

Interpretation:

- common-mode large, differential small -> candidate whole-marker radial translation,
- common-mode small, differential large -> candidate marker height/shape difference,
- both large -> compound difference or segmentation/reference problem.

This logic is generic across marker shapes whenever stable inner/centre/outer landmarks can be defined.

## 5. Tangential common-mode vs slope

For multiple observed landmarks along a marker's radial extent, model signed tangential residual as a function of radial coordinate:

`t(r) = alpha + beta * (r - r_centre)`

Interpretation:

- alpha approximates lateral/tangential translation,
- beta approximates rotation-like divergence relative to the expected radial axis.

For only two points this reduces to the signed endpoint pattern above.

For a triangle, apex, centroid and base-centre give three samples.

This may be more stable and interpretable than a single PCA orientation angle.

## 6. A generic marker residual vector

A useful architecture is to express every marker, regardless of shape, through a small residual vector:

- radial translation,
- tangential translation,
- orientation/axis divergence,
- radial size/span difference,
- tangential width/shape difference,
- asymmetry.

The image-processing code can be generic while watch/model profiles supply expected landmarks and normal ranges.

### Round marker

Orientation is irrelevant. Useful components are:

- radial centre residual,
- tangential centre residual,
- diameter/size residual,
- optional circularity/asymmetry.

### Baton

Useful components are:

- radial centre residual,
- tangential centre residual,
- endpoint signed tangential pattern,
- length residual,
- width residual.

### Triangle

Useful components are:

- apex/centre/base radial common-mode,
- apex/centre/base radial differential,
- apex/base-centre tangential pattern,
- base width / height,
- left/right half-width asymmetry,
- base-line consistency.

This provides a generic interpretation layer while keeping watch-specific geometry in profile data.

## 7. Pairwise/opposite-marker relationships

Absolute dial pose may still be imperfect. Pairwise relationships can provide additional checks.

Potential rules:

- 12 and 6 centres should lie on one physical dial diameter after projection,
- 3 and 9 references should lie on one physical diameter where both exist,
- opposite round-marker pairs should be consistent with a common dial centre/conic,
- left/right symmetric hour pairs (1/11, 2/10, 4/8, 5/7) can be compared using peer-relative canonical coordinates.

Caution: do not let a suspect marker define the centre/axis used to judge itself. Prefer centre/pose from independent dial/minute-track evidence.

## 8. Use leave-one-out expectations for round markers

For a round marker under test, exclude it from the peer conic fit when practical.

Then derive:

- expected centre from nominal hour line intersecting leave-one-out centre conic,
- expected inner/outer positions from corresponding leave-one-out conics,
- signed radial/tangential residuals.

This prevents a displaced marker from pulling its own expected envelope toward itself.

A robust product can fall back to all-peer fit only when leave-one-out support is insufficient, and should lower confidence when doing so.

## 9. Measurement uncertainty should be propagated into the rule

A deviation is only meaningful relative to measurement uncertainty.

For each observed feature retain at least:

- pose confidence,
- peer conic residual,
- projective/cross-ratio conditioning,
- marker segmentation confidence,
- apparent tilt,
- local image resolution/marker pixel size.

A useful research score is not simply `abs(deviation)`, but something analogous to:

`deviation / expected measurement noise at this viewpoint`

Do not turn this into a production z-score until enough calibration evidence exists.

## 10. Multi-photo evidence can separate watch geometry from viewpoint artefact

Dealer QC sets often provide several views of the same physical watch.

For any suspected geometric residual:

- measure each usable photo independently,
- map findings to the same marker/feature,
- inspect sign consistency across views,
- treat repeated photos as repeatability observations, not independent watches.

A real marker displacement should tend to preserve its canonical signed direction across usable views after normalisation. A viewpoint artefact may vary or disappear.

This is especially important for borderline 6/9 tilt and cyclops concerns frequently seen in community QC.

## 11. Implication for the human-facing overlay

The overlay should explain residual components, not merely draw a template.

For a marker under review:

- expected centre target,
- observed centre point,
- expected local axis,
- observed marker axis,
- expected inner/outer or apex/base gates,
- optional subtle displacement vector between expected and observed centre.

For translation vs rotation:

- same-side endpoint residuals can be visualised as a small parallel offset,
- opposite-side endpoint residuals can be visualised as axis divergence.

No warning colour is required at the research stage. Geometry itself should be visible.

## 12. Recommended order for Claude's first GMT 12 experiment

Before freezing the 12 feature set, evaluate these candidates in roughly this priority order:

1. projective/cross-ratio base canonical radius,
2. projective/cross-ratio centre canonical radius,
3. projective/cross-ratio apex canonical radius,
4. radial common-mode translation from apex/centre/base residuals,
5. radial differential/span residual,
6. tangential common-mode displacement,
7. tangential residual slope / apex-vs-base-centre sign pattern,
8. base width / height,
9. left/right base asymmetry.

The first six are most directly connected to recurring Reddit reports of high/low, left/right and tilted markers.

Do not assume all nine belong in the final profile. The calibration data should remove unstable/redundant features.
