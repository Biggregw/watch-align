# Watch Align project closeout — 2026-10-04

## Status

**Project paused / closed by owner for now.**

This is a documentation-only closeout. It does not merge, delete, archive, or change production behaviour.

The purpose is to preserve enough context that the project can be resumed later without repeating months of investigation.

## Live repository state at closeout

- Default branch: `main`.
- PR #49: **open, draft, do not merge automatically**.
  - title: `Calibration outcome and coverage-funnel foundation`
  - head branch: `feature/calibration-outcome-funnel-v1`
  - head SHA: `3b4eb2aa8d45a841079af4aeaf920d240f428621`
  - base branch: `feature/calibration-measurement-contract-v2`
  - the head differs from the proven Phase 3 implementation checkpoint only by the Phase 4 research-plan documentation commit.
- Proven Phase 3 implementation checkpoint before that documentation commit:
  - `98797e97c94a168457998afb31e034537b035928`
- Phase 4 research branch:
  - `research/phase4-rectification-benchmark`
  - head SHA at closeout: `f9b63823bf4f0870613de37886a32e23dbe2c9b1`
- This closeout is frozen on:
  - `archive/watch-align-closeout-2026-10-04`

## Product constraint that governed the final research

The intended user flow was deliberately constrained to:

`one externally supplied QC-style watch photo -> analysis`

No production design should assume guided capture, multiple user photos, a movement photo, a caseback photo, or a second angle.

Multiple images remain valid for offline research and repeatability checks, but not as a runtime requirement.

## What was successfully built or proven

### 1. Explainable watch-QC measurement is viable

The project can recover useful, reference-specific measurements from suitable watch photographs and explain what was measured rather than emitting an opaque score.

The GMT work, Submariner 124060 work, marker detectors, triangle measurements, dial-edge handling, pose checks and overlays demonstrated that deterministic visual metrology is feasible when the image supports it.

### 2. Harvester / evidence infrastructure is valuable

The acquisition, physical-watch grouping, source tracking, frozen evidence, exact-SHA deduplication, replay, coverage reporting and calibration machinery are useful and should be preserved.

They are research/validation infrastructure rather than the product's authentication engine.

### 3. Phase 3 calibration plumbing is sound

The Phase 3 work established deterministic calibration outcomes, coverage/sufficiency reporting, replay equivalence and governing-snapshot proof without changing live QC behaviour.

The main conclusion from Phase 3 was not that the system failed technically; it was that the existing seven calibrated metrics did not show useful replica separation in the available evidence.

### 4. Image suitability / abstention is essential

Several experiments independently showed that angle, occlusion, detector stability and photography can dominate small physical differences.

The defensible architecture is therefore:

`single photo -> suitability gate -> reference-specific evidence -> per-feature confidence -> abstain where unsupported`

rather than forcing a measurement or a genuine/fake probability.

### 5. A genuine 124060 image-space master was built

Experiment 3 produced a versioned genuine 124060 image-space master from independent genuine watches, including lower-dial typography geometry and marker morphology.

Important files:

- `docs/research/phase4_genuine_master_2026-10-04.md`
- `docs/research/data/124060_genuine_image_master_v0.json`

This is an image-space statistical reference, not a claim about Rolex factory engineering dimensions.

## What the final research ruled out

### Projective rectification from current one-photo evidence

Controlled synthetic tests showed that full projective information could theoretically reduce perspective error substantially.

However, the real-image benchmark did not recover those constraints reliably enough. Marker-fitted projective transforms appeared better on the fitted marker ring but failed independent minute-track holdout tests.

Decision: **NO-GO for current projective-refinement approach in production.**

Relevant docs / commits:

- controlled benchmark: `de3bd4462eb90bc6278ed448d469fe13731dab90`
- real-image NO-GO: `4f3c3b1c99bf53744dd187700565424292bc8eb7`

### Existing seven metrics as an authenticity classifier

Individual replica watches sometimes sit near genuine-envelope extremes, but the effect is inconsistent.

A robust multivariate anomaly test of the existing calibrated metrics did not reliably separate the independent replica watches from genuine watches.

Decision: **do not spend more time tuning or recombining the current seven metrics in the hope they become an authenticity classifier without new evidence.**

### Universal Clean / VSF tells

Paired genuine/VSF/Clean comparisons exposed recurring areas of interest, but alleged factory tells were batch-sensitive and inconsistent.

Decision: **NO-GO for hard-coded universal `Clean tell` / `VSF tell` rules.**

