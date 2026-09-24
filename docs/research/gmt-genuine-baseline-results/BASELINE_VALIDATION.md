# Genuine GMT baseline stability validation

> Research-only empirical image geometry. Not Rolex factory tolerance and not an authenticity classifier.

Independent physical watches in input: **10**.

## Method

Each feature is recomputed after leaving out each physical watch in turn. A feature is provisionally stable only when coverage is adequate and neither its median nor MAD is excessively controlled by any one watch.

Gates: n >= 8; maximum LOWO median shift <= 0.75 baseline MAD; maximum LOWO MAD change <= 60% of baseline MAD.

These are conservative engineering stability gates, not manufacturing tolerances.

## Results

| Feature | n | median | MAD | LOWO median shift / MAD | LOWO MAD change | status |
|---|---:|---:|---:|---:|---:|---|
| `h12.stage3_apex_r_simple` | 10 | 0.555642 | 0.00354053 | 0.403766 | 0.487486 | **stable** |
| `h12.stage3_centre_r_projective` | 10 | 0.736449 | 0.00241698 | 0.1538 | 0.192501 | **stable** |
| `h12.stage3_base_r_projective` | 10 | 0.808512 | 0.00452225 | 0.0906523 | 0.122716 | **stable** |
| `h12.stage3_axis_incidence_canonical` | 10 | -0.00170679 | 0.00388 | 0.0773179 | 0.198873 | **stable** |
| `h12.stage3_centroid_tangential_offset_canonical` | 10 | -0.000285368 | 0.00235989 | 0.232953 | 0.499838 | **stable** |
| `h06.centre_r` | 10 | 0.688051 | 0.00470228 | 0.0214104 | 0.233664 | **stable** |
| `h06.centre_t` | 10 | 0.00110164 | 0.00639401 | 0.233555 | 0.344973 | **stable** |
| `h06.axis_residual_deg` | 10 | 0.400434 | 0.45502 | 0.579217 | 0.435324 | **stable** |
| `h06.radial_span` | 10 | 0.242391 | 0.0133446 | 0.139805 | 0.322082 | **stable** |
| `h09.centre_r` | 10 | 0.686224 | 0.00405806 | 0.165799 | 0.30748 | **stable** |
| `h09.centre_t` | 10 | 0.00283169 | 0.00519661 | 0.265815 | 0.278731 | **stable** |
| `h09.axis_residual_deg` | 10 | 0.143867 | 0.497785 | 0.140522 | 0.166629 | **stable** |
| `h09.radial_span` | 10 | 0.249478 | 0.0067086 | 0.125551 | 0.155731 | **stable** |

## Interpretation

Provisionally stable: **13**. Sample-sensitive: **0**. Insufficient: **0**.

Only `stable` features are emitted into `provisionally_frozen_features` in `genuine-baseline-v1.json`. Sample-sensitive features remain useful diagnostics but must not become QC pass/fail thresholds without further evidence.
