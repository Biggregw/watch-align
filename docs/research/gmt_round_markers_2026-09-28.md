# Round hour markers (2026-09-28, alpha61)

The eight round markers (1, 2, 4, 5, 7, 8, 10, 11) are now checked. With the 12 triangle
and the 6 and 9 batons, that covers every hour marker. The 3 position is the date window.

## What is measured

`GmtRoundMarkerAnalyzer` measures each marker in the original image, with no rotation or
resampling.

1. **Placing.** Each marker is first searched for widely, around its place on the fitted
   dial ellipse (starting from the 12's 60 tick). When at least four are found, an affine
   map from the master layout (plus the 60 tick) to those markers is fitted, dropping bad
   points. Every marker is then searched for again, closely, where that map puts it.
   - On steep photos the dial-edge ellipse can sit several marker radii off. On 3KSuGhC
     image_02, 6 of 8 markers were missed; with the affine map, all 8 were found.
2. **Outline.** The search starts from the bright area nearest the placed position, then
   traces the outermost falling edge along 72 rays. A circle is fitted to these edge points:
   - a least-median centre first, so that a hand lying along one side can't drag the fit;
   - then trimmed least squares, dropping rays more than 3 robust SDs off the circle.
   - This is repeated from the fitted centre.
3. **Reference.** The reference is the inner ends of the minute ticks one minute either side
   (for the 1: the 04 and 06 ticks), found by the 12's tick search, which now works from any
   angle.
   - The hour tick is the one nearest where the marker belongs (the affine placement), not
     the one nearest the marker. Otherwise a marker moved 0.3 of its width anticlockwise was
     measured against the next minute and read +0.19.
   - Tick inner ends are never taken inside the marker. On one replica, the walk inward from
     a tick ran along a marker rim close to the track. That tilted the tick line and read
     offsets of +0.22 on the 10 and 11 of gpZWOfy, and on seven other replica photos.
4. **Numbers.** Measurements are made with the dial's ellipse squash undone, so a tilted
   photo doesn't read as an offset. There are three:
   - **offset**: the marker centre's sideways distance from the midpoint of the two ticks, as
     a fraction of its diameter. + means clockwise.
   - **inset**: the centre-to-tick-line distance over the tick spacing. This doesn't depend
     on which edge was traced.
   - **size**: the marker's diameter against the median of the confidently traced round
     markers on the same dial. It is only used when that median is itself 24 px or more.

## Gates

A marker is not judged when any of these apply:

- **Size.** It is under 24 px across.
- **Photo angle.** The photo is rated too angled or unrateable. No concern survives this,
  unlike the batons: on angled genuine photos the affine model still leaves offsets up to
  0.23.
- **Fit quality.** Any of the following:

  | measure | limit |
  |---|---|
  | edge contrast | under 80 grey levels |
  | tick score | under 45 |
  | rays off the circle | more than 25% |
  | inset | outside 0.40–0.75 |
  | disagreement between the tick at the marker and the midpoint of the ticks either side | more than 0.04 |
  | 12 marker | not found |

  Every genuine offset past 0.10 in the first run failed one of these. The causes were a
  bracelet or caseback mistaken for a dial, a blurred or unlit dial, or a hand over the
  ticks. Clean genuine readings have contrast of 120 or more, a tick score of 57 or more,
  an inset of 0.52–0.67 and a disagreement under 0.02.
- **Resize check.** It is measured again at 94% and 88%. It is withheld if the offset moves
  more than about a pixel across the level, or the radius moves more than 1.5 px.
- **Hand check.** Either of these:
  - more than 3.5% of a ring just outside the marker (0.12–0.5 diameters beyond its edge,
    dial side of the tick line) is marker-bright;
  - more than 1% of the ring out to 2.2 radii is red or blue, above the dial's own tint.
    This is the GMT hand. Its arrowhead can cover a marker while its bright parts barely
    leave the outline. On the official render, the arrow over the 5 was read as a clear
    marker. On Phillips 147798, the arrowhead over the 4 read as a marker 12% too large.

  The 12's ±14° wedge isn't used here. Around a round marker it reaches a marker's width
  either side, and on 3KSuGhC image_02 it caught hands that were near 4 of the 8 markers,
  not over them.

## Levels

These are set outside the genuine spread, with pixel minimums.

| | CHECK | STRONG | genuine judged maximum |
|---|---:|---:|---:|
| offset (fraction of diameter) | 0.15 and ≥ 2 px | 0.25 and ≥ 4 px | 0.103 (Phillips Pepsi 151477, the 4) |
| size against the dial median | ±0.12 and ≥ 2 px | not used | 0.051 |

## Corpus (262 photos, phone-like loading)

| | genuine | replica |
|---|---:|---:|
| photos with a dial fitted (of 262) | 49 | 174 |
| round markers judged clear | 75 | 176 |
| round markers flagged | 0 | 0 |
| clear-marker offset, median / 99th percentile | 0.018 / 0.096 | 0.020 / 0.075 |

On dial photos (12 found, usable angle) there were 720 marker readings. 251 were judged
clear. The rest were not judged, for these reasons:

| reason | count |
|---|---:|
| not found (mostly non-dial photos where a "12" was found, or hands) | 174 |
| a hand | 110 |
| too small | 77 |
| outline not on a circle | 64 |
| resize check | 32 |
| other low confidence | 12 |

**No replica in this corpus has a round marker outside the genuine spread.** The largest
replica offset is 0.08. Round-marker placement isn't a tell on these watches, so the check
reports them as centred rather than inventing a concern. The first uncorrected run
"flagged" 10/11 offsets of about 0.2 on several VSF watches, and every one of them was the
tick-line fault described above.

The 12, 6, 9 and photo-angle results are identical to alpha60 on all 262 photos, in every
column. The tick search now reads pixels from a byte array (`GmtTwelveLandmarkAnalyzer.Px`)
instead of `Mat.get`, and gives the same values.

## Sensitivity (synthetic)

`tools/desktop-harness/drivers/RoundShift.java` moves one marker on a real photo and
re-runs the analysis. The measured change follows the applied change:

| photo | move | read |
|---|---|---|
| official render, 7 | −0.30 | −0.28 (STRONG) |
| official render, 11 | +0.40 | +0.43 (STRONG) |
| official render, 8 | −0.45 | −0.42 (STRONG) |
| official render, 10 | +0.25 | +0.29 (STRONG) |
| 1TDYtpN image_01, 4 | +0.18, from −0.045 | +0.14 (CLEAR, under 0.15) |
| 1TDYtpN image_01, 4 | −0.25 | −0.29 (STRONG) |
| Phillips 151477, 10 | +0.18 | +0.16 (CHECK) |
| official render, 10 | shrunk to 0.85 | size 0.85 (CHECK) |

## Not done

- Perspective beyond affine: on steep photos the round markers are withheld, not corrected.
- Radial position (inset) is reported, not judged. It still varies with the angle
  (0.54–0.64 on 3KSuGhC image_02), because the dial ellipse is poor on steep photos.
- The edge-to-track gap is reported only. Which surround edge is traced (the polished inner
  lip or the outer bevel) varies with lighting.