Relevant Experiment 2 commit:

- `20b6b33d0e72b8e1f1bf64d0ef59cef03106ae37`

### Lower-dial typography as standalone authentication

Experiment 4 tested lower-line layout, the final `m`, `SUBMARINER` glyph shape and marker morphology.

A good VSF and a good Clean example could sit inside genuine image-space variation. The final `m` was repeatably different on some replica watches but another independent Clean watch matched the genuine median closely.

Decision: **NO-GO for typography or marker morphology as a standalone authenticity classifier on current evidence.**

Relevant Experiment 4 commit:

- `f9b63823bf4f0870613de37886a32e23dbe2c9b1`

## Ideas that remain useful but unproven

### Loose genuine 124060 dials

This idea was not exhausted.

A small set of genuine loose 124060 no-date dials could still be disproportionately useful for recovering nominal design geometry because they remove crystal/rehaut distortion and hand occlusion and expose the entire printing and marker layout.

If research is ever restarted, this is the lowest-complexity unresolved idea worth testing first.

Keep the task simple:

1. obtain 3–5 independent, credibly genuine loose 124060 no-date dials;
2. use the cleanest front image of each;
3. measure the same fixed coordinates on every dial;
4. check how tightly the independent dials agree;
5. only continue if the result is materially tighter than assembled-watch image-space evidence.

Do not broaden this into date dials, generic Submariner variants or another large acquisition project until the 124060 result is known.

### Rehaut finish / engraving

Rehaut geometry is useful for pose/suitability. Rehaut surface finish and engraving may contain discriminative information, but uncontrolled reflections and lighting remain a major confounder.

Keep on HOLD unless better-normalized comparable data becomes available.

### SEL / lug / case geometry

Humans repeatedly notice these areas in gen/rep comparisons. They remain plausible reference-specific QC features, but were not sufficiently developed or validated to justify another implementation cycle.

## Things not to restart without materially new evidence

Do not resume by:

- adding more ratios to the existing seven-metric family;
- tweaking thresholds until current replicas separate;
- forcing free projective homographies from noisy marker detections;
- treating `floating m` or any other forum tell as binary authentication evidence;
- building a generic LLM genuine/fake scorer;
- interpreting colour/material appearance from uncontrolled single QC photos as strong evidence;
- counting multiple photos of the same watch as independent watches;
- measuring seller/source image-processing differences as if they were physical-watch differences.

## If the project is restarted

There are only three sensible restart directions.

### A. Product restart: QC measurement app, not authenticator

This is the most defensible product direction.

Use Watch Align as a reference-specific QC tool that reports measurable alignment, geometry and image suitability, with explicit confidence and `not assessable` states.

Do not promise genuine/fake classification.

### B. Authentication-research restart: data first

Only resume authentication research after obtaining a deliberately balanced set of new physical watches:

- multiple independent Clean watches across batches;
- multiple independent VSF watches across batches;
- other factories if relevant;
- fresh near-frontal genuine 124060 watches held completely outside the current master;
- comparable pose/image quality between genuine and replica groups.

Then rerun existing research methods blind at physical-watch level before changing product code.

### C. Small loose-dial experiment

If curiosity returns but a large project does not, perform only the 3–5 genuine loose-dial comparison described above.

This is the one unresolved experiment with a favourable effort-to-information ratio.

## Important Phase 4 research record

- governing research plan: `3b4eb2aa8d45a841079af4aeaf920d240f428621`
- controlled rectification benchmark: `de3bd4462eb90bc6278ed448d469fe13731dab90`
- real-image rectification NO-GO: `4f3c3b1c99bf53744dd187700565424292bc8eb7`
- paired gen/rep tell discovery: `20b6b33d0e72b8e1f1bf64d0ef59cef03106ae37`
- genuine master report: `63c65f2edbb69c26ab6c5d115cb3f7d77416d8aa`
- genuine master data: `7971d88447b37731ebf65b52b10fdd74ceff1f37`
- typography / OCV validation: `f9b63823bf4f0870613de37886a32e23dbe2c9b1`

## Final conclusion

Watch Align did not fail because the engineering infrastructure was poor. The work progressively removed plausible shortcuts and showed how difficult single-photo authentication of modern high-end replicas actually is.

The project produced a credible measurement/QC foundation, a disciplined evidence/calibration system and several useful negative results. What it did **not** produce is sufficient evidence for a trustworthy one-photo genuine/fake verdict.

That is the correct stopping point.

No further work is required unless the owner deliberately chooses one of the restart paths above.
