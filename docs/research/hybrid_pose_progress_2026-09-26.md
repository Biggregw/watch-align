# Hybrid pose estimator progress — 2026-09-26

Branch: `research/rehaut-perspective`

## What changed
Added `tools/research/gmt_hybrid_pose.py`.

The research estimator combines:

- rehaut 360-degree visible-width asymmetry for signed pose direction and severe-angle detection;
- an independent ellipse fit to an annular dial/rehaut boundary for planar foreshortening magnitude;
- 12-marker orientation, where available, to rotate the rehaut vector into watch-relative 12/6 and 3/9 coordinates;
- an experimental cue-consistency check;
- research-only labels: `GOOD`, `CORRECTABLE`, `RETAKE`, `UNASSESSABLE`.

No GMT12 numeric correction, genuine calibration band, Android behaviour, or production threshold has been changed.

## Provisional research gate
Current control-set-only behaviour is intentionally conservative:

- `RETAKE` when the minimum fitted rehaut width drops below 50% of mean width;
- `RETAKE` when rehaut fit residual exceeds 0.16;
- `RETAKE` when the ellipse implies at least ~14 degrees of planar tilt;
- `CORRECTABLE` for intermediate rehaut compression (<0.80 min/mean), strong first-harmonic asymmetry (>=0.15), or moderate ellipse tilt (>=8 degrees);
- otherwise `GOOD` when the evidence is well constrained;
- contradictory strong rehaut/ellipse axes become `UNASSESSABLE`, not a forced correction.

These are deliberately marked experimental and must be recalibrated from a known-angle GMT series before production use.

## Submariner 124060 blind check
The 11-image same-watch control set was relabelled from the already-measured rehaut diagnostics. Results are saved at `datasets/rehaut_tilt_series/sub124060_hybrid_labels_2026-09-26.csv`.

Experimental labels:

- GOOD: 34094, 34095, 34097, 34098, 34100, 34103, 34104
- CORRECTABLE: 34096
- RETAKE: 34099, 34101, 34102

After the measurements were produced, the photographer confirmed that 34101 and 34102 were among the deliberately furthest-off-axis captures. This is an independent qualitative validation of the rehaut collapse signal. 34099 is also conservatively labelled RETAKE because its rehaut fit is much less stable than the near-frontal group.

## Important limitation exposed
The full-phone-photo path still needs a robust watch ROI/scale stage before the hybrid estimator can be used as a one-call production component. The existing rehaut prototype can choose the wrong Hough ring when the dial occupies only a small part of a large phone image. The same-watch analysis avoided that by localising the watch region first.

Therefore the next engineering task should be a fail-closed ROI/scale validator before integrating the hybrid pose logic into Android.

## Next steps
1. Add a research `WatchPoseRoi` stage that finds a plausible dial region at multiple scales and verifies it using dark dial interior + annular edge evidence + 12-marker position.
2. Run the complete hybrid estimator end-to-end on the 11 Sub images with no hand-selected crop.
3. Run the same code against the Batgirl video sample set.
4. When the GMT is available, capture known-angle stills and replace the provisional GOOD/CORRECTABLE/RETAKE boundaries with empirical ones.
5. Only then test a numeric local-Jacobian correction for GMT12 clearance.
