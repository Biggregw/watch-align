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

## Resolution limit (2026-09-27, alpha50)
The gap is a fraction of triangle width, so 1 px = 1/width of gap: ~0.018 on a 55 px
triangle. The whole spread between genuine readings (~0.085) and the attention level
(0.070) is under one pixel at typical phone-photo framing. A second replica photo
(126710BLNR on Oyster, sideways tilt, triangle ~55 px) read 0.062 with a clear 3-4 px
visible gap; the old report called it "very small or touching".

`GmtHumanQcAnalyzerV2` now applies `GAP_PX_UNCERTAINTY = 0.75` px (base-edge line fit
plus tick-end location). A small-gap flag is only kept if gap + 0.75/width is still below
the attention level, and "normal" only if gap − 0.75/width is still above it; otherwise
the result is "too close to call at this resolution". "Touching" is only reported when
the gap is under 1 px. Getting a verdict needs the triangle ≳ 100 px wide (dial filling
the frame), where 1 px ≈ 0.01.

## Field set run (2026-09-27, alpha51 → alpha52)
Corpus: `datasets/126710BLNR` manifest fetched from Imgur (31/35 sources, 252 images;
8 marketplace genuine candidates, 22 labelled replicas). App analysis code run on desktop
OpenCV 4.9 at the phone's 1600 px working size.

Run 1 (alpha51, first photo of 8 genuine + 12 replica watches): 14/20 found a triangle.
7 correct, 1 correctly low-confidence, 6 wrong — all false alarms:
round hour dot accepted as the triangle (2), GMT arrowhead merged with the triangle (1),
tick frame thrown off beside a GMT hand on 25–34 px triangles (3, false STRONG).

alpha52 fixes: triangle shape check (blob area / corner-triangle area 0.80–1.25),
MIN_TRIANGLE_PX = 40, HandIntrusion wedge check (±14°, 0.66–0.92R, >3% marker-bright).
Re-run of the same 20: all 6 errors now "too small" or rejected; the 7 correct unchanged.

Run 2 (alpha52, 20 new photos): no false alarms. Verdicts given on 7:
genuine 1TDYtpN gap 0.103 clear; Clean bpdi5xV gap **0.040 STRONG** (base visibly almost
touching the ticks, 71 px triangle); other Clean/VSF 0.084–0.129. One false hand flag
(crown logo inside the wedge) fixed by moving the wedge's inner edge from 0.55R to 0.66R.

Real genuine outer-edge gaps now: 0.084 (user), 0.103, 0.103, 0.106 (r/Watchexchange).
Replica gaps where measurable: 0.040, 0.067, 0.084, 0.086–0.088, 0.097–0.129. A small gap
is a flaw on some replicas, not a general tell; 0.070 catches the clear cases.

## Community-feedback comparison (2026-09-27, alpha53)
Three r/RepTimeQC threads read by the user (screenshots), compared with the app:

| watch | community | app |
|---|---|---|
| VSF p3hHVMB | mod: "slight CW cant" (owner thought CCW) | +1.63/+1.68 deg on two photos = CW; top edge level; whole photo tilted ~0.9 deg CCW explains the owner's impression. **Agrees with mod.** |
| VSF 7s6PyXJ | "mini clockwise tilt, smidge left of centre"; 6 bar well left (RL) | alpha52: -1.3/-1.8 deg (CCW) **wrong**: the left side's dark bevel made the fit take the inner line, bending the outline (apex 47.9-49.1 deg vs 43.5-44.9 on correct fits). alpha53 rejects fits whose apex is >2 deg from 44.3 deg; falls back to low confidence reading +2.3/+3.3 deg (CW). **Now agrees, low confidence.** |
| Clean bpdi5xV | RL for unusual dial font; index alignment "looks good"; gap not mentioned | gap 0.040 STRONG. Side-by-side with genuine at the same scale shows a visibly shallower band (~half) and longer ticks. Community and app both find the dial off, for different visible reasons. |
