# Round-marker size: compare like edges (2026-09-29, alpha62)

## The false flag

On the clean genuine set (40 photos), one round marker was flagged: the 8 on Bob's Watches
126720VTNR 182860, "a different size from the other round markers" at 1.21x.

The edge candidates on each ray show two rings on every marker: the lume edge at about 25 px
radius and the surround's outer edge at about 30 px. The fit picked the lume on seven markers
and the surround on the 8, whose lume ring is faint (found on about a quarter of the rays).
1.21 is the surround-to-lume ratio, not a size difference.

## The change

`GmtRoundMarkerAnalyzer`:
- the circle fit keeps every falling edge it found on each ray (`Marker.edgePts`);
- `ringRadii`: radii where an edge is found on at least 40% of the rays, each refined to the
  median of the nearest edge per ray;
- `surroundRadius`: the outer ring, known only when a second ring lies 1.12-1.5x inside it
  (the lume). With one ring it can't be told which edge it is, so it is NaN;
- `sizeRatios`: when 4 or more markers have both rings, sizes are compared surround to
  surround and markers with one ring are not size-judged. Otherwise the fitted edges are
  compared as before.

A first version compared the outermost ring whether or not a second ring was found. It
removed the genuine flag but added 8 replica flags at 0.82-0.87x: markers whose surround ring
was faint, so the "outermost ring" was the lume. Requiring both rings removed those.

## Checks

- 319-photo regression: every main-CSV value and every round-marker decision identical.
- Clean sets (40 genuine, 19 replica): the only change is the genuine 182860 8 (CHECK to
  CLEAR). Size comparisons made: 369 to 352.
- Synthetic (`RoundShift`, one marker rescaled on a real photo), before and after identical:

  | photo | marker | scale | reads | decision |
  |---|---|---|---|---|
  | Bob's 182028 | 10 | 0.85 | 0.856 | CHECK |
  | Bob's 182028 | 10 | 1.15 | 1.134 | CHECK |
  | Bob's 182028 | 4 | 1.12 | 1.129 | CHECK |
  | Bob's 182028 | 7 | 0.88 | 0.888 | clear |
  | WOS 40411407 | 10 | 0.85 | 0.859 | CHECK |
  | WOS 40411407 | 10 | 1.15 | 1.142 | CHECK |
  | WOS 40411407 | 4 | 1.12 | 1.120 | clear |

- JVM tests: 213 (three new: a marker fitted on its surround among lume fits, a marker that
  really is larger, a marker with one ring).

## The "retake" photos (not changed)

Five sharp genuine photos are rated "retake" (Phillips 151263, 180697, 180859, NY080121_35;
WOS 40411271). The trigger is the local rehaut check: one side of the rehaut reads about 4-6 px
against 11-22 px elsewhere. Close up, the rehaut really is narrow on that side on the Phillips
photos: the camera is slightly towards the 9 side. So the rating reflects a real, small
sideways tilt, which the steep rehaut exaggerates. Neither the opposite-side widening nor the
dial edge ellipse separates these from genuinely angled photos (edge ellipse: 3-13 deg on these
five, 14 deg on a photo rated good). Deciding how much tilt should block measurements needs a
synthetic tilt test (known tilts applied to good photos, measuring how each reading moves).
