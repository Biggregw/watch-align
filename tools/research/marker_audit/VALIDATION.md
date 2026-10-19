# Validation record

## Completed local checks

- Unmodified production 1.6.3: official Gradle unit suite, **455 passed**.
- Corrected production: official Gradle unit suite, **467 passed**, zero failed,
  errored or skipped. Existing tests were not disabled or weakened.
- Corrected `:app:assembleDebug`: passed.
- Corrected `:app:assembleDebugAndroidTest`: passed with all four verified image
  fixtures prepared. Building instrumentation is not counted as executing it.
- Audit integrity tests: **5 passed**, including missing ten-case coverage,
  missing target hours, mismatching matched-run keys, conflicting duplicates,
  equal-but-incomplete cohorts and changed tracing anchors.
- Exact ten-case reproduction: **110/110 marker rows identical** to the
  independently retrieved original artifact before the correction.
- All ten corrected traces match the actual pipeline polygon/rotation/coverage
  to 1e-10; all ten retain the exact original photo and homography.
- Matched genuine: **3,743 markers / 328 assessed unique photographs**, of which
  **3,258 markers / 286 photographs** meet the reviewed QC input criteria.
  No new Can't check; all 2,996 previously uncertainty-free measurements retained.
- Seven existing SHA-pinned QC controls: all **81** classifications preserved;
  the labelled GMT crooked-6 miss already exists in baseline (see report).

Local Java was Temurin 17.0.20.1, desktop OpenCV 4.9.0-0; official Gradle wrapper
and committed Android dependencies were used without upgrades. Example command:

```sh
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/sdk \
  bash android/gradlew -p android :app:testDebugUnitTest :app:assembleDebug
```

The managed workspace has no KVM support. A local emulator was not counted as
validated. Hardware-accelerated GitHub Android instrumentation is tracked below.

## GitHub Android runs

