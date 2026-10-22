# Watch Align measurement architecture review (research, 22 October 2026)

**Status:** research only.
- Production measurement, reference geometry, QC thresholds, classification rules and calibration assets are
  unchanged.
- Nothing has been released, merged or uploaded.
- A recommendation that changes production behaviour needs the owner's approval (section 10).

**Evidence:**
- 230 genuine photographs of 203 physical watches (GMT 126710, Submariner Date 126610 family, Submariner 124060) and
  18 replica photographs (6 replica watches, 124060), all exact-SHA-verified originals.
- The unchanged production chain was run on each, and on 82 genuine photographs under about 20–25 controlled perturbations
  each.
- Measurement CI run `39735723021`; comparison run `39740448417`.
- The committed evidence (`docs/image-archive/face-on-evidence.jsonl`, 972 records) was analysed independently.

**Reproduce:**
- `python -m tools.research.measurement_architecture.genuine_offline docs/image-archive/face-on-evidence.jsonl`
  (committed numbers only).
- `.github/workflows/measurement-architecture.yml`:
  - the driver `tools/desktop-harness/drivers/ArchMeasure.java` runs the production code and dumps raw per-marker
    quantities;
  - `methods.py` holds every competing method, computed from the same raw measurements;
  - `evaluate.py` holds the statistics;
  - `select_items.py` makes the photo selection.
- `.github/workflows/measurement-architecture-compare.yml` re-runs only the comparison on a finished run.

Units:
- **% R** is percent of the dial radius.
- Tangential is + clockwise; radial is + outwards.
- Statistics count **physical watches**: one value per marker per watch, the median over that watch's photos.
- Learned constants are fitted leave-one-watch-out.

---

## Summary

1. **The QC result is decided by one pathway:** `ClearlyOff` with `MetalFit`, reached through `Alpha99Pipeline`. It
   measures each marker's outer-metal centre against two **different** references:
   - **sideways:** against hour lines from the printed minute track (an absolute dial-frame reading);
   - **in/out:** against a ring fitted to the other markers (a relative reading).
2. **The sideways pattern is parallax**, not registration error, model geometry or a genuine manufacturing pattern.
   - The applied markers stand above the printed minute track, so any camera tilt shifts all of them together in the
     registered dial frame.
   - One physical constant explains it in all three families: effective height k = 2.28 % R per unit tan(tilt).
   - This shift dominates production sideways readings (per-photo first-harmonic R² 0.81–0.86).
   - It reaches about 0.4 % R at the archive's 10.6° tilt limit.
3. **Recommended method: a two-level decomposition.**
   - Each marker is measured against a robust (biweight), leave-one-out similarity ring of the **other** markers.
   - The ring's own placement on the dial is measured separately: translation minus predicted parallax, rotation
     against the minute track, and scale.
   - Compared with production on the same photographs, it gives:
     - about half the genuine spread;
     - no tilt dependence;
     - immunity to registration and perspective errors;
     - detection of a 0.5 % R single-marker defect in 96–100 % of cases at the genuine 99th-percentile threshold,
       against 48–72 % for production.
   - It does not normalise away a whole-ring defect, because ring placement is reported as its own finding.
4. **Shared calibration by shape is supported.** Genuine spreads of the recommended readings are the same in every
   family (round position sd 0.049–0.054 % R). Small per-hour reference offsets (up to about 0.09 % R) exist, and some
   are shared across families.
5. **Legacy engines:**
   - The watch-family calibrator (`CalibMeasure` → `WatchAlignCoreV13`) measures with a legacy 124060-only engine whose
     composite metrics are not used by the app; its envelopes cannot be transferred to the app.
   - `MainActivity` (the same legacy engine) is not reachable from the app's launcher.

---

## 1. Audit of the measurement pathways

