# Alpha91 automatic GMT overlay registration — 2026-10-06

This is research only. Alpha90 production (`f66acee`) is not touched, and no APK was built.

- Script: `alpha91_overlay_registration.py`
- Outputs: `alpha91-overlay-registration-results/`
  - `run_log.txt`
  - `results.json`, with per-marker vectors and the H matrices
  - overlay renders

Reproduce with:

```
python3 android/research/alpha91_overlay_registration.py \
  --inputs <alpha91_claude_overlay_inputs> --out android/research/alpha91-overlay-registration-results
```

The input images aren't committed. They are the four genuine controls and the bare genuine dial from the supplied input pack.

## Result

**The target is met on all four genuine controls with one fixed method.** The method uses no applied-marker positions, no per-photo parameters and no lens model.

| Control | R (px) | Ticks used / sectors | Tick fit RMS | Marker holdout mean | Median | Max | Usable markers |
|---|---:|---:|---:|---:|---:|---:|---:|
| WEX_01 | 140 | 44 / 11 | 0.16 px | **0.54 px** | 0.56 | 1.09 | 9 |
| WEX_02 | 155 | 45 / 11 | 0.21 px | **0.40 px** | 0.35 | 0.63 | 8 |
| HO_01 | 203 | 44 / 11 | 0.21 px | **0.97 px** | 0.92 | 2.02 | 9 |
| HO_02 | 143 | 44 / 11 | 0.36 px | **0.45 px** | 0.46 | 0.71 | 10 |

How the holdouts were measured:
- The holdouts are applied-marker outlines measured automatically. Round markers use a robust circle fit; the batons and the triangle use a robust per-side line fit and the polygon area centroid.
- Each holdout is compared with the bare-dial master projected through the dial-plane homography.
- Markers whose outline is mostly hidden by a hand are flagged `OCC` and left out of the statistics. The occluded markers were WEX1 12/4, WEX2 12/4/6, HO1 6/8 and HO2 6.
- The per-marker vectors, radial/tangential components and canonical radius/angle are in `run_log.txt` and `results.json`.

The overlays (`overlay_*_plane.jpg`) put every minute tick on the exact 6° lattice and every master outline on its marker, all the way round the dial.

## Master measured from the bare genuine dial

The bare reference is itself very slightly off-frontal. It was rectified with a homography fitted only to the exact 6° minute lattice and the circular dial edge. After rectification, the tick angles fit within ±0.18°. The leftover is a 3-fold pattern that comes from the image itself, not from perspective.

| Quantity | Bare dial | Frozen Alpha90 master |
|---|---:|---:|
| Minute-tick inner end | **0.9325R** | 0.925 |
| Minute-tick outer end | **0.9803R** | 0.972 |
| Round-marker centre | **0.813R** (spread 0.807–0.818) | 0.816 |
| Round-marker outer radius | **0.0915R** | 0.088 |
| Baton centre | **0.755R** (6: 0.751, 9: 0.759) | 0.758 |
| Baton half-length / half-width | 0.1525 / 0.059 | 0.150 / 0.060 |
| Triangle apex / base / half-base | 0.5925 / 0.9025 / 0.124 | inward 0.150 / outward 0.152 / 0.123 about 0.750 |
| Triangle **area centroid** (holdout point) | **0.799R** | — (`TRI_CENTER_R=0.750` is the extent midpoint) |

What this shows:
- **The minute track in the frozen master sits too far inward.** Both ends are about 0.008R inside the genuine dial, which is about 1.2 px at R=150. The earlier "about 0.936 / 0.984" estimate came from an unrectified bare image.
- **Correction to `alpha91-canonical-marker-consistency-2026-10-06.md`:** the bare dial does *not* support moving the batons to 0.763R. The bare-dial ratio of baton to round marker (0.755/0.813 = 0.929) equals the frozen master's ratio (0.758/0.816). The 0.763 estimate came from the old manual labels, which are biased (see below).
- On the bare dial itself, all applied markers sit about 0.003R "up" relative to the printed track. This is the same raised-marker parallax described below, seen in a slightly off-frontal reference photo, and it sets the uncertainty of the master marker radii at about ±0.003R.

## Method (fixed; no per-photo tuning)

1. **Coarse placement (basin only).**
   - SIFT + MAGSAC between the rectified bare dial and the photo, with a check that rejects degenerate homographies.
   - Then masked ECC on blurred gradient magnitude in the canonical frame, over 0.2R–0.99R.
   - This puts the start within about 1–2 px. It touches whole-dial texture, but only to choose the basin; see the basin test.
2. **Lattice centroid stage.**
   - Each minute tick is predicted on the exact 6° lattice through the current H.
   - Its 2D intensity centroid is measured in a locally rectified patch, with the per-row median subtracted.
   - Ticks are gated on mass and width, which rejects hands.
   - The solve is a sector-balanced (12 sectors, equal weight) Huber least squares over the 8 free parameters, iterated.
3. **Final occlusion-safe stage, at full source resolution.** Observables per tick:
   - the tangential centreline from the inner part of the tick, which is always visible;
   - the radial **inner-end** position, taken at the maximum dark-to-bright gradient, which does not depend on blur.

   The outer part of the ticks is deliberately not used. On oblique photos the flange/rehaut hides it on the far side; in HO_01's lower-left this biased the centroid stage inward.
