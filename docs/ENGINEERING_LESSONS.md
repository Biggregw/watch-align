# Watch Align engineering lessons

This is the short, living index of lessons that should be reused across watch families.
It is not a tolerance table and it does not make model-specific geometry generic by assertion.
The purpose is to stop a new family from rediscovering problems already solved elsewhere.

Before designing or changing a detector, measurement, confidence rule, recovery path, or QC decision:

1. Read this file.
2. Find the closest mature implementation, normally the GMT path.
3. Read the relevant handoff/research note for that implementation.
4. Separate what is genuinely generic from what is model-specific.
5. Try reuse/adaptation diagnostically before creating a parallel algorithm.
6. Preserve the mature family's regression behaviour while experimenting.

## Reuse-first checklist

For every new family-specific check, answer these before implementation:

- What is the equivalent mature check, if any?
- Which detector primitives, coordinate frames, confidence gates, recovery rules and tests already exist?
- Which parts are pure geometry or image-quality logic and which parts depend on model dimensions/layout?
- What known failure modes did the mature path already solve?
- Can the existing implementation be parameterised or composed rather than copied?
- If we intentionally diverge, what measured evidence shows the mature approach is worse or inapplicable?

A new family-specific implementation should not be created just because the marker shape or model name is different.
Equally, shared code must not be forced when the evidence shows a genuinely different measurement strategy is more reliable.

## Reusable lessons from GMT and 124060 work

### Detection is not trust

Finding a plausible marker is only the first stage. Fine QC measurements need independent confidence checks before they can support a judgement. Keep detection, measurement, confidence and QC decision separate.

### Re-measure before judging fine geometry

The mature GMT path re-measures important landmarks at 94% and 88% image scale. This catches results that depend on a particular decode/resample rather than the physical watch. The same principle should be considered for every fine marker measurement before adding a tolerance.

### Reuse confidence logic and failure modes, not only detector code

The most valuable transferable work is often the guardrail around an algorithm: minimum pixel size, hand obstruction, local-frame quality, resize repeatability, alternate-reference disagreement, recovery confidence and fail-closed behaviour.

When adapting a mature check, audit these before writing a new detector or measurement formula.

### Local and global references are independent evidence

A local minute-track reference can reject dial-centre error; a dial-radial reference can be less noisy when local tick endpoints jitter. Do not assume one is universally superior.

Where both are available:
- measure both diagnostically;
- choose the primary reference from repeatability evidence for that marker/model;
- retain disagreement between the references as a possible confidence signal rather than discarding the losing reference.

The 124060 12 study is the current example: its dial-radial rotation was more repeatable than the GMT-style 59/01 chord, so GMT geometry should not simply replace the Sub formula. The GMT cross-check concept is still reusable.

### Recovery must not lower the primary standard

A mature path may use a bounded recovery detector when the primary detector fails. A recovered result should carry lower confidence where appropriate. Do not widen primary plausibility gates merely to improve coverage.

### Pixel support matters

A normalized geometric value is not equally trustworthy at every image size. Fine spacing/rotation checks should establish a minimum usable pixel scale or uncertainty model before production judgement.

### Model-specific shape priors stay model-specific

Triangle apex angle, marker dimensions, date-side layout, radial spacing and calibrated thresholds may differ by family. These belong in model configuration or family-specific detection where evidence requires it.

The generic part is usually the relationship being tested: alignment to the nominal hour axis, local minute-track geometry, centring, rotation, spacing, symmetry and confidence handling.

### Common markers should use common primitives where the assumptions match

GMT and 124060 already share dial localisation, baton detection, round-marker detection and hand-obstruction machinery. Prefer model layout/configuration around shared marker primitives instead of parallel copies.

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

For any substantial detector, geometry or QC experiment, include a short section with:

- **Lessons reused:** mature mechanisms deliberately carried over.
- **Deliberate divergences:** mature mechanisms not reused, with evidence/reason.
- **New reusable lesson:** anything learned that should affect future families.

If a new reusable lesson is established, update this file in the same branch before the work is considered complete.
