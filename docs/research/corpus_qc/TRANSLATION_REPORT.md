# Research cycle 2: landmark and pose translation stability

Branch `research/landmark-translation-stability` (from `main`; the cycle-1 research branch is **not** merged,
only its research tooling was copied in). Production Android code is unchanged, and no APK was built.

**Bottom line.** The production detector is mostly stable under ±1–2% translations, but it has heavy tails:
- the 12-triangle rotation and the rehaut-sector inputs to the pose policy are the unstable parts;
- 49–57% of judged 12-triangle verdicts change under a 1–2% shift.

A translation-stability gate was frozen on development and validated unchanged. It withheld every flag it saw (2 on development, 3 on validation, all on replicas), but:
- there were **no genuine flags** to remove in either partition;
- **no flag could be confirmed** by a second photo, so known-defect retention cannot be measured.

The corpus has **zero unseen independent watches**, so no fresh holdout is possible. Following the brief, this cycle stops before any production recommendation. **Nothing is promoted.**

## 1. Fresh split and excluded watches

`split_v2.py` → `split_v2.csv` (seed `watch-align-translation-split-v2`, watch-level, 60/40 per stratum).

- **Excluded** (the spent cycle-1 locked holdout): gen_126710BLRO_phillips_146213, hv_imgur_album_75b0b1130a,
  rep_cf_UpTW8nx, rep_cf_bpdi5xV, rep_vsf_p3hHVMB, rep_vsf_sh7PSsh, swe_60177, wos_cpo_40616911.
- **Unseen accepted watches available for a fresh locked holdout: 0.** Every remaining ACCEPT population watch
  was development or validation in cycle 1, so this split is re-partitioned seen data. It is suitable for
  development and validation only.

| Stratum | Development | Validation |
|---|---|---|
| gen 126710BLNR | 5 | 4 |
| gen 126710BLRO | 3 | 2 |
| rep BLNR ARF | 1 | 1 |
| rep BLNR C+ | 1 | 0 |
| rep BLNR Clean | 4 | 3 |
| rep BLNR VSF | 4 | 3 |
| rep BLRO Clean | 1 | 0 |
| rep BLRO VSF | 1 | 0 |
| **Watches** | **20** | **13** |

Every genuine watch has exactly one usable photo, so the genuine false-positive proxy has one photo per watch.

## Method

`translate.py` takes every split_v2 photo whose dial production could read in cycle 1: 70 photos (40 development, 30 validation). It writes 9 variants of each:

- `t0`, the same photo re-encoded as the control;
- `x±1`, `x±2`, `y±1`, `y±2`, with the content shifted by that percentage of the width or height.

All variants are the same size and are saved identically as JPEG q97. The uncovered strip is filled with the flat median border colour, so it cannot create edges or marker evidence, and content pushed past the far edge is cropped.

The unmodified production path runs on every variant via `CorpusQc.java`: preview, then `GmtDialCrop`, then `GmtHumanQcAnalyzerV2`, then `GmtHumanPosePolicy`. That makes 630 analyses.

Landmark centres are mapped back to original pixels, the known shift is removed, and spreads are expressed in t0 dial radii. Spread means the maximum distance from the median across the 9 views.

On re-encoding alone, 3 of the 70 photos (1 development, 2 validation) lost a readable dial at t0 and are excluded from the tables. That leaves 39 development and 28 validation photos.

## 2. Baseline landmark translation stability

Figures are median / p95. Centre spread is in dial radii. The flip rate is the share of judged t0 verdicts that change (CLEAR↔CHECK or judged↔not judged) under at least one translation.

