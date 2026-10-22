# Image archive admission profile `face-on-v3`: validation (21 October 2026)

**Status:** `face-on-v3` is the production screener of the research archive. Since 22 October 2026 the archive admits
on photographic suitability plus a per-marker eligibility mask (`marker-mask-v1`, section 10); the strict full-watch
standard below is unchanged and still available. Nothing has been uploaded or migrated, and no Microsoft account is
connected.

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

- **Too few clean photographs under full-watch admission.** In most dealer photographs the 10:10 minute hand lies
  across a marker, so only 13 photographs (no GMT) are fully unobstructed. Per-marker eligibility masks (section 10)
  now keep the other markers of such photographs.
- **Thin pose evidence.** Only 2 reviewed oblique photographs have recoverable bytes. The tilt bounds rest on
  genuine-measurement degradation, from 3 watches above 12°.
- **Coverage bands.** They are bins chosen to test the shift. The 80–90 % band is withheld, not rejected.
- **Triangle and baton soft-warning rules** borrow the round-marker evidence.
- **Synthetic counterexamples.** They test the archive's use of the app's evidence. They are flat bars, not real hands.

## 9. Ready for a OneDrive pilot?

The screening is now dependable on what was tested: every artificial obstruction is caught, every reviewer-rejected
photograph is rejected, and no counterexample is admitted. It is ready for a **small** pilot of the 13 admitted
photographs, once the owner has reviewed them in the visual review pack.

Superseded by section 10: with per-marker masks the archive admits 186 genuine photographs (176 declared watches,
including 24 GMT watches).

The remaining steps, in order: owner review of the pack (now to be regenerated for the masked admissions), Microsoft
consent, the synthetic smoke test, then a pilot of the approved photographs.

## 10. Per-marker calibration eligibility (`marker-mask-v1`, 22 October 2026)

Photographic quality decides whether a photograph is kept; each marker's own reliability decides which of its markers
are trusted. Code: `tools/image_archive/eligibility.py`, `tools/watch_calibrator/marker_mask.py`,
`tools/watch_calibrator/marker_population.py`. Reproduce the populations from committed numbers only:

```sh
python tools/watch_calibrator/marker_population.py docs/image-archive/face-on-evidence.jsonl \
    docs/image-archive/marker-populations.json docs/image-archive/marker-populations.md
```

### 10.1 Two decisions

1. **Photographic suitability.** Every photo-level face-on-v3 check, unchanged: 250 px dial radius; tilt ≤ 10.60°
   (10.6–13.3° inconclusive, ≥ 13.29° rejected); the independent dial-edge ellipse and perspective gradients; all 12
   hour lines registered and the marker lattice agreeing with the minute track; complete dial and minute track;
   sharpness, exposure, glare and marker reflections; verified SHA-256 and provenance. A mask never excuses any of
   these.
2. **Per-marker eligibility.** Each of the 12 positions gets exactly one status, with its evidence and reason:

| Status | When (the face-on-v3 marker rules, named) |
|---|---|
| ELIGIBLE | a credible reading that is reliable, or whose only warning is "inconsistent edge", with ≥ 90 % of its outline seen |
| OBSTRUCTED | a hand confirmed by the app's own check, withheld by the app's hand gate, < 80 % of the outline seen, or mostly reconstructed |
| UNRELIABLE | withheld by the app for any other reason; rough, reconstructed, low-resolution or unrecognised edge warnings; a number without the app's reliability |
| INCONCLUSIVE | 80–90 % of the outline seen (untested), coverage unknown, or no reading reported |
| NOT_APPLICABLE | not an hour marker: the 3 o'clock date window on Date and GMT dials |

Round markers have no tilt and none is required. The statuses reproduce the face-on-v3 per-marker decisions exactly
on all 682 analysed photographs (7,941 markers, 0 differences; `test_statuses_reproduce_face_on_v3_exactly…`), and
`faceon.evaluate()` now derives its marker decisions from them, so there is one rule set.

### 10.2 Minimum usable markers (evidence)

Photographically suitable photographs, eligible markers only, compared with full-watch photographs:

| Required markers excluded | Photos | Round-marker position, median of photo medians (% R) | p (photo medians vs 0–2 excluded) | Marker-level p vs 0 excluded |
|---|---:|---:|---:|---:|
| 0 | 13 | 0.143 | 0.36 | — |
| 1 | 73 | 0.160 | 0.39 | 0.058 |
| 2 | 100 | 0.155 | 0.57 | 0.41 |
| 3 or more | 44 | 0.171 | **0.046** | **0.009** (3–4) |

With up to two markers excluded the remaining eligible readings behave like full-watch readings; from three, the
markers that are still clean shift, which points to a photo-wide problem. Rule (`MAX_EXCLUDED_MARKERS = 2`): at least
**10 of 12** eligible markers on No-Date dials and **9 of 11** on Date and GMT dials. Below that the photograph fails,
or is inconclusive when untested (80–90 %) markers could still make up the shortfall. An empty or invalid mask can
never enter calibration.

### 10.3 The mask record

