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

# Reuse-first engineering workflow

Before creating or substantially changing family-specific detector, geometry, confidence, recovery or QC logic, read `docs/ENGINEERING_LESSONS.md` and audit the closest mature implementation first.

- Start from the mature analogue, normally GMT. Identify what problem it solved, the failure modes it encountered, and the guardrails added around it.
- Reuse lessons as well as code. Minimum pixel support, resize repeatability, local-frame quality, hand checks, alternate-reference cross-checks, recovery behaviour, fail-closed rules and regression strategy are all candidates for reuse even when marker geometry differs.
- Before writing a parallel algorithm, make a reuse map: **shared unchanged / shared with parameters or model layout / deliberately model-specific / not applicable**.
- Prefer composition, parameterisation or extraction of proven primitives over copying or rebuilding. Do not force a shared abstraction when measured evidence shows a family-specific method is more reliable.
- If a new family deliberately diverges from the mature method, record the evidence or concrete assumption that makes the mature method worse or inapplicable.
- Before declaring a new-family metric too noisy or unusable, check whether the mature path addressed the same failure with a different reference frame, cross-check, confidence gate, pixel floor, resize test, recovery path, pose gate or normalization.
- Before sourcing new images or starting a new corpus, check whether existing project datasets, artifacts and prior experiments can answer the question.
- Cross-family experiments should start diagnostic-only. Preserve mature GMT outputs and thresholds until equivalence/non-regression is demonstrated.
- Every substantial experiment or handoff must state **Lessons reused**, **Deliberate divergences**, and any **New reusable lesson**.
- When a reusable lesson is established, update `docs/ENGINEERING_LESSONS.md` in the same branch so future work does not have to rediscover it.

# Calibration-first new-model workflow

For a newly supported watch reference, do not begin by manually collecting photos or hand-picking QC limits. Start with the autonomous Watch Family Calibrator described in `docs/WATCH_FAMILY_CALIBRATOR.md`.

- The normal entry point is only the exact model reference. Its model config supplies layout, approved discovery sources, mature measurement adapter and eligible metrics.
- Let the calibrator discover/reuse source watches, acquire images, deduplicate them, create the locked physical-watch split, measure perturbations and propose limits end-to-end. Human sourcing should fill a reported evidence gap, not be the default workflow.
- Development genuine watches are the only data allowed to set a limit. Validation and untouched holdout may confirm/reject a frozen limit but must never move it. Replica data is stress-test evidence only and must never determine a limit.
- A metric that is under-sampled, pose/scale-sensitive or fails validation/holdout stays unavailable. Never guess a tolerance just to make the new model look complete.
- New families should inherit the mature Watch Align workflow and presentation layer. Model-specific code should stop at model geometry/detection/calibration adapters unless evidence requires otherwise.
- Treat `CALIBRATED` output as evidence ready for an explicit production integration change, not permission for a research workflow to silently alter Android verdicts.
