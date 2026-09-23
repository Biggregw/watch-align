# GMT 126710BLNR genuine-image marker baseline

Research-only empirical image distribution. It is not a Rolex factory tolerance and not an authenticity classifier.

- Source manifest: **7** independent listed watches
- Sources yielding at least one measurement: **2**
- Sources yielding a <=10° image: **1**
- <=10° measured images: **1**
- Independent physical watches in primary baseline: **1**
- Source classes represented: **Watchfinder**

## Primary marker baselines (physical-watch medians, <=10°)

| Feature | n watches | median | MAD | p10 | p90 |
|---|---:|---:|---:|---:|---:|
| `h12.stage3_apex_r_simple` | 1 | 0.5697 | 0.0000 | 0.5697 | 0.5697 |
| `h12.stage3_centre_r_projective` | 1 | 0.7446 | 0.0000 | 0.7446 | 0.7446 |
| `h12.stage3_base_r_projective` | 1 | 0.8122 | 0.0000 | 0.8122 | 0.8122 |
| `h12.stage3_axis_incidence_canonical` | 1 | -0.0112 | 0.0000 | -0.0112 | -0.0112 |
| `h12.stage3_centroid_tangential_offset_canonical` | 1 | 0.0008 | 0.0000 | 0.0008 | 0.0008 |
| `h06.centre_r` | 1 | 0.6875 | 0.0000 | 0.6875 | 0.6875 |
| `h06.centre_t` | 1 | 0.0055 | 0.0000 | 0.0055 | 0.0055 |
| `h06.axis_residual_deg` | 1 | 1.1030 | 0.0000 | 1.1030 | 1.1030 |
| `h06.radial_span` | 1 | 0.2588 | 0.0000 | 0.2588 | 0.2588 |
| `h09.centre_r` | 1 | 0.6871 | 0.0000 | 0.6871 | 0.6871 |
| `h09.centre_t` | 1 | 0.0064 | 0.0000 | 0.0064 | 0.0064 |
| `h09.axis_residual_deg` | 1 | 0.3847 | 0.0000 | 0.3847 | 0.3847 |
| `h09.radial_span` | 1 | 0.2502 | 0.0000 | 0.2502 | 0.2502 |

Full round-marker and shape-feature distributions are in `baseline_watch_level.csv`.

## Date-centering viewpoint hypothesis

Date detector produced usable centring values on **2** measured images.
- Spearman |date horizontal offset| vs tilt: **n/a**
- Spearman |date vertical offset| vs tilt: **n/a**
- Spearman total date offset vs tilt: **n/a**
- Within-watch centred Pearson total date offset vs tilt: **n/a**
- Spearman total date offset vs 12-apex simple/projective disagreement: **n/a**

Interpretation rule: date centring may be a useful *supporting* frontalness signal only if the correlations are consistently positive and there are enough repeated views. It must not be treated as proof of perfect perspective because date-wheel print/position and cyclops optics can create or cancel apparent offsets.

## Files

- `source_status.csv`
- `per_image_measurements.csv`
- `per_physical_watch_medians.csv`
- `baseline_watch_level.csv`
- `baseline_by_source_class.csv`
- `summary.json`
