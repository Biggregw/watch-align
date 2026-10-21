# Face-on admission profile `face-on-v2`: validation (21 October 2026)

**Status:** the profile is in code and is the production screener of the research archive. Nothing has been uploaded or
migrated. No Microsoft account is connected. No production QC measurement, tolerance, reference or app code changed.
Several choices are for the owner to make; they are listed at the end.

Reproduce every number below from committed numbers only:

```sh
python -m tools.image_archive.analyse_validation docs/image-archive/face-on-evidence.jsonl
```

`face-on-evidence.jsonl` holds, for each photograph, its SHA-256, its reviewed label, and what the production analysis
measured on it. It contains no image bytes. Source run: CI `39651765901`
(`.github/workflows/image-archive-faceon-validation.yml`), plus a local run on the 12 committed public originals.
The counterexample run is CI `39657616336`.

## 1. Evidence used

**Reviewed labels.** These come from an offline review of the plain original photographs as contact sheets, without
overlays or measurements, made after the owner's angle correction (`tools/research/marker_audit/REPORT.md`, "Matched
genuine regression"). `near_frontal=no` marks obviously oblique product or handheld views. `eligible` adds the existing
400 px dial-diameter floor. Files: `results/{date,gmt,nodate}-suitability.csv` (328 photos) and
`results/date-additional-angle-review.csv` (228 photos).

**Bytes.** Every photo was refetched with the harvester's HTTP client. It was kept only if its SHA-256 equals the
catalogued hash (`fetch_verified.py`). That recovers the original itself, not a re-encoding.

| Reviewed set | Photos | Exact original recovered |
|---|---:|---:|
| GMT 126710 (`gmt-suitability`) | 66 | 66 |
| No-Date 124060 (`nodate-suitability`) | 135 | 135 |
| Date 126610 (`date-suitability`) | 127 | 111 |
| Date 126610 additional angle review | 228 | **1**: Bob's image CDN now serves different bytes for 227 |

So 313 reviewed photos could be analysed: 311 near-frontal and **only 2 oblique**. The other 39 oblique labels are in the
unrecoverable additional set.

**Historical originals.** These are the 373 SHA-addressed files of artifact `11300604104`
(`watch-calibrator-124060-37194686346`) plus the 12 committed public originals: 384 unique.

**Signals.** All come from the production pipeline (`ArchiveEvidence.java` runs `Alpha99Pipeline`, the same route as
TrackDump):
- the dial-edge ellipse;
- the minute-tick registration homography (tilt = acos of its minor/major local scale at the centre, plus the 12-vs-6
  and 3-vs-9 scale gradients);
- whether the hour markers confirm the 12 direction;
- every hour line placed from the minute track;
- the dial inside the frame;
- the harvester's dial pixel metrics;
- per marker, the clearly-off state, gate and outline coverage.

## 2. Pose: tilt degrades genuine measurements

Reviewed near-frontal genuine photos with a dial radius of at least 200 px. A marker is "outside" when it is beyond the
frozen strict genuine limits (`docs/CALIBRATION_BASELINE_2026-10-20.md` §1).

| Model | Tilt | Photos (watches) | Markers outside strict genuine | Photos with any |
|---|---|---:|---:|---:|
| GMT | 0–4° | 19 (19) | 0 / 207 | 0 |
| GMT | 4–8° | 17 (11) | 3 / 181 | 2 |
| GMT | 8–12° | 3 (1) | 0 / 29 | 0 |
| GMT | **12–20°** | **11 (3)** | **29 / 119** | **11** |
| Date | 0–4° | 45 (45) | 0 / 493 | 0 |
| Date | 4–8° | 67 (67) | 0 / 732 | 0 |
| No-Date | 0–4° | 51 (44) | 10 / 607 | 3 |
| No-Date | 4–8° | 49 (29) | 9 / 582 | 3 |
| No-Date | 8–12° | 12 (5) | 0 / 143 | 0 |

The rank correlation between tilt and a photo's median round-marker position error is 0.81 for GMT, 0.44 for No-Date
and 0.36 for Date.

