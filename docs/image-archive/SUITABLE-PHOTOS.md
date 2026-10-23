# Suitable Photos collection (OneDrive)

A browsable collection of **exact copies** of the archived originals that a reviewer has explicitly judged suitable:

```
WatchAlign/Suitable Photos/Submariner 124060/<sha256>.jpg
WatchAlign/Suitable Photos/Submariner Date 126610/<sha256>.jpg
WatchAlign/Suitable Photos/GMT-Master II 126710/<sha256>.jpg
WatchAlign/Suitable Photos/Unknown model/<sha256>.jpg
WatchAlign/Suitable Photos/_audit/<selection_id>/selection.json        the selection exactly as applied
WatchAlign/Suitable Photos/_audit/<selection_id>/run-<time>-<hash>.json every run: each entry and its outcome
```

The 417 originals in `WatchAlign/unreviewed/objects/` are only **read**: never deleted, modified, re-encoded or moved.
Production QC, calibration evidence, tolerances, the Android app, credentials, environment protection and workflow
permissions are unchanged.

## 1. Selection manifest — `archive/selections/sel-YYYYMMDD-<name>.json`

Example: `docs/image-archive/examples/selections/sel-20261024-example.json`. Per photo: `sha256` (the original's),
`model` (`submariner_124060`, `submariner_126610`, `gmt_126710` or `unknown`), `suitability`
(`suitable`, `not_suitable`, `uncertain`), `reasons`, `comment`. Top level: `schema`, `selection_id` (= file name),
`reviewer` (`owner`, `chatgpt`, `claude`), `reviewed_at` (UTC ISO-8601).

**Photo suitability only.** A clear, reasonably face-on photograph is suitable even if hands cover some hour markers.
The manifest refuses marker fields (`markers`, `hours`, …), and `not_suitable` needs a photo-level reason from:
oblique, blurred, low_resolution, dial_cropped, heavy_glare, not_a_photograph, wrong_model, watermark_over_dial,
heavily_edited, duplicate_view, other (with a comment). Hour markers are assessed separately in `archive/decisions/`.

## 2. Run it (protected)

Commit the selection, then a request, to `experiment/archive-first-onedrive-20261023`:

```json
{"task": "suitable_copy", "options": {"selection": "archive/selections/sel-20261024-first.json", "dry_run": true}}
```

The existing archive workflow runs it in the `onedrive-archive` environment, which waits for the owner's approval.
Do a `dry_run: true` first (reads only; its result lists every planned copy), then the same with `dry_run` removed.
The result is committed to `archive/results/<request>.json` with the full per-photo audit.

## 3. What happens to each entry

| Entry | Outcome |
|---|---|
| `suitable`, in the verified index, model agrees with the catalogue (or is `unknown`) | source downloaded and re-hashed → copied with the archive's immutable put (never overwrites, read back byte for byte) → destination re-hashed → `copied_verified` (or `already_present_verified` on a re-run) |
| `not_suitable` / `uncertain` | `excluded`, recorded with reasons; an earlier copy stays (nothing is deleted) and is noted |
| not in the verified archive index | `held` |
| model contradicts the archive catalogue | `held` (fix the selection or the catalogue; nothing is guessed) |
| already filed under another model folder | `held` (a photo is never copied twice) |
| listed twice identically | merged, counted as `duplicate_entries` |
| listed twice with different model/suitability | the whole selection is refused |
| model `unknown` | copied to `Unknown model/`; the catalogue's families are recorded in the audit, never substituted |
| source or destination fails SHA-256, or a different file already sits at the destination | `failed`, not copied/overwritten; other photos continue |

Run status (in `archive/results/<request>.json`, which always carries the full per-photo audit):

| status | meaning | Actions run |
|---|---|---|
| `complete` | every planned copy verified **and** the OneDrive run record written and read back identical | green |
| `dry_run` | nothing written; every planned source downloaded and re-hashed | green |
| `incomplete` | a copy failed, or the OneDrive run record could not be written/verified (`audit_record` says which) | red |
| `refused` | the selection could not be recorded (e.g. its id already holds different content); nothing copied | red |

A run with any failure is `incomplete`; the Actions run turns red. Retry by committing a new request file for the same
selection (e.g. `suitable-first-retry1.json`): finished copies are verified no-ops, the rest are completed. A selection id
is immutable: applying different content under the same id is refused before anything is copied.

## 4. First selection: `sel-20261023-screened-373` (ChatGPT, 23 Oct 2026)

Committed byte for byte (SHA-256 `7734af95…b22ab`). 373 photos, all `submariner_124060`: 86 suitable (38 primary views,
45 alternate views of the same watches, 3 reused across listings), 26 uncertain, 261 not suitable (photo-level reasons
only: non-dial/accessory view, oblique, thumbnail-sized, too few pixels, graphics over the dial). Offline check:
schema valid, no duplicates, every SHA-256 in the repository catalogue, no model conflicts; the plan is 86 copies to
`Submariner 124060/` and 287 exclusions. 15 of the 86 come from curated replica listings (recorded per photo as
`catalogue_provenance`), which matters for calibration, not for photo suitability. The other 44 archived originals
are not in this selection and stay unclassified.

Steps (each run waits for the owner's approval in `onedrive-archive`):
1. Merge PR #74 into `experiment/archive-first-onedrive-20261023`.
2. Commit `archive/requests/suitable-373-dry-run.json`:
   `{"task": "suitable_copy", "options": {"selection": "archive/selections/sel-20261023-screened-373.json", "dry_run": true}}`
   → approve → check `archive/results/suitable-373-dry-run.json`: `status: dry_run`, `summary.would_copy: 86`,
   `held: 0`, `failed: 0`.
3. Commit `archive/requests/suitable-373-copy.json` (same, without `dry_run`) → approve → expect `status: complete`,
   `summary.copied: 86`, `audit_written: true`.
4. If not complete: commit `suitable-373-copy-retry1.json` (same content). Verified copies are no-ops.

## 5. Concerns not solved here

- Near-duplicates (the same photograph re-encoded or resized) have different SHA-256 and are not detected; only exact
  duplicates are. A reviewer can mark them `not_suitable` with `duplicate_view`.
- Each copy downloads and re-uploads the bytes through the runner (Graph server-side copy is asynchronous and gives no
  byte guarantee); about 417 × (2 downloads + 1 upload) at most.
- The collection keeps a copy that a later selection excludes; removal, if ever wanted, needs a separate owner decision.
