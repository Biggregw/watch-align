# GMT dial check fix list — 2026-09-27 (alpha55)

This note covers the whole fix list agreed after alpha54. The code under test is the
app's own analysis, run on desktop OpenCV 4.9 through `tools/desktop-harness`.

The regression set has 106 photos:

- every genuine corpus photo
- 11 official renders
- the user's dealer photo, Bruce Wayne, Pepsi, Root Beer and GRNR field photos
- 45 replica photos from the earlier field runs

It is compared against main plus the Hough speed-up alone (item 7), so that every other
change is isolated.

## Result on the regression set

| | before (main) | alpha55 |
|---|---:|---:|
| genuine photos with any flag | 3 | **0** |
| replica photos with any flag | 8 | 8 |
| photos whose 12 result changed | — | 9 of 106 |

The 9 changes, and nothing else:

- **Five photos now measured.** The shape checks used to reject these, and item 1 now
  recovers them:
  - Bruce Wayne GRNR: "straight and centred", rotation −0.5° (CCW). That matches the
    community's "tiniest CCW leaning". The gap reads 0.072, which is too close to call
    at 53 px.
  - 7s6PyXJ image_01: rotation +0.9° (CW), clear. The community said "mini clockwise
    tilt". The reading is CW but under the visible threshold.
  - 7s6PyXJ image_02: rotation +1.0° (CW), CHECK.
  - 6I00d8w: rotation −1.6°, CHECK.
  - genuine 3KSuGhC image_04: hand at 12, not judged, as before.
- **Four false flags removed** (item 6): genuine e99gXKb image_08 (+13.5°) and 1TDYtpN
  image_04/05 (−11°, −26°), and replica 7s6PyXJ image_03 (+32°). The triangle or tick
  frame was misdetected on steeply angled photos and flagged as a rotation.

## 1. Shadowed triangle edges

**What changed.** `TriangleEdgeRefiner` first fits with the calibrated edge level:
halfway between the dial and the brightest part of the marker. Only when that fit fails
the apex-angle or squareness check does it retry with a "band" level, taken from the
outermost bright band of each profile. That is the brightest value within 0.02R inside
the point where the profile first rises above a quarter of the contrast.

A shadowed surround is dimmer than the lume. With the band level it sets its own
half-level, so the edge stays on the outside of the surround instead of dropping onto
the lume.

**Why only as a fallback.** Using the band level everywhere moved normally lit edges
slightly outward. The user's genuine dealer photo went from a gap of 0.084 to 0.076,
too close to the 0.070 limit. As a fallback, no previously measured photo changes.

## 2. "Rotated" vs "skewed" vs "off-centre"

The summary now says which of these it measured:

- **rotated**: the point and the top edge are turned the same way, and the top edge is
  tilted by at least 0.75°.
- **skewed**: the point leans but the top edge is level (within 0.75°). For
  r/RepTimeQC p3hHVMB it now reads "skewed: the point leans clockwise by about 1.6° but
  the top edge is level".
- **off-centre**: the spacing to the 59 and 01 ticks differs by at least 0.06, with no
  lean.

## 3. Thin hands at 12

The existing wedge check needs more than 3% of the area to be bright. A seconds hand
covers less than that. The new check splits the same wedge into 0.5° bins, stopping one
pixel short of the minute-track circle so that no tick is ever included.

A hand is a run of at most 4° of mostly bright bins (at least 50% of samples) with dark
bins (under 20%) on both sides. A base touching the minute track makes every bin across
the triangle bright, which is much wider than 4°, so it is not mistaken for a hand
(unit test).

On the regression set this caused no new hand calls. Synthetic seconds hands through
and beside the triangle are detected.

## 4. Close-ups and "not judged" style

The result screen and the export card now show close-ups of the 12, and of the 6 when it
is measured. Each has a status strip:

- NOTHING FLAGGED
- WORTH A LOOK
- CHECK CLOSELY
- NOTHING FLAGGED · GAP NOT CALLED
- NOT JUDGED: reason

When a marker is not judged (too small, or a hand is present), its outline is drawn grey
and dashed with no numbers, and the close-up border is dashed.

