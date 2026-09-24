# GMT 126710BLNR genuine-image marker baseline

Research-only empirical image distribution. It is not a Rolex factory tolerance and not an authenticity classifier.

- Source manifest: **25** independent listed watches
- Sources yielding at least one measurement: **12**
- Sources yielding a <=10° image: **10**
- <=10° measured images: **15**
- Independent physical watches in primary baseline: **10**
- Source classes represented: **auction_house, established_dealer, rolex_cpo**

## Primary marker baselines (physical-watch medians, <=10°)

| Feature | n watches | median | MAD | p10 | p90 |
|---|---:|---:|---:|---:|---:|
| `h12.stage3_apex_r_simple` | 10 | 0.5556 | 0.0035 | 0.5493 | 0.5780 |
| `h12.stage3_centre_r_projective` | 10 | 0.7364 | 0.0024 | 0.7316 | 0.7396 |
| `h12.stage3_base_r_projective` | 10 | 0.8085 | 0.0045 | 0.8035 | 0.8152 |
| `h12.stage3_axis_incidence_canonical` | 10 | -0.0017 | 0.0039 | -0.0106 | 0.0090 |
| `h12.stage3_centroid_tangential_offset_canonical` | 10 | -0.0003 | 0.0024 | -0.0036 | 0.0019 |
| `h06.centre_r` | 10 | 0.6881 | 0.0047 | 0.6522 | 0.6920 |
| `h06.centre_t` | 10 | 0.0011 | 0.0064 | -0.0049 | 0.0174 |
| `h06.axis_residual_deg` | 10 | 0.4004 | 0.4550 | -0.4524 | 1.4497 |
| `h06.radial_span` | 10 | 0.2424 | 0.0133 | 0.1892 | 0.2538 |
| `h09.centre_r` | 10 | 0.6862 | 0.0041 | 0.6580 | 0.6901 |
| `h09.centre_t` | 10 | 0.0028 | 0.0052 | -0.0054 | 0.0190 |
| `h09.axis_residual_deg` | 10 | 0.1439 | 0.4978 | -0.4807 | 0.8553 |
| `h09.radial_span` | 10 | 0.2495 | 0.0067 | 0.1957 | 0.2560 |

Full round-marker and shape-feature distributions are in `baseline_watch_level.csv`.

## Date-centering viewpoint hypothesis

Date detector produced usable centring values on **21** measured images.
- Spearman |date horizontal offset| vs tilt: **0.118**
- Spearman |date vertical offset| vs tilt: **0.196**
- Spearman total date offset vs tilt: **-0.005**
- Within-watch centred Pearson total date offset vs tilt: **0.616**
- Spearman total date offset vs 12-apex simple/projective disagreement: **-0.036**

Interpretation rule: date centring may be a useful *supporting* frontalness signal only if the correlations are consistently positive and there are enough repeated views. It must not be treated as proof of perfect perspective because date-wheel print/position and cyclops optics can create or cancel apparent offsets.

## Files

- `source_status.csv`
- `per_image_measurements.csv`
- `per_physical_watch_medians.csv`
- `baseline_watch_level.csv`
- `baseline_by_source_class.csv`
- `summary.json`
