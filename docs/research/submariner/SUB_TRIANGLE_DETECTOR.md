# Submariner 12-triangle research detector (v2)

This detector is research only. It produces raw measurements and no verdicts.

- No production change: `android/app/src/main` is identical to `main`.
- No GMT threshold is used.
- No genuine/replica rule exists.
- The GMT 44.3° ± 2° apex gate is not applied.

All design and tuning used the **development** partition only. The detector was frozen in commit
`fed2303` and then run **once** on validation. The holdout has not been measured or inspected.

Code:

- `tools/desktop-harness/drivers/SubTriangle.java`: candidate enumeration, refit, features and score.
- `tools/dataset_harvester/subresearch/triangle.py`: cross-variant consensus and plausibility.
- `tools/dataset_harvester/subresearch/triangle_eval.py`: metrics. It refuses the holdout.

## 1. Failure modes of the old research fit (v1, now landmark `12_legacy`)

The development review covered every photo where either detector reported a 12 at the original,
56 photos in all. The labels are in `triangle/dev_review_labels.csv`.

| Failure mode | Photos | What happened |
|---|---|---|
| Tick walk into the triangle's surround | 9 | The 59/01 tick "inner end" is found by walking inward along the tick. Where a base corner sits under the tick, the walk runs into the surround and stops at the search floor (0.835 R). The tick chord tilts by about 25° while the triangle itself is stable. This was the main cause of the 93° rotation-spread tail. |
| Dial fit on the bezel/rehaut ring | 2 (plus photos with no output) | The edge fit reports success but follows the bezel insert or rehaut ring. The "minute track" found is then the bezel's graduations, and the bezel's own 12 triangle/pip becomes a candidate. In some perturbations the fit changes ring, so the dial-centre reference moves several degrees. |
| Misfit beyond the outline | 2 | The refit extends past the surround. |
| Hand crossing / hand taken | 3 | |
| Watch turned more than 30° | 2 | The 12 is outside the search window, and another marker or a hand is taken instead. |
| QC annotation drawn over the dial | 1 | |
| Lume vs surround | 1 | The lume outline is traced instead of the metal surround. This is not wrong, but it is a different definition. |
| No dial in the photo | several | Strap, caseback or side view. The detector correctly returns nothing. |

## 2. Candidate model (v2)

1. **Enumerate.** Contours near 12 (±30° from image-up, 0.45–1.0 R) are taken from four binarisations: Otsu, two adaptive levels, and mean + 1 SD. All contours are kept, nested ones included, so both lume and surround outlines are candidates.
   - Each hull is reduced to tip and base corners.
   - It must fill its triangle (0.75–1.3) and point inward.
   - Duplicates are merged, and a count of how many binarisations agree is kept.
2. **Refit.** The three outer edges are refitted with the side-fitting procedure of `TriangleEdgeRefiner`, keeping each side's support fraction and RMS residual. There is **no** apex, squareness or GMT displacement gate. A corner that moves more than 0.08 R keeps the contour corners.
3. **Local frame.** The tick phase and pitch are found near the candidate.
   - The track's inner radius is the median of the tick ends at −4…−2 and +2…+4 minutes, never the 59/60/01 ends that the triangle contaminates.
   - The 59/01 reference points lie on that circle.
   - **Rotation** is the triangle axis (base midpoint to tip) against the radial through the 60 tick, with the dial ellipse's squash removed.
   - A purely local track-chord rotation (tick pairs ±2…±4) is kept as a diagnostic, `rotation_vs_track_chord_deg`. On development its median spread was 2.5°, against 0.4° for the dial-radial reference.
4. **Features per candidate.** These are written to `sub_triangle_candidates.csv`:
   - centre, ρ, angle from the 60 tick;
   - width, height, apex, squareness, symmetry;
   - edge completeness and fit residual;
   - nested outer/inner (lume) class;
   - track radius and spread, gap to the track, and whether the base lies outside the track (bezel/rehaut evidence);
   - the raw 59/60/01 tick-end radii;
   - every score term.
5. **Transparent score** (sum of soft terms; lower is better):

| Term | Meaning |
|---|---|
| `s_pos` | \|angle from the 60 tick\| / 4° |
| `s_rho` | ρ outside 0.74–0.86, per 0.02 R |
| `s_size` | width outside 0.20–0.30 R, per 0.02 R |
| `s_apex` | apex outside 40–49°, per 2°. Soft only, never a gate. |
| `s_sym` | side-length asymmetry / 0.05 |
| `s_square` | \|squareness\| / 3° |
| `s_axis` | orientation beyond 8° only, so real rotations of a few degrees are never penalised |
| `s_fit` | (1 − completeness) / 0.2 + residual / 0.004 R; 2 for a contour-only candidate |
| `s_track` | base outside the track (2) or no track found (0.5) |
| `s_outline` | inner outline of a nested pair (1) |
| `s_masks` | −0.1 per extra agreeing binarisation, up to 4 |

6. **Plausibility.** These windows decide whether a candidate is a 12 triangle at all. All come from development data and none is a QC threshold. A candidate outside any of them is reported but cannot be selected.

| Window | Range | Development basis |
|---|---|---|
| ρ | 0.68–0.92 | p5–p95 of complete refits 0.745–0.827 |
| Width | 0.12–0.35 R | p5–p95 0.139–0.261 (lume included) |
| Track gap | −0.01 to 0.10 R | p5–p95 0.020–0.075 |
| Apex | 30–58° | p5–p95 37.9–46.8°. Fourteen times wider than the GMT ±2° gate; only excludes non-triangles. |
| Height/width | 0.9–1.9 | p5–p95 1.15–1.46 |
| Score | ≤ 6.5 | Correct development selections scored at most 5.3; reviewed wrong ones 7.4–14.5 |

