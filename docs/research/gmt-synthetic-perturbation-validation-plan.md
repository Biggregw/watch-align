# GMT synthetic perturbation validation plan

Status: ACTIVE

## Purpose

Validate the sensitivity and selectivity of the 13 frozen GMT geometry features without relying on a catalogue of replica-watch defects.

The experiment starts from the provisionally frozen genuine geometry baseline and applies controlled, known geometric perturbations. Because the injected displacement is known exactly, this provides stronger detector ground truth than subjective image-review labels.

This phase does not change `genuine-baseline-v1.json` and does not define production pass/fail tolerances.

## Frozen features

### 12 marker
- `h12.stage3_apex_r_simple`
- `h12.stage3_centre_r_projective`
- `h12.stage3_base_r_projective`
- `h12.stage3_axis_incidence_canonical`
- `h12.stage3_centroid_tangential_offset_canonical`

### 6 marker
- `h06.centre_r`
- `h06.centre_t`
- `h06.axis_residual_deg`
- `h06.radial_span`

### 9 marker
- `h09.centre_r`
- `h09.centre_t`
- `h09.axis_residual_deg`
- `h09.radial_span`

## Experimental design

Use genuine/reference geometry as the starting state. Inject one controlled perturbation at a time while holding unrelated geometry fixed.

For each marker test:

1. radial displacement inward and outward;
2. tangential displacement in both directions;
3. clockwise and anticlockwise rotation;
4. radial span/size change where the feature supports it;
5. for the 12 triangle, independent apex/centre/base proportional perturbations where geometrically meaningful.

Use a sweep from zero through small, medium and deliberately obvious perturbations. The implementation should choose increments fine enough to reveal response curves rather than testing only a single arbitrary displacement.

Run symmetric positive and negative perturbations wherever the geometry permits.

## Selectivity requirement

The primary success criterion is not merely that a measurement changes. It should respond predominantly to the physical perturbation it represents.

Examples:

- 6-marker rotation should strongly affect `h06.axis_residual_deg` while producing minimal false response in unrelated 9-marker features.
- 6-marker tangential translation should strongly affect `h06.centre_t` without masquerading as radial movement.
- 12-marker tangential movement should affect the corresponding 12 tangential/axis relationship.
- 12 radial/proportional perturbations should produce coherent responses in apex/centre/base radial features.

Quantify cross-talk between features.

## Robustness layer

After canonical-coordinate tests pass, repeat representative perturbations through realistic image conditions so the full measurement pipeline is exercised rather than only testing formulas.

Perturb nuisance variables independently from the defect geometry:

- modest perspective/pose variation;
- small image rotation;
- scale changes;
- realistic resampling/compression;
- mild blur;
- modest illumination variation where supported by the existing test harness.

Do not manually rescue failed registration. Record failures.

## Controls

Include zero-perturbation controls for every run.

Where possible, run perturbations across multiple genuine/reference instances rather than a single canonical specimen. Preserve physical-watch identity so repeated images are not treated as independent watches.

## Outputs

Produce machine-readable raw results containing at least:

- source/reference identity;
- target marker;
- perturbation type;
- injected magnitude and direction;
- nuisance transformation parameters if any;
- all 13 measured frozen features;
- signed feature response from the zero-perturbation control;
- registration success/failure.

Produce a summary report with:

- response curve for every target feature;
- monotonicity/directionality;
- minimum perturbation reliably measurable above genuine/repeatability noise;
- cross-talk matrix between injected perturbations and measured features;
- robustness under realistic pose/image degradation;
- registration failure rate;
- classification of each feature as `candidate_qc_signal`, `diagnostic_only`, or `insufficient_evidence`.

## Visually meaningful threshold

Detector sensitivity is not the same as user-facing significance.

This experiment may determine the smallest displacement the detector can measure reliably, but it must NOT automatically turn that value into a QC threshold.

A separate validation step must establish when a geometric difference becomes visually meaningful in normal QC imagery. Production reporting thresholds must be at least as conservative as both detector reliability and visual significance require.

## Acceptance criteria

A feature can advance as a `candidate_qc_signal` when:

1. it was stable in genuine leave-one-watch-out validation;
2. it responds monotonically and directionally to its intended controlled perturbation;
3. unrelated perturbations do not create unacceptable cross-talk;
4. the response survives realistic pose/image nuisance conditions at useful magnitudes;
5. zero-perturbation genuine controls remain consistent with the frozen genuine baseline.

## Implementation constraints

- Do not modify `genuine-baseline-v1.json`.
- Do not retune genuine geometry from perturbation results.
- Do not define production pass/fail thresholds in this phase.
- Reuse the same Stage 3 measurement definitions used for the genuine baseline.
- Keep research outputs separate from Android production QC behaviour.
- Commit intermediate results so failed hypotheses remain visible.

## Next execution step

Build a deterministic perturbation runner around the existing Stage 3 geometry tooling. Start with canonical tests for 12/6/9 radial, tangential and rotational perturbations plus zero controls. Once feature response/selectivity is verified, add the image-space robustness layer.
