# Alpha96: pixel and dial-radius comparison (research only, offline)

Genuine: one primary photo per provenance-strong watch (CI run 37500197377), with the exact dial radius R of that photo.
Scaled experiment: the 8 local photos re-measured at several scales, with resampling variants at each scale.
No thresholds are derived; replica rows never enter a genuine statistic.

De-duplicated at level **possible**: 83 shared / stock photos excluded (`photos.csv`).

Genuine watches: 31; dial radius R px: min 129, median 435, max 835; 4 watches with R <= 240 px.

## A. Genuine: does the value depend on dial radius?

Spearman rho of |value| against R (one photo per watch). Negative rho in px would mean more detector noise on smaller dials; rho near 0 in px means the px value is resolution-independent, so R units penalise small dials.

| Feature | unit | n | rho(|value|, R) | median |v| R<=240 | median |v| R 240-360 | median |v| R>360 |
|---|---|---:|---:|---:|---:|---:|
| ring shift | px | 28 | 0.47 | 0.797 (n=4) | 0.868 (n=3) | 1.570 (n=21) |
| ring shift | R | 28 | -0.50 | 0.0046 (n=4) | 0.0032 (n=3) | 0.0029 (n=21) |
| ring rotation | deg | 28 | -0.02 | 0.052 (n=4) | 0.044 (n=3) | 0.051 (n=21) |
| ring scale % | % | 28 | -0.00 | 0.147 (n=4) | 0.104 (n=3) | 0.084 (n=21) |
| 6 rotation | deg | 28 | -0.12 | 0.374 (n=3) | 0.402 (n=3) | 0.176 (n=22) |
| 6 local offset | px | 26 | 0.64 | 0.345 (n=3) | 0.369 (n=3) | 0.922 (n=20) |
| 6 local offset | R | 26 | 0.08 | 0.0018 (n=3) | 0.0011 (n=3) | 0.0016 (n=20) |
| 9 rotation | deg | 30 | -0.03 | 0.128 (n=3) | 0.473 (n=3) | 0.177 (n=24) |
| 9 local offset | px | 27 | 0.75 | 0.466 (n=3) | 0.467 (n=3) | 1.075 (n=21) |
| 9 local offset | R | 27 | 0.04 | 0.0023 (n=3) | 0.0019 (n=3) | 0.0017 (n=21) |
| 12 centreline rotation | deg | 31 | -0.38 | 0.778 (n=4) | 0.228 (n=3) | 0.297 (n=24) |
| 12 local offset | px | 28 | 0.85 | 0.273 (n=4) | 1.242 (n=3) | 1.703 (n=21) |
| 12 local offset | R | 28 | 0.58 | 0.0016 (n=4) | 0.0046 (n=3) | 0.0043 (n=21) |
| rounds max local | px | 28 | 0.66 | 0.530 (n=4) | 0.569 (n=3) | 0.630 (n=21) |
| rounds max local | R | 28 | -0.24 | 0.0027 (n=4) | 0.0023 (n=3) | 0.0014 (n=21) |

## B. Scaled experiment: detector noise and drift with dial radius

Pose acceptance by scale (all 8 photos x variants): 1.00: 39/40, 0.85: 39/40, 0.70: 38/40, 0.55: 30/40, 0.45: 20/40

Noise = SD of the value across resampling variants of the same photo at the same scale (same physical geometry). Median over photos. Drift = median over photos of |value(smallest accepted scale) - value(native)| in R units for offsets, degrees for rotations, against the native noise.

