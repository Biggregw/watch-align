# Alpha91 four-cardinal local-snap proof — 2026-10-05

Research only. Alpha90 production code is unchanged.

## Purpose

Test the practical version of the four user-labelled outer-dial anchors, not only the ideal four-point homography maths.

The user supplies the identity/location of the four cardinal sectors (12, 3, 6, 9). The algorithm then searches only a small local strip around each supplied location and snaps **normal to the dial boundary** using the local image gradient. This deliberately avoids trying to identify the correct ring globally.

For this offline test, the previously established oracle cardinal locations were used only to simulate where a correctly instructed user would tap. Applied markers were not used by the local edge snap or by the resulting four-point homography.

## Local snap method

For each of the four rough cardinal locations:

1. use the coarse dial centre only to estimate the local outward normal;
2. examine a small strip around the supplied point;
3. average image-gradient energy across a short tangential span;
4. select the nearest strong local boundary response rather than the globally strongest ring;
5. keep the user's along-edge/cardinal identity fixed;
6. solve the unrestricted 3x3 homography from the four snapped outer-dial points.

All applied hour-marker centres remain holdouts.

## Genuine-control result with zero simulated tangential tap error

| Genuine control | Mean non-triangle holdout | Median | Max |
|---|---:|---:|---:|
| EXT_EXT_GEN_BLRO_WEX_01 | **1.18 px** | 0.82 px | 2.05 px |
| EXT_EXT_GEN_BLRO_WEX_02 | **1.08 px** | 0.84 px | 2.49 px |
| POOL_GEN_HO_01 | **1.95 px** | 1.65 px | 5.17 px |
| POOL_GEN_HO_02 | **0.93 px** | 1.00 px | 1.99 px |

`POOL_GEN_HO_01` contains the previously identified anomalous hour-2 holdout. Excluding hour 2 from that diagnostic only, its mean is approximately **1.59 px**. This does not change the homography; it only shows how much that single holdout dominates the summary.

## Simulated user along-edge error

After normal edge snapping, the remaining user-controlled uncertainty is principally tangent/along-edge placement. Random tangential perturbations were added before the same local snap.

Median mean holdout error over repeated trials:

### 0.5 px RMS tangential placement error

- WEX_01: ~1.21 px
- WEX_02: ~1.16 px
- HO_01: ~1.98 px
- HO_02: ~0.96 px

### 1.0 px RMS tangential placement error

- WEX_01: ~1.37 px
- WEX_02: ~1.32 px
- HO_01: ~2.08 px
- HO_02: ~1.14 px

At 2 px RMS tangential error the method degrades to roughly 1.8–2.6 px mean, confirming that the UI should help the user place the cardinal point accurately along the boundary rather than relying on an unassisted normal-size finger tap.

## UI implication

A practical selector should therefore:

- ask for 12, 3, 6, 9 one at a time;
- show a large loupe / magnified crop immediately after the rough tap;
- auto-snap normal to the local black-dial boundary;
- constrain fine adjustment along that detected boundary;
- provide a crosshair and left/right (along-edge) nudge at sub-pixel/image-pixel granularity;
- only accept the four anchors when the user is satisfied.

This converts the hard automatic problem (which of many similar rings/ticks is correct?) into an easy local problem (where exactly on this already-labelled boundary is the cardinal point?).

## Interpretation

The four-assisted-anchor route is materially more robust than the fully automatic 60-tick assignment and is already near the desired ~1 px regime on three genuine controls, with the fourth dominated by a known individual holdout anomaly.

The remaining work is now UI/local-snap engineering rather than perspective research.

## Decision

Proceed with a small Alpha91 interaction prototype for four user-labelled **outer black-dial edge** cardinal anchors. Keep Alpha90 production untouched until the assisted flow has been tested by a real user on genuine and replica QC photos.
