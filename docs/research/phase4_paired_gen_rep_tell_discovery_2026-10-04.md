# Phase 4 Experiment 2 — paired genuine / VSF / Clean tell discovery — 2026-10-04

## Status

Research only. No Android, detector, calibration, verdict, threshold, UI, or production-path changes.

Decision: **GO for targeted typography/print-geometry research; HOLD for generic whole-dial residual classification; NO-GO for treating the current seven metrics or one alleged factory tell as an authenticity classifier.**

## Purpose

Experiment 2 asks a narrower question than general replica QC:

> Which visual differences repeatedly appear when genuine, VSF and Clean 124060 watches are compared under similar photography, and which of those differences can plausibly survive the product constraint of one externally supplied QC-style photo?

The intended outcome is a ranked set of feature families for later research, not a production verdict.

## Evidence used

### Frozen Watch Align evidence

The completed #73 124060 calibration artifact was used as the quantitative reference set.

The replica subset contains six independent physical watches:

- Clean: `rep_124060_clean_15spe47`, `rep_124060_clean_1d06ekm`, `rep_124060_clean_1lnn2kz`;
- VSF: `rep_124060_vsf_1gwpcm4`, `rep_124060_vsf_1rslupa`, `rep_124060_vsf_1u8hbiz`.

These provide independent QC-style photos rather than repeated crops of one watch.

### Paired public comparisons

Candidate tells were taken only from comparisons where genuine and replica watches were shown side by side or by an owner who had the compared watches in hand. The strongest source was the archived RWI 124060 comparison in which genuine, Clean and VSF were photographed at the same time with the same phone and described as natural/unprocessed.

Additional independent paired Reddit comparisons were used to check whether the same regions recur across owners and batches.

Because reliable full-resolution downloads of every public paired image were not available in this environment, no claim is made that pixel-level residual maps were computed from those public screenshots. Public paired comparisons were used for **candidate discovery**; the frozen #73 images were used for quantitative checks where the current detector exposes a corresponding measurement.

## Repeated candidate regions from paired comparisons

The same feature families recur across independent comparisons:

1. **dial printing / typography**
   - overall print weight and brightness;
   - `SUBMARINER` / depth-rating text appearance;
   - the well-known `m` spacing/position discussion;
   - coronet and small-text morphology;
2. **rehaut finish and engraving**
   - polish / reflectivity;
   - engraving sharpness and appearance;
3. **SEL / lug / case geometry**
   - SEL fit and gaps;
   - crown-guard and case-profile differences;
4. **hour-marker geometry and morphology**
   - 12 triangle centring/shape;
   - isolated marker alignment;
   - claims that some Clean batches place markers too near the minute track;
5. **hands, pip and material/colour cues**
   - hand finish / apparent white-gold appearance;
   - pip tone;
   - minute-track / print whiteness.

The important observation is that several of these are described differently across batches. A tell seen on one VSF or Clean batch is not automatically a factory-wide invariant.

## Quantitative check of the existing seven calibrated metrics

The existing calibrated geometry was tested at physical-watch level rather than photo level.

### Individual metrics

Some replica watches sit near genuine-envelope extremes on individual measurements, for example:

- several replica watches have comparatively large round-marker spacing RMS;
- two VSF watches have relatively low round-marker radial position;
- individual watches show unusual 12 rotation, centring, or 12–6 axis values.

However, these effects are inconsistent across the six independent replica watches and across factories.

In particular, the community claim that Clean markers are systematically too close to the minute track is **not validated as a universal Clean signature** by the frozen sample. One Clean watch is relatively high in round-marker radius while another is effectively in the genuine distribution.

### Joint anomaly test

A gen-only robust multivariate anomaly test was also run on watch-level medians of the existing calibrated metrics. Several combinations were tested, including:

- 12 gap + 12 rotation;
- round-marker radius + round-marker spacing RMS;
- 12 gap + rotation + centring;
- round-marker radius + spacing + rotation;
- the five most commonly available metrics together.

No combination produced reliable separation of all or even most replica watches from genuine watches. Replica anomaly ranks generally remained inside the genuine leave-one-watch-out distribution.

**Decision:** do not rescue the current seven metrics by wrapping them in a multivariate anomaly score. Phase 3's conclusion stands: they are useful explainable QC measurements, but the current replica evidence does not make them an authenticity classifier.

