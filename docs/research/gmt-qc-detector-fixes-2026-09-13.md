# GMT QC detector fixes — targeted research — 2026-09-13

## Scope

Targeted research prompted by the real-device GMT test on the Root Beer QC image. The aim is to fix false positives without weakening genuine defect detection. This is implementation guidance, not Rolex factory tolerance data.

## 1. Rectify first, then detect in canonical dial coordinates

The strongest recurring computer-vision pattern is to remove perspective before fine geometry measurement. OpenCV's own marker-detection guidance rectifies candidate regions before classification, and recent circular-dial research likewise corrects projective deformation before angle/scale measurements.

Implementation consequence for Watch Align:
- Treat the corrected 12/3/6/9 pose as the single source of truth for fine GMT geometry.
- Warp the dial to a canonical circular plane once.
- Run marker, minute-track and date-window localisation in canonical coordinates.
- Map accepted detections back to the source image only for overlays.
- Do not run a second independent perspective estimator for individual extended checks.

This removes the current contradiction where the index module reports high confidence while older extended checks say perspective cannot be verified.

## 2. Hour-marker localisation should be expected-position constrained, not global bright-pixel search

The current detector is vulnerable because bright hands, text, reflections and bezel features can satisfy generic brightness criteria. A better approach is model-guided candidate detection.

Recommended pipeline:
1. Rectify dial.
2. For each expected hour independently, create a narrow angular sector and expected radial band from the GMT profile.
3. Use adaptive/Otsu thresholding plus connected contours inside that sector only.
4. Score candidates by radial distance, angular centre, area, compactness, brightness contrast and expected shape class.
5. Reject a candidate unless its score exceeds a minimum and it is spatially isolated from hands/text.
6. Require ring-level consistency using robust median/MAD across accepted markers.
7. The 12 triangle remains owned by the dedicated five-point triangle module.
8. The 3 o'clock position remains owned by the date module.
9. Circular plots should report position only. Orientation is meaningful only for elongated markers such as 6 and 9, and only when a contour supports a stable principal axis.

For elongated markers, prefer contour PCA/minAreaRect only after a plausible marker contour is isolated. Do not estimate body orientation from a large generic image patch.

## 3. Minute-track registration should be global after rectification

The current local minute-track search can latch onto nearby bright structures and produced implausible 12/6/9 offsets. After canonical rectification, a GMT has known 6-degree minute spacing.

Recommended pipeline:
- Detect multiple minute ticks around the dial annulus, not one local maximum.
- Fit a single global angular phase using robust consensus across visible ticks.
- Compare each hour marker to this common track phase.
- If too few track ticks are detected, fall back to the rectified profile axis and mark the result lower confidence.
- Do not independently search for a local minute tick beside each marker.

This should eliminate outputs such as a visually normal 12 marker being reported nearly 3 degrees away from its track reference.

## 4. Date aperture and numeral must be separate objects

Community QC repeatedly distinguishes date-window geometry, date-wheel/numeral centring and cyclops geometry. The app should do the same.

Recommended date pipeline in canonical coordinates:
1. Search only inside the model-defined date ROI near 3 o'clock.
2. Detect a quadrilateral/rounded-rectangle aperture candidate using edges plus contour geometry rather than thresholding for a generic bright blob.
3. Require plausible aspect ratio, size, radial centre and orientation relative to the dial.
4. Rectify the aperture itself to a small canonical rectangle.
5. Segment dark numeral ink inside an inset aperture mask.
6. Measure numeral centroid relative to the verified aperture box.
7. Reject the numeral result if the aperture is not confidently isolated.
8. For cyclops, detect the lens separately and compare lens centre/orientation to the already verified aperture.

A useful OpenCV pattern is adaptive threshold -> contour candidates -> convex/shape filtering -> perspective-normalised candidate analysis. HoughLinesP can also support rectangular edge evidence where contour boundaries are fragmented.

The current Root Beer result of roughly -30% horizontal / +31% vertical should therefore be suppressed unless the aperture detector itself passes confidence gating.

## 5. SEL detection should be visual-evidence based, not dark-pixel fraction

RepTimeQC consensus is consistent: a dark seam/shadow is not a SEL gap. Strong evidence is visible air/background/light through the lug-to-end-link joint. Examples reviewed explicitly state that a white/background region through the gap is meaningful while a black line can simply be shadow.

Recommended behaviour now:
- Remove automatic SEL severity based on dark-pixel proportion.
- Keep SEL as visual-only inspection in the current release.
- If automation is reintroduced later, first segment the lug boundaries and end-link boundary, then look for background-connected regions passing through the interface.
- Require connectivity from external background into the joint, not merely darkness.
- Multi-frame/video confirmation would be stronger than one still image.

Until this is implemented, the app should say `SEL: visual inspection only; no automatic gap score` rather than reporting 0.75/0.72-like pseudo-measurements.

## 6. Confidence must propagate from one object

Create/retain one `PerspectiveConfidenceService.Assessment` from the corrected four-anchor pose and pass it to every fine GMT module.

Rules:
- HIGH: fine measurements may be shown if component detection also passes.
- MEDIUM: show raw measurements but mark them advisory.
- LOW: suppress fine component verdicts and retain only coarse/visual observations.
- A component may further reduce confidence but must never upgrade above the shared pose confidence.

No report should simultaneously contain `high perspective` and `perspective could not be verified`.

## 7. Regression set and acceptance criteria

Use the existing real images as a mandatory regression pack:
- Root Beer QC image that exposed bad date/SEL/local-track outputs.
- User's Batgirl image.
- corrected genuine GMT pilot controls.
- known replica/QC images already used for triangle testing.

Acceptance criteria before calling GMT baseline complete:
- no physically impossible index coordinates survive.
- at least 7 non-date/non-12 markers are detected on suitable straight QC images, otherwise report unavailable without inventing values.
- dedicated 12-triangle result is unchanged by per-index work.
- circular markers never receive fake orientation values.
- 6/9 orientation appears only when contour isolation confidence is sufficient.
- date numeral centring is emitted only after confident aperture isolation.
- SEL is visual-only until a background-connectivity detector exists.
- all modules share the same corrected-anchor perspective confidence.
- no legacy local-track path may override the dedicated 12 result.

## Sources consulted

- OpenCV marker-detection guidance: candidate thresholding/contours, shape filtering and perspective normalisation before analysis.
- OpenCV Hough Line Transform guidance: Canny + HoughLinesP for line-segment evidence when useful.
- Circular dial perspective-correction literature using known scale/dial structure as rectification keypoints.
- Cognex watch-dial inspection guidance: locate/orient the dial first, then inspect in the normalised ROI.
- RepTimeQC examples on cyclops/date-angle false positives and SEL evaluation, especially the distinction between visible background through a joint versus a dark shadow line.

## Recommended implementation order

1. Shared corrected-pose confidence plumbing across all GMT checks.
2. Canonical rectified dial image helper.
3. Replace local marker search with sector-constrained per-hour candidates.
4. Replace local minute-track anchors with a global track-phase fit.
5. Rebuild date aperture detection around verified rectangle geometry, then numeral centring.
6. Disable numeric SEL scoring and make it visual-only.
7. Run regression pack and only then produce the next APK.
