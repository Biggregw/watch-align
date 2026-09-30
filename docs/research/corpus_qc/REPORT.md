# Corpus-driven QC research (research/corpus-driven-qc-improvement, 2026-09-30)

Source: `data/harvest` at `4aaf0c6` (the 25-source canary). Code: `tools/research/corpus_qc/`,
driver `tools/desktop-harness/drivers/CorpusQc.java` (read-only instrumentation of the production
analysis). Result files: `docs/research/corpus_qc/results/*.json`. **No production code changed.**

## 1. Accepted corpus (independent physical watches are the unit)

| class | reference | factory | watches | usable images |
|---|---|---|---|---|
| genuine | 126710BLNR | – | 11 | 11 |
| genuine | 126710BLRO | – | 6 | 6 |
| replica | 126710BLNR | Clean | 9 | 24 |
| replica | 126710BLNR | VSF | 9 | 20 |
| replica | 126710BLNR | ARF | 2 | 2 |
| replica | 126710BLNR | C+ | 1 | 4 |
| replica | 126710BLRO | Clean | 2 | 3 |
| replica | 126710BLRO | VSF | 1 | 1 |
| **population** | | | **41 (17 gen, 24 rep)** | **71** |
| reference only | 126710BLNR catalogue | – | 1 (not counted) | 1 |

All 245 image rows of accepted watches were available locally and verified against the sha256 in
state. The 29 rows from three new Imgur albums were re-fetched and matched byte for byte; nothing was
substituted. Every genuine watch has exactly **one** usable photo, so within-watch repeatability
comes from the replica watches only. Geometry repeatability does not depend on class.

## 2. Deterministic split (`split_v1.csv`, seed `watch-align-corpus-split-v1`)

Split at watch level, stratified by class/model/factory, largest-remainder 60/20/20:

| stratum | dev | val | holdout |
|---|---|---|---|
| gen 126710BLNR | 7 | 2 | 2 |
| gen 126710BLRO | 4 | 1 | 1 |
| rep BLNR Clean | 5 | 2 | 2 |
| rep BLNR VSF | 5 | 2 | 2 |
| rep BLNR ARF | 1 | 1 | 0 |
| rep BLNR C+ | 1 | 0 | 0 |
| rep BLRO Clean | 1 | 0 | 1 |
| rep BLRO VSF | 1 | 0 | 0 |
| **total** | **25** | **8** | **8** |

The holdout was not exported or analysed until the candidates were frozen
(`frozen_candidates_v1.json`, commit `e322cee`). It was then run once.

## 3. Baseline (current production analysis)

| | development | validation | holdout (final, once) |
|---|---|---|---|
| photos / watches | 140 / 25 | 51 / 8 | 53 / 8 |
| harvest-usable photos | 44 (31%) | 17 (33%) | 10 (19%) |
| dial analysed (readable) | 51 | 19 | 15 |
| pose GOOD / CORRECTABLE / RETAKE / UNASSESSABLE | 16/33/1/1 | 6/13/0/0 | 4/10/1/0 |
| 12 triangle found (readable dials) | 96% | 100% | 87% |
| minute track stable | 71% | 68% | 73% |
| round markers found | 95.6% | 98% | 94% |
| ≥6 round markers | 98% | 100% | 93% |
| 6 baton / 9 baton | 84% / 78% | 84% / 84% | 80% / 60% |
| marker-layout pose valid (rectification confidence) | 63%, resid 0.004 | 79%, 0.003 | 60%, 0.003 |
| pose label flips under trivial perturbation | 23% | 29% | 38% |
| median analysis time | 4.7 s | 4.7 s | 5.5 s |

Most "not usable" photos in accepted albums are legitimately not dial photos: clasps, casebacks,
side views, grazing angles. A contact sheet of 24 random "no readable dial" photos showed about
20 non-dial or grazing-angle shots.

**Repeatability, the key baseline finding.** Across different photos of one watch, the raw
measurements vary a lot (development pooled within-watch SD: gap 0.10, rotation 4.7°, 6-baton
rotation 6.4°). That spread comes from a few outlier photos. The app already refuses to judge them
(unstable minute frame, or the resize check moves the reading). **Values the app actually judges
are highly repeatable:** judged gap SD 0.0045 (all values 0.10), judged rotation SD 0.22° (4.7°),
judged 6-baton centring SD 0.013 (0.12). The cost is coverage: only about 45–55% of usable photos
receive a gap or alignment verdict. No verdict was ever given on an unstable minute frame.

**Perturbation sensitivity** (JPEG q75, 90% resize, 1° rotation, 2% shift), p90 of |change|:
gap 0.095 (dev) / 0.051 (val), rotation 2.1° / 1.8°, 6 centring 0.055 / 0.056. Judged verdicts
change under perturbation mostly between CLEAR and not-judged. Real CLEAR↔CHECK flips were 4 photos
in development and 2 in validation, all borderline.

