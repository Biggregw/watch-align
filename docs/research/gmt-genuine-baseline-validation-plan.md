# Genuine GMT marker baseline validation plan

Status: prepared while the bounded 126710BLNR genuine-baseline run is executing.

## Purpose

Turn the raw genuine-watch measurements into a defensible empirical reference for Watch Align. This is a measurement-quality study, not an authenticity classifier and not a claim about Rolex manufacturing tolerances.

The physical watch is the independent statistical unit. Multiple photographs of one watch must not increase the effective sample size.

## 1. Required run outputs

Before accepting a baseline run, require:

- `source_status.csv`: acquisition and processing outcome for every source.
- per-image measurements with source ID, physical-watch ID, image URL/index and pose diagnostics.
- per-watch collapsed measurements, using the median across accepted images of the same physical watch.
- `summary.json` containing robust statistics from the per-watch table.
- enough provenance to trace every retained watch back to its genuine-labelled source page.

A run that times out before writing these outputs is not evidence and must not be partially interpreted.

## 2. Measurement-quality gates

### Pose gate

Reject an image from baseline estimation when dial pose is unavailable or obviously unstable. Prefer low-tilt images for geometry intended to describe marker placement. Keep rejected/high-tilt images in diagnostics rather than silently dropping their provenance.

### Marker detection gate

For each hour marker retain the existing segmentation confidence and require the detected component to be geometrically plausible. Do not substitute the master position when a marker is not detected. Missing data stays missing.

The first review should explicitly inspect 12, 6 and 9 o'clock because these are strong QC landmarks and are not obscured by the GMT date aperture. The 3 o'clock region requires separate treatment because the date window replaces the normal hour marker.

### Independence gate

Collapse repeated photographs by `physical_watch_id` before estimating genuine distributions. Report both image count and independent-watch count.

### Outlier handling

Do not delete a value merely because it is far from the median. First determine whether it is a segmentation/pose failure. Genuine-looking geometric variation remains in the dataset. Confirmed detector failures may be excluded from baseline estimation but must remain documented in a rejection table with reason.

## 3. Primary marker metrics

For each detected marker, analyse the normalized quantities already emitted by the baseline builder:

- `centre_r`: radial position of marker centre.
- `centre_t`: tangential displacement from the ideal hour radial.
- `outer_r` and `inner_r`: outer/inner radial extents.
- `radial_span`: marker radial size.
- `outer_t` and `inner_t`: tangential edge positions.
- `area_norm`: normalized marker area.
- `diameter_over_dial`: apparent marker scale normalized to dial radius.
- `angular_residual_deg`: angular placement residual.
- `axis_residual_deg`: marker orientation residual where meaningful.
- `segmentation_confidence`: detector-quality diagnostic, not a QC defect metric.

Derived relationships to calculate after the run:

- opposite-marker radial symmetry where both markers exist, especially 12↔6 and 11↔5 / 1↔7.
- left/right paired radial symmetry, especially 10↔2, 9↔3 where applicable, 8↔4.
- adjacent-marker spacing residuals in normalized dial coordinates.
- marker-size ratios between like marker classes rather than comparing unlike shapes directly.
- 12-triangle centre, inner/outer radial placement and orientation relative to the 60-minute axis.

## 4. Genuine reference statistics

Use per-watch values, not per-image values, for the reference distribution.

For every metric report:

- independent-watch `n`;
- median;
- MAD;
- p10 and p90 initially;
- minimum/maximum for diagnostics only;
- missing/detection-failure count.

Do not convert the first small sample into hard pass/fail manufacturing tolerances. Initial bands are empirical observed-image reference bands.

Once the independent-watch sample is sufficiently broad, add bootstrap confidence intervals for the median and robust spread. Until then, label all bands provisional.

## 5. Repeatability requirement

Where a physical watch has multiple usable photographs, measure within-watch spread for every primary metric. A feature is suitable for QC only when between-watch/defect separation is meaningfully larger than ordinary same-watch photographic variation.

For candidate QC feature `f`, record:

- median within-watch absolute deviation across repeated photographs;
- p95 within-watch absolute deviation where sample size permits;
- genuine between-watch MAD;
- observed displacement on known defective replica examples.

A metric should not drive a QC warning if the supposed defect displacement is comparable to normal repeatability error.

## 6. Known-defect validation

After the genuine reference passes the gates above, run a labelled replica/QC set with human-reviewed defects. Keep human labels independent of Watch Align measurements.

Priority defect families:

1. 12 triangle/marker too high or low.
2. 12 triangle lateral displacement or rotation.
3. 6 marker radial displacement or rotation.
4. 9 marker radial/tangential displacement or rotation.
5. individual round-marker radial displacement.
6. asymmetric marker spacing that is visible to human QC reviewers.

For each labelled defect, record which metric should respond before examining its numerical result. This avoids choosing a metric after seeing the answer.

Example mapping:

| Human QC defect | Primary expected signal | Supporting signals |
| --- | --- | --- |
| 12 marker high/low | `h12.centre_r` | `h12.outer_r`, `h12.inner_r`, 12↔6 symmetry |
| 12 marker left/right | `h12.centre_t` | edge tangential residuals |
| 12 marker rotated | `h12.axis_residual_deg` | triangle-specific orientation |
| 6 marker high/low | `h06.centre_r` | `h06.outer_r`, `h06.inner_r`, 12↔6 symmetry |
| 9 marker high/low | `h09.centre_r` | paired/adjacent spacing |
| round marker misplaced | corresponding `centre_r` / `centre_t` | adjacent spacing residuals |

## 7. Decision categories

The validation report should classify each proposed feature as one of:

- **supported**: repeatable on genuine images and separates at least one independently labelled QC defect beyond photographic noise;
- **promising**: repeatable enough, but labelled defect evidence is still too small;
- **diagnostic only**: useful for debugging pose/detection but not defensible as a QC judgment;
- **reject**: unstable, confounded by pose/optics, or no useful separation.

Do not create an overall watch score from these measurements at this stage.

## 8. Immediate acceptance checklist for the current run

When the bounded run finishes:

1. Confirm it completed rather than timed out.
2. Count source pages attempted, successfully fetched and failed.
3. Count downloaded images, pose-success images and retained low-tilt images.
4. Count independent physical watches contributing measurements.
5. Inspect missingness and segmentation confidence by marker.
6. Inspect 12/6/9 distributions first for impossible or multimodal values indicating detector contamination.
7. Compare repeated images of the same watch before interpreting between-watch spread.
8. Only then publish provisional genuine reference statistics.
9. If detector contamination is present, fix/reject the affected measurement path and rerun before using the baseline in the Android QC feature.

## 9. Next deliverable

After the current run, produce `gmt-genuine-baseline-validation-report.md` summarizing source coverage, independent-watch count, detector/repeatability quality, robust genuine distributions, accepted/rejected features, and the exact next replica-control-set experiment.
