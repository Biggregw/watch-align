# Alpha106: exact yellow-template area parity and single-photo stress (11 October 2026)

**Status: completed offline research; no production, template, detector, thresholds or APK changes.**

## Which expected polygon?

The current `MarkerMismatchBatch.java` already obtains the expected shape from the same functions used by the yellow Android close-up in `Alpha98Closeups.java`: `moved(spec.batonPolygon(), ring)` for batons and `moved(nominalTriangle(spec, ref), ring)` for the 12 triangle. The original research runner therefore already supports strict yellow-template parity.

A separate exploratory `calibrate_shapes.py` calculation then changed the expected polygon's vertices using training genuine watches. **Do not present the resulting experimental percentage as the area mismatch against the yellow shape that the user sees.** Keep this calibrated study separate, clearly labelled.

Formula chosen by owner: `100 * (extra_area + missing_area) / filled_expected_yellow_area`, with the entire outer marker surround measured, not merely the lume. Expected template and detected shape must share the same rectified pose and ring correction. Numeric percentages count areas (continuous subpixel analogue of pixels).

## Exact case: genuine Rolex Pepsi, Phillips CH080120 lot 75

- SHA-verified original photo ID `3cf5442ab9c87b9c`, dial radius `379.229 px`.
- 9 o'clock baton original yellow expected area: `0.03611897 R^2`, detector polygon: `0.03563752 R^2`, intersection `0.03544097 R^2`.
- **Extra: 0.544% of expected filled area. Missing: 1.877%. Total 2.421%.**
- The exploratory vertex-adjusted reference had given **3.997%**. That is not the yellow-overlay mismatch.
- Yellow original baton length `115.481 px`, fitted length `115.503 px`, width `44.981 px` vs `44.378 px`. Four canonical corner differences range `0.50–0.92 px`.
- Screenshot: marker fits the yellow outline visually, gaps appear balanced. Current app reports rotation `0.19° CCW` WITHIN and position `0.07% R` WITHIN.
- **Interpretation:** No visible manufacturing defect is established. Slight detected outer-edge differences are enough for an area percent of 2.42, but may originate in systematic edge selection, genuine dimensional variation, or both.

## Held-out genuine watches, same yellow reference (watch-level medians)

These are from the same held-out physical-watch subsets used for earlier per-vertex calibration research, but **without** those vertex corrections. Percentages here use the owner's filled expected yellow area denominator, not union.

| Family | Marker | Watches | Median | p95 | Maximum |
| --- | --- | ---: | ---: | ---: | ---: |
| GMT 126710 | 6 baton | 14 | 3.25% | 4.18% | 4.34% |
| GMT 126710 | 9 baton | 15 | 4.11% | 5.32% | 7.30% |
| GMT 126710 | 12 triangle | 16 | 12.54% | 15.67% | 16.18% |
| Submariner 124060 | 3 baton | 12 | 2.39% | 4.17% | 4.39% |
| Submariner 124060 | 6 baton | 12 | 2.62% | 4.53% | 5.21% |
| Submariner 124060 | 9 baton | 12 | 2.19% | 5.20% | 5.28% |
| Submariner 124060 | 12 triangle | 13 | 8.49% | 10.78% | 11.33% |
| Submariner 126610 | 6 baton | 12 | 2.41% | 3.24% | 3.35% |
| Submariner 126610 | 9 baton | 13 | 1.72% | 2.84% | 3.27% |
| Submariner 126610 | 12 triangle | 12 | 9.72% | 11.13% | 11.63% |

The uncalibrated triangle discrepancy is **large and systematic**: exact parity with the yellow drawing does not mean the baseline drawing equals the average genuine manufactured outer contour. No universal defect threshold follows.

## End-to-end one-original-image stress

Research CI: https://github.com/Biggregw/watch-align/actions/runs/38155286452

The workflow downloads the **exact SHA-verified original Phillips image**, generates eight mild transformations of that image alone, then reruns the **unmodified Java pipeline** on each. This is compatible with the product's **single-photo** constraint.

- Original 9: `2.421%` filled-yellow area mismatch.
- Six variants measurable, score range `2.240%–3.036%`, largest deviation `0.615 percentage points`.
- Two variants had **pose failures**, not pass results: contrast reduction (minute lattice residual) and small blur (lattice evidence insufficient).
- Photo perturbations reveal sensitivity to source processing but **cannot prove actual rim location or what fraction of the initial 2.42% is edge-fitting error**. A consistent wrong facet can survive perturbations.
- The current image cannot be treated as evidence that visible marker alignment is defective.

## Recommended bounded next test

Independently annotate the metal surround in the *existing* full-resolution Phillips photo, blind to detector vertices. Compare an annotation versus yellow and detector polygons in identical canonical coordinates. Distinguish the fitted metal outer edge from inner luminous edge. Only then decide whether the 2.42% is true shape variation versus repeatable boundary-selection error. Validate the reference size on variant-matched genuine watches and keep gap deviation separate. **No new tolerance or APK update now.**