One JSON record per photograph, stored in the archive with the admission decision and in every manifest snapshot:
`sha256`, `family`, layout `model`, `photo_policy_version` (`face-on-v3`), `mask_policy_version` (`marker-mask-v1`),
`profile`, `required_marker_ids`, `eligible_marker_ids`, `full_watch`, the 12 per-marker statuses with reasons, shape
and evidence, and `mask_digest` (SHA-256 of the canonical record). `eligibility.validate()` refuses a mask that:

- is missing or has an unknown policy version;
- is bound to another photograph;
- has a missing position, or a NOT_APPLICABLE status on a real marker;
- has a status that does not follow from its own evidence;
- has an eligible list or full-watch flag that disagrees with its statuses;
- has a non-finite value or a changed digest;
- has too few eligible markers.

The archive re-derives the mask from the bytes on every sync, and refuses a snapshot whose mask is invalid.

### 10.4 Enforcement in the watch-family calibrator

The calibrator path was traced end to end:

1. `acquire.py` writes `acquired_images.csv` (sha256, physical_watch_id, class, source, local_path).
2. `split.py` locks a watch-level, source-stratified split.
3. `production_measure.py` runs the desktop-harness `CalibMeasure` (production `WatchAlignCoreV13`, app reliability
   gates).
4. The per-watch medians feed the legacy `calibrate.py`.
5. `genuine_envelope.py` fits the envelope.

The calibrator metrics are composites computed by the app (`Sub124060Calibration.assess`). Each is kept only if every
marker it depends on is ELIGIBLE:

| Metric | Depends on |
|---|---|
| `twelve.rotation_deg`, `twelve.gap_r`, `twelve.centring_w` | 12 |
| `round.ring_rho`, `round.spacing_rms_deg` | every round marker (1, 2, 4, 5, 7, 8, 10, 11) |
| `baton.3_9_line_offset_r` | 3, 9 |
| `axis.12_6_line_offset_r` | 12, 6 |
| `marker.<hour>.<quantity>` (per-marker) | that marker only |

A masked run is declared in the model config (`"marker_eligibility": {"policy": "marker-mask-v1"}`) and reads masks
only from `eligibility_masks.jsonl` beside `acquired_images.csv`. That file is written by
`restore_acquired(..., purpose='marker_masked')`. The run fails closed on any of these:

- no mask file, or a photograph without a mask;
- photo bytes whose SHA-256 differs from the mask's;
- a tampered or duplicated mask, or a mask for another layout;
- an unknown policy, or a metric with no declared dependency.

Ineligible values are blanked before any statistic and logged by marker and status, never by value. Both
`production_measure.summarise` and `genuine_envelope` re-check every row where they consume it: a value whose markers
are not all in the row's eligible list, or an unmasked row in a masked run, stops the run.

The **legacy unmasked path is unchanged**, but it refuses an acquisition root that holds masks and any row carrying
mask columns, so a partially eligible photograph cannot reach it. The legacy restore still requires a full-watch mask.
Watch-level aggregation, source diversity, the locked split and replica separation are untouched. No tolerance is
computed, changed or deployed by this work.

### 10.5 Populations (genuine photographs only; replicas, unknown classes and shared stock photos excluded)

| Family | Genuine analysed | Photographically suitable | ≥ 1 eligible marker | Admitted (≤ 2 excluded) | Too many excluded | Full-watch | Declared watches (admitted) |
|---|---:|---:|---:|---:|---:|---:|---:|
| GMT 126710 | 122 | 34 | 34 | **26** | 8 | 0 | **24** |
| No-Date 124060 | 384 | 88 | 86 | **64** | 24 | 6 | **56** |
| Date 126610 family | 121 | 119 | 119 | **96** | 23 | 7 | **96** |
| **Total** | 627 | 241 | 239 | **186** | 55 | 13 | **176** |

Photo-level rejections: mostly resolution below 250 px (180) and a dial not registered, chiefly gallery shots (190).

Independent genuine watches per marker (watch-level medians, eligible photos only). The position reading is shown;
side and in/out are the same counts. Triangle = side. Sources are listing domains, with one company's two sites
counted once.

| Marker | GMT watches (sources) | No-Date watches (sources) | Date watches (sources) |
|---|---:|---:|---:|
| 12 triangle | 23 (3) | 55 (4) | 85 (1) |
| 3 baton | — date window | 55 (4) | — date window |
| 6 baton | 23 (3) | 52 (3) | 88 (1) |
| 9 baton | 23 (3) | 55 (4) | 93 (1) |
| 1 round | 23 (3) | 52 (4) | 91 (1) |
| 2 round | **6 (2)** | **11 (3)** | **16 (1)** |
| 4 round | 24 (3) | 52 (4) | 91 (1) |
| 5 round | **14 (1)** | 53 (4) | 87 (1) |
| 7 round | 24 (3) | 53 (4) | 87 (1) |
| 8 round | 23 (3) | 52 (4) | 93 (1) |
| 10 round | 23 (3) | 54 (4) | 93 (1) |
| 11 round | 18 (3) | 52 (4) | 92 (1) |

Under full-watch admission every marker had 6 No-Date and 7 Date watches and no GMT. Full per-marker, per-quantity
counts, partitions, statuses and reasons are in [marker-populations.md](marker-populations.md) and
[marker-populations.json](marker-populations.json).

