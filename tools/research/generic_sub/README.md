# Generic Submariner / GMT: do other references share the 124060 / GMT geometry? (2026-10-09)

Question (owner): can the app be "pick GMT or Submariner and it does the rest"? A pooled reference is only allowed
where every pooled reference has the same marker geometry (QC guardrails §6: one model must not widen another's limits).

**Data.** Owner's Bob's Rolex Harvester packs (app 1.4; genuine dealer photos, original files, sha256 in each pack;
images not committed). Straight-on photos only (wrist shots and 3/4 views excluded): 49 photos of 15 references.
Measured with the production runner (`Alpha96Calib`); per-watch medians (`geom.py`).

**Findings run (app classification, current code).** Maxi-dial photos (31, 12 references) on the borrowed 124060 / GMT
references: 0 clear, 1 worth a look. Older dials (14060, 16610, 16610T; 17 photos): every marker withheld (fails
closed, but labelled "hand crosses marker", which is wrong for these dials).

**Geometry (all measured with the 124060 model; 124060 reference = 106 genuine watches, per-watch sd in brackets):**

| metric (% of dial radius) | 124060 | 41 mm date 126610/126618 (9 watches) | 40 mm date 116610/116613 (11) | 40 mm 114060 (4) | Kermit maxi 16610LV/V (4) |
|---|---|---|---|---|---|
| marker ring scale | -0.01 (0.06) | +0.02 | -0.10 | -0.12 | -0.24 |
| round marker size | -0.04 (0.05) | +0.00 | +0.07 | +0.10 | -0.12 |
| 6 baton radial | +0.03 (0.12) | +0.01 | -0.22 | -0.25 | -0.24 |
| 12 triangle radial | -0.18 (0.14) | -0.27 | -0.35 | -0.31 | -0.54 |

With the GMT 126710 model and reference (68 genuine watches, CI run 37500197377), the 40 mm date Subs agree with the
GMT and the 41 mm date Subs read +0.2% ring scale (all 5 references).

**Conclusion (provisional, 1-3 watches per reference).** Geometry follows the dial generation / case size, not the
model name: (A) 41 mm, 2020 on: 124060 and 126610/126618 (date window instead of the 3 baton); (B) 40 mm maxi dial:
114060, 116610, 116613 and the GMT 126710; (C) 16610LV maxi Kermit: its own; (D) pre-maxi dials (14060, 16610): need
their own master. The Bob's photos come from one studio, and Bob's 41 mm photos match the multi-source 124060
reference while Bob's 40 mm photos do not, so the split is not a source effect.

## Update: more 40 mm watches (owner packs 08:13-08:14)

Seven more straight-on 116610 / 116610LN watches (SKUs 193076, 192290, 189508, 142346, 193767, 193258, 191928, 193924;
other photos in these packs were repeats or wrist shots). On the GMT reference: 0 clear, 0 worth a look. Updated table
(all with the 124060 model; median, and distance from the 124060 median in 124060 per-watch sd):

| group | watches | ring scale | round size | 6 baton radial | 9 baton radial | 12 radial |
|---|---|---|---|---|---|---|
| 124060 reference | 106 | -0.00 ± 0.06 | -0.04 ± 0.05 | +0.03 ± 0.12 | +0.01 ± 0.08 | -0.18 ± 0.13 |
| 41 mm date (126610/126618) | 9 | +0.4 sd | +0.9 sd | -0.2 sd | -0.5 sd | -0.7 sd |
| 40 mm date (116610/116613) | 19 | -1.8 sd | +2.5 sd | **-2.8 sd** | -1.3 sd | -1.3 sd |
| 40 mm no-date (114060) | 4 | -1.9 sd | +2.9 sd | -2.3 sd | -0.9 sd | -1.0 sd |
| Kermit maxi | 4 | -3.8 sd | -1.5 sd | -2.2 sd | -3.2 sd | -2.7 sd |

6 baton radial per 40 mm date watch: 9 of 12 below the 124060 5th percentile (-0.15); 41 mm date watches all within
the 124060 5-95% range. The 41 mm / 40 mm split holds with twice the 40 mm sample.

## Update: 116613 packs (owner, harvester 1.4.1, 08:20)

Five more 40 mm two-tone watches (116613 192315, 192398, 192653; 116613LB 192106; 116613LN 193279): on the GMT reference
0 clear, 0 worth a look. 40 mm date group now 24 watches: 6 baton -2.6 sd, round size +2.2 sd from the 124060; 6 baton
below the 124060 5th percentile on 11 of 15 watches (41 mm date: 0 of 7). One 3/4 view passed harvester 1.4.1 (ellipse
0.9507, parallax 0.0288); harvester 1.4.2 tightens both limits.

## Result: CI harvest run 1 (2026-10-09) - three families

Harvest-ci run 37900999332 (Bob's Rolex Harvester on an emulator, 11 searches): 1040 straight-on photos, 860 listings;
on the app (date on GMT 126710, no-date on 124060): 0 clear, 36 worth a look on 1004 assessed photos. Re-measured by
harvest-lists run 37919395438 (462 photos byte-verified; 606 re-downloads differed from the harvested bytes, the CDN
re-encodes, so those were dropped). Photos >= 250 px, per-watch medians, effect size d = median difference / pooled
per-watch sd:

| per reference group (watches) | ring scale | round size | 6 baton radial | 9 baton radial | 12 radial |
|---|---|---|---|---|---|
| 124060 reference (106) | -0.00 | -0.04 | +0.03 | +0.01 | -0.18 |
| 41 mm date: 126610/LN/LV, 126613/LB/LN, 126618/LB, 126619/LB (136) | +0.02 | -0.02 | -0.01 | +0.00 | -0.20 |
| 40 mm Sub: 114060, 116610/LN/LV, 116613/LB/LN, 116618/LB, 116619 (139) | -0.06..-0.16 | -0.10..+0.08 | -0.08..-0.33 | -0.04..-0.12 | -0.27..-0.50 |
| GMT 116710 / BLNR / LN (40) | -0.05 | -0.04 | -0.18 | -0.09 | -0.30 |
| GMT 126710 BLRO / BLNR / GRNR (91) | -0.17 | +0.09 | -0.10 | -0.04 | -0.24 |

- **41 mm (2020 on): one family.** 41 mm date Subs vs the 124060: |d| <= 0.52 on every metric except round size
  (d +0.70, a 0.02% R difference). Per-reference medians are near-identical across all nine 41 mm references.
- **40 mm Subs + GMT 116710: one family.** B date vs B no-date vs GMT 116710 on the baton and 12 positions: |d| <= 0.5.
  Against the 124060: 6 baton d -1.7, 9 baton d -1.1, 12 d -1.0, ring scale d -1.1 -> must not share the 124060 reference.
- **GMT 126710: its own family** (the existing GMT reference). Ring scale d -2.6 and round size d +3.6 vs the 124060;
  vs GMT 116710 ring scale d -1.9, round size d +3.0. Consistent across BLRO / BLNR / GRNR.
- **Round size depends on dial finish within a family** (40 mm: 116610LV and 116613LN -0.07/-0.09, 116610LN / 116613LB
  +0.07/+0.08): it is a measurement of the metal surround's edge, not only geometry. It already has no uncertainty
  allowance (never clear); a pooled family reference would keep it that way.
- Kermit maxi (16610LV/V) and pre-maxi dials (14060, 16610, 16610T): not supported (too few, or not measurable).
