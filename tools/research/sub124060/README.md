# Submariner 124060 (no date): genuine-first calibration (Alpha102)

The 124060 is the first Submariner reference added through the GMT's proven method (`docs/QC-GUARDRAILS.md` sections 6-8). Everything here is genuine-only; the replica seeds are kept apart for validation and never set anything.

## 1. Genuine catalogue (`catalogue_124060.csv`)

Reused from the earlier watch-family calibrator run 37194686346 (its `acquired_images.csv`, read by the `sub-genuine-manifest` job). No new acquisition was needed.

- **Genuine:** 41 physical watches, 318 photos.
- **Sources:** Bob's Watches 12 watches, European Watch Company 8, DavidSW 7, SwissWatchExpo 6, Watches of Switzerland CPO 6, Phillips 2. The largest source has 29%.
- **Replica seeds:** 6 watches. These are Reddit/Imgur albums that were never acquired as images, so they are not used (see section 6).

Each row has the dealer image URL and sha256. CI refetches with `alpha96_calibration/fetch_verified.py` and keeps only byte-identical files. Images are never committed.

## 2. Dial master (`models/submariner_124060/model.json`)

Measured with the model-generic `SubMaster` driver, which uses the app's frozen pose and marker measurement. First a provisional spec was used: the GMT markers, plus the 3 o'clock baton, with no date window. CI run 37767221075 gave 42 dial photos of 25 genuine watches. Values are the median over watches of each watch's median.

| Feature | 124060 genuine | GMT 126710 master |
|---|---|---|
| Minute track (tick extent) | 0.933-0.981 | 0.9325-0.9803 |
| Round centres | 0.8136 | 0.8128 |
| Round size | same as GMT (-0.0002 R) | 0.0915 |
| Batons 3 / 6 / 9 | 0.7591 | 0.7551 |
| 12 triangle centroid | 0.7989 | 0.7992 |

On the GMT photos, `SubMaster` reproduces the GMT master: ticks 0.933-0.981, rounds 0.811-0.814, batons 0.756. `Alpha96Calib` now writes every baton in the spec. Its GMT output is byte-identical: 0 differences over 3,383 values.

## 3. Genuine reference and uncertainty (`reference_src/`)

The runner job (`sub124060-genuine-runner.yml`) applies the frozen master to every verified photo. In this run 290 of the 318 genuine photos verified, and 72 of them are dial photos.

**Shared photos:** a photo is shared when every marker offset matches a photo of a different watch to within 0.0005 R. That flags 2 reused Bob's images (0.0002 and 0.0003 R apart; the next-closest pair is 0.001 R apart). A whole-image perceptual hash was tried first. It flagged half of Bob's photos, because Bob's photographs every watch in the same studio framing, so it was not used.

**Triangle nominal:** `alpha96_calibration/calibrate_m12_nominal.py` on these files, with 24 watches. The leave-one-watch-out spread is close to the in-sample spread (median 0.0012 vs 0.0011 R).

**Markers and uncertainty:** `build_sub_reference.py`, which uses the GMT definitions but takes the batons from the spec. Its per-photo features are identical to the GMT script's on all 221 GMT photos.
- 23-24 watches per feature.
- Single-photo uncertainty from 16-17 watches with repeat photos, K = 3.
- 12 side-agreement limit 1.73 deg (GMT 0.99). One genuine watch's sides disagree by 1.57 deg consistently. That makes the safeguard less protective, not more aggressive.

## 4. Held-out genuine (§8 step 5)

**Leave-one-watch-out** (`heldout_genuine.py`, raw measurement, dealer photos): 0 clear on every feature. Worth a look on 1-5 of about 44 photos per feature.

**External SwissWatchExpo photos** (never in the reference):
- On the raw measurement, the 6 baton read clear on all 3 watches (about 2 deg, 0.7-1.2% R). Log-only close-ups show the seconds hand and its lume dot (r about 0.51) lying across the 6.
- In the app's full pipeline (`sub124060-genuine-check.yml`), the seconds-hand line check withholds the 6 on 22 of 24 of those photos. That is the guardrails §3 path working.

