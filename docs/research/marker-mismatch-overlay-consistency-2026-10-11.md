# Marker outline measurement and display consistency — 11 October 2026

Starting source: `dff381b0`, branch `feature/android-alpha106-marker-mismatch-research`.
Implementation: separate task branch `feature/marker-mismatch-overlay-consistency`.
Protocol v0.3. This follows the [completed investigation](alpha106-yellow-template-parity-2026-10-11.md).
The subsequent [real-world inspection review](inspection-reliability-review-2026-10-11.md)
records preserved measurements, reporting corrections and the latest tests.

## Root cause and bounded change

The investigation is confirmed against current source. `AutomaticDialOverlay.warpOutline`
warps the fixed `Alpha92BakedDialOutline` master. `Alpha98Closeups` adjusts its
marker outlines by the measured ring; the triangle also retains its existing
genuine nominal offset. `calibrate_shapes.py` is an experimental third reference
and is not used here. The full-photo stroke obscures small placement differences.

A second inconsistency was still present in Java: `MarkerPolygonMismatch` divided
extra/missing areas by **union** area. The investigation and stress workflow had
recomputed the owner's **filled expected** denominator offline. Java now directly
uses `100 × (extraArea + missingArea) / expectedArea`. Separation of equal-sized
shapes gives 200%, so the number is neither clamped to 100 nor assigned severity.

`ExpectedMarkerOutline.polygon` is now the authoritative ring-adjusted nominal
outer-metal polygon for both marker close-up yellow and individual mismatch.
It extracts existing geometry unchanged: batons from `batonPolygon`, triangles
from the existing calibrated nominal, rounds from the existing outer-metal radius
and 128-point circle convention. Drawing the same round polygon also removes the
old integer-rounded circle rendering discrepancy. Areas are in the frozen
canonical dial plane, in R²; they are continuous polygon areas, not counts of
anti-aliased yellow stroke pixels. The entire filled outer-metal template is the
denominator. A line-fit polygon remains an approximation to a physical rim.

Every individual marker, including WITHIN markers, is accessible through
**Inspect marker outlines**. Its detail shows the yellow scoring reference and
the extra/missing/total percentages when reportable. The full-watch inspector now
offers **Scoring (ring adjusted)** and **Fixed master** as separate labelled
options, with scoring selected initially when available. Scoring yellow uses the
same saved expected polygon as its close-up and percentage; it is projected once
through the frozen H. The original fixed-master bitmap is retained unchanged.
When the ring is unavailable the scoring option is disabled, with no silent
fixed-master substitution. There is no added photo requirement.

Close-ups fill **orange = extra (A minus E)** and **magenta = missing (E minus A)**
with a text legend; yellow is expected and cyan is detected. Calculation retains
snapshots of its exact expected/detected polygons, used by both renderers. Convex
polygon subtraction partitions those polygons into non-overlapping pieces; their
summed areas match the numerical extra/missing areas. The crop transform preserves
the existing half-pixel origin. Each colour uses a single vector fill path to avoid
alpha seams along internal partition boundaries. Thin residuals are not expanded
or padded; antialias coverage on screen does not change the calculated percentage.

The original pose, detector, marker fits, ring fit, model files, calibration data,
QC thresholds and findings are unchanged. Global marker-ring placement remains
the existing separate finding. Minute-track gaps remain separate; their area is
never part of marker mismatch. No detector padding, vertex calibration or
perspective adjustment is introduced.

## Withholding and interpretation

The percentage is an outline measurement, not a physical-defect finding. It has
no severity limits and does not feed `Alpha99Findings`. Its UI displays no raw
percentage when the ring/fit is unavailable, the polygon is invalid, interference
is not cleared, the existing finding is NOT_ASSESSED/partial, or an existing
photo/edge reliability cap is active. Missing reliability checks fail closed.
Partial round position fits are never used for an area score.
Coloured regions and the scoring view's detected outline are withheld by the same
checks. The nominal scoring outline remains inspectable wherever the ring is
available, including at an unmeasured marker. Colours indicate area contributions,
not defect severity. Triangle region fills remain withheld with their percentage.

Triangle raw area comparisons remain available in offline CSVs only. Their radial
edges and reference-shape parity are unresolved, so the area percentage is withheld
in the UI even when robust triangle angles/position are assessed. Existing QC
classifications stay unchanged. Raw rows below labelled **withheld** are diagnostic
evidence, not displayed percentages or asserted defects.

## Exact genuine comparisons

