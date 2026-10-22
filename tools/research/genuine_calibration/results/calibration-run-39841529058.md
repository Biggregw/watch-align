# Genuine calibration of the MarkerGeometry readings (research; nothing deployed)

## Populations

| Layout | Photos | Watches | Watches by source |
|---|---:|---:|---|
| gmt_126710 | 24 | 22 | bobswatches.com 21, phillips.com 1 |
| submariner_124060 | 64 | 56 | europeanwatch.com 27, bobswatches.com 24, davidsw.com 3, swisswatchexpo.com 2 |
| submariner_126610 | 90 | 90 | bobswatches.com 90 |

## Pooled genuine distributions (watch-weighted; |value - centre|; 90 % watch-bootstrap intervals)

| Group | Unit | Watches | Photos | Sources (max share) | Centre | Median | Robust SD | p95 | p99 | Max | Within-watch SD | Between-watch SD |
|---|---|---:|---:|---|---:|---:|---:|---|---|---:|---:|---:|
| round.tang | % R | 168 | 178 | 5 (0.80) | 0.000 | 0.052 | 0.076 | 0.154 [0.146, 0.165] | 0.232 [0.192, 0.260] | 0.321 | 0.004 | 0.024 |
| round.rad | % R | 168 | 178 | 5 (0.80) | 0.000 | 0.049 | 0.072 | 0.142 [0.135, 0.155] | 0.199 [0.185, 0.216] | 0.252 | 0.004 | 0.026 |
| round.pos | % R | 168 | 178 | 5 (0.80) | 0.000 | 0.090 | 0.135 | 0.189 [0.182, 0.196] | 0.257 [0.232, 0.293] | 0.342 | 0.004 | 0.031 |
| baton.tang | % R | 168 | 178 | 5 (0.80) | 0.000 | 0.067 | 0.108 | 0.177 [0.170, 0.185] | 0.222 [0.209, 0.251] | 0.265 | 0.005 | 0.055 |
| baton.rad | % R | 168 | 178 | 5 (0.80) | 0.000 | 0.060 | 0.089 | 0.162 [0.153, 0.174] | 0.200 [0.183, 0.234] | 0.296 | 0.012 | 0.062 |
| baton.pos | % R | 168 | 178 | 5 (0.80) | 0.000 | 0.115 | 0.167 | 0.216 [0.198, 0.225] | 0.262 [0.229, 0.296] | 0.313 | 0.006 | 0.042 |
| baton.tilt | deg | 168 | 178 | 5 (0.80) | 0.000 | 0.212 | 0.335 | 0.624 [0.591, 0.676] | 0.780 [0.716, 0.784] | 0.810 | 0.023 | 0.226 |
| marker.pos | % R | 168 | 178 | 5 (0.80) | 0.000 | 0.096 | 0.139 | 0.196 [0.190, 0.203] | 0.260 [0.244, 0.278] | 0.342 | 0.004 | 0.027 |
| triangle.tang | % R | 157 | 167 | 5 (0.79) | 0.000 | 0.052 | 0.077 | 0.167 [0.156, 0.192] | 0.218 [0.182, 0.350] | 0.350 | 0.002 | 0.077 |
| triangle.rad | % R | 157 | 167 | 5 (0.79) | 0.000 | 0.069 | 0.102 | 0.196 [0.171, 0.221] | 0.257 [0.221, 0.418] | 0.418 | 0.007 | 0.091 |
| triangle.tilt | deg | 157 | 167 | 5 (0.79) | 0.000 | 0.318 | 0.471 | 0.990 [0.886, 1.049] | 1.095 [1.016, 1.167] | 1.167 | 0.011 | 0.462 |
| ring.place | % R | 168 | 178 | 5 (0.80) | (-0.010, 0.007) | 0.100 | 0.148 | 0.241 [0.223, 0.268] | 0.345 [0.268, 0.445] | 0.445 | — | — |
| ring.rot | deg | 168 | 178 | 5 (0.80) | 0.006 | 0.039 | 0.058 | 0.117 [0.106, 0.143] | 0.166 [0.143, 0.178] | 0.178 | 0.001 | 0.057 |
| ring.scale | % | 168 | 178 | 5 (0.80) | -0.016 | 0.045 | 0.065 | 0.185 [0.171, 0.251] | 0.289 [0.251, 0.298] | 0.298 | 0.005 | 0.065 |

## By family (p95 / p99 of the pooled deviation, 90 % intervals)

