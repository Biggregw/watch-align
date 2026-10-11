# Alpha106 yellow-template audit: two distinct references (corrected 11 October 2026)

**Research only.** This document supersedes the earlier mistaken assumption that the full-photo yellow overlay and the Java research runner use the same expected polygon. No Android production code, reference geometry, thresholds, or detector were changed.

## Code-traced geometry: full-photo yellow and close-up yellow differ

**The user's screenshot is the full-photo "Bright perspective outline" view:**

1. `Alpha99Pipeline.java` calls `AutomaticDialOverlay.build(photo, model)`, then `BatonFitDiagnosticOverlay.render(...)` to add cyan fitted baton lines.
2. `AutomaticDialOverlay.warpOutline(...)` perspective-warps `Alpha92BakedDialOutline.bitmap(model)` using the fixed dial pose `H`.
3. `Alpha92BakedDialOutline.bitmap(model)` draws **unmoved model-master marker polygons** (`drawBaton`, `drawTriangle`, and standard rounds). **No measured global marker-ring correction** is applied to yellow.
4. The yellow stroke is 4px at a 480px canonical dial radius, about **3.16px thick** at the test photo's 379px radius. Thus a small ring shift can be hard to see beneath the stroke.

**A different yellow is used in marker CLOSE-UPS** by `Alpha98Closeups.forFinding(...)`: `moved(spec.batonPolygon(), ring)`. This reference is adjusted for overall ring translation, rotation, and scale. Existing `MarkerMismatchBatch.java` and `MarkerMismatchResearch.java` use **this ring-adjusted close-up** polygon for their expected batons.

A third **experimental** reference from `tools/research/marker_mismatch/calibrate_shapes.py` adjusts polygon vertices using training genuine watches. It is neither standard yellow outline.

## Metric specified by owner

For a chosen expected outer-metal marker polygon E and fitted actual outer-metal marker polygon A:

`mismatch_percent = 100 × [area(A − E) + area(E − A)] / area(E)`.

Denominator is **filled genuine marker-template area** (not union, dial, or bounding circle). Extra and missing are counted separately. Ring-adjusted reference is appropriate for studying **individual marker defects**, provided the global ring shift itself is independently assessed. Do not silently present the fixed-master full-photo outline as the ring-adjusted scoring outline.

## Genuine Rolex Pepsi case

Source: SHA-verified Phillips CH080120 lot 75, photo ID `3cf5442ab9c87b9c`, model 126710BLRO; measured dial radius 379.229px. App result: 9 rotation 0.19° CCW and local position 0.07% R, both WITHIN genuine reference.

| Reference | Extra | Missing | Total filled-template mismatch |
| --- | ---: | ---: | ---: |
| **Fixed master (user's full-photo yellow)** | **3.490%** | **5.137%** | **8.627%** |
| **Ring-adjusted marker close-up yellow** | **0.544%** | **1.877%** | **2.421%** |
| Experimental per-vertex trained reference | separate experimental reference | separate experimental reference | **3.997%** |

All three use the SAME detector-fitted actual polygon. The **6.206 percentage-point** difference between fixed-master and ring-adjusted mismatch results is from expected template placement. The measured ring global shift on this photo is 2.31px. For the ring-adjusted expected polygon, nominal length 115.481px versus detector 115.503px, width 44.981px versus 44.378px; four corner separations 0.50–0.92px in canonical dial-pixel coordinates. This is consistent with a visually good marker but not proof that all of its residual disagreement is a detector error.

## Held-out genuine scores, grouped by physical watch

Read scores with care: exact yellow rendering does not imply the master equals real manufacturing median. Same independent physical-watch held-out subsets were used for both reference modes. Here all percentages use filled expected shape area.

| Family | Marker | Watches | Full-photo fixed median / p95 | Ring-adjusted close-up median / p95 |
| --- | --- | ---: | --- | --- |
| GMT 126710 | 6 baton | 14 | 4.36% / 12.42% | 3.24% / 4.18% |
| GMT 126710 | 9 baton | 15 | 5.64% / 8.37% | 4.11% / 5.32% |
| GMT 126710 | 12 triangle | 16 | 12.61% / 16.02% | 12.54% / 15.67% |
| Sub 124060 | 3 baton | 12 | 3.49% / 6.57% | 2.39% / 4.17% |
| Sub 124060 | 6 baton | 12 | 4.00% / 7.32% | 2.62% / 4.53% |
| Sub 124060 | 9 baton | 12 | 5.08% / 7.69% | 2.19% / 5.20% |
| Sub 124060 | 12 triangle | 13 | 8.51% / 10.92% | 8.49% / 10.78% |
| Sub 126610 | 6 baton | 12 | 3.17% / 7.47% | 2.41% / 3.24% |
| Sub 126610 | 9 baton | 13 | 4.30% / 9.04% | 1.71% / 2.84% |
| Sub 126610 | 12 triangle | 12 | 9.80% / 11.02% | 9.72% / 11.13% |

The triangle master is systematically inconsistent with many measured genuine triangle outlines. Do not infer 4% or any other general severity threshold from these scores. Round marker geometry needs a separate audited reference treatment.

## Single-photo perturbation study

Research-only workflow run: https://github.com/Biggregw/watch-align/actions/runs/38155286452

One SHA-verified original photo, eight mild derived variations, **unchanged Java pose/marker edge detector**:

- Six variants usable; two pose failures (mild contrast reduction, mild blur).
- Ring-adjusted close-up percentage: baseline 2.421%; usable varied scores **2.240%–3.036%**; max deviation 0.615 percentage points.
- Fixed full-photo percentage: baseline 8.627%; usable varied scores **8.480%–8.931%**; max deviation 0.304 percentage points.
- **Precision is not accuracy**: cannot establish how much of a stable 2.42% comes from genuine metal size versus systematic incorrect edge selection.

## Decision for the project

- Keep owner's `extra + missing / filled expected marker area` formula unchanged.
- When reporting **individual marker** mismatch, prefer ring-adjusted nominal geometry to avoid counting global ring movement against every marker. Check overall marker-ring placement separately.
- **Explicitly reconcile the full-photo yellow and measured reference before shipping this new mismatch number.** Either provide a separately labelled ring-adjusted yellow inspection overlay or show which yellow is used in the calculation; do not quietly change production overlay.
- Inspect outer gold/steel surround (not lume-only), and manually trace the Phillips marker rim independently to distinguish detector bias from manufacturing variation before adopting limits.
- Measure spacing gaps independently; no averaging away a single defective marker.

No production code or calibration threshold changes have been made.
