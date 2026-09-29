# Dial-plane pose and per-measurement gating: prototype and evidence (2026-09-29)

Research only. The app's pose policy, thresholds and classifications are unchanged.
This follows `gmt_rehaut_pose_model_2026-09-29.md`, which showed the rehaut is too noisy to give a per-photo angle.

## 1. Proposed pose representation

- **Source.** An affine fit of the round-marker centres (up to 8) to the master layout. Tick60 is excluded because its height and radius differ from the markers'.
- **Squash vector.** s = (1 − b/a)·(cos 2φ, sin 2φ), where b/a is the singular-value ratio and φ is the clock direction of the compressed axis (mod 180).
  - The tilt is acos(b/a). φ is the axis the camera moved along.
  - The vector form averages without the upward bias that acos has near 0.
- **Uncertainty.** A leave-one-marker-out jackknife sd of s, σ. The tilt bounds are tilt_lo/hi = acos(1 − max(0, |s| ∓ 2σ)).
- **Camera side** (which end of the axis). This comes from index-vs-print parallax: round-marker offsets fitted to c + A·cos30h + B·sin30h.
  - (−A, B) points towards the camera.
  - The side counts as known when the parallax projected on φ is ≥ 2 sd. Otherwise both ends are assumed and the worse one is used.
- **By-product: index height.** parallax / tan(tilt) gives it. Across 30 genuine photos over 6° it is **0.028 R (IQR 0.023–0.033)**. The model's nominal 0.02 is slightly low; the stress case uses 0.03.

### Affine confidence requirements (prototype values)

| requirement | value | why |
|---|---|---|
| markers | ≥ 6, in all 4 quadrants (1–2, 4–5, 7–8, 10–11) | fewer, or one-sided, markers leave the squash direction poorly constrained |
| residual (rms / dial R) | ≤ 0.006; above 0.003 the single worst marker is dropped if that removes ≥ 40% of the residual | a clean fit is about 0.001; perspective at 10–15° leaves 0.003–0.005; one misfit marker gives about 0.008 |
| affine scale vs fitted dial R | within 10% | catches gross mislabelling or a wrong dial fit |
| jackknife σ of s | ≤ 0.015 | anything below this is carried into tilt_hi rather than rejected |

## 2. Per-measurement distortion budgets (3D model, `pose_budget.py`)

The table gives the tilt, worst direction, at which pose alone uses **50% / 100% of the CHECK level**. The model uses index height 0.02 R. Camera distance D is in dial radii; for a 40 mm watch, D = 6 is about 9 cm and D = 15 is about 22 cm.

| measurement | pose component that moves it | D = 6 | D = 15 | D = 50 | stays enabled when others are withheld? |
|---|---|---|---|---|---|
| round-marker centring (0.15) | parallax, any direction | 21° / >30° | 28° / >30° | >30° | **yes**, practically pose-immune |
| round-marker size (0.12) | perspective, near side larger; only close up | 17° / >30° | >30° | >30° | yes, except in very close shots |
| 12 gap (0.094 → 0.070) | **pitch only**: camera towards the 6 shrinks it, towards the 12 inflates it, sideways ≈ 0 | 7.1° / 13.7° | 7.5° / 14.3° | 7.7° / 14.7° | yes, if the pose is sideways |
| 12 rotation (2.0° lean, top edge not turning) | yaw keystone plus oblique planar shear; pure pitch = 0 | 5.9° / 10° | 8.3° / 12.7° | 9.9° / 14.3° | yes, if the pose is pure pitch |
| 12 off-centre (0.10) | yaw parallax | 18.6° / >30° | 19.9° / >30° | 20.7° / >30° | yes |
| 6 baton centring (0.10) | yaw parallax | 15.1° / 28° | 15.9° / 30° | 16.4° / >30° | yes |
| 6 baton rotation (2.0°) | yaw keystone plus oblique shear | 6.1° / 10.2° | 8.4° / 12.8° | 10° / 14.3° | yes, if the pose is pure pitch |
| 3/9 baton centring (0.10) | pitch parallax | 15.1° / 28° | 15.9° / 30° | 16.4° / >30° | yes |
| 3/9 baton rotation (2.0°) | pitch keystone plus oblique shear | 6.1° / 10.2° | 8.4° / 12.8° | 10° / 14.3° | yes, if the pose is pure yaw |

The 12 top edge never turns with the pose. A pose-induced lean therefore always falls into the app's skew-only branch (SKEW_ONLY_MIN_DEG = 2.0), which is why 2.0° is used as the 12 level.

