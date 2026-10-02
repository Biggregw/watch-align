# Rolex Submariner 124060 — alpha70 checkpoint (alpha73 status below)

## alpha74 status (2026-10-02)

Verdicts are back on with **provisional** bands from the repaired calibrator run `37004915187`. Measurement went through the production route. 12 development genuine watches set the limits; 0 outliers were rejected; validation and holdout genuine watches were 100% clear. The product owner accepted these bands as provisional.

| Check | Clear | Check closely beyond |
|---|---|---|
| 12 rotation | −0.96° to +1.40° | −1.75° / +2.19° |
| 12 centring | −0.019 to +0.020 of triangle width | ±0.032 |
| Round-marker ring radius | 0.8085–0.8280 R | 0.8021 / 0.8345 R |
| Round-marker spacing RMS | ≤ 0.74° | > 1.05° |
| 12–6 axis offset | ≤ 0.0053 R | > 0.0079 R |

The 12 gap and the 3–9 axis did not have enough repeat data and stay measured-only. Sensitivity to real defects is not yet proven: the run had no replica evidence because the Reddit API secrets are not set. Expect some false alarms on oblique photos, and record them.

## alpha73 status (2026-10-02)

- **App:** `1.3.0-alpha73` keeps the alpha71/72 GMT-style 124060 UI, but `Sub124060Calibration.VERDICTS_ENABLED=false`. Reliable values show as **MEASURED / NOT YET JUDGED** (neutral blue outline, dot badge); unmeasured items keep the grey dashed / dash state. No 124060 verdict reaches the user.
- **Why:** the alpha72 bands from calibrator run 36927036008 were too wide to flag anything. For example, rotation was clear between −13.9° and +15.2°, and spacing RMS / axis offsets had symmetric bands on metrics that cannot go negative. In that run's development and validation data, 0 of 6–7 replica watches fell outside clear on any metric except ring radius (1 of 7).
- **Calibrator repaired** (`tools/watch_calibrator`, see its README): official Reddit API only, no search-engine scraping, Watchfinder rejected; measurement through the production app route with alpha70 gates; outlier-robust limits; one-sided bands; and a sensitivity requirement before anything is CALIBRATED.
- **Next decision (product owner):** for each metric, set either the largest useful clear band (`max_clear_half_width`) or a small set of confirmed-defect cases (`defect_evidence`) in `calibration/models/124060.json`, then re-run the calibrator. Only metrics that come back CALIBRATED may be frozen into the app and `VERDICTS_ENABLED` turned on.

Status: the experimental 124060 measurement route is merged to `main` and ready for user testing. It measures, but it still does not judge. GMT production behaviour is intended to remain unchanged.

- **Main merge:** PR #37, merge commit `b402cdeb776f185523f5d2cc5ab79d8107479b3f`.
- **App version:** `1.3.0-alpha70` (`versionCode 13070`).
- **Route:** `Sub124060QcAnalyzer`, selected before the GMT/legacy analysis path.
- **Policy:** no 124060 pass/fail tolerance, score, authenticity rule or verdict exists.

## What alpha70 adds

The 124060 route keeps the frozen Submariner-specific 12-triangle detector and the existing shared GMT dial/marker primitives, but now reuses more of the reliability framework that made the GMT path robust.

### 12 triangle

The existing fail-closed requirements remain:

- the dial edge must be fitted, automatically or from a hand alignment;
- the dial edge must reproduce at 94% and 88% scale;
- the triangle must be at least 40 px wide;
- no hand may obstruct 12;
- the same physical triangle outline must reappear at 94% and 88%.

Alpha70 adds a second, independent check: the **actual numeric measurements** are re-measured at 100%, 94% and 88%.

- rotation, gap and centring are checked independently;
- each metric is converted to an approximate source-pixel movement;
- if one metric moves by more than about one source pixel, only that metric is withheld;
- another stable metric can still be reported;
- this is a measurement-quality gate, not a watch QC tolerance.

The Submariner dial-radial rotation remains the primary rotation measurement. The local 59/01 chord and the triangle base-edge angle are retained as diagnostics only; they do not alter the reported rotation or create a verdict.

### Batons and round markers

The 3/6/9 batons use `GmtSixLandmarkAnalyzer` and the eight round markers use `GmtRoundMarkerAnalyzer` as before.

Alpha70 also runs the mature GMT resize/re-measure diagnostics for these shared markers, but **does not import GMT decision thresholds**:

- baton resize movement is recorded diagnostically only;
- round-marker centre resize movement is recorded diagnostically only;
- if a round marker changes between lume and surround edge identity under resize, its centre can remain usable while its size comparison is suppressed;
- no otherwise stable 124060 baton or round marker is downgraded merely because a GMT-calibrated decision rule would do so.

### Photo pose

