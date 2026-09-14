# GMT QC detector research convergence review — 2026-09-14

## Purpose

Repeat the technical research independently until two consecutive review passes reach the same implementation conclusion. This is implementation guidance, not Rolex factory tolerance data.

## Review pass A

Sources/themes checked:
- OpenCV homography and perspective correction guidance.
- OpenCV contour/Hu-moment shape descriptors.
- circular-gauge rectification literature using ellipse-to-circle normalisation.
- RepTimeQC examples covering cyclops/date angle effects and SEL false positives from shadows.

Conclusion:
1. rectify the planar dial once using the corrected 12/3/6/9 anchors;
2. validate that warp independently against visible dial-boundary/ellipse evidence;
3. use sector-constrained marker localisation with shape verification and ring consistency;
4. estimate one global 60-tick minute-track phase from the rectified annulus rather than local per-marker tick searches;
5. keep 12 owned by the dedicated triangle module and 3 owned by the date module;
6. split rectification confidence from component-detection confidence;
7. treat date aperture, numeral and cyclops as separate objects and avoid converting cyclops-distorted pixels directly into dial-plane offsets;
8. remove numeric SEL scoring until a true boundary/background-connectivity detector exists;
9. use the Root Beer, Batgirl and genuine/replica controls as a regression pack before producing the next APK.

## Review pass B

A second, independently phrased search checked:
- planar homography versus optical/lens distortion;
- circular-gauge ellipse rectification;
- OpenCV shape matching and elongated-object orientation;
- QC discussions about straight-on date/cyclops photos and apparent SEL gaps.

Result: the same architecture emerged. No design reversal was justified.

Additional nuance:
- a good global rectification does not imply that every local component detection is trustworthy;
- cyclops appearance is strongly viewing-angle sensitive, so fine date/cyclops judgments should require local component confidence and a suitable view;
- 6/9 orientation should only be emitted after a stable elongated contour is isolated;
- a dark SEL seam is not adequate evidence of a physical gap.

## Review pass C

A third check focused specifically on the potentially controversial choices:
- OpenCV `warpPolar` for converting a rectified annulus into angle/radius coordinates;
- gauge-reading research that rectifies an apparent ellipse to a circle before angular analysis;
- RepTimeQC guidance distinguishing visible-through SEL gaps from shadows;
- optical/camera calibration literature showing that metric image work needs distortion-aware modelling rather than assuming every observed region is an ideal planar homography.

Result: **the conclusion was materially identical to pass B**. This is the requested second consecutive matching conclusion, so the research loop stops here.

## Converged implementation plan

1. Build a canonical rectified dial from the corrected 12/3/6/9 pose.
2. Validate the warp using anchor residuals plus dial-boundary/ellipse-to-circle sanity checks.
3. Call that output `rectification confidence`.
4. Detect each planar hour marker only inside its expected angular/radial sector.
5. Verify candidate geometry with expected shape class and robust ring consistency.
6. Do not assign orientation to circular plots; only 6/9 may receive orientation, and only with stable contour confidence.
7. Keep the 12 triangle on the dedicated five-point module and do not let legacy local-track logic override it.
8. Convert the minute-track annulus to polar/angular space and estimate a single robust 60-period phase from the whole ring.
9. Give each component a separate localisation confidence that cannot exceed rectification confidence for planar dial features.
10. Detect the date aperture before analysing the numeral. Keep date/cyclops analysis local and view-sensitive rather than deriving fine offsets solely from the global dial warp.
11. Keep SEL visual-only for the next release. Reintroduce automation only when lug/end-link boundaries and background connectivity are explicitly segmented.
12. Run all changes against the Root Beer failure case, the user's Batgirl, corrected genuine GMT controls and known replica/QC controls. Reject any change that fixes one case while regressing the others.

## Final research decision

Two consecutive independent passes reached the same result. No further architectural change is justified before implementation. Future research should now be driven by regression failures from the new deterministic pipeline rather than additional broad browsing.
