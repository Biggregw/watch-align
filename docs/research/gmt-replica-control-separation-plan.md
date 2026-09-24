# GMT replica/control separation phase

Status: STARTED

## Purpose

Test whether the 13 provisionally frozen genuine GMT geometry features can distinguish known, visually meaningful replica QC defects without falsely flagging genuine controls.

The genuine baseline at `docs/research/gmt-genuine-baseline-results/genuine-baseline-v1.json` is frozen input for this phase. Do not retune that baseline from replica observations.

## Initial feature set

12 marker:
- `h12.stage3_apex_r_simple`
- `h12.stage3_centre_r_projective`
- `h12.stage3_base_r_projective`
- `h12.stage3_axis_incidence_canonical`
- `h12.stage3_centroid_tangential_offset_canonical`

6 marker:
- `h06.centre_r`
- `h06.centre_t`
- `h06.axis_residual_deg`
- `h06.radial_span`

9 marker:
- `h09.centre_r`
- `h09.centre_t`
- `h09.axis_residual_deg`
- `h09.radial_span`

## Control-set rules

- Unit of independence is the physical watch, not the image.
- Use modern right-handed black-dial 12-series GMT QC images compatible with the dial geometry where appropriate.
- Preserve model/reference, factory when known, source URL/thread, image identity and physical-watch identity.
- Ground-truth defect labels must be independently supported by the QC discussion or other documented evidence. Do not invent labels from the measurement under test.
- Prefer face-on dealer-style QC photographs with visible 12/6/9 markers and minute track.
- Keep clean/GL replica controls as well as defect-positive examples. Separation testing needs both.
- Keep genuine baseline controls untouched and evaluate false-positive behaviour against the frozen genuine set.

## First target defects

Prioritise defects directly observable by the frozen feature set:

1. 12 triangle high/low radial placement.
2. 12 triangle tangential/axis misalignment.
3. 12 triangle abnormal radial proportions, including apex/centre/base relationships.
4. 6 marker high/low placement.
5. 6 marker tangential offset or rotation.
6. 6 marker abnormal radial span.
7. 9 marker high/low placement.
8. 9 marker tangential offset or rotation.
9. 9 marker abnormal radial span.

## Required outputs

Create a raw per-image/per-watch measurement table retaining provenance and ground-truth labels.

For every frozen feature report:
- genuine median and MAD from the frozen baseline
- replica/control value and signed deviation
- robust deviation in genuine MAD units where meaningful
- distributions grouped by ground-truth defect class
- false-positive behaviour on genuine controls
- detection/separation behaviour on defect-positive replicas
- repeatability/sensitivity where more than one suitable image exists for the same physical watch

Also produce a concise validation report identifying which features are:
- `candidate_qc_signal`
- `diagnostic_only`
- `insufficient_evidence`

Do not convert empirical p10/p90, MAD multiples or the largest observed genuine deviation directly into production pass/fail thresholds. Production thresholds require a separate visually-meaningful threshold-validation step.

## Success criterion

A feature advances as a candidate QC signal only if the measurement is already stable on genuine watches and the replica/control experiment demonstrates useful separation for the intended visible defect without unacceptable genuine false positives.

## Product linkage

Follow `docs/product/watch-align-qc-product-spec.md`. Findings should ultimately be visual and plain-English. Raw metrics remain research evidence and are not intended for the normal user-facing result.
