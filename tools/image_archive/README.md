# Eligible original-image archive (research infrastructure)

**Draft.** Storage, Graph transport, credential renewal, acquisition hooks and calibration
restoration are implemented. The production screener uses the validated face-on admission
profile `face-on-v2` (GMT 126710, Submariner Date 126610 family and No-Date 124060); its
evidence and limitations are in [FACE-ON-VALIDATION.md](../../docs/image-archive/FACE-ON-VALIDATION.md).
Live collection still needs the owner decisions listed there and explicit Microsoft
authorization.

This implements the committed [handoff](../../docs/architecture/onedrive-image-archive-handoff-2026-10-21.md)
on a branch based on audit commit `d585fb3e`. The owner's current request extends the
handoff's closing Phase-1-only instruction to all feasible stages. Nothing under
`android/`, production measurement, model assets, genuine references or calibration
limits is changed. Existing acquisition and calibration commands work unchanged.

## Components

- `screening.py`: independent image-level PASS / FAIL / INCONCLUSIVE, exact byte hash,
  decode/integrity, policy/profile, all required checks, per-marker eligibility.
- `diagnostics.py`: reuses existing `Suit`, quality diagnostics and model-specific
  `TrackDump`. These are read-only observations, never a production geometry change.
- `archive.py`: SQLite decision/association ledger, SHA-addressed immutable originals,
  create-only filesystem provider, pending uploads, incremental retry, verified atomic
  restoration, reconciliation and append-only remote manifest snapshots.
- `onedrive.py`: consumer Graph app-folder backend, conflict-failing upload sessions,
  chunking, bounded retries, quota/consent failures, hash-verified restoration,
  paginated snapshot discovery and a synthetic-only connection test.
- `auth.py`: MSAL consumer device flow, secure external cache, encrypted GitHub-secret
  renewal. No unattended device-login fallback and no broader permission fallback.
- `integration.py`: existing acquired-image CSV import and restoration into its exact
  `local_path`; no discovery, measurement, recalibration or deletion.
- `inventory.py`: dry-run catalogue/hash inventory, including originals in ZIPs with
  extensionless SHA filenames and read-only historical Git branches.

The existing GMT harvester's discovery, URL resolvers, download/storage paths,
metadata/provenance inference and watch-level decisions are retained. Submariner
acquisition and its Watch-Family wrapper retain dealer/Reddit resolvers, source-diversity
checks and their CSV schema. Optional `archive=` arguments write a separate archive
sidecar. Bob's Watches output uses the same manifest adapter; generic extracted ZIP
originals must have an explicit metadata manifest and pass the same screen. No new
acquisition mechanism or APK changes are introduced.

## Admission contract (face-on-v2)

A PASS needs affirmative evidence for **all** of:

1. **Face-on.** The dial tilt from the minute-track registration is at most 10.60°, and the dial-edge ellipse and both
   perspective gradients are inside the validated envelope. The hour markers confirm the pose.
2. **Complete dial and minute track.** The whole dial edge is in the frame and all 12 hour lines are placed.
3. **Resolution.** The dial radius is at least 200 px (the frozen app floor) and the short side at least 400 px.
4. **Image quality.** Sharpness, exposure and glare are inside the validated envelope, and no marker is withheld for
   glare.
