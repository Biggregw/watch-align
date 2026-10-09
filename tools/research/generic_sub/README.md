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
