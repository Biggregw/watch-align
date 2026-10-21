# Watch Align: unified image archive audit and Codex implementation specification

Date: 21 October 2026
Scope: research infrastructure only; design and implementation handoff.
Repository: Biggregw/watch-align

## Decision

Do **not** rewrite both harvesters. They already share code. Introduce a common, model-independent **eligible-image archive** with OneDrive as durable backing storage, retain existing discovery/measurement entry points, and refactor duplicated acquisition plumbing only after parity tests.

**Owner requirement (non-negotiable): the permanent library must contain only genuinely usable, substantially face-on QC/calibration photographs.** Unlike a general image backup, this archive is a curated image dataset. Discard rejected image bytes from the proposed OneDrive archive; keep only rejection counts, source identity, reason codes and retry metadata. Candidate images must be downloaded temporarily to test their suitability, but those that fail must not be permanently archived. Do not migrate historical unsuitable images.

No production QC, geometry, reference, model eligibility or tolerance changes.

## Verified code audit

| Component | Existing behaviour | Recommendation |
| --- | --- | --- |
| `tools/dataset_harvester/harvester/pipeline.py` | Discovers/resolves/downloads sources, computes SHA-256 and perceptual hashes, performs quality screening, measures and decides; `_store` persists exact original bytes under `images/<sha[:2]>/<sha>.<ext>`. | Integrate archive after reliable suitability decision, rather than blindly copying the raw local cache. |
| `tools/dataset_harvester/harvester/quality.py` and `config.py` | Already check dial presence/completeness, image resolution, blur, exposure, glare, marker landmarks, 12 obstruction and app pose. `perspective()` currently reports rehaut and ellipse diagnostics **without rejecting based on those diagnostics**. Existing image size minima (300px shortest side, 200px dial diameter) were designed for permissive acquisition, not precision calibration. | Reuse evidence and tests, but introduce a separate, stricter **archive suitability profile**. Never silently reuse lenient existing `suitable` flag as proof of face-on image suitability. |
| `tools/dataset_harvester/harvester/decide.py` | Watch-level ACCEPT/QUARANTINE/REJECT, independently counting physical watches. | Archive eligibility is an image-level decision, **not** equivalent to watch-level ACCEPT. |
| `tools/dataset_harvester/harvester/state.py` | `sources.jsonl`, `images.jsonl`, SHA-256 indexed ImageRecord, SourceRecord/source-watch links; atomic JSONL writes. | Preserve original schemas/semantics. Keep archive-specific status separately or add backward-compatible optional fields. |
| `tools/dataset_harvester/submariner_acquire.py` | Model-aware acquisition-only tool, no GMT QC; original files are written under `images/<candidate_id>/<index>_<sha-prefix>.<ext>` and `acquired_images.csv`. | Preserve the local path convention. Screen each acquired photo before archive upload with appropriate family-aware quality logic. |
| `tools/watch_calibrator/acquire.py` | Wraps `submariner_acquire`; adds approved dealer gallery/Reddit OAuth image resolving and source-diversity checking. | Preserve source and provenance protections; reuse the common archive layer. |
| `tools/watch_calibrator/run.py` | Per-model discovery/acquisition, source-diversity, locked split, production measurements and genuine envelope. `--fresh` clears model-specific **local workspace**. | Never interpret `--fresh` as deletion from permanent OneDrive; optionally restore eligible originals to expected paths before measurement. |
| `tools/watch_calibrator/production_measure.py` | Reads `acquired_images.csv` and opens `acq_root / local_path`; filters exact duplicates; groups by `physical_watch_id`. | Keep interface unchanged; restore original bytes to that path before measuring. |
| `.github/workflows/dataset-harvester.yml` | Scheduled at 03:17 UTC plus manual runs; state kept on `data/harvest` branch, images only Actions cache. | Keep working while integration is added as opt-in. |
| `.github/workflows/watch-family-calibrator.yml` | Per-model manual runs; photos are ephemeral cache, reports artifact. | Add opt-in eligible-image restore/sync, without recalibration side effects. |
| `tool/bobs-rolex-harvester-fix` | Android `bobsharvester` APK remains a separate collector branch. | Optional ZIP importer into the same screening pipeline. Do not assume APK outputs are already screened sufficiently. |

