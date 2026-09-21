# GMT 126710 perspective frame validation

> **Provenance:** ported from the orphaned `feature/android-gmt-triangle-reference-overlay` branch (last commit 2026-09-14), which shares no git history with `main`/this branch. Brought forward on 2026-09-21 for context and reference; the generic `qc`/`profile` contract layer it describes (`QcModule`, `RawMeasurement`, `QcModuleResult`, `WatchProfile`, `WatchProfileLoader`) was ported into this lineage, but its manual 5-point-tap measurement UI and concrete modules (`GmtTriangle12QcModule`, `IndexGeometryQcModule`) were not — see `README.md` for the architecture decision.


Date: 2026-09-14

## Purpose

Validate that Watch Align's planar GMT rectification does not manufacture index-placement errors merely because the same physical watch is photographed from a different camera angle.

The strongest real-world test is a single genuine/reference 126710BLNR seen across many frames of one video: the watch geometry is physically unchanged while camera perspective changes continuously.

## Reference source selected

Primary candidate: Watchfinder & Co. Extra, **Rolex GMT Master II 126710BLNR**, YouTube video ID `dAGR-wDB72E`:

`https://www.youtube.com/watch?v=dAGR-wDB72E`

A Watchfinder listing for a 2024 126710BLNR also explicitly labels its gallery as images of the actual watch and provides a useful still-image cross-check:

`https://www.watchfinder.com/Rolex/GMT%20Master%20II/126710%20BLNR/39491/item/384578`

These sources are validation references, not Rolex CAD and not factory-tolerance evidence.

## Frame-extraction status

The current automated environment can discover the Watchfinder/YouTube source and its still-image listing, but cannot decode/download the YouTube media stream into local frame bytes. Therefore no claim is made here that arbitrary frames from that video have already been measured.

Instead, the production perspective calculation has now been subjected to the same mathematical experiment deterministically in unit tests: one unchanged canonical dial is projected through several independent camera/projective views, then rectified using the exact four-cardinal-anchor code used by the app.

The regression lives in:

`android/app/src/test/java/com/watchalign/mobile/PerspectiveFrameInvarianceTest.java`

## What is tested

Five deliberately different projective views cover:

- face-on
- left/right projective tilt
- vertical tilt
- compound tilt plus shear/roll-like projective terms

For every view the test projects and recovers:

- non-date hour-marker centres
- a dense sample of the minute-track ring
- the date-window centre
- the three 12-triangle vertices

The recovered canonical geometry must remain invariant to within numerical precision.

A second regression deliberately moves only the 9 o'clock anchor 6% outward, simulating selection of the larger/outer rehaut boundary while the other three anchors remain on the inner dial edge.

That single wrong physical edge creates false radial residuals greater than 0.020 dial radius around both 8 and 10 o'clock while the opposite quadrant remains below 0.010 dial radius. This reproduces the failure pattern seen during phone testing: a mathematically valid homography can still give a wrong QC result when an anchor refers to the wrong physical circle.

## Interpretation

The four-point homography equations themselves are not expected to drift with perspective when all four anchors are correct and lie on the same planar physical boundary. The dominant real-world risk is correspondence error: selecting a different rehaut edge on one side, or allowing reflection/feature detection to move an anchor between frames.

Therefore future perspective confidence must be based on independent physical-edge evidence and multi-feature consistency, not on four-anchor self-reprojection alone.

## Real-video acceptance test when frame bytes are available

For 15-30 sharp frames of the same 126710BLNR spanning face-on to clearly oblique views:

1. run automatic inner-edge registration independently per frame;
2. rectify to the canonical dial;
3. retain only frames whose same physical inner boundary is independently verified;
4. record each marker's radial and tangential canonical coordinates, triangle geometry, date-window centre and minute-track phase;
5. calculate within-watch frame-to-frame standard deviation and range;
6. inspect any residual against view angle to detect systematic perspective bias.

A component that appears to move systematically as viewing angle changes is evidence of registration/localisation error, not physical watch variation.
