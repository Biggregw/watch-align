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

# Product-scope guardrails

Before substantial planning or implementation, read `docs/PRODUCT_SCOPE.md`.

- Watch Align analyses uploaded dealer/QC photos. Do not redesign it around taking new camera photos.
- The existing GMT Android QC experience is the reference workflow for new watch families.
- Current production target: Rolex Submariner 124060, then 126610LN/LV.
- Preserve GMT production behaviour and regression protection.
- Reuse generic infrastructure only where the assumptions are genuinely generic. Never silently reuse GMT-specific geometry, thresholds, pose policy, or date-side logic for another family.
- Research must address a specific production blocker. Do not start open-ended corpus, detector, or calibration work without stating the production decision it enables.
- If one family-specific check is unreliable, suppress or mark that check unavailable rather than redesigning the whole product.
- Do not turn Watch Align into an authenticity classifier. The product is replica QC.
- Family work should end in working Android QC support and a testable APK, not indefinitely expanding research statistics.
- If a task begins expanding beyond `docs/PRODUCT_SCOPE.md`, stop before implementing the expansion and report the proposed scope change for approval.
