# Watch Align: 12 o'clock top-edge radial calibration proposal (21 Oct 2026)

**Research only. Not integrated into the APK; no frozen thresholds or classification logic changed.**

## Inputs and reproducibility

Research source: [GitHub Actions run 39623345796](https://github.com/Biggregw/watch-align/actions/runs/39623345796), commit `492b486ec53980689fca70dd35198e7239cf552d`, model artifacts `triangle-top-edge-submariner_126610`, `triangle-top-edge-submariner_124060`, and `triangle-top-edge-gmt_126710`. Exact source measurement fields: `genuine.csv`, `synth.csv`, `qc.csv`: `new_radial_pct`, `rpx`. Restrict this candidate's evaluation to measured photos with `rpx >= 200` (existing genuine radius gate); do not adjust that gate.

## Calibrated candidate

Pooled median of 256 measurable, eligible genuine-photo readings: **+0.44015% of dial radius** (95% photo-resampling bootstrap interval approximately +0.4239 to +0.4523% R; does not account for multiple photos of one watch).

**Zero-corrected top-edge radial displacement = new_radial_pct - 0.44015.**

**Candidate universal symmetric radial limit = +/-0.40% R + the existing 0.5-pixel allowance** (equivalently `abs(new_radial_pct - 0.44015) <= 0.40 + 50/rpx` for the experimental *radial-only* assessment, rpx in pixels). This would be a new approved calibration, NOT an existing frozen threshold. Do not silently replace `triInoutR=0.00373`.

No new model-specific tolerance is needed in this candidate. On measured, eligible genuine photos, the family raw medians were:

| Model | Photos measured / eligible | Eligible median raw %R | False radial failures under candidate |
|---|---:|---:|---:|
| Submariner Date 126610 | 112 / 111 | +0.4414 | 0/111 |
| Submariner No-Date 124060 | 113 / 96 | +0.4577 | 0/96 |
| GMT-Master II 126710 | 63 / 49 | +0.3932 | 0/49 |
| **Total** | **288 / 256** | **pooled +0.44015** | **0/256** |

A narrower +/-0.35% R also gives zero false failures on these images, but the worst legitimate measured case lies just 0.004% R below that limit after the allowance. +/-0.40% R provides modest guard-band for that extreme; this is a provisional calibration, not a statistically validated manufacturer tolerance.

## Synthetic radial changes from genuine photographs

Evaluated with the same 0.44015% R correction, +/-0.40% R limit, +0.5 px allowance and radius >=200px:

| Known artificial displacement | Detected outside candidate radial limit |
|---|---:|
| +/-0.5% R | 78 / 188 (41.5%) |
| +/-1.0% R | 188 / 188 |
| +/-1.5% R | 187 / 187 |

Do not interpret the synthetic negatives as proof that a genuine marker fails, or the rate as an independent model benchmark: these edits were generated from the genuine source images used during calibration. Evaluating the offset with five photo-level folds (median from 4 folds, evaluate the fifth) gave zero false failures in each fold at this pre-selected limit, but tolerance selection was not held out.

## Known genuine Starbucks case

`d3a2b077fcf388c3` (Bob's Watches Rolex Submariner Date 126610LV listing 193218): current MetalFit centre radial approximately -0.617% R, RMS 1.63 px, 65% inconsistent edge. New top-edge raw +0.470% R, corrected +0.030% R. New line 78/78 usable profiles, RMS 0.14 px. The annotated close-up is `closeups_genuine/d3a2b077fcf388c3_12_topedge.png` in the Date artifact.

## Deployment and remaining tests

- **Do not OR the new radial fail with the old centre-based radial fail**: the old false radial reading would still reject the genuine Starbucks photo. A controlled substitution policy is needed after validation.
- **Keep existing sloping-side, shape, sideways-position and rotation checks.** Top-edge radial cannot detect malformed or rotated diagonal sides, and reads near the genuine radial position on some known replicas.
- Review independent edge selection: an illuminated bevel edge can be selected instead of the true metal boundary in dim/reflective photographs.
- Hold out **physical watches** (not merely photos) and obtain new verified genuine photos; current 256 images may include repeat physical watches. Verify the same correction and limit on these, and audit all known replicas.
- Only after explicit owner approval: use a new dated calibration baseline recording this method and values, plus before/after classifier regressions. Preserve the 20 Oct frozen baseline unchanged for history.

Detailed recalculation archive and per-photo corrected results were also produced during this research session. This file records the candidate values on the experimental branch only.
