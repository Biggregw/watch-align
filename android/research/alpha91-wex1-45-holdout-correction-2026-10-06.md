# Alpha91 WEX1 4/5 residual diagnosis correction — 2026-10-06

Research only. Alpha90 production remains unchanged.

## Why this check was done

The previous residual-vector pass found a common apparent down/right residual and a remaining WEX1 4/5 sector anomaly after translation correction. This note checks whether that anomaly belongs to the homography or to the holdout measurements.

## Key observation

In EXT_EXT_GEN_BLRO_WEX_01, the 4 o'clock round marker is heavily covered by the minute hand and the 5 o'clock round marker is crossed by another hand. Their original holdout centres were therefore not reliable independent ground truth.

## Consistent remeasurement

A single constrained robust circle-fit was applied to all eight round hour markers (1,2,4,5,7,8,10,11) in the original candidate image. The same radius constraint and fitting method was used for every round marker; 4/5 were not tuned separately.

The original holdout centres for the clear round markers were themselves systematically about 1 px down/right of the robust ring centres. That bias closely matches the previously inferred global +1.3,+1.0 px 'registration correction'. Therefore the proposed fixed translation correction was largely compensating for holdout-label bias, not a real transform bias.

Using the automatic dense-reference homography exactly as previously computed, with NO fixed translation correction, errors against the remeasured round-marker centres are:

| Hour | error px |
|---:|---:|
| 1 | 0.68 |
| 2 | 0.66 |
| 4 | 2.01 |
| 5 | 0.72 |
| 7 | 0.21 |
| 8 | 0.22 |
| 10 | 0.12 |
| 11 | 0.38 |

Hour 4 remains unsuitable as precision ground truth because the physical ring is severely occluded by the hand; its robust fit is visibly less trustworthy than the other seven.

Excluding only that severely occluded hour-4 holdout, WEX1 round-marker validation is:

- mean: **0.43 px**
- median: **0.38 px**
- max: **0.72 px**

Hour 5, which had previously appeared to be a ~4 px sector failure after the translation correction, remeasures at **~0.72 px** against the unchanged automatic homography.

## Cross-check on other genuine controls

Applying the same robust round-marker remeasurement method, again without adding the fixed translation correction:

- WEX2: ~0.61 px mean across eight round markers, max ~1.07 px.
- HO2: ~0.80 px mean across eight round markers; a couple of locally weak/occluded fits remain around 1.5 px.
- HO1: ~1.32 px mean across eight round markers, with individual weak/occluded fits still needing inspection.

These figures indicate that the old holdout-centre extraction was materially contributing to the previously reported 1.7–2.6 px means.

## Corrected interpretation

1. The WEX1 4/5 pattern is primarily a holdout-measurement/occlusion problem, not evidence of a localized projective warp failure.
2. Do NOT add the previously proposed global +1.3,+1.0 px calibration translation to production.
3. The fully automatic dense-reference 2D homography is already sub-pixel on the reliable WEX1 round-marker holdouts and about sub-pixel to ~1 px on WEX2/HO2 round-marker checks.
4. Future validation must use occlusion-aware marker ground truth: reject heavily covered markers and fit visible metal-surround arcs consistently rather than trusting a generic centre detector.

## Next decision

Before any APK, re-score all genuine controls with the corrected occlusion-aware holdout method, including batons/triangle only where their geometry is independently measurable. If the unchanged automatic homography remains <=1 px mean on reliable holdouts across the control set, the alignment stage is ready for an Android prototype.