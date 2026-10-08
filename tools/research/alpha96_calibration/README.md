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

**Per-photo follow-up.** The run artifact's `per_photo.csv` is now in `results/ci_run_37500197377/`. It was uploaded by the owner and agrees with all 250 checked primary values. The analysis is `m12_mechanism.py`, with results in `results/m12_dial_size/mechanism.md`.

6. **Most of the genuine 12 offset is one shared bias, not scatter.**
   - 58 of 59 genuine watches read the 12 toward the dial centre, with a median signed radial offset of −0.0038 R and tangential −0.0009 R.
   - Every model and every source shows it.
   - The triangle is also measured about 0.13° wider per side than the master, in every source.
   - The Alpha92 master's 12 position and shape therefore differ from what Alpha96 measures on genuine watches. The master is a nominal that has not yet been calibrated against the genuine population.
7. **Edge position, obliqueness and resolution do not explain the source difference.**
   - Linear model: edge position plus ellipse ratio explains R² = 0.00 of the 12 radial offset.
   - Across 43 same-photograph pairs at two resolutions, the median change is −0.0001 R.
   - Source explains R² = 0.22. SWE adds −0.0025 R over Bob's within the same models (BLNR −0.0064 vs −0.0037 R; CHNR −0.0051 vs −0.0025 R), in both its full-size and its separate 900 px shots.
   - It correlates with ring scale (Spearman +0.29 overall, +0.41 within SWE): photos whose marker ring reads smaller also pull the 12 further in.
   - That points to a photographic factor that acts radially, such as lens distortion or marker-height parallax with a close camera, rather than to the watches. It is not resolved.
8. **Re-centred on the genuine nominal** (research only; Alpha92 is unchanged), the genuine per-watch 12 offset drops from median 0.0039 / P90 0.0060 / max 0.0083 R to 0.0012 / 0.0028 / 0.0046 R.

   | Photo | Offset from the genuine nominal | Genuine watches at least as far |
   |---|---:|---:|
   | RL_LOCAL_BLNR | 0.0134 R | 0/59 |
   | ARF crooked-6 | 0.0116 R | 0/59 |
   | THEONE | 0.0050 R | 0/59 (marginal: genuine max is 0.0046 R) |
   | Marketplace candidates | 0.0010 R and 0.0014 R | 32/59 and 23/59 |

   - The two labelled replicas are about 3× the genuine maximum.
   - The marketplace candidates sit well inside the genuine spread.
   - SWE watches remain about twice as far from the nominal as Bob's (0.0022 vs 0.0010 R).

**Consequences:**
- For the 12, compare against a genuine-calibrated nominal, not the Alpha92 master position.
- Keep the SWE photographic effect in mind before any limit.
- Phone-photo genuine references remain a named evidence gap; every replica control is a phone photo.

No limits are derived and no production code is changed.

## Prototype: genuine-calibrated 12 reference (desktop harness only, 2026-10-06)

The prototype is research-only. Nothing under `android/` changes; the Alpha92 master and the production 12 measurement are untouched.

**Pieces:**
- `calibrate_m12_nominal.py` writes `m12_nominal.properties` and `results/m12_nominal/calibration.md`. It uses one value per physical watch (median of its photos) from the CI per-photo data, with shared photos excluded (level dial; 59 watches). The nominal is the median over watches.
- `tools/desktop-harness/drivers/Alpha97TriangleNominal.java` takes the unchanged production `Report`. It re-centres the 12 triangle's ring-local offset and its angles on that nominal, and fails closed exactly as production does (unusable triangle or unfitted ring gives no value).
- `tools/desktop-harness/drivers/Alpha97TriProto.java` runs `run.sh Alpha97TriProto <manifest> <root> <properties> <out.csv>`. It prints the production 12 line next to the prototype line.
- `check_m12_proto_parity.py` checks Java against Python on every output field. Result: 8 local photos and 166 scaled variants, 0 mismatches.

**Nominal, relative to the Alpha92 master** (bootstrap 95% interval over watches):
- radial −0.0038 R (−0.0043 to −0.0036), i.e. toward the centre;
- tangential −0.0009 R (−0.0011 to −0.0007);
- left side −0.17°, right side +0.11°, base −0.18°, centreline −0.10°.

**Held-out checks:**

| Genuine 12 offset, per watch (R) | Median | P90 | Max |
|---|---:|---:|---:|
| Alpha92 master | 0.0039 | 0.0061 | 0.0083 |
| Genuine nominal, leave-one-watch-out | 0.0012 | 0.0029 | 0.0046 |

- Leave-one-watch-out equals in-sample.
- Leaving out a whole source moves the nominal by at most 0.0007 R.
- The held-out SWE watches stay about twice as far from it as Bob's (median 0.0023 vs 0.0015 R). The SWE photographic effect is not modelled.
- The angle spreads shrink less (median |side error| 0.33° → 0.24°), so the shape correction is small.

**Local photos, prototype output:**

| Photo | Prototype 12 offset | Genuine watches at least as far |
|---|---|---:|
| GEN_CAND_HO_01 | 0.21 px (0.0010 R) | 32/59 |
| GEN_CAND_HO_02 | 0.20 px (0.0014 R) | 24/59 |
| THEONE | 0.37 px left · 0.74 px down = 0.83 px (0.0050 R); centreline +1.06° CW | 0/59, marginal (genuine max 0.0046 R) |
| RL_LOCAL_BLNR | 2.88 px left · 0.66 px up = 2.95 px (0.0134 R) | 0/59 |
| ARF crooked-6 | 1.96 px left · 1.87 px up = 2.71 px (0.0115 R) | 0/59 |
| Batgirl, WEX 01/02 | withheld (occluded), as in production | – |

**Display difference to note:** the production 12 line shows the raw offset from the master, before the ring model. The prototype shows the offset after the ring model and from the genuine nominal, because that is the quantity the nominal was calibrated on.

**Before this could become an app change:**
- an owner decision, because it changes the 12 numbers the phone shows;
- genuine phone-photo references, since every replica control is a phone photo and the nominal comes from dealer/auction photos;
- an account of the SWE effect;
- a refresh of the nominal whenever the genuine set changes.

No limits are derived.

## SWE photography effect (2026-10-06)

Inputs:
- `swe_effect.py` on the CI per-photo data, with results in `results/swe_effect/analysis.md`;
- the harness diagnostic `M12Diag`, run on the genuine photos by `.github/workflows/alpha96-m12-diagnostic.yml` (run 37508028297), with results in `results/swe_effect/ci_run_37508028297/`.

