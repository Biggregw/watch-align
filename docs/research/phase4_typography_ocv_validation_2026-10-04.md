# Phase 4 Experiment 4 — typography / OCV validation — 2026-10-04

## Status

Research only. No Android, detector, calibration, verdict, threshold, UI, or production-path changes.

Decision: **NO-GO for lower-dial typography or marker morphology as a standalone authenticity classifier on the current evidence. HOLD selected local features as explainable, batch-sensitive QC evidence.**

## Purpose

Experiment 4 tested the genuine 124060 image-space master v0 against independent replica watches using one-photo-compatible visual features:

- lower-line position, width, height and spacing;
- depth-line final `m` geometry;
- normalized local `SUBMARINER` glyph morphology;
- marker morphology as supplementary evidence.

The acceptance criterion remained physical-watch-level separation. Multiple photos of the same watch were used only for repeatability checks and were never counted as independent watches.

## Fixed genuine master

The genuine master was frozen before replica validation. It was built from 24 independent genuine watches normalized using the eight round hour markers, with the lower text then measured independently.

Key genuine depth-line `m` baseline values from v0:

- bottom offset vs preceding `300`: median `0.0000 R`;
- top offset: median `0.0111 R`;
- height ratio vs preceding digits: median `0.7391`, empirical q05 about `0.7188`;
- gap from preceding zero: median `0.00444 R`.

`SUBMARINER` per-glyph geometry remained provisional (`n=4`) and was therefore treated as exploratory morphology rather than a verdict feature.

## Strict blind split

The 24 genuine watches used to build v0 were excluded from the strict blind genuine set.

Remaining blind set:

- 17 independent genuine watches;
- 6 independent replica watches: 3 Clean, 3 VSF.

The fixed marker-ring normalizer succeeded on:

- 11/17 blind genuine watches;
- 6/6 replica watches.

### Critical pose imbalance

The strict blind genuine set is substantially harder than the replica set because the near-frontal genuine images were preferentially consumed by the master-building stage.

Most normalized blind genuine images are approximately 8–20 degrees off-axis, while the useful replica QC images are mostly around 1–7 degrees.

With the fixed Experiment 3 text rules, on the single preselected blind image per watch:

- `SUBMARINER` was assessable on 2/11 normalized genuine vs 3/6 replicas;
- depth line on 0/11 genuine vs 1/6 replicas;
- `SUPERLATIVE CHRONOMETER` on 1/11 genuine vs 3/6 replicas;
- `OFFICIALLY CERTIFIED` on 1/11 genuine vs 3/6 replicas.

Therefore the strict blind set cannot provide a fair sensitivity/specificity estimate for typography. The failure is primarily assessability/pose, not evidence that genuine typography is intrinsically less stable.

This is itself a product result: typography requires a strong image-suitability gate and must abstain on poor-angle / poorly normalized photos.

## Repeated-photo physical-watch checks

To test whether apparent replica differences were physical-watch features rather than one-photo noise, the same frozen rules were applied to multiple photos of the same replica watches. These repetitions are not extra independent samples.

### VSF `rep_124060_vsf_1gwpcm4`

Five near-frontal photos independently passed all four lower text lines.

Final `m` height ratio across the five photos:

- 0.7083
- 0.6800
- 0.7083
- 0.6250
- 0.6957

All five are below the genuine v0 empirical q05 (~0.7188). The other coarse line geometry was strikingly close to genuine: depth-line width, `SUBMARINER` width, line centres, and line spacing were generally inside or near the genuine v0 distribution.

Interpretation: this particular VSF watch has a repeatable local `m` difference, but its overall lower-dial layout is close to genuine.

### Clean `rep_124060_clean_15spe47`

One cleanly assessable depth-line photo gave final `m` height ratio about `0.6818`, also below the genuine q05.

Some photos of this watch include QC overlay graphics, which contaminate marker/photometric morphology. Those views must not be treated as clean visual evidence.

### Clean `rep_124060_clean_1lnn2kz`

Two independent photos passed the depth-line rules.

Both measured final `m` height ratio `0.7391`, effectively the genuine v0 median. Other depth-line geometry was also inside the genuine image-space range.

This is the decisive counterexample to using the `m` as a universal authenticity tell.

### Clean `rep_124060_clean_1d06ekm`

