# GMT-Master II 126710BLNR proportional/projective 12-triangle geometry -- Stage 1

**Branch:** `experiment/gmt-proportional-geometry-v1` (from the frozen `experiment/generic-geometry-reticle-v2` HEAD). **Research input:** `research/proportional-geometry-knowledge-base` (fetched and re-read at every checkpoint the task specified; see "Knowledge-base checkpoints" below). **Status:** feasibility/method result, not a validated genuine profile -- see the sample-size caveat throughout.

## Headline caveat (read first)

**The calibration-split `gen_candidate` pool for this corpus contains only 2 independent physical watches (7 images total).** Every statistic in this document is computed honestly from that sample, but n=2 does not support a validated genuine-population distribution by any reasonable standard. This experiment demonstrates that the measurement pipeline (independent triangle segmentation, peer-only round-marker corridor, local 1D projective radial mapping, feature computation, frozen-profile application, overlay) works end to end and produces internally consistent, non-fabricated results -- it does not yet demonstrate that the resulting bands are trustworthy genuine geometry. This is stated once here and not repeated at every number below, but it applies to all of them.

## Why raw pixels are unsuitable

A pixel distance mixes photo scale, camera distance, and dial-plane perspective into one number that means nothing on its own. This project's own prior research (`radial-drift-root-cause-2026-09-22.md` and related docs) already established that raw radial pixel measurements are not comparable across photographs. Everything in this experiment is expressed in canonical, dial-radius-normalised coordinates (`axis_coords.py`, built on the already-tested `marker_consensus.inverse_map`), never raw pixels, and the 12-triangle's own geometry is measured completely independently of any expected position before any comparison happens.

## How viewpoint was handled

- The generic structural layer (dial centre, minute-track channel, 12 gapped hour axes, round-marker peer corridor) is the frozen `experiment/generic-geometry-reticle-v2` code, unchanged.
- The nominal 12-axis *direction* comes from the affine ellipse+roll basis (`marker_consensus_analysis._nominal_direction`), established elsewhere in this project as reliable for angle specifically -- never for radial extrapolation.
- Two radial representations were computed and compared for every triangle point: a **simple** affine-normalised canonical radius (`axis_coords.canonical_axis_components`, the direct analogue of `rho = distance / dial radius`) and a **local 1D projective radial mapping** `t(r) = a*r/(c*r+1)` fit *only* from the round-marker peer corridor's inner/centre/outer conics intersected with the 12 axis (`projective_radial.py`). The 12 triangle contributes nothing to that fit.

## Did projective normalisation actually improve stability?

**Inconclusive, and reported as inconclusive rather than forced to a conclusion.** In this specific 7-image, 2-watch sample:

- **Spread was tighter under the projective representation** for all 6 simple/projective feature pairs tested (e.g. `base_r`: simple MAD 0.047 vs. projective MAD 0.007 canonical units) -- this is consistent with the hypothesis.
- **Apparent tilt correlation was *not* better under the projective representation** -- in fact it was often much stronger (e.g. `apex_r`: simple r=-0.06 vs. projective r=+0.87), which runs counter to the hypothesis on its face.
- **Coverage was far worse for the projective form**: only 3 of 7 images had a usable round-marker corridor (>=5 round markers with a well-conditioned fit) at all, all 3 of them at <=10deg tilt, so there is zero `>10deg` evidence for the projective form in this sample and the "tilt correlation" figure above is an n=3 Pearson correlation -- essentially noise-level evidence in either direction.

The honest reading: the projective mechanism is implemented correctly and internally consistent (fit residuals against its own 3 calibration points were sub-0.001 canonical units on the reference image), but this sample cannot support a claim that it outperforms simple normalisation for tilt-robustness. That question remains open, exactly as the shared research knowledge base's open-questions list anticipated.

## Which proportions survived / which failed

Five core features were selected (full definitions, medians, spreads and known limitations are in the frozen profile, `tools/watch_align_py/gmt_12_triangle_profile.json`):

