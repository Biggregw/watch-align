# Alpha96 genuine calibration runner (research only)

This folder runs the **exact Alpha96 measurement code** over genuine GMT photos the project already has, to establish the genuine distribution of the numbers Alpha96 shows on the phone.

It does not change production code, and it derives no thresholds.

## What runs

`tools/desktop-harness/run.sh Alpha96Calib <manifest.csv> <dataset_root> <out.csv>`

The driver (`tools/desktop-harness/drivers/Alpha96Calib.java`) compiles the app's own sources through the existing desktop harness and calls:
- `AutomaticDialOverlay.build(bitmap)`: the frozen Alpha96 minute-lattice pose, fail-closed;
- `Alpha94MarkerMeasurement.analyse(bitmap, H)`: the ring, rounds, 6/9 batons and the 12 triangle (research).

How it behaves:
- It writes every field those objects expose. It has no measurement maths of its own.
- Photos are loaded with the Alpha96 app rule (`PerspectiveOverlayPocActivity.readBitmap`): power-of-two subsampling while the long side stays ≥ 3200 px, then a scale-down to 3200 px.
- Occluded markers are written as `usable=false` with the production reason; no value is estimated.

Two scripts support it:
- `build_manifests.py` builds the manifests from repository evidence only. It downloads nothing.
- `aggregate.py` produces `results/per_photo.csv`, `results/watch_level.csv` (one row per physical watch and metric) and `results/report.md`. Positional metrics are given in px and in units of dial radius R. Genuine population, marketplace candidates and replica controls are kept strictly apart.

The only harness change was adding `Paint.Join` to the desktop shim; Alpha92's outline renderer needs it to compile there.

## Evidence inventory (2026-10-06)

| Source | What it is | Images present? |
|---|---|---|
| `origin/data/harvest` `manifest.csv` | Harvester catalogue: 2,974 rows; 2,269 `gen` (1,939 established dealer, 222 auction house, 61 Rolex CPO, 46 gen_candidate, 1 official) | **No.** `.gitignore` excludes `images/`; the photos lived only in the Actions cache `harvest-images-*`, which now holds about 10 MB (today's run saved 10,556,122 bytes). |
| `datasets/126710BLNR`, `datasets/gmt_phase_b_genuine`, `tools/research/validation/gmt12` | URL manifests: Reddit/Imgur gen_candidate, Phillips and dealer BLRO lists | No; URLs only. Their workflow artifacts had 3–7 day retention. |
| `origin/data/collected` | Phone-collected photos | Replicas only; images not committed. |
| Alpha91 fixture packs (session uploads) | 4 genuine controls and 2 replica defect cases | Yes, but the 4 genuine controls are **marketplace seller-asserted** (`gen_wex_1TDYtpN`, `gen_wex_vmbUDwy`, and one r/Watchexchange BLRO listing). |
| User uploads | Theonewatches BLNR replica; user Batgirl replica | Yes |
| Whole git history, all branches | 50 image files in total, none of them provenance-strong genuine GMT photos | n/a |

`catalogue_provenance_strong.csv` lists every provenance-strong genuine photo the harvester accepted as suitable, for same-layout black-dial 40 mm GMT references:

| Layout group | References | Photos | Physical watches |
|---|---|---:|---:|
| primary_steel | 126710BLNR / BLRO / GRNR | 126 | 73 |
| gold_surround_variant | 126711CHNR, 126713GRNR, 126715CHNR, 126718GRNR | 87 | 50 |

Excluded references:
- 126720VTNR and 126729VTNR: mirrored left-crown layout;
- 126719BLRO: blue or meteorite dial;
- 5 rows with a blank model.

**All 213 catalogued photos are missing locally** (`results/runner_catalogue_provenance_strong.csv`, status `missing`). Each row keeps its `sha256`, `image_url`, source and `physical_watch_id`, so the files could be restored and verified exactly.

## Results of this run

| Group | Photos measured | Watches | Accepted by Alpha96 pose |
|---|---:|---:|---:|
| Genuine population (provenance-strong) | **0** | **0** | 0 |
| Marketplace genuine candidates (descriptive only) | 4 | 3 | 4 |
| Replica regression controls | 4 | 4 | 4 |

- **Regression:** the runner reproduces every Alpha96 phone value for the Theonewatches BLNR and Batgirl references within display rounding (`results/report.md`).
- **Fidelity:** the full harness also runs the production 12-recovery path. With it, the Alpha96 pose is accepted on GEN_CAND_HO_02, which the stand-alone parity harness, with recovery stubbed, had rejected.
- **Conclusion:** no provenance-strong genuine watch has been measured with Alpha96, so no Alpha96 genuine distribution can be stated yet. The candidate numbers in `report.md` are descriptive only.

## Named evidence gap and the smallest way to close it

The gap is the photo files behind the 213 rows of `catalogue_provenance_strong.csv`. They are already catalogued, de-duplicated and hashed, and the harvester judged them suitable. Nothing new needs to be found.

This environment cannot reach the image hosts (bobswatches.com, dist.phillips.com, cdn.swisswatchexpo.com, thewosgroup.com). GitHub-hosted runners can.

The smallest closing step is a manual (`workflow_dispatch`) research job that:
1. re-fetches exactly the catalogued `image_url`s;
2. keeps a file only if its sha256 equals the catalogued value, so it is the same evidence and not a new source;
3. runs `Alpha96Calib` and `aggregate.py`;
4. uploads only the CSVs and report as an artifact, never committing images.

This needs the owner's approval because it downloads third-party photographs. Listings that have since changed will fail the hash check and are reported, not replaced.
