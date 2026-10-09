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
