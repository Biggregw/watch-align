# Watch Align 1.5.5: alignment-only hour-marker check (14 Oct 2026)

Current version 1.5.5 (versionCode 13121). The 1.5.0 description below still applies; the later sections list what
changed in 1.5.1–1.5.5.

Branch `feature/android-alignment-only-20261014`, built from Alpha107 (`outer-metal/integration-20261013` @ `5e6d565`).
Merged into `main` with 1.5.3. Rollback: install the Alpha107 APK (run 38493791860) or check out `5e6d565`.

## What the app does
For every hour marker of the selected model (GMT-Master II 126710: triangle, 2 batons, 8 rounds; Submariner 124060:
triangle, 3 batons, 8 rounds) the answer is **No defect**, **Defect detected** or **Cannot assess**. From 1.5.3 a marker
whose lume is in place but whose outer metal reads beyond every genuine watch within the photo's uncertainty is shown
as **Worth a look** (borderline, not a confirmed defect).

- The photo is treated as straight-on (owner decision). The existing frozen dial pose supplies only the centre, scale
  and orientation.
- **The luminous fill is measured, not the bevel** (`LuminousAlignment`).
  - Each ray starts in confirmed lume and runs outward. The edge is placed half-way to the first supported intensity
    minimum.
  - All edge samples are fitted together (Huber weights): translation and rotation of the known shape, and translation
    only for a circle.
  - Samples lying well outside the known-size fit are set aside as a lit bevel merged with the lume.
- **Known-shape inference** (`LuminousAlignment.measure`).
  - A triangle with two visible sides gets its position and rotation from the known shape. The missing side is drawn
    dashed and labelled as inferred, and the known-size uncertainty is added to the error.
  - A baton with only its long sides keeps sideways position and rotation; its radial position is withheld because
    those sides do not fix it.
  - A round marker with 50–70 % of its outline visible is fitted at the photo's known size.
  - Covered by unit tests (`LuminousAlignmentTest`).
- **The reference never uses the judged marker.** Each marker's genuine position is placed by the ring fitted to the
  *other* markers only (`LuminousAlignment.ringWithout`).
- **Decision** (`AlignmentDecision`). A marker is a defect when its deviation from the per-hour genuine nominal exceeds
  the largest deviation of any verified genuine watch (one value per physical watch per marker) by more than the
  measurement uncertainty. That uncertainty is 2 × the fit error combined with the genuine photo-to-photo
  repeatability, and never less than half a native pixel. The dominant issue is the largest visible excess; a rotation
  counts as the distance it moves the marker's far end.
- **Display only** (`AlignmentCloseup`):
  - yellow: fixed genuine outer-metal reference;
  - translucent silver: surround around the fitted lume, drawn at the genuine bevel width;
  - cyan: fitted lume outline, dashed where inferred;
  - red: direction of the dominant defect.
- No area percentages, shape grading, scores or sliders. The Alpha107 outer-metal text is kept under Technical details
  for reference only.

## Genuine luminous references (bundled)
`assets/models/<id>/alignment/luminous_alignment.json`, from calibration run **38565452660** (commit `295b1de`):

- 2-pass calibration with `tools/research/alignment_only/calibrate.py` and `AlignmentCalib.java`;
- sha256-verified genuine catalogues only; no replica or QC photo was used.

| Model | Genuine photos / watches | Insets tri / baton / round (%R) | Envelopes radial / tangential (%R), rotation (deg) |
|---|---|---|---|
| GMT 126710 | 67 / 39 | 0.89 / 1.36 / 1.38 | tri 0.47 / 0.54 / 0.95; baton 0.33 / 0.41 / 1.63; round 0.57 / 0.39 |
| Sub 124060 | 125 / 76 | 1.08 / 1.47 / 1.61 | tri 0.33 / 0.24 / 1.28; baton 0.39 / 0.42 / 0.79; round 0.36 / 0.37 |

The 126610 has no alignment reference and is not offered in the model picker.

## Validation (one bounded run, plus the app's own decision with the frozen reference)

