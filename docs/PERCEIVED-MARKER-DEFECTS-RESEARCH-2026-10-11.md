# Watch Align: perceived marker errors vs geometric pixel mismatch (research, 11 October 2026)

**Status:** research only. Do not change the Android app thresholds, genuine references, pose, or marker detectors on the basis of this document. Core geometric computation and held-out original-genuine research are on `feature/android-alpha106-marker-mismatch-research`. No model authenticity claim.

## The question

Can we count the actual marker area outside a calibrated genuine shape (**extra**), plus the expected marker area not occupied (**missing**), and use one percentage, perhaps 4%, to indicate obvious visual defects? Should the denominator be marker area or the entire dial? Does a triangle systematically show more error than a rectangle, or a circle less?

**Working result:** the symmetric-difference percentage is a good transparent *geometric* measure of total mismatch, but not a validated measure of *visibility*. Number of sides is not a perceptual ranking; direction of displacement, scale, line length, sharp corners, radial symmetry, adjacent minute ticks, contrast, photo quality and viewing distance all matter. A perfect circle has zero angular-rotation mismatch yet a size or centring defect can be perceptually salient.

## Exact definitions

Let E = calibrated expected filled outline, A = measured filled outline, I = intersection(E,A), U = union(E,A).

- extra = area(A minus E)
- missing = area(E minus A)
- **marker mismatch %** = 100 * (extra + missing) / area(U) (0% identical, 100% disjoint).
- **dial-area mismatch %** = 100 * (extra + missing) / (pi * dial_radius^2).

Both are independent of photographed pixel resolution when calculated on the same canonical, perspective-corrected geometry. Neither accounts for detector uncertainty by itself. Whole-dial normalization systematically reduces the contribution of small markers; it is not a psychophysical equalizer. The preferred *reported raw geometry* is percentage of each marker's union area, with dial-area percentage available as a secondary physical comparison.

## Actual 126710 model shape areas

Computed directly from `android/app/src/main/assets/models/gmt_126710/model.json`:

| shape | Area in dial-radius-squared | As % of dial |
| --- | ---: | ---: |
| 12 triangle | 0.0383625 | 1.2211% |
| 9 baton | 0.0362340 | 1.1534% |
| round plot | 0.0263022 | 0.8372% |

The triangle occupies only **5.9% more area** than the 9 baton, not dramatically more, despite their different visible outlines.

## Controlled geometry: identical physical perturbations on the three shapes

These are **pure geometric calculations**, not photographs or human visibility thresholds. A GMT baton is horizontally oriented at 9. The triangle is upright at 12. Circles were approximated with >= 512 segments.

| Shape | 1.0 degree rotation | 0.5% dial-radius shift right | 0.5% dial-radius shift upward |
| --- | ---: | ---: | ---: |
| 9 baton | 2.52% mismatch | 3.23% mismatch | 8.08% mismatch |
| 12 triangle | 3.17% mismatch | 7.69% mismatch | 6.20% mismatch |
| round plot | 0% mismatch | 6.72% mismatch | 6.72% mismatch |

A common 4% score corresponds to strikingly different displacements on a theoretical 28-mm dial: baton longitudinal 0.622% R (~0.087 mm), baton transverse 0.242% R (~0.034 mm), triangle lateral 0.254% R (~0.036 mm), circle lateral 0.293% R (~0.041 mm). This geometric anisotropy demonstrates that a constant mismatch fraction is **not an equal physical-distance tolerance**. Equal physical-distance tolerances, however, are not necessarily equally visible either.

For an outward shift of 0.0068 R, a geometrically unchanged 12 triangle would score 8.317% mismatch. This is a **hypothetical positional perturbation**, not a verified Batgirl result: the measured triangle size, side angles and radial edge confidence need to be independently checked.

## What published vision research says (without pretending it specifies watch tolerances)

1. Relative contour position can be discriminated much better than ordinary resolution under controlled foveal conditions (Westheimer & McKee 1977; Westheimer 1977). These laboratory hyperacuity thresholds **must not** be copied directly to a real dial at wrist distance.
2. Orientation discrimination improves with oriented-line length (Westheimer & Ley 1997); one human line-orientation study reports approximately 0.4 to 1 degree in a particular comparison task (Vázquez et al. 2000). This is not a universal 1-degree watch threshold.
3. Humans have better discrimination for horizontal/vertical than oblique orientations in experimental settings (Westheimer 2003). A 6/9 baton and sloped triangle sides therefore are **not perceptually interchangeable**.
4. Circular shapes are highly sensitive to small non-circular deformation, with some specialized radial-frequency stimuli producing very low laboratory thresholds (Wilkinson et al. 1998; Zanker & Quenzer 1999). Circles are rotationally invariant, **not** immune to size/centering/curvature defects.
5. Nearby or surrounding contours can interfere with vernier judgments (Levi et al. 1985; Manassi et al. 2016). Printed minute tracks, nearby markers and hands affect apparent alignment.
6. Structural image similarity and human visual quality need not equal pointwise pixel discrepancy (Wang et al. 2004). Watch Align's geometry metric measures physical outline overlap, not validated visual salience.

Selected primary sources:

