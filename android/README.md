# Watch Align Android

Watch Align is an on-device visual QC aid for watch photographs. It is not an authenticity test and its image-derived genuine-control ranges are not manufacturer factory tolerances.

Version 1.3.0-rc1 provides:

- gallery selection and guided Camera2 capture with live framing, tilt, roll, blur and glare guidance
- FULL GEOMETRY and VISUAL ONLY capability labels before analysis
- the Alpha52-compatible manual 12, 3, 6 and 9 dial-edge alignment flow
- perspective-rectified 12-triangle measurements with values, median, observed range and signed deviation
- local inspection history and a shareable QC image
- a local personal library for user-confirmed genuine reference photos

All analysis is local. The app has no Internet permission. See [docs/PRIVACY.md](docs/PRIVACY.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md).

## Build

Use Java 17, Gradle 8.10.2 and Android SDK 35:

```sh
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
gradle :app:bundleRelease
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The release AAB is written to `app/build/outputs/bundle/release/app-release.aab`. Set the four `WATCH_ALIGN_*` signing environment variables documented in the release checklist to sign release output.
