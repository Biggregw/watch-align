# Rolex GMT-Master II 126710 ideal geometry model

## Purpose

Define a model-first, mathematically ideal coordinate system for Watch Align GMT QC. The aim is to separate geometry that is exact by construction from model-specific design constants and from empirical image uncertainty.

This document does **not** claim Rolex factory tolerances. It defines an idealized canonical layout against which observed geometry can be measured.

## Canonical coordinate system

Use dial centre C=(0,0). Let normalized dial radius be R=1. Positive x points to 3 o'clock. Positive y points to 6 o'clock so image coordinates remain intuitive after rectification.

For any angle theta measured clockwise from 12 o'clock:

x = r * sin(theta)
y = -r * cos(theta)

Angles are expressed in degrees unless otherwise stated.

## Exact angular structure

These relationships are mathematical and should not require a genuine-watch population to define them.

### Hour-marker axes

For hour h in {1,...,12}:

theta_h = 30° * h, with 12 o'clock treated as 0° modulo 360°.

Equivalent canonical axes:
- 12 -> 0°
- 1 -> 30°
- 2 -> 60°
- 3 -> 90°
- 4 -> 120°
- 5 -> 150°
- 6 -> 180°
- 7 -> 210°
- 8 -> 240°
- 9 -> 270°
- 10 -> 300°
- 11 -> 330°

Every applied marker centre should lie on its corresponding radial axis after canonical rectification.

### Minute track

There are 60 ideal minute positions:

theta_m = 6° * m, m in {0,...,59}.

A minute-track phase estimate must therefore be reduced modulo 6° and reported as nearest-tick residual in [-3°, +3°). For example +5.75° should be reported as -0.25°.

### GMT 24-hour bezel

The bezel is a 24-hour scale, so ideal bezel positions are spaced by:

15° per hour.

The neutral bezel triangle at 24/0 hours is collinear with dial centre, the 12 o'clock dial axis and the 60-minute track axis.

### Mirror and opposite-point constraints

Exact ideal relations:
- 1 and 11 are mirrors about the vertical 12-6 axis.
- 2 and 10 are mirrors.
- 4 and 8 are mirrors.
- 5 and 7 are mirrors.
- 12 and 6 are opposite by 180°.
- 3 and 9 are opposite by 180°.
- Any accepted marker pair h and h+6 should differ by exactly 180° in the ideal model.

These are useful as internal consistency checks and do not require calibration data.

## Exact orientation rules by component class

### 12 triangle

Ideal conditions:
- triangle centreline angle = 0°
- apex lies on 12-axis
- base midpoint lies on 12-axis
- base is perpendicular to 12-axis
- left/right sides are mirror-symmetric about the 12-axis
- triangle centreline, minute-60 axis and dial centre are collinear

### Circular hour plots

Hours 1,2,4,5,7,8,10,11 on current 126710 black-dial GMT layouts use circular luminous plots. Their ideal QC variable is **position**, not rotation.

### Rectangular/baton plots

Hours 6 and 9 use elongated rectangular plots. Their long axis should be radial:
- 6 long-axis ideal angle = 180° radial
- 9 long-axis ideal angle = 270° radial

Body-axis residual should be reduced to the smallest equivalent orientation error because a rectangle's long axis is 180° periodic.

### 3 o'clock

No hour marker. The date aperture occupies this position and should be assessed separately.

## Model-specific design constants

The following are not derivable from pure angular geometry and must be calibrated from trusted design evidence or genuine reference images. They should be stored as versioned profile constants, normalized to dial radius or another stable local dimension.

Candidate constants:
- common centre radius of circular hour plots
- centre radius of 6/9 batons
- circular plot outer diameter / dial radius
- baton width / dial radius
- baton length / dial radius
- 12 triangle base width / dial radius
- 12 triangle height / dial radius
- 12 triangle base-to-minute-track radial gap / triangle base width
- minute-track inner and outer radii
- date-aperture centre radius
- date-aperture width and height
- date-aperture orientation
- cyclops centre and dimensions in observed image space only, not dial-plane physical coordinates
- rehaut inner/outer radii if used

