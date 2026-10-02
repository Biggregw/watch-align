# Watch-family calibrator

Builds conservative per-model replica-QC envelopes from genuine watches, end to end:
`discover → acquire → source-diversity gate → locked diagnostic split → production measurement on all splits → all-genuine envelope → replica stress evidence`.

```
python3 tools/watch_calibrator/run.py 124060 [--fresh]
```

Config: `calibration/models/<MODEL>.json`. CI: `.github/workflows/watch-family-calibrator.yml`.
The original run that produced the alpha72 bands is preserved at branch
`archive/watch-family-calibrator-8337fd9`.

## Product rule

Watch Align is replica QC, not a perfection grader and not an authenticity classifier. A reliable
measurement already observed on a genuine watch is part of accepted genuine variation for this
product, even when the genuine watch has a small imperfection. A replica is highlighted only when a
reliable value is clearly outside the supported genuine envelope.

Consequences:

- Every reliable genuine photo measurement from development, validation and holdout contributes to
  the final production envelope.
- Development/validation/holdout remain useful diagnostics. They show whether the earlier subset
  generalized, but a valid genuine observation in validation or holdout is not discarded merely
  because it lies outside a narrower development fit.
- There is no cross-watch statistical trimming in the final envelope.
- An observation can be removed only as an obvious photo-level measurement failure within the same
  physical watch: by default one extreme photo must disagree strongly with at least three other
  reliable photos of that exact genuine watch. A consistently unusual genuine watch is retained.
- Each physical watch remains an independent source identity. Multiple photos of one watch do not
  create multiple watches.
- Replica data never moves a genuine-derived boundary. It only reports whether the resulting
  genuine envelope has useful separation on the replica stress set.

The older split/freeze/Hampel calculation still runs as `legacy_split_calibration.json` for audit
comparison. It no longer controls `calibration.json`.

## Sources (compliance)

| Allowed | Not allowed (rejected or not implemented) |
|---|---|
| Configured dealer `seed_urls` / reviewed `listing_seed_urls`, fetched with an honest User-Agent; bot challenges are recorded, never bypassed | Search-engine result pages (Bing, DuckDuckGo, Google). `check_source_policy` rejects any `queries` |
| Reddit via the official OAuth API only (`reddit_oauth.py`; `REDDIT_CLIENT_ID` / `REDDIT_CLIENT_SECRET`) | Anonymous Reddit `.json`, RSS/Atom feeds or HTML; browser-like User-Agents |
| Imgur albums via the harvester's Imgur API path (`IMGUR_CLIENT_ID`) | Quarantined dealers (Watchfinder) |

Without Reddit credentials, Reddit contributes nothing. It fails closed.

## Genuine source diversity

Calibration is gated on **acquired genuine physical watches**, not discovery hits or raw image count.
A dealer only counts after usable, non-duplicate images have actually been downloaded for enough
independent watches. Model config controls `minimum_sources`, `minimum_watches_per_source`,
`minimum_acquired_watches` and `max_single_source_share` under
`discovery.genuine_source_diversity`.

The locked split preserves `source_name` and stratifies genuine watches by dealer/source. Replica
watches remain stratified by factory. A fresh source population intentionally produces a fresh split.
The split is now a generalisation diagnostic, not a rule that excludes withheld genuine values from
the final envelope.

## Measurement

`production_measure.py` runs the desktop-harness driver `CalibMeasure`, which is the app's own
`WatchAlignCoreV13` model route. A value is written only if it passes the app's production reliability
gates; otherwise it is blank and cannot shape an envelope.

Both photo-level and per-watch summaries are kept. The final genuine-envelope calibration uses the
photo-level reliable values because the app judges one uploaded photo, so legitimate photo-to-photo
variation must be represented. Per-watch identity is still retained for within-watch outlier checks,
source diversity and diagnostics.

Pose sensitivity is reported rather than automatically deleting a metric from the final envelope.
If a photo passed the product's pose/reliability gates and its value occurs on a genuine watch, that
variation is relevant to the app. A pose-sensitive metric will therefore tend to produce a wider and
less useful genuine envelope, which is honest evidence that the metric has poor discriminating power.

## Final envelope

For each metric:

1. Pool every reliable genuine photo measurement from development, validation and holdout.
2. Remove only obvious within-watch measurement spikes under the strict rule described above.
3. Require enough independent genuine watches and source diversity.
4. Record the observed genuine minimum and maximum.
5. Add a repeatability guard based on the larger of the metric floor, the 90th percentile of
   within-watch half-ranges, and a robust within-watch noise estimate.
6. `CLEAR` includes the observed genuine envelope plus that guard.
7. `CHECK` begins beyond `CLEAR`; the outer `CHECK` band uses twice the repeatability guard by
   default, so values farther out can become `STRONG`.
8. For naturally one-sided error metrics, only the upper boundary is used.

This intentionally favours low false positives. If a replica lies inside genuine variation, that
metric clears it even if a human enthusiast might still dislike the alignment.

## GMT consistency

GMT production thresholds are maintained separately from the 124060 calibrator, but they follow the
same product rule. `docs/research/gmt_genuine_envelope_audit_2026-10-02.md` compares documented GMT
genuine evidence with the production boundaries. Regression tests ensure the GMT CHECK boundaries
sit outside the observed genuine values used by this project.
