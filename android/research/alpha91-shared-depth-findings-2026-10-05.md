# Alpha91 shared-depth oracle findings — 2026-10-05

Research only. Alpha90 production code is unchanged.

## Question

Can multiple genuine GMT-Master II photographs be explained by one shared physical separation between a rear dial-plane circle and a front bezel/rehaut circle, while allowing each photograph its own camera pose?

## Data used

The frozen Alpha90 validation artifact contains 15 genuine candidates. Four already had sufficiently clean, manually reviewed ring fits and marker observations for a bounded oracle test:

- EXT_EXT_GEN_BLRO_WEX_01
- EXT_EXT_GEN_BLRO_WEX_02
- POOL_GEN_HO_01
- POOL_GEN_HO_02

The remaining genuine candidates were not forced into this fit because their relevant circular edges were not already identified with equivalent confidence. Adding weak ring annotations would make the depth estimate less trustworthy, not more.

## Model

Adapted from FairScan's pinhole-camera / camera-intrinsics approach.

Per-photo variables:

- focal length in pixels
- 3D camera rotation
- image translation / camera offset
- camera distance

Shared variables across all photos:

- front-ring radius relative to rear dial circle radius
- front-ring depth relative to rear dial circle radius

The fit used the two observed ellipses plus the previously measured physical centre / 12-direction constraint. Hour-marker centres were not used to determine shared depth.

## Result

Joint fit:

- shared front-ring radius ratio: **1.0521 × dial radius**
- shared depth: **0.4158 × dial radius**

Per-photo ring RMS error (pixels):

| Photo | Dial ring RMS | Front ring RMS |
|---|---:|---:|
| EXT_EXT_GEN_BLRO_WEX_01 | 4.95 | 3.22 |
| EXT_EXT_GEN_BLRO_WEX_02 | 2.16 | 1.02 |
| POOL_GEN_HO_01 | 3.79 | 1.60 |
| POOL_GEN_HO_02 | 4.01 | 1.74 |

A depth-profile check showed a broad minimum rather than a sharp physical solution:

| Shared depth / dial radius | Robust cost |
|---:|---:|
| 0.01 | 704.0 |
| 0.03 | 720.2 |
| 0.06 | 685.0 |
| 0.10 | 644.0 |
| 0.15 | 610.7 |
| 0.25 | 588.2 |
| 0.40 | 584.7 |
| 0.55 | 585.4 |

The solver therefore prefers an implausibly large depth and remains almost indifferent across roughly 0.4–0.55 dial radii.

## Interpretation

This does **not** validate a recoverable physical bezel-to-dial depth from the two currently selected image rings.

The likely problem is upstream of the FairScan camera model: the visible edges currently labelled "dial ring" and "front ring" are not yet guaranteed to correspond to two exact, coaxial physical circles on known parallel planes. Crystal/refraction and the sloped rehaut may also mean one of the observed contours is not a simple projected physical circle.

This test therefore rejects the current two-ring pairing, not the broader physical-camera idea.

## Next bounded experiment

Before adding sapphire/refraction or production code:

1. identify one rear-plane reference that is definitely on the dial plane (preferably a calibrated minute-track circle or the true bare-dial perimeter);
2. identify one front-plane physical circle whose exact part is known;
3. verify both edges on a teardown / bare-case image;
4. rerun the same shared-depth fit;
5. only proceed if the recovered depth is physically plausible and has a narrow, repeatable optimum.

Do not tune the camera solver around marker positions or replica defects.
