# Submariner Date 41 mm (126610 family): genuine reference

Model `submariner_126610`: 126610LN/LV, 126613LN/LB, 126618LN/LB, 126619LB. Owner priority (2026-10-09): models that
are currently replicated (TheOneWatches / Clean / VS listings) - the 41 mm Submariner Date is the most common after the
124060 and the GMT 126710.

1. **Geometry.** The 124060 dial master with the date window at 3 instead of the 3 baton. Justified by
   `tools/research/generic_sub/README.md`: 136 genuine 41 mm date watches (9 references) read within the 124060
   watch-to-watch spread on every marker (|d| <= 0.52; round size d +0.70 = 0.02% R). Date window, cyclops minute
   exclusions (11-19) and the seconds-hand search come from the GMT 126710 spec.
2. **Catalogue.** `catalogue_126610.csv`: 358 straight-on genuine photos of 307 Bob's Watches listings, from the
   Bob's Rolex Harvester (owner packs and CI harvest run 37900999332; harvester straight-on gate). sha256-verified
   re-fetch in CI (`sub126610-genuine-runner.yml`); images are never committed.
3. **Reference.** Built with the 124060 tooling (`../sub124060/build_sub_reference.py`, model-generic). Allowances: the
   more cautious of this catalogue's and the 124060's (`edge_safe_uncertainty.py --all-families`), because one-photo-
   per-listing catalogues have few repeat photos.
4. **Date window.** Not referenced yet: reported "not assessed - no genuine reference yet" (fails closed).

## Build 1 (2026-10-09)

- Runner (CI 37941638232): 121 of 358 catalogued photos byte-verified (Bob's CDN re-encodes the rest even when JPEG
  is requested), 120 measured. Whole-image hash dedup flags 117 of 120 (studio photos look alike) and is not used;
  measured dedup (`dedup_measured.py`, 0.0005 R): 2 shared photos.
- Reference: 118 watches (12 triangle 118, 6 baton 109, 9 baton 117, rounds 118). `calibrate_m12_nominal.py` now skips
  the leave-one-source-out check for a single-source catalogue.
- Allowances: no watch has two photos, so the 126610 photo-to-photo spread cannot be measured; the 124060 allowance
  file is used (same detector, 41 mm dial geometry and dealer photography). The 12 edge filter uses the 124060 limit.
- Held-out (leave one watch out): 0 clear on every feature except one watch, bobs_126613LB_181567 (6 baton rotation
  2.04 deg, position 0.71% R; every other watch <= 0.75 deg / 0.36% R). It stays in the reference until its photo is
  inspected (QC guardrails 1: exclusions need a documented contamination reason); `sub126610-genuine-check.yml`
  runs the app (hand / glare check) on the catalogue and prints that photo.

## Build 2: the app's hand / glare withholds applied (2026-10-09)

`sub126610-genuine-check.yml` (CI 37945684586, app at 25d15a7) ran the app on the catalogue: 0 clear, 0 worth a look,
4 too small to see on genuine photos. Its hand check withholds the 6 on bobs_126613LB_181567: the seconds hand runs
straight across the 6 baton (photo printed in the log and inspected) - the 2.04 deg reading was the hand, not the
watch. `../sub124060/apply_interference.py` now marks every marker the app withholds (hand / glare) as unusable before
the reference is built (116 markers on 104 photos), so the reference holds only readings the app would trust.
Result: 6 baton genuine max 2.04 -> 0.75 deg (124060: 0.86), 108-118 watches per feature; leave-one-watch-out 0 clear
(10 worth a look). The 124060 reference was built without this step; adopting it there needs its own regression run.

## Alpha105g: one-sided edge readings excluded (2026-10-10)

Same rule as `../sub124060/README.md` section 17: genuine readings whose two-side width deviates from the model median
by more than 3 x robust spread (CI 38054695092) are lighting on one polished bevel, not the watch.
`../alpha105g/apply_edge_consistency.py` with `../alpha105g/edge_flags.csv` marks them unusable in
`results/runner/per_photo_edge_consistent.csv` (6 baton: b08abca874101115, 5cc54f9348e6fc46, and aae59db4a70ab5e9,
already withheld by the hand check; 9 baton: b08abca874101115; 12: none). The reference is rebuilt with the same
recipe (`calibrate_m12_nominal.py --no-priority`, `build_sub_reference.py`) on that file, after the committed reference
was reproduced byte for byte from `per_photo_edge.csv`. Allowances unchanged.

| Feature | before (n, max) | after (n, max) |
|---|---|---|
| six_rot / six_off | 108, 0.754 deg / 0.356% R | 106, unchanged |
| nine_rot | 115, 0.799 deg | 114, 0.802 deg (nominal 0.057 -> 0.054 deg) |
| nine_off | 115, 0.222% R | 114, unchanged |
| 12 triangle, rounds, ring | unchanged (triangle files byte-identical) | |

Leave-one-watch-out: 0 clear before and after; worth a look unchanged (6: 1 / 2, 9: 1 / 1).
