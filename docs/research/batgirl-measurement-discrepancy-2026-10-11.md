# Batgirl Android / desktop measurement discrepancy — 11 October 2026

Protocol v0.3. Continue draft PR #52, branch
`feature/marker-mismatch-overlay-consistency`, before source `6a93b1fc`.
Read the evidence pack's `README_FIRST.md`, the
[reference investigation](alpha106-yellow-template-parity-2026-10-11.md),
[overlay report](marker-mismatch-overlay-consistency-2026-10-11.md) and
[previous inspection review](inspection-reliability-review-2026-10-11.md).

## Finding and bounded change

**The exact Samsung input is still unidentified.** The supplied original and
phone screenshot cannot yet be treated as identical-pixel runs. No demonstrated
production implementation fault justifies changing the detector, perspective
correction, colour handling, genuine geometry, uncertainty or QC decisions.

Controlled copies of the supplied original demonstrate substantial rotation
sensitivity to resolution and resampling. Classification changes are reproducible
under the existing decision rule, while both long edges remain anticlockwise.
Freezing the source perspective transform leaves almost all of the variation.
This identifies sampled edge localisation as the dominant effect in **these
controlled desktop tests**, not the proven cause of the Samsung result.

The only production addition is passive reproduction evidence inside the existing
collapsed **Exact measurements** section: processed width/height, SHA256 of the
actual OpenCV RGBA and grayscale bytes, frozen homography, precise radius/tick RMS,
ring coefficients, raw/genuine-adjusted/scoring-relative baton angles and exact
rotation comparison values. Hashing reads matrix rows without changing pixels,
and uses bounded row storage. It avoids `Bitmap.getPixels`, which could introduce
a colour-space conversion before hashing. Diagnostics do not feed any measurement
or decision; a fingerprint failure does not prevent analysis.

No APK, merge, detector tuning, template resizing/padding, new score, uncertainty
multiplier, genuine tolerance or automatic downgrade is introduced. The phone's
recorded CLEAR rotation remains recorded CLEAR; the supplied original remains
WORTH. Position and area are withheld independently of rotation.

## Exact provenance and import path

Available original: `04_batgirl_original_37828.jpg` (supplied as `37828.jpg`),
**341108 bytes, 1152 × 1536, RGB JPEG**, SHA256
`c50e0845ddb6c50701a4ba8ab6b396092e67988a747206f77743c02e7c7e54eb`.
No EXIF orientation tag or other EXIF entries. Its 520-byte ICC profile is
**Display P3 Gamut with sRGB Transfer**. This is one owner-supplied replica,
not a genuine manufacturing or acceptance calibration sample.

The pack describes this as the unmodified original associated with the app test;
that description is preserved. It does not establish the selected content URI's
bytes, subsequent import processing or decoded matrix identity. Screenshots
05–07 are evidence of phone output, never numerical input substitutes. The exact
phone file dimensions, SHA256, EXIF, decoder output and frozen H are unavailable.
“Latest PR #52 APK” is the user's build description; a precise APK commit and
Samsung decoder/Skia version were not independently captured.

Current source import, `PerspectiveOverlayPocActivity`:

1. `ACTION_OPEN_DOCUMENT`, `image/*`, `CATEGORY_OPENABLE`; reads the selected
   content-resolver stream, not the resized preview widget.
2. `BitmapFactory.decodeStream` reads bounds, then decodes with preferred
   `ARGB_8888`. Power-of-two sampling occurs only while
   `maxDimension / (sample * 2) >= 3200`.
3. A decoded long side above 3200 is resized with
   `Bitmap.createScaledBitmap(..., true)`; otherwise an immutable ARGB copy is used.
4. No explicit EXIF rotation, preferred colour space or other resize below this
   limit. The candidate bitmap is passed to `Alpha99Pipeline`.

**If this exact supplied file was selected, this source requests sample 1 and
retains 1152 × 1536.** The 3200-px rule alone does not explain R 225 instead of 270.
An alternate-sized source, crop, prior re-encoding or different fitted pose remains
possible. R 225 is numerically consistent with an approximately 960-wide version,
but is not proof of its dimensions or resizing method.

