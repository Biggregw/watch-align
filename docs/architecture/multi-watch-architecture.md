# Watch Align multi-watch architecture

## Purpose

Watch Align is moving from a GMT-focused proof of concept toward a general watch QC platform that can support Rolex, Tudor, Omega, AP, Patek, Cartier, IWC, Breitling and other rep-watch families without hard-coding model logic into activities.

The architecture must keep raw geometry, model capability, calibration evidence and QC interpretation separate.

## Core principles

1. The measurement engine must be brand/model agnostic.
2. A watch profile decides which QC modules are applicable.
3. Raw measurements must remain separate from classification and severity.
4. Calibration data must be model/reference specific.
5. Genuine control observations, replica observations and legacy classifier bands must remain distinct.
6. Perspective/pose confidence must gate fine-grained QC claims.
7. Model detection should be separate from QC. Initial UX should be Brand -> Model -> Reference.
8. New watch support should usually mean adding/configuring a profile plus reusable modules, not writing a new monolithic activity.

## Proposed layers

### 1. Generic geometry engine

Responsibilities:

- image coordinates and canonical coordinates
- four-point perspective rectification
- residual/pose confidence
- distances, angles and projections
- normalisation by dial radius, marker width or other model-defined reference
- overlays and visualisation primitives
- measurement uncertainty / repeatability hooks

This layer must know nothing about Rolex, GMTs, AP screws, cyclopses or specific acceptance ranges.

### 2. Watch profiles

Each supported family/reference has a declarative profile, for example:

- Rolex GMT-Master II 126710
- Rolex Submariner 124060
- Omega Seamaster Diver 300M
- AP Royal Oak 15500
- IWC Mark XX

A profile defines brand, family, reference, dial type, capabilities, enabled modules and calibration dataset references. Profiles are versioned JSON loaded through `WatchProfileLoader` and represented by immutable `WatchProfile` objects.

### 3. Reusable QC modules

Implement independent modules with common result contracts.

Initial module set:

- IndexGeometryCheck
- TriangleMarkerCheck
- DoubleMarkerSymmetryCheck
- DateCenteringCheck
- DateApertureGeometryCheck
- CyclopsAlignmentCheck
- BezelAlignmentCheck
- RehautAlignmentCheck
- SelGapCheck
- LogoRotationCheck
- PrintedDialRegistrationCheck
- BezelScrewCheck
- SubdialAlignmentCheck
- HandAlignmentCheck
- LumeShapeCheck
- TimegrapherCheck

Each module returns raw measurements, confidence and evidence. It does not directly decide whether a watch is fake.

### Implemented common contracts

The first migration step is implemented under `com.watchalign.mobile.qc`:

- `RawMeasurement`
- `QcModuleResult`
- `QcModule<I>`

### First reusable production module

`GmtTriangle12QcModule` is the first adapter from existing production measurement code into the reusable module contract.

It delegates directly to `Triangle12RelationalMetric.measureRectifiedRaw` and does not duplicate or alter the GMT geometry calculations. It exposes the five raw production values through `QcModuleResult`:

- `base_to_60_over_base`
- `apex_to_crown_over_base`
- `rotation_deg`
- `height_over_base`
- `lateral_px`

The wrapper deliberately contains no pass/fail or genuine/replica classification.

### Generic per-index geometry

`IndexGeometryQcModule` is the first new brand/model-agnostic geometry module.

It operates only on marker observations already transformed into the canonical rectified dial frame. For each applied hour marker it reports raw measurements for:

- marker-centre radius relative to dial radius
- radial offset from the model/profile supplied expected centre radius
- tangential offset from the expected hour axis
- marker rotation/cant relative to the expected radial axis
- optional marker width and height normalised by dial radius

The module does not contain model-specific tolerances. The expected marker-centre radius is supplied with each observation so a GMT, Submariner, Royal Oak or other applied-index model can reuse the same geometry implementation with separate calibration data.

Rotation is treated as line orientation, so reversing the two axis endpoints cannot turn a correct marker into a 180-degree error. Positive rotation is defined as clockwise in image coordinates.

The module inherits `HIGH`, `MEDIUM` or `LOW` confidence from `PerspectiveConfidenceService` without modifying the raw measurements. This is intended to prevent tiny apparent marker cants caused by oblique photographs from being presented with unjustified confidence.

`QcModuleRegistry` knows about this generic module and now verifies required profile capabilities before returning modules. In particular, `generic.index_geometry` requires `applied-index-geometry`, so a printed-dial profile cannot accidentally run an applied-marker detector merely because a module ID was added to its JSON.

This step provides the measurement layer only. Automatic marker localisation / point placement and model-specific calibration remain separate later work.