| Landmark | Partition | Detected t0 | Centre spread | Rotation spread ° | Size spread (rel.) | Judged t0 | Flip rate |
|---|---|---|---|---|---|---|---|
| 12 triangle | development | 35/39 | 0.0005 / 0.0115 | 0.85 / **25.0** | 0.006 / 0.151 | 35 | **0.49** |
| 12 triangle | validation | 26/28 | 0.0026 / 0.0128 | 0.97 / 3.7 | 0.031 / 0.247 | 21 | **0.57** |
| 3/9 baton | development | 31/39 | 0.0011 / 0.0092 | 0.88 / 4.5 | 0.010 / 0.103 | 10 | 0.30 |
| 3/9 baton | validation | 24/28 | 0.0003 / 0.0105 | 0.78 / 2.9 | 0.007 / 0.140 | 9 | 0.44 |
| 6 baton | development | 34/39 | 0.0008 / 0.0116 | 0.69 / 2.5 | 0.010 / 0.276 | 16 | 0.06 |
| 6 baton | validation | 23/28 | 0.0010 / 0.0085 | 0.77 / **31.4** | 0.008 / 0.286 | 10 | 0.30 |
| round markers | development | 300/312 | 0.0004 / 0.0084 | – | 0.003 / 0.086 | 197 | 0.15 |
| round markers | validation | 214/224 | 0.0003 / 0.0121 | – | 0.003 / 0.139 | 144 | 0.27 |

- **Centres are stable.** The median is 0.0003–0.0026 of the dial radius and the p95 about 1%, so typical jitter is well under a pixel.
- **Rotation and size have heavy tails.** The p95 reaches 25–31° on a few photos, where the fitted landmark changes shape rather than moving. Landmarks were occasionally lost in some translations, and a few appeared that were absent at t0.
- **Verdicts flip much more often than geometry moves.** Many judged values sit near the CLEAR/CHECK limits, so sub-pixel changes cross them. The 12 triangle is the worst case, driven by the gap and alignment verdicts.

## 3. Baseline pose translation stability

| | Development (39) | Validation (28) |
|---|---|---|
| Photos whose pose label changes under some translation | 54% | 68% |
| Label changes per translation | 19.9% | 29.9% |
| Photos with a label change | 21 | 19 |
| …of which a policy quantity straddles a boundary across the 9 views | 17 | 17 |
| Share with rh_maxasym margin to boundary < translation spread | 63% | 82% |
| Share with rh_min_mean margin to boundary < translation spread | 63% | 75% |
| Translation spread of rh_v (median / p95) | 0.147 / 0.64 | 0.167 / 0.85 |
| Translation spread of rh_h (median / p95) | 0.131 / 0.81 | 0.195 / 0.71 |
| Translation spread of the marker-pose tilt, ° (median / p95) | 0.58 / 2.7 | 1.66 / 3.2 |
| Translation spread of the ellipse tilt, ° (median / p95) | 2.2 / 8.7 | 1.2 / 5.3 |

Transitions from t0 to a translation:

| Partition | Transitions |
|---|---|
| Development | GOOD→CORRECTABLE 30, CORRECTABLE→RETAKE 16, CORRECTABLE→GOOD 11, CORRECTABLE→UNASSESSABLE 4, GOOD→RETAKE 1 |
| Validation | GOOD→CORRECTABLE 26, CORRECTABLE→GOOD 23, RETAKE→CORRECTABLE 7, GOOD→RETAKE 6, CORRECTABLE→RETAKE 5 |

**Rehaut question: yes.** A 1–2% translation changes the rehaut sector widths enough to explain most of the pose instability:
- In both partitions, 17 of the photos whose label changed have a rehaut quantity straddling a policy boundary.
- The rh_maxasym ≥ 0.14 and rh_min_mean < 0.45 / 0.75 boundaries are the main culprits. The ellipse tilt contributes only 3–4 photos.

No broad rehaut work was reopened.

**Consensus rule tested.** The rule takes the upper median of three views, with ties resolved to the stricter label. It is compared on disjoint view sets A = {t0, x+1, y+1} and B = {x−1, y−1, x+2}.

| | Single-label agreement | 3-view consensus agreement | Majority-RETAKE photos | RETAKE kept (single) | RETAKE kept (consensus) |
|---|---|---|---|---|---|
| Development | 0.77 | 0.85 | 2 | 0 | 1 |
| Validation | 0.68 | 0.71 | 1 | 0 | 0 |

