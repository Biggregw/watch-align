# Watch Align: silver bevel pixel-trace experiment (14 October 2026)

Research-only, no code changed in Watch Align. This is a small transparent test of a simpler alternative to full fitted marker polygons.

## Inputs
- `tri_watch.jpg`, an enlarged genuine-watch dial triangle crop from the conversation.
- `qc_triangle_crop_work.png`, a crop made directly from the supplied QC photo `38097.jpg`.

## Method
1. Manually place three **rough search guides**, top-left, top-right and tip, around each triangle. This is an intentional limitation; the prototype does **not** yet automatically find the marker.
2. Sample 66 short strips normal to each of its three nominal sides, skipping tips/corners (13%-85% of each side). Strips retain original photo pixels, converted to grayscale and lightly smoothed.
3. For each strip, search for *two* darkening transitions: luminous material to bevel, and bevel to dial. Search a range of possible separations; favor pairs with strong evidence and a *soft* approximate metal-border width prior. These are photographic edges, not a rendered triangle.
4. Use continuity along the side to choose a plausible sequence of transitions rather than independent isolated bright spots.
5. Repeat the complete tracing with approximate border width changed by ±20% and the search guide shifted in four directions by 1 original pixel. Flag any outer edge position that changes >2 original-image pixels as **unstable**, rather than pretending it was measured accurately.
6. Show original, blue fill of supported silver-band pixels, and a detail overlay. Cyan = supported outer edge; pink = supported inner edge; amber = unstable/ambiguous edge.

## Results
| Case | Top stable /66 | Left stable /66 | Right stable /66 |
|---|---:|---:|---:|
| Genuine crop | 66 | 23 | 34 |
| QC crop | 64 | 0 | 66 |

The prototype appears to trace some of the photographed silver band correctly, particularly the top and the QC right side. It also **fails the key test of robustness** on the QC left side: changing border-width prior alters its outer edge by roughly 4 image pixels. On the genuine crop, prior changes can jump ~9.5 pixels on part of the left side. Consequently, this is **not yet a reliable fully automatic detector**.

## Interpretation and limitations
- This is a visual feasibility experiment, not a QC verdict. Two images, with a human-supplied initial search corridor, cannot prove genuine-versus-QC specificity.
- 'Stable' only means stable under the listed limited perturbations, not agreement with a manually labelled physical metal boundary.
- No numerical defect measurements are derived from the prototype. Brightness transitions can follow lume, inner bevel, polished reflection, or the real outer boundary.
- We have intentionally **not inferred the tip or corners**. They are outside the scan window, not missing for no reason.
- The source code and imagery are provided so the transition search, thickness prior, continuity penalty, and stability thresholds can be inspected and challenged.

## Files
- `silver_bevel_comparison.png`: aligned six-panel comparison.
- `bevel_genuine_evidence.png`, `bevel_qc_evidence.png`: enlarged edge evidence on the photographs.
- `bevel_genuine_filled.png`, `bevel_qc_filled.png`: inferred silver band.
- `bevel_profiles.png`: raw normal-scan intensity profiles.
- `reconstruct_bevel.py`, `annotate_bevel_confidence.py`, `bevel_probe.py`: runnable Python analysis (Python with Pillow, NumPy, SciPy, OpenCV, Matplotlib).
