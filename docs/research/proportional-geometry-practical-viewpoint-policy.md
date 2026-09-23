# Proportional Geometry - Practical Viewpoint Policy

Status: live supplemental research

Date: 2026-09-23

Purpose: prevent Watch Align proportional-geometry research from over-optimising for extreme camera tilt when the intended product input is ordinary dealer QC photography, which is usually close enough to frontal for the dial to remain measurable.

## Product assumption

The proportional engine should optimise first for the common useful case: a clear, approximately frontal QC photograph with the whole dial visible.

Perfect orthographic/frontal capture is not required.

The system should tolerate modest viewpoint error, but it should not sacrifice coverage, simplicity or measurement stability in the common case merely to mathematically rescue highly oblique photographs.

## Recommended operating modes

### Mode A - ordinary QC view

Approximate apparent tilt <= 10 degrees.

This is the primary design target.

Preferred behaviour:

- use the simplest normalisation that calibration demonstrates is sufficiently repeatable,
- favour high coverage and robust segmentation over mathematically elaborate transforms,
- allow radial, tangential and orientation features when their calibration behaviour is stable,
- preserve viewpoint metadata so residual tilt dependence remains measurable,
- do not require projective correction merely because it is theoretically more exact.

If simple dial-radius or peer-relative normalisation outperforms a projective method in repeatability/coverage, prefer the simple method here.

### Mode B - moderately oblique but still useful

Approximate apparent tilt >10 to 15 degrees.

Preferred behaviour:

- keep angular/incidence-style measurements where supported,
- use projective/cross-ratio radial features only if calibration demonstrates they materially improve stability,
- downgrade confidence on angle-sensitive radial/shape features,
- treat unsupported features as unavailable rather than forcing a correction.

### Mode C - strongly oblique

Approximate apparent tilt >15 degrees.

This is not the primary product target.

Preferred behaviour:

- retain only features empirically shown to remain stable,
- suppress ordinary radial conclusions,
- ask for another/straighter QC view where the product workflow permits,
- do not spend disproportionate engineering effort trying to rescue every high-tilt image.

## Research priority consequence

When choosing between two feature representations, rank them using a product-weighted order:

1. accuracy/repeatability on ordinary QC views,
2. usable-image coverage,
3. low false-positive risk,
4. interpretability,
5. graceful degradation at moderate tilt,
6. extreme-tilt robustness.

Extreme-tilt invariance should not outrank ordinary-view performance unless the intended input distribution later proves otherwise.

## Important distinction

Camera angle still matters scientifically because it can create false residuals.

The practical conclusion is not "ignore tilt".

It is:

- estimate tilt,
- validate that selected rules are stable over the ordinary QC range,
- gate or downgrade features when the image leaves that range,
- avoid unnecessary complexity if simple proportions are already stable enough in the images users will actually submit.

## Implication for the current GMT 12 experiment

The current simple-vs-projective result should be interpreted pragmatically.

If the simple representation has:

- materially higher coverage,
- low enough repeatability error inside <=10 degrees,
- and no meaningful residual tilt trend inside that operating band,

then it may be the preferred production candidate even if a projective representation is theoretically more viewpoint-correct.

Projective methods should remain available as research/fallback candidates, not mandatory architecture.

## Implication for future QC rule design

For marker defects commonly raised in community QC:

- angular/tangential placement,
- marker orientation,
- radial high/low placement,
- size/span differences,

prefer rules that are dependable on ordinary near-frontal dealer photos.

The product can simply report "view too oblique for this measurement" when a photo falls outside a rule's supported range.

That is preferable to producing false precision from an unstable perspective correction.