| Feature | unit | noise at native (median R 203 px) | noise at 0.7 | noise at 0.45 | noise in R units native | noise in R units 0.45 | drift (R units or deg) |
|---|---|---:|---:|---:|---:|---:|---:|
| ring shift | px | 0.019 | 0.015 | 0.043 | 0.0001 | 0.0005 | 0.0016 |
| ring rotation | deg | 0.018 | 0.012 | 0.049 | – | – | 0.025 |
| ring scale % | % | 0.010 | 0.015 | 0.101 | – | – | 0.095 |
| 6 rotation | deg | 0.111 | 0.115 | 1.222 | – | – | 0.050 |
| 6 local offset | px | 0.042 | 0.067 | 0.193 | 0.0002 | 0.0020 | 0.0007 |
| 9 rotation | deg | 0.095 | 0.135 | 0.433 | – | – | 0.150 |
| 9 local offset | px | 0.031 | 0.050 | 0.317 | 0.0002 | 0.0030 | 0.0008 |
| 12 centreline rotation | deg | 0.053 | 0.085 | 0.155 | – | – | 0.257 |
| 12 local offset | px | 0.047 | 0.052 | 0.076 | 0.0002 | 0.0007 | 0.0033 |
| rounds max local | px | 0.017 | 0.015 | 0.061 | 0.0001 | 0.0007 | 0.0009 |

Per-photo median value at each scale (R units for offsets, degrees or % otherwise; – = fewer than 3 variants usable). Geometry stays constant down the row; resolution artefacts drift.

**ring shift** (R)

| Photo | 1.00 | 0.85 | 0.70 | 0.55 | 0.45 |
|---|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | +0.0035 | +0.0036 | +0.0037 | +0.0039 | +0.0079 |
| GEN_CAND_HO_02 | +0.0029 | – | +0.0041 | – | – |
| GEN_CAND_WEX_BLRO_01 | +0.0027 | +0.0025 | +0.0030 | +0.0021 | – |
| GEN_CAND_WEX_BLRO_02 | +0.0018 | +0.0018 | +0.0021 | – | – |
| RL_ARF_BLRO_CROOKED6 | +0.0049 | +0.0050 | +0.0051 | +0.0051 | +0.0047 |
| RL_LOCAL_BLNR | +0.0023 | +0.0040 | +0.0033 | +0.0037 | +0.0063 |
| RL_THEONE_BLNR | +0.0035 | +0.0037 | +0.0040 | +0.0058 | – |
| RL_USER_BATGIRL | +0.0034 | +0.0033 | +0.0034 | +0.0028 | +0.0054 |

**ring rotation** (deg)

| Photo | 1.00 | 0.85 | 0.70 | 0.55 | 0.45 |
|---|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | -0.0260 | -0.0245 | -0.0199 | -0.0175 | -0.1128 |
| GEN_CAND_HO_02 | +0.0305 | – | +0.0609 | – | – |
| GEN_CAND_WEX_BLRO_01 | +0.0026 | +0.0180 | +0.0144 | +0.0219 | – |
| GEN_CAND_WEX_BLRO_02 | +0.0240 | +0.0061 | +0.0115 | – | – |
| RL_ARF_BLRO_CROOKED6 | -0.0474 | -0.0443 | -0.0459 | -0.0487 | -0.0270 |
| RL_LOCAL_BLNR | +0.5526 | +0.5960 | +0.6279 | +0.6259 | +0.5047 |
| RL_THEONE_BLNR | +0.0559 | +0.0771 | +0.0191 | +0.0453 | – |
| RL_USER_BATGIRL | +0.1136 | +0.1167 | +0.1139 | +0.0754 | +0.0559 |

**ring scale %** (%)

| Photo | 1.00 | 0.85 | 0.70 | 0.55 | 0.45 |
|---|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | +0.1761 | +0.1664 | +0.1615 | +0.1799 | +0.0828 |
| GEN_CAND_HO_02 | -0.0419 | – | -0.0658 | – | – |
| GEN_CAND_WEX_BLRO_01 | +0.2535 | +0.2229 | +0.2487 | +0.2174 | – |
| GEN_CAND_WEX_BLRO_02 | +0.1952 | +0.2271 | +0.2124 | – | – |
| RL_ARF_BLRO_CROOKED6 | +0.9530 | +0.9558 | +0.9072 | +0.9036 | +0.8162 |
| RL_LOCAL_BLNR | +0.7105 | +0.8633 | +0.8697 | +0.9654 | +1.0298 |
| RL_THEONE_BLNR | +0.1756 | +0.1699 | +0.2103 | -0.0272 | – |
| RL_USER_BATGIRL | +0.0583 | +0.0310 | +0.0218 | +0.0601 | +0.1548 |

