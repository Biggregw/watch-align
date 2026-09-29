# Rehaut visibility as a camera-pose cue: controlled 3D model vs real genuine photos (2026-09-29)

Research only. No production thresholds, QC logic or classifications were changed.

## Question

Can the visible rehaut width at 12/3/6/9 estimate camera pitch/yaw well enough to (1) improve Watch Align, (2) correct marker measurements, or (3) justify a maximum-angle rejection rule? Is there a usable chain from **rehaut asymmetry → camera pose → expected measurement distortion**?

## Method

### 1. Controlled 3D model (`tools/rehaut-model/rehaut_model.py`)

This is not a 2D warp of photos. The model is built in 3D and then projected:

- **Dial plane** at z = 0 with radius 1. The printed minute-track tick ends are at r = 0.925.
- **Rehaut** is a conical wall from (r = 1, z = 0) to (r = 1 + w0, z = w0·tan α). Its frontal visible width is w0 = 0.06 R, which matches the real median of 0.062 R.
- **Applied indices** (round markers and the 12 triangle) stand hm = 0.02 R above the dial.
- **Camera** is a pinhole at distance D (6, 15 or 50 dial radii) looking at the centre. It is displaced by a known pitch (+ towards the 12) and yaw (+ towards the 3).

Each case is measured the way the app measures it:
- sector widths (median over ±18° every 2°);
- V, H and min/mean, as in `GmtRehautSectorAnalyzer`;
- the global first harmonic (`GmtRehautPoseAnalyzer`);
- the dial-edge ellipse ratio and minor-axis clock;
- round-marker offsets after the app's `DialFrame` un-squash;
- the 12 gap and the 12 axis rotation against the 59–01 chord.

### 2. Sweep (`sweep.py` → `results/sweep.csv`)

The sweep covers 7,500 cases: slope α ∈ {45, 60, 70, 80}° × D ∈ {6, 15, 50} × pitch and yaw from −30° to 30° in 2.5° steps. The rehaut slope α is not known for the real watch, so it is swept.

### 3. Real photos

`tools/desktop-harness/drivers/RehautPose.java` runs the app's own components on 127 genuine photos and writes `results/real_genuine_raw.csv`.

`analyse_real.py` adds two pose cues that do not use the rehaut:
- **8-marker affine:** the round-marker centres fitted to the master layout. This gives tilt and minor-axis direction. tick60 is deliberately excluded, because it biased the tilt.
- **Index-vs-print parallax:** the round-marker offset against the ticks, fitted as c + A·cos30h + B·sin30h. This gives the side the camera is on.

### 4. Inversion, budget and table (`report.py`)

`report.py` writes:
- `inversion.csv`: pose recovered from H, with real noise and with a wrong α;
- `distortion_budget.csv`;
- `retake_photos.csv`;
- three plots.

## Results

### Model: what rehaut asymmetry does with pose

The size of the asymmetry depends mostly on the unknown wall slope α. At the same 10° of yaw:

| slope α | H at 10° yaw | yaw at which min/mean < 0.45 (app RETAKE) |
|---|---|---|
| 45° | −0.14 | ≈30° |
| 60° | −0.25 | ≈20° |
| 70° | −0.38 | ≈14° |
| 80° | −0.70 | ≈7° |

A 2D homography cannot reproduce this effect. The near wall of a raised cone foreshortens and the far wall opens up, and the amount depends on α.

The direction of the effect is correct in the code:
- camera towards the 3 → the 3-side rehaut gets narrower (H < 0);
- camera towards the 12 → V < 0.

### Model: what pose does to the QC measurements

These distortions do not depend on α (`distortion_budget.csv`, `model_distortion_vs_tilt.png`).