| Group | Layout | Watches | Signed median | p95 | p99 |
|---|---|---:|---:|---|---|
| round.tang | gmt_126710 | 22 | 0.003 | 0.169 [0.130, 0.184] | 0.196 [0.169, 0.242] |
| round.tang | submariner_124060 | 56 | -0.004 | 0.148 [0.138, 0.159] | 0.232 [0.176, 0.289] |
| round.tang | submariner_126610 | 90 | 0.002 | 0.154 [0.145, 0.173] | 0.241 [0.185, 0.261] |
| round.rad | gmt_126710 | 22 | 0.021 | 0.135 [0.118, 0.157] | 0.188 [0.144, 0.189] |
| round.rad | submariner_124060 | 56 | -0.009 | 0.141 [0.131, 0.158] | 0.186 [0.158, 0.216] |
| round.rad | submariner_126610 | 90 | -0.001 | 0.145 [0.134, 0.160] | 0.216 [0.195, 0.230] |
| round.pos | gmt_126710 | 22 | 0.091 | 0.193 [0.164, 0.202] | 0.244 [0.202, 0.245] |
| round.pos | submariner_124060 | 56 | 0.087 | 0.183 [0.162, 0.191] | 0.303 [0.193, 0.325] |
| round.pos | submariner_126610 | 90 | 0.091 | 0.194 [0.179, 0.215] | 0.260 [0.232, 0.263] |
| baton.tang | gmt_126710 | 22 | 0.043 | 0.169 [0.125, 0.265] | 0.265 [0.169, 0.265] |
| baton.tang | submariner_124060 | 56 | -0.009 | 0.166 [0.149, 0.174] | 0.221 [0.176, 0.251] |
| baton.tang | submariner_126610 | 90 | -0.014 | 0.184 [0.170, 0.214] | 0.222 [0.214, 0.246] |
| baton.rad | gmt_126710 | 22 | -0.067 | 0.166 [0.150, 0.173] | 0.173 [0.166, 0.173] |
| baton.rad | submariner_124060 | 56 | 0.028 | 0.185 [0.160, 0.200] | 0.272 [0.200, 0.296] |
| baton.rad | submariner_126610 | 90 | 0.005 | 0.138 [0.129, 0.152] | 0.183 [0.152, 0.183] |
| baton.pos | gmt_126710 | 22 | 0.110 | 0.193 [0.176, 0.313] | 0.313 [0.193, 0.313] |
| baton.pos | submariner_124060 | 56 | 0.122 | 0.223 [0.198, 0.229] | 0.278 [0.229, 0.296] |
| baton.pos | submariner_126610 | 90 | 0.108 | 0.205 [0.186, 0.226] | 0.254 [0.225, 0.262] |
| baton.tilt | gmt_126710 | 22 | -0.034 | 0.499 [0.414, 0.583] | 0.583 [0.458, 0.583] |
| baton.tilt | submariner_124060 | 56 | -0.072 | 0.599 [0.506, 0.703] | 0.750 [0.703, 0.781] |
| baton.tilt | submariner_126610 | 90 | 0.004 | 0.634 [0.591, 0.699] | 0.784 [0.699, 0.810] |
| marker.pos | gmt_126710 | 22 | 0.098 | 0.193 [0.175, 0.202] | 0.245 [0.213, 0.313] |
| marker.pos | submariner_124060 | 56 | 0.096 | 0.195 [0.186, 0.207] | 0.278 [0.224, 0.314] |
| marker.pos | submariner_126610 | 90 | 0.096 | 0.197 [0.187, 0.212] | 0.257 [0.236, 0.263] |
| triangle.tang | gmt_126710 | 21 | -0.004 | 0.160 [0.106, 0.163] | 0.163 [0.156, 0.163] |
| triangle.tang | submariner_124060 | 55 | -0.023 | 0.207 [0.166, 0.218] | 0.218 [0.207, 0.218] |
| triangle.tang | submariner_126610 | 81 | -0.017 | 0.146 [0.106, 0.182] | 0.350 [0.147, 0.350] |
| triangle.rad | gmt_126710 | 21 | -0.022 | 0.120 [0.100, 0.235] | 0.235 [0.103, 0.235] |
| triangle.rad | submariner_124060 | 55 | 0.040 | 0.247 [0.183, 0.418] | 0.418 [0.247, 0.418] |
| triangle.rad | submariner_126610 | 81 | 0.023 | 0.185 [0.146, 0.193] | 0.200 [0.193, 0.200] |
| triangle.tilt | gmt_126710 | 21 | -0.087 | 0.657 [0.490, 0.688] | 0.688 [0.596, 0.688] |
| triangle.tilt | submariner_124060 | 55 | 0.210 | 0.990 [0.770, 1.167] | 1.167 [0.990, 1.167] |
| triangle.tilt | submariner_126610 | 81 | -0.130 | 0.991 [0.886, 1.056] | 1.095 [1.049, 1.095] |
| ring.place | gmt_126710 | 22 | — | 0.236 [0.169, 0.270] | 0.270 [0.232, 0.270] |
| ring.place | submariner_124060 | 56 | — | 0.268 [0.222, 0.445] | 0.445 [0.244, 0.445] |
| ring.place | submariner_126610 | 90 | — | 0.235 [0.181, 0.243] | 0.345 [0.241, 0.345] |
| ring.rot | gmt_126710 | 22 | -0.010 | 0.099 [0.070, 0.117] | 0.117 [0.096, 0.117] |
| ring.rot | submariner_124060 | 56 | -0.008 | 0.143 [0.118, 0.166] | 0.166 [0.143, 0.166] |
| ring.rot | submariner_126610 | 90 | 0.008 | 0.111 [0.094, 0.144] | 0.178 [0.115, 0.178] |
| ring.scale | gmt_126710 | 22 | -0.194 | 0.296 [0.251, 0.298] | 0.298 [0.271, 0.298] |
| ring.scale | submariner_124060 | 56 | -0.018 | 0.131 [0.096, 0.185] | 0.185 [0.130, 0.185] |
| ring.scale | submariner_126610 | 90 | 0.005 | 0.134 [0.081, 0.143] | 0.149 [0.134, 0.149] |

## By clock position (signed watch-weighted median; p95 of the pooled deviation)

**round.tang** (Kruskal-Wallis over hours on watch medians: p = 0.0000)

gmt_126710/1: n 21, med -0.072, p95 0.190 · gmt_126710/2: n 5, med -0.040, p95 0.102 · gmt_126710/4: n 22, med 0.044, p95 0.096 · gmt_126710/5: n 14, med 0.036, p95 0.139 · gmt_126710/7: n 22, med -0.037, p95 0.132 · gmt_126710/8: n 21, med -0.066, p95 0.157 · gmt_126710/10: n 21, med 0.003, p95 0.184 · gmt_126710/11: n 16, med 0.017, p95 0.178 · submariner_124060/1: n 52, med -0.020, p95 0.146 · submariner_124060/2: n 11, med -0.019, p95 0.103 · submariner_124060/4: n 52, med -0.018, p95 0.184 · submariner_124060/5: n 53, med 0.008, p95 0.151 · submariner_124060/7: n 53, med 0.017, p95 0.154 · submariner_124060/8: n 52, med 0.004, p95 0.153 · submariner_124060/10: n 54, med 0.003, p95 0.134 · submariner_124060/11: n 52, med -0.004, p95 0.098 · submariner_126610/1: n 86, med -0.040, p95 0.154 · submariner_126610/2: n 16, med -0.063, p95 0.192 · submariner_126610/4: n 85, med 0.014, p95 0.138 · submariner_126610/5: n 81, med 0.048, p95 0.180 · submariner_126610/7: n 81, med -0.013, p95 0.141 · submariner_126610/8: n 87, med -0.065, p95 0.153 · submariner_126610/10: n 86, med 0.037, p95 0.174 · submariner_126610/11: n 86, med 0.046, p95 0.160

**round.rad** (Kruskal-Wallis over hours on watch medians: p = 0.0000)

gmt_126710/1: n 21, med 0.033, p95 0.111 · gmt_126710/2: n 5, med 0.052, p95 0.110 · gmt_126710/4: n 22, med 0.022, p95 0.107 · gmt_126710/5: n 14, med 0.051, p95 0.122 · gmt_126710/7: n 22, med 0.035, p95 0.150 · gmt_126710/8: n 21, med -0.011, p95 0.109 · gmt_126710/10: n 21, med -0.028, p95 0.141 · gmt_126710/11: n 16, med -0.011, p95 0.125 · submariner_124060/1: n 52, med -0.010, p95 0.132 · submariner_124060/2: n 11, med 0.029, p95 0.093 · submariner_124060/4: n 52, med 0.008, p95 0.158 · submariner_124060/5: n 53, med -0.009, p95 0.147 · submariner_124060/7: n 53, med -0.004, p95 0.117 · submariner_124060/8: n 52, med -0.025, p95 0.155 · submariner_124060/10: n 54, med -0.017, p95 0.135 · submariner_124060/11: n 52, med -0.007, p95 0.151 · submariner_126610/1: n 86, med 0.029, p95 0.155 · submariner_126610/2: n 16, med 0.052, p95 0.217 · submariner_126610/4: n 85, med -0.013, p95 0.157 · submariner_126610/5: n 81, med -0.027, p95 0.147 · submariner_126610/7: n 81, med 0.001, p95 0.102 · submariner_126610/8: n 87, med -0.008, p95 0.121 · submariner_126610/10: n 86, med -0.004, p95 0.129 · submariner_126610/11: n 86, med -0.003, p95 0.166

**baton.tang** (Kruskal-Wallis over hours on watch medians: p = 0.0018)

