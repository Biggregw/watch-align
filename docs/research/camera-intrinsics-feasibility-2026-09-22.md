# Camera2/intrinsics feasibility assessment - 2026-09-22

## Correction (2026-09-22, later same day)

The original version of this document concluded "there is no existing
Camera2 capture path," based only on grepping the current experiment
branch's `android/` tree. That conclusion was too narrow and has been
corrected below (see "Where Camera2 capture code actually exists"). It was
also written before an explicit product clarification from the project
owner, which is decisive and is stated up front:

> **Watch Align is NOT intended to require users to take the QC photo
> in-app.** The normal use case is that the user receives QC photos from a
> dealer and imports those existing images. Production input must be
> assumed to be an arbitrary imported image: unknown camera/lens, unknown
> focal length, unknown distance, unknown zoom/crop, EXIF possibly absent
> or stripped. Only image pixels and known watch geometry are dependable.

Consequently: **Camera2/capture-time metadata must never be an
architectural dependency for the QC algorithm.** Everything below is
documented for completeness (it answers a real question the project owner
asked, and corrects a factual gap in the original answer), but none of it
drives, or should drive, the Part B dial-geometry-recovery work. Part B is
scoped entirely to what can be recovered from image pixels plus known watch
geometry, with no assumption that any capture metadata will ever be
present.

## Three-way distinction

1. **On the current experiment branches** (`experiment/dial-geometry-homography`,
   `experiment/projective-marker-normalization`, and `main`): no Camera2
   capture path exists. `AndroidManifest.xml` declares no `CAMERA`
   permission and no camera `<uses-feature>`. `MainActivity.pickWatch()` /
   `pickReferences()` use `Intent.ACTION_OPEN_DOCUMENT` (Storage Access
   Framework) to import an existing image file chosen by the user; the app
   never opens a camera session itself here. This part of the original
   finding was correct as far as it went.

2. **Elsewhere in repo history, unmerged:** a real, working Camera2
   `ImageReader` still-capture implementation exists on four sibling
   feature branches never merged to `main` --
   `feature/android-gmt-triangle-reference-overlay` (most complete, 56-line
   `CaptureActivity.java`), `feature/android-issue-10-product-hardening`,
   `feature/gmt-triangle-reference-overlay`, and
   `feature/issue-10-product-hardening` (all four also carry a
   `CaptureHistoryExportDeviceTest.java`, plus sibling files
   `CameraCaptureConfig.java`, `CaptureGuideView.java`,
   `CaptureQualityAnalyzer.java`). `CaptureActivity.java` on the most
   complete branch: selects the rear camera via `CameraCharacteristics`
   (`LENS_FACING`, `REQUEST_AVAILABLE_CAPABILITIES` multi-cam check,
   `SENSOR_INFO_ACTIVE_ARRAY_SIZE`, `LENS_INFO_MINIMUM_FOCUS_DISTANCE` --
   all used only for camera-selection scoring, not persisted), reads
   `SCALER_STREAM_CONFIGURATION_MAP` and `SENSOR_ORIENTATION` for stream
   setup, optionally applies `CONTROL_ZOOM_RATIO_RANGE` zoom (<=1.5x), and
   captures a full-resolution JPEG via `TEMPLATE_STILL_CAPTURE` +
   `ImageReader`. It does **not** read or persist any of the
   intrinsics-relevant fields -- `LENS_INTRINSIC_CALIBRATION`,
   `LENS_DISTORTION`, `SENSOR_INFO_PHYSICAL_SIZE`,
   `LENS_INFO_AVAILABLE_FOCAL_LENGTHS`, or `SCALER_CROP_REGION` -- anywhere
   alongside the resulting photo. So even where a real capture path exists
   in history, it was never wired up to log anything this feasibility
   question actually needs; it would need extension, not just restoration,
   before it could supply calibration-grade metadata. Per explicit
   instruction, this code has not been merged, restored, or otherwise
   touched -- this is a read-only historical finding.

3. **What Camera2 could realistically provide if this path were restored
   and extended:** answered in sections 1-5 below, technically unchanged
   from the original assessment, now grounded in a real (if incomplete)
   implementation rather than a hypothetical one.

Even with item 2 corrected, item 3's answer is now explicitly **out of
scope for the product**: the corpus, and real-world dealer QC photos in
general, are always imported, not captured in-app, so no design should
depend on this metadata being available.

## 1. Which Camera2 fields are available on typical modern Android devices?

Static, from `CameraCharacteristics` (queried once per camera, before/without
capturing):

- `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` -- near-universal. Almost always a
  single value on phones; phones do not have continuously variable optical
  zoom lenses, so this is a fixed constant per physical camera, not a live
  reading.
- `SENSOR_INFO_PHYSICAL_SIZE` -- near-universal (sensor width/height in mm).
- `SENSOR_INFO_PIXEL_ARRAY_SIZE` / `SENSOR_INFO_ACTIVE_ARRAY_SIZE` --
  near-universal.
- `LENS_FACING` -- always present.
- `LENS_INTRINSIC_CALIBRATION` (fx, fy, cx, cy, skew, a genuine calibrated
  intrinsic matrix) -- **conditionally available**. Present on many
  flagship/upper-midrange devices with a calibrated camera HAL; commonly
  absent on budget devices and inconsistently populated across OEMs. Cannot
  be assumed.
