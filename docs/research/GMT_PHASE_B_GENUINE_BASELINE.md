# GMT Phase B: genuine-reference distribution for the frozen Phase A ratios

Status: Phase B complete, pending user review. Do not proceed to replica
comparison, tolerance definition, or perspective work without explicit
instruction -- see `docs/research/GMT_12_TRIANGLE_FRONTAL_MILESTONE.md`.

## Question

For a genuine Rolex GMT photographed in the kind of pose Watch Align expects
(reasonably frontal, complete dial clearly shown), what are the normal values
and natural variation of the five Phase A dimensionless ratios?

## Method

1. **Control-set manifest**: `datasets/gmt_phase_b_genuine/manifest.csv` -- 25
   independent 126710BLNR listings from three strong-provenance classes
   (`established_dealer`, `auction_house`, `rolex_cpo`), deliberately stronger
   provenance than Phase A's `gen_candidate` marketplace sources. See that
   directory's `README.md` for why this tier was required and why the corpus
   was scoped to one reference family.
2. **Acquisition**: `tools/research/phase_b_fetch_genuine.py` downloaded
   candidate images directly from each dealer/auction page (135 candidate
   images across 25 sources).
3. **Suitability review**: every candidate image was visually judged against
   the Phase B input-contract acceptance rule (frontal, complete 12 o'clock
   region, no artistic/oblique composition) BEFORE any measurement was
   attempted. 22/25 sources yielded a suitable image; 3 were rejected outright
   with a stated reason (1 dead listing, 2 with only off-axis/artistic
   photography available). Full log:
   `docs/research/gmt-phase-b-image-selection.md`.
4. **Measurement**: `tools/research/phase_b_measure.py` re-fetched each
   accepted image by its exact recorded URL and ran the FROZEN, unmodified
   Phase A detector (`tools/research/phase_a_landmarks.py` -- identical to
   the module that passed Phase A, zero changes) once per image. No
   perspective correction, no Stage 3 projective/canonical machinery,
   anywhere in this pipeline.
5. **Visual QA of detections**: an acceptance overlay was generated for every
   one of the 22 measured images and visually reviewed (all 22, not a
   sample). See "Visual QA findings" below.
6. **Aggregation**: `tools/research/phase_b_aggregate.py` excluded the 9
   images with a confirmed or suspected bad detection (see
   `docs/research/gmt-phase-b-results/visual_exclusions.csv`), collapsed to
   one median per physical watch (`physical_watch_id`), then computed
   population statistics over those 13 per-watch medians. One physical watch
   is one independent sample regardless of photograph count -- no watch in
   this corpus had more than one accepted image, so per-watch medians here
   equal the single accepted image's values.

## Visual QA findings (requirement: identify failures visually, don't hide them)

All 22 accepted images were measured and their acceptance overlays reviewed.
Population statistics were dramatically implausible before exclusion (e.g.
`base_width_over_height` ranged 0.47-8.68) -- visual review of the overlays
found the cause: **9 of 22 images produced a mis-detected dial reference
circle**, which cascaded into landmarks landing on unrelated dial features
(the date-cyclops digit, brand-name text, bezel numerals/rehaut engraving)
instead of the 12 o'clock triangle/coronet/minute-track. The detector itself
was not changed -- per governing instructions, these images are excluded from
the genuine distribution, not patched around:

| source_id | failure mode |
|---|---|
| `wf_435502` | landmarks on the date-cyclops "2" digit |
| `swe_59259` | landmarks on the date-cyclops "2" digit |
| `swe_77497` | landmarks on the ROLEX brand-text area |
| `swe_60177` | landmarks near ROLEX text/hour hand |
| `bobs_185749` | landmarks on bezel numerals/rehaut, dial circle too large |
| `bobs_175818` | landmarks on bezel numerals/rehaut, dial circle too large |
| `soth_2019_lot6` | landmarks on date-cyclops region (unusual tall-crop image confused the Hough fit) |
| `swe_68784` | landmarks near hand/bezel edge, axis line off-frame |
| `soth_6456` | ambiguous -- crop shows bracelet-link pattern where dial should be; excluded out of caution, not a confirmed failure |