1. **No dial-wide photographic signature.** If lens distortion, marker-height parallax or crystal refraction were at work, they would also move other markers depending on their radius or direction. Nothing besides the 12 differs by source:
   - baton radial offsets: Bob's +0.0014 R, SWE +0.0016 to +0.0018 R;
   - round markers by hour: same pattern in every source;
   - round anisotropy and top-minus-bottom gradient: about 0;
   - dial-edge / lattice radius: 0.995 in both.

   The earlier ring-scale correlation came from mixing sources: within SWE it is 0.00. The effect is confined to the 12 triangle.
2. **Per-side diagnostic.** `M12Diag` calls the production edge finder and sampler by reflection, so no maths is copied. It reports where each triangle side's edge sits relative to the ring-moved master side. The side offsets reproduce the production 12 shift to a median of 0.0003 R over 95 genuine photos (and 0.0003 R on the local photos).
3. **The universal 12 bias is a master-shape mismatch.**

   | Source | Long sides outside master | Base outside master |
   |---|---:|---:|
   | Bob's | +0.0068 L / +0.0055 R | +0.0024 R |
   | Phillips | +0.0062 L / +0.0056 R | +0.0010 R |
   | SWE (6 photos) | +0.0085 L / +0.0056 R | +0.0007 R |

   - Geometrically, +0.001 R on both long sides moves the centroid −0.0009 R (toward the centre); +0.001 R on the base moves it +0.00067 R.
   - The long-sides-minus-base difference predicts the 12 radial almost exactly: Spearman −0.89 overall, −0.97 within Bob's.
   - The master triangle is narrower than the outermost (polished surround) edge that Alpha96 finds on genuine watches, mostly along the long sides. That explains the −0.0038 R nominal and the wider apex angle (+0.13°/side).
4. **SWE's extra shift is a per-side edge-selection effect, not overall brightness.**
   - Surround brightness terciles give 12 radial −0.0037, −0.0040 and −0.0041 R. Within Bob's the correlation has the opposite sign.
   - In SWE photos the base edge sits closer to the master (+0.0007 R vs +0.0024 R) and the left side further out (+0.0085 R vs +0.0068 R).
   - The CI montage (not committed: derived from third-party images) is consistent with this. Under SWE lighting the lume and polished surround read as one bright shape; in Bob's photos a dark gap and a bright outer rim are visible.
   - The likely mechanism: studio lighting changes which facet of the polished surround presents the outermost bright-to-dark edge on each side, and only the triangle turns unequal side outsets into a centroid shift.
   - Round markers absorb a uniform outset as radius error, and batons are symmetric.
5. **The evidence is decaying.** By this run, 50 of SWE's 56 catalogued images no longer match their sha256 (in run 37500197377, 47 did). The CDN now serves different bytes, so the sha256 check rejects them and they are not replaced. Point 4 therefore rests on only 6 SWE photos, and this SWE evidence cannot be re-measured byte-identically.

**Implications:**
- The 12 centroid is inherently sensitive to lighting on the surround facets.
- A genuine-calibrated nominal removes the shared bias but not lighting-dependent per-side differences.

**Lighting-robust alternatives worth testing offline:**
- side angles and centreline only, which are unaffected by side offsets;
- the inner lume edge instead of the outermost surround edge;
- a per-side consistency check that withholds the 12 when the side outsets disagree.

No limits are derived; no app code changes.

## Lighting-robust 12 alternatives, tested offline (2026-10-06)