| Feature | Retained | Why |
|---|---|---|
| `apex_r_simple` | Core | Full coverage (7/7), clear physical meaning, tightest within-watch repeatability observed (MAD 0.0065/0.0063) |
| `base_r_simple` | Core | Full coverage, clear physical meaning; redundant with `centre_r_simple` (r=0.92) but kept for its distinct overlay role (base gate) |
| `centroid_tangential_offset_canonical` | Core | Directly addresses the most common community-reported 12-marker concern (left/right shift) per the refreshed shared research knowledge base; full coverage |
| `symmetry_axis_angular_deviation_deg` | Core, flagged weakest | Addresses the #1 community-reported concern type (orientation), but within-watch MAD was 2.8 deg and 6.7 deg on the two calibration watches -- substantially noisier than the radial features. Retained provisionally, not because the evidence is strong. |
| `base_width_over_height` | Core | Shape/size discriminator; full coverage; one watch showed much larger within-watch spread than the other (0.157 vs. 0.030), not understood with this sample size |
| `centre_r_simple` | Secondary, not core | Redundant with `base_r_simple` (r=0.92 in-sample); still shown on the overlay (centre gate) for human interpretability |
| `base_to_minute_track_gap_simple` | Secondary, not core | Deterministic transform of `base_r_simple`; shown on the overlay per the explicit local-bracket requirement |
| `*_projective` forms | Secondary, not promoted | Coverage only 3/7 in this implementation; tilt-correlation evidence inconclusive at this sample size (see above) |
| `base_half_width_symmetry` | **Rejected** | Tautologically ~0 (1e-16) under the current corner-finding method -- `base_centre` is defined as the exact midpoint of the found corners, so this cannot express real asymmetry without a different, independent asymmetry estimator |
| `centroid_position_within_span` | **Rejected** | Fully determined by already-tracked features (apex/base/centre radii) -- redundant |
| `apex_to_base_span_*` | **Rejected as independent features** | Deterministic difference of already-tracked/reported features; still reportable as a derived quantity |

## Calibration sample limitations