## Proposed ideal-marker representation

For each component define:
- component id
- expected angular axis
- expected radial centre ratio
- shape class
- expected width ratio if applicable
- expected height ratio if applicable
- orientation rule
- periodicity of orientation residual

Example:

```json
{
  "id": "index_05",
  "angle_deg": 150.0,
  "radius_ratio": "CALIBRATED",
  "shape": "circle",
  "orientation": "not_applicable"
}
```

For hour 6:

```json
{
  "id": "index_06",
  "angle_deg": 180.0,
  "radius_ratio": "CALIBRATED",
  "shape": "baton",
  "orientation": "radial",
  "orientation_period_deg": 180.0
}
```

## Measurement residuals

After rectification, an observed marker centre (x,y) is converted to polar coordinates:

r_obs = sqrt(x^2+y^2)
theta_obs = atan2(x,-y)

For an ideal component angle theta_ref and calibrated radius r_ref:

angular residual = wrapped(theta_obs - theta_ref)
radial residual = r_obs - r_ref

tangential residual can be expressed as:

tangential = r_ref * sin(angular residual)

For small angles this approximates r_ref * angular residual in radians.

This is preferable to comparing one observed watch to another observed watch.

## 12-triangle residuals

The dedicated 12 module should report at least:
- centreline angular residual from 0°
- left/right symmetry residual
- base midpoint tangential residual from 12-axis
- apex tangential residual from 12-axis
- base width ratio
- height/base ratio
- base-to-minute-track gap / base width

The first four have mathematically ideal values of zero. The final three require model-specific design constants or empirical reference distributions.

## Minute-track phase

The app currently estimates a global 60-position phase. The ideal residual must be normalized by 6° periodicity:

phase_residual = ((phase + 3°) mod 6°) - 3°

Thus:
- +5.75° -> -0.25°
- -5.80° -> +0.20°
- +3.10° -> -2.90°

Confidence must be based on independent periodic support, not only whether a numerical phase exists.

## Confidence separation

Three distinct concepts should remain separate:

1. **Ideal geometry**: exact canonical target defined here.
2. **Design calibration**: model-specific normalized dimensions learned from trusted references.
3. **Measurement uncertainty**: error introduced by image quality, rectification, contour localization and point placement.

A component should be flagged only when the measured residual is meaningfully larger than expected measurement uncertainty and, where relevant, normal genuine production variation.

## Genuine reference population role

Genuine watches are still required, but for a narrower purpose:
- estimate model-specific radial and size constants
- quantify normal production variation around the ideal
- quantify photograph/annotation repeatability
- test whether different references share the same calibrated constants

Genuine images should **not** define angular spacing that mathematics already determines exactly.

## Known Rolex-supported structural facts

Rolex describes current 126710 GMT-Master II models as 40 mm watches with a 24-hour graduated bidirectional bezel. Rolex also describes the dial as using simple triangle, circle and rectangle hour-marker shapes, and the 24-hour hand reads against the bezel graduations. These facts support the structural model but do not provide factory dimensional drawings or tolerance values.

## Immediate implementation tasks

1. Add an `IdealGmtGeometry` class that generates exact angular axes and ideal normalized target points.
2. Replace local/empirical minute-phase interpretation with modulo-6 nearest-tick residual.
3. Refactor `IndexGeometryQcModule` so angular/tangential residuals are measured against exact model axes, while only radial placement uses calibrated profile constants.
4. Keep 12-triangle angular/symmetry targets mathematically zero.
5. Move radial/size constants to versioned profile data rather than hard-coded Java where possible.
6. Build a calibration worksheet from genuine 126710BLNR/BLRO/GRNR and CHNR-family images to test which normalized constants are shared and which are reference-specific.
7. Never label an ideal residual as a Rolex factory tolerance.