**6 rotation** (deg)

| Photo | 1.00 | 0.85 | 0.70 | 0.55 | 0.45 |
|---|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | +0.5725 | +0.5626 | +0.5495 | +0.5065 | +0.5133 |
| GEN_CAND_HO_02 | -0.4486 | – | – | – | – |
| GEN_CAND_WEX_BLRO_01 | -0.4046 | – | – | – | – |
| GEN_CAND_WEX_BLRO_02 | -0.1099 | -0.0694 | – | – | – |
| RL_ARF_BLRO_CROOKED6 | +0.0813 | +0.0352 | +0.1190 | +0.2308 | +0.1633 |
| RL_LOCAL_BLNR | -0.1175 | -0.1533 | -0.1680 | +0.1218 | +0.1074 |
| RL_THEONE_BLNR | +0.3313 | +0.3568 | – | – | – |
| RL_USER_BATGIRL | +0.7761 | +0.8844 | +0.8060 | +0.8684 | +0.8058 |

**6 local offset** (R)

| Photo | 1.00 | 0.85 | 0.70 | 0.55 | 0.45 |
|---|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | +0.0018 | +0.0012 | +0.0016 | +0.0034 | +0.0016 |
| GEN_CAND_HO_02 | +0.0024 | – | – | – | – |
| GEN_CAND_WEX_BLRO_01 | +0.0024 | – | – | – | – |
| GEN_CAND_WEX_BLRO_02 | +0.0026 | +0.0020 | – | – | – |
| RL_ARF_BLRO_CROOKED6 | +0.0035 | +0.0029 | +0.0026 | +0.0033 | +0.0043 |
| RL_LOCAL_BLNR | +0.0037 | +0.0050 | +0.0051 | +0.0049 | +0.0047 |
| RL_THEONE_BLNR | +0.0046 | +0.0050 | – | – | – |
| RL_USER_BATGIRL | +0.0027 | +0.0025 | +0.0026 | +0.0032 | +0.0067 |

**9 rotation** (deg)

| Photo | 1.00 | 0.85 | 0.70 | 0.55 | 0.45 |
|---|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | +0.2231 | +0.1739 | +0.3061 | +0.0225 | – |
| GEN_CAND_HO_02 | +0.1537 | – | – | – | – |
| GEN_CAND_WEX_BLRO_01 | -0.3699 | -0.3115 | – | – | – |
| GEN_CAND_WEX_BLRO_02 | -0.0405 | -0.0923 | -0.1400 | – | – |
| RL_ARF_BLRO_CROOKED6 | +0.1869 | +0.1903 | +0.3085 | +0.2104 | +0.4536 |
| RL_LOCAL_BLNR | – | -0.8033 | – | – | – |
| RL_THEONE_BLNR | -0.8491 | -0.8937 | -0.5723 | – | – |
| RL_USER_BATGIRL | -1.5198 | -1.4354 | -1.4823 | – | – |

**9 local offset** (R)

| Photo | 1.00 | 0.85 | 0.70 | 0.55 | 0.45 |
|---|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | +0.0030 | +0.0030 | +0.0036 | +0.0034 | – |
| GEN_CAND_HO_02 | +0.0020 | – | – | – | – |
| GEN_CAND_WEX_BLRO_01 | +0.0024 | +0.0040 | – | – | – |
| GEN_CAND_WEX_BLRO_02 | +0.0028 | +0.0040 | +0.0037 | – | – |
| RL_ARF_BLRO_CROOKED6 | +0.0019 | +0.0019 | +0.0092 | +0.0017 | +0.0114 |
| RL_LOCAL_BLNR | – | +0.0020 | – | – | – |
| RL_THEONE_BLNR | +0.0014 | +0.0013 | +0.0007 | – | – |
| RL_USER_BATGIRL | +0.0048 | +0.0050 | +0.0051 | – | – |

