# submariner_124060 measurement-uncertainty allowances (genuine photos only)

Written by `build_sub_reference.py` (measurement_uncertainty.py definitions). sigma = pooled within-watch SD of
one photo's reading over every genuine physical watch with 2+ usable non-shared photos. CLEAR only when the excess
beyond the genuine maximum exceeds K = 3 sigma.

| family | unit | sigma | K x sigma | watches | photos | dof |
|---|---|---:|---:|---:|---:|---:|
| nine_off | R | 0.000473 | 0.001420 | 17 | 74 | 40 |
| nine_off | px | 0.106540 | 0.319619 | 17 | 74 | 40 |
| nine_rot | deg | 0.101831 | 0.305492 | 17 | 37 | 20 |
| nine_rot | degR | 49.077413 | 147.232239 | 17 | 37 | 20 |
| ring_rot | deg | 0.014949 | 0.044847 | 17 | 37 | 20 |
| ring_rot | degR | 5.688415 | 17.065245 | 17 | 37 | 20 |
| ring_shift | R | 0.001713 | 0.005140 | 17 | 74 | 40 |
| ring_shift | px | 0.316616 | 0.949847 | 17 | 74 | 40 |
| round_size_rel | R | withheld (downgraded: at most worth a look) | | 16 | 35 | 19 |
| round_size_rel | px | withheld (downgraded: at most worth a look) | | 16 | 35 | 19 |
| rounds_off | R | 0.000323 | 0.000968 | 16 | 510 | 278 |
| rounds_off | px | 0.090341 | 0.271024 | 16 | 510 | 278 |
| rounds_size | R | withheld (downgraded: at most worth a look) | | 16 | 35 | 19 |
| rounds_size | px | withheld (downgraded: at most worth a look) | | 16 | 35 | 19 |
| six_off | R | 0.000682 | 0.002046 | 17 | 74 | 40 |
| six_off | px | 0.161369 | 0.484108 | 17 | 74 | 40 |
| six_rot | deg | 0.130996 | 0.392988 | 17 | 37 | 20 |
| six_rot | degR | 39.020991 | 117.062972 | 17 | 37 | 20 |
| three_off | R | 0.000491 | 0.001473 | 16 | 70 | 38 |
| three_off | px | 0.099706 | 0.299117 | 16 | 70 | 38 |
| three_rot | deg | 0.098495 | 0.295485 | 16 | 35 | 19 |
| three_rot | degR | 50.555710 | 151.667131 | 16 | 35 | 19 |
| twelve_centreline | deg | 0.116892 | 0.350675 | 16 | 35 | 19 |
| twelve_centreline | degR | 36.195226 | 108.585678 | 16 | 35 | 19 |
| twelve_lateral | R | 0.000940 | 0.002819 | 16 | 35 | 19 |
| twelve_lateral | px | 0.161133 | 0.483400 | 16 | 35 | 19 |
| twelve_sides | deg | 0.116213 | 0.348639 | 16 | 70 | 38 |
| twelve_sides | degR | 35.433887 | 106.301661 | 16 | 70 | 38 |

12 side agreement: 22 watches with 2+ photos; max per-watch median |left - right| 0.682 deg; robust spread 0.048 deg; limit 0.826 deg.

## Genuine reference

| feature | watches | genuine max (far) |
|---|---:|---:|
| three_rot | 24 | 0.7283 |
| three_off | 24 | 0.0025 |
| six_rot | 24 | 0.5521 |
| six_off | 24 | 0.0035 |
| nine_rot | 24 | 0.7763 |
| nine_off | 24 | 0.0021 |
| rounds_off | 23 | 0.0029 |
| ring_rot | 24 | 0.1472 |
| ring_shift | 24 | 0.0066 |
| rounds_size | 23 | 0.0012 |
| round_size_rel | 23 | 0.0014 |

Nominals: three_rot -0.0560, six_rot -0.0291, nine_rot +0.0916, ring_rot +0.0158, rounds_size -0.0004
