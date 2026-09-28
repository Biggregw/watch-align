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
   collects falling edges along 72 rays at two levels: half-way, and 30% of the way from the
   dial to the surround. With light from one side, the shadowed half of the surround sits
   below the half level, so the lower level is needed to find its outer edge there. Without
   it, a third of the rays on sharp studio photos traced the lume instead. A circle is fitted to these edge points:
   - a least-median centre first, so that a hand lying along one side can't drag the fit;
   - then trimmed least squares, dropping rays more than 3 robust SDs off the circle.
   - Each ray then takes the edge candidate nearest that circle, and the circle is fitted
     again. This is repeated from the fitted centre.
   - The rays-off-the-circle gate counts only gross misses: no edge, or more than 2 px and 8%
     of the radius off the circle.
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
     markers on the same dial. It is only compared when that median is 36 px or more. On
     smaller markers the surround is only about 3 px wide, and the fit takes the lume edge on
     some markers and the outer edge on others. That read as a 12–18% size difference on
     three photos of one replica (rep_cplus_wEYZOyK images 00, 02 and 03) and on
     rep_vsf_gpZWOfy image_02.

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
| size against the dial median (median ≥ 36 px) | ±0.12 and ≥ 2 px | not used | 0.072 |

## Corpus (280 photos, phone-like loading)

This is the 262-photo regression set, plus 18 genuine photos the user supplied on
2026-09-28:

- 12 Watches of Switzerland CPO studio photos: BLNR, BLRO and 126711CHNR;
- 6 official 2026 renders: 126710GRNR, 126711CHNR, 126713GRNR, 126715CHNR, 126718GRNR and
  126720VTNR.

These are all black-dial GMT-Master IIs with the same marker layout.

| | genuine | replica |
|---|---:|---:|
| photos with a dial fitted | 67 | 174 |
| round markers judged clear | 181 | 200 |
| round markers flagged | 0 | 0 |
| clear-marker offset, median / 99th percentile / maximum | 0.013 / 0.074 / 0.096 | 0.022 / 0.076 / 0.080 |

The 18 new genuine photos give 106 clear round markers, with a largest offset of 0.041 and
a largest size difference of 0.054. They are sharp and square-on.

**No replica in this corpus has a round marker outside the genuine spread.** The largest
replica offset is 0.08. Round-marker placement isn't a tell on these watches, so the check
reports them as centred rather than inventing a concern. The first uncorrected run
"flagged" 10/11 offsets of about 0.2 on several VSF watches, and every one of them was the
tick-line fault described above. The first size flags were all the lume-edge versus
outer-edge mix-up at small sizes (see size above).

### Also found with the new photos

- **The 6 and 9 were "not found" on clean photos.** The baton finder required the lume
  outline to fill 80% of its bounding rectangle. Clean studio photos and the renders read
  0.64–0.78 (anti-aliased, slightly rounded ends). The limit is now 0.60; a surround-only
  ring reads well under 0.5.
  - Genuine 6 clear: 6 → 11. Genuine 9 unchanged at 17. No genuine flags.
  - On the official renders the 6 is still not found, correctly: the seconds hand lies
    along it.
- **A low-confidence baton no longer keeps a rotation concern.** The change above let a
  minute hand lying along the 9 of rep_vsf_gpZWOfy image_01 through, and it read +7.7°.
  Only a clear offset now survives low confidence.
- **The 12 gap on all 18 new genuine photos reads 0.089–0.104**, all clear. That supports
  the 0.070 attention level.
- **One genuine photo still gets a 12 alignment CHECK:** WOS 406107958490, a 126711CHNR.
  The triangle reads −1.1° with the top edge at −1.0°. It is stable under resizing and the
  photo angle is rated good. It is the first genuine photo past the 1.0° level. It isn't
  changed here, because a single case isn't enough to move a level.

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

## Reporting every marker

The summary now opens with an "All markers" line covering every hour position, so none can
go unmentioned, for example:

"12 OK · 1 OK · 2 hand in the way · 3 date window (not a marker) · 4 OK … · 6 not found …"

Round markers that weren't found are drawn on the overlay as grey dashed circles where they
should be. When the 12 can't be checked, the bottom line still names the markers that were
clear.