The normalizer succeeded on most photos, but none passed the fixed depth-line segmentation rules. The feature is therefore unassessable on this watch with the current one-photo pipeline.

## `SUBMARINER` glyph-shape OCV test

The accepted `SUBMARINER` word masks were cropped by the fixed line detector, resized to a common canonical mask, and compared using a symmetric distance-transform / Chamfer-style shape distance.

### Genuine leave-one-out reference

Eight genuine word masks were available.

Leave-one-out genuine shape distances:

`0.077, 0.104, 0.109, 0.142, 0.198, 0.232, 0.345, 1.330`

- median about `0.170`;
- empirical q95 about `0.985`.

### Replica physical-watch medians

- VSF `1gwpcm4`: median about `0.106` across five accepted photos;
- Clean `15spe47`: median about `0.264` across accepted photos;
- Clean `1lnn2kz`: median about `0.123` across two accepted photos.

All are inside the genuine leave-one-out variation. The VSF and one Clean watch are actually closer to the genuine median template than many genuine leave-one-out examples.

**Decision:** normalized `SUBMARINER` glyph morphology does not provide replica separation in the current sample.

## Per-glyph geometry check

The provisional genuine `SUBMARINER` widths/gaps were also compared with repeated replica photos.

VSF `1gwpcm4` and Clean `1lnn2kz` track the genuine provisional per-letter widths and gaps closely. Clean `15spe47` is somewhat narrower in several glyphs, but the shift is not a stable cross-factory authenticity signature and may include threshold/photometric effects.

**Decision:** no production use. More genuine per-glyph data would be required even for a QC tolerance.

## Marker morphology

Marker bounding-box morphology remains useful as descriptive QC evidence, but it also failed the universality requirement:

- a good VSF image can sit close to the genuine marker-morphology master;
- other replica images can look strongly anomalous, but the same fixed detector also becomes strongly biased on tilted genuine images;
- apparent large marker-centre shifts on the strict genuine holdout track failed affine normalization / perspective rather than physical marker placement.

**Decision:** do not use photometric marker bounding boxes as an authenticity classifier.

## Main findings

### NO-GO

1. **Lower-line layout as a standalone authenticity classifier.** High-end replicas can match genuine line placement and spacing closely.
2. **`SUBMARINER` normalized glyph morphology as a standalone classifier.** Replica word shapes fall inside genuine leave-one-out variation.
3. **Final `m` as a universal binary tell.** It is repeatably different on one VSF and one Clean watch, but another independent Clean watch matches the genuine median exactly.
4. **Marker morphology as a universal factory/authenticity signature.** Pose and segmentation effects remain too large and at least one good VSF is close to genuine.

### HOLD / useful as explainable QC evidence

1. Final-`m` height/baseline geometry is a real measurable feature and appears batch/watch-sensitive.
2. Individual line placement/spacing features are stable enough to report when image suitability is strong.
3. Marker morphology may identify specific per-watch defects when the evidence is clean.
4. Local typography features may contribute to a future multi-evidence score, but only after substantially more independent replica watches and balanced genuine holdouts.

## Product implication

The research does **not** support turning Watch Align into a photo-only genuine/fake classifier from the current geometry + typography evidence.

It does support a stronger product distinction:

`single QC photo -> suitability gate -> reference-specific measurements -> per-feature evidence -> confidence / abstention`

rather than:

`single QC photo -> fake probability`

A high-end VSF can reproduce the 124060 lower dial layout and `SUBMARINER` morphology closely enough to sit inside genuine image-space variation.

## Data implication

The next bottleneck is data, not another transformation or threshold.

If authenticity research continues, the highest-value acquisition is:

- more independent Clean / VSF / other-factory physical watches, ideally several batches;
- new near-frontal genuine 124060 watches held completely outside the current master;
- comparable image quality/pose between genuine and replica groups;
- exact physical-watch and source grouping retained by the existing harvester/calibration infrastructure.

The harvester and calibration system therefore remain useful as evidence infrastructure even though the current seven metrics and new typography features have not produced universal gen/rep separation.

## Decision

**Experiment 4 is complete.**

Do not implement an authenticity verdict from the current Phase 4 features.

Retain the genuine master and typography measurements as research assets and possible reference-specific QC evidence. Any next authenticity experiment should begin with a deliberately balanced acquisition plan rather than more production code.
