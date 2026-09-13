# Watch Align Android architecture

## Active data flow

`MainActivity` selects a model and an image, then delegates analysis to `WatchAlignCore`. The core coordinates these explicit components:

1. `DialAnalysisEngine` detects the dial and marker observations.
2. `ReferenceComparisonEngine` registers an optional user-selected genuine image.
3. `QcExtendedAnalyzer` produces model-aware local diagnostics.
4. `PerspectiveGmtOverlay` estimates pose without changing reference geometry.
5. `PerspectiveMasterRenderer` renders exact-model geometry from `Gmt126710BlnrMaster`.
6. `ManualAlignActivity` and `PerspectiveRectifier` preserve the Alpha52 four-anchor semantics.

Exact-model geometry is represented in Java constants. The duplicate JSON catalog and template were removed so there is one runtime source of truth. Pose and registration are image-derived and remain separate from those constants.

## Behavioural contract

Alpha52 is the frozen baseline for the four manual dial-edge anchors, projective rectification, and 12-triangle control classification. Automatic points are suggestions. No inspected marker is moved to improve a score. Genuine-control ranges are observations from images, not manufacturer tolerances and not an authenticity test.

## OpenCV audit

The app uses the OpenCV Android artifact locally. Used modules and APIs are:

- `core`: `Mat`, channel conversion/copy, masks, statistics and transforms
- `imgproc`: colour conversion, blur, Canny, contours, Hough circles, ellipse fitting, thresholding, drawing, affine/perspective warp and Laplacian sharpness
- `calib3d`: four-point homography estimation
- `android`: `Bitmap` to/from `Mat`

No DNN, video, network or cloud API is used. Replacing OpenCV would require validated equivalents for the detector and projective operations, so it remains isolated behind analysis components for now.

## Storage and network

Analysis and capture are local. The app has no Internet permission. User-selected and captured images remain local unless the user explicitly invokes Android sharing.
