# Proportional Geometry - Calibration Statistics Protocol

Status: live supplemental research

Date: 2026-09-23

Purpose: define how proportional-geometry calibration should treat repeated photographs, physical-watch independence, viewpoint variation and small genuine-candidate samples. This is intended to prevent apparently tight bands that are really caused by many photos of only one or two watches.

## 1. Physical watch is the primary statistical unit

A dealer or marketplace album may contain many photographs of one watch. Those images are useful for repeatability and viewpoint testing but they do not increase the independent genuine-watch population size.

Therefore maintain two levels of data:

### Image level

Use for:

- segmentation coverage,
- pose/viewpoint sensitivity,
- same-watch repeatability,
- multi-view sign consistency,
- failure analysis.

### Physical-watch level

Use for:

- genuine population centre,
- between-watch spread,
- candidate geometry bands,
- leave-one-watch-out stability.

Do not calculate final calibration percentiles by pooling all images as though they are independent.

## 2. Equal-weight watch summaries

For each feature `f` and physical watch `w`:

1. retain only supported measurements,
2. compute a robust watch-level centre, preferably median,
3. record within-watch spread separately,
4. carry one watch-level centre into the population summary.

This prevents a watch with ten usable photos from dominating a watch with one or two usable photos.

Recommended fields:

- `physical_watch_id`
- `feature`
- `n_images_usable`
- `watch_median`
- `watch_MAD`
- `tilt_min`
- `tilt_max`
- `coverage_fraction`
- `sign_consistency` where relevant.

## 3. Decompose measurement noise from genuine between-watch variation

For each feature, report both:

### Within-watch repeatability

Typical absolute deviation of image measurements from that watch's median.

This estimates the image/pose/segmentation noise floor.

### Between-watch variation

Robust spread of the independent watch medians.

A feature is useful only if meaningful between-watch departures can be distinguished from its own measurement noise.

A useful research ratio is:

`between_watch_MAD / median_within_watch_MAD`

Interpret cautiously:

- very small ratio can mean the feature is dominated by measurement noise,
- very large ratio can mean either true production variation or source/measurement bias,
- a tight between-watch distribution with low within-watch error is ideal for a geometry fingerprint.

Do not convert this into a production score yet.

## 4. Leave-one-watch-out stability

For every candidate feature, repeatedly remove one physical watch and recompute the calibration centre/spread.

Report:

- maximum median shift,
- maximum MAD shift,
- whether feature ranking changes,
- whether the current user's qualitative position relative to the band would depend on one particular calibration watch. Do not actually inspect the user's watch until the genuine profile is frozen; this last check belongs later.

A feature whose calibration band changes dramatically when one watch is removed is not mature enough for a strong rule.

This is especially important with 8-12 watches, where one unusual example can still influence the result materially.

## 5. Small-sample language

Suggested interpretation of independent-watch counts:

- `<5 watches`: feasibility only,
- `5-7 watches`: exploratory calibration, too sparse for strong range claims,
- `8-12 watches`: useful early research distribution if provenance and viewpoint coverage are good, but still not a validated manufacturing tolerance,
- `>12 watches`: progressively better, but quality/diversity matter more than a numerical cutoff.

These are research guardrails, not statistical laws.

Do not describe an 8-watch central range as a Rolex manufacturing tolerance.

## 6. Use robust summaries, not mean +/- standard deviation by default

Recommended population summaries of physical-watch medians:

- median,
- MAD,
- p10/p90 where sample size makes those quantiles meaningful,
- min/max for context only,
- leave-one-watch-out range of the median/MAD.

For very small samples, quantiles can look more precise than the data justify. Always show `n_watches` next to them.

## 7. Hierarchical bootstrap for later uncertainty estimates

If confidence intervals are needed later, use a hierarchical/watch-level bootstrap rather than resampling images independently.

Recommended bootstrap structure:

1. sample physical watches with replacement,
2. optionally sample usable images within each selected watch,
3. recompute watch medians,
4. recompute population median/spread.

This preserves the repeated-measures structure.

Do not use a simple image bootstrap because it treats near-duplicate views as independent evidence.