**12 centreline rotation** (deg)

| Photo | 1.00 | 0.85 | 0.70 | 0.55 | 0.45 |
|---|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | -0.3848 | -0.2117 | -0.4817 | -0.2079 | +0.4470 |
| GEN_CAND_HO_02 | +0.5912 | – | – | – | – |
| GEN_CAND_WEX_BLRO_01 | – | – | – | – | – |
| GEN_CAND_WEX_BLRO_02 | – | – | – | – | – |
| RL_ARF_BLRO_CROOKED6 | +0.2782 | +0.2584 | +0.2619 | +0.2790 | +0.2327 |
| RL_LOCAL_BLNR | +0.7837 | +0.8207 | +0.8723 | +0.7978 | +1.0156 |
| RL_THEONE_BLNR | +0.8003 | +0.7415 | +1.0833 | – | – |
| RL_USER_BATGIRL | – | – | – | – | – |

**12 local offset** (R)

| Photo | 1.00 | 0.85 | 0.70 | 0.55 | 0.45 |
|---|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | +0.0044 | +0.0047 | +0.0048 | +0.0046 | +0.0071 |
| GEN_CAND_HO_02 | +0.0052 | – | – | – | – |
| GEN_CAND_WEX_BLRO_01 | – | – | – | – | – |
| GEN_CAND_WEX_BLRO_02 | – | – | – | – | – |
| RL_ARF_BLRO_CROOKED6 | +0.0103 | +0.0107 | +0.0115 | +0.0119 | +0.0142 |
| RL_LOCAL_BLNR | +0.0139 | +0.0156 | +0.0165 | +0.0168 | +0.0035 |
| RL_THEONE_BLNR | +0.0090 | +0.0095 | +0.0083 | – | – |
| RL_USER_BATGIRL | – | – | – | – | – |

**rounds max local** (R)

| Photo | 1.00 | 0.85 | 0.70 | 0.55 | 0.45 |
|---|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | +0.0024 | +0.0025 | +0.0027 | +0.0032 | +0.0067 |
| GEN_CAND_HO_02 | +0.0019 | – | +0.0028 | – | – |
| GEN_CAND_WEX_BLRO_01 | +0.0019 | +0.0014 | +0.0015 | +0.0017 | – |
| GEN_CAND_WEX_BLRO_02 | +0.0020 | +0.0022 | +0.0018 | – | – |
| RL_ARF_BLRO_CROOKED6 | +0.0046 | +0.0048 | +0.0052 | +0.0049 | +0.0055 |
| RL_LOCAL_BLNR | +0.0018 | +0.0039 | +0.0045 | +0.0050 | +0.0078 |
| RL_THEONE_BLNR | +0.0039 | +0.0042 | +0.0035 | +0.0038 | – |
| RL_USER_BATGIRL | +0.0045 | +0.0042 | +0.0042 | +0.0038 | +0.0061 |


## C. Each local photo against genuine

Exceedance = share of genuine watches whose |value| is at least the photo's |value| (0% = beyond every genuine watch). Matched = genuine watches with dial radius within ±30% of the photo's. SNR = |value| / resampling noise of this photo at native scale.

### Replica regression controls (validation only)

