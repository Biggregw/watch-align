# submariner_124060 measurement-uncertainty allowances (genuine photos only)

Written by `build_sub_reference.py` (measurement_uncertainty.py definitions). sigma = pooled within-watch SD of
one photo's reading over every genuine physical watch with 2+ usable non-shared photos. CLEAR only when the excess
beyond the genuine maximum exceeds K = 3 sigma.

| family | unit | sigma | K x sigma | watches | photos | dof |
|---|---|---:|---:|---:|---:|---:|
| nine_off | R | 0.000427 | 0.001282 | 33 | 146 | 80 |
| nine_off | px | 0.098231 | 0.294694 | 33 | 146 | 80 |
| nine_rot | deg | 0.100213 | 0.300640 | 33 | 73 | 40 |
| nine_rot | degR | 37.879405 | 113.638215 | 33 | 73 | 40 |
| ring_rot | deg | 0.010861 | 0.032583 | 33 | 73 | 40 |
| ring_rot | degR | 4.321250 | 12.963750 | 33 | 73 | 40 |
| ring_shift | R | 0.001252 | 0.003756 | 33 | 146 | 80 |
| ring_shift | px | 0.241586 | 0.724759 | 33 | 146 | 80 |
| round_size_rel | R | withheld (downgraded: at most worth a look) | | 32 | 71 | 39 |
| round_size_rel | px | withheld (downgraded: at most worth a look) | | 32 | 71 | 39 |
| rounds_off | R | 0.000259 | 0.000777 | 32 | 1038 | 570 |
| rounds_off | px | 0.073724 | 0.221171 | 32 | 1038 | 570 |
| rounds_size | R | withheld (downgraded: at most worth a look) | | 32 | 71 | 39 |
| rounds_size | px | withheld (downgraded: at most worth a look) | | 32 | 71 | 39 |
| six_off | R | 0.000513 | 0.001540 | 33 | 146 | 80 |
| six_off | px | 0.122111 | 0.366332 | 33 | 146 | 80 |
| six_rot | deg | 0.108498 | 0.325494 | 33 | 73 | 40 |
| six_rot | degR | 31.047022 | 93.141065 | 33 | 73 | 40 |
| three_off | R | 0.000426 | 0.001277 | 32 | 142 | 78 |
| three_off | px | 0.094849 | 0.284547 | 32 | 142 | 78 |
| three_rot | deg | 0.106631 | 0.319894 | 32 | 71 | 39 |
| three_rot | degR | 39.082930 | 117.248790 | 32 | 71 | 39 |
| twelve_centreline | deg | 0.098075 | 0.294226 | 32 | 71 | 39 |
| twelve_centreline | degR | 35.298359 | 105.895077 | 32 | 71 | 39 |
| twelve_lateral | R | 0.000675 | 0.002024 | 32 | 71 | 39 |
| twelve_lateral | px | 0.133224 | 0.399672 | 32 | 71 | 39 |
| twelve_sides | deg | 0.109090 | 0.327271 | 32 | 142 | 78 |
| twelve_sides | degR | 35.931120 | 107.793360 | 32 | 142 | 78 |

12 side agreement: 38 watches with 2+ photos; max per-watch median |left - right| 0.659 deg; robust spread 0.083 deg; limit 0.908 deg.

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
