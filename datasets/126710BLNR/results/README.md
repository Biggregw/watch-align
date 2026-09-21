# Batgirl corpus analysis results

This directory holds **derived numeric/diagnostic output only** — no
third-party source photographs. Third-party images are fetched fresh on a
GitHub Actions runner, analyzed there, and discarded when the job ends;
only the measurements below ever reach this repository. See
`.github/workflows/analyze-batgirl-corpus.yml` for the job that produces
these files, and `../FETCH_RESULTS.md` / `../README.md` for the dataset
and provenance design.

This is an **acquisition-reliability and measurement-repeatability**
study, not a genuine-vs-replica classifier. Nothing here should be read as
"this measurement proves authenticity" — `gen_candidate` is a
source-labelled marketplace claim, not authenticated ground truth, and
`rep_labelled` watches are explicitly replica-sourced. See the parent
README's provenance rules.

## Files

- **`per_image_measurements.csv`** — one row per fetched image. Every image
  that was fetched appears here, including acquisition failures and
  decode failures — nothing is silently dropped. Key columns:
  - Provenance (from the manifest): `source_id`, `physical_watch_id`,
    `class_label`, `provenance`, `split`, `factory`, `bracelet`,
    `local_path`, plus dedup info (`sha256`, `dhash`,
    `possible_duplicate_of`).
  - `pipeline_outcome`: `accepted` / `rejected` / `acquisition_failed` /
    `decode_failed` / `exception`. `failure_reason` carries the detail for
    any non-`accepted` outcome.
  - Pose/acquisition: `pose_confidence`, `tilt_deg`,
    `center_displacement_frac`, ellipse geometry, minute-track fit
    metrics, held-out validation metrics, projective-refinement
    before/after evidence, identity-gate verdict.
  - Per-marker measurements (`marker_<hour>_measured`,
    `marker_<hour>_angular_deg`, `marker_<hour>_radial_pct_r`,
    `marker_<hour>_body_rotation_deg`, plus the 12 marker's
    triangle-outward delta) for every hour except 3 (date window, no
    applied marker on this model).

- **`per_watch_summary.csv`** — aggregated by `physical_watch_id` so that
  ten photos of one watch are one sample, not ten. For every numeric
  column in `per_image_measurements.csv` this adds `<col>__n` (usable
  photo count for that metric), `<col>__median`, `<col>__min`,
  `<col>__max`, and `<col>__mad` (median absolute deviation, a robust
  spread measure), plus `n_images_total`, `n_images_pose_accepted` and
  `acquisition_success_rate` per watch.

- **`run_metadata.json`** — reproducibility metadata for the run: Python
  engine git commit, manifest/resolved-images hashes, Python/OpenCV/NumPy
  versions, timestamp, GitHub Actions workflow run ID, and source
  attempted/populated/failed counts.

## Rules for using this data

- Aggregate to `physical_watch_id` before comparing anything between
  `gen_candidate` and `rep_labelled` populations — never compare raw image
  counts.
- Validation-split watches must stay held out from any threshold or
  algorithm tuning; use them only to check whether calibration-derived
  expectations hold up.
- A measurement is only worth treating as evidence once it is shown to be
  repeatable across multiple photos of the same physical watch. Check
  `<col>__mad` and `<col>__n` in `per_watch_summary.csv` before trusting
  any single-photo value from `per_image_measurements.csv`.
- Do not derive or change production QC thresholds from this data without
  a separate, explicit review step.
