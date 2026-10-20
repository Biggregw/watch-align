# Shared engine and tolerances: evidence review (20 Oct 2026)

Research only. No production code, threshold or reference is changed. Baseline: main `64caf8b` (1.6.3 plus PR #63).

## Questions and short answers

| Question | Answer from the evidence |
|---|---|
| Is one shared measurement engine feasible? | It already exists. The launcher (`PerspectiveOverlayPocActivity`) sends every family through `UnifiedMarkerReview` → `Alpha99Pipeline` → `ClearlyOff`/`MetalFit`. Families differ only in their asset folders. |
| Do families measure the same physical marker the same way? | Yes for sideways and tilt (identical to 0.03 % R and 0.007° across configs). In/out differs only by each family's reference radius. |
| Are common tolerances justified? | Mostly yes: clean-edge genuine spread is similar across families once the GMT baton reference offset is accounted for. |
| Should the pooled MAD limits from PR #64 replace the current ones? | No. They raise genuine watches flagged from 3–6 % to 7–21 % and catch only one more labelled defect. |
| Is any current limit too lenient? | Baton tilt, on clean edges only. The labelled crooked 6 (1.41°, clean edge) passes 1.75°; genuine clean batons never exceed 0.85°. |
| Is any current limit too strict? | No confident genuine false alarms except one 12 triangle at 1.221° against a 1.2° limit. The other 12 genuine flags are "Possible" from rough or low-resolution edges. |

## 1. Code map

- **The app's live path:** reached from the launcher, 61 classes.
  - The model-agnostic core is `Alpha99Pipeline`, `AlignmentAnalysis`, `MetalAlignment`, `ClearlyOff`, `MetalFit`, `ClearlyOffEvidence`, `ModelRegistry` and `UnifiedMarkerReview`.
  - Nothing in that core branches on the model id, except the owner's per-model saved limits in `ClearlyOff.forModel`.
- **Not reachable from the launcher:** 57 classes, 11,201 lines, including:
  - the earlier GMT analysers (`GmtHumanQcAnalyzer*`, `GmtRehaut*`, `GmtRoundMarkerAnalyzer`, `GmtSixLandmarkAnalyzer`, …);
  - `Sub124060QcAnalyzer` and the `Sub124060*` overlays;
  - `WatchAlignCore` V–V13 and `MainActivity`, which is still declared in the manifest but is not the launcher.

  These are the "family-specific engines". They no longer decide anything in the app. Removing them is a separate cleanup, because some unit tests still cover them.
- **What genuinely differs per family** (asset folders only):
  - the date window at 3 (GMT, Date) versus a 3 baton (No-Date);
  - the minute exclusions for the cyclops;
  - the seconds-hand print spots;
  - the reference radii: GMT batons 0.7551 R and rounds 0.8128 R, against 0.7591 R and 0.8136 R for both Submariners.

  The triangle, minute track and marker sizes are identical.

## 2. Same photo, different family config (`Cross.java`, `results/cross_family.csv`)

Three committed public fixtures (GMT, No-Date, Date) were each run with their own and the other families' configs:

- **Sideways (all markers):** change of 0.000–0.032 % R.
- **Tilt (batons, triangle):** change of at most 0.007°.
- **In/out between GMT and Sub configs:**
  - batons: 0.32–0.39 % R;
  - triangle: 0.14–0.18 % R;
  - rounds: 0.08 % R or less.

  Each change equals the difference in reference radius.
- **Date and No-Date configs on the same Date photo:** identical, 0.011 % R or less.
- **A wrong config invents markers.**
  - The No-Date config on a GMT photo "measured" the **date window** as a 3 o'clock baton and passed it as *No visible issue*.
  - On a Date photo it marked the date window *Possible*.

  So "a GMT works through the No-Date engine" is true only for the shared markers. Model-specific layout must stay; the 3 o'clock position must come from the model.

## 3. Reference offset, not manufacturing (`offsets.py`)

- **Watch-level median in/out of genuine GMT batons:** +0.25 % R (6) and +0.28 % R (9). Both Submariners: ≤ 0.05 % R.
- **The GMT master's baton radius** is 0.40 % R smaller than the Submariner master's. It was taken from one bare BLNR dial.
- **Effect of re-centring each hour on its genuine median:** GMT clean baton position median falls 0.337 → 0.164 % R, matching the Submariners (0.177 and 0.137).

  This removes the GMT/Sub family difference that PR #64 reported. It is in-sample and diagnostic, not a new reference.
- **A side pattern in every family:** about −0.1 to −0.3 % R at 1–5 and +0.1 to +0.2 % R at 7–11. Markers on both halves sit slightly towards 12 relative to the minute track. It is about 0.2 % R, small against the 1.0 % R limit, and is worth checking against the round-marker reference before any position limit is tightened.

## 4. Genuine distribution and photo repeatability (`dist.py`)

Clean = the outer-metal fit carries no uncertainty note.

| Measurement (clean edges) | GMT max | No-Date max | Date max | Current limit |
|---|---:|---:|---:|---:|
| Round position % R | 1.065 | 0.791 | 0.561 | 1.0 + 0.5 px |
| Baton position % R | 0.723 | 0.819 | 0.641 | 1.0 + 0.5 px |
| Baton tilt ° | 0.845 | 0.805 | 0.831 | 1.75 |
| Triangle tilt ° | 1.019 | 1.221 | 1.113 | 1.2 |
| Triangle gap % R, minimum | 2.553 | 2.448 | 3.299 | 2.2 − 0.5 px |

- **Uncertain edges are much wider:** baton tilt up to 1.63°, round position up to 1.78 % R.
- **Repeatability, same watch and hour across photos:**
  - baton tilt sd 0.09–0.10° (Subs), 0.27° (GMT, 12 groups, including uncertain edges);
  - round position sd 0.06–0.09 % R;
  - triangle tilt sd 0.12–0.17°.

  The Date cohort has too few repeat photos (2–15 groups) to estimate repeatability.
- **Two photos of one replica** (SUB_accepted12) give 9-baton tilts of 1.03° and 1.71°, both from rough edges. Uncertain readings must not be judged on the clean-edge limit.

## 5. Ground truth: known turns (`BatonTurn.java`, `results/known_turns.csv`)

- **Setup:** 9 batons and 4 triangles in the 4 committed fixtures were turned about their centres by ±0.5 to ±2.5°, which gives 156 trials (MetalSynth's existing move).
- **Recovered change minus applied turn:**
  - batons: mean −0.001°, sd 0.040°, worst 0.15°;
  - triangles: mean +0.002°, sd 0.032°, worst 0.08°.
- **Edge status:** the edge stayed clean in 163 of 169 rows.

The engine resolves tilt about ten times finer than any limit in question. **The tolerance decides the outcome, not measurement noise.** Under 1.75°, a baton turned 1.4° from a genuine start passes or fails depending on which way its genuine tilt already leaned.

## 6. Limits compared (`sweep.py`, `twotier.py`)

The re-scorer reproduces the app's state for **all 3,835 committed rows** at the current limits before any comparison.

Columns are genuine markers flagged and genuine physical watches with at least one flag (Clearly off or Possible).

| Limit set | GMT (733 mk / 38 w) | No-Date (1,611 / 75) | Date (1,396 / 125) | Labelled replica controls |
|---|---|---|---|---|
| Current 1.6.3 | 1 mk, 1 w (3 %) | 5 mk, 2 w (3 %) | 7 mk, 7 w (6 %) | crooked 6 **missed**; rejected 12 Possible |
| Pooled PR #64 (all) | 18, 8 w (21 %) | 28, 11 w (15 %) | 13, 9 w (7 %) | crooked 6 caught; 8 extra flags on accepted and control photos |
| Current + pooled round 0.671 | 9, 3 w | 16, 4 w | 11, 7 w | + accepted GMT 1, control 2 |
| Current + pooled gap 2.81 | 5, 2 w | 17, 9 w (12 %) | 7, 7 w | none gained |
| Current + pooled triangle in/out 0.373 | 4, 4 w (11 %) | 5, 2 w | 9, 9 w | + accepted Sub 12 (×2) |
| Current + pooled baton tilt 1.096 (all edges) | 3, 2 w | 5, 2 w | 7, 7 w | crooked 6 caught; + accepted Sub 9 and control 3 (Possible) |
| **Current + clean-edge baton tilt 0.9–1.4** | **1, 1 w** | **5, 2 w** | **7, 7 w** | **crooked 6 caught; nothing else changes** |

What the labelled controls show:

- **GMT_accepted6 (two photos):** its 12 is *Clearly off* under the current limits on both photos.
  - Gap 1.56 / 1.83 % R, against a genuine minimum of 2.45.
  - Sideways −0.90 / −0.87 % R, against a genuine clean maximum of 0.64.

  The community label concerns the 6. Across both photos the 12 is consistently outside every genuine watch, so this is **not** evidence of a false alarm.
- **SUB_rejected12:** stays *Possible* (tilt 1.26°, gap 1.90 % R, rough edge) under every set.
- **SUB_control12:** its 12 gap is 2.00 % R, below every genuine watch, and it is flagged under every set.

## 7. Recommendation (needs owner approval; not implemented in the app)

1. **Keep the current limits as the shared default for every family.** Do not adopt the MAD-derived pooled limits.
   - They are set at the edge of genuine manufacturing variation.
   - On these data they mostly add warnings on genuine watches and community-accepted replicas.
2. **One evidence-based change:** judge baton tilt at **1.2° when the outer-metal edge is clean**, and keep 1.75° (shown as *Possible*) when it is uncertain.
   - 1.2° is 0.35° above every clean genuine baton (661 batons, three families), more than 3 photo-repeatability sds.
   - It is 0.2° below the labelled crooked 6.
   - It matches the existing 1.2° triangle limit.
   - On every committed row it changes exactly one result: the crooked 6.

   Caveat: this rests on **one** labelled real defect plus synthetic turns. More labelled crooked batons are the evidence still missing.
3. **Fix the GMT baton (and triangle) reference radius** from its genuine medians rather than changing any tolerance. That would make GMT and Submariner position spreads alike, so one position limit is justified.
4. Keep the layout per model, so the date window is never a marker. Remove the unreachable family analysers as a separate, test-backed cleanup.

## Evidence gaps

- **Labelled real defects:**
  - only one baton tilt defect (GMT crooked 6);
  - no Date family defect;
  - no Yacht-Master photo or model in the repository.

  The Yacht-Master 8 o'clock case stays qualitative until its original file is supplied.
- **Small Date repeatability sample.** The Date "Possible" triangles (5 of 7 Date flags) all lean −1.4 to −1.6° from rough edges of roughly 1.4–2.2 px on 436 px dealer photos. That is a separate fitter question; this report does not touch it.

## Reproduce

```sh
python3 tools/research/shared_engine/rescore.py      # parity: rows 3835 mismatch 0
python3 tools/research/shared_engine/sweep.py        # section 6
python3 tools/research/shared_engine/twotier.py      # clean-edge baton tilt
python3 tools/research/shared_engine/dist.py         # section 4
python3 tools/research/shared_engine/offsets.py      # section 3
python3 tools/research/shared_engine/turn_summary.py # section 5
# drivers (desktop OpenCV 4.9.0-0 jar; the fixtures are committed public dealer photos)
H=tools/desktop-harness; javac -encoding UTF-8 -nowarn -cp $JAR -sourcepath $H/shim:android/app/src/main/java -d cls \
  tools/research/shared_engine/{BatonTurn,Cross}.java $H/drivers/*.java
java -Dwatchalign.model=gmt_126710 -cp $JAR:cls com.watchalign.mobile.BatonTurn tools/research/marker_audit/fixtures/49ba843c0a7e19d7.jpg 49ba843c0a7e19d7
```

Inputs: `tools/research/marker_audit/results/{gmt,nodate,date}-after.csv` and `qc/*-after.csv`, from main. No private photos are used.
