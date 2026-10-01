# Watch Align engineering lessons

This is the living reuse index for all watch families. It is not a tolerance table. The goal is to stop each new reference from rediscovering problems already solved by GMT or earlier family work.

## Reuse-first rules

1. Find the closest mature implementation before building family-specific code.
2. Reuse confidence/failure-handling lessons as well as detector code.
3. Separate shared unchanged, shared with model parameters, deliberately model-specific, and not applicable.
4. Keep new-family experiments diagnostic until mature-family regression safety is demonstrated.
5. Physical watch, not photo, is the independent data unit.

## Established lessons

### Detection is not trust
A plausible marker is only the first step. Measurement, confidence and QC decision are separate stages.

### Re-measure the number, not only the marker
Finding the same outline at 100%, 94% and 88% does not prove gap, rotation or centring is stable. Fine measurements need numeric repeatability checks in physical pixels.

### Reuse mechanisms separately from policy
Shared resize, pose, hand-obstruction, edge-identity and recovery mechanisms often transfer. GMT verdict thresholds do not automatically transfer with them.

### Quantity-specific confidence matters
A detector may provide a stable centre but unstable size/edge identity. Withhold only the affected quantity when evidence supports that distinction.

### Local and global references are independent evidence
A mature local reference can be an excellent cross-check while a model-specific radial reference remains the more repeatable primary measurement. Do not force one reference universally.

### Recovery must not weaken the primary detector
A bounded recovery path may improve coverage, but recovered results carry their own confidence. Do not widen primary plausibility gates to make failures disappear.

### Pixel support and pose matter
Normalized numbers can look precise while moving materially by one or two source pixels. Establish repeatability and pose/scale sensitivity before attaching a QC limit.

### Reuse an estimator before its threshold
A pose estimator can transfer cleanly while its mature family's decision boundary does not. Calibrate family policy separately.

### Model-specific priors stay model-specific
Marker shape, radial spacing, date layout and QC limits belong in model/family configuration unless evidence supports a common value.

### Source style can confound watch class
Dealer genuine photos and QC replica photos can differ systematically. Never let a genuine-vs-replica comparison set a tolerance when source style is also changing.

## New reusable lesson: calibration is product infrastructure

The 124060 work showed that manual source gathering and model-by-model tolerance research should not be repeated for every reference. New models should start with the autonomous Watch Family Calibrator.

- Input should normally be the exact watch reference plus its model config.
- The system should discover or reuse approved sources itself, acquire images, deduplicate them and group them by physical watch.
- Splits are locked by physical watch before calibration.
- Genuine development watches alone fix candidate limits.
- Validation can reject a frozen limit but cannot move it.
- Holdout stays unopened until limits are frozen; it can confirm/reject but cannot move them.
- Replica watches are stress tests only and never determine a QC boundary.
- Under-sampled, pose-sensitive or unstable metrics remain unavailable instead of receiving guessed limits.
- A new watch should reuse the mature workflow, renderer, confidence machinery and calibrator. Only genuinely different geometry/detection/calibration belongs in a model adapter.

See `docs/WATCH_FAMILY_CALIBRATOR.md`.

## Required handoff note

Substantial detector/calibration work must state:

- **Lessons reused**
- **Deliberate divergences**
- **New reusable lesson**

Update this file when a new reusable lesson is established.