**Genuine vs replica** (one median per watch; descriptive only, never used for tuning): no metric
separates the classes beyond noise. AUCs 0.2–0.67 flip direction between development and validation.
Every "median difference / noise" ratio is below 0.6 on development. In development and validation,
**0 of 14 usable genuine photos were flagged**. Replica flag rates: alignment 9%, 6 baton 6%, gap 3%
(development).

## 4. Rehaut / perspective hypothesis: not supported on this corpus

The app's local rehaut sector widths (12/3/6/9 with coverage) were tested as dimensionless
asymmetries (top−bottom, left−right).

| test (coverage ≥ 0.6) | development | validation |
|---|---|---|
| rehaut top/bottom vs marker keystone_y (signed) | ρ 0.13 | ρ −0.11 |
| rehaut left/right vs marker keystone_x | ρ −0.33 | ρ −0.50 |
| rehaut top/bottom vs marker top/bottom radial bias | ρ −0.17 | ρ −0.18 |
| sign agreement rehaut top/bottom vs keystone_y (\|asym\|>0.05) | 62% | 56% |
| sign agreement rehaut left/right vs keystone_x | 38% | 33% |
| \|rehaut top/bottom\| vs ellipse tilt (vertical part) | ρ −0.40 | ρ +0.50 |
| fitted keystone_y ~ rehaut top/bottom, R² | 0.002 | −0.20 |
| fitted left/right bias ~ rehaut left/right, R² | 0.11 | −0.77 |
| rehaut asymmetry predicts perturbation sensitivity | ρ 0.04 | ρ −0.09 |
| rehaut asymmetry predicts within-watch disagreement | ρ +0.33 | ρ −0.40 |

Neither magnitude nor sign relationships survive from development to validation. Fitted relations
have zero or negative validation R². As measured by the current sector analysis, the rehaut
asymmetry does not carry reliable pose information in these photos. One caveat: nearly all usable
photos are close to frontal (marker-layout tilts 1–9°), where the expected rehaut effect is small.
The hypothesis is **not proven false** for steep angles; this corpus cannot test it.

## 5. Rectification