| Component | What it does | Feeds the QC result? |
|---|---|---|
| `AutomaticDialOverlay` (+ `Alpha91MinuteLatticeFitter`, `DialEdgeEllipseFit`) | Projective homography H from the **printed minute-track tick lattice** (markers and hands never pull it); dial-edge ellipse as a check | **Yes**, as the frame for everything |
| `Alpha94MarkerMeasurement` | Outer-metal outline per round/baton marker (outermost bright→dark edge; RANSAC circle / four-side fit); **similarity ring** (translation, scale, rotation; Huber 1 px) and per-marker local residuals; triangle research-only | **Indirectly:** its leave-one-out ring places each marker's yellow reference |
| `LuminousAlignment.ringWithout` / `AlignmentAnalysis` | Yellow outer-metal reference = master outline moved by the Alpha94 ring of the **other** markers; luminous fits (`AlignmentDecision`) | Yellow: **yes**. Luminous verdicts: overwritten by `ClearlyOff`; kept as text only |
| `MetalFit` | Fixed-shape outer-metal fit against the yellow outline: position (and rotation) with reliability grades and the hand check | **Yes** (centre `cObs`, tilt) |
| `ClearlyOff` | Hour lines from minute ticks; per-marker metrics and decision (replica-QC and strict-genuine limits) | **Yes, decides** |
| `TriangleTopEdge` | 12 radial from the top edge against a circle scaled by the round markers (owner calibration, 21 Oct) | **Yes** (triangle radial) |
| `WatchClassification` | Whole-watch label from the `ClearlyOff` markers | **Yes** (label) |
| `Alpha99Findings`, `MarkerMismatchResearch`, `Alpha98Closeups` | Earlier findings, close-ups, mismatch research | Display only |
| `ArchiveEvidence` (harness) | The same production chain, numbers per marker | Research archive |
| `CalibMeasure` (harness) → `WatchAlignCoreV13` → `Sub124060QcAnalyzer` / `Sub124060Calibration` | Legacy 124060 engine. Composites: ring radius median, spacing RMS, 3–9 / 12–6 line offsets, 12 rotation/gap/centring | **No.** Not reached by the app's launcher (`PerspectiveOverlayPocActivity` → `UnifiedMarkerReview` → `Alpha99Pipeline`) |
| `MainActivity` | The same legacy engine | Not reachable (no reference to it) |

### What actually determines the Android result

For each marker the decision is `ClearlyOff.decide`:

| Shape | Reading | Reference | Frame |
|---|---|---|---|
| Round, baton | position = hypot(sideways, in/out) | sideways: hour line from the minute ticks through the registered centre; in/out: \|centre\| against ring scale × \|yellow\| | **sideways absolute, in/out ring-relative** |
| Baton | tilt | MetalFit rotation against the ring-rotated yellow, **minus** the hour-line-pair deviation | ring-relative, then corrected again for the registration (§1.1) |
| Triangle | sideways | hour line | absolute |
| Triangle | radial | `TriangleTopEdge`, against a circle from the round markers | ring-relative |
| Triangle | rotation | top edge against minute ticks 59–1 | absolute (no parallax: a translation does not rotate) |
| Triangle | gap | top edge against the tick ends | absolute (parallax-affected) |

### 1.1 Defects found in the production measurement

- **Mixed reference frames in one "position".**
  - Sideways is absolute, so it includes parallax and attenuated registration error.
  - In/out is ring-relative, so it is blind to a whole-ring shift or scale.
  - The combined distance therefore has an anisotropic, tilt-dependent noise floor.
- **Tilt double-compensation.**
  - Production tilt = MetalFit rotation against the ring-rotated yellow, minus the hour-line deviation.
  - A pure registration rotation of 0.3° (synthetic, no physical change) changes production baton tilt by
    **+0.30°**, one-for-one. A whole-ring rotation of 0.4° is invisible to it (error −0.39°).
  - In real photographs H is fitted to the same ticks, so the hour-line deviation is tiny (median 0.003°, p95 0.009°).
    The practical impact today is therefore small, but the construction is wrong.
- **In/out spill.** Production in/out of other markers moves by up to 0.09 % R (p95) when one marker moves 0.5 % R,
  through the ring scale and the yellow ring.
- **Legacy calibration engine.** The watch-family calibrator measures different quantities with a different detector.
  Its limits cannot be applied to the app's readings.

## 2. The sideways pattern

**Observation** (committed evidence, eligible genuine markers):
- Per photo, the sideways readings follow sideways(h) = a + b cos θh + c sin θh with median R² 0.81–0.86, which is the
  signature of **one shared translation** of all markers.
- The mean translation points towards 12 (c = −0.17 % R on Date, −0.15 on GMT, −0.07 on No-Date).

| Hypothesis | Test | Result |
|---|---|---|
| Registration (dial-centre) error | A 0.3 % R shift of H, injected | Moves production sideways by only about 0.2× (p95 0.07 % R), the expected (1 − r_marker/r_tick) attenuation; too small, and random in sign |
| Model geometry | Production sideways does not use the reference geometry at all | Cannot cause it |
| Genuine manufacturing pattern | Per-photo translation against each photo's own camera tilt | Correlated with tilt (r = 0.75, pooled) |
| **Parallax of raised markers** | Predicted shift = k · tan(tilt) along the far-side direction, both from H | **Explains it.** One constant fits all families: GMT 2.31 %, No-Date 1.92 %, Date 2.21 % (committed evidence); 2.28 % R (CI run, 217 photos). Intercepts ≈ 0: dealer photos are typically shot from slightly below the 6, hence the bias towards 12 |

