# Measurement Contract v2 and Coverage Funnel Design

Date: 2026-10-03
Status: approved governing design for the next calibration-foundation stage. No implementation is implied by this document.

Schema v2 makes the reliability gate the single author of every metric state and reason, keeps raw and eligible values distinct, and adds a complete coverage funnel plus per-metric sufficiency reporting. Current 124060 eligibility, calibration bands and Android QC behaviour must remain unchanged during the migration.

## 1. Ownership and architecture

The generic core owns structure and identity. The shared Java family core owns family meaning and every family decision. The Python adapter only orchestrates and serialises. The calibration engine owns admission, outlier handling and limits. Reporting never feeds calibration decisions.

### Generic core

Owns:
- record types and validation
- stage IDs and closed outcome sets
- reason-code namespace rules and generic reason classes
- stable photo identity from frozen evidence
- adapter registry and fingerprints
- eligible-to-engine bridge
- funnel reconciliation and sufficiency arithmetic
- generic layout result validation and quarantine admission policy

Never:
- knows dial shape, marker types or metric meaning
- computes family geometry
- decides family reliability

### Shared Java family core

For Submariner 12-series this is the code path centred on `Sub124060QcAnalyzer` and authoritative `Sub124060Calibration.decide()`.

It is the only implementation of:
- detector and geometry results
- raw metrics
- reliability gates and gate order
- accepted / withheld / unavailable state
- primary and secondary reason selection
- eligible values
- family layout evidence and compatibility

It must not receive genuine/replica class, partition, calibration role or separation results.

### Android, desktop harness and Python adapter

Android consumes the shared Java decision for verdict inputs, summary and overlay presentation.

The desktop harness compiles and invokes the same Java family core.

`submariner12_measured_v2` and the v2 desktop driver may orchestrate, validate, join provenance and serialise only. They must not reimplement geometry, thresholds, gate predicates, gate order, state or reason selection.

A future change to 3/9, 12/6, round-marker or 12-o'clock reliability is therefore made once and inherited by Android and calibration together.

## 2. Authoritative decision API

`Sub124060Calibration.decide()` is the authoritative per-metric decision API.

Each metric decision returns:
- raw value
- state: `accepted`, `withheld` or `unavailable`
- primary reason code
- optional reason subject
- ordered secondary codes for conditions already evaluated by the normal path
- optional human-readable reason detail
- eligible value
- support information
- diagnostics

`assess()` survives only as a mechanical compatibility projection of `decide()`. It must contain no independent condition, threshold or reason logic.

Decision behaviour is class and partition blind:
- no genuine/replica input
- no development/validation/holdout input
- no calibration role input
- no separation signal input
- no state carried between photos

Relabelling, reordering or re-batching the same frozen photos must not alter decisions.

## 3. Measurement contract v2

Format: canonical JSON Lines, one file per partition and class.

Each file contains:
1. exactly one header
2. exactly one photo record per requested photo
3. exactly one metric record per photo and configured metric

Calibration outcomes are written to a separate engine-owned file so the measurement contract remains immutable after measurement.

### Header

Must include:
- `schema = watch_align.measurement_contract`
- `schema_version = 2`
- snapshot ID
- claimed model and family
- partition and class label
- adapter ID/version
- reliability policy ID/version
- metric catalogue ID/version/hash
- reason catalogue IDs/versions/hashes
- stage map
- measurement fingerprint bound to adapter, policy, catalogues and checkout

### Metric catalogue

Family-owned, versioned semantics for each metric:
- metric ID
- metric version
- unit
- raw definition
- eligible definition
- eligible relation: `identical_to_raw` or `recomputed_on_reliable_support`
- declared diagnostics and their types/units

### Photo record

Must include:
- workspace-independent `photo_key` from manifest local path
- image SHA-256
- physical watch ID
- provenance
- ordered photo-stage outcomes
- layout evidence block

### Metric record

Must include:
- photo key and metric ID
- state
- terminal stage
- origin: photo or metric
- reason code, subject, secondary codes, detail
- raw value
- eligible value
- raw and eligible support
- declared diagnostics only

### Raw versus eligible

`raw_value` is the metric's own pre-reliability quantity.

