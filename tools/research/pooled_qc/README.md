# Pooled Rolex QC study

Independent, read-only pooled calibration research. No change to main, the APK, production thresholds, model visibility, measurement detectors or genuine references.

Run from repo root: python3 tools/research/pooled_qc/pooled_calibration.py

Sources: existing genuine model-family after-measurements and frozen photo-suitability lists under tools/research/marker_audit/results. The study pools the common markers, excludes No-Date 3 for baton comparability, groups repeated photographs per physical watch, produces family medians and median absolute deviations, saves every observed extreme, and holds out 20% of physical watches.

Quality exclusion is independent of measurement deviation or whether the app returned a green/amber/red result. Candidate limits are exploratory and are never applied automatically. Other families, especially Yacht-Master, need their own genuine or confirmed-defect controls before accepting claims of universal validity.