- `LENS_DISTORTION` (radial/tangential distortion coefficients) -- same
  conditional availability as intrinsic calibration, often populated
  together or not at all.
- `LENS_POSE_ROTATION` / `LENS_POSE_TRANSLATION` -- rare; mostly
  ARCore/depth-capable devices.

Per-capture, from `CaptureResult` (returned with each frame):

- `LENS_FOCAL_LENGTH` -- echoes which static focal length was used; only
  interesting on the rare device with a real variable-focal-length lens.
- `SCALER_CROP_REGION` -- **the practically important one**. Digital zoom on
  phones is implemented as a crop of the active sensor array (optionally
  upscaled), not a focal-length change. This varies per capture and directly
  changes the effective focal length in pixels of the saved image even
  though the physical lens focal length did not change.
- `LENS_FOCUS_DISTANCE` -- varies with autofocus; accuracy for a close-range
  macro-style shot (a watch QC photo) is device-dependent and often coarse,
  and many devices do not populate it meaningfully outside manual-focus
  mode.
- On logical multi-camera devices, which physical lens is actually active
  for a given frame is not automatically implied by a single focal-length
  reading unless the app explicitly reads the active physical camera ID --
  otherwise a "zoom" that silently switches physical lenses can change the
  true focal length/sensor without the naive focal-length field reflecting
  it consistently.

## 2. Static vs per-capture

Answered inline above. The one field that is genuinely per-capture and
matters for this problem is `SCALER_CROP_REGION` (plus, on multi-camera
devices, which physical lens is active). Everything else needed for a
focal-length-in-pixels estimate is effectively a fixed constant for a given
physical camera and can be read once.

## 3. Would they let us estimate focal length in pixel units accurately enough?

- When `LENS_INTRINSIC_CALIBRATION` is populated: yes, directly, and this is
  the most accurate available option (OEM factory calibration).
- Otherwise, the standard estimate
  `focal_px = focal_length_mm * (active_array_width_px / sensor_width_mm)`,
  corrected for the actual `SCALER_CROP_REGION` and any further resize the
  app itself performs (the existing 1600px longest-side cap), is a
  well-established, reasonably accurate first-order estimate -- good enough
  to materially constrain, though not perfectly nail, the true focal length.
- Neither path is universally available or perfectly precise, and both are
  **capture-time-only**: they require a live Camera2 session logging this
  metadata into the saved output, which does not exist today.

## 4. Does knowing focal length remove the ambiguity previously identified?

Partially, and only for a subset of the underlying problem -- and, as Part B
below shows, it is **not actually the binding constraint** once the dial's
own geometry is used properly (see Part B's identifiability result). The
earlier rejected closed-form correction failed because it needed *both* the
tilt (encoded in the fitted ellipse's axis ratio) *and* an independent
focal-length/standoff estimate to correctly predict how a calibration-radius
ellipse relates to a different marker radius. Camera focal length in pixels
would supply the missing half of that pair for a live-captured photo. But
critically: **it still would not, by itself, replace the need for real image
correspondences.** A homography's extra projective degrees of freedom are
about the *relationship between the dial plane and the image*, not simply
"the lens's focal length" -- you still need to know the plane's orientation,
which is exactly what a single ellipse under-constrains. Knowing focal
length narrows the search (removes one unknown from a decomposition
`H = K[r1 r2 t]`), it does not hand you the rotation directly without also
having enough image correspondences to solve for it.

## 5. Is camera-to-watch distance required, or can pose be solved without it?

**Not required**, and this is an important simplification. Every downstream
quantity in this pipeline (marker radial/angular offsets) is expressed as a
fraction of the dial's own canonical radius, never in millimetres or in
absolute camera-space units. A homography that correctly maps the dial's own
canonical plane coordinates to image pixels captures the full projective
relationship needed to place a marker at any radius correctly, and that
mapping is invariant to the overall metric scale of the scene (a bigger dial
farther away and a smaller dial closer produce the identical image, and the
identical correct homography, for this purpose). Standoff/distance would
only matter if the goal were metric 3D reconstruction; it is not needed to
resolve the specific ambiguity that broke the earlier axis-ratio correction.
This point carries into Part B: the earlier correction failed for lack of
*enough independent image correspondences to directly fit the true
homography*, and that gap can be closed using the dial's own known geometry,
without needing camera intrinsics, distance, or any new capture-time
metadata at all.

## Conclusion for Part A

Camera2 metadata is real (an unmerged capture path already exists in repo
history), would work roughly as described in sections 1-5, and would be a
reasonable thing to log if that path were ever restored and extended for an
optional in-app capture flow -- but it is **not a viable product
dependency**: Watch Align's normal use case is importing existing dealer
photos, not in-app capture, so any design that required or preferred
Camera2 metadata would not work for the actual product. It is architecturally
limited even on its own terms (capture-time only, never applies to the
picker path or to any already-imported photo, including the entire existing
corpus) and, per the Part B investigation below, is not the constraint
actually missing from the current approach anyway. It is not pursued
further as an experiment here and must not drive the Part B architecture.
Part B investigates the dial-geometry path directly -- pixels plus known
watch geometry only -- which is testable against the existing corpus today,
works for the actual "import an arbitrary photo" product, and does not
depend on any product change to the capture flow.
