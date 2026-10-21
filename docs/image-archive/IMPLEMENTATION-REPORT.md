# Unified image archive implementation — 21 October 2026

**Review status: draft; the full acceptance baseline is not yet satisfied.**
The archive infrastructure is implemented, but no photographic admission profile
has enough validated evidence to be promoted. It currently admits zero real images.
This prevents an unsafe library, but does not yet deliver a usable positive-admission
system for ongoing collection. No personal Microsoft account was connected.

## What was built

| Stage | Delivered | Remaining boundary |
| --- | --- | --- |
| A — screening | Independent fail-closed decisions; original integrity; family-specific read-only measurements; explicit per-marker coverage; twelve photographic controls and historical diagnostics | Validated multi-signal positive admission, precision-resolution criterion, complete perimeter/reflection checks and broader held-out photographs |
| B — archive | SHA-256 immutable originals, transactional metadata, exact dedup, identity conflicts, pending retries, restoration, manifest recovery and reconciliation | Real-photo positive-path pilot after profile validation |
| C — OneDrive | App-folder Graph upload sessions, chunk/retry/conflict/quota handling, verified downloads, synthetic cleanup, MSAL device consent and encrypted refresh-cache renewal | Owner-authorized personal-account smoke test and renewal across two fresh runners |
| D — harvesters | Optional common archive hooks for new acquisitions; CSV adapter for existing/Bob's output; existing schemas retained | No live sync enabled |
| E — calibration | Restore selected originals to existing CSV `local_path`; full marker eligibility and identity checks | No real-photo archive pilot; no recalibration triggered |
| F — history | Dry-run inventory across current catalogues, historical branches, ZIP originals, GitHub artifact and cache availability | Other artifact contents/cache originals not assumed recovered; no migration |

## Reused components

The GMT `Pipeline`, existing downloader/resolvers, exact-byte local storage and
metadata source rules; `submariner_acquire`; Watch-Family dealer/Reddit resolving and
source-diversity logic; `Suit`/quality/rehaut diagnostics; model-specific `TrackDump`
and the existing native analysis. Acquisition was not rebuilt. Current production
QC decisions and measurements were not changed.

## Photograph rules and results

A PASS requires all applicable face-on cues (ellipse, both rehaut axes and marker
lattice), complete dial/minute perimeter, original precision resolution, sharp
boundaries, adequate exposure, limited glare/reflection, reliable landmarks and
all 11 Date or 12 No-Date hour markers. Unknown evidence is withheld. Partial-marker
admission is disabled because the legacy calibrator cannot enforce masks.

The existing reviewed 400-pixel dial floor is used only to identify definite
insufficient-resolution cases. No new arbitrary pose cutoff is represented as
validated. No production resolution gate or tolerance changes.

Twelve existing public originals were inspected with exact hashes:

- Eleven have prior near-frontal pose/resolution labels. Nine fail stricter
  marker-visibility/reliability checks; two layouts are unsupported/inconclusive.
- The documented oblique `94ad771e9e2aedde` image fails resolution and marker checks.
- No image passes. This is **not** an estimate of a validated classifier's false-accept
  rate: the production positive-admission registry is empty.
- The GMT `49ba843c0a7e19d7` control is historically `suitable=true` with `CORRECTABLE`
  pose. Its top/bottom rehaut ratio is 0.483 and left/right ratio 1.647. The archive
  diagnostic withholds it rather than treating that historical flag as proof.

[Full photographic metrics](photographic-validation.json) contain no new image bytes.

## Historical recovery

The inventory covers 12,725 overlapping catalogue/association rows and 3,026 distinct
SHA-256 identities. Do not add dataset totals or interpret 785 declared watch IDs as
verified independent watches; 37 shared-photo identity conflicts need review.

| Exact photographs | Count |
| --- | ---: |
| Recoverable in inspected files/artifact | 384 |
| Fail current diagnostic checks | 108 |
| Inconclusive | 276 |
| Suitable/admitted | 0 |
| Original hash still missing | 2,642 |

Four pinned public fixtures plus ten originals in the existing review ZIP yield
12 unique committed originals. GitHub artifact `11300604104`,
`watch-calibrator-124060-37194686346`, contains extensionless SHA-addressed originals;
checking its actual bytes increases recovery to 384. No source-photo refetch or
re-encoded substitution was used. The native run made 464 image/model assessments
with zero analyzer exceptions; unique-photo status gives failed assessments precedence
over inconclusive ones, so photo totals are mutually exclusive.

All 1,941 currently listed artifacts and 55 caches were enumerated. Eight image caches
are listed, but their contents were not fetched through an Actions runner. Other
artifact names/sizes do not count as recovered photographs.
[Dataset/model/provenance/source totals](historical-inventory.json) and
[GitHub availability](github-availability.json) document these boundaries.

## Verification

- 158 Python archive/harvester/calibrator tests passed; the optional native test is
  separately run, not weakened or deleted.
- Two photographic test cases passed with JDK 17/OpenCV (including a live re-analysis
  of the frontal GMT and oblique Submariner controls).
- Five existing marker-audit integrity tests passed.
- Mock coverage includes app-folder auth, cache deserialization/rotation/new-runner
  setup, failed cache persistence, revoked consent, 401/403/507, 429/503 and transport
  retries, chunk resume, immutable conflicts, corrupt upload/restore, signed-URL
  credential separation, pagination, synthetic cleanup and exact byte preservation.
- Source diff against `d585fb3e` confirms no changes under `android/` or `calibration/`,
  or to `production_measure.py`, calibration/envelope code or calibrator `run.py`.
- The pre-existing calibrator PR workflow was cancelled during unit tests before
  autonomous discovery/calibration. Its calibration and image-cache steps are now
  manual-dispatch-only; PR regression tests remain enabled.
- Existing Pillow deprecation warnings remain. No live Microsoft behaviour or
  real-photo archive-to-calibrator measurement parity is claimed.

## Owner setup and next work

[Android-phone-friendly consent, GitHub secrets and manual synthetic-test instructions](../../tools/image_archive/README.md#onedrive-authorization-from-an-android-phone)
are included. Only delegated `Files.ReadWrite.AppFolder` is requested; a 403 stops the
process without escalation. The workflow contains no real-photo upload step.

Before real collection: validate/promote the full-watch admission profile, then obtain
explicit owner authorization for the personal-account synthetic test and two-runner
renewal test, followed by an approved small real-photo pilot. Bulk migration requires
separate approval. This branch is not merged and no migration was executed.