The current desktop harness uses `Alpha96Calib.loadAlpha96`, OpenCV 4.9
`imread(IMREAD_COLOR)`, DCT-reduced decoding only above its 3200-px rule and
`INTER_LINEAR` final downsizing. It does not perform ICC colour conversion.
`Load.fromBgr` supplies opaque RGBA. The legacy `Load.photo` 1600-px rule is
**not** this harness's production loader. The optional ImageIO comparison below
has a 1600 limit, inactive for this original's 1536-px long side.

Android's unspecified preferred colour space does not prove sRGB conversion:
[Android decoder source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/jni/BitmapFactory.cpp)
and [Skia output-space selection](https://skia.googlesource.com/skia/+/refs/heads/main/src/codec/SkAndroidCodec.cpp)
allow an embedded colour space. These upstream sources are context, not an audit
of Samsung's exact binaries. Explicit P3-to-sRGB conversion was tested separately.

Minimum missing evidence: **the exact file selected on Samsung, exported as a file
without resaving/recompression**, and how it reached the picker. This is the same
QC photograph, not an additional capture. If that file is unavailable, the new
processed dimensions/fingerprints and frozen H from a subsequently reviewed build
can distinguish pixel identity from pose differences. Hashes identify matrices but
cannot reconstruct unknown pixels; a lossless processed bitmap would then be needed
to independently rerun the precise phone input. No new build is requested here.

## Stage comparison

Angles below are CCW magnitudes except where a signed raw angle is explicit.
Phone values are rounded observations, not invented exact exports.

| Stage / measure | Samsung screenshot / user report | Supplied original, desktop |
| --- | --- | ---: |
| Processed dimensions / hashes | unavailable | 1152 × 1536; hashes in CSV |
| Frozen H | unavailable | exact nine coefficients in CSV |
| Dial radius, px | approximately 225 | 269.635814259098 |
| Minute ticks / sectors | 45 / 11 | 45 / 11 |
| Tick RMS, px | approximately 0.19 | 0.233542950998 |
| Minute-branch change | not independently exported | 0 |
| Ring rotation, CW | approximately 0.10° | 0.094940369691° |
| Raw 9 rotation | approximately −1.37° | −1.116635321737° |
| Genuine-adjusted 9 comparison | reported approximately 1.37° | 1.174605321737° |
| Upper long edge vs scoring reference | approximately 1.37° | 1.171810879032° |
| Lower long edge vs scoring reference | approximately 1.57° | 1.251289796443° |
| Width inconsistency, px | approximately 2 inward; cause uncertain | −2.430242132234 vs limit 1.601636736699 |
| Position / filled-area comparison | withheld / withheld | withheld / withheld |
| Classification | CLEAR FINDING | WORTH A LOOK |

The raw and genuine-adjusted readouts are distinct: production QC subtracts
`nine_rot.nominal = +0.057970°` from raw rotation, then compares its magnitude.
It does **not** subtract the ring rotation. Long-edge diagnostics instead compare
each fitted edge with the ring-adjusted scoring polygon. The phone's approximate
1.37° is not a full-precision export identifying both of these readouts; it must
not be silently compared as though it were. Even this readout ambiguity does not
explain the radius discrepancy.

Existing genuine maximum **0.645430°**, sigma **0.205383°**, 3-sigma allowance
**0.616149°**; CLEAR boundary **1.261579°** (also subject to existing visibility
and assessment gates). Desktop original margin is **−0.086973678263°**.
At both R 225 and R 269.636 the same genuine sigma floor wins over
`43.434971 deg·px / R`. Consequently the classification discrepancy is not caused
by a different allowance at these radii. The exact phone sigma was not exported;
these are the current code's values at its reported R.

**Earliest observable discrepancy: image/pose scale**, already different before
individual marker fitting. Equal tick/sector counts and similar ring rotation do
not prove equal pixels or equal homography. The precise stage of the actual
phone/desktop divergence cannot be assigned without the phone input or diagnostics.

## Controlled sensitivity experiment

[All 47 stage exports](batgirl-input-sensitivity-2026-10-11.csv) and
[preparation hashes / versions](batgirl-input-preparation-2026-10-11.json).
24 predeclared inputs: original OpenCV and Java ImageIO paths; encoded P3 RGB
values versus explicit ImageCms conversion to sRGB; native lossless PNG; JPEG
qualities 95/85; 960 × 1280 BOX/BILINEAR/BICUBIC/LANCZOS; JPEG 95/85 of the
960 BILINEAR copy; neighbouring BILINEAR widths 936 and 984. Profile stripped
from derivatives; no implicit second conversion. Pillow **12.3.0**, LittleCMS
**2.19**, JDK **17**, OpenCV **4.9.0**. JPEG quality is an encoder setting, not a
universal measure of image quality; conclusions are bounded to these inputs.

Every input is measured using the existing pipeline geometry and evidence rules.
For each after the baseline, a second **diagnostic intervention** retains the
original frozen H mapped to destination pixel centres:
`x' = sx × (x + 0.5) − 0.5`, similarly for y. Marker/ring fits are rerun on the
new pixels. These frozen-pose rows are not independent production QC runs.
CSV `refit_*` pose counts/RMS describe each input's fresh pose, not the intervention
H. H, ring and marker results on each row describe that row's actual measurement.

| Input (encoded RGB unless stated) | R, px | 9 CCW, genuine-adjusted | Upper / lower CCW | Status |
| --- | ---: | ---: | ---: | --- |
| Original OpenCV | 269.635814 | 1.174605° | 1.171811° / 1.251290° | WORTH |
| Same JPEG, ImageIO path | 269.647208 | 1.174219° | 1.171548° / 1.251023° | WORTH |
| Native JPEG re-encode q95 | 269.626030 | 1.176709° | 1.169212° / 1.255371° | WORTH |
| Native JPEG re-encode q85 | 269.584296 | 1.170049° | 1.171375° / 1.241185° | WORTH |
| 960 BOX | 224.758324 | 1.610219° | 1.672085° / 1.637795° | CLEAR |
| 960 BILINEAR | 224.760360 | 1.197444° | 1.205094° / 1.269953° | WORTH |
| 960 BILINEAR JPEG q95 | 224.761242 | 1.220165° | 1.200435° / 1.313246° | WORTH |
| 960 BILINEAR JPEG q85 | 224.829830 | 1.203030° | 1.230172° / 1.253286° | WORTH |
| 960 BICUBIC | 224.787543 | 1.265001° | 1.254934° / 1.351660° | CLEAR |
| 960 LANCZOS | 224.768946 | 1.274103° | 1.261743° / 1.371205° | CLEAR |
| 936 BILINEAR | 219.114224 | 1.279961° | 1.308232° / 1.337954° | CLEAR |
| 984 BILINEAR | 230.380569 | 1.257454° | 1.330716° / 1.260704° | WORTH |
| Native explicit P3 → sRGB PNG | 269.644931 | 1.173963° | 1.171641° / 1.250389° | WORTH |

- **24/24 fresh poses valid**, 43–45 ticks, 11 sectors, branch change 0.
  Genuine-adjusted rotations span **1.168204463240–1.610219459235° CCW**:
  16 WORTH, 8 CLEAR. Every fresh and frozen-pose row has both long edges CCW,
  position withheld and area withheld. Ring rotation spans 0.093219–0.102720° CW.
- All **47 rows exactly repeat** at exported precision on a second run, including
  pixel hashes, H, ring, edge angles, withholding and classifications. This is
  deterministic repeatability for identical desktop pixels, not physical accuracy.
- Same-file OpenCV versus ImageIO changes the QC angle by **0.000386505849°**.
  This tests those desktop paths, not Samsung's actual JPEG decoder.
- Explicit P3 → sRGB changes the native result by **0.000641921211°**; across
  matched variants its largest change is **0.006151427051°**. Tested colour
  handling alone does not explain approximately 0.2° or the radius difference.
- JPEG q95/q85 alone changes native rotation by at most **0.005759°** across
  the two colour preparations, and the 960 BILINEAR result by at most **0.023644°**.
  They do not change status for these native/BILINEAR comparisons. This does not
  exclude stronger compression, previous resampling or the unknown Samsung path.
- Kernel choice at the **same 960 × 1280 size** spans **0.412775050104°** in
  encoded RGB; neighbouring BILINEAR widths also straddle the CLEAR boundary.
  Neither a single “R≈225” label nor rounded “1.3°” predicts status reliably.
- Refit versus source-frozen pose changes QC angle by at most
  **0.010052019403°** across the 23 interventions. The 960 BOX frozen result is
  **1.607851364371°**, and BILINEAR **1.194189680925°**. Their difference remains
  approximately 0.414° without refitting perspective. No perspective fault is
  demonstrated. Ring correction is not fed into the individual rotation score.
- No tested copy independently reproduces the exact phone input and its two
  recorded edge angles. No extra parameter search was used to obtain its verdict.

The approximately 70-px marker length at R 225 makes a 0.2° line change comparable
to roughly a quarter-pixel change in end-to-end rise. Sampling transitions and
corner/reflection cues can shift a fitted angle while opposite lines retain a
credible common direction. Repeated direction is stronger evidence than a claim
that the exact physical rotation has been resolved to hundredths of a degree.

## Verification and recommendation

- **362 Android JVM tests, zero failures/errors**, using JDK 17 and the committed
  wrapper. New tests pin the existing unrounded classification of the controlled
  measurements, unchanged sigma at R 225/270, and diagnostic text without altering
  measured angles or statuses. Existing GMT/Submariner regressions remain green.
- **7 native desktop diagnostic checks**: contiguous and strided row hashing,
  RGBA bytes/alpha, pixel-centre homography identity/mapping and no H mutation.
- Preparation script recreates **all 24 input file hashes**. Repeated audit
  recreates **all 47 stage rows** exactly. Full pipeline diagnostic output matches
  the exported original's actual matrix hashes, frozen H and rotation values.
- Against before source `6a93b1fc`: **33 original marker exports identical** and
  **42 original QC rows identical**, including verified Pepsi, Batgirl and WOS CPO.
  Pepsi 9 retains **2.42127847% = 0.54416046% extra + 1.87711801% missing**;
  raw rotation **−0.19115108°**. Pepsi 12 area remains withheld. Genuine controls
  remain physical-watch controls, not additional samples from derived pixels.
- **12 production-renderer / desktop Canvas checks pass** for shared scoring
  outlines, orange extra, magenta missing, subpixel regions, withholding and view
  handoff. Filled-area maths and the saved expected/detected polygons are untouched.
- No APK assembled, device run, merge or threshold change. Desktop diagnostics
  are not Android decoder parity certification.

**Recommend greater qualitative photographic caution**, especially when the
marker spans few pixels and the measured magnitude is near an existing decision
boundary: explain that direction may be repeatable while its exact angle and
statistical label can depend on resampling/edge localisation. A CLEAR reference
comparison is not an independently established normal-view rejection. Keep the
current classification and separately withheld components; do not silently cap or
downgrade it based on this replica experiment.

Do not inflate sigma from this one replica or invent a resolution cutoff. Before
changing uncertainty numerically, test a fixed preparation protocol on independently
verified genuine physical watches, including held-out sources, and preserve
watch grouping. Independently resolved edge truth and acceptance decisions remain
necessary for physical accuracy and visual-unacceptability claims. The existing
allowance describes genuine photo-to-photo variation; it is not proven to bound
every transfer/resampling/reflection condition.

Reproduce (original stays local):

```sh
python3 tools/desktop-harness/prepare_batgirl_input_audit.py /path/to/37828.jpg /tmp/batgirl-audit
bash tools/desktop-harness/run.sh BatgirlInputAudit --self-test
bash tools/desktop-harness/run.sh BatgirlInputAudit /tmp/batgirl-audit/manifest.csv /tmp/batgirl-audit/results.csv
bash tools/desktop-harness/run.sh BatgirlInputAudit --pipeline-check /path/to/37828.jpg
# From android/, JDK 17 and configured SDK:
bash ./gradlew :app:testDebugUnitTest
```

## LESSONS LEARNED

The same watch and even the same nominal radius are insufficient provenance.
Record actual analysis pixels and frozen pose before comparing numerical outputs.
Raw rotation, genuine-nominal-adjusted rotation and scoring-relative edge angles
have different references and must be named. Deterministic results from identical
pixels do not remove sampling uncertainty. Opposing-edge direction agreement can
preserve tilt evidence while their separation invalidates position and area.
This investigation documents uncertainty without optimising an owner's watch to
pass, treating derived pixels as independent watches, or widening genuine limits.