gmt_126710/6: n 21, med 0.063, p95 0.169 · gmt_126710/9: n 21, med 0.003, p95 0.143 · submariner_124060/3: n 55, med -0.013, p95 0.198 · submariner_124060/6: n 52, med -0.012, p95 0.156 · submariner_124060/9: n 55, med -0.007, p95 0.171 · submariner_126610/6: n 83, med 0.008, p95 0.172 · submariner_126610/9: n 88, med -0.039, p95 0.184

**baton.rad** (Kruskal-Wallis over hours on watch medians: p = 0.2594)

gmt_126710/6: n 21, med -0.103, p95 0.162 · gmt_126710/9: n 21, med -0.035, p95 0.166 · submariner_124060/3: n 55, med 0.016, p95 0.200 · submariner_124060/6: n 52, med 0.045, p95 0.192 · submariner_124060/9: n 55, med 0.023, p95 0.164 · submariner_126610/6: n 83, med -0.002, p95 0.152 · submariner_126610/9: n 88, med 0.016, p95 0.130

**baton.tilt** (Kruskal-Wallis over hours on watch medians: p = 0.0010)

gmt_126710/6: n 21, med -0.079, p95 0.499 · gmt_126710/9: n 21, med 0.007, p95 0.458 · submariner_124060/3: n 55, med -0.119, p95 0.676 · submariner_124060/6: n 52, med -0.068, p95 0.512 · submariner_124060/9: n 55, med -0.030, p95 0.603 · submariner_126610/6: n 83, med -0.020, p95 0.638 · submariner_126610/9: n 88, med 0.050, p95 0.634

## Photographic conditions (p95 of the deviation; watches)

- **round.tang**: rpx 250-350: 0.154 (33); rpx 350-500: 0.154 (136); tilt 0-3: 0.154 (54); tilt 3-6: 0.156 (101); tilt 6-11: 0.139 (13)
- **round.rad**: rpx 250-350: 0.151 (33); rpx 350-500: 0.141 (136); tilt 0-3: 0.159 (54); tilt 3-6: 0.139 (101); tilt 6-11: 0.113 (13)
- **round.pos**: rpx 250-350: 0.187 (33); rpx 350-500: 0.191 (136); tilt 0-3: 0.195 (54); tilt 3-6: 0.188 (101); tilt 6-11: 0.154 (13)
- **baton.tang**: rpx 250-350: 0.171 (33); rpx 350-500: 0.178 (136); tilt 0-3: 0.160 (54); tilt 3-6: 0.183 (101); tilt 6-11: 0.214 (13)
- **baton.rad**: rpx 250-350: 0.192 (33); rpx 350-500: 0.155 (136); tilt 0-3: 0.181 (54); tilt 3-6: 0.155 (101); tilt 6-11: 0.132 (13)
- **baton.pos**: rpx 250-350: 0.207 (33); rpx 350-500: 0.221 (136); tilt 0-3: 0.210 (54); tilt 3-6: 0.216 (101); tilt 6-11: 0.221 (13)
- **baton.tilt**: rpx 250-350: 0.562 (33); rpx 350-500: 0.632 (136); tilt 0-3: 0.562 (54); tilt 3-6: 0.646 (101); tilt 6-11: 0.526 (13)
- **marker.pos**: rpx 250-350: 0.193 (33); rpx 350-500: 0.197 (136); tilt 0-3: 0.196 (54); tilt 3-6: 0.198 (101); tilt 6-11: 0.178 (13)
- **triangle.tang**: rpx 250-350: 0.217 (33); rpx 350-500: 0.156 (125); tilt 0-3: 0.217 (52); tilt 3-6: 0.156 (94); tilt 6-11: 0.192 (11)
- **triangle.rad**: rpx 250-350: 0.247 (33); rpx 350-500: 0.190 (125); tilt 0-3: 0.193 (52); tilt 3-6: 0.171 (94); tilt 6-11: 0.418 (11)
- **triangle.tilt**: rpx 250-350: 1.016 (33); rpx 350-500: 0.938 (125); tilt 0-3: 0.938 (52); tilt 3-6: 0.991 (94); tilt 6-11: 1.016 (11)
- **ring.place**: rpx 250-350: 0.293 (33); rpx 350-500: 0.235 (136); tilt 0-3: 0.244 (54); tilt 3-6: 0.238 (101); tilt 6-11: 0.445 (13)
- **ring.rot**: rpx 250-350: 0.137 (33); rpx 350-500: 0.114 (136); tilt 0-3: 0.118 (54); tilt 3-6: 0.114 (101); tilt 6-11: 0.096 (13)
- **ring.scale**: rpx 250-350: 0.185 (33); rpx 350-500: 0.220 (136); tilt 0-3: 0.184 (54); tilt 3-6: 0.177 (101); tilt 6-11: 0.292 (13)

## Source and watch sensitivity of p99

| Group | p99 | p99 without each source | Largest change from one watch |
|---|---:|---|---:|
| round.tang | 0.232 | bobswatches.com 0.213, davidsw.com 0.232, europeanwatch.com 0.232, phillips.com 0.232, swisswatchexpo.com 0.230 | 0.002 |
| round.rad | 0.199 | bobswatches.com 0.216, davidsw.com 0.199, europeanwatch.com 0.199, phillips.com 0.199, swisswatchexpo.com 0.199 | 0.005 |
| round.pos | 0.257 | bobswatches.com 0.303, davidsw.com 0.257, europeanwatch.com 0.252, phillips.com 0.257, swisswatchexpo.com 0.252 | 0.012 |
| baton.tang | 0.222 | bobswatches.com 0.209, davidsw.com 0.222, europeanwatch.com 0.222, phillips.com 0.222, swisswatchexpo.com 0.222 | 0.003 |
| baton.rad | 0.200 | bobswatches.com 0.272, davidsw.com 0.196, europeanwatch.com 0.192, phillips.com 0.200, swisswatchexpo.com 0.200 | 0.004 |
| baton.pos | 0.262 | bobswatches.com 0.278, davidsw.com 0.262, europeanwatch.com 0.262, phillips.com 0.262, swisswatchexpo.com 0.262 | 0.007 |
| baton.tilt | 0.780 | bobswatches.com 0.741, davidsw.com 0.780, europeanwatch.com 0.780, phillips.com 0.780, swisswatchexpo.com 0.780 | 0.050 |
| marker.pos | 0.260 | bobswatches.com 0.303, davidsw.com 0.260, europeanwatch.com 0.257, phillips.com 0.260, swisswatchexpo.com 0.257 | 0.009 |
| triangle.tang | 0.218 | bobswatches.com 0.218, davidsw.com 0.218, europeanwatch.com 0.182, phillips.com 0.218, swisswatchexpo.com 0.218 | 0.000 |
| triangle.rad | 0.257 | bobswatches.com 0.418, davidsw.com 0.257, europeanwatch.com 0.257, phillips.com 0.257, swisswatchexpo.com 0.247 | 0.010 |
| triangle.tilt | 1.095 | bobswatches.com 1.167, davidsw.com 1.095, europeanwatch.com 1.056, phillips.com 1.095, swisswatchexpo.com 1.095 | 0.039 |
| ring.place | 0.345 | bobswatches.com 0.445, davidsw.com 0.345, europeanwatch.com 0.345, phillips.com 0.345, swisswatchexpo.com 0.293 | 0.052 |
| ring.rot | 0.166 | bobswatches.com 0.143, davidsw.com 0.166, europeanwatch.com 0.166, phillips.com 0.166, swisswatchexpo.com 0.166 | 0.000 |
| ring.scale | 0.289 | bobswatches.com 0.296, davidsw.com 0.292, europeanwatch.com 0.292, phillips.com 0.271, swisswatchexpo.com 0.292 | 0.018 |