**App pipeline on all 72 genuine dial photos, in-sample:** 30 worth a look and 1 clear. The clear one is SWE `531bbfa97d5559b8`, where the 1 and 11 o'clock plots read 0.28% smaller than the others.
- That is one watch photographed at four sizes. The two large versions cannot measure those plots cleanly, so this is studio lighting at the top of the dial.
- Under guardrails §11 the round-plot size checks (`rounds_size`, `round_size_rel`) have their uncertainty allowance withheld for the 124060 (`--no-allowance`). They can be at most worth a look.

**After the downgrade** (CI run 37771849905, `results/check/`): 78 genuine dial photos, 308 photos verified. Results across 1,092 feature readings:
- 0 clear;
- 34 worth a look: 9 o'clock 11, 3 o'clock 8, ring 5, 12 o'clock 2, single round plots 5, round plot size 2, 6 o'clock 1;
- 391 not assessed, mostly hands at 10:10 and the resolution-matching rule.

## 5. In the app (testing build)

`android/app/src/main/assets/models/submariner_124060/` holds the spec and the reference exported from `reference_src/`. `ModelSpecTest` keeps the app copies byte-identical to the research files. The start screen offers it next to the GMT.

GMT regression: findings, hand check and preview text are identical, and all 18 results screens are pixel-identical to main.

APK: `1.4.0-alpha102-submariner-arm64`, build run 37771849644. The build's unit tests pass.

## 6. Not done yet

- **Usefulness on replicas / QC photos (§8 step 6).** The replica seeds are Reddit/Imgur albums, which we agreed not to fetch. Owner-supplied Submariner QC photos are needed. Until then the 124060 is a testing build, not promoted to main.
- **Other Submariner references** (126610LN/LV date, two-tone, older 40 mm). Each needs its own genuine catalogue; date models also need the date window. Until then they are not in the app.

## 7. Small photos (2026-10-08, after owner testing)

### Batons on a small Rolex catalogue render (dial radius 131 px)

The render read 3 and 9 as "worth a look", both apparently shifted down. The batons sat where the master says (0.01 and 0.08 px). The marker ring fit had moved 0.36 px, and at 131 px that is 0.3-0.4% of the dial. Baton position was compared with genuine photos that were all sharper. Opposite batons do not move together on genuine dealer photos (correlation 0.13), so this was a resolution effect, not the watch.

**Fix:** `model.json` now has `resolution_matched: [three_off, six_off, nine_off]`. With fewer than 8 genuine watches photographed at R_ref <= 1.3 R, the baton position is not assessed; rotation still is. The summary says "3 rotation" (within) and "3 position (resolution too low)". Matched counts are now distinct physical watches. The GMT is unchanged: findings and preview text identical, screens pixel-identical to main.

### Round plots on small photos

Round plots were "not assessed" below about 181 px dial radius. Fewer than 8 genuine reference watches were photographed that small (GMT: about 175 px).

The `sub124060-lowres` job shrank every genuine dial photo to dial radii of 130, 150 and 170 px and re-measured it.

| Shrunk to | Batons | Round plots |
|---|---|---|
| 170 px | outliers already up to 0.70% | close to full resolution: position max 0.31% vs 0.29%, size spread 0.13% vs 0.14% |
| 150 and 130 px | outliers | single photos read 0.5-1.0% off |

Using all the shrunk rows would have widened the genuine limit for sharp photos too (round position 0.29% to 1.0%). Guardrails §11 rules that out.

**What is used:** only the 170 px rows, only for round plots and the ring. Each row carries `max_photo_r` (181 px, the full-resolution coverage), so it applies only to photos the full-resolution reference cannot cover. The app honours `max_photo_r`; the GMT has no such rows.