7. **Consensus across perturbations.** This is done in Python. The variants are original, 94%, 88%, ±1% and ±2% in x and y, and ±5° rotation.
   - Only variants whose dial is the *same edge-fitted dial* as the original's take part (centre within 0.01 R, radius within 2%). The rest are excluded and counted.
   - Candidates are grouped into physical outlines (centroid within 0.02 R, width within 12%).
   - Each variant votes with its best plausible candidate. The outline with the most votes is selected in every variant where it appears.
   - Landmark rows: `12` is the consensus selection; `12_top` is each variant's own best (diagnostic); `12_legacy` is v1.
   - `consensus_support`, `selected_is_variant_top`, `cand_rank` and `sel_score` are written for every selection.

**Prerequisite.** Only photos whose original dial was edge-fitted are used. Fallback (seed-circle) dials, and variants whose fit changed ring, are excluded and counted. On development, 77 of 179 photos qualified (26 watches):

- 66 photos were excluded because the original dial was not edge-fitted;
- 36 had no dial at all;
- 135 variants were excluded for a fallback or different dial.

## 3. What was tuned, all on development

1. The candidate model and frame described above.
2. Choosing the dial-radial over the chord rotation reference.
3. The dial-consistency rule for variants.
4. The plausibility windows, taken from the development distributions and the visual review.
5. The score ceiling.

There were four development iterations. Development photos are a fixed local image set. The same sources re-downloaded in CI can differ byte-for-byte, because the dealer CDN re-encodes some images. The acquisition now reuses cached bytes by URL, so later runs stay fixed.

## 4. Results

Before and after on identical photos and variants: edge-fitted originals, with variants restricted to the same dial. The "v1 as originally run" column applies only the old rule (edge-fit source) to v1.

**Development** (77 photos, 26 watches):

| Metric | v1, as originally run | v1 (`12_legacy`) | v2 (`12`) |
|---|---|---|---|
| Detection at original | 67.5% | 67.5% | 63.6% |
| Correct outline **and** frame among detections (review) | – | 34/52 (65%) | 48/49 (98%) |
| Wrong outline among detections (review) | – | 6/52 (11.5%) | 1/49 (2.0%) |
| Contaminated or wrong-ring tick frame (59/60/01 radii differ by more than 0.03 R) | – | 13/52 | 0 by construction |
| Centre spread, median / p95 (R) | 0.0024 / 0.287 | 0.0015 / 0.022 | 0.0013 / 0.012 |
| Rotation spread, median / p95 (°) | 1.73 / 58.3 | 1.12 / 25.9 | **0.29 / 1.55** |
| Apex spread, median / p95 (°) | 0.45 / 17.6 | 0.28 / 14.4 | 0.36 / 3.33 |
| Fit-path switch rate | 24% | 19% | 13% |
| Rank consistency / consensus support (median) | – | – | 1.0 / 1.0 |

v2 detects slightly less often at the original (49 against 52). Every photo it gives up was a wrong outline, a contaminated frame or a wrong dial ring under v1. Correct detections rise from 34 to 48.

**Validation, run once after the freeze** (23 photos, 8 watches; labels in `triangle/validation_review_labels.csv`):

| Metric | v1 (`12_legacy`) | v2 (`12`) |
|---|---|---|
| Detection at original | 73.9% | 78.3% |
| Wrong outline among detections (review) | 1/17 (5.9%) | 2/18 (11.1%) |
| Contaminated tick frame | 2/17 | 0 |
| Correct outline and frame | 14/17 | 16/18 |
| Centre spread, median / p95 (R) | 0.0015 / 0.016 | 0.0013 / 0.0077 |
| Rotation spread, median / p95 (°) | 1.22 / 9.78 | **0.29 / 1.72** |
| Apex spread, median / p95 (°) | 0.34 / 3.42 | 0.43 / 3.34 |
| Fit-path switch rate | 12% | 6% |

Both validation wrong outlines are hand-crossing cases: a hand covers part of the triangle and the visible part is traced. One of them is a low-resolution photo that v1 also got wrong. The validation result was **not** used to change the detector.

## 5. Known remaining failure modes

- **Hand crossing the triangle.** The visible part, or the part merged with the hand, can still pass the windows. There was 1 case on development and 2 on validation.
- **Watch turned more than 30°.** The 12 lies outside the search window. Since the score ceiling, v2 usually returns nothing rather than a wrong outline (1 remaining case on development).
- **Dial fit on the bezel/rehaut ring.** The triangle is not measured; it is rejected through the track gap. Dial localisation itself was not changed in this cycle.
- **Lume vs surround.** When the surround is low-contrast, the inner (lume) outline is measured. It is labelled `outline_class=inner`, and its size differs from the surround's.
- **Small samples.** 77 development and 23 validation photos, from 26 and 8 watches. Several photos come from one watch, so the photo-level rates above are not independent samples.

## 6. Why no production threshold was derived

The purpose was a stable measurement, not a decision.

- No verdict level was fitted.
- Genuine and replica were never used to tune anything.
- The holdout is untouched.
- Calibrating Submariner triangle geometry, such as gap, rotation or centring distributions on genuine watches, needs its own development-only study on this frozen detector. It should then be validated, with the holdout used once at the end, and every count made at the level of `physical_watch_id`.
