# 124060 provisional alignment QC checkpoint - 2026-10-02

## Scope

This checkpoint integrates the frozen Rolex Submariner 124060 watch-family calibration into the Android product as **provisional alignment QC**. It does not create an authenticity decision, an overall pass/fail result, or any GMT threshold reuse.

The production landmark detectors, dial-edge fit, image-quality gates and alpha70 repeatability rules are unchanged. GMT source, constants, thresholds and judgement logic are unchanged.

## Calibration provenance

- Workflow run: `36927036008`
- Workflow: `Watch-family Calibrator`
- Model/family: `124060` / `submariner_12`
- Calibration state: `CALIBRATED`
- Artifact: `watch-calibrator-124060-36927036008`
- Artifact SHA-256: `5a23dcf54486d99aa6a0b50cbd7b7027c0a190a777030ff67586706b88d311da`
- Principle: development genuine fixes limits; validation may reject only; replica never moves limits.

The six calibrated metrics passed the validation and holdout gates in that run. The replica stress set was used as a stress test only and did not set the bands.

## Frozen bands

| App key | Clear band | Check band | Product treatment |
| --- | --- | --- | --- |
| `twelve.rotation_deg` | -13.88038488935 to 15.22676818935 deg | -21.157173159025 to 22.503556459025 deg | Provisional alignment judgement when the published rotation survives the alpha70 resize gate |
| `twelve.gap_r` | none | none | **Not judged**. Calibration status `INSUFFICIENT`: pose/scale sensitive in development |
| `twelve.centring_w` | -0.147616360146 to 0.136472106146 widths | -0.233275680438 to 0.222131426438 widths | Provisional alignment judgement when the published centring survives the alpha70 resize gate |
| `round.ring_rho` | 0.7826128775166086 to 0.8526717081695576 R | 0.7650981698533712 to 0.870186415832795 R | Provisional alignment judgement from at least four reliable round-marker centres on a reproducible edge-fitted dial |
| `round.spacing_rms_deg` | -5.587231836527973 to 6.370183430972006 deg | -9.573036925694634 to 10.355988520138666 deg | Provisional alignment judgement from at least four reliable round-marker angles with a 60-tick orientation |
| `baton.3_9_line_offset_r` | -0.08170984795356724 to 0.09378104194951688 R | -0.14020681125459528 to 0.1522780052505449 R | Provisional alignment judgement only when both 3 and 9 are reliable edge-fitted batons |
| `axis.12_6_line_offset_r` | -0.1343132175899482 to 0.1396427365740998 R | -0.21825249276636563 to 0.22358201175051726 R | Provisional alignment judgement only with a reliable 12 triangle and reliable edge-fitted 6 baton |

The relational metric formulas in production mirror the calibration adapter: ellipse-squash-corrected dial coordinates, median round-marker radius, common-offset-removed angular RMS, and perpendicular line-to-dial-centre offsets.

## Product labels

- Inside the clear band: `PROVISIONAL CLEAR`
- Outside clear but inside check band: `PROVISIONAL CHECK`
- Outside the check band: `OUTSIDE PROVISIONAL RANGE`
- Missing, withheld, unreliable or uncalibrated: no provisional judgement

These labels describe only the measured geometric alignment relative to the frozen genuine calibration bands. The calibration bands are deliberately not presented as authenticity thresholds.

## Important limitation from replica stress testing

The bands are broad. In the frozen calibration output, most calibrated metrics did not separate the small replica stress set at all. `round.ring_rho` placed 1 of 7 replica stress watches outside its clear/check band; the other calibrated metrics placed none of their available replica stress watches outside. This is why the feature is labelled provisional alignment QC rather than a genuine/replica classifier.

## Android checkpoint

Package version for this integration: `1.3.0-alpha71` (`versionCode 13071`). `WatchAlignCoreV13.CORE_VERSION` remains at alpha70 deliberately because the GMT core algorithm is unchanged; alpha71 is the Android package/product-integration checkpoint.