`GmtMarkerPose` is reused as a round-marker-layout pose estimator and shown diagnostically in Full results. The GMT 5° near-frontal threshold is **not** applied to the 124060.

## Development evidence behind alpha70

The bounded alpha70 reliability study used **39 existing development photos from 13 physical 124060 watches**. Validation and holdout were not inspected.

Among the 25 photos where the triangle was large enough, the same outline repeated and the dial fit was reproducible:

- rotation repeated within about one source pixel on **21/25**;
- gap repeated within about one source pixel on **18/25**;
- centring repeated within about one source pixel on **21/25**;
- median movement was about **0.32 px rotation**, **0.41 px gap** and **0.34 px centring**;
- worst observed movement was **1.51 px rotation-tip travel**, **4.07 px gap** and **1.36 px centring**.

A strict one-pixel marker-status gate was deliberately rejected. In the development study it would have demoted **44/83** otherwise stable/found baton readings and **60/232** otherwise stable/found round-marker readings without a 124060 calibration basis. Those marker checks therefore remain diagnostic.

See `docs/research/submariner/sub124060_reliability_framework_alpha70_2026-10-01.md` and `docs/ENGINEERING_LESSONS.md`.

## What works in the APK

The main screen offers:

- **GMT-Master II 126710** — the mature GMT route;
- **Submariner 124060 (experimental)** — the isolated 124060 route.

The 124060 flow supports choosing a photo, Check watch, summary, close-up, Inspect overlay, Full results, Export card and manual dial-edge alignment.

The report continues to label 124060 values **MEASURED / NOT YET JUDGED** and states that experimental support is not yet a QC pass/fail result.

The neutral overlay draws only detected/measured geometry. It does not draw warning colours, expected-template verdicts or scores.

## Still deliberately missing

- **No 124060 tolerances or verdicts.**
- **No authenticity classification.**
- **No imported GMT pose or marker thresholds.**
- Bezel/pearl, rehaut, hands, printing and lume are not production 124060 QC checks yet.
- Validation and holdout remain reserved for later calibration work.

## Current verification

- **Android JVM suite:** alpha70 CI passed with 253 tests and 0 failures.
- **Build/signing:** alpha70 standalone APK build and signing verification passed.
- **Merge review:** independent review after PR #37 found no GMT behavioural code change apart from the version label and confirmed the 124060 route still exits before GMT QC logic.
- **GMT golden fixture:** the committed fixture remains the SHA-keyed 337-photo baseline. The last full image-backed run was 337/337 identical at the preceding checkpoint. The alpha70 code review shows no shared GMT detector change, but an alpha70 image-backed rerun still requires the third-party 337-photo corpus because those image bytes are intentionally not committed.

## User-testing checkpoint

The next step is phone testing, not another broad redesign.

When testing alpha70, record cases where:

1. the dial or marker outline is visibly on the wrong physical edge;
2. a value is withheld even though the geometry looks stable;
3. a value is reported even though the measurement visibly looks unreliable;
4. analysis speed is unacceptable;
5. report wording is confusing.

Fix those issues one at a time.

## Follow-up before production tolerances

Add a direct pure unit test for **independent per-metric 12 withholding**. The desired cases are explicit, for example:

- unstable rotation while gap and centring remain available;
- unstable gap while rotation and centring remain available;
- unstable centring while rotation and gap remain available.

The current OpenCV route implements this behaviour, but the final min/max-to-withhold decision should be extracted to a pure helper before production tolerances are introduced so it can be regression-tested directly.

## Reuse-first rule

Before adding any new 124060 detector, measurement, confidence rule or QC decision, audit the closest mature GMT mechanism first. Reuse its proven primitives and reliability lessons where they apply, while keeping model-specific geometry and calibration separate when evidence says they differ.

See `AGENTS.md` and `docs/ENGINEERING_LESSONS.md`.

## Where things are

| What | Where |
|---|---|
| 124060 analysis route | `android/app/src/main/java/com/watchalign/mobile/Sub124060QcAnalyzer.java` |
| 12-triangle detector | `.../SubTwelveTriangle.java` |
| Layout | `.../Sub124060Layout.java` |
| Overlay | `.../Sub124060Overlay.java` |
| Summary and Full results | `.../Sub124060Summary.java` |
| Shared pixel-repeatability helper | `.../MeasurementRepeatability.java` |
| Routing/model selector | `WatchAlignCoreV13` and `MainActivity` |
| 124060 tests | `Sub124060Test`, `SubReliabilityReuseTest`, `SubmarinerProductionIsolationTest` |
| GMT protection | `GmtConstantsSnapshotTest`, `GenericGmtTest`, `tools/desktop-harness/gmt_golden.py` |
| Reliability evidence | `docs/research/submariner/sub124060_reliability_framework_alpha70_2026-10-01.md` |
| Reusable engineering lessons | `docs/ENGINEERING_LESSONS.md` |