**Held-out** (`heldout_lowres.py`, other watches only, with the app's hand/glare withholding):
- Full-resolution photos: exactly the baseline. The limits did not change.
- Real genuine photos under 181 px: round plots and ring assessed on 12 of 13, 0 clear, 1 worth a look per round feature and 3 for the ring. See `reference_src/heldout_lowres.md`.

**New coverage:** round plots are assessed down to about 131 px dial radius (about 262 px across). The 131 px render is just below that and stays "resolution too low".

**Genuine catalogue after both changes** (CI run 37785443660; 72 dial photos this run, 290 verified):
- 0 clear;
- 31 worth a look;
- 296 not assessed, down from 391;
- 87 readings went from not assessed to within range, and 2 to worth a look.

APK: build run 37785443638.

## 8. Seconds-hand check and the dial print (owner report: "no hand crossed the 5 or 7")

The seconds-hand check finds the hand by its lume dot: a bright filled disc at r 0.50-0.60 (the GMT band), at >= 0.45 x the photo's lume contrast. Every marker on the line from the centre through that dot is withheld.

**What went wrong on the 124060:** the start ("S") and end ("R") of the printed line SUPERLATIVE CHRONOMETER sit at r 0.52-0.54, at 143-145 deg and 214-216 deg. On 33 genuine photos (CI 37795400723, `results/check/interference_log_before_print_spots.txt`) they passed as dots, and their lines withheld the 5 and/or 7.

| Candidate | Score (x lume contrast) |
|---|---|
| Print, recurring on 3-12 different genuine watches at the same spot | 0.45-0.70 |
| Real lume dots at those spots | 0.82-0.99 |
| Real lume dot elsewhere: SWE studio photos, hand over the 6 | as low as 0.47 |

A global threshold would therefore lose real hands.

**Fix:** the spec lists the four print spots (`seconds_hand.print_spots`). A candidate within 2 deg and 0.012 R of one counts as the seconds hand only at >= 0.75 x lume contrast, which lies between the two genuine ranges. Elsewhere the GMT rule applies unchanged.

**Results:**
- On the owner's photo the 5 and 7 are no longer withheld. The 1 (minute hand) and the 6 (seconds hand) still are.
- The GMT has no print spots: findings, hand check and preview text are identical.

## 9. The 12's genuine limit (owner report: "the 12 is clearly rotated but not reported")

The owner's photo (dial radius 234 px) read the 12 at -1.35 deg (centreline), with both sides agreeing (-1.36 / -1.37 deg), so the marker really is turned. It reported "within" because the 124060 centreline limit was 1.75 deg.

**Where 1.75 came from:** genuine DavidSW watch `47ed7fb4d547`, the median of two photos.
- The 256 px photo reads -0.66 deg, with the sides 0.33 deg apart.
- The 154 px photo reads -2.83 deg, with the sides 3.46 deg apart. That is beyond the app's own edge-consistency limit: a reading the app treats as lighting or blur, at most worth a look.

**Fix:** `edge_filter.py` removes the 12 reading of genuine photos that fail the edge-consistency test (§1: a documented measurement-quality reason; one photo). Every other marker and photo is untouched. Then `calibrate_m12_nominal.py` and `build_sub_reference.py` are rerun.

| | Before | After |
|---|---|---|
| Centreline genuine max | 1.75 deg | 1.25 deg |
| Centreline photo-to-photo sigma | 0.36 deg | 0.12 deg |
| Edge-consistency limit | 1.73 deg | 0.83 deg (GMT 0.99) |

The marker reference is unchanged. The owner's 12 now reads 1.35 vs 1.25 deg with a 0.46 deg allowance, so it is worth a look.

**The remaining 1.25 deg:** it comes from genuine EWC watch `ceb5b8d52802` (-1.21 / -0.92 deg, sides agreeing), so it is valid and kept. But in both its photos every marker reads anticlockwise: 3 at -0.5/-0.6, 6 at -0.4/-0.3, 9 at -0.6/-0.3 deg. That is a shared offset of about -0.5 deg, so this 12 is only about -0.5 deg off relative to its own dial.

**Proposed next feature (not implemented):** judge the 12's rotation relative to the dial's common marker rotation. It must be validated on genuine GMT and 124060 data first.

## 10. Wording: "hand crosses marker" when no hand was detected (owner report on the 9)

A marker that passed the hand / glare check, but failed the measurement's own outline test, was labelled "hand crosses marker". The underlying reason is "hand/occluder crosses outline (clean x)".

That test sometimes catches real hands the hand check misses, e.g. GMT `RL_LOCAL_BLNR` 8 o'clock (the GMT hand's arrow tip covers it). Other times it is glare, a reflection or the photo, e.g. the owner's 124060 9 o'clock.

