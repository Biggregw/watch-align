# Submariner research path (issue #34)

This path collects **raw measurements** for 12-series Submariners (124060, 126610LN, 126610LV). It is
research only:

- no production Submariner QC;
- no authenticity classifier;
- no thresholds derived from this corpus;
- no change to GMT behaviour.

Android still offers only the generic GMT. `GmtHumanQcAnalyzerV2` still returns UNASSESSABLE for every
Submariner reference, and `SubmarinerProductionIsolationTest` checks both.

## Pipeline

| Stage | Code | Output |
|---|---|---|
| Acquire | `tools/dataset_harvester/submariner_acquire.py` (one or more `--pool` files) | `acquired_images.csv`, `candidate_summary.csv`, acquisition report |
| Dataset states | `tools/dataset_harvester/subresearch/dataset.py` | `sub_dataset_images.csv`, `sub_dataset_watches.csv` |
| Locked split | `tools/dataset_harvester/subresearch/split.py`, committed as `split_sub_v1.csv` | partition per physical watch |
| Measure | `tools/desktop-harness/drivers/SubMeasure.java`, run by `tools/dataset_harvester/submariner_measure.py` | JSON lines |
| Tables | `tools/dataset_harvester/subresearch/tables.py` | `sub_images.csv`, `sub_landmarks.csv`, `sub_landmark_stability.csv`, `sub_summary.json` / `.md` |

The workflow is `.github/workflows/submariner-research.yml`. It runs on manual dispatch, or on a push to
`feature/submariner-research-hardening`. It runs the deterministic tests first, then:

1. restores the Submariner image cache;
2. rebuilds the acquisition manifest from every pool;
3. measures the photos;
4. uploads the tables as an artifact.

A separate job runs the Android JVM tests.

## Independence and labels

- **The independence unit is `physical_watch_id`.** Several photos of one watch are one sample, and every
  count in the summary is by watch. Photos of the same watch are repeatability data.
- **Duplicates within one watch.** A second copy of the same photo in one watch is REJECTED
  (`reject_exact_duplicate`, or `reject_near_duplicate` for a dHash + pHash match confirmed at pixel level).
- **The same photograph under two different watches** belongs to neither. Every copy is QUARANTINED
  (`quarantine_cross_watch_duplicate` / `_near_duplicate`). These are usually site or stock images.
- **Dealer images.** An image whose own path does not carry the listing id is QUARANTINED
  (`quarantine_unverified_listing_image`). Dealer pages also show navigation images and other watches'
  thumbnails; one Bob's Watches 126610LN page showed a 126610LV from another SKU.
- **Unsupported models.** Only `submariner_12` models are admitted. GMTs and the 11-series (114060,
  116610LN, 116610LV) are REJECTED (`reject_unsupported_model`).
- **Class labels are `gen` / `rep`.** Genuine watches establish normal geometry. Replicas are stress cases
  for the detectors, not classifier labels. Class never affects a research state and never selects a
  threshold.
- **The locked split** is 60/20/20 at watch level, stratified by class, model and (for replicas) factory.
  It is created once and never regenerated.
  - Watches that appear later are `unassigned` until a new split version is made deliberately.
  - Holdout photos are not measured unless `--include-holdout` is given.

## Adding a supplemental pool

Add another `--pool` file. The workflow picks up `docs/research/submariner_*topup*.csv` automatically.
Two schemas are accepted:

```
candidate_id,class,model,factory,source_type,source_name,source_url,image_album_url,provenance_note,candidate_status
physical_watch_id,family,model,class_tag,source_type,source_name,source_url,provenance_note,candidate_status
```

- **Identity.** `candidate_id` falls back to `physical_watch_id`, and `class` falls back to `class_tag`.
- **Listing id.** Taken from `listing_id`, from "SKU n" / "product code n" in the provenance note, or
  from the trailing number of the dealer URL.
- **Duplicate rows.** A second row for the same listing (same dealer and listing id) is skipped as
  `duplicate_listing_of:<first>`. It is never a second watch.

## What is generic, and what stays GMT-only

**Reused as primitives.** These are detection and fitting only; their GMT-tuned search windows are
labelled as priors (below).

