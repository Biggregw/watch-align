# Genuine GMT baseline stability validation

> Research-only empirical image geometry. Not Rolex factory tolerance and not an authenticity classifier.

Independent physical watches in input: **9**.

## Method

Each feature is recomputed after leaving out each physical watch in turn. A feature is provisionally stable only when coverage is adequate and neither its median nor MAD is excessively controlled by any one watch.

Gates: n >= 8; maximum LOWO median shift <= 0.75 baseline MAD; maximum LOWO MAD change <= 60% of baseline MAD.

These are conservative engineering stability gates, not manufacturing tolerances.

## Results

| Feature | n | median | MAD | LOWO median shift / MAD | LOWO MAD change | status |
|---|---:|---:|---:|---:|---:|---|
| `h12.stage3_apex_r_simple` | 9 | 0.557071 | 0.00467366 | 0.305872 | 0.319743 | **stable** |
| `h12.stage3_centre_r_projective` | 9 | 0.73682 | 0.00213879 | 0.372045 | 0.263475 | **stable** |
| `h12.stage3_base_r_projective` | 9 | 0.808922 | 0.0047872 | 0.0856351 | 0.234288 | **stable** |
| `h12.stage3_axis_incidence_canonical` | 9 | -0.0014068 | 0.00368697 | 0.081366 | 0.177605 | **stable** |
| `h12.stage3_centroid_tangential_offset_canonical` | 9 | -0.00083511 | 0.00227981 | 0.535124 | 0.477817 | **stable** |
| `h06.centre_r` | 9 | 0.688152 | 0.00360353 | 0.119369 | 0.304911 | **stable** |
| `h06.centre_t` | 9 | -0.000391716 | 0.00418825 | 0.356558 | 0.526653 | **stable** |
| `h06.axis_residual_deg` | 9 | 0.136879 | 0.581416 | 0.453299 | 0.217393 | **stable** |
| `h06.radial_span` | 9 | 0.244257 | 0.00904652 | 0.308704 | 0.475104 | **stable** |
| `h09.centre_r` | 9 | 0.686897 | 0.00281029 | 0.295347 | 0.444002 | **stable** |
| `h09.centre_t` | 9 | 0.00145035 | 0.00388239 | 0.355796 | 0.338509 | **stable** |
| `h09.axis_residual_deg` | 9 | 0.0739171 | 0.440831 | 0.294518 | 0.287441 | **stable** |
| `h09.radial_span` | 9 | 0.25032 | 0.00566386 | 0.328062 | 0.35129 | **stable** |

## Interpretation

Provisionally stable: **13**. Sample-sensitive: **0**. Insufficient: **0**.

Only `stable` features are emitted into `provisionally_frozen_features` in `genuine-baseline-v1.json`. Sample-sensitive features remain useful diagnostics but must not become QC pass/fail thresholds without further evidence.
