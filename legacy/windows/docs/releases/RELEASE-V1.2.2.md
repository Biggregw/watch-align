# Watch Align V1.2.2

Full functional-audit bug-fix release.

## Fixed
- Adds official Rolex Submariner 124060 source manifest (`m124060-0001`) so Compare with Genuine can automatically source both supported models.
- Makes the visible reference selector actually control which cached reference is compared.
- Ranks candidate references using apparent perspective distortion as well as framing/scale.
- Makes multi-reference consensus marker-aware; conflicting marker measurements can no longer produce a misleading HIGH/Strong match result.
- Surfaces marker disagreement as an explicit inconclusive warning rather than implying a watch defect.
- Preserves trusted/official provenance when a selected cached reference is submitted through the UI.

## Validation
- Expanded functional audit retained in the regression suite.
- Both 126710BLNR and 124060 source/status paths are tested.
- Windows PyInstaller build, bundled EXE smoke test, installer build and portable ZIP are required before production publication.

Measurements remain image-geometry diagnostics, not physical metrology or proof of authenticity.
