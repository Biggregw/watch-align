# JVM parity harness (research only)

This harness runs the real Android Alpha91 pose path and the Alpha94 marker measurement on desktop
OpenCV 4.9. Desktop OpenCV is the same version the app uses. The output can then be compared with the
Python research pipeline (`alpha91_overlay_registration.py`, `alpha91_marker_measurement.py`).

What is real and what is stubbed:
- **Compiled unchanged:** every Android-free class in `com.watchalign.mobile`, including
  `Alpha94MarkerMeasurement`.
- **AutomaticDialOverlay:** only its outline-bitmap rendering is stubbed. The pose path is the
  production code.
- **Stubs:**
  - `android.graphics.Bitmap` and `org.opencv.android.Utils`, which carry an RGBA `Mat`;
  - `GmtTwelveRecoveryAnalyzer`, which depends on Android-only `WatchAlignCoreV7`. The harness reports
    recovery as unavailable, so a photo whose primary 12 analyser fails is rejected here. On device the
    recovery path may still succeed.

```
android/research/jvm-parity-harness/run.sh <image.png> <name> [h00,h01,...,h22]
```

- `FULL`: the complete Android pose path (`AutomaticDialOverlay.build`).
- With an H argument (for example the frozen research H):
  - `FIT`: the minute-lattice fitter on its own, started from that H;
  - `M` / `RING`: the Alpha94 measurement on that H.

Parity on 2026-10-06, using the six marker-measurement fixtures:
- Java H vs Python H: within 0.001 px at 0.95R.
- Java vs Python marker centres: within 0.033 px.
- Baton rotation: identical.
- Usability flags: identical.
