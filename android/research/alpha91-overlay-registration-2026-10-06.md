# Alpha91 automatic GMT overlay registration — FROZEN — 2026-10-06

This is research only. Alpha90 production (`f66acee`) is not touched, and no APK was built.

**Status: the perspective-overlay problem is solved to the agreed standard. The solver is frozen.**

Files:
- Script: `alpha91_overlay_registration.py`, one script run unchanged on all four controls.
- Integration check: `alpha91_overlay_seed_check.py`
- Outputs in `alpha91-overlay-registration-results/`:
  - `run_log.txt`
  - `seed_check_log.txt`
  - `results.json`, with the H matrices and per-marker vectors
  - `overlay_<photo>.jpg`, the full dial
  - `sectors_<photo>.jpg`, enlarged crops at 12, 1:30, 3, 4:30, 6, 7:30, 9 and 10:30

Reproduce with:

```
python3 android/research/alpha91_overlay_registration.py --robustness \
  --inputs <alpha91_claude_overlay_inputs> --out android/research/alpha91-overlay-registration-results
python3 android/research/alpha91_overlay_seed_check.py --inputs <alpha91_claude_overlay_inputs>
```

The input images are the four genuine controls and the bare genuine dial from the supplied pack. They are not committed.

## Frozen method

1. **Coarse pose/localisation.**
   - SIFT + MAGSAC, with degenerate solutions rejected.
   - Then masked ECC on gradient magnitude in the canonical frame, over 0.2R–0.99R.
   - Its only job is to land within about 1–2 px.
2. **12 direction and minute-lattice branch.**
   - The photo's marker annulus (0.56R–0.93R, all 360°) is correlated with the same band of the bare genuine dial. The layout of the 12 triangle, the 6/9 batons and the 3 date window is unique over 360°.
   - The start is rotated onto that branch.
   - This cue chooses the branch only. It is never a correspondence in the homography fit.
3. **Refine H from the tick lattice, with one global rule for every photo.**
   - First stage: per-tick 2D intensity centroids on the exact 6° lattice predicted by the current H.
   - Final stage, at full source resolution, uses two observables per tick:
     - the **tangential centreline from the inner, always-visible part of each tick**;
     - the **radial tick inner-end edge**, at the maximum dark-to-bright gradient.
   - The outer part of the ticks is never used. The rehaut hides it on the far side of oblique photos.
   - The solve is a sector-balanced Huber least squares (12 × 30° sectors, equal weight) over the 8 free parameters.
4. **Masks, identical on all photos.**
   - Everything inside 0.918R: applied markers, hands, centre, print.
   - Ticks 11–19: date window / cyclops.
   - Ticks 29–31: SWISS ♛ MADE.
   - Any individual tick crossed by a hand, rejected by mass/width gating.
5. **Fail closed.** The photo is rejected if any of these hold:
   - the 12 cue is not distinctive from the 30° alternatives (margin below 1.02);
   - the refinement changed the lattice phase by more than 2°;
   - the result landed on a ±6° branch;
   - fewer than 8 tick sectors were used;
   - the tick-lattice residual is not clean: median over 0.30 px or RMS over 0.60 px.

   The last check was added after the seed test below showed a wrong local fit with only a 1.4° phase change. That fit had a 1 px median and 2 px RMS residual, and the gate rejects it.

**Not used and not applied to H:**
- applied-marker positions;
- per-photo parameters;
- lens distortion (one k1 tested and rejected: unstable, under 0.02 px gain);
- marker-height parallax, which is reported separately below.

## Master measured from the bare genuine dial

The bare dial was rectified on the exact 6° lattice and its circular edge.

| | Bare dial | Frozen Alpha90 master |
|---|---:|---:|
| Minute-tick inner / outer end | **0.9325 / 0.9803R** | 0.925 / 0.972 |
| Round-marker centre / outer radius | **0.813 / 0.0915R** | 0.816 / 0.088 |
| Baton centre / half-length / half-width | **0.755 / 0.1525 / 0.059R** | 0.758 / 0.150 / 0.060 |
| Triangle apex / base / half-base | **0.5925 / 0.9025 / 0.124R** | about 0.600 / 0.902 / 0.123 |
| Triangle area centroid (holdout point) | **0.799R** | `TRI_CENTER_R` 0.750 is the extent midpoint |

## Results: frozen method, all four controls unchanged

| Control | Accepted | Ticks used (sectors) | Gated ticks | Tick-fit median / RMS | Leave-one-sector-out tick residual, mean (worst) | Clean holdouts | **Mean** | Median | Max |
|---|---|---|---|---|---|---:|---:|---:|---:|
| WEX_01 | yes | 44 (11) | 20, 28, 32, 58 | 0.13 / 0.16 px | 0.22 (0.52) px | 9 | **0.54** | 0.56 | 1.09 |
| WEX_02 | yes | 45 (11) | 28, 32, 58 | 0.14 / 0.21 px | 0.28 (0.56) px | 8 | **0.40** | 0.35 | 0.63 |
| HO_01 | yes | 44 (11) | 21, 28, 32, 40 | 0.16 / 0.21 px | 0.32 (0.72) px | 9 | **0.97** | 0.92 | 2.02 |
| HO_02 | yes | 44 (11) | 0, 32, 43 | 0.11 / 0.36 px | 0.25 (0.58) px | 10 | **0.45** | 0.46 | 0.71 |

