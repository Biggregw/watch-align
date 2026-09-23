# GMT replica ground-truth control-set protocol

## Goal

Test whether proportional marker measurements that are stable on genuine GMT images detect independently human-labelled replica QC defects. Labels are fixed before measurement results are examined.

## Seed set

The companion CSV contains initial r/RepTimeQC candidates covering 12-marker displacement/rotation, 6-marker displacement/rotation, 9-marker rotation, an aligned replica negative control, and a deliberately pose-confounded false-positive case.

These are candidate controls, not automatically authoritative ground truth. `label_strength` records the evidence level. Before quantitative use, preserve the QC image used by the reviewers, verify that it is sufficiently frontal and unobstructed, and ensure that separate Reddit posts are separate physical watches.

## Blind procedure

1. Freeze `human_label`, `label_strength`, `expected_primary_metric` and `expected_supporting_metrics` before running Watch Align measurements.
2. Acquire the original QC image(s) without editing geometry. Rotation/crop copies may be generated only as explicit repeatability perturbations.
3. Run the same pose and marker-measurement path used for the genuine baseline.
4. Save raw measurements without consulting the human label during detector tuning.
5. Compare each predeclared primary metric with the genuine per-watch reference distribution.
6. Report the signed displacement from the genuine median, robust standardized displacement where the genuine sample supports it, and the measurement's repeatability uncertainty.
7. Do not count a detection as successful if the displacement is no larger than ordinary same-watch/photo perturbation error.

## Label-strength rules

- `strong_consensus`: defect is explicitly identified by the OP and supported by reviewer discussion/action such as RL for that defect.
- `reviewer_confirmed`: an independent reviewer explicitly confirms the defect after considering image angle/alignment.
- `op_plus_reviewer_partial`: OP identifies the defect and reviewer acknowledges some related displacement but does not strongly endorse it.
- `op_label`: only the submitter clearly identifies it. Keep provisional.
- `consensus_negative`: post/review discussion explicitly treats marker alignment as good.
- `reviewer_rejected_label`: OP suspects a defect but reviewer attributes it to photo/pose and considers the watch aligned. This is a valuable robustness negative control.

For the first sensitivity analysis, use strong-consensus and reviewer-confirmed positives as the primary positive set. Keep weaker cases for secondary/borderline analysis.

## Required outputs

Create a per-control table with:

- control ID and physical-watch ID;
- human label and strength;
- usable-image count;
- pose quality;
- primary metric value;
- genuine median and MAD/reference band;
- signed difference from genuine median;
- repeatability error estimate;
- whether the expected metric moved in the expected direction/magnitude;
- detector failure or confounding reason if not interpretable.

Also produce a feature-level summary showing true labelled positives detected, negative controls remaining inside the reference range, pose-confounded cases correctly suppressed, and failures.

## Important interpretation rule

The purpose is not to force every Reddit observation to become a Watch Align warning. The experiment decides which geometric features survive perspective, segmentation and photographic noise. A visually discussed defect that cannot be measured repeatably should cause that feature to remain diagnostic-only or be rejected.

## Expansion target

After the seed experiment works end-to-end, expand toward at least 10 independent watches for each high-priority defect family where sufficient public examples exist, while preventing repeated QC images of one physical watch from being treated as independent samples. Prioritise modern right-handed black-dial 12-series GMTs (126710 BLNR/BLRO/GRNR and closely compatible dial geometry) and record reference/factory explicitly.
