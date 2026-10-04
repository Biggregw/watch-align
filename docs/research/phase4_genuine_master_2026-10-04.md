# Phase 4 Experiment 3 — genuine 124060 nominal image master — 2026-10-04

## Status

Research only. No Android, detector, calibration, verdict, threshold, UI, or production-path changes.

Decision: **GO to Experiment 4 using a versioned image-space genuine master.**

This experiment establishes a reproducible *image-space* nominal reference for the Rolex Submariner 124060. It does **not** claim access to Rolex engineering drawings or factory manufacturing tolerances.

## Evidence hierarchy

The master uses three evidence tiers:

1. **Official / retailer reference material** for identity and clean-layout corroboration.
2. **Physical loose-dial evidence** where provenance is credible enough for research corroboration.
3. **Independent genuine complete watches** from the frozen #73 corpus for population-scale image-space nominal values and observed tolerance.

### Official reference

Rolex identifies reference 124060 as the Submariner with a black dial. Rolex's own model material describes the dial as black lacquer with Chromalight hour markers and hands in 18 ct white gold.

### Loose dial

One independently identified physical 124060 loose dial was found in the current market search. The seller describes it as a Rolex 124060 Chromalight dial and supplies four photographs. These four views count as **one physical dial**, not four independent samples.

The seller claim is useful corroboration but is not treated as Rolex authentication and is not used alone to establish a population tolerance.

### Frozen genuine corpus

The completed #73 evidence contains **41 independent genuine physical 124060 watches** across:

- Bob's Watches: 12 watches;
- European Watch Company: 8;
- DavidSW: 7;
- SwissWatchExpo: 6;
- Watches of Switzerland Rolex CPO: 6;
- Phillips: 2.

For the new image-space master, one best near-frontal image per watch was selected before normalization so repeated photos of one watch do not inflate sample size.

## Canonical normalization

The new typography/morphology master was built from **24 independent genuine watches** that passed image-size, pose, and marker-detection checks.

Each image was normalized using only the eight round hour markers at 1, 2, 4, 5, 7, 8, 10 and 11:

1. detect round-marker centres;
2. fit their common ring;
3. use that ring for centre and scale;
4. estimate in-plane roll from the known hour locations;
5. map to a 1000 x 1000 canonical image with dial radius `R = 450 px`.

This is intentionally a similarity normalization. Typography is **not** used to align typography, which avoids fitting the answer into the reference.

The marker-ring target uses the existing genuine nominal `round.ring_rho ≈ 0.819`.

## Existing genuine geometry carried into v0

These are physical-watch-level medians from the frozen calibration evidence. They remain explainable QC measurements, not authenticity classifiers.

| metric | watches | median | MAD | q05 | q95 |
|---|---:|---:|---:|---:|---:|
| `round.ring_rho` | 29 | 0.819038 | 0.001562 | 0.814329 | 0.823763 |
| `round.spacing_rms_deg` | 29 | 0.285242° | 0.122780° | 0.094670° | 0.999314° |
| `twelve.rotation_deg` | 29 | 0.244362° | 0.226425° | -0.408152° | 1.040082° |
| `twelve.centring_w` | 30 | 0.002239 | 0.007387 | -0.017759 | 0.014647 |
| `twelve.gap_r` | 22 | 0.028250 | 0.001274 | 0.023414 | 0.043237 |
| `baton.3_9_line_offset_r` | 19 | 0.004896 | 0.003391 | 0.001076 | 0.016341 |
| `axis.12_6_line_offset_r` | 18 | 0.001636 | 0.001257 | 0.000173 | 0.004911 |

The tails, particularly `twelve.gap_r`, include detector/photo repeatability and must not be interpreted as direct Rolex manufacturing tolerance.

## New marker morphology master

Values below are median **photometric image-space bounding geometry**, normalized by dial radius. They are not physical millimetre dimensions.

| feature | n | radial / major dimension | thickness / minor dimension | centre |
|---|---:|---:|---:|---|
| 12 triangle | 10 | width 0.2100 R | height 0.2556 R | x -0.0006 R, y -0.7622 R |
| 3 baton | 9 | length 0.2778 R | thickness 0.0933 R | x +0.7644 R, y 0 |
| 6 baton | 11 | length 0.2778 R | thickness 0.0911 R | x +0.0011 R, y +0.7644 R |
| 9 baton | 10 | length 0.2789 R | thickness 0.0911 R | x -0.7628 R, y +0.0017 R |

