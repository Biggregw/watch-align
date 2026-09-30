# Watch Align: handoff notes (updated 2026-09-29, alpha63)

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

Since alpha55 it also checks the **6 o'clock baton**: centring between the 29 and 31
ticks, and rotation (see `docs/research/gmt_fix_list_2026-09-27.md`). The result screen
shows enlarged close-ups of the 12 and 6. The 9 baton followed in alpha59 and the eight
**round markers** (1, 2, 4, 5, 7, 8, 10, 11) in alpha61, so every hour marker is now
checked (the 3 is the date window).

The app never says "genuine" or "fake". It flags things to look at.

- Version: `CORE_VERSION "1.3.0-alpha63"`, `versionCode 13063` in `android/app/build.gradle`.

## 2. Branches and PRs

| Branch | State |
|---|---|
| `main` | Has alpha49–54 (PR #24, merged 2026-09-27) and alpha55–61 (PR #25, merged 2026-09-28). |
| `feature/gmt-qc-fix-list` | alpha55 (the whole fix list below) and alpha56 (resize check, emulator photo run) and alpha57 (off-centre 12, 6 confidence reasons, band fallback and resize check for the 6), then alpha58–61 (12 chord axis, 9 baton, photo-angle resize check, round markers). PR **#25** into `main`. |
| `experiment/template-marker-consensus-shelved` | A shelved experiment: it fitted a template from marker consensus to fix a Pepsi overlay offset. The user rejected this in favour of the measured overlay, so it is **kept for reference only and should not be merged**. |

The `gh` CLI isn't installed, but the GitHub API works through the proxy, so use curl
against `api.github.com/repos/Biggregw/watch-align`. A new session needs its own GitHub
connection to push.

## 3. Build and test

Use JDK 17 and the committed Gradle 8.14.5 wrapper. Don't upgrade the toolchain.

```
cd android
JAVA_HOME=<jdk17> sh ./gradlew --no-daemon -q \
  -Dorg.gradle.java.installations.paths=<jdk17> \
  -Dorg.gradle.java.installations.auto-download=false \
  :app:testDebugUnitTest :app:assembleDebug
```

- 201 JVM unit tests, all passing.
- `:app:compileDebugAndroidTestJavaSource` compiles the on-device test
  (`GenuineOfficialImageValidationTest`). It needs network access for
  androidx.test, so **don't pass `--offline`** for it. Only check the result of the
  compile you actually ran before pushing: a previous session pushed after an
  offline compile failure.
- The device tests run in CI (`.github/workflows/build-android.yml`) on an Android 15
  x86_64 emulator, on every push to a PR into `main`. This workspace can't run an
  emulator (no KVM).
  - `GenuineOfficialImageValidationTest` fetches official fixtures with
    `fetch_genuine_fixtures.py`. It asserts that the report contains `SUMMARY`,
    `HUMAN 12-MARKER QC` and `Bottom line: nothing flagged at 12`.
  - `EmulatorPhotoRunTest` (alpha56) runs every photo in `androidTest/assets/e2e/`
    through the Check-button path. CI fetches about 10 corpus photos into that folder
    first; it is git-ignored, and third-party photos are never committed. CI then pulls
    each report, the timing, the close-ups, the overlay and the lossless working image
    with `run-as`, and uploads them as the `emulator-photo-run` artifact. Download it
    through the Actions API.
  - To run a new photo on the emulator, add its path to the "Fetch photos for the
    emulator photo run" step.

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
- Since alpha56 the drivers load photos through `drivers/Load.java`, which works like
  `MainActivity.decode`: libjpeg decode, `inSampleSize` as DCT scaling, and a bilinear
  reduction to 1600 px. `-Dwa.load=imageio` gives the old loader.
- Use the harness to reproduce any screenshot the user sends, but it will not match the
  phone to the grey level. The decoders differ by about 0.3–0.5 grey levels on average,
  and before alpha56 that was enough to flip borderline 12 readings. For an exact
  reproduction, analyse the phone's own working image (`*_working.png` from the
  emulator run) with `Perturb`, which reads PNGs as they are.
- `Perturb photo.jpg`: the same photo under six near-identical loads. It prints how far
  the 12 and 6 readings move, and the resize-check range.
- `PixCmp device_working.png photo.jpg`: pixel difference between the device's decode
  and the harness loaders.

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

Reference gap readings (alpha55 corpus run):

- Official renders: 0.087–0.095.
- Real genuine photos: 0.081, 0.083, 0.090, 0.099, 0.102, 0.107.
- The summary quotes "about 0.08–0.11" as reference only, not as a decision boundary.
  The limit stays at 0.070 (section 8 of the fix-list note).

alpha55 additions to this pipeline:

- The Hough proposals run at 480 px or less (this was minutes per photo before).
- The refiner retries with a "band" level when the shape checks fail.
- Rotations over 8° are treated as misdetections.
- There is a thin-hand line check.
- The 6 baton runs through `GmtSixLandmarkAnalyzer` on the image turned 180°.
- The dial-edge helper now lives in `DialEdgeFitter`; the old overlay classes are gone.

