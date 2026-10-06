# Alpha91 dense genuine-reference registration proof — 2026-10-06

Research only. Alpha90 production code is unchanged.

## Goal

Test whether Watch Align can recover an accurate flat 2D homography automatically from a real GMT QC photograph without manual 12/3/6/9 taps and without using applied hour-marker positions in the precision fit.

The fixed reference is the supplied front-on genuine GMT bare-dial photograph.

## Automatic pipeline tested

1. Locate the physical black-dial region with a strict dark-inside / bright-outside circular seed.
2. Fit a coarse dial ellipse from radial dark-to-bright boundary transitions.
3. Identify the 12 sector automatically from the obvious large bright 12 marker region only as a coarse orientation cue. Its exact position is not used as a homography correspondence.
4. Warp the candidate into the same canonical dial frame as the genuine bare-dial reference.
5. Refine one unrestricted 3x3 planar homography with masked dense ECC on gradient-magnitude images.
   - trusted inner printed dial regions and the minute annulus are included;
   - applied hour-marker regions, centre/hands region and date/cyclops region are masked out.
6. Run a second small ECC refinement using only the minor-minute annulus, with all 12 hour sectors masked.
7. Project the genuine reference marker centres through the recovered homography and compare them with independently inspected genuine candidate marker centres.

Applied-marker centres are HOLDOUT ONLY. They never participate in the precision registration.

## Genuine holdout results

Unchanged pipeline on four genuine controls:

| Genuine control | Mean marker-centre error | Median | Max |
|---|---:|---:|---:|
| EXT_EXT_GEN_BLRO_WEX_01 | **2.57 px** | 2.21 px | 4.96 px |
| EXT_EXT_GEN_BLRO_WEX_02 | **1.94 px** | 1.85 px | 3.23 px |
| POOL_GEN_HO_01 | **2.48 px** | 1.84 px | 8.18 px |
| POOL_GEN_HO_02 | **1.68 px** | 1.68 px | 2.37 px |

POOL_GEN_HO_01 retains the previously identified anomalous hour-2 holdout; most of its other markers are around 1–3 px.

## Important negative results

Several alternatives were tested and rejected rather than tuned per photo:

- four human-selected cardinal points: mathematically valid but too sensitive to along-edge tap error;
- automatic global 60-tick correspondence assignment: catastrophic failures on some genuine photos;
- conic + pinion centre + 12 direction: about 3–9 px marker error on these controls;
- raw SIFT homography from the bare dial: about 5–10 px marker error;
- SIFT/text-only homography: still about 5–10 px;
- optical-flow sub-pixel refinement after dense registration: helped some photos but worsened others, so rejected;
- direct raw-intensity ECC: unstable.

## Interpretation

This is the first fully automatic method in this research sequence that avoids catastrophic genuine-photo failures while remaining in the low-single-pixel regime across all four controls.

However, it **does not meet the ~1 px mean target** required for a near-perfect visual overlay. The current result is approximately 1.7–2.6 px mean, with one known larger holdout anomaly.

Therefore this should **not yet replace Alpha90** and should **not yet be presented as a solved production overlay**.

## Important product constraint

The strongest dense-registration version uses printed dial text as trusted pose evidence in addition to the minute annulus. If that remains in production, those same text features cannot later be independently judged as QC defects because they helped determine the pose.

If independent text QC is required, the final alignment evidence must be restricted further (for example minute annulus plus other non-QC printed geometry), and its accuracy must be revalidated.

## Decision

Continue only if the next work is explicitly aimed at removing the remaining ~1–2 px systematic registration bias and is validated unchanged across the same genuine controls.

Do not build another APK until the offline method reaches the agreed visual accuracy target.

Do not reintroduce bezel depth, sapphire, 3D camera recovery or manual cardinal taps as a response to these residuals.