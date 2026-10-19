# Outer-metal marker investigation

Completed findings and matched results: [REPORT.md](REPORT.md).
Android execution, remaining limitations and merge recommendation:
[VALIDATION.md](VALIDATION.md). Numeric evidence is in `results/`.

Baseline: `cf373d205a873648fde46c9704aba177284bc920` / v1.6.3.
The original Date run is `39271752978`. Experimental PR #62 is not merged or used as the baseline.

## Reproduce the exact ten cases

Use JDK 17, the desktop harness OpenCV 4.9.0-0 jar, and Python with
`requests numpy matplotlib opencv-python-headless` installed. From the repository root:

```sh
PYTHON=/path/to/python bash tools/research/marker_audit/run.sh /absolute/audit/output /absolute/verified/images
```

The runner verifies the **catalogued SHA-256 of the image bytes**, fails if any of
the ten cases are unavailable, and fails on missing analysis. JPEG negotiation
is recorded; a different rendition is never substituted. Its shell uses
`set -euo pipefail`, so `tee` cannot hide a failed Java audit.

`prepare_trace.py` generates a renamed copy of the current fitter with only
observational assignments inserted. It does not edit production source. Each
traced result is checked against the real pipeline's polygon, rotation and
coverage to 1e-10. The JSON records all four iterations, actual profile origins,
normals, measured offsets, exclusion reasons, least-squares Jacobians, solve
weights, updated quality weights, residuals and parameter updates. Solve weights
and quality weights differ by the final robust reweighting step and are kept
separate. `render.py` plots the actual observed edge coordinates, reference and
final fitted polygon. Hidden samples are labelled at their search origins;
they do not have invented observations.

Output includes the analysis bitmap, annotated PNGs, JSON traces and target
sample CSVs. Keep bulk photographs and derived image panels outside Git. The
four public dealer originals in `fixtures/` are the sole regression-fixture
exception: CDN negotiation did not reproduce their bytes on GitHub's runner,
so the exact catalogued images are pinned for repeatable Android tests. No
owner photographs are used. See `fixtures/README.md` and `android_fixtures.json`
for provenance; preparation and Android execution both verify the hashes.

## Established baseline

All ten originals were recovered on 2026-10-19. The unmodified desktop pipeline
reproduced **all fields of all 110 marker rows exactly**, including the ten
warnings. The original artifact was recovered independently through the GitHub
connector after the CLI artifact redirect failed.

The two baton traces show nonzero-weight observations on the seconds shaft.
The independent pivot detector's reaches are 0.69 R (9 baton) and 0.58 R
(6 baton), so it does not mask the shaft where the fitter reads it. Alpha99
detects seconds directions of 275.532° and 174.186°, respectively.

The ten findings do not have a single cause. The 22060f9a8f867d35 triangle
rejects every remaining top-edge observation while fitting its two sloping
sides; its warning is radial position, not rotation. The 94ad771e9e2aedde round
marker has no detected seconds contact and needs separate low-resolution /
registration uncertainty. The owner specifically excluded its oblique angle from
normal QC inputs; it remains in the historical audit, not the primary accuracy
cohort. See the completed report and per-photo suitability decisions.

## Matched cohorts

`results/{gmt,nodate,date}-verified.csv` freezes the exact recovered cohort.
Run `TrackDump` at the baseline and corrected commits with the selected model's
own assets, retaining stdout as `before.log` / `after.log`. Never change the
cohort between versions. Then run, for each family:

```sh
python tools/research/marker_audit/compare.py before.csv after.csv cohort.csv /verified/image/root comparison.json \
  --before-log before.log --after-log after.log --suitability reviewed-suitability.csv
```

The comparison checks original hashes, all runner outcomes, every expected hour,
model identity, duplicate aliases and matched keys. Explicit no-pose results are
reported, never silently counted as successful measurements. Full-cohort results
are always retained alongside the reviewed suitable-QC subset. The suitability
CSV is an offline review of pose and the existing 400 px dial-diameter floor,
not a changed production threshold.

`summarise_cases.py before-trace after-trace cases-summary.json` recomputes the
target case statistics and verifies unchanged hashes and homographies. The
uniform-offset sensitivity is a diagnostic counterfactual, not a true-deviation
estimate. Compressed target traces in `results/` preserve all inputs to it.

Run tooling integrity tests with:

```sh
python -m unittest discover -s tools/research/marker_audit -p test_audit.py
```

Android preparation is `python tools/research/marker_audit/fetch_android.py`.
It copies and independently verifies the four pinned public originals into the
ignored instrumentation-assets directory. `MetalMarkerImageRegressionTest`
checks each hash again before the actual Android decoder/native pipeline run.

## Constrained-heap reproduction

`MarkerMemoryProbe.java` runs the full pipeline and exposes the underlying
ClearlyOff exception if the pipeline withholds the result. Compile it against
each revision's desktop-harness classes, then run from the repository root:

```sh
java -Xmx192m -Dwatchalign.model=submariner_126610 \
  -cp "$OPENCV_JAR:$HARNESS_CLASSES:$PROBE_CLASSES" com.watchalign.mobile.MarkerMemoryProbe \
  tools/research/marker_audit/fixtures/ee07ceedbb6f0bd3.jpg
```

Unmodified 1.6.3 fails in full-grid polar allocation; the corrected revision
completes all 11 markers with the same input and heap limit. Probe logs and the
executed Android test XML/measurements are retained in `results/`. The storage
change must also preserve every exported field in the matched cohorts; a
successful run alone is not evidence of numerical equivalence.
