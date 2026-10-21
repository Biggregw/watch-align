# 12 o'clock triangle: top-edge radial measurement (experimental) — report

Branch `claude/triangle-top-edge-experiment`, 21 Oct 2026. Research only: nothing here changes a verdict, a tolerance,
the classification rules, photo-wide eligibility, the production radial measurement or the reference geometry. The new
reading is computed beside the existing one and shown only as an "Experimental, not used in any result" line in the 12's
measured numbers.

CI run: <https://github.com/Biggregw/watch-align/actions/runs/39623345796> (artifacts `triangle-top-edge-<model>` hold
the CSVs and annotated close-ups, 14 days).

## Method (`TriangleTopEdge.java`)

1. Profiles along the 12's hour line (from the printed minute ticks), every 1 px across the genuine top edge's width
   (15 % trimmed at each corner), from 0.035 R inside the expected edge out to 0.003 R short of the minute-tick inner ends.
2. On each profile the outermost rim-to-dial crossing with MetalFit's rule (half-way between the dial and the rim's own
   level; a profile bright again outside the crossing is rejected).
3. RANSAC line over every pair of edge points (inlier tolerance 0.5 px), least squares on the inliers. A second
   consensus on the remaining points is reported as **ambiguous** when it carries ≥ 50 % as many points and lies > 1 px
   away. Nothing is chosen between them.
4. Measured radius is the line's perpendicular distance from the dial centre, so a sideways move does not change it.
   The reference circle is the genuine top-edge distance × the ring scale. The ring scale is the median measured/genuine
   centre radius of the other round markers (never the triangle); reliable fits are preferred, with at least 3 rounds.
   The rounds' own measured edge offset on that photo is then added, so that the detector's photo-wide edge offset
   cancels. Fixed genuine dimensions; nothing is resized.
5. Not measured (with a plain-words reason) for any of these:
   - fewer than 12 inliers;
   - inliers under 50 % of the usable profiles;
   - inliers spanning under 30 % of the width.

Internal edge-selection parameters: `STEP_PX 1.0`, `PROFILE_STEP_PX 0.25`, `CORNER_FRAC 0.15`, `INSIDE_R 0.035`,
`TRACK_CLEAR_R 0.003`, `MIN_CONTRAST 30`, `RIM_FRAC 0.25`, `RIM_PX 1.5`, `INLIER_PX 0.5`, `MIN_POINTS 12`,
`MIN_FRAC 0.5`, `MIN_SPAN 0.3`, `SECOND_FRAC 0.5`. None of them uses a genuine or replica limit. A unit test checks that
the reading is the same whatever limits are in force.

## Results

### 1. A systematic offset: the new reading is not zero on genuine watches

The measured top edge sits outside the frozen genuine reference's top edge on every genuine set:

| genuine set | photos measured | NEW median (% R) | NEW MAD | NEW p5…p95 | OLD median | OLD MAD | OLD p5…p95 | OLD min…max |
|---|---|---|---|---|---|---|---|---|
| 126610 (Sub Date) | 112 / 112 (1 ambiguous) | **+0.442** | **0.048** | +0.311…+0.589 | +0.001 | 0.060 | −0.214…+0.156 | −1.422…+0.245 |
| 124060 (Sub No-Date) | 113 / 114 | **+0.465** | 0.084 | +0.188…+0.662 | +0.047 | 0.071 | −0.265…+0.221 | −0.535…+0.365 |
| 126710 (GMT) | 63 / 67 | **+0.358** | 0.086 | +0.099…+0.601 | −0.053 | 0.071 | −0.245…+0.272 | −0.346…+0.328 |

- NEW − OLD tracks the difference between the triangle's and the rounds' measured edge offsets. The correlation is
  0.99 on GMT and 0.96 on 124060; on 126610 the offset barely varies (MAD 0.03), so there is little to correlate.
- So the half-level edge of the triangle's thin bright rim lands about 2 px (≈ 0.44 % R) outside the genuine outline's
  top edge, more than the rounds' edge does. MetalFit absorbs this with its per-shape edge bias; the new method
  deliberately does not (the brief says not to recalibrate).
- To be adopted, the new reading would need a genuine-pooled triangle offset, or equivalently new genuine limits for
  it. **That is a recalibration and needs the owner's approval.** The "centred" figures below subtract each model's
  genuine median, for comparison only; that is not applied anywhere.

**Spread on genuine.** Spread is what matters for detecting a move.
- On 126610 the new reading is tighter: MAD 0.048 vs 0.060, p5–p95 width 0.28 vs 0.37, and no gross outlier (range
  0.45 vs 1.67).
- On 124060 and GMT it is about the same as the old reading: MAD 0.084–0.086 vs 0.071, p5–p95 width 0.47–0.50 vs
  0.49–0.52.
