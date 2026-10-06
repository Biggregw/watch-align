# Alpha91 applied-marker residual measurement — 2026-10-06

This is research only. Alpha90 is not touched, nothing was merged, and no pass/fail tolerances are introduced.

Files:
- Script: `alpha91_marker_measurement.py`
- Outputs in `alpha91-marker-measurement-results/`:
  - `marker_residuals.csv`, all photos
  - `marker_residuals_<photo>.csv` and `.json`
  - `genuine_ranges.json`
  - `markers_<photo>.jpg`, per-marker diagnostic crops
  - `residuals_<photo>.jpg`, full dial with measured outlines and ×10 residual vectors
  - `run_log.txt`

Inputs: the supplied fixture pack `alpha91_marker_measurement_inputs` (4 genuine controls, plus `RL_LOCAL_BLNR` and `RL_ARF_BLRO_CROOKED6`) and the bare genuine dial crop. The images are not committed.

```
python3 android/research/alpha91_marker_measurement.py --inputs <alpha91_marker_measurement_inputs> \
  --bare <bare_genuine_dial_reference_crop.png> --out android/research/alpha91-marker-measurement-results
```

## Order of operations (H is frozen before any marker is measured)

1. H comes only from the frozen minute-lattice solver (`alpha91_overlay_registration.register`). All six photos were accepted. After registration, H is set read-only.
2. Applied markers are measured against the bare-dial master in H-rectified coordinates. No marker value feeds back into H. The old hand-placed centres are not used.
3. Raised-marker parallax is a separate column. It is one genuine-derived height: pooled 0.020R, applied leave-one-photo-out on the genuine photos. It never changes H.
4. **Ring decomposition, also separate.** All clean marker centres are fitted together with one common translation, one uniform scale and one rotation about the dial centre. That is the marker ring relative to the minute track. Each marker is then reported as **raw** (vs the master through H) and as **local** (after removing the ring terms).

## One solver-side change, made only because of a reproducible failure

`RL_ARF_BLRO_CROOKED6` was falsely rejected by the fail-closed RMS gate.
- Tick median 0.108 px, but RMS 0.684 px.
- The cause was a single tick (25) crossed by the GMT-hand tip, at 4.4 px. The sector crop shows the lattice otherwise locked.
- The gate now computes RMS after setting aside gross single-tick outliers (more than max(1 px, 6 × median)), and rejects if more than 10 % of ticks are gross outliers.
- **H is computed exactly as before.** The genuine-control H matrices are unchanged after the change (largest element difference 6e-7); all four are still accepted with the same holdouts.
- The known wrong fits are still rejected: the ellipse-only seeds on WEX1 and WEX2 have a 0.9–1.0 px median.

## Marker measurement method

- **Edge rule:** the outer metal outline, i.e. the marker footprint, taken as the **outermost** significant bright→dark transition. Taking the strongest one instead jumped to the inner ring/lume edge on the shadowed side of raised markers.
- **Two passes:** a wide search (±0.035R) around the master, a robust fit, then a narrow re-search (±0.012R) around the **fitted** outline. The result follows the real marker, not the master.
- **Round markers:** RANSAC + least-squares circle. Gives centre, radius, and circularity (ellipse axis ratio, perspective-corrected).
- **6/9 batons:** four freely fitted sides (RANSAC line + TLS). Gives the polygon area centroid, long-axis rotation, long-side parallelism, and length/width.
- **12 triangle:** three freely fitted sides. Gives the area centroid, centreline rotation (apex to base midpoint), left/right side-angle errors and their mismatch, base tilt, base width and height.
- **Occlusion rule: OCCLUDED / INSUFFICIENT CLEAN EDGE.** A marker is not measured when any of these hold:
  - the outline fails the integrity test: along the fitted outline the inside (lume) band must be bright and the outside band dial-dark, and fewer than 80 % of samples pass (per side for polygons);
  - edge coverage is below 0.60 of rays (rounds) or below 0.50 of samples on any side (polygons);
  - the fit is physically implausible.

  Integrity scores separate cleanly on these photos:
  - visibly hand-crossed markers: 0.37–0.76;
  - clean markers: 0.83–1.00.

## Results

### Genuine controls: a stable, tight normal range

| | WEX1 | WEX2 | HO1 | HO2 |
|---|---|---|---|---|
| Clean markers measured | 9 | 9 | 9 | 11 |
| Occluded (hands) | 12, 4 | 12, 4 | 2, 8 | — |
| Ring translation | (+0.39, −0.06) px | (+0.25, −0.12) | (+0.36, −0.49) | (+0.25, −0.21) |
| Ring scale | +0.26 % | +0.18 % | +0.12 % | −0.07 % |
| Ring rotation | +0.00° | +0.02° | −0.04° | +0.02° |

The ring translation matches the raised-marker parallax of about 0.3–0.6 px.

Pooled genuine values (descriptive; these are **not** tolerances):

| Metric | Round (n=28) | Baton (n=8) | Triangle (n=2) |
|---|---|---|---|
| Raw centre offset, mean / max | 0.54 / 1.27 px | 0.36 / 0.62 px | 0.27 / 0.49 px |
| Parallax-adjusted offset, mean / max | 0.33 / 0.58 px | 0.39 / 0.71 px | 0.95 / 0.97 px |
| Local (ring-removed) offset, mean / max | 0.21 / 0.66 px | 0.37 / 0.55 px | 0.69 / 0.69 px |
| Angular position error, max | 0.39° | 0.19° | 0.05° |
| Rotation | — | −0.07 ± 0.34°, max 0.47° | centreline +0.17 ± 0.85°, max 0.76° |
| Size | radius +0.29 ± 0.09 px, max 0.53; circularity ≥ 0.95 | length +1.17 ± 0.36, width +0.66 ± 0.22 px | base width +2.70, height +3.57 px |
| Other | — | — | base tilt +0.71°, max 1.12°; L/R mismatch max 0.16° |

