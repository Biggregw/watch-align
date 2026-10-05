# Alpha91 flat-homography minimal oracle — 2026-10-05

Research only. Alpha90 production code is unchanged.

## Question

Before doing more depth/rehaut/refraction research, is a single flat dial-plane homography even capable of mapping the canonical GMT master onto a genuine oblique QC photo closely enough to justify pursuing a planar approach?

## Smallest possible oracle

Primary photo: `POOL_GEN_HO_02`, chosen because Alpha90 had a large visible phase/pose error on it.

To isolate geometry from detector quality, four genuine round-marker centres at hours 1, 4, 7 and 10 were used temporarily as oracle correspondences to define one unrestricted 3x3 homography from the canonical dial plane to the photo. They are not proposed as production fitting features.

The remaining applied markers were holdout only.

A second, disjoint four-round-marker anchor set (2, 5, 8, 11) was also tested to check that the result was not dependent on one cherry-picked quartet.

## Primary result: POOL_GEN_HO_02

Using anchors 1,4,7,10:

- 2 round: 0.238 px
- 5 round: 0.092 px
- 8 round: 0.243 px
- 11 round: 0.262 px
- 6 baton: 0.983 px
- 9 baton: 0.527 px
- non-triangle holdout mean: **0.391 px**
- non-triangle holdout median: **0.252 px**
- non-triangle holdout max: **0.983 px**

The 12 triangle centre showed 6.78 px error, but its canonical `TRI_CENTER_R=0.750` is not geometrically comparable with the manually annotated visual triangle centre. Holding the homography fixed and varying only the canonical triangle-centre radius put its best centre near ~0.798R, reducing that centre error below 1 px. This points to a triangle-centre definition issue, not a planar perspective failure.

Using the disjoint anchor set 2,5,8,11 gave essentially the same conclusion: the remaining non-triangle markers stayed within ~1.1 px maximum on this photo.

## Zero-cost sanity check on the other three genuine controls

Using the same fixed anchor pattern 1,4,7,10:

| Photo | non-triangle mean | median | max |
|---|---:|---:|---:|
| EXT_EXT_GEN_BLRO_WEX_01 | 1.31 px | 1.44 px | 2.17 px |
| EXT_EXT_GEN_BLRO_WEX_02 | 1.11 px | 1.00 px | 2.31 px |
| POOL_GEN_HO_01 | 1.90 px | 1.38 px | 6.52 px |
| POOL_GEN_HO_02 | 0.39 px | 0.25 px | 0.98 px |

`POOL_GEN_HO_01` has one large holdout at hour 2; the rest remain much tighter. That outlier now needs to be separated into annotation/master variation versus image-model error before being used as evidence against the planar model.

## Decision

**Continue with the flat dial-plane approach.**

This experiment shows that a single homography is capable of explaining the marker geometry on the deliberately difficult rolled genuine photo to sub-pixel / ~1 px accuracy for every non-triangle holdout. Depth is therefore not required merely to make the dial overlay geometrically coherent at this viewing angle.

This does **not** prove the production solver yet. The next bounded problem is to recover the same homography from trusted non-QC dial-plane evidence only (minute track / true dial perimeter / one absolute orientation anchor), without using applied markers.

Do not add bezel/rehaut depth, sapphire refraction, or marker-based fitting unless the dial-plane-only solver later leaves a repeatable residual pattern that requires them.