| effect | 5° | 10° | 15° | 20° | 30° | QC scale |
|---|---|---|---|---|---|---|
| max round-marker centring offset (diameters, D = 15) | 0.011 | 0.023 | 0.035 | 0.047 | 0.073 | CHECK 0.15: **negligible** |
| 12 gap error, camera towards the 6 (triangle widths, D = 15) | −0.013 | −0.021 | −0.031 | −0.040 | −0.059 | true 0.094; LOW_CLEARANCE at 0.070 is crossed at ≈11° |
| 12 gap error, camera towards the 12 (D = 15) | −0.001 | +0.009 | +0.015 | +0.019 | +0.027 | inflates |
| 12 axis rotation, camera towards the 3, D = 6 / 15 / 50 | −0.8 / −0.3 / −0.1° | −1.6 / −0.65 / −0.2° | −2.4 / −1.0 / −0.3° | | −5.3 / −2.1 / −0.6° | CHECK ≈1° |

- The gap error comes from **index-height parallax**: the triangle stands above the printed ticks. It does not come from planar foreshortening, which is second order. At D = 15 there is also a −0.005 offset when the camera is straight on, from perspective magnification of the raised index.
- The rotation comes from keystone, ≈ (0.925/D)·sin(yaw) rad. It only matters for close phone shots.
- The code's gap-trend sign (camera nearer the 12 → INFLATED) is **correct**.

### Inversion: can H recover yaw?

`inversion.csv` answers this in two parts.

**Measurement noise.** The noise level is H sd ≈ 0.19, measured on WOS photos that the affine shows are near frontal (<4°). With that noise, a single photo's yaw estimate has an sd of **10–13°** and a 90% error of **16–21°** at α = 45°. At steeper α the error is smaller in degrees, but α is unknown.

**Wrong α.** With no noise at all, a true 20° yaw at α = 45° is read as **11.8°, 7.7° or 4.3°** if α is assumed to be 60°, 70° or 80°. The degrees-per-unit-H conversion is not known.

The noise is not from pixel quantisation. The median rehaut width is 17.5 px, so a 1 px error per side gives an H error of only ≈0.04. The remaining ≈0.18 comes from the edge search picking something else in some sectors, such as the bezel lip, a reflection or the engraving.

### Real genuine photos, binned by independent tilt (8-marker affine)

| affine tilt | n | median H | H sd | share H < 0 | GOOD / CORR / RETAKE |
|---|---|---|---|---|---|
| 0–4° | 34 | +0.06 | 0.18 | 21% | 12 / 21 / 1 |
| 4–8° | 22 | +0.05 | 0.15 | 18% | 6 / 16 / 0 |
| 8–14° | 11 | −0.24 | 0.28 | 73% | 3 / 5 / 3 |
| 14–30° | 12 | −0.19 | 0.23 | 75% | 1 / 10 / 1 |

These photos are nearly all Phillips shots with the camera towards the 3, which the affine minor axis ≈90° and parallax A < 0 confirm.

**There is a real pose signal in H across a population.** It has the right sign, and about −0.2 at ≈13° implies an effective α ≈ 55–60°.

**Per photo, the signal is weaker than the noise:**
- corr(affine tilt, |H|) = −0.13 within Phillips;
- corr(parallax A, H) = 0.14 across all sources.

The two dial-plane cues do agree with each other: corr(affine tilt, |parallax A|) = 0.73. Measured 12 rotation tracks parallax A (corr −0.53) but not H (−0.17).

The current labels barely follow the actual angle:
- one near-frontal photo (≈2°) is RETAKE;
- four photos at 8–20° are GOOD.

See `real_H_vs_independent_pose.png`.

### The genuine RETAKE photos (`retake_photos.csv`)

Six genuine photos are RETAKE, all because of narrow-side sector min/mean. The summary had five; `phillips_122669` is the sixth.