Both source originals were restored byte-for-byte with their recorded SHA256.
The Pepsi is Phillips CH080120 lot 75, photo `3cf5442ab9c87b9c`, 126710BLRO,
SHA256 `3cf5442ab9c87b9c0b9481fd0eb2b92316a0287581635c49fc9cf6cdc4f31a00`.
The independent WOS CPO control is `wos_cpo_40616911`, photo `a5431af6379b78a5`,
126710BLNR, SHA256 `a5431af6379b78a5233303aeec379b30ef8a5c46ffa70be67a55a1126de8c138`.
Each inspection uses one photograph. The numeric-only [local results](marker-mismatch-consistency-results.csv)
contain every marker's before/after percentages, areas, vertices and reasons.

For the Pepsi 9, the exact same actual fit produces:

| Expected reference | Extra % | Missing % | Total % of filled expected area |
| --- | ---: | ---: | ---: |
| Fixed full-photo master | 3.49048548 | 5.13667909 | 8.62716457 |
| Ring-adjusted close-up nominal | 0.54416046 | 1.87711801 | 2.42127847 |

The expected-reference choice changes the result by **6.20588610 percentage
points**. Before this change Java's ring-adjusted union-denominator score was
2.40817414%; after it is 2.42127847%, matching the agreed research calculation.
The experimental trained 3.997% result is not adopted.

### Pepsi — ring-adjusted nominal

| Shape / hour | Before Java union % | Extra % | Missing % | After filled expected % | UI |
| --- | ---: | ---: | ---: | ---: | --- |
| triangle 12 | 12.97441815 | 14.90874048 | 0.00000000 | 14.90874048 | withheld |
| round 1 | 1.12367305 | 0.93829557 | 0.19592085 | 1.13421642 | withheld |
| round 2 | — | — | — | — | withheld |
| round 4 | 1.45456462 | 1.47603450 | 0.00000000 | 1.47603450 | measured |
| round 5 | 1.68047320 | 1.44295333 | 0.26176831 | 1.70472164 | measured |
| baton 6 | 2.60559274 | 2.46548139 | 0.20435176 | 2.66983315 | withheld |
| round 7 | 1.74920569 | 1.73840672 | 0.04120729 | 1.77961400 | withheld |
| round 8 | 1.42281441 | 1.20047627 | 0.23941869 | 1.43989496 | measured |
| baton 9 | 2.40817414 | 0.54416046 | 1.87711801 | 2.42127847 | measured |
| round 10 | 1.62984530 | 1.02798402 | 0.61861583 | 1.64659985 | measured |
| round 11 | 0.55411594 | 0.31982283 | 0.23606530 | 0.55588813 | measured |

### WOS CPO — ring-adjusted nominal

| Shape / hour | Before Java union % | Extra % | Missing % | After filled expected % | UI |
| --- | ---: | ---: | ---: | ---: | --- |
| triangle 12 | 9.87578463 | 10.95797016 | 0.00000000 | 10.95797016 | withheld |
| round 1 | 1.48360051 | 0.12162987 | 1.36377514 | 1.48540501 | measured |
| round 2 | 2.33915010 | 2.33621385 | 0.05758380 | 2.39379765 | measured |
| round 4 | 2.87971615 | 2.96510269 | 0.00000000 | 2.96510269 | measured |
| round 5 | — | — | — | — | withheld |
| baton 6 | 2.31194053 | 2.36665621 | 0.00000000 | 2.36665621 | measured |
| round 7 | 2.00204810 | 2.01857088 | 0.02388998 | 2.04246086 | measured |
| round 8 | 2.33859513 | 2.39459501 | 0.00000000 | 2.39459501 | measured |
| baton 9 | 2.22667411 | 1.73731739 | 0.52804111 | 2.26535850 | measured |
| round 10 | 2.03314420 | 2.07533883 | 0.00000000 | 2.07533883 | measured |
| round 11 | 2.31453275 | 2.36937266 | 0.00000000 | 2.36937266 | measured |

Before/after reruns agree exactly at exported precision on all expected/actual
vertices and areas, overlaps, dial radius, fitted rotation/position and marker
usability for both watches. All 28 original QC findings/short lines across Pepsi
and WOS are byte-identical before/after; the Pepsi 9 remains WITHIN. The final
visualization update also leaves all fields in the 22 exported marker rows
identical to the preceding PR version. A third restored genuine image,
Phillips photo `641dc405c27149d0` (CH080218 lot 1), was excluded in both versions:
`minute-lattice median residual too high (wrong local fit)`. No pose rescue or
measurement was invented.

## Residual 2.421%: edge audit, not a correction

`MarkerEdgeAudit` exports profiles using the **unchanged production spline
sampler and frozen H**, plus an unoutlined crop and the scoring-outline crop.
No original photo or derivative crop is committed. The raw crop was visually
inspected separately from the numerical comparison; this is not a metrology-grade
manual trace with independently established subpixel accuracy.