## Candidate limits and false alarms

### `axis/quantile`: common per-reading exceedance q; per-reading p99 limits alone give whole-watch false alarms 0.183

| Target whole-watch FA | Parameter | In-sample FA | 5-fold CV FA | Holdout FA (watches) | Per-reading FA | round.tang | round.rad | baton.tang | baton.rad | baton.tilt | triangle.tang | triangle.rad | triangle.tilt | ring.place | ring.rot | ring.scale |
|---|---|---:|---:|---:|---:|---|---|---|---|---|---|---|---|---|---|---|
| 0.05 | q 0.00298 | 0.054 | 0.137 | 0.172 (29) | 0.0021 | 0.289 [0.251, 0.313] | 0.236 [0.216, 0.249] | 0.251 [0.221, 0.265] | 0.272 [0.200, 0.296] | 0.784 [0.750, 0.810] | 0.350 [0.208, 0.350] | 0.418 [0.247, 0.418] | 1.167 [1.056, 1.167] | 0.445 [0.270, 0.445] | 0.178 [0.165, 0.178] | 0.298 [0.270, 0.298] |
| 0.01 | q 0.00085 | 0.003 | 0.092 | 0.138 (29) | 0.0001 | 0.317 [0.291, 0.321] | 0.252 [0.236, 0.252] | 0.265 [0.246, 0.265] | 0.296 [0.221, 0.296] | 0.810 [0.780, 0.810] | 0.350 [0.217, 0.350] | 0.418 [0.235, 0.418] | 1.167 [1.056, 1.167] | 0.445 [0.293, 0.445] | 0.178 [0.166, 0.178] | 0.298 [0.254, 0.298] |

### `axis/maxz`: one multiplier k on robust genuine scales (sigma: round.tang 0.078, round.rad 0.073, baton.tang 0.099, baton.rad 0.089, baton.tilt 0.315, triangle.tang 0.077, triangle.rad 0.102, triangle.tilt 0.471, ring.place 0.085, ring.rot 0.058, ring.scale 0.067)

| Target whole-watch FA | Parameter | In-sample FA | 5-fold CV FA | Holdout FA (watches) | Per-reading FA | round.tang | round.rad | baton.tang | baton.rad | baton.tilt | triangle.tang | triangle.rad | triangle.tilt | ring.place | ring.rot | ring.scale |
|---|---|---:|---:|---:|---:|---|---|---|---|---|---|---|---|---|---|---|
| 0.05 | k 3.81 | 0.048 | 0.048 | 0.069 (29) | 0.0022 | 0.297 [0.264, 0.348] | 0.278 [0.252, 0.333] | 0.379 [0.339, 0.456] | 0.339 [0.296, 0.420] | 1.201 [1.031, 1.475] | 0.292 [0.231, 0.350] | 0.390 [0.322, 0.475] | 1.797 [1.502, 2.169] | 0.322 [0.272, 0.380] | 0.220 [0.196, 0.271] | 0.254 [0.215, 0.298] |
|  | alarms by group at this k: ring.scale 5, round.tang 3, ring.place 2, triangle.tang 1 |
| 0.01 | k 4.56 | 0.006 | 0.024 | 0.000 (29) | 0.0002 | 0.355 [0.313, 0.445] | 0.333 [0.294, 0.418] | 0.454 [0.402, 0.580] | 0.406 [0.342, 0.527] | 1.437 [1.223, 1.847] | 0.350 [0.283, 0.438] | 0.467 [0.372, 0.600] | 2.151 [1.749, 2.756] | 0.386 [0.337, 0.464] | 0.264 [0.231, 0.339] | 0.304 [0.271, 0.410] |
|  | alarms by group at this k: ring.place 1 |

### `position/quantile`: common per-reading exceedance q; per-reading p99 limits alone give whole-watch false alarms 0.126

| Target whole-watch FA | Parameter | In-sample FA | 5-fold CV FA | Holdout FA (watches) | Per-reading FA | round.pos | baton.pos | baton.tilt | triangle.tang | triangle.rad | triangle.tilt | ring.place | ring.rot | ring.scale |
|---|---|---:|---:|---:|---:|---|---|---|---|---|---|---|---|---|
| 0.05 | q 0.00595 | 0.048 | 0.116 | 0.103 (29) | 0.0031 | 0.263 [0.251, 0.313] | 0.278 [0.251, 0.313] | 0.784 [0.730, 0.810] | 0.350 [0.208, 0.350] | 0.418 [0.247, 0.418] | 1.167 [1.049, 1.167] | 0.445 [0.270, 0.445] | 0.178 [0.166, 0.178] | 0.298 [0.271, 0.298] |
| 0.01 | q 0.00184 | 0.012 | 0.074 | 0.103 (29) | 0.0007 | 0.314 [0.263, 0.342] | 0.313 [0.278, 0.313] | 0.810 [0.763, 0.810] | 0.350 [0.217, 0.350] | 0.418 [0.235, 0.418] | 1.167 [1.056, 1.167] | 0.445 [0.270, 0.445] | 0.178 [0.144, 0.178] | 0.298 [0.254, 0.298] |

### `position/maxz`: one multiplier k on robust genuine scales (sigma: round.pos 0.077, baton.pos 0.098, baton.tilt 0.315, triangle.tang 0.077, triangle.rad 0.102, triangle.tilt 0.471, ring.place 0.085, ring.rot 0.058, ring.scale 0.067)

| Target whole-watch FA | Parameter | In-sample FA | 5-fold CV FA | Holdout FA (watches) | Per-reading FA | round.pos | baton.pos | baton.tilt | triangle.tang | triangle.rad | triangle.tilt | ring.place | ring.rot | ring.scale |
|---|---|---:|---:|---:|---:|---|---|---|---|---|---|---|---|---|
| 0.05 | k 4.08 | 0.048 | 0.054 | 0.069 (29) | 0.0036 | 0.313 [0.264, 0.342] | 0.398 [0.339, 0.446] | 1.286 [1.040, 1.469] | 0.313 [0.243, 0.350] | 0.417 [0.325, 0.486] | 1.924 [1.577, 2.202] | 0.345 [0.281, 0.390] | 0.236 [0.197, 0.276] | 0.272 [0.220, 0.308] |
|  | alarms by group at this k: ring.scale 4, round.pos 4, ring.place 2, triangle.tang 1 |
| 0.01 | k 4.56 | 0.006 | 0.030 | 0.000 (29) | 0.0004 | 0.350 [0.322, 0.439] | 0.445 [0.407, 0.559] | 1.437 [1.234, 1.839] | 0.350 [0.289, 0.442] | 0.467 [0.393, 0.608] | 2.151 [1.813, 2.761] | 0.386 [0.344, 0.470] | 0.264 [0.233, 0.336] | 0.304 [0.271, 0.402] |
|  | alarms by group at this k: ring.place 1 |

