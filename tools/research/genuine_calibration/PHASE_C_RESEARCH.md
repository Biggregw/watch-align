# Phase C research: offsets, side-by-side comparison, real QC cases, operating points (23 October 2026)

**Research only. Nothing deployed.** No tolerance, reference geometry, classification or the approved triangle top-edge
measurement changed. The existing results stay authoritative in the app.

Data:
- genuine calibration run 39833195097: 178 photos, 168 watches;
- replica records from run 39827340472;
- reviewed QC cases: CI run 39844207180 (public), plus local controls measured locally and never published.

Full tables:
- [`results/offsets-run-39833195097.md`](results/offsets-run-39833195097.md)
- [`results/operating-points.md`](results/operating-points.md)
- [`results/side-by-side-public-qc.md`](results/side-by-side-public-qc.md)

## 1. Systematic offsets: cause and held-out corrections

### GMT ring scale (−0.19 %): the GMT reference geometry, not photography

| Evidence | GMT 126710 | No-Date 124060 | Date 126610 |
|---|---|---|---|
| Median ring scale [90 % CI] | **−0.196 % [−0.234, −0.180]** | −0.017 % | +0.005 % |
| Photos below zero | **24 of 24** | 56 % | 43 % |
| By variant | BLNR −0.20, BLRO −0.29, GRNR −0.16 | — | 11 variants within ±0.05 |
| By dealer | Bob's −0.19 (21 photos), Phillips −0.31 (3) | Bob's −0.016, EWC −0.023 | Bob's +0.005 |
| Low-tilt photos (< 3°) | −0.194 (8 photos) | −0.014 | +0.020 |
| Same quantity against the independent luminous reference | −0.160 (r 0.96) | −0.004 | +0.019 |
| Dependence on resolution / tilt | r 0.23 / −0.38 | 0.10 / 0.00 | 0.00 / −0.14 |

- **Not photographic:**
  - it is present at low tilt and does not depend on resolution;
  - it appears in every GMT variant and at both dealers;
  - Bob's Date photos, taken under the same dealer's photography, show no offset.
- **Not a measurement artefact:** an independent reference (the luminous master) shows the same offset.
- **Conclusion:** the GMT reference places the hour-marker ring about 0.2 % R too far out relative to the minute track,
  compared with every genuine GMT measured. Whether the master was built from a biased photograph or GMT dials differ from
  it, the practical meaning is the same: the reference does not match the genuine population.

**Held-out test:** a per-family ring-scale centre, estimated on training watches only and tested on others.
- The GMT ring-scale spread (p95) falls from 0.293 to 0.118 %, the Submariners' level.
- GMT genuine false alarms at the 5 % point fall from **22.7 % to 4.5 %** (out-of-fold).
- The holdout's whole-watch rate falls from 6.9 to 3.4 %.

This is a reference-geometry correction and needs your approval.

### Per-hour marker offsets: real for Date and GMT, small, not worth a geometry change yet

