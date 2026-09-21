# Radial QC safe operating area - 2026-09-21

## Purpose

This is a provisional measurement-quality study using the fast local cache from the current accepted corpus. It does not change production QC.

For each physical watch and marker with at least two measured photos, the watch's own median marker measurement is used as the repeatability reference. Each image contributes an absolute residual from that watch/marker median. These residuals are repeated observations, not independent watches, so the watch counts are shown alongside the marker-observation counts.

## Current evidence by apparent tilt

| Tilt group | Radial marker observations | Watches | Median radial residual | P75 radial residual | Angular marker observations | Watches | Median angular residual |
|---|---:|---:|---:|---:|---:|---:|---:|
| <=10 deg | 360 | 18 | 0.211 %R | 1.091 %R | 360 | 18 | 0.250 deg |
| >10 deg | 86 | 10 | 1.131 %R | 2.782 %R | 86 | 10 | 0.499 deg |
| >12 deg | 50 | 7 | 1.364 %R | 2.771 %R | 50 | 7 | 0.556 deg |
| >15 deg | 16 | 3 | 2.473 %R | 3.978 %R | 16 | 3 | 0.284 deg |

The >15 degree angular number is based on only three watches and should not be interpreted as proof that angular QC becomes better at high tilt.

The main signal is radial:
- above 10 degrees, median radial repeatability error is about 5.4x the <=10 degree level,
- above 15 degrees, it is about 11.7x the <=10 degree level.

The 10 to 15 degree region is relatively sparse and not monotonic enough to justify a finely tuned numerical cutoff from this corpus alone.

## Marker-specific observation

Using the same within-watch residual method, the >10 degree median radial residual is materially elevated across several markers, not only the upper-dial group:

| Hour marker | <=10 deg median radial residual | >10 deg median radial residual |
|---:|---:|---:|
| 1 | 0.192 %R | 1.149 %R |
| 2 | 0.198 %R | 1.159 %R |
| 4 | 0.352 %R | 1.082 %R |
| 5 | 0.311 %R | 2.376 %R |
| 6 | 0.130 %R | 2.045 %R |
| 7 | 0.385 %R | 0.703 %R |
| 8 | 0.141 %R | 2.607 %R |
| 9 | 0.150 %R | 0.026 %R |
| 10 | 0.137 %R | 1.065 %R |
| 11 | 0.359 %R | 0.723 %R |
| 12 | 0.151 %R | 0.570 %R |

Do not over-read individual rows with small high-tilt sample sizes. In particular, marker 9's low median does not mean it is proven tilt-safe; its broader distribution still contains large residuals. The useful conclusion is that radial degradation is distributed across the dial.

## Provisional operating policy for research

This is a working research policy, not a production threshold:

- **Green, <=10 degrees apparent tilt:** radial QC is within the best-supported part of the current corpus. Continue to apply the existing per-marker measurability/sanity gates.
- **Amber, >10 to 15 degrees:** radial values should be treated as advisory. Do not use them to establish new production QC thresholds until this region has more independent-watch coverage.
- **Red, >15 degrees:** suppress radial pass/fail decisions and ask for a straighter photo. The current evidence shows a large radial-repeatability penalty here.
- **Angular QC:** more robust than radial overall, but high-tilt evidence is too sparse to declare it universally safe. Keep its existing sanity gates and validate marker-specific behaviour before using high-tilt angular results for automatic classification.

The exact 10 degree and 15 degree boundaries are provisional. They are useful engineering guardrails from the current corpus, not model constants that should be hard-coded without further validation.

## What would justify production gating

Before implementing a tilt gate in Android/Python production QC:
1. add more independent accepted watches in the 10 to 25 degree range, especially genuine candidates,
2. keep validation watches held out,
3. confirm the same radial degradation using watch-level summaries rather than image-count significance,
4. verify visually that the measured marker component is the intended lume/marker body in the high-residual cases,
5. test the proposed gate against false suppression on otherwise usable photos.

For now, the safest product behaviour would be to prefer a straighter capture rather than attempting to mathematically rescue radial QC at extreme tilt.
