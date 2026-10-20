# Rolex shared calibration research

Read-only study of genuine GMT, Submariner No-Date and Submariner Date measurements. Runs from the repository root:

    python3 tools/research/pooled_qc/pooled_calibration.py

Inputs are the previously acquired verified-photo measurement CSVs and pre-existing photographic suitability reviews. Outputs are pooled-calibration-output/REPORT.md and results.json.

Existing pass/fail verdicts never select calibration measurements. Independent physical watches have equal influence; repeated photos of one hour on one watch are collapsed. Only matching 6 and 9 batons are pooled across date/no-date layouts. Proposed robust-MAD limits are exploratory and held-out by watch. An outlying genuine measurement is not automatically bad evidence. Do not merge model availability or detector changes with this study.
