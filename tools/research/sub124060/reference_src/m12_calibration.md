# 12-triangle nominal calibrated on genuine watches (research only)

95 physical watches (shared / stock photos excluded at level dial; sources excluded: SWE), one value per watch. Sources: Bob's 50, Phillips 1, other 44.

## Nominal (correction relative to the Alpha92 master) with bootstrap 95% interval over watches

| Quantity | nominal | 95% interval |
|---|---:|---:|
| radial_R | -0.0017 | -0.0020 .. -0.0014 |
| tangential_R | -0.0002 | -0.0005 .. -0.0001 |
| left_side_deg | -0.013 | -0.106 .. +0.008 |
| right_side_deg | +0.353 | +0.249 .. +0.442 |
| base_tilt_deg | +0.160 | -0.003 .. +0.226 |
| rotation_deg | +0.167 | +0.057 .. +0.235 |

## Genuine 12 offset (R units): median / P90 / max over watches

| Reference | median / P90 / max |
|---|---:|
| Alpha92 master (as shipped) | 0.0022 / 0.0035 / 0.0047 |
| genuine nominal, in-sample | 0.0013 / 0.0025 / 0.0044 |
| genuine nominal, leave-one-watch-out | 0.0013 / 0.0025 / 0.0044 |

## Leave-one-source-out (nominal from the other sources only)

| Held-out source | watches | nominal radial / tangential from the rest | held-out offset median / P90 / max |
|---|---:|---:|---:|
| Bob's | 50 | -0.0016 / -0.0003 | 0.0012 / 0.0021 / 0.0035 |
| Phillips | 1 | -0.0016 / -0.0002 | 0.0018 / 0.0018 / 0.0018 |
| other | 44 | -0.0017 / -0.0002 | 0.0016 / 0.0030 / 0.0044 |

## Shape after re-centring (degrees, median |value| over watches: master -> genuine nominal)

- left_side_deg: 0.221 -> 0.218
- right_side_deg: 0.465 -> 0.232
- base_tilt_deg: 0.416 -> 0.319
- rotation_deg: 0.294 -> 0.257

## Local photos against the leave-one-watch-out genuine spread

| Photo | group | R px | offset from master R | offset from genuine nominal R | genuine watches at least as far (LOWO) | centreline rot re-centred deg | left / right side re-centred deg |
|---|---|---:|---:|---:|---:|---:|---:|

No limits are derived. The Alpha92 master and production code are unchanged; the nominal is applied only by the harness prototype (`tools/desktop-harness/drivers/Alpha97TriangleNominal.java`).

