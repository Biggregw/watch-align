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

A profile should define:

- brand
- model family
- reference
- dial type: applied-index / printed / mixed
- marker topology and expected hour axes
- expected marker shapes
- date aperture presence and nominal location
- cyclops presence
- rotating bezel presence
- rehaut presence
- SEL checks applicable or not
- screw geometry where relevant
- double-marker geometry where relevant
- logo/print orientation checks
- subdial checks
- hand-alignment test support
- timegrapher support and movement metadata where applicable
- enabled QC modules
- calibration dataset IDs and versions

Profiles now live outside measurement code as versioned JSON assets. The initial schema is `android/app/src/main/assets/profiles/watch-profile.schema.json` and is loaded through `com.watchalign.mobile.profile.WatchProfileLoader` into immutable `WatchProfile` objects.

Schema version 1 currently carries:

- `schemaVersion`
- `id`
- `brand`
- `modelFamily`
- `reference`
- `displayName`
- `dialType`: `APPLIED_INDEX`, `PRINTED`, or `MIXED`
- `capabilities`
- `enabledModules`
- `calibrationIds`

Capability and module identifiers intentionally remain strings so new modules can be added without changing the parser contract. The loader rejects unsupported schema versions, missing required fields, unsupported dial types and duplicate capability/module entries.

Suggested structure:

```text
profiles/
  rolex/
    gmt-master-ii/
      126710.json
    submariner/
      124060.json
  omega/
    seamaster-diver-300m.json
  ap/
    royal-oak-15500.json
```

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

Each module should return raw measurements, confidence and evidence. It should not directly decide whether a watch is fake.

### Implemented common contracts

The first migration step is implemented under `com.watchalign.mobile.qc`:

- `RawMeasurement` holds an immutable metric id, finite numeric value and unit.
- `QcModuleResult` holds a module id, immutable raw measurements, confidence and supporting evidence only.
- `QcModule<I>` is the generic interface for reusable measurement modules.

These types intentionally contain no model-specific pass/fail or genuine/replica classification. That remains the responsibility of the later assessment layer.

### 4. Calibration and reference data

Calibration should be versioned and model/reference specific.

For each metric store where available:

- genuine-control population count
- genuine observations/distribution
- replica observations/distribution
- median / quantiles / observed range
- repeatability / point-placement error
- image quality criteria
- calibration version
- provenance/source IDs
- whether data are physical-watch photos or renders
- whether limits are empirical, legacy or manually configured

Do not encode image-derived observations as factory tolerances.

### 5. Assessment layer

Assessment consumes raw metric + profile + calibration + confidence.

Example output:

```text
Raw measurement:
6 marker rotation = +1.14 deg
radial offset = -0.021 marker widths

Reference:
genuine controls n=18
median rotation = +0.08 deg
observed range = -0.42 to +0.51 deg

Assessment:
outside current reference observations
confidence = high
```

The raw value must remain available even if the classification logic changes later.

## Capability matrix

Profiles should explicitly declare applicable features. Example:

| Capability | GMT | Submariner | Datejust | Royal Oak | Seamaster | IWC Mark XX |
| --- | --- | --- | --- | --- | --- | --- |
| Applied-index geometry | yes | yes | yes | yes | yes | no |
| Printed-dial registration | limited | limited | limited | limited | limited | yes |
| Date centring | yes | optional | yes | yes | yes | yes |
| Cyclops | yes | date refs | yes | no | no | no |
| Rotating bezel | yes | yes | no | no | yes | no |
| Rehaut | yes | yes | yes | no | no | no |
| SEL check | yes | yes | yes | bracelet-specific | bracelet-specific | bracelet-specific |
| Bezel screws | no | no | no | yes | no | no |
| Double-marker symmetry | 12 triangle special | 12 triangle special | ref-dependent | ref-dependent | ref-dependent | no |

This matrix should prevent inappropriate checks from running on the wrong model.

## Configuration inheritance

Use data/config inheritance or composition, not deep Java class inheritance.

Example:

```text
RolexSportsWatch
  -> GMTMasterII
      -> 126710BLNR
      -> 126710BLRO
      -> 126711CHNR
  -> Submariner
      -> 124060
      -> 126610
```

BLNR and BLRO can share geometry capability while retaining separate calibration data if evidence shows they differ.

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
Extract a common QC result interface and raw-measurement result type.

Implemented with `RawMeasurement`, `QcModuleResult` and `QcModule<I>`, plus unit tests covering raw-value preservation, immutability and invalid numeric rejection. No existing GMT production calculation has been changed.

### Step 2 - complete
Define the watch-profile schema and loader.

Implemented with immutable `WatchProfile`, validated `WatchProfileLoader`, JSON schema version 1, asset loading support and JVM unit tests.

### Step 3
Wrap the current GMT 12-triangle logic as the first reusable module without changing its production calculations.

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

Do not:

- create one giant per-brand QC activity
- bury reference-specific thresholds inside generic geometry classes
- treat Reddit consensus as manufacturing tolerance
- reuse a GMT calibration on Submariner or other families
- infer authenticity from one geometry metric
- run all detectors on every watch

## Recommended implementation order

1. Common result contracts - complete
2. Profile schema + loader - complete
3. GMT 126710 profile migration
4. Perspective-confidence service
5. Per-index geometry module
6. Submariner profile
7. Date/cyclops modules
8. Bezel/rehaut/SEL modules
9. First non-Rolex profile
10. Model-recognition work only after the profile architecture is stable

## Relationship to research

The evidence base under `docs/research/` and issue #13 should drive module priority and calibration work. Architecture should remain stable even as research findings and thresholds evolve.
