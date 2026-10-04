# Opposing minute-marker real-photo control

Status: **research only; no production GMT change**

Revision: self-phasing minute track; even/odd opposing-pair cross-validation.

- source watches successfully fetched: **8**
- watches with independently cross-fittable opposing pairs: **7**
- measurable images: **28 / 44**
- descriptively coherent images: **7**

## Watch-level cross-validation

Each pair is corrected only by a projective vector fitted to the opposite parity of pairs. The evaluated pair therefore did not train its own correction.

- median cross-fitted reduction in opposing-pair radial asymmetry: **-1.2%**
- median even/odd projective-vector disagreement: **0.01814**
- median fitted projective magnitude: **0.01983**

| watch | split | pairs | vector disagree | raw asym | cross-fit corrected | cross-fit improvement | status |
|---|---|---:|---:|---:|---:|---:|---|
| gen_wex_3KSuGhC | calibration | 25 | 0.01001 | 0.02967 | 0.03002 | -1.2% | MEASURED |
| gen_wex_K4gqk6U | calibration | 17 | 0.05102 | 0.06061 | 0.07188 | -18.6% | MEASURED |
| gen_wex_e99gXKb | calibration | 27 | 0.00548 | 0.03297 | 0.02483 | 24.7% | COHERENT |
| gen_wex_1TDYtpN | validation | 26 | 0.01063 | 0.04073 | 0.04431 | -8.8% | MEASURED |
| gen_wex_8Rg3vqJ | validation | 28 | 0.02810 | 0.05679 | 0.05469 | 3.7% | MEASURED |
| gen_wex_bRffRhD | validation | 23 | 0.01814 | 0.05241 | 0.06135 | -17.1% | MEASURED |
| gen_wex_vmbUDwy | validation | 24 | 0.02193 | 0.03884 | 0.02762 | 28.9% | MEASURED |

## Calibration partition

- measurable watches: **3**
- median cross-fit improvement: **-1.2%**

## Validation partition

- measurable watches: **4**
- median cross-fit improvement: **-2.5%**

## Unassessable-image reasons

- insufficient independent even/odd opposing-pair coverage: **7**
- outer ellipse not stable: **5**
- 60-fold minute-track phase not found: **4**

## Gate

This run does not authorize a perspective correction. A useful result requires the cross-fitted improvement to persist on independent validation watches and the even/odd fitted projective vectors to agree. Only after that should an independent landmark such as a common-normalized 12/6 clearance be corrected, followed by an intentional local-defect test to prove the pose model does not absorb a genuine marker error.
