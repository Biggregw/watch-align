# GMT empirical genuine-baseline integration + blind replica validation — 2026-09-23

**Branch:** `feature/gmt-genuine-baseline-integration`
**Status:** empirical profile integrated and tested; blind defect-discrimination validation **not completed** (external blocker, see D/G).

This report covers integrating the completed empirical genuine-baseline research
(`docs/research/gmt-genuine-baseline-validation-report.md`, commit `434b492d`) into the
Watch Align proportion/QC architecture, and attempting to validate it against known
RepTimeQC replica defects. It does not redo, re-run, or revise that prior research;
the genuine-side numbers here are cited from it unchanged.

## A. What was tested

Two separate things:

1. **The deviation-layer code itself** (does the arithmetic and classification behave
   correctly, independent of any specific watch data) — fully tested, 78 new/updated unit
   tests plus 9 pre-existing `qc`-package tests, all passing.
2. **Whether the empirical genuine profile can distinguish known RepTimeQC replica
   defects from genuine variation** — attempted but not completed. All 9 predeclared
   control watches (`docs/research/gmt-replica-ground-truth-control-set.csv`) failed to
   fetch; see D.

## B. Genuine baseline (cited, not re-derived)

Per the frozen research report at commit `434b492d` (`research/gmt-pinned-trigger`):
18 independent genuine 126710BLNR watches sourced, **5** independent watches passed the
`<=10°` pose gate and produced the primary population (a 6th/7th watch contributed partial
metrics to some 9-watch figures noted in that report). Physical watch is the unit of
independence throughout; repeated photographs of one watch were never counted as
separate watches.

This is encoded as a versioned artifact, kept separate from ideal/master geometry:

- `docs/research/gmt-genuine-baseline-profile-v1.json` (canonical)
- `android/app/src/main/assets/genuine-profiles/126710BLNR-gmt-marker-baseline-v1.json`
  (byte-identical Android-loadable copy)

13 metrics, each with `n`/`median`/`mad`/`p10`/`p90`/`status` (`PROMISING` or
`DIAGNOSTIC_ONLY` — no metric starts as `SUPPORTED`; that requires blind-defect
validation this run could not complete). `profile.status = "PROVISIONAL"`.

## C. Known-defect controls (attempted)

`docs/research/gmt-replica-ground-truth-control-set.csv` predeclares 9 RepTimeQC
control watches (`rep-001`..`rep-009`) with human-reviewed labels spanning 12-marker
rotation/lateral shift, 6/9-marker tilt, a negative control (`markers_aligned_negative_control`),
and one reviewer-rejected false-positive label (`pose_confounded_false_positive`) —
i.e. the set already includes a case explicitly expected to show *no* real defect, not
only positive cases.

`tools/research/run_replica_control_set.py` runs the same frozen measurement pipeline
used for the genuine baseline (pose acquisition, `marker_features`, Stage-3 triangle
features) against each control's source images, blind — it computes no deviation and
makes no judgment; that is deliberately kept in the separate `analyze_replica_control_set.py`
step so the raw measurement is never touched by the interpretation that consumes it.

**Independent genuine watches used: 5** (cited from B, none re-measured this run).
**Independent known-defect replica/control watches actually measured: 0 of 9 attempted.**

## D. Which metrics separated defects

None could be evaluated. Every one of the 9 controls failed at the image-fetch step,
before any measurement was possible — see `docs/research/gmt-replica-control-set-results/control_status.csv`
(`fetch_log_tail` column) and `discrimination_analysis.csv`.

**Root cause (confirmed, not assumed):** `gallery-dl`'s Reddit extractor returns
`gallery_dl.exception.AbortExtraction: "You've been blocked by network security."` for
every control URL. This was reproduced identically from two independent network paths:

1. The GitHub Actions CI runner (workflow run `35911266191`).
2. This session's own sandboxed outbound network path (direct `curl` to
   `https://www.reddit.com/.../<post>.json` returned a 403 at the TLS-tunnel level).

