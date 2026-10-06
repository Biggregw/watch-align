# 12-triangle nominal calibrated on genuine watches (research only)

59 physical watches (shared / stock photos excluded at level dial), one value per watch. Sources: Bob's 32, Phillips 7, SWE 18, other 2.

## Nominal (correction relative to the Alpha92 master) with bootstrap 95% interval over watches

| Quantity | nominal | 95% interval |
|---|---:|---:|
| radial_R | -0.0038 | -0.0043 .. -0.0036 |
| tangential_R | -0.0009 | -0.0011 .. -0.0007 |
| left_side_deg | -0.174 | -0.348 .. -0.086 |
| right_side_deg | +0.107 | -0.085 .. +0.193 |
| base_tilt_deg | -0.176 | -0.335 .. -0.039 |
| rotation_deg | -0.098 | -0.188 .. +0.047 |

## Genuine 12 offset (R units): median / P90 / max over watches

| Reference | median / P90 / max |
|---|---:|
| Alpha92 master (as shipped) | 0.0039 / 0.0061 / 0.0083 |
| genuine nominal, in-sample | 0.0012 / 0.0029 / 0.0046 |
| genuine nominal, leave-one-watch-out | 0.0012 / 0.0029 / 0.0046 |

## Leave-one-source-out (nominal from the other sources only)

| Held-out source | watches | nominal radial / tangential from the rest | held-out offset median / P90 / max |
|---|---:|---:|---:|
| Bob's | 32 | -0.0045 / -0.0011 | 0.0015 / 0.0024 / 0.0029 |
| Phillips | 7 | -0.0037 / -0.0009 | 0.0007 / 0.0017 / 0.0021 |
| SWE | 18 | -0.0037 / -0.0008 | 0.0023 / 0.0041 / 0.0045 |
| other | 2 | -0.0038 / -0.0009 | 0.0018 / 0.0030 / 0.0033 |

## Shape after re-centring (degrees, median |value| over watches: master -> genuine nominal)

- left_side_deg: 0.334 -> 0.243
- right_side_deg: 0.275 -> 0.249
- base_tilt_deg: 0.318 -> 0.266
- rotation_deg: 0.263 -> 0.221

## Local photos against the leave-one-watch-out genuine spread

| Photo | group | R px | offset from master R | offset from genuine nominal R | genuine watches at least as far (LOWO) | centreline rot re-centred deg | left / right side re-centred deg |
|---|---|---:|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | gen_candidate | 203 | 0.0045 | 0.0010 | 32/59 | -0.33 | +0.04 / -0.41 |
| GEN_CAND_HO_02 | gen_candidate | 143 | 0.0053 | 0.0014 | 24/59 | +0.70 | +0.65 / +0.54 |
| RL_THEONE_BLNR | rl_control | 165 | 0.0089 | 0.0050 | 0/59 | +1.06 | +1.16 / +0.78 |
| RL_LOCAL_BLNR | rl_control | 220 | 0.0140 | 0.0134 | 0/59 | +0.89 | +0.93 / +1.00 |
| RL_ARF_BLRO_CROOKED6 | rl_control | 234 | 0.0101 | 0.0116 | 0/59 | +0.38 | +0.20 / +0.51 |

No limits are derived. The Alpha92 master and production code are unchanged; the nominal is applied only by the harness prototype (`tools/desktop-harness/drivers/Alpha97TriangleNominal.java`).

