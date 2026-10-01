# Genuine 124060 geometry calibration (development only)

This is a research study. It asks one question: **which geometric measurements on a genuine Rolex
Submariner 124060 repeat well enough, from photo to photo of the same watch, to be worth studying
later?**

What it is not:

- It is not a genuine-vs-replica comparison. No replica row is read.
- It derives no QC threshold, verdict or score.
- Validation and holdout were neither measured nor opened.
- It changes no detector. The 12-triangle detector is the frozen v2 (`fed2303`; see
  `SUB_TRIANGLE_DETECTOR.md`).
- It changes no Android production code.

Code:

- `tools/dataset_harvester/subresearch/geometry124060.py`: the analysis.
- `tools/dataset_harvester/submariner_measure.py --partition development --model 124060 --class-tag gen`:
  the measurement.
- `tools/dataset_harvester/tests/test_submariner_geometry.py`: the tests.

Frozen results are in `docs/research/submariner/geometry/`:

| File | Content |
|---|---|
| `sub124060_photo_geometry.csv` | One row per usable photo × metric: the value at the original, perturbation range and MAD, synthetic 88% / ±5° shifts, and the photo's pose covariates. |
| `sub124060_watch_geometry.csv` | One row per `physical_watch_id` × metric: usable photo count, watch median, within-watch MAD and range. |
| `sub124060_metric_repeatability.csv` | One row per metric: watch and photo counts, the three spreads, the ratio, the class, pose correlations and the pose flags. |
| `sub124060_geometry_summary.json` / `.md` | Counts, exclusions, the class lists and the pose flags. |
| `sub124060_dev_review.csv` | Exclusions from the visual review. |

The CI workflow re-runs the study on its own measurement (step "Genuine 124060 geometry study") and
uploads it under `geometry124060/`. The committed tables come from the fixed local development image
set used for the triangle work. CI results may differ slightly if a dealer CDN served different bytes.

## 1. Dataset

The scope is `family == submariner_12`, `model == 124060`, `class_tag == gen` and
`partition == development` under the locked split `split_sub_v2.csv`. The script reads only rows
matching all four and stops if there are none. A test checks that validation, holdout, replica and
other-model rows are ignored.

| | Photos | Physical watches |
|---|---|---|
| Accepted and in scope | 36 | 9 |
| No dial in the photo (strap, caseback, side view) | 10 | |
| Dial located only by the fallback seed circle, not edge-fitted | 4 | |
| Excluded at review (see section 2) | 3 | |
| **Usable** | **19** | **9** |

