# GMT QC detector fixes — targeted research — 2026-09-13

## Scope

Targeted research prompted by the real-device GMT test on the Root Beer QC image. The aim is to fix false positives without weakening genuine defect detection. This is implementation guidance, not Rolex factory tolerance data.

## 1. Rectify first, then detect in canonical dial coordinates

The strongest recurring computer-vision pattern is to remove perspective before fine geometry measurement. OpenCV's own marker-detection guidance rectifies candidate regions before classification, and circular-dial research likewise corrects projective deformation before angle/scale measurements.

Implementation consequence for Watch Align:
- Treat the corrected 12/3/6/9 pose as the primary source of truth for fine GMT geometry.
- Warp the dial to a canonical circular plane once.
- Run marker and minute-track localisation in canonical coordinates.
- Map accepted detections back to the source image only for overlays.
- Do not run a second independent perspective estimator for individual extended checks.

### Deeper-research revision: validate the homography, do not blindly trust it

Recent gauge-reading work shows that perspective rectification can materially improve measurements under tilt, but also explicitly guards against over-correction. Watch Align should therefore add an independent post-warp validation step:
- fit the visible dial/rehaut boundary as an ellipse in the source image where possible;
- after the four-anchor warp, measure how circular the corresponding dial boundary becomes;
- calculate anchor reprojection residuals and opposite-axis consistency;
- downgrade rectification confidence if the warp makes the dial less circular or if boundary/anchor evidence disagree.

The confidence object should therefore be renamed conceptually from `perspective confidence` to `rectification confidence`. A strongly oblique photo is not automatically unusable if the homography is well constrained; conversely, a visually straight photo with badly placed anchors should not receive high confidence.

## 2. Hour-marker localisation should be expected-position constrained, not global bright-pixel search

The current detector is vulnerable because bright hands, text, reflections and bezel features can satisfy generic brightness criteria. A better approach is model-guided candidate detection.

Recommended pipeline:
1. Rectify dial.
2. For each expected hour independently, create a narrow angular sector and expected radial band from the GMT profile.
3. Use gradient/edge evidence plus adaptive thresholding and connected contours inside that sector only.
4. Score candidates by radial distance, angular centre, area, compactness, brightness/edge contrast and expected shape class.
5. Reject a candidate unless its score exceeds a minimum and it is spatially isolated from hands/text.
6. Require ring-level consistency using robust median/MAD across accepted markers.
7. The 12 triangle remains owned by the dedicated five-point triangle module.
8. The 3 o'clock position remains owned by the date module.
9. Circular plots report position only. Orientation is meaningful only for elongated markers such as 6 and 9, and only when a contour supports a stable principal axis.

### Deeper-research revision: add shape matching as a second-stage verifier

OpenCV's Hu-moment `matchShapes` comparison is invariant to translation, rotation and scale to a useful degree. That makes it suitable as a candidate verifier after sector-constrained localisation. For each model profile we can store simple expected contour classes such as circle, rectangle/baton and triangle. Candidate acceptance should combine position evidence with shape similarity rather than relying on brightness alone.

Do not use full-image template matching as the primary detector because hands, lighting and dial printing vary. If reference-image matching is used later, apply it only to masked, rectified local sectors and use edge/gradient representations rather than raw colour/intensity.

For elongated markers, prefer contour PCA/minAreaRect only after a plausible marker contour is isolated. Do not estimate body orientation from a large generic image patch.

## 3. Minute-track registration should be global after rectification

The current local minute-track search can latch onto nearby bright structures and produced implausible 12/6/9 offsets. After canonical rectification, a GMT has known 6-degree minute spacing.

Recommended pipeline:
- sample the complete minute-track annulus after rectification;
- convert the annulus to an angular signal by aggregating radial edge/contrast energy for each angle;
- estimate one global 60-tick phase by correlation/robust consensus rather than independent local maxima;
- compare each hour marker to this common track phase;
- mask hands, date/cyclops area and known text contamination where possible;
- require enough periodic support around the dial before publishing a phase;
- if support is weak, fall back to profile axes and lower confidence.

This is stronger than merely detecting a handful of ticks. It uses the expected periodic structure of all 60 minute positions and should be much less vulnerable to one bright hand or reflection.

## 4. Date aperture and numeral must be separate objects

Community QC repeatedly distinguishes date-window geometry, date-wheel/numeral centring and cyclops geometry. The app should do the same.

Recommended date pipeline:
1. Use the model-defined date ROI near 3 o'clock.
2. Detect a quadrilateral/rounded-rectangle aperture candidate using edges plus contour geometry rather than thresholding for a generic bright blob.
3. Require plausible aspect ratio, size, radial centre and orientation relative to the dial.
4. Measure the numeral only inside an inset mask belonging to that verified aperture.
5. Reject the numeral result if aperture confidence is insufficient.
6. Detect cyclops geometry separately from date-wheel/numeral centring.

### Deeper-research revision: do not assume the cyclops region obeys the dial homography

