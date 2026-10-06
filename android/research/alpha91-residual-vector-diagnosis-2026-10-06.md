# Alpha91 residual-vector diagnosis — 2026-10-06

Research only. Alpha90 unchanged.

Using the strongest fully automatic dense-reference + minute-annulus homography, compute vector residuals as observed genuine marker centre minus projected genuine-reference centre.

## Main finding

All four genuine controls share a strong common translation component: projected geometry sits up-left of the observed genuine geometry. Robust pooled residual (excluding the previously identified POOL_GEN_HO_01 hour-2 annotation/outlier) is approximately **(+1.32 px, +1.04 px)** in candidate-image coordinates.

This is not primarily a scale error: mean radial residual is near zero on WEX1/WEX2/HO2. It is also not primarily a global roll error. Translation dominates.

## Fixed-offset check

Applying the same robust +1.32,+1.04 px correction to every photo gives:

- WEX1: 2.57 -> 1.27 px mean overall. Its remaining error is concentrated almost entirely at hours 4/5; excluding those two lower-right sectors the mean is ~0.57 px and max ~1.05 px.
- WEX2: 1.94 -> ~0.60 px mean.
- HO1: 2.48 -> ~0.82 px mean after excluding the already-known hour-2 outlier; max ~1.23 px for the remaining markers.
- HO2: 1.68 -> ~0.51 px mean; max ~1.13 px.

## Leave-one-photo-out check

A robust translation learned only from the other three genuine photos was applied to the held-out photo:

- WEX1: ~0.60 px mean excluding its 4/5 sector anomaly (~1.30 px including them).
- WEX2: ~0.63 px mean.
- HO1: ~0.90 px mean excluding the known hour-2 outlier.
- HO2: ~0.59 px mean.

So the sub-pixel result is not just a same-photo calibration artefact.

## Residual shape

- WEX2 and HO2 are almost pure common translation; after translation removal their residual RMS is ~0.83 px and ~0.47 px respectively.
- HO1 is similarly tight except the known hour-2 outlier.
- WEX1 has a genuine remaining lower-right sector pattern at hours 4/5; those two vectors are both about 4 px after common translation while the other eight markers average ~0.57 px.

## Interpretation

The current 2D homography is substantially better than the raw 1.7–2.6 px means suggested. The dominant error is a repeatable reference/registration-origin bias. A fixed genuine-calibrated origin correction is legitimate because it is global and candidate-independent; it does not let a replica marker pull its own overlay.

The next precision problem is now narrow: explain/remove the WEX1 lower-right sector residual without per-photo marker fitting. Sector-balanced trusted-edge refinement is the appropriate next test.
