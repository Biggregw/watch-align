# Alpha96: pixel and dial-radius comparison (research only, offline)

Genuine: one primary photo per provenance-strong watch (CI run 37500197377), with the exact dial radius R of that photo.
Scaled experiment: the 8 local photos re-measured at several scales, with resampling variants at each scale.
No thresholds are derived; replica rows never enter a genuine statistic.

Genuine watches: 68; dial radius R px: min 129, median 434, max 835; 8 watches with R <= 240 px.

## A. Genuine: does the value depend on dial radius?

Spearman rho of |value| against R (one photo per watch). Negative rho in px would mean more detector noise on smaller dials; rho near 0 in px means the px value is resolution-independent, so R units penalise small dials.

| Feature | unit | n | rho(|value|, R) | median |v| R<=240 | median |v| R 240-360 | median |v| R>360 |
|---|---|---:|---:|---:|---:|---:|
| ring shift | px | 64 | 0.40 | 0.600 (n=8) | 0.512 (n=7) | 1.041 (n=49) |
| ring shift | R | 64 | -0.17 | 0.0044 (n=8) | 0.0021 (n=7) | 0.0021 (n=49) |
| ring rotation | deg | 64 | 0.06 | 0.031 (n=8) | 0.042 (n=7) | 0.048 (n=49) |
| ring scale % | % | 64 | 0.09 | 0.079 (n=8) | 0.104 (n=7) | 0.060 (n=49) |
| 6 rotation | deg | 62 | -0.13 | 0.374 (n=5) | 0.402 (n=7) | 0.176 (n=50) |
| 6 local offset | px | 59 | 0.58 | 0.325 (n=5) | 0.324 (n=7) | 0.719 (n=47) |
| 6 local offset | R | 59 | 0.06 | 0.0015 (n=5) | 0.0013 (n=7) | 0.0015 (n=47) |
| 9 rotation | deg | 66 | -0.21 | 0.304 (n=7) | 0.473 (n=7) | 0.172 (n=52) |
| 9 local offset | px | 62 | 0.65 | 0.448 (n=7) | 0.467 (n=7) | 0.905 (n=48) |
| 9 local offset | R | 62 | -0.12 | 0.0023 (n=7) | 0.0019 (n=7) | 0.0019 (n=48) |
| 12 centreline rotation | deg | 66 | -0.25 | 0.413 (n=8) | 0.228 (n=7) | 0.263 (n=51) |
| 12 local offset | px | 62 | 0.78 | 0.870 (n=8) | 0.865 (n=7) | 1.694 (n=47) |
| 12 local offset | R | 62 | 0.30 | 0.0040 (n=8) | 0.0035 (n=7) | 0.0039 (n=47) |
| rounds max local | px | 64 | 0.64 | 0.354 (n=8) | 0.352 (n=7) | 0.643 (n=49) |
| rounds max local | R | 64 | -0.32 | 0.0022 (n=8) | 0.0014 (n=7) | 0.0014 (n=49) |

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
| RL_THEONE_BLNR | 165 | ring shift | +0.527 | px | 73% (64) | 67% (6) | 27% / 67% | 18.5 |
| RL_THEONE_BLNR | 165 | ring rotation | +0.056 | deg | 38% (64) | 17% (6) | n/a | 3.2 |
| RL_THEONE_BLNR | 165 | ring scale % | +0.189 | % | 6% (64) | 17% (6) | n/a | 11.4 |
| RL_THEONE_BLNR | 165 | 6 rotation | +0.331 | deg | 40% (62) | 67% (3) | n/a | 3.6 |
| RL_THEONE_BLNR | 165 | 6 local offset | +0.777 | px | 39% (59) | 0% (3) | 0% / 0% | 18.4 |
| RL_THEONE_BLNR | 165 | 9 rotation | -0.849 | deg | 2% (66) | 20% (5) | n/a | 9.0 |
| RL_THEONE_BLNR | 165 | 9 local offset | +0.279 | px | 95% (62) | 80% (5) | 68% / 80% | 4.0 |
| RL_THEONE_BLNR | 165 | 12 centreline rotation | +0.965 | deg | 5% (66) | 17% (6) | n/a | 4.0 |
| RL_THEONE_BLNR | 165 | 12 local offset ** | +1.454 | px | 53% (62) | 0% (6) | 0% / 0% | 15.6 |
| RL_THEONE_BLNR | 165 | rounds max local ** | +0.657 | px | 36% (64) | 0% (6) | 0% / 0% | 38.0 |
| RL_USER_BATGIRL | 205 | ring shift | +0.705 | px | 58% (64) | 33% (9) | 22% / 33% | 29.4 |
| RL_USER_BATGIRL | 205 | ring rotation | +0.114 | deg | 3% (64) | 11% (9) | n/a | 7.5 |
| RL_USER_BATGIRL | 205 | ring scale % | +0.064 | % | 55% (64) | 67% (9) | n/a | 8.5 |
| RL_USER_BATGIRL | 205 | 6 rotation | +0.776 | deg | 2% (62) | 11% (9) | n/a | 5.5 |
| RL_USER_BATGIRL | 205 | 6 local offset | +0.544 | px | 59% (59) | 11% (9) | 8% / 11% | 5.3 |
| RL_USER_BATGIRL | 205 | 9 rotation ** | -1.520 | deg | 0% (66) | 0% (9) | n/a | 16.5 |
| RL_USER_BATGIRL | 205 | 9 local offset ** | +0.931 | px | 39% (62) | 0% (9) | 0% / 0% | 34.5 |
| RL_USER_BATGIRL | 205 | rounds max local ** | +0.902 | px | 11% (64) | 0% (9) | 0% / 0% | 56.8 |
| RL_LOCAL_BLNR | 220 | ring shift | +0.488 | px | 78% (64) | 70% (10) | 50% / 60% | 10.6 |
| RL_LOCAL_BLNR | 220 | ring rotation ** | +0.550 | deg | 0% (64) | 0% (10) | n/a | 15.0 |
| RL_LOCAL_BLNR | 220 | ring scale % ** | +0.697 | % | 0% (64) | 0% (10) | n/a | 11.6 |
| RL_LOCAL_BLNR | 220 | 6 rotation | -0.013 | deg | 98% (62) | 100% (10) | n/a | 0.1 |
| RL_LOCAL_BLNR | 220 | 6 local offset ** | +0.863 | px | 32% (59) | 0% (10) | 0% / 0% | 8.8 |
| RL_LOCAL_BLNR | 220 | 12 centreline rotation | +0.787 | deg | 11% (66) | 50% (10) | n/a | 10.4 |
| RL_LOCAL_BLNR | 220 | 12 local offset ** | +3.043 | px | 16% (62) | 0% (10) | 0% / 0% | 32.3 |
| RL_LOCAL_BLNR | 220 | rounds max local | +0.368 | px | 88% (64) | 70% (10) | 31% / 70% | 1.6 |
| RL_ARF_BLRO_CROOKED6 | 234 | ring shift | +1.129 | px | 38% (64) | 11% (9) | 6% / 22% | 64.0 |
| RL_ARF_BLRO_CROOKED6 | 234 | ring rotation | -0.048 | deg | 44% (64) | 33% (9) | n/a | 5.5 |
| RL_ARF_BLRO_CROOKED6 | 234 | ring scale % ** | +0.953 | % | 0% (64) | 0% (9) | n/a | 218.8 |
| RL_ARF_BLRO_CROOKED6 | 234 | 6 rotation | +0.008 | deg | 98% (62) | 100% (9) | n/a | 0.1 |
| RL_ARF_BLRO_CROOKED6 | 234 | 6 local offset ** | +0.862 | px | 32% (59) | 0% (9) | 0% / 0% | 16.0 |
| RL_ARF_BLRO_CROOKED6 | 234 | 9 rotation | -0.037 | deg | 95% (66) | 100% (9) | n/a | 0.4 |
| RL_ARF_BLRO_CROOKED6 | 234 | 9 local offset | +0.380 | px | 90% (62) | 89% (9) | 69% / 78% | 8.9 |
| RL_ARF_BLRO_CROOKED6 | 234 | 12 centreline rotation | +0.277 | deg | 48% (66) | 56% (9) | n/a | 5.5 |
| RL_ARF_BLRO_CROOKED6 | 234 | 12 local offset ** | +2.380 | px | 21% (62) | 0% (9) | 0% / 0% | 72.6 |
| RL_ARF_BLRO_CROOKED6 | 234 | rounds max local ** | +1.080 | px | 6% (64) | 0% (9) | 0% / 0% | 47.1 |