## Candidate ranking

| Feature family | Paired-source support | Current frozen quantitative support | Batch stability | One-photo robustness | Decision |
|---|---|---|---|---|---|
| Dial typography / print geometry | Strong and recurring | Not yet directly measured | Medium: exact defect varies, but print is repeatedly cited | High after normalization | **GO** |
| Rehaut engraving / finish | Strong and recurring | Existing rehaut geometry is useful for pose, not finish/authenticity | Medium | Medium: lighting-sensitive | **HOLD / targeted research** |
| SEL / lug / case geometry | Strong and recurring | Not represented in current seven metrics | Medium | Medium if full case visible | **HOLD / later geometry study** |
| Marker morphology / isolated alignment | Strong as per-watch QC | Existing geometry catches some defects but not factory-wide separation | Low-medium | High on good dial images | **GO as QC evidence, NO-GO as universal factory tell** |
| Clean marker radial-placement claim | Community/batch reports | Not reproduced consistently across frozen Clean watches | Low | High if real | **NO-GO as global Clean signature** |
| `floating m` as binary tell | Repeatedly cited | Not directly measured | Low-medium; genuine variation and rep batch variation reported | High after normalization | **NO-GO as binary tell; GO as one typography feature** |
| Hands / pip / colour/material | Repeatedly noticed | Not currently measured | Unknown | Low-medium: white balance and reflections dominate | **LOW PRIORITY** |
| Whole-dial raw pixel residual map | Conceptually attractive | Not validated with enough controlled paired full-res pixels | Unknown | Low without stronger photometric normalization | **HOLD** |
| Current seven-metric joint anomaly | Quantitatively tested | No useful separation | N/A | High | **NO-GO** |

## Main finding

The best new single-photo direction is **not another global ratio** and not a hard-coded factory tell.

The strongest candidate is to treat the known 124060 dial printing as a metrology problem:

`known reference -> normalized text regions -> glyph / baseline / spacing / stroke morphology -> per-feature evidence`

This is closer to industrial OCV than generic OCR. The app already knows what text should be present, so the useful measurements are shape, placement, spacing and morphology rather than transcription.

The `m` issue is a good example of why this must be statistical rather than binary: it is repeatedly noticed by humans, but both genuine variation and replica batch variation make a simple yes/no rule unsafe. It should become one coordinate/morphology feature inside a broader print model.

## Implications for later experiments

### Experiment 3 — genuine loose-dial master

The next governing experiment should proceed as planned, but its scope should explicitly include typography and morphology, not only marker coordinates.

For each credible genuine loose dial / clean reference image, capture where possible:

- marker centres and dimensions;
- 12-triangle vertices and surround geometry;
- minute-track locations;
- coronet bounding geometry;
- line baselines and line-to-line spacing;
- word bounding boxes;
- individual glyph widths/heights;
- inter-character spacing, especially the depth-rating line;
- stroke-width / edge morphology at available resolution.

This will provide the nominal reference needed for Experiment 4 OCV/template/anomaly testing.

### Rehaut

Keep rehaut as a second research family, but separate:

- **geometry / visible-width evidence**, already useful for pose and image suitability;
- **surface finish / engraving appearance**, which is potentially discriminative but must be normalized for lighting before it can be trusted.

### Factory signatures

Do not create a single `VSF tell` or `Clean tell`. If factory classification is revisited, model it as batch-sensitive probabilistic evidence and require validation across several independent physical watches per batch/factory.

## Decision

**Experiment 2 is complete.**

- **GO:** typography / print geometry as the highest-priority new feature family.
- **GO:** continue marker morphology as explainable per-watch QC evidence, not as a universal authenticity tell.
- **HOLD:** rehaut finish/engraving and SEL/case geometry pending better normalization and data.
- **HOLD:** generic whole-dial residual/anomaly maps until a genuine nominal master and stronger canonical normalization exist.
- **NO-GO:** current seven-metric multivariate anomaly classifier.
- **NO-GO:** hard-coded universal Clean/VSF tell from one community observation.

Next governing step: **Experiment 3, build the genuine 124060 nominal master from loose-dial and high-confidence genuine reference material, explicitly including dial typography geometry.**