## 8. Viewpoint coverage is part of feature qualification

For each feature report coverage in tilt bands, for example:

- <=5 deg,
- >5 to 10 deg,
- >10 to 15 deg,
- >15 deg.

Do not declare a feature viewpoint-robust if nearly all successful measurements happen to come from frontal images.

A useful table should include:

- attempted images,
- usable images,
- independent watches represented,
- median tilt of usable images,
- failure rate by tilt band,
- repeatability by tilt band.

Coverage and stability must be considered together.

## 9. Missingness can bias the apparent genuine distribution

If difficult images or unusual marker geometry are disproportionately rejected by segmentation/pose gates, the surviving measurements can appear artificially tight.

For each feature, compare rejected versus accepted images on observable factors such as:

- tilt,
- resolution,
- blur,
- hand occlusion,
- marker visibility,
- source/watch identity.

If one physical watch has no usable measurements for a feature, do not silently remove it from the narrative. Record that the feature failed on that watch.

A feature with beautiful spread but only 40% watch coverage may be less useful than a slightly noisier feature with 95% coverage.

## 10. Source diversity and provenance

The calibration set should avoid being dominated by one seller, one photographic style or one production period where practical.

Track:

- provenance source,
- year where known,
- bracelet configuration where recorded,
- source album/listing,
- image style/background.

Bracelet type is not expected to affect dial proportions, but tracking it helps detect accidental source clustering and duplicate watches.

The official Rolex catalogue image remains a sanity/control reference, not an independent population watch.

## 11. Duplicate/near-duplicate protection

Before counting new calibration watches as independent, inspect for accidental duplicates across listings or mirrored/reposted albums.

Useful checks:

- same serial/tag/card clues where visible and ethically appropriate,
- identical scratches/dust patterns,
- identical hand positions across supposedly independent listings,
- perceptual image similarity,
- identical metadata/source URLs,
- identical bezel/dial micro-features.

Do not use any private identifying data beyond what is already public in the research source. The purpose is only to avoid double-counting the same physical watch.

## 12. Feature ranking should include coverage and uncertainty

A candidate feature ranking should not be based only on narrow spread.

Suggested qualitative factors:

1. watch-level coverage,
2. image-level coverage,
3. median within-watch repeatability error,
4. between-watch spread,
5. tilt correlation,
6. leave-one-watch-out stability,
7. segmentation/reference confidence,
8. physical interpretability,
9. redundancy with already retained features.

A feature can be rejected for poor coverage even if its successful measurements are very tight.

## 13. Multi-view sign consistency as evidence

For signed residuals such as outward/inward or CW/CCW:

- compute each usable image independently,
- map to canonical signed direction,
- report fraction of views sharing the watch-level sign.

A real physical displacement should tend to preserve sign across supported viewpoints.

If sign flips repeatedly within one watch, suspect measurement/pose instability before concluding that the physical geometry varies.

This is particularly relevant to Reddit-style borderline claims such as slight 12 CCW tilt or 6-left placement.

## 14. Calibration profile should store uncertainty metadata

A machine-readable genuine profile should eventually store more than a median/range.

For each feature include at least:

- `n_watches`
- `n_images`
- `watch_median`
- `watch_MAD`
- `median_within_watch_error`
- `coverage_watch_fraction`
- `coverage_image_fraction`
- `supported_tilt_range`
- `leave_one_watch_out_median_span`
- `measurement_method_version`
- `source/provenance note`

This will allow later rule logic to know when the baseline itself is weak.

## 15. Current recommendation for the GMT expansion phase

The immediate target of 8-12 independent usable calibration GMTs is appropriate as an early research milestone, but the resulting band should still be labelled exploratory.

Before comparing an unseen replica strongly against it, require at minimum:

- enough independent watches for the selected feature,
- acceptable watch-level coverage,
- within-watch noise materially smaller than the deviation being discussed,
- no severe leave-one-watch-out instability,
- supported viewpoint/tilt,
- frozen feature definition and measurement version.

Only after those conditions are met should the validation genuine set be opened for a genuine false-positive test.
