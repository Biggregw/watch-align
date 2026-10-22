# Watch-family calibrator: legacy measurement adapter retired (22 October 2026)

**Approved by the repository owner on 22 October 2026** ("Approved", in reply to the proposal to retire the legacy
calibrator route `CalibMeasure` in favour of the Phase B adapter). This changes the **offline calibration asset**
`calibration/models/124060.json` and the calibrator tooling only.

## What did not change

- No app behaviour. The app's measurement, reference geometry, genuine and replica-QC tolerances, reliability and
  classification rules are untouched: `docs/CALIBRATION_BASELINE_2026-10-20.md` and
  `docs/CALIBRATION_BASELINE_2026-10-21_TOP_EDGE.md` remain in force.
- The seven provisional bands frozen in `Sub124060Calibration` (alpha75, accepted 2 Oct 2026 from run 37008521836)
  stay in the app as they are. Their source config is preserved, see below.
- No calibration was run and no new limit was calculated or deployed. The calibrator's end-to-end run stays
  manual-only (`workflow_dispatch`).
- `WatchAlignCoreV13` and `MainActivity` stay in the app source; only the calibrator stops using them.

## Old and new values

| Item | Old (until 22 Oct 2026) | New |
|---|---|---|
| `measurement_adapter` in `calibration/models/124060.json` | `production_app_route_v1` (harness `CalibMeasure` → legacy `WatchAlignCoreV13`) | `marker_geometry_v1` (harness `GeometryMeasure` → the app's `Alpha99Pipeline` read through `MarkerGeometry`) |
| Calibration metrics | 7 composites: `twelve.rotation_deg` (two-sided, floor 0.2°), `twelve.gap_r` (two, 0.002 R), `twelve.centring_w` (two, 0.01 W), `round.ring_rho` (two, 0.002 R), `round.spacing_rms_deg` (upper, 0.1°), `baton.3_9_line_offset_r` (upper, 0.002 R), `axis.12_6_line_offset_r` (upper, 0.002 R) | 32 per-marker and ring metrics, all two-sided: `marker.<1-12>.tang` and `.rad` (floor 0.05 % R), `marker.<3,6,9,12>.tilt` (floor 0.2°), `ring.place_x`, `ring.place_y` (0.05 % R), `ring.rot_deg` (0.05°), `ring.scale_pct` (0.05 %) |
| `accepted_provisional` | in the config | moved with the legacy config (historical record) |
| Legacy config | `calibration/models/124060.json` | preserved at `calibration/models/retired/124060_production_app_route_v1.json`, unchanged except for an added `retired` record |
| Use of `production_app_route_v1` | any config | refused by `production_measure.adapter` (so by `run.py` before any measurement) unless `WATCH_CALIBRATOR_REPRODUCE_RETIRED=production_app_route_v1` is set to reproduce a historical run |

Unchanged in the config: acquisition adapter, layout, replica factories, discovery and source-diversity rules,
`calibration_policy` (sigmas, partition sizes, rates) and `sensitivity_note`. `max_clear_half_width` and
`defect_evidence` stay null, so no new metric can reach CALIBRATED without a further product decision.

The new `minimum_half_width` values are calibrator resolution floors (the narrowest band the envelope may report),
chosen well below the Phase A genuine p99 spreads (round 0.254, baton 0.289, triangle 0.328 % R; baton tilt 0.76°;
ring placement 0.338 % R, rotation 0.172°, scale 0.288 %; `tools/research/measurement_architecture/REPORT.md`). They
are not app tolerances.

## Why

- `CalibMeasure` measured with a legacy 124060-only engine that the app no longer uses, so its limits described a
  different measurement from the one users see.
- Its `round.*` composites need every round marker, and the engine does not report which markers it used. With
  per-marker eligibility masks, a hand at any one round marker therefore discards the photo's round values.
- The new adapter measures each marker against the ring of the other eligible markers, and records which markers each
  ring fit used, so only values that actually depend on an excluded marker are dropped
  (`tools/watch_calibrator/marker_mask.py`).

## Commit

The change is introduced by the commit that adds this file, on branch `feature/unified-image-archive-20261021`
(PR #69); `git log --diff-filter=A -- docs/CALIBRATION_BASELINE_2026-10-22_CALIBRATOR_ADAPTER.md` shows it.

## Change control

Deploying any limit produced from these metrics into the app (Phase C) needs the owner's explicit approval and a new
dated baseline document. Re-enabling the legacy adapter for new calibration also needs approval.