- **Clean range.** The largest tilt with clean readings is **10.60°**. It is also just below the smallest tilt of a
  reviewed oblique photo, **10.61°** (GMT `66c4527d`).
- **Failure range.** From **13.29°** up, every reviewed genuine photo (11 photos, 3 Phillips watches) puts markers
  outside the strict genuine limits. A genuine watch would read as a replica.
- **The two reviewed oblique photos.** Both are rejected. Both are also below the resolution floor (dial radius 120 and
  161 px).

The reviewers' near-frontal label alone cannot set the cut-off, because it includes photos up to 17°.

## 3. The profile

A check passes only inside the envelope of the 263 reviewed near-frontal genuine photos (217 watches) with clean
readings (radius ≥ 200 px, tilt ≤ 10.60°). It fails where evidence shows harm or an existing documented bound applies.
Between the two it is **inconclusive**: that range was not tested, so the photo is withheld rather than guessed.

| Check | Pass | Fail | Source |
|---|---|---|---|
| Face-on: tilt and dial-edge ellipse | tilt ≤ 10.60° and edge ratio ≥ 0.981 | tilt ≥ 13.29° | §2 |
| Face-on: perspective gradient 12/6 | 0.9743–1.012 | — | envelope |
| Face-on: perspective gradient 3/9 | 0.9893–1.017 | — | envelope |
| Marker lattice / landmarks | the hour markers confirm the pose and all 12 hour lines are placed | otherwise | production pose check |
| Complete dial / minute track | whole dial edge in frame and 12 hour lines | cut off | geometry |
| Resolution | dial radius ≥ 200 px, short side ≥ 400 px | below | frozen app floor (`MIN_DIAL_RADIUS_PX`) |
| Sharpness | ≥ 1963 | < 25 | envelope / harvester bound |
| Exposure | crushed ≤ 0.750, clipped ≤ 0.100 | > 0.95 / > 0.25 | envelope / harvester bounds |
| Glare | ≤ 0.129 | > 0.30 | envelope / harvester bound |
| Reflections on markers | no marker withheld for glare | withheld | app glare gate |
| Every required marker (11 Date, 12 No-Date) | measured | withheld (hand, glare, Can't check) **or under half its outline seen** (`MetalFit.MIN_COVER`) | owner `credible()` rule + app coverage |

**Out-of-sample check.** Leaving each watch out in turn and rebuilding the envelope from the others:
- in-sample, 205 of 311 reviewed near-frontal photos pass, 100 fail and 6 are inconclusive;
- held out, 201 pass, 100 fail and 10 are inconclusive;
- no held-out photo turns into a fail.

## 4. Results

**Reviewed photographs.**

| Reviewed label | With bytes | Pass | Fail | Inconclusive |
|---|---:|---:|---:|---:|
| near-frontal and eligible | 273 | **204** | 63 | 6 |
| near-frontal, below 400 px | 37 | 0 | 37 | 0 |
| oblique | 2 | 0 | 2 | 0 |

The 63 fails among the reviewer-eligible photos:
- **53:** a hand withholds or hides most of the outline of one or two markers. These are 10:10 dealer photos, rejected
  only by the full-watch rule.
- **10:** tilt ≥ 13.29° (the Phillips GMT photos in §2).

Every reviewer-ineligible photo is rejected.

**The 384 historical originals.**

| Outcome | Photos | Why |
|---|---:|---|
| Suitable (pass) | **56** | 49 artifact originals (42 of them also in the reviewed set) and 8 public originals, one shared |
| Fail | 118 | 96 below the resolution floor (74 are thumbnails under 400 px); 21 with a hidden or withheld marker; 1 landmarks |
| Inconclusive | 210 | 197 have no readable dial (the calibrator downloaded whole listing galleries: side, clasp and paper shots are not dials, so the production 124060 registration finds no black dial or minute lattice); 13 just outside the tested envelope |

The earlier report of 0 suitable was caused by two defects, now fixed:
- the per-marker check demanded a tilt from round markers, which have none, so no photo could pass;
- a soft edge-fit warning was treated as an obstruction.

**Resolution sensitivity.** Reviewed face-on photos with a dial radius of at least 400 px were resampled to smaller dials
and re-measured. The table gives the 95th-percentile change from full resolution, in % R; the strict triangle in/out
limit is 0.373.

| Dial radius | GMT in/out | No-Date in/out | Date in/out | Reliable markers |
|---:|---:|---:|---:|---|
| 350 | 0.064 | 0.071 | 0.062 | 94–97 % |
| 300 | 0.086 | 0.099 | 0.087 | 94–98 % |
| 250 | 0.133 | 0.125 | 0.108 | 94–99 % |
| 225 | 0.148 | 0.151 | 0.124 | 94–98 % |
| 200 | 0.166 | 0.140 | 0.159 | 31–66 % (the app's floor) |
| 175 | 0.133 | 0.126 | 0.152 | 0 % |

Precision degrades smoothly. There is no cliff above the frozen floor.

**Controlled counterexamples.** These are known-bad copies of admitted photos, screened end to end
(`tools/image_archive/degrade.py`): blur σ 3 and 8 px, an hour-hand-sized bar over the 4 marker, a near-opaque glare
over a third of the dial, the frame cut through the 12, and a 150 px dial. Results: in CI run `39657616336`, 24 admitted
originals (8 per model) all passed end to end through the production screener, in agreement with the offline
evaluation. Of the 144 degraded copies, **143 were rejected and 1 was admitted**:

| Copy | Rejected | Admitted |
|---|---:|---:|
| blur σ 3 px | 24 (13 fail, 11 inconclusive) | 0 |
| blur σ 8 px | 24 (1 fail, 23 inconclusive) | 0 |
| hand-sized bar over the 4 | 23 (fail: marker 4 hidden) | **1** (No-Date `17484cbe61baaa1a`) |
| glare over a third of the dial | 24 (fail: markers 9–11 withheld) | 0 |
| frame cut through the 12 | 24 (inconclusive: no dial registered) | 0 |
| 150 px dial | 24 (fail: resolution) | 0 |

The one false accept is a known limitation. The app did not mark the 4 marker as hidden on that photo; it is likely
that more than half of the marker's outline stayed visible beside the bar. The screening was not changed for this
pack.

## 5. Limitations (read before relying on the profile)

- **Too few real oblique counterexamples.** Only 2 reviewed oblique photos have recoverable bytes, and both are also
  below the resolution floor. The false-accept rate for pose therefore cannot be bounded from real reviewed photos. The
  tilt bounds rest on genuine-measurement degradation (§2), not on reviewer labels.
- **The fail bound rests on 3 watches.** All are Phillips auction shots, so tilt is confounded with that one
  photographic source. 10.6–13.3° is untested and stays inconclusive.
- **Blur, glare and hands are not reviewer-labelled.** No reviewer-labelled photos of these exist in the repository.
  Their evidence is the app's own per-marker gates and the controlled counterexamples above, which include one false
  accept (a dark bar over a marker that the app did not see as hiding it).
- **Envelope bounds are sample extremes.** They are conservative: they withhold rather than admit.
- **Bob's CDN re-encoding.** It has made the 228-photo additional review set (39 of the 44 oblique labels)
  unrecoverable as exact originals.

## 6. Owner decisions

1. **Review the admitted photos.** Check the 217 unique admitted photos in the visual review pack (workflow
   *Image archive visual review pack*, a private artifact) before any pilot upload.
2. **Resolution floor.** Keep the frozen 200 px dial-radius floor (217 admitted), or require more: 250 px admits 192,
   300 px admits 161.
3. **Marker standard.**
   - (a) As implemented: every marker measured and unobstructed, with soft edge warnings recorded but allowed (217).
   - (b) Every marker fully reliable, the app's genuine-pass standard (148).
   - (c) Partial-marker admission with a per-marker mask, which would keep the 53 hand-at-10:10 photos.
4. **Tilt range 10.6–13.3°.** It stays inconclusive unless new reviewed photographs there are measured.
5. **Then the Microsoft steps.** Connection consent, the synthetic smoke test, and a small approved real-photo pilot.
