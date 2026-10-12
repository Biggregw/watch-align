# Curated genuine marker measurements — 12 October 2026

Research only. Frozen PR #52 production source `593966433079d04816e98f78da5b66abd1f4553d`. No production, geometry, calibration, QC, perspective or denominator changes. No new photos/APK/merge.

Source: curated run [38222239457](https://github.com/Biggregw/watch-align/actions/runs/38222239457), artifact `WatchAlign-genuine-faceon-132-originals-Codex-ready`. All 132 original SHA256 values verified before analysis. Only this archive was downloaded; 124060 placeholder identities were resolved against the frozen catalogue by exact SHA.

One original per recorded watch, chosen before measurement by highest minimum source dimension, then source pixel count, then SHA. Repeated originals are retained in the provenance audit, not counted again. Recorded/inferred listing groups are not serial-authenticated independent physical watches. Cross-vendor resale overlap remains unproven. Existing measured shared-dial warnings exclude suspect photos from distributions, without changing app findings.

| Family | Original photos | Recorded groups selected | Reliable area groups (any marker) | Partially assessed groups |
|---|---:|---:|---:|---:|
| submariner_124060 | 46 | 44 | 38 | 43 |
| gmt_126710 | 31 | 31 | 26 | 29 |
| submariner_126610 | 55 | 55 | 55 | 52 |

## Filled-template distributions

Every marker remains an individual observation. Equal total weight per recorded watch for descriptive shape distributions; no overall watch quality score. P90/P95 publication requires >=20/>=40 usable recorded groups to retain at least two expected upper-tail groups. Neither percentiles nor maxima are Rolex factory tolerances or replica acceptance thresholds.

| Family / shape | Usable groups | Usable markers | Median total % | P90 % | P95 % | Maximum % |
|---|---:|---:|---:|---:|---:|---:|
| gmt_126710 / triangle | 0 | 0 | — | — | — | — |
| gmt_126710 / baton | 26 | 41 | 3.059967 | 4.015634 | — | 4.711614 |
| gmt_126710 / round | 22 | 135 | 2.173007 | 2.724122 | — | 3.222077 |
| submariner_124060 / triangle | 0 | 0 | — | — | — | — |
| submariner_124060 / baton | 37 | 106 | 2.746568 | 5.145011 | — | 6.931386 |
| submariner_124060 / round | 36 | 229 | 1.582947 | 2.923736 | — | 4.041513 |
| submariner_126610 / triangle | 0 | 0 | — | — | — | — |
| submariner_126610 / baton | 55 | 98 | 1.719510 | 2.603722 | 2.882384 | 3.603356 |
| submariner_126610 / round | 55 | 369 | 1.008873 | 1.706962 | 1.906305 | 3.018094 |

## Interpretation and limitations

Mismatch = 100 × (extra area + missing area) / filled expected ring-adjusted genuine-template area. Entire fitted outer-metal footprint; no experimental vertex-calibrated shapes. Orange extra and magenta missing use exactly the saved numerical polygons. Continuous geometric area is separate from photographic boundary uncertainty. `area_trusted` means existing app gates cleared, not independently proven physical edge accuracy.

All 12 triangle area percentages remain withheld, including offline diagnostic values. Their base width/height differences and percentage directions appear in `triangle_diagnostics.csv`; review the genuine outliers before attributing systematic shape disagreements to reference geometry, manufacture or bright bevels. No triangle template is changed or scoring enabled.

Spacing remains separate. The existing 12 radial genuine comparison is a position-based estimate. Fitted-base-to-nominal-track values are unvalidated model-circle geometry; no direct metal-to-detected-tick endpoint measurement exists in this pipeline. Direct gap fields remain blank. Ring shift/scale/rotation are separately exported and never added to marker-area mismatch.

Source bytes remain unchanged. The frozen desktop loader can apply its existing 3200-px working-size rule; original and working dimensions are both recorded. No presentation thumbnail or transformed photo becomes a manufacturing sample. Native resolution associations are confounded by watch/source/lighting; this cohort does not independently separate physical variation from photographic edge localisation or normal-view acceptability.

No unavailable or unreliable marker is silently removed: `per_marker.csv` includes every marker on every selected original, failed poses, component withholding and interference reasons. Non-selected repeat photographs are separately listed. Full-photo selection was human near-frontal QC screening; successful pose correction alone is not evidence of acceptable photographic suitability.

## LESSONS LEARNED

Provenance and original access must precede measurement. Photographic curation does not guarantee clean polished edges. Identity grouping remains a named source of uncertainty, and repeated source views must not increase manufacturing sample counts. Individual maxima need raw/outlined inspection, not automatic tolerance expansion. Genuine marker variation and edge/template uncertainty remain mixed until independently resolved boundaries and further held-out controls support separation.

[Per-marker results](per_marker.csv) · [Watch/marker summaries](physical_watch_marker_summary.csv) · [Distributions](model_shape_distributions.csv) · [Outlier index](outlier_index.csv) · [Provenance](provenance_verified.csv) · [Identity warnings](identity_shared_dial_pairs.csv)

Measurement run: https://github.com/Biggregw/watch-align/actions/runs/38235551437

## Post-run provenance review

Two curated entries (`b0a1a6ff0bf6db97`, `7888550a54853ebb`) claim 126710BLNR but both their recorded listing URLs and original image filenames say 116710. This is an unresolved source-model contradiction, not proof of a newly identified physical reference. They remain in the measured CSV as comparisons against the declared 126710 template, but are quarantined from the genuine 126710 baseline. All 1,474 numerical rows and app statuses are unchanged. The original claimed-model aggregate is preserved separately; the displayed table and current distribution CSV use the conservative reviewed subset. No exclusion was based on obtaining smaller percentages.

`auto_*` and `hv_page_*` identifiers are explicitly marked provisional inferred listing groups. Other IDs are still listing/lot groups, not serial-verified watch independence. Four 124060 photos trigger the existing shared-dial test (two pairs); a GMT photo has a historical shared-image flag. All remain inspectable but do not enter the independent-group distributions. Counts of partially assessed groups overlap counts with at least one usable area; these are not mutually exclusive whole-watch categories.

The curated archive therefore contains 130 measured recorded groups, 128 with no detected source-model contradiction. There are 978 baseline-eligible marker observations after current reliability, shared-image and model-consistency exclusions. Those are conditional photographed-sample statistics, not 978 independent physical watches.

Small model-specific scalar CSVs are available as `gmt_126710_markers_compact.csv`, `submariner_124060_markers_compact.csv`, and `submariner_126610_markers_compact.csv`. The detailed CSV retains the exact polygons and all uncertainty/interference fields.