- 2 physical watches, 7 images, both `gen_candidate` (source-labelled genuine, not independently authenticated).
- Round-marker corridor availability (needed for the projective form) was only 3/7 images -- the bottleneck is round-marker segmentation succeeding on >=5 of the 8 round markers, not the projective mathematics itself.
- No `>10deg` tilt evidence exists for any projective-form feature in this sample.
- Every statistic in the frozen profile and this document should be read as a demonstration that the *pipeline* works, not as a validated expectation for real 126710BLNR watches. A useful next step (not undertaken here, per the task's scope) would be gathering materially more calibration `gen_candidate` watches before trusting any band.

## How the overlay communicates the maths

Two modes, both reusing the frozen structural layer (now shown more subdued, per the shared research knowledge base's overlay principles) plus a new local 12-only proportion layer:

- **User mode**: expected apex/base gates (short brackets, not full-dial rings) and a subtle expected-centre target from the frozen profile; a subtle expected-genuine band at the apex/base gates; the observed apex/centre/base points and the observed triangle axis from this photo's own independent segmentation; a local base-to-minute-track bracket. No numeric text, no colour-coded verdict, no red/green, no PASS/FAIL, no authenticity wording.
- **Diagnostic mode**: the identical image, plus a separate text panel (never drawn over the dial) with apparent tilt, the 5 core feature names, calibration median/range, observed value, signed difference, projective-fit residual, segmentation quality (`triangle_confidence`, `triangle_axis_agreement_deg`), and an explicit "within / outside central range (n=2 watches -- illustrative only)" status string that never uses defect language.

## What remains unproven

- Whether any of these 5 features would survive feature selection with a real calibration population (5+ independent physical watches, ideally more).
- Whether the projective radial mapping materially reduces tilt-sensitivity -- open, not resolved by this sample.
- Whether `symmetry_axis_angular_deviation_deg`'s poor within-watch repeatability in this sample is a real property of the measurement or an artifact of only 2 watches.
- Whether any of these 5 proportions are shared with other Rolex sports-watch models (explicitly not tested here; see "Future shared-family research").

## User QC photo result (descriptive only, per the task's explicit reporting rules)

Applied the frozen pipeline and frozen profile, unchanged, to the user's own attached dealer QC photograph (tilt 3.94 deg, 8/8 round markers segmented, projective fit available). No feature, normalisation, or visual rule was altered after this result was seen.

| Feature | User observed | Calibration median (n=2) | Central range | Status |
|---|---|---|---|---|
| `apex_r_simple` | 0.5812 | 0.5605 | [0.5257, 0.5953] | within central range |
| `base_r_simple` | 0.8346 | 0.7592 | [0.7030, 0.8154] | **outside** central range (toward the outward edge) |
| `centroid_tangential_offset_canonical` | -0.0023 | -0.0484 | [-0.1310, 0.0342] | within central range |
| `symmetry_axis_angular_deviation_deg` | -0.38 | -3.71 | [-6.46, 0.03] | within central range |
| `base_width_over_height` | 0.902 | 1.145 | [1.026, 1.264] | **outside** central range (toward the narrower/taller edge) |

No authenticity, GL/RL, "good replica", "bad replica" or defect-confirmation language is used or implied by this table or anywhere else in this experiment, per the task's explicit rule. These are geometric comparisons against an n=2 illustrative sample, nothing more. The official reference's own values (tilt 9.53 deg) are in `gmt_proportional_official_reference_measurements.json` for the same kind of comparison, applied identically.

## Knowledge-base checkpoints

The research branch (`research/proportional-geometry-knowledge-base`) was fetched and re-read at every specified checkpoint (task start, before finalising the feature catalogue, before feature ranking, before freezing the profile, before overlay design, before final conclusions). It was updated twice during this experiment:

1. An edit to the authoritative file itself, adding a "Defect-driven research priorities from RepTimeQC sampling" section (marker placement/orientation as priority 1, cyclops/date as priority 2, bezel-internal geometry as priority 3, date-glyph centring as priority 4, rehaut explicitly deprioritised). This update's 12-triangle feature list matches, almost bullet-for-bullet, the feature catalogue already implemented here -- no conflict, no retroactive change needed. Its guidance to express marker orientation "in the same projected coordinate system" as the expected hour-axis direction (never a raw image vertical/horizontal) was already the design of `axis_coords.py` before this update landed.
2. A new supplemental (non-authoritative) file, `docs/research/proportional-geometry-defect-rule-roadmap.md`, elaborating the same priorities. Consulted for context, not treated as a required or authoritative input, consistent with the task's instruction that only the named authoritative file is the shared specification.

No conflict arose between newer research guidance and an already-frozen decision in this run.

## Follow-up experiments the refreshed knowledge base recommends (not started here)

Explicitly out of scope for this narrow 12-triangle proof, per both the task and the refreshed knowledge base: 6/9 baton orientation/placement, cyclops-to-date-window geometry, internal bezel geometry, date-glyph centring, rehaut alignment. All are documented as priority follow-ups in the knowledge base, none implemented here.

## Future shared-family research (recorded, not promoted)

None of the 5 retained features are promoted to a shared GMT/Submariner or generic-clock rule -- that would require genuine Submariner calibration data this project does not have. For future reference, likely categories once such data exists:

- **Likely generic clock geometry**: `centroid_tangential_offset_canonical` (any dial marker's tangential placement relative to its own nominal hour axis is a generic concept).
- **Likely Rolex sports-watch family geometry**: the minute-track-relative gap concept (`base_to_minute_track_gap_simple`), since many Rolex sports dials share a similar minute-track/marker-band relationship -- unverified.
- **GMT-specific**: the exact `apex_r_simple` / `base_r_simple` canonical values and `base_width_over_height`, since the 126710BLNR triangle's specific proportions are not assumed shared with any other model.

## Deliverables

- `tools/watch_align_py/axis_coords.py`, `projective_radial.py`, `triangle_measurement.py`, `gmt_proportional_features.py` -- the measurement engine.
- `tools/watch_align_py/experiment_gmt_proportional_calibration.py` + `.github/workflows/gmt-proportional-calibration.yml` -- calibration run (CSV committed directly, no images).
- `datasets/126710BLNR/results/gmt_proportional_calibration.csv` -- per-image calibration features (deliverable 1).
- `datasets/126710BLNR/results/gmt_proportional_watch_summary.csv` -- physical-watch-level summary (deliverable 2).
- `datasets/126710BLNR/results/gmt_proportional_stats_report.txt` -- full per-feature statistics, simple-vs-projective comparison, and apex/centre/base correlation analysis (deliverables 3-4).
- `tools/watch_align_py/gmt_12_triangle_profile.json` -- frozen profile, machine-readable (deliverables 5-6).
- `datasets/126710BLNR/results/gmt_proportional_official_reference_measurements.json`, `gmt_proportional_user_qc_measurements.json` -- deliverables 7-8.
- `tools/watch_align_py/gmt_proportional_render.py`, `gmt_proportional_render.py` (overlay code), `render_gmt_proportional.py` (driver) -- deliverables 9-11 (rendered images sent directly to the user, not committed -- derived from third-party/first-party source photos).
- This document -- deliverable 14. Deliverable 12 (exact calibration `physical_watch_id`s): `gen_wex_3KSuGhC`, `gen_wex_e99gXKb`. Deliverable 13 (rejected features + reasons): see the feature table above.

## Explicitly out of scope / not done here

No automatic warning thresholds. No pass/fail. No authenticity, GL/RL, factory, or combined-quality score. No Submariner support. No Android changes. No production QC changes. No validation-split inspection. Nothing merged, no PR opened.
