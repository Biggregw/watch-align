# submariner_124060 measurement-uncertainty allowances (genuine photos only)

Written by `build_sub_reference.py` (measurement_uncertainty.py definitions). sigma = pooled within-watch SD of
one photo's reading over every genuine physical watch with 2+ usable non-shared photos. CLEAR only when the excess
beyond the genuine maximum exceeds K = 3 sigma.

| family | unit | sigma | K x sigma | watches | photos | dof |
|---|---|---:|---:|---:|---:|---:|
| nine_off | R | 0.000433 | 0.001298 | 32 | 142 | 78 |
| nine_off | px | 0.099466 | 0.298399 | 32 | 142 | 78 |
| nine_rot | deg | 0.101463 | 0.304390 | 32 | 71 | 39 |
| nine_rot | degR | 38.348226 | 115.044677 | 32 | 71 | 39 |
| ring_rot | deg | 0.010999 | 0.032996 | 32 | 71 | 39 |
| ring_rot | degR | 4.375977 | 13.127930 | 32 | 71 | 39 |
| ring_shift | R | 0.001268 | 0.003804 | 32 | 142 | 78 |
| ring_shift | px | 0.244663 | 0.733988 | 32 | 142 | 78 |
| round_size_rel | R | withheld (downgraded: at most worth a look) | | 31 | 69 | 38 |
| round_size_rel | px | withheld (downgraded: at most worth a look) | | 31 | 69 | 38 |
| rounds_off | R | 0.000262 | 0.000787 | 31 | 1010 | 556 |
| rounds_off | px | 0.074610 | 0.223829 | 31 | 1010 | 556 |
| rounds_size | R | withheld (downgraded: at most worth a look) | | 31 | 69 | 38 |
| rounds_size | px | withheld (downgraded: at most worth a look) | | 31 | 69 | 38 |
| six_off | R | 0.000520 | 0.001560 | 32 | 142 | 78 |
| six_off | px | 0.123616 | 0.370847 | 32 | 142 | 78 |
| six_rot | deg | 0.109865 | 0.329596 | 32 | 71 | 39 |
| six_rot | degR | 31.432390 | 94.297169 | 32 | 71 | 39 |
| three_off | R | 0.000431 | 0.001293 | 31 | 138 | 76 |
| three_off | px | 0.096028 | 0.288084 | 31 | 138 | 76 |
| three_rot | deg | 0.107972 | 0.323916 | 31 | 69 | 38 |
| three_rot | degR | 39.565820 | 118.697461 | 31 | 69 | 38 |
| twelve_centreline | deg | 0.099346 | 0.298039 | 31 | 69 | 38 |
| twelve_centreline | degR | 35.753717 | 107.261151 | 31 | 69 | 38 |
| twelve_lateral | R | 0.000684 | 0.002051 | 31 | 69 | 38 |
| twelve_lateral | px | 0.134965 | 0.404894 | 31 | 69 | 38 |
| twelve_sides | deg | 0.110285 | 0.330854 | 31 | 138 | 76 |
| twelve_sides | degR | 36.264931 | 108.794792 | 31 | 138 | 76 |

12 side agreement: 37 watches with 2+ photos; max per-watch median |left - right| 0.659 deg; robust spread 0.073 deg; limit 0.878 deg.

## Genuine reference

| feature | watches | genuine max (far) |
|---|---:|---:|
| three_rot | 95 | 0.8551 |
| three_off | 95 | 0.0025 |
| six_rot | 88 | 0.8617 |
| six_off | 88 | 0.0035 |
| nine_rot | 96 | 0.8317 |
| nine_off | 96 | 0.0025 |
| rounds_off | 95 | 0.0029 |
| ring_rot | 96 | 0.1688 |
| ring_shift | 96 | 0.0066 |
| rounds_size | 95 | 0.0012 |
| round_size_rel | 95 | 0.0029 |

Nominals: three_rot -0.1277, six_rot -0.0712, nine_rot +0.0126, ring_rot -0.0076, rounds_size -0.0004
