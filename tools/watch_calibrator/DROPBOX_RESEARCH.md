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

## Gates (fail closed; every exclusion has its reason in `input_rejected.csv`)

1. File present exactly once in `Suitable Photos/<model folder>/<sha256>.<ext>`.
2. The latest applied selection in `Suitable Photos/_audit/` says `suitable`, and its complete, non-dry run verified that copy.
3. Exactly one consistent record in the source and review index, saying the photograph is suitable.
4. Index authenticity genuine (suitability is never taken as authenticity); no replica provenance; confirmed physical-watch identity.
5. Selection model, folder and exact index reference agree. The exact reference must be explicitly mapped to supported app
   geometry (`GEOMETRY` in `dropbox_inputs.py`: 124060, 126610LN, 126710BLNR/BLRO/GRNR). Statistics stay per exact reference.
6. Bytes hash to the recorded SHA-256 (staging, cache and the Java driver all check).

The index's column names are matched against known aliases; if a required field cannot be found the run stops and asks
for `--column-map '{"identity_status": "<column>"}'` rather than guessing.

## Statistics

- Only readings production treats as credible; the triangle gap only when measured (not placed from the outline); the 12
  o'clock top-edge radial and its legacy centre fallback are separate dimensions.
- Per watch: median of its photographs (representative). Within-watch SD from repeated photographs.
- Across watches: n, median, MAD, min, max, p5, p95, distribution-free 95 % CI of the median. No range from fewer than 3
  watches (individual values only). Unusual genuine readings (robust z > 3.5) are flagged and kept.
- Estimated physical SD = sqrt(max(0, variance of watch means − within-watch variance / harmonic-mean photos per watch)).
- Evidence: insufficient (< 3 watches), descriptive (< 10), preliminary (< 30, < 5 multi-photo watches, or one dealer),
  sufficient (otherwise; owner review still required). Outline quality and photo-level geometry are diagnostics only.
- Comparison with production uses the frozen limits exactly as the production code applied them to each photo.

## Running

```bash
# credentials for a scoped read-only Dropbox app (files.metadata.read, files.content.read), environment only
export DROPBOX_REFRESH_TOKEN=… DROPBOX_APP_KEY=…            # or DROPBOX_ACCESS_TOKEN=…
python tools/watch_calibrator/run.py dropbox-research fetch-cache --cache ~/private/wa-cache      # optional
python tools/watch_calibrator/run.py dropbox-research run --cache ~/private/wa-cache --out research-output/dropbox-genuine-$(date +%Y%m%d)
python tools/watch_calibrator/run.py dropbox-research run --dropbox --out research-output/...      # direct
```

Outputs: `REPORT.md`, `results.json`, `input_accepted.csv`, `input_rejected.csv`, `marker_measurements.csv`,
`marker_withheld_summary.csv`, `genuine_distributions.csv`, `watch_representatives.csv`,
`within_watch_repeatability.csv`, `production_comparison.csv` — every row traceable to the SHA-256 and physical watch.
The output directory must be new and may not be inside `android/`, `calibration/`, `datasets/` or the cache.

## Verification on 24 Oct 2026 (offline; no Dropbox access in this session)

- Unit and offline integration tests: `python -m pytest tools/watch_calibrator/tests` — 75 passed (18 new).
- Real-photograph fixture run (owner-uploaded genuine 124060 QC photographs, 48 photos / 31 watch ids from one dealer,
  arranged in the archive layout with fixture index labels; not the Dropbox dataset): pipeline completed, 1 replica-labelled
  photo rejected, parity 1,152 per-marker comparisons with 0 differences. Its numbers are a pipeline check, not findings.
