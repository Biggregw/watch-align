# Watch Align 1.2.4

- Automatically match the candidate's in-plane angle to the genuine reference across a full 360 degrees, replacing the previous 15-degree limit when dial detail is sufficient.
- Correct the sign of the legacy polar rotation estimator to match OpenCV's rotation convention.
- Use radial dial detail to distinguish repeating hour markers; ambiguous orientation is flagged for manual review and cannot produce high visual confidence.
- Show the applied rotation and open the aligned overlay after comparison.
- Rotate the source photograph rigidly for annotation and measurements, with an expanded canvas; do not measure the appearance-matched or perspective-warped layer.
- Preserve the cancellable worker, timeout, and performance improvements from 1.2.3.

Validation includes clockwise/anticlockwise, sideways, near-180-degree, size/center changes, different hands, featureless and rotationally symmetric cases, and full-pipeline transform composition. Rotation matching does not guarantee that different camera viewpoints or perspective distortion can be perfectly reconciled.
