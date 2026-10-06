# 12-triangle nominal calibrated on genuine watches (research only)

41 physical watches (shared / stock photos excluded at level dial; sources excluded: SWE), one value per watch. Sources: Bob's 32, Phillips 7, other 2.

## Nominal (correction relative to the Alpha92 master) with bootstrap 95% interval over watches

| Quantity | nominal | 95% interval |
|---|---:|---:|
| radial_R | -0.0037 | -0.0038 .. -0.0031 |
| tangential_R | -0.0008 | -0.0009 .. -0.0006 |
| left_side_deg | -0.137 | -0.366 .. -0.045 |
| right_side_deg | +0.154 | -0.070 .. +0.273 |
| base_tilt_deg | -0.140 | -0.254 .. +0.070 |
| rotation_deg | +0.040 | -0.188 .. +0.107 |

## Genuine 12 offset (R units): median / P90 / max over watches

| Reference | median / P90 / max |
|---|---:|
| Alpha92 master (as shipped) | 0.0038 / 0.0046 / 0.0058 |
| genuine nominal, in-sample | 0.0009 / 0.0020 / 0.0032 |
| genuine nominal, leave-one-watch-out | 0.0009 / 0.0020 / 0.0032 |

## Leave-one-source-out (nominal from the other sources only)

| Held-out source | watches | nominal radial / tangential from the rest | held-out offset median / P90 / max |
|---|---:|---:|---:|
| Bob's | 32 | -0.0041 / -0.0008 | 0.0012 / 0.0021 / 0.0024 |
| Phillips | 7 | -0.0034 / -0.0007 | 0.0010 / 0.0020 / 0.0024 |
| other | 2 | -0.0037 / -0.0008 | 0.0017 / 0.0029 / 0.0032 |

## Shape after re-centring (degrees, median |value| over watches: master -> genuine nominal)

- left_side_deg: 0.338 -> 0.274
- right_side_deg: 0.273 -> 0.296
- base_tilt_deg: 0.258 -> 0.258
- rotation_deg: 0.263 -> 0.293

## Local photos against the leave-one-watch-out genuine spread

| Photo | group | R px | offset from master R | offset from genuine nominal R | genuine watches at least as far (LOWO) | centreline rot re-centred deg | left / right side re-centred deg |
|---|---|---:|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | gen_candidate | 203 | 0.0045 | 0.0012 | 16/41 | -0.47 | -0.00 / -0.46 |
| GEN_CAND_HO_02 | gen_candidate | 143 | 0.0053 | 0.0016 | 10/41 | +0.56 | +0.61 / +0.49 |
| RL_THEONE_BLNR | rl_control | 165 | 0.0089 | 0.0052 | 0/41 | +0.93 | +1.13 / +0.73 |
| RL_LOCAL_BLNR | rl_control | 220 | 0.0140 | 0.0135 | 0/41 | +0.75 | +0.89 / +0.96 |
| RL_ARF_BLRO_CROOKED6 | rl_control | 234 | 0.0101 | 0.0116 | 0/41 | +0.24 | +0.17 / +0.46 |

No limits are derived. The Alpha92 master and production code are unchanged; the nominal is applied only by the harness prototype (`tools/desktop-harness/drivers/Alpha97TriangleNominal.java`).