At R = 379.22856553 px, Pepsi 9 nominal length/width are
115.48096671 / 44.98078377 canonical dial-pixels. The fitted means are
115.50258827 / 44.37813552. Length differs by +0.02162156 px; width by
−0.60264825 px. The fitted widths at the two ends differ (43.80184406 and
44.95442698 px), rather than showing a uniform inset. Corner separations are
approximately 0.50–0.92 px. Fitted area is 1.33295755% below expected; the identity
`total = net missing area + 2 × extra` explains the remaining 1.08832092 percentage
points algebraically, without assigning either term to manufacture or detection.

Five profiles per side, sampled at 0.05 canonical px, show the main outer-rim
falling transition between −0.30 and +0.25 px of the fitted line. Separate inner
transitions occur around 2.5–5.5 px inward. Visual inspection and profiles support
an outer-metal fit rather than a wholesale lume-only trace. One profile has an
isolated further-out peak at +2.70 px, 0.756 of its largest gradient; adjacent
profiles do not show a continuous second rim. This is remaining local edge
ambiguity, not evidence for uniformly expanding the fitted marker.

The other Pepsi baton and most measured rounds read **larger**, as do both WOS
batons. A general detector undersizing correction is therefore unsupported by
these controls. Strongest-gradient localization can still carry a stable
subpixel bias from polishing, sampling, sharpening or lighting. This audit uses
the detector's sampler, so it is not an independent accuracy ground truth.
Nominal-vs-specimen geometry and local detector bias remain inseparable at the
precision needed here. No proven fault justifies changing detector or perspective.

The existing single-photo stress evidence is preserved: baseline 2.421%; six
usable derived variations span 2.240–3.036%; two variations fail pose. Those are
repeatability measurements of one photo/watch, not independent genuine watches
or an uncertainty tolerance. Precision does not prove accuracy. The prior
held-out genuine tables remain context, not new QC severity limits; the triangle
parity warning is still unresolved.

## Validation and reproduction

- JDK 17, committed wrapper: `cd android && bash ./gradlew :app:testDebugUnitTest`:
  **353 tests, zero failures/errors** after the final visualization improvement.
  Android activity code compiled; no APK built.
  Device/emulator navigation was not exercised in this run.
- Focused regressions: filled expected denominator, extra/missing areas, >100%
  results, malformed polygons, Pepsi fixed/ring parity, same-photo perturbations,
  WOS polygon overlaps, shared round geometry, missing/partial/interference/capped
  checks and triangle withholding. Nine additional consistency tests cover coloured
  region area sums and containment, non-overlapping partitions, identical/nested/
  separated shapes, opposite winding, 128-point rounds, all 12 frozen polygon
  comparisons, snapshot retention, frozen full-watch projection, crop half-pixel
  alignment and absence of a scoring fallback without a ring.
- Desktop `MarkerMismatchBatch`: local before/after runs against both SHA-verified
  originals; unchanged geometry, changed denominator only for numeric scores.
- Desktop `Alpha99Preview --no-images`: all 28 Pepsi/WOS QC rows byte-identical
  to the starting source. `MarkerEdgeAudit`
  also renders the actual scoring close-up; it was visually inspected.
- Desktop `MarkerMismatchVisualCheck`: **12 renderer/store assertions passed** for
  region colours, clear overlap/background, projected expected/detected boundaries,
  withholding, both full-view options' handoff and clearing stale scoring overlays.
  It uses the production renderer via the desktop Canvas shim, not Android device
  navigation. Real Pepsi and WOS scoring overlays and coloured close-ups were
  rendered locally. The Pepsi full-watch view and 9 close-up were visually inspected.
  Local per-marker numbers stay identical to the tables above.
- Reproduce edge profiles with
  `tools/desktop-harness/run.sh MarkerEdgeAudit <verified-original.jpg> <local-outdir>`.
  Regenerate percentages with `MarkerMismatchBatch <manifest.csv> <image-root> <out.csv>`.
- Reproduce the rendering checks and local previews with
  `tools/desktop-harness/run.sh MarkerMismatchVisualCheck <local-outdir> [verified-original.jpg]`.
- Frozen fixtures cite [stress run 38155286452](https://github.com/Biggregw/watch-align/actions/runs/38155286452)
  and [genuine run 38145439812](https://github.com/Biggregw/watch-align/actions/runs/38145439812)
  in their [README](../../android/app/src/test/resources/marker_mismatch/README.md).

## LESSONS LEARNED

The largest avoidable disagreement was reference placement; denominator drift was
a separate code defect. A thick yellow stroke and a stable percentage do not prove
the physical metal boundary. State the scoring reference explicitly, reuse it in
drawing, and keep edge uncertainty separate from QC severity. Reject a failed pose
without rescuing it from inspection targets. Any proposed edge-bias correction
needs an independent outer-rim trace with quantified uncertainty and held-out
genuine controls first; it must not be tuned to make this Pepsi score zero.
No governing protocol or reference is changed by this run.
