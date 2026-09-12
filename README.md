# Watch Align

Watch Align is now an **Android-only active development project**.

The supported application lives in [`android/`](android/). Current work focuses on model-specific visual QC overlays, perspective registration, full-screen inspection, zoom/pan, opacity and blink comparison.

## Active platform

- Android
- Java 17
- OpenCV
- Gradle
- APK builds via GitHub Actions

## Build

From the repository root:

```bash
cd android
gradle :app:assembleDebug
```

The GitHub Actions workflow `.github/workflows/build-android.yml` builds the standalone APK.

## Legacy desktop/Windows code

The original Python/Windows implementation has been archived under [`legacy/windows/`](legacy/windows/). It is retained for history and for recovering useful ideas, but it is no longer an active or supported build target.

New development should be made in `android/` unless the desktop implementation is explicitly revived in future.
