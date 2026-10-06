# Lighting-robust 12-triangle measures (research only, offline)

Genuine: 59 physical watches (shared photos excluded), sources Bob's 32, Phillips 7, SWE 18, other 2. Values re-centred on the genuine nominal; genuine scored leave-one-watch-out. No limits are derived.

## Robustness scores

| Measure | unit | source shift vs Bob's, signed (Phillips / SWE), in genuine MADs | source shift of magnitude (Phillips / SWE) | resolution SD / genuine MAD (photos) | within-watch MAD / between-watch MAD (watches) | genuine median / P90 / max |
|---|---|---:|---:|---:|---:|---:|
| offset2d | R | n/a (magnitude only) | -0.56 / +1.94 | 0.84 (5) | 0.33 (17) | 0.0012 / 0.0029 / 0.0046 |
| radial | R | -1.00 / -2.32 | -0.46 / +2.48 | 0.82 (5) | 0.47 (17) | 0.0008 / 0.0026 / 0.0046 |
| lateral | R | -0.60 / -1.25 | -1.16 / -0.98 | 2.14 (5) | 0.66 (17) | 0.0003 / 0.0014 / 0.0021 |
| centreline | deg | -1.57 / -0.87 | +1.81 / -0.23 | 0.38 (5) | 0.17 (17) | 0.23 / 0.73 / 1.54 |
| sides | deg | n/a (magnitude only) | +1.63 / +0.34 | 0.26 (5) | 0.14 (17) | 0.31 / 0.81 / 2.38 |
| apex | deg | -1.01 / -0.56 | +1.07 / +0.86 | 1.15 (5) | 0.59 (17) | 0.04 / 0.16 / 1.00 |

## Local photos: value and genuine watches at least as far (leave-one-watch-out spread)

| Photo | group | offset2d | radial | lateral | centreline | sides | apex |
|---|---|---:|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | gen_candidate | 0.0010 (32/59) | 0.0002 (46/59) | 0.0010 (14/59) | 0.33 (25/59) | 0.41 (25/59) | 0.21 (4/59) |
| GEN_CAND_HO_02 | gen_candidate | 0.0014 (24/59) | 0.0013 (19/59) | 0.0006 (18/59) | 0.70 (8/59) | 0.65 (13/59) | 0.05 (25/59) |
| RL_THEONE_BLNR | rl_control | 0.0050 (0/59) | 0.0045 (1/59) | 0.0023 (0/59) | 1.06 (2/59) | 1.16 (2/59) | 0.18 (5/59) |
| RL_LOCAL_BLNR | rl_control | 0.0134 (0/59) | 0.0030 (5/59) | 0.0131 (0/59) | 0.89 (4/59) | 1.00 (3/59) | 0.05 (25/59) |
| RL_ARF_BLRO_CROOKED6 | rl_control | 0.0116 (0/59) | 0.0080 (0/59) | 0.0083 (0/59) | 0.38 (22/59) | 0.51 (20/59) | 0.16 (6/59) |

## Per-side consistency gate (M12Diag, CI run 37508028297; curve only, no gate chosen)

Inconsistency = |(long sides - base) - genuine median +0.0041 R| in units of its genuine MAD (0.0008 R). Withholding photos above k MADs: share of each source kept, and the remaining radial-offset source shift.

| k | kept: all / Bob's / Phillips / SWE | 12 radial median: Bob's / Phillips / SWE | genuine 12 radial MAD |
|---:|---:|---:|---:|
| none | 95/95 / 40/40 / 47/47 / 6/6 | -0.0037 / -0.0041 / -0.0057 | 0.0006 |
| 3 | 81/95 / 38/40 / 39/47 / 3/6 | -0.0037 / -0.0041 / -0.0045 | 0.0005 |
| 2 | 74/95 / 34/40 / 36/47 / 3/6 | -0.0037 / -0.0040 / -0.0045 | 0.0005 |
| 1.5 | 64/95 / 28/40 / 33/47 / 2/6 | -0.0038 / -0.0040 / -0.0045 | 0.0003 |
| 1 | 48/95 / 22/40 / 25/47 / 1/6 | -0.0038 / -0.0040 / -0.0045 | 0.0003 |

