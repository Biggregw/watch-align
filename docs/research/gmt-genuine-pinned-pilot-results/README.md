# GMT 126710BLNR genuine-image marker baseline

Research-only empirical image distribution. It is not a Rolex factory tolerance and not an authenticity classifier.

- Source manifest: **15** independent listed watches
- Sources yielding at least one measurement: **6**
- Sources yielding a <=10° image: **4**
- <=10° measured images: **4**
- Independent physical watches in primary baseline: **4**
- Source classes represented: **Chrono24, IDWX, Watchfinder, XL Jewelers**

## Primary marker baselines (physical-watch medians, <=10°)

| Feature | n watches | median | MAD | p10 | p90 |
|---|---:|---:|---:|---:|---:|
| `h12.stage3_apex_r_simple` | 4 | 0.5537 | 0.0038 | 0.5493 | 0.5656 |
| `h12.stage3_centre_r_projective` | 3 | 0.7398 | 0.0048 | 0.7351 | 0.7436 |
| `h12.stage3_base_r_projective` | 3 | 0.8122 | 0.0015 | 0.8111 | 0.8174 |
| `h12.stage3_axis_incidence_canonical` | 4 | -0.0039 | 0.0132 | -0.0194 | 0.0229 |
| `h12.stage3_centroid_tangential_offset_canonical` | 4 | -0.0004 | 0.0048 | -0.0105 | 0.0059 |
| `h06.centre_r` | 4 | 0.6798 | 0.0122 | 0.6453 | 0.6937 |
| `h06.centre_t` | 4 | 0.0028 | 0.0024 | -0.0030 | 0.0053 |
| `h06.axis_residual_deg` | 4 | 0.9058 | 0.7487 | -0.0635 | 5.2958 |
| `h06.radial_span` | 4 | 0.2423 | 0.0145 | 0.1727 | 0.2576 |
| `h09.centre_r` | 4 | 0.6792 | 0.0089 | 0.6447 | 0.6884 |
| `h09.centre_t` | 4 | 0.0027 | 0.0049 | -0.0027 | 0.0094 |
| `h09.axis_residual_deg` | 4 | 0.7844 | 0.3322 | 0.4252 | 1.9343 |
| `h09.radial_span` | 4 | 0.2381 | 0.0168 | 0.1740 | 0.2567 |

Full round-marker and shape-feature distributions are in `baseline_watch_level.csv`.

## Date-centering viewpoint hypothesis

Date detector produced usable centring values on **6** measured images.
- Spearman |date horizontal offset| vs tilt: **0.486**
- Spearman |date vertical offset| vs tilt: **0.657**
- Spearman total date offset vs tilt: **0.829**
- Within-watch centred Pearson total date offset vs tilt: **n/a**
- Spearman total date offset vs 12-apex simple/projective disagreement: **-0.900**

Interpretation rule: date centring may be a useful *supporting* frontalness signal only if the correlations are consistently positive and there are enough repeated views. It must not be treated as proof of perfect perspective because date-wheel print/position and cyclops optics can create or cancel apparent offsets.

## Files

- `source_status.csv`
- `per_image_measurements.csv`
- `per_physical_watch_medians.csv`
- `baseline_watch_level.csv`
- `baseline_by_source_class.csv`
- `summary.json`
