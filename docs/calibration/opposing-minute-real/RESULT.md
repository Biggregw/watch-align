# Opposing minute-marker real-photo control

Status: **research only; no production GMT change**

- source watches attempted: **6**
- watches with a measurable opposing-pair field: **2**
- measurable images: **3 / 33**
- high-coherence images: **0**

## Watch-level result

One image per physical watch is selected only for this summary, preferring the most complete pair set and then the lowest residual. All image-level data remain in `image_results.csv`.

- median reduction in opposing-pair radial asymmetry: **19.3%**
- median projective-fit residual: **0.02049**
- median fitted projective magnitude: **0.03328**

| watch | split | pairs | residual | raw asym | corrected asym | improvement |
|---|---|---:|---:|---:|---:|---:|
| gen_wex_3KSuGhC | calibration | 15 | 0.02258 | 0.05666 | 0.04372 | 22.8% |
| gen_wex_1TDYtpN | validation | 21 | 0.01841 | 0.03984 | 0.03354 | 15.8% |

## Calibration partition

- measurable watches: **1**
- median pair-asymmetry improvement: **22.8%**

## Validation partition

- measurable watches: **1**
- median pair-asymmetry improvement: **15.8%**

## Gate

This run tests only whether the minute-pair field is measurable and spatially coherent on real genuine photographs. It does **not** yet authorize correction of the 12-marker QC measurement. The next gate is whether using this field reduces the spread of an independent genuine landmark measurement on validation watches without absorbing an intentionally local defect.