Consensus improves self-agreement modestly: +8 points on development and +3 on validation. On validation it did not retain the single majority-RETAKE photo, and nor did the single label. With only 1–2 RETAKE cases per partition, "does not weaken poor-angle rejection" cannot be demonstrated. So the rule is **not** proposed for production.

## Same-watch decomposition (robust)

This covers watches with at least 2 photos: 8 in development and 7 in validation. Repeated photos of one watch are never counted as independent: between-watch spread uses one median per watch.

Values are development / validation. Translation MAD is the within-photo median. Photo-to-photo MAD is the within-watch median, which mixes camera position with detector noise. Between-watch MAD uses per-watch medians.

| Metric | Translation MAD | Photo-to-photo MAD | Between-watch MAD |
|---|---|---|---|
| gap (R) | 0.0013 / 0.0019 | 0.0044 / 0.0036 | 0.020 / 0.016 |
| rot ° | 0.05 / 0.11 | 0.46 / 0.07 | 0.50 / 0.53 |
| sp59 | 0.0011 / 0.0022 | 0.0058 / 0.0046 | 0.0072 / 0.0125 |
| sp01 | 0.0012 / 0.0018 | 0.0067 / 0.0067 | 0.019 / 0.009 |
| six_centring | 0.0001 / 0.0002 | 0.0095 / 0.0018 | 0.019 / 0.033 |
| six_rot ° | 0.035 / 0.081 | 0.35 / 0.25 | 0.53 / 0.26 |

- **Typical detector noise under translation is 3–10× smaller than photo-to-photo variation.** That variation comes from camera position and lighting, plus the same noise.
- **Photo-to-photo variation is in turn smaller than between-watch differences** for gap, centring and sp01.
- **The exception is rotation:** on development, photo-to-photo rotation (0.46°) is as large as between-watch rotation (0.50°). So a single photo cannot separate a 0.5° misalignment from camera position, consistent with cycle 1.
- **The tails are catastrophic, not Gaussian.** Non-robust SDs are dominated by a few failed fits (for example, gap SD 0.17–0.47 R). This is the case for a stability check.

## 4. Development threshold experiments (translation-stability gate)

A t0 flag (CHECK/STRONG) is kept only if the gate rule finds it in the required translations. Otherwise it is reported as "Not confidently measurable". Its outcome comes from **other photos of the same watch**:

| Status | Meaning |
|---|---|
| Confirmed | Another photo flags the same landmark verdict |
| Contradicted | Another photo judges it CLEAR |
| Unconfirmable | No other photo judges it |

Class labels never enter the choice.

The candidates were fraction-stable ≥ {0.25, 0.5, 0.625, 0.75, 0.875, 1.0}, or all-of pairs {x+2,y+2}, {x−2,y−2}, {x+1,y+1}, {x±2}, {y±2}. The selection keeps every confirmed flag, withholds the most contradicted and then the most unconfirmable flags, and prefers the fewest extra analyses.

Development had 258 judged landmark verdicts and only **2 flags**, both 12-gap CHECK on replicas, with no genuine flags and no confirmed flags:

| Watch | Stability | Status |
|---|---|---|
| hv_imgur_album_62cbf2f8f1 | 0 | contradicted |
| rep_vsf_HCz9EAj | 0.62 | unconfirmable |

Frozen rule: **all_of [x−2, y−2]**, in `frozen_translation_gate_v1.json`. It withholds both flags at a cost of 2 extra analyses. The retention constraint was vacuous (no confirmed flags), so this choice is weakly determined.

## 5–8. Validation result (frozen rule, unchanged)

| | Baseline | Gated |
|---|---|---|
| Judged landmark verdicts | 184 | 181 |
| Flags (CHECK/STRONG) | 3 (12 triangle 2, 6 baton 1; all replicas) | 0 |
| **6. Genuine false positives** | **0** | **0**, no change measurable |
| **7. Coverage** | 184 judged | 181 judged (−1.6%); 3 withheld as "Not confidently measurable" |
| **8. Known-defect retention** | 0 confirmed flags | **Not measurable**; the 3 withheld flags were all *unconfirmable* |
| Repeatability of withheld flags | stability 0.625, 0.75, 0.625 | – |

