# GMT 12 Triangle: Frontal Measurement Milestone

Status: first implementation milestone after the QC architecture reset.

## Question

Can Watch Align accurately and repeatably measure the physical relationships of the GMT 12 o'clock triangle on a high-quality, essentially frontal image without perspective correction?

No perspective, projective radial correction or canonical-dial reconstruction is required to pass this milestone.

## Initial landmark set

The first experiment should locate only what is needed to answer the physical QC question:

- a defined Rolex coronet reference point/edge below the triangle;
- triangle apex;
- triangle base left and right endpoints, allowing base midpoint and width;
- the 60/minute-track reference immediately above the triangle;
- a local 12 o'clock axis derived from directly observed local evidence where needed.

Each landmark definition must be documented visually/numerically before it is used for a baseline.

## Candidate measurements

Do not freeze all of these in advance. Implement the smallest useful set first and retain a candidate only if detection and repeatability support it.

- Triangle apex position within the coronet-to-60 reference interval.
- Triangle base midpoint position within the coronet-to-60 reference interval.
- Triangle height divided by the coronet-to-60 interval.
- Triangle base width divided by triangle height.
- Triangle horizontal displacement from the local 12 axis, normalized by a local scale.
- Left/right triangle symmetry where the segmentation supports a reproducible definition.

## Phase A: deterministic single-image proof

Image suitability is an input contract, not a problem for the measurement engine to solve (see `docs/architecture/QC_PRINCIPLES.md`'s second principle). Reject any candidate outside a realistic QC-photograph pose/visibility envelope explicitly, with a stated reason, rather than spending effort trying to measure it.

Start with one high-quality, near-frontal genuine GMT image with clearly visible coronet, triangle and minute track.

Requirements:

- run landmark detection repeatedly on the identical pixels;
- output the detected landmark coordinates and ratios;
- identical input must produce identical output;
- save a diagnostic overlay showing exactly which physical points generated every ratio;
- do not use perspective correction to improve the result.

A failure here is a detector/measurement problem and must be fixed before moving on.

## Phase B: same-watch frontal repeatability

Use multiple suitable near-frontal photographs of the same physical genuine watch where provenance permits.

Requirements:

- keep the physical watch ID explicit;
- calculate per-feature spread across images;
- inspect overlays for any apparent candidate switching;
- determine whether the measurement spread is small enough to support the eventual QC tolerance.

Do not count these images as independent genuine watches.

## Phase C: independent genuine baseline

Only features that pass A and B proceed to a multi-watch genuine baseline.

Requirements:

- independent physical watches;
- high-quality frontal/near-frontal images;
- provenance retained;
- raw coordinates and ratios retained;
- report median, IQR, robust range/outliers and sample size;
- do not define a production pass/fail tolerance until the sample is adequate.

## Phase D: defect sensitivity

Use known replica/QC examples or controlled synthetic landmark shifts to answer whether the feature can distinguish a physically meaningful defect from genuine variation plus measurement error.

A feature that cannot separate those quantities is not useful for automated QC even if it can be measured precisely.

## Perspective gate

Perspective testing begins only after at least one useful 12-triangle feature passes phases A-D.

The frontal measurement becomes ground truth for the perspective experiment. Off-axis images are judged by how far their measurements move from that reference. Any perspective correction is judged by how much it restores the validated frontal ratios.

## Immediate next task

Build a research-only frontal landmark measurement harness for the 12 o'clock region. Reuse existing detection code only where it directly helps landmark localisation. Do not inherit Stage 3 projective features or correction steps by default.

The harness must emit:

1. source image/watch ID;
2. raw landmark coordinates;
3. explicit landmark confidence/assessability;
4. local dimensionless ratios;
5. a diagnostic overlay;
6. machine-readable CSV/JSON output.

This harness is research instrumentation. It must not alter production Android QC until the milestone evidence supports promotion.
