# 12 rotation: local tick reference (2026-09-28, alpha58)

## Finding

Another session found 10 genuine 126710BLRO (Pepsi) photos: 8 Phillips auction lots and
2 from the dealer ElegantSwiss (source list: `tools/research/validation/gmt12/126710blro_sources.csv`
on `main`). alpha57 flagged 5 of the 8 photos where it could measure the 12, all genuine:

| photo | alpha57 result |
|---|---|
| 122669 | off-centre, with the axis at +4.5° |
| 151477 | rotated +3.1° |
| 147798 | skewed +1.6° |
| 146213 | skewed +1.4° |
| CH080120_75 | skewed +1.4° |

## Cause

Rotation was measured against the line from the fitted dial centre to the 60 tick. That
line swings with any error in the centre fit. On these photos it sat 0.7–4.2° off the
square to the 59–01 tick chord. The 6 baton had already moved to a local reference in
alpha55 for the same reason.

The harness driver `AxisRef` measures both references:

| photo | vs centre (alpha57) | vs tick chord |
|---|---:|---:|
| 122669 | +4.46 | +0.26 |
| 151477 | +3.13 | +0.36 |
| 147798 | +1.57 | +0.92 |
| 146213 | +1.44 | +0.78 |
| CH080120_75 | +1.39 | +1.39 |

## Changes

1. **Local axis.** The 12 axis is now the square to the 59–01 tick chord, in both the
   main and the recovery paths.
2. **Cross-check.** The chord is short, about 0.2 dial radii, so a misplaced tick end
   tilts it. When the chord axis and the dial-centre line differ by more than 1.5°, no
   rotation verdict is given. Correct fits agree within about 0.7°.
3. **Skew needs the top edge.** A triangle that has really turned tilts its top edge with
   it. A point that leans with a level top edge is a shape or camera-angle effect: genuine
   photos show up to 1.4°. So a lean without a matching top-edge tilt is flagged only from
   2.0° (`SKEW_ONLY_MIN_DEG`).

## Results (262 photos, phone-like loading)

| | alpha57 | alpha58 |
|---|---:|---:|
| Batgirl genuine flagged | 0 | 0 |
| Pepsi genuine flagged | 5 | **0** |
| replica photos flagged | 17 | 9 |

- **No new flags anywhere.** Every change removes or withholds a verdict.
- **Genuine.** One genuine photo, vmbUDwy image_06, loses its CLEAR rotation verdict and
  is now not judged.
- **Replicas.** The replicas that lost flags either read under 0.6° against the ticks
  (SHiztEn), had a lean without a top-edge tilt (ZVxf97o), or had axes that disagreed
  (6I00d8w, UpTW8nx, sx6zSKZ).
- **Community-checked photos are unchanged:**
  - 7s6PyXJ image_02: still rotated, CHECK, with its top edge tilting the same way. The
    community said "mini clockwise tilt".
  - p3hHVMB image_01: still CHECK.
  - bpdi5xV: still a small gap, STRONG.

## Open

- CH080120_75 still reads a 1.4° lean. It is now not judged, because the resize check
  and the new rules stop it being flagged.
- The Pepsi photos are in the CI emulator run (4 of them) and in the regression list.