### `shared/quantile`: common per-reading exceedance q; per-reading p99 limits alone give whole-watch false alarms 0.126

| Target whole-watch FA | Parameter | In-sample FA | 5-fold CV FA | Holdout FA (watches) | Per-reading FA | marker.pos | baton.tilt | triangle.tang | triangle.rad | triangle.tilt | ring.place | ring.rot | ring.scale |
|---|---|---:|---:|---:|---:|---|---|---|---|---|---|---|---|
| 0.05 | q 0.00595 | 0.048 | 0.116 | 0.103 (29) | 0.0031 | 0.278 [0.257, 0.313] | 0.784 [0.730, 0.810] | 0.350 [0.217, 0.350] | 0.418 [0.246, 0.418] | 1.167 [1.049, 1.167] | 0.445 [0.293, 0.445] | 0.178 [0.166, 0.178] | 0.298 [0.271, 0.298] |
| 0.01 | q 0.00132 | 0.009 | 0.068 | 0.103 (29) | 0.0005 | 0.322 [0.313, 0.342] | 0.810 [0.750, 0.810] | 0.350 [0.217, 0.350] | 0.418 [0.247, 0.418] | 1.167 [1.049, 1.167] | 0.445 [0.270, 0.445] | 0.178 [0.166, 0.178] | 0.298 [0.254, 0.298] |

### `shared/maxz`: one multiplier k on robust genuine scales (sigma: marker.pos 0.082, baton.tilt 0.315, triangle.tang 0.077, triangle.rad 0.102, triangle.tilt 0.471, ring.place 0.085, ring.rot 0.058, ring.scale 0.067)

| Target whole-watch FA | Parameter | In-sample FA | 5-fold CV FA | Holdout FA (watches) | Per-reading FA | marker.pos | baton.tilt | triangle.tang | triangle.rad | triangle.tilt | ring.place | ring.rot | ring.scale |
|---|---|---:|---:|---:|---:|---|---|---|---|---|---|---|---|
| 0.05 | k 3.83 | 0.048 | 0.048 | 0.069 (29) | 0.0032 | 0.314 [0.293, 0.364] | 1.208 [1.040, 1.440] | 0.294 [0.227, 0.352] | 0.392 [0.325, 0.471] | 1.807 [1.544, 2.158] | 0.324 [0.282, 0.378] | 0.222 [0.194, 0.265] | 0.256 [0.217, 0.298] |
|  | alarms by group at this k: ring.scale 5, marker.pos 3, ring.place 2, triangle.tang 1 |
| 0.01 | k 4.56 | 0.006 | 0.024 | 0.000 (29) | 0.0004 | 0.374 [0.334, 0.472] | 1.437 [1.198, 1.861] | 0.350 [0.267, 0.444] | 0.467 [0.373, 0.601] | 2.151 [1.760, 2.783] | 0.386 [0.340, 0.477] | 0.264 [0.226, 0.342] | 0.304 [0.271, 0.413] |
|  | alarms by group at this k: ring.place 1 |

Production on the same admitted genuine photos and eligible markers: strict genuine whole-watch FA 0.006 (per marker 0.0006); Clearly off whole-watch 0.006 (per marker 0.0006).

## Synthetic defects (image-level moves on admitted genuine photos; detected / assessable)

