# Rolex Submariner 124060 — checkpoint (paused)

Status: an experimental 124060 option exists in the debug APK. It measures, but it does not judge. GMT is unchanged.

- **Branch:** `feature/android-sub124060-qc`, based on `main` at `69be8e7`.
- **Commit:** the commit that adds this file (`git log -1 -- docs/124060_CHECKPOINT.md`).
- **App version:** 1.3.0-alpha69.
- **Not merged to `main`.**

## What works in the APK

The model selector on the main screen now offers two models:

- **GMT-Master II 126710** — the default. It runs the unchanged GMT check.
- **Submariner 124060 (experimental)** — runs its own route (`Sub124060QcAnalyzer`). It never reaches the GMT analyser or the old legacy non-GMT analysers.

The 124060 route uses the existing flow unchanged: choose photo, Check watch, summary, close-up, Inspect overlay, Full results, Export card, and Align dial edge by hand.

What the 124060 route does:

1. **Dial.** It uses the existing dial seed and edge fit, unchanged, and reports which of these it got:
   - automatic edge fit;
   - hand-aligned, then the dial edge was re-fitted;
   - hand-aligned circle only;
   - not assessable.

   Align dial edge by hand is offered whenever the dial or the 12 is not found.
2. **12 triangle.** It uses the frozen Submariner detector v2, ported from the research branch as `SubTwelveTriangle`.
   - Single photo only, with no perturbation consensus.
   - No GMT 44.3° apex gate.
   - On the 25 development 124060 photos where both production and research found a triangle, the production selection is in the same place as the frozen research selection: at most 0.94 px apart in original pixels.
   - On 2 further photos research found a triangle and production did not. Production's dial fit there differs from the research fit, so the route fails closed.
3. **Batons 3/6/9 and round markers 1, 2, 4, 5, 7, 8, 10, 11.** These use the existing GMT detectors, unchanged. The layout comes from the model, so there is no date window and no date-side logic.
4. **Summary.** It shows:
   - dial source;
   - whether the 12 was found;
   - 12 rotation, gap to the minute track and centring, each labelled **MEASURED / NOT YET JUDGED**;
   - how many batons and round markers were found.

   It always shows: "124060 experimental support: measurements are not yet QC pass/fail results." It never says OK, worth a look or check closely.
5. **Overlay** (neutral). It draws only what was found:
   - the dial boundary;
   - the 12 triangle, plus the minute-track arc and the 59/60/01 points it was measured against;
   - the radial through the 60 tick;
   - the batons and the round markers.

   Cyan means found and measured. Grey dashed means found but not measured, with the reason beside it. There are no ticks, no warning colours, no scores and no expected template geometry.

### Fail-closed rules for the three 12 values

The three 12 values are shown only when all of these hold:

- the dial edge was fitted, automatically or from a hand alignment;
- the edge fit gives the same dial when the photo is reduced to 94% and 88% (centre within 0.01 R, radius within 2%);
- the triangle is at least 40 px wide;
- no hand is at 12;
- the same outline is found again at 94% and 88%.

Gap and centring also need the outer (surround) outline and a located minute track.

Why the dial-reproducibility rule matters: the development photos with the worst 12 readings were the ones whose dial fit did not reproduce. For example, one genuine watch read 8.9° rotation while turned 8° in the photo.

## What is experimental or missing

- **No 124060 tolerances.** Nothing is flagged, cleared, scored or passed. The numbers are for looking, not for judging.
- **Not checked:** bezel and pearl, rehaut, hands, printing, lume, and the photo-angle rating.
- **No device proof.** OpenCV loading, run time and analysis on a phone are not proven by the build. On desktop, the 124060 route takes a median of about 5.6 s per photo, and up to about 45 s while another job was running.

## Known limitation: automatic dial-edge fit on replica QC photos

On the 80 development 124060 photos:

| | Photos | Automatic dial-edge fit | 12 measured |
|---|---|---|---|
| Genuine dealer photos | 36 | 22 | 9 |
| Replica QC photos | 44 | 14 | 5 |

Most failures are a seed-circle fallback, or low resolution: a 12 triangle under 40 px wide. In those cases, use Align dial edge by hand.

## Blockers when the project resumes

- **B1 — improve automatic dial-edge fitting on replica QC photos.** Start with the development replica photos where the edge fit fails, and classify why it fails before changing anything. `DialEdgeEllipseFit` is shared with GMT, so any fix must keep the GMT golden comparison identical.
- **B2 — establish conservative 124060 tolerances.**
  - Use the development partition: genuine watches, with the physical watch as the unit, plus replica photos.
  - Candidate checks: 12 gap to minute track, 12 rotation, 12 centring.
  - Check once on validation; use the holdout once at the end.
  - Apply the same fail-closed rules as above.
- **126610LN / 126610LV** remain future work. They have the date at 3 and batons at 6 and 9; the explicit layout already exists on the research branch.

## Exact next step when resuming

1. Install this APK and run the 124060 route on real replica QC photos. Note where the overlay outlines are wrong, and where Align dial edge by hand succeeds when the automatic fit fails.
2. Use those notes to decide whether B1 (dial fit) or B2 (tolerances) comes first.
   - If the dial or 12 is missed on most real QC photos, do B1 first.
   - Do B2 only after the outlines are reliably on the right markers.

## Verification at this checkpoint

- **Android JVM suite:** 247 tests, 0 failures. This includes `Sub124060Test` (13), `SubmarinerProductionIsolationTest` (6), `GmtConstantsSnapshotTest` (3) and `GenericGmtTest` (7).
- **GMT golden comparison:** 337 of 337 photos, 0 differences. This covers the GMT analyser outputs and every round-marker row.
- **GMT whole-route check:** `CoreReport` ran `WatchAlignCoreV13.analyse` on 16 GMT photos, built from both `main` and this branch.
  - Reports, overlay pixels and close-up pixels are identical; only the version label differs.
  - On 3 of the photos both builds throw the same legacy error, as before.
- **Debug APK:** `:app:assembleDebug` succeeds. It has not been installed or run on a phone by the developer.

## Where things are

| What | Where |
|---|---|
| 124060 analysis route | `android/app/src/main/java/com/watchalign/mobile/Sub124060QcAnalyzer.java` |
| Ported 12-triangle detector | `.../SubTwelveTriangle.java` |
| Layout | `.../Sub124060Layout.java` |
| Overlay | `.../Sub124060Overlay.java` |
| Summary and Full results text | `.../Sub124060Summary.java` |
| Routing and selector | `WatchAlignCoreV13.analyse` (first lines) and `MainActivity.offeredModels` |
| Tests | `Sub124060Test`, `SubmarinerProductionIsolationTest`, `GmtConstantsSnapshotTest` |
| GMT golden comparison | `tools/desktop-harness/gmt_golden.py` (337 photos; the images themselves are not committed) |
| Desktop run of the 124060 route | `tools/desktop-harness/run.sh SubCheck <list.txt> <out_dir>` |
| Research evidence | Branch `feature/submariner-research-hardening` (`docs/research/submariner/`) |