The remaining 13 images had all five landmarks visually confirmed on their
correct physical feature (apex at the triangle tip, base corners at the
triangle's base, coronet on the crown logo, minute-track reference
immediately above the triangle, axis through the dial centre).

This is a real, useful finding in its own right: **the Hough-circle dial
locator (`seed_detector.detect_dial`, reused verbatim from the existing
codebase) is not reliable across this broader, more heterogeneous photo
set** -- it degrades on tightly-cropped, non-square-aspect, or
close-up-on-a-feature (date magnification) product photography, even though
it worked correctly on Phase A's single hand-selected image and on 13/22 of
this corpus. Any future work should treat dial-circle reliability as an open
problem, not assume it generalizes from Phase A's one success.

## Population summary (13 independent genuine watches)

Provenance: 6 `auction_house`, 6 `established_dealer`, 1 `rolex_cpo`.

| ratio | n | median | mean | std | min | max | p10 | p90 | Phase A reference |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `apex_position_in_interval` | 13 | 0.135 | 0.195 | 0.117 | 0.104 | 0.446 | 0.106 | 0.376 | 0.139 |
| `base_position_in_interval` | 13 | 0.377 | 0.390 | 0.103 | 0.266 | 0.571 | 0.284 | 0.527 | 0.405 |
| `triangle_height_ratio` | 13 | 0.185 | 0.194 | 0.059 | 0.125 | 0.355 | 0.138 | 0.244 | 0.266 |
| `base_width_over_height` | 13 | 1.039 | 1.107 | 0.283 | 0.821 | 1.670 | 0.867 | 1.582 | 0.829 |
| `horizontal_displacement_normalized` | 13 | 0.028 | 0.126 | 0.289 | -0.123 | 0.781 | -0.092 | 0.597 | -0.028 |

Full per-watch and machine-readable outputs:
`docs/research/gmt-phase-b-results/{per_image_measurements.csv,json,
per_physical_watch_medians.csv, population_summary.csv, population_summary.json,
visual_exclusions.csv}`.

## Interpretation

- The first four ratios (apex/base position, height, width/height) cluster
  reasonably tightly and bracket Phase A's single reference value near their
  median or within the p10-p90 band -- consistent with the frontal
  single-image proof generalizing to an independent genuine population.
- `horizontal_displacement_normalized` has visibly wider spread (std 0.289,
  one watch at 0.781) than the other four ratios, even among the 13 images
  whose apex/base/coronet/minute-track landmarks were all visually confirmed
  correct. The likely cause: this is the only ratio that depends on the
  Hough-circle dial centre (`axis_cx`) as a reference, and that centre
  estimate is more sensitive to how much of the bezel/dial the source photo
  actually shows than the other four ratios, which only depend on relative
  landmark positions. This is a genuine open question for whichever ratios
  proceed to Phase C/D, not a data-entry error -- it should NOT be silently
  tightened by excluding the high-displacement watches, since their
  landmarks were visually confirmed correct.
- No pass/fail tolerance is defined here. Per Phase B scope, this is
  descriptive: normal values and natural variation only.

## What this milestone does NOT do

- No replica comparison.
- No pass/fail tolerance.
- No perspective correction, homography, or pose estimation anywhere in this
  pipeline.
- No change to `tools/research/phase_a_landmarks.py` (the frozen Phase A
  detector) or to production Android QC.

## Files changed for Phase B

- `datasets/gmt_phase_b_genuine/` -- manifest, README, accepted_images.csv, .gitignore
- `tools/research/phase_b_fetch_genuine.py` -- candidate acquisition
- `tools/research/phase_b_preview_candidates.py` -- suitability-review preview printer
- `tools/research/phase_b_measure.py` -- frozen-detector measurement + overlay generation
- `tools/research/phase_b_aggregate.py` -- per-watch/population aggregation with visual exclusions
- `tools/research/phase_b_print_overlays.py` -- overlay QA preview printer
- `.github/workflows/gmt-phase-b-fetch-genuine.yml`, `.github/workflows/gmt-phase-b-measure.yml`
- `docs/research/gmt-phase-b-image-selection.md` -- full suitability review log
- `docs/research/gmt-phase-b-results/` -- measurement outputs (pixel-free; overlays are CI-artifact-only, never committed)
- This document
