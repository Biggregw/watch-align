# Build instructions for Watch Align

The supported app is in android/. Open that directory as the Android Studio Gradle project.

- Use JDK 17 and the committed Gradle 8.14.5 wrapper. Do not upgrade the build toolchain as part of unrelated fixes.
- Windows: from android/, run .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --stacktrace. Linux/macOS: bash ./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace.
- Verify a dependency's exact published coordinates and Android AAR packaging before changing it. OpenCV uses the official org.opencv:opencv package on Maven Central.
- Diagnose the first underlying failed task and its original error. Missing reports after a failed build are symptoms, not evidence of failing tests.
- Do not disable validation tasks, skip tests, or weaken QC thresholds to make a build pass.
- Validate locally before proposing a commit or push. If a command fails twice for the same reason, inspect the cause and change the approach instead of repeating it.
- Use PowerShell-compatible syntax on Windows. Keep independent commands separate and inspect exit codes.
- Do not commit, push, publish releases, or send messages unless the user requests those actions.
- Report which checks actually passed and which checks remain blocked. APK compilation does not establish camera, native OpenCV, or image-analysis correctness on a device.

## Adding or changing a QC check (fragility rules)

- A new check may only add information. It fails closed to "not judged" and must never change another check's verdict or the photo-angle rating, unless that coupling is the explicit purpose of the change and is reviewed as such.
- The corpus run is the gate. Before merging, run the desktop regression on the full corpus and compare every verdict and value with the current main. Any change must be intended, listed and explained in the PR; anything else blocks the merge.
- New checks start as research: measure silently, with no verdict and no UI, until the genuine and replica spreads and the resize-check stability are known and written up in docs/research/.
- Genuine false-flag budget: a new check flags nothing on the clean genuine set, and the app-wide share of usable genuine photos with nothing flagged must not drop.

