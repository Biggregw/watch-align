# Watch Align 1.2.3

## Comparison responsiveness

- Bound expensive affine refinement to 720-pixel working images and 150 iterations, restoring transforms to full-resolution coordinates before applying existing geometry guards.
- Run UI comparisons in a separate process with stage updates, elapsed time, cancellation, and a three-minute deadline.
- Reject overlapping comparisons so repeated clicks cannot pile up image-processing work.
- Restore controls and show an error when reference loading, processing, or polling fails.
- Preserve multi-reference consensus and measurement reliability checks.

## Repository maintenance

- Refresh the README and collect historical release notes under `docs/releases/`.
- Ignore generated UI and test artifacts.
- Build and test every pull request; publish releases only through an explicit workflow action.

The legacy synchronous analysis API remains available for compatibility. This release does not change its cancellation contract. Image measurements are not proof of authenticity.
