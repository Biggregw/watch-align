# GMT 126710BLNR genuine-image marker baseline

Research-only empirical image distribution. It is not a Rolex factory tolerance and not an authenticity classifier.

- Source manifest: **14** independent listed watches
- Sources yielding at least one measurement: **13**
- Sources yielding a <=10° image: **11**
- <=10° measured images: **20**
- Independent physical watches in primary baseline: **11**
- Source classes represented: **auction_house, established_dealer, rolex_cpo**

## Primary marker baselines (physical-watch medians, <=10°)

| Feature | n watches | median | MAD | p10 | p90 |
|---|---:|---:|---:|---:|---:|
| `h12.stage3_apex_r_simple` | 11 | 0.5532 | 0.0034 | 0.5498 | 0.5777 |
| `h12.stage3_centre_r_projective` | 11 | 0.7368 | 0.0024 | 0.7337 | 0.7407 |
| `h12.stage3_base_r_projective` | 11 | 0.8070 | 0.0024 | 0.8037 | 0.8138 |
| `h12.stage3_axis_incidence_canonical` | 11 | -0.0020 | 0.0050 | -0.0108 | 0.0077 |
| `h12.stage3_centroid_tangential_offset_canonical` | 11 | -0.0008 | 0.0026 | -0.0035 | 0.0048 |
| `h06.centre_r` | 11 | 0.6871 | 0.0047 | 0.6659 | 0.6918 |
| `h06.centre_t` | 11 | -0.0004 | 0.0042 | -0.0046 | 0.0082 |
| `h06.axis_residual_deg` | 11 | 0.0109 | 0.5342 | -1.4571 | 1.1233 |
| `h06.radial_span` | 11 | 0.2452 | 0.0072 | 0.2190 | 0.2582 |
| `h09.centre_r` | 11 | 0.6856 | 0.0040 | 0.6661 | 0.6889 |
| `h09.centre_t` | 11 | 0.0028 | 0.0052 | -0.0045 | 0.0088 |
| `h09.axis_residual_deg` | 11 | 0.2138 | 0.4148 | -0.3732 | 0.8539 |
| `h09.radial_span` | 11 | 0.2486 | 0.0073 | 0.2145 | 0.2560 |

Full round-marker and shape-feature distributions are in `baseline_watch_level.csv`.

## Date-centering viewpoint hypothesis

Date detector produced usable centring values on **21** measured images.
- Spearman |date horizontal offset| vs tilt: **0.512**
- Spearman |date vertical offset| vs tilt: **0.327**
- Spearman total date offset vs tilt: **0.422**
- Within-watch centred Pearson total date offset vs tilt: **0.795**
- Spearman total date offset vs 12-apex simple/projective disagreement: **-0.295**

Interpretation rule: date centring may be a useful *supporting* frontalness signal only if the correlations are consistently positive and there are enough repeated views. It must not be treated as proof of perfect perspective because date-wheel print/position and cyclops optics can create or cancel apparent offsets.

## Files

- `source_status.csv`
- `per_image_measurements.csv`
- `per_physical_watch_medians.csv`
- `baseline_watch_level.csv`
- `baseline_by_source_class.csv`
- `summary.json`
