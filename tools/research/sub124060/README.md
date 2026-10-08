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