Correcting the ring translation for the predicted parallax (held-out k):

| Ring translation | Before correction | After correction |
|---|---:|---:|
| \|t\| median | 0.184 % R | 0.102 % R |
| \|t\| p95 | 0.373 % R | 0.241 % R |

The remainder (p99 0.336 % R) is genuine ring placement plus residual registration and model error.

**Implication.** Treating this pattern as a defect, or widening limits to absorb it, both reduce accuracy. It must be
modelled (ring level) and removed from the per-marker reading (marker level).

## 3. Methods compared (identical raw measurements)

All methods use the same production outer-metal centre (`MetalFit`) and differ only in the reference:

| | Reference |
|---|---|
| **P** | production (§1) |
| **C** | chord: sideways against the line through the two opposite hour ticks (does not use the registered centre) |
| **F** | fixed genuine geometry in the registered dial frame |
| **FP** | F minus the parallax prediction |
| **LA** | production's yellow reference: Alpha94 leave-one-out ring, Alpha94 edges |
| **LR** | leave-one-out similarity ring of the other markers' outer-metal centres, Huber (production's robust weights) |
| **LB** | as LR with Tukey biweight (c = 0.45 % R), so one strongly displaced marker does not move the ring the others are measured against |
| **LT** | as LR with translation and rotation only |
| **Ring level** | similarity of all eligible ring markers against the fixed geometry: translation − parallax, rotation, scale |

## 4. Quantitative evidence

### 4.1 Genuine populations (eligible markers, watch-level; pooled by shape across families)

p99 of position size (round, baton), or of the combined triangle position, % R:

| Method | Round (1,268–1,283) | Baton (425–441) | Triangle (188–191) | \|position\| vs tilt, r |
|---|---:|---:|---:|---:|
| P | 0.475 | 0.539 | 0.446 | +0.24 / +0.23 / +0.19 |
| C | 0.577 | 0.623 | 0.502 | +0.36 |
| F | 0.585 | 0.625 | 0.547 | +0.36 |
| FP | 0.430 | 0.499 | 0.461 | −0.02 / 0.00 / +0.13 |
| LA | 0.262 | 0.292 | 0.319 | +0.04 |
| LR | 0.252 | 0.288 | 0.328 | 0.00 |
| **LB** | **0.254** | **0.289** | **0.328** | **0.00 / +0.01 / +0.11** |

Components:
- Production sideways sd is 0.182 % R (round) against 0.081 for LB tangential.
- Production in/out sd (0.085) is already close to LB radial (0.075), because it is already ring-relative.
- Baton tilt sd is 0.30° and triangle rotation sd 0.46° for **every** method. Orientation is limited by the edge fit,
  not by the reference.

### 4.2 Synthetic known geometry

