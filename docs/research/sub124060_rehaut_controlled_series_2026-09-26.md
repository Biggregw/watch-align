# Submariner 124060 same-watch rehaut perspective series — 2026-09-26

Source: 11 user-supplied still photographs (`34094.jpg` through `34104.jpg`) of the same unchanged 124060 Submariner. The watch remained physically identical; camera position/angle changed. Images are not committed to the repository.

## Purpose
Test whether visible rehaut geometry adds a useful camera-pose signal before a GMT-specific known-angle series is available.

This is research-only. No production QC thresholds, genuine bands, or Android behaviour are changed.

## Important preprocessing lesson
The existing GMT rehaut prototype assumes the dial occupies a large crop. Running it directly on the full phone photographs can make the Hough seed lock onto the wrong outer ring. For this series the analysis first localized the watch/dial region, then refined scale from the detected 12-marker geometry before analysing the rehaut. Any production implementation must perform equivalent scale/ROI validation rather than trust the first circle blindly.

## Rehaut results
The refined same-watch analysis produced a 360-degree visible-width profile for all 11 images. `V` is top-vs-bottom asymmetry, `H` is right-vs-left asymmetry. `min/mean` is the narrowest fitted rehaut section divided by mean visible width.

| image | V | H | harmonic strength | min/mean | fit residual |
|---|---:|---:|---:|---:|---:|
| 34094 | -0.039 | +0.046 | 0.060 | 0.906 | 0.030 |
| 34095 | -0.066 | +0.044 | 0.079 | 0.904 | 0.030 |
| 34096 | +0.254 | +0.076 | 0.266 | 0.630 | 0.045 |
| 34097 | -0.034 | +0.052 | 0.062 | 0.901 | 0.032 |
| 34098 | -0.024 | +0.066 | 0.070 | 0.895 | 0.020 |
| 34099 | +0.373 | +0.116 | 0.391 | 0.528 | 0.180 |
| 34100 | -0.032 | +0.064 | 0.072 | 0.903 | 0.036 |
| 34101 | -0.340 | +0.249 | 0.422 | 0.231 | 0.129 |
| 34102 | -0.109 | -0.113 | 0.157 | 0.474 | 0.068 |
| 34103 | ~0.000 | +0.092 | 0.092 | 0.876 | 0.025 |
| 34104 | -0.011 | +0.048 | 0.050 | 0.920 | 0.025 |

The signal clearly separates some frames. In particular, 34101 has a section of rehaut collapsing to roughly 23% of mean fitted width; 34102 falls below 48%. The full 360-degree profile is necessary because diagonal tilt can make the minimum occur away from the cardinal 12/3/6/9 positions.

## Independent planar-angle check
The inner circular boundary was also fitted as an ellipse as a separate, weaker planar perspective cue. Approximate axis ratios / unsigned tilt estimates were:

- most images: ellipse ratio about 0.991–1.000 (roughly 2–8 degrees under the simple circular-plane approximation);
- 34101: ratio 0.9621, about 15.8 degrees;
- 34102: ratio 0.9618, about 15.9 degrees.

Across all 11 images the rehaut first-harmonic strength had a moderate positive association with the ellipse-derived tilt magnitude (sample correlation about 0.60). This is not enough to use rehaut strength alone as a calibrated angle meter, but it supports the claim that the visible-depth signal contains pose information.

## Critical correction finding
The raw 12-marker clearance detector varied from roughly 0.134 to 0.206 on this same physical watch, including substantial spread among near-frontal frames. That spread is much larger than the planar foreshortening expected from these camera angles and indicates that current landmark/tick repeatability is still a major confounder.

A planar SIFT/RANSAC homography between the same-watch dial images showed local radial-vs-tangential anisotropy of only about 0–2% for this series. This is consistent with the simple geometry: even a ~16 degree plane tilt gives only ~4% cosine foreshortening. Therefore a rule such as “top rehaut is 50% wider, so correct the 12 gap by 50%” would massively over-correct.

## Current conclusion
The rehaut idea **adds real value now**, primarily as:

1. an independent pose-direction/depth cue that a near-circular dial alone does not provide;
2. an image-quality gate, because severe/diagonal angle causes one section of the visible rehaut to collapse;
3. a likely sign/direction cue for a later perspective correction.

It is **not yet validated as a standalone correction magnitude**. The most promising correction architecture is hybrid:

`rehaut 360-degree profile -> far-side direction + reject/confidence`

combined with

`dial ellipse / planar homography -> foreshortening magnitude`

then apply the resulting local planar Jacobian to the GMT12 geometry.

## Provisional separation observed, not a production threshold
This tiny set suggests a natural qualitative separation:

- high `min/mean` (~0.85–0.92) with low residual: good/near-frontal candidates;
- around `min/mean` ~0.63 with a clean fit: potentially correctable;
- `min/mean` below ~0.5, or low width plus a poor fit: strong retake/reject candidates.

Do **not** hard-code these values from 11 images. Use them only to design the next validation.

## Next step
Implement a research-only hybrid pose estimator that reports:

- watch-relative rehaut V/H and 360-degree minimum-width location;
- ellipse axis ratio and tilt-axis orientation;
- consistency between the two cues;
- GOOD / CORRECTABLE / RETAKE as experimental labels only;
- no GMT12 numeric correction unless cue consistency is high.

Then rerun this 11-image Sub set and the existing Batgirl video. When the GMT is available, repeat with deliberate known-angle stills and use those to calibrate the correction magnitude and final retake boundary.
