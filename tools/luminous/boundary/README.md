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

The focused mode checks the complete reconstructed circle, centre, radius, fitted
and alignment-only scores, and OBSERVED withholding for the documented failures
and a previously retained HAND-filtered circle. It also checks nine unchanged
genuine circle/triangle/baton controls, three reviewed outer-metal selection
regressions, three exact-original repeats, and seven contradictory-boundary
abstentions. Batgirl / 9 and / 12 are required by the full audit.

Golden numerical assertions are regression checks on frozen photographs, never
QC thresholds or inputs used by the detector. The three additional boundary
regressions must retain their reviewed correct baseline boundary or safely abstain.
A smaller reported percentage cannot satisfy these checks.

Focused replay verifies the archived measurements/references against the full
audit's frozen input hashes. Its default validation file is
`docs/research/luminous-boundary-review-2026-10-13/validation.json`; pass
`--validation-report` to use a different full audit. Full replay writes all
comparisons and a failed validation record before exiting if photographic gates
fail. Preserve these artifacts and the existing APK when validation fails.

```bash
python tools/luminous/boundary/test_validation.py
python tools/luminous/boundary/verify_build.py \
  --report docs/research/luminous-boundary-review-2026-10-13
```

The build gate requires the full source/test/replay hash contract and evidence
checksums, cross-checks CSV coverage and summaries, and rejects old Boolean-only
reports. Validation checks also remain active when Python runs with `-O`.

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