| Kind | Shape | Move | Amount | Not assessable | axis/maxz@0.05 | axis/maxz@0.01 | position/maxz@0.05 | position/maxz@0.01 | shared/maxz@0.05 | shared/maxz@0.01 | shared/quantile@0.05 | shared/quantile@0.01 | production strict | production Clearly off |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| multi | baton | r | 0.5 | 0 | 0.97 (96) | 0.88 (96) | 0.91 (96) | 0.76 (96) | 0.98 (96) | 0.94 (96) | 1.00 (96) | 0.98 (96) | 0.00 (96) | 0.00 (96) |
| multi | baton | t | 0.5 | 3 | 0.94 (81) | 0.72 (81) | 0.91 (81) | 0.78 (81) | 0.96 (81) | 0.94 (81) | 1.00 (81) | 0.96 (81) | 0.01 (81) | 0.01 (81) |
| multi | other markers: new false flag |  | 0 | 0 | 0.07 (306) | 0.05 (306) | 0.08 (306) | 0.07 (306) | 0.08 (306) | 0.07 (306) | 0.10 (306) | 0.10 (306) | — | — |
| multi | round | r | 0.5 | 2 | 1.00 (242) | 0.98 (242) | 0.99 (242) | 0.97 (242) | 0.99 (242) | 0.94 (242) | 1.00 (242) | 0.99 (242) | 0.02 (242) | 0.00 (242) |
| multi | round | t | 0.5 | 5 | 0.99 (213) | 0.95 (213) | 0.99 (213) | 0.97 (213) | 0.99 (213) | 0.94 (213) | 0.99 (213) | 0.99 (213) | 0.02 (213) | 0.00 (213) |
| multi | triangle | r | 0.5 | 0 | 0.84 (38) | 0.53 (38) | 0.82 (38) | 0.53 (38) | 0.84 (38) | 0.53 (38) | 0.82 (38) | 0.82 (38) | 0.39 (38) | 0.00 (38) |
| multi | triangle | t | 0.5 | 0 | 0.97 (34) | 0.91 (34) | 0.97 (34) | 0.91 (34) | 0.97 (34) | 0.91 (34) | 0.91 (34) | 0.91 (34) | 0.32 (34) | 0.00 (34) |
| ring | markers: new false flag | rot | 0.4 | 0 | 0.01 (102) | 0.00 (102) | 0.01 (102) | 0.00 (102) | 0.03 (102) | 0.00 (102) | 0.02 (102) | 0.03 (102) | — | — |
| ring | markers: new false flag | scale | 0.5 | 0 | 0.01 (102) | 0.01 (102) | 0.01 (102) | 0.02 (102) | 0.02 (102) | 0.01 (102) | 0.04 (102) | 0.03 (102) | — | — |
| ring | markers: new false flag | t | 0.3 | 0 | 0.00 (102) | 0.01 (102) | 0.00 (102) | 0.01 (102) | 0.01 (102) | 0.01 (102) | 0.04 (102) | 0.02 (102) | — | — |
| ring | markers: new false flag | t | 0.6 | 0 | 0.01 (101) | 0.01 (101) | 0.01 (101) | 0.01 (101) | 0.01 (101) | 0.01 (101) | 0.04 (101) | 0.03 (101) | — | — |
| ring | ring | rot | 0.4 | 0 | 1.00 (102) | 0.98 (102) | 0.99 (102) | 0.98 (102) | 1.00 (102) | 0.98 (102) | 1.00 (102) | 1.00 (102) | — | — |
| ring | ring | scale | 0.5 | 0 | 0.94 (102) | 0.92 (102) | 0.94 (102) | 0.92 (102) | 0.94 (102) | 0.92 (102) | 0.92 (102) | 0.92 (102) | — | — |
| ring | ring | t | 0.3 | 0 | 0.42 (102) | 0.20 (102) | 0.34 (102) | 0.20 (102) | 0.41 (102) | 0.20 (102) | 0.14 (102) | 0.14 (102) | — | — |
| ring | ring | t | 0.6 | 1 | 1.00 (101) | 1.00 (101) | 1.00 (101) | 1.00 (101) | 1.00 (101) | 1.00 (101) | 0.95 (101) | 0.95 (101) | — | — |
| single | baton | r | 0.25 | 0 | 0.19 (26) | 0.04 (26) | 0.04 (26) | 0.00 (26) | 0.27 (26) | 0.08 (26) | 0.50 (26) | 0.23 (26) | 0.00 (26) | 0.00 (26) |
| single | baton | r | 0.5 | 0 | 1.00 (31) | 0.87 (31) | 0.90 (31) | 0.74 (31) | 1.00 (31) | 0.97 (31) | 1.00 (31) | 1.00 (31) | 0.00 (31) | 0.00 (31) |
| single | baton | r | 0.75 | 0 | 1.00 (33) | 1.00 (33) | 1.00 (33) | 1.00 (33) | 1.00 (33) | 1.00 (33) | 1.00 (33) | 1.00 (33) | 0.39 (33) | 0.00 (33) |
| single | baton | r | 1.0 | 1 | 1.00 (24) | 1.00 (24) | 1.00 (24) | 1.00 (24) | 1.00 (24) | 1.00 (24) | 1.00 (24) | 1.00 (24) | 1.00 (24) | 0.29 (24) |
| single | baton | rot | 1.0 | 1 | 0.23 (81) | 0.07 (81) | 0.17 (81) | 0.07 (81) | 0.25 (81) | 0.07 (81) | 0.72 (81) | 0.68 (81) | 0.32 (81) | 0.02 (81) |
| single | baton | rot | 2.0 | 0 | 1.00 (80) | 0.99 (80) | 0.99 (80) | 0.99 (80) | 1.00 (80) | 0.99 (80) | 1.00 (80) | 1.00 (80) | 1.00 (80) | 0.85 (80) |
| single | baton | rot | 3.0 | 0 | 1.00 (72) | 1.00 (72) | 1.00 (72) | 1.00 (72) | 1.00 (72) | 1.00 (72) | 1.00 (72) | 1.00 (72) | 1.00 (72) | 1.00 (72) |
| single | baton | t | 0.25 | 0 | 0.06 (36) | 0.00 (36) | 0.03 (36) | 0.00 (36) | 0.42 (36) | 0.11 (36) | 0.47 (36) | 0.39 (36) | 0.03 (36) | 0.00 (36) |
| single | baton | t | 0.5 | 0 | 0.89 (27) | 0.70 (27) | 0.89 (27) | 0.78 (27) | 0.93 (27) | 0.89 (27) | 1.00 (27) | 0.93 (27) | 0.00 (27) | 0.00 (27) |
| single | baton | t | 0.75 | 2 | 1.00 (24) | 1.00 (24) | 1.00 (24) | 1.00 (24) | 1.00 (24) | 1.00 (24) | 1.00 (24) | 1.00 (24) | 0.38 (24) | 0.04 (24) |
| single | baton | t | 1.0 | 4 | 1.00 (26) | 1.00 (26) | 1.00 (26) | 1.00 (26) | 1.00 (26) | 1.00 (26) | 1.00 (26) | 1.00 (26) | 0.77 (26) | 0.23 (26) |
| single | other markers: new false flag |  | 0 | 0 | 0.01 (1341) | 0.00 (1341) | 0.00 (1341) | 0.01 (1341) | 0.01 (1341) | 0.00 (1341) | 0.02 (1341) | 0.02 (1341) | — | — |
| single | round | r | 0.25 | 0 | 0.29 (78) | 0.12 (78) | 0.23 (78) | 0.15 (78) | 0.23 (78) | 0.08 (78) | 0.36 (78) | 0.22 (78) | 0.00 (78) | 0.00 (78) |
| single | round | r | 0.5 | 2 | 1.00 (77) | 0.99 (77) | 1.00 (77) | 0.96 (77) | 1.00 (77) | 0.95 (77) | 1.00 (77) | 1.00 (77) | 0.01 (77) | 0.00 (77) |
| single | round | r | 0.75 | 4 | 1.00 (84) | 1.00 (84) | 1.00 (84) | 1.00 (84) | 1.00 (84) | 1.00 (84) | 1.00 (84) | 1.00 (84) | 0.51 (84) | 0.00 (84) |
| single | round | r | 1.0 | 6 | 1.00 (93) | 1.00 (93) | 1.00 (93) | 1.00 (93) | 1.00 (93) | 1.00 (93) | 1.00 (93) | 1.00 (93) | 1.00 (93) | 0.22 (93) |
| single | round | t | 0.25 | 0 | 0.26 (89) | 0.09 (89) | 0.20 (89) | 0.10 (89) | 0.20 (89) | 0.08 (89) | 0.46 (89) | 0.16 (89) | 0.00 (89) | 0.00 (89) |
| single | round | t | 0.5 | 3 | 1.00 (87) | 0.94 (87) | 1.00 (87) | 0.97 (87) | 1.00 (87) | 0.95 (87) | 1.00 (87) | 1.00 (87) | 0.06 (87) | 0.00 (87) |
| single | round | t | 0.75 | 11 | 1.00 (71) | 1.00 (71) | 1.00 (71) | 1.00 (71) | 1.00 (71) | 1.00 (71) | 1.00 (71) | 1.00 (71) | 0.30 (71) | 0.00 (71) |
| single | round | t | 1.0 | 27 | 1.00 (45) | 1.00 (45) | 1.00 (45) | 1.00 (45) | 1.00 (45) | 1.00 (45) | 1.00 (45) | 1.00 (45) | 0.76 (45) | 0.13 (45) |
| single | triangle | r | 0.25 | 0 | 0.00 (12) | 0.00 (12) | 0.00 (12) | 0.00 (12) | 0.00 (12) | 0.00 (12) | 0.00 (12) | 0.00 (12) | 0.00 (12) | 0.00 (12) |
| single | triangle | r | 0.5 | 0 | 1.00 (10) | 0.70 (10) | 0.90 (10) | 0.70 (10) | 1.00 (10) | 0.70 (10) | 0.90 (10) | 0.90 (10) | 0.50 (10) | 0.00 (10) |
| single | triangle | r | 0.75 | 0 | 1.00 (13) | 1.00 (13) | 1.00 (13) | 1.00 (13) | 1.00 (13) | 1.00 (13) | 1.00 (13) | 1.00 (13) | 1.00 (13) | 0.00 (13) |
| single | triangle | r | 1.0 | 0 | 1.00 (19) | 1.00 (19) | 1.00 (19) | 1.00 (19) | 1.00 (19) | 1.00 (19) | 1.00 (19) | 1.00 (19) | 1.00 (19) | 0.00 (19) |
| single | triangle | rot | 1.0 | 0 | 0.00 (25) | 0.00 (25) | 0.00 (25) | 0.00 (25) | 0.00 (25) | 0.00 (25) | 0.24 (25) | 0.24 (25) | 0.36 (25) | 0.28 (25) |
| single | triangle | rot | 2.0 | 0 | 0.62 (32) | 0.38 (32) | 0.56 (32) | 0.38 (32) | 0.62 (32) | 0.38 (32) | 0.91 (32) | 0.91 (32) | 0.94 (32) | 0.94 (32) |
| single | triangle | rot | 3.0 | 1 | 1.00 (40) | 0.97 (40) | 1.00 (40) | 0.97 (40) | 1.00 (40) | 0.97 (40) | 1.00 (40) | 1.00 (40) | 1.00 (40) | 1.00 (40) |
| single | triangle | t | 0.25 | 0 | 0.41 (17) | 0.12 (17) | 0.35 (17) | 0.12 (17) | 0.41 (17) | 0.12 (17) | 0.12 (17) | 0.12 (17) | 0.00 (17) | 0.00 (17) |
| single | triangle | t | 0.5 | 0 | 1.00 (10) | 1.00 (10) | 1.00 (10) | 1.00 (10) | 1.00 (10) | 1.00 (10) | 1.00 (10) | 1.00 (10) | 0.40 (10) | 0.00 (10) |
| single | triangle | t | 0.75 | 0 | 1.00 (11) | 1.00 (11) | 1.00 (11) | 1.00 (11) | 1.00 (11) | 1.00 (11) | 1.00 (11) | 1.00 (11) | 0.82 (11) | 0.00 (11) |
| single | triangle | t | 1.0 | 0 | 1.00 (6) | 1.00 (6) | 1.00 (6) | 1.00 (6) | 1.00 (6) | 1.00 (6) | 1.00 (6) | 1.00 (6) | 1.00 (6) | 0.17 (6) |

