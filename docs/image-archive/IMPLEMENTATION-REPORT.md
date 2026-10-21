# Unified image archive implementation — 21 October 2026

**Review status: draft.** Update (21 Oct 2026, continued): the face-on admission profile
`face-on-v2` is validated and promoted (evidence: [FACE-ON-VALIDATION.md](FACE-ON-VALIDATION.md)).
It admits 56 of the 384 recovered historical originals and 204 of 273 reviewer-eligible
photographs, 217 unique photographs in all. Every reviewer-ineligible photograph is rejected. Of 144
controlled counterexamples, one was admitted (a dark bar over a marker); see the validation document. No personal Microsoft account was connected and nothing
was uploaded or migrated. The owner decisions are listed in that document.

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

Superseded by [FACE-ON-VALIDATION.md](FACE-ON-VALIDATION.md). In summary, a PASS needs every required signal
affirmatively true inside the envelope of 263 reviewed near-frontal genuine photographs (217 watches) whose production
measurements stayed clean. A known failure rejects, and anything untested is inconclusive. Required for a PASS:

- **Tilt:** at most 10.60°. From 13.29° up, every reviewed genuine photograph produced strict-genuine false failures.
- **Resolution:** the frozen 200 px dial-radius floor.
- **Framing:** a whole dial and all 12 hour lines placed from the minute track.
- **Image quality:** harvester and envelope sharpness, exposure and glare bounds.
- **Markers:** every required marker measured, not withheld and not more than half hidden.

The first version admitted no photograph because of two defects:
- it required a tilt from round markers, which have none;
- it treated a soft edge-fit warning as an obstruction.

All 12 public controls now match their reviews: 8 pass, and 4 fail. The 4 failures are the oblique 94ad, and three with
a hand over a marker.

## Historical recovery

The inventory covers 12,725 overlapping catalogue/association rows and 3,026 distinct
SHA-256 identities. Do not add dataset totals or interpret 785 declared watch IDs as
verified independent watches; 37 shared-photo identity conflicts need review.

| Exact photographs (face-on-v2) | Count |
| --- | ---: |
| Recoverable in inspected files/artifact | 384 |
| Suitable (pass) | 56 |
| Fail (96 resolution, 21 hidden or withheld marker, 1 landmarks) | 118 |
| Inconclusive (197 no readable dial in a gallery shot, 13 just outside the tested envelope) | 210 |
| Original hash still missing | 2,642 |

Exact-hash refetch from the catalogued URL also recovers originals. It recovered 313 of the 556 reviewed photographs;
Bob's image CDN now serves different bytes for most of the 228-photo additional review. Running it over the 2,642
missing hashes is a further recovery step for the owner to approve.

Four pinned public fixtures plus ten originals in the existing review ZIP yield
12 unique committed originals. GitHub artifact `11300604104`,
`watch-calibrator-124060-37194686346`, contains extensionless SHA-addressed originals;
checking its actual bytes increases recovery to 384. No source-photo refetch or
re-encoded substitution was used. The native run made 464 image/model assessments
with zero analyzer exceptions; unique-photo status gives failed assessments precedence
over inconclusive ones, so photo totals are mutually exclusive.

[historical-inventory.json](historical-inventory.json) is the original dry run, made under the unpromoted profile:
its suitable=0 predates face-on-v2. The current per-photo outcomes are in
[face-on-evidence.jsonl](face-on-evidence.jsonl), reproduced by `python -m tools.image_archive.analyse_validation`.

All 1,941 currently listed artifacts and 55 caches were enumerated. Eight image caches
are listed, but their contents were not fetched through an Actions runner. Other
artifact names/sizes do not count as recovered photographs.
[Dataset/model/provenance/source totals](historical-inventory.json) and
[GitHub availability](github-availability.json) document these boundaries.

## Verification

- 175 Python archive/harvester/calibrator tests pass, including the native JDK 17/OpenCV photographic tests
  (`ARCHIVE_NATIVE_PHOTO_TESTS=1`). They cover the face-on-v2 geometry and envelope, marker obstruction, the public
  controls and the committed validation counts. The original 158 tests are retained.
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

Before real collection: owner review of the admitted photographs and decisions in
FACE-ON-VALIDATION.md §6, then explicit owner authorization for the personal-account synthetic test and two-runner
renewal test, followed by an approved small real-photo pilot. Bulk migration requires
separate approval. This branch is not merged and no migration was executed.