**Underrepresented** (< 8 watches or < 3 sources, the calibrator's envelope minimums):
- the GMT 2 (6 watches, 2 sources) and GMT 5 (14 watches but 1 source);
- **every Date marker**, which all come from one source (Bob's Watches);
- the No-Date 2 meets the minimums (11 watches, 3 sources) but is thin;
- GMT markers nominally meet 3 sources, but 21 of 23 watches come from Bob's Watches (one each from Phillips and Watches
  of Switzerland). No-Date is the only family with balanced sources: Bob's and European Watch Company, about 24 each,
  plus DavidSW and SwissWatchExpo.

The 2 is obstructed in most photographs (Date: 91 below 80 % coverage, 81 hand-confirmed among 119 suitable photos).

### 10.6 Validation of the required properties

| # | Property | Evidence |
|---|---|---|
| 1, 10 | The strict 13 are unchanged and fully eligible; strict results unchanged | full-watch counts still 13 (6 + 7) with all markers eligible; the 12 public controls' full-watch decisions are byte-identical apart from the added mask fields; with all markers eligible the masked calibrator gives exactly the legacy envelope and watch medians (`check_the_strict_fully_eligible_dataset…`) |
| 2 | A genuine photo with the 2 obscured contributes its other markers | public GMT `49ba…` (native rerun): full-watch fails only on the 2; masked admission passes with markers 1, 4–12 eligible. 8 of the 11 reviewed-frontal public photos are now admitted, 94ad (angled) is still rejected |
| 3 | The obscured 2 never enters a statistic | calibrator tests: 2-dependent composites are blanked, absent from watch medians and the envelope; re-inserting the value or stripping mask columns stops the run |
| 4 | Dark-bar and steel-bar obstructions stay excluded | CI run 39708201218: 0 of 89 bars over an eligible marker left it ELIGIBLE; 0 of 120 photo-wide degradations admitted (10.7) |
| 5 | Inconclusive markers stay excluded | `6f07…` stays inconclusive with its 80–90 % marker 10 excluded; unit tests |
| 6 | Round markers need no tilt | tests; round quantities are side, in/out and position |
| 7 | Harmless reflections are not rejected | "inconsistent edge" only → ELIGIBLE (tests; §4 evidence) |
| 8 | Several photos of one watch are one sample | watch-level medians in the calibrator and populations (tests) |
| 9 | Model and source diversity still enforced | layout-bound masks; the envelope still refuses too few sources on masked data (tests) |
| — | Tampering and bypass | 24 tamper cases on masks (with and without a recomputed digest), tampered mask files, forged snapshots, replaced photo bytes, re-inserted values, stripped columns, legacy-path misuse: all refused |

### 10.7 Counterexamples under masked admission

CI run `39708201218` repeated the counterexamples on the same 24 pinned originals, with the final code:
`degrade.py` now also assesses every copy with `purpose='marker_masked'` and reads the target marker's status from its
mask.

| | Date | GMT | No-Date | Total |
|---|---:|---:|---:|---:|
| Originals admitted under masking (full-watch: 0) | 8 of 8 | 5 of 8 | 4 of 8 | **17 of 24** |
| Bars over a marker ELIGIBLE in its original | 32 | 29 | 28 | **89** |
| … that left that marker ELIGIBLE in the copy's mask | 0 | 0 | 0 | **0** |
| Photo-wide degradations admitted (blur ×2, glare, crop at the 12, 150 px; 120 copies) | 0 | 0 | 0 | **0** |
| Full-watch false accepts (unchanged rule) | 0 | 0 | 0 | **0** |

A copy with a bar is admitted only when its target marker is OBSTRUCTED and no more than two markers are excluded
(for example a Date original with the minute hand at 2 and a bar at 4: markers 2 and 4 excluded, nine eligible). The
other bar copies fail or stay inconclusive because the bar made a third marker unusable. The steel bar at the 2 lands
on markers that are already obstructed in every Date original.

### 10.8 Limitations

- **Composite calibrator metrics lose more than they gain.** `round.ring_rho` and `round.spacing_rms_deg` need every
  round marker, so a hand at the 2 removes them for that photograph. The gain from masking is in per-marker
  quantities (`marker.<hour>.<quantity>`, supported by the dependency map and `marker_population.py`). The current
  `CalibMeasure` driver emits only composites; adding per-marker columns there is a research-driver change, left for
  owner approval because it would make the calibrator fit new envelopes.
- **The digest is not a signature.** A forger who rewrites a marker's evidence, status, eligible list and digest
  together makes a self-consistent mask. The archive catches this by re-deriving the mask from the bytes on sync. The
  calibrator verifies the bytes and the mask's consistency, but does not re-run the analysis.
- **Small statistical units.** The minimum-marker evidence uses photo medians from 44 photos with 3 or more exclusions
  (p = 0.046). The marker-level tests treat markers as independent.
- **Identity.** Declared watch IDs are not merged across catalogues; shared stock photos are excluded.
- **Single-source Date evidence.** Every Date marker is underrepresented on source diversity.
