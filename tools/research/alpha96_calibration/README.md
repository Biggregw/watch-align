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

The closing step is `.github/workflows/alpha96-genuine-calibration.yml`, a research job approved by the owner on 2026-10-06. It:
1. re-fetches exactly the catalogued `image_url`s with `fetch_verified.py`, using the harvester's own HTTP client (same User-Agent, robots.txt respected, rate-limited per host);
2. keeps a file only if its sha256 equals the catalogued value, so it is the same evidence and not a new source. A file at the target path that does not match is deleted, so it can never be measured;
3. runs `Alpha96Calib` on the restored photos, then `aggregate.py --fetch-log`, which adds a restore table by host and status to the report;
4. uploads only the CSVs and report (artifact, 30 days) and prints the report to the job log. Images are never committed, cached or uploaded.

Listings that have changed since harvesting fail the hash check and are reported, not replaced.

It runs on a push that changes the workflow or `fetch_verified.py` on this branch. Manual dispatch works once the file is on the default branch.

## CI run 37500197377 (2026-10-06): first Alpha96 genuine distribution

Results are in `results/ci_run_37500197377/`: `report.md`, `watch_level.csv`, `fetch_status.csv` and `layout_split.txt`. The per-photo CSV and the fetch log are in the run artifact (30 days), which cannot be downloaded from this environment.

