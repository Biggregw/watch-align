# 12-triangle nominal calibrated on genuine watches (research only)

45 physical watches (shared / stock photos excluded at level dial; sources excluded: SWE), one value per watch. Sources: Bob's 32, Owner priority 4, Phillips 7, other 2.

## Nominal (correction relative to the Alpha92 master) with bootstrap 95% interval over watches

| Quantity | nominal | 95% interval |
|---|---:|---:|
| radial_R | -0.0037 | -0.0039 .. -0.0033 |
| tangential_R | -0.0007 | -0.0008 .. -0.0005 |
| left_side_deg | -0.174 | -0.366 .. -0.077 |
| right_side_deg | +0.133 | -0.088 .. +0.250 |
| base_tilt_deg | -0.158 | -0.335 .. +0.058 |
| rotation_deg | -0.089 | -0.191 .. +0.073 |

## Genuine 12 offset (R units): median / P90 / max over watches

| Reference | median / P90 / max |
|---|---:|
| Alpha92 master (as shipped) | 0.0038 / 0.0050 / 0.0120 |
| genuine nominal, in-sample | 0.0010 / 0.0022 / 0.0083 |
| genuine nominal, leave-one-watch-out | 0.0010 / 0.0022 / 0.0083 |

## Leave-one-source-out (nominal from the other sources only)

| Held-out source | watches | nominal radial / tangential from the rest | held-out offset median / P90 / max |
|---|---:|---:|---:|
| Bob's | 32 | -0.0043 / -0.0006 | 0.0015 / 0.0023 / 0.0027 |
| Owner priority | 4 | -0.0037 / -0.0008 | 0.0029 / 0.0067 / 0.0083 |
| Phillips | 7 | -0.0037 / -0.0006 | 0.0008 / 0.0018 / 0.0021 |
| other | 2 | -0.0037 / -0.0007 | 0.0017 / 0.0029 / 0.0032 |

## Shape after re-centring (degrees, median |value| over watches: master -> genuine nominal)

- left_side_deg: 0.338 -> 0.243
- right_side_deg: 0.273 -> 0.288
- base_tilt_deg: 0.335 -> 0.277
- rotation_deg: 0.263 -> 0.232

## Local photos against the leave-one-watch-out genuine spread

| Photo | group | R px | offset from master R | offset from genuine nominal R | genuine watches at least as far (LOWO) | centreline rot re-centred deg | left / right side re-centred deg |
|---|---|---:|---:|---:|---:|---:|---:|
| GEN_CAND_HO_01 | gen_candidate | 203 | 0.0045 | 0.0013 | 18/45 | -0.34 | +0.04 / -0.44 |
| GEN_CAND_HO_02 | gen_candidate | 143 | 0.0053 | 0.0016 | 13/45 | +0.69 | +0.65 / +0.51 |
| RL_THEONE_BLNR | rl_control | 165 | 0.0089 | 0.0052 | 1/45 | +1.05 | +1.16 / +0.75 |
| RL_LOCAL_BLNR | rl_control | 220 | 0.0140 | 0.0136 | 0/45 | +0.88 | +0.93 / +0.98 |
| RL_ARF_BLRO_CROOKED6 | rl_control | 234 | 0.0101 | 0.0116 | 0/45 | +0.37 | +0.20 / +0.49 |

No limits are derived. The Alpha92 master and production code are unchanged; the nominal is applied only by the harness prototype (`tools/desktop-harness/drivers/Alpha97TriangleNominal.java`).