- `GmtDialSeedAnalyzer`: dark-dial proposal from Hough plus a dark-inside/bright-outside ring.
- `DialEdgeFitter` / `DialEdgeEllipseFit`: dial-edge ellipse.
- `GmtTwelveLandmarkAnalyzer`: triangle contour and the 59/60/01 tick frame.
- `TriangleEdgeRefiner.fitSide` / `intersect`.
- `GmtSixLandmarkAnalyzer`: batons at 3, 6 and 9.
- `GmtRoundMarkerAnalyzer`: circle fits and local tick frames.
- `GmtMarkerPose.fit`: the raw affine singular-value ratio.
- Rehaut sector analysers: raw widths and coverage only.
- `GmtEllipsePoseAnalyzer`: raw axis ratio.
- Research tooling: splits, hashing and near-duplicate confirmation, pixel metrics.

**Isolated: never called on Submariner photos.**

- `GmtHumanQcAnalyzerV2`;
- `GmtHumanPosePolicy` and the GOOD / CORRECTABLE / RETAKE labels;
- `GmtHumanQcMath` decisions and thresholds (CLEAR / CHECK / STRONG);
- the no-readable-dial decision;
- `GmtDialLayout` (the date side comes from the model's layout, below);
- `GmtDialCrop` (its logic is mirrored with labelled constants);
- `GmtDirectionalClearancePolicy`;
- `GmtMarkerPose.estimate` gating;
- every resize-stability pass/fail with its GMT escape clause;
- the rehaut gap-trend cue.

`SubMeasure.java` is compiled on its own. The tests assert that none of these classes enters its class
closure, and the run refuses to start if one does.

**GMT values used only as labelled wide search priors.** These are recorded in the `priors` column of
every row:

- the 126710BLNR master round-marker centre radius (0.816 R) and surround size (0.088 R, fitted 0.55–1.45×), which place the round-marker seeds;
- the detector search windows (12 at 0.52–0.88 R, batons at 0.62–0.88 R, ticks at 0.835–0.99 R);
- the dial-crop resolution (preview radius under 230 px, target radius 380 px).

The GMT 44.3° ± 2° triangle apex prior is **not applied**. The driver re-fits the triangle's three
outer edges without it and records `apex_deg`, `squareness_deg` and which fit path was used. The
primitive's own result is kept as `primitive_*` diagnostics.

**Model layouts are explicit.** They come from `subresearch/layout.py`:

- **124060:** triangle at 12; batons at 3, 6 and 9; round markers at 1, 2, 4, 5, 7, 8, 10 and 11.
- **126610LN / 126610LV:** triangle at 12; batons at 6 and 9; date at 3 (not measured in phase 1); the same eight round markers.

## What is measured

Each photo is measured in its original form and in these variants, each analysed from scratch
(including dial localisation) and mapped back to original pixels through its known transform:

- resized to 94% and 88%;
- shifted by ±1% and ±2% in x and in y, with a flat border fill;
- rotated in-plane by ±5°.

Variants are only run when the original photo has a located dial and at least one detected landmark.

**`sub_images.csv`** (one row per photo × variant):

- dial seed (centre, radius, quality);
- edge-fit ellipse (centre, semi-axes, angle, axis ratio, RMS, inliers, seed-to-fit displacement);
- analysis path;
- marker-layout affine ratio, tilt, residual and scale;
- raw rehaut widths and coverage;
- raw dial-contour ellipse ratio;
- pixel quality on the original photo.

**`sub_landmarks.csv`** (one row per photo × variant × landmark):

- whether it was detected, and why not;
- fit path;
- centre in original pixels, radius and angle from 12 in the dial's rectified frame, angular offset from nominal;
- rotation against the local tick normal;
- width and length (or radius, surround and lume ring);
- apex angle and squareness (12);
- raw gap, inset and centring;
- local tick geometry and fit diagnostics.

**`sub_landmark_stability.csv`:** min, max and spread per photo and landmark over the variants, with
the variants in which it was lost. Spreads only; there is no stable/unstable decision.

## What is deliberately not judged

- No CLEAR / CHECK / STRONG or pose label.
- No UNASSESSABLE or "no readable dial" decision.
- No genuine/replica decision or score.
- No pass/fail on stability.
- No threshold of any kind.

`tables.forbidden_columns()` refuses to write any such column. Date window, cyclops and bezel are not
measured in phase 1.

## GMT regression protection

- `GmtConstantsSnapshotTest` pins the 126710BLNR master, the GMT verdict thresholds and the detector priors.
- `SubmarinerProductionIsolationTest` keeps Submariner production analysis unavailable.
- The existing Android JVM and harvester tests still run.
- `tools/desktop-harness/gmt_golden/` is a sha256-keyed fixture: the 337-photo GMT regression's
  production Batch outputs. The photos themselves are not committed.
  `python3 tools/desktop-harness/gmt_golden.py check --images <dir>` re-runs Batch wherever the photos
  are available and compares every column exactly.