### Genuine coverage
| Model | Readings assessed | Rate |
|---|---:|---:|
| GMT | 338 of 748 | 45 % |
| Sub | 1,007 of 1,500 | 67 % |

### Held-out false "Defect detected" on genuine photos (leave one watch out)
| Model | Photos with a false defect | Rate by marker type |
|---|---:|---|
| GMT | 6 of 49 | triangle 5.4 %, baton 1.4 %, round 1.3 % |
| Sub | 0 of 100 | – |

### Repeat photos of the same genuine watch
| Model | Marker pairs with the same outcome in every photo |
|---|---:|
| GMT | 20 of 22 |
| Sub | 185 of 185 |

### Public sha256-verified QC controls
Reviewer labels are not alignment ground truth.

- **GMT "crooked 6" (rejected):** Defect at 6 ("too high"), 9 and 12. The 6 was not assessable in earlier runs; it is
  now flagged.
- **GMT accepted watch:**
  - photo 1: 1 round flagged (8 o'clock);
  - photo 2: no defects;
  - photo 3: Cannot assess (too low resolution).
- **Sub "rejected 12":** No defect. Its 12 reads +1.63° rotation, within the 1.28° envelope plus this photo's
  uncertainty.
- **Sub accepted watch:** 12 flagged "too far left" in both photos (consistent).
- **Sub "control":** 4 rounds flagged.

### Owner's private QC 38097 (run locally, not published)
- **12:** No defect. Its luminous triangle reads rotation −1.0° and radial +0.14 % R. The earlier +1.5 % R came from the
  outer bevel, not the lume.
- **Rounds 1, 4, 8, 10:** flagged.

## Known limitations (for the owner's phone test)
1. **Coverage.** Many markers are "Cannot assess". The main reasons, in order:
   - **Resolution:** a dial under 400 px across withholds the whole photo.
   - **Hand or glare touching a marker:** the inherited Alpha99 interference check withholds the whole marker before
     any fit.
   - **Lit bevel merged with the lume:** on more than 20 % of the outline.
   - **Irregular edge:** noise above 0.8 px.
   - **Edge offset inconsistent with the marker's genuine shape or with the other markers in the same photo.**

   Round markers are hit hardest because the hands sweep over them and their bevel often reflects as brightly as the
   lume.
2. **Known-shape inference is implemented and unit-tested but rarely reached on real photos.** None of the assessed
   QC markers in the validation used it; every assessed marker had all its edges seen. The usual cause of a partly
   hidden marker is a hand, and the existing hand check withholds the whole marker instead of fitting the visible
   part. Changing that is a detection change and has not been made.
3. **False positives:**
   - GMT: 6 of 49 held-out genuine photos showed one false defect.
   - Sub: no held-out false defects, but round markers on the "control" QC photo were flagged.

   Both suggest that photo-to-photo variation of phone and QC photos is larger than the dealer-photo genuine sample
   shows.
4. **Limited genuine repeat photos:** 3–13 pairs per marker type for GMT.
5. **A visibly displaced outer metal surround with a correctly placed lume is not reported.** This is by design
   (luminous alignment only).

## Signing
The repository has no shared signing-key secret, so CI signs with a fresh throwaway "Android Debug" key on every
build. The APK installs as `com.watchalign.mobile.poc` but **cannot update** an earlier Watch Align / Alpha build:
uninstall the old app first. Doing so removes its saved settings (only the last-chosen model). This is not a production
signing.

## 1.5.1–1.5.2 additions (owner request after phone testing, 14 Oct 2026)
- **Outer-metal alignment** (`MetalAlignment`) for the triangle and batons covers position and rotation only.
  - **Reuse:** it uses the existing Alpha107 measurement, genuine references and nominal offsets, with Alpha107's
    CLEAR standard.
  - **Borderline readings:** a reading beyond every genuine watch but within the allowance is shown as a note, not
    as a defect (labelled "Worth a look" from 1.5.3).
  - **Wording:** results are reported as "Outer metal ...".
- **The 12's distance from the minute track** previously had no photo-to-photo allowance. It now has one, in
  `alignment/metal_alignment.properties`.
  - **Pooled within-watch SD:** GMT 0.123 % R (0.50 px), Submariner 0.051 % R (0.19 px).
  - **Repeat photos used:** only those the app itself assesses (dial radius ≥ 200 px). The sub-200 px copies of the
    same auction images had inflated the GMT value to 0.218 %.
  - **Multiplier:** k = 2, as in every luminous decision.
  - **Genuine check:** on the genuine photos the app assesses (92 GMT, 105 Submariner), neither k = 2 nor k = 3 flags
    any 12.
  - **Track-referenced reading:** measuring from the minute track instead of the ring of other markers was checked and
    is not more reliable (GMT σ 0.120 vs 0.123 % R, Submariner 0.070 vs 0.051 %; wider genuine spread), so the
    ring-relative measure is kept.
  - **Owner's Batgirl 12:**
    - outer metal 0.685 % R towards the track, against a genuine maximum of 0.318 %;
    - excess 0.367 % R (0.99 px at its dial radius of 270 px), against an allowance of 0.371 %;
    - result: borderline ("Worth a look" from 1.5.3), by about 0.01 px. A genuinely sharper original photo of the
      same watch (dial radius 273 px or more) would flag it; enlarging this photo adds no detail and cannot.
- **Round markers:**
  - **Edge:** the lume edge is the steepest point of its fall; a shallower separator counts when a lit bevel follows
    it.
  - **Size check:** a fully seen round's size is checked only against a merged bevel (half a surround width). Its
    centre does not depend on its size.
  - **Owner's genuine GMT:** all seven rounds without a hand over them are now assessed, with no defects.

## 1.5.3 (14 Oct 2026, merged into main)

### Round-marker visual tolerance (owner decision)
- **Rule:** a round marker's radial or tangential position is a defect only when BOTH of these hold:
  1. it is beyond the genuine envelope plus this photo's uncertainty (the existing genuine rule);
  2. it is displaced from its genuine reference position by more than **1.00 % of the dial radius**
     (`AlignmentDecision.ROUND_VISUAL_FLOOR_R`).
- **How the thresholds combine:** the larger of the two thresholds applies; they are never added together.
- **Why:** this is a product acceptability choice. Watch Align reports visually unacceptable misalignment, not every
  measurable difference from genuine averages.
- **What does not change:**
  - triangles, batons, rotations and the outer-metal checks;
  - the bundled genuine references (byte-identical, pinned by `RoundVisualFloorTest`);
  - a round that cannot be measured still reads "Cannot assess".
- **Technical details:** shows the floor next to the genuine envelope for every round component.

### Borderline outer metal: "Worth a look"
- **When it applies:** a triangle or baton whose lume is in place but whose outer metal reads beyond every genuine
  watch, while staying within this photo's measurement uncertainty.
- **How it is shown:**
  - labelled **Worth a look** in amber, with a "?" badge on the dial;
  - the detail screen states that the luminous fill is within the genuine range and the outer metal is borderline.
- **How it is counted:** it is not counted as a defect.
- **The note's advice:** a sharper original photo may settle it; an enlarged copy of the same photo cannot.

### Close-ups
- A thin pink dotted outline marks where the genuine luminous fill is expected. It is placed by the other markers'
  ring plus the genuine per-hour nominal.
- It is display only and does not affect any decision.

### Owner's photos (run locally; not published)

| Photo | Marker | Result |
|---|---|---|
| Batgirl | 7 | **No defect.** 0.89 % R, beyond the genuine GMT range but under the 1.00 % floor |
| Batgirl | 8 | **No defect.** 0.93 % R, beyond the genuine GMT range but under the 1.00 % floor |
| Batgirl | 12 | **Worth a look.** Outer metal 0.68 % R high against a genuine maximum of 0.32 %, still within this photo's uncertainty by about 0.01 px. Not forced to a defect. |
| Batgirl | 9 baton | Worth a look (outer-metal rotation) |
| Genuine GMT control | All | Every round marker free of a hand is assessed; no defects (the shallow-separator fix holds) |
| QC 38097 | 12, 9 | Defect (outer metal) |
| QC 38097 | 1 round | Defect (above 1.00 %) |
| QC 38097 | 8, 10 rounds | Now under the floor |
| Old 116710 control | Rounds | Withheld (lit bevel merged with the lume) |

### Final regression

Run 38581584394, on the final decision code (commit `ae9b828`) with the bundled references.

| | GMT 126710 | Submariner 124060 |
|---|---:|---:|
| Genuine photos with a pose / watches | 67 / 39 | 133 / 76 |
| Genuine photos with any "Defect detected" | **0** | **2** (one physical watch) |
| False defects: triangle / baton / round | 0 / 0 / 0 | 1 / 1 / 0 |
| Genuine rounds held by the 1.00 % floor | 0 | 2 (both on that same watch) |
| Genuine outer metal "Worth a look" | 2 of 204 | 4 of 532 |
| Repeat photos: same outcome in every photo | 29 of 29 pairs | 181 of 181 pairs |
| Assessed markers using known-shape inference | 0 | 0 |

**Coverage.** Genuine round markers assessed: GMT 247 of 544, Submariner 619 of 1,064. The main reasons for "Cannot
assess" are low resolution and a hand over the marker.

**The two Submariner genuine false defects.**
- Both photos belong to one dealer listing (SwissWatchExpo 47665, watch `auto_swe_124060_053392110b92`).
- **What was flagged:**
  - photo `f27a64f8`: the 12 triangle, 0.46 % R right;
  - photo `c321f617`: the 9 baton, 0.67 % R.
- **The pattern:** the same watch's other markers also read displaced, and the genuine calibration had already marked
  it as a far-out outlier on 12 marker readings.
- **What the diagnostics showed:**
  - edge noise 0.3–0.5 px;
  - no known-shape inference;
  - merged bevel 0–11 %.
- **Conclusion:** the pattern is photo-wide, consistent with dealer retouching or a photo that is not straight-on. It
  does not point to one marker's code path.
- **Not reproduced or fixed:** the originals could not be fetched outside CI, so no implementation bug was
  demonstrated, and none was fixed.
- **Recorded as a known limitation.** The references were not altered to hide these flags.

**Public QC controls** (reviewer labels are not alignment ground truth):

| QC case | Result |
|---|---|
| GMT "crooked 6" (rejected) | 6 baton defect "too high"; 9 and 12 also flagged |
| GMT "accepted", photo 1 | The 8 round is now held by the floor. The 12's outer metal reads Defect "too high" under the 1.5.2 allowance (k = 2). |
| GMT "accepted", photo 2 | No defects |
| GMT "accepted", photo 3 | Too low resolution |
| Sub "rejected 12" | 12 outer metal Defect "too high" (identified) |
| Sub "accepted" | Photos 1 and 5 both report the 12 (lume left and outer metal high); the 11 round is now held by the floor |
| Sub "control" | The four round flags of earlier runs are now all under the floor; no defects |

### Known limitations (unchanged unless stated)
- **The photo must be straight-on.** Photos that are not, or are heavily retouched, can shift several markers together
  and cause false defects, as with the one Submariner dealer listing above.
- **A borderline outer-metal 12 depends on resolution.** It turns into a defect only when a sharper original photo
  separates it from the uncertainty.
- **Coverage is limited:**
  - dials under 400 px across are withheld;
  - a hand anywhere on a marker withholds it;
  - known-shape inference is implemented and unit-tested but was not used by any assessed genuine marker.
- **Signing:** builds use a throwaway debug key (no shared signing-key secret in the repository). Uninstall any
  earlier Watch Align before installing.

## 1.5.4: corrections from the 1.5.3 audit

1. **Incomplete measurements no longer pass.**
   - A marker reads "No defect" only when every alignment component it has was measured: position (radial and
     sideways) and, for triangles and batons, rotation.
   - When a component cannot be measured (for example a baton with both ends hidden), the marker reads "Cannot assess".
     The reason names the missing component and says the measured part is within the genuine range.
   - A confirmed defect still stands when another component is missing.
   - Missing reference or measurement numbers (NaN) fail closed.
   - The "Not measured: …" explanation is never hidden by an outer-metal note.
2. **Round markers are fitted exactly at any resolution.**
   - **Cause:** the rays start at the expected centre, and the old linear approximation of a displaced circle was read
     as edge noise at high resolution. A clean 2 % R shift became "Cannot assess" at 600 px.
   - **Fix:** rounds are now re-centred on the fitted centre, and the same measured edge points are re-expressed
     exactly, until the fit converges.
   - The 0.8 px edge-noise gate is unchanged.
3. **Close-ups show only accepted evidence.**
   - The cyan and silver luminous geometry is drawn only when the luminous fit itself was accepted. An outer-metal
     defect no longer restores geometry that was rejected as bevel or glare.
   - An outer-metal rotation no longer draws the lume's axis, which was wrong evidence. No axis is drawn instead.
4. **Changing model during a check is safe.**
   - Each check captures the photo and model it started with, and loaded data is keyed by model id.
   - A result is discarded when the photo or model changed meanwhile.
5. **Photo import applies EXIF orientation.** Rotation and mirroring are applied before the preview and the analysis,
   so a sideways-stored phone JPEG is analysed upright.
6. **Shallow separators:** for round markers, the plateau exit and separator acceptance now both use the documented
   20 % criterion (`SEPARATOR_SHARE`), measured from the plateau. Triangles and batons are unchanged.
7. **CI instrumentation:**
   - The emulator job adds x86_64 native code only for its own test build (`-PtestAbi=x86_64`); the delivered APK
     stays arm64-only.
   - Its extraction step now uses the real package `com.watchalign.mobile.poc`.
8. **The outer metal must be measured too.** This was adopted from the independent PR #54 after review.
   - The rule: a triangle or baton whose outer metal could not be measured is never an unqualified "No defect".
   - Why: a misplaced surround can sit around a correctly placed lume, which was the owner's Batgirl 12 case.
   - When it applies: the metal is not assessed, no metal measurement was produced, or the comparison could not run
     or failed.
   - The result: such a marker reads "Cannot assess". The reason says the luminous fill is within the genuine range.
   - Confirmed luminous or metal defects stand, and round markers are unaffected.
9. **Enlarged photos (qualified, not fixed):**
   - The outer-metal 12 allowance has a pixel term, so the same normalised reading can be judged more strictly on a
     photo with more pixels. An enlarged (resampled) photo is not detected and would count as a sharper one.
   - The note no longer claims enlargement cannot change the result. It asks the user not to enlarge, and says the
     check cannot yet tell an enlarged photo from a sharper one.
   - Preventing this needs a validated estimate of a photo's native resolution, which does not exist yet.
     Known limitation.

**Documented, not changed:**
- **Duplicate photos in the luminous calibration.** The calibration does not apply the earlier measured exclusions
  of reused photos. Excluding them would tighten the Submariner round radial envelope from 0.3627 % to 0.3461 % R.
  - This changes no round verdict, because the 1.00 % R visual floor is the larger threshold.
  - Rebuilding the references is a recalibration and was not done. It belongs to the next calibration.
- **Outer-metal ring definition.** The outer-metal baton reference uses Alpha107's ring, which includes the judged
  baton. A displacement is partly absorbed by it (0.40 % R read as 0.33 % R). The genuine references use the same
  definition, so the comparison is like for like. The claim that references never use the judged marker applies
  to the luminous check only.

### 1.5.4 validation

Regression run 38588906217 on the final decision code (`22d7052`), with the genuine references and visual thresholds
unchanged.

| | GMT 126710 | Submariner 124060 |
|---|---:|---:|
| Genuine photos with a pose | 67 | 147 |
| Genuine photos with any "Defect detected" | **0** | **2**, the same dealer listing as in 1.5.3 (1 triangle, 1 baton) |
| Genuine triangles and batons withheld only by the new outer-metal assessability rule | 1 of 111 | 1 of about 380 |
| Repeat photos: same outcome in every photo | 32 of 32 | 190 of 190 |
| Public QC controls | unchanged from 1.5.3 | unchanged from 1.5.3 |

**Round coverage against 1.5.3, same photos** (run 38587729590):
- GMT: +1 round assessed.
- Submariner: 6 fewer rounds assessed, about 1 %, from the consistent 20 % separator criterion.

**Owner's photos (local, not published):** outcomes are identical to 1.5.3.
- Batgirl: 7 and 8 No defect; 12 Worth a look.
- Genuine GMT: all unobstructed rounds assessed, no defects.

**On-device tests:** the emulator instrumentation job now runs for the first time since 4 Oct.
- Run 38588915705: 6 tests on an API 35 emulator.
- Included: the three EXIF orientation tests of the real upload path.

## 1.5.5: false hand warnings from the marker's own reflections

This release contains only one targeted correction, in the per-marker hand check (`Alpha99MarkerInterference`).

**Unchanged:** the genuine references, the 1.00 % round visual tolerance and the 20 % bevel-merge limit.

**The two exclusions**
1. **Glint on the surround.** A bright structure touching the marker that never leaves the glow just outside the
   marker's metal is the marker's own reflection, not a hand. "Just outside" means the halo plus 0.012 R
   (`GLOW_BAND`).
   - Owner photo: GMT 11 was withheld for a reflection on its surround's shoulder.
2. **Edge reflection inside the lume.** A thin streak across the marker face that keeps a constant distance from the
   outline is the lume's own edge reflection, not a hand. "Constant" means within 0.006 R (`EDGE_STREAK`), inside the
   face's outer band.
   - Owner photo: GMT 6 was withheld for a 1–2 px bright line along its long edge.

**What is still treated as a hand**
- A real hand reaching a marker runs on, straight out from the centre, well beyond these bands.
- A hand lying along a baton's axis sits deeper than the edge band.
- The seconds-hand line rule (from its lume dot) is unchanged.

**Owner and supplied photos:** 9 photos, 101 markers, deterministic A/B with each image's SHA-256 recorded.
- Exactly two markers change, the two false warnings:
  - GMT-25 11 becomes No defect;
  - GMT-13 6 becomes Cannot assess, with the true reason: its outer-metal edge on that side is not clean, and its lume
    is within the genuine range.
- The 20 real hand obstructions in those photos are all still withheld.
- One further false warning remains, and is not addressed by this release: the Batman QC 2. Broad glare reaches
  0.06 R beyond its metal, which this rule cannot separate from a real hand tip, so the marker stays withheld (fails
  closed).

**CI A/B against 1.5.4 on the same verified genuine photos** (workflow `watch-align-ab.yml`, run 38613197456)
- Both builds ran in one job on one photo set. Every image was checked against its catalogue's SHA-256 before use and
  checked again after both runs. The references and model spec were byte-identical in both builds.
- GMT 126710: 77 genuine photos, 747 markers (set digest `3291e4fe…5a1128`). 5 markers change; there are 0 new defects
  and 0 lost defects. Public QC controls: 3 photos, 33 markers, no changes.
  - 3 markers previously withheld as "hand" are now still withheld, for a real measurement reason: two 1 o'clock rounds
    are irregular by 1.2–1.3 px.
  - 2 markers previously withheld as "hand" are now No defect.
  - One 12 o'clock triangle (photo `fc62906af2b272de`) changes from No defect to Cannot assess.
- **The triangle change.** The triangle itself is measured identically in both builds; it changes indirectly.
  - The growth check compares each marker's edge offset with the other markers in the same photo.
  - The 4 o'clock round in that photo was previously wrongly excluded as a hand. It is now included in the comparison,
    which places the triangle's edge 2.8 px further out than the rest.
  - The triangle is therefore withheld. This fails closed and adds no defect.
- Submariner 124060: 372 genuine photos, 1967 markers (set digest `5c72c11c…3a1c4e`). 19 markers change; there are 0
  new defects and 0 lost defects, and no measurement value changes. Public QC controls: 4 photos, 48 markers, no
  changes.
  - Every change is a marker previously withheld as "hand".
  - 2 of them are now No defect.
  - 17 are still withheld, now for their real reason: bevel merge (23–69 %), an irregular edge, a shape mismatch, or
    too few other markers.
- In both models, every photo's SHA-256 was unchanged after both runs.
