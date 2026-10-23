# Watch Align photographic archive — automation, security and ChatGPT access

Branch `experiment/archive-first-onedrive-20261023` (draft; not for `main`). Production QC tolerances, classifications,
the Android app and calibration are untouched. Originals are never deleted, re-encoded or overwritten.

## 1. What runs where

```
 OneDrive (personal, app folder only: Apps/<app>/WatchAlign/)        GitHub repository (public: no photos)
 ├─ unreviewed/objects/ab/<sha256>.jpg   exact original bytes       ├─ archive/requests/*.json   what to do
 ├─ unreviewed/manifests/runs/*.json      catalogue (immutable)      ├─ archive/results/*.json    what happened
 ├─ review/batches/<batch>/               viewing copies + sheets    ├─ archive/batches/*.json    batch index (hashes)
 ├─ review/decisions/*.json               mirrored decisions         ├─ archive/decisions/*.json  review decisions
 └─ review/approved/*.json                approved evidence          └─ archive/approved-evidence.json  calibration input
```

All OneDrive work runs in GitHub Actions (`.github/workflows/watch-align-onedrive-archive.yml`), job `automation`,
inside the protected `onedrive-archive` environment. Nothing needs a Codespace after the one-time hand-over (§3).

| Trigger | What it does |
|---|---|
| Commit a new `archive/requests/<name>.json` to this branch | Runs that request; commits `archive/results/<name>.json` (counts, hashes, no photos) |
| Commit or change `archive/decisions/*.json` | Validates every decision, rebuilds `archive/approved-evidence.json`, mirrors both to OneDrive |
| Pull request touching the archive | Tests and decision validation only; **never** credentials |
| Weekly schedule / manual buttons | **Only after this workflow file is on the default branch** (a GitHub rule). Weekly: keep-alive + verify |

Request tasks (`{"task": ..., "options": {...}}`):

| task | options | effect |
|---|---|---|
| `verify` | `max_photos` | Downloads every indexed original, checks SHA-256 and size; reports orphans/missing objects |
| `reconcile` | `repair: true` | Lists objects without a manifest entry (interrupted upload); with repair, re-hashes each and writes a recovery manifest. Corrupt objects are reported, never listed or deleted |
| `recover` | `sources: [{run_id, artifact}]`, `confirm_no_other_transfer: true`, `limit` | Downloads the named Actions artifacts, uploads only originals not already stored, indexes stored-but-unlisted ones |
| `review_batch` | `size` (1–60), `family` | Next batch of unreviewed photos: viewing copies + contact sheets + template in OneDrive, batch index in the repo |
| `ingest` | — | Same as a decision push |
| `keepalive` | — | Renews the Microsoft token (saving the rotated cache) and reports the cache-writer token's days left |
| `reauthorize` | — | Device sign-in from your phone when consent has lapsed: the code appears in the Actions log; the cache is saved only if the account matches your pin |

Examples are in `docs/image-archive/examples/`. A request runs once (its result file marks it done).

## 2. Integrity rules (tested)

- Content-addressed, immutable keys: an existing key with different size or bytes is a conflict, never overwritten.
- Every upload is read back and compared byte-for-byte; `verify` re-hashes everything on demand.
- Re-runs are incremental: one folder listing, no re-download of photos already archived and indexed.
- Interrupted uploads (manifest written every 20 photos) leave *orphans*; `reconcile` finds them, `recover`/`reconcile repair` index them after re-hashing.
- Lost manifests are rebuilt from the verified objects plus the repository catalogue (source URL, model, watch id, provenance).
- Unsuitable photos are never rejected from the archive: review only withholds calibration use.

## 3. Credentials (secure, renewing, no scope increase)

- Scope: delegated `Files.ReadWrite.AppFolder` only (the app cannot see the rest of your OneDrive). Unchanged.
- The MSAL token cache (contains the refresh token) is the encrypted **environment secret** `ONEDRIVE_MSAL_CACHE`.
  Each time Microsoft rotates the refresh token, the job writes the new cache back with `ONEDRIVE_CACHE_WRITE_TOKEN`
  (a fine-grained GitHub token for this repository only). A failed write stops the job before the new token is used.
- `ONEDRIVE_ACCOUNT_SHA256` pins your Microsoft account (SHA-256 of the sign-in name, not the address). Every token use
  and every re-authorisation checks it, so a device code read from a public log cannot redirect the archive to
  another person's OneDrive.
- Microsoft refresh tokens for personal accounts expire after about 90 days *without use*; any run renews them. The
  weekly keep-alive (once on the default branch) keeps them alive indefinitely. If they lapse, commit a `reauthorize`
  request and enter the code shown in the run log at microsoft.com/link — no Codespace.
