# Watch Align: handoff notes (2026-09-27)

This file is for a new Claude session taking over the Android GMT dial QC work. Read it
first, then `AGENTS.md`, then `docs/research/gmt12_outer_edge_gap_2026-09-26.md`.

## 1. What the app does now

The Android app (`android/`, package `com.watchalign.mobile`) checks one watch photo
of a Rolex GMT-Master II 126710BLNR (the model dropdown lists only 126710* models)
and reports on the **12 o'clock triangle**. It checks two things:

- **12 gap**: the clearance between the triangle's outer base and the 59/60/01
  minute-tick line, as a fraction of the triangle width.
- **12 alignment**: whether the triangle is rotated or off-centre, from its axis and
  the spacing to the 59 and 01 ticks.

The report opens with a plain-English SUMMARY (`GmtHumanSummary`), then a DETAILS
section. The overlay is drawn **from the measurements**, not from a template
(`MeasuredOverlayRenderer`). It shows a faint dial ring, the detected triangle, the
tick ends, a gap bracket and spacing lines, coloured green, amber, red or grey. Leaving
the template markers off was a deliberate choice by the user: a full template can come
back later, once more markers are measured.

Other markers (6, 9, 3, batons, dots) are **not checked yet**, and the summary says so.

The app never says "genuine" or "fake". It flags things to look at.

- Version: `CORE_VERSION "1.3.0-alpha54"`, `versionCode 13054` in `android/app/build.gradle`.

## 2. Branches and PRs

| Branch | State |
|---|---|
| `feature/android-gmt-human-qc-rehaut` | All current work. PR **#24** into `main` is open and mergeable. CI (build-android and genuine-image-instrumentation) passed on d22db43. |
| `main` | Does not have alpha49–54 yet. |
| `experiment/template-marker-consensus-shelved` | A shelved experiment: it fitted a template from marker consensus to fix a Pepsi overlay offset. The user rejected this in favour of the measured overlay, so it is **kept for reference only and should not be merged**. |

**First job: merge PR #24** (the user already approved merging once CI passed). The
`gh` CLI isn't installed, but the GitHub API works through the proxy, so use curl
against `api.github.com/repos/Biggregw/watch-align`.

## 3. Build and test

Use JDK 17 and the committed Gradle 8.14.5 wrapper. Don't upgrade the toolchain.

```
cd android
JAVA_HOME=<jdk17> sh ./gradlew --no-daemon -q \
  -Dorg.gradle.java.installations.paths=<jdk17> \
  -Dorg.gradle.java.installations.auto-download=false \
  :app:testDebugUnitTest :app:assembleDebug
```

- 128 JVM unit tests, all passing.
- `:app:compileDebugAndroidTestJavaSource` compiles the on-device test
  (`GenuineOfficialImageValidationTest`). It needs network access for
  androidx.test, so **don't pass `--offline`** for it. Only check the result of the
  compile you actually ran before pushing: a previous session pushed after an
  offline compile failure.
- The device test runs in CI (`.github/workflows/build-android.yml`). It fetches
  official fixtures with `fetch_genuine_fixtures.py` and asserts that the report
  contains `SUMMARY`, `HUMAN 12-MARKER QC` and `Bottom line: nothing flagged at 12`.

### Delivering an APK to the user

The user installs a signed debug APK on his phone. To make one:

1. Strip the unused native libraries (x86, x86_64, armeabi-v7a) from `app-debug.apk`.
2. Run `zipalign`.
3. Run `apksigner` with `~/.android/debug.keystore` (build-tools 35.0.0).
4. Send the file to the user.

Bump `CORE_VERSION` and `versionCode` for each build you send, so his screenshots show
which build they came from.

## 4. Desktop harness (run the real app code without a phone)

`tools/desktop-harness/` compiles `android/app/src/main/java` against small Android
stand-ins (`shim/`: Bitmap, Canvas, Paint, Path, Color, RectF and OpenCV `Utils`) and
desktop OpenCV 4.9 (`org.openpnp:opencv`).

```
tools/desktop-harness/run.sh E2E  photo.jpg overlay.png        # summary + full report + overlay PNG
tools/desktop-harness/run.sh Batch datasets/126710BLNR/resolved_images.csv datasets/126710BLNR out.csv crops/ [nshards shard]
tools/desktop-harness/run.sh AxisViz photo.jpg out.png         # 12 close-up: true-12 vs triangle axis
tools/desktop-harness/run.sh Apex photo1.jpg photo2.jpg ...    # apex angle / squareness of the fitted triangle
```

- The first run downloads the openpnp jar (about 110 MB) into `.cache/`, which is
  gitignored. Maven Central sometimes rate-limits this with a 429. The script detects
  that and stops; retry later.
- Use the harness to reproduce any screenshot the user sends. Its output matches the
  phone.

## 5. Data

- `datasets/126710BLNR/manifest.csv` lists labelled sources: official, gen_candidate
  and rep_labelled, with the factory where known. Run `fetch_images.py` (it uses
  gallery-dl) to download the images into `gen/` and `rep/`, and to write
  `resolved_images.csv`. None of these outputs are committed.
- The user's own replica is a **blind validation case** and stays out of the manifest.
- Network notes from the last sandbox:
  - imgur and i.redd.it are reachable.
  - reddit.com is blocked, both from the shell and from WebFetch.
  - For r/RepTimeQC threads, ask the user for screenshots of the feedback, as he did
    before.

## 6. How the 12 analysis works (pipeline order)

1. **Dial seed** (`GmtDialSeedAnalyzer`): HoughCircles, then the innermost ring that
   is consistently dark inside and bright outside. Without this step it locks onto
   the bezel.
