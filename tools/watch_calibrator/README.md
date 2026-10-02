# Watch-family calibrator

Proposes per-model alignment-QC limits from genuine watches, end to end:
`discover → acquire → source-diversity gate → locked split → measure (production app route) → propose → holdout → finalize`.

```
python3 tools/watch_calibrator/run.py 124060 [--fresh]
```

Config: `calibration/models/<MODEL>.json`. CI: `.github/workflows/watch-family-calibrator.yml`.
The original run that produced the alpha72 bands is preserved at branch
`archive/watch-family-calibrator-8337fd9`.

## Sources (compliance)

| Allowed | Not allowed (rejected or not implemented) |
|---|---|
| Configured dealer `seed_urls`, fetched with an honest User-Agent; bot challenges are recorded, never bypassed | Search-engine result pages (Bing, DuckDuckGo, Google). `check_source_policy` rejects any `queries` |
| Reddit via the official OAuth API only (`reddit_oauth.py`; `REDDIT_CLIENT_ID` / `REDDIT_CLIENT_SECRET`) | Anonymous Reddit `.json`, RSS/Atom feeds or HTML; browser-like User-Agents |
| Imgur albums via the harvester's Imgur API path (`IMGUR_CLIENT_ID`) | Quarantined dealers (Watchfinder) |

Without Reddit credentials, Reddit contributes nothing. It fails closed.

## Genuine source diversity

Calibration is gated on **acquired genuine physical watches**, not discovery hits or raw image count.
A dealer only counts after usable images have actually been downloaded for enough independent watches.
Model config controls `minimum_sources`, `minimum_watches_per_source`, `minimum_acquired_watches` and
`max_single_source_share` under `discovery.genuine_source_diversity`.

For 124060 a fresh calibration currently requires at least four genuine sources with at least four
acquired watches each, at least 20 acquired genuine watches overall, and no single dealer may provide
more than 50% of them. If this gate fails, acquisition exits non-zero before a split is created or any
calibration limit is fitted. The report is written as `dataset/source_diversity.json`.

The locked split preserves `source_name` and stratifies genuine watches by dealer/source. Replica
watches remain stratified by factory. Existing locked split files are never regenerated; a new source
population therefore requires an intentional fresh calibration run.

## Measurement

`production_measure.py` runs the desktop-harness driver `CalibMeasure`, which is the app's own
`WatchAlignCoreV13` 124060 route. A value is written only if it passes the app's alpha70 reliability
gates; otherwise it is blank. So limits are never fitted to measurements the app would withhold.
Noise is the within-watch spread across a watch's own photos. A metric is pose-sensitive when its
within-watch deviations track the marker-layout tilt estimate (|Spearman| ≥ 0.5, ≥ 8 photos).

## Limits

- Only development genuine watches set limits. Validation and holdout can only reject. Holdout is
  measured once, after freezing. Replicas never move a limit; they are reported as stress evidence only.
- Centre and spread are calculated after Hampel outlier rejection (`outlier_mad_k`). The most
  extreme development watch no longer sets the band.
- `sided: "upper"` metrics (RMS and absolute offsets) get an upper limit only.
- **Sensitivity requirement:** a metric becomes `CALIBRATED` only if its clear half-width is
  ≤ `max_clear_half_width`, or the configured `defect_evidence` cases (CSV `metric,value`) fall
  outside clear. Both are product decisions and are left `null` in the config. Without them, the
  best a metric can reach is `HOLDOUT_PASSED_SENSITIVITY_UNPROVEN`, which must not be shipped as a tolerance.