The 3 validation flags:
- rep_cf_6I00d8w: alignment, 12 triangle.
- rep_vsf_7s6PyXJ: six-baton centring.
- rep_vsf_oVwWMrC: alignment, 12 triangle.

Each held in 5–6 of 8 translations. The gate removed all of them. They might be real replica defects: on this data the gate cannot be shown to remove false alarms rather than true ones.

**Result: the gate does not materially improve validation.** It has no genuine false positives to remove, and it costs every replica flag, with retention unknowable.

## 9. Fresh holdout result

**Not run: there is no fresh unseen data.** The cycle-1 holdout is spent, and the corpus has 0 accepted watches that no cycle has seen. Following the brief, this cycle stops before any production recommendation.

### Acquisition requirement (research only, no app change)

A fresh locked holdout needs newly harvested, ACCEPTED, population watches that appear in neither split_v1 nor split_v2. They must be independent by `physical_watch_id`. Each photo must be measurement quality, with the dial readable by production.

1. **Genuine: at least 50 watches, 100 preferred, each with at least 2 photos from different camera positions.**
   - Cycle 1 saw 1 genuine false positive in about 17 genuine watches (about 6%).
   - With zero observed false positives in n watches, the 95% upper bound is 3/n (the rule of three). Bounding the gated rate below the 6% baseline therefore needs n ≥ 50.
   - A before/after difference needs several baseline false positives, which means about 100 watches at 6%.
2. **Replica: at least 30 watches, each with at least 3 photos**, across VSF, Clean, C+ and ARF and both BLNR and BLRO.
   - Retention is measurable only when a defect is *confirmed* by a second photo.
   - This cycle had 0 confirmed flags across 33 watches, partly because most replicas have 1–2 photos.
3. **Pose: at least 10 of those watches with a deliberately poor-angle photo** that is genuinely RETAKE.
   - This shows whether a consensus rule keeps RETAKE. There are currently only 1–2 such photos per partition.
4. Lock the partition before any analysis, and run it once.

Until the harvester has collected these watches (they need new source material, because re-harvesting the same sources adds seen watches), no translation-gate or pose-consensus result can be claimed.

## 10–13. Promotion, code, commit, APK

| Item | Outcome |
|---|---|
| 10. Promotion | **Not earned.** Validation did not materially improve, and there is no fresh holdout. |
| 11. Code changes | None to production or Android. Research tooling only: `split_v2.py`, `translate.py`, `stability.py` and `test_translation.py`, plus the cycle-1 research tooling copied from its branch. |
| 12. Commit | See the git log of this branch. |
| 13. APK | None, because Android is unchanged. |

## Regression

| Check | Result |
|---|---|
| Harvester tests | 31/31 pass |
| Research tests | 12/12 pass (cycle 1 + cycle 2) |
| Android JVM unit tests (`:app:testDebugUnitTest --rerun`) | 225 pass, 0 failures |
| 337-photo production regression vs alpha66 reference | Identical: 337/337 photos, 0 verdict changes, 0 measurement changes |

Not verified:
- device camera behaviour;
- native OpenCV on Android;
- the GitHub Actions workflow in this cycle.

## 14. Next highest-value experiment

1. **Collect the fresh data above.** Every remaining question depends on it.
2. With it, test a **translation re-check applied only to flags**, which is what this gate is. It adds 2 analyses, and only for photos that already carry a flag. Measure genuine false positives and confirmed-defect retention on the fresh holdout.
3. **Without new data, the most useful detector work is the 12-triangle fit.**
   - Its rotation p95 of 25° and its verdict flip rate of 49–57% come from the triangle outline changing shape under sub-pixel moves.
   - A fit that is sub-pixel stable, measured on development only, would reduce flips at the source rather than hiding them.
4. **The same applies to the rehaut sector widths** that feed `GmtHumanPosePolicy`. Their jitter across translations exceeds the margin to the policy boundary for about two-thirds of photos.
