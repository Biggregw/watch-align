# "No readable dial" (2026-09-28, alpha61)

A "12" was sometimes found on photos with no readable dial: side and bracelet views, a
caseback, a watch in its box across the room, a dial worn upside down. Every check then
withheld its verdict, but the summary still read as if a dial had been checked ("12 not
judged", "6 not found", and so on).

**Rule** (`GmtHumanQcAnalyzerV2`): the photo is treated as having no readable dial when all
of these hold:

- the 12's minute-track frame is not confident;
- neither the 6 nor the 9 was traced cleanly;
- at most 2 round markers show a clean edge (contrast of 80 or more) and clear minute ticks
  (tick score of 45 or more).

Across 319 photos the count of clean round markers is split in two: 6–8 on clean dials, and
0–2 elsewhere.

When the rule applies, the summary says only "no readable watch dial found … take a sharp,
straight-on photo of the dial", and nothing is drawn.

**Effect on 319 photos:** 41 photos are now reported this way (14 genuine, 27 replica). None
of them had any verdict before: every 12, 6, 9 and round-marker check on them already
withheld. No other result changed.