| Photo | R px | Feature | value | unit | all genuine: exceed (n) | R-matched: exceed (n) | in R units: all / matched | SNR |
|---|---:|---|---:|---|---:|---:|---:|---:|
| RL_THEONE_BLNR | 165 | ring shift | +0.527 | px | 93% (28) | 100% (3) | 46% / 100% | 18.5 |
| RL_THEONE_BLNR | 165 | ring rotation | +0.056 | deg | 43% (28) | 33% (3) | n/a | 3.2 |
| RL_THEONE_BLNR | 165 | ring scale % | +0.189 | % | 7% (28) | 33% (3) | n/a | 11.4 |
| RL_THEONE_BLNR | 165 | 6 rotation | +0.331 | deg | 39% (28) | 50% (2) | n/a | 3.6 |
| RL_THEONE_BLNR | 165 | 6 local offset | +0.777 | px | 42% (26) | 0% (2) | 0% / 0% | 18.4 |
| RL_THEONE_BLNR | 165 | 9 rotation | -0.849 | deg | 0% (30) | 0% (2) | n/a | 9.0 |
| RL_THEONE_BLNR | 165 | 9 local offset | +0.279 | px | 93% (27) | 100% (2) | 63% / 100% | 4.0 |
| RL_THEONE_BLNR | 165 | 12 centreline rotation | +0.965 | deg | 10% (31) | 33% (3) | n/a | 4.0 |
| RL_THEONE_BLNR | 165 | 12 local offset | +1.454 | px | 61% (28) | 0% (3) | 0% / 0% | 15.6 |
| RL_THEONE_BLNR | 165 | rounds max local | +0.657 | px | 36% (28) | 0% (3) | 0% / 0% | 38.0 |
| RL_USER_BATGIRL | 205 | ring shift | +0.705 | px | 79% (28) | 75% (4) | 39% / 75% | 29.4 |
| RL_USER_BATGIRL | 205 | ring rotation | +0.114 | deg | 0% (28) | 0% (4) | n/a | 7.5 |
| RL_USER_BATGIRL | 205 | ring scale % | +0.064 | % | 64% (28) | 75% (4) | n/a | 8.5 |
| RL_USER_BATGIRL | 205 | 6 rotation | +0.776 | deg | 4% (28) | 25% (4) | n/a | 5.5 |
| RL_USER_BATGIRL | 205 | 6 local offset | +0.544 | px | 62% (26) | 25% (4) | 15% / 25% | 5.3 |
| RL_USER_BATGIRL | 205 | 9 rotation | -1.520 | deg | 0% (30) | 0% (4) | n/a | 16.5 |
| RL_USER_BATGIRL | 205 | 9 local offset | +0.931 | px | 44% (27) | 0% (4) | 0% / 0% | 34.5 |
| RL_USER_BATGIRL | 205 | rounds max local | +0.902 | px | 21% (28) | 0% (4) | 0% / 0% | 56.8 |
| RL_LOCAL_BLNR | 220 | ring shift | +0.488 | px | 96% (28) | 100% (5) | 75% / 100% | 10.6 |
| RL_LOCAL_BLNR | 220 | ring rotation ** | +0.550 | deg | 0% (28) | 0% (5) | n/a | 15.0 |
| RL_LOCAL_BLNR | 220 | ring scale % ** | +0.697 | % | 0% (28) | 0% (5) | n/a | 11.6 |
| RL_LOCAL_BLNR | 220 | 6 rotation | -0.013 | deg | 100% (28) | 100% (5) | n/a | 0.1 |
| RL_LOCAL_BLNR | 220 | 6 local offset ** | +0.863 | px | 38% (26) | 0% (5) | 0% / 0% | 8.8 |
| RL_LOCAL_BLNR | 220 | 12 centreline rotation | +0.787 | deg | 16% (31) | 60% (5) | n/a | 10.4 |
| RL_LOCAL_BLNR | 220 | 12 local offset ** | +3.043 | px | 25% (28) | 0% (5) | 0% / 0% | 32.3 |
| RL_LOCAL_BLNR | 220 | rounds max local | +0.368 | px | 93% (28) | 100% (5) | 36% / 100% | 1.6 |
| RL_ARF_BLRO_CROOKED6 | 234 | ring shift | +1.129 | px | 61% (28) | 25% (4) | 14% / 50% | 64.0 |
| RL_ARF_BLRO_CROOKED6 | 234 | ring rotation | -0.048 | deg | 54% (28) | 50% (4) | n/a | 5.5 |
| RL_ARF_BLRO_CROOKED6 | 234 | ring scale % | +0.953 | % | 0% (28) | 0% (4) | n/a | 218.8 |
| RL_ARF_BLRO_CROOKED6 | 234 | 6 rotation | +0.008 | deg | 100% (28) | 100% (4) | n/a | 0.1 |
| RL_ARF_BLRO_CROOKED6 | 234 | 6 local offset | +0.862 | px | 38% (26) | 0% (4) | 0% / 0% | 16.0 |
| RL_ARF_BLRO_CROOKED6 | 234 | 9 rotation | -0.037 | deg | 97% (30) | 100% (4) | n/a | 0.4 |
| RL_ARF_BLRO_CROOKED6 | 234 | 9 local offset | +0.380 | px | 85% (27) | 100% (4) | 63% / 100% | 8.9 |
| RL_ARF_BLRO_CROOKED6 | 234 | 12 centreline rotation | +0.277 | deg | 55% (31) | 75% (4) | n/a | 5.5 |
| RL_ARF_BLRO_CROOKED6 | 234 | 12 local offset | +2.380 | px | 32% (28) | 0% (4) | 0% / 0% | 72.6 |
| RL_ARF_BLRO_CROOKED6 | 234 | rounds max local | +1.080 | px | 14% (28) | 0% (4) | 0% / 0% | 47.1 |

