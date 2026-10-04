# GMT v0.3 pose-residual experiment — 2026-10-04

Status: **research only**. No production code, APK, CI or calibration threshold changes.

## Purpose

Test the v0.3 calibration idea using evidence already in the repository: photographic perspective should create an opposing, spatially coherent pattern around the dial. An isolated bad marker must not be excused as perspective.

This experiment reuses historical measurements from the same proven-genuine candidate watch, `gen_wex_3KSuGhC`, so physical watch geometry is held constant while photographic pose changes.

## Governing rule

For marker residuals around the dial, consider three components:

1. **common-mode** — a near-uniform shift caused by frame/reference placement;
2. **directional pose field** — a smooth change with dial angle, expected to reverse across opposing regions and be shared by neighbouring markers;
3. **local residual** — an isolated departure after the first two components are accounted for; this remains a QC-defect or local-measurement candidate.

Do not call a single marker perspective merely because its opposite differs. Perspective requires coherent neighbouring support.

## Evidence

Historical Phase-A work selected `gen_wex_3KSuGhC/image_04.jpg` as the strongest near-frontal actual-watch image in its tranche. `image_00` was a mildly angled runner-up and `image_02` had appreciably stronger tilt.

The existing round-marker measurements were compared against image_04. The most useful quantity is **centre-based inset**, matching the lesson already recorded in `GMT_V03_CONTROL_2026-10-04.md`: it is less dependent on which lume/surround edge is traced than outer-edge gap.

### Centre-based inset: image_04 reference

| hour | inset |
|---:|---:|
| 1 | 0.5887 |
| 2 | 0.5861 |
| 4 | 0.5859 |
| 5 | 0.5882 |
| 7 | 0.5869 |
| 8 | 0.5923 |
| 10 | 0.5972 |
| 11 | 0.6081 |

### Centre-based inset: image_00 residuals versus image_04

Clean common hours: 1, 2, 4, 5, 7, 8, 10.

Residuals: **+0.0076, +0.0074, +0.0130, +0.0013, +0.0040, +0.0037, +0.0052**.

Summary:
- mean/common shift: **+0.00603**
- median: **+0.0052**
- first-harmonic directional amplitude: **~0.00268**
- first-harmonic R²: **~0.336**
- residual RMSE after common + directional fit: **~0.00285**

Interpretation: this mildly different view is dominated by a small common-mode shift. There is little coherent directional distortion.

### Centre-based inset: image_02 residuals versus image_04

Clean common hours: 1, 2, 4, 5, 7, 8, 10. Hour 11 was excluded because the stored measurement marks a hand beside it.

Residuals: **+0.0051, -0.0425, -0.0427, -0.0035, +0.0480, +0.0321, +0.0438**.

Summary:
- mean/common shift: **+0.00576**
- first-harmonic directional amplitude: **~0.04454**
- first-harmonic R²: **~0.850**
- residual RMSE after common + directional fit: **~0.01366**

Interpretation: the more tilted image produces a strong smooth directional field on the **same physical genuine dial**. Hours 2/4 move strongly one way, 7/8/10 move strongly the other way, and neighbouring markers agree. This is the exact signature required before a deviation may be attributed to perspective.

## Cross-check with legacy outer-edge local gap

The same-watch outer-edge round-marker gap values tell the same story, although they are a noisier quantity:

- image_00 versus image_04: directional amplitude **~0.00093**, R² **~0.184**;
- image_02 versus image_04: directional amplitude **~0.0460**, R² **~0.907**.

The agreement between the centre-based and outer-edge views on the strongly tilted image makes the pose result materially stronger than relying on either metric alone.

The 12-triangle gap itself changed from **0.0922** on image_04 to **0.1022** on image_00 and **0.1285** on image_02. The production 12-gap metric is not in the same units as the round-marker inset, so the harmonic field must not be used as a numerical 12 correction yet.

## Result

**SUPPORTED.** The core v0.3 idea is strongly supported on a same-watch genuine control:

- mild view change: mostly common-mode, very small directional amplitude;
- stronger tilt: large coherent directional field with opposite regions moving in opposite directions and neighbours moving together.

This is better evidence than comparing different watches because genuine manufacturing geometry is held constant.

This still does **not** justify correcting an isolated marker. A marker may only be treated as perspective-contaminated when its local value participates in the coherent regional field.

## Important measurement-definition issue

A literal 12↔6 equality test still cannot be recovered directly from the historical summary CSV because the production gaps use different denominators:

- 12 gap is normalised by the 12-triangle width;
- 6 gap is normalised by the 6-baton width.

The 6 analyzer internally calculates its gap but the historical batch CSV does not export that field.

For the direct opposing-gap test, put both into one physical definition:

- `g12_px = twelve_gap * triangle_width_px`
- `g6_px = six_gap * six_width_px`
- `g12_R = g12_px / dial_radius_px`
- `g6_R = g6_px / dial_radius_px`

Then compare `g12_R` and `g6_R`, with neighbouring positions confirming the regional trend.

## Additional calibration consequence

This result changes how genuine calibration outliers should be handled. A genuine image with an extreme feature reading should not automatically widen the genuine manufacturing envelope. First ask whether the rest of the dial shows a coherent pose field.

- coherent opposite/nearby trend -> mark that image/feature as pose-contaminated or correct only after a validated same-definition model exists;
- isolated feature departure with normal neighbours -> **do not call it perspective**; investigate local detection and, if measurement is sound, retain it as possible real genuine variation.

This is especially important because the earlier v0.3 control found globally symmetric genuine photos with materially different 12-gap readings. Global frontalness cannot erase a local anomaly.

## LESSONS LEARNED

1. **Centre-based inset is the better existing pose signal.** The same-watch test supports the earlier v0.3 control conclusion.
2. **The useful decomposition is `common_mode + directional_pose + local_residual`.** This is simpler and more explainable than attempting to infer a full camera homography.
3. **Neighbourhood coherence is essential.** Opposing movement plus nearby markers sharing the trend is what distinguishes perspective from a single bad marker.
4. **Same-watch multi-view evidence is extremely valuable.** It isolates photographic effects from Rolex manufacturing variation and should be used wherever available.
5. **A first-harmonic/dipole field is a promising compact pose descriptor**, but it remains a research model until it survives additional genuine validation.
6. **Do not numerically correct the 12 gap from round-marker inset.** The units/features differ; the direct 12↔6 common-definition test is still required.
7. **Do not alter `docs/CALIBRATION_PROTOCOL.md` yet.** Promote these lessons only after the shared-definition 12↔6 test and a separate validation check.

## Next experiment

Use an **existing corpus image**, not a new search. Recover or remeasure 12 and 6 with one common radial-gap definition. Then test a near-frontal and a more angled genuine example, requiring neighbouring-marker coherence. Keep the work offline and do not change production code or thresholds.
