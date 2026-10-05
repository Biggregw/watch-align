# Alpha91 minute-track-only proof — 2026-10-05

Research only. Alpha90 production code is unchanged.

## Goal

Test the proposed flat-dial Alpha91 architecture on the deliberately difficult genuine photo `POOL_GEN_HO_02` without using any applied hour marker to determine the homography.

Known oracle ceiling from the prior flat-homography experiment:

- non-triangle holdout mean: 0.391 px
- median: 0.252 px
- max: 0.983 px

Those oracle numbers used four genuine round markers only to prove what a mathematically correct flat homography can achieve. The present experiment removes those marker correspondences from fitting.

## Fitting evidence used

- coarse dial ellipse only for initial rectification
- one coarse 12-sector cue solely to resolve the 12-fold rotational ambiguity; it was not used for precision fitting
- minute-track annulus only for the precision solve

No applied marker centre was used in the homography fit.

## Minute-track detector

The candidate image was approximately rectified by the coarse ellipse transform. In that view:

1. CLAHE was applied.
2. Sobel gradients were measured through the minute annulus.
3. Only edge energy whose gradient was tangential to the dial (therefore whose edge itself was radial) was accumulated. This strongly suppresses circular dial/rehaut edges and emphasises the radial minute strokes.
4. The angular response produced 60 regularly spaced peaks, essentially one per minute position.
5. Inner/outer tick endpoints were estimated from radial intensity-gradient transitions.
6. Hour sectors could be excluded so applied markers never determined the projective fit.
7. OpenCV USAC/MAGSAC estimated one unrestricted 3×3 homography from the surviving minute-track correspondences.
8. A bounded robust refinement used the full 60 angular observations plus trusted radial endpoint observations.

## Result on POOL_GEN_HO_02

Final untouched applied-marker centre errors (pixels):

| Hour | Error px |
|---:|---:|
| 1 | 1.49 |
| 2 | 1.04 |
| 4 | 0.29 |
| 5 | 0.63 |
| 6 | 1.09 |
| 7 | 1.66 |
| 8 | 2.23 |
| 9 | 1.79 |
| 10 | 2.52 |
| 11 | 2.13 |

Non-triangle summary:

- mean: **1.49 px**
- median: **1.58 px**
- max: **2.52 px**

The 12 triangle remains excluded from this centre-error summary because the current canonical `TRI_CENTER_R` is not geometrically comparable with the manually annotated visual triangle centre; the earlier oracle already isolated that as a master-definition problem rather than a perspective problem.

## Interpretation

This is a successful go/no-go proof for the architecture, but not yet production accuracy.

A minute-track-only automatic fit, on the exact genuine photo where Alpha90 had a large pose/phase failure, is already within roughly 1–2.5 px of the untouched markers. The unrestricted flat homography oracle on the same photo shows that ~0.4 px mean is geometrically attainable. Therefore the remaining gap is dominated by minute-track localisation/refinement quality, not by missing depth, camera-pose, sapphire or 3D-watch modelling.

The useful detector insight is the orientation-filtered minute-annulus response: it recovers the 60 radial tick positions far more cleanly than raw brightness or an unfiltered edge-distance transform, which were attracted to unrelated circular/reflection edges.

## Decision

**Continue with the flat 2D Alpha91 path.**

Next bounded step:

1. keep the orientation-filtered 60-tick detector;
2. improve sub-pixel tick-centre / inner-end / outer-end localisation;
3. refine the homography locally from that result;
4. repeat on 2–3 additional genuine controls;
5. only consider lens distortion/refraction if a repeatable residual field remains after the minute-track solver approaches the oracle.

Do not reintroduce bezel depth or applied-marker fitting at this stage.