This is an important change. A cyclops is a refractive magnifying element, so the image seen through it is not simply the planar dial transformed by the same projective homography. Computer-vision literature on imaging through refractive interfaces explicitly warns that ordinary pinhole/projective calibration becomes inaccurate through refraction.

Therefore:
- use the global dial homography to predict the approximate date ROI, but do not use it to make sub-percent numeral or magnification claims through the cyclops;
- measure date numeral centring relative to the observed aperture/lens geometry in a local coordinate system;
- require a near-frontal or dedicated date photo for fine cyclops/date judgments;
- apparent magnification should remain reference-comparative and advisory unless camera/view geometry is tightly matched;
- never convert cyclops-distorted pixels directly into dial-plane physical offsets as if they were non-refracted geometry.

This explains why date/cyclops measurements can remain unstable even when the rest of the dial rectifies well.

## 5. SEL detection should be visual-evidence based, not dark-pixel fraction

RepTimeQC consensus is consistent: a dark seam/shadow is not a SEL gap. Strong evidence is visible air/background/light through the lug-to-end-link joint.

Recommended behaviour now:
- remove automatic SEL severity based on dark-pixel proportion;
- keep SEL as visual-only inspection in the current release;
- if automation is reintroduced later, segment lug and end-link boundaries and look for background-connected regions passing through the interface;
- require connectivity from external background into the joint, not merely darkness;
- prefer multiple views/video for a strong automated conclusion.

Until this is implemented, report `SEL: visual inspection only; no automatic gap score`.

## 6. Confidence should be two-dimensional

The earlier plan used one shared perspective confidence. Deeper review suggests two separate confidence dimensions are cleaner:

1. **Rectification confidence**: are the 12/3/6/9 anchors and resulting warp geometrically trustworthy?
2. **Component confidence**: was this particular marker/date aperture/cyclops/track feature actually isolated reliably?

Rules:
- component confidence may never exceed rectification confidence for planar dial features;
- low component confidence suppresses that component without degrading unrelated components;
- cyclops/date-through-lens checks also need a view/refraction suitability flag because good dial rectification does not guarantee valid lens geometry;
- report the reason for withholding a measurement instead of a pseudo-number.

This avoids both previous failure modes: one poor detector poisoning the whole QC report, and a globally high perspective score incorrectly legitimising a bad component fit.

## 7. Optional reference-image refinement should be constrained and secondary

OpenCV ECC alignment can refine a roughly aligned image against a template, including homography-capable alignment, but it requires a reasonable initial alignment and can fail to converge. Because Watch Align already has manual anchor geometry, ECC could be useful later as a small refinement step on a masked dial annulus.

Recommended use:
- initialise from the manual four-anchor homography;
- mask hands, date/cyclops and central text;
- permit only a small correction;
- accept refinement only if correlation improves and geometric sanity checks remain valid;
- never let ECC overwrite manual geometry silently.

This is optional phase-two work, not required for the next APK.

## 8. Regression set and acceptance criteria

Use the existing real images as a mandatory regression pack:
- Root Beer QC image that exposed bad date/SEL/local-track outputs;
- user's Batgirl image;
- corrected genuine GMT pilot controls;
- known replica/QC images already used for triangle testing.

Acceptance criteria before calling GMT baseline complete:
- no physically impossible index coordinates survive;
- at least 7 non-date/non-12 markers are detected on suitable straight QC images, otherwise report unavailable without inventing values;
- dedicated 12-triangle result is unchanged by per-index work;
- circular markers never receive fake orientation values;
- 6/9 orientation appears only when contour isolation confidence is sufficient;
- date numeral centring is emitted only after confident aperture isolation;
- no fine cyclops/date physical-offset claim is derived solely from the global homography;
- SEL is visual-only until a background-connectivity detector exists;
- all planar modules share the same rectification-confidence object;
- each component has its own localisation confidence;
- no legacy local-track path may override the dedicated 12 result;
- rectified dial-boundary circularity must not materially worsen versus source ellipse evidence.

## Sources consulted

- OpenCV contour, ellipse fitting, shape matching/Hu moments and ECC image-alignment documentation.
- GAUREAD / DLR research on ellipse-to-circle rectification of circular gauges in unstructured views.
- 2026 virtual-point geometric rectification work showing strong gains under tilt while also using safeguards against over-correction.
- Research on refractive-interface camera calibration showing that ordinary projective models become inaccurate through refractive media.
- RepTimeQC examples on angle-dependent cyclops appearance, date-wheel centring, marker alignment and the distinction between true SEL gaps and dark seams.

## Revised implementation order

1. Rename/expand shared pose assessment into rectification confidence and add post-warp circularity/reprojection validation.
2. Build one canonical rectified dial helper.
3. Replace marker search with sector-constrained candidates plus shape verification and ring-consistency checks.
4. Replace local minute anchors with a 60-period global annulus phase estimator.
5. Rebuild date aperture detection, but keep numeral/cyclops measurements in a local observed-image coordinate system because of lens refraction.
6. Split rectification confidence from per-component confidence.
7. Disable numeric SEL scoring and make it visual-only.
8. Run the full regression pack before producing the next APK.
9. Consider masked ECC refinement only after the deterministic pipeline is stable.
