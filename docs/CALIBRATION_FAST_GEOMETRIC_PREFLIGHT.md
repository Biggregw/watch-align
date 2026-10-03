# Fast geometric calibration preflight

This note records the proposed cheap image preflight and the first retrospective checks against the 124060 run-44 evidence.

## Goal

Avoid sending obviously unusable images into the expensive Watch Align measurement path.

The preflight is deliberately conservative. Its job is not to decide whether a photo is suitable for calibration. Its job is only to reject images that have no realistic chance of producing a valid measurement.

Target pipeline:

`acquired image -> cheap geometric preflight -> full measurement adapter -> reliability gates -> calibration`

Every preflight rejection must remain in the evidence manifest with a machine-readable reason. Nothing is silently dropped.

## Proposed cheap checks

Run on image metadata and a small downsampled copy only:

1. **Image size**
   - reject only clearly tiny images before any computer-vision work.
2. **Dial candidate presence**
   - look for a strong circular or elliptical watch-dial candidate.
   - absence must be high-confidence before rejection; uncertain images continue to full analysis.
3. **Dial size**
   - candidate ellipse must be large enough in the original image to have a realistic chance of supporting marker measurement.
4. **Completeness**
   - candidate ellipse should not be substantially clipped by the image boundary.
5. **Symmetry / viewing angle**
   - use minor-axis / major-axis ratio as a cheap perspective signal.
   - a tilted circular dial projects approximately to an ellipse, so a low ratio is evidence of a poor viewing angle.
   - this is a rejection signal, not a precision pose measurement.
6. **Optional concentric support**
   - where cheap enough, agreement between two approximately concentric structures such as dial/rehaut/bezel can increase confidence that the detected ellipse is really the watch.

## Run-44 protected-positive definition

Run 44 contains 319 unique genuine photos. A photo is treated as a protected positive for preflight benchmarking if the existing production measurement route produced at least one of the seven calibrated metrics.

That gives:

- 319 genuine photos examined
- 73 protected positives
- 246 photos that produced no calibrated metric

The preflight quality target is initially **zero protected-positive rejection**. Speed is secondary to preserving useful genuine evidence.

## Preliminary benchmark from the available artifact

The run-44 artifact contains image dimensions and full-analysis outputs, but it does not contain the original image bytes. Therefore the actual cheap ellipse detector cannot yet be replayed pixel-for-pixel against the frozen run-44 corpus. The following checks use the evidence that is available now.

### Image-size gate already has useful value

The 319 genuine images have a very obvious thumbnail population:

- 7 images have shortest side 32 px
- 12 have shortest side 40 px
- 12 have shortest side 60 px
- 18 have shortest side 80 px
- 18 have shortest side 90 px
- 7 have shortest side 100 px

That is 74 images below 300 px on the shortest side.

**Result:** rejecting `min(width,height) < 300` would have rejected 74 of 319 genuine images, or about 23%, while rejecting **zero of the 73 protected positives**.

This is consistent with the existing general dataset harvester's conservative 300 px minimum image-side rule. It is a strong candidate for the first cheap preflight stage, provided the rejection is recorded rather than silently omitted.

The smallest protected positive in run 44 had a shortest side of 418 px. A 500 px rule would already lose one useful image, so there is no evidence for making the metadata-only gate more aggressive than the existing 300 px rule.

### Angle alone must not be aggressive

The run-44 measurement CSV contains a full-analyser marker-pose tilt for 77 photos. This is not the proposed cheap ellipse ratio, but it is useful as a proxy check for how much angle genuine useful photos can tolerate.

Of those 77 photos:

- 70 were protected positives
- 7 produced no calibrated metric
- the maximum tilt among protected positives was about 19.55 degrees
- the maximum tilt among the seven non-useful photos was about 17.90 degrees

Therefore no simple tilt cutoff on this subset can reject a poor photo while retaining every useful photo. A cutoff low enough to reject one of the non-useful pose-estimated images would also reject at least one protected positive.

Examples from the current full-analysis tilt proxy:

- >10 degrees would reject 11 images, including 7 protected positives
- >12 degrees would reject 5 images, including 2 protected positives
- >15 degrees would reject the same 5 images, including 2 protected positives
- >18 degrees would reject 1 image, and that image is a protected positive
- >20 degrees would preserve every protected positive, but reject none of the 77 pose-estimated images

**Conclusion:** symmetry/angle is still worth testing as part of a cheap geometric preflight, especially among the many images where the expensive analyser never obtains a usable dial/pose. But it must not become an aggressive stand-alone angle rule based on the current evidence.

## Required full benchmark

Before the ellipse/symmetry preflight is allowed to reject calibration evidence, replay it over an immutable image snapshot and report:

- total images
- protected positives retained/rejected
- non-useful images rejected
- full-analysis calls avoided
- rejection reason counts
- dial candidate size distribution
- axis-ratio distribution
- completeness distribution
- source/model breakdown
- runtime per image and total runtime saved

The primary acceptance condition is:

`protected positives rejected == 0`

Only after that condition is satisfied should the preflight be enabled as a hard gate.

## Frozen evidence requirement exposed by this work

The current run-44 Actions artifact does not include the image bytes. That prevents exact retrospective benchmarking of a new pixel-level preflight against the same frozen corpus.

The future evidence-snapshot design should therefore preserve either:

- the immutable image bytes/content-addressed image store, or
- a durable artifact/reference that allows those exact bytes to be materialised later.

A manifest of URLs alone is not sufficient because dealer/CDN content can disappear or be re-encoded.

## Architectural rule

The geometric preflight belongs before the expensive model-specific measurement adapter and should be generic across watch families. It should know only about cheap image geometry, not Submariner marker semantics.

It must produce explicit states such as:

- `PASS`
- `REJECT_IMAGE_TOO_SMALL`
- `REJECT_NO_CREDIBLE_DIAL`
- `REJECT_DIAL_TOO_SMALL`
- `REJECT_DIAL_INCOMPLETE`
- `REJECT_EXTREME_ELLIPSE`
- `INCONCLUSIVE_CONTINUE`

`INCONCLUSIVE_CONTINUE` always proceeds to the full measurement adapter.

The preflight detector and thresholds must be versioned and included in the measurement fingerprint/run manifest, because changing an upstream hard gate changes the evidence population seen by calibration.

## Current recommendation

Implement the foundation so it can support this preflight cleanly, but do not yet hard-code an ellipse-ratio rejection threshold.

The metadata-only `<300 px shortest side` rule has already shown a zero-loss 23% reduction on run 44 and can be considered once the coverage funnel records those rejects explicitly.

For circle/ellipse presence, dial size, completeness and symmetry, first preserve/materialise the frozen image snapshot and run the zero-loss benchmark above. Then choose the most aggressive rule combination that still retains every protected positive.
