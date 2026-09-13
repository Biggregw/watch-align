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

`GmtTriangle12QcModule` is now the first adapter from existing production measurement code into the reusable module contract.

Important guardrail: it delegates directly to `Triangle12RelationalMetric.measureRectifiedRaw` and does not duplicate or alter the GMT geometry calculations. It exposes the five raw production values through `QcModuleResult`:

- `base_to_60_over_base`
- `apex_to_crown_over_base`
- `rotation_deg`
- `height_over_base`
- `lateral_px`

The wrapper deliberately contains no pass/fail or genuine/replica classification. Unit tests compare every wrapped raw value exactly against the legacy production result and verify caller mutation cannot alter a captured input.

### 4. Calibration and reference data

Calibration is versioned and model/reference specific. Keep genuine observations, replica observations, legacy bands, repeatability data and provenance distinct. Do not encode image-derived observations as factory tolerances.

### 5. Assessment layer

Assessment consumes raw metric + profile + calibration + confidence. Raw values remain available even if classification logic changes later.

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

Implemented with immutable `WatchProfile`, validated `WatchProfileLoader`, versioned JSON schema support, capabilities/modules/calibration references and unit tests.

### Step 3 - complete
Wrap current GMT 12-triangle logic as the first reusable QC module without changing production calculations.

Implemented with `GmtTriangle12QcModule`, which delegates to `Triangle12RelationalMetric.measureRectifiedRaw`. Regression tests compare all raw values exactly against the legacy production result.

### Step 4
Create the first declarative profile for modern Rolex GMT 126710-family watches.

### Step 5
Move genuine pilot / legacy classifier metadata out of UI logic into versioned calibration/profile data.

### Step 6
Add a perspective-confidence service used by all modules.

### Step 7
Implement per-index geometry as the first new generic module.

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
5. Perspective-confidence service
6. Per-index geometry module
7. Submariner profile
8. Date/cyclops modules
9. Bezel/rehaut/SEL modules
10. First non-Rolex profile
11. Model recognition after profile architecture is stable

## Relationship to research

The evidence base under `docs/research/` and issue #13 should drive module priority and calibration work. Architecture should remain stable even as research findings and thresholds evolve.