alpha56: the **resize check** (`GmtTwelveLandmarkAnalyzer.measureStability`). The 12 is
measured again at 94% and 88% scale. A gap or rotation that moves by more than about
1 px at the marker, and could change the verdict, is withheld. So is one where a
re-measurement found a different edge. A concern that every scale agrees on is kept as
CHECK. `docs/research/gmt_resample_stability_2026-09-27.md` has the evidence: no
genuine reading changed, and 8 replica readings were withheld.

alpha57 additions:

- An **off-centre 12** check (`GmtHumanQcMath.assessOffCentre`). It flags uneven 59/01
  spacing when there is no rotation: CHECK at 0.06 and STRONG at 0.12, set against a
  genuine maximum of 0.04.
- The 6 now gives a **reason when it is low confidence**, and retries the **edge fit with
  the band level**, as the 12 does.
- The 6 has its own **resize check**.

`docs/research/gmt12_offcentre_2026-09-27.md` has the evidence.

alpha58: the **12 rotation is now measured square to the 59–01 tick chord**, not from the
dial centre. It is withheld when the two references differ by more than 1.5°, and a lean
without a top-edge tilt is only flagged from 2°. On genuine Phillips Pepsi photos this
takes the app from 5 false flags to 0 (`docs/research/gmt12_axis_reference_2026-09-28.md`).

alpha59: the **9 o'clock baton** is checked with the same code as the 6, using a 90°
turn instead of 180° (`GmtSixLandmarkAnalyzer.Position`). No genuine 9 is flagged
(`docs/research/gmt9_baton_2026-09-28.md`).

alpha60: the **photo-angle rating and gap-direction cue get the resize check**. The rating
is the median over the photo and its 94% and 88% copies, and the gap direction is used
only when all three copies agree (`docs/research/gmt_pose_stability_2026-09-28.md`).

alpha61: the **round hour markers** (`GmtRoundMarkerAnalyzer`). Each marker's outer edge is
traced on 72 rays and fitted with a circle (least-median start, trimmed least squares). It is
measured against the inner ends of the minute ticks either side: sideways offset (fraction of
its diameter), inset, and size against the other round markers on the dial. Markers are
placed by an affine fit of the master layout to the markers found, with the dial's ellipse
squash undone for the measurements. Gates: size (24 px), photo angle, fit quality (edge
contrast, tick score, rejected rays, inset range, tick agreement), resize check, and a local
hand check (a marker-bright ring just outside the marker, plus red/blue GMT-hand colour).
Levels: offset CHECK 0.15 / STRONG 0.25, size ±0.12 (compared only when the median is 36 px or more).
The summary opens with an "All markers" line listing every hour position. The baton finder's
rectangularity limit went from 0.80 to 0.60, so clean photos stop reporting the 6 and 9 as
not found. A low-confidence baton keeps only an offset concern, not a rotation concern.
18 more genuine photos (WOS CPO, and the official 2026 renders of the other GMT models) are
in the regression. They are downloaded, not committed; the list is in the research note. The tick search was also moved from
`Mat.get` to a byte array (`GmtTwelveLandmarkAnalyzer.Px`), with identical 12/6/9 results
(`docs/research/gmt_round_markers_2026-09-28.md`).

alpha61 also re-reads **small dials at full resolution** (`GmtDialCrop`, `FullResSource`; on the
phone a `BitmapRegionDecoder`). This applies when the preview dial radius is under 230 px and
the original has more pixels; the crop is scaled to a radius of at most 380 px. It also
changes the 12 rotation rule: STRONG now needs 2° as well as a visible rise. Genuine WOS CPO
photos read up to −1.6°, which overlaps the replica flags at 1.0–1.7°. See
`docs/research/gmt_dial_crop_2026-09-28.md`, which also lists the two 12 CHECKs that remain on
the 20 Phillips genuine photos (off-centre 0.080; skew +2.2°). Both were then dealt with
(`docs/research/gmt12_offcentre_recheck_2026-09-28.md`):

- The off-centre reading is real placement, not a misplaced tick. The levels are now 0.10
  and 0.15.
- The summary had named the wrong side since alpha57; it is shifted towards the larger
  spacing.
- A lean with a level top edge must clear 2° at every resize scale to stay a concern.

