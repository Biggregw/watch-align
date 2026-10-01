# 124060: reuse of mature GMT reliability mechanisms (2026-10-01)

This is a development-only diagnostic study. It asks which reliability mechanisms from the mature GMT path should surround the existing 124060 geometry before any Submariner QC tolerances are introduced.

No production analyzer, QC threshold, verdict or authenticity rule was changed. Validation and holdout were not acquired or inspected.

## Setup

- 39 existing development photos.
- 13 independent physical 124060 watches: 9 genuine-source development watches and 4 replica-source development watches.
- Existing source pools only; no new photo sourcing.
- Existing alpha69 Sub detector and shared GMT/Sub marker detectors unchanged.
- The same photo was additionally reduced to 94% and 88%, following the mature GMT stability pattern.
- GitHub Actions run: `36899858354`.
- Diagnostic driver: `SubGmtLessonsStudy` on `feature/android-sub124060-gmt-lessons-experiment`.

## 1. Triangle identity is not enough: re-measure the numbers

The existing Sub production guard checks that the same 12 outline is found after reducing the image by 6% and 12%. In this study every measured triangle passed that identity check:

- 31/31 measured triangles returned the same outline at 100%, 94% and 88%.
- 27 of those also had a reproducible dial fit.

But the actual measurements were not always stable even though the same physical outline was found.

Among those 27 reproducible-dial / same-outline readings:

| measurement | median movement | p95 | maximum | over 1 image pixel |
|---|---:|---:|---:|---:|
| Sub gap, converted to dial pixels | 0.38 px | 3.28 px | 4.07 px | 7/27 |
| Sub rotation | 0.18 deg | 0.85 deg | 0.87 deg | 4/27 by triangle-tip travel |
| Sub centring | 0.33 px at the triangle | 1.31 px | 1.36 px | 4/27 |

All of the >1 px cases in this development run were genuine-source photos. This is not evidence about watch class; it is evidence that a correct marker identity does not guarantee a stable fine measurement.

The gap is the clearest example. Some high-resolution dealer inputs still moved by 3-4 px when the already-decoded working image was reduced slightly. Therefore a simple original-image resolution floor cannot replace a direct numeric stability check.

**Reuse decision:** before any 124060 12 metric can support a verdict, re-measure the metric itself at 100/94/88 and store its range in physical pixels. Keep the existing same-outline check as a prerequisite, not as the whole stability test. Gap, rotation and centring should be allowed to become independently assessable/unassessable, as GMT already does for separate measurements.

No Sub movement limit is set by this study. The GMT one-pixel concept is the mature precedent and was used here only as a descriptive reference.

## 2. Shared batons should use the shared GMT resize safety

The Sub route already uses `GmtSixLandmarkAnalyzer` for the 3, 6 and 9 batons, but alpha69 does not call its existing `measureStability()` routine.

Applying that existing GMT routine diagnostically to alpha69 Sub results found:

- 83 previously `FOUND` baton readings were re-measured.
- 5/83 failed the existing GMT resize-repeatability rule.
- 3 o'clock: 2/28 unstable.
- 6 o'clock: 0/26 unstable.
- 9 o'clock: 3/29 unstable.

The five failures included both genuine-source and replica-source photos. The detector itself does not need to be reinvented; the mature confidence layer simply was not being used by the Sub route.

**Reuse decision:** when Sub baton QC is enabled, call the existing shared baton resize check and apply the same fail-closed principle before judging centring or rotation. Model-specific QC levels remain separate.

## 3. Round-marker reuse is strong, with an important edge-identity nuance

The Sub route already uses `GmtRoundMarkerAnalyzer` for the same eight round-hour positions as GMT: 1, 2, 4, 5, 7, 8, 10 and 11.

- 232 previously `FOUND` round-marker readings were re-measured at 94% and 88%.
- 0/232 failed the existing GMT offset repeatability rule.
- However, 63/232 changed fitted edge/radius identity across scale: 50/172 genuine-source readings and 13/60 replica-source readings.

This is exactly why the mature GMT path separates marker-centre offset from marker-size confidence. A changed lume/surround edge does not necessarily invalidate the centre offset, but it does invalidate a size comparison.

**Reuse decision:** reuse the GMT round-marker stability policy, not merely the detector. Offset can remain usable when its centre is repeatable; size must be withheld when the edge identity changes. Do not create a separate Sub round-marker implementation.

## 4. Keep Sub radial rotation primary; use other geometry as corroboration, selectively

The current Sub triangle already calculates three independent orientation clues:

