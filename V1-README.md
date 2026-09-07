# Watch Align V1 development build

## What is new

V1 adds a model-aware workflow on top of the current Watch Align engine.

- **QC Analysis**: upload one QC/watch photo and analyse internal geometry without needing a genuine image.
- **Gen Compare**: select the exact model and compare the QC photo to an uploaded or locally stored genuine/reference image.
- Exact-model profiles currently included: **Rolex 126710BLNR** and **Rolex 124060**.
- Perspective diagnostics, per-region confidence, hour-marker geometry, bezel-top estimate, date-window estimate where applicable, annotated analysis and QC report PNG.

## Windows test build

GitHub Actions builds an ONEDIR Windows package called `WatchAlign-V1-Windows`. Keep the full extracted `WatchAlign-V1` folder together and run `WatchAlign.exe` from inside it.

The V1 Windows launcher opens:

`http://127.0.0.1:8000/v1`

The original manual two-image interface remains available at:

`http://127.0.0.1:8000/`

## Reference library

Open:

`http://127.0.0.1:8000/v1/references`

References are stored in the local runtime directory, grouped by exact watch reference. Each stored image records its source/provenance and whether the user has explicitly checked it as suitable for genuine/reference comparison.

The public repository deliberately does not label arbitrary third-party photos as verified genuine. Official Rolex model source pages are recorded in the model metadata:

- 126710BLNR: https://www.rolex.com/watches/gmt-master-ii/m126710blnr-0002
- 124060: https://www.rolex.com/watches/submariner/m124060-0001

## Interpreting measurements

Current measurements are **relative image geometry**, normalised to detected crystal radius. They are useful for comparing alignment within a photo or between appropriately matched photos. They are not calibrated physical millimetre measurements and should not be treated as factory tolerances.

Perspective warnings matter. Strongly off-axis photographs can make correctly positioned features appear misaligned. V1 therefore lowers confidence instead of blindly projective-warping a watch until the pixels look similar.

## Benchmarking

See `benchmarks/README.md` and run:

```bash
python benchmark_v1.py benchmarks/corpus
```

The public repo contains the harness and corpus format, not third-party QC/gen image sets. A real-world corpus can be maintained locally/private for calibration.