What the columns mean:
- **Masked on every photo:** ticks 11–19 and 29–31. Twelve tick sectors exist; sectors 2 and 3 are masked by the date/cyclops mask, so at most 10–11 sectors can be used.
- **Leave-one-sector-out:** H is refitted with one 30° sector removed, and that sector's ticks are then predicted. This is an independent dial-plane residual. No sector drifts by more than 0.72 px.
- **Clean holdouts:** applied markers whose outline is mostly hidden are excluded as invalid holdouts. The rule is the same for every photo: edge coverage below 0.6. The excluded markers are:
  - WEX1: 12 and 4 (hands);
  - WEX2: 12 and 4 (hands) and 6 (SWISS ♛ MADE print next to the baton end);
  - HO1: 6 (print) and 8 (GMT hand);
  - HO2: 6 (print).
- **Hand-adjacent markers:** WEX1 h5 and HO1 h2 pass the coverage rule but are hand-adjacent. Dropping them as well gives WEX1 0.53 px and HO1 0.97 px mean.

Per-marker residual vectors, radial and tangential components, and canonical radius and angle are in `run_log.txt` and `results.json`.

**Branch-lock robustness.** Each photo was rerun from injected start rolls of −20, −9, −6, +6, +9 and +20°. All 24 runs reached exactly the same H (0.000 px difference) and were accepted. On every photo the final 12 cue sits within 0.04° of the 12 axis, and the refinement changed the phase by less than 0.08°.

**Visual check (`sectors_*.jpg`).**
- The minute ticks coincide with the projected lattice in every sector, including the date-side ticks that are masked out of the fit (magenta).
- The marker footprints coincide at 12, 1:30, 4:30, 7:30, 9 and 10:30. The excluded 6 o'clock batons (red) also visibly coincide.
- No drift is visible around the dial on any photo.

## Remaining error: kept separate from H

- **Common offset.** The clean marker holdouts carry a small common offset of +0.24 to +0.51 px in x and −0.06 to −0.71 px in y.
  - It is parallax from the raised applied-marker tops.
  - One shared height, estimated leave-one-photo-out at about 0.020R (about 0.27 mm), explains it.
  - Applied to the marker outlines only, the means would fall to 0.41, 0.32, 0.49 and 0.37 px. It is **not** applied to H.
- **The older (+1.3, +0.8) px offset was mostly a labelling bias.** The old hand-placed centres sit +1.2 to +2.2 px right of the automatically measured outlines.
- **HO_01 12 at 2.0 px** is the largest clean holdout. HO_01 is the most oblique control (about 11°). The 12 sector crop shows the outline sitting on the triangle, so this is mostly the edge detector reading the visible side wall of a tall triangle. It is not dial-plane drift: the neighbouring ticks fit within 0.22 px.

## Integration check: Alpha90's own seed, no SIFT needed (`seed_check_log.txt`)

| Seed | WEX1 | WEX2 | HO1 | HO2 |
|---|---|---|---|---|
| Alpha90 dial ellipse + 12 cue | REJECT (wrong local fit, 1 px tick median) | REJECT | ACCEPT, identical H | ACCEPT, identical H |
| Alpha90 dial ellipse + 12 cue + masked ECC | ACCEPT, identical H | ACCEPT, identical H | ACCEPT, identical H | ACCEPT, identical H |

## Smallest Android integration plan

This touches a new Alpha91 path only; Alpha90 stays as is.

1. **New class `GmtTickLatticeRegistration`.** Pure Java on the existing `org.opencv:opencv:4.9.0`.
   - **Seed:** Alpha90's existing dial ellipse (`PerspectiveGmtOverlay.findDialEllipse` / `DialEdgeEllipseFit`) gives the affine start.
   - **12 cue:** the marker-band correlation against a baked bare-dial polar band, about 75 × 1440 floats, is used to pick the branch. Alpha90's `GmtTwelveLandmarkAnalyzer.trackRollClockDeg` serves as an agreement cross-check only.
   - **Basin step:** `Video.findTransformECC` with `MOTION_HOMOGRAPHY` on the masked gradient image at about 160 px/R.
   - **Lattice refinement:** the two stages, using bicubic or bilinear sampling of small per-tick patches and a hand-written 8 × 8 Gauss-Newton with Huber weights and 12-sector balancing.
   - **Fail-closed checks:** exactly as above. When any check fails, show "overlay unavailable", never a forced pose.
2. **Master constants.** Add the bare-dial values above for the GMT overlay drawing (new Alpha91 constants; leave `Gmt126710BlnrMaster` untouched). This is a product-owner decision.
3. **Draw** the mathematical outline (60 ticks plus marker footprints) through the accepted H, reusing the existing outline renderer. Do not apply the marker-height parallax to H. If it is wanted at all, it is a separate optional drawing offset.
4. **Parity test before any APK.** A JVM unit test runs the Java path on the four control images and must reproduce the Python H to within 0.1 px at 0.95R, with all four accepted. The same test should confirm the ellipse-only seed is rejected on WEX1 and WEX2.
5. Then build the APK.
