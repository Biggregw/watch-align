# Watch Align 1.5.0: alignment-only hour-marker check (14 Oct 2026)

Branch `feature/android-alignment-only-20261014`, built from Alpha107 (`outer-metal/integration-20261013` @ `5e6d565`).
Not merged into main. Rollback: install the Alpha107 APK (run 38493791860) or check out `5e6d565`.

## What the app does
For every hour marker of the selected model (GMT-Master II 126710: triangle, 2 batons, 8 rounds; Submariner 124060:
triangle, 3 batons, 8 rounds) the answer is **No defect**, **Defect detected** or **Cannot assess**.

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
  - **Inconclusive readings:** a reading beyond every genuine watch but within the allowance is shown as an
    "Inconclusive" note, not as a defect.
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
    - result: Inconclusive, by about 0.01 px. A photo of the same watch with a dial radius of 273 px or more would
      flag it.
- **Round markers:**
  - **Edge:** the lume edge is the steepest point of its fall; a shallower separator counts when a lit bevel follows
    it.
  - **Size check:** a fully seen round's size is checked only against a merged bevel (half a surround width). Its
    centre does not depend on its size.
  - **Owner's genuine GMT:** all seven rounds without a hand over them are now assessed, with no defects.
