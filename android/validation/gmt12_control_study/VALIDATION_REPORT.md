# GMT 12-triangle validation report

> **Provenance:** ported from the orphaned `feature/android-gmt-triangle-reference-overlay` branch (last commit 2026-09-14), which shares no git history with `main`/this branch. Brought forward on 2026-09-21 for context and reference; the generic `qc`/`profile` contract layer it describes (`QcModule`, `RawMeasurement`, `QcModuleResult`, `WatchProfile`, `WatchProfileLoader`) was ported into this lineage, but its manual 5-point-tap measurement UI and concrete modules (`GmtTriangle12QcModule`, `IndexGeometryQcModule`) were not — see `README.md` for the architecture decision.


## Accepted sample

- Genuine: 1 independent watch/reference image.
- Replica: 1 independent physical watch.
- Minimum 10 + 10 target: not reached. Nine additional independent genuine watches and nine additional independent replica watches remain required.

The D195/PHS image and Infamous_QC screenshots remain grouped as `rep-001-greg-D195` and are not counted independently.

## Raw results and separation

| Metric | Genuine | Greg replica | Separation | Median error | P95 error | Maximum error | Beyond maximum? |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Base-to-60 | 0.164 | 0.086 | 0.078 | 0.002077 | 0.005457 | 0.008031 | Yes |
| Apex-to-crown | 0.291 | 0.284 | 0.007 | 0.002392 | 0.006552 | 0.009271 | No |
| Rotation | 0.000 degrees | -0.290 degrees | 0.290 degrees | 0.137654 degrees | 0.364686 degrees | 0.536056 degrees | No |

Greg's base-to-60 separation is 37.55 times median perturbation error, 14.29 times p95, and 9.71 times the maximum error. Under this defined perturbation model, `0.086` is clearly distinguishable from the accepted genuine control. Apex-to-crown only narrowly exceeds p95 and does not exceed maximum error. Rotation does not exceed p95 or maximum error.

## Reference consistency and subgroup analysis

Reference pooling cannot be tested with one accepted genuine BLNR. There are no accepted BLRO, GRNR or CHNR controls. Factory effects cannot be tested with one accepted ARF replica. The generated breakdown file records these one-case strata without implying a distribution.

## Limitations

The minimum valid sample was not reached. Reddit candidates were retained in the inventory but rejected from measurement when a confidently corrected nine-point set was unavailable. Sprite/VTNR and materially different references were rejected by design. Historical app builds retained the three raw outputs for the two accepted cases but not their exact corrected point coordinates, preventing coordinate-level replay. The result therefore supports a strong within-method finding for Greg's base-to-60 value, not a calibrated genuine-versus-replica population claim.

No range, threshold, or classification behavior was changed.