Measured response (reading change / applied move): triangle/t 1.014 (n 44, p10-p90 0.984-1.054); triangle/rot 1.006 (n 97, p10-p90 0.992-1.025); round/r 0.997 (n 332, p10-p90 0.985-1.008); round/t 0.996 (n 292, p10-p90 0.981-1.009); baton/t 1.010 (n 113, p10-p90 0.993-1.037); baton/rot 1.003 (n 233, p10-p90 0.981-1.019); baton/r 1.000 (n 114, p10-p90 0.983-1.023); triangle/r 1.008 (n 54, p10-p90 0.978-1.030)

Spill-over onto other markers (|change| / applied): triangle/t median 0.000, p95 0.000; triangle/rot median 0.000, p95 0.000; round/r median 0.013, p95 0.112; round/t median 0.015, p95 0.127; baton/t median 0.015, p95 0.114; baton/rot median 0.000, p95 0.002; baton/r median 0.014, p95 0.096; triangle/r median 0.000, p95 0.000

Every moved marker detected in multi-marker cases: axis/quantile/0.05/2 188/199; axis/quantile/0.01/2 187/199; axis/maxz/0.05/2 187/199; axis/maxz/0.01/2 154/199; position/quantile/0.05/2 188/199; position/quantile/0.01/2 185/199; position/maxz/0.05/2 179/199; position/maxz/0.01/2 155/199; shared/quantile/0.05/2 188/199; shared/quantile/0.01/2 185/199; shared/maxz/0.05/2 187/199; shared/maxz/0.01/2 164/199; axis/quantile/0.05/3 96/98; axis/quantile/0.01/3 96/98; axis/maxz/0.05/3 92/98; axis/maxz/0.01/3 73/98; position/quantile/0.05/3 97/98; position/quantile/0.01/3 93/98; position/maxz/0.05/3 90/98; position/maxz/0.01/3 71/98; shared/quantile/0.05/3 97/98; shared/quantile/0.01/3 92/98; shared/maxz/0.05/3 94/98; shared/maxz/0.01/3 77/98

Ring translation recovery error: median 0.006, p95 0.019 % R (n 203)

## Replica photographs (markers flagged; eligible markers only)

| Layout | Watch | Photo | rpx | Eligible | axis/maxz@0.05 | axis/maxz@0.01 | position/maxz@0.05 | position/maxz@0.01 | shared/maxz@0.05 | shared/maxz@0.01 | shared/quantile@0.05 | shared/quantile@0.01 | Production strict | Production Clearly off |
|---|---|---|---:|---:|---|---|---|---|---|---|---|---|---|---|
| submariner_124060 | rep_124060_clean_15spe47 | 0bc6f00d7ce8a109 | 233 | 9 | 1 6 7 8 9 ring | 1 6 8 9 ring | 1 6 7 8 9 ring | 1 6 7 8 9 ring | 1 6 7 8 9 ring | 1 6 8 9 ring | 1 6 7 8 9 ring | 1 6 7 8 9 ring | — | — |
| submariner_124060 | rep_124060_vsf_1gwpcm4 | 1e090129a34bfbf1 | 312 | 8 | 3 4 10 | 4 | 3 4 10 | 3 4 10 | 3 4 10 | 3 4 | 3 4 6 10 | 3 4 10 | — | — |
| submariner_124060 | rep_124060_vsf_1gwpcm4 | 2cdac89f6ac7c40d | 270 | 0 | — | — | — | — | — | — | — | — | — | — |
| submariner_124060 | rep_124060_clean_1lnn2kz | 531bf26df63f8e28 | 252 | 8 | 6 9 10 | 6 | 6 | — | 6 9 | 6 | 6 9 10 | 6 9 | — | — |
| submariner_124060 | rep_124060_vsf_1u8hbiz | 72d1133a1f25445f | 519 | 2 | — | — | — | — | — | — | — | — | — | 12 |
| submariner_124060 | rep_124060_vsf_1gwpcm4 | 72d75735b3df22da | 269 | 0 | — | — | — | — | — | — | — | — | — | — |
| submariner_124060 | rep_124060_vsf_1gwpcm4 | 7748e630c6703233 | 315 | 6 | — | — | — | — | — | — | — | — | — | — |
| submariner_124060 | rep_124060_clean_1lnn2kz | 7924b3438d027820 | 252 | 8 | 6 9 11 12 | 6 9 11 12 | 6 9 11 12 | 6 9 11 12 | 6 9 11 12 | 6 9 11 12 | 6 9 11 12 | 6 9 11 12 | — | — |
| submariner_124060 | rep_124060_vsf_1gwpcm4 | 7c6665e9293bba99 | 313 | 6 | 12 | 12 | 12 | 12 | 12 | 12 | 12 | 12 | — | — |
| submariner_124060 | rep_124060_vsf_1rslupa | 86c421e3fc503832 | 303 | 2 | — | — | — | — | — | — | — | — | — | 12 |
| submariner_124060 | rep_124060_vsf_1gwpcm4 | 881f5b3aa1abb05e | 270 | 2 | — | — | — | — | — | — | — | — | — | — |
| submariner_124060 | rep_124060_vsf_1rslupa | 9c4d6cd0bf6c77d4 | 299 | 5 | — | — | — | — | — | — | — | — | — | — |
| submariner_124060 | rep_124060_clean_1lnn2kz | a699939a5839d8b0 | 252 | 7 | 4 5 6 7 9 12 | 5 6 7 9 12 | 5 6 7 9 12 | 5 6 7 9 12 | 3 5 6 7 9 12 | 6 7 9 12 | 3 4 5 6 7 9 12 | 3 5 6 7 9 12 | — | — |
| submariner_124060 | rep_124060_clean_1d06ekm | c46f94befa166a95 | 204 | 4 | — | — | — | — | — | — | — | — | — | — |
| submariner_124060 | rep_124060_vsf_1rslupa | c63e219ccd3d8b16 | 301 | 2 | — | — | — | — | — | — | — | — | — | — |
| submariner_124060 | rep_124060_clean_1d06ekm | e42f2509dbe91be7 | 357 | 2 | — | — | — | — | — | — | — | — | 12 | 12 |
| submariner_124060 | rep_124060_clean_15spe47 | ecdf7ca4f611b956 | 414 | 5 | — | — | — | — | — | — | — | — | — | — |
| submariner_124060 | rep_124060_vsf_1u8hbiz | f15decbaae76e4c0 | 384 | 2 | — | — | — | — | — | — | — | — | — | 12 |

