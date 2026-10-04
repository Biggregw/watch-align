# GMT v0.3 calibration control — 2026-10-04

Status: **completed research control; no production change**

Governing protocol: `docs/CALIBRATION_PROTOCOL.md` v0.3.

## Question

Can the new opposing-marker / neighbourhood-coherence protocol reproduce or improve the known-working GMT 12-marker gap calibration using evidence already in the repository, without new harvesting, CI, APK builds, or production-code changes?

## Evidence reused

This control deliberately reused the stored 2026-09-30 measurement export on branch `data/harvest` (`measurements/20260930T094818Z/...`) plus the previously frozen genuine-envelope evidence in `docs/research/`.

The stored export contains the production 12-gap result plus per-hour round-marker `gap` and `inset` measurements. The round-marker `inset` is centre-to-local-minute-track geometry and does not depend on which lume/surround edge was traced, so it is the cleaner existing quantity for this pose/symmetry experiment.

No images were downloaded and no workflow was run.

## Feature under control

Production 12 gap: the existing outer-edge clearance at 12.

Historical genuine evidence already in the project:

- stable real genuine readings had previously reached about **0.081–0.107**;
- sharp additional genuine GMT photos later read about **0.089–0.104**;
- the existing low-clearance attention boundary is **0.070**.

This experiment does **not** fit to those values. They are compared only after deriving the result from the stored 2026-09-30 evidence.

## Stored genuine sample available for this control

Eight independent genuine watches in the stored export had a stable 12-gap reading and enough marker evidence to be useful here. Their 12 gaps were:

| watch | 12 gap | stored pose |
|---|---:|---|
| `bobs_175818` | 0.1356 | CORRECTABLE |
| `bobs_185749` | 0.1255 | CORRECTABLE |
| `gen_126710BLRO_phillips_146213` | 0.1235 | CORRECTABLE |
| `gen_126710BLRO_phillips_147798` | 0.1036 | GOOD |
| `gen_126710BLRO_phillips_NY080121_35` | 0.1038 | CORRECTABLE |
| `gen_126710BLRO_phillips_CH080120_75` | 0.0916 | CORRECTABLE |
| `swe_60177` | 0.1035 | GOOD |
| `wos_cpo_40616911` | 0.1011 | CORRECTABLE |

Descriptive result for these eight independent watches:

- median: **0.1037**;
- minimum: **0.0916**;
- maximum: **0.1356**;
- IQR: about **0.1029–0.1240**.

The Rolex catalogue image measured 0.0919 but is a reference render, not an independent manufacturing sample.

## Opposing-marker symmetry test

For pose evidence, opposite round-marker pairs were compared using **centre-based inset**, not outer-edge gap. The usable pairs are 1↔7, 2↔8, 4↔10 and 5↔11. On a date GMT, 2↔8 and 4↔10 are especially useful because 3 is the date window.

For each image, a simple symmetry score was calculated as the mean absolute inset difference for clean opposing pairs. Lower is more symmetric. Pairs marked hand-obstructed or otherwise unassessable were not treated as clean training pairs.

| watch | clean symmetry score | clean pairs | 12 gap |
|---|---:|---:|---:|
| `wos_cpo_40616911` | **0.0071** | 2 | 0.1011 |
| `bobs_175818` | **0.0128** | 4 | 0.1356 |
| `bobs_185749` | **0.0177** | 3 | 0.1255 |
| `gen_126710BLRO_phillips_146213` | **0.0204** | 2 | 0.1235 |
| `gen_126710BLRO_phillips_147798` | **0.0267** | 3 | 0.1036 |
| `gen_126710BLRO_phillips_NY080121_35` | **0.0363** | 2 | 0.1038 |
| `swe_60177` | **0.0476** | 1 | 0.1035 |
| `gen_126710BLRO_phillips_CH080120_75` | **0.0658** | 1 | 0.0916 |

### What initially looked promising

There is directional evidence that greater opposite-pair asymmetry can make the apparent 12 gap smaller. Across these eight genuine watches, the relationship between the clean symmetry score and 12 gap is moderately negative (Pearson approximately **-0.64**). An illustrative split at the natural break in these scores (not a proposed threshold) gives:

- four lower-asymmetry watches: median 12 gap **0.1245**;
- four higher-asymmetry watches: median 12 gap **0.1036**.

This is consistent with the user's hypothesis that pose can compress one region while expanding its opposite.

