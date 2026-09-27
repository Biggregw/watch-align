# Off-centre 12, 6-baton confidence and the 6 resize check (2026-09-27, alpha57)

The trigger was the user's photo from the "ONE" seller (date 4, 12 hands at about 10:10).
On that photo alpha56 said the 12 was "straight and centred … even spacing either side",
but the spacing it printed was 0.08 on the 59 side against 0.16 on the 01 side. It also
said the 6 was "could not be judged reliably" without saying why.

## 1. Off-centre triangle at 12

**Why it was missed.** The 59/01 spacing asymmetry was only ever used to back up a
rotation. A triangle shifted sideways with no rotation was never flagged, and the
summary still printed "even spacing".

**What the corpus shows.** These are stable 12 readings with no hand and not too small.
The asymmetry is the 01-side spacing minus the 59-side spacing, as a fraction of the
triangle width.

| | asymmetry |
|---|---|
| genuine (official render, 3KSuGhC, e99gXKb, vmbUDwy, 1TDYtpN) | −0.021 to +0.040 (at most 2.0 px) |
| replicas | mostly within ±0.04; HCz9EAj 0.050, oVwWMrC 0.052, 6I00d8w −0.078 (twice) and −0.210 |
| user's photo | +0.072 (0.084 vs 0.156, 5.1 px) |

**The rule** (`GmtHumanQcMath.assessOffCentre`). It applies only when the rotation check
found nothing, the 12 landmarks are high confidence and the photo angle is usable. It
gives:

- **CHECK** at an asymmetry of 0.06 or more (1.5× the genuine maximum) and at least
  2 px.
- **STRONG** at 0.12 or more and at least 3 px.
- no verdict when the resize-check scales disagree: every scale must be past the level,
  on the same side.

The summary now says "possibly off-centre: the triangle sits closer to the 59 tick than
the 01 tick", and the bottom line lists "the 12 marker position (off-centre)".

"Even spacing" is now used only below a 0.025 difference. From 0.025 to 0.06 the summary
says "slightly uneven, within what genuine photos show".

**Corpus result.** No genuine photo changed. One replica photo gains an off-centre CHECK:
6I00d8w image_02, at −0.078 (3.3 px). The same watch's image_00 was already flagged for
rotation. The user's photo now reads "2 things to check: the gap at 12 and the 12 marker
position (off-centre)".

## 2. Why the 6 was "low confidence"

Every low-confidence 6 now carries its reason into the report and summary. Of the 53
batons found in the corpus, 26 were low confidence, and all 26 came from the edge fit or
orientation, never from the minute ticks:

| reason | photos |
|---|---:|
| the two long sides fitted more than 2° from parallel | 11 |
| a long side couldn't be traced | 8 |
| the outer end couldn't be traced | 4 |
| no 12 found, so the orientation is unknown | 3 |

14 of these batons are under 20 px wide, and they are already reported as "too small".

**The fix.** The 12's fallback now applies to the 6 as well. When the calibrated edge
level fails the shape check, the fit is retried with the outer-band level. Some examples
of the long sides' angle apart:

| photo | calibrated level | band level |
|---|---:|---:|
| user photo (date 4) | 4.2° | 0.3° |
| GKJDidL | 3.4° | 0.1° |
| 1TDYtpN (genuine) | 1.6° | 0.5° (already stable before) |

Replica batons with a confident reading went from 24 to 30. Genuine photos didn't change.

On the user's photo, alpha56's summary said the seconds hand beside the baton was the
likely cause. The band-level result shows that one long side was fitted on the lume
instead of the surround. That is the same shadowed-surround problem the 12 had.

## 3. The resize check on the 6

This works the same way as the 12. The baton is measured again at 94% and 88%. The
reading is withheld when the offset or rotation moves by more than about a pixel and the
movement crosses the check level (0.10 of the width, or 2°), or when a different edge is
found. A concern that every scale agrees on stays CHECK.

The report gains a "6 resize check" line and a "6 fit detail" line.

## Corpus totals (252 photos, phone-like loading)

| | alpha56 | alpha57 |
|---|---:|---:|
| genuine photos flagged | 0 | 0 |
| replica photos flagged | 15 | 17 |
| confident 6 readings on replicas | 24 | 30 |
