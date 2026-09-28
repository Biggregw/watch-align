# Full-resolution dial crop, and the 12 rotation level (2026-09-28, alpha61)

## Full-resolution dial crop

The app decodes a 1600 px preview. When the dial is small in the frame, the markers are too
small to judge: round markers under about 40 px, and batons under 20 px wide.
`GmtDialCrop` now works like this:

1. Find the dial on the preview.
2. Re-read just the dial from the original photo, using `FullResSource`. On the phone this is
   a `BitmapRegionDecoder`, so the whole photo is never decoded at full size.
3. Scale the crop to a dial radius of at most 380 px, and analyse it.
4. Map every result back onto the preview for the overlay. Close-ups come from the crop
   itself.

The crop is only made when both of these hold:

- the preview dial radius is under 230 px;
- the original gives at least 1.25× the preview's pixels.

A hand-aligned dial is never cropped, because its taps are in preview coordinates.

### Why it is limited to small dials

A first version cropped every dial up to a radius of 560 px. On the Watches of Switzerland
CPO studio photos, whose preview radius is 252 px, it made things worse:

- Three genuine 12 markers went to STRONG rotation. They read −1.1° to −1.7°, against −0.9°
  to −1.1° on the preview.
- The round markers were fitted on the lume instead of the surround.
- Rose-gold surrounds (126711CHNR) read as a red GMT hand. That hand check now starts outside
  the whole surround.
- Genuine round markers judged clear went from 194 to 161.

The checks were tuned on dials of radius 150–420 px. So the crop only brings small dials up
into that range; it doesn't push well-resolved ones past it.

### Test: the same photos, small in the frame

The 12 WOS CPO photos (3000 px) were placed on a 5000 px canvas, which gives a preview dial
radius of about 150 px:

| | preview only | with crop |
|---|---:|---:|
| 12 gap judged | 1 | 11 |
| 12 alignment judged | 2 | 10 |
| 6 judged | 0 | 7 |
| 9 judged | 0 | 9 |
| round markers clear | 35 | 48 |

With the crop, the 12 rotation readings match the ones on the unpadded photos to within
about 0.1° on most watches (for example −0.11 against −0.11 and −0.36 against −0.40).

## 12 rotation: genuine and replica readings overlap

Genuine photos now include the WOS CPO set, and their 12 rotations reach −1.1° to −1.6°. In
each case the top edge agrees, the photo angle is rated good, and the reading is stable
under resizing. Most read anticlockwise. The replica rotation flags in the corpus read
1.0–1.7°. One of them is p3hHVMB, where a r/RepTimeQC moderator also saw the cant. So below
about 2° the two groups overlap.

**Change:** STRONG now needs 2° as well as a visible rise
(`GmtHumanQcMath.GENUINE_ROTATION_SEEN_DEG`). Before, 1.5 px of rise alone was enough, and
the rise grows with resolution. CHECK stays at 1.0°, and the summary adds: "Borderline:
genuine watches photographed so far read up to about 1.6°."

The CHECK level is **not** raised to clear the genuine photos. That would drop four of the
five replica flags, including the one a person independently confirmed. The honest answer
is that the 12 rotation can't separate genuine from replica between 1° and 2°.

## Regression with the crop on, plus 20 more genuine Phillips photos (300 photos)

The user supplied 20 more genuine photos from Phillips auctions: 126710BLNR, BLRO and GRNR
(Bruce Wayne); 126711CHNR; 126715CHNR; 126718GRNR; and 126720VTNR. They are listed in
`gmt_genuine_more_20_2026-09-28.csv`.

| | genuine (87 photos) | replica (174) |
|---|---:|---:|
| round markers clear / flagged | 275 / 0 | 234 / 0 |
| 6 and 9 flagged | 0 | 2 / 0 (unchanged) |
| 12 gap flagged | 0 | unchanged |

On the original photo set, compared with the run before the crop, only the official 2026
renders changed. These are 2160 px PNGs whose preview dial radius is 174 px, so the crop
applies to them:

- the 9 is now measured and clear on five of them;
- the 12 alignment is withheld on four of them, because the rotation reading moves between
  +0.2° and +1.2° across the resize check. It was clear before.

The 20 new genuine photos include two 12 alignment CHECKs. These are the open question for
the 12:

- **Phillips CH080120/2 (126710BLNR)** reads **off-centre**: 59-side spacing 0.197 against
  01-side 0.117, a difference of 0.080 (6.5 px). It is stable under resizing. The genuine
  maximum had been 0.04 and the CHECK level is 0.06. The user's replica photo (ONE seller,
  date 4) read 0.072, and 6I00d8w image_02 read 0.078. The genuine and replica ranges now
  overlap here too.
- **Phillips Perpetual, Bruce Wayne 126710GRNR** reads a **skew** of +2.2° with a level top
  edge. It reads +1.3° to +2.2° across the resize check, so it is kept as CHECK only because
  every scale leans the same way. It is past the 2.0° skew-only level.

Neither level is changed on this evidence. Both need more genuine photos, and the replica
flags they would drop include ones people have confirmed.

**User review (2026-09-28):** after looking at the full-resolution crop, the user judged the
−1.1° turn on the genuine WOS Root Beer (406107958490) to be a real, visible flaw, not a
misreading. A "worth a look" there is therefore correct. The app flags things to inspect,
and genuine watches can have small flaws too. This supports keeping CHECK at 1.0°.
