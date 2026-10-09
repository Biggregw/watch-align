# Adding a watch model

Since Alpha100 the app reads everything model-specific from data. A model is a folder under
`android/app/src/main/assets/models/<id>/`:

```
models/<id>/
  model.json                     dial layout (this file is the model)
  reference/                     genuine reference, exported from the research outputs
    genuine_reference.csv        per genuine watch: how far each feature reads (batons, rounds, ring, date)
    nominal.properties           genuine nominals of the signed features
    triangle_nominal.properties  robust 12-triangle nominal
    triangle_reference.csv       robust 12-triangle per-watch context
    uncertainty.properties       single-photo measurement uncertainty per feature family
```

Models with a genuine reference today:

- GMT-Master II 126710 (`gmt_126710`): research files in `tools/research/alpha96_calibration/`.
- Submariner 124060 (`submariner_124060`, testing): research files in `tools/research/sub124060/`. **This is the
  worked example for a new model.** Its README records every step and every owner-reported problem.

QC guardrails apply throughout (`docs/QC-GUARDRAILS.md`): genuine photos alone set limits; replicas only show usefulness;
a new model must not change an existing model's results (§6).

## Steps

### 1. Genuine catalogue

- Collect dealer / auction photos of proven-genuine watches of the reference. For each, record the image URL, its
  sha256, the physical watch (`physical_watch_id`, from the listing / lot) and the source. Example:
  `tools/research/sub124060/catalogue_124060.csv`.
- CI refetches with `alpha96_calibration/fetch_verified.py` and keeps only byte-identical files. Images are never
  committed, cached or uploaded.