Marker positions were compared across photos of the same watch (pooled within-watch SD of each
marker's residual, in dial-radius units):

| normalisation | dev SD | val SD | perturbation p90 |
|---|---|---|---|
| circle (dial-edge centre and radius) | 0.051 | 0.047 | 0.016 |
| dial ellipse | 0.051 | 0.046 | 0.016 |
| affine, fitted leave-one-out on the other markers | 0.0087 | 0.0098 | 0.008 |
| homography, leave-one-out | 0.0085 | 0.0111 | 0.008 |

1. The dial-edge centre/scale varies photo to photo by about 5% of the radius, while the marker
   layout itself is consistent. The ellipse normalisation adds nothing over the circle on these
   near-frontal photos.
2. A projective model gives no gain over affine and is slightly worse on validation.
3. The production round-marker offset is referenced to neighbouring minute ticks. It is **more**
   repeatable than an affine-rectified residual in comparable units (within-watch SD 0.044 dev /
   0.064 val against about 0.054 / 0.085). Keep the local reference; don't replace it with global
   rectification.

## 6. Other failure patterns

- **Baton mis-localisation that survives the resize check.** The holdout's genuine
  gen_126710BLRO_phillips_146213 gets a 6-baton CHECK (centring −0.43, rotation 6.2°). The value
  holds under JPEG re-encoding and a 90% resize, which is why the app's resize check keeps it. A 2%
  shift of the frame gives centring 0.02 and rotation −0.6°. A related development photo of a
  genuine Phillips BLRO shows a 6-baton rotation of 24.7° and a 9-baton CHECK in 2 of 4
  perturbations. This is the only genuine false-positive mechanism found.
- **The pose label is unstable.** GOOD↔CORRECTABLE changes in 23–38% of trivially perturbed photos.
  It rarely matters, because only RETAKE blocks verdicts.
- **Round-marker misses** are mostly "no bright area at the expected position" (a hand or date
  area) and "shape found off the expected position". Hour 8 is the most frequent (date-side and
  hand occlusion).
- **Suitability.** On all dial-located harvested images (accepted, quarantined and rejected;
  holdout watches excluded), the strongest early signal is a triangle width under 21 px: it caught
  3/21 development and 9/22 validation bad photos with 0 good lost. The app's existing 40 px
  triangle floor is already stricter, so this adds nothing. Rehaut coverage, ellipse tilt,
  marker-layout tilt and glare gave no dependable early rejection.
- The "no readable dial" failure is not an in-plane rotation problem. An upside-down dial photo
  stayed unreadable at 150–180°, and rated RETAKE at 180°.

## 7. Candidates tested (thresholds from development, frozen, then validation, then holdout)

Each candidate can only withdraw verdicts. Scored on judged verdicts: agreement with other views of
the same watch, stability under perturbation, and genuine flags.

| candidate | hypothesis | dev: judged / unstable / LOO err | val: judged / unstable / LOO err | decision |
|---|---|---|---|---|
| baseline | – | 77 / 51 / 0.085 | 27 / 14 / 0.043 | – |
| C1 rehaut asymmetry > 0.655 → not judged | rehaut asymmetry marks unreliable photos | 61 / 44 / 0.096 | 27 / 15 / 0.043 | **rejected**: worse LOO on dev, more unstable on val |
| C2 marker-layout tilt > 8.36° → not judged | tilted photos give unreliable verdicts | 62 / 50 / 0.085 | 24 / 17 / 0.043 | **rejected**: withdraws good verdicts, more unstable on val |
| C3 resize gap spread > 0.079 → not judged | the resize-check spread predicts error | 75 / 46 / 0.073 | 27 / 14 / 0.043 | **rejected**: no effect on validation |
| C6 alignment CHECK needs \|rot\| ≥ 1.0°+0.68° | borderline flags flip under noise | 74 / 44 / 0.091 | 27 / 14 / 0.043 | **rejected**: no effect on validation |
| R1 homography instead of affine for the marker layout | projective refinement helps | SD 0.0085 vs 0.0087 | 0.0111 vs 0.0098 | **rejected** |
| R2 affine-rectified marker offset instead of the tick-local offset | global rectification is more repeatable | 0.054 vs 0.044 | 0.085 vs 0.064 | **rejected** |
| R3 ellipse instead of circle normalisation | ellipse removes perspective | 0.0514 vs 0.0514 | 0.0464 vs 0.0465 | **rejected** (no effect) |
| S1 early rejection on triangle width < 21 px | small triangles fail | 3/21 caught | 9/22 caught | **not needed**: the app's 40 px floor is stricter |

No candidate earned promotion. The frozen final system is the unchanged production analysis.

## 8. Locked holdout (run once, after freezing)

8 watches, 53 photos, 10 usable (19%), 2 watches with more than one usable view.
- Readable dials: 15. 12 triangle found: 87%. Minute track stable: 73%. Round markers: 94%.
- Perturbation p90: gap 0.006, rotation 0.73°. Pose label flips: 38%.
- Judged verdicts: 21. Unstable under perturbation: 16. Flags: 4, **1 on a genuine watch** (the 6
  baton above).
- The rejected C2 gate would have removed that genuine flag on the holdout (gen flags 1 → 0). It
  failed validation, and per protocol nothing was retuned after seeing this.

## 9. Conclusion

- **What improved:** measurement knowledge, not the app. There is now a reproducible, sha-verified,
  watch-level baseline and a research harness (split, export, experiments, frozen candidates,
  one-shot holdout).
- **What did not improve:** production QC. No change beat the baseline on validation.
- **What this establishes:**
  - The app's existing "not judged" gating is what makes its verdicts repeatable.
  - Rehaut asymmetry adds no usable pose information on near-frontal photos.
  - Projective rectification adds nothing over affine, and the tick-local marker offset beats
    global rectification.
- **Remaining weaknesses:**
  - About half of usable photos get no 12-marker verdict, mainly because the resize check's
    "same edge" test fails.
  - Baton mis-localisation can survive the resize check and produce a genuine false positive.
  - Pose labels are unstable.
  - Genuine watches have one usable photo each, so genuine within-watch variation is unmeasured.
  - The corpus is almost entirely 126710BLNR/BLRO and near-frontal.
- **Next highest-value experiment:** a translation re-check for baton (and 12) verdicts. Re-measure
  on a 2% shifted crop, as the resize check re-measures on 94% and 88%, and withhold a flag that
  moves. Test it on a fresh split, since this holdout has now been seen. Alongside, collect multiple
  photos per genuine watch.

## Reproducing

```bash
git worktree add /tmp/harvest origin/data/harvest          # state; images come from the harvest cache/store
python3 tools/research/corpus_qc/split.py /tmp/harvest docs/research/corpus_qc/split_v1.csv   # must reproduce the committed file
python3 tools/research/corpus_qc/export.py /tmp/harvest docs/research/corpus_qc/split_v1.csv out --parts development,validation --store <image store>
cd tools/research/corpus_qc
python3 baseline.py ../../../out/corpus_qc.jsonl --parts development
python3 experiments.py ../../../out/corpus_qc.jsonl --out ../../../out/exp --parts development,validation
python3 candidates.py ../../../out/corpus_qc.jsonl --frozen ../../../docs/research/corpus_qc/frozen_candidates_v1.json --parts validation
python3 suitability.py /tmp/harvest ../../../docs/research/corpus_qc/split_v1.csv
python3 -m unittest discover -s . -p 'test_*.py'
```

These runs were made locally, in the development container, on the complete sha-verified corpus.
The workflow `.github/workflows/research-corpus-qc.yml` (manual trigger) runs the same scripts on
GitHub Actions. It checks out `data/harvest` read-only and uploads only reports.

**It has not been run on a hosted runner.** Actions caches are scoped by branch, so it can only
restore `harvest-images-*` caches saved on `main` or on its own branch. The canary saved its cache
on `feature/unattended-dataset-harvester`. If the cache is incomplete, `corpus.load()` stops with
an error rather than analysing a subset.