| Stage | Photos | Watches |
|---|---:|---:|
| Catalogued (provenance-strong) | 213 | 123 |
| Restored with matching sha256 | 156 | 76 |
| Hash mismatch (listing image changed; not replaced) | 57 (Bob's 48, SWE 9) | – |
| Accepted by Alpha96 pose | 144 | 70 |

- **Regression:** both phone references are still reproduced.
- **Statistics:** every distribution uses per-watch medians, so one physical watch counts once.
- **Limits:** no production limits have been set.

**Caveats before any limit is considered:**
1. **Resolution confound.** This was tested offline; see the next section. The worry holds for rotations, ring shift and the worst round marker. It does not hold for the 6, 9 and 12 local offsets.
2. **Duplicate images.** Resolved by removing shared photos; see the de-duplication section below.
3. **Outliers.** `m12_raw` reaches 12.9 px (0.036 R) on one photo, probably detector or pose error. Inspect it before using the tails.
4. **Pooled layouts.** Primary steel and gold-surround layouts are pooled in `report.md`. `layout_split.txt` shows they are similar, but gold-surround 9 and 12 rotations have wider tails.
5. **Within-watch repeatability.** It comes mainly from the Phillips multi-photo lots: 6–8 photos per watch, with a 6/9/12 rotation MAD of about 0.05–0.15°. That is smaller than the between-watch spread.

## Pixel and dial-radius comparison (offline, 2026-10-06)

Inputs:
- `resolution_analysis.py` with `make_scaled_set.py`;
- results in `results/resolution/comparison.md`;
- the scaled runner CSVs, with no images.

The genuine reference is one primary photo per watch from the CI run, 68 watches. The px and R values come from the same image, so that photo's dial radius is exact: median 434 px, range 129–835, 8 watches at 240 px or less.

The 8 local photos were re-measured at scales 1.0, 0.85, 0.7, 0.55 and 0.45, with 5 resampling variants per scale (a small rotation and a crop offset). That gives 200 runs of the unchanged Alpha96 code.

**Findings:**

1. **Genuine local offsets are geometric, so R units are the correct comparison.**
   - In px, the genuine 6, 9 and 12 local offsets and the worst round marker grow with dial radius (Spearman rho 0.58–0.78).
   - In R units they are flat: rho −0.12 to +0.06 for the 6 and 9 offsets. The R ≤ 240 bin matches the R > 360 bin (6: 0.0015 vs 0.0015 R; 12: 0.0040 vs 0.0039 R).
   - A small dial therefore does not inflate these values.
   - The exceptions are ring shift and the worst round marker. Their R ≤ 240 medians are about 2× and 1.6× those of large dials, so compare them with radius-matched genuine watches.
2. **Rotations are noisier on small dials.**
   - The genuine 6 and 9 rotation magnitudes at R ≤ 360 are about twice those at R > 360.
   - Rotations must be compared with radius-matched genuine watches. Those groups are small: 3–11 watches.
3. **Resampling noise is small at the controls' resolution.**
   - At about 200 px dial radius, the spread across variants is about 0.0002 R for local offsets and 0.05–0.11° for 6, 9 and 12 rotation.
   - That is well below the genuine between-watch spread.
   - The noise grows steeply below about 100 px (scale 0.45), where pose acceptance also falls to 50%.
   - Values in R units are stable from scale 1.0 down to 0.7. The 12 local offset has a mild upward bias of about 5–15% as the dial shrinks.
4. **Replica controls, after the resolution checks:**
   - **THEONE BLNR:**
     - 6 local offset 0.0047 R is beyond every genuine watch in R units (n=59) and stable across scales.
     - 12 local offset 0.0088 R and the worst round marker 0.0040 R are also beyond every genuine watch.
     - 9 rotation −0.85° and 12 rotation 0.96° are inside the radius-matched genuine range (20% and 17% exceedance). They are not distinctive once resolution is matched.
   - **Batgirl:**
     - 9 rotation −1.52° is beyond all 66 genuine watches, including the 9 radius-matched ones, and stays at −1.44° to −1.52° across scales. This is the strongest single signal.
     - 9 local offset 0.0045 R and the worst round marker are also beyond every genuine watch.
   - **RL_LOCAL BLNR** (labelled "6 left, 12 slightly tilted"):
     - Ring rotation 0.55° and ring scale 0.70% are beyond every genuine watch.
     - So are the 6 local offset and the 12 local offset (0.0139 R).
     - 12 rotation 0.79° is not distinctive (50% radius-matched exceedance), so the tilt shows up as offset, not rotation.
   - **ARF BLRO "crooked 6":**
     - 6 rotation is 0.008°, so the crooked 6 does not show as rotation.
     - It shows as 6 local offset 0.0037 R, beyond every genuine watch, together with ring scale 0.95%, 12 local offset 0.0102 R and the worst round marker 0.0046 R.
5. **Marketplace candidates:**
   - None is beyond every genuine watch on any marker offset or rotation.
   - Ring scale on 2 of 4 candidates (0.17% and 0.25%, stable across scales) exceeds every genuine primary photo.
   - Ring scale therefore responds to something other than resolution, perhaps lens or crop. It is not ready to be used.

**Limits of this evidence:**
- Only 4 replica controls and 4 candidates.
- Downscaling a photo imitates a smaller dial but not phone optics, blur or JPEG.
- The genuine reference uses one photo per watch (the largest dial).
- Beyond all n genuine watches means a genuine watch would do so with probability about 1/(n+1): about 1.5% for n=64, but about 14% for n=6.

No limits are derived.

## De-duplication of shared / stock photos (offline, 2026-10-06)

`dedup_units.py` writes `results/dedup/photos.csv`. It uses the harvester's own image state (`origin/data/harvest:state/images.jsonl`) to find photographs that appear under more than one catalogue watch:
- identical bytes;
- harvester-confirmed near copies;
- dial-crop copies;
- as a worst case, also the unconfirmed "possible" hash matches.

Such a photo cannot be tied to one physical watch, so it is excluded from every watch that lists it. Sharing a page alone links nothing; site-wide images would otherwise join 89 Bob's pages into one.

| Level | Shared photos | Watches losing all photos | Genuine reference watches (primary photo) |
|---|---:|---:|---:|
| none | 0 | 0 | 68 |
| exact | 6 | 6 | – |
| near / dial (harvester's confirmed definition; primary analysis) | 16 | 15 | 62 |
| possible (unconfirmed hash matches; worst case) | 86 | 71 | 31 |

No measured watch that still had an unshared photo was lost at the dial level (1 at the possible level), so working from primary photos costs nothing here.

**What the shared photos had been doing:**
- **Conflicting labels.** The genuine 9 rotation maximum (+1.17°) came from one Bob's photo. It is listed as a 126713GRNR on a "gmt_master_ii-black" page, and the same file is also listed as a 126710BLNR.
- **Double counting.** Two Bob's Root Beer pages shared one photo with large rotations (6 +0.68°, 9 +0.66°, 12 −0.82°), so it counted twice.
- **Spread.** After de-duplication at the dial level the genuine 9 rotation range is −0.58° to +0.68° (P90 0.44°); see `results/dedup/genuine_spread.md`. The other features barely change.
- **Small dials.** Most of the apparent "rotations noisier on small dials" effect came from these stock photos. Spearman rho for 6 and 9 rotation magnitude against R goes from −0.13 / −0.21 to −0.02 / −0.09, but only 4–5 genuine watches remain at R ≤ 240.

**Comparison rerun** (`results/dedup/comparison_dial.md`, `comparison_possible.md`):
- **Theonewatches 9 rotation −0.85°** is now beyond all 60 genuine watches (it was 2% before, and 20% among radius-matched watches). The genuine watches that matched it were stock photos. The radius-matched group is now only 2 watches, so this rests on the full-population comparison.
- **Unchanged at both levels:** every replica marker-offset finding (6, 9 and 12 local offsets beyond every genuine watch in R units), Batgirl's 9 rotation −1.52°, and the Local BLNR ring rotation 0.55°.
- **Marker offsets and rotations:** none of the marketplace candidates is beyond every genuine watch on these.
- **Ring scale:** still flags candidates, so it remains unusable.
- **12 local offset:** in R units it now rises with dial radius (rho +0.46; small-dial median 0.0019 R). The small-dial replica values of 0.009–0.014 R are therefore further from genuine, not closer.

## Why the genuine 12 offset appears to rise with dial size (offline, 2026-10-06)

Inputs:
- de-duplicated primary photos (level dial, 56 watches with a usable 12);
- `results/ci_run_37500197377/watch_level.csv`;
- an edge-definition test, `make_edge_set.py`, with results in `results/m12_dial_size/`.

**1. It is a source effect, not resolution.** Every genuine dial above R = 580 px is a SwissWatchExpo (SWE) full-size studio photo. Median 12 local offset by source:

| Source | Median 12 local offset | Dial radius |
|---|---:|---:|
| SWE full-size | 0.0064 R | ~776 px |
| Phillips | 0.0041 R | ~380 px |
| Bob's | 0.0035 R | 434 px, fixed image size |

The correlation with R comes from which source supplies the large dials.

**2. Same photograph, two resolutions.** Every SWE watch has its full-size photo plus SWE's own 900 px copy of the same shot and a second 900 px shot.
- Full-size minus the mean of the 900 px photos: +0.0006 R (range −0.0011 to +0.0017, 10 watches).
- That is about a fifth of the SWE-vs-Bob's gap.
- The scaled experiment on local photos drifts the other way (slightly up as the dial shrinks).
- Resolution alone therefore does not explain it.

**3. It is mostly the "down" (toward centre) component.**

| Source | Raw 12 down | Ring scale |
|---|---:|---:|
| SWE full-size | +0.0042 R | −0.13% |
| Phillips | +0.0043 R | −0.09% |
| Bob's | +0.0018 R | −0.01% |

Ring scale follows the same source order. Round-marker, 6 and 9 local offsets do not differ by source (0.0014–0.0018 R).

**4. Why the 12 is uniquely sensitive.**
- The 12 position is the centroid of the three fitted sides.
- The master triangle is tall: height 0.31 R, half-base 0.124 R. Its incentre therefore lies 0.019 R outside its centroid.
- A uniform outward shift d of the detected outline (lighting on the polished surround, bloom) moves the centroid toward the centre by 0.23 d, and the apex by 2.7 d.
- Round markers absorb the same shift as radius error, and batons stay centred.
- So photography that changes which edge is "outermost" moves the 12, and only the 12.

**5. The offline edge test is inconclusive for the 12.**
- Shifting outlines by ±1–2 px with greyscale dilation or erosion of the local photos (R 140–234) moves the round-marker radius error about 0.9 px per px. That confirms radius error as a direct gauge of edge definition.
- It also moves ring scale about +0.4–0.5% per px. This is a likely reason ring scale flags marketplace candidates.
- At these radii, though, about half the variants lose the pose or the 12 outline. The 12 responses that survive are not consistent in sign.

**Not established yet:** which mechanism applies. Candidates are edge definition, marker-height parallax under close or oblique studio cameras, or a master triangle position that matches Bob's-style photos.

**Evidence that would decide it:** per-photo `m*_radius_err_px` (edge definition), `ellipse_ratio` (obliqueness), and the 12 values for every genuine photo, compared across sources and within SWE's same-photo pairs. They are in `per_photo.csv` in the run 37500197377 artifact (expires 2026-11-05), which this environment cannot download.

**Consequence for now:**
- Treat the genuine 12 offset spread as photography-dependent, and do not pool it across sources for any limit.
- The replica 12 offsets (0.009–0.014 R) still exceed the SWE maximum (0.0084 R).
- They come from phone photos, yet another photographic condition. Phone-photo genuine references are a named evidence gap.
