# Remaining 44 photographed originals — private suitability review

This is a **photo-level** screening step, not hour-marker approval, calibration, or a change
to the Android measurement engine.

The earlier selection `archive/selections/sel-20261023-screened-373.json`
already classifies 373 SHA-256 identities (86 suitable, 26 uncertain, 261 not suitable).
The remote OneDrive archive contains 417 verified SHA-256 originals.

## Protected review-batch request

On `experiment/archive-first-onedrive-20261023`, create a new request:

```json
{
  "task": "review_batch",
  "options": {
    "size": 44,
    "exclude_selection": "archive/selections/sel-20261023-screened-373.json",
    "expected_remaining": 44
  }
}
```

The runner first checks that **all 373 screened hashes are in the live OneDrive index**
and that **exactly 44 different hashes remain**. If either check fails, it stops before
writing contact sheets or viewing copies. It then uses the existing review-batch
generator; there is no new photo storage, harvesting or public photo artifact.

The result in `archive/results/<request>.json` includes `indexed_originals`,
`excluded_previously_screened`, `remaining_after_selection`,
`review_photos` and `onedrive_folder`. The SHA-256 batch index is committed in
`archive/batches/`. All viewing photos, contact sheets, and optional review templates
stay **private in the owner's OneDrive** under `WatchAlign/review/batches/<batch_id>/`.
The batch's generic marker-assessment template need **not** be completed for this
photo-suitability task; it does not approve markers or feed calibration.

To allow ChatGPT to *visually inspect* the 44, the owner must provide those private
viewing copies (for example, share a ZIP or upload the contact sheets and individual
images into the conversation). The GitHub connector alone can see the batch index
but **cannot read the private OneDrive image bytes**. Do not publish private photos
in a public GitHub repository, workflow logs, or unencrypted artifacts.

Once reviewed, create a **new** `archive/selections/sel-YYYYMMDD-<name>.json`
with the 44 entries, using the existing `watch-align-suitable-selection/1` schema.
Keep the simple rule: a clear, reasonably face-on photo is suitable even if watch
hands obscure some markers. Provenance (genuine vs replica) is recorded separately.

A protected `suitable_copy` dry run then checks the new selection; after owner
approval, the real operation copies suitable exact originals into the appropriate
model folder and re-verifies every copy. Do not delete, replace, or modify originals.