| Run | Revision | Outcome / meaning |
|---|---|---|
| [39301372339](https://github.com/Biggregw/watch-align/actions/runs/39301372339) | 6a0f11e6 | Baseline diagnostics branch CI passed; does not validate the later correction. |
| [39305849352](https://github.com/Biggregw/watch-align/actions/runs/39305849352) | c812a83f | Build/unit job passed. Instrumentation preparation failed because both Date originals were unavailable at their pinned hashes. Emulator tests did not start. |
| [39306132742](https://github.com/Biggregw/watch-align/actions/runs/39306132742) | 31078ac6 | Same preparation failure after another JPEG negotiation option. No emulator validation claimed. |
| [39307094723](https://github.com/Biggregw/watch-align/actions/runs/39307094723) | cc66657c | Fixtures verified; 10 emulator tests executed, 6 passed, 4 new image regressions failed. Logcat proves OutOfMemoryError under the unchanged 192 MB heap. |
| [39309182307](https://github.com/Biggregw/watch-align/actions/runs/39309182307) | 9a7eecfb | Evidence-only revision of the same code; repeat failure, not a validated build. |
| [39311780904](https://github.com/Biggregw/watch-align/actions/runs/39311780904) | 29c2403d | **467 unit tests and 10 API 35 emulator tests passed**, zero failures/errors/skips. All four hash-pinned image regressions executed successfully. APK build passed. |

The four files were retained unchanged with their catalogue hashes and source
URLs. This repairs test reproducibility without substituting images, skipping
tests or altering expected measurements. Missing/corrupt fixtures remain fatal.

The new instrumentation class runs the actual Android Bitmap decoder and native
OpenCV pipeline for GMT, No-Date and both Date baton cases. It checks byte hashes,
successful analysis, expected marker status, numeric rotation/position, and
seconds-hand evidence on both targets. Numeric tolerances allow Android/desktop
JPEG decoding differences (0.2° rotation, 0.15 percentage points of R) and remain
far smaller than the original approximately 2° shaft errors.

The final run was checked from the downloaded test XML and per-test logcat, not
just its workflow conclusion. `results/android-validation.json` retains the
run/revision/artifact identifiers, all ten executed test names and the four
actual image measurements; `android-instrumentation.xml` retains the raw report.
On the API 35 x86_64 Pixel 2 emulator with its unchanged 192 MB heap:

| Photo / target | Rotation | Side % R | Radial % R | Coverage | Edge uncertainty |
|---|---:|---:|---:|---:|---|
| GMT 49ba843c0a7e19d7 / 6 | +0.379418° | −0.082974 | +0.341849 | 95.87% | none |
| No-Date d15ed6cf309dec1f / 6 | −0.165304° | −0.029255 | +0.179566 | 98.78% | none |
| Date ee07ceedbb6f0bd3 / 6 | −0.071607° | −0.039682 | +0.063850 | 62.24% | none |
| Date 2802b4a30cfb277b / 9 | +0.430342° | +0.226892 | +0.049566 | 60.55% | none |

The other six tests are two legacy official-image checks, three EXIF checks,
and the existing bulk-photo smoke test. Their passing status is not substituted
for per-marker numerical validation. No physical Android handset was tested.

### Memory failure diagnosed during Android validation

The new full-pipeline tests exposed an existing allocation failure, rather than
a numeric assertion mismatch. `ClearlyOff.Img.polar` allocated full-grid X, Y,
projected X, projected Y and sampled-value arrays before copying them into the
output rows. The minute grid for these photographs has roughly 4.6 million
values: a single temporary double array takes about 36.6 MB. Android logcat
records that allocation failing with only 24 MB available in a 192 MB heap.
The two-dimensional median also boxed every sample into a `Double` list.

`MarkerMemoryProbe` independently reproduces this on **unmodified 1.6.3** with
`-Xmx192m`: pose succeeds but ClearlyOff fails, and the uncaught diagnostic stack
identifies `Img.polar` called by `tickTrack`. The minimal storage correction
samples one polar row at a time with identical coordinates/interpolation and
computes the grid median in a single primitive scratch array. It changes neither
resolution nor mathematical definitions, thresholds, pose or model references.
The same original image now completes all 11 markers under the same heap limit.
The baseline and corrected probe logs are retained in `results/memory-*.log`.

Two new unit tests compare every sample bit-for-bit against the original batch
calculation, including projective mapping/off-image samples, and compare median
semantics for odd/even inputs, NaNs, signed zero and infinities. The full genuine
cohort is being rechecked for identical output after this memory-only change.
The emulator heap limit and image-test assertions remain unchanged.

## Remaining discrepancies

These do not change before/after, except the excluded GMT 5 already described:

- **No-Date `728e4c157ef1075d`, triangle 12:** Alpha99 clean; no detected seconds
  contact, 100% fitted coverage, 0.118 px RMS, zero inconsistent samples. It is
  classified Clearly off at −1.2209°, just beyond 1.2°. Another verified photo
  of the same catalogue watch (`445e3c952d68ede4`) reads −1.0646° and passes.
  This is not a shaft error. Local reference-line/registration repeatability and
  a small real deviation cannot be separated by these two photographs. A low
  residual alone does not prove the manufacturing interpretation. The existing
  genuine clear warning is disclosed, not removed by changing thresholds.
- **No-Date `6ac54fe6dfcbd61a`, rounds 4, 5 and 7:** Alpha99 clean, no seconds
  contact, 100% nominal coverage. RMS is 0.978 / 1.018 / 1.073 px; inconsistent
  fractions are 35.5 / 37.1 / 34.4%. Exact plotted observations switch between
  bright outer metal and the inner bright boundary where the outer rim is dark.
  The geometric fit therefore mixes boundary types. This is poor metal-edge
  visibility, not proven marker position error or seconds interference. Another
  lower-resolution photo of the same watch (`855041528fc1ac79`, 399 px dial)
  retains the 5 warning but passes 4/7; it is excluded from primary QC results by
  the existing resolution floor. No luminous-only measurement was substituted.
- **GMT `66c4527d45cf91b0`, round 8:** excluded oblique/241 px image. Alpha99
  detects generic hand contact, but no seconds direction at this marker. The
  close-up shows a broad hand crossing the marker, 57.8% coverage and rough
  residuals. The seconds-only correction cannot resolve this independent hand
  reconstruction problem. The same photograph's round 5 has seconds contact;
  its warning disappears but quality remains poor, so no accuracy gain is claimed.
- **Date:** six remaining primary warnings are the triangle cases documented
  individually in `REPORT.md`. One further passing triangle still has very poor
  boundary consistency. Date activation is not justified by this regression.

The additional warnings were traced using the same observational fitter and
production-parity check. Their annotated panels are in the local review bundle.

## Merge and activation decision

Date activation is **not supported**: the triangle boundary/reconstruction
discrepancies remain, the recovered cohort is smaller than the original run, and
there is no Date-specific real-defect ground-truth control.

The seconds-crossing correction is independently supported by the two real
baton traces, known-geometry synthetic tests and matching three-family results.
The final merge recommendation depends on the completed Android execution below.
The branch remains a draft; production `main`, releases and model visibility are
unchanged. No change is proposed to hide the unrelated genuine warnings or the
existing labelled-GMT defect miss.
