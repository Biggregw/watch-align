# Watch Align engineering lessons

This is the short, living index of lessons that should be reused across watch families.
It is not a tolerance table and it does not make model-specific geometry generic by assertion.
The purpose is to stop a new family from rediscovering problems already solved elsewhere.

Before designing or changing a detector, measurement, confidence rule, recovery path, presentation, or QC decision:

1. Read this file.
2. Find the closest mature implementation, normally the GMT path.
3. Read the relevant handoff/research note for that implementation.
4. Separate what is genuinely generic from what is model-specific.
5. Try reuse/adaptation diagnostically before creating a parallel algorithm or user experience.
6. Preserve the mature family's regression behaviour while experimenting.

## Reuse-first checklist

For every new family-specific check, answer these before implementation:

- What is the equivalent mature check, if any?
- Which detector primitives, coordinate frames, confidence gates, recovery rules, presentation components and tests already exist?
- Which parts are pure geometry or image-quality logic and which parts depend on model dimensions/layout?
- What known failure modes did the mature path already solve?
- Can the existing implementation be parameterised or composed rather than copied?
- If we intentionally diverge, what measured evidence shows the mature approach is worse or inapplicable?

A new family-specific implementation should not be created just because the marker shape or model name is different.
Equally, shared code must not be forced when the evidence shows a genuinely different measurement strategy is more reliable.

## Start from the mature product, not only the mature algorithm

The alpha69-alpha70 124060 work exposed an important process mistake. We reused many GMT detector and confidence ideas but allowed the Submariner to grow a separate research-style overlay and wording. By the time the measurements were reliable enough for phone testing, the app did not feel like Watch Align's mature GMT experience even though much of the underlying engineering had already been reused.

The corrected rule is:

- when a mature family already exists, the default starting point for a new production family is its **end-to-end workflow and presentation contract**;
- keep model-specific layout, geometry, references, calibration and thresholds behind an adapter;
- where thresholds are not yet calibrated, preserve the mature UI and show **measured / not yet judged** rather than replacing the UI with a separate research presentation;
- a family-specific debug/research overlay may exist for diagnostics, but it must not become the user-test checkpoint by accident;
- missing markers, hand obstruction, confidence withholding, whole-dial markup, close-up structure, summary hierarchy and interaction flow should be assumed reusable until a real model difference says otherwise.

This is the preferred migration sequence for future families: **copy the mature product behaviour, disable or neutralise unsupported judgements, then replace only the model-specific pieces that fail under evidence.** That is safer and usually faster than rebuilding the product experience around a new detector and trying to add parity later.

## Reusable lessons from GMT and 124060 work

### Detection is not trust

Finding a plausible marker is only the first stage. Fine QC measurements need independent confidence checks before they can support a judgement. Keep detection, measurement, confidence and QC decision separate.

### Re-measure before judging fine geometry

The mature GMT path re-measures important landmarks at 94% and 88% image scale. This catches results that depend on a particular decode/resample rather than the physical watch. The same principle should be considered for every fine marker measurement before adding a tolerance.

### Marker identity stability and metric stability are separate

Re-detecting the same physical marker at 100%, 94% and 88% does not prove that its fine geometry is stable. On the 124060 development study, all 31 measured 12 triangles returned the same outline at every scale, but several gap, rotation and centring readings still moved by more than one image pixel.

Therefore:
- first verify that the same physical outline was found;
- then re-measure each numeric quantity itself;
- store movement in physical pixels as well as normalized units;
- allow gap, rotation and centring to become independently assessable or unassessable.

See `docs/research/submariner/gmt_reuse_diagnostics_2026-10-01.md`.

### Reuse confidence logic and failure modes, not only detector code

The most valuable transferable work is often the guardrail around an algorithm: minimum pixel size, hand obstruction, local-frame quality, resize repeatability, alternate-reference disagreement, recovery confidence and fail-closed behaviour.

When adapting a mature check, audit these before writing a new detector or measurement formula.

### Reuse a stability mechanism separately from its decision policy

A mature stability routine can mix two different things: generic evidence about how much a measurement moves, and family-specific knowledge about whether that movement could change a QC verdict. Reuse the evidence first. Do not silently import the second part.

The alpha70 124060 work exposed this clearly. A strict one-pixel diagnostic would classify 44 of 83 otherwise stable/found baton readings and 60 of 232 otherwise stable/found round-marker readings as numerically jumpy. The mature GMT `resampleStable()` accepts many of those because every re-measurement remains safely below GMT-calibrated QC levels. Neither rule is a justified 124060 marker-status boundary before 124060 marker tolerances exist.

Therefore:
- run the shared 100/94/88 re-measurement machinery;
- record physical-pixel movement and edge identity;
- use inherently model-neutral consequences immediately, such as refusing a size comparison when the physical edge changes;
- keep user-facing marker status unchanged by numeric movement until the new family has evidence for the corresponding decision boundary.

The 12 can be stricter when the question is only whether to display a precise informational number: B2 directly showed that same-outline 12 values can move by several pixels, so alpha70 withholds that individual number when it is not repeatable to about one pixel. This is a measurement-quality rule, not a watch tolerance.

