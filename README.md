# Watch Align

Watch Align is now an **Android-only active development project**.

The supported application lives in [`android/`](android/). Current work focuses on model-specific visual QC overlays, perspective registration, full-screen inspection, zoom/pan, opacity and blink comparison.

## Active platform

- Android
- Java 17
- OpenCV
- Gradle
- APK builds via GitHub Actions

## Branch policy

- `main` is the only supported/tested baseline and the source for normal APK builds and releases.
- New development should start from current `main` and return through a focused pull request.
- `feature/android-gmt-triangle-reference-overlay` is retained temporarily as a preservation branch because it contains unique model-driven/multi-watch architecture work referenced by issue #14. It is not the release baseline and should be reconciled selectively against `main` before reuse.
- The active product/QC roadmaps are issues #13 and #14. Older roadmap issue #10 and its PRs are retired historical context.

## Build

From the repository root:

```bash
cd android
./gradlew :app:assembleDebug
```

The GitHub Actions workflow `.github/workflows/build-android.yml` builds the standalone APK.

## Legacy desktop/Windows code

The original Python/Windows implementation has been archived under [`legacy/windows/`](legacy/windows/). It is retained for history and for recovering useful ideas, but it is no longer an active or supported build target.

New development should be made in `android/` unless the desktop implementation is explicitly revived in future.