**Un-squash finding.** The round markers are measured after undoing the dial ellipse; the 12 and the batons are measured in the raw image. Measuring the 12 and baton rotations after the same un-squash removes the planar-shear term and needs **no pose estimate**:
- D = 15: the tilt that uses the whole rotation budget rises from 12.8° to over 30°;
- D = 50: 14.3° becomes over 30°;
- D = 6: no benefit, because keystone dominates there.

Results files: `results/pose_budget*.csv` and `results/pose_budget_limits*.csv`.

## 3. Proposed selective gating

1. Compute the affine pose. If it fails the confidence requirements, keep **today's behaviour**, including the rehaut rule.
2. For each measurement, predict the pose-induced error at tilt_hi:
   - use the known camera side, or the worse of both sides if unknown;
   - nominal D = 10 and index height 0.02, with a stress case of D = 6 and 0.03.
3. **Withhold only the measurements whose prediction is ≥ 50% of their CHECK level.** The others stay judged.
4. The rehaut becomes a secondary check. It may force a photo-wide RETAKE only when the affine pose is unavailable, or when the affine agrees (tilt_lo ≥ 5°). It never overrides a confident near-frontal affine.

The prototype is `pose_policy.py`. Its outputs are `results/pose_policy_photos.csv`, `pose_policy_measurements.csv` and `pose_policy_summary.txt`.

## 4. Comparison with the genuine corpus (127 photos)

The corpus is 109 corpus photos plus 18 Bob's Watches photos, with verdicts from the alpha62-review build.

- **Current labels:** GOOD 26, CORRECTABLE 79, RETAKE 9, UNASSESSABLE 13.
- **Confident affine pose:** 76 of 127 (GOOD 20, CORRECTABLE 51, RETAKE 5).
  - Among these, the tilt median is 4.4° (IQR 2.6–10) and the median tilt_hi is 9.0°.
  - The camera side was known for 39 of them.
- **No affine pose:** 51 photos (47 had fewer than 6 markers or none; 4 failed on residual). **This is the main coverage limit.**

Per measurement, where the current app judges or withholds against what the proposal would do:

| measurement | judged, still allowed | judged → **withhold** | held for pose → **allow** | held for pose, still held | no affine pose |
|---|---|---|---|---|---|
| round centring | 70 | 0 | 5 | 0 | 27 |
| 12 gap | 47 | 9 | 5 | 0 | 51 |
| 12 rotation | 31 | 9 | 1 | 4 | 51 |
| 6 baton | 31 | 10 | 1 | 4 | 51 |
| 3/9 baton | 30 | 13 | 5 | 0 | 51 |

### A. Current system rejects usable measurements (the RETAKE photos)

| photo | affine tilt (hi) | camera side | rehaut min/mean | released by the proposal (budget used) |
|---|---|---|---|---|
| WOS 40411271 | **2.0° (4.1°)** | – | 0.44 | everything: round 0.08, gap 0.11, rotation 0.23, 6 0.11, side 0.05 |
| Phillips 151263 | 13.4° (15.1°) | towards 3 | 0.36 | round 0.26, gap 0.10, side 0.04 (6 and 12 rotation stay held) |
| Phillips 180697 | 10.9° (14.1°) | towards 3 | 0.29 | round 0.24, gap 0.08, side 0.05 |
| Phillips 180859 | 11.7° (13.8°) | towards 3 | 0.27 | round 0.24, gap 0.06, side 0.08 |
| Phillips NY080121_35 | 16.9° (18.7°) | towards 3 | 0.82 | round 0.34, gap 0.31, side 0.38 |

The other four RETAKE photos have no affine pose (1–2 markers, or no dial fit), so they keep today's behaviour.

**WOS 40411271 reproduced.** The app reports RETAKE with the reason "local rehaut sectors show one cardinal side collapsing". The sector widths are:

| sector | width | coverage |
|---|---|---|
| 12 | 13 px | – |
| 3 | 20 px | 0.53 |
| 6 | 15 px | – |
| 9 | 6 px | 0.79 |

min/mean is 0.444. The other cues do not support a tilted camera:
- the global rehaut harmonic gives min/mean 0.69;
- the dial-edge ellipse ratio is 0.9985, which is 3.1°.

The marker affine drops the misfitted 5 automatically: its radius was 16.5 px against about 23.5 px for the rest, and the residual falls from 0.0083 to 0.0006. The remaining fit gives **2.0°, with an upper bound of 4.1°**. At that bound every measurement uses ≤ 23% of its budget (≤ 35% under the stress case). Under the proposal, the bad 9 sector cannot force RETAKE on its own.

### B. Current system trusts measurements the proposal would withhold (28 photos, all currently CLEAR, no CHECK hidden)