Following the owner's rule (name a hand only when the hand check detects one), these now read **"outline not clear"**: "part of the marker's outline could not be measured cleanly (see the close-up)". The close-up stays, so the cause is visible.

Status changes: none, on either model. GMT `results/alpha99/findings_local.csv` and `preview_text.txt` are updated for that one tile's wording.

**Genuine catalogue after sections 8-10** (CI run 37798406503, 79 genuine dial photos):
- 0 clear;
- 38 worth a look;
- 269 not assessed. The 5 / 7 round plots are now withheld on 16 / 20 photos, down from 46 / 39 of 72, after the print-spot fix.

**Owner's QC photo (necoclock, dial radius 234 px):**
- 12 worth a look: 1.4 deg anticlockwise, genuine up to 1.3.
- 6 worth a look.
- Round plot size worth a look: 0.26% larger, genuine up to 0.12%.
- 9 "outline not clear": outline integrity 0.60 vs the 0.80 limit (3 and 6: 1.00). The close-up shows no hand; the polished surround of the 9 reflects brightly along one edge, so the outline test's outside samples land on bright metal.

That is a correct fail-closed (reflection), now worded neutrally. Making the outline test tolerate reflective surrounds would change the shared measurement (GMT too) and is not done here.

## 11. Wrong model selected (owner report "no hand crosses 5 / 7", 16:27)

The screenshot was a GMT-model run. It reported "date window (window not found)", the 12's "genuine up to 1.0 deg", and no 3 o'clock tile, and it reproduces exactly with the GMT spec.

On the GMT, the Submariner's printed "S" / "R" of SUPERLATIVE CHRONOMETER are taken for the seconds hand, because the GMT has no print there. That is why the 124060 has its own print spots.

**Cause:** the picker went back to GMT on every app start, and the results screen did not say which model was used.

**Fix (screen-only, no measurement change):**
- The chosen model is remembered.
- The results screen shows "Checked as: <model>".
- A GMT run whose date window was not found at all ("window edges not found" / "no plausible window") suggests the no-date Submariner. On genuine GMT catalogue photos that happens for about 5% (10 of 213); it is a suggestion only.

## 12. Experiment: let a bright bevel pass the outline test (tried, reverted)

The owner's QC photo's 9 fails the outline test because its polished surround reflects just outside the lower lume edge: 2.5 px outside reads 56-165 grey instead of dark dial.

**Tried:** "outside" = the darkest of 2.5 px, +0.008 R and +0.016 R beyond the edge, so a narrow bevel passes.

**GMT result:**
- The real hand over RL_LOCAL_BLNR's 8 is still caught (0.67).
- 0 finding changes on the local photos.
- One marker becomes withheld (Theonewatches 2, 0.78).

**Owner's photo:** the 9 became measurable and read a clear finding, shifted 0.59% towards the 10. The 1, 7 and ring moved to worth a look.

**Independent check:** a brightness profile across the rectified 9 and 3 puts the 9 about 0.8 px higher than the 3. The fit puts it 2.3 px higher. Below its lume the 9 has a long bright bevel tail that the 3 does not, so the fitted edge is pulled by the reflection. The finding would come largely from the bevel.

**Reverted.** The original test was right to withhold this 9.

**Possible later work:** measure a baton from its clean edges when exactly one edge is bevel-contaminated, validated on genuine photos first.

## 13. Held-out script now applies the spec's resolution rule (2026-10-08)

`heldout_genuine.py` matched resolution only for the built-in features (rounds, ring). It ignored the spec's
`resolution_matched` list, so the baton positions (`three_off`, `six_off`, `nine_off`) were held out against every
genuine watch, not only those photographed at R_ref <= 1.3 R as the app does. Fixed (built-in + spec list, as
`heldout_lowres.py` and the app). Rerun on the current reference input (`results/runner/per_photo_edge.csv`):
`reference_src/heldout_genuine.md`.

