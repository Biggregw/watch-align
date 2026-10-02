# Claude instructions for Watch Align

Read `docs/PRODUCT_SCOPE.md` before planning or implementing substantial work.

Key rules:

- Watch Align analyses uploaded dealer/QC photos. Do not redesign it around taking new camera photos.
- The existing GMT Android QC experience is the product reference for new watch families.
- Current production target: Rolex Submariner 124060, then 126610LN/LV.
- Preserve GMT production behaviour while adding new families, except when the product owner explicitly changes a cross-family QC rule.
- Reuse generic infrastructure only where assumptions are genuinely generic. Never silently reuse GMT-specific geometry, thresholds, pose policy, or date-side logic.
- Research must solve a named production blocker. Do not start open-ended corpus, detector, or calibration work without explaining the production decision it enables.
- If an individual Submariner check is not reliable enough, suppress or mark that check unavailable rather than redesigning the whole product.
- Calibration is conservative replica QC: every reliable value observed on a genuine watch belongs inside the accepted genuine envelope, even when it is a small genuine imperfection. Do not flag a replica solely for a value already supported by genuine evidence. Only obvious measurement failures may be excluded, and replicas never move a genuine-derived boundary.
- Do not build an authenticity classifier. The product is replica QC.
- The goal of family work is a working Android QC implementation and testable APK.
- If scope starts expanding beyond `docs/PRODUCT_SCOPE.md`, stop before implementing the expansion and ask for approval.