### Quantity-specific confidence should travel with a shared detector

A shared detector can return several quantities whose reliability differs. Reuse the mature confidence policy per quantity, not just the detector implementation.

The 124060 round-marker study is the current example: 232/232 previously found round-marker offsets passed the existing GMT resize-repeatability rule, while 63/232 changed fitted edge/radius identity across scale. The mature GMT policy correctly allows a stable centre offset to survive while withholding size when the lume/surround edge identity changes.

Do not invalidate every output because one fitted edge changed, and do not keep size/edge-dependent outputs merely because the centre stayed stable.

### Local and global references are independent evidence

A local minute-track reference can reject dial-centre error; a dial-radial reference can be less noisy when local tick endpoints jitter. Do not assume one is universally superior.

Where both are available:
- measure both diagnostically;
- choose the primary reference from repeatability evidence for that marker/model;
- retain disagreement between the references as possible confidence evidence rather than discarding the losing reference.

The 124060 12 study is the current example: its dial-radial rotation was more repeatable than the GMT-style 59/01 chord, so GMT geometry should not simply replace the Sub formula. The GMT cross-check concept is still reusable.

### An alternate reference becomes a gate only when evidence supports it

Do not copy a mature cross-check threshold simply because the same quantities exist on another family. In the 124060 development study, radial/chord disagreement did not correlate with rotation resize error, and stable genuine-source readings could disagree by roughly 2 to 2.7 degrees.

Keep an alternate reference diagnostic until family-specific evidence shows that disagreement predicts a bad measurement. Independent geometry can still be useful for describing the kind of issue: for example, the triangle base edge can corroborate whether an axis lean is a whole-marker rotation or only a point/shape lean.

### Recovery must not lower the primary standard

A mature path may use a bounded recovery detector when the primary detector fails. A recovered result should carry lower confidence where appropriate. Do not widen primary plausibility gates merely to improve coverage.

### Pixel support matters

A normalized geometric value is not equally trustworthy at every image size. Fine spacing/rotation checks should establish a minimum usable pixel scale or uncertainty model before production judgement.

A resolution floor alone is not a substitute for direct repeatability testing: in the 124060 development study, some well-resolved dealer images still showed multi-pixel movement after small resizes.

### Reuse an estimator before reusing its policy threshold

An algorithm can transfer cleanly while its decision boundary does not. `GmtMarkerPose` produced valid marker-layout pose estimates on 29/39 124060 development photos with low affine residuals, showing that the estimator itself is reusable. However, only 5/29 valid Sub estimates met the GMT-specific 5-degree `nearFrontal` policy.

Therefore reuse the estimator first, inspect its uncertainty and failure modes, then calibrate any family-specific pose policy separately. Do not invent a new family pose detector until the mature estimator has been tested.

### Model-specific shape priors stay model-specific

Triangle apex angle, marker dimensions, date-side layout, radial spacing and calibrated thresholds may differ by family. These belong in model configuration or family-specific detection where evidence requires it.

The generic part is usually the relationship being tested: alignment to the nominal hour axis, local minute-track geometry, centring, rotation, spacing, symmetry and confidence handling.

### Common markers should use common primitives where the assumptions match

GMT and 124060 already share dial localisation, baton detection, round-marker detection and hand-obstruction machinery. Prefer model layout/configuration around shared marker primitives instead of parallel copies.

The shared re-measurement machinery matters too. In the 124060 development study, the existing GMT baton stability routine identified 5/83 readings as unstable under its own GMT-calibrated policy. Treat that as evidence that the shared mechanism is useful, not as permission to import GMT marker thresholds into the 124060.

### Before declaring a metric unusable, check the mature path

If a new-family metric is noisy or fails repeatability, first ask whether the mature path solved the same type of problem with:
- a different reference frame;
- a cross-check;
- a local-frame confidence score;
- minimum pixel support;
- resize repeatability;
- recovery;
- perspective/pose gating;
- or a different normalization.

Only after that audit should a new algorithm be proposed.

### Physical watch is the independent unit

Repeated files, resolutions or crops of one photograph are not independent watches. Aggregate and split by physical watch. Treat re-encodes/resolutions as measurement-repeatability evidence, not population evidence.

### Source style can confound watch class

If genuine images come from dealer photography while replica images come from QC photography, apparent genuine/replica separation can be caused by image domain. Do not set a production tolerance from a class comparison that is also a source-style comparison.

### Preserve the mature path while learning

Cross-family experiments should begin as diagnostics. Do not alter GMT behaviour merely to make a new family fit a shared abstraction. Prove equivalence on GMT and improvement or non-regression on the new family before replacing production logic.

## Required experiment/handoff note

For any substantial detector, geometry, presentation or QC experiment, include a short section with:

- **Lessons reused:** mature mechanisms deliberately carried over.
- **Deliberate divergences:** mature mechanisms not reused, with evidence/reason.
- **New reusable lesson:** anything learned that should affect future families.

If a new reusable lesson is established, update this file in the same branch before the work is considered complete.