- One physical watch is one sample. Remove shared / stock photos by **measured** dedup, not whole-image hashes:
  two photos whose measured offsets agree within 0.0005 R are the same dial (the 124060's `dedup_measured.csv`).

### 2. Dial master: `model.json`

Measure the master from the genuine catalogue (`SubMaster` driver: median over watches of each watch's median), not
from one photo. Units: canonical, dial radius 1, 12 at the top.

- **`pose`:** minute-track inner and outer radius, plus `excluded_minutes`, the minute marks hidden by the date
  window or printing. These never pull the pose fit.
- **`markers`:** one entry per applied marker, giving its `hour`, `shape` and size:
  - `triangle`: `apex_r`, `base_r`, `half_base`, `area_centroid_r`, `corridor_start_r`;
  - `baton`: `centre_r`, `radial_half`, `tangential_half`;
  - `round`: `centre_r`, `outer_r`.
  - Give each triangle and baton a `key`, the name its reference entries use (e.g. `"six"` → `six_rot`, `six_off`).
    Rounds share `rounds`.
- **`date_window`:** the crop region, the expected window centre, and the region the seconds-hand search skips.
  Use `null` for a model without a date.
- **`seconds_hand`:** the radii between which the seconds hand carries its lume dot, plus, when the dial has
  printing at those radii, the **print spots** (see step 5).
- **`resolution_matched`** (optional): features compared only with genuine watches photographed at similar or lower
  resolution (see step 4). The rounds and ring are always resolution-matched.
- **`label`:** shown on the results screen as "Checked as: …". Mark an unvalidated model "- testing".

### 3. Runner and genuine reference

1. Run the production runner (`Alpha96Calib`, with `-Dwatchalign.model=<id> -Dwatchalign.models_root=<research dir>`)
   on the catalogue in CI. Output: `per_photo.csv`.
2. **12 edge filter** (`sub124060/edge_filter.py`, QC guardrails §1): remove the 12 reading of genuine photos whose
   triangle sides disagree beyond the edge-consistency limit (lighting or blur on one edge). The limit comes from the
   unfiltered data, in one pass. Every other marker is untouched.
3. `alpha96_calibration/calibrate_m12_nominal.py` (triangle nominal and per-watch 12 reference), on the filtered file.
4. `sub124060/build_sub_reference.py --per-photo … --dedup … --spec model.json --out-dir reference_src` (marker reference,
   nominals and single-photo uncertainty, model-generic: batons come from the spec).
5. Export: `python3 tools/research/alpha96_calibration/export_model_reference.py --model <id> --source-dir <dir>`.
   Add a test like `ModelSpecTest.submariner124060ReferenceIsTheResearchFilesAndRoundSizeIsDowngraded`.

### 4. Small photos (resolution matching)

A small photo's round and baton positions read noisier than a sharp photo's, so comparing them with a sharp-photo
reference makes genuine watches look off. Two tools:

- **Resolution matching:** a photo of dial radius R is compared only with genuine watches photographed at R_ref ≤ 1.3 R.
  Fewer than 8 such watches → that feature is "not assessed - resolution too low".
- **Low-resolution genuine rows:** shrink every genuine dial photo (INTER_AREA, JPEG q92) to dial radii 130 / 150 / 170 px
  (originals at least 15% larger only) and re-measure (`sub124060-lowres.yml`). Add only the rows that help without
  widening sharp-photo limits: `build_sub_reference.py --lowres … --lowres-level 170 --lowres-feature …`. Those rows carry
  `max_photo_r`, so a sharp photo is never judged against them.
- Check with `sub124060/heldout_lowres.py` (real small genuine photos, other watches only, with the app's hand / glare
  withholding): **0 clear** is required.

### 5. Hand check and dial print

Run `Alpha99Interference` on the genuine catalogue and read the `[SEC]` withholdings in its log. If printed text sits at
the seconds-hand dot radii (the 124060's "S" and "R" of SUPERLATIVE CHRONOMETER at r 0.52-0.54), it is taken for the hand
and withholds markers on genuine photos. List each print spot in `seconds_hand.print_spots` (`[angle_deg, r]`) with
`print_spot_tol_deg`, `print_spot_tol_r` and `print_spot_min_frac` (a candidate there counts as the hand only at ≥ that
fraction of lume contrast; choose it between the two genuine ranges).

### 6. Validate before showing it to anyone

- `heldout_genuine.py` (leave-one-watch-out on dealer photos) and `heldout_lowres.py`: **0 clear** on genuine photos.
- The genuine-catalogue CI check (`sub124060-genuine-check.yml` is the template): findings, interference, previews.
  Every clear or worth-a-look finding on a genuine photo is looked at in the log close-ups.
- Every existing model's regression: findings, hand check and preview text unchanged (§6), unless a change is
  independently justified and regression-tested.
- Owner photos of the model, then replica examples (usefulness only; they never move a limit).

### 7. Applying a fix to every model

A fix found on one model (owner report, new failure mode) is checked on every model with a reference before it ships
(`gmt-alpha102-check.yml` checked the 124060 fixes on the GMT: candidate constants are built in CI and the app's
classification is run with current and candidate constants, printing every status change). A fix is applied to a model
only when that model's genuine evidence supports it; otherwise the reason is recorded in its research README.

## What happens with an incomplete model

- **No reference, or no uncertainty, for a feature:** it is reported "not assessed - no genuine reference yet" and can
  never be a finding (`ModelSpecTest.aSecondModelNeedsNoCodeAndNoReferenceMeansNoFindings`).
- **No uncertainty allowance for a feature** (e.g. the 124060's round size, too few repeat-photographed watches): a
  reading beyond the genuine range is at most "worth a look", never "clear".
- **Too few resolution-matched genuine watches:** "not assessed - resolution too low".

## Practical significance and photo trust (Alpha103, every model)

Applied by the findings layer to every model from its `model.json`, with no per-model data:

- **Too small to see:** a reading outside the genuine range by less than 5% of the marker's own width (positions,
  sizes) or 0.75° (angles) is listed as "too small to see", never as a deviation (QC guardrails §4). It cannot be clear.
- **Small photos:** a feature judged against shrunk-genuine rows (`max_photo_r`) is at most worth a look.
- **Opposite markers** (3/9, 1/7, 2/8, 4/10, 5/11) displaced the same way in the picture: at most worth a look (photo
  angle or lighting, not two misplaced markers).
- **Glare:** a round marker whose size reads off by at least half its position offset: its position is at most worth a look.

Origin: a replica QC video (4 frames of one watch) gave clear findings that changed from frame to frame, all about one
pixel; `.github/workflows/alpha103-regression.yml` checks that the genuine catalogues only ever get less severe.

## When code is still needed

- **A new marker shape** (Arabic numerals, Explorer 3-6-9, applied logos): new measurement code, a new `shape`, and the
  interference / close-up outlines for it.
- **A date window that is not horizontal at 3 o'clock** (e.g. at 6): the window detector assumes a horizontal aperture.
- **Model picker:** lists every folder in `assets/models/` and remembers the choice. A GMT run whose date window is not
  found suggests the no-date Submariner (a suggestion only).