4. **Masks.**
   - Nothing inside 0.918R is used: applied markers, hands, centre, text and logo.
   - Ticks 11–19 are excluded (date window / cyclops), as are ticks 29–31 (SWISS ♛ MADE).
   - Hands crossing individual ticks are rejected by the gating.
5. **Fail-closed checks.**
   - Accept only if at least 8 of the 12 tick sectors are used.
   - Accept only if the roll between the coarse and final estimates is under 2° (guards against a 6° lattice slip).

## Diagnostics

- **Convergence basin.**
  - From 12 random starts 5–11 px away from the solution (±2.5° roll, ±3 % scale, ±5 px shift), 11 converged to the *identical* H, with a difference under 0.001 px.
  - One HO_02 start slipped by one tick, 6° or about 14 px. That is why the roll check exists.
  - Because the result does not depend on where it starts, the coarse whole-dial step cannot leak marker positions into the final fit.
- **Holdout detector check.**
  - Shifts of 1.5–2.5 px were injected; mean recovery was 1.35–2.49 px, with 0.03–0.6 px spread across markers.
  - With a wider edge window (0.020R), clutter such as hands and baton ends next to ticks inflates the WEX2 and HO1 means to 0.90 and 1.31 px. Visual inspection confirms these are detector failures, not misalignment.
  - The narrow window (0.013R) is the reported one.
- **Lens distortion (one k1, about the image centre): rejected.**
  - k1 came out as +48, −3.9, +1.2 and +0.02 across the four photos.
  - The tick fit improved by at most 0.013 px.
  - It is unstable and does nothing, so it is discarded.

## What the remaining error is, and the old "common translation"

1. **The old (+1.32, +1.04) px common offset is mostly a bias in the manual labels.** The manual centres in `alpha91_fairscan_pose_proof.py`, compared with this H, sit at:

   | Control | Mean offset (x, y) |
   |---|---|
   | WEX1 | (+2.17, +0.29) px |
   | WEX2 | (+1.54, +0.54) px |
   | HO1 | (+1.46, +1.07) px |
   | HO2 | (+1.17, +0.86) px |

   The same markers measured automatically sit within about 0.5 px. The manual centres should no longer be used as precision holdouts.
2. **The genuine physical residual is parallax from the raised applied markers.**
   - The marker tops sit above the dial plane, so in an oblique photo they shift towards the far, foreshortened side.
   - Model: a single shared marker-top height *h*. The direction and size of the shift per unit height come from each photo's own homography, using only the local affine tilt plus the sign of the projective row; no focal length is needed.
   - Each photo's own best *h* is 0.013–0.033R. Leave-one-photo-out *h* is 0.018–0.022R; pooled it is **0.020R**, about 0.27 mm on a dial of about 13.5 mm radius, which is physically plausible for applied indices.
   - With the leave-one-out *h* applied to the marker outlines only, the holdouts become:

     | Control | Mean | Median | Max |
     |---|---:|---:|---:|
     | WEX1 | 0.41 | 0.34 | 1.02 |
     | WEX2 | 0.32 | 0.36 | 0.51 |
     | HO1 | 0.49 | 0.45 | 1.38 |
     | HO2 | 0.37 | 0.34 | 0.91 |

   This is not a camera model: the dial-plane homography is unchanged, and marker outlines are drawn on a plane raised by one genuine constant. It is optional: the plain planar result already meets the target.
3. **Remaining HO_01 pattern.** The triangle at 12 is +1.3 px in x, and hours 4/5 and 10/11 have about 1 px opposite tangential components. HO_01 has the strongest tilt (about 11°). After the parallax term, only 12 stays above 1 px; its outline is partly crossed by the GMT hand. No sector drift is left unexplained beyond this.
4. **What was ruled out:** translation, scale or roll bias in H, lens distortion, and a wrong master radius for the round markers. WEX1 hours 4/5 are not an anomaly: hour 5 measures 0.58 px, and hour 4 is hand-occluded and flagged.

## Integration decision

The offline evidence meets the stated target: every control has a mean of 1 px or less; the worst max is 2.0 px, on one partly hand-covered triangle; there is no unexplained sector drift; and the overlay is visually convincing. The smallest safe Android path (not yet implemented) would be:

1. Port the fine stages only: centroid lattice stage, then inner-end stage, then the fail-closed checks. They need bilinear or bicubic sampling, a small 8-parameter Gauss-Newton/Huber solve, and no new dependencies.
2. Seed them from the existing Alpha90 dial-ellipse/orientation pose, provided it lands within the basin (about ±2.5° roll, a few px). Otherwise add the ECC basin step. SIFT is not required if the existing seed is good enough, and that needs checking first.
3. Change the master constants for the GMT overlay to the bare-dial values: track 0.9325/0.9803, round 0.813/0.0915 and the triangle geometry. This is a product-owner decision because it changes cross-family drawing constants.
4. Optionally offset the drawn applied-marker outlines by the fixed 0.020R marker-height parallax.
5. When the fail-closed checks reject a photo, show "overlay unavailable" rather than a forced pose.
