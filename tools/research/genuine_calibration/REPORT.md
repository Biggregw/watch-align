# Genuine calibration of the shared MarkerGeometry measurement (research, 22 October 2026)

**Research only. Nothing here is deployed.** No production measurement, tolerance, reference geometry, classification rule
or the approved triangle top-edge calibration was changed. The candidate limits below are for the owner's decision on
Phase C.

Full numbers: [`results/calibration-run-39841529058.md`](results/calibration-run-39841529058.md) (every table the
analysis prints). Code: this folder. Workflows: `.github/workflows/genuine-calibration.yml` (measurement) and
`genuine-calibration-analyse.yml` (analysis only).

## 1. Commit, data and method

| | |
|---|---|
| Measurement run | [39833195097](https://github.com/Biggregw/watch-align/actions/runs/39833195097), commit `cce5787` |
| Analysis run | [39841529058](https://github.com/Biggregw/watch-align/actions/runs/39841529058), commit `6601e48` |
| Replica records | measurement-architecture run [39827340472](https://github.com/Biggregw/watch-align/actions/runs/39827340472) (`25f8a3c`), same Java MarkerGeometry |
| Photographs | `tools/research/marker_audit/results/{nodate,date,gmt}-verified.csv` and `checkpoint/date-expanded-verified.csv`, refetched only on an exact SHA-256 match, plus the SHA-addressed originals of calibrator artifact 11300604104. No new harvest. Nothing uploaded or committed. |
| Admission | A fresh, job-local image archive (`tools.image_archive.Archive`) with the production face-on-v3 screener. Every photo gets its SHA-bound `marker-mask-v1` mask. |
| Restore | `restore_acquired(..., purpose='marker_masked')` writes `eligibility_masks.jsonl` beside `acquired_images.csv`. |
| Measurement | The watch calibrator's own masked path (`production_measure.measure`) with adapter `marker_geometry_v1` (`GeometryMeasure`). Each photo's bytes are checked against its mask's SHA. A ring value is kept only if every marker its fit used is ELIGIBLE. A missing mask stops the run. |
| Research driver | `ArchMeasure` on the same restored originals: production readings, top edge, and image-level synthetic defects (new grid mode 2). |

**Config check (owner's question).** `calibration/models/124060.json` declares no `marker_eligibility`, so the
calibrator would run it **unmasked**. This run therefore uses research configs that declare `marker-mask-v1`
(`configs.py`; 32 metrics for No-Date, 29 for Date and GMT). Without restored masks `measure.py` refuses to run (tested).
The masks were genuinely applied:
- 4,093 GeometryMeasure values were compared with the research driver: largest difference 5.0e-7, which is the CSV's six-decimal rounding.
- The MarkerGeometry eligibility agreed with the archive mask on all 2,022 markers (1,762 eligible, 260 not).

**Phase B parity, incorporated.**
- Measurement review on `25f8a3c`: the parity step passed on every CI record (tolerance 1e-6).
- This run: 2,335 ArchMeasure records and 75,217 values, Java against Python. Largest difference 1.0e-8 % R. No eligibility mismatch, nothing present on one side only.

**Statistical unit: the physical watch.** Every observation keeps its watch, photo, source, family and clock position.
A watch carries total weight 1 in every statistic, however many photos and markers it has. Intervals are 90 % bootstraps
over watches. Out-of-sample checks use 5-fold cross-validation over watches and the calibrator's locked holdout
(29 watches, stratified by source).

## 2. Eligible photographs and independent watches

| Family | Items | Original bytes | Admitted photos | Independent watches | Sources (watches) |
|---|---:|---:|---:|---:|---|
| GMT-Master II 126710 | 66 | 66 | 24 | **22** | Bob's 21, Phillips 1 |
| Submariner 124060 | 439 | 439 | 64 | **56** | European Watch 27, Bob's 24, DavidSW 3, SwissWatchExpo 2 |
| Submariner Date 126610 | 355 | 110 | 90 | **90** | Bob's 90 |
| **Total** | 860 | 615 | **178** | **168** | 5 sources; largest share 80 % (Bob's) |

The admissions reproduce the archive's earlier masked-admission counts (FACE-ON-VALIDATION.md §10.5): 64 / 56 No-Date
exactly, 24 / 22 GMT (26 / 24 before), 90 / 90 Date (96 / 96 before; 245 Date originals no longer refetch with their
exact SHA).

**Withheld, with reasons.**
- **Photo level:** dial radius below 250 px (159), more than 2 required markers unusable (104 fail, 289 inconclusive), dial not registered or photo-level evidence inconclusive (gallery shots, about 271 No-Date), tilt (22), sharpness, glare and exposure. Photos can carry several reasons.
- **Marker level, in admitted photos:**
  - round markers: 1,193 eligible, 219 obstructed, 4 unreliable, 8 inconclusive;
  - batons: 402 eligible, 14 obstructed, 4 unreliable;
  - triangles: 167 eligible, 11 obstructed.
- **After measurement:** no value had to be blanked by the mask. GeometryMeasure had already withheld every ineligible marker itself.
- **Ring:**
  - every admitted photo had a ring fit with at least 8 markers (8: 56, 9: 79, 10: 37, 11: 6);
  - the obstructed 2 never removed a ring, because the ring uses only the markers that are eligible.

## 3. Shared calibration distributions

Deviations from the reference geometry:
- **Per marker:** the reading against the leave-one-out ring of the other markers. The signed median is reported and never subtracted.
- **Whole ring:** centred on the pooled genuine median (placement minus parallax).

Values are watch-weighted, in % R or degrees, with 90 % intervals.

| Group | Watches | Median | p95 | p99 | Max | Genuine scale σ |
|---|---:|---:|---|---|---:|---:|
| Round position (any direction) | 168 | 0.090 | 0.189 [0.182, 0.196] | 0.257 [0.232, 0.293] | 0.342 | 0.077 |
| Baton position | 168 | 0.115 | 0.216 [0.198, 0.225] | 0.262 [0.229, 0.296] | 0.313 | 0.098 |
| **Round + baton position (shared)** | 168 | 0.096 | 0.196 [0.190, 0.203] | 0.260 [0.244, 0.278] | 0.342 | 0.082 |
| Baton rotation (deg) | 168 | 0.212 | 0.624 [0.591, 0.676] | 0.780 [0.716, 0.784] | 0.810 | 0.315 |
| Triangle sideways | 157 | 0.052 | 0.167 [0.156, 0.192] | 0.218 [0.182, 0.350] | 0.350 | 0.077 |
| Triangle in/out | 157 | 0.069 | 0.196 [0.171, 0.221] | 0.257 [0.221, 0.418] | 0.418 | 0.102 |
| Triangle rotation (deg) | 157 | 0.318 | 0.990 [0.886, 1.049] | 1.095 [1.016, 1.167] | 1.167 | 0.471 |
| Ring placement | 168 | 0.100 | 0.241 [0.223, 0.268] | 0.345 [0.268, 0.445] | 0.445 | 0.085 |
| Ring rotation (deg) | 168 | 0.039 | 0.117 [0.106, 0.143] | 0.166 [0.143, 0.178] | 0.178 | 0.058 |
| Ring scale (%) | 168 | 0.045 | 0.185 [0.171, 0.251] | 0.289 [0.251, 0.298] | 0.298 | 0.067 |

Separate sideways and in/out tables for round markers and batons are in the results file. Round sideways p99 0.232 and
in/out 0.199; baton sideways 0.222 and in/out 0.200.

**Pooling across families is supported for positions:**

| Group | GMT p95 | No-Date p95 | Date p95 |
|---|---:|---:|---:|
| Round position | 0.193 | 0.183 | 0.194 |
| Shared marker position | 0.193 | 0.195 | 0.197 |
| Baton rotation (deg) | 0.499 | 0.599 | 0.634 |
| Triangle rotation (deg) | 0.657 | 0.990 | 0.991 |

The intervals overlap throughout.

**Round markers and batons can share one position limit.**
- Round p99 0.257 [0.232, 0.293] against baton 0.262 [0.229, 0.296]: overlapping intervals, the same measurement.
- The shared group keeps the same p95 across families (0.193–0.197).

**Not supported, one exception each:**
1. **Ring scale on the GMT.**
   - The GMT's signed median is −0.194 %, against about 0 for both Submariners.
   - Its p95 is 0.296, against 0.13 for both Submariners.
   - This is a constant offset in the GMT reference radius, not watch variation. It causes half of all whole-watch alarms at the 5 % target (below).
   - Correcting it is a reference-geometry change, which needs your approval.
2. **Clock position.**
   - Round sideways and in/out medians differ by hour (Kruskal-Wallis p < 1e-4). Examples: GMT 1 −0.072, Date 8 −0.065, Date 5 +0.048 % R.
   - These offsets are up to about 1σ and are per-model, per-hour reference offsets.
   - They do not change the spread at each hour much (p95 by hour 0.10–0.19), so a shared tolerance is still sound.
   - Correcting the reference per hour (Phase A estimated it) would tighten the spread, but it is a reference-geometry change.

**Photographic conditions.**
- **Resolution and tilt:** the shared position p95 is the same at a dial radius of 250–350 px and 350–500 px (0.193 and 0.197), and at tilts of 0–3°, 3–6° and 6–11° (0.196, 0.198, 0.178).
- **Ring placement:**
  - the 13 watches tilted 6–11° have p95 0.445 against 0.24 otherwise;
  - the triangle in/out of the same watches has p95 0.418;
  - that is the residual parallax of high-tilt photos.
- **Photo-to-photo repeatability cannot be measured here.** Only 10 watches have a second admitted photo, mostly re-encodings of the same image (within-watch SD 0.004 % R). The earlier resampling tests remain the evidence: 95th-percentile reading change 0.08–0.09 % R at a 250 px dial (measurement-architecture REPORT.md).

**Source and watch sensitivity of p99:**
- One watch moves any p99 by at most 0.05.
- Leaving out Bob's Watches moves round position p99 from 0.257 to 0.303 and triangle in/out from 0.257 to 0.418.
- Both rest on single watches at other dealers. The upper tails are thin.

## 4. Candidate limits, with uncertainty

The owner asked for a small consistent set, not 32 tolerances. Two ways to set them were compared on identical data:

- **Per-reading quantile (rejected).** Each group's limit is its genuine quantile at a common per-reading exceedance q,
  with q solved for the whole-watch target.
  - At the 5 % target this needs q = 0.3–0.6 % per reading, beyond what 168 watches can estimate: the limits sit at the sample maxima.
  - Out of sample the 5 % target became **11.6–13.7 %** (cross-validation) and 10–17 % on the holdout.
- **One multiplier on genuine scales (recommended).** Each group has a robust genuine scale σ (from its median, which
  168 watches estimate well). A photo's alarm statistic is its largest standardised deviation, |reading| / σ, over all
  its readings. One multiplier k is calibrated on that photo-level statistic, so the whole-watch rate is estimated
  directly at 5 % or 1 %. Limit = k × σ.

**Recommended set (`shared/maxz`): one multiplier on eight genuine scales.**

| Reading | Unit | σ | Limit at 5 % whole-watch (k 3.83) | Limit at 1 % whole-watch (k 4.56) | Frozen production strict genuine (for comparison) |
|---|---|---:|---|---|---|
| Round and baton position (any direction) | % R | 0.082 | **0.314** [0.293, 0.364] | **0.374** [0.334, 0.472] | round 0.671, baton 0.752 (+0.5 px) |
| Baton rotation | deg | 0.315 | **1.21** [1.04, 1.44] | **1.44** [1.20, 1.86] | 1.096 |
| Triangle sideways | % R | 0.077 | **0.294** [0.227, 0.352] | **0.350** [0.267, 0.444] | 0.437 (+0.5 px) |
| Triangle in/out | % R | 0.102 | 0.392 [0.325, 0.471] | 0.467 [0.373, 0.601] | **top edge 0.40 stays** (§8) |
| Triangle rotation | deg | 0.471 | **1.81** [1.54, 2.16] | **2.15** [1.76, 2.78] | 1.174 |
| Ring placement (minus parallax) | % R | 0.085 | **0.324** [0.282, 0.378] | **0.386** [0.340, 0.477] | none (new finding) |
| Ring rotation | deg | 0.058 | **0.222** [0.194, 0.265] | **0.264** [0.226, 0.342] | none |
| Ring scale | % | 0.067 | **0.256** [0.217, 0.298] | **0.304** [0.271, 0.413] | none |

The table gives seven readings plus the triangle in/out, which stays with the approved top edge. The ring readings are
centred on the pooled genuine medians (placement (−0.010, +0.007) % R, rotation +0.006°, scale −0.016 %). No resolution
allowance is added: every photo here has a dial radius of at least 250 px, and the spread does not grow towards 250 px.

**Tilt limits are conservative, not tight.**
- The model extrapolates a Gaussian tail. Genuine rotations stay well inside it: the largest of 168 watches is 0.81° for batons and 1.17° for the triangle, against limits of 1.21° and 1.81°.
- As a result a 1° baton turn is caught only 25 % of the time (§6).
- If you want tighter rotation limits (for example the empirical p99, 0.78°), they need more genuine evidence. With 168 watches that tail is not reliably estimable.

## 5. False alarms, per marker and whole watch

| Scheme / method | Target | Whole-watch in sample | 5-fold CV | Holdout (29 watches) | Per reading |
|---|---:|---:|---:|---:|---:|
| shared / one multiplier | 5 % | 4.8 % | **4.8 %** | 6.9 % | 0.32 % |
| shared / one multiplier | 1 % | 0.6 % | **2.4 %** | 0 % | 0.04 % |
| position / one multiplier | 5 % / 1 % | 4.8 / 0.6 % | 5.4 / 3.0 % | 6.9 / 0 % | 0.36 / 0.04 % |
| axis (separate sideways, in/out) / one multiplier | 5 % / 1 % | 4.8 / 0.6 % | 4.8 / 2.4 % | 6.9 / 0 % | 0.22 / 0.02 % |
| shared / per-reading quantile | 5 % / 1 % | 4.8 / 0.9 % | 11.6 / 6.8 % | 10.3 / 10.3 % | 0.31 / 0.05 % |
| per-reading p99 limits alone (axis) | — | 18.3 % | | | 1 % |
| **Production strict genuine (frozen)**, same photos and markers | — | **0.6 %** | | | 0.06 % |
| **Production "Clearly off"**, same photos and markers | — | **0.6 %** | | | 0.06 % |

**Per marker is not per watch.**
- A photo has about 11–15 readings.
- A 1 % per-reading limit flags 12.6–18.3 % of genuine watches.
- The 5 % whole-watch target needs about 0.3 % per reading.

**The 1 % target is not demonstrated at 1 %.** Cross-validation gives 2.4–3.0 %, and the holdout's 0 of 29 is a wide
interval. Proving 1 % needs several hundred independent watches.

**Where the genuine alarms come from at 5 %:** ring scale 5 (mostly GMT, §3), position 3, ring placement 2, triangle
sideways 1.

## 6. Defect detection

Defects are moved inside the image on admitted genuine photos, one photo per watch: 22 GMT, 40 No-Date, 40 Date.
Measured response: reading change / applied move 0.996–1.014 (median), p10–p90 0.98–1.05.

Detection of the moved marker, `shared/maxz`, against the production app on the same photos and markers:

| Defect | 5 % limits | 1 % limits | Production strict genuine | Production Clearly off |
|---|---:|---:|---:|---:|
| Round 0.25 % R, in/out / sideways | 0.23 / 0.20 | 0.08 / 0.08 | 0 / 0 | 0 / 0 |
| **Round 0.5 % R**, in/out / sideways | **1.00 / 1.00** | **0.95 / 0.95** | 0.01 / 0.06 | 0 / 0 |
| Round 0.75 % R | 1.00 / 1.00 | 1.00 / 1.00 | 0.51 / 0.30 | 0 / 0 |
| Round 1.0 % R | 1.00 / 1.00 | 1.00 / 1.00 | 1.00 / 0.76 | 0.22 / 0.13 |
| **Baton 0.5 % R**, in/out / sideways | **1.00 / 0.93** | 0.97 / 0.89 | 0 / 0 | 0 / 0 |
| Baton 0.75 % R | 1.00 / 1.00 | 1.00 / 1.00 | 0.39 / 0.38 | 0 / 0.04 |
| Baton turned 1° / 2° / 3° | 0.25 / 1.00 / 1.00 | 0.07 / 0.99 / 1.00 | 0.32 / 1.00 / 1.00 | 0.02 / 0.85 / 1.00 |
| **Triangle 0.5 % R**, sideways / in/out | **1.00 / 1.00** | 1.00 / 0.70 | 0.40 / 0.50 | 0 / 0 |
| Triangle turned 1° / 2° / 3° | 0 / 0.62 / 1.00 | 0 / 0.38 / 0.97 | 0.36 / 0.94 / 1.00 | 0.28 / 0.94 / 1.00 |
| **Two markers moved 0.5 % R** (both found) | **187/199 (94 %)** | 164/199 (82 %) | | |
| **Three markers moved 0.5 % R** (all found) | **94/98 (96 %)** | 77/98 (79 %) | | |
| Ring moved 0.3 / 0.6 % R | 0.41 / 1.00 | 0.20 / 1.00 | — | — |
| Ring turned 0.4° | 1.00 | 0.98 | — | — |
| Ring scaled 0.5 % | 0.94 | 0.92 | — | — |

**No averaging.** Every marker is judged on its own reading against the leave-one-out ring of the others:
- One displaced marker moves the other markers' readings by a median 1.5 % (p95 11–13 %) of its displacement.
- Moving one marker created a false flag on another marker in 1 % of cases.
- With two or three markers moved this rose to 7–8 %, the cost of the ring being fitted to fewer clean markers.

**Whole-ring displacement stays a separate finding.**
- A moved, turned or scaled ring is measured as such: recovery error median 0.006, p95 0.019 % R.
- It created new per-marker flags in only 0–3 % of cases.

**What this means.**
- The production strict-genuine limits (0.67–0.75 % R) find almost none of the 0.5 % R displacements, while flagging 0.6 % of genuine watches.
- At 5 % whole-watch false alarms, the recommended limits find 93–100 % of single 0.5 % R displacements; at 1 % (2.4 % cross-validated), 89–97 %.
- Rotation is the exception: for 1–2° turns the production limits do as well or better, because the rotation limits here are conservative (§4).

**Replicas.**
- Only 18 exact-SHA replica photographs are available (6 replica watches, all 124060), judged as the app judges one photo: dial radius at least 200 px, eligible markers only.
- `clean_1lnn2kz`: the 6 and 9 are flagged in all three photos (two of them independent), and also the 12 in two. The production strict-genuine check flags none of its photos.
- `clean_15spe47`: one photo (tilt 6.9°) flags five markers and the ring. Its other photo shows nothing, so this may be photographic.
- `vsf_1gwpcm4`: 2 of 6 photos flag different markers (3, 4, 10 / 12), with no repetition. It looks photographic, not physical.
- Most other replica photos have only 2–6 eligible markers because of the 10:10 hands.

**No owner-reviewed real defects exist in the repository.** Detection of real manufacturing defects is therefore
unvalidated.

**Genuine variation, photographic uncertainty and defects, separated as far as the evidence allows:**
- genuine variation between watches: position σ 0.08 % R;
- photographic uncertainty: resampling to a 250 px dial changes readings by p95 0.08–0.09 % R; tilts above 6° inflate ring placement and triangle in/out;
- defects: synthetic displacements are recovered 1:1.

A 0.5 % R defect is about 6σ above genuine variation. A 0.25 % R defect (about 3σ) is not reliably separable from
genuine variation with a single photo.

## 7. Source-diversity limitations

- **Date:** all 90 watches come from Bob's Watches.
- **GMT:** 21 of 22 from Bob's.
- **No-Date:** the only balanced family (European Watch 27, Bob's 24, others 5).
- **Overall:** Bob's supplies 80 % of watches, and a single dealer's photography (lighting, lens, pose) dominates.
- Leaving Bob's out raises the round-position p99 from 0.257 to 0.303.
- The holdout has only 29 watches.

## 8. Triangle: the approved top edge, unchanged

On 167 photos (157 watches) where both are available:

| | MarkerGeometry in/out | Approved top edge (calibrated) |
|---|---:|---:|
| Genuine p99 (watch-weighted, absolute) | 0.257 % R | 0.343 % R |
| Response to a synthetic radial move (median gain, n 54) | 1.008 | 0.997 |
| Genuine photos over its limit | — | 0.6 % (limit 0.40 + 0.5 px) |

- **Agreement:** Spearman 0.82. Median difference (MarkerGeometry minus top edge) +0.016 % R, robust SD of the difference 0.055 % R.
- **Verdict:** the two are compatible. They measure the same in/out displacement with no material offset, and both track a known move 1:1.
- **Difference 1:** MarkerGeometry reads tighter on genuine watches (p99 0.257 against 0.343).
- **Difference 2:** for a 0.5 % R move the top edge, at its approved 0.40 limit, detects 50 %, against 70–100 % here.
- **Unresolved:** the No-Date's single extreme in/out watch (0.418 on MarkerGeometry) and the high-tilt photos (p95 0.418 above 6° tilt).

Nothing was replaced or combined. The top-edge method stays the triangle's in/out measurement, and any change needs your
approval.

## 9. Comparison with the production app

| Reading | This calibration (5 % / 1 %) | Production strict genuine (frozen) | Production replica QC (adjustable) |
|---|---|---|---|
| Round / baton position | 0.314 / 0.374 % R | 0.671 / 0.752 % R + 0.5 px | 1.00 % R + 0.5 px |
| Baton rotation | 1.21 / 1.44° | 1.096° | 1.75° |
| Triangle sideways | 0.294 / 0.350 % R | 0.437 % R + 0.5 px | 1.00 % R + 0.5 px |
| Triangle in/out | top edge unchanged | 0.40 (top edge) / 0.373 (fallback) % R + 0.5 px | — |
| Triangle rotation | 1.81 / 2.15° | 1.174° | — |
| Ring placement / rotation / scale | 0.324 % R / 0.222° / 0.256 % | none | none |

The production readings are not the same quantities. Production measures each marker against the yellow reference;
MarkerGeometry measures it against the leave-one-out ring with parallax removed. Production's limits are wider because
its own readings are noisier on genuine watches (measurement-architecture REPORT.md). On identical photos, the shared
limits give 5 % whole-watch false alarms (production 0.6 %) and detect 0.5 % R displacements 93–100 % of the time
(production 0–6 %).

## 10. Missing evidence that prevents deployment

1. **Owner-reviewed real defects:** none in the repository. Real-defect detection is unvalidated.
2. **Replicas:** 18 photographs of 6 replica watches, No-Date only. No GMT or Date replica originals are available.
3. **Sources:** 80 % Bob's. Date and GMT are single-dealer.
4. **1 % whole-watch target:** not demonstrated (2.4–3.0 % cross-validated). The holdout has 29 watches.
5. **Photo-to-photo repeatability on real repeat photos:** not measurable here (10 repeat watches).
6. **Rotation tails:** the limits are conservative, and tighter ones need more genuine watches.
7. **Reference offsets:** GMT ring scale (−0.19 %) and per-hour round offsets (up to about 0.07 % R). Correcting them is a reference-geometry change.
8. **The app's 200–250 px dials:** the archive admitted only photos at 250 px or more. Below 250 px the earlier resampling evidence applies, and an allowance would need to be set.

## 11. Recommended next step towards Phase C (for your approval)

1. **Adopt the structure, not yet the numbers:**
   - one multiplier k on eight pooled genuine scales;
   - shared round/baton position, baton rotation, triangle sideways and rotation, ring placement, rotation and scale;
   - the triangle in/out staying on the approved top edge.

   That is 7 new limits plus the existing top edge, in place of 32, the same for all three families.
2. **Close the evidence gaps first:**
   - an owner-reviewed set of real defect photos (even 10–20 watches across families);
   - genuine photos from more dealers for Date and GMT;
   - a decision on the two reference offsets (GMT ring scale; per-hour round offsets), which would remove most remaining false alarms.
3. **Then Phase C proper, behind an off-by-default flag:**
   - show the shared-measurement findings beside the existing results, without changing the classification;
   - regress against every verified genuine photo and the replica set;
   - only then propose a dated calibration baseline (old and new values) for your approval, choosing between the 5 % and 1 % operating points.

## Reproduce

```sh
# measurement (CI): .github/workflows/genuine-calibration.yml   analysis only: genuine-calibration-analyse.yml (GCAL_RUN)
python -m tools.research.genuine_calibration.analyse GCAL_DIR --replicas base_submariner_124060.jsonl items_submariner_124060.csv \
    --json calibration.json --md calibration.md
python -m pytest tools/research/genuine_calibration
```