Inputs:
- `m12_alternatives.py` on the CI per-photo data (59 watches, including 18 SWE watches from before SWE's images changed);
- the local and scaled photos;
- the M12Diag CSV.

Results are in `results/m12_alternatives/report.md`. The inner-edge prototype is `tools/desktop-harness/drivers/M12Inner.java`, evaluated by `m12_inner_eval.py` (`results/m12_alternatives/inner_eval.txt`).

Scores, all re-centred on the genuine nominal, with genuine watches scored leave-one-watch-out:

| Measure | SWE shift vs Bob's, genuine MADs | Same-photo resolution SD / genuine MAD | Within-watch / between-watch MAD | Replicas beyond every genuine watch |
|---|---:|---:|---:|---|
| current 12 offset (2-D) | magnitude +1.94 | 0.84 | 0.33 | 3/3 |
| radial only | −2.32 (signed) | 0.82 | 0.47 | 1/3 (ARF) |
| lateral (left/right) only | −1.25 signed, −0.98 magnitude | 2.14 | 0.66 | 3/3 (THEONE, LOCAL, ARF all 0/59) |
| centreline rotation | −0.87 signed, −0.23 magnitude | 0.38 | 0.17 | 0/3 (THEONE 2/59, LOCAL 4/59, ARF 22/59) |
| side angles | magnitude +0.34 | 0.26 | 0.14 | 0/3 (THEONE 2/59, LOCAL 3/59, ARF 20/59) |
| apex half-angle | −0.56 signed | 1.15 | 0.59 | 0/3 |

Reading the table:
- **Radial** is the lighting-sensitive component: SWE moves it by 2.3 genuine MADs.
- **Angles** (centreline, sides) are the most lighting-robust and repeatable. They cannot see a translated 12, so ARF's shifted triangle sits mid-genuine on angles.
- **Lateral position** keeps translation sensitivity with about half the SWE shift of radial. Its genuine spread is very small (P90 0.0014 R), so pixel noise is large relative to it at phone resolution. The two marketplace candidates sit mid-genuine on it (14/59 and 18/59).

**Per-side consistency gate** (M12Diag, curve only, no gate chosen):
- Withholding photos whose long-sides-minus-base edge difference is more than 3 genuine MADs from the median keeps 81 of 95 genuine photos.
- That changes the SWE 12 radial from −0.0057 to −0.0045 R (Bob's −0.0037 R), removing about half the SWE excess.
- It rests on only 6 SWE photos.

**Inner lume edge: rejected at phone resolution.**
- The surround reads only about 0.016 R wide (0.011–0.021 R), 2–3 px on phone photos.
- The inner edge was separable on 17 of 24 sides but on all three sides of only 1 of 8 photos. Separability falls with scale: 68% of sides at 1.0, 25% at 0.45.
- When it is not separable, the side jumps by a whole surround width.
- Same-photo SD across scales is 0.0026 R radial with the inner edge, against 0.0007 R with the outermost edge. That is as large as the SWE effect it was meant to remove.
- Untested on high-resolution dealer photos; they are not the target use.

**Recommendation for a future 12 (research only; no limits; app unchanged):**
- Report the 12 as centreline rotation plus side angles, which are robust, together with lateral position re-centred on the genuine nominal, which keeps translation sensitivity.
- Show the radial component only with a lighting caveat, or withhold it.
- Optionally add the per-side consistency gate as a fail-closed quality check, once genuine phone-photo references can set its scale.

## SWE excluded from the genuine reference; angles + lateral 12 prototype (2026-10-06)

**Owner decision: ignore the SWE photos.** Their studio lighting moves the 12, and most of their catalogued images no longer verify. `calibrate_m12_nominal.py` now leaves SWE out by default (`--exclude-source none` restores the old behaviour). `m12_nominal.properties` and the new `m12_genuine_reference.csv` are rebuilt from 41 watches: Bob's 32, Phillips 7, other 2.

**New nominal, relative to the master:**
- radial −0.0037 R, tangential −0.0008 R;
- left side −0.14°, right side +0.15°, base −0.14°, centreline +0.04°.

The genuine 12 offset from it is median 0.0009 / P90 0.0020 / max 0.0032 R, both in-sample and leave-one-watch-out (with SWE it was 0.0012 / 0.0029 / 0.0046 R). Leaving out a source moves the nominal by at most 0.0004 R.

**Prototype pieces (harness only; app unchanged):**
- `tools/desktop-harness/drivers/Alpha97TwelveAngles.java` takes the unchanged production `Report`. It reports centreline rotation, left/right side angles and lateral (left/right) position, all from the genuine nominal. Context is the number of genuine watches at least as far on each component, from `m12_genuine_reference.csv` (each watch computed without itself).
- The radial position is shown only as a bracketed lighting-sensitive note.
- No thresholds or verdicts; fail-closed as production.
- `Alpha97TwelveProto.java` is the driver: `run.sh Alpha97TwelveProto <manifest> <root> <properties> <reference> <out.csv>`.
- `check_m12_angles_parity.py` checks Java against Python: 8 local photos and 166 scaled variants, 0 mismatches.

**Local photos** (results in `results/m12_angles_lateral/`). Values are genuine watches at least as far, out of 41:

| Photo | Centreline | Sides | Lateral | Full 2-D offset |
|---|---:|---:|---:|---:|
| GEN_CAND_HO_01 | 10 | 14 | 7 | 16 |
| GEN_CAND_HO_02 | 8 | 9 | 13 | 10 |
| THEONE | 2 | 0 | 0 | 0 |
| RL_LOCAL_BLNR | 4 | 1 | 0 | 0 |
| ARF crooked-6 | 23 | 14 | 0 | 0 |
| Batgirl, WEX 01/02 | withheld (occluded) | | | |

- Lateral position puts every replica beyond all genuine watches; the angles catch THEONE and LOCAL but not ARF.
- With SWE excluded, the full 2-D offset (including radial) also separates all three replicas while keeping both candidates inside.
- Whether radial needs demoting therefore depends on how much lighting varies in phone photos. Genuine phone-photo references are still the deciding evidence.

**Decision (owner, 2026-10-06): robust readout.**
- Assess the 12 by centreline rotation, side angles and lateral position from the genuine nominal.
- Show radial only as a lighting-sensitive note.
- The recalibrated full 2-D offset is not used.

## Alpha97 research-display APK (2026-10-06)

`android/app/src/main/java/com/watchalign/mobile/Alpha97TwelveReadout.java` implements the robust 12 readout in the app. Its frozen constants are generated from `m12_nominal.properties` and `m12_genuine_reference.csv`: 41 watches, SWE excluded.

**What changed:**
- The phone summary and inspector now show `Alpha97TwelveReadout.summary(report)`. This is the Alpha96 `compactSummary()` with only its 12 line replaced.
- The 12 line reports centreline, left side, right side and left/right position from the genuine reference, plus reference-watch context counts.
- Toward-centre is shown only as "lighting-sensitive · not assessed".
- No thresholds, verdicts or colour.
- The 12 is withheld exactly as in Alpha96 when it is occluded, has insufficient clean edge, or the ring is not fitted.
- Pose, Alpha94 measurement, Alpha92 master and recovery code are byte-identical to Alpha96 (5adde5c).

**Checks:**
- **`Alpha97TwelveReadoutTest`** (JUnit, 8 tests) pins the constants to the research files. It checks parity with the desktop prototype on the recorded production inputs (8 local photos and the scaled variants), the known local-photo findings, fail-closed behaviour and wording, and that every non-12 summary line is identical to Alpha96.
- **`tools/desktop-harness/drivers/Alpha97AppParity.java`** compares the app class with the desktop prototype on the full production pipeline: 8 local and 166 scaled photos, 0 mismatches.
- **Alpha96 regression:** `Alpha96Calib` rerun on the local photos is identical to `results/runner_local.csv` (ring, 6, 9, rounds, 12 fields, pose: 1,592 values, 0 differences).

**User-supplied photos through the Alpha97 readout** (descriptive only; not added to the reference; images not committed). Results are in `results/m12_angles_lateral/user_photos_alpha97.csv`. Counts are reference watches at least as far, out of 41.

| Photo | Result |
|---|---|
| 16700 (older reference, Rolex CPO image) | withheld: ring not fitted |
| 126710BLRO dealer screenshot | withheld: 12 occluded by the GMT hand |
| 116710LN ×3 (two Rolex CPO images, one listing screenshot) | centreline 8–26, sides 10–23, lateral 2–18 |
| 126710GRNR dealer | centreline 6, sides 7, lateral 22 |
| 126710BLNR Oyster | centreline 32, sides 23, lateral 11 |
| 126710BLNR Jubilee Rolex CPO | centreline 27, sides 32, lateral 28 |
| 126715CHNR Rolex CPO | centreline −0.99°, sides −0.94°/−0.95°: **1/41**; lateral 20 |

- A genuine CPO watch reads 12 angles as far out as THEONE (+0.93°, 2/41). The angle findings are therefore not distinctive on their own.
- Lateral position held: genuine photos read 0.0002–0.0017 R, against THEONE 0.0024 R, ARF 0.0084 R and LOCAL 0.0132 R.

## Owner-priority official / Rolex CPO photos in the reference (2026-10-06, Alpha97b)

**Owner decision:** the owner-supplied official / Rolex CPO photos take priority.
- They are listed in `priority_genuine.csv` with provenance, an include/exclude reason and a sha256; the images are not committed.
- Their production measurements are in `results/priority_genuine_runner.csv`.
- `calibrate_m12_nominal.py` always includes rows with `include=yes`, labelled "Owner priority". They are never removed by `--exclude-source`, so SWE stays excluded.
- They get no extra weight in the medians: 4 photos would otherwise dominate a reference built from one value per watch.

**In-scope photos:**

| Photo | Included |
|---|---|
| 126710GRNR, 126710BLNR (Oyster), 126710BLNR (Jubilee, CPO), 126715CHNR (CPO) | yes |
| 126710BLRO screenshot | included, but its 12 is occluded |

**Out of scope:** 16700 and three 116710LN images. These are predecessor references, listed as `unsupported_predecessors` in `families.py`.

**Contamination check before widening the envelope** (CLAUDE.md), for the 126715CHNR CPO, whose 12 reads about −0.9° on angles:
- square-on (ellipse ratio 0.999) with tick RMS 0.12 px;
- ring rotation +0.10° and 9 rotation +0.05°;
- so there is no shared rotation, and the 12 angle is the triangle's own.

Its 6 baton reads +2.30°, with the seconds hand at 6; it is likely contaminated and not used.

**New reference (45 watches: Bob's 32, Phillips 7, other 2, Owner priority 4):**
- nominal radial −0.0037 R, tangential −0.0007 R;
- left −0.17°, right +0.13°, base −0.16°, centreline −0.09°.

The 2-D offset maximum rose to 0.0083 R entirely through the 126710GRNR photo's radial part (2.1 px toward the centre on a white background; its lateral is only 0.0004 R). Even official imagery moves the radial reading, which the robust readout does not assess.

**Readout against 45 watches** (counts are reference watches at least as far: centreline / sides / lateral):

| Photo | Counts |
|---|---|
| THEONE | 0 / 0 / 0 (centreline +1.05°; the nearest genuine is the CHNR CPO at −0.86°, a thin margin) |
| RL_LOCAL_BLNR | 3 / 1 / 0 |
| ARF crooked-6 | 18 / 16 / 0 (lateral finding, ordinary angles) |
| GEN_CAND_HO_01 | 18 / 17 / 6 |
| GEN_CAND_HO_02 | 7 / 8 / 12 |
| Batgirl, WEX 01/02 | withheld |

**Alpha97b checks:**
- `gen_alpha97_constants.py` regenerates the app constants.
- `Alpha97TwelveReadoutTest` is updated: 45 watches, 4 owner-priority watches present, SWE excluded; 8/8 pass locally.
- App-versus-prototype parity: 8 local, 166 scaled and 9 owner photos, 0 mismatches.
- Alpha96 regression: 0 differences.

## Date window: digit centring and tilt prototype (offline, 2026-10-07)

**Pieces:**
- `tools/desktop-harness/drivers/DateCrop.java` (harness only) exports an upright, dial-plane-rectified crop of the 3 o'clock region. It uses the frozen production pose and the production sampler, by reflection. The crop covers x 0.22–1.02 and y ±0.38 R at 0.0025 R/px.
- `date_window.py` measures inside the magnified window. Only ratios and angles inside the magnified image are used, so the cyclops magnification cancels:
  - **window:** the brightest class (two-level Otsu) nearest the expected position, fitted with a rotated rectangle;
  - **window_tilt:** rectangle angle plus sub-pixel gradient tilt of the window's top and bottom edges, against the dial horizontal;
  - **digit_dx / digit_dy:** numeral ink-box centre minus window centre, as a fraction of window width / height;
  - **digit_tilt:** two-digit dates only; the line through the two numerals' sub-pixel mid-heights, against the window.
- It withholds with a reason for: no plausible window, a window that isn't rectangular (glare, reflection or occlusion), an implausible aspect, a numeral cut by the window edge (mid-change or a hand), or implausible ink. No thresholds are applied to the measurements, and there are no verdicts.

**Synthetic validation** (`date_window_synth_check.py`, `results/date_window/synthetic_checks.txt`):
- Whole crop rotated ±2°: window tilt follows (+1.88 to +2.17°, −1.99 to −2.15°), and digit tilt stays put (within 0.2°; one case 0.46°).
- Interior only rotated +2° / −1°: digit tilt follows (+1.96 to +2.20°, −0.82 to −1.18°), and window tilt stays put (within 0.08°).
- Interior shifted 2–3 px: centring follows exactly, apart from the 1 px rounding of the ink box (about 0.004–0.007 of the window).

**Font effects mean a reference is needed for each date:**
- Glyph shapes shift both centring and row tilt.
- Round digits overshoot flat ones: four genuine "28" photos read −0.38 to −0.59° row tilt.
- A single "13" listing screenshot read +2.2°.
- So genuine references are needed for each date, or each numeral pair, before anything is compared. The genuine catalogue covers many dates; the Rolex CPO images are all "28".

**Local and owner photos** (`results/date_window/date_local_owner.csv`): 10 of 18 measured.

| Photo | Reading |
|---|---|
| genuine 28s (4 CPO-style) | dx −0.004 to −0.011, dy −0.012 to −0.018, row tilt −0.38 to −0.59°, window tilt −0.29 to +0.27° |
| RL_LOCAL_BLNR ("11", identical glyphs) | window tilt +2.44°, digit row −2.51° relative to it (numerals level with the dial, aperture rotated) |
| Batgirl (two photos, dates 9 and 4) | window tilt −0.82° and −1.14° |

Withheld:
- glare or reflection: 3;
- reflection merged into the window: 2;
- a hand: 1;
- numeral cut by the edge: 2 (THEONE and ARF, both darker photos). These are probably false withholds, because the window box hugs the digits there; refining the window edges from the gradient profile is the next fix.

**Next:**
- Refine the window edges.
- Run DateCrop and `date_window.py` on the sha256-verified genuine catalogue in CI to build genuine references for each date (one value per watch).
- Check repeatability on the multi-photo genuine watches.

## Date window on the genuine catalogue (CI runs 37662260742, 37663577357, 37664650850; 2026-10-07)

**Window edges** (step 1):
- Edges are refined from the brightness profile: a sub-pixel half-way crossing, searched outward from the numerals.
- Window tilt comes from Theil-Sen lines fitted to the top and bottom edges.
- Synthetic whole-crop rotation of ±2° reads ±1.96 to 2.03°, and +1° reads +0.97 to +1.03°. Digit tilt stays within 0.2°.

**Row-tilt fixes:**
- Glyph extents now use the extreme ink rows (15% of the peak). Numerals with a heavy bar (7, 4, 2) broke the 50% level, giving −30 to +13° for 17, 24 and 14.
- Row tilt is withheld when the two glyph heights disagree by more than 12%. The Phillips "24" lot read +11° on every photo.

**Dates:** hand-labelled from the run-1 montage (`results/date_window/date_labels_catalogue.csv`). 157 photos are labelled; 2 are unreadable (mid-change, no window) and 4 have a hand over the window.

**Merging runs:**
- SWE's CDN flips between serving the catalogued bytes and different bytes: 171, then 112, then 112 photos verified across the three runs.
- Run 3 is used where available.
- Photos verified only in run 1 contribute window tilt and centring, which the fixes did not change, but not row tilt.

**Reference** (step 2): `date_reference.py` gives one value per watch, with shared photos excluded and owner-priority CPO photos included. The primary analysis excludes SWE (`results/date_window/reference.md`); `results/date_window/reference_with_swe.md` is a sensitivity analysis that includes it.

| | SWE excluded (36 watches) | SWE included (52 watches) |
|---|---|---|
| window tilt median / P10 / P90 | −0.08 / −0.34 / +0.12° | −0.04 / −0.38 / +0.35° |
| window tilt range | −0.66 to +0.42° | −1.09 to +0.52° |
| dates with ≥ 3 genuine watches | 22, 23, 28, 30 | adds 9 (16 watches, almost all SWE) |
| two-digit row tilt, per date | within about ±0.5° (28: −0.15 to +0.19°; 30: −0.73 to −0.17°) | same |

**Repeatability** (step 3), from watches with ≥ 2 photos of the same date:

| | Watches with ≥ 2 photos | Window tilt within / between | dx within / between | dy within / between |
|---|---:|---|---|---|
| SWE excluded | 4 | 0.13° / 0.15° | – | – |
| SWE included | 20 | 0.11° / 0.18° | 0.004 / 0.005 | 0.005 / 0.009 |

(Within = median spread across one watch's photos; between = between-watch MAD, for centring on date 9.)

- Window tilt is repeatable within a watch.
- Centring is repeatable to about 0.005 of the window, which is comparable to the genuine spread between watches.

**Photos against the reference** (genuine watches at least as far):

| Photo | Window tilt | Centring / row tilt |
|---|---|---|
| Batgirl photo 1 (date 9) | −0.86°: 0/36 (1/52 with SWE) | ordinary against 16 genuine "9"s (16/16 and 8/16; SWE only) |
| Batgirl photo 2 (date 4) | −0.72°: 0/36 (3/52) | – |
| RL_LOCAL_BLNR (date 11) | +1.00°: 0/36 (1/52) | row tilt −1.14°; no "11" reference |
| THEONE (date 25) | +0.11°: ordinary | row tilt −0.82°; no "25" reference with ≥ 3 watches |
| Genuine CPO 116710LN | −0.65°: 1/36 | – |

**Reading:**
- Window tilt is the usable date-window feature now: it is date-independent, repeatable, and has 36–52 reference watches.
- Batgirl and LOCAL sit at or beyond the genuine edge. But SWE includes one genuine watch at −1.09°, so the Batgirl reading is edge-of-genuine rather than clearly beyond.
- Centring and row tilt need references for each date. Only 4–5 dates have ≥ 3 genuine watches, and most dates have none. That is a named evidence gap: genuine photos across all 31 dates.

No limits are derived and the app is unchanged.

**Owner decision (2026-10-07): include SWE in the date-window reference.**
- `date_reference.py` now includes SWE by default: `results/date_window/reference.md`, 52 watches, the primary reference.
- `--exclude-swe` writes the sensitivity analysis: `reference_without_swe.md`, 36 watches.
- The 12-triangle reference is unchanged and still excludes SWE (that decision was about SWE's studio lighting moving the 12).
- With SWE included:
  - window tilt reference: −1.09 to +0.52° (median −0.04°, P10 −0.38°, P90 +0.35°);
  - Batgirl photos −0.86° and −0.72°: 1 and 3 genuine watches as far, so edge-of-genuine;
  - RL_LOCAL_BLNR +1.00°: 1 genuine watch as far;
  - date 9 gets a 16-watch centring reference, against which Batgirl photo 1 is ordinary.

## Alpha98: human-readable results with close-ups (2026-10-07)

**Owner decisions:**
- A feature is reported as "outside the measured genuine range" only when it reads further than every genuine reference watch.
- Features covered: 6, 9, 12 (robust readout), round markers, ring, date-window tilt.
- Technical numbers sit behind a collapsed "Technical details" section.

**Reference:** `build_alpha98_reference.py` writes `alpha98_reference.csv` and `alpha98_nominal.properties`; `gen_alpha98_constants.py` turns them into `Alpha98Reference.java`.
- 6, 9, round markers and ring: SWE excluded plus owner-priority watches (44–48 watches).
- 12: Alpha97b, 45 watches.
- Date-window tilt: SWE included (52 watches).
- Each watch's own distance is computed from a nominal without it.
- Round markers and ring shift: compared only with genuine watches whose dial radius is at most 1.3× the photo's; fewer than 8 such watches means not assessed. Small dials inflate these two.
- A reference watch can never be flagged against itself: the comparison allows for the 6-decimal storage of the reference.

**App changes** (`android/`; pose and Alpha94 measurement byte-identical to Alpha96):
- `Alpha98DateWindow`: port of the date-window tilt. Parity: 18/18 crops pixel-identical to DateCrop, 18/18 usable/withheld decisions identical, tilts within 0.014° (`results/alpha98/date_parity.txt`). The residual comes from OpenCV 4.9 vs 5.0.
- `Alpha98Findings`: plain-language findings with fail-closed reasons.
- `Alpha98Closeups`: an upright, perspective-corrected close-up per finding. The genuine outline is drawn where the other markers predict the marker (ring fit); the ring close-up uses the unmoved master; the date window shows its measured edges plus a level line.
- `Alpha98ResultsActivity`: headline, disclaimer, close-up cards (tap to zoom), the within-range list, not-assessed reasons, the full overlay, and collapsed technical details. The main screen wording is simplified.

**Checks:**
- `Alpha98FindingsTest` (7 tests) pins the constants to the research files. The flag decisions match the offline check on all local and owner photos. It also covers the resolution rule, the date rule, the self-reference tolerance, the wording (no verdict words, disclaimer present) and the numpy-equivalent helpers.
- The Alpha97 tests (8) still pass.
- Alpha96 regression: 0 differences over 1,592 values.

**Desktop preview** (`Alpha98Preview`, results in `results/alpha98/preview_text.txt`):

| Photo | Outside the genuine range |
|---|---|
| Batgirl, photo 1 | 6 rotation; 9 rotation and offset; 2 o'clock round marker (12 withheld: hand) |
| Batgirl, photo 2 | 6 rotation; 9 rotation and offset; 2 o'clock round marker |
| THEONE | 12 (centreline, side, lateral); 6 offset; 9 rotation |
| RL_LOCAL_BLNR | 12 lateral; 6 offset; ring rotation |
| ARF crooked-6 | 12 lateral; 6 offset; 8 o'clock round marker |
| Genuine owner photos | nothing, except the 126715CHNR CPO's 6, where the seconds hand sits on the marker (visible in the close-up; the caption tells the user to check for a hand) |
| Marketplace candidates | nothing |

## Alpha99: evidence strength and per-marker interference (2026-10-07)

Alpha99 changes only the evidence layer and the results screen. Pose, the Alpha94 measurement, the Alpha92 master, the date-window maths and the genuine reference are byte-identical to Alpha98.

### Measurement-uncertainty allowances (genuine photos only)

`measurement_uncertainty.py` writes `alpha99_uncertainty.properties` and `results/alpha99/uncertainty.md`; `gen_alpha99_constants.py` turns them into `Alpha99Uncertainty.java`.

- **Source:** every genuine physical watch photographed two or more times, with non-shared photos (`ci_run_37500197377/per_photo.csv` and `results/dedup/photos.csv`). Same basis as the Alpha98 reference: SWE excluded for the markers and the 12; SWE included for the date window (`date_window/run3`, with `run1` as fallback).
- **No replica, marketplace-candidate or owner test photo is read.**
- **sigma** is the pooled within-watch standard deviation of a single photo's reading: the spread between different photos of the same genuine watch, around that watch's own mean. It covers pose, perspective, lighting and detector error together.
- Positional families pool their radial and tangential components. They are pooled both in R and in px; the app uses max(sigma_R, sigma_px / photo R), so a small photo never gets a smaller allowance than its pixel noise implies.
- Angle families are also pooled as degrees × R, because an angle's error is a pixel edge error divided by the feature's length, which scales with R. The app uses max(sigma_deg, sigma_degR / photo R).

| Family | sigma | 3 sigma | Watches (photos) |
|---|---|---|---|
| 6 rotation | 0.195° | 0.585° | 9 (51) |
| 9 rotation | 0.205° | 0.616° | 8 (49) |
| 6 position | 0.125% R / 0.25 px | 0.37% R | 9 (51) |
| 9 position | 0.090% R / 0.20 px | 0.27% R | 8 (49) |
| Round-marker position | 0.057% R / 0.11 px | 0.17% R | 9 (43) |
| 12 lateral | 0.202% R / 0.41 px | 0.61% R | 9 (51) |
| 12 centreline | 0.184° | 0.552° | 9 (51) |
| 12 sides | 0.487° | 1.460° | 9 (51) |
| Ring rotation | 0.024° | 0.072° | 9 (51) |
| Ring shift | 0.059% R / 0.32 px | 0.18% R | 9 (51) |
| Date-window tilt | 0.149° | 0.447° | 20 (71) |

Angle families, scaled by resolution (sigma_degR / R; this applies when it is larger than the fixed sigma above):

| Family | sigma_degR | At R 100 | At R 200 | At R 300 |
|---|---|---|---|---|
| 6 rotation | 45.3 | 0.45° | 0.23° | 0.195° (fixed) |
| 9 rotation | 43.4 | 0.43° | 0.22° | 0.205° (fixed) |
| 12 centreline | 72.6 | 0.73° | 0.36° | 0.24° |
| 12 sides | 82.8 | 0.83° | 0.49° (fixed) | 0.49° (fixed) |
| Ring rotation | 7.9 | 0.079° | 0.040° | 0.026° |
| Date-window tilt | 119.8 | 1.20° | 0.60° | 0.40° |

The genuine-catalogue run (37675166666) is why angles now scale with resolution. Before the change, one genuine photo was a CLEAR finding: a 126711CHNR from Phillips at dial radius 103 px, whose 12 left side read 3.2° off. The same watch's 7 other photos all read about 0.9°. The fixed angle sigma came mostly from photos at R 230–330, so it did not cover a 103 px photo. With scaling, that photo is WORTH A LOOK. The local photos' classifications are unchanged.

The marker families come mostly from Phillips auction lots, which have 6–8 photos each from different angles. Bob's adds pairs. 12 lateral is driven by two Phillips lots (146213 and 210072): their photo-to-photo SD is 0.45% R and 0.25% R, against at most 0.07% R for the rest. That makes the 12 lateral allowance conservative, so fewer findings become CLEAR.

**Rule.** It was fixed before any photo was classified, and K is a single value for every family.
- **WITHIN RANGE:** not beyond every genuine reference watch. This is the Alpha98 rule, unchanged; the genuine range is never widened.
- **WORTH A LOOK:** beyond the genuine maximum, by no more than 3 sigma. Photo or measurement error could account for the excess.
- **CLEAR FINDING:** beyond the genuine maximum by more than 3 sigma.
- A family without a sigma could be outside the range but never CLEAR. Every assessed family currently has one.

### Per-marker interference check (`Alpha99MarkerInterference`)

Every individual marker (12, 6, 9 and each round) must pass this check before it can become a finding. Otherwise it is NOT ASSESSED, with the reason "hand crosses or touches this marker" or "reflection or glare around this marker". The check reads the image on the frozen pose and never changes a measurement.

Alpha94's outline-integrity test (≥ 0.80) misses thin hands: a seconds hand or a GMT shaft covers only a few percent of the outline. The check has three parts. All its geometry is fixed in canonical dial units from the Alpha92 master.

1. **Strip beside and inside the marker.**
   - Sampled along the marker's radial axis: the marker ± 0.04 R, plus a 0.12 R corridor towards the centre (for the 12, from r 0.50).
   - A sample is foreign if it differs from its row's median dial by more than max(20 grey levels, 5 × robust noise).
   - A hand is a connected foreign structure that spans at least 0.05 R radially and comes within **0.012 R + 1 px** of the marker's outline. To measure how close it comes, the structure is followed into the outline's halo, judged against the halo's own glow profile.
   - Why 0.012 R: Alpha94's final edge search reads ± 0.012 R around the outline. Anything closer can enter the measured edge; anything further cannot. This rule comes from the measurement code, not from any photo.
2. **Marker face.** A thin structure (≤ 0.05 R wide) spanning ≥ 0.05 R across the lume, such as a hand lying over the marker.
3. **Seconds hand.**
   - On a black dial, the dark shaft is visible only where it crosses a marker's bright edge. The 126715CHNR CPO's 6 is the example: it passed both Alpha94 and parts 1–2.
   - Its lume dot fixes the line the shaft runs along. The dot is a bright disc on a thin shaft, centred between r 0.50 and 0.60, outside the date-window region.
   - Contrast: at least 0.45 × the photo's marker-lume contrast.
   - Every marker that the line from the centre through the dot passes within 0.012 R + 1 px + 0.006 R (the shaft's half-width) is withheld.
   - The 0.45 was set on the 18 local photos. Real dots read 0.51–1.0 of the lume contrast; text and hand lume bars read at most 0.39.

**Fail closed:** no image, no pose, or an unreadable strip means the marker is withheld.

**Local photos** (`results/alpha99/interference_local.csv`, `summary_local.txt`): 38 of 198 marker checks withheld, 23 of which Alpha94 had also withheld. Every withheld marker was inspected in a close-up:

- GMT arrows over the 5, 8, 9 and 12;
- minute and seconds hands across rounds;
- seconds hands along the 6 on four CPO photos, including the CHNR.

The rule passed three hands that run 3–5 px clear of a marker. Batgirl photo 1's minute hand passes 5.1 px from the 9, so the 9 stays assessed.

### Before / after (desktop preview, `results/alpha99/findings_local.csv`, `preview_text.txt`)

| Photo | Alpha98 (beyond every genuine watch) | Alpha99 |
|---|---|---|
| Batgirl, photo 1 (R 205) | 6 rotation; 9 rotation and offset; 2 o'clock round; 12 withheld | **CLEAR:** 9 (1.6° CCW, max 0.6°); 2 o'clock (0.44%, max 0.26%). **WORTH A LOOK:** 6 (0.8° CW, max 0.7°); 8 o'clock (0.39%). **NOT ASSESSED:** 12 (GMT hand) |
| Batgirl, photo 2 (R 278) | 6 rotation; 9 rotation and offset; 2 o'clock round | **CLEAR:** 9 (1.4° CCW); 2 o'clock (0.43%). **WORTH A LOOK:** 6 (1.0° CW); 8 o'clock (0.39%). **NOT ASSESSED:** 4 and 10 (hands) |
| Theonewatches (R 165) | 12 centreline, side, lateral; 6 offset; 9 rotation | **WORTH A LOOK:** 6 (0.47%, max 0.30%); 9 (0.9° CCW). **NOT ASSESSED:** 12 (seconds hand alongside); round markers (resolution) |
| Local BLNR (R 220) | 12 lateral; 6 offset; ring rotation | **CLEAR:** 12 (1.33% to the left, max 0.23%); dial marker ring (0.52° CW, max 0.20°). **NOT ASSESSED:** 6 (hand across its face); 9, 5, 8 (hands) |
| ARF crooked-6 (R 234) | 12 lateral; 6 offset; 8 o'clock round | **CLEAR:** 12 (0.85% to the left); 8 o'clock (0.46%); 11 o'clock (0.45%). **WORTH A LOOK:** 6 (0.37%); 7 o'clock; 10 o'clock. **NOT ASSESSED:** 1, 2, 5 (hands) |
| 126715CHNR CPO | 6 rotation 2.3° and offset (seconds hand) | **NOT ASSESSED:** 6 (seconds hand). Nothing outside |
| Other owner official / CPO photos, marketplace candidates | nothing | nothing (markers under hands withheld) |

- No feature is outside in Alpha99 unless Alpha98 had it outside (`alpha99_genuine_summary.py`).
- The extra round hours (Batgirl 8 o'clock; ARF 7, 10 and 11 o'clock) were already outside in Alpha98's single round-marker feature, which named only the worst. Alpha99 judges and names each clean round.
- The recent QC photo with a hand across the flagged round marker (Geektime / RepTimeQC) is not in this session's files. The synthetic regression tests cover that case (`Alpha99FindingsTest`); the real photo should be added to the local checks when supplied.

### Regression

- **Alpha96 runner:** 0 differences over 1,592 values on the 8 local photos, and 0 over 1,791 on the 9 owner photos (timing column excluded).
- **Frozen files:** `Alpha94MarkerMeasurement`, `Alpha92GmtMaster`, `AutomaticDialOverlay`, `Alpha91*`, `Alpha98DateWindow`, `Alpha98Reference` and `Alpha97TwelveReadout` have no diff from Alpha98 (2cb22c2).
- **Unit tests:** `Alpha99FindingsTest` (16), `Alpha98FindingsTest` (7) and `Alpha97TwelveReadoutTest` (8) pass.
- **Date window needs the marker ring:** the date tilt is read relative to the pose, so it is assessed only when the marker ring was measured, which confirms the dial's orientation. Otherwise it is not assessed ("orientation not confirmed"). This was found on the genuine catalogue (run 37678284382): a Bob's 126710BLRO photo with a mis-registered pose had its markers withheld and no ring fit, and read a level date window as a 6.0° CLEAR finding. Locally, only the 16700 CPO changes (its ring cannot be fitted on the predecessor layout).
- **Genuine catalogue, final (run 37680031278):** 104 photos with a pose.
  - 229 of 1,177 marker checks were withheld (19.5%); Alpha94 had already withheld 139 of them. The 2 o'clock is withheld most often, because catalogue photos usually show the hands at 10:10.
  - 0 new findings relative to Alpha98.
  - 35 genuine photos still show at least one WORTH A LOOK (51 features). Each is a single photo compared with per-watch medians, typically an angled Phillips auction shot; Alpha98 already reported every one as outside, and Alpha99 grades them as within measurement uncertainty rather than clear.
  - One genuine photo remains a CLEAR finding: Bob's 126715CHNR `ee1933f4d3ba3a7d`, 6 o'clock 0.72% out of place (genuine max 0.30%). The close-up shows no hand. This watch was not in the reference, because its photo failed to fetch in run 37500197377. It shows that the 44-watch genuine range is not exhaustive. A CLEAR finding means "well beyond every genuine watch measured so far", not proof.
- **Overview badges (owner decision):** a marker withheld for a photo-wide reason gets no badge. Example: round markers on a photo too small for a comparable genuine set. The reason is the photo, not the marker; the not-assessed line under the grid explains it. Grey dashes remain for marker-specific reasons (hand, glare, edge).
- **Genuine catalogue:** `.github/workflows/alpha99-genuine-interference.yml` runs the interference check and the Alpha99 classification. It prints withhold rates and status changes, and fails on any new finding.

## Alpha100: model specs (2026-10-08)

The app no longer has GMT geometry or GMT reference values in code.

- **Dial layout:** `android/app/src/main/assets/models/gmt_126710/model.json` holds:
  - the minute-track radii and the minutes the pose ignores;
  - every applied marker's hour, shape and size;
  - the date-window region;
  - where the seconds hand carries its lume dot.
- **Code that reads it:** the pose fitter, marker measurement, date window, interference check, findings, close-ups, overview and overlay outline all take a `ModelSpec`.
- **Genuine reference:** `export_model_reference.py --model gmt_126710` copies the research outputs byte for byte into `assets/models/gmt_126710/reference/`, and the app loads them as a `ModelReference`. This replaces the generated `Alpha98Reference`, `Alpha99Uncertainty` and the generated block of `Alpha97TwelveReadout`. `gen_alpha97/98/99_constants.py` were removed; the sections above that mention them describe the earlier builds.
- **How to add a model:** `docs/ADDING_A_MODEL.md`.

**Nothing changed numerically.**
- The Alpha96 runner gives 0 differences: 1,592 values on the 8 local photos and 1,791 on the 9 owner photos.
- The Alpha99 classifications (`results/alpha99/findings_local.csv`), interference results and preview text are identical.
- All 18 rendered results screens and the overlay outline bitmap are pixel-identical to Alpha99 (7f2268f).

**Tests.** `ModelSpecTest` pins the GMT spec to the frozen `Alpha92GmtMaster` / pose / date-window constants and checks that the app's reference copies are byte-identical to the research files. It also runs a second, test-only layout (batons at 3, 6 and 9, no date) through the evidence layer with no code change: with no genuine reference, every feature reads "not assessed".

### 12 side angle on its own: at most worth a look (owner decision, 2026-10-08)

**Rule:** when only the side angle of the 12 is outside the genuine range, and the centreline and lateral position are not, the 12 is WORTH A LOOK at most. The detail view explains why. A side angle together with an outside centreline or position can still be CLEAR. Test: `Alpha99FindingsTest.twelveSideAngleOnItsOwnIsAtMostWorthALook`. Local photos: no classification changes; the local 12 CLEAR findings (Local BLNR, ARF) are lateral.

**Correction: the photo that prompted the rule is not side-only.** It was proposed after the Alpha100 genuine-catalogue run (37731661635), which flagged a Swiss Watch Expo 126710BLNR studio photo, `ce997f77df67b365`, as a CLEAR finding. That run's log showed only the strongest measure, the right side at 4.1°. Re-evaluated from its recorded measurements (`ci_run_37500197377/per_photo.csv`, dial radius 172 px):

| Measure | Reading | Genuine max | Status |
|---|---|---|---|
| Right side | 4.1° CCW | 1.0° | outside |
| Left side | 0.42° off | — | within |
| Centreline | 2.5° CCW | 1.0° | outside; excess 1.5° vs a 3-sigma allowance of about 1.3° |
| Lateral | 0.25% | 0.23% | just outside |

So the rule does not apply, and the photo is still a CLEAR finding. Its two sides disagree (left about 0.42° off, right 4.1°), so the centreline, which is computed from the same edges, is moved by the one side edge. The run that checked the rule (37733632259) did not fetch this photo.

## Alpha101: index alignment round (2026-10-08)

### 1. Edge consistency of the 12 triangle

A real rotation turns both of the triangle's sides together; lighting or blur on one edge moves one side alone.

- **Measure:** |left side − right side|, both relative to the genuine nominal.
- **Genuine data** (`ci_run_37500197377/per_photo.csv`, all 131 usable genuine photos):
  - median 0.14°, 90% within 0.48°;
  - the large values are single bad photos. Watch `hv_page_6d3d310e79` reads 6.0° on its 103 px photo but 0.00–0.18° on its 7 other photos; the SWE watch `swe_59259` reads 3.7° on `ce997f77df67b365` but 0.23–0.25° on its 2 others.
- **Limit** (written by `measurement_uncertainty.py`, `twelve_sides_agreement.limit` = 0.99°): the largest per-watch median across the 25 watches with 2+ photos (0.77°), plus 3× the robust photo-to-photo spread (0.074°).
  - Single-photo watches are excluded from the range: their "median" is the photo itself.
  - The spread is robust (MAD), because the photos this targets would inflate a plain SD.
- **Rule:** when the two sides disagree by more than the limit, the 12's centreline and side readings are at most WORTH A LOOK; the detail view gives the reason. Lateral position is unaffected.
- **Effect:**
  - 6 of 131 genuine photos exceed the limit, all of them tiny, studio-lit or blurred; this includes `ce997f77df67b365`, which is now WORTH A LOOK instead of CLEAR.
  - The local defect cases read 0.05–0.41° (Local BLNR, ARF, Theonewatches), so they are unaffected.
  - Local classifications are unchanged.
- **Test:** `Alpha99FindingsTest.twelveWithDisagreeingSidesIsEdgeAffected`, which uses the studio photo's recorded values.

### 2. Findings worded the way QC posts are written

Short lines follow the RepTimeQC guide's "be specific and directional" style, e.g.:
- "rotated 1.6° anticlockwise; genuine up to 0.6°";
- "shifted towards the centre and the 1 by 0.44% of the dial";
- "shifted left by 0.85% of the dial";
- "markers as a set turned 0.52° clockwise".

A marker shift is named by its dominant direction: towards its minute mark, towards the centre, or towards the neighbouring hour. Both directions are named when neither dominates. No status changed (`results/alpha99/findings_local.csv`).

Real size in mm is deliberately not shown yet. It needs a sourced dial diameter per model; one route is the bezel's outer diameter, the published 40 mm case, measured in dial-radius units on genuine photos.

### 5. Round lume-plot size

Each round marker's fitted outer radius was already measured (Alpha94 `radius_err_px`). There are two features, built like the others in `build_alpha98_reference.py` and `measurement_uncertainty.py`, from genuine photos only:

- **`rounds_size`:** the median size of a dial's round plots (signed; the genuine nominal is +0.09% R). It answers "all plots oversized or undersized".
- **`round_size_rel`:** the largest single plot's size difference from its own dial's median (magnitude). It answers "one plot larger or smaller than the others". Lighting and blur cancel because they affect every plot equally.

Blur widens the measured outer edge. So, like round-marker positions, both are compared only with genuine watches photographed at a similar or lower resolution (8 or more needed), and a photo below that is not assessed. In the app, only rounds clear of hands count; fewer than 5 clean rounds means not assessed.

**Genuine reference:**
- `rounds_size`: 48 watches, max distance from nominal 0.10% R; uncertainty 0.09% R / 0.14 px.
- `round_size_rel`: 48 watches, max 0.16% R; uncertainty 0.08% R / 0.13 px.
- Existing reference rows are byte-identical.

**Local photos:**
- ARF: all round plots larger by 0.20% (WORTH A LOOK).
- Local BLNR: +0.28% offline, but not assessed in the app (only 4 rounds are clean of hands).
- Marketplace genuine-candidate POOL_GEN_HO_01: its 10 o'clock plot is 0.12% larger than the others (genuine up to 0.10%), WORTH A LOOK.
- Theonewatches and the CPO photos (dial radius about 170 px or less): not assessed, resolution too low.

**Tooling:** these checks have no Alpha98 counterpart, so the Alpha98 regression checks skip them (`Alpha99Findings.outsideOnlyByNewMeasures`, `alpha101_only` column). `alpha99_genuine_summary.py` lists them separately.