### Marketplace genuine candidates (descriptive only)

| Photo | R px | Feature | value | unit | all genuine: exceed (n) | R-matched: exceed (n) | in R units: all / matched | SNR |
|---|---:|---|---:|---|---:|---:|---:|---:|
| GEN_CAND_WEX_BLRO_01 | 140 | ring shift | +0.397 | px | 96% (28) | 100% (3) | 61% / 100% | 26.7 |
| GEN_CAND_WEX_BLRO_01 | 140 | ring rotation | +0.003 | deg | 100% (28) | 100% (3) | n/a | 0.1 |
| GEN_CAND_WEX_BLRO_01 | 140 | ring scale % | +0.254 | % | 0% (28) | 0% (3) | n/a | 27.7 |
| GEN_CAND_WEX_BLRO_01 | 140 | 6 rotation | -0.443 | deg | 18% (28) | 0% (2) | n/a | 2.6 |
| GEN_CAND_WEX_BLRO_01 | 140 | 6 local offset | +0.305 | px | 85% (26) | 100% (2) | 27% / 50% | 11.3 |
| GEN_CAND_WEX_BLRO_01 | 140 | 9 rotation | -0.406 | deg | 20% (30) | 0% (2) | n/a | 3.1 |
| GEN_CAND_WEX_BLRO_01 | 140 | 9 local offset | +0.335 | px | 89% (27) | 100% (2) | 33% / 50% | 5.8 |
| GEN_CAND_WEX_BLRO_01 | 140 | rounds max local | +0.266 | px | 96% (28) | 67% (3) | 36% / 67% | 11.8 |
| GEN_CAND_WEX_BLRO_02 | 155 | ring shift | +0.280 | px | 96% (28) | 100% (3) | 82% / 100% | 40.0 |
| GEN_CAND_WEX_BLRO_02 | 155 | ring rotation | +0.024 | deg | 89% (28) | 100% (3) | n/a | 0.9 |
| GEN_CAND_WEX_BLRO_02 | 155 | ring scale % | +0.176 | % | 11% (28) | 33% (3) | n/a | 17.6 |
| GEN_CAND_WEX_BLRO_02 | 155 | 6 rotation | -0.106 | deg | 68% (28) | 100% (2) | n/a | 0.9 |
| GEN_CAND_WEX_BLRO_02 | 155 | 6 local offset | +0.438 | px | 73% (26) | 50% (2) | 12% / 50% | 10.3 |
| GEN_CAND_WEX_BLRO_02 | 155 | 9 rotation | +0.032 | deg | 97% (30) | 100% (2) | n/a | 0.6 |
| GEN_CAND_WEX_BLRO_02 | 155 | 9 local offset | +0.392 | px | 85% (27) | 50% (2) | 33% / 50% | 14.0 |
| GEN_CAND_WEX_BLRO_02 | 155 | rounds max local | +0.295 | px | 96% (28) | 67% (3) | 36% / 67% | 17.6 |
| GEN_CAND_HO_01 | 203 | ring shift | +0.708 | px | 79% (28) | 75% (4) | 39% / 75% | 53.1 |
| GEN_CAND_HO_01 | 203 | ring rotation | -0.026 | deg | 79% (28) | 75% (4) | n/a | 1.4 |
| GEN_CAND_HO_01 | 203 | ring scale % | +0.173 | % | 11% (28) | 0% (4) | n/a | 23.4 |
| GEN_CAND_HO_01 | 203 | 6 rotation | +0.475 | deg | 18% (28) | 50% (4) | n/a | 10.5 |
| GEN_CAND_HO_01 | 203 | 6 local offset | +0.351 | px | 77% (26) | 25% (4) | 46% / 50% | 19.7 |
| GEN_CAND_HO_01 | 203 | 9 rotation | +0.197 | deg | 43% (30) | 50% (4) | n/a | 1.7 |
| GEN_CAND_HO_01 | 203 | 9 local offset | +0.589 | px | 67% (27) | 0% (4) | 11% / 0% | 19.2 |
| GEN_CAND_HO_01 | 203 | 12 centreline rotation | -0.432 | deg | 35% (31) | 75% (4) | n/a | 8.2 |
| GEN_CAND_HO_01 | 203 | 12 local offset | +0.876 | px | 89% (28) | 50% (4) | 46% / 25% | 18.8 |
| GEN_CAND_HO_01 | 203 | rounds max local | +0.498 | px | 79% (28) | 75% (4) | 14% / 75% | 35.4 |
| GEN_CAND_HO_02 | 143 | ring shift | +0.410 | px | 96% (28) | 100% (3) | 61% / 100% | 20.7 |
| GEN_CAND_HO_02 | 143 | ring rotation | +0.030 | deg | 79% (28) | 100% (3) | n/a | 4.6 |
| GEN_CAND_HO_02 | 143 | ring scale % | -0.017 | % | 89% (28) | 100% (3) | n/a | 1.2 |
| GEN_CAND_HO_02 | 143 | 6 rotation | -0.449 | deg | 18% (28) | 0% (2) | n/a | 51.3 |
| GEN_CAND_HO_02 | 143 | 6 local offset | +0.350 | px | 77% (26) | 50% (2) | 23% / 50% | 25.0 |
| GEN_CAND_HO_02 | 143 | 9 rotation | +0.154 | deg | 57% (30) | 50% (2) | n/a | 2.4 |
| GEN_CAND_HO_02 | 143 | 9 local offset | +0.281 | px | 93% (27) | 100% (2) | 48% / 100% | 26.1 |
| GEN_CAND_HO_02 | 143 | 12 centreline rotation | +0.603 | deg | 32% (31) | 33% (3) | n/a | 14.9 |
| GEN_CAND_HO_02 | 143 | 12 local offset | +0.750 | px | 89% (28) | 0% (3) | 21% / 0% | 21.3 |
| GEN_CAND_HO_02 | 143 | rounds max local | +0.273 | px | 96% (28) | 67% (3) | 36% / 67% | 39.0 |

** = beyond every R-matched genuine watch (at least 5 matched watches).

