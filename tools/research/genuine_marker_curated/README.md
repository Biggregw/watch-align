# Frozen curated genuine marker study

Run `.github/workflows/genuine-marker-curated-study-20261012.yml` on
`research/genuine-marker-study-curated-20261012`. It downloads only run
`38222239457` / `WatchAlign-genuine-faceon-132-originals-Codex-ready`, on the
GitHub runner. No source URL is fetched, no original image is committed, and no
APK is built. Production files are checked against PR #52 source
`593966433079d04816e98f78da5b66abd1f4553d` before and after measurement.

`prepare.py` verifies all 132 SHA256 values and family counts (46/31/55), matches
every file to its existing genuine catalogue and resolves the 124060 placeholder
IDs. It selects one original per recorded watch before looking at any numerical
result: largest minimum original dimension, then pixel count, then SHA256. Repeats
remain in the provenance audit. Listing groups are not serial-verified physical
identities; inferred groups, existing shared-image flags and cross-vendor ambiguity
are reported rather than silently promoted to independent manufacturing samples.

`GenuineMarkerStudy` runs the unmodified `Alpha99Pipeline`, exports every selected
marker and reliability component, and uses its saved `MarkerMismatchResearch`
polygons. Shape distributions admit only existing trusted area measurements and
exclude recorded/measured shared-dial suspects. The existing shared-dial diagnostic
is reused (8 common markers within 0.0005 R); it is an identity alert, not a new QC
rule or proof of physical-watch equality. There is no overall watch score.

The image gallery ranks the two highest trusted baton/round scores and highest
withheld diagnostic examples separately for each family/shape. All triangles stay
withheld. Yellow, cyan, orange and magenta use the exact polygons/regions from the
calculation. Source crops are aspect-preserving display resizes, never original
inputs or additional watches. Full-original hashes and source links are retained.

Results are published under `docs/research/genuine-marker-curated-2026-10-12/` on
this research branch and as a small separate report/outlier artifact. CSV and
selected derived crops can be reviewed through Git without downloading the
150 MB original archive. The workflow uses `actions: read` for source retrieval
and `contents: write` only to publish these research outputs on this branch.

The post-run source review quarantines two curated 126710BLNR claims whose
recorded listing URLs AND original image filenames reference 116710. It retains
all frozen measurements and app statuses, but excludes these unresolved models
from the genuine 126710 distribution. The original claimed-model distribution
is separately preserved. Generated `auto_*` / `hv_page_*` identities are flagged
as provisional. This is a provenance correction, not a detector/geometry change.
The standalone review workflow can reprocess already-published numerical results
without downloading images or repeating the measurement. Compact per-model CSVs
are small enough to inspect directly through GitHub.

Local isolated checks (Pillow needed for the input-selection module):

```sh
python -m unittest discover -s tools/research/genuine_marker_curated -p 'test_*.py' -v
```

The output report records limitations and lessons. Maxima/percentiles describe
this photographed sample, not Rolex manufacturing tolerances or replica acceptance
bands. Direct metal-to-tick gaps are not available in the frozen pipeline and are
not invented; its radial spacing estimate/model-circle diagnostics remain separate.
