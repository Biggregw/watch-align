# Alpha91 minute-track sub-pixel refinement — 2026-10-05

Research only. Alpha90 production code is unchanged.

## Scope

Single-photo bounded experiment on `POOL_GEN_HO_02`, the deliberately rolled/oblique genuine GMT control.

Candidate applied hour markers were never used to determine the homography. They remained holdout checks only.

## Starting point

The previous minute-track-only proof achieved approximately:

- non-triangle holdout mean: 1.49 px
- median: 1.58 px
- max: 2.52 px

The four-marker oracle on the same image had previously shown that a flat homography is capable of roughly 0.39 px mean holdout error, so the remaining gap was detector/reference quality rather than missing 3D geometry.

## Important discovery: minute-track reference radius

The current GMT master uses:

- `MINUTE_TRACK_R = 0.925`
- `MINUTE_TRACK_OUTER_R = 0.972`
- midpoint = 0.9485R

The bare genuine GMT dial image supplied by Greg was analysed independently.

Using a circle fit to the bare dial plus the same radial bright-tick detector used on the candidate:

- bare-dial minute-track effective centre ≈ 0.9550 of the photographed dial radius
- bare-dial round-marker centre median ≈ 0.8111 of that same radius

Keeping the existing master round-marker radius fixed at 0.816 gives a detector-consistent minute-track centre of approximately **0.9598R**.

The corresponding calibrated bright-tick interval is approximately:

- inner ≈ **0.9358R**
- outer ≈ **0.9837R**

This is materially farther outward than the frozen Alpha90 minute-track geometry. The size of the discrepancy is of the same order as the residual pose error seen in the automatic overlay.

## Refinement method

1. Affine/conic coarse rectification only to localise the dial annulus.
2. Top-hat / local-contrast extraction of radial minute-tick components.
3. Local projected tick-template matching around each predicted minute position.
4. Per-tick sub-pixel radial/tangential translation search.
5. Robust MAGSAC homography refit from high-confidence minute-tick observations only.
6. Repeat local match/refit until stable.

No applied marker centre, marker outline, date window or replica defect was used to fit the transform.

## Best stable result on POOL_GEN_HO_02

Using the independently recalibrated bare-dial minute-track geometry:

- non-triangle holdout mean: **~1.13 px**
- median: **~1.15 px**
- maximum: **~1.81 px**

Representative holdout errors from the stable pass:

- 1 round: ~1.15 px
- 2 round: ~1.26 px
- 4 round: ~0.86 px
- 5 round: ~0.90 px
- 7 round: ~1.20 px
- 8 round: ~1.20 px
- 9 baton: ~0.65 px
- 10 round: ~1.15 px
- 11 round: ~1.13 px
- 6 baton: ~1.81 px

The 12 triangle is still excluded from this numeric comparison because the frozen master's `TRI_CENTER_R=0.750` does not represent the same visual centre definition as the manually annotated triangle centre. That is a separate master-geometry issue already demonstrated in the oracle test.

## Interpretation

The automatic flat-dial solver improved from about 1.49 px mean to about 1.13 px mean without using any applied markers.

More importantly, the experiment uncovered a genuine master-geometry problem: the frozen minute-track radii are too far inward for the detector-visible tick geometry in the bare genuine dial reference.

Further optimisation on this one photograph is now likely to overfit local JPEG/lighting/tick appearance. The next useful test is therefore not more single-photo tuning. It is to freeze this calibrated minute-track method and run it unchanged on several other genuine controls at different angles.

## Decision rule for next stage

Continue toward Alpha91 only if the unchanged solver generalises across multiple genuine photos with roughly <= 1–1.5 px mean non-triangle holdout error and no systematic sector drift.

If one sector repeatedly remains displaced across genuine controls, diagnose reference geometry / detector bias there before adding any QC scoring.

Do not reintroduce bezel depth, sapphire refraction or 3D camera recovery unless a repeatable residual remains after this planar calibration is validated.