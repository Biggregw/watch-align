# Opposing minute-marker perspective experiment — 2026-10-04

Status: **synthetic geometry control only; no production change**

Branch: `research/opposing-minute-perspective`

## Question

Can opposing minute markers provide a better perspective signal than the small set of opposing hour markers used in GMT calibration v0.3, and can they specifically recover the projective component that the earlier ellipse-only pose work could not determine?

## Why this is different from the v0.3 symmetry score

The v0.3 control compared a small number of opposing hour-marker centre/inset measurements and found a useful directional tendency, but not a stable correction formula. It therefore correctly refused to change GMT production calibration.

The minute track gives a stronger construction. Excluding the 12 five-minute/hour positions leaves 48 minor minute ticks, which form **24 clean opposing pairs**. Each pair is intended to represent two physical marks at the same true radius on the same dial plane.

## Geometry

After translation to the dial centre and after the affine part of pose has been handled, the remaining projective distortion can be written in normalized dial coordinates as:

```text
y = x / (1 + c·x)
```

For opposite points `x` and `-x` at the same true radius:

```text
k = |y(x)| / |y(-x)|
  = (1 - c·x) / (1 + c·x)
```

Therefore:

```text
c·x = (1 - k) / (1 + k)
```

Each opposing pair supplies one equation for the two unknown projective components. Twenty-four minor-tick pairs therefore give an overdetermined system that can be fitted robustly and can down-weight isolated bad detections.

This is directly relevant to the earlier synthetic homography failure: ellipse/vector correction dealt with the affine/conic geometry but the missing `h31/h32` projective terms remained unresolved. Opposing equal-radius pairs contain information about exactly those terms.

## Synthetic control

Reproducible script:

`tools/research/opposing_minute_perspective.py`

The control deliberately adds:

- random projective direction;
- projective magnitude from 0 to 0.12 in normalized coordinates;
- Gaussian point noise of 0.0015 dial radii;
- two deliberately corrupted minute points per trial;
- 5,000 trials with a fixed random seed.

It compares:

1. all 24 non-hour opposing minute-tick pairs;
2. four hour-marker-style pairs equivalent to 1↔7, 2↔8, 4↔10 and 5↔11.

Result from the fixed 2026-10-04 run:

```text
projective-vector absolute error (median / p95 / p99)
24 minor-tick pairs: 0.000416 / 0.000880 / 0.001086
 4 hour-marker pairs: 0.001047 / 0.005818 / 0.010002

true local radial gap: 0.120000R
raw gap:       mean=0.120801R sd=0.011277R
corrected gap: mean=0.120064R sd=0.002127R
spread reduction: 81.1%
```

## Interpretation

This is a strong **mathematical/synthetic pass**, not evidence that the real-photo detector is ready.

The important findings are:

- opposing minor minute ticks recover the injected projective vector exactly in the noise-free case;
- with realistic synthetic noise and two bad points, the 24-pair fit is materially more robust than the four-pair hour-marker fit;
- the recovered projective term reduces the synthetic spread of a known local radial gap by about 81%;
- isolated bad ticks do not have to be interpreted as camera pose because the fit is overdetermined and can use robust residuals;
- this provides a plausible route to complement, rather than replace, the existing ellipse/affine normalization.

## Important measurement rule

Do **not** compare the existing production `12 gap` and `6 gap` values directly.

They are currently normalized by different marker widths:

- 12 clearance is divided by triangle width;
- 6 clearance is divided by baton width.

The 6 detector already calculates a real gap, but the September batch CSV does not export it. For perspective research, both local clearances must instead be expressed in a common coordinate system, preferably dial-radius or minute-track-radius units.

## Real-photo experiment required next

Keep production GMT untouched. Add a research-only minute-track measurement/export that records, for each usable minor tick:

- minute index 0–59;
- detected point used for the common radial definition;
- radius from the fitted dial centre;
- detection confidence / inferred status;
- resize-repeatability result where practical.

Exclude the 12 five-minute/hour positions for the first test. Pair the remaining ticks `i ↔ i+30` and fit the two-component projective vector robustly.

Then test on existing genuine GMT evidence:

1. whether pair residuals form the predicted smooth first-harmonic pattern;
2. whether projective correction reduces photo-to-photo spread of stable genuine measurements;
3. whether the gain survives a held-out watch partition;
4. whether local deliberately bad-marker examples remain local outliers rather than being absorbed into the pose correction;
5. whether correction magnitude agrees in direction with the existing 12↔6 and neighbourhood evidence.

## Decision gate

Only promote this beyond research if real-photo genuine repeatability improves on held-out watches without hiding isolated defects.

If it passes, the likely architecture is:

**ellipse/conic normalization for affine pose + opposing-minute-pair fit for residual projective terms + normal genuine-reference measurement comparison.**

No production threshold or APK behaviour should change before that gate passes.