`eligible_value` is the value admitted by reliability policy.

They are not required to be equal. Accepted round-marker metrics may be recomputed using only repeatable marker support. Equality is enforced only for catalogue entries declared `identical_to_raw`.

A value measuring a different geometric quantity is never used as raw. For example, an inner lume outline is not a substitute for the intended 12 gap edge.

## 4. Canonical JSON Lines

The serializer must be deterministic:
- UTF-8, no BOM
- LF line endings only
- one object per line and final LF present
- non-ASCII strings NFC-normalised and escaped deterministically
- object keys sorted at every nesting level
- no whitespace outside strings
- finite numbers only
- negative zero canonicalised to `0.0`
- Java measurement boundary remains six fractional digits where required for v1 parity
- parsed numeric values serialise deterministically
- numbers typed as numbers, counts as integers
- declared null fields written as `null`, not omitted
- header first
- photo records sorted by photo key
- metric records sorted by metric ID directly after each photo
- semantically ordered arrays preserve their defined order
- all other arrays use a declared deterministic sort key

Parsing and reserialising a valid file must reproduce identical bytes.

## 5. Reason codes and precedence

Every non-accepted metric has a machine-readable namespaced reason code.

Namespaces:
- `core.*` for generic conditions
- family namespace such as `sub12.*` for family detector/gate conditions
- `calib.*` for calibration admission/outcome conditions

Each namespace has a versioned catalogue. Catalogue hashes are part of the measurement fingerprint. A reason meaning is never silently repurposed.

The core recognises a small family-agnostic reason-class set for reporting, for example input, not_found, occluded, out_of_range_geometry, unstable, layout_conflict, policy and internal_error.

### Decision 8: confirmed stage-local precedence

Primary reason is the first failing condition in the existing authoritative order that produced the terminal state:
- `unavailable`: first failure in the existing raw-production path
- `withheld`: first failure in the existing reliability-gate path

Do not use one combined precedence order.

`secondary_codes` contains only additional failures the normal execution path already evaluated, in existing gate order. Do not execute extra detectors or resize checks merely to populate diagnostics.

The function deciding state also owns the authoritative reason. The desktop driver and Python adapter contain no reason reconstruction.

## 6. Coverage funnel

The complete funnel joins four sources by stable identity:
1. acquisition ledger
2. frozen evidence snapshot and locked split
3. measurement contract
4. calibration outcome file

Stage 0 acquisition is upstream of the measurement contract. A failed download has no image bytes, so it cannot have a measurement photo record or image hash.

Core stages:
0. acquisition, upstream ledger
1. acquired in snapshot/split
2. readable
3. preflight
4. analysis region found
5. geometry usable
6. layout evidence
7. metric raw production
8. metric reliability
9. calibration admission
10. calibration outcome

A metric never disappears silently. Every terminal path has a stage and reason.

State semantics:
- `unavailable`: no meaningful raw measurement exists
- `withheld`: raw measurement exists but reliability does not admit it
- `accepted`: eligible value exists
- `rejected_outlier`: measurement remains reliability-accepted, but calibration later excluded it from the genuine envelope

Outlier rejection never rewrites the immutable measurement contract.

Funnel reconciliation must hold exactly, including:
- population = unavailable + withheld + accepted
- accepted = admitted + excluded
- admitted genuine = retained + rejected_outlier

Reports roll up by metric, class, partition, physical watch, source and photo.

## 7. Layout evidence and quarantine

Keep separate:
- claimed model
- expected layout
- positive/negative observations
- layout hypotheses
- compatibility result
- layout reliability
- quarantine state

Absence is not positive evidence. `not_detected` alone never proves compatibility or incompatibility.

Compatibility states:
- compatible
- unresolved
- incompatible
- conflicting

The family evidence/rule is computed in shared Java. Python re-checks the result only as an invariant, not as a second decision path.

For Submariner 12-series:
- confident 3 baton supports no-date layout
- future confident date-window evidence supports date layout
- future confident cyclops evidence supports date layout
- neither positive detection means unresolved, not guessed
- contradictory positive evidence becomes conflicting

Initial quarantine enforcement is report-only. Enforcement is a separately approved behaviour change followed by frozen-evidence recalibration.

