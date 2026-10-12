# Reproduce the isolated circular-boundary correction

Use Java 17, the committed desktop harness and Pillow **12.3.0**. Do not run a
calibrator, derive references, collect photographs or update Android assets.

Download and extract the existing GitHub artifacts:

* run **38222239457**, `WatchAlign-genuine-faceon-132-originals-Codex-ready`;
* run **38295283386**, `luminous-partial-calibration-reproducible-results`.

The latter contains `luminous-calibration-work/` with frozen measurements,
references, original hashes, manifests and processing hashes. Supply the verified
original Batgirl photograph separately for the full audit; its bytes are not
committed. `batgirl_frozen_geometry.jsonl.gz` contains only its baseline measurements.

```bash
python -m pip install pillow==12.3.0
python tools/luminous/boundary/replay.py \
  --originals /path/to/extracted-original-artifact \
  --saved /path/to/extracted-calibration/luminous-calibration-work \
  --work /tmp/watchalign-boundary-full \
  --batgirl /path/to/04_batgirl_original_37828.jpg
```

This verifies all 132 originals, reproduces all 18 archived processing-byte hashes,
runs the native controls, measures the original photographs, scores the unchanged
references, checks exact repeatability/outer and straight-edge preservation, and
writes CSV comparisons and `validation.json` under the work directory. It performs
no reference estimation. Use a fresh work directory.

For a quick automated photographic regression, including the two documented
failures and the newly discovered contradictory 124060 / 4 boundary:

```bash
python tools/luminous/boundary/replay.py --focused \
  --originals /path/to/extracted-original-artifact \
  --saved /path/to/extracted-calibration/luminous-calibration-work \
  --work /tmp/watchalign-boundary-focused
```

The focused mode asserts three reviewed fitted-circle outputs and seven
contradictory-boundary abstentions. It is required by the separate APK workflow.
Golden numerical assertions are regression checks on the frozen photographs,
not new QC thresholds or inputs used by the detector.

To reproduce the *old* failures, check out `8a53c1e5` in a separate worktree, copy
the completed research branch's `LuminousCalibrationStudy.java` into its desktop
drivers, and run `extract` and `score` on the same selected manifests and unchanged
`calibrated_references.json`. The report preserves the exact old/native comparisons.

`LuminousCalibrationStudy replay SNAPSHOT MANIFEST OUTPUT` is an optional
detector-only replay on a previously extracted native pose/ring snapshot. It checks
the source bytes and decoded grayscale hash, regenerates and checks the existing
interference evidence, and passes the stored transformation unchanged to the
actual detector. It never estimates perspective or changes an outer result.

`LuminousCircleEvidence` exports native rectified grayscale and radial profiles
for an original/snapshot/hour. `gallery` renders the exact scored polygons and
extra/missing regions; `-Dwatchalign.study.stage=BEFORE` or `AFTER` labels the output.
No measurement is made from a gallery thumbnail.