- Leave-one-watch-out: still 0 clear on every feature. Baton positions now "not assessed" on 6-7 small photos each;
  worth a look 3 o'clock 4 -> 3, 6 o'clock 2 -> 0, 9 o'clock 5 -> 4.
- External SWE photos: the raw 6 baton clears (seconds hand across the 6, §4) drop from 11 to 8; in the app the
  hand check withholds them. The round-size clear is gone (the app caps round size at worth a look anyway).

## 14. Owner-supplied genuine packs: 31 EWC + 37 Bob's watches (2026-10-08)

Two owner-supplied packs of certified-genuine dealer photos (image URL + sha256 per photo; all hashes verified):
European Watch Company (48 photos, 31 stock numbers) and Bob's Watches (48 photos, 48 SKUs; 11 already catalogued,
37 new). Both READMEs ask that they not be promoted silently; the owner approved after each was held out first.

**Held out first (current app reference, 24 watches):** EWC 0 clear / 15 worth a look on 672 readings; Bob's (37 new)
0 clear / 10 worth a look on 518. Worth-a-look results clustered on the 6's rotation (0.6-0.8 deg vs genuine 0.55), as
in the owner's genuine photo (0.58 deg).

**Dedup (`dedup_measured.py`, now a script):** EWC listings 59112, 59921 and 62175 agree within 0.0003-0.0006 R,
closer than any two other genuine watches (two photos of ONE watch typically differ by 0.0012 R): counted as one
physical watch. No other new duplicates.

**Allowances:** the packs are quality-selected (EWC: dial radius >= 180 px, clean tick fit; Bob's: manually reviewed
face-on), so their repeat photos understate photo error on ordinary photos. With EWC alone the shrunk allowances made an
external SWE genuine photo's 3 o'clock clear. Rule (template): new genuine watches may widen genuine ranges, never shrink
the photo-to-photo allowances: `edge_safe_uncertainty.py --all-families` keeps the larger of the new and previous value
per family (smaller side-agreement limit).

**Result (90 watches; 12 triangle 89):**

| | before | after |
|---|---|---|
| three_rot genuine max | 0.70 | 0.85 |
| six_rot genuine max | 0.55 | 0.86 |
| nine_rot genuine max | 0.78 | 0.83 |
| leave-one-watch-out | 0 clear | 0 clear (27 worth a look) |
| external SWE | 6 o'clock clears (seconds hand, withheld by the app) | the same; the SWE 3 o'clock photo is not clear |

Owner photos: the 6 on the necoclock QC photo, the 16:32 photo and the genuine photo of the 6 question now read within
range; no new clear. Rebuild order: `calibrate_m12_nominal.py` + `build_sub_reference.py` on `per_photo.csv`
(unfiltered) -> `edge_filter.py` -> both again on `per_photo_edge.csv` -> `edge_safe_uncertainty.py` (12) ->
`edge_safe_uncertainty.py --all-families` against the previous allowance file -> held-out scripts -> export.

## 15. Six more genuine watches from the Bob's Rolex Harvester (2026-10-08)

The owner's first run of the repaired harvester (1.4) on a phone exported 30 face-on-filtered 124060 photos with
traceable SKU, listing URL and sha256. Checked: 30 distinct files from 25 listings; 6 were off-axis (3/4 views, wrist
shots: harvester 1.4.1 now rejects them); of the 24 face-on photos, 18 are watches already in this catalogue (measured
dial match <= 0.0005 R; 16 identical files). The 6 new watches (SKUs 188829, 188842, 188960, 189216, 189892, 194255)
were held out first against the 90-watch reference: all within on every assessed feature (0 clear, 0 worth a look).

Rebuilt in the section-14 order: 96 watches (12 triangle 95). Genuine maxima move by at most 0.004 deg; allowances
unchanged (never shrink); leave-one-watch-out 0 clear (27 worth a look); low-resolution held-out 0 clear; no status
change on any owner photo.