### Marketplace genuine candidates (descriptive only)

| Photo | R px | Feature | value | unit | all genuine: exceed (n) | R-matched: exceed (n) | in R units: all / matched | SNR |
|---|---:|---|---:|---|---:|---:|---:|---:|
| GEN_CAND_WEX_BLRO_01 | 140 | ring shift | +0.397 | px | 81% (64) | 67% (6) | 38% / 67% | 26.7 |
| GEN_CAND_WEX_BLRO_01 | 140 | ring rotation | +0.003 | deg | 98% (64) | 83% (6) | n/a | 0.1 |
| GEN_CAND_WEX_BLRO_01 | 140 | ring scale % ** | +0.254 | % | 0% (64) | 0% (6) | n/a | 27.7 |
| GEN_CAND_WEX_BLRO_01 | 140 | 6 rotation | -0.443 | deg | 21% (62) | 0% (3) | n/a | 2.6 |
| GEN_CAND_WEX_BLRO_01 | 140 | 6 local offset | +0.305 | px | 88% (59) | 67% (3) | 19% / 33% | 11.3 |
| GEN_CAND_WEX_BLRO_01 | 140 | 9 rotation | -0.406 | deg | 23% (66) | 20% (5) | n/a | 3.1 |
| GEN_CAND_WEX_BLRO_01 | 140 | 9 local offset | +0.335 | px | 94% (62) | 80% (5) | 31% / 60% | 5.8 |
| GEN_CAND_WEX_BLRO_01 | 140 | rounds max local | +0.266 | px | 97% (64) | 67% (6) | 20% / 67% | 11.8 |
| GEN_CAND_WEX_BLRO_02 | 155 | ring shift | +0.280 | px | 88% (64) | 83% (6) | 64% / 100% | 40.0 |
| GEN_CAND_WEX_BLRO_02 | 155 | ring rotation | +0.024 | deg | 72% (64) | 50% (6) | n/a | 0.9 |
| GEN_CAND_WEX_BLRO_02 | 155 | ring scale % | +0.176 | % | 9% (64) | 17% (6) | n/a | 17.6 |
| GEN_CAND_WEX_BLRO_02 | 155 | 6 rotation | -0.106 | deg | 65% (62) | 100% (3) | n/a | 0.9 |
| GEN_CAND_WEX_BLRO_02 | 155 | 6 local offset | +0.438 | px | 73% (59) | 33% (3) | 5% / 33% | 10.3 |
| GEN_CAND_WEX_BLRO_02 | 155 | 9 rotation | +0.032 | deg | 95% (66) | 100% (5) | n/a | 0.6 |
| GEN_CAND_WEX_BLRO_02 | 155 | 9 local offset | +0.392 | px | 87% (62) | 40% (5) | 29% / 60% | 14.0 |
| GEN_CAND_WEX_BLRO_02 | 155 | rounds max local | +0.295 | px | 95% (64) | 50% (6) | 20% / 67% | 17.6 |
| GEN_CAND_HO_01 | 203 | ring shift | +0.708 | px | 58% (64) | 27% (11) | 22% / 27% | 53.1 |
| GEN_CAND_HO_01 | 203 | ring rotation | -0.026 | deg | 67% (64) | 45% (11) | n/a | 1.4 |
| GEN_CAND_HO_01 | 203 | ring scale % ** | +0.173 | % | 9% (64) | 0% (11) | n/a | 23.4 |
| GEN_CAND_HO_01 | 203 | 6 rotation | +0.475 | deg | 15% (62) | 44% (9) | n/a | 10.5 |
| GEN_CAND_HO_01 | 203 | 6 local offset | +0.351 | px | 78% (59) | 22% (9) | 41% / 33% | 19.7 |
| GEN_CAND_HO_01 | 203 | 9 rotation | +0.197 | deg | 48% (66) | 82% (11) | n/a | 1.7 |
| GEN_CAND_HO_01 | 203 | 9 local offset | +0.589 | px | 69% (62) | 9% (11) | 11% / 9% | 19.2 |
| GEN_CAND_HO_01 | 203 | 12 centreline rotation | -0.432 | deg | 35% (66) | 45% (11) | n/a | 8.2 |
| GEN_CAND_HO_01 | 203 | 12 local offset | +0.876 | px | 85% (62) | 45% (11) | 35% / 27% | 18.8 |
| GEN_CAND_HO_01 | 203 | rounds max local | +0.498 | px | 73% (64) | 27% (11) | 6% / 27% | 35.4 |
| GEN_CAND_HO_02 | 143 | ring shift | +0.410 | px | 80% (64) | 67% (6) | 38% / 67% | 20.7 |
| GEN_CAND_HO_02 | 143 | ring rotation | +0.030 | deg | 67% (64) | 50% (6) | n/a | 4.6 |
| GEN_CAND_HO_02 | 143 | ring scale % | -0.017 | % | 84% (64) | 83% (6) | n/a | 1.2 |
| GEN_CAND_HO_02 | 143 | 6 rotation | -0.449 | deg | 21% (62) | 0% (3) | n/a | 51.3 |
| GEN_CAND_HO_02 | 143 | 6 local offset | +0.350 | px | 78% (59) | 33% (3) | 14% / 33% | 25.0 |
| GEN_CAND_HO_02 | 143 | 9 rotation | +0.154 | deg | 59% (66) | 80% (5) | n/a | 2.4 |
| GEN_CAND_HO_02 | 143 | 9 local offset | +0.281 | px | 95% (62) | 80% (5) | 50% / 80% | 26.1 |
| GEN_CAND_HO_02 | 143 | 12 centreline rotation | +0.603 | deg | 26% (66) | 33% (6) | n/a | 14.9 |
| GEN_CAND_HO_02 | 143 | 12 local offset | +0.750 | px | 92% (62) | 50% (6) | 19% / 50% | 21.3 |
| GEN_CAND_HO_02 | 143 | rounds max local | +0.273 | px | 97% (64) | 67% (6) | 19% / 67% | 39.0 |

** = beyond every R-matched genuine watch (at least 5 matched watches).

