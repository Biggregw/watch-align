# Watch Align Product Scope

## Product definition

Watch Align is a replica-watch QC application for analysing uploaded dealer/QC photos.

The existing GMT experience is the product reference. New watch families should be added to that same core workflow unless the owner explicitly approves a redesign.

## Non-negotiable rules

1. Users upload QC/dealer photos. Do not redesign the product around taking new camera photos.
2. Preserve the existing GMT workflow and behaviour unless a task explicitly asks to change it.
3. New watch families should fit the existing GMT-style experience rather than create a separate product flow.
4. Research exists to unblock production QC features. Research is not the product.
5. Do not redesign the workflow to solve a measurement problem. Improve, replace, or suppress the individual analyser/check instead.
6. Reuse generic infrastructure where valid, but never silently reuse GMT-specific geometry, thresholds, detector assumptions, or date-side logic for another family.
7. If a family-specific check is not reliable enough, mark that check unavailable/insufficient rather than changing the whole product.
8. Watch Align is for replica QC. Do not turn it into an authenticity classifier.
9. Do not start open-ended corpus expansion, detector research, or statistical work unless it addresses a named production blocker.
10. Before beginning any substantial new research phase, state the production problem it solves and the expected production decision it will enable.
11. The next production family target is Rolex Submariner 124060, followed by 126610LN and 126610LV.
12. GMT production behaviour must remain regression-protected while Submariner support is added.
13. Real dealer/QC photos are the target input. Production validation must include them.
14. The end state of a family implementation is working Android QC support and a testable APK, not indefinitely expanding research statistics.
15. If a requested task begins expanding beyond these boundaries, stop before implementing the expansion and report the proposed scope change for approval.

## Current Submariner direction

The Submariner research work is supporting evidence, not a new product architecture. The production objective is to add 124060 support into the existing GMT-style Android QC experience, carrying across only the research findings and family-specific components that are actually needed.

Do not assume that every research metric must become a production check. Use conservative production behaviour where evidence is weak.

## Decision rule for future work

Before adding work, ask:

- What user-facing QC capability does this enable?
- Is this needed for the existing GMT-style workflow?
- Is there a smaller production-focused solution?
- Does it preserve GMT behaviour?
- Does it move the family toward a usable APK?

If those questions cannot be answered clearly, the work is probably outside current scope.
