# Watch Align Copilot Instructions

- Never commit generated Gradle caches, `.gradle` directories, build outputs, IDE caches, or temporary files.
- Never change production code unless the task explicitly requires it.
- Never change AGP, Gradle, SDK, Java, or dependency versions merely to make the Copilot environment work.
- Preserve the current native Android Java/OpenCV architecture unless explicitly told otherwise.
- Before claiming a geometry or mathematical bug, provide a numerical example proving the existing calculation is wrong.
- For QC logic changes, write a deterministic failing test before modifying the implementation wherever practical.
- Never widen tolerances or thresholds simply to make a failing test pass.
- Distinguish overlay/rendering problems from marker-detection, pose-estimation, calibration, and tolerance problems.
- Do not delete or replace apparently obsolete code without first reporting it and explaining why it appears obsolete.
- Before completing any task, verify the actual branch diff against `origin/main`, not just `git status`.
- Report every file changed and explain why it changed.
- Do not claim tests passed unless they actually executed.
- Clearly distinguish confirmed findings from hypotheses.
