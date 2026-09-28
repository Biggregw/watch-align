# Photo-angle rating: resize check (2026-09-28, alpha60)

## Trigger

The user's ARF 126710BLRO photo was rated "too angled" on the phone but "slight angle"
on the desktop harness. The two copies of the photo differed by less than one grey level.
Of six near-identical loads, one gave RETAKE. The gap stayed at 0.052–0.056 across all
six loads.

The rating matters because RETAKE does three things:
- it withholds the 6 and 9;
- it makes the bottom line hedge;
- it flips the gap-direction cue, which turns "small, may be the angle" (CHECK) into
  "clearly small, the angle would make it look bigger" (STRONG).

## Change

The angle rating and the gap-direction cue both come from how wide the rehaut ring looks
around the dial. Both are now also measured on the photo's 94% and 88% copies.

- **Angle rating:** the median of the three ratings, ordered GOOD < CORRECTABLE < RETAKE,
  is used. UNASSESSABLE copies are ignored.
- **Gap direction:** the cue is used only when all three copies agree; otherwise it
  becomes UNKNOWN. With UNKNOWN the gap wording is "small, check by eye", with no claim
  about direction.
- **Report:** the Perspective line notes when the three ratings differed.

Also in this build: a 6 or 9 withheld only because the photo is too angled now says so
("not judged: the photo angle is too steep to clear the baton from this photo").

## Results

**The ARF photo.** All six loads now read "slight angle" with the gap flagged CHECK,
and the 6 is clear on every load.

**The corpus (262 photos).** The angle rating changed on 36 photos: 9 moved to "too
angled" and 9 moved away from it, so the check has no bias in either direction.

| | alpha59 | alpha60 |
|---|---:|---:|
| genuine photos flagged | 0 | 0 |
| replica photos flagged | 9 | 10 |

- **The new replica flag** is oVwWMrC image_01. It is no longer rated "too angled",
  and its 12 now reads rotated (CHECK).
- **bpdi5xV image_00** goes from gap STRONG to CHECK, because its gap-direction cue
  disagreed across the copies. It is still flagged.
- **Coverage lost on genuine photos:** two Phillips Pepsi photos went from "slight
  angle" to "too angled", so they lose their clear 6 or 9 verdict.