## 8. Evidence sufficiency

Each metric has three separate dimensions:

### Coverage
Descriptive measurement reach:
- photo population
- photos with raw
- accepted / withheld / unavailable photos
- watch population
- watches with raw
- watches with accepted values
- reason breakdowns

### Calibration sufficiency
Based on retained genuine evidence and physical-watch independence:
- contributing watches
- retained and rejected-outlier photos
- contributing and qualifying sources
- dominant source share by watches, not photos
- multi-photo watches
- development/validation/holdout support
- explicit threshold/criterion results

### QC usefulness
Replica stress only:
- replica watches/photos/factories
- outside-clear / outside-check counts and rates against genuine limits

A metric may be well calibrated and still show no useful replica separation. Calibration completion never promotes a metric automatically.

## 9. Duplicate and independence policy

Schema v2 records the deduplication policy ID/version used by each sufficiency report.

Current SHA-256 exact duplicate handling detects identical bytes only. It does not solve:
- resized or re-encoded copies
- cropped copies
- re-photographs of the same watch
- the same physical watch listed by different dealers

Perceptual/near-duplicate clustering and stronger physical-watch independence remain a separate foundation workstream. Do not overclaim independence from SHA-256 alone.

## 10. Migration from v1

V2 ships beside v1 and must prove equivalence before replacing it.

Migration steps:
1. keep v1 readable and replayable
2. add versioned metric/reason catalogues
3. make `decide()` authoritative and `assess()` a mechanical projection
4. add `submariner12_measured_v2` and canonical JSONL output
5. replay frozen evidence through v1 and v2 in the same CI run
6. add calibration outcome, funnel and sufficiency outputs without changing `calibration.json`
7. switch live measurement to v2 only after explicit approval
8. later approve independently: direct v2 engine input, quarantine enforcement, preflight enforcement and reliability changes

Required replay proof on frozen snapshot `ca23588a...94610`:
- legacy calibration remains byte-identical to known SHA `3ae71e98a533be4e87cbdfc48b0d893118ebe2c6d761eeb34ca981a90fb2b796`
- v1/v2 accepted set identical by photo, metric and eligible value
- every v1 raw value that exists equals the v2 raw value
- only documented `twelve.*` observability reclassification is allowed: v1 unavailable -> v2 withheld where pre-reliability triangle raw exists
- any other state change fails
- every v1 free-text reason maps to a v2 reason code
- detector and resize-check counts per photo remain unchanged
- Android and desktop decisions agree on fixture/frozen evidence

## 11. Behaviour-preserving 124060 mapping

V2 adds observability and reason ownership, not new conditions or thresholds.

Important mapping points:
- 12 rotation/gap/centring raw values come from the pre-reliability triangle candidate when that candidate is the same intended quantity
- accepted eligible values remain today's gated values
- round ring/spacing raw values use all valid raw marker support, while eligible values may be recomputed on repeatable marker support
- 3/9 raw is the relational offset from detected 3 and 9 geometry before the repeatability gate
- 12/6 raw is the relational offset from 12 and 6 geometry before the repeatability gate

For the three `twelve.*` metrics, v1 currently stores values only after resize reliability passes. V2 may therefore reclassify some v1 `unavailable` rows as `withheld` while preserving the same eligible set and calibration behaviour.

## 12. Production app implications / deferred Android QC follow-up

This migration records production findings but does not change visible QC behaviour.

Documented findings:
1. pre-reliability 12 geometry is currently lost downstream when the resize gate fails
2. checks described in analyser notes as diagnostic-only are later reused as eligibility gates
3. reason ownership is split between analyser messages, calibration eligibility, desktop reconstruction and Android overlay reconstruction
4. 3/9 and 12/6 relational checks are especially affected
5. `Sub124060Overlay.unjudgedBatonNote()` can show `reading not steady enough` for several distinct underlying causes

Run #52 evidence motivating later reliability work:
- 70 genuine photos had raw 3/9 geometry; 38 were withheld, including 32 for baton resize repeatability and 6 for dial-edge reproducibility
- 55 genuine photos had raw 12/6 geometry; 25 were withheld, including 15 baton resize, 6 dial-edge and 4 12-chain cases

