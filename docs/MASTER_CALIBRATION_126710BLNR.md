# 126710BLNR visual master calibration

> **Provenance:** ported from the orphaned `feature/android-gmt-triangle-reference-overlay` branch (last commit 2026-09-14), which shares no git history with `main`/this branch. Brought forward on 2026-09-21 for context and reference; the generic `qc`/`profile` contract layer it describes (`QcModule`, `RawMeasurement`, `QcModuleResult`, `WatchProfile`, `WatchProfileLoader`) was ported into this lineage, but its manual 5-point-tap measurement UI and concrete modules (`GmtTriangle12QcModule`, `IndexGeometryQcModule`) were not — see `README.md` for the architecture decision.


The Android visual master is calibrated against genuine front-on 126710BLNR imagery rather than against QC photos of the watch being inspected.

Primary reference:
- Rolex official GMT-Master II 126710BLNR product/brochure front view, reference m126710blnr-0002.

Cross-check references:
- Genuine 126710BLNR front-on dealer photography from Watch Club / Watchnian / Ginza Rasin used only to sanity-check proportions and placement against the official Rolex image.

Calibration policy:
- Coordinates are normalized to the dial/rehaut radius.
- The master remains fixed. QC photos never alter marker geometry.
- Round applied markers, 6/9 batons and the 12 triangle have independent outer-body and lume geometry.
- This is not Rolex CAD and must not be represented as factory dimensional data.
- Perspective projection is performed after the canonical front-on geometry is defined.
