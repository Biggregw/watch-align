# 12 off-centre and lean: recheck with 38 more genuine photos (2026-09-28, alpha61)

Two genuine photos from the user's Phillips list got a 12 "worth a look":

- **CH080120/2 (126710BLNR)**: off-centre;
- **Bruce Wayne (126710GRNR)**: a lean.

## Off-centre: it is the triangle, not a misplaced tick

I expected a tick found in the wrong place, because the 59–60 and 60–01 spacings looked
unequal in the close-up. Measured along the 59–01 chord (`tools/desktop-harness/drivers/Twelve.java`),
they are not:

| photo | 60 tick against the chord midpoint | base midpoint against the 60 tick | tip against the 60 tick | width |
|---|---:|---:|---:|---:|
| Phillips CH080120/2 (genuine) | −0.04 px | −3.95 px | −3.0 px | 82 px |
| user's date-4 photo (ONE seller) | +0.02 | +2.70 | +2.87 | 71 |
| 6I00d8w image_02 (replica) | +0.01 | −1.83 | −0.47 | 42 |
| official render (genuine) | −0.01 | −0.88 | −1.69 | 44 |
| WOS 40411116 (genuine) | +0.02 | −0.32 | +0.02 | 66 |

The base and the tip agree, so on the genuine Phillips watch the triangle really does sit
about 4 px (0.05 of its width) to the left of the 60 tick in this photo. That is more than the
user's replica photo, at 2.7 px (0.04).

The spacing asymmetry across 35 stable genuine photos reads, from the largest:

- 0.080;
- then 0.057, 0.048, 0.043, 0.040.

The largest stable replica readings are 0.210 (a misread) and 0.078 (6I00d8w, twice).

**Change:** the CHECK level goes from 0.06 to 0.10 (1.25× the genuine maximum), and STRONG
from 0.12 to 0.15. In this corpus no replica flag depended on the old level: 6I00d8w
images 00 and 02 are already withheld for other reasons. The user's date-4 photo
**loses its off-centre flag**. At 2.7 px it is inside what a genuine watch shows.

### The direction was named the wrong way round

The "spacings" are distances from each base corner to its tick. The base is wider than the
gap between the 59 and 01 ticks, so each corner overhangs its tick, and the triangle is
shifted **towards** the side with the larger spacing. Since alpha57 the summary named the
other side. On the date-4 photo, whose triangle is shifted right towards 01, it said "sits
closer to the 59 tick". The summary now says "sits towards the 01 tick side (to the right of
the 60 tick)".

## Lean with a level top edge

The Bruce Wayne photo reads +1.3° to +2.2° across the resize check, with the top edge at
+0.6°. The resize check kept a concern whenever every scale was turned at least 1.0° the same
way. For a lean with a level top edge, the concern only starts at 2.0° (`SKEW_ONLY_MIN_DEG`),
so every scale now has to clear 2.0°. This photo is now "not judged: the reading changes when
the photo is resized slightly".

## Regression after these changes (303 photos), plus 16 more genuine photos

**Main set.** Relative to the previous run, the only 12 changes are the two genuine photos
above:

- Phillips CH080120/2 goes from CHECK to clear.
- The Bruce Wayne goes from CHECK to not judged.

No replica 12 flag changed. The round-marker change (concentric edges, see
`gmt_round_markers_2026-09-28.md`) takes the round markers judged clear from 275 to 280 on
genuine photos and from 234 to 267 on replicas. Nothing is flagged in either group.

**The user's third list, rows 38–57.** 16 of the 20 photos downloaded: 4 Phillips and
12 WOS CPO. The 4 Prestons images returned 403. On those 16:

- the 12 gap is clear on all 13 that were measured, reading 0.084–0.105;
- round markers: 76 clear, none flagged;
- the 6 and 9 flag nothing;
- one 12 alignment CHECK: WOS 40410003 reads −1.17° with the top edge agreeing. That falls
  in the 1–2° band where genuine and replica readings overlap, and the summary now calls it
  borderline.
