# Genuine calibration: strict face-on admission policy (research, 20 Oct 2026)

**User requirement:** The genuine reference set must contain only front-on photographs. An obvious sideways angle is not acceptable, even if projective correction could make the hour-marker overlay look correct. Quantity is secondary to quality.

## Two-stage filter

1. **Independent geometric screen**: physical dial ellipse axis ratio, central Jacobian anisotropy, opposing-local-scale asymmetry and dial-fit quality from the frozen minute-track/physical-edge pose. Computed *before* consulting any marker measurement or expected genuineness. Strict and ultra-strict sensitivity thresholds in `face_on_screen.py` are exploratory, not certified camera-angle measurements. All non-measurable photos are excluded.
2. **Manual case/crown review**: compare only against model-specific verified front-on case photographs. Inspect the 3 o'clock winding crown, crown guards, lateral case wall and symmetry of the bezel/case. Both seeing **too much sidewall** and a crown becoming **unusually hidden** may indicate camera yaw. Also inspect perspective along the vertical direction; crown alone cannot reveal tilt up or down. If any angle is apparent, or crown/case details are obscured, exclude from the pristine calibration subset. Frame rotation (watch turned clockwise in the image) is not camera perspective and is not itself a rejection reason.

Only photographs passing **both** screens can be admitted. The generated machine-readable results deliberately say `CANDIDATE_requires_visual_crown_review`; they are **not yet admitted**. There is no production whitelist until visually reviewed photographs, exclusions, and SHA-256 image identities are recorded.

## Important protection against bias

- Original images, genuine master references, all previous CSVs, thresholds, and the Android APK remain unchanged.
- Do not discard an image because its marker reading fails a proposed tolerance. Strict pose screening is performed independently, in advance.
- Do not infer that a watch is inauthentic or poorly manufactured because a photograph was excluded.
- Do not count alternate crops/scales of the same source image as independent poses.
- If any accepted photo has high inter-marker disagreement, investigate its quality independently; don't automatically delete it as a statistical outlier.
- If very few images remain, collect better images rather than loosen admission criteria quietly.
- This research should eventually produce an **explicit user-approved** frozen reference manifest, retaining every excluded photo and reason in an audit ledger.