**Important distinction:** the existing GMT harvester uses a GMT-specific analysis harness. Submariner acquisition deliberately avoids GMT QC assumptions. The shared layer must be *model-independent storage*, with model-aware pose/landmark screening. Adding a family to the archive does not confer app support or calibration eligibility.

## Strict photograph admission policy

Treat discovery, temporary download, screening and acceptance as separate stages:

```text
Discover candidate URLs / ZIP photos
         |
     Temporary bytes
         |
 Exact-byte hash + format / resolution check
         |
 Dial / pose / landmark / occlusion / image quality inspection
         |
  +------+--------------------------+
  |                                 |
PASS calibrated archive policy    FAIL / INCONCLUSIVE
  |                                 |
SHA-256 archive + source record     Reason-only report, NO OneDrive image
```

Only **PASS** uploads original image bytes. Unknown, ambiguous, analyser-failed or unsupported layouts are withheld. No permissive `CORRECTABLE` admission without proving pose correction and measurement stability to a stricter face-on archive standard.

An archived image must meet all applicable requirements:

1. A substantially **face-on** perspective, with both axes assessed: dial ellipse/foreshortening, rehaut asymmetry, and marker lattice/pose. Rotation *within* the image plane is acceptable; tilting away from the camera is not. Existing harvester `perspective()` records diagnostics but does not itself reject off-axis views; the new archive screen must make a documented decision. Avoid using an unvalidated single ellipse-ratio cutoff as a substitute for a validated multi-signal check.
2. Entire dial and all minute-track perimeter required for the intended measurements visible, no crop/truncation.
3. Sharp, sufficiently high-resolution **original** image with readable marker boundaries, not thumbnail or messaging downsample. Existing 200px dial-diameter harvester minimum is **not** a sufficient precision-calibration criterion. Research a more conservative separate threshold against high-resolution representative cases, publish before/after admitted counts and false-accept review; do not alter the app's production resolution gate.
4. Clear hour-marker landmarks and reference geometry. Require all *critical* markers for the selected calibration use-case, including the 12 triangle. A watch can contribute to a subset of marker analyses only if every intended marker is individually marked visible/reliable; it cannot silently contribute an obscured marker. Store per-marker usability flags, and allow a configurable `all_markers_required` strict mode for full-watch population studies.
5. No hand, cyclops, reflection, bezel obstruction or other object covering the marker under evaluation. Avoid outright rejecting every conventional 10:10 dealer image if other intended markers are completely usable; instead admit only for declared measurable subset if the owner enables that mode. Default for the **face-on strict archive** should require a meaningful full-watch set and no ambiguity about the inspected marker coverage.
6. Adequate exposure and colour, limited glare/bloom, sharp metal/lume boundaries, no significant glass reflection, motion blur or depth-of-field distortion.
7. Exact Rolex model reference and trustworthy genuine/replica provenance. Unverified seller claims may only enter a separately **unverified** pool if they independently pass photo geometry screening and owner explicitly enables that pool; never count as established genuine.
8. Distinct physical-watch identity. Several photographs of one watch do not increase the genuine-watch sample count.
9. Genuine catalogue/master renders are `reference_only` and excluded from the independent watch population.
10. Persist the *reason* for rejection without retaining rejected image bytes in OneDrive. Rejected/inconclusive images do not become calibration inputs.

Add an archive-specific decision object with `pass/fail/inconclusive`, documented reason codes, all observable pose/quality metrics, `archive_policy_version`, family/layout assessment profile and `usable_marker_ids`. Every admission must be auditable and reproducible. Make screening **fail closed**. Existing harvested originals must remain untouched until specifically approved for migration; the new archive filter must not delete user's historical local/remote photographs.

