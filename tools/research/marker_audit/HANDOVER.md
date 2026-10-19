# Developer handover — 19 October 2026

The owner requested an immediate checkpoint and handover. **Stop research here;
the remaining work below is for the next developer.** No measurement jobs are
left running. Main, releases and Submariner Date visibility are unchanged.

## Branch and revisions

- Branch: `research/metal-marker-evidence`.
- Draft PR: https://github.com/Biggregw/watch-align/pull/63
- Production baseline/main: `cf373d205a873648fde46c9704aba177284bc920` (1.6.3).
- Validated production-code revision: `29c2403d6f2900c671b154482e9a32c14f62c806`.
- Evidence checkpoint: `1f606473adf0047b2b9429f43ce861a63529c7ec`.
- This handover is added by a subsequent documentation/tooling commit; the final
  delivered tip is recorded in PR #63. Obtain its exact SHA with `git rev-parse HEAD`.
  Production code is unchanged since the validated revision above.

## What was corrected

1. **Observed seconds-shaft crossings:** Alpha99 detects a seconds direction
   from evidence outside the marker, but MetalFit's independent pivot mask can
   stop short. In both original suspect batons it accepted positively weighted
   samples on the shaft. The correction passes the evidenced direction into
   ClearlyOff/MetalFit and excludes only observed crossings within the thin
   forward shaft corridor, rather than masking a broad strip of reference
   origins. Clean sides and short ends remain available to the unchanged
   geometric sufficiency checks. No blanket hand rejection was added.
2. **Android allocation failure:** new full-image tests exposed an existing
   OutOfMemoryError in unmodified 1.6.3 under a 192 MB heap. Polar sampling now
   processes one row at a time, with identical coordinates/interpolation; the
   2D median uses primitive scratch storage instead of millions of boxed values.
   Resolution, mathematical definitions and the emulator heap are unchanged.

No thresholds, references, pose architecture, family routing, version number or
Date enablement were changed. PR #62 was not merged. The production changes are
confined to `MetalFit.java`, `ClearlyOff.java` and `Alpha99Pipeline.java`.

## Established findings and tests

All ten original findings were recovered with the exact catalogue SHA-256.
Baseline reproduces all 110 original target-photo marker rows, every field.
The actual sample endpoints, solve weights, masks, reference and reconstructed
outlines are preserved in compressed traces and annotated panels. See
[REPORT.md](REPORT.md), [VALIDATION.md](VALIDATION.md), and `results/cases-summary.json`.

| Original baton | Rotation before → after | Result |
|---|---:|---|
| 2802b4a30cfb277b / 9 | +2.0221° → +0.4303° | Measurable, no edge uncertainty; confirmed shaft error |
| ee07ceedbb6f0bd3 / 6 | −2.1010° → −0.0716° | Measurable, no edge uncertainty; confirmed shaft error |

