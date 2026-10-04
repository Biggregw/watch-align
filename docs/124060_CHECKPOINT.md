# Rolex Submariner 124060 — historical checkpoint

Status: **preserved evidence; not the active plan**

The 124060 implementation and research remain in the repository, but Submariner expansion is paused while the new calibration protocol is proved against the known-working GMT path.

Do not use older alpha-era next-step instructions in this file or under `docs/research/` as current direction.

Active direction:

- `docs/PRODUCT_SCOPE.md`
- `docs/CALIBRATION_PROTOCOL.md`
- `docs/HANDOFF.md`
- `docs/architecture/QC_PRINCIPLES.md`

## What should be preserved from the 124060 work

The work established useful image-space genuine geometry and repeatability evidence. Important reference values include the later genuine image master:

- round-marker ring radius median: about **0.8190 R**;
- 12 triangle rotation median: about **0.244°**;
- 12 triangle centring median: about **0.00224** of its width definition;
- 12 gap median: about **0.02825 R**;
- 3–9 axis offset median: about **0.00490 R**;
- 12–6 axis offset median: about **0.00164 R**.

Marker morphology work also produced useful nominal image-space geometry, including a 6-o'clock baton centre around **0.7644 R** from dial centre.

These are research measurements, not Rolex engineering specifications.

## Key lesson from the old calibration approach

A genuine photo should not automatically widen the genuine manufacturing envelope merely because the watch is genuine.

Pose, dealer photography, sharpening, crystal/rehaut effects and detector instability can move the measurement. The old approach sometimes allowed those effects to become part of the accepted genuine range.

The new protocol instead starts from a clean nominal master, separates manufacturing variation from photo/detector variation, and checks related/opposing geometry for perspective contamination before admitting an apparent outlier.

## Quick protocol check already performed

A small independent remeasurement of the 6-o'clock marker centre using existing genuine 124060 images reproduced the previous morphology result closely: roughly **0.763 R** versus the earlier **0.7644 R** result.

The same quick experiment showed why marker size needs a separate edge definition: crude brightness thresholding included differing amounts of white-gold surround/reflection and inflated length/thickness while the marker centre remained stable.

This is exactly how the new reusable calibration prompt should be developed: known result -> independent attempt -> error diagnosis -> protocol improvement -> held-out retest.

## Perspective lesson

Existing genuine photographs also showed that opposite marker distances can reveal poor pose. A strongly angled image produced a large 12/6 radial asymmetry while near-frontal images showed very small asymmetry.

This supports testing opposing-marker residuals as a simpler pose/contamination signal before explicit projective reconstruction.

It does **not** prove a universal correction formula. That relationship must be validated feature by feature.

## Production status

The repository still contains the experimental 124060 Android route and associated research tooling. Preserve it. Do not broaden it or recalibrate it yet.

The next active research step is GMT, not Submariner:

1. choose one simple already-working GMT feature;
2. recalibrate it offline using `docs/CALIBRATION_PROTOCOL.md` and existing genuine evidence;
3. compare with the established GMT result;
4. refine the protocol using held-out evidence;
5. only after the protocol proves itself, return to 124060 using the same method.

## Historical material

Older 124060 alpha notes, calibrator runs, thresholds, branches and research documents remain valuable for provenance and negative/positive evidence. They are deliberately retained rather than deleted, but they no longer govern new work.
