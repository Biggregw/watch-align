# Watch Align 1.6.7 — approved 12 o'clock top-edge radial calibration (21 October 2026)

**Approved by the repository owner on 21 October 2026. Supersedes ONLY the 12 o'clock radial measurement whenever a usable calibrated top-edge fit is available.** The 20 October frozen baseline remains preserved as the historical record of the prior production engine.

## Calibration and units

- Scope: GMT-Master II 126710, Submariner No-Date 124060, Submariner Date 126610; **12 triangle radial position only**.
- The raw top-edge displacement `TriangleTopEdge.Result.radialPct` is percent of canonical dial radius, positive outward.
- **Pooled, photo-weighted genuine median zero offset = +0.44015% R**. Calibrated radial displacement = raw − 0.44015% R.
- **New strict genuine radial limit = ±0.40% R**, plus the existing 0.5-pixel allowance once: `abs(calibrated radial) <= 0.40 + 50/rpx`.
- Replica-QC radial limit remains **±1.25% R** (plus existing 0.5-pixel allowance), and all other genuine, replica-QC and shape/rotation/gap/sideways values remain unchanged.
- No changes to the marker outlines, minute-track/dial registration, photo-wide exclusions or the independent triangle gap measurement.

## Integration / fallback policy

- A top-edge measurement is eligible for radial substitution only when the existing dial-radius quality gate is met (`rpx >= 200`), the fit returns a finite measured result, and no second competing edge is reported as ambiguous.
- On eligible readings, the calibrated top-edge result **replaces** (it is not OR-ed with) the old whole-triangle-centre radial measurement in both displayed QC and strict genuine scoring.
- The strict genuine limit for that substituted reading is 0.40% R. The old centre-derived radial retains its historical **0.373% R genuine limit** when the top-edge method is not eligible. The source constant has not been overwritten.
- When the new top edge cannot be measured (including insufficient coverage, competing edge or low resolution), the existing whole-outline radial result remains the fallback; it may still be affected by a poor diagonal fit.
- The old whole-outline method continues to supply triangle sideways position, shape-related evidence and rotation. The separate minute-track gap check is preserved.
- A good top edge does **not** suppress other credible genuine or replica-QC failures. Genuine, Replica and Can't Determine are alignment screening results, not authentication.

## Reference population and limitations

Source: [three-model experimental run 39623345796](https://github.com/Biggregw/watch-align/actions/runs/39623345796), `tools/research/triangle_top_edge/REPORT.md` and `tools/research/triangle_top_edge/CALIBRATION_PROPOSAL_2026-10-21.md`.

| Genuine family | Eligible genuine photographs (dial radius >=200 px) | Median raw %R | Newly outside candidate radial limit |
|---|---:|---:|---:|
| Submariner Date 126610 | 111 | +0.4414 | 0 |
| Submariner No-Date 124060 | 96 | +0.4577 | 0 |
| GMT-Master II 126710 | 49 | +0.3932 | 0 |
| Total | **256** | Pooled +0.44015 | **0** |

Synthetic movement recovery within the research population: 78/188 at ±0.5% R; 188/188 at ±1.0% R; 187/187 at ±1.5% R. These tests reuse source genuine photos, so they are *not* independent hold-out validation or a manufacturer specification. The correction pools photographs, not unique physical watches.

The known genuine 126610LV photo `d3a2b077fcf388c3` changes from an old rough-fit radial −0.617% R to calibrated top-edge +0.030% R (78/78 agreeing edge profiles, 0.14px RMS). It may still be marked uncertain by other whole-outline measurements.

**Known limitations:** a darkened rim can be mistaken for the lume boundary; consistent bezel reflections can bias edge selection; top-edge alone cannot detect deformed triangle sides, and the old rotation/shape fit remains necessary. Fallback can retain old false-positive modes. Run genuine and replica regression on each deployment; assess newly collected, physically independent genuine watches before any future threshold changes.

## Change control

- Historical frozen baseline: `docs/CALIBRATION_BASELINE_2026-10-20.md`, unchanged.
- Implemented values: `TriangleTopEdge.CALIBRATION_OFFSET_PCT=0.44015`, `TriangleTopEdge.GENUINE_RADIAL_LIMIT_PCT=0.40`. The old `ClearlyOff.Limits.genuine().triInoutR=0.00373` remains for fallback.
- Owner explicitly approved calibration and promotion on 21 October 2026.
- Any future alteration of the correction, detection method, eligibility or tolerance requires explicit approval and a new documented baseline.