Verified CI: **[39311780904](https://github.com/Biggregw/watch-align/actions/runs/39311780904)**
at `29c2403d`: **467 unit tests and all 10 API 35 x86_64 emulator tests passed**,
zero failures/errors/skips. Both APKs built. Raw instrumentation XML and the
four actual Android image measurements are committed as
`results/android-instrumentation.xml` and `results/android-validation.json`.
All four new hash-pinned image tests ran: GMT, No-Date and both Date batons.
The Android baton readings agree with desktop to about 0.000001°.

Local baseline Gradle: 455 passed. Candidate Gradle: 467 passed. Tool integrity:
5 tests passed. Two memory tests check bitwise sampler equivalence and original
median semantics. Ten seconds tests cover aligned/rotated/translated markers,
adjacent/overlapping shafts, surviving fragments and insufficient geometry.
The constrained-heap probe fails on baseline and completes all 11 Date markers
after the storage fix; logs are committed. No physical handset was tested.

Earlier CI failures are real and documented: two fixture-fetch failures, then
four executed image tests failing from memory exhaustion. They were not fixed
by relaxing assertions. Documentation-only runs `39314325758` and `39314778913`
were intentionally cancelled at handover to avoid repeating expensive CI on
unchanged production code. The final handover commit skips CI for that reason.

## Complete initial matched comparison

These results are **complete for the frozen initial cohorts**, before the
subsequent Date expansion. Keys are identical photograph/hour pairs with image
hashes, model-specific hours and every runner outcome checked. Full results:

| Family | Assessed photos | Markers | Pass / Possible / Clear / Can't check before → after |
|---|---:|---:|---|
| GMT | 66 | 726 | 720 / 2 / 0 / 4 → 721 / 1 / 0 / 4 |
| No-Date | 135 | 1,620 | 1,606 / 4 / 1 / 9 → unchanged |
| Date, initial cohort | 127 | 1,397 | 1,386 / 10 / 0 / 1 → 1,389 / 7 / 0 / 1 |

The owner rejected the oblique original `94ad771e9e2aedde`. It stays in the
historical audit and is excluded from primary QC evidence. Every assessed
photo was reviewed for angle; the existing 400 px dial-diameter quality floor
also applies to this offline subset, without changing the app's gates.

| Suitable-QC subset | Photos / watches | Markers | Warnings before → after | Uncertainty-free before → after |
|---|---:|---:|---:|---:|
| GMT | 50 / 33 | 550 | 0 → 0 | 474 → 474 |
| No-Date | 112 / 75 | 1,344 | 4 → 4 | 1,241 → 1,241 |
| Date, initial cohort | 124 / 124 | 1,364 | 9 → 6 | 1,281 → 1,284 |

No new Can't-check classifications or lost uncertainty-free measurements in
either the full initial cohorts or suitable subset. “Uncertainty-free” is the
app's operational output, not independently certified physical accuracy. Only
two of the three Date warning removals are newly reliable: the other is the
still-rough 6f071 triangle. The third newly uncertainty-free measurement is an
already-passing 9 baton whose existing same-shape edge-bias estimate improves
when the contaminated 6 is excluded from the bias pool.

All seven existing SHA-pinned QC controls retain all 81 classifications. The
labelled GMT crooked-6 is **missed by both versions**, +1.4134° below 1.75°.
The rejected No-Date 12 remains Possible at +1.2577°. Do not claim universal
preservation of known real defects; independent synthetic rotations remain
detected. No Date-specific real-defect ground-truth control was recovered.

## Memory-output parity at handover

The completed initial before/after comparison above predates the storage-only
fix. Its subsequent parity check is saved in `results/checkpoint/status.json`:

| Cohort | Checked marker records | Status |
|---|---:|---|
| GMT | 726 / 726 unique (737 raw, one image alias) | Entire CSV byte-identical |
| Date initial | 1,397 / 1,397 | Entire CSV byte-identical; finished during handover |
| No-Date | 972 / 1,620 | Every checked field identical; **648 markers / 54 photos remain** |
| Seven QC controls | 81 / 81 | Both complete CSVs byte-identical |

Do not claim complete three-family memory parity until the remaining No-Date
photos finish. This is an interrupted validation, not an observed mismatch.

## Expanded Date checkpoint — incomplete, fully preserved

356 of 358 catalogue originals are now available with verified hashes, including
**all 219 photos from the original run**. The two unavailable catalogue images
are `b68ab624da78aaf2` and `558fa9ce23c03665`: returned bytes do not match their
catalogue hashes. Neither belongs to the original ten or original assessed 219.
Acquisition attempts are in `results/date-recovery.json`.

The expansion adds 228 photos to the initial 128 verified inputs. Long runs were
intentionally stopped for this handover; no partial results were discarded:

| Interrupted job | Inputs processed | Assessed / explicit NOPOSE | Saved markers | Inputs remaining |
|---|---:|---:|---:|---:|
| Additional Date baseline | 47 / 228 | 46 / 1 | 506 | 181 |
| Additional Date candidate | 52 / 228 | 51 / 1 | 561 | 176 |
| No-Date memory parity | 240 / 294 | 81 / 159 | 972 | 54 |

`results/checkpoint/` contains the raw CSVs/logs, full expansion manifest, and
**exact `*-processed.csv` / `*-remaining.csv` manifests for each job**. These
account for every input; the interrupted current photo is in the remaining list.
Do not label a missing input as NOPOSE. The 228-photo angle review was completed
on plain originals before inspecting measurements; 39 are visibly oblique.
Resolution eligibility must be joined to the completed results later.

For the original **219-photo** validation specifically:

- Original baseline artifact: all 2,409 rows preserved as `original219-baseline.csv`.
- Baseline rerun currently reproduces every field of **1,309 rows / 119 photos**.
- Candidate compared with original baseline: **1,342 rows / 122 photos**;
  **97 original photos remain** on the candidate side.
- This partial subset: 1,331 pass / 10 possible / 1 Can't check →
  1,334 pass / 7 possible / 1 Can't check. No new Can't check; 1,238 existing
  uncertainty-free records retained, 1,241 after. **Not the full 219 result.**
- `original219-progress.csv` names every original photo and both rerun statuses.
  `original219-{before,after}-remaining.csv` supports prioritising that subset.
  Matched partial CSVs and `original219-partial-comparison.json` are also saved.

## Remaining problems and recommendation

- Triangles: fixed-outline fitting under asymmetric visibility can convert
  systematic rim/bevel offsets into apparent rotation/position. The 22060
  triangle gives every top-edge observation zero solve weight; the two slopes
  alone do not validate its reconstructed radial position. Six primary Date
  warnings remain, and a seventh passing triangle remains very uncertain.
- No-Date: clean 12 triangle `728e4c157ef1075d` reads −1.2209°, versus −1.0646°
  in another verified photo of the same watch. Three round warnings in
  `6ac54fe6dfcbd61a` mix outer metal and an inner bright boundary. These are
  separate repeatability/visibility problems, not fixed by the seconds change.
- GMT crooked-6 control remains missed as noted above. The remaining full-cohort
  genuine GMT warning is a broad-hand/poor-angle case excluded from primary QC.
- **Keep PR #63 draft pending the outstanding parity and expanded regression.**
  The two baton corrections are supported; final merge recommendation remains
  deferred. **Date is not ready to enable.** Do not merge, release, alter genuine
  references or raise production thresholds to clear residual findings.

## Finish validation without repeating completed work

Workspace retained: `/workspace/watch-align`, image cache `/workspace/evidence/images`.
JDK: `/workspace/jdk/jdk-17.0.20.1+1`; Python: `/workspace/venv/bin/python`.
Compiled classes: `/workspace/evidence/base-classes` and `final-classes`.
The bulk image cache is **not in Git**; copy it when transferring machines.
Original ten-case visuals and public source images are preserved in
`review-bundle.zip` for handover; no owner/private photographs are included.

From the repository root, use these existing class directories, or compile fresh:

```sh
export JAVA_HOME=/path/to/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
TASK_ROOT=$(pwd)
AUD=tools/research/marker_audit
SAVED=$AUD/results/checkpoint
IMAGES=/workspace/evidence/images
OUT=/tmp/watch-align-resume
JAR="$TASK_ROOT/tools/desktop-harness/.cache/opencv-4.9.0-0.jar"
mkdir -p "$OUT/base-classes" "$OUT/final-classes"
git worktree add --detach /tmp/watch-align-baseline cf373d205a873648fde46c9704aba177284bc920
BASE=/tmp/watch-align-baseline
javac -encoding UTF-8 -nowarn -cp "$JAR" \
  -sourcepath "$BASE/tools/desktop-harness/shim:$BASE/android/app/src/main/java" \
  -d "$OUT/base-classes" "$BASE"/tools/desktop-harness/drivers/*.java
javac -encoding UTF-8 -nowarn -cp "$JAR" \
  -sourcepath tools/desktop-harness/shim:android/app/src/main/java \
  -d "$OUT/final-classes" tools/desktop-harness/drivers/*.java
```

Verify hashes before running. If the cache was not transferred, use the existing
`fetch_cases.fetch(rows, image_root, log_path)` on each remaining manifest; it
accepts arbitrary manifest rows, unlike the ten-case CLI. Do not substitute
different CDN bytes. The OpenCV jar/bootstrap requirements are in the desktop
harness documentation and this audit's README.

Run the three remaining jobs, retaining logs (these commands are **for the next
developer**, not jobs left running):

```sh
java -Xmx1g -Dwatchalign.model=submariner_124060 -cp "$JAR:$OUT/final-classes" \
  com.watchalign.mobile.TrackDump "$SAVED/nodate-final-remaining.csv" "$IMAGES" \
  "$OUT/nodate-rest.csv" > "$OUT/nodate-rest.log" 2>&1
java -Xmx1g -Dwatchalign.model=submariner_126610 -cp "$JAR:$OUT/base-classes" \
  com.watchalign.mobile.TrackDump "$SAVED/date-additional-before-remaining.csv" "$IMAGES" \
  "$OUT/date-before-rest.csv" > "$OUT/date-before-rest.log" 2>&1
java -Xmx1g -Dwatchalign.model=submariner_126610 -cp "$JAR:$OUT/final-classes" \
  com.watchalign.mobile.TrackDump "$SAVED/date-additional-after-remaining.csv" "$IMAGES" \
  "$OUT/date-after-rest.csv" > "$OUT/date-after-rest.log" 2>&1
```

Join only completed, disjoint batches. The merge helper rejects incomplete
inputs, missing hours and duplicated outcomes before writing output:

```sh
python "$AUD/merge_runs.py" "$AUD/results/nodate-verified.csv" \
  "$OUT/nodate-final.csv" "$OUT/nodate-final.log" \
  "$SAVED/nodate-final.csv" "$SAVED/nodate-final.log" "$OUT/nodate-rest.csv" "$OUT/nodate-rest.log"
cmp "$AUD/results/nodate-after.csv" "$OUT/nodate-final.csv"
python "$AUD/merge_runs.py" "$SAVED/date-expanded-verified.csv" \
  "$OUT/date-before.csv" "$OUT/date-before.log" \
  "$AUD/results/date-before.csv" "$AUD/results/date-before.log" \
  "$SAVED/date-additional-before.csv" "$SAVED/date-additional-before.log" \
  "$OUT/date-before-rest.csv" "$OUT/date-before-rest.log"
python "$AUD/merge_runs.py" "$SAVED/date-expanded-verified.csv" \
  "$OUT/date-after.csv" "$OUT/date-after.log" \
  "$SAVED/date-final.csv" "$SAVED/date-final.log" \
  "$SAVED/date-additional-after.csv" "$SAVED/date-additional-after.log" \
  "$OUT/date-after-rest.csv" "$OUT/date-after-rest.log"
python "$AUD/compare.py" "$OUT/date-before.csv" "$OUT/date-after.csv" \
  "$SAVED/date-expanded-verified.csv" "$IMAGES" "$OUT/date-comparison.json" \
  --before-log "$OUT/date-before.log" --after-log "$OUT/date-after.log"
```

Then join existing `results/date-suitability.csv` with the saved additional angle
review and measured `rpx >= 200`, covering exactly the newly assessed photos;
rerun `compare.py` with `--suitability` to obtain the primary QC results. Keep full
results alongside them. Compare the original 2,409 baseline rows by photo/hour
against the completed baseline and candidate, reporting any mismatch explicitly.
Investigate any new Can't check, lost reliable measurement or changed warning;
do not accept a warning-count reduction alone. Update REPORT/VALIDATION/PR with
final denominators and a merge recommendation only after those checks.

## Prepare a test APK

The validated `29c2403d` APK already exists in the **Watch Align** artifact of
run `39311780904` (artifact ID `12216960601`, 30-day retention). This is a test
artifact, not a published release. For a fresh local build with JDK 17/API 35:

```sh
python tools/research/marker_audit/fetch_android.py
bash android/gradlew -p android :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
Use the repository's existing signing configuration; do not change version or
enable Date. A local debug-key build may not install over the shared-key app.
On a hardware-accelerated API 35 x86_64 emulator, the CI-equivalent instrumented
command is:

```sh
bash android/gradlew -p android :app:connectedDebugAndroidTest -PtestAbi=x86_64 \
  -Pandroid.testInstrumentationRunnerArguments.class=com.watchalign.mobile.GenuineOfficialImageValidationTest,com.watchalign.mobile.EmulatorPhotoRunTest,com.watchalign.mobile.PhotoExifOrientationTest,com.watchalign.mobile.MetalMarkerImageRegressionTest
```

The current workspace has no KVM; use GitHub's existing workflow or another
machine. Follow its official-fixture and bulk-photo preparation steps if running
all four classes. Inspect XML and logcat, not just a green workflow.