82 genuine photographs. Each change is applied inside the photo (Lanczos/cubic move of the marker's own pixels), and
the photo is re-measured by the production chain. The table gives the error of the measured change against the injected
change: 95th percentile \|error\| in % R or °.

| Perturbation | Role | P | F | FP | LR | **LB** |
|---|---|---:|---:|---:|---:|---:|
| one marker moved 0.5 % R tangentially | moved marker | 0.021 | 0.020 | 0.020 | 0.020 | 0.020 |
| | other markers, tangential | 0.000 | 0.000 | 0.000 | 0.121 | **0.037** |
| one marker moved 0.5 % R radially | moved, radial | 0.089 | 0.015 | 0.015 | 0.016 | 0.016 |
| | other markers, radial | 0.094 | 0.002 | 0.002 | 0.120 | **0.035** |
| one baton or triangle turned 1° | moved, tilt | 0.050 | 0.050 | 0.050 | 0.050 | 0.050 |
| registration shifted 0.3 % R (no physical change) | all, tangential | 0.070 | 0.299 | 0.299 | 0.007 | **0.007** |
| registration rotated 0.3° | all, tilt | **0.310** | 0.300 | 0.300 | 0.008 | **0.008** |
| registration scaled 0.5 % | all, radial | 0.019 | 0.406 | 0.406 | 0.013 | **0.013** |
| flat perspective 8° (pose re-estimated) | all, tangential | 0.008–0.023 | 0.008–0.020 | 0.40–0.44 (no parallax in a flat warp) | 0.014–0.022 | **0.015–0.022** |
| resolution to a 250 px dial | all, tangential / radial | 0.048 / 0.123 | 0.042 / 0.090 | 0.044 / 0.089 | 0.055 / 0.090 | 0.054 / 0.086 |
| resolution to a 200 px dial | all, tangential / radial / tilt | 0.066 / 0.138 / 0.37° | 0.068 / 0.095 | 0.066 / 0.088 | 0.069 / 0.085 | 0.066 / 0.085 / 0.36° |

Whole marker set moved against the printed minute track. These are physical whole-ring defects; each cell says whether
the per-marker reading shows the change (p95 \|error\|):

| Ring move | P | F / FP | LR / LB | Ring-level estimator |
|---|---|---|---|---|
| translation 0.4 % R | sideways yes (0.015), in/out **no** (0.41) | yes (0.014–0.018) | no (0.40), by design | **recovered:** \|t\| error p95 0.015 % R |
| rotation 0.4° | sideways yes, tilt **no** (0.45) | yes (0.016; tilt 0.07) | no, by design | **recovered:** bias +0.001°, sd 0.003 |
| scale 0.5 % | in/out **no** (0.43) | yes (0.020) | no, by design | **recovered:** bias −0.002 %, sd 0.008 |

The same estimator under registration errors moves by exactly the injected amount (0.300 % R, −0.300°, −0.497 %). A
registration error and a whole-ring defect cannot be told apart from one photograph, so the ring level carries the
registration uncertainty in its genuine population. Its genuine p99 limits are 0.336 % R, 0.172° and 0.308 %.

Reference-geometry errors (offline: the reference positions in the records perturbed):
- **One marker's reference moved 0.3 % R:** that marker's reading moves 0.300 % R in every reference-using method,
  P's in/out included. Other markers move up to 0.081 % R (LB) or 0.092 (LR).
- **Every reference scaled +0.5 %:** absorbed by LR/LB (0.000). F, FP and C read −0.40 % R radially; LT −0.43.
- So the per-hour reference geometry must be accurate; a held-out per-hour correction is evaluated in §5.

### 4.3 Detection of a single-marker defect

A 0.5 % R move, detected when the moved marker exceeds the method's own genuine pooled p99:

| Method | Round (545) | Baton (185) | Triangle (80) |
|---|---:|---:|---:|
| P | 72 % | 48 % | 72 % |
| F | 38 % | 25 % | 46 % |
| FP | 73 % | 56 % | 79 % |
| LR | 100 % | 99 % | 96 % |
| **LB** | **100 %** | **99 %** | **96 %** |

At the same genuine false-positive rate, LB's threshold is about half of production's (0.25 against 0.48 % R for
rounds). Production needs roughly a 0.6 % R defect for the detection LB reaches at about 0.3 % R.

### 4.4 Replica photographs (6 replica watches, 124060)

The replica set is small and the 10:10 hands make many replica markers ineligible, so this is illustrative, not proof.

- **Flags across independent photographs.** A marker over the method's genuine p99 in one independent photograph of a
  replica watch was:

  | Method | Flagged again in the other photograph | Flagged in one photograph only |
  |---|---:|---:|
  | P | 0 | 6 |
  | LB | 4 | 2 |

  A physical defect repeats across photographs; production's flags behaved like photographic artefacts.
- **Replica `clean_1lnn2kz`** (three photographs, two independent):
  - LB flags the 5, 6, 9 and 12 in both independent photographs (3, 4, 7 also in one, 10, 11 in another). Production
    flags the 7 or the 9 in single photographs.
  - Its ring sits 0.25 % R off the parallax prediction in all three photographs, within the genuine p99 (0.336).
- **Replica `vsf_1gwpcm4`:** production's large same-sign sideways readings at 6.9° tilt are not repeated by LB (nor
  by FP). They are parallax.

## 5. Shared calibration populations

| Recommended reading (LB) | Family sd (GMT / No-Date / Date) | Verdict |
|---|---|---|
| Round position | 0.049 / 0.053 / 0.054 % R | Pool |
| Baton position | 0.057 / 0.060 / 0.057 % R | Pool |
| Triangle position | 0.048 / 0.079 / 0.066 % R | Pool with care (No-Date wider) |
| Baton tilt | 0.28 / 0.28 / 0.32° | Pool |
| Triangle rotation | 0.36 / 0.43 / 0.48° | Pool |

- **Spreads:** the spread is the same in every family, so **one population per shape**, across families and clock
  positions, is justified for the noise and manufacturing spread.
