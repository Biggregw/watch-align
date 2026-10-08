# Adding a watch model

Since Alpha100 the app reads everything model-specific from data. A model is a folder under
`android/app/src/main/assets/models/<id>/`:

```
models/<id>/
  model.json                     dial layout (this file is the model)
  reference/                     genuine reference, exported from the research outputs
    genuine_reference.csv        per genuine watch: how far each feature reads (6/9 batons, rounds, ring, date)
    nominal.properties           genuine nominals of the signed features
    triangle_nominal.properties  robust 12-triangle nominal
    triangle_reference.csv       robust 12-triangle per-watch context
    uncertainty.properties       single-photo measurement uncertainty per feature family
```

The GMT-Master II (`gmt_126710`) is the only model with a genuine reference today.

## Steps

1. **Write `model.json`** from a bare genuine dial rectified onto the minute lattice (as the Alpha92 GMT master was).
   - Units: canonical, dial radius 1, 12 at the top.
   - Use `models/gmt_126710/model.json` as the template.
   - **`pose`:** minute-track inner and outer radius, plus `excluded_minutes`, the minute marks hidden by the date window or printing. These never pull the pose fit.
   - **`markers`:** one entry per applied marker, giving its `hour`, `shape` and size:
     - `triangle`: `apex_r`, `base_r`, `half_base`, `area_centroid_r`, `corridor_start_r`;
     - `baton`: `centre_r`, `radial_half`, `tangential_half`;
     - `round`: `centre_r`, `outer_r`.
     - Give each triangle and baton a `key`, the name its reference entries use (e.g. `"six"` → `six_rot`, `six_off`). Rounds share `rounds`.
   - **`date_window`:** the crop region, the expected window centre, and the region the seconds-hand search skips. Use `null` for a model without a date.
   - **`seconds_hand`:** the radii between which the seconds hand carries its lume dot.
2. **Build the genuine reference** with the research tools in `tools/research/alpha96_calibration/`:
   - collect sha256-verified genuine photos (one physical watch is one sample; shared / stock photos de-duplicated);
   - run the Alpha96 runner;
   - run `build_alpha98_reference.py`, `calibrate_m12_nominal.py` and `measurement_uncertainty.py`, the last on genuine watches photographed two or more times.
   - Replica photos never define these files.
3. **Export the reference** with `python3 tools/research/alpha96_calibration/export_model_reference.py --model <id> --source-dir <dir>`. Add a test like `ModelSpecTest.referenceAssetsAreTheResearchFiles` for the new model.
4. **Validate before showing it to anyone:**
   - the desktop harness drivers (`-Dwatchalign.model=<id>`);
   - the genuine-catalogue CI job (no findings that the reference cannot explain);
   - the hand check on real photos of the model.

## What happens with an incomplete model

- **No reference, or no uncertainty, for a feature:** it is reported "not assessed - no genuine reference yet" and can never be a finding (`ModelSpecTest.aSecondModelNeedsNoCodeAndNoReferenceMeansNoFindings`).
- **No uncertainty file:** a feature beyond the genuine range can be "worth a look" but never "clear".

## When code is still needed

- **A new marker shape** (Arabic numerals, Explorer 3-6-9, applied logos): new measurement code, a new `shape`, and the interference / close-up outlines for it.
- **A date window that is not horizontal at 3 o'clock** (e.g. at 6): the window detector assumes a horizontal aperture.
- **Model selection on the main screen:** the screen currently checks `PerspectiveOverlayPocActivity.MODEL_ID`. A picker can list `assets/models/` once a second model has a validated reference.
