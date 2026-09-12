# Watch Align V1 benchmark corpus

This folder defines the regression format for real-world Watch Align testing.

## Purpose

V1 changes should improve geometric truth, not merely increase image similarity. Real QC/reference pairs can be added locally or in a private test branch without committing copyrighted or personally supplied images to the public repository.

## Corpus layout

```text
benchmarks/corpus/
  case-name/
    candidate.jpg
    reference.jpg          # optional for QC-only cases
    expected.json
```

Example `expected.json`:

```json
{
  "model_ref": "126710BLNR",
  "mode": "gen",
  "notes": "Front-on QC photo with known small bezel offset",
  "max_perspective_mismatch_deg": 4.0,
  "expected_confidence": ["medium", "high"],
  "marker_median_abs_error_max_deg": 1.0
}
```

## Recommended cases

Include straight-on, mildly angled and strongly angled photographs, different hand positions and dates, varied lighting/reflections, genuine-vs-genuine pairs, and factory QC photos for each supported model.

Do not encode an assumed physical millimetre tolerance unless it comes from a defensible measurement source. Watch Align's current marker/date outputs are relative image-geometry diagnostics.

Run:

```bash
python benchmark_v1.py benchmarks/corpus
```

The command writes a JSON summary and exits non-zero when an explicit expected constraint fails.
