# 12-triangle metric validation

This phase does not change the Alpha52 manual workflow or any genuine-control boundary. The production result now exposes `rawRectified` measurements separately from `classification`. Existing result fields remain compatible and contain the same values as before.

## Repeatability

`TriangleMetricValidationTest` runs 2,000 deterministic perturbations with each of the five manual feature points moved independently by up to 0.75 px and each perspective anchor by up to 1.0 px. It prints median, 95th-percentile and maximum absolute change for base-to-60, apex-to-crown and rotation. This quantifies operator-placement noise plus residual perspective-anchor error without using those results to tune thresholds.

## Control-set runner

Put genuine and replica images plus `controls.csv` in the app-specific external directory shown by a first run of `TriangleControlSetDeviceRunnerTest`. CSV columns are:

```text
group,id,image,a12x,a12y,a3x,a3y,a6x,a6y,a9x,a9y,leftx,lefty,rightx,righty,apexx,apexy,minute60x,minute60y,crownx,crowny
```

Every row must use manually corrected anchors and feature points for its referenced image. Run:

```sh
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.watchalign.mobile.TriangleControlSetDeviceRunnerTest
```

The runner applies `Triangle12RelationalMetric.measureRectifiedRaw`, the same implementation used by the app, and writes:

- `measurements.csv`: one raw measurement and frozen classification per image
- `distributions.csv`: count, mean, sample standard deviation, median, minimum and maximum by group and metric

Compare genuine-versus-replica separation with the repeatability p95 values. Do not revise the genuine-control ranges until the control set contains enough independently photographed watches and the between-group difference is consistently larger than operator and perspective sensitivity.