| Family | Largest | RMS | Reproducible between disjoint watch halves (r) | Low vs higher tilt (r) | Between dealers (r) |
|---|---:|---:|---|---:|---:|
| GMT | 0.103 % R (6 in/out) | 0.043 | 0.63 (0.45–0.80) | 0.60 | — (one dealer) |
| Date | 0.066 | 0.033 | **0.80** (0.70–0.90) | **0.82** | — (one dealer) |
| No-Date | 0.045 | 0.019 | 0.37 (0.08–0.60) | 0.11 | −0.11 (EWC vs Bob's) |

- **Date and GMT:** the offsets repeat across independent watches and across tilt, so they are reference or model
  geometry, not photography.
- **No-Date:** they do not repeat across watches or dealers, so they are measurement noise.
- **Correction tested on held-out watches:** the position scale drops 9 % (σ 0.082 to 0.075 % R) and the held-out
  position p95 from 0.196 to 0.193. Whole-watch false alarms are unchanged.
- **Recommendation:** do not correct per-hour geometry now; the benefit is too small.

## 2. Side-by-side comparison in Watch Align (implemented, off by default)

- **Switch:** a start-screen checkbox, "Research: compare with the new marker measurement (does not change results)". It is off by default and remembered.
- **When it is on:** the results screen gains a collapsed section, "Research comparison (not used for the result)". It has one entry for each marker and one for the whole ring:
  - **existing:** the app's own answer (Clearly off / Possible / No visible issue, and the strict genuine standard);
  - **new:** the MarkerGeometry reading, its multiple of the genuine scale, and where it falls against the two research candidates (5 % and stricter);
  - **agreement:** both flag, existing only, new only, or both clear.
- **Triangle in/out:** shown but not judged; the approved top edge stays.
- **What does not change:** `WatchClassification`, `ClearlyOff` and `AlignmentDecision` are untouched, and the existing verdict, cards and close-ups are identical.
- **Tests:** `ResearchComparisonTest` (thresholds, ineligible markers, ring as its own finding, triangle in/out not judged, off by default). Phase B parity tests still pass.
- **Desktop driver:** `ResearchCompare` prints exactly what the app shows, for any photo list.

## 3. Labelled real QC cases (start of the defect-validation set)

[`qc_cases.csv`](qc_cases.csv) holds 31 photographs. Each keeps its label source and evidence, and the categories stay separate:

| Status | Photos | What it means |
|---|---:|---|
| confirmed defect | **0** | Needs your confirmation. No case in the repository has an owner-confirmed defect. |
| uncertain | 6 | A defect claimed by a QC reviewer or a note (GMT crooked 6 ×2, Sub rejected 12, RL_LOCAL 6/12), or conflicting evidence (Sub accepted 12: reviewer accepted, engines flag the 12). |
| acceptable imperfection | 5 | The reviewer accepted the named marker (GMT accepted 6 ×3, Sub control 12 ×2). Only that marker is labelled. |
| unlabelled | 20 | Replicas or phone references with no reviewed verdict. Not assumed defect-free. |

Photographs labelled "accepted" or "control" are not treated as defect-free. Only the reviewed marker carries the label.

## 4. Three operating points on identical photographs

### Genuine false alarms

Share of genuine watch photos with any alarm. Out-of-fold: every watch is judged by scales and k fitted without it.

| | Existing strict genuine | Existing Clearly off | New 5 % | Stricter | New 5 % + GMT ring-scale centre (research) |
|---|---:|---:|---:|---:|---:|
| **All (168 watches)** | 0.6 % | 0.6 % | 5.7 % | 1.8 % | 4.8 % |
| GMT (22) | 0 % | 0 % | **22.7 %** (5 watches, all ring scale) | 4.5 % | 4.5 % |
| No-Date (56) | 1.8 % | 1.8 % | 4.5 % | 1.8 % | 7.1 % |
| Date (90) | 0 % | 0 % | 2.2 % | 1.1 % | 3.3 % |
| Bob's (135) | 0 % | 0 % | 4.8 % | 1.5 % | 3.7 % |
| European Watch (27) | 3.7 % | 3.7 % | 3.7 % | 0 % | 3.7 % |
| SwissWatchExpo (2) | 0 % | 0 % | 50 % | 50 % | 100 % |

The flagged watches, with their markers, are listed in `results/operating-points.md`. With the GMT scale corrected, the
alarms move from the GMT ring to single markers on the Submariners. There k is recalibrated to the same 5 % overall.

### Synthetic defects

Detection of the moved marker, pooled across families. Per family is in the results file.

| Defect | Existing strict | Existing Clearly off | New 5 % | Stricter |
|---|---:|---:|---:|---:|
| Round 0.5 % R | 0.03–0.05 | 0 | **1.00** | 0.94–0.97 |
| Round 0.75 % R | 0.36–0.53 | 0 | 1.00 | 1.00 |
| Baton 0.5 % R | 0 | 0 | 0.92–1.00 | 0.83–1.00 |
| Triangle sideways 0.5 % R | 0–0.60 | 0 | **1.00** | 1.00 |
| Triangle in/out 0.5 / 0.75 % R | 0.50 / 1.00 | 0 | not judged (top edge) | not judged |
| Baton turned 1° | 0.19–0.47 | 0–0.05 | 0.15–0.33 | 0.05–0.11 |
| Triangle turned 1° / 2° | 0.29–0.50 / 0.90–1.00 | 0.14–0.38 / 0.90–1.00 | **0 / 0.50–0.70** | 0 / 0.30–0.50 |

For positions, the new engine finds a 0.5 % R displacement that the existing one almost never finds. **For rotation, the
existing limits are better.** The new rotation candidates are conservative (§4 of REPORT.md).

### Reviewed real QC cases (the question that matters)

| Case | Claim / label | Existing app flags | New 5 % flags | Claimed marker assessed by new? |
|---|---|---|---|---|
| GMT crooked 6 (public, 593 px) | 6 rejected; uncertain | **6**, 9, 12 | none | no: too few eligible markers for a ring |
| GMT crooked 6 (local ARF, 234 px) | 6 reported; uncertain | 8, 12 | **6** (0.77 % R inward), 4, 7, 8, 9, 11, 12, ring (+0.94 % size) | yes |
| RL_LOCAL BLNR (220 px) | 6 left, 12 tilted; uncertain | **6** (Clearly off), 5, 9–12 | none | no: markers not eligible |
| Sub rejected 12 (public) | 12 rejected; uncertain | **12** (Possible; outside strict) | 3, 4, 5, 6, 7, ring | no: 12 not eligible |
| Sub accepted 12 (2 independent photos) | 12 accepted; conflicting | none / 9 | **12** (sideways −0.48/−0.45 % R, turned −1.1°), **2**, **11**, ring, in **both** photos | yes |
| GMT accepted 6 (3 photos) | 6 accepted | 12 | none | 6 not judged (too few for a ring) or ineligible |
| Sub control 12 (public) | 12 accepted as control | **12** (Clearly off), 2, 3, 9 | none | no |

**Interpretation:**
1. **Coverage is the new engine's main weakness on real QC photos.** It requires at least 90 % of the outline seen, no
   hand and at least five other eligible markers for its ring. At 10:10 hands, with glare or with small dials, it could
   not assess the claimed marker in 5 of 7 reviewed cases. The existing app answered in all of them, and flagged the
   claimed marker in 3 of the 4 claimed-defect watches (missing the local ARF crooked 6, at 234 px).
2. **Where it can assess, it finds repeatable physical deviations that the existing app does not:**
   - on the reviewer-accepted Sub, the 12, 2 and 11 in both independent photos;
   - on the 124060 replica `clean_1lnn2kz`, the 6 and 9 in all three photos.

   Because these repeat across photos, they are on the watch, not in the photograph.
3. **Whether those deviations are visually unacceptable is not known.** The one reviewer verdict that covers such a
   case (Sub accepted 12) says acceptable, and the replicas carry no verdict. Until you review them, the new engine is
   proven sensitive to real deviations of 0.35–0.5 % R. It is not proven to find visually unacceptable defects.

## 5. Recommended next decision

1. **Review a short, targeted set and give each marker a verdict** (confirmed defect, acceptable imperfection, or uncertain):
   - **cases where the engines disagree:** Sub accepted 12 (12, 2, 11), 124060 `clean_1lnn2kz` (6, 9), ARF crooked 6 (6 and the others);
   - **claimed defects the new engine could not assess:** public GMT crooked 6, Sub rejected 12, RL_LOCAL 6.

   This, not more synthetic testing, decides whether the new engine detects defects you consider unacceptable.
2. **Approve, or not, the GMT ring-scale reference correction** (−0.19 %, held-out tested). Without it, the new engine's
   GMT false alarms are 22.7 % at the 5 % point.
3. **Research next (no app change):**
   - widen the new engine's coverage on QC photos by judging markers the app already treats as credible (the archive's 90 % outline rule is stricter than the app's), tested for genuine false alarms;
   - re-check rotation limits, where the existing app is currently better.

Until then, the existing results remain authoritative. The comparison stays behind the off-by-default research switch.
