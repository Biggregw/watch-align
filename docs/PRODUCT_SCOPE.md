# Watch Align Product Scope

## Product definition

Watch Align analyses **one uploaded dealer/QC photo** and reports how the measured watch compares with proven-genuine reference geometry for that model or family.

The product does **not** answer “is this genuine?” and must not output a fake/genuine probability. It answers:

> **Which measurable features differ from the genuine reference, by how much, and with what confidence?**

A watch can legitimately return **“no detectable deviation from the genuine reference in the features assessable from this photo.”** That does not mean every unmeasured part of the watch is genuine or perfect.

## Governing principles

1. Genuine evidence defines the reference. Replica evidence never moves the genuine envelope.
2. Establish a clean nominal master first, then estimate genuine variation around it.
3. Keep three sources of spread separate:
   - nominal design geometry;
   - genuine watch-to-watch variation;
   - photo/detector uncertainty.
4. A genuine image affected by pose must not widen the manufacturing envelope merely because the watch is genuine.
5. Prefer relational pose checks over increasingly complex reconstruction when they work. Opposing-marker residuals are a first-class research direction.
6. Multiple photos of one physical watch estimate repeatability/photo error. They do not count as independent manufacturing samples.
7. Use existing project photos and artifacts before sourcing more data.
8. Date Submariner references may contribute to **shared** geometry only when the feature is demonstrably the same. Date-specific areas must be excluded from a no-date calibration.
9. Research succeeds when it reproduces known genuine measurements and known real-world QC defects, not when it separates every replica from every genuine watch as two populations.
10. Do not broaden tolerances to accommodate questionable photographs. Correct, pair, or exclude the contaminated measurement instead.
11. The user input remains an externally supplied QC/dealer photo. Do not redesign the product around guided capture or multiple runtime images.
12. Every feature must ultimately be classified as one of: **deterministic code**, **vision AI**, **hybrid**, or **not reliable enough**.
13. Choose that implementation only after empirical validation. Do not force every visual check into OpenCV and do not default everything to AI.
14. Production output should be feature-by-feature and explainable: measured value, genuine reference/range, deviation, confidence/assessability, and concise interpretation.
15. If all assessed features sit within the genuine reference, say so plainly. Do not invent a defect and do not force a class verdict.

## Current restart plan

The restart begins with **GMT**, because the existing GMT implementation is a known-working control.

The first experiment is deliberately small:

1. choose one already-working GMT feature;
2. use existing proven-genuine images before searching for anything new;
3. select the best genuine image as the nominal candidate;
4. measure the same feature on several independent genuine watches;
5. use opposing/shared dial geometry to identify perspective contamination;
6. derive a prompt-based calibration result offline;
7. compare it with the existing GMT calibration;
8. refine the prompt until it reproduces the known answer reliably on fresh held-out images.

Only after this succeeds should the protocol be expanded to more GMT markers.

## Validation stage

Once a genuine feature envelope is frozen, validation moves to independent RepTimeQC examples:

- collect examples where reviewers identified a specific visible QC defect;
- preserve the reported defect label separately;
- measure the photo using the frozen protocol, preferably blind to the label;
- check whether the same defect is supported by the genuine-reference comparison;
- run accepted/GL cases as negative controls;
- record misses and false flags and determine whether the failure is calibration, pose, detector, image quality, or an inherently subjective feature.

This stage determines whether a feature should be implemented in code, AI, hybrid form, or withheld.

## What is explicitly paused

Until the GMT control experiment proves the new method, do not restart:

- broad Submariner-family expansion (superseded 2026-10-08 by owner decision: the Submariner family is added one reference at a time, each with its own genuine reference before it can report anything; see `docs/HANDOFF.md`);
- calibration-platform/contract migrations;
- open-ended corpus growth;
- projective/homography research as a goal in itself;
- authenticity classification;
- expensive CI jobs whose question can be answered offline.

Historical branches and research documents are reference material only unless their findings are deliberately revalidated under this scope.