- `rotationDialRadialDeg`, currently the primary `rotationDeg`;
- `rotationChordDeg`, from symmetric local minute-track pairs away from the triangle;
- `baseEdgeDeg`, the triangle base against the local tangent.

On the 27 reproducible same-outline readings:

- radial vs symmetric-chord disagreement: median 0.35 deg, p95 2.62 deg, max 2.70 deg;
- radial vs base-edge disagreement: median 0.27 deg, p95 2.82 deg, max 5.93 deg;
- chord vs base-edge disagreement: median 0.85 deg, p95 4.43 deg, max 5.92 deg.

The radial/chord disagreement did **not** predict resize instability in this set (correlation with rotation tip-travel about -0.27). Several stable genuine-source readings had about 2-2.7 deg radial/chord disagreement. Therefore the mature GMT idea of using an independent reference is reusable, but the GMT-style chord agreement must not be copied as a Sub gate simply because it exists.

The base edge is more physically relevant for distinguishing a whole-marker rotation from a point/shape lean. One genuine-source reading had radial rotation +1.63 deg while the base read -1.45 deg; treating the axis alone as a simple whole-triangle rotation would be misleading. GMT already learned to distinguish axis lean from corroborated marker rotation.

**Reuse decision:** retain radial rotation as the Sub primary measurement. Keep the chord as diagnostic evidence unless a future study establishes a useful Sub-specific relationship. Investigate base-edge agreement as corroboration for the *kind* of alignment issue (whole marker rotated versus point/shape lean), without introducing a threshold from this small set.

## 5. Reuse the round-marker pose estimator before inventing a Sub angle detector

`GmtMarkerPose` was run unchanged on the 124060 round-marker detections.

- Valid pose estimate: 29/39 photos.
- Median affine residual: 0.0028 of dial radius; p95 0.0051; maximum valid 0.0058.
- No photo failed because the GMT master round-marker scale was grossly incompatible with the Sub layout.
- The existing GMT `nearFrontal()` policy accepted only 5/29 valid estimates.

The estimator itself is therefore highly reusable, but its GMT policy threshold is not automatically portable.

It also behaved usefully on known difficult development views:

- two strongly angled 800x800 genuine dealer views estimated about 19-20 deg tilt, with upper bounds about 22 deg;
- the previously diagnosed 2160x2160 three-quarter `Black-Dial` views supplied too few clean round markers for a pose result, which is itself useful low-evidence information;
- ordinary usable dealer views often had upper bounds around 8-10 deg, so copying GMT's 5 deg `nearFrontal` cutoff would reject too much Sub coverage.

**Reuse decision:** use the existing marker-layout pose estimator as the first Sub pose candidate rather than building a new rehaut-only system. Calibrate any Sub pose policy separately. An invalid pose estimate must not itself become a verdict; combine it with the existing dial/marker evidence.

## Recommended implementation order

This study does **not** implement these production changes. If/when implementation starts, the smallest reuse-first sequence is:

1. Add numeric 100/94/88 stability ranges to the existing Sub 12 result, while preserving the current triangle detector and primary formulas.
2. Invoke the already-existing baton and round-marker stability routines from the Sub path. For rounds, preserve the GMT distinction between offset stability and edge/size stability.
3. Add the existing marker-layout pose result as a Sub diagnostic, initially with no Sub verdict threshold.
4. Add base-edge corroboration to the Sub alignment diagnostics so a point/shape lean is not automatically described as whole-marker rotation. Keep chord disagreement diagnostic until evidence supports more.

At every stage, GMT production behaviour remains unchanged and the current alpha69 Sub outputs remain the comparison baseline.

## Reuse-first handoff

**Lessons reused**

- GMT numeric resize repeatability, not only marker identity.
- Shared baton and round-marker stability logic.
- Separation of centre-offset confidence from size/edge confidence.
- Independent geometric corroboration rather than trusting one orientation number.
- Marker-layout pose before inventing another angle heuristic.

**Deliberate divergences**

- Keep the Sub triangle detector, radial primary rotation and robust multi-tick gap definition; earlier direct GMT-formula comparison found the Sub definitions more repeatable.
- Do not transfer GMT QC thresholds or its 5 deg near-frontal pose policy.
- Do not make radial/chord disagreement a Sub gate: the development evidence does not support that use.

**New reusable lessons**

- Re-detecting the same physical marker is weaker than re-measuring the same geometry consistently. Identity stability and numeric stability are separate gates.
- When a shared detector returns multiple quantities, reuse the mature quantity-specific confidence policy as well as the detector. A marker centre can be stable while its fitted physical edge is not.
- An alternate reference is only useful as a gate if disagreement is empirically related to measurement error for that family; otherwise keep it diagnostic.