Usable photos per watch (all Bob's Watches listings):

| Watch | 151200 | 174149 | 182386pl | 182455 | 182482 | 187439 | 187502pl | 190837 | 191110 |
|---|---|---|---|---|---|---|---|---|---|
| Photos | 2 | 3 | 1 | 3 | 3 | 2 | 1 | 2 | 2 |

Seven watches have two or more usable photos. Two have one, so they add to the between-watch spread
but not to the within-watch spread.

## 2. Exclusion rules

1. **No dial**, or **fallback dial**: the original photo has no edge-fitted dial ellipse. The photo is
   excluded.
2. **Perturbation variants** are kept only if their dial is the same edge-fitted dial as the
   original's: centre within 0.01 R and radius within 2% (`triangle.dial_usable`).
3. **Visual review** (`sub124060_dev_review.csv`). Every in-scope photo with a dial was rendered with all
   its landmarks and checked by eye.
   - **Whole photo excluded (3):** the watch is upside down and small in the frame, and the detections
     fall on the bezel.
   - **Single landmarks excluded (19 photos):**
     - a hand crosses the landmark: rounds at 1, 2, 4, 5, 7, 8 and 11, the 12 on three photos, and b3
       on one photo;
     - a round marker's circle lies on the bezel or rehaut: four markers on three photos, two of them
       oblique.
   - An excluded landmark contributes no metric, and no relational metric that uses it.
   - No QC annotations were found on in-scope photos.
4. **Unusable fits.**
   - Batons count only with fit path `edge_refit`.
   - Round markers count only if no more than 25% of their edge points were rejected.
   - A lume radius is recorded only when the fitter found two rings.
5. **Lume vs surround.** The triangle's metrics carry the outline class in their names: `t12_surround_*`
   for the outer outline and `t12_lume_*` for the inner one. The two are never pooled. Round markers
   report `surround_radius_r` and `lume_radius_r` separately. `fitted_edge_radius_r` is whichever edge
   the fitter took.
6. **Wrong-outline triangles.** Under the frozen detector, none was found among the usable photos.

## 3. Independence unit

`physical_watch_id` is the unit.

- Photos of one watch are repeatability observations, never extra samples.
- The between-watch spread is computed over **watch medians**, so every watch counts once.
- The within-watch spread is computed per watch, using watches with two or more usable photos, and
  summarised as the median over those watches.

## 4. Measurement definitions

All radial quantities are divided by the dial radius R = √(a·b) of the edge-fitted ellipse. Positions
are taken in the **rectified dial frame**, with the ellipse's squash removed as in `DialFrame`.

- **Angular reference.** The angular reference is the frozen triangle's 60-tick radial: the
  triangle's rectified clock angle minus its `dtheta_from_nominal_deg`.
- **Without a triangle.** If a photo has no usable triangle, it has no angular offsets.

| Group | Metrics |
|---|---|
| 12 triangle | `rho`, `dtheta_from_60tick_deg` (angle from the 60 tick), `width_r`, `height_r`, `apex_deg`, `rotation_deg` (axis against the 60-tick radial), `gap_to_track_r`, `lateral_offset_from_60tick_w` (centre-to-60-tick offset in triangle widths). Prefix `t12_surround_` or `t12_lume_`. |
| Batons (b3, b6, b9) | `rho`, `dtheta_deg` (from h × 30° past the 60-tick reference), `length_r`, `width_r`, `rotation_deg`, `inset`, `gap_to_track_r` |
| Round markers (r1 … r11) | `rho`, `dtheta_deg`, `fitted_edge_radius_r`, `surround_radius_r`, `lume_radius_r`, `inset`, `gap_to_track_r` |
| Dial / track | `dial_axis_ratio`, `dial_r_px`, `dial_fit_rms_over_r`, `track_radius_r`, `track_pitch_deg`, `track_spread_r` (from the triangle's local tick frame), `mpose_tilt_deg` and `mpose_residual_r` (marker-layout affine), `rehaut_rh_w{12,3,6,9}_r`, `rehaut_min_over_mean` (descriptive) |
| Image frame, descriptive only | `dial_cx_px`, `dial_cy_px`, `dial_semi_major_px`, `dial_semi_minor_px`, `tick60_image_clock_angle_deg`. These describe the photo, not the watch, and are not classified. |

Relational metrics:

| Metric | Definition |
|---|---|
| `ring_round_rho_median`, `ring_round_rho_mad` | Ring radius, and the consistency of the round markers about it |
| `ring_round_circle_radius_r`, `ring_round_circle_centre_offset_r` | Least-squares circle through the round-marker centres; offset of that circle's centre from the dial centre |
| `ring_round_mean_angle_offset_deg`, `ring_round_spacing_rms_deg` | Common angular offset of the round markers, and the RMS of their individual offsets about it |
| `opp_rA_rB_angle_dev_deg`, `opp_rA_rB_rho_diff` | Opposite pairs 1–7, 2–8, 4–10, 5–11: deviation from 180°, and radial difference |
| `mirror_rA_rB_rho_diff` | Mirror pairs about the 12–6 axis (1–11, 2–10, 4–8, 5–7): radial symmetry |
| `b3_b9_angle_dev_deg`, `b3_b9_rho_diff`, `b3_b9_line_centre_offset_r` | The 3-to-9 relationship |
| `t12_b6_angle_dev_deg`, `t12_b6_line_centre_offset_r` | Triangle-to-6 alignment |
| `line12_6_vs_line3_9_orthogonality_deg` | Whether the 12–6 line is square to the 3–9 line |
| `baton_minus_round_rho` | Median baton ρ minus median round ρ |
| `track_minus_round_ring_r`, `track_minus_baton_rho_r` | Minute-track inner radius minus the marker radii |

Pose covariates, per photo:

- `dial_axis_ratio`;
- `mpose_tilt_deg`, the inferred tilt;
- `inplane_rotation_deg`, the image clock angle of the triangle centre about the dial centre;
- `dial_r_px`, the dial size in pixels.

The synthetic 88% rescale stands in for image scale.

## 5. Variance decomposition and classes

For each metric:

| Spread | Definition |
|---|---|
| Perturbation | Range and MAD of one photo's value over its usable variants (94% and 88%, ±1 and ±2% shifts, ±5°), with the median and p90 over photos. Variants are analysed from scratch and mapped back to original pixels, so this is detector and sampling sensitivity, not pose. |
| Within-watch | MAD of a watch's original-photo values (photo-to-photo: pose, light, hands, detector), summarised as the median over watches with two or more photos. Range is reported as well. |
| Between-watch | MAD, range and median of the watch medians. |

Angles are unwrapped about a reference before any spread is taken.

The **usefulness ratio** is between-watch MAD / median within-watch MAD. It is descriptive only.

| Class | Condition |
|---|---|
| Insufficient data | Fewer than 5 watches, or fewer than 2 watches with two or more photos |
| Measurement noise dominates | Ratio < 1 |
| Similar scale | Ratio 1–2 |
| Between-watch variation clearly exceeds measurement noise | Ratio ≥ 2 |

The cut-offs at 1 and 2 only sort a descriptive table. They are not limits on a watch.

**Pose dependence** is tested in two ways:

1. **Within-watch centred Spearman correlation.** Each watch's median is subtracted from both the metric
   and the covariate, so differences between watches cannot create a correlation. A permutation p-value
   (2000 permutations) is computed. A metric is flagged when |ρ| ≥ 0.5, n ≥ 10 and p < 0.05.
2. **Synthetic flag.** A metric is also flagged when the median systematic change under the 88% rescale,
   or the median absolute change under ±5° rotation, exceeds half the within-watch MAD.

## 6. Results

Of 134 metrics:

| Class | Metrics |
|---|---|
| Between-watch clearly exceeds noise | 5 |
| Similar scale | 23 |
| Noise dominates | 70 |
| Insufficient data | 36 |
| Descriptive only | 5 |

The full table is in `geometry/sub124060_geometry_summary.md`.

### Promising (between-watch spread at least at the scale of photo-to-photo noise)

| Metric | Watches (2+ photos) | Within MAD | Between MAD | Ratio | Caveat |
|---|---|---|---|---|---|
| `r1_inset` | 7 (4) | 0.0045 | 0.0247 | 5.5 | Perturbation range (0.030) exceeds the within spread, and the flags for 88% scale and ±5° rotation are both set. Inset is a fitting quantity, so treat this one with suspicion. |
| `r4_fitted_edge_radius_r` | 8 (4) | 0.0011 | 0.0038 | 3.6 | Not pose-flagged. Which edge (lume or surround) is fitted may differ between watches. |
| `r10_fitted_edge_radius_r` | 9 (4) | 0.0029 | 0.0066 | 2.3 | Correlates with dial size in pixels (ρ −0.85), which points to resolution. |
| `r10_dtheta_deg` | 9 (3) | 0.12° | 0.25° | 2.1 | Mildly sensitive to ±5° rotation. |
| `track_spread_r` | 9 (7) | 0.022 | 0.045 | 2.1 | A spread of tick ends, so likely to reflect image quality rather than the watch. Correlates with axis ratio. |
| `ring_round_circle_centre_offset_r` | 9 (4) | 0.0008 | 0.0015 | 1.8 | Pose-sensitive (axis ratio, tilt). |
| `t12_surround_rotation_deg` | 9 (4) | 0.56° | 0.92° | 1.6 | Correlates with in-plane rotation (ρ −0.89, n 10). |
| `t12_surround_gap_to_track_r` | 9 (4) | 0.0024 | 0.0032 | 1.3 | Synthetic scale and rotation flags are set. |
| `r1_fitted_edge_radius_r`, `r11_inset`, `r7_rho`, `r1_rho`, `r10_rho`, `ring_round_rho_mad`, `ring_round_mean_angle_offset_deg`, `opp_r4_r10_rho_diff`, round-marker gaps | | | | 1.0–1.5 | Similar scale. |

Two points to keep in view:

- Even the "promising" metrics have very few within-watch pairs: three or four watches. A single
  outlying photo moves the class.
- Individual round markers are mostly measured on only some photos, because of hands and the reject
  rule. The relational ring metrics combine several markers, but most of them were still
  noise-dominated.

### Dominated by noise (ratio < 1)

At this sample size, photo-to-photo variation on one watch is as large as, or larger than, the
differences between genuine watches:

- **Triangle:** `t12_surround_rho`, `width_r`, `height_r`, `apex_deg`, `dtheta_from_60tick_deg` and
  `lateral_offset_from_60tick_w`. The within-watch apex MAD is 2.15° against a between-watch MAD of
  0.26°.
- **Batons:** nearly all of them (ρ, dtheta, length, width, rotation, inset, gap). The worst are
  `b9_dtheta_deg` (within 2.96°), `b6_dtheta_deg` (1.89°) and `b6_rotation_deg` (3.1°).
- **Relational:** `t12_b6_angle_dev_deg` and `t12_b6_line_centre_offset_r`; the opposite-pair angles;
  most mirror and opposite radial differences; `ring_round_rho_median`, `ring_round_circle_radius_r`
  and `ring_round_spacing_rms_deg`; `baton_minus_round_rho` and `track_minus_*`.
- **Dial and track:** `track_radius_r`, `track_pitch_deg`, `dial_axis_ratio`, `mpose_tilt_deg`, and all
  rehaut widths except `rh_w3`.

Across all of these, the perturbation spread is usually several times *smaller* than the within-watch
spread. The noise comes from the photographs (pose, lighting, hands, compression), not from the
detector's sampling.

### Pose-sensitive

These flags come from within-watch-centred correlations or synthetic shifts. Most correlations rest on
10–17 centred points from 4–7 watches. 134 metrics were each tested against 4 covariates, so a few
flags are expected by chance.

- **Triangle against in-plane rotation:** `t12_surround_width_r` (ρ +0.88), `apex_deg` (+0.95),
  `rotation_deg` (−0.89), `dtheta_from_60tick_deg` (+0.83), `rho` (+0.68) and
  `lateral_offset_from_60tick_w` (+0.70). `apex_deg` and `rho` also correlate with dial px.
  - This fits a perspective or lighting effect on the triangle's outline. The ±5° synthetic rotation
    does *not* reproduce it (shifts of about 0.06°), so it is not a sampling artefact of the image
    grid.
- **Baton at 6** (`b6_rho`, `length_r`, `width_r`): against in-plane rotation, tilt and dial px.
- **Round-marker ring against tilt:** `ring_round_circle_radius_r` (−0.95), `ring_round_rho_mad`
  (+0.90), `track_minus_round_ring_r` (+0.98), `ring_round_circle_centre_offset_r` and
  `ring_round_rho_median`.
  - Rectifying with the dial ellipse does not remove tilt fully. This is expected if the markers sit
    in a different plane from the dial edge that was fitted, which is a purely geometric effect.
- **Single round markers:** `r4_rho` (tilt −0.92), `r11_rho` (axis ratio −0.82, rotation −0.85),
  `r10_rho`, `r10_inset`, `r10_gap`, `r4_inset`, `r11_gap`.
- **Opposite pairs:** `opp_r4_r10_angle_dev_deg` (axis ratio +0.88) and `opp_r4_r10_rho_diff`.
- **Resolution:** `r10_` and `r11_fitted_edge_radius_r` against dial px. `dial_fit_rms_over_r` is
  expected to fall with resolution.
- **Rehaut:** all of it (tilt, dial px, axis ratio). It stays descriptive.
- **Synthetic flags** (88% rescale and/or ±5° rotation moves the value by more than half the
  within-watch MAD): `r1_inset`, `r1_dtheta_deg`, `r1_rho`, `r2_inset`, `r4_inset`, `r11_inset`,
  `r5_gap`, `r10_dtheta_deg`, `b3_rotation_deg`, `b9_rotation_deg`, the b3–b9 relations,
  `t12_surround_gap_to_track_r` and `ring_round_mean_angle_offset_deg`.

**No pose correction was applied.**

- The candidate geometric correction is a tilt-aware marker plane: the rectified frame scaled by the
  depth offset of the marker plane.
- It could be validated without genuine-vs-replica data, using the within-watch spread alone. With 4–7
  multi-photo watches, though, it would be fitted and judged on the same handful of pairs. It is left
  for a larger development set.

## 7. Limits from sample size

- **Small numbers.** There are 9 physical watches. Only 7 have two or more usable photos, and for most
  individual markers only 2–5 watches do. A MAD over 9 watch medians, or a median over 3–4 within-watch
  MADs, is a rough estimate. Ratios between 0.7 and 1.5 should be read as "unknown", not as a ranking.
- **Single source.** All watches come from one dealer (Bob's Watches) with a similar photographic style.
  Within-watch spread from other sources (forums, owners' photos) may be larger, and the between-watch
  spread may include dealer-batch effects.
- **No lume pairs.** Lume and surround radii of round markers, and every `t12_lume_*` metric, have no
  within-watch pairs. Their repeatability is unknown.
- **Few pairs on some markers.** The 3–9 baton relations rest on one multi-photo watch. r2 is measured
  on only 4 watches; it is crossed by hands on many photos.
- **Remaining contamination.** Some within-watch spread may still come from unflagged partial
  occlusion, or from the most oblique photos (axis ratios 0.94–0.96: `6a4ce9c64f`, `32736f25fb` and
  `98dd3ab2d8`). This would inflate the noise estimates.
- **Not a population model.** Nothing here describes the genuine population beyond these 9 watches.

## 8. Why no QC thresholds were derived

The study asks whether a measurement *repeats*, not where a genuine watch *ends*.

- **Too few watches for limits.** A threshold would need a population model of genuine watches, which 9
  watches from one dealer cannot give.
- **Separation was never examined.** A threshold would need validated separation from replicas, and
  this study deliberately did not look at replicas.
- **Classes are not limits.** The ratio classes describe the relative size of two spreads in this
  sample. They are not limits on any watch, and no watch was ranked or scored.
- **Next steps.** Any later QC research should:
  - start from the metrics that are not noise-dominated, after controlling for pose;
  - be designed on development data;
  - be checked once on validation;
  - touch the holdout only at the end;
  - count everything by `physical_watch_id`.
