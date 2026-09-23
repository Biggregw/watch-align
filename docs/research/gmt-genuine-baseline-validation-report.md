# GMT 126710BLNR genuine marker deviation baseline

## Status

The original empirical objective is complete at **provisional-baseline** level: measure independent genuine 126710BLNR watches and quantify observed marker-geometry deviation rather than assuming a single perfect master.

This is **not** a Rolex manufacturing tolerance and must not be used as an authenticity classifier. The primary set contains five independent genuine watches whose selected images passed the <=10 degree pose gate. The broader run measured nine independent genuine listings, but higher-tilt views are excluded from the primary marker baseline.

## Primary empirical deviation

Values are normalized to dial geometry unless explicitly expressed in degrees. `MAD` is median absolute deviation across independent physical watches. p10-p90 is the observed central empirical band from this small genuine sample.

| Feature | n | Genuine median | MAD | p10-p90 | Interpretation |
|---|---:|---:|---:|---:|---|
| 12 apex radial | 5 | 0.5512 | 0.0026 | 0.5493-0.5643 | Tightest useful 12 radial signal |
| 12 centre radial, projective | 4 | 0.7389 | 0.0029 | 0.7351-0.7431 | Promising, one missing projective detection |
| 12 base radial, projective | 4 | 0.8141 | 0.0026 | 0.8112-0.8178 | Promising radial reference |
| 12 axis incidence | 5 | 0.0006 | 0.0118 | -0.0182-0.0202 | Wider photographic/detection variation; diagnostic until replica separation is shown |
| 12 tangential centroid offset | 5 | -0.0015 | 0.0024 | -0.0102-0.0052 | Promising lateral-placement signal |
| 6 centre radial | 5 | 0.6875 | 0.0089 | 0.6491-0.6960 | Detectable but one low-side genuine/detection value broadens band |
| 6 centre tangential | 5 | 0.0049 | 0.0024 | -0.0024-0.0066 | Promising lateral-placement signal |
| 6 axis residual | 5 | 0.7087 deg | 0.5948 deg | -0.1911-4.6968 deg | Too noisy for a hard QC threshold at present |
| 6 radial span | 5 | 0.2430 | 0.0132 | 0.1809-0.2572 | Size/segmentation diagnostic; broad lower tail |
| 9 centre radial | 5 | 0.6871 | 0.0086 | 0.6485-0.6930 | Similar behaviour to 6 radial position |
| 9 centre tangential | 5 | -0.0009 | 0.0025 | -0.0027-0.0090 | Promising lateral-placement signal |
| 9 axis residual | 5 | 0.5197 deg | 0.4062 deg | 0.2220-1.8079 deg | More stable than 6 rotation but still needs replica separation |
| 9 radial span | 5 | 0.2388 | 0.0129 | 0.1814-0.2558 | Size/segmentation diagnostic; broad lower tail |

## What this establishes

1. Genuine watches do **not** collapse to one perfect geometry in photographs. The app must compare a measured watch to an empirical distribution, not to zero residual.
2. The 12 marker's radial geometry is the cleanest current signal. Its robust genuine spread is only about 0.0026-0.0029 dial-normalized units by MAD in the primary sample.
3. Tangential placement at 12, 6 and 9 also has relatively small MAD values, about 0.0024-0.0025 normalized units, and is suitable for blind replica validation.
4. 6-marker rotation is currently too unstable to support a hard finding. A genuine sample already spans a broad upper tail, so rotation must remain inconclusive/diagnostic unless a replica defect clearly exceeds that noise.
5. 9-marker rotation is better behaved than 6-marker rotation but is not yet independently validated as a defect discriminator.
6. Radial span/size has a broad lower tail at 6 and 9, consistent with segmentation sensitivity. It should not be treated as a manufacturing tolerance.
7. Date-centering offset correlates positively with image tilt in the expanded run and remains useful only as supporting pose evidence, not as proof of frontalness.

## Stability check

Expanding the primary sample from four to five independent <=10-degree genuine watches did not materially move the strongest medians:

- 12 apex radial median moved from 0.5537 to 0.5512.
- 12 projective centre moved from 0.7398 to 0.7389.
- 12 projective base moved from 0.8122 to 0.8141.
- 6 centre radial moved from 0.6798 to 0.6875.
- 9 centre radial moved from 0.6792 to 0.6871.

The 12-marker metrics are therefore already comparatively stable. The 6/9 radial distributions remain more sensitive to the small sample and detector/pose effects.

## Integration decision

The empirical profile may now be wired into the Android reference architecture as **provisional research evidence**, with these constraints:

- No overall watch score.
- No authenticity classification.
- No invented Rolex tolerance.
- Do not turn p10/p90 into automatic pass/fail boundaries.
- Preserve detector-confidence and inconclusive states.
- Initially mark 12 radial and 12/6/9 tangential placement as `PROMISING` pending blind replica-control separation.
- Keep 6 axis rotation and radial-span metrics `DIAGNOSTIC_ONLY` until repeatability/replica evidence improves.
- 9 axis rotation remains `PROMISING` only for blind validation, not production warning.

## Next experiment

Run the predeclared RepTimeQC control set blind against this frozen genuine profile. For each labelled defect, evaluate only the metric declared before measurement. A metric becomes `SUPPORTED` only when the labelled replica displacement exceeds genuine spread and ordinary measurement error by a practically meaningful margin.

The Android integration should therefore load this profile without enabling new production pass/fail behaviour yet. The next Claude task is to wire the empirical profile into the existing `GenuineReferenceProfile` / reference-comparison path, expose evidence and inconclusive states, and add the blind control-set runner without changing current production QC findings.
