# 12-triangle nominal calibrated on genuine watches (research only)

24 physical watches (shared / stock photos excluded at level dial; sources excluded: SWE), one value per watch. Sources: Bob's 8, Phillips 1, other 15.

## Nominal (correction relative to the Alpha92 master) with bootstrap 95% interval over watches

| Quantity | nominal | 95% interval |
|---|---:|---:|
| radial_R | -0.0010 | -0.0017 .. -0.0003 |
| tangential_R | -0.0001 | -0.0002 .. +0.0005 |
| left_side_deg | -0.015 | -0.111 .. +0.050 |
| right_side_deg | +0.329 | +0.167 .. +0.507 |
| base_tilt_deg | +0.040 | -0.097 .. +0.395 |
| rotation_deg | +0.181 | +0.057 .. +0.263 |

## Genuine 12 offset (R units): median / P90 / max over watches

| Reference | median / P90 / max |
|---|---:|
| Alpha92 master (as shipped) | 0.0016 / 0.0037 / 0.0044 |
| genuine nominal, in-sample | 0.0011 / 0.0033 / 0.0040 |
| genuine nominal, leave-one-watch-out | 0.0013 / 0.0035 / 0.0041 |

## Leave-one-source-out (nominal from the other sources only)

| Held-out source | watches | nominal radial / tangential from the rest | held-out offset median / P90 / max |
|---|---:|---:|---:|
| Bob's | 8 | -0.0006 / +0.0003 | 0.0008 / 0.0019 / 0.0026 |
| Phillips | 1 | -0.0008 / -0.0000 | 0.0026 / 0.0026 / 0.0026 |
| other | 15 | -0.0015 / -0.0002 | 0.0015 / 0.0036 / 0.0043 |

## Shape after re-centring (degrees, median |value| over watches: master -> genuine nominal)

- left_side_deg: 0.111 -> 0.097
- right_side_deg: 0.450 -> 0.203
- base_tilt_deg: 0.422 -> 0.409
- rotation_deg: 0.195 -> 0.145

## Local photos against the leave-one-watch-out genuine spread

| Photo | group | R px | offset from master R | offset from genuine nominal R | genuine watches at least as far (LOWO) | centreline rot re-centred deg | left / right side re-centred deg |
|---|---|---:|---:|---:|---:|---:|---:|

No limits are derived. The Alpha92 master and production code are unchanged; the nominal is applied only by the harness prototype (`tools/desktop-harness/drivers/Alpha97TriangleNominal.java`).

