# Watch Family Calibrator

The calibrator is the reusable path for adding a new watch reference without rebuilding the research process by hand.

## Contract

Input: an exact model reference already described in `calibration/models/<MODEL>.json`.

Output: a calibration evidence bundle containing source discovery, provenance, watch-level split, measurements, repeatability, frozen development limits, validation results, untouched holdout results, and a final `calibration.json`.

The runner is intentionally one command:

```bash
python3 tools/watch_calibrator/run.py 124060 --fresh
```

No source-pool argument is required. The model config tells it where it is allowed to search, what layout the watch has, which mature measurement adapter to use, and which production-facing metrics are eligible for calibration.

## End-to-end flow

1. Search approved public sources for the exact model.
2. Keep only distinct listing/post URLs with model evidence and source-specific provenance rules.
3. Acquire the image sets and hash every byte.
4. Reuse the existing dataset rules for exact/near duplicates, cross-watch duplicates, resolution and listing identity.
5. Lock a physical-watch-level development/validation/holdout split. Photos never count as independent watches.
6. Measure development and validation with the configured mature detector adapter and synthetic perturbations.
7. Derive candidate limits from **genuine development watches only**.
8. Validation may reject a frozen limit but can never change it.
9. Replica watches are optional stress cases; they can never move a limit.
10. Only if at least one metric survives validation does the runner open the holdout partition.
11. Holdout may confirm or reject frozen limits but can never change them.
12. A metric receives state `CALIBRATED` only after the untouched holdout criteria pass.

The workflow is `.github/workflows/watch-family-calibrator.yml`.

## Safety against the mistakes made during 124060 development

The calibrator is deliberately built around mature reusable infrastructure instead of one-off model research:

- source discovery is config-driven;
- physical watch is always the independence unit;
- family layout is data, not copied presentation or workflow code;
- detector/measurement adapters are reused rather than rewritten;
- perturbation and within-watch noise are part of calibration, not an afterthought;
- development fixes limits before validation/holdout are opened;
- image-source class differences cannot set a limit;
- a pose/scale-sensitive metric is rejected by default;
- insufficient data produces `INSUFFICIENT`, never a guessed tolerance;
- adding a new model means adding a config and, only if genuinely necessary, a new detector adapter.

## Current adapter

`submariner_research_v2` reuses the hardened Submariner research measurement path and accepts the model's baton/round layout from config. This lets later 12-series Submariners reuse the same acquisition, split, perturbation, geometry and calibration framework instead of starting a parallel project.

The first proving model is `124060`.

## Calibration output

Each metric records:

- independent development/validation/holdout watch counts;
- development centre;
- detector/photo noise floor;
- robust between-watch spread;
- frozen CLEAR and CHECK bands;
- validation rates;
- holdout rates;
- final state.

`CALIBRATED` means the limit survived the configured validation and untouched holdout criteria. It does **not** automatically publish the limit into the Android production QC path. Publication remains an explicit product change so a calibration run cannot silently change user-facing verdicts.
