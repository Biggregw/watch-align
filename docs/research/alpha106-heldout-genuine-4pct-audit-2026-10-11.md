# Alpha106 held-out genuine 4% cutoff audit (11 October 2026)

**Research only.** No changes to the Android app, production thresholds, genuine reference files, model outlines, or detectors. This note examines the existing `calibrated_shape_heldout_results.csv` archive alongside the Alpha106 area-overlap research.

## What was measured

Marker mismatch is `100 * area(expected XOR actual) / area(expected UNION actual)` on corrected canonical polygons. A training-watch-only median vertex adjustment was fitted on separate physical watches. The results below were evaluated on held-out independent physical watches for each marker family.

| Model | Marker | Held-out genuine watches | Above 4% | Wilson 95% rate interval | Held-out 95th-percentile mismatch |
| --- | --- | ---: | ---: | ---: | ---: |
| GMT 126710 | 12 triangle | 20 | 4 (20.0%) | 8.1 to 41.6% | 5.11% |
| GMT 126710 | 9 baton | 19 | 2 (10.5%) | 2.9 to 31.4% | 4.52% |
| GMT 126710 | 6 baton | 18 | 0 | 0 to 17.6% | 3.05% |
| Submariner 124060 | 12 triangle | 13 | 3 (23.1%) | 8.2 to 50.3% | 4.63% |
| Submariner 124060 | 3 baton | 12 | 2 (16.7%) | 4.7 to 44.8% | 4.29% |
| Submariner 126610 | 12 triangle | 12 | 0 | 0 to 24.3% | 2.87% |

Source counts reconstructed from the supplied held-out rates and sample sizes. The confidence intervals express **binomial sampling uncertainty only**, not dealer/photo selection bias, annotation quality, systematic pose errors or boundary detection confidence.

## Key consequence

A **universal 4% cutoff fails the genuine false-positive check** on these research samples. A 0/N result on one family does not establish a 0% population rate.

Marker area is still meaningful as a geometric feature. It does not equate perceptual sensitivity across marker types: a 0.5%-of-dial-radius shift to the right yielded 7.69% mismatch for the GMT triangle, 3.23% for its horizontal 9 baton and 6.72% for a round plot. A 1-degree pure rotation of an ideal circle produces 0% mismatch.

## Preserve worst-marker logic, but do not infer a watch-wide rate

A watch with one reliable, serious alignment defect must not achieve a pass through averaging the other markers. The current aggregate CSV does not retain common physical-watch identifiers across marker families. Therefore, it cannot establish how many **whole genuine watches** would fail a worst-marker rule. Neither adding individual marker failure percentages nor multiplying independent pass probabilities is justified.

## Critical remaining gaps

1. The current held-out calibration covers triangles and batons but not actual round-marker genuine distributions. Do not silently treat circles as having validated equal visibility or genuine spread.
2. The aggregate file provides >4% rates but **not counts above 5% and 6%**. Percentiles cannot reconstruct those counts. Retrieve `heldout_watch_scores.csv` or rerun the research-only batch to compute 4/5/6 by marker and by physical watch.
3. The earlier visual pilot's automated 1,960 trials confirmed code execution, **not human recognition**. Collect blinded human observations at controlled viewing size, followed by realistic photo-based watch stimuli and original Reddit QC examples.
4. Each marker needs a trustworthy pose/edge/occlusion assessment before interpreting a percentage. Every unassessed marker means incomplete whole-watch QC; no silent green/pass.
5. The 12 triangle is especially affected by reflected polished-surround edges; require repeat-photo and lighting validation before promoting its geometric templates.

## Proposed production gate

Retain `model + shape + hour` genuine evidence and a separate measured human-visibility criterion. Use a single user-facing strictness slider only **after** the available thresholds are empirically supported. Do not hard-code 4%, 5%, 6%, an arbitrary one-degree equivalence, a shape penalty or an averaged dial score into production yet.

See `docs/PERCEIVED-MARKER-DEFECTS-RESEARCH-2026-10-11.md` and `tools/research/marker_mismatch/calibrate_shapes.py`.