- Westheimer & McKee 1977, [Spatial configurations for visual hyperacuity](https://www.sciencedirect.com/science/article/pii/0042698977900694)
- Westheimer 1977, [Spatial frequency and light-spread descriptions of visual acuity and hyperacuity](https://pubmed.ncbi.nlm.nih.gov/839301/)
- Westheimer & Ley 1997, [Spatial and Temporal Integration of Signals in Foveal Line Orientation](https://journals.physiology.org/doi/10.1152/jn.1997.77.5.2677)
- Vázquez et al. 2000, [Discrimination of Line Orientation in Humans and Monkeys](https://journals.physiology.org/doi/10.1152/jn.2000.83.5.2639)
- Westheimer 2003, [Preferred orientations](https://pubmed.ncbi.nlm.nih.gov/12505604/)
- Zanker & Quenzer 1999, [How to tell circles from ellipses](https://pubmed.ncbi.nlm.nih.gov/10541660/)
- Wilkinson et al. 1998, [Detection and recognition of radial frequency patterns](https://pubmed.ncbi.nlm.nih.gov/9893789/)
- Levi et al. 1985, [Vernier acuity, crowding and cortical magnification](https://pubmed.ncbi.nlm.nih.gov/4049746/)
- Manassi et al. 2016, [Crowding and object representations](https://pubmed.ncbi.nlm.nih.gov/26913627/)
- Wang et al. 2004, [Image quality assessment: from error visibility to structural similarity](https://pubmed.ncbi.nlm.nih.gov/15376593/)

## Relevant *actual genuine Rolex* experiments (not subjective visibility)

An existing original-photo study, using one independent measurement per physical watch and exact polygon comparison, produced raw mismatches above 4% even on verifiable genuine watches. On the GMT 126710, uncalibrated **triangle** mismatch median was around 10.9% and all 48 usable genuine watches exceeded 4%; the master triangle is not an adequate pixel-perfect manufacturing standard as-is.

A follow-up experimental **training-watch-only per-vertex geometric correction** (held-out watch evaluation, not installed in app) reduced the GMT 126710 results to:

| Shape | Held-out genuine watches | Calibrated median mismatch | Held-out 95th percentile | Genuine watches >4% |
| --- | ---: | ---: | ---: | ---: |
| 12 triangle | 20 | 2.59% | 5.11% | 20.0% |
| 9 baton | 19 | 1.80% | 4.52% | 10.5% |
| 6 baton | 18 | 1.94% | 3.05% | 0.0% |

Other held-out models from the same research: 124060 12 triangle 13 watches / p95 4.63% / 23.1% >4%; 126610 12 triangle 12 watches / p95 2.87% / 0% >4%. Different model/sample distributions, sample-selection uncertainty, photographs, and polished-surround radial measurement sensitivity mean **no universal threshold is proven**. Per-vertex calibration is an *experimental nominal outline*, not a production manufacturing reference and not an official Rolex tolerance.

Inputs: `WatchAlign_Original_Genuine_Mismatch_gmt_126710.zip`, `WatchAlign_124060_Original_Genuine_Mismatch_Research.zip`, `WatchAlign_Original_Genuine_Mismatch_submariner_126610.zip`, and `calibrated_shape_heldout_results.csv` from the owner's research artifacts. The reproducible watch-held-out calibration is under `tools/research/marker_mismatch` on this branch.

## Proposed practical QC design, pending human perception testing

1. Measure the full **extra + missing %** using the exact calibrated expected outline and detector polygon, all in the frozen canonical pose. Keep model's calibrated nominal outline separate from the yellow ring-adjusted *visual* expected outline: document which is used, and display the same one.
2. Keep marker mismatch % as a factual measurement; optionally show whole-dial area for intuition but **never use the whole dial alone to determine a pass**.
3. Display primary cause (tilted, shifted, too close to minute track, shape distorted). A triangle's radial spacing can be very noticeable despite a modest area mismatch; a single unusual corner can matter even with small symmetric-difference area.
4. Retain reference-based genuine statistics, repeat-photo uncertainty, per-shape/per-model evidence and photo fit gates. Do not let a 4% numerical score override NOT ASSESSED due glare, hands, insufficient pixels or unstable pose.
5. No overall averaged dial score. Whole-watch statement only if **all assessed markers** pass selected QC strictness; if any marker is unassessed, say incomplete assessment.
6. **Test 4%, 5%, 6% first**, plus shape-specific held-out genuine percentiles, on independent original QC photographs with *blinded human labels*. Blind viewers to the defect angle, overlap percentage and factory/model. Evaluate both true-size ~30 cm viewing and magnified QC screening, with separately recorded outcomes. Choose a threshold only after measuring genuine false positives and missed visually clear defects. Human judgments should be collected by repeated trials and multiple observers if possible.
7. To capture a single highly salient spike with small area: record maximum reliable **local contour displacement** as a *separate diagnostic*, not necessarily a hidden arbitrary penalty inside the percentage. Report both area and localization; treat tiny specular edge artefacts as unassessed.
8. Preserve current genuine reference files and production UI until this research is validated. Spend Claude credits on narrowly scoped final check rather than discovery.

**Bottom line:** one symmetric-difference formula is reasonable across triangles, batons and circles. One identical numerical *human-perceptibility* cutoff is **not yet supported**. The shape-specific genuine data show 4% would mark some genuinely manufactured Rolex markers as defective, so hold off on 'excellent replica if every marker <4%' until both the genuine and blinded visual validations are complete.