## 5. The 6 baton

`GmtSixLandmarkAnalyzer` turns the image 180° about the dial centre. That is a point
reflection, so there is no resampling. The 12 marker's tick finder and outer-edge line
fitting are then reused unchanged.

Measured:

- **centring**: the offset of the baton's outer end from the midpoint of the 29 and 31
  tick ends, as a fraction of the baton width. Positive means towards 29, the viewer's
  right.
- **rotation**: the baton axis against the square to the 29–31 chord.
- **gap**: to the 29–31 tick line. This is reported only; it doesn't produce a flag.

The reference is local because there is no printed 30 tick; the SWISS MADE coronet sits
there. An earlier version measured from the dial centre. It read 3.9° on a genuine photo
whose centre fit was off, while the 12 on the same photo read −2.5°.

The baton's end is taken as square to its long sides. The end is only about 0.7 baton
widths of usable edge, and its own fitted direction was ±5° noisy.

Guards:

- The 6 must lie within 8° of directly opposite the measured 12. Otherwise, on a turned
  photo, the "bottom" baton is the 3 or 9.
- With no 12 found, the 6 is low confidence.
- A baton narrower than 20 px isn't judged.
- The hand checks (wedge and thin line) apply to the 6 too.

Genuine photos where the 6 was measured, as centring / rotation:

| photo | centring | rotation |
|---|---:|---:|
| user dealer photo | 0.000 | −0.04° |
| 3KSuGhC 00 | −0.003 | −0.59° |
| 3KSuGhC 02 (angled) | +0.075 | +1.79° |
| 3KSuGhC 04 | 0.000 | +0.24° |
| 1TDYtpN 01 | +0.013 | +1.19° |

Provisional levels, just outside that spread:

- centring: CHECK at 0.10, STRONG at 0.20 of the baton width
- rotation: CHECK at 2.0°, STRONG at 3.5°

**Community check.** r/RepTimeQC 7s6PyXJ said the "6 bar well left". The app reads
−0.21 (left). The same watch's duplicate photo, oVwWMrC, reads −0.15 but its baton is
below the size gate. The close-up shows the baton's right edge roughly over the
coronet. Other replicas read within ±0.08. The official renders have the seconds hand
over the 6, so the baton isn't found on them, which is correct.

## 6. Implausible rotations

A factory marker is never turned by more than a few degrees. At 12, a reading over 8°
(and at 6, over 8° or a centring over 0.6) now means the landmarks were misdetected, not
that there is a rotation. These readings came from steeply angled photos. They were the
only false flags on genuine photos.

## 7. Speed: dial seed Hough

On some photos `HoughCircles` with a low accumulator threshold took minutes at the
1600 px working size. The CLAHE fallback on genuine e99gXKb image_01 took about 2 minutes
at 800 px and about 10 s at 480 px.

The circle proposals are now found at 480 px or less. Every proposal is still scored and
its radius fitted at full resolution, and the dial-edge fit then refines the centre. The
baseline for this note includes this change, because main was too slow to run over the
whole set. On the 7 photos main finished, 6 matched exactly. The seventh went from "12 not
found" to a low-confidence 12 reading; neither gives a verdict.

The GMT path also no longer runs the non-GMT extended analysis, whose result was never
shown for GMT models.

## 8. Gap limit review

These are genuine gap readings on the outer-edge definition, stable fits only:

- renders: 0.087–0.095
- real photos: 0.081 (user dealer), 0.083, 0.090, 0.099, 0.102, 0.107

The lowest genuine reading is 0.081, which leaves 0.011 above the 0.070 limit. Raising
the limit to 0.075 (the idea in the handoff) would leave 0.006, about half a pixel on a
70 px triangle. **The limit stays at 0.070.** The reference range quoted in the summary
is now "about 0.08–0.11".

## 9. Dead code

- Removed: `SafePerspectiveGmtOverlayV2`. Its one live helper moved to `DialEdgeFitter`.
- Removed: `SafePerspectiveGmtOverlay`, which was unused.
- Kept: the older WatchAlignCore versions V7–V12, which are still on the non-GMT path.
