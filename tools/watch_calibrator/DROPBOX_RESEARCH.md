# Dropbox-verified genuine marker variation (research mode)

Research only. Production measurement, reference geometry, tolerances, classifications, marker eligibility, calibration
assets and the Android app are unchanged. Nothing produced here is approved for production calibration.

## What it answers

How much each individual hour marker genuinely varies between independent, manufactured Rolex watches, with the
photographic (measurement) variation separated from the physical (manufacturing) variation.

## Incompatibilities found in the existing tooling

| Component | Finding | Consequence here |
|---|---|---|
| `CalibMeasure` / `production_measure.py` | Runs the legacy `WatchAlignCoreV13` 124060 route and writes 7 photo-level summary metrics (ring rho, spacing RMS, 3–9 line offset…), not individual markers; not the route the current app uses | Not used. Never substituted for per-marker readings |
| `AlignmentCalib` | Runs the current `Alpha99Pipeline` but dumps only the earlier luminous-fit layer; omits the `ClearlyOff` readings that production decisions use (sideways, in/out, rotation, gap, 12 o'clock top edge, strict genuine) | Not used for measurement |
| `run.py` (calibrator) | Discovers and scrapes dealer/public photographs and fits envelopes into calibration configs | Dropbox mode is a separate entry (`run.py dropbox-research …`) that never reaches discovery, acquisition or calibration writing |
| Desktop decode | OpenCV `imread` with the app's resampling rule (halve while ≥ 3200 px, cap 3200, EXIF upright) | Android's decoder can differ by sub-pixel amounts; not measured |

## Pieces

- `tools/desktop-harness/drivers/GenuineMarkerDump.java` — loads the family through the app's own `ModelRegistry.check`
  (unvalidated families refused) and measures through `UnifiedMarkerReview.run`, the app's single entry point. It writes
  one row per marker: production state, credibility, withheld reason, sideways, in/out, 12 o'clock top-edge flag and raw
  value (with its legacy centre fallback kept as a separate dimension), rotation, gap (measured vs placed), combined and
  relative position, reported uncertainties, outer-metal outline quality, production limits as applied, strict-genuine
  state and limits. Each file's SHA-256 is re-checked before measuring.
- `GenuineMarkerDumpParity.java` — re-measures every photograph through the established harness route and a second time
  through the production entry point; every reading must be identical (run automatically; `--skip-parity` to omit).
- `dropbox_source.py` — read-only Dropbox (three read endpoints and the token refresh, nothing else) or an offline cache.
- `dropbox_inputs.py` — the verification gates (below).
- `marker_variation.py` — statistics per exact reference, hour and dimension, with the physical watch as the unit.
- `dropbox_genuine.py` — orchestration and outputs (`run`, `fetch-cache`).

## Population designation

The index records these watches as `provenance_status = dealer_attributed_genuine_candidate`: attributed as genuine by
the dealer, not independently authenticated. Only that exact value is measured, it is carried verbatim into every
output and report heading, and no authenticity approval is invented. Photographic suitability is never authenticity.

## Gates (real index schema; fail closed; every exclusion has its reason in `input_rejected.csv`)

Index columns (all required): `sha256, original_path, bytes, initial_photo_suitability, initial_model_family,
provenance_status, specific_model, physical_watch_id, source_name, original_source_url, association_count,
identity_conflict, current_curated_location, calibration_approved, notes`. Another schema stops the run.

1. The file is in exactly one recognised curated folder (`Submariner 124060`, `Submariner Date 126610`,
   `GMT-Master II 126710`, `Unknown model`) and `current_curated_location` (any root prefix; file or folder) points there.
2. The latest applied selection in `Suitable Photos/_audit/` says `suitable` and a complete, non-dry run recorded
   `copied_verified` / `already_present_verified` for it.
3. Exactly one consistent index row; `initial_photo_suitability = suitable`; the file size equals `bytes`.
4. `provenance_status = dealer_attributed_genuine_candidate` (replicas, unknown or blank provenance excluded).
5. `physical_watch_id` present and `identity_conflict` explicitly false (true or blank excluded).
6. The exact `specific_model` has supported geometry (`GEOMETRY`: 124060, 126610LN, 126710BLNR/BLRO/GRNR), that geometry
   is the curated folder's model, and the selection's model agrees. A selection that said `unknown` and was later
   refined by a curated move is accepted and marked `model_refined_after_selection`.
7. SHA-256 re-verified at staging, in the cache and by the Java driver.

Every photograph in the curated folders whose `specific_model` has no supported geometry is listed in the report's
"Unsupported or unspecific references" table, whatever else it failed; none is measured or pooled.

## Offline cache format (schema watch-align-research-cache/2)

```
CACHE_MANIFEST.json      {"schema": "watch-align-research-cache/2", "retained_photos": [sha256...],
                          "inventory_sha256": "...", "support_files": {"<relative path>": "<sha256>", ...}}
FOLDER_INVENTORY.json    {"schema": "watch-align-folder-inventory/1", "root": "Suitable Photos",
                          "entries": [{"path": "Suitable Photos/<folder>/<sha256>.jpg", "size": n, "bytes_retained": true|false}]}
Research/WatchAlign_417_Source_And_Review_Index_20261024.csv
Suitable Photos/_audit/<selection>/selection.json, run-*.json
Suitable Photos/<folder>/<sha256>.jpg     image bytes of the eligible photographs ONLY
```

The inventory lists every Suitable Photos folder entry (82 on 24 Oct 2026), so the snapshot sees the archive's real
curated population while the 8 unsupported references stay metadata-only (no image bytes). Opening the cache verifies
everything: support-file and inventory hashes, retained photographs' SHA-256 and size, no bytes for metadata-only
entries, no unexpected files. `dropbox_genuine.py verify-cache --cache DIR --expect SNAPSHOT` checks a cache without
measuring. A manifest without a schema is a plain full mirror (all bytes present, listed from disk).

## Source snapshot and empty runs

`--expect snapshots/dropbox_index_20261024.json` compares the eligible population with the reviewed snapshot (82
photographs / 46 watches in Suitable Photos; accepted 124060 71/35, 126610LN 2/2, 126710BLNR 1/1; 8 unsupported) before
anything is measured; any difference writes `FAILED.md` and exits non-zero. A run in which no photograph passes the
gates also writes `FAILED.md` and exits non-zero; no report is produced in either case.

## Running

```bash
# credentials for a scoped read-only Dropbox app (files.metadata.read, files.content.read), environment only
export DROPBOX_REFRESH_TOKEN=… DROPBOX_APP_KEY=…            # or DROPBOX_ACCESS_TOKEN=…
python tools/watch_calibrator/run.py dropbox-research fetch-cache --cache ~/private/wa-cache      # optional
python tools/watch_calibrator/run.py dropbox-research run --cache ~/private/wa-cache --out research-output/dropbox-genuine-$(date +%Y%m%d) \
    --expect tools/watch_calibrator/snapshots/dropbox_index_20261024.json
python tools/watch_calibrator/run.py dropbox-research run --dropbox --out research-output/... --expect ...   # direct
```

Outputs: `REPORT.md`, `results.json`, `input_accepted.csv`, `input_rejected.csv`, `marker_measurements.csv`,
`marker_withheld_summary.csv`, `marker_distributions.csv`, `input_audit.json`, `watch_representatives.csv`,
`within_watch_repeatability.csv`, `production_comparison.csv` — every row traceable to the SHA-256 and physical watch.
The output directory must be new and may not be inside `android/`, `calibration/`, `datasets/` or the cache.

## Verification (offline; no Dropbox access in this session)

- `python -m pytest tools/watch_calibrator/tests` — 83 passed (26 for this mode), including the real 82/74 offline-cache
  split (74 image files, 82 inventory entries, snapshot match, the 74 measured) and six cache-integrity failures: every gate on the real schema, folder /
  location / model / reference correspondence, a curated move, real audit-record structure, a 417-row snapshot archive
  matching the committed snapshot, snapshot mismatch and zero-accepted runs failing visibly, per-photo production
  decisions with photo-specific limits, the read-only source and the cache.
- Real-photograph fixture (owner-uploaded 124060 photographs arranged in the real schema; fixture labels, one dealer;
  not the Dropbox archive): 48 accepted / 31 watch ids, 1 replica rejected; parity 1,152 per-marker comparisons with 0
  differences (including the decision flags); re-derived decisions 0 disagreements. A pipeline check, not findings.
- No measurement of the real Dropbox genuine-candidate photographs has been made; no variation findings exist yet.
  The supplied cache ZIPs (24 Oct 2026) arrived truncated (9,920 KiB and 11 MiB, no central directory): 29 of 74
  photographs recoverable and verified, index / audit / snapshot / inventory missing, so the study was not run.
