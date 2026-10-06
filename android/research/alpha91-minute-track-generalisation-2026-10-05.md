# Alpha91 minute-track generalisation check — 2026-10-05

Research only. Alpha90 production code is unchanged.

## Question

Does the minute-track-only 2D homography approach that worked on `POOL_GEN_HO_02` generalise unchanged to other genuine GMT photos at different image conditions?

## Result

The answer is **not yet**.

The flat-homography model itself remains supported by the earlier oracle test: the same genuine controls can be explained by a single planar homography with low-pixel holdout error when reliable correspondences are supplied.

The weak point is the automatic minute-track localisation.

Using the same calibrated minute-track geometry and orientation-filtered radial-edge detector family without per-photo tuning:

- `EXT_EXT_GEN_BLRO_WEX_02`: about **1.48 px mean**, **1.47 px median**, **2.78 px max** non-triangle marker error.
- `EXT_EXT_GEN_BLRO_WEX_01`: about **4.64 px mean**, **4.31 px median**, **9.47 px max**.
- `POOL_GEN_HO_01`: about **5.62 px mean**, **3.84 px median**, **12.46 px max**.

The previously documented `POOL_GEN_HO_02` sub-pixel pass remains about **1.13 px mean**.

These failures are sector-dependent rather than a uniform scale/rotation error. Visual inspection shows the failing photos differ in lighting, blur, hand interference and local tick contrast. The detector sometimes selects nearby radial/reflection structure as the minute-tick evidence and then MAGSAC fits a self-consistent but wrong local geometry.

## Global 60-tick sequence follow-up

A second bounded experiment replaced independent local tick choices with one ordered minute-track sequence in a polar/annular representation. Peaks were constrained to preserve circular order and near-6-degree spacing, then radial endpoints were estimated and one homography fitted from the resulting sequence.

This helped materially on two controls:

- `EXT_EXT_GEN_BLRO_WEX_02`: about **1.58 px mean**, **1.44 px median**, **3.34 px max**.
- `POOL_GEN_HO_01`: about **2.03 px mean**, **1.72 px median**, **6.76 px max**.

However `EXT_EXT_GEN_BLRO_WEX_01` still failed at about **7.24 px mean**. Inspection showed that its global sequence can choose the wrong nearby minute identity / radial endpoint under the particular reflection and hand pattern even while preserving a superficially regular 60-tick sequence.

So global ordering is necessary, but not sufficient by itself.

## Important interpretation

This **does not reject the 2D planar approach**.

The earlier oracle already demonstrated that these same genuine photos admit a low-error planar homography. Therefore the current generalisation failure is a detector/feature-assignment problem, not evidence that depth, sapphire refraction or a 3D camera model is required.

## What should change next

Do not tune thresholds separately for each photo.

The next solver should keep the global sequence idea but strengthen tick identity and endpoint evidence:

1. rectify coarsely by the dial conic;
2. construct one polar/annular representation of the full minute track;
3. recover the ordered 60-tick sequence globally, enforcing circular order and near-6-degree spacing;
4. score each candidate tick using both radial-edge orientation and the full bright-stroke profile, not one local maximum;
5. use the coarse 12-direction cue only to choose the correct 6-degree sequence branch, never for precision fitting;
6. reject ambiguous tick endpoints rather than forcing them;
7. fit one robust homography from the surviving globally assigned correspondences;
8. optionally perform a small edge-distance refinement after the sequence is frozen.

The key requirement is that no individual tick should be allowed to jump independently to a reflection/hand edge, and the complete sequence must not be allowed to shift by one minute branch.

## Decision

**Continue with flat 2D Alpha91.** The perspective model is sufficiently proven. The remaining research target is robust global minute-track correspondence, particularly branch selection and endpoint rejection, before any Android integration.

Do not add depth, bezel geometry, sapphire modelling or QC thresholds at this stage.
