# Alpha91 final bounded automatic-detector test — 2026-10-05

Research only. Alpha90 production code remains unchanged.

## Agreed stopping rule

Before investing further in automatic minute-track fitting, freeze the detector family and require:

- 5 genuine GMT photos
- no per-photo tuning
- at least 4/5 photos with <= 1.5 px mean non-triangle holdout error
- no catastrophic sector drift

If repeated 4–8 px failures remain on otherwise reasonable genuine photos, stop investing in the fully automatic minute-track detector rather than tuning around individual images.

## Evidence entering this final check

Best frozen/global-sequence results already obtained on the four genuine controls with independent marker holdouts:

- `POOL_GEN_HO_02`: ~1.13 px mean
- `EXT_EXT_GEN_BLRO_WEX_02`: ~1.58 px mean
- `POOL_GEN_HO_01`: ~2.03 px mean after global ordered-sequence improvement
- `EXT_EXT_GEN_BLRO_WEX_01`: still catastrophic, ~7.24 px mean in the global-sequence attempt

The flat-homography oracle remains much better on the same photographs, including approximately 0.39 px mean on `POOL_GEN_HO_02`, ~1.11 px on `EXT_EXT_GEN_BLRO_WEX_02`, ~1.31 px on `EXT_EXT_GEN_BLRO_WEX_01`, and ~1.90 px on `POOL_GEN_HO_01` with one isolated annotation/outlier sector. This continues to support a 2D planar model and identifies automatic correspondence recovery as the bottleneck.

## Final frozen detector-family attempt

A stricter global assignment variant was tried without per-photo tuning:

1. coarse ellipse/conic rectification;
2. white top-hat / local-contrast extraction in the minute annulus;
3. connected radial-component filtering by radius and orientation;
4. one-to-one global assignment of minor-minute components to the ordered canonical tick sequence;
5. one MAGSAC homography from the assigned minute correspondences;
6. applied markers remain holdout only.

This variant did not rescue the generalisation problem. Representative non-triangle holdout means on the same controls were approximately:

- `EXT_EXT_GEN_BLRO_WEX_01`: 2.64 px
- `EXT_EXT_GEN_BLRO_WEX_02`: 2.72 px
- `POOL_GEN_HO_01`: 5.53 px
- `POOL_GEN_HO_02`: 10.46 px

This attempt is worse than the best previous sequence method on some controls, but that is itself informative: multiple reasonable detector formulations remain sensitive to lighting, blur, hand/reflection structure and exact tick appearance.

## Decision

**STOP the current fully automatic minute-track-detector research path.**

The agreed 4/5 criterion is already mathematically impossible once at least two genuine controls exceed the threshold, so testing a fifth photo cannot rescue this round. Continuing to tune would violate the pre-agreed guardrail and risks overfitting individual photographs.

This does **not** reject the flat 2D homography architecture. The oracle experiments show that a single planar homography can align genuine dial geometry to roughly sub-pixel / low-pixel accuracy when reliable correspondences are supplied.

What has failed is the assumption that the current image-only minute-track detector can recover those correspondences robustly enough across ordinary QC-photo conditions.

## Recommended next product decision

The next work should not be another threshold/detector tuning cycle. Choose between:

1. **minimal user-assisted anchoring**: ask for a tiny number of trusted dial-plane anchors, then let the app solve/refine the homography automatically; or
2. a materially different correspondence source (for example a learned/keypoint model), only if there is a strong reason to keep the process fully automatic.

Given the project goal and time spent, minimal user assistance is the lower-risk next experiment.

Do not reintroduce depth, bezel geometry, sapphire modelling or marker-driven fitting as a response to this detector failure.