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

A run with any failure is `incomplete`; the Actions run turns red. Retry by committing a new request file for the same
selection (e.g. `suitable-first-retry1.json`): finished copies are verified no-ops, the rest are completed. A selection id
is immutable: applying different content under the same id is refused before anything is copied.

## 4. Concerns not solved here

- Near-duplicates (the same photograph re-encoded or resized) have different SHA-256 and are not detected; only exact
  duplicates are. A reviewer can mark them `not_suitable` with `duplicate_view`.
- Each copy downloads and re-uploads the bytes through the runner (Graph server-side copy is asynchronous and gives no
  byte guarantee); about 417 × (2 downloads + 1 upload) at most.
- The collection keeps a copy that a later selection excludes; removal, if ever wanted, needs a separate owner decision.
