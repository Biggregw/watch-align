# Watch Align engineering lessons

Status: **living lessons for future work**

This file captures lessons that should influence new research and implementation. It is not a roadmap and not a tolerance table.

## 1. The product is reference deviation, not classification

The useful product question is not whether all replicas separate statistically from all genuine watches. It is whether a submitted watch shows a measurable deviation from a proven-genuine reference in the features that can be assessed from that photo.

If a replica sits inside the genuine reference on every assessed feature, report no detectable deviation. Do not invent a tell.

## 2. Establish nominal geometry before tolerance

A clean, high-quality genuine reference gives a better zero point than averaging noisy photographs together from the start.

Use the best available proven-genuine near-frontal image as the nominal candidate, then use independent genuine watches to estimate variation around it.

A loose genuine dial can be especially useful because the centre hole and absence of crystal/rehaut distortion simplify geometry, but it is not required if existing images are already adequate.

## 3. Genuine-photo extremes are not automatically manufacturing variation

A genuine watch photographed at an angle can produce extreme measurements. Accepting every such value directly into the genuine envelope turns the envelope partly into a camera-angle tolerance.

Before widening the range, inspect related/opposing geometry. Coherent opposite-signed deviations can reveal perspective contamination.

Correct, pair, exclude or withhold contaminated measurements instead of automatically broadening tolerance.

## 4. Opposing markers may solve pose more cheaply than explicit pose reconstruction

Perspective often creates structured distortion: one side expands while the opposite side compresses.

Testing residual relationships such as 12/6, 3/9 and diagonal pairs may provide enough pose evidence for a feature without estimating camera tilt, homography or full projective geometry.

This must be validated per feature. It is a promising simplification, not a universal assumption.

## 5. Keep nominal variation and measurement uncertainty separate

Three spreads matter:

- nominal/reference geometry;
- between-watch genuine variation;
- within-watch/photo/detector variation.

Multiple photos of the same physical watch are valuable for repeatability but do not increase the independent manufacturing sample size.

## 6. One physical watch is one independent sample

Repeated dealer photos, crops, re-encodes and resolutions of the same watch are not independent watches.

Use them to measure photo/measurement stability. Count different physical watches for genuine population variation.

## 7. Source style can masquerade as watch variation

Dealer photography, QC photography, sharpening, compression, lighting and angle can alter apparent geometry.

Cross-source consistency matters. Do not calibrate a physical tolerance from a source-style difference.

## 8. Marker position and marker size are different problems

The quick 6-o'clock Submariner check reproduced marker-centre position very closely while crude thresholding overestimated marker length/thickness because reflections and white-gold surround changed the visible boundary.

Lesson: define centre, lume boundary, outer metal boundary, size and shape separately. If size is unstable but centre is stable, keep centre and withhold size.

## 9. Use a known result to improve the research protocol

A known-working calibration is an answer key for the method, not a target to manually fit.

Process:

1. measure independently;
2. compare afterwards;
3. diagnose error;
4. propose a protocol change;
5. retest on fresh/held-out evidence;
6. promote only if the change improves performance.

This is how the reusable golden research prompt should evolve.

## 10. Self-critique helps; automatic self-rewriting does not

Every research run should end with `LESSONS LEARNED` and proposed prompt/protocol improvements.

Do not let the same run automatically rewrite its governing prompt. One unusual image can otherwise cause prompt drift and overfitting.

Validate proposed wording/rule changes separately before promoting a new protocol version.

## 11. Use existing evidence before harvesting more

A recurring process mistake was treating a larger corpus as the answer to every uncertainty.

Before searching for more images, ask whether the existing datasets, artifacts and known genuine watches can answer the question. Start with the smallest useful sample.

Only acquire more data for a named evidence gap.

## 12. Research offline before spending CI/build effort

Do not use Android builds, GitHub Actions or a calibration platform to discover whether a measurement idea is promising.

Preferred order:

**existing images -> quick local/offline measurement -> compare to known result -> refine -> held-out test -> production implementation -> build/CI**

CI is for validating an implementation, not discovering the measurement rule.

## 13. Detection, measurement, confidence and judgement are separate

Finding a plausible marker does not mean every quantity derived from it is trustworthy.

Keep these stages separate:

- landmark detection;
- numeric measurement;
- measurement stability/assessability;
- reference comparison;
- user-facing judgement.

A failure in one quantity should not invalidate unrelated stable quantities.

## 14. Re-measurement remains valuable

The existing 100/94/88 style resize checks proved useful for finding measurements that depend on a particular decode/resample.

Use repeatability as evidence about measurement quality, but do not import another family's verdict thresholds simply because the same stability mechanism is reused.

## 15. Prefer simple physical relationships

Local relationships that correspond directly to what a human reviewer sees are easier to explain and often more robust than reconstructing a theoretically perfect canonical dial.

Use complex rectification/projective methods only when they measurably improve held-out feature accuracy.

## 16. Real QC validation comes after genuine calibration

Once a genuine envelope is frozen, test independent RepTimeQC examples with known visible defects and accepted/GL controls.

Measure blind to the stated defect where practical, then check whether the genuine-reference comparison reproduces the human-observed problem.

Misses and false flags are diagnostic evidence. Do not immediately move thresholds to make the examples fit.

## 17. Let each feature choose its implementation

After validation, a feature may belong in deterministic code, vision AI, hybrid form, or nowhere.

Do not force all checks into OpenCV and do not assume AI is always better. Use the cheapest/repeatable method that actually performs reliably.

## 18. Preserve the working GMT path as the control

GMT already works well enough to serve as a known control. The new calibration protocol should first reproduce a simple existing GMT result offline.

Do not alter production GMT behaviour until the replacement calibration method has demonstrated equal or better evidence.

## 19. Submariner work remains useful evidence

The 124060 research was not wasted. It produced genuine geometry, repeatability observations and useful failure modes. The mistake was allowing photo/source spread and population-separation thinking to dominate the interpretation.

When Submariner resumes, reuse that evidence under the new calibration protocol rather than restarting a broad platform programme.

## 20. Every substantial experiment should leave a reusable lesson

Research output should include:

- what was measured;
- what was excluded and why;
- what matched the known result;
- what failed;
- what would improve the protocol;
- how the proposed improvement will be validated on held-out evidence.

The governing protocol is `docs/CALIBRATION_PROTOCOL.md`.
