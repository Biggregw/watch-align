# 12-triangle nominal calibrated on genuine watches (research only)

89 physical watches (shared / stock photos excluded at level dial; sources excluded: SWE), one value per watch. Sources: Bob's 44, Phillips 1, other 44.

## Nominal (correction relative to the Alpha92 master) with bootstrap 95% interval over watches

| Quantity | nominal | 95% interval |
|---|---:|---:|
| radial_R | -0.0017 | -0.0020 .. -0.0014 |
| tangential_R | -0.0002 | -0.0005 .. -0.0001 |
| left_side_deg | -0.017 | -0.106 .. +0.007 |
| right_side_deg | +0.317 | +0.248 .. +0.433 |
| base_tilt_deg | +0.111 | -0.003 .. +0.220 |
| rotation_deg | +0.161 | +0.057 .. +0.227 |

## Genuine 12 offset (R units): median / P90 / max over watches

| Reference | median / P90 / max |
|---|---:|
| Alpha92 master (as shipped) | 0.0022 / 0.0034 / 0.0046 |
| genuine nominal, in-sample | 0.0014 / 0.0025 / 0.0044 |
| genuine nominal, leave-one-watch-out | 0.0014 / 0.0025 / 0.0044 |

## Leave-one-source-out (nominal from the other sources only)

| Held-out source | watches | nominal radial / tangential from the rest | held-out offset median / P90 / max |
|---|---:|---:|---:|
| Bob's | 44 | -0.0016 / -0.0003 | 0.0012 / 0.0019 / 0.0024 |
| Phillips | 1 | -0.0017 / -0.0002 | 0.0017 / 0.0017 / 0.0017 |
| other | 44 | -0.0018 / -0.0002 | 0.0016 / 0.0030 / 0.0044 |

## Shape after re-centring (degrees, median |value| over watches: master -> genuine nominal)

- left_side_deg: 0.205 -> 0.207
- right_side_deg: 0.463 -> 0.224
- base_tilt_deg: 0.377 -> 0.345
- rotation_deg: 0.294 -> 0.180

## Local photos against the leave-one-watch-out genuine spread

| Photo | group | R px | offset from master R | offset from genuine nominal R | genuine watches at least as far (LOWO) | centreline rot re-centred deg | left / right side re-centred deg |
|---|---|---:|---:|---:|---:|---:|---:|

No limits are derived. The Alpha92 master and production code are unchanged; the nominal is applied only by the harness prototype (`tools/desktop-harness/drivers/Alpha97TriangleNominal.java`).

