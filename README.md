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
- `feature/android-gmt-triangle-reference-overlay` shares no git history with `main` (it branched from the pre-Android desktop-app lineage and was last touched 2026-09-14). It is not the release baseline. On 2026-09-21 it was reconciled selectively rather than adopted wholesale — see "Architecture" below for what was kept, ported and left behind, and why.
- The active product/QC roadmaps are issues #13 and #14. Older roadmap issue #10 and its PRs are retired historical context.

## Architecture

Watch Align's automatic pose acquisition and per-marker QC (`MinuteTrackFirstOverlay`, `GmtMarkerQcRepair`, `Gmt126710BlnrMaster`) is the actively maintained, safety-reviewed lineage: it contains the fixes for a self-consistency false-accept bug, a genuine-catalogue-only marker calibration, and a validated triangle/minute-track crowding check (all on `main`/this branch). `feature/android-gmt-triangle-reference-overlay` independently built toward a more general, brand-agnostic architecture (declarative `WatchProfile`s selecting reusable `QcModule`s — see [`docs/architecture/multi-watch-architecture.md`](docs/architecture/multi-watch-architecture.md)) but stalled before that architecture absorbed any of the automatic-detection or safety work above, and its manual 5-point-tap measurement flow is a different interaction model from this app's automatic-first flow.

The decision: keep this lineage as the baseline (it is what is live and safety-tested) and migrate toward the other branch's *documented target shape* incrementally rather than switching wholesale. Concretely, as of 2026-09-21:

- Ported verbatim: the generic `com.watchalign.mobile.qc` contract layer (`QcModule`, `RawMeasurement`, `QcModuleResult`) and `com.watchalign.mobile.profile` (`WatchProfile`, `WatchProfileLoader`), with tests. These are additive — nothing in the app wires to them yet.
- Not ported: `QcModuleRegistry`, `GmtTriangle12QcModule` and `IndexGeometryQcModule`, which depend on that branch's own manual-tap geometry engine (`Triangle12RelationalMetric`, `PerspectiveRectifier`) rather than this app's automatic minute-track-first pose. Wrapping the *existing*, already-tested measurements behind the `QcModule` contract (instead of re-deriving them) is the natural next step whenever a second watch model is added.
- Preserved for reference under `docs/` and `android/validation/`: the multi-watch architecture proposal, GMT-specific research notes, and a genuine-vs-replica control study. That study statistically validated (n=1 genuine, n=1 replica — the user's own watch) that the 12-triangle's outward/base position relative to the minute track is the most clearly separating GMT defect signal found so far; this app's `GmtMarkerQcRepair.triangleOutwardDeltaPctR` check is a from-scratch implementation of that same signal against the automatic pose, not a port of that branch's code.

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
