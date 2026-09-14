# Repository Instructions

- Never commit generated Gradle caches, `.gradle` directories, build outputs, IDE caches, or temporary files.
- Never change production code unless the task explicitly requires it.
- Never change AGP, Gradle, SDK, Java, or dependency versions merely to make the Copilot environment work.
- Preserve the existing native Android Java/OpenCV architecture unless explicitly instructed otherwise.
- Before claiming a geometry or mathematical bug, provide numerical proof.
- For QC logic changes, create a deterministic failing test before modifying implementation wherever practical.
- Never widen tolerances simply to make tests pass.
- Clearly distinguish overlay/rendering issues from marker detection, pose estimation, calibration, and tolerance issues.
- Do not delete suspected obsolete code without reporting it first.
- Never claim a test passed unless it actually executed.
- Clearly distinguish confirmed findings from hypotheses.
- Before finishing any task, verify the actual branch diff against `origin/main`, not just `git status`.
- Report every changed file and why it changed.