Two size quantities have consistent offsets: the round radius (+0.3 px) and the triangle size (+2.7 / +3.6 px). These are consistent definition offsets between the bare-dial threshold outline and the outermost-edge footprint, not scatter.

### RL_LOCAL_BLNR (known local-marker case: "6 left; 12 slightly tilted")

- **Pose:** minute lattice locked, tick median 0.105 px.
- **Occluded:** 8 (GMT hand), 9 (hand), and 10 and 11 (edge coverage 0.57–0.60; visually clean, so conservative misses).
- **Ring:** rotated **+0.46°** clockwise relative to the minute track (genuine ≤ 0.04°), scale **+0.73 %** (genuine ≤ 0.26 %), translation (−0.74, +0.24) px, from 7 markers. Because the minute lattice fits to 0.1 px, this is the marker ring disagreeing with the printed track. It is not pose error.
- **6:** raw (−2.78, +1.67) px, i.e. **2.84 px tangential = left** (genuine baton tangential max 0.36 px). Rotation −0.01°, so displaced, not rotated. Local residual 0.76 px left after the ring terms. The "6 left" is real; most of it is the ring rotation, plus about 0.8 px of local displacement.
- **12:** raw tangential −1.86 px (left), local tangential **−2.74 px** (genuine local max 0.30). Centreline rotation +0.79°, base tilt **+1.82°** (genuine 0.30 / 1.12°), right side +1.11°. The 12 is locally displaced left of where the rest of the ring puts it. The tilt is at the edge of a 2-sample genuine range, so it is suggestive, not conclusive.
- **Other rounds** sit 1.7–2.3 px off raw, but only 0.2–0.5 px off locally. They are consistent with the ring rotation and scale, not with local defects.

### RL_ARF_BLRO_CROOKED6 (secondary control: reported crooked 6)

- **Pose:** accepted after the gate fix, tick median 0.108 px.
- **Occluded:** 2 (hand).
- **Ring:** scale **+1.01 %** (+1.9 px at the round-marker radius; genuine ≤ 0.26 %), translation (−0.16, +1.00) px, rotation −0.10°.
- **6:** rotation **+0.01°**, parallelism 0.0°, so **no measurable crookedness**. Raw +1.97 px radial is ring scale. Local radial −0.81 px (genuine baton local radial +0.21 … +0.48), so the 6 sits about 1.1 px inward relative to the genuine pattern after the ring terms. The "crooked 6" label is **not supported as a rotation**; at most there is a small radial-position deviation.
- **12:** raw tangential −2.36 px, local tangential **−1.88 px** (left; genuine local max 0.30). Rotation +0.28°.
- **Other locals:** several rounds reach 0.8–1.0 px (8, 10, 11), above the genuine local round maximum of 0.66.

## Answers

1. **Do the genuine controls establish a stable normal range?** Yes for round markers and batons; not yet for the 12 triangle.
   - Rounds: 28 measurements, raw 0.54 ± 0.27 px, local 0.21 ± 0.14 px, ring scale within ±0.26 % and ring rotation within ±0.04°.
   - Batons: 8 measurements, rotation within ±0.47°.
   - Triangle: only 2 clean genuine triangles, because hands cover 12 on both WEX photos. That is too few for a range.
2. **Does RL BLNR show the expected 12/6 deviation numerically?** Yes.
   - 6 is displaced 2.84 px left, about 8× the genuine baton tangential maximum. It is not rotated.
   - 12 is locally displaced 2.7 px left, with a +1.8° base tilt.
   - The measurement also shows that much of this comes from the whole marker ring being rotated about 0.5° and scaled about +0.7 % against the minute track.
3. **Does ARF show a measurable 6-marker issue?** Not a crooked (rotated) 6: rotation 0.01°. The measurable deviations are:
   - a marker ring about 1 % too large for the minute track;
   - the 12 about 1.9 px left;
   - a small (about 1 px) inward 6.
4. **Reliable enough to port to Android:**
   - the frozen-H, read-only measurement order;
   - the outline-integrity occlusion test;
   - round-marker centre, with radial/tangential and angular position;
   - baton centre and long-axis rotation;
   - the ring decomposition (translation / scale / rotation of the marker set relative to the minute track), which is the most discriminating quantity found so far.
5. **Keep research-only:**
   - triangle rotation, side-angle and base-tilt metrics (n=2 genuine);
   - all size metrics (radius, length/width, base/height), which carry definition offsets;
   - circularity;
   - the parallax adjustment;
   - any thresholds or verdict wording. Ranges come from 4 genuine photos only.

## Amendment: deterministic edge fitting (2026-10-06, later)

Porting this layer to Android exposed a fragility in the line and circle fits. With 21 edge samples per side and a 0.5 px tolerance, several candidate lines can tie on inlier count, so a baton side could flip by about 1–2° depending on sampling order.

The fits are now deterministic:
- **Lines:** exhaustive pair search, with ties broken by residual.
- **Circles:** ties broken by residual.
- **Both:** a short local-optimisation loop (refit, re-select inliers, refit).

Effect on these results:
- The genuine ranges are unchanged to three decimals.
- On RL BLNR, hour 11 drops just below the coverage rule (0.57), and the ring now reads +0.46° / +0.73 % from 7 markers.

The Android port (`Alpha94MarkerMeasurement`, branch `feature/android-alpha91-minute-lattice`) uses the identical algorithm and interpolant. The JVM parity harness reproduces these marker centres to within 0.033 px.
