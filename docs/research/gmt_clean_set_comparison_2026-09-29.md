# Clean genuine vs clean replica (2026-09-29, alpha61, harness = app code)

40 clean genuine photos (`gmt_genuine_clean_set_2026-09-29.csv` plus the 5 Bob's Watches
photos) and 19 clean replica photos (`gmt_replica_clean_set_2026-09-29.csv`). All are
face-on with no hand over the 12/6/9 (3 on the Sprite), checked by eye.

## What was judged

| | genuine (40) | replica (19) |
|---|---|---|
| 12 gap judged / flagged | 32 / 0 | 8 / 2 |
| 12 alignment judged / flagged | 24 / 2 | 7 / 2 |
| 6 judged / flagged | 25 / 0 | 7 / 0 |
| 3 or 9 baton judged / flagged | 29 / 0 | 8 / 1 |
| round markers judged / flagged | 221 / 1 | 107 / 1 |
| no readable dial | 0 | 0 |

## 12 gap: the only separating measure

Every genuine reading is 0.080 or more (0.080-0.155). 7 of the 19 replicas read below that:
0.028, 0.038, 0.065, 0.068, 0.070, 0.073, 0.074. Only the two VSF Sprites (0.028, 0.038)
are flagged. The five at 0.065-0.074 are withheld as "too close to 0.070 at this resolution":
their triangles are small enough that one pixel spans the gap between the reading and the
0.070 level. That is the fail-closed design working, not a bug. A closer photo of those
watches would be needed to judge them. No level was changed.

## Not separating on these photos

- 12 alignment: genuine -1.17 to +1.49 deg, replica -1.53 to +0.54 deg. Two flags in each.
- 6 centring: genuine -0.074 to +0.077, replica -0.057 to +0.039.
- Side baton centring: genuine -0.040 to +0.059, replica -0.023 to +0.101 (the one flag is
  the VSF Sprite's 3 baton, +0.10).
- Round-marker offset, markers 40 px or larger: genuine median 0.009 / 90th percentile 0.030,
  replica 0.013 / 0.042. Slightly wider on replicas, far below the 0.15 level.

## False flags found

- Genuine Bob's Watches Sprite 182860, the 8: "a different size" (1.21x). The seconds hand
  passes just beside that marker, so this is the hand check missing a hand beside a marker,
  not a real size difference. Not fixed here.
- The 2 genuine 12 alignment CHECKs are the known WOS -1.1 deg readings (borderline).

## Also seen

5 genuine photos that look face-on are rated "retake" and nothing at 12 is measured
(Phillips 151263, 180697, 180859, NY080121_35; WOS 40411271). Not investigated yet.