- **9 photos with real tilt ≥ 8°**, mostly Phillips shot sideways at 13–16° towards the 3. Here the 6 rotation and 12 rotation are withheld because keystone plus shear could reach 1–2.6° at D = 10.
  - Examples: 159236, 223302, CH080120_2, 137349, 159233, 210072, CH080120_75, vmbUDwy/06, 146853 (the gap, camera towards the 6 at 9.8°).
  - Their measured rotations are small, which suggests the auction photos were taken from further away than D = 10.
- **19 photos with a small tilt (3–8°) but a wide uncertainty (tilt_hi 8.2–11.4°).** Examples: six official Rolex renders (truly frontal, reading 3.4–4.5°), 8 Bob's photos, official_rolex_2026/00, WOS 40410010.
  - These withholds come from **affine noise, not pose**. With the camera side unknown, the worst case is taken at tilt_hi.

## 5. Findings

1. The per-measurement structure is real. The 12 gap depends only on pitch. The 12, 6 and 3/9 rotations depend on the orthogonal keystone plus oblique shear. Centring is nearly pose-immune. A photo is rarely unusable for everything:
   - three of the four real-tilt RETAKE photos (Phillips, 10–13°, sideways) keep a usable gap, round markers and side baton;
   - NY080121_35 (16.9°) keeps its round markers, gap and side baton only at a moderate budget.
2. The marker affine settles WOS 40411271 (2.0°) and confirms the Phillips yaw.

   It is **not yet precise enough to gate the most sensitive measurements**:
   - Its effective floor is about 3–4° with a 2σ bound of about 9°. Frontal renders read 3.4–4.5°.
   - The 12 gap and the rotations use half their budget at 6–8°. The proposal would therefore withhold clean near-frontal photos (group B, second bullet).
3. Coverage: 40% of genuine photos have too few confidently placed markers for an affine pose.
4. The un-squash of the 12 and baton rotations is a pose-free improvement for D ≥ 15. It helps little for close phone shots.
5. Camera distance D is the unknown that matters for the rotations. Index height is now measured at about 0.028 R.

## 6. Recommendation (for review, not implemented)

1. **First production step, low risk:** stop letting the rehaut sector alone force RETAKE when a confident marker affine has tilt_hi < 5°. On this corpus that affects WOS 40411271 only. The rehaut stays in charge whenever the affine is unavailable.
2. **Second step, pose-free:** measure the 12 and baton rotations after the same dial un-squash the round markers use. Validate it on the corpus before it is used for gating.
3. **Hold the per-measurement gating** until the pose precision improves:
   - combine the marker affine with the dial-edge ellipse (an independent planar cue that is already fitted);
   - weight the markers by fit quality;
   - consider adding the 6/3/9 baton centres;
   - then re-run this comparison, aiming for a frontal floor ≤ 2° and tilt_hi ≤ 5° on the renders.
4. Take a short known-angle photo series of one watch (0/5/10/15/20° towards the 6 and towards the 3, at about 10 and 25 cm). That would pin down D and validate the budgets. There are almost no pitch-towards-the-6 photos in the corpus, and that is the direction that affects the 12 gap.

## Limitations

- The model has no refraction, lens distortion or bezel occlusion.
- Batons are modelled at the same index height as the markers.
- The camera side is known for only half the photos with a valid affine pose.
- The "held for pose" classification treats every UNASSESSABLE verdict on a RETAKE or UNASSESSABLE photo as pose-caused.
- Bob's photos had no alpha62-review batch run before this one. They are included with the same build.

## 7. Follow-up (alpha63): what was shipped and what was dropped

- **Shipped:** the rehaut-only RETAKE is overruled when the round-marker layout confidently shows the camera near straight on (tilt_hi < 5°), in `GmtMarkerPose` and `GmtHumanPosePolicy`.
  - The requirements are as in section 1, using cleanly traced markers only.
  - On 319 corpus photos plus 18 Bob's Watches photos, only WOS 40411271 changes. It goes from RETAKE to CORRECTABLE, and its 12, its 9 and seven round markers are now judged, all CLEAR.
- **Dropped:** measuring the 12 and baton rotations after undoing the fitted dial-edge ellipse. It was tried on the same corpus and did not reduce the rotations of genuine photos, which should read about 0:

  | rotation | median \|rotation\| before → after | genuine readings ≥ 2° before → after |
  |---|---|---|
  | 12 | 0.53° → 0.52° | 9 → 9 |
  | 6 | 0.39° → 0.34° | 2 → 2 |
  | 9 | 0.30° → 0.36° | 1 → 4 |

  It also produced a new genuine 12 CHECK: Phillips 224135 went from 1.7° to 3.4°. The model's shear is real, but the dial-edge ellipse is not an accurate enough measure of it. At a ratio of 0.97, fit noise alone gives about 1.7° of false shear.
  - The marker affine has a squash sd of about 0.0016, roughly 0.1° of shear, so it is the candidate source if this is retried.
