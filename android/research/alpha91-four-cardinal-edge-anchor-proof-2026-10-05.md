# Alpha91 four-cardinal dial-edge anchor proof — 2026-10-05

Research only. Alpha90 production code is unchanged.

## Question

If the user identifies the **outer edge of the black dial** at 12, 3, 6 and 9 o'clock, can those four points determine the flat 2D homography accurately enough to replace the fragile automatic 60-minute-track assignment?

## Geometry result

Yes. Four non-collinear point correspondences uniquely determine the 8-DoF planar homography.

The canonical points are simply:

- 12 = `(0, -1)`
- 3 = `(1, 0)`
- 6 = `(0, 1)`
- 9 = `(-1, 0)`

where radius 1.0 is the true outer edge of the dial.

Using the previously established oracle homographies for the genuine controls, projecting these four canonical edge points and then solving a new homography from only the four projected edge points reproduces the same projective mapping to numerical precision. No applied marker is required once the four edge correspondences are known.

This is therefore not an approximation: for a planar dial, four correct cardinal edge points are sufficient to recover the same projective transform.

## Sensitivity to selection error

The practical question is how accurately the four points must be selected.

A Monte-Carlo perturbation test was run on all four existing genuine oracle controls. Because the app can snap the user's rough selection **normal to the detected dial boundary**, the most important residual uncertainty is along the edge/tangent direction.

Typical added marker-position error from tangent-direction anchor uncertainty:

| RMS error per edge anchor | Mean added overlay error | Typical worst marker shift |
|---:|---:|---:|
| 0.25 px | ~0.20 px | ~0.33 px |
| 0.50 px | ~0.39 px | ~0.65 px |
| 1.00 px | ~0.78 px | ~1.30 px |
| 2.00 px | ~1.54 px | ~2.57 px |

Across the four genuine controls the sensitivity was very similar; this is not peculiar to the rolled `POOL_GEN_HO_02` photograph.

At 0.5 px RMS along-edge anchor uncertainty, the worst 95th-percentile mean added error across the controls was about 0.68 px. At 1 px RMS it was about 1.38 px.

## Important implementation consequence

The user should **not** be asked to hit a single exact pixel at normal phone scale.

Proposed interaction:

1. Ask for 12, then 3, then 6, then 9.
2. User taps roughly on the outer black-dial edge at that clock position.
3. Show a large magnified loupe / crosshair around that local region.
4. Snap the crosshair normal to the strongest local black-dial boundary so the user does not have to judge radial depth.
5. Constrain fine adjustment to movement **along the detected boundary**.
6. Allow a simple drag/nudge until the crosshair is visually centred on the intended cardinal point.
7. After four anchors, solve one unrestricted homography.
8. Optionally run only a *very small* masked local refinement that cannot move the four cardinal anchors materially.
9. Freeze the transform before drawing/checking any applied marker.

The existing broad/global ellipse fit should **not** be used as the final cardinal point source. Earlier experiments showed it can lock to a neighbouring ring/rehaut contour by several pixels in individual sectors. The user-labelled local edge point bypasses that ambiguity.

## Decision

**Proceed with the four user-labelled outer-dial cardinal anchors as the preferred Alpha91 pose path.**

The fully automatic 60-tick detector failed the agreed generalisation gate, but this four-point route removes the hard identity problem entirely while retaining the mathematically correct flat homography.

Target UI accuracy should be about **0.5 px effective along-edge placement after magnification/snap**. If achieved, the four-anchor contribution to overlay error should remain comfortably sub-pixel on average.

Do not use applied 12/6/9 markers themselves as anchors. The selected point is the outer black-dial boundary at that clock position, so a crooked applied marker cannot pull the reference toward the defect being inspected.