### 4. Calibration and reference data

Calibration is versioned and model/reference specific. Keep genuine observations, replica observations, legacy bands, repeatability data and provenance distinct. Do not encode image-derived observations as factory tolerances.

The first production calibration asset is:

`calibrations/rolex/gmt-master-ii/126710/triangle12-corrected-pilot-v2.json`

It stores the current four-watch genuine pilot observations, pilot medians, frozen legacy classifier bands, angular envelope and base-to-60 point-placement p95 error. It is explicitly marked `factoryTolerance: false` and identifies its provenance as image-derived controls.

`Triangle12CalibrationLoader` validates schema version, finite numeric values, observation count and the rule that this image-derived dataset must not be labelled as a factory tolerance.

`FinalQcActivity` no longer owns these pilot/legacy values. It loads the versioned calibration asset and delegates explanatory assessment text to `GmtTriangle12Assessment`. Raw geometry remains unchanged.

### 5. Assessment layer

Assessment consumes raw metric + profile + calibration + confidence. Raw values remain available even if classification logic changes later.

### Perspective confidence

`PerspectiveConfidenceService` is the common brand/model-agnostic confidence gate for four-anchor rectification.

It evaluates the supplied 12/3/6/9 dial-edge quadrilateral using scale-independent geometry: convexity, side-length balance, diagonal balance, diagonal-intersection margin and area relative to the diagonals. It returns `HIGH`, `MEDIUM` or `LOW` confidence plus a 0-1 score and supporting evidence.

The service does not suppress raw measurements and does not classify the watch. A low-confidence image can still produce raw geometry, but fine alignment claims must be treated cautiously. The GMT triangle module is the first module wired to this common service, replacing its previous unconditional `HIGH` confidence.

## Capability matrix

Profiles explicitly declare applicable features so unsupported checks do not run.

## Configuration inheritance

Use data/config inheritance or composition, not deep Java class inheritance.

## Model selection

Initial UX:

1. Brand
2. Model family
3. Reference
4. Load profile
5. Run only supported QC modules

Automatic model recognition can be added later as a separate feature without coupling it to QC logic.

## Migration from current GMT implementation

Do not rewrite everything at once. Migrate incrementally.

### Step 1 - complete
Extract common QC result contracts.

### Step 2 - complete
Define the watch-profile schema and loader.

### Step 3 - complete
Wrap current GMT 12-triangle logic as the first reusable QC module without changing production calculations.

### Step 4 - complete
Create the first declarative profile for modern Rolex GMT 126710-family watches and resolve its enabled modules through `QcModuleRegistry`.

### Step 5 - complete
Move genuine pilot / legacy classifier metadata out of Final QC UI logic into versioned calibration data. The UI now reads the calibration asset instead of owning those values.

### Step 6 - complete
Add a common perspective-confidence service. The first production consumer is `GmtTriangle12QcModule`; raw GMT measurements remain unchanged while confidence now reflects four-anchor geometry quality. Corrected validation passed in workflow run 423.

### Step 7 - complete
Add `IndexGeometryQcModule` as the generic applied-marker geometry engine. It independently measures marker cant, radial position and tangential position for each supplied hour marker in rectified dial coordinates, with optional normalized width/height. It is confidence-gated by the common perspective assessment and registered behind the `applied-index-geometry` capability.

Automatic marker localisation and model-specific acceptance/calibration are intentionally not part of this module.

### Step 8
Add a second model family, ideally Submariner 124060, to prove the architecture is genuinely reusable.

### Step 9
Add a non-Rolex family such as Omega Seamaster or IWC Mark XX to prove capability-based behaviour, especially applied-index vs printed-dial logic.

## Testing requirements

For every module:

- deterministic unit tests for raw geometry
- perspective-rectification tests
- repeatability / point-placement sensitivity tests
- model capability tests proving unsupported modules do not run
- calibration-version tests
- regression tests preserving existing GMT measurements during migration

## Anti-goals

Do not create one giant per-brand QC activity, bury thresholds inside generic geometry, treat Reddit consensus as factory tolerance, reuse a GMT calibration on another family, infer authenticity from one metric, or run all detectors on every watch.

## Recommended implementation order

1. Common result contracts
2. Profile schema + loader
3. GMT 12-triangle module wrapper
4. GMT 126710 profile migration
5. Versioned calibration data
6. Perspective-confidence service
7. Per-index geometry module
8. Submariner profile
9. Date/cyclops modules
10. Bezel/rehaut/SEL modules
11. First non-Rolex profile
12. Model recognition after profile architecture is stable

## Relationship to research

The evidence base under `docs/research/` and issue #13 should drive module priority and calibration work. Architecture should remain stable even as research findings and thresholds evolve.