2. **Dial edge** (`DialEdgeEllipseFit`): candidate edges on each ray, then a consensus
   circle, then each ray's edge is picked again and the fit is trimmed. This is robust
   to the dark rehaut.
3. **Triangle and ticks** (`GmtTwelveLandmarkAnalyzer`, fallback
   `GmtTwelveRecoveryAnalyzer`):
   - An Otsu or adaptive contour gives the triangle, which must pass two checks:
     - Fill ratio: contour area divided by the three-corner triangle area must be
       between 0.80 and 1.25. This rejects dots and blobs.
     - Size caps: width must be within [0.09r, 0.32r] and height within
       [0.10r, 0.36r].
   - Then the 59/60/01 tick frame is located.
4. **Refine** (`TriangleEdgeRefiner`): fits the outermost half-level edge crossings
   on each side as lines and intersects them. The base search stops 1 px short of the
   tick line. A fit is rejected if any of these fail:
   - apex angle within 44.3° ± 2.0°
   - base square to the axis within ±2.5°
   - no corner moves more than 0.06R
5. **Gates** (`GmtHumanQcAnalyzerV2`), in this order:
   - Triangle too small: under 40 px (`MIN_TRIANGLE_PX`).
   - Hand at 12 (`HandIntrusion`): in a ±14° wedge from 0.66R to 0.92R, more than 3%
     of the area is marker-bright.
   - Resolution: the gap is within 0.75 px of the limit (`GAP_PX_UNCERTAINTY`).
6. **Decide** (`GmtHumanQcMath`): the gap attention limit is
   `LOW_CLEARANCE_ATTENTION = 0.070`, measured on the outer-edge definition. **This
   value is provisional** (see the research doc).
7. **Report**: `GmtHumanSummary` writes the text and `MeasuredOverlayRenderer` draws
   the overlay. `WatchAlignCoreV13` assembles the report, and `MainActivity` is the UI.

Reference gap readings:

- Official renders: 0.086–0.096.
- Real genuine photos: 0.084, 0.103, 0.103, 0.106.
- The summary quotes "about 0.085–0.105" as reference only, not as a decision boundary.

`SafePerspectiveGmtOverlayV2` is now dead code apart from `fitDialEdgeBgr`, which is
still used.

## 7. What's been validated

These results are recorded in `docs/research/gmt12_outer_edge_gap_2026-09-26.md`.

**Agrees with r/RepTimeQC feedback:**

- p3hHVMB: the app reads clockwise, matching the moderator's "slight CW cant".
- 7s6PyXJ: the app reads clockwise at low confidence, matching the commenter. This
  needed the apex check.
- Clean factory bpdi5xV: the app reads a gap of 0.040, which is visibly shallow.
- Bruce Wayne / GRNR: the gap reads normal. Its fine lean is below what the photo can
  resolve. This needed the squareness check.

**Field run, 50 labelled Reddit QC photos:** most typical QC photos can't be judged at
12, because the triangle is too small, the photo is angled, a hand is in the way, or
it isn't a dial shot. The app now says "not judged" in those cases instead of giving a
false flag. A small gap is a real flaw on some replicas, but it isn't a general tell.

## 8. To-do, in the order the user approved

1. **Merge PR #24** (CI is green).
2. **Triangle edge on shadowed surround.** Measure the triangle edge lower on the
   brightness scale, around 25–35% of the way from dial to marker instead of 50%, so
   a dim or shadowed surround still counts as marker. Re-check against the genuine
   gap readings and the RepTimeQC cases in section 7 so the gap definition doesn't
   drift. If it does drift, recalibrate `LOW_CLEARANCE_ATTENTION` with provenance.
3. **Wording: "rotated" vs "skewed / tip off-centre".** Split the alignment message
   into rotation, where the whole triangle is turned, and skew, where the tip is off
   the base's centre line. The data is already in the geometry.
4. **12-marker close-up panel.** In the UI, add a zoomed crop of the 12 area with the
   measured overlay. Also give "not judged" (too small, hand, retake) a clearer visual
   style.
5. **Then the 6-marker check**: the baton at 6 and its gap to the minute track. Use
   the same pattern as the 12 check: measure, gate, summarise, draw.

Other open items:

- Detect a thin seconds hand at 12. The current hand check misses it.
- The gap limit of 0.070 needs more genuine photos. It might move to around 0.075.
- Clean up dead code (`SafePerspectiveGmtOverlayV2`, apart from `fitDialEdgeBgr`).
- About 210 corpus photos haven't been run yet. Use the harness Batch mode, sharded.

## 9. Working with this user (Greg)

- He tests on his phone and sends screenshots. Reproduce each one with the harness
  before changing code.
- **Don't run long blocking waits or polling loops.** He has interrupted them several
  times. Check CI once, then report.
- Start with small batches (for example "do the first 20"), show the results, then
  continue.
- He wants the overlay to show only what was measured.
- He doesn't want template guesses. He prefers honest "not judged" over a confident
  wrong answer.
- `AGENTS.md` rules:
  - Don't weaken QC thresholds to make tests pass.
  - Only commit or push when asked. He has asked for this throughout, so push the
    feature branch after each verified alpha.
- End commit messages with the attribution lines your harness provides.

## 10. Lessons learned (bugs that cost time)

- `pkill -f <pattern>` killed its own shell. Don't use patterns that match your own
  command line.
- A synthetic test's geometry was wrong twice: the hand sat outside the wedge, and a
  sign was flipped. Check synthetic fixtures by drawing them.
- The triangle contour sometimes follows the lume and sometimes the metal surround.
  This is why the gap moved to the outer edge.
- Hull corners clip and bevels bend side fits. That's why the apex and squareness
  checks exist.
