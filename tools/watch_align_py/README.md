# Watch Align — Python Batgirl QC engine

**Status (2026-09-21): this Python implementation is now the primary,
actively-developed Batgirl QC engine and the source of truth for the
analysis algorithm.** The Android app under `android/` is frozen as a
reference/legacy implementation: it is kept for parity checks and is not
receiving further algorithm/QC changes while Python development is
ongoing. New algorithm work, calibration, genuine-vs-replica comparison,
diagnostics and batch testing all happen here. Nothing is ported back to
Android, and no API/hosting layer is built, until this engine is proven
stable against genuine and replica control sets.

This started as a faithful, line-by-line Python port (opencv-python +
numpy) of the deterministic GMT-dial pose-acquisition and measurement
pipeline that existed in the Android app
(`android/app/src/main/java/com/watchalign/mobile/`), originally built for
fast local iteration (the Java/Android/Gradle/emulator round-trip takes
minutes per change; this takes seconds). That origin is why file-by-file
Java-source references appear below — they document where the ported
logic came from and back Android's role as a parity reference, not an
ongoing dependency.

**Next milestone**: a reliable Python Batgirl QC engine that takes a
126710BLNR image, analyses it, produces diagnostics/overlays, and can be
validated against genuine and replica control sets. Not an APK.

## Why this exists

The Android app is a local, on-device OpenCV tool that photographs a Rolex
GMT-Master II 126710BLNR ("Batgirl") watch, fits its dial geometry (minute
track, hour markers), and flags visual defects against a calibrated genuine
reference. Several real-photo bug reports (rejected poses, biased marker
readings) were hard to pin down because each iteration required a full
Android build + emulator/CI run. This port reproduces the same algorithm in
plain Python so those bugs can be root-caused quickly, then the fix is
ported back into the real Java source.

## What's ported (headless measurement only — no rendering)

| File | Java source | Purpose |
|---|---|---|
| `master.py` | `Gmt126710BlnrMaster.java` | Fixed visual-master geometry constants |
| `geometry.py` | shared `map()`/basis helpers | `RotatedRect`, point mapping, percentile/bilinear sampling |
| `seed_detector.py` | `WatchAlignCoreV7.detectDial` | Legacy Hough-circle approximate seed |
| `minute_track_dial_finder.py` | `MinuteTrackDialFinder.java` | Concentric-ellipse fit from minor minute ticks — owns centre/scale/phase |
| `minute_track_pose_validator.py` | `MinuteTrackPoseValidator.java` | Held-out tick rotation solve + validation |
| `dial_projective_refiner.py` | `DialProjectiveRefiner.java` | Bounded projective correction search |
| `identity_gate.py` | `MinuteTrackIdentityGate.java` | Independent dial/marker identity check |
| `marker_qc.py` | `GmtMarkerQcRepair.java` | Per-hour-marker angular/radial/rotation measurement |
| `pipeline.py` | `MinuteTrackFirstOverlay.build()` | Orchestration: acceptance gates, confidence, full text report |
| `run.py` | — | CLI: decode a photo (replicating `MainActivity.readBitmap`'s 1600px cap) and print the full report |

Deliberately **not** ported: Canvas/Bitmap rendering (`renderNative`,
`rectify`, `drawX` methods) and the legacy `PerspectiveGmtOverlay.build()`
fallback pipeline (only used when acquisition can't even get a seed/ellipse
at all). Neither carries measurement logic; both only matter for the
on-device UI.

Usage:
```
pip install -r requirements.txt
python3 run.py /path/to/photo.jpg [more photos...]
```

## Tests

`tests/test_pipeline_fixtures.py` runs the pipeline against the real photo
fixtures shared with the Android app (`android/app/src/androidTest/assets/debug/`)
and pins the current known-good output as a regression baseline —
`tests/fixtures_manifest.py` lists each case's expectations plus any
independently-known human/community ground truth. Run with:
```
pytest tests/
```
When a deliberate algorithm change moves these numbers, update the manifest
in the same commit and explain why; an unexplained change is a regression
until proven otherwise.

## Fidelity notes

This is not a bit-exact numerical twin of the Android app. OpenCV's JPEG
decode + `cv2.resize` bilinear filter are not guaranteed pixel-identical to
Android's `BitmapFactory`/`Bitmap.createScaledBitmap`. All constants,
thresholds, loop bounds and algorithm structure are ported unmodified and
verified by re-reading the Java source line-by-line; small numeric
differences (a degree or two of apparent tilt, sub-pixel fit residuals) are
expected in marginal/single-candidate cases and are not port bugs.

## Confirmed finding (2026-09-21): seed-tolerance acquisition bug

Running this port against a real, only mildly tilted catalogue-style photo
(`android/app/src/androidTest/assets/debug/community-tilted-126710blnr-01.jpg`)
reproduced a known real-world failure: the pipeline picked a badly wrong,
~35°-"tilted", mis-centred dial ellipse and rejected the pose, despite the
photo being close to front-on.

**Root cause**: `MinuteTrackDialFinder`'s `findConcentricShapes` step admits
a candidate ellipse only if its centre is within `centreTolerance =
max(seedR*0.48, minDim*0.11)` of the legacy Hough-circle seed
(`WatchAlignCoreV7.detectDial`). On this photo the legacy seed was itself
~110px off the true dial centre — a bigger error than the ~81px tolerance —
so the correct, near-circular, right-diameter minute-track candidate was
discarded *before* the pose search ever ran, leaving only a single spurious
contour to be selected.

**Fix validated in this port** (`minute_track_dial_finder.py`, see
`centre_tolerance_admission`): keep `centre_tolerance` as the scale for the
*ranking* penalty (still prefers candidates close to the seed) but widen the
hard *admission* cutoff to `centre_tolerance * 2.2`, so a mediocre seed can
no longer silently exclude the correct candidate.

Result on the broken photo: candidates considered went from 1 → 7, the
correct near-circular ellipse was found, apparent tilt corrected from a
bogus 36.9° down to a realistic 5.4°, and the pose went from REJECTED to
ACCEPTED at 79% confidence with a tight 0.46px median tick-fit residual.
Re-ran a second, previously-good real photo through the same code with
zero change in output — no regression.

This exact one-line-of-logic fix has also been applied to the production
Java source (`MinuteTrackDialFinder.java`, `centreToleranceAdmission`) and
is pending validation through the project's Gradle/CI pipeline (local
Gradle validation is blocked in the authoring sandbox: its network policy
does not allow reaching `dl.google.com`, which Android Gradle Plugin
resolution requires).

## Open items

- `marker_qc.py` measurement on a second real photo
  (`community-vsf-batgirl-crooked12-01.jpg`) currently fails to isolate any
  of the 12 hour markers even though pose acquisition succeeds — not yet
  root-caused.
- No automated regression suite yet; validation so far is two real photos
  run by hand via `run.py`.
