# Alpha99 measurement-uncertainty allowances (genuine photos only)

Written by `measurement_uncertainty.py`. sigma = pooled within-watch SD of one photo's reading, over every
genuine physical watch with 2+ usable non-shared photos (components pooled). Rule fixed in advance: CLEAR
only when the excess beyond the genuine maximum exceeds K = 3 sigma.

| family | unit | sigma | K x sigma | watches | photos | dof |
|---|---|---:|---:|---:|---:|---:|
| date_tilt | deg | 0.148998 | 0.446995 | 20 | 71 | 51 |
| date_tilt | degR | 119.817161 | 359.451483 | 19 | 67 | 48 |
| nine_off | R | 0.000897 | 0.002691 | 8 | 98 | 82 |
| nine_off | px | 0.197582 | 0.592745 | 8 | 98 | 82 |
| nine_rot | deg | 0.205383 | 0.616149 | 8 | 49 | 41 |
| nine_rot | degR | 43.434971 | 130.304914 | 8 | 49 | 41 |
| ring_rot | deg | 0.023854 | 0.071562 | 9 | 51 | 42 |
| ring_rot | degR | 7.908438 | 23.725313 | 9 | 51 | 42 |
| ring_shift | R | 0.000592 | 0.001775 | 9 | 102 | 84 |
| ring_shift | px | 0.319598 | 0.958795 | 9 | 102 | 84 |
| rounds_off | R | 0.000573 | 0.001718 | 9 | 688 | 560 |
| rounds_off | px | 0.112675 | 0.338024 | 9 | 688 | 560 |
| six_off | R | 0.001246 | 0.003738 | 9 | 102 | 84 |
| six_off | px | 0.250860 | 0.752581 | 9 | 102 | 84 |
| six_rot | deg | 0.194996 | 0.584988 | 9 | 51 | 42 |
| six_rot | degR | 45.340069 | 136.020206 | 9 | 51 | 42 |
| twelve_centreline | deg | 0.184105 | 0.552315 | 9 | 51 | 42 |
| twelve_centreline | degR | 72.582101 | 217.746302 | 9 | 51 | 42 |
| twelve_lateral | R | 0.002019 | 0.006058 | 9 | 51 | 42 |
| twelve_lateral | px | 0.412108 | 1.236325 | 9 | 51 | 42 |
| twelve_sides | deg | 0.486607 | 1.459822 | 9 | 102 | 84 |
| twelve_sides | degR | 82.765016 | 248.295048 | 9 | 102 | 84 |

## 12 triangle side agreement (Alpha101 edge-consistency limit)

Genuine watches with 2+ photos: 25 (96 photos), SWE included (lighting is
exactly what this measures). Max per-watch median |left - right| 0.770 deg;
robust photo-to-photo spread 0.074 deg; limit = max + 3 x spread = 0.992 deg.
