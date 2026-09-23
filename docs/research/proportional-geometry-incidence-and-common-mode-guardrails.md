# Proportional Geometry - Incidence-First Rules and Common-Mode Pose Guardrails

Status: live supplemental research

Date: 2026-09-23

Purpose: strengthen the proportional-geometry approach against the failure mode repeatedly seen in GMT QC discussions where a canted or imperfectly normalised source photo makes several markers appear wrong at once.

This note focuses on projectively meaningful line/incidence relationships and on separating global pose error from a true local marker anomaly.

## Community pattern motivating this note

Recent r/RepTimeQC GMT threads repeatedly show the same ambiguity:

- users suspect the 12 triangle is left/right or rotated but explicitly wonder whether the photo angle is responsible,
- some examples show 12, 6 and 9 all apparently shifted/tilted together,
- other examples show one marker, especially 6 or 12, remaining locally abnormal after the overall image is mentally straightened,
- reviewers often disagree until the source image is rotated or better aligned.

Representative recent examples include:

- `https://www.reddit.com/r/RepTimeQC/comments/1u91yef/` - 12 perceived slightly counter-clockwise, with photo angle explicitly raised as an alternative explanation,
- `https://www.reddit.com/r/RepTimeQC/comments/1ugosh6/` - possible rotation at 12/6/9 while the watch itself is visibly tilted,
- `https://www.reddit.com/r/RepTimeQC/comments/1w7u8xu/` - 12 and 6 both reported left of centre while 9 is slightly CCW,
- `https://www.reddit.com/r/RepTimeQC/comments/1wiweof/` - reviewers independently call out 12, 6 and 9 index issues on the same GMT,
- `https://www.reddit.com/r/RepTimeQC/comments/1vmpk0d/` - 12, 9 and 6 all receive small tilt comments.

These are not prevalence statistics. They are evidence that a useful system must distinguish a global registration/pose mode from a local marker residual.

## 1. Prefer projective incidence statements before Euclidean angles

A planar projective transform preserves:

- points,
- lines,
- point-on-line incidence,
- collinearity,
- concurrency,
- cross-ratio along one line.

It does not preserve ordinary Euclidean angles or distance ratios in arbitrary image space.

Therefore the first questions for a marker should be incidence questions.

### 1.1 Marker centre on nominal hour line

Let `L_h` be the independently predicted image-space hour line for marker `h`.

For a correctly centred marker, its observed centre should lie on or very near `L_h`.

Useful residual:

`tau_center = signed_distance(marker_center, L_h) / local_scale`

where `local_scale` can be peer round-marker diameter, local marker width, or another calibrated projected scale.

The zero condition itself is projectively meaningful. The magnitude still requires calibration.

### 1.2 Marker symmetry axis through dial centre

For a radial baton or the 12 triangle, the physical symmetry axis should normally be a radial line. A true radial line passes through the physical dial centre, so after projection the observed symmetry-axis line should pass through the projected dial centre.

Define:

`axis_incidence = signed_distance(dial_center, observed_marker_axis) / local_scale`

This is a strong orientation/placement diagnostic because a correctly radial axis should have near-zero incidence residual regardless of photo rotation.

Do not compare the marker to global screen vertical/horizontal.

### 1.3 Opposite-marker diameter consistency

Where both markers are structurally suitable, opposite hour positions should share one projected dial diameter.

Examples:

- 12 centre, dial centre and 6 centre should be collinear,
- a 3/9 relation is conceptually similar but the GMT date aperture means the 3 position is not a normal hour-marker observation.

For 12/6, do not let both suspect markers define the line used to judge them. Prefer the independently established dial centre and nominal hour geometry as the primary reference, then use 12/6 collinearity as a secondary consistency check.

## 2. Signed side-of-line patterns are more robust than one angle number

For an elongated marker, take two or more observed landmarks along its long/radial extent.

Examples:

- 12 triangle: apex, centroid, base-centre,
- baton: inner endpoint, centre, outer endpoint.

For each point compute signed side-of-line/tangential residual relative to the independently predicted hour line.

The sign pattern is highly informative:

- all points on the same side with similar magnitude -> translation-like,
- inner/apex and outer/base on opposite sides -> rotation-like,
- centre near zero but endpoints opposite -> rotation about an approximately correct centre,
- one endpoint near zero and the other displaced -> pivot-like rotation or compound shift.

The exact residual magnitudes are viewpoint-dependent unless normalised/calibrated, but the same-side versus opposite-side pattern is much less fragile than comparing a marker with screen vertical.

## 3. Separate global pose mode from local marker residual

This is a critical false-positive guardrail.

If several independently detected markers share a similar signed residual, the simplest explanation may be imperfect roll/pose/reference registration rather than several physical marker defects in the same direction.

### 3.1 Tangential common mode

For each well-measured marker `j`, calculate a signed tangential centre residual `tau_j` against its predicted image-space hour line.

Estimate a robust global mode using markers that are not currently under test:

