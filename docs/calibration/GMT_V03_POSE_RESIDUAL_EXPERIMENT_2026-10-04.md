# GMT v0.3 pose-residual experiment — 2026-10-04

Status: **research only**. No production code, APK, CI or calibration threshold changes.

## Purpose

Test the v0.3 calibration idea using evidence already in the repository: photographic perspective should create an opposing, spatially coherent pattern around the dial. An isolated bad marker must not be excused as perspective.

This experiment reuses historical measurements from the same proven-genuine candidate watch, `gen_wex_3KSuGhC`, so physical watch geometry is held constant while photographic pose changes.

## Governing rule

For marker residuals around the dial, consider three components:

1. **common-mode** — a near-uniform shift caused by frame/edge/reference placement;
2. **directional pose field** — a smooth change with dial angle, expected to reverse across opposing regions and be shared by neighbouring markers;
3. **local residual** — an isolated departure after the first two components are accounted for; this remains a QC-defect or local-measurement candidate.

Do not call a single marker perspective merely because its opposite differs. Perspective requires coherent neighbouring support.

## Evidence

Historical Phase-A work selected `gen_wex_3KSuGhC/image_04.jpg` as the strongest near-frontal actual-watch image in its tranche. `image_00` was a mildly angled runner-up and `image_02` had appreciably stronger tilt.

The existing round-marker local-gap values were compared against image_04. These legacy gaps are not the new common physical 12↔6 gap definition; they are useful here only as an already-measured residual field around the same physical dial.

### image_04 reference values

| hour | local gap |
|---:|---:|
| 1 | 0.1210 |
| 2 | 0.1169 |
| 4 | 0.1260 |
| 5 | 0.1228 |
| 7 | 0.1199 |
| 8 | 0.1314 |
| 10 | 0.1326 |
| 11 | 0.1423 |

Existing 12-triangle gap: **0.0922**.

### image_00 residuals versus image_04

| hour | residual |
|---:|---:|
| 1 | +0.0075 |
| 2 | +0.0060 |
| 4 | +0.0061 |
| 5 | +0.0059 |
| 7 | +0.0083 |
| 8 | +0.0050 |
| 10 | +0.0036 |

Summary:
- mean residual: **+0.00606**
- median residual: **+0.0060**
- residual SD: **0.00154**
- first-harmonic directional amplitude: **~0.00093**
- first-harmonic R²: **~0.184**
- 12-triangle residual: **+0.0100**

Interpretation: this image is dominated by a common-mode shift rather than a strong directional perspective field. Removing the round-marker common mode from the 12 residual leaves roughly **+0.004**, reducing the apparent discrepancy by about 60% without invoking a marker-specific excuse.

### image_02 residuals versus image_04

| hour | residual |
|---:|---:|
| 1 | -0.0019 |
| 2 | -0.0352 |
| 4 | -0.0443 |
| 5 | -0.0120 |
| 7 | +0.0443 |
| 8 | +0.0318 |
| 10 | +0.0541 |
| 11 | +0.0231 |

Summary:
- mean residual: **+0.00748**
- residual SD: **0.03569**
- first-harmonic directional amplitude: **~0.0460**
- first-harmonic RMSE: **~0.01044**
- first-harmonic R²: **~0.907**
- 12-triangle gap: **0.1285** versus **0.0922** on image_04

Interpretation: the more tilted image produces a strong, smooth directional field around the same physical genuine dial. Neighbouring markers move together and the sign reverses across the dial. This is exactly the type of evidence that should be treated as photographic perspective rather than allowed to widen genuine manufacturing tolerance.

## Result

**SUPPORTED.** The core v0.3 idea is strongly supported on this same-watch control: substantial pose produces a spatially coherent, approximately low-order residual field, while a milder view is largely common-mode in the legacy local measurements.

This does **not** justify correcting any isolated marker. The correction/withholding rule must require regional coherence.

## Important measurement-definition issue

The existing production outputs cannot yet be used for a literal 12↔6 equality test because the two stored gaps use different denominators:

- 12 gap is normalised by the 12-triangle width;
- 6 gap is normalised by the 6-baton width.

The 6 analyzer internally calculates its gap but the historical batch CSV does not export that field.

For the direct opposing-gap test, convert both to one physical definition:

- `g12_px = twelve_gap * triangle_width_px`
- `g6_px = six_gap * six_width_px`
- `g12_R = g12_px / dial_radius_px`
- `g6_R = g6_px / dial_radius_px`

Then compare `g12_R` and `g6_R`, and use neighbouring positions to confirm any perspective field.

## Lessons learned

1. **Pair symmetry alone is not sufficient to choose the master.** Use multiple pairs plus image quality and neighbourhood coherence.
2. The useful model is likely `residual = common_mode + directional_pose + local_residual`.
3. Common-mode shifts should be separated from directional perspective before estimating genuine manufacturing variation.
4. A first-harmonic/dipole model is a promising simple description of pose, but it must remain a research model until validated on more genuine images.
5. Legacy local-gap metrics can already reveal the spatial pose pattern, but the direct 12↔6 test must use a shared physical gap definition.
6. Do not alter `docs/CALIBRATION_PROTOCOL.md` during this experiment. These lessons should be promoted only after the direct shared-definition test and a fresh/held-out genuine check.

## Next experiment

Use an **existing corpus image**, not a new search. Prefer a high-resolution genuine GMT already accepted in the project (for example SWE 60177 or another direct-source image). Measure 12 and 6 with the same physical radial-gap definition, then test one more angled genuine image and inspect neighbouring markers. Keep the experiment offline and do not change production code or thresholds.