- Logs and results never contain tokens, signed download URLs or the cache; the commit step refuses image files.

### One-time hand-over (≈5 minutes, after the Codespace transfer has finished)

1. github.com → Settings → Developer settings → Fine-grained tokens → *Generate*: repository `Biggregw/watch-align` only;
   permissions **Secrets: Read and write**, **Environments: Read and write**; longest expiry offered.
2. In the Codespace (once): `gh auth login` (your account), then
   `python -m tools.image_archive.setup_ci --cache /tmp/watch-align-private/msal-cache.json` (dry run, prints the pin)
   and the same with `--apply`. This creates the `onedrive-archive` environment **restricted to this branch**, sets
   `ONEDRIVE_CLIENT_ID` and `ONEDRIVE_ACCOUNT_SHA256`, and stores the cache secret.
3. Repository → Settings → Environments → `onedrive-archive` → add secret `ONEDRIVE_CACHE_WRITE_TOKEN` = the token from step 1.
4. Stop using the Codespace cache (one active holder of the refresh token). Commit `archive/requests/verify-first.json`
   containing `{"task": "verify"}` to confirm.

Security choice for you: the environment is restricted to this branch **without required reviewers**, so automation
runs unattended. Anyone able to push to this branch could change the workflow and read the secret; today that is you
and the agents you authorise. Adding yourself as a required reviewer makes every run wait for a tap in the GitHub app.

## 4. ChatGPT access — what works, what is proposed

ChatGPT cannot browse a private OneDrive by itself, and it cannot read GitHub Actions secrets. The design gives it
three routes; **none has been exercised end-to-end with ChatGPT from here**, so each is labelled honestly.

| Route | What ChatGPT gets | Status |
|---|---|---|
| A. Repository (GitHub connector / Codex) | Batch indexes, results, approved evidence (hashes, models, source URLs). **Writes** decisions and requests by commit (Codex or any agent with push access) | Built and tested here (schema, validation, ingest). Requires ChatGPT's GitHub access to this repo |
| B. Your OneDrive via ChatGPT's Microsoft OneDrive connection | The batch folder `Apps/<app>/WatchAlign/review/batches/<batch>/`: contact sheets, per-photo viewing copies, `batch.json`, `decisions-template.json`, README | Folder contents built and tested. Whether your ChatGPT plan's OneDrive connection can open that folder must be checked by you once (attach/upload "from OneDrive" in ChatGPT) |
| C. Sealed review artifact | Set repository variable `REVIEW_PUBLIC_KEY_PEM` (a public key whose private half lives only in the reviewer's environment, e.g. a Codex environment secret). Each new batch is also published as an encrypted Actions artifact, unreadable to anyone else | Workflow step built; not exercised with a real key. Decrypt: `openssl pkeyutl -decrypt -inkey private.pem -pkeyopt rsa_padding_mode:oaep -in key.bin.enc -out key.bin && openssl enc -d -aes-256-cbc -pbkdf2 -in review.tar.xz.enc -pass file:key.bin \| xz -d \| tar -x` |
| D. Read-only photo proxy (MCP server / Custom GPT Action) | Direct, on-demand image fetch by SHA-256 | **Proposed only.** Needs hosting and a credential outside GitHub; needs your approval |

Recommended loop (A + B): ChatGPT commits `archive/requests/batch-<n>.json` → the workflow puts batch *n* in OneDrive
and commits its index → ChatGPT opens the batch from OneDrive, fills `decisions-template.json` following
`archive/CHATGPT-REVIEWER.md`, commits it to `archive/decisions/` → the workflow validates it and updates
`archive/approved-evidence.json`. If ChatGPT cannot push, paste its JSON into a commit (GitHub web or mobile).

## 5. Review and calibration boundary

- Decisions: per photo `approved | excluded | needs_owner`; per physical marker `approved | excluded | cant_assess`;
  plus view, authenticity, watch id, reasons and comment. Strict validation: SHA-256 must be in a committed batch,
  hours must exist on that model (no 3 o'clock marker on 126610/126710), an approval needs ≥1 approved marker.
- Ledger: every decision file is kept; the owner's decision overrides an AI reviewer's regardless of time; among
  equal reviewers the latest wins and a same-time disagreement is a *conflict* (withheld).
- `archive/approved-evidence.json` is the **only** calibration input: approved photos, and only their approved markers.
  No calibrator consumes it yet; wiring it to the existing calibration is a separate, owner-approved step.