**Validate face-on screening empirically** with curated frontal and angled/poor-quality counterexamples from the existing project. Output example thumbnails/pose and histogram diagnostics to CI review *without embedding or publishing private image bytes by default*. Avoid fixed new pose thresholds presented as established truth. Obtain human review of the proposed admission rule and counts before bulk migration.

## Permanent archive contract

Suggested small Python module: `tools/image_archive/`, providing:

- `assess_candidate(path, family, purpose)` which never writes remotely and records the independent image-level archive decision.
- `put_verified(original_file, expected_sha256, association)`: refuses unless latest valid pass decision is present for the same SHA-256 and policy version; create-only immutable SHA-addressed blob.
- `restore(sha256, destination)`: downloads exact bytes, verifies hash, atomically materialises expected local path.
- `reconcile(dry_run=True)`: identifies missing, corrupt, unlinked or pending blobs; no remote deletion.
- `report()`: admitted vs rejected vs inconclusive by model, source, rejection reason, and distinct watches.
- `connectivity_test()`: reads app-root, writes/reads/verifies and cleans up ONLY one unique synthetic owned test file.

Suggested OneDrive app-root structure:

```text
Apps/<registered application name>/WatchAlign/
   objects/<sha-prefix>/<full-sha256>.<original-ext>
   manifests/runs/<unique-run-id>.jsonl
   manifests/exports/<schema-version>/
```

Do not copy each blob into separate model/factory directories. A catalogue maps the same SHA to its source/model/factory/physical-watch identity. Include original URL, listing ID, source classification, image resolution, association source and archive-screen evidence. Preserve original downloaded bytes and original extension. No recompression, resizing or redrawing.

The archive stores **accepted usable images only**, not all downloads. All original download failures/rejections remain in the local metrics/reporting index by source URL for audit and to avoid repeated useless fetches.

Deduplicate blobs by **exact SHA-256 only**; near-duplicate perception is an advisory classification and must not merge independent studio watches. Upload success must follow server acknowledgement and verification; never trust a previously-known filename without validating remote bytes or reliable metadata. A restore always verifies SHA-256. File creation must be conflict-safe. Manifest snapshots must be append-only, versioned or guarded by conditional ETags for concurrent jobs. No automatic remote pruning.

## Personal OneDrive security preflight

Reference:
- Microsoft Graph app folder: https://learn.microsoft.com/graph/onedrive-sharepoint-appfolder
- Permissions reference: https://learn.microsoft.com/en-us/graph/permissions-reference
- App-root API: https://learn.microsoft.com/en-us/graph/api/drive-get-specialfolder?view=graph-rest-1.0
- Upload sessions: https://learn.microsoft.com/en-us/graph/api/driveitem-createuploadsession

Use delegated consumer Microsoft authentication with least-privilege `Files.ReadWrite.AppFolder` plus necessary sign-in/renewal scopes. It is documented for personal accounts but is a preview permission. Recent personal-account reports describe `403 / serviceReadOnly` on some newly consented app registrations. Stop on 403; never silently escalate to `Files.ReadWrite` or `Files.ReadWrite.All`.

Before any real data uploads, run a bounded synthetic write/read/hash-verify/delete test, using phone-friendly interactive authorization. Do not require the user's password or token in chat.

Check whether the chosen MSAL consent flow and refresh-token persistence **actually survive a new GitHub Actions runner**; do not assume one stored static refresh token makes unattended execution permanent. Provide a safe secure renewal/re-authentication mechanism or a clearly manual method until that is proven. No tokens in PR descriptions, logs, Git, artifact ZIPs or OneDrive manifests.

Handle Graph upload sessions, 429 and Retry-After, 503, 507 quota, expired or revoked consent, interrupted chunked uploads, conflicts and concurrent runs. Keep local pending files until verified remote archival. Do not start large migrations automatically.