- It is not a precision gain everywhere.

### 2. d3a2b077fcf388c3 (Bob's Watches 126610LV, listing 193218)

Provenance (CI):
- `bobs_126610LV_193218`, image `https://www.bobswatches.com/images/zUsed-Rolex-Submariner-126610-SKU193218.jpg`.
- SHA-256 `d3a2b077fcf388c3…2815` matches `date-verified.csv`. 1412 × 2160 px, the highest resolution served.

| | OLD (MetalFit whole-outline centre) | NEW (top edge) |
|---|---|---|
| radial | **−0.617 % R** (fitted top −0.658) | +0.470 % R (2.03 px); raw +0.497 |
| centred on the 126610 genuine median | −0.62 (below genuine p5 −0.21) | **+0.03** (typical) |
| fit quality | edge RMS 1.63 px, 65 % of the seen edge inconsistent, 59 % of the outline seen ("rough") | 78 of 78 usable profiles on one line, RMS 0.14 px, full width, no second edge |
| angle | tilt +0.35° | top edge −0.78° |
| ring | — | scale 1.0005 from 8 reliable rounds; round edge offset 0.12 px |

- The existing whole-outline fit pulls this genuine triangle about 0.6 % R inwards, because the diagonal sides are
  shadowed or reflecting.
- The top edge is clean and lands where the other genuine 126610 watches put it.
- The two angles disagree by 1.1°, the largest difference in the 126610 set. Neither angle has been checked against a
  ground truth on this photo.
- The annotated close-up is `closeups_genuine/d3a2b077fcf388c3_12_*.png` in the `triangle-top-edge-submariner_126610`
  artifact. It shows:
  - yellow: the genuine outline as registered;
  - orange: the existing fit;
  - cyan: the reference arc;
  - green and red: inlier and outlier edge points;
  - green: the fitted line;
  - the numbers.

### 3. Known moves in real genuine photos (`MetalSynth.move`, then the full analysis re-run)

Recovered change minus applied change, mean |error| (max |error|), % R:

| move | 126610 OLD | 126610 NEW | 124060 OLD | 124060 NEW | GMT OLD | GMT NEW |
|---|---|---|---|---|---|---|
| in −1.5 | 0.017 (0.13) | **0.010 (0.03)** | 0.020 (0.06) | 0.020 (0.06) | 0.017 (0.05) | 0.012 (0.05) |
| in −1.0 | 0.011 (0.05) | 0.010 (0.04) | 0.021 (0.12) | 0.031 (**0.47**) | 0.016 (0.07) | 0.015 (0.07) |
| in −0.5 | 0.005 (0.03) | 0.005 (0.02) | 0.019 (0.33) | 0.024 (0.32) | 0.011 (0.05) | 0.013 (0.05) |
| out +0.5 | 0.007 (0.02) | 0.004 (0.02) | 0.037 (1.17) | 0.030 (0.70) | 0.010 (0.04) | 0.010 (0.04) |
| out +1.0 | 0.010 (0.11) | 0.006 (0.02) | 0.040 (1.27) | 0.036 (0.82) | 0.021 (0.08) | 0.018 (0.11) |
| out +1.5 | 0.011 (0.07) | 0.031 (0.10) | 0.263 (1.58) | 0.067 (1.56); 2/40 not measured | 0.244 (1.73) | **0.049 (0.52)** |
| side ±0.8 | 0.011–0.019 (0.36) | **0.007 (0.02)** | 0.006–0.013 (0.05) | 0.008–0.011 (0.06) | 0.020–0.028 (0.35) | **0.006–0.008 (0.03)** |
| turn ±1.5 radial | 0.009–0.010 (0.10) | 0.019–0.029 (0.06) | 0.009–0.011 (0.07) | 0.017–0.024 (0.10) | 0.011 (0.05) | 0.023–0.027 (0.11) |
| turn ±1.5 angle (°) | 0.035–0.072 (0.85) | **0.031–0.034 (0.14)** | **0.042–0.056 (0.59)** | 0.147–0.235 (**1.14**) | 0.068–0.076 (0.87) | 0.097–0.145 (0.60) |

Successes:
- The new reading is never fooled by a sideways move (max 0.06 % R, against up to 0.36 for the old reading).
- Large outward moves are better recovered: at +1.5 the mean error is 0.05–0.07 against 0.24–0.26 for the old reading.
- Top-edge angles on 126610 are accurate.

