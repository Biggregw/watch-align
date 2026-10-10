# 12-triangle nominal calibrated on genuine watches (research only)

93 physical watches (shared / stock photos excluded at level dial; sources excluded: SWE), one value per watch. Sources: Bob's 50, Phillips 1, other 42.

## Nominal (correction relative to the Alpha92 master) with bootstrap 95% interval over watches

| Quantity | nominal | 95% interval |
|---|---:|---:|
| radial_R | -0.0017 | -0.0020 .. -0.0015 |
| tangential_R | -0.0003 | -0.0005 .. -0.0001 |
| left_side_deg | -0.011 | -0.082 .. +0.041 |
| right_side_deg | +0.353 | +0.248 .. +0.433 |
| base_tilt_deg | +0.160 | +0.003 .. +0.226 |
| rotation_deg | +0.167 | +0.074 .. +0.248 |

## Genuine 12 offset (R units): median / P90 / max over watches

| Reference | median / P90 / max |
|---|---:|
| Alpha92 master (as shipped) | 0.0022 / 0.0034 / 0.0047 |
| genuine nominal, in-sample | 0.0012 / 0.0023 / 0.0041 |
| genuine nominal, leave-one-watch-out | 0.0013 / 0.0023 / 0.0041 |

## Leave-one-source-out (nominal from the other sources only)

| Held-out source | watches | nominal radial / tangential from the rest | held-out offset median / P90 / max |
|---|---:|---:|---:|
| Bob's | 50 | -0.0017 / -0.0005 | 0.0011 / 0.0022 / 0.0035 |
| Phillips | 1 | -0.0017 / -0.0003 | 0.0017 / 0.0017 / 0.0017 |
| other | 42 | -0.0017 / -0.0002 | 0.0013 / 0.0025 / 0.0041 |

## Shape after re-centring (degrees, median |value| over watches: master -> genuine nominal)

- left_side_deg: 0.221 -> 0.216
- right_side_deg: 0.465 -> 0.230
- base_tilt_deg: 0.395 -> 0.308
- rotation_deg: 0.307 -> 0.221

## Local photos against the leave-one-watch-out genuine spread

| Photo | group | R px | offset from master R | offset from genuine nominal R | genuine watches at least as far (LOWO) | centreline rot re-centred deg | left / right side re-centred deg |
|---|---|---:|---:|---:|---:|---:|---:|

No limits are derived. The Alpha92 master and production code are unchanged; the nominal is applied only by the harness prototype (`tools/desktop-harness/drivers/Alpha97TriangleNominal.java`).

