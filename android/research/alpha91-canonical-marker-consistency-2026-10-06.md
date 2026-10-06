# Alpha91 canonical-space master consistency check — 2026-10-06

Research only. Alpha90 (f66acee) is unchanged. Script: `alpha91_canonical_marker_consistency.py`.

## Why this check, and its limits

Neither the four genuine control photos, the bare genuine dial reference, nor the
dense-registration script are in the repository on any branch, and the image hosts
can't be reached from the review environment. So the dense-registration numbers
(2.57 / 1.94 / 2.48 / 1.68 px) and the "+1.32, +1.04 px common offset" **could not be
reproduced** here.

This check uses only the pixel coordinates already embedded in
`alpha91_fairscan_pose_proof.py`. An oracle homography is fitted by least squares to
the eight round-marker centres. Every marker is then inverse-projected into canonical
space.

A common label translation is mostly absorbed by the homography fit. What the check
can test is the ratios between master radii and angles, not absolute pixel accuracy.
It is a diagnostic only, not a solver: it is fitted from the markers themselves.

## Results

| Photo | Round-radius spread | Baton radius (master 0.758) | Triangle "centre" radius (master 0.750) |
|---|---|---:|---:|
| WEX_01 | 0.810–0.822 | 0.7646 | 0.7675 |
| WEX_02 | 0.813–0.819 | 0.7620 | 0.8039 |
| HO_01 | 0.802–0.830 (hours 1 and 2 opposed by about ±2.8 px) | 0.7636 | 0.8053 |
| HO_02 | 0.8152–0.8169 | 0.7633 | 0.7972 |

1. **The 6 and 9 batons sit outward of the master on all four genuine photos.**
   - Measured radius is 0.762–0.765R, against the master's 0.758.
   - In pixels this is about 0.5–1.5 px.
   - The ratio to the round markers repeats from photo to photo, so this is a shared
     master-dimension discrepancy, not annotation noise.
   - Candidate genuine-derived value: about 0.763R.
   - This needs confirming on the bare genuine dial before it changes anything.
2. **The 12 "triangle error" is a definition mismatch, not a perspective failure.**
   - `TRI_CENTER_R=0.750` is the midpoint of the base-to-apex extent.
   - The base sits at 0.902 and the apex at 0.600.
   - The triangle's area centroid is 0.600 + ⅔·0.302 = **0.801R**.
   - Three of the four annotations sit at 0.797–0.805, so they mark the centroid.
   - Holdout comparisons must project the centroid, not `TRI_CENTER_R`.
   - The WEX_01 12 annotation (0.7675) is inconsistent with the other three and
     should be re-annotated before it is used.
3. **The round markers are consistent with one radius, 0.816, and exact 30° axes.**
   - HO_02 residuals are all ≤0.13 px.
4. **HO_01, hours 1 and 2.** The radial errors are equal and opposite (+2.8 / −2.9 px).
   - This is the known bad hour-2 label pulling the least-squares fit.
   - It is not dial-plane geometry. Exclude hour 2, or re-annotate it.
5. **WEX_01, hours 4 and 5.** Once the fit is constrained to the shared master, these
   hours show no special anomaly: 0.35 and 0.20 px radial.
   - This is consistent with the earlier occlusion/annotation explanation.
   - It does not point to a template or perspective fault.

## Consequence for the overlay work

- Two master/holdout definition corrections (baton radius, triangle centroid) explain
  part of the residual that was attributed to registration.
- The remaining question of registration accuracy cannot be answered without the
  pixels: the four control photos at original resolution, the bare genuine dial
  reference, and the dense-registration script.

## Correction — later the same day

The bare genuine dial was later supplied; see `alpha91-overlay-registration-2026-10-06.md`. Rectified, it gives:

- baton centre about 0.755R and round-marker centre about 0.813R, the same ratio as the frozen master;
- triangle area centroid 0.799R.

The "batons outward at about 0.763R" finding above was produced by the biased manual labels and **should not be used**. The triangle-centroid finding stands.