The 3/6/9 symmetry is a useful sanity check on the normalization. Thresholding can include different proportions of lume and white-gold surround, so these values are suitable for statistical image comparison, not claims about physical component dimensions.

## New lower-dial typography master

The lower four text lines were much more reproducible than the upper `ROLEX` / coronet area because hands frequently obscure the upper dial.

Robust nominal geometry:

| line | accepted n | robust core n | width | height | centre y |
|---|---:|---:|---:|---:|---:|
| `SUBMARINER` | 8 | 5 | 0.5533 R | 0.0578 R | +0.2844 R |
| `1000ft = 300m` | 8 | 6 | 0.5100 R | 0.0667 R | +0.3633 R |
| `SUPERLATIVE CHRONOMETER` | 10 | 6 | 0.6933 R | 0.0478 R | +0.4344 R |
| `OFFICIALLY CERTIFIED` | 8 | 7 | 0.5333 R | 0.0467 R | +0.4989 R |

All four lines are effectively centred on the dial within the current image-space precision.

### Line-to-line spacing

On the six independent watches where all four lines passed together:

- `SUBMARINER -> depth`: median **0.0800 R**;
- `depth -> SUPERLATIVE CHRONOMETER`: **0.0711 R**;
- `SUPERLATIVE CHRONOMETER -> OFFICIALLY CERTIFIED`: **0.0656 R**.

These relative spacings were among the most stable new measurements in the experiment and are strong candidates for Experiment 4.

## Depth-line final `m`

The often-discussed final `m` in `300m` can now be described as geometry rather than forum terminology.

Across eight accepted genuine watches:

- median bottom offset versus the preceding `300`: **0 px** in the canonical frame;
- median top offset versus digit tops: **5 px = 0.0111 R** lower;
- median `m` height: **0.739 x** the median preceding digit height;
- median gap from the preceding `0`: **2 px = 0.00444 R**.

Interpretation: in the genuine image-space baseline the lowercase `m` is naturally shorter and begins lower than the digits, but its **bottom is effectively aligned with the digit baseline**.

This is not an authenticity threshold. It is a concrete nominal feature to test blind against replica watches in Experiment 4.

## Per-glyph `SUBMARINER` geometry

Only four images segmented all ten letters cleanly enough to derive per-glyph widths/gaps.

That is promising but insufficient for a mature tolerance model. It is stored in `124060_genuine_image_master_v0.json` as **provisional n=4** and must not drive a verdict yet.

## Upper dial text and coronet

**HOLD.**

The current corpus contains good upper-dial images, but hand overlap reduced clean independent samples enough that `ROLEX`, `OYSTER PERPETUAL`, and coronet morphology are not frozen as mature v0 features.

A future loose-dial or unobstructed genuine-image expansion can add them without changing the lower-text master.

## Cross-source sanity check

The lower-text master was built from accepted images spanning four independent dealer sources rather than one seller. Source medians for `SUBMARINER` width clustered closely after normalization, which argues against the result being one source's resizing/sharpening signature.

The physical loose-dial photographs and clean official-retailer/reference imagery also qualitatively agree with the same line ordering, centring, and relative widths. They are used as corroboration, not as pseudo-independent statistical samples.

## What v0 establishes

**GO:**

- a genuine 124060 image-space master is viable from one-photo-compatible visual evidence;
- lower-dial typography is measurable with useful cross-watch stability;
- line placement and line spacing are strong new feature candidates;
- the depth-line final `m` can be tested as continuous geometry rather than a binary folklore tell;
- marker morphology can supplement the existing geometric QC evidence.

**HOLD:**

- upper `ROLEX` / coronet morphology until more unobstructed independent evidence is available;
- rehaut surface-finish / engraving master;
- exact physical/manufacturing tolerances.

**NO CLAIM:**

- v0 does not provide Rolex factory engineering dimensions;
- v0 does not prove that any typography feature separates genuine from replica;
- v0 does not justify an authenticity verdict or production threshold.

## Decision

**Experiment 3 is complete: GO to Experiment 4.**

Experiment 4 should test the frozen v0 master blind against genuine holdout and independent VSF/Clean watches using OCV-style features:

1. line centre / width / height;
2. inter-line spacing;
3. depth-line final `m` baseline, height ratio, and gap;
4. local glyph morphology where segmentation is reliable;
5. marker morphology as supplementary evidence.

The acceptance criterion must remain physical-watch-level separation, not photo-level separation. Any feature that only distinguishes seller processing, compression, or lighting is a NO-GO.

The versioned machine-readable master is stored beside this report as `docs/research/data/124060_genuine_image_master_v0.json`.
