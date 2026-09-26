# Rehaut perspective experiment — 2026-09-26

Branch: `research/rehaut-perspective`

## Hypothesis
The visible Rolex rehaut is a 3-D depth cue. Camera tilt should make the far-side rehaut surface more visible and the near-side surface less visible. This information is not captured adequately by dial-ring circularity alone.

Use a 360-degree visible-width profile rather than only top/bottom:

- vertical asymmetry = `(top - bottom) / (top + bottom)`
- horizontal asymmetry = `(right - left) / (right + left)`
- first-harmonic amplitude/direction = overall perspective strength/direction candidate

No correction factor or image-rejection threshold is accepted yet.

## Prototype
Added `tools/research/gmt_rehaut_perspective.py`.

Method:
1. reuse the existing Hough circle only as a centre/scale seed;
2. polar-unwrap 360 degrees around the dial;
3. find persistent inner/outer rehaut edges from radial gradients;
4. robustly fit both boundaries with first+second angular harmonics;
5. subtract them to obtain visible rehaut width around the dial;
6. fit the width first harmonic and report top/bottom/left/right widths, 2-D asymmetry, strength, coverage, residual and minimum-width/mean-width.

The module deliberately does **not** alter GMT12 QC, genuine calibration, or Android behaviour.

## Existing-image pilot
First-pass values from the current prototype. Pixel widths are image-specific; normalized asymmetry is the useful cross-image quantity.

| image | top | bottom | left | right | V asym | H asym | harmonic |
|---|---:|---:|---:|---:|---:|---:|---:|
| 33459 | 30.6 | 21.3 | 28.9 | 23.0 | +0.178 | -0.113 | 0.211 |
| 33461 | 22.5 | 22.8 | 21.5 | 23.8 | -0.007 | +0.050 | 0.051 |
| 33492 | 20.0 | 21.6 | 23.3 | 18.3 | -0.039 | -0.122 | 0.128 |
| 1790340784792 | 17.4 | 16.0 | 14.0 | 19.4 | +0.042 | +0.160 | 0.166 |
| 31235 | 14.2 | 20.6 | 16.2 | 18.6 | -0.185 | +0.067 | 0.197 |
| 31245 | 22.9 | 24.1 | 20.9 | 26.1 | -0.025 | +0.111 | 0.114 |

`31253` was intentionally left unmeasured because the provisional edge-pair selection could not constrain it reliably.

### Important observation
33459 and 33461 are especially interesting: 33459 has a strong top/bottom rehaut asymmetry while 33461 is almost vertically symmetric. This is visible in the polar edge overlays as well as in the numeric fit. Therefore the rehaut contains a pose signal that the earlier near-circular ring test did not eliminate.

The Sep-9 Batgirl photographs also show materially different asymmetry vectors across photographs of the same watch, supporting the basic premise that the signal changes with camera pose rather than only with fixed watch construction.

## Why the correction is not a simple percentage
For a shallow 3-D frustum/rehaut model under vertical tilt `a`, a useful first approximation is:

`w_top ≈ dr*cos(a) + h*sin(a)`

`w_bottom ≈ dr*cos(a) - h*sin(a)`

so

`(w_top - w_bottom)/(w_top + w_bottom) ≈ (h/dr)*tan(a)`.

The observed rehaut asymmetry is therefore a sensitive tilt indicator, but it is **not** equal to the planar distortion percentage. The planar 12-gap measurement and the triangle width also foreshorten differently depending on tilt direction. Vertical and horizontal tilt can affect `gap / triangle_width` in opposite directions.

The correct target is therefore:

`rehaut 2-D asymmetry -> estimated pose/local planar Jacobian -> corrected GMT12 geometry`

not:

`top rehaut 50% wider -> add/subtract 50% from clearance`.

## Rejection concept
The same signal can later gate poor photos. Candidate diagnostics already exposed for calibration:

- `min_width_over_mean`
- `edge_coverage`
- `normalized_fit_residual`
- first-harmonic strength

Do not set thresholds from the current small sample. Derive GOOD/CORRECTABLE/REJECT limits from a controlled same-watch tilt series by finding the point where corrected GMT12 measurements stop returning to the same physical geometry.

## Next experiment
Photograph one unchanged GMT at known progressively increasing up/down, left/right and diagonal angles. For every image record:

- rehaut width profile and V/H asymmetry;
- current raw GMT12 top clearance;
- pose-corrected GMT12 top clearance candidate;
- correction residual relative to the same watch's near-frontal baseline.

This directly calibrates the mapping and the rejection threshold without genuine-vs-replica or manufacturing-variation confounding.
