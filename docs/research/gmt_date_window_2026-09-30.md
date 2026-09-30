# Date window as a QC check: first measurement (2026-09-30)

Research only. The app is unchanged. This follows the AGENTS.md rule that a new check starts as silent measurement.

## What was measured

`tools/desktop-harness/drivers/DateWin.java` runs the app's own analysis on each photo: dial fit, 12, date side and round markers. It then measures the date window at the date side (3, or 9 on the Sprite) in a local frame:

- **u** runs along the dial edge (clockwise).
- **v** runs radially.

It measures three things:

- **The minute ticks at the date side** (14/15/16 or 44/45/46), with the 12's tick finder turned a quarter as for the 3 and 9 batons.
- **The lens.** This is the bright region at the date position, about 0.5–0.9 R. What the camera sees is the cyclops lens with the magnified window, not the window itself.
- **The aperture and the numeral.** The aperture is the white date disc inside the lens. The numeral is the dark ink surrounded by the disc.

The measurements are:
- aperture centre against the 15 (or 45) tick;
- numeral centring in the aperture, along u and v;
- numeral height;
- aperture size.

Each is repeated at 94% and 88% (the resize check). `results/datewin_examples.png` shows the detections on four photos: lens in red, aperture in green, numeral in blue.

Results are in `results/datewin_2026-09-30.csv` and `results/datewin_report_2026-09-30.txt`. `datewin_report.py` reproduces them.

## Results

The input was 153 dial photos where the 12 was found: 109 genuine and replica corpus photos, Bob's Watches photos and the clean set.

- **Coverage:** 104 had the date side determined; the other 49 are "date side not determined". Of the 104, tick, aperture and numeral were all found on 96: 53 genuine and 43 replica.
- **Stability:** the median change across the resize check is small, 0.005–0.013. But one reading in ten moves by 0.05–0.12, which is a large share of any plausible check level.
- **Genuine spread:** wide on every measurement.

| measurement | genuine range | replicas outside the genuine range |
|---|---|---|
| aperture centre vs tick (aperture widths) | −0.19 to +0.33 | 4 of 38 |
| numeral across the aperture | −0.19 to +0.22 | 1 of 39 |
| numeral along the aperture | −0.15 to +0.11 | 5 of 36 |
| numeral height (dial radii) | 0.08 to 0.32 | 3 of 38 |
| aperture size (dial radii) | 0.17 to 0.43 | 4 to 7 of 38 |

- **Camera angle:** on genuine photos, the distance from the typical value grows with the marker-layout tilt; correlations are 0.2 to 0.57. The lens sits on the crystal, well above the dial, so any tilt shifts it and the magnified numeral against the ticks.
- **Near-frontal genuine photos** (tilt at most 6°, n about 23) are tighter:
  - numeral along the aperture: sd 0.016 over −0.07 to +0.02;
  - aperture centring and numeral centring across the aperture: still ±0.13 to 0.19.

  Only confidently straight-on photos can be judged at all, and none of the replica photos met that bar, so there is nothing to compare against yet.

## Conclusion

The date window, measured this way, does **not** yet separate genuine from replica well enough to be a check.

The reason is the lens on the crystal. Parallax and the magnification make its readings depend on the camera angle far more than the 12's do, and the genuine spread covers almost all the replica readings.

A check built now would either flag genuine watches or catch almost nothing, so none was built.

The one lead is the numeral's radial centring inside the aperture on photos known to be straight on. On genuine photos it is tight (sd 0.016). To test it, we need near-frontal replica photos, ideally ones with a known cyclops or date misalignment.

**Next step, if pursued:** collect about 20 straight-on replica date-window photos, rerun `DateWin`, and compare that one measurement. Nothing else from this study should go into the app.