5. **Every actual hour marker** (12 on No-Date, 11 excluding 3 on Date layouts) is measured. None may be withheld (hand,
   glare, Can't check) or have less than half of its outline seen.
6. **Identity.** Exact model, physical-watch identity, source URL and accepted provenance/class.

How the bounds are set:
- Every bound comes from `faceon.ENVELOPE`, the evidence in FACE-ON-VALIDATION.md, or an existing documented bound.
- Beyond a failure bound (for example, tilt of 13.29° or more) the photo is rejected.
- Between the envelope and a failure bound, the range is untested, so the photo is **inconclusive**. Missing,
  non-finite, unsupported and ambiguous evidence is also inconclusive.
- The historical `suitable` flag and the `GOOD`/`CORRECTABLE` pose labels never admit an image.

`faceon.VALIDATED_PROFILES` is the only registry. No CLI option, environment variable or manifest field changes it.
Tests also inject a test-only synthetic analyzer to exercise storage. Yacht-Master and other unsupported layouts remain
inconclusive.

Partial-marker admission is deliberately disabled. The legacy calibrator cannot
honour a marker eligibility mask, so it must not receive partial-coverage images.
The decision still records precisely which markers are observed usable. Production
QC may assess partial photographs as before; this separate archive policy does not
alter that behaviour.

Accepted provenance is reused from the harvester's source rules or explicit trusted
association: established dealer, auction house, Rolex CPO, official, owner-verified,
or labelled replica. Unknown seller claims remain withheld. Official material needs
an explicit sample role; catalogue/master renders use `reference_only` and contribute
zero independent watches. No unverified pool is enabled.

## Local use

From the repository root, with Python 3.11+:

```sh
python -m pip install -r tools/image_archive/requirements.txt
python -m tools.image_archive assess photo.jpg --family 124060
# Optional detailed legacy GMT diagnostics (JDK 17 required):
python -m tools.image_archive assess photo.jpg --family 126710BLNR --gmt-diagnostics
python -m tools.image_archive --root /private/archive ingest-local selected.csv --images-root /private/acquisition
python -m tools.image_archive --root /private/archive report
python -m tools.image_archive --root /private/archive reconcile
python -m tools.image_archive --root /private/archive sync-local
python -m tools.image_archive --root /private/archive restore-local selected.csv --images-root /private/experiment
```

The standalone `assess` command and archive submissions automatically run
family-specific read-only diagnostics when the native harness is available. The
`--gmt-diagnostics` flag selects the additional legacy GMT evidence path. Missing JDK/OpenCV yields
inconclusive, never guessed measurements. `selected.csv` retains the calibrator's
columns including `sha256,physical_watch_id,model,local_path`; source/provenance
metadata is required for ingestion. Keep originals and catalogue outside Git.

Python consumers can pass `Archive('/private/archive')` to `Pipeline(archive=...)`,
`submariner_acquire.run(..., archive=...)` or `watch_calibrator.acquire.run(..., archive=...)`.
Calls are explicit opt-ins and submit only new acquisitions from that run, never the
whole historical state. Historical imports require an explicitly selected local manifest.
Existing acquisition schedules are unchanged. The calibrator workflow retains PR
regression tests but requires manual dispatch for autonomous acquisition/calibration.
`--fresh` still affects only the calibrator's workspace. Restoring never starts a
measurement or recalibration job.

Before saving, `put_verified` re-reads bytes once, verifies the expected SHA and runs
the current screen on those same bytes. A caller cannot supply a forged PASS object.
Failures retain metadata/reasons but never send photo bytes to the provider. Passed
but unarchived bytes remain in `pending/`; an upload or manifest failure is `pending`,
never `archived`. Retries reassess the current profile and bytes.

Storage keys use `objects/<sha-prefix>/<sha>.<format-extension>`; the original
filename extension is also retained in association metadata. Bytes are never
resized/recompressed. Exact bytes deduplicate even when source extensions differ.
Different hashes are never merged by perceptual similarity. Several photos of one
watch count once; identical photos linked to different watch IDs raise an identity
conflict without merging those watches. Conflicted photos cannot be restored into
calibration. `declared_watch_ids` in migration reports is not a proven independent
sample count; aliases still need reconciliation from provenance evidence.

Each successfully uploaded object has a unique append-only `manifests/runs/*.json`
snapshot containing its associations and admission evidence. A new runner can call
`backend.list_snapshots()`, `archive.import_snapshot(key)` and `archive.restore(...)`
to rebuild the catalogue and verify bytes without the original source. Reconciliation
reports indexed missing/corrupt/unavailable blobs, pending state and unlinked objects
from a read-only object-tree listing; it never deletes anything. Local `pending/` cleanup is not automatic.

## OneDrive authorization from an Android phone

**Do not do these steps until the owner authorizes account connection.** No live
Microsoft connection was made during implementation. The workflow is manual-only and
uses synthetic text, never research photographs. Real-image remote sync is not exposed
by the CLI; it needs the owner decisions in FACE-ON-VALIDATION.md and approval first.

1. In a mobile browser (desktop-site mode can help), open the Microsoft Entra app
   registration portal. Create an application supporting personal Microsoft accounts;
   copy its Application (client) ID. If app registration is unavailable for your account,
   resolve that registration access first; do not substitute a different account or
   broader OneDrive permission.
2. In Authentication, enable public-client/device-code flows. No client secret is
   needed. Add only Microsoft Graph **delegated `Files.ReadWrite.AppFolder`**. MSAL
   adds the necessary sign-in/offline-renewal scopes. Do not add
   `Files.ReadWrite` or `Files.ReadWrite.All`.
3. Open a trusted terminal from the phone, for example a private GitHub Codespace
   in the mobile browser, and install the requirements above. Run:

   ```sh
   python -m tools.image_archive authorize --authorized --client-id YOUR_APP_ID --cache /tmp/watch-align-private/msal-cache.json
   ```

   This prints a short code. Open `https://microsoft.com/devicelogin` in another tab,
   enter that code, sign in to the intended personal account and review the app-folder
   permission. Never paste a password or token into chat. The cache is saved with mode
   0600 outside the repository. Do not put it into artifacts or commit it.
4. After consent, test **synthetic bytes only**:

   ```sh
   python -m tools.image_archive smoke --authorized --client-id YOUR_APP_ID --cache /tmp/watch-align-private/msal-cache.json
   ```

   Success means app-root read, uniquely named synthetic upload, exact download/hash
   verification and deletion of that specific test file. A 403/serviceReadOnly means
   stop; the app-folder consumer permission is preview and cannot be assumed to work
   for every registration. No permission expansion is attempted.
5. For optional unattended synthetic tests, create the `onedrive-archive` GitHub
   Environment and configure **required reviewers** (yourself). This repository is
   public, so every credential lives in that protected environment. In repository Settings:

   | Name | Kind | Purpose |
   | --- | --- | --- |
   | `ONEDRIVE_CLIENT_ID` | Repository variable | Public application ID |
   | `ONEDRIVE_MSAL_CACHE` | **Environment secret** (`onedrive-archive`) | Serialized delegated MSAL cache |
   | `ONEDRIVE_CACHE_WRITE_TOKEN` | Environment secret (`onedrive-archive`) | Fine-grained GitHub PAT for this repository only, Environments: read and write |

   Keep the cache an environment secret, never a repository secret: a repository secret
   is readable by any workflow on any branch, while the environment secret is released
   only to a job you approve. The renewal writer updates that environment secret
   (`gh secret set --env onedrive-archive`). `GITHUB_TOKEN` cannot write Actions secrets.
   Upload the cache directly from the trusted terminal, without printing its contents:

   ```sh
   gh secret set ONEDRIVE_MSAL_CACHE --env onedrive-archive --repo Biggregw/watch-align < /tmp/watch-align-private/msal-cache.json
   ```

   Enter the fine-grained PAT via GitHub's secret UI. It is a GitHub credential, not a
   request for broader Microsoft permissions. Use the narrowest repository selection
   and expiry, and keep its renewal separate from Microsoft consent.
6. On the phone, open Actions → **Image archive synthetic connection test** → Run
   workflow. GitHub lists a manually dispatched workflow only once its file is on the
   default branch, so this step becomes available after the archive is merged; until
   then use the local synthetic test in step 4. Tick the
   explicit authorization input only after approval. The job uses no photograph inputs
   and publishes no credential artifacts.

Refresh-cache changes are encrypted into the GitHub environment secret **before an
access token is released to Graph**. Jobs share a non-cancelling concurrency group to
avoid refresh-token rotation races. Every future OneDrive workflow must use the same
group. An external concurrent cache writer is unsupported. A failed secret update,
revoked consent or expired PAT stops the job. Reauthorize interactively and replace the
secret; the workflow never silently starts interactive login or broadens scopes.

This new-runner renewal path is mocked, **not live-proven**. Verify it by running the
synthetic workflow on two fresh runners after explicit connection approval. If you do
not want the additional GitHub secret-writer credential, retain manual local-cache
operation; do not rely on a static refresh token indefinitely. Remove the temporary
local cache after transferring it and finishing local smoke tests.

Microsoft references: [app folder](https://learn.microsoft.com/graph/onedrive-sharepoint-appfolder),
[permissions](https://learn.microsoft.com/graph/permissions-reference),
[upload sessions](https://learn.microsoft.com/graph/api/driveitem-createuploadsession).

## Tests and evidence

```sh
python -m pytest tools/image_archive/tests tools/dataset_harvester/tests tools/watch_calibrator/tests -q
python -m unittest discover -s tools/research/marker_audit -p test_audit.py
# With JDK 17 and existing native harness dependencies:
ARCHIVE_NATIVE_PHOTO_TESTS=1 python -m pytest tools/image_archive/tests/test_photos.py -q
python -m tools.image_archive.validate_photos --output /private/photographic-validation.json
python -m tools.image_archive inventory --repo . --scan /private/recovered-artifacts --native-diagnostics --output /private/inventory.json
```

See [photographic evidence](../../docs/image-archive/photographic-validation.json),
[historical inventory](../../docs/image-archive/historical-inventory.json), and
[GitHub availability](../../docs/image-archive/github-availability.json).
Reports contain metrics/metadata, no new thumbnails, private images or signed download
URLs. The original public fixtures are reused in place, not copied into this change.

Remaining release work: validate/promote model-specific full-watch photo screening,
review counts and independent identities, owner-authorized synthetic consumer-account
smoke tests and two-runner refresh renewal, then an explicitly approved small real-image
pilot. Bulk migration requires separate approval. No migration command is provided.