- **Per-hour means:** small systematic per-hour means exist in LB tangential, up to 0.08 % R (e.g. Date 8: −0.054,
  t = −8.8; GMT 1: −0.077).
  - They correlate between GMT and Date (r = 0.93) but not with No-Date (r = 0.02–0.30).
  - They are part reference geometry (per model) and part measurement.
- **Held-out correction:** removing per-family, per-hour means learned on other watches reduces LB round tangential
  p99 only from 0.228 to 0.207 % R, and leaves the rest unchanged.
- **Recommendation:**
  - Pool the spread by shape across families.
  - Keep a **model-specific per-hour reference correction**, learned from genuine watches and stored with the model
    geometry, since the offsets differ between Date/GMT and No-Date dials.
  - Never pool across the Date window: the 3 is not a marker on Date and GMT dials.
- Replica markers are never averaged; every marker is judged on its own reading.

## 6. Recommended measurement approach

**Two levels, from one physical measurement per marker.**

1. **Frame.** Keep H from the printed minute track (`AutomaticDialOverlay`). It is the only reference that markers
   and hands do not influence.
2. **Marker geometry.** Keep the outer-metal fit (`MetalFit`) for each marker's centre and orientation, with its
   reliability grades and the hand check. It is the actual metal boundary, and every method agreed on it within
   0.02 % R on synthetic moves.
3. **Marker level (individual defects).**
   - Fit a robust **biweight** similarity ring (translation, rotation, scale) to the **other** eligible round markers
     and batons, against the model geometry with its per-hour correction.
   - Report each marker's tangential, radial and rotational residual.
   - This removes parallax, registration, perspective and camera-distance effects, and does not let one defective
     marker move the others' references.
4. **Ring level (whole-set defects).** Report, from all eligible markers, against genuine p99 limits (0.336 % R, 0.172°,
   0.308 %; to be confirmed on more data):
   - **placement:** translation minus k · tan(tilt) · far-side, with k = 2.28 % R from genuine watches;
   - **rotation** against the minute track;
   - **scale.**

   A whole-ring defect is therefore reported, never normalised away.
5. **Triangle extras.**
   - Keep the owner's minute-track-relative **rotation** (ticks 59–1) and **gap**. Correct the gap for parallax along
     the 12 axis, or judge it with a tilt-aware allowance.
   - Measure triangle sideways and radial at marker level like every other marker.
6. **Uncertainty.** Each reading carries:
   - photographic uncertainty: resolution, from the synthetic 250/200 px tables; the MetalFit standard error;
   - ring-prediction uncertainty: from the number of eligible others.

   It is kept separate from genuine manufacturing variation (the pooled population). Below a reliable level the
   marker is Can't check, as today.

**Why not the others:**
- **F and C** carry parallax and registration errors at full size.
- **FP** depends on the parallax model being exact; it adds error on flat or unusual photographs (perspective test) and
  still has twice LB's genuine spread.
