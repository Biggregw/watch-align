# Instructions for an AI reviewer (ChatGPT, Claude) of Watch Align archive photographs

Purpose: decide which archived Rolex photographs, and which of their hour markers, are trustworthy evidence for
calibrating Watch Align's marker-position checks. You are not judging authenticity or rejecting photos: an excluded
photo stays in the archive. When unsure, say so (`cant_assess` / `needs_owner`); never guess.

## Get a batch

1. Ask for one: commit `archive/requests/batch-<YYYYMMDD>-<n>.json` = `{"task": "review_batch", "options": {"size": 20}}`
   on branch `experiment/archive-first-onedrive-20261023` (optionally `"family": "submariner_124060" | "submariner_126610" | "gmt_126710"`).
2. The result `archive/results/batch-….json` names the batch; its index is `archive/batches/<batch_id>.json`.
3. Open the viewing copies in the owner's OneDrive: `Apps/<app folder>/WatchAlign/review/batches/<batch_id>/`
   (`sheet-*.jpg`, `photos/<id>_<family>.jpg`, `decisions-template.json`). If you have the sealed artifact route
   instead, decrypt `watch-align-review-batch-sealed` from that run (docs/image-archive/AUTOMATION.md §4).

## Decide (per photo)

- `approved` — a real dial of the stated model, the dial is roughly straight-on (the bezel looks round, not a strong
  ellipse), in focus, not heavily retouched or watermarked over the markers, and at least one marker is clean.
- `excluded` — unsuitable for calibration: strongly oblique, blurred, low resolution, cropped dial, heavy glare,
  rendering/illustration, wrong model, collage, a different watch than catalogued. Give the reason.
- `needs_owner` — you cannot decide (for example authenticity doubts that matter, or the catalogue looks wrong).

Also record `view` (face_on | slightly_oblique | oblique | not_a_dial | unknown), `authenticity`
(genuine_dealer | genuine_owner_verified | replica | unknown — "genuine_dealer" only when the catalogue source is a
dealer listing; do not infer authenticity from appearance), `physical_watch_id` if the catalogue gives one, `comment`.

## Decide (per marker, only when the photo is approved)

Each physical hour marker of that model (the template already lists them; date models have no 3 o'clock marker):
- `approved` — fully visible, sharp edges, no hand, glare, shadow or reflection on or touching it.
- `excluded` — a hand, glare, dirt, crop or blur touches it. Give the reason (e.g. "minute hand over 10").
- `cant_assess` — too small or unclear to judge.
An approved photo must have at least one approved marker.

## Return the decisions

Save the filled template as `archive/decisions/<batch_id>-chatgpt-1.json` (use `-2`, `-3`… for later revisions; never
edit an earlier file) with `"reviewer": "chatgpt"` and `"reviewed_at"` in UTC (`2026-10-24T09:30:00Z`). Commit it to
the branch above. The workflow validates it (unknown hashes, impossible markers or approvals without an approved marker
are rejected; fix and commit a new file) and rebuilds `archive/approved-evidence.json`. The owner's decisions always
override yours.