Deferred production principles:
- preserve raw geometry before reliability gating where technically possible
- UI wording should ultimately reflect the authoritative shared reason
- do not loosen thresholds as a shortcut
- any reliability change must be rerun against frozen evidence and recalibrated before verdict behaviour changes
- a shared reliability change must reach Android and calibration together through the one authoritative implementation

Visible Android wording remains unchanged during the v2 migration. Fixing the misleading message is a separately approved product change.

## 13. Observational preflight

After schema v2, preflight is the next planned stage before 3/9 reliability work.

The first implementation is benchmark/report-only. It may emit `would_reject` diagnostics but may not reject evidence.

Enforcement requires:
- frozen-evidence evaluation
- proof that no protected currently useful photo is removed, where protected means any photo with a raw or accepted value under the current route
- explicit approval

## 14. Deferred GMT parity audit

Do not modify GMT during the Submariner schema-v2 implementation.

After the Submariner shared-decision architecture is proven, audit the GMT path against the same architectural principle without assuming GMT has the same defects.

Trace:
`GMT detector -> raw geometry -> reliability -> authoritative reason/state -> eligible/verdict input -> Android summary/overlay -> desktop/calibration path`

Audit for:
- UI reason reconstruction
- duplicated gates or thresholds
- raw geometry lost before reliability
- Android/calibration divergence

GMT and Submariner keep family-specific algorithms. The shared requirement is one authoritative implementation per family with consistent consumers.

## 15. Validator and regression requirements

At minimum, reject:
- adapter/policy/snapshot/catalogue identity mismatch
- missing, duplicate or extra photo/metric rows
- invalid stage order or blocked/pass contradictions
- unavailable with raw
- withheld without raw or with eligible value
- accepted without both raw and eligible
- unregistered or wrongly namespaced reason codes
- undeclared diagnostics
- NaN/infinity/numeric strings
- absolute/workspace paths
- invalid layout compatibility evidence
- admission/outcome mismatch
- engine input differing from admitted eligible set
- non-canonical JSONL bytes

Regression/adversarial proof must include:
- Android and desktop identical decisions on the same photos
- static guard that driver/Python adapter contain no rule logic
- mutation test proving one shared reliability-rule change affects both consumers
- reflection test that `decide()` cannot receive class/partition/role/separation inputs
- relabel/reorder/rebatch invariance
- unchanged detector/resize-check execution counts
- short-circuit secondary-code behaviour
- catalogue constant/catalogue-file consistency
- v1/v2 frozen replay equivalence
- square/rectangular synthetic future family proving the generic core needs no watch-shape-specific change

## 16. Approved decisions

1. Gate-owned reasons: approved in principle through authoritative shared Java `decide()`.
2. Contract format: canonical JSON Lines.
3. `twelve.*` observability: report true raw candidates as withheld where reliability fails; eligibility unchanged.
4. Inner lume-outline-only gap: unavailable because it is a different quantity.
5. 3-baton presence for no-date evidence: FOUND is enough for presence evidence; fine-geometry repeatability is separate.
6. Quarantine: photo-level and report-only first.
7. Raw/eligible equality: enforce only where catalogue declares `identical_to_raw`.
8. Reason precedence: confirmed stage-local order, raw-production order for unavailable and reliability-gate order for withheld. Secondary codes only for already-evaluated conditions.
9. Reason classes: approve the small generic class set.
10. Calibration outcomes: separate engine-written file.
11. Stable photo identity: manifest local path plus SHA-256, with duplicate-content flags.
12. Metric semantics: family catalogue shipped with shared family core; config keeps IDs and calibration policy.
13. Next stage after v2: observational preflight, then 3/9 reliability work.
14. Near-duplicate/physical-watch independence: separate foundation workstream; v2 records only the policy relied on.

## 17. Implementation guardrail

This document is the governing design for the v2 implementation branch. If implementation requires a material deviation from these rules, stop and review the design change explicitly rather than silently changing architecture.

Do not change during the behaviour-preserving v2 migration:
- GMT behaviour
- QC thresholds
- reliability criteria
- calibration bands
- visible Android wording
- model routing
- production promotion policy
