# Watch Align

Watch Align is an **Android-only active development project** for visual watch inspection and evidence-based geometric QC.

The supported application lives in [`android/`](android/).

## QC direction

The governing QC design is documented in [`docs/architecture/QC_PRINCIPLES.md`](docs/architecture/QC_PRINCIPLES.md).

The core principle is simple: detect physical dial landmarks, calculate dimensionless relationships between them, establish the normal distributions from independent genuine watches, and report only deviations that are larger than both genuine variation and measurement uncertainty.

For marker QC, prefer simple local relationships between directly observed neighbouring landmarks over increasingly complex reconstruction of a theoretically perfect canonical dial. Global rectification/projective correction is supporting machinery and must demonstrate a repeatability benefit before becoming part of a production feature.

Existing Stage 3 proportional/projective experiments are research evidence, not a frozen production architecture. Their perturbation results should be retained and used to inform the reset rather than automatically carried forward.

## Active platform

- Android
- Java 17
- OpenCV
- Gradle
- APK builds via GitHub Actions

## Branch policy

- `main` is the supported/tested baseline and source for normal APK builds and releases.
- New development should start from current `main` and return through a focused pull request.
- `feature/android-gmt-triangle-reference-overlay` is retained temporarily as a preservation branch because it contains unique model-driven/multi-watch architecture work referenced by issue #14. It is not the release baseline and should be reconciled selectively against `main` before reuse.
- Historical roadmaps and experiments remain useful context, but new QC work must conform to `QC_PRINCIPLES.md` rather than inheriting old architecture by default.

## Build

From the repository root:

```bash
cd android
./gradlew :app:assembleDebug
```

The GitHub Actions workflow `.github/workflows/build-android.yml` builds the standalone APK.

## Legacy desktop/Windows code

The original Python/Windows implementation has been archived under [`legacy/windows/`](legacy/windows/). It is retained for history and for recovering useful ideas, but it is no longer an active or supported build target.

New product development should be made in `android/`. Research tooling may live outside `android/` when it is explicitly used to establish baselines, validate landmark measurements or quantify uncertainty.
