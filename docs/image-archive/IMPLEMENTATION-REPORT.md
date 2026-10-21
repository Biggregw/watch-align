# Unified image archive implementation — 21 October 2026

**Review status: draft.** Update (21 Oct 2026, owner refinements): the archive admission profile is now
`face-on-v3` (evidence: [FACE-ON-VALIDATION.md](FACE-ON-VALIDATION.md)). It is archive-only; the Android app's QC
logic, its 200 px floor, measurements and tolerances are unchanged. The rules are:

- an archive floor of 250 px dial radius;
- the app's own per-marker hand check and an outline-coverage envelope for obstruction;
- of the soft edge warnings, only the harmless "inconsistent edge" (reflection) class is accepted.

At 250 px:
- **15 photographs (15 watches; Date 9, No-Date 6, GMT 0)** pass with every marker unobstructed.
- 178 pass if partly hand-covered markers and all soft warnings are allowed.
- 123 pass if every marker must meet the app's reliability standard; that standard still lets hands through.

In most dealer photographs the 10:10 minute hand lies across the 2 marker, and the evidence shows such readings shift.

Counterexamples: all 85 artificial obstructions of previously usable markers are detected, with 0 false accepts.
The recovery pilot found 56 of 100 exact originals, none suitable. No Microsoft account was connected and nothing
was uploaded or migrated.

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

See [FACE-ON-VALIDATION.md](FACE-ON-VALIDATION.md). A PASS needs every required signal affirmatively true:

- **Pose:** tilt ≤ 10.60°; ≥ 13.29° rejected.
- **Resolution:** an archive dial radius of at least 250 px.
- **Framing:** a whole dial and all 12 hour lines placed.
- **Image quality:** sharpness, exposure and glare inside the envelope.
- **Markers:** every required marker measured; not withheld, not hand-confirmed, at least 90 % of its outline seen
  (80–90 % inconclusive); no soft edge warning other than "inconsistent edge".

The first versions admitted the dark-bar counterexample: a bar left 51.6 % of a marker's outline, above the app's 50 %
reconstruction bound, and no hand was confirmed. The coverage envelope, from genuine readings that shift below 80 %,
and the app's hand check now catch every tested bar. All 12 public controls are rejected:
- 94ad as oblique and low resolution;
- the other 11 for a hand-covered or unreliable marker, mostly the minute hand at 2 or a rough 12 triangle.

## Historical recovery

The inventory covers 12,725 overlapping catalogue/association rows and 3,026 distinct
SHA-256 identities. Do not add dataset totals or interpret 785 declared watch IDs as
verified independent watches; 37 shared-photo identity conflicts need review.

| Exact photographs (face-on-v2, before the owner refinements) | Count |
| --- | ---: |
| Recoverable in inspected files/artifact | 384 |
| Suitable (pass) | 56 |
| Fail (96 resolution, 21 hidden or withheld marker, 1 landmarks) | 118 |
| Inconclusive (197 no readable dial in a gallery shot, 13 just outside the tested envelope) | 210 |
| Original hash still missing | 2,642 |

Exact-hash refetch from the catalogued URL also recovers originals. The approved pilot of 100 catalogued missing
originals:
- recovered 56 exact originals (55 GMT, 1 Date; 23 watches);
- found 43 now served with different bytes, 39 of them from Bob's;
- found 1 gone.

None of the 56 is a suitable face-on photograph: half are gallery shots with no dial. The originals are drifting:
21 previously admitted photographs are no longer served byte-exact. A wider recovery needs owner approval.
Current per-photo outcomes under face-on-v3 are in [face-on-evidence.jsonl](face-on-evidence.jsonl).

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