This is Reddit's own anti-bot/datacenter-IP block, not a bug in this project's fetch
code — the same `gallery-dl` mechanism that already works for this project's genuine-corpus
fetching (`datasets/126710BLNR/fetch_images.py`) hit it here specifically. No Reddit API
credentials (OAuth client id/secret) are available in this environment to attempt an
authenticated alternative, and none of the CSV's 9 controls or the 7 additional
"expansion" candidates in `docs/research/gmt-replica-control-set-expansion-2026-09-23.md`
have a non-Reddit (e.g. direct imgur) URL to fall back to — every predeclared source is a
`reddit.com/r/RepTimeQC/comments/...` permalink. **This is treated as a genuine external
blocker per the task's own completion criteria** (network-level block, unsolvable from
this repository or environment, not a code defect).

## E. Which metrics did not separate defects

Not applicable — no control produced a measurement to evaluate against.

## F. Which metrics are safe to expose now

**None.** Every metric in the profile remains `PROMISING` or `DIAGNOSTIC_ONLY`; none is
`SUPPORTED` (the only status `DefaultFindingPresenter` will ever resolve to a
`MeasurableDeviation`). In the current, real evidence state, every comparison the app
could run today resolves to `Inconclusive` (metric not yet validated) — there is nothing
a `MeasurableDeviation` overlay could show without fabricating validation that has not
happened. Item 6/7's instruction not to clutter the UI with research diagnostics, and
not to promote unsupported findings, is honoured by **not** wiring the deviation layer
into the live capture/overlay flow yet.

## G. What additional evidence is needed

1. A working, credentialed (or otherwise unblocked) fetch path for the 9 predeclared
   RepTimeQC controls (and/or the 7 expansion candidates) — e.g. Reddit OAuth API
   credentials for `gallery-dl`'s authenticated mode, or manually-supplied local images
   for the same predeclared, already-labelled controls. Without at least one control's
   images, item 4/5 (the "critical" blind validation) cannot proceed at all.
2. Once any controls are measurable: run `run_replica_control_set.py` then
   `analyze_replica_control_set.py` (both already built and tested against the live CI
   pipeline this run, just starved of input) to get real `mad_multiples`/evidence-strength
   numbers per control per metric.
3. Repeatability data (`repeatabilityError` in `MetricBaseline`) is still `null` for
   every metric — no genuine watch in the baseline has more than one usable low-tilt
   image yet, so `standardizedDeviation()` is unused in practice; only the MAD-multiple
   path is currently live.

## H. Recommended next validation step

Do not retry the same Reddit fetch mechanism again without a credential or source
change — it has now failed identically on two independent network paths for the same
reason. Two concrete unblocking options, in order of effort:

1. Ask a human to manually download the images for the 9 already-labelled controls
   (their URLs and labels are already in `docs/research/gmt-replica-ground-truth-control-set.csv`)
   and supply them as local files; `run_replica_control_set.py`'s `local_image_candidates()`
   path would consume them directly with a small path-source change, no researcher-judgment
   changes needed.
2. Register a Reddit API application (`client_id`/`client_secret`) and configure
   `gallery-dl`'s OAuth mode, if that is an acceptable operational step for this project
   going forward.

Once even one or two controls are measurable, re-run the existing pipeline exactly as
built; do not lower the evidence bar (item 6) to compensate for a small n.

## Files

- `docs/research/gmt-genuine-baseline-profile-v1.json` / Android asset copy — versioned empirical profile (unchanged this session; carried from the prior phase, cited above).
- `android/app/src/main/java/com/watchalign/mobile/baseline/` — `ReferenceComparison` (extended: `absoluteDeviation()`, `madMultiples()`), `DefaultReferenceComparator`, `MetricKeys`, `DefaultEvidenceClassifier`, `GenuineReferenceProfileLoader`, `DefaultFindingPresenter`, `DirectionWording` (new this session). **Not referenced by `WatchAlignCoreV13` or any other production report path** — see F.
- `tools/research/run_replica_control_set.py` — blind fetch+measure runner (this session: added `fetch_log_tail` diagnostic capture).
- `tools/research/analyze_replica_control_set.py` — new this session; joins measurements (if any) against the genuine profile and classifies evidence strength, using the same thresholds as `DefaultEvidenceClassifier.java`.
- `docs/research/gmt-replica-control-set-results/` — `control_status.csv`, `per_image_measurements.csv`, `per_control_medians.csv` (all empty of measurements — 0/9 fetched), `discrimination_analysis.csv` (new; 9 rows, all `evaluable=false` with the fetch-blocked reason).
