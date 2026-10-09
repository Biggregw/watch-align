# 12-triangle nominal calibrated on genuine watches (research only)

118 physical watches (shared / stock photos excluded at level dial; sources excluded: SWE), one value per watch. Sources: Bob's 118.

## Nominal (correction relative to the Alpha92 master) with bootstrap 95% interval over watches

| Quantity | nominal | 95% interval |
|---|---:|---:|
| radial_R | -0.0019 | -0.0021 .. -0.0017 |
| tangential_R | -0.0009 | -0.0011 .. -0.0008 |
| left_side_deg | -0.261 | -0.365 .. -0.154 |
| right_side_deg | -0.005 | -0.110 .. +0.130 |
| base_tilt_deg | -0.118 | -0.182 .. +0.012 |
| rotation_deg | -0.141 | -0.251 .. +0.022 |

## Genuine 12 offset (R units): median / P90 / max over watches

| Reference | median / P90 / max |
|---|---:|
| Alpha92 master (as shipped) | 0.0023 / 0.0034 / 0.0044 |
| genuine nominal, in-sample | 0.0009 / 0.0017 / 0.0031 |
| genuine nominal, leave-one-watch-out | 0.0009 / 0.0017 / 0.0031 |

## Leave-one-source-out (nominal from the other sources only)

| Held-out source | watches | nominal radial / tangential from the rest | held-out offset median / P90 / max |
|---|---:|---:|---:|

## Shape after re-centring (degrees, median |value| over watches: master -> genuine nominal)

- left_side_deg: 0.373 -> 0.282
- right_side_deg: 0.325 -> 0.325
- base_tilt_deg: 0.341 -> 0.397
- rotation_deg: 0.334 -> 0.295

## Local photos against the leave-one-watch-out genuine spread

| Photo | group | R px | offset from master R | offset from genuine nominal R | genuine watches at least as far (LOWO) | centreline rot re-centred deg | left / right side re-centred deg |
|---|---|---:|---:|---:|---:|---:|---:|

No limits are derived. The Alpha92 master and production code are unchanged; the nominal is applied only by the harness prototype (`tools/desktop-harness/drivers/Alpha97TriangleNominal.java`).

