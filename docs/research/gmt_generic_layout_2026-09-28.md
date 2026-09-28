# Generic GMT: the model selector, and the Sprite's mirrored date (2026-09-28, alpha61)

This is a review of the generic-GMT change against the code rather than its comments.

## 1. The model selector was still shown

`MainActivity` offered every catalog profile that `CanonicalGmtGeometryAnalyzer.supports()`
accepts, which is every code starting with 126710: BLNR, BLRO and GRNR. The alpha61 change
only hid the dropdown when a single model was offered, so at runtime it still showed three
entries. The user's screenshot showed it.

**Fix:** `MainActivity.offeredModels()` returns only the generic profile (code 126710BLNR,
label "Rolex GMT-Master II"), and the dropdown is always hidden. `GenericGmtTest` checks
that exactly one profile is offered, and that the old rule would have offered more than
one.

## 2. The Sprite (126720VTNR) has its date at 9 and a baton at 3

Before this fix the side baton was always measured at 9, and the summary always called the 3
a "date window". On a Sprite that meant:

- the 3 baton was never checked;
- the summary named the wrong side as the date;
- the new search window from the ticks could have traced the date window's frame as a
  baton.

On the photos in the corpus it didn't; the 9 came out "not found" on all five Sprites.

**Fix:** `GmtDialLayout` reads the date side from the photo, once the 12 is confidently
found. It probes a strip where a baton's lume would be at 3 and at 9. Lume is uniformly
bright; the date window is white with black numerals. The measure is the share of the strip
darker than half its bright level: batons 0.00–0.09, date windows 0.15–0.43.

| reading | layout |
|---|---|
| 3 ≥ 0.12 and 9 ≤ 0.05 | date at 3 |
| 9 ≥ 0.12 and 3 ≤ 0.05 | date at 9 |
| anything else, or no confident 12 | unknown |

- **Date at 3:** the 9 baton is measured, exactly as before.
- **Date at 9:** the 3 baton is measured (`Position.THREE`, turned 90° anticlockwise, ticks
  14/15/16). The summary names the 9 as the date window.
- **Unknown:** the 9 is measured, but without the search window from the ticks, so a date
  window can't be traced as a baton. The summary says "3 not checked (date side not
  determined)" and doesn't name either side as the date.

## 3. Wording

The code comment and the handoff notes said "all current references tested". The corpus runs
genuine photos of each current reference, but the other references are **assumed** to share
the 126710BLNR master geometry, and several have only one or two photos: 126713GRNR has only
the official render. All replica photos are 126710BLNR. The wording now says so, and the
handoff notes list the counts.

## Regression (319 photos, compared with PR #26 as it was)

| | genuine before | genuine after | replica before | replica after |
|---|---:|---:|---:|---:|
| photos | 109 | 109 | 210 | 210 |
| photos with any flag | 2 | 2 | 13 | 13 |
| 12 gap judged | 45 | 45 | 20 | 20 |
| 12 alignment judged | 29 | 29 | 29 | 29 |
| 6 judged | 27 | 27 | 22 | 22 |
| side baton (3 or 9) judged | 39 | 40 | 17 | 17 |
| side baton flagged | 0 | 0 | 0 | 0 |
| round markers clear / flagged | 356 / 0 | 356 / 0 | 297 / 0 | 297 / 0 |
| "no readable dial" | 39 | 39 | 112 | 112 |

These are all 319 photos, including ones with no dial.

The only changed result is the official 126720VTNR render: its 3 baton is now measured and
clear, and the summary names the 9 as the date window. It was previously "9 not found",
with the 3 described as the date window.

Date side on photos with a drawn 12:

| | genuine | replica |
|---|---:|---:|
| date at 3 | 41 | 33 |
| unknown | 21 | 23 |
| date at 9 | 1 (the Sprite render) | 0 |

The four Phillips Sprite photos come out unknown: a hand covers the 3 baton on two, and on
the other two no dial is read. They still measure nothing at 9, and no longer call the 3 a
date window. The first version of the probe read one standard Phillips Pepsi (172193), whose
12 was misread by 30°, as date-at-9. The layout is now only read when the 12 frame is
confident.

**Limitation:** only one Sprite photo in the corpus shows its 3 baton clearly, so the date-at-9
path is demonstrated on one image.