**One generic GMT check (user's decision, 2026-09-28):** the app doesn't tell GMT-Master II
references apart. Only the generic profile is offered (`MainActivity.offeredModels`), the model
dropdown is hidden, the screen and report say "Rolex GMT-Master II", and the internal model code
stays `126710BLNR`. A per-model list or a bezel check is not wanted.

The date side is read from the photo (`GmtDialLayout`): the date is at 3 with a baton at 9, or at
9 with a baton at 3 on the mirrored 126720VTNR Sprite. See
`docs/research/gmt_generic_layout_2026-09-28.md`.

**What the regression corpus actually covers.** It assumes the other references share the
126710BLNR master geometry; it has not measured them separately.

| reference | genuine photos |
|---|---|
| 126710BLNR | the bulk: Reddit sets, the official render, Phillips, WOS |
| 126710BLRO | 10 Phillips Pepsi, 12 WOS, 2 Phillips |
| 126711CHNR | 10 |
| 126715CHNR | 6 |
| 126710GRNR | 2 |
| 126718GRNR | 2 |
| 126713GRNR | 1 (official render only) |
| 126720VTNR | 5; only the official render gives a readable 3 baton |

All replica photos are 126710BLNR.

**Scope:** the deliverable is the Android app. The desktop harness only runs the app's
Java code for fast testing. The Python tools in `tools/research/` (branch
`feature/gmt-human-qc-auto-landmarks`) are a separate, older research track and don't
ship in the app.

alpha62: **round-marker size is compared surround to surround.** A marker's lume and its
surround are both edges, and the fit can land on either: on genuine Bob's Watches 126720VTNR
182860 the 8 was traced on its surround (60 px) and the rest on their lume (50 px), and the 8
read 1.21x the others. The fit now keeps every edge it found on each ray; a marker whose lume
and surround are both found as rings (surround 1.12-1.5x the lume) is compared on its
surround, and a marker with only one ring is not size-judged. With fewer than 4 such markers
size is not assessed at all (review: the old fallback to fitted edges was the same lume-vs-surround
ambiguity). The one false flag on the clean genuine set is gone; synthetic 0.85x / 1.15x markers
still read 0.856 / 1.134 (CHECK) (`docs/research/gmt_round_size_edges_2026-09-29.md`).

alpha62 review fixes: (1) no fitted-edge size fallback, as above: on the 319-photo regression size
is now compared on 12 photos instead of 92; no size flag existed on the dropped ones. (2) Date side
UNKNOWN: neither the 3 nor the 9 baton gets a verdict ("not checked (date side not determined)");
12 photos lose a side-baton CLEAR, none a CHECK. (3) "No readable dial" is only reported when no
marker or baton has a CHECK or STRONG verdict; one replica photo (rep_cf_UpTW8nx image_06, 12 gap
0.030) now shows its gap CHECK instead of "nothing checked". No other verdict changed.

alpha63: **the rehaut alone can no longer reject a photo that the round markers show is straight on.**
The angle rating's rehaut RETAKE rules (a local sector below 0.45 of the mean, or a coherent
global fit below 0.48) now become CORRECTABLE when the round-marker layout (`GmtMarkerPose`)
confidently puts the camera within 5 degrees of straight on at its upper bound: at least 6 cleanly
traced markers covering all four quarters, affine fit to the master layout without the 60 tick,
residual at most 0.006 of the radius after leaving out one misplaced marker, and a
leave-one-out jackknife for the bound. Without a confident layout the rehaut decides as before, and
the dial-ellipse RETAKE is untouched. The round markers are now traced before the angle rating
(same call, moved). Corpus (319 + 18 Bob's Watches photos): only WOS 40411271 changes, RETAKE to
CORRECTABLE (markers 2.0 deg, at most 4.1; the 9 rehaut sector was mismeasured at 6 px), and its
12, 9 and seven round markers are now judged, all CLEAR. A pose-free "un-squash" of the 12 and
baton rotations (measuring them after undoing the dial-edge ellipse) was tried and dropped: on
genuine photos it did not reduce the rotations (9 o'clock readings of 2 deg or more went from 1 to 4)
and it added a false 12 CHECK (Phillips 224135). See `docs/research/gmt_dial_pose_policy_2026-09-29.md`.

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

## 8. To-do

The whole list the user approved after alpha54 is done in alpha55: shadowed edges,
rotated/skewed wording, thin hands, close-ups, the 6 baton, the gap limit review and dead
code. `docs/research/gmt_fix_list_2026-09-27.md` has the measurements for each.

Open items:

- **The 6-baton levels are provisional.** They rest on 5 genuine photos (centring within
  ±0.075, rotation within ±1.8°). Add genuine photos where the 6 is visible without a
  hand.
- About 150 corpus replica photos haven't been run yet. Run them with the harness Batch
  mode; the driver also prints the 6 columns.
- Possible next markers: 9 and 3 (the date window at 3 needs different handling), and
  the round markers.
- Emulator timing (CI, x86_64, no GPU): 4–29 s per photo with alpha55. The resize
  check adds two 12-only re-measurements. The user's phone hasn't been timed.
- The off-centre levels rest on 5 genuine photos. Add more genuine straight-on photos
  with the hands away from 12.
- When the edge fit fails, the "too small" gate measures the lume contour, about 80% of
  the outer width. A 43 px triangle can then read "too small at 34 px".

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
- Slow runs came from `HoughCircles` at full size (minutes on busy photos), not from the
  harness. Keep the proposals downscaled.
- A synthetic test's geometry was wrong twice: the hand sat outside the wedge, and a
  sign was flipped. Check synthetic fixtures by drawing them.
- The triangle contour sometimes follows the lume and sometimes the metal surround.
  This is why the gap moved to the outer edge.
- Hull corners clip and bevels bend side fits. That's why the apex and squareness
  checks exist.
