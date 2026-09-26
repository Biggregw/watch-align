# GMT 12 gap on the triangle's outer edge — 2026-09-26 (alpha48)

## Why the definition changed
Up to alpha47 the Android 12-gap was measured from the thresholded triangle contour.
That contour followed the lume on some photos and the outer white-gold surround on
others, so the same kind of genuine dial could read 0.18 or 0.12. The provisional
`LOW_CLEARANCE_ATTENTION = 0.129` and the "genuine 0.14–0.18" reference were built on
that mixed definition.

From alpha48 the gap is measured on the surround's outer edge (`TriangleEdgeRefiner`,
half-level crossing, lines fitted per side, base search bounded by the 59–01 ticks).
If the outer edge cannot be fitted the gap is still reported but the result is low
confidence (`detectorStable=false`, caps at CHECK). The triangle-finder size caps were
raised from 0.29r/0.30r to 0.32r/0.36r because the physical surround is ~0.25r × ~0.30r.

## Measurements (app code on desktop OpenCV 4.9, 1600 px working size)

| image | status | contour gap | outer-edge gap |
|---|---|---:|---:|
| m126710blnr-0002 (official render) | genuine | 0.135 | 0.089 |
| m126710blnr-0003 (official render) | genuine | 0.138 | 0.092 |
| m126710grnr-0003 (official render) | genuine | 0.136 | 0.090 |
| m126710grnr-0004 (official render) | genuine | 0.140 | 0.094 |
| m126711chnr-0002 (official render) | genuine | 0.135 | 0.090 |
| m126713grnr-0001 (official render) | genuine | 0.137 | 0.091 |
| m126715chnr-0001 (official render) | genuine | 0.140 | 0.096 |
| m126718grnr-0001 (official render) | genuine | 0.136 | 0.090 |
| m126720vtnr-0001 (official render) | genuine | 0.137 | 0.091 |
| m126720vtnr-0002 (official render) | genuine | 0.135 | 0.088 |
| m126729vtnr-0001 (official render) | genuine | 0.131 | 0.086 |
| 126710BLNR dealer-box photo (field) | genuine (user) | 0.115–0.175* | 0.084 |
| 126711CHNR seller photo (field, rebuilt from screenshot) | unknown | 0.119 | 0.105 |
| 126710BLNR "ONE" sticker photo (field, screenshot) | replica (user) | 0.095 | not fitted — GMT hand touches triangle |

\* depends on which edge the contour happened to follow; the phone read 0.175–0.180.

Official renders are one CAD design rendered repeatedly, so their spread (0.086–0.096)
understates real photo-to-photo variation. Edge location is about ±0.5 px on a ~50 px
triangle, i.e. about ±0.01 in gap.

## Provisional boundary
`LOW_CLEARANCE_ATTENTION = 0.070`: clearly below every genuine reading (lowest 0.084)
after allowing ~0.01 measurement noise. No replica has been measured cleanly on this
definition yet. Re-derive from labelled originals (not screenshots), with the hands away
from 12, before treating the boundary as more than a prompt to look.
