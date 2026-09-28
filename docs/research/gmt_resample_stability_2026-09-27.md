# Phone vs desktop, and the 12 resize check (2026-09-27, alpha56)

## What the emulator run showed

The CI emulator (Android 15, x86_64) ran 11 corpus photos through the same path as the
Check button (`EmulatorPhotoRunTest`). On 6 of them the result differed from the desktop
harness. The largest difference was on rep_cf_6I00d8w image_01:

| | gap | triangle | rotation |
|---|---:|---:|---:|
| desktop harness | 0.14 | 43 px | −1.5° |
| emulator | 0.07 | 45 px | −1.2° |

## Cause

- **The two decoders differ very slightly.** The emulator now saves the exact working
  image it analysed. Compared with the harness's decode of the same JPEG, the average
  difference is 0.3–0.5 grey levels, and fewer than 0.04% of values differ by more than
  8.
- **The analysis code is identical.** Running the desktop analysis on the emulator's
  working images reproduces every emulator result exactly.
- **So some readings are fragile.** On 6I00d8w image_01, six near-identical loads gave
  different answers. They differ only in the resampling filter, or in a 1500 or 1400 px
  working size:

  | load | triangle | gap |
  |---|---:|---:|
  | 1 | 34 px | 0.23 |
  | 2 | 43 px | 0.14 |
  | 3 | 45 px | 0.07 |

  There are three failure routes:
  - The left side of the triangle couldn't be fitted, so the result fell back to the lume
    contour.
  - The base fitted at a tilt (5–7° out of square), which triggered the band-level
    fallback.
  - A clean fit was accepted.

  Each route gives a different edge, and a sub-grey-level change decides which one runs.

The harness now loads photos the way the phone does (`tools/desktop-harness/drivers/Load.java`):
libjpeg decode, `inSampleSize` as DCT scaling, and a bilinear reduction to 1600 px. It
is slightly closer to the phone than ImageIO, but it can't match it exactly, and no
loader can: the fragility is in the measurement.

## The resize check

`GmtTwelveLandmarkAnalyzer.measureStability` measures the 12 twice more, on the same
photo reduced to 94% and to 88%. It uses only reductions, because enlarging only
interpolates pixels that were never captured. On the official render at full size, a
106% copy read a rotation of 1.3°, while every reduction read 0.2–0.5°.

**Gap and rotation are judged separately.** A reading is stable when all three
measurements found the same kind of edge (outer edge or not) and either of these holds:

- it moved by at most 1 px at the marker. For rotation this means tip travel,
  `tan(Δ) × 1.23 × width`.
- every reading is on the same side of the level where a verdict starts, so the
  movement can't change what the user is told:
  - a gap that clears 0.070 by more than a pixel. The emulator's 6I00d8w reading of
    0.074 to 0.148 doesn't qualify, because 0.074 is within a pixel of the limit.
  - a rotation under 1.0°.

When a reading is withheld, the bottom line never says "nothing flagged at 12". It says
"nothing flagged, but the 12 … reading changes when the photo is resized slightly".

**An unstable reading gets no verdict.** The summary says "not judged: the reading
changes when the photo is resized slightly", with the range. A concern that every
re-measurement agrees on is kept as CHECK and never as STRONG. That means every gap
below 0.070, or every rotation at least 1.0° in the same direction. The detailed report
gains a line such as "12 resize check (94%, 88%): …".

## Corpus (252 photos, phone-like loading)

| | before | alpha56 |
|---|---:|---:|
| genuine photos with a gap verdict | 4 | 4 |
| genuine photos with a rotation verdict | 5 | 5 |
| genuine photos flagged | 0 | 0 |
| replica photos with a gap verdict | 21 | 17 |
| replica photos with a rotation verdict | 35 | 31 |
| replica photos flagged | 18 | 15 |

Every judged reading on a genuine photo moved by 0.6 px or less. On the replica photos,
8 readings on 7 photos were withheld, and one was downgraded:

- Gap withheld on 4 photos (6I00d8w image_02, 7s6PyXJ image_01, BXiTXD1 image_07 and
  p3hHVMB image_01): the gap moved 3–13 px, or a different edge was found.
  BXiTXD1's gap CHECK came from a gap that moved 13 px.
- Rotation withheld on 4 photos (sx6zSKZ image_00, 8xahrjm image_01, 7s6PyXJ image_01
  and p3hHVMB image_00): a different edge was found, or the rotation crossed 1.0°.
- p3hHVMB image_01's rotation went from STRONG to CHECK: it is turned the same way at
  every scale.
- p3hHVMB image_00 is now borderline. Its rotation reads +0.5° to +1.7° across scales,
  so it gets CHECK on some loads and "not judged" on others. The phone's own working
  image gives CHECK.

## The same photos on different loads

6I00d8w image_01 across the six loads:

- Before alpha56, three of the six loads, and the phone, gave a 12 verdict (gap CLEAR, or
  rotation CHECK) from readings between 0.07 and 0.23.
- Now its gap is withheld on all six, and its rotation on five of the six.

Genuine 1TDYtpN image_01 is CLEAR on all six. The official render is CLEAR at every
working size from 1600 to 2160 px.

## Open

- The 6 baton isn't re-measured yet. 7s6PyXJ image_02 gives a 6 reading of CHECK on 2
  loads and "not judged" on 4.
- When the edge fit fails, the "too small" size gate measures the lume contour, which is
  about 80% of the outer width. On 6I00d8w image_01 that reads 34 px against 43 px
  outer. That load now says "too small" when "not reliable" would describe it better.