Failures:
- On 124060 a few photos give 0.3–0.8 % R errors on ±0.5–1.0 moves; the old reading has the same photos wrong too.
- The 124060 turn angle is worse than the old rotation (mean 0.15–0.24°, max 1.14°).
- Turns add about 0.02 % R of radial bias.
- At +1.5 outward, 2 of 40 124060 photos are not measured because the edge reaches the minute track. On the owner's
  small-gap replica (local test), 2 of 3 photos were not measured.

### 4. Replica / QC photos (centred figures subtract each model's genuine median; not applied)

| photo | existing state | OLD radial | NEW raw (centred) | NEW angle / OLD tilt |
|---|---|---|---|---|
| 124060 control12 | Clearly off | −0.48 | +0.17 (**−0.30**) | +0.16 / +0.32 |
| 124060 rejected12 | Possible | +0.33 (rough 0.84 px) | +0.62 (+0.16) | +1.91 / +1.26 |
| 124060 accepted12 | No visible issue | +0.55 | +0.81 (+0.35) | −1.10 / −0.97 |
| 124060 accepted12 photo5 | No visible issue | +0.52 | +0.79 (+0.33) | −0.93 / −1.02 |
| GMT accepted6 | Clearly off | +0.85 | +0.95 (**+0.59**) | +0.36 / +0.46 |
| GMT accepted6 photo2 | Clearly off | +0.64 | +0.55 (+0.19) | +0.17 / +0.64 |
| GMT crooked6 | No visible issue | −0.40 | −0.12 (−0.48) | −0.67 / −0.64 |
| Reddit 126610 replica (local, 2 crops) | Replica (whole watch) | +0.17 / +0.43 | +0.36 / +0.40 (**−0.08 / −0.04**) | ≈ −0.1…+0.2 / −1.44…−1.60 |

- The direction agrees with the old reading in every case.
- The centred magnitudes are mostly smaller than the old ones. On GMT accepted6 photo2 the radial difference falls from
  +0.64 to +0.19.
- On the Reddit replica the top edge is at the genuine radius and hardly turned, while the old fit reports a 1.4–1.6°
  rotation. The rotation comes from the diagonal sides fitting badly, i.e. a triangle shape that differs from genuine.
  The top-edge method measures only the top edge, so it **does not see a shape difference**. If adopted for the radial
  reading, it would not replace the existing shape and rotation checks.

### 5. Hand, reflections, lume boundary, partial edge (unit tests, `TriangleTopEdgeTest`)

All pass, 12 tests; the suite has 512.

Successes:
- A hand across a quarter of the edge is left out by the consensus, and a true 1 % move is still recovered within
  0.05 % R.
- A bevel highlight just inside the edge moves the reading under 0.1 % R.
- A shadowed rim (120 against lume 230) is read at the rim, not at the lume boundary 0.8 % R inside (±0.12).
- With 40 % of the edge visible it is measured (±0.05); with 15 % it is not measured, and says why.
- A reflection strip outside the edge is reported as AMBIGUOUS, with the second edge's offset (≈ 5.6 px), not chosen.

Known failure, kept as a test:
- A dim rim reads too far in: rim 90 gives −0.23 % R (0.9 px).
- At rim 60, under a quarter of the contrast, the lume boundary is read instead.
- MetalFit's edge rule shares this limitation.

Real photos:
- 1 of 112 genuine 126610 photos was flagged ambiguous.
- 4 of 67 GMT photos were not measured: the points did not agree on one line.
- 1 of 114 124060 photos was not measured: its hour line could not be placed.

### 6. Is the edge demonstrably the outer metal?

- On the owner's genuine Sub Date photo (local close-up), the fitted line lies on the outer edge of the thin bright
  metal rim. The yellow genuine outline lies about 1 px inside it; that offset is the one in §1.
- The CI close-ups for d3a2b077 and the genuine/QC disagreements are in the artifacts for the owner to review. I could
  not view them from this environment.

## Conclusion and recommendation

- **Measurement:** the top edge is a cleaner and more robust radial reference than the whole-outline centre, in these
  cases:
  - d3a2b077: OLD −0.62 → NEW +0.03 centred;
  - sideways moves;
  - large outward moves;
  - tighter genuine spread on 126610.
  It is not more precise on 124060 or GMT genuine photos, its angle is worse on 124060, and it is blind to triangle
  shape.
- **It cannot be used as it stands:** it reads +0.36…+0.47 % R on genuine watches. Adoption would need the owner to
  approve a triangle-specific genuine offset (or limits) derived from the pooled genuine sets above. That is a
  recalibration and is not done here.
- **Suggested next step, if the owner wants it:**
  1. review the d3a2b077 close-up and a sample of the genuine close-ups in the artifacts;
  2. then decide whether to calibrate the top-edge reading as a second, independent radial check for the 12 only;
  3. keep the existing fit for side, rotation and shape.
- No production change is recommended before that review.