## Migration evidence inventory (historical and not additive)

The following represent possible catalogue rows, not verified available image bytes:

- `data/harvest` branch source and image JSONL, manifests and measurements. A 2026-10-06 research audit reported **2,974 catalogue entries**, whose original images were not committed and largely missing locally. Current count/availability must be audited afresh.
- `tools/research/alpha96_calibration/` genuine GMT research catalogues with URLs and SHA-256; report described 213 missing provenance-strong photos in the then-local run; later exact-hash refetch recovered a subset, not proof images still reside in a durable artifact.
- `tools/research/sub124060/catalogue_124060.csv`: historical 318 photos, 41 genuine watches (per its README).
- `tools/research/sub126610/catalogue_126610.csv`: historical 358 photos from 307 Bob's Watches listings (per its README); some CDN re-encoded, causing hash mismatches.
- `datasets/126710BLNR/`, `datasets/gmt_phase_b_genuine/`: research manifests and old short-lived workflow artifacts.
- `data/collected` and `testset-inbox` branches, archived Android ZIP packs, current Actions caches/artifacts, and any on-disk verified originals.

**Do not add historical counts together.** They overlap. Inventory per dataset: rows, unique SHA-256 hashes, distinct physical watches, confirmed available original bytes, face-on PASS images and watches, failures/inconclusive, missing sources. A newly re-encoded image is a **different** original, not recovery of an old hash. Do not import until it passes the current strict face-on policy.

## Codex implementation sequence

1. Build local-only, tested candidate screening and archival API. Keep existing harvester outputs unchanged. Make a clear strict **face-on** policy with sample-based review. Do not change production QC thresholds.
2. Add a filesystem mock/provider and tests for archive idempotency, byte integrity, rejected candidate never uploaded, large/full-watch original input, incomplete dial, off-axis perspective, blur/glare/occlusion, inconclusive analysis, duplicate photos across distinct watches, and partial per-marker eligibility.
3. Add OneDrive adapter and opt-in phone-friendly consent/bootstrap, an explicit synthetic smoke test and clear failure reporting. No real photos until approved.
4. Integrate opt-in post-suitability archive write and exact restore for the GMT harvester and Submariner/Watch-Family acquisition path. Existing no-OneDrive operation and manifest schema stay intact.
5. Pilot on a handful of **approved face-on** photos from different models; verify original SHA and restore into each consumer's legacy local path, then show that existing measurement works unchanged.
6. Create dry-run-only historical migration inventory with strict face-on counts. Owner approval required before sending images or deleting local source copies.
7. Only when parity is shown, consider sharing further resolver/downloader utilities. The current GMT and Submariner analysis rules must remain independent.

## Acceptance tests and release boundaries

- No Android app QC, master geometry, thresholds, model eligibility, reference files or calibration results altered.
- Existing harvesters and calibrator still run with OneDrive disabled, producing backward-compatible outputs.
- **Never archive** a photo that fails face-on/quality/landmark screening; never archive inconclusive images.
- No archived image can enter calibration for a marker marked obstructed or unreliable.
- Identical full-resolution bytes restored with matching SHA-256 and original metadata.
- Disconnected/failed OneDrive runs show pending, not success; never discard successfully screened but unarchived bytes before retry.
- No automatic remote deletion; re-run safe; multiple source associations do not merge distinct physical watches.
- Regression tests and mock Graph failure tests pass. Any requirement not live-tested clearly labelled.
- Draft PR with a concise mobile-readable OneDrive account setup guide and instructions for manual connection smoke test.
- **Do not merge, request broader Microsoft permissions, or run a historical migration without owner approval.**

## Codex instruction

Start with this audit, confirm repository head has not diverged, and implement Phase 1 only on a new branch. Document which existing decisions you reused, show the strict face-on admission policy and its evidence, run tests, and open a draft PR. Deliver usable implementation, not just a proposal. Clearly identify anything that needs owner approval or live Microsoft account consent.
