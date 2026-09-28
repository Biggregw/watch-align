# Why the 6 is often not measured, and what changed (2026-09-28, alpha61)

On photos with a usable dial (12 found, photo angle usable), the 6 was judged on 22 of 68
genuine photos and the 9 on 39. Before this change, the 6's outcomes on those photos broke
down as:

- not found: 49;
- judged clear: 40;
- a hand, or a reading that moved on resizing: 17;
- the edge fit not clean enough: about 20;
- too small: 8.

## Changes

1. **Search where the ticks say the baton is** (`GmtSixLandmarkAnalyzer.priorFromTicks`).
   When no outline passes the baton shape tests (typically because a hand has merged with
   it), the baton's edges are searched for inside a window built from the minute ticks
   either side of it. The window is placed just inside the tick line, 0.30 × 0.12 dial
   radii, square to the tick chord, and oriented by the 12. It is only a search window: if
   the edges can't be traced there, the result is "outline not found where the minute ticks
   put it, usually because a hand lies along it".
2. **Resize check: a different edge kind at one scale no longer withholds a clear baton**
   when every reading is below both check levels (0.10 offset, 2°). WOS CPO 6s reading offset
   −0.01 to +0.02 and rotation −0.7° to +0.6° had been withheld.

## Result (319 photos)

On usable dial photos:

| | before | after |
|---|---:|---:|
| genuine 6 clear | 22 / 68 | 27 / 68 |
| replica 6 clear | 18 / 70 | 20 / 70 |
| 6 flags (genuine / replica) | 0 / 2 | 0 / 2 |
| 9 clear (genuine / replica) | 39 / 16 | 39 / 17 |

No 12 or photo-angle result changed.

## What the rest are

I looked at the search window on all 22 genuine photos where the 6 still isn't found:

- **The seconds hand is parked along the baton.** This covers all seven official 2026
  renders and most of the WOS and Phillips studio shots, which park the seconds hand at
  about 30 seconds. It's also the GMT arrow on two Phillips photos. The window sits on the
  baton; the hand is what stops the edges being traced. "Not measured" is the right answer.
- **The photo isn't a dial**, but a "12" was found on a bracelet, fabric or a caseback (6
  photos). The window lands on texture.
- **The window is tilted**, because the tick frame locked onto a hand (2 photos).

So the 6 lags the 9 mostly because of where watches are photographed with the seconds hand,
not because of the measuring.
