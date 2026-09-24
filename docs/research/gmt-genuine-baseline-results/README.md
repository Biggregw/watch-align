# GMT 126710BLNR genuine-image marker baseline

Research-only empirical image distribution. It is not a Rolex factory tolerance and not an authenticity classifier.

- Source manifest: **25** independent listed watches
- Sources yielding at least one measurement: **11**
- Sources yielding a <=10° image: **9**
- <=10° measured images: **14**
- Independent physical watches in primary baseline: **9**
- Source classes represented: **auction_house, established_dealer, rolex_cpo**

## Primary marker baselines (physical-watch medians, <=10°)

| Feature | n watches | median | MAD | p10 | p90 |
|---|---:|---:|---:|---:|---:|
| `h12.stage3_apex_r_simple` | 9 | 0.5571 | 0.0047 | 0.5513 | 0.5782 |
| `h12.stage3_centre_r_projective` | 9 | 0.7368 | 0.0021 | 0.7332 | 0.7399 |
| `h12.stage3_base_r_projective` | 9 | 0.8089 | 0.0048 | 0.8033 | 0.8163 |
| `h12.stage3_axis_incidence_canonical` | 9 | -0.0014 | 0.0037 | -0.0083 | 0.0103 |
| `h12.stage3_centroid_tangential_offset_canonical` | 9 | -0.0008 | 0.0023 | -0.0037 | 0.0006 |
| `h06.centre_r` | 9 | 0.6882 | 0.0036 | 0.6594 | 0.6922 |
| `h06.centre_t` | 9 | -0.0004 | 0.0042 | -0.0052 | 0.0100 |
| `h06.axis_residual_deg` | 9 | 0.1369 | 0.5814 | -0.4603 | 1.7641 |
| `h06.radial_span` | 9 | 0.2443 | 0.0090 | 0.2039 | 0.2543 |
| `h09.centre_r` | 9 | 0.6869 | 0.0028 | 0.6618 | 0.6904 |
| `h09.centre_t` | 9 | 0.0015 | 0.0039 | -0.0062 | 0.0106 |
| `h09.axis_residual_deg` | 9 | 0.0739 | 0.4408 | -0.5883 | 0.7026 |
| `h09.radial_span` | 9 | 0.2503 | 0.0057 | 0.2062 | 0.2561 |

Full round-marker and shape-feature distributions are in `baseline_watch_level.csv`.

## Date-centering viewpoint hypothesis

Date detector produced usable centring values on **18** measured images.
- Spearman |date horizontal offset| vs tilt: **0.300**
- Spearman |date vertical offset| vs tilt: **0.282**
- Spearman total date offset vs tilt: **0.168**
- Within-watch centred Pearson total date offset vs tilt: **0.759**
- Spearman total date offset vs 12-apex simple/projective disagreement: **-0.153**

Interpretation rule: date centring may be a useful *supporting* frontalness signal only if the correlations are consistently positive and there are enough repeated views. It must not be treated as proof of perfect perspective because date-wheel print/position and cyclops optics can create or cancel apparent offsets.

## Files

- `source_status.csv`
- `per_image_measurements.csv`
- `per_physical_watch_medians.csv`
- `baseline_watch_level.csv`
- `baseline_by_source_class.csv`
- `summary.json`