## Triangle: approved top edge against MarkerGeometry radial

```
{
 "pairs": 167,
 "watches": 157,
 "spearman": 0.82058602244736,
 "median_diff_mg_minus_top": 0.016190916169999986,
 "robust_sd_diff": 0.05450095834407883,
 "top_edge_p99_abs": 0.342773633529,
 "mg_p99_abs_centred": 0.257039198839,
 "top_edge_over_its_limit": 0.006369426751592357,
 "synthetic_gain": {
  "mg": {
   "n": 54,
   "median": 1.0081022676529998
  },
  "top_edge": {
   "n": 54,
   "median": 0.9971958533297499
  }
 }
}
```

## Ring geometry quality

```
{
 "bins": {
  "max_gap_90": {
   "photos": 164,
   "watches": 154,
   "p95_place": 0.24085499913640984,
   "median_dop": 0.489025168071954
  },
  "max_gap>=120": {
   "photos": 14,
   "watches": 14,
   "p95_place": 0.29347647152369816,
   "median_dop": 0.5367512821054087
  }
 },
 "markers_used": {
  "8": 56,
  "10": 37,
  "9": 79,
  "11": 6
 }
}
```

## Cross-checks

```
{
 "crosscheck": {
  "values_compared": 4093,
  "max_abs_diff": 4.999899999857949e-07,
  "p99_abs_diff": 4.963500839905155e-07,
  "eligibility_mg_vs_mask": {
   "False/False": 260,
   "True/True": 1762
  }
 },
 "parity": {
  "records": 2335,
  "values": 75217,
  "max_abs_diff": 1.0474248354069848e-08,
  "eligibility_mismatches": 0,
  "one_side_only": 0,
  "pass": true
 }
}
```

## Withheld

```
{
 "gmt_126710": {
  "admission": {
   "items": 66,
   "outcomes": {
    "admitted": 24,
    "not_admitted": 40,
    "identity_conflict_or_unknown_watch": 2
   },
   "admitted_photos": 24,
   "admitted_watches": 22,
   "replica_photos": 0,
   "replica_watches": 0,
   "not_admitted_reasons": {
    "fail_face_on_ellipse": 14,
    "fail_original_resolution": 28,
    "inconclusive_face_on_rehaut_horizontal": 17,
    "fail_too_many_excluded_markers": 24,
    "inconclusive_face_on_ellipse": 4,
    "inconclusive_sharp_boundaries": 17,
    "inconclusive_too_many_excluded_markers": 5,
    "fail_complete_minute_track": 1,
    "fail_landmarks": 1
   }
  },
  "marker_status_in_admitted_photos": {
   "round": {
    "ELIGIBLE": 154,
    "OBSTRUCTED": 37,
    "INCONCLUSIVE": 1
   },
   "triangle": {
    "ELIGIBLE": 23,
    "OBSTRUCTED": 1
   },
   "baton": {
    "ELIGIBLE": 46,
    "OBSTRUCTED": 2
   }
  },
  "mask_exclusions_after_measurement": {}
 },
 "submariner_124060": {
  "admission": {
   "items": 439,
   "outcomes": {
    "admitted": 64,
    "not_admitted": 375
   },
   "admitted_photos": 64,
   "admitted_watches": 56,
   "replica_photos": 0,
   "replica_watches": 0,
   "not_admitted_reasons": {
    "fail_original_resolution": 130,
    "fail_too_many_excluded_markers": 65,
    "inconclusive_face_on_rehaut_vertical": 285,
    "inconclusive_sharp_boundaries": 299,
    "inconclusive_too_many_excluded_markers": 280,
    "fail_complete_minute_track": 1,
    "fail_landmarks": 3,
    "inconclusive_face_on_rehaut_horizontal": 287,
    "inconclusive_glare": 275,
    "inconclusive_exposure": 272,
    "inconclusive_face_on_ellipse": 272,
    "fail_face_on_ellipse": 7,
    "inconclusive_complete_dial": 271,
    "inconclusive_complete_minute_track": 271,
    "inconclusive_face_on_marker_lattice": 271,
    "inconclusive_landmarks": 271,
    "inconclusive_original_resolution": 271,
    "inconclusive_factory_identity": 55,
    "inconclusive_unverified_provenance": 55,
    "fail_face_on_marker_lattice": 2
   }
  },
  "marker_status_in_admitted_photos": {
   "round": {
    "ELIGIBLE": 431,
    "OBSTRUCTED": 73,
    "UNRELIABLE": 4,
    "INCONCLUSIVE": 4
   },
   "triangle": {
    "ELIGIBLE": 63,
    "OBSTRUCTED": 1
   },
   "baton": {
    "ELIGIBLE": 185,
    "OBSTRUCTED": 7
   }
  },
  "mask_exclusions_after_measurement": {}
 },
 "submariner_126610": {
  "admission": {
   "items": 355,
   "outcomes": {
    "admitted": 90,
    "no_original_bytes": 245,
    "not_admitted": 20
   },
   "admitted_photos": 90,
   "admitted_watches": 90,
   "replica_photos": 0,
   "replica_watches": 0,
   "not_admitted_reasons": {
    "fail_too_many_excluded_markers": 15,
    "inconclusive_too_many_excluded_markers": 4,
    "inconclusive_sharp_boundaries": 2,
    "fail_face_on_ellipse": 1,
    "fail_original_resolution": 1
   }
  },
  "marker_status_in_admitted_photos": {
   "round": {
    "ELIGIBLE": 608,
    "OBSTRUCTED": 109,
    "INCONCLUSIVE": 3
   },
   "triangle": {
    "OBSTRUCTED": 9,
    "ELIGIBLE": 81
   },
   "baton": {
    "ELIGIBLE": 171,
    "OBSTRUCTED": 5,
    "UNRELIABLE": 4
   }
  },
  "mask_exclusions_after_measurement": {}
 }
}
```