| photo | w12/w3/w6/w9 (÷R) | H | min/mean | affine tilt, side | est. pose | predicted gap error | predicted 12 rotation D6 / D15 | predicted max offset | verdict |
|---|---|---|---|---|---|---|---|---|---|
| Phillips 151263 | .044/.014/.054/.037 | −0.47 | 0.36 | 13.4°, towards 3 | yaw 13° | −0.003 | −2.3° / −0.9° | 0.03 | **real displacement**; the rotation is the only material effect, and only if the shot was close |
| Phillips 180697 | .053/.012/.064/.035 | −0.50 | 0.29 | 10.3°, towards 3 | yaw 10° | −0.002 | −1.5° / −0.5° | 0.03 | real displacement, modest effect |
| Phillips 180859 | .058/.014/.058/.071 | −0.68 | 0.27 | 11.7°, towards 3 | yaw 12° | −0.004 | −2.0° / −0.9° | 0.03 | real displacement, modest effect |
| Phillips NY080121_35 | .047/.038/.038/.065 | −0.25 | 0.82 | **16.9°**, towards 3 | yaw 17° | −0.004 | −3.3° / −1.6° | 0.04 | the largest real angle in the set; its sector min/mean is fine, so the RETAKE came from another scale or cue |
| WOS 40411271 | .052/.080/.060/**.024** | +0.54 | 0.44 | **2.0°** | ≈frontal | −0.006 | ±0.3° | 0.005 | **false RETAKE**: w9 is mismeasured and the camera is not displaced |
| Phillips 122669 | .022/.061/.069/.040 | +0.21 | 0.46 | not judged (too few markers) | — | — | — | — | not judged |

What the RETAKE photos show:
- Three of the Phillips RETAKEs are correctly detecting a real 10–13° yaw.
- At that yaw, the modelled damage is small:
  - centring: ≈0.03 against 0.15;
  - 12 gap: <0.005, because the displacement is sideways and not towards the 6;
  - 12 rotation: 0.5–2°. This depends on camera distance, which the photo does not reveal.
- The measured 12 rotations on these photos are −0.5° to +0.14°. That is consistent with a far (auction-house) camera, D ≳ 15.
- So these photos probably did not need a retake for gap or centring QC. Rotation QC is borderline.

## Answers

1. **Can rehaut visibility estimate pitch/yaw accurately enough to improve Watch Align?** **No.** Per photo, the H noise of ±0.19 corresponds to ±10–13°. The conversion also depends on the unknown wall slope α, by a factor of about 4 between α = 45° and α = 80°. The dial-plane cues are better:
   - the 8-marker affine has a floor of about 2–4° and needs no α;
   - index-vs-print parallax gives the side the camera is on.
2. **Can the estimated pose correct local marker measurements?** Not from the rehaut. From the dial-plane cues, partly:
   - Centring needs no correction; the error is <0.05 up to 20°.
   - The 12 rotation (keystone) and the 12 gap (index parallax) are the two measurements that move. Their correction needs index height hm and camera distance D, and neither is measured. The model gives the correction's direction and scale but not a per-photo number.
3. **Can the rehaut support a defensible maximum-angle rule?** Not the current min/mean < 0.45 rule, because its angle equivalent ranges from 7° to 30° depending on α. It rejected a 2° photo and passed photos at 12–20°.

   A defensible rule would be stated in the quantity that actually matters: the modelled distortion budget. For example:
   - **camera towards the 6 (V side):** the gap loses ≈0.002 per degree; it uses half of the 0.024 LOW_CLEARANCE margin by ≈5° and all of it by ≈11°, so a limit near 5–8° is defensible;
   - **sideways:** the only limit is rotation, and it depends on D.

   The angle should come from the marker affine, not from the rehaut.
4. **Is there an asymmetry → pose → distortion relationship?** The **pose → distortion** half is solid and α-independent; the tables above give it. The **asymmetry → pose** half exists only statistically, across many photos. It cannot be relied on for one photo.

## Code review findings (flagged, not changed)

1. **`GmtHumanPosePolicy` sector rule (min/mean < 0.45 / < 0.75, max asymmetry ≥ 0.14):** it assumes rehaut asymmetry maps to one angle scale. In fact the mapping scales with the wall slope α, by about 4× between α = 45° and α = 80°. It is also dominated by edge mis-picks: sd 0.19 on frontal photos. The min/mean statistic also reacts to a single bad sector (WOS 40411271).
2. **`GmtRehautSectorAnalyzer`:** it picks the strongest gradient within ±0.55·separation of the seeds. That is wide enough to catch the bezel lip or a reflection. There is no check that a sector's width agrees with its neighbours. Coverage ≥0.35 does not stop a confident wrong edge.
3. **`GmtRehautPoseAnalyzer` / gap-trend:** the sign convention (camera nearer the 12 → gap INFLATED) is **correct**. The mechanism is index-height parallax, not dial foreshortening, so its size scales with hm and 1/D, not with the rehaut.
4. **`GmtEllipsePoseAnalyzer`:** the Canny closed-contour ellipse rarely yields a result on real photos. The dial-edge ellipse from `DialEdgeFitter` is available but unused as a pose cue. At 10° the ellipse ratio is 0.985, a 1.5% effect that is near its noise.
5. **Any affine or pose fit that includes tick60** with the round markers is biased; it moved WOS tilt from 2.2° to 6.3°. Tick60 sits at a different height and radius from the markers and has its own detector error.
6. **`DialFrame` un-squash** uses the dial-edge ellipse, which lies in the dial plane. It correctly removes the planar squash but cannot remove index-height parallax. The residual centring error in the model (≤0.05 at 20°) comes from that parallax.

## Limitations

- Index height hm, camera distance D and wall slope α are unknown for real photos. The model's distortion predictions assume hm = 0.02 R and D = 15 unless stated.
- There is no crystal refraction, bezel-lip occlusion (the `lip` parameter exists but was not swept), lens distortion or chapter-ring engraving in the model.
- The affine tilt has a 2–4° floor, and the source of an in-plane squash is ambiguous: a camera tilt and a slightly elliptical dial print look the same.
- Parallax offsets exist only for the 80 photos with round-marker batch results.
- The high-tilt real photos are almost all one source (Phillips, sideways), so pitch towards the 6 is barely represented. That is the direction that matters for the gap.
- The binned-H result pools sources with different lighting.

## Recommendation for the next production change

Evidence supports **replacing the rehaut min/mean RETAKE decision with a dial-plane pose estimate**. It does not support calibrating the rehaut.

1. Compute tilt and minor-axis direction from the 8-marker affine. This is already possible with `GmtRoundMarkerAnalyzer.affine`, but the fit must **exclude tick60**. Use the parallax A/B sign for the camera side.
2. Gate on the **predicted distortion of the specific QC measurement**, not on a single angle:
   - pitch towards the 6 above ≈5–8° → the 12 gap is not judged, or is flagged "gap may read small";
   - sideways tilt → 12 rotation widens its tolerance by the keystone term.

   Centring does not need a pose gate below about 25°.
3. Demote the rehaut sectors to a **consistency check**. For example, RETAKE only when the rehaut and the affine agree on the side. Never let the rehaut alone reject a photo whose affine tilt is under 5°. This would clear the WOS 40411271 false RETAKE.
4. Before any threshold is set, collect a small pitched sequence of one real watch at known angles (0/5/10/15/20° towards the 6 and the 3, phone at a fixed distance). That fixes hm, D and the true α. The corpus has almost no pitch-towards-the-6 photos, and that is exactly the direction that damages the gap.

## Files

- `tools/rehaut-model/rehaut_model.py`, `sweep.py`, `analyse_real.py`, `report.py`
- `tools/desktop-harness/drivers/RehautPose.java`
- `tools/rehaut-model/results/`:
  - `sweep.csv`, `inversion.csv`, `distortion_budget.csv`;
  - `real_genuine_raw.csv`, `real_genuine_pose.csv`, `retake_photos.csv`;
  - `model_asymmetry_vs_yaw.png`, `model_distortion_vs_tilt.png`, `real_H_vs_independent_pose.png`.

Reproduce with:

```
cd tools/rehaut-model
python3 sweep.py
python3 analyse_real.py '<round csv globs>'
python3 report.py
```