### Why no correction formula is accepted

The relationship is not stable enough to promote.

Using the project's historical watch-level partition as a check, development and validation examples show the expected direction, but the former holdout examples are not monotonic. In particular, `wos_cpo_40616911` has the best measured symmetry in this sample yet a 12 gap of only 0.1011, while `gen_126710BLRO_phillips_146213` is less symmetric but reads 0.1235. `swe_60177` is more asymmetric again but reads 0.1035.

Therefore **round-marker symmetry alone cannot be used to correct the 12 gap**. Doing so would risk turning a real/local 12-marker difference or local detector error into a fake perspective correction.

This is exactly the safeguard required by protocol v0.3: perspective must be spatially coherent and feature-specific evidence must not be replaced by a convenient global proxy.

## Master-photo result

Two candidates illustrate an important lesson:

- `wos_cpo_40616911` has the lowest clean-pair symmetry score (0.0071), although only two opposing pairs are fully clean because hands affect other positions;
- `bobs_175818` is the strongest **fully observed** candidate here: all four round-marker opposing pairs are clean, symmetry score 0.0128, the 12 is stable, and the 6 baton is valid and centred.

Yet their production 12-gap readings differ materially: **0.1011 vs 0.1356**.

So a photo being globally symmetric does not by itself define the nominal value of a local outer-edge metric. The 12 gap still contains local edge/tick-definition and measurement effects. One 'perfect-looking' photo must not set a 12-gap tolerance until the **12 and its direct opposite are measured with a common definition**.

## Main result

The v0.3 control is a **partial success, not a recalibration of the production threshold**.

It successfully identified a useful pose signal and, more importantly, prevented the project from making an unjustified correction. It did **not** produce evidence strong enough to replace the existing GMT 12-gap boundary.

The existing **0.070 low-gap attention boundary remains unchanged**. The earlier genuine observations down to about 0.081 are not invalidated by this run because the stored export does not contain the direct comparable 6-gap measurement needed to decide whether those low readings were perspective-compressed.

## LESSONS LEARNED

1. **Use marker centres for pose evidence.** Round-marker outer-edge `gap` was visibly more sensitive to hand/lighting/which edge was traced. The centre-based `inset` is a cleaner symmetry signal because it does not depend on lume-versus-surround edge identity.
2. **A feature needs its direct opposite in the same units before correction.** For the 12 gap, the next useful measurement is 12↔6 using one common radial-gap definition (preferably normalized to dial radius), not a correction inferred only from round markers elsewhere on the dial.
3. **Neighbouring markers are corroboration, not a substitute.** 11/1 and 5/7 should confirm the same regional trend before a 12↔6 difference is called perspective.
4. **Do not excuse isolated local values.** A globally symmetric photo can still have a materially different 12 reading. That is a local feature/detector/manufacturing question until direct opposite evidence proves otherwise.
5. **Do not promote a formula from an apparent correlation.** The symmetry/gap trend looked promising in the combined sample but did not behave monotonically in the historical held-out partition.
6. **The current production pose label is not the new ground truth.** GOOD/CORRECTABLE did not order these symmetry scores reliably; that supports deriving pose evidence from the measured dial itself rather than trusting the old label.

## Proposed protocol change for v0.4 — NOT YET PROMOTED

The next protocol revision should be tested before it replaces v0.3:

- use centre-to-track inset (or another centre-based radial quantity) as the default opposing-marker pose signal when available;
- require a direct same-definition opposite measurement for the feature being corrected (12↔6 for 12 radial gap);
- require at least two clean opposing relationships plus neighbouring regional coherence before applying a perspective correction;
- allow partially obstructed pairs only as corroboration, never as the sole basis for a correction;
- keep isolated feature deviations uncorrected until local detection error is ruled out.

## Next experiment

Without changing GMT production behaviour, expose/recover the existing 6-baton gap and express both 12 and 6 clearances with one common radial normalization. Then test:

1. whether 12 and 6 residuals are complementary on genuine photos;
2. whether 11/1 and 5/7 (plus 2/8 and 4/10 where clean) show the same smooth regional gradient;
3. whether paired correction reduces genuine photo-to-photo spread on a development set;
4. whether that improvement survives a separate validation set;
5. whether an intentionally/local bad marker remains bad rather than being 'corrected' by the pose model.

Only if those gates pass should v0.4 be promoted or the production 12-gap calibration be changed.
