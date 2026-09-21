# 126710BLNR research corpus

This folder is the source manifest and fetch workflow for the Python-first Watch Align Batgirl research.

The goal is not to collect as many photographs as possible. The goal is to maintain a provenance-aware set of **independent physical watches/QC batches** that can be used to find repeatable differences between source-labelled genuine Rolex GMT-Master II 126710BLNR watches and explicitly labelled replica 126710BLNR watches.

Third-party photographs are deliberately **not committed** to the public repository. Run `fetch_images.py` to populate the local `gen/` and `rep/` trees.

## Current source set

The expanded manifest contains:

- 1 first-party Rolex catalogue reference (`official`, split `reference`). This is a visual reference, not an independent physical-watch population sample.
- 11 marketplace source-labelled genuine candidates (`gen_candidate`): 6 calibration watches and 5 held-out validation watches.
- 23 explicitly replica-labelled watches/QC batches (`rep_labelled`): 14 calibration watches and 9 held-out validation watches.
- Replica factories represented include VSF, Clean, ARF and C+ Factory.

The user's own known replica is intentionally **not** included. It should remain a blind validation case.

Some exact-reference 126710BLNR sources use Oyster rather than Jubilee. They are retained because the dial and core marker geometry are the same reference, but Batgirl-only experiments should filter `bracelet == Jubilee`. The manifest records bracelet explicitly so this cannot happen silently.

The source set intentionally includes both near-clean replica examples and examples with community-noted defects. This is important because a classifier that only learns from obviously bad replicas will not be useful on the better VSF, Clean or ARF examples that matter most in practice.

## Provenance rules

`official` means a first-party Rolex catalogue source.

`gen_candidate` means the seller/listing describes the watch as a genuine Rolex 126710BLNR. It is **not** independently authenticated by Watch Align and must never be promoted to `official` or treated as unquestionable ground truth merely because the source says genuine.

`rep_labelled` means the source explicitly identifies the watch as a replica and normally names the factory. No watch is labelled replica by visual inference.

These labels are deliberately conservative. They describe source provenance, not an authentication conclusion made by this project.

## Independence and leakage

`physical_watch_id` is the unit of independence. Multiple photographs from one album are multiple views of **one watch**, not multiple samples.

Calibration and validation are split by `physical_watch_id`. The fetcher refuses a manifest in which one physical watch appears in more than one split.

When computing distributions, aggregate measurements per physical watch before comparing classes. Do not give a ten-photo album ten times the statistical weight of a one-photo album.

A repeated sale/listing of the same physical watch is a potential leakage risk even if it has a different URL. The fetcher therefore records exact SHA-256 hashes and a small perceptual hash and flags close visual duplicates for human review. Treat any suspected duplicate as one physical watch until proven otherwise.

## Fetching

From the repository root:

```bash
python -m pip install -r datasets/126710BLNR/requirements.txt
python datasets/126710BLNR/fetch_images.py --clean
```

Useful subsets:

```bash
python datasets/126710BLNR/fetch_images.py --class gen
python datasets/126710BLNR/fetch_images.py --class rep --split validation
python datasets/126710BLNR/fetch_images.py --source rep_vsf_7s6PyXJ
```

Use `--strict` when you want the command to fail if any selected source is unavailable.

The script writes images under:

```text
gen/<split>/<source_id>/image_XX.jpg
rep/<split>/<source_id>/image_XX.jpg
```

and creates `resolved_images.csv`, containing the local path, SHA-256, a small perceptual hash, dimensions and the original provenance fields. Exact duplicates are detected and close perceptual duplicates are flagged for review.

Public image hosts can disappear or change. A source that fails to download should be reported as unavailable, not silently replaced with an unlabeled image from elsewhere. Any replacement source must be added to `manifest.csv` with its own provenance and physical-watch identity.

## What Claude should do first

Before changing QC thresholds, use the Python engine in `tools/watch_align_py` to run acquisition/rectification across the corpus and produce a per-image measurement table. Then collapse repeated images to one robust record per `physical_watch_id`.

The first analysis should answer:

1. Which photographs produce a trustworthy pose and which fail acquisition?
2. Which measurements are repeatable across different photographs of the same watch?
3. Which candidate features have a genuine-candidate distribution that is visibly distinct from the replica distribution?
4. Which proposed features overlap too heavily and therefore must **not** be used as defect evidence?
5. Do held-out validation watches behave like the calibration results predict?
6. Does a feature still separate classes after controlling for factory, bracelet and view angle, or is the apparent difference just a dataset artefact?

Do not derive a hard "genuine" threshold from marketplace candidates alone. At this stage a result can support language such as "outside the observed/calibrated genuine-candidate range", not proof that a watch is counterfeit.

The held-out validation split must stay untouched while feature selection and thresholds are being developed. Do not repeatedly tune against validation failures. If a validation example is pulled into calibration, replace it with a new independent validation watch and record that change explicitly.

## Candidate features

For the planar dial layer, useful candidates include 12-triangle angular/radial position and shape, 6/9 baton position and rotation, round-marker radial placement, minute-track phase and dial-print geometry. Date numeral/cyclops, bezel/rehaut and SEL features should remain separate because they do not share the same dial-plane homography.

A feature is eligible to become a production anomaly only after it is:

- reliably measurable,
- repeatable on the same physical watch,
- calibrated on genuine-labelled sources,
- useful on held-out validation watches,
- and sufficiently separated from replica controls to justify the claim.

Otherwise keep it diagnostic-only.

## Copyright / redistribution

The manifest records public source URLs for research reproducibility. The source photographs remain with their original publishers and are downloaded locally for analysis. Do not commit downloaded third-party image collections back into the public repository unless redistribution rights are clear.
