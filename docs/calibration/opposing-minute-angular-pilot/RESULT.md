# Opposing minute angular homography pilot

Status: **research only; no production change**

This pilot replaced the failed radial tick-end ratio with the angular positions of detected minute ticks after ellipse normalisation. Even and odd opposing pairs were cross-fitted, so no evaluated pair trained the homography used to score that pair.

Workflow run: `37224591725`.

## Pilot coverage

- source watches fetched: **3**
- measurable images: **14 / 24**
- descriptively coherent images: **4**

## Watch-level held-out result

The watch-level summary selected one image per physical watch by pair coverage and then model agreement, not by correction improvement.

- median cross-fitted improvement: **-17.7%**
- median raw opposing angular error: **1.020°**
- median corrected opposing angular error: **1.105°**
- median even/odd homography disagreement: **0.696°**

| watch | split | pairs | raw error | corrected error | improvement | model disagree | status |
|---|---|---:|---:|---:|---:|---:|---|
| `gen_wex_e99gXKb` | calibration | 27 | 0.920° | 1.105° | **-20.1%** | 0.524° | MEASURED |
| `gen_wex_1TDYtpN` | validation | 26 | 1.020° | 0.949° | **+7.0%** | 1.408° | MEASURED |
| `gen_wex_vmbUDwy` | validation | 24 | 1.050° | 1.236° | **-17.7%** | 0.696° | MEASURED |

### Calibration partition

- measurable watches: **1**
- median held-out improvement: **-20.1%**

### Validation partition

- measurable watches: **2**
- median held-out improvement: **-5.4%**

## Image-level behaviour

The result was not merely a weak average. Different photos of the same genuine watch frequently changed sign, which is incompatible with a stable camera-pose correction signal.

Examples from the completed run:

- `gen_wex_e99gXKb`: -20.1%, +15.4%, -24.0%, +13.8%
- `gen_wex_1TDYtpN`: +7.0%, +3.5%, +24.4%, -3.8%, +1.4%
- `gen_wex_vmbUDwy`: -13.1%, -71.1%, -0.1%, -17.7%, +47.1%

## Unassessable-image reasons

- fewer than 12 complete opposing pairs: **5**
- 60-fold minute-track phase not found: **4**
- independent angular homography fit failed: **1**

## Decision

**FAIL as an automatic perspective-correction method.**

The earlier radial opposing-minute method also failed held-out validation. Replacing radial endpoints with angular tick positions did not rescue the approach. On the bounded pilot, the angular correction made the selected watch-level result worse overall and did not improve both validation watches.

Therefore opposing minute markers should **not** currently be used to derive a projective correction or alter production GMT measurements.

They may still be useful as a **photo-quality / perspective-coherence diagnostic** because strong disagreement between opposite pairs or between independent half-pair models can identify a photo whose fine geometry should not be trusted.

## Recommended next experiment

Return to the local, feature-specific experiment already identified by GMT calibration v0.3:

1. express the 12 and 6 clearances in the **same dial-radius units**;
2. compare the direct `12 ↔ 6` relationship on genuine photos;
3. require neighbouring corroboration around 11/1 and 5/7;
4. use opposing minute evidence only as a confidence/exclusion signal, not as a correction formula;
5. only correct a local 12 measurement if direct same-definition opposite evidence reduces genuine repeatability spread on held-out watches and leaves an intentional local defect uncorrected.

No production threshold, APK behaviour, or main-branch code was changed by this experiment.
