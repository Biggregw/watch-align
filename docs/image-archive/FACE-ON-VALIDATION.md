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

- the final CI run `39679462444` (`.github/workflows/image-archive-faceon-validation.yml`), with the final code;
- the 12 committed public originals, measured locally;
- the recovery pilot, union of runs `39673334481` and `39679462444`.

The previous profile `face-on-v2` is superseded. Its counts are in git history.

## 1. What changed from face-on-v2, and why

| Rule | face-on-v2 | face-on-v3 | Evidence |
|---|---|---|---|
| Archive dial-radius floor | 200 px (the app's) | **250 px** (archive only) | owner decision; §5 |
| A hand at a marker | ignored unless < 50 % of the outline is seen | **the app's own per-marker hand check (`MetalFit.handConfirmed`) makes the marker obstructed** | §3 |
| Outline coverage | < 50 % obstructed (`MetalFit.MIN_COVER`) | **≥ 90 % usable, 80–90 % inconclusive, < 80 % obstructed** | §3 |
| Soft edge warnings | all allowed | **only "inconsistent edge" (a reflection) allowed**; rough, reconstructed, low-resolution or unrecognised warnings make the marker unreliable | §4 |
| Pose, framing, sharpness, exposure, glare | — | unchanged | §6 |

## 2. Results at the 250 px floor

944 unique photographs are catalogued in the evidence, and 682 of them have exact original bytes and were analysed.
"Watches" counts independent declared physical-watch IDs; aliases are not merged.

| Marker standard (every photo-level check identical) | Photos | GMT 126710 | No-Date 124060 | Date 126610 family | Watches |
|---|---:|---:|---:|---:|---:|
| Measured markers, soft warnings allowed (v2 marker rule) | 194 | 17 | 73 | 104 | 173 |
| Every marker meets the app's genuine reliability standard | 127 | 9 | 46 | 72 | 121 |
| **face-on-v3** (no obstructed marker; only harmless warnings) | **13** | **0** | **6** | **7** | **13** |

244 photographs pass every photo-level check at 250 px (pose, framing, resolution, sharpness, exposure, glare). 13 of
them also pass the marker standard. Why the other 231 are withheld (a photograph can have several reasons):

| Marker condition | Photos |
|---|---:|
| A hand at a marker, confirmed by the app's own per-marker hand check | **211** (164 at the 2 o'clock marker) |
| An unreliable edge (rough or reconstructed) | 55 (8 for this reason alone) |
| A marker with 80–90 % of its outline visible (untested band) | 30 (4 for this reason alone) |
| A marker the app withheld (hand gate, glare, Can't check) | 27 |
| Withheld with no hand involved at all | 20 |

Dealer photographs are taken at 10:10, so the minute hand lies across the 2 marker in most of them. That is why the
obstruction-free archive is small.

"Every marker reliable" does **not** protect against hands: the app rates many hand-covered markers reliable, and those
readings shift (§3).

## 3. Obstruction: why the dark bar was admitted, and the archive-only fix

**Cause (`17484cbe61baaa1a`, hand_4 copy, CI run 39673334481).** A dark hour-hand-sized bar over the 4 marker left
**51.6 %** of its outline visible, so it was:

- above the app's 50 % reconstruction bound (`MetalFit.MIN_COVER`), so no "reconstructed" warning;
- not flagged by the app's hand check;
- still measured cleanly (credible, no soft warning).

Face-on-v2 checked only the 50 % bound, so the marker counted as available. The photograph itself is a 1080×864 image
with a 202 px dial radius, which the 250 px floor now rejects as well.

Before the fix, the extended counterexamples (four more bars per original) missed 8 of 107 targeted markers that were
clean in their originals:

- **6 missed copies:** the bar left 50–52 % of the outline visible and no hand was confirmed.
- **2 missed copies:** a steel-coloured bar was hand-confirmed, but its rough and inconsistent edges had been allowed.

**Coverage evidence.** Reliable genuine round markers on reviewed near-frontal photographs (radius ≥ 250 px,
tilt ≤ 10.6°), compared with fully seen reliable markers:

| Outline coverage | Reliable markers | Median position % R | p | Hand-confirmed markers | Median % R | p |
|---|---:|---:|---:|---:|---:|---:|
| ≥ 0.99 | 1147 | 0.158 | — | 50 | 0.314 | **7e-12** |
| 0.90–0.99 | 338 | 0.160 | 0.51 | 37 | 0.147 | 0.27 |
| 0.80–0.90 | 50 | 0.208 | **0.017** | 33 | 0.248 | **0.005** |
| < 0.80 | 195 | 0.204 | **2e-6** | 222 | 0.195 | **2e-6** |

No reading crossed a strict genuine limit, but partly hidden or hand-touched markers shift systematically. For a
calibration archive that is contamination.

**Fix (archive only).** A marker is obstructed when any of these applies:
- the app withheld it;
- the app's hand check confirmed a hand at it;
- under 80 % of its outline is seen.

It is inconclusive at 80–90 % coverage, and usable at 90 % or more. No production hand detection or measurement
changed.

**Counterexample results, final code (CI run 39679462444).** All 24 pinned originals of the first counterexample run
(`docs/image-archive/degrade-pinned.csv`) were fetched byte-exact. That gives 240 copies: the original 6 variants
(144 copies) plus 4 new bars, each in 24 copies (96).

- **Photo level: 0 false accepts.** None of the 24 originals is admitted under v3: each has a hand-covered or
  unreliable marker. The degraded copies are therefore also checked at marker level.
- **Marker level: all 89 bars laid over a marker that was usable in its original are detected; 0 were missed.** The
  other 31 bars landed on markers that were already unusable, mostly the steel bar at the 2, which the minute hand
  already covers.
- **Detection by bar type:**
  - dark hour-hand bar: 22 of 22;
  - narrow dark bar: 22 of 22;
  - dark bar at the 8: 22 of 22;
  - steel bar at the 10: 22 of 22;
  - steel bar at the 2: 1 of 1.

## 4. Marker reliability: harmful contamination versus harmless reflections

Genuine round markers (reviewed near-frontal photographs, radius ≥ 250 px, tilt ≤ 10.6°), compared with reliable, fully
seen markers:

| App soft warning | Markers | Median % R | p95 % R | p |
|---|---:|---:|---:|---:|
| none (reliable, any coverage) | 1730 | 0.163 | 0.394 | 0.13 |
| **inconsistent edge only** (part of the edge shadowed or reflecting) | 44 | 0.162 | 0.367 | **0.92** |
| rough edge | 26 | 0.236 | 0.482 | 6e-4 |
| rough + inconsistent | 55 | 0.318 | 1.211 | **1e-12** |

**Implemented:**
- An "inconsistent edge" warning alone is a harmless reflection, and the marker is usable.
- A rough edge, a reconstructed outline, low resolution, or any warning the archive does not recognise makes the marker
  unreliable for the calibration archive.

This keeps photographs whose only flaw is a reflection, and excludes edges that measurably shift. The triangle and baton
evidence for soft warnings is thin (fewer than 10 markers per class), so the round-marker evidence is applied to them
conservatively.

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

**Search for new labelled angled photographs.**
- The only angle labels in the repository are the existing contact-sheet reviews.
- Every image file on every remote branch was hashed: 218 unique images. None is a recoverable original of the 39
  reviewed-oblique Date photographs, and the 204 unlabelled ones carry no independent angle label.
- The recovery pilot found one unlabelled photograph at 12.1°.

The limits stay as they are. Three Phillips watches remain the only evidence for the 13.3° bound, and it is not revised
from so few watches.

## 7. Recovery pilot (owner-approved, 100 catalogued missing originals)

The pilot was selected deterministically across family and host (`tools/image_archive/recovery_pilot.py`,
`docs/image-archive/recovery-pilot-manifest.csv`). It was fetched with the existing `fetch_verified.py` (the
harvester's HTTP client, robots.txt, per-host interval), once in each of the two CI runs. A file was kept only on an
exact SHA-256 match, and nothing was fetched beyond the catalogued URL.

| Outcome | Run 39673334481 | Run 39679462444 |
|---|---:|---:|
| Exact original | 56 | 40 |
| Different bytes now served | 43 | 59 |
| Unavailable (HTTP 410) | 1 | 1 |

Over both runs, **57 exact originals** were recovered (56 GMT, 1 Date; 24 declared watches). **None is suitable:**

- 28 have no readable dial: gallery shots (side, clasp, caseback) from the GMT harvester's listing downloads.
- 23 are below the resolution floor.
- 6 have an obstructed marker.

By source:
- Phillips, Swiss Watch Expo, Goldsmiths/Watches of Switzerland and Bob's served exact bytes at times.
- Bob's re-encodes most older Submariner images: only 1 of the 32 No-Date and Date pilot images was recovered.

**Sources drift between runs.** Swiss Watch Expo re-encoded all 53 of its No-Date originals in one run and served them
byte-exact in the next. The same pilot image can verify one day and not the next. This argues for archiving suitable
originals once they are verified.

## 8. Limitations

- **Too few clean photographs.** Full-watch admission is strict: in most dealer photographs the 10:10 minute hand lies
  across a marker. The obstruction-free archive is therefore small (13 photographs, no GMT). Growing it needs either
  photographs without a hand over a marker, or per-marker eligibility masks in the calibration engine (not
  implemented, by instruction).
- **Thin pose evidence.** Only 2 reviewed oblique photographs have recoverable bytes. The tilt bounds rest on
  genuine-measurement degradation, from 3 watches above 12°.
- **Coverage bands.** They are bins chosen to test the shift. The 80–90 % band is withheld, not rejected.
- **Triangle and baton soft-warning rules** borrow the round-marker evidence.
- **Synthetic counterexamples.** They test the archive's use of the app's evidence. They are flat bars, not real hands.

## 9. Ready for a OneDrive pilot?

The screening is now dependable on what was tested: every artificial obstruction is caught, every reviewer-rejected
photograph is rejected, and no counterexample is admitted. It is ready for a **small** pilot of the 13 admitted
photographs, once the owner has reviewed them in the visual review pack.

A useful archive (well over 100 watches, any GMT at all) needs one of these, which is an owner decision:

1. **Per-marker eligibility masks in the calibration engine.** The hand-covered marker would be excluded and the other
   10–11 markers kept. That would admit roughly the 194 photographs (173 watches) of the measured-marker rule. It is not
   implemented, as instructed.
2. **New photographs** without a hand over any marker.

The remaining steps, in order: owner review of the pack, Microsoft consent, the synthetic smoke test, then a pilot of
the approved photographs.
