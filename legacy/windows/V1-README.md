# Watch Align V1.2

## What is new

V1.2 keeps the model-aware geometry engine but makes the workflow task-led and easier to use.

- **Check my watch**: upload one watch/QC photo and analyse internal geometry.
- **Compare with genuine**: Watch Align automatically uses the best suitable official/stored reference when available.
- **Manual overlay**: opens the original fine-control two-image comparison interface.
- Exact-model profiles currently included: **Rolex 126710BLNR** and **Rolex 124060**.
- Photo pre-check for resolution, sharpness, watch-boundary detection and perspective suitability.
- Model suggestion, official-reference preview, closest-framing reference selection, multi-reference consensus, plain-language area verdicts, overlay opacity, blink comparison, and local recent-comparison history.
- Perspective suitability, visual alignment confidence and measurement reliability are deliberately reported separately.

## Windows build

GitHub Actions builds an ONEDIR Windows package called `WatchAlign-V1-Windows` and a production installer `WatchAlignSetup.exe`. Keep the full extracted portable folder together if using the ZIP.

The V1 Windows launcher opens:

`http://127.0.0.1:8001/v1`

The original manual two-image interface remains available from the **Manual overlay** task.

## Reference library

Open from the V1.2 **Reference library** button or directly at:

`http://127.0.0.1:8001/v1/references`

References are stored in the local runtime directory, grouped by exact watch reference. Each stored image records its source/provenance and whether the user has explicitly checked it as suitable for genuine/reference comparison.

For 126710BLNR, Watch Align can fetch official Rolex Jubilee and Oyster sources on demand, cache the usable stock/reference images locally with provenance, and select the closest photographic geometry automatically. When multiple suitable official references are available, V1.2 can compare against up to three and report consensus so a one-photo mismatch is not treated as a stable watch defect.

The public repository deliberately does not redistribute arbitrary third-party photos as verified genuine.

## Interpreting measurements

Measurements are **relative image geometry**, normalised to detected crystal radius. They are useful for QC and comparison, but they are not calibrated physical millimetre measurements and should not be treated as factory tolerances or proof of authenticity.

Perspective distortion is an image-geometry estimate, not a calibrated physical camera angle. V1.2 uses perspective suitability to decide whether bezel/date precision is safe without falsely downgrading an otherwise strong visual overlay.

## Benchmarking

See `benchmarks/README.md` and run:

```bash
python benchmark_v1.py benchmarks/corpus
```

The public repo contains the harness and corpus format, not third-party QC/gen image sets. A real-world corpus can be maintained locally/private for calibration.
