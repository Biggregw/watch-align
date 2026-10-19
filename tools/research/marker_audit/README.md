# Outer-metal marker investigation

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
sample CSVs. Keep photographs and derived image panels outside Git. Public
dealer photos are still verified originals; no owner photographs are used.

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
registration review. Full conclusions and matched regression results will be
recorded with the completed investigation.