- **LR** (production's Huber weights) lets a defective marker move its neighbours' references (spill 0.12 % R).
- **LT** cannot absorb scale.
- **LA** uses a different edge detector (Alpha94) for the reference than for the marker, with worse spill.

## 7. Retain, replace, remove

| Component | Recommendation |
|---|---|
| `AutomaticDialOverlay` / minute-lattice H | **Retain** (frame) |
| `MetalFit` outer-metal fit, reliability grades, hand check | **Retain** (the physical measurement) |
| `ClearlyOff` sideways from hour lines | **Replace** with the marker-level residual (LB tangential) |
| `ClearlyOff` in/out (ring scale × yellow) | **Replace** with LB radial (same reference type, no spill through the scale) |
| Baton tilt (yellow-relative minus hour-line deviation) | **Replace** with LB rotational residual (removes the double compensation) |
| Triangle rotation against ticks 59–1, gap | **Retain**; add a parallax-aware gap |
| `TriangleTopEdge` radial | Retain until LB radial is validated on the triangle top edge |
| Alpha94 similarity ring | **Replace** in the reference role by the biweight ring on outer-metal centres; keep Alpha94's detector only as a cross-check |
| Luminous `AlignmentDecision`, `Alpha99Findings` verdicts | **Remove** from the analysis path (display-only today) |
| `WatchAlignCoreV13` / `CalibMeasure` / `MainActivity` | **Remove** from calibration (legacy 124060 engine, not the app's measurement); replace `CalibMeasure` with an `ArchMeasure`-style dump of the authoritative measurement |
| `ArchiveEvidence` | **Extend** to the raw centres and references (as `ArchMeasure`), so archive validation and calibration read the same numbers as the app |

**One authoritative implementation.** A single Java `MarkerGeometry` stage (H → MetalFit centres → biweight ring →
marker and ring readings with uncertainty). It would be consumed unchanged by:
- the app (QC and classification);
- the genuine reference analysis;
- `ArchiveEvidence` (archive validation);
- the calibrator.

Only the reliability rules and the statistical limits differ by purpose.

## 8. Expected improvement and remaining risks

**Expected:**
- About 2× smaller genuine spread and thresholds for position.
- No tilt dependence, so fewer false Replica results on oblique photographs.
- Immunity to registration and perspective errors.
- 0.5 % R single-marker defects detected in 96–100 % of cases (production 48–72 %).
- Whole-ring defects reported explicitly.
- The calibrator measures what the app measures.

**Risks and limits:**
- **Strict genuine limits.** The frozen strict-genuine and replica-QC limits were set on production readings. They
  **must not** be reused with LB readings; new limits need the owner's approval under `CALIBRATION_BASELINE` change
  control.
- **Independent-photo repeatability of genuine watches** is not established. The "repeat" photographs in the evidence
  are mostly re-encodings of one image. The replica set has only 6 watches (124060) and few eligible markers.
- **Synthetic moves** shift pixels in the photo. They test the measurement and its references, not 3D effects, and the
  flat perspective test has no parallax.
- **Biweight tuning** (c = 0.45 % R) is set from the genuine noise, not optimised. Several neighbouring defective
  markers, or more than about half the ring, defeat any ring reference. The ring level and the replica's consistent
  flags are the safeguard; this needs dedicated tests.
- **The parallax constant** is a single effective value, which may vary with marker design (it was consistent across
  three families).
- **Orientation** (baton tilt sd 0.30°, triangle 0.46°) is not improved by any reference change; it is limited by the
  edge fit.
- **Per-hour reference offsets** (up to about 0.08 % R) need a model-specific correction table and enough genuine
  watches per hour. The 2 o'clock marker is thin (§10 of `docs/image-archive/FACE-ON-VALIDATION.md`).

## 9. Answers to the brief

1. **Audit:** section 1.
2. **What decides the Android QC result:** `ClearlyOff` (§1); `WatchClassification` builds the label from it.
3. **Production against historical calibration:** the calibrator uses `WatchAlignCoreV13` composites, not the app's
   measurement. Production itself mixes absolute and relative frames and double-compensates tilt (§1.1).
4. **The sideways pattern:** parallax of raised markers, one constant across families (§2).
5. **Ring method against alternatives:** §3–4. The production Huber ring spills; the biweight ring does not. The ring
   needs a ring-level companion.
6. **Quantitative evidence:** §4.
7. **Recommendation:** §6.
8. **Retain, replace, remove:** §7.
9. **Improvements and risks:** §8.
10. **Phased plan:** §10.

## 10. Phased implementation proposal

**Phase A — research validation (no production change; can continue on this branch):**
1. Genuine independent-photo repeatability: several photographs of the same genuine watch at different tilts. This is
   the missing evidence.
2. More replica and owner-reviewed defect photographs across all three families, measured by `ArchMeasure`.
3. Multi-defect synthetic tests (two adjacent markers, half the ring), biweight tuning, and the triangle top edge
   under LB.
4. A per-hour reference correction table per model, learned leave-one-watch-out; and the parallax-aware triangle gap.
5. Proposed LB and ring-level genuine limits, with uncertainty, for owner review. Not deployed.

**Phase B — shared implementation behind a flag (owner approval needed to start):**
1. A Java `MarkerGeometry` stage reproducing `methods.LB` and the ring level bit-for-bit. The research driver and unit
   tests compare it with this Python.
2. `ArchiveEvidence` and the calibrator switched to it (research consumers first); `CalibMeasure`/`WatchAlignCoreV13`
   retired from calibration.

**Phase C — production change (owner approval, a new dated calibration baseline):**
1. App QC reads `MarkerGeometry`, with new limits approved under change control and the old readings kept in the
   technical details.
2. Regression against every verified genuine photograph and the replica set; release only after review.

---

## Addendum: Phase A research validation (22 October 2026)

Source:
- `phase_a.py`, on the same measurement run `39735723021`; comparison run `39766088665`.
- 185 genuine photographs (173 watches) have at least 8 eligible ring markers. The limits use all 199–203 genuine
  watches with eligible readings.
- Nothing here is deployed.

### A.1 Several defective markers on one dial, and biweight tuning

0.5 % R displacements in random directions were added to the measured centres of eligible genuine markers. This was
done offline; §4.2 showed that a moved marker's centre is measured to 0.02 % R. Each cell gives detection of the
displaced markers / false flags on the clean markers, at the fit's own genuine p99:

| Ring fit | 1 marker | 2 adjacent | 2 opposite | 3 adjacent | 3 random | 4 random |
|---|---|---|---|---|---|---|
| Huber (production weights) | 100 % / 3.4 % | 99 % / 9.3 % | 100 % / 10.3 % | 99 % / 13.8 % | 99 % / 16.2 % | 98 % / 22.5 % |
| **biweight c = 0.30 % R** | **100 % / 1.5 %** | **100 % / 3.1 %** | **100 % / 2.5 %** | **100 % / 6.9 %** | **100 % / 7.6 %** | **99 % / 14.2 %** |
| biweight c = 0.45 % R (§3) | 100 % / 1.6 % | 100 % / 3.6 % | 100 % / 2.8 % | 100 % / 11.0 % | 100 % / 10.4 % | 99 % / 21.3 % |
| biweight c = 0.60 % R | 100 % / 2.0 % | 100 % / 5.5 % | 100 % / 4.9 % | 99 % / 16.4 % | 99 % / 15.3 % | 98 % / 28.3 % |
| biweight c = 0.90 % R | 100 % / 2.8 % | 99 % / 8.5 % | 100 % / 8.9 % | 99 % / 16.8 % | 99 % / 17.5 % | 97 % / 26.3 % |

Findings:
- Every displaced marker is still found, with three or four defects on one dial.
- The cost of several defects is extra flags on clean markers.
- The tighter biweight (c = 0.30 % R) halves those extra flags without changing the genuine thresholds (round p99
  0.259 against 0.254). **Recommended for Phase B: c = 0.30 % R.**
- With four or more defective markers, about 1 in 7 clean markers is also flagged. The watch is a QC failure anyway,
  but the close-ups must show each marker's own evidence.

### A.2 Proposed genuine limits for the recommended readings (for owner review; NOT deployed)

Watch-level p99, pooled across the three families, with a 90 % bootstrap interval over watches:

| Reading | Watches / values | p99 | 90 % interval | Max | Current strict genuine limit (production reading) |
|---|---:|---:|---|---:|---|
| Round position (LB) | 195 / 1,268 | 0.254 % R | 0.234–0.264 | 0.368 | 0.671 % R |
| … per-hour corrected | 195 / 1,263 | 0.240 % R | 0.218–0.257 | 0.379 | |
| Baton position (LB) | 195 / 425 | 0.289 % R | 0.253–0.315 | 0.327 | 0.752 % R |
| Triangle position (LB) | 188 / 188 | 0.328 % R | 0.257–0.433 | 0.433 | sideways 0.437, radial 0.373 % R |
| Baton tilt (LB) | 195 / 425 | 0.76° | 0.72–0.78 | 0.82 | 1.096° |
| Triangle rotation against ticks 59–1 (production) | 188 | 1.07° | 1.00–1.22 | 1.22 | 1.174° |
| Ring placement \|t − parallax\| | 199 | 0.338 % R | 0.26–0.44 | 0.44 | none |
| Ring rotation | 199 | 0.172° | 0.13–0.17 | 0.17 | none |
| Ring scale | 199 | 0.288 % | 0.25–0.32 | 0.32 | none |

**Whole-watch false-alarm rate.**
- A per-marker p99 flags 1 % of genuine markers, but **10.1 % of 199 genuine watches** have at least one eligible
  marker over these limits (about 11 markers per watch).
- Scaling all position limits by **1.09** flags 5 % of genuine watches; by **1.28**, 1 %.
- At the 1 % level the position limits are about **0.33 % R (round), 0.37 % R (baton) and 0.42 % R (triangle)**.
  That is roughly half the current round and baton limits.
- The owner's acceptance that some genuine watches fail the strict standard decides which level is used. That decision
  is the owner's, under `CALIBRATION_BASELINE` change control.

The per-hour reference correction helps only the round markers (p99 0.254 → 0.240); for batons and the triangle it is
within noise.

### A.3 Per-hour reference offsets (mean LB residual, tangential/radial, % R; * = |mean| ≥ 3 se)

| Model | Offsets |
|---|---|
| GMT 126710 | 1 −0.077/+0.028*, 4 +0.044/+0.014*, 5 +0.041/+0.060*, **6 +0.035/−0.095***, 8 −0.049/+0.022*; others within ±0.04 |
| No-Date 124060 | 4 −0.034/−0.004*, 6 −0.000/+0.053*, 10 +0.007/−0.028*; others within ±0.04 |
| Date 126610 family | 1 −0.046/+0.035*, **2 −0.061/+0.084*** (14 watches), 4 +0.022/−0.011*, 5 +0.053/−0.023*, 8 −0.055/−0.007*, 9 −0.033/+0.008*, 10 +0.039/−0.001*, 11 +0.043/−0.010* |

- The offsets are small (all below 0.1 % R) but real, and they differ by model.
- They belong in a model-specific reference correction stored with the model geometry. That is a reference-geometry
  change, so it needs the owner's approval before any production use.

### A.4 The triangle's clear gap and parallax

- The gap (production reading, 213 genuine triangles) correlates with the predicted outward parallax shift of the raised
  triangle (r = −0.33, slope −2.1). The slope is about twice the shift itself, consistent with the visible side wall of
  a tilted triangle also narrowing the gap.
- Correcting for it narrows the spread only slightly (sd 0.588 → 0.564 % R) and raises the genuine minimum from 2.34 to
  2.45 % R.
- Genuine variation dominates the gap. A parallax correction is worth applying, but it will not make the gap a much
  tighter test.

### A.5 Still open (needs new data)

1. Independent photographs of the same genuine watch, at different tilts.
2. More replica and owner-reviewed defect photographs in all three families.
3. Confirmation that the triangle's top edge behaves under the LB reading as it does under the owner's top-edge
   calibration.

---

## Phase B: shared implementation behind a flag (owner-approved 22 October 2026)

**Production behaviour is unchanged.**
- The new stage judges nothing, and `ClearlyOff` still decides every answer.
- It runs in the app only with `-Dwatchalign.markergeometry=on`. It is off by default; a unit test pins that.
- Even when enabled, it only adds a "Marker geometry (research, not judged)" block to the technical details.
- The 12 public photographs' archive decisions were re-run natively with the change and are byte-identical.

| Item | Where |
|---|---|
| The stage (Java, no Android/OpenCV dependency): per-marker biweight leave-one-out residuals (c = 0.30 % R), ring placement minus parallax (k = 2.28 % R per unit tan(tilt)), rotation, scale; archive eligibility rules | `android/app/src/main/java/com/watchalign/mobile/MarkerGeometry.java` |
| Flagged hook (`Output.geometry`) | `Alpha99Pipeline.java` |
| Research reference, same constant | `tools/research/measurement_architecture/methods.py` (`BIWEIGHT_C = 0.0030`) |
| Parity, unit level: 35 ArchMeasure records of the committed public photographs (originals and synthetic moves), 386 marker rows, inputs and Python results; plus known-geometry tests | `android/app/src/test/java/com/watchalign/mobile/MarkerGeometryTest.java`, `android/app/src/test/resources/marker_geometry/parity.csv` |
| Parity, every CI record: `ArchMeasure` emits the Java output (`mg`, `mg_ring`) beside the raw measurement, and the workflow fails on any difference | `tools/research/measurement_architecture/parity.py`, `.github/workflows/measurement-architecture.yml` |
| Archive validation reads the same measurement: `mg_tang_pct`, `mg_rad_pct`, `mg_tilt_deg`, `mg_eligible` per marker; `mg_ring` per photo | `tools/desktop-harness/drivers/ArchiveEvidence.java` |
| Calibrator adapter `marker_geometry_v1`: driver `GeometryMeasure` writes `marker.<hour>.tang/rad/tilt` and `ring.place_x/place_y/rot_deg/scale_pct`, eligible markers only; `ring.*` declares every round marker and baton as dependencies, so masks fail closed | `tools/desktop-harness/drivers/GeometryMeasure.java`, `tools/watch_calibrator/production_measure.py`, `run.py`, `marker_mask.py`, `tests/test_geometry_adapter.py` |

**Parity:** the largest \|Java − Python\| on the local records is 9.3e-11 % R (1,057 values), with eligibility identical.

**Legacy calibrator route.**
- `CalibMeasure` (`production_app_route_v1`, `WatchAlignCoreV13`) is kept only so the existing
  `calibration/models/124060.json` is not migrated (assets unchanged, as instructed).
- New or migrated configs should use `marker_geometry_v1`.
- Retiring the legacy adapter, and any production use of the new readings or their limits (Phase C), needs the owner's
  approval and a new dated calibration baseline.