`tau_global = median(tau_j for reliable peer markers)`

Then define the marker-specific residual:

`tau_local_j = tau_j - tau_global`

Research questions:

- does subtracting the peer median materially improve same-watch repeatability,
- does it reduce correlation with photo roll/cant,
- does a known locally tilted marker remain anomalous after common-mode removal?

Do not automatically subtract the global mode in production until calibration proves it helps. Record both raw and common-mode-corrected values during research.

### 3.2 Radial common mode

A scale/pose/radial-model error can make many markers appear collectively inward or outward.

Using independent round markers, calculate radial centre residuals against their leave-one-out expectations.

Estimate:

`rho_global = median(radial_peer_residuals)`

Then compare a special marker's radial residual both before and after the peer common mode.

A true 12-specific outward shift should remain directionally abnormal relative to peer markers. A mapping error affecting the whole dial should appear as a broad shared radial mode.

### 3.3 Coherence flag

Record a diagnostic such as:

- fraction of reliable markers sharing the same residual sign,
- robust spread of peer residuals,
- number of markers outside their own peer bands.

If many markers move together, lower confidence in an isolated-defect interpretation and raise confidence in a pose/reference problem.

## 4. 12 triangle: incidence-first decomposition

For the 12 triangle retain the existing radial apex/centre/base model, but add incidence-first evidence.

### 4.1 Centre placement

- expected 12 line comes from independent structural geometry,
- triangle centroid tangential residual estimates sideways placement.

### 4.2 Axis incidence

- observed triangle symmetry axis from apex to base-centre,
- test whether that line passes through the independently estimated dial centre.

### 4.3 Rotation versus translation

Use the signed tangential residuals of apex, centroid and base-centre.

Interpretation:

- common same-sign residual -> lateral translation candidate,
- endpoint sign reversal around a near-zero centroid -> rotation candidate,
- non-zero centroid plus residual slope -> translation + rotation candidate.

### 4.4 Radial translation versus shape

Retain the existing canonical/projective apex/centre/base residual decomposition:

- common-mode radial residual -> whole-marker radial translation candidate,
- differential residual -> height/span/shape candidate.

The strongest 12 conclusion should ideally require agreement between more than one independent evidence family, for example:

- canonical radial common-mode says outward,
- base-to-minute-track relation says outward,
- peer round-marker radial mode is near zero.

This is stronger than one raw gap measurement.

## 5. 6 and 9 batons: generic elongated-marker rules

The same residual architecture should later be reused for 6 and 9.

For each baton estimate:

- inner endpoint centre,
- geometric centre,
- outer endpoint centre,
- long-axis line.

Then calculate:

- centre tangential residual,
- axis-through-dial-centre incidence residual,
- inner/outer signed tangential residuals,
- canonical/projective inner/centre/outer radial coordinates where supported,
- length/span residual.

Logical patterns:

- inner and outer same side -> sideways translation-like,
- inner and outer opposite sides -> rotation-like,
- all radial positions shifted together -> radial translation-like,
- centre stable but length differs -> size/span difference.

This directly targets the recurring 6-left / 9-CCW style concerns in GMT QC.

## 6. Local tangent relation for marker bases

For a correctly radial triangle, its base is expected to be approximately tangential to a constant-radius dial curve at 12.

Under projective mapping, tangency between a line and the correct conic is preserved.

Research opportunity:

- derive or approximate the expected triangle-base constant-radius conic from the calibrated radial family,
- compare the observed triangle base line with the tangent to that expected conic at the 12 position.

Caution:

- do not simply use the round-marker outer conic as exact triangle-base geometry because their canonical radii differ,
- the current GMT constants place triangle base near, but not identical to, the round-marker outer radius,
- implement only if the expected triangle-base conic can be derived without using the observed 12 triangle itself.

This could become a perspective-aware base-orientation test superior to screen-angle comparison.

## 7. Rule confidence should combine local and global evidence

For a marker-level observation retain:

- local segmentation confidence,
- pose/minute-track confidence,
- peer-conic fit residual,
- apparent tilt,
- image resolution at marker,
- common-mode peer residual,
- number of reliable peer markers,
- sign consistency across repeated photos of the same watch.

A local deviation should be considered stronger when:

- the marker residual is repeatable across views,
- peer markers remain close to their expected geometry,
- the deviation survives common-mode subtraction,
- the relevant projective/radial mapping is well-conditioned.

## 8. Suggested experiment order after the current 12 feasibility work

When the genuine calibration sample is large enough:

1. evaluate raw vs common-mode-corrected tangential centre residuals,
2. evaluate axis-through-centre incidence for 12,
3. compare this with PCA or direct angle-based orientation metrics,
4. keep whichever has better repeatability and lower tilt dependence,
5. repeat the same architecture on 6 and 9 without redesigning the rule system,
6. use multiple photos of the same physical watch to test whether local residual sign is stable across viewpoint.

Do not add production warning thresholds until these comparisons are calibrated on independent genuine watches.
