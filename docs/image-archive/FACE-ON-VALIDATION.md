# Image archive admission profile `face-on-v3`: validation (21 October 2026)

**Status:** `face-on-v3` is the production screener of the research archive. Nothing has been uploaded or migrated, and
no Microsoft account is connected.

These are **archive-only** rules. The Android app's QC logic, its own 200 px dial-radius floor, its measurements,
tolerances, reference geometry and model eligibility are unchanged.

Reproduce every number from committed numbers only:

```sh
python -m tools.image_archive.analyse_validation docs/image-archive/face-on-evidence.jsonl
```

`face-on-evidence.jsonl` contains no image bytes. Sources:

- CI run `39673334481` (`.github/workflows/image-archive-faceon-validation.yml`): reviewed photographs, recovered
  artifact originals, recovery pilot, counterexamples.
- A local run on the 12 committed public originals.

The previous profile `face-on-v2` is superseded. Its counts are in git history.

## 1. What changed from face-on-v2, and why

| Rule | face-on-v2 | face-on-v3 | Evidence |
|---|---|---|---|
| Archive dial-radius floor | 200 px (the app's) | **250 px** (archive only) | owner decision; §5 resolution table |
| A hand at a marker | ignored unless < 50 % of the outline is seen | **the app's own per-marker hand check (`MetalFit.handConfirmed`) makes the marker obstructed** | §3 |
| Outline coverage | < 50 % obstructed (`MetalFit.MIN_COVER`) | **≥ 90 % usable, 80–90 % inconclusive, < 80 % obstructed** | §3 |
| Soft edge warnings | all allowed | **only "inconsistent edge" (reflection) allowed**; rough, reconstructed, low-resolution or unknown warnings make the marker unreliable | §4 |
| Pose, framing, sharpness, exposure, glare | — | unchanged | §6 |

## 2. Results at the 250 px floor

Unique photographs with exact original bytes: 652 analysed, out of 944 catalogued in the evidence. "Watches" counts
independent declared physical-watch IDs; aliases are not merged.

| Marker standard (every photo-level check identical) | Photos | GMT 126710 | No-Date 124060 | Date 126610 family | Watches |
|---|---:|---:|---:|---:|---:|
| Measured markers, soft warnings allowed (v2 marker rule) | 178 | 17 | 60 | 101 | 165 |
| Every marker meets the app's genuine reliability standard | 123 | 9 | 43 | 71 | 117 |
| **face-on-v3** (no obstruction; only harmless warnings) | **15** | **0** | **6** | **9** | **15** |

220 photographs pass every photo-level check at 250 px (pose, framing, resolution, sharpness, exposure, glare). Of
these, 15 pass the marker standard. The other 205 are withheld because of a marker:

- **183 have a hand at a marker, confirmed by the app.** 148 of these are at the 2 o'clock marker. Dealer photographs
  are taken at 10:10, so the minute hand lies across the 2.
- 7 have under 80 % of a marker's outline visible, with no hand confirmed. These are mostly the minute hand too.
- 5 have a marker the app withheld itself.
- 4 have an unreliable edge.
- 3 are withheld only because a marker falls in the untested 80–90 % coverage band. These are the only possible false
  rejections the evidence cannot settle.

"Every marker reliable" does **not** protect against hands: the app rates many hand-covered markers reliable. 177
reliable round markers have under 80 % of their outline visible, and they read shifted (p = 3e-7, §3).

## 3. Obstruction: why the dark bar was admitted, and the archive-only fix

**Cause (`17484cbe61baaa1a`, hand_4 copy).** A dark hour-hand-sized bar over the 4 marker left **51.6 %** of its outline
visible, so it was:

- above the app's 50 % reconstruction bound (`MetalFit.MIN_COVER`), so no "reconstructed" warning;
- not flagged by the app's hand check, which is tuned for real hands;
- still measured cleanly (credible, no soft warning).

Face-on-v2 used only the 50 % bound, so the marker counted as available. The photograph itself was a 1080×864 image
with a 202 px dial radius, so the 250 px floor now rejects it on resolution as well.

The extended counterexamples in CI run 39673334481 used the 22 pinned originals that are still fetchable byte-exact, each
with four extra bars. They show the same weakness more widely. Before the fix, 8 of 107 targeted markers that were
clean in the original were missed:

- **6 missed copies:** the bar left 50–52 % of the outline visible and no hand was confirmed.
- **2 missed copies:** a steel-coloured bar was hand-confirmed, but its rough and inconsistent edges had been
  allowed.

**Evidence for the coverage bands.** Reliable genuine round markers on reviewed near-frontal photos, compared with fully
seen reliable markers:

| Outline coverage | Markers | Median position % R | p (Mann-Whitney) |
|---|---:|---:|---:|
| ≥ 0.99 | 1077 | 0.153 | — |
| 0.90–0.99 | 301 | 0.157 | 0.17 |
| 0.80–0.90 | 44 | 0.182 | 0.15 |
| < 0.80 | 177 | 0.198 | **3e-7** |

Hand-confirmed markers behave the same way:

- 90–99 % of the outline seen: p = 0.85 (23 markers);
- 80–90 %: p = 0.14;
- under 80 %: p = 1.6e-6 (202 markers).

No reading crossed a strict genuine limit, but a partly hidden marker shifts systematically. For a calibration archive
that is contamination.

**Fix (archive only).** A marker is obstructed when any of these applies:
- the app withheld it;
- the app's hand check confirmed a hand at it;
- under 80 % of its outline is seen.

It is inconclusive at 80–90 % coverage, and usable at 90 % or more. A narrow bar, a steel bar and every dark bar is now
caught by the hand check, coverage, or the edge rule (§4). No production hand detection or measurement changed.

**Counterexample results (CI run 39673334481, rescored under the final rule).**

- **Marker level:** every one of the **85** bars laid over a marker that was usable in the original is detected.
  0 were missed. The other 25 bars landed on markers that were already unusable in the original; most of these were
  the steel bar over the 2, which the minute hand already covered.
- **Photo level:** 0 false accepts among the copies of the 22 originals (6 original and 4 new variants each).
- **The original 144 copies:** 2 of the 24 pinned originals (`docs/image-archive/degrade-pinned.csv`) are no longer
  served byte-exact, so 132 of the 144 copies could be rebuilt exactly.
- **The originals themselves:** under v3 only 2 of the 22 are admitted. The other 20 have a hand at a marker, which is
  correct under the full-watch rule. A photo-level false-accept count on their copies is therefore not informative,
  which is why the marker-level count is reported.

## 4. Marker reliability: harmful contamination versus harmless reflections

Genuine round markers (reviewed near-frontal photos, radius ≥ 250 px, tilt ≤ 10.6°), compared with reliable, fully
seen markers:

| App soft warning | Markers | Median % R | p95 % R | p |
|---|---:|---:|---:|---:|
| none (reliable) | 1599 | 0.158 | 0.373 | 0.085 (vs the fully seen subset) |
| **inconsistent edge only** (shadowed or reflecting part of the edge) | 38 | 0.140 | 0.367 | **0.78** |
| rough edge | 18 | 0.196 | 0.553 | 0.021 |
| rough + inconsistent | 26 | 0.262 | 1.385 | **2e-5** |

**Recommendation, implemented:**
- An "inconsistent edge" warning alone is a harmless reflection, and the marker is usable.
- A rough edge, a reconstructed outline, low resolution, or any warning the archive does not recognise makes the marker
  unreliable for the calibration archive.

This keeps photographs whose only flaw is a reflection, and excludes edges that measurably shift. The triangle and
baton evidence for soft warnings is thin (fewer than 10 markers per class), so the round-marker evidence is applied to
them conservatively.

## 5. Resolution floor (250 px)

Reviewed face-on photographs resampled to smaller dials, 95th-percentile in/out change from full resolution (% R):

| Dial radius | GMT | No-Date | Date |
|---:|---:|---:|---:|
| 300 | 0.086 | 0.099 | 0.087 |
| **250** | **0.133** | **0.125** | **0.108** |
| 200 | 0.166 | 0.140 | 0.159 |

The archive floor is 250 px dial radius, so the photograph's short side must be at least 500 px. The app keeps its own
200 px floor.

## 6. Perspective (unchanged, provisional)

- **Validated:** tilt ≤ 10.60°. The largest reviewed near-frontal tilt with clean measurements, and below the smallest
  reviewed-oblique tilt (10.61°).
- **Rejected:** tilt ≥ 13.29°. Every reviewed genuine photograph there (11 photos, 3 watches) put markers outside the
  strict genuine limits.
- **Withheld:** 10.6–13.3°, untested.

**Search for new labelled angled photographs (owner item 4).**
- The only angle labels in the repository are the existing contact-sheet reviews.
- Every image file on every remote branch was hashed: 218 unique images. None is a recoverable original of the 39
  reviewed-oblique Date photographs, and the 204 unlabelled ones carry no independent angle label.
- The recovery pilot found one unlabelled photograph at 12.1°.

The perspective limits therefore stay as they are. Three Phillips watches remain the only evidence for the 13.3° bound.
It is not revised from so few watches.

## 7. Recovery pilot (owner-approved, 100 catalogued missing originals)

The pilot was selected deterministically across family and host (`tools/image_archive/recovery_pilot.py`,
`docs/image-archive/recovery-pilot-manifest.csv`). It was fetched once with the existing `fetch_verified.py` (the
harvester's HTTP client, robots.txt, per-host interval). A file was kept only on an exact SHA-256 match. Nothing was
scraped beyond the catalogued URL.

| Outcome | Images |
|---|---:|
| Exact original recovered | **56** (55 GMT, 1 Date; 23 declared watches) |
| Now served with different bytes | 43 (Bob's Watches 39, Elegant Swiss 2, Watchfinder 1, Swiss Watch Expo 1) |
| Unavailable | 1 (HTTP 410) |
| Suitable under face-on-v3 | **0** |

Why none of the 56 exact originals is suitable:
- 28 have no readable dial. They are gallery shots: side, clasp and caseback from the GMT harvester's listing downloads.
- 23 are below the resolution floor.
- 5 have an obstructed marker.

By source:
- Phillips, Swiss Watch Expo and Watches of Switzerland/Goldsmiths served exact bytes.
- Bob's Watches re-encodes most older images (39 of 48).
- No-Date and Date pilot images were all Bob's, and only 1 of 32 was recovered.

**Source drift.** Since the visual review pack was made, 21 previously admitted photographs are no longer served
byte-exact: Swiss Watch Expo now re-encodes all 53 of its No-Date originals, and Bob's several more. Exact originals are
disappearing, which argues for archiving the ones that are suitable.

## 8. Limitations

- **Too few clean photographs.** Full-watch admission is strict: in most dealer photographs the 10:10 minute hand lies
  across a marker. The obstruction-free archive is therefore small (15 photographs, 0 GMT). Growing it needs either
  photographs without a hand over a marker, or per-marker eligibility masks in the calibration engine (not
  implemented, by instruction).
- **Thin pose evidence.** Only 2 reviewed oblique photographs have recoverable bytes. The tilt bounds rest on
  genuine-measurement degradation, from 3 watches above 12°.
- **Coverage bands.** They are bins chosen to test the shift. The 80–90 % band is withheld because its trend (p = 0.15)
  is untested, not because harm was shown.
- **Triangle and baton soft-warning rules** borrow the round-marker evidence.

## 9. Ready for a OneDrive pilot?

The screening is ready for a **small** pilot, but not yet a useful one. Before a pilot:

1. Review the 15 admitted photographs in the visual review pack (workflow *Image archive visual review pack*) and the
   comparison sheets of hand-rejected photos.
2. Decide whether a 15-photograph full-watch archive is worth piloting now, or whether per-marker eligibility masks
   should come first. With masks, roughly 178 photographs would contribute their unobstructed markers.
3. Then: Microsoft consent, the synthetic smoke test, and a pilot of the approved photographs.
