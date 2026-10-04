# Watch Align Android

The supported application lives in this directory.

Watch Align analyses **uploaded dealer/QC watch photos** and reports measurable deviations from a proven-genuine reference. It is not an authenticity classifier.

The current repository direction is governed by:

- `../docs/PRODUCT_SCOPE.md`
- `../docs/CALIBRATION_PROTOCOL.md`
- `../docs/HANDOFF.md`
- `../docs/architecture/QC_PRINCIPLES.md`

## Current baseline

The existing GMT route is the known-working production control and must remain unchanged while the new calibration protocol is validated offline.

The repository also contains an experimental 124060 route and historical research tooling. Preserve them, but do not treat old alpha-era notes or thresholds as the active roadmap.

The immediate project task is **not** an Android implementation change. It is to recalibrate one known-working GMT feature offline from existing genuine evidence, compare it with the established GMT result, and refine the reusable calibration protocol.

Only after a research rule is validated should production Android code be changed.

## Build

Use JDK 17 and the committed Gradle wrapper.

From this directory:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Do not build an APK or trigger CI merely to answer a research question that can be tested offline.

## Engineering rules

- Preserve GMT behaviour unless an explicitly validated change requires otherwise.
- Reuse existing images and artifacts before searching for new data.
- Keep detector output, numeric measurement, confidence/assessability and QC judgement separate.
- Do not widen genuine limits to accommodate poor photographic pose.
- Prefer simple directly observed relationships; complex perspective correction must demonstrate a held-out accuracy benefit.
- Replica evidence validates usefulness but never moves a genuine-derived reference range.
- A feature may ultimately be deterministic code, vision AI, hybrid, or not reliable enough; let validation decide.

Historical implementation details remain available in Git history and `docs/research/` when needed.
